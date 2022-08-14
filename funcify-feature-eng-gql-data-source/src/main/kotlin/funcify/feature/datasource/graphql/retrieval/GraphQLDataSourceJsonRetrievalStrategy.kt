package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategy
import funcify.feature.json.JsonMapper
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.GraphqlErrorException
import graphql.language.AstPrinter
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-12
 */
internal class GraphQLDataSourceJsonRetrievalStrategy(
    internal val jsonMapper: JsonMapper,
    internal val graphQLDataSource: GraphQLApiDataSource,
    override val parameterVertices:
        ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    override val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
) : DataSourceSpecificJsonRetrievalStrategy<GraphQLSourceIndex> {

    companion object {
        private val logger: Logger = loggerFor<GraphQLDataSourceJsonRetrievalStrategy>()
        private const val DATA_KEY = "data"
        private const val ERRORS_KEY = "errors"
    }

    override val dataSource: DataSource<GraphQLSourceIndex> = graphQLDataSource

    override val parameterPaths: ImmutableSet<SchematicPath> by lazy { super.parameterPaths }

    override val sourcePaths: ImmutableSet<SchematicPath> by lazy { super.sourcePaths }

    private val queryOperationDefinitionFunction by lazy {
        GraphQLQueryPathBasedComposer
            .createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
                sourcePaths
            )
    }

    init {
        parameterVertices
            .asSequence()
            .filterNot { pjvOrPlv ->
                pjvOrPlv.fold(
                    { pjv ->
                        pjv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { plv ->
                        plv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>()) {
                ps,
                pjvOrPlv ->
                ps.add(pjvOrPlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { pjvOrPlv ->
                                pjvOrPlv.fold(
                                    ParameterJunctionVertex::path,
                                    ParameterLeafVertex::path
                                )
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following parameter_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
        sourceVertices
            .asSequence()
            .filterNot { sjvOrSlv ->
                sjvOrSlv.fold(
                    { sjv ->
                        sjv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { slv ->
                        slv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>()) { ps, sjvOrSlv
                ->
                ps.add(sjvOrSlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { sjvOrSlv ->
                                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following source_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
    }

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        val parameterPathsSetStr = valuesByParameterPaths.keys.joinToString(",", "{ ", " }")
        logger.debug("invoke: [ values_by_parameter_paths.keys: $parameterPathsSetStr ]")
        return Deferred.fromAttempt(
                attemptToCreateGraphQLQueryStringFromValuesByParameterPathsInput(
                    valuesByParameterPaths,
                    parameterPathsSetStr
                )
            )
            .flatMap { queryString ->
                graphQLDataSource.graphQLApiService.executeSingleQuery(queryString)
            }
            .flatMap(convertResponseJsonIntoThrowableIfErrorsPresent())
            .flatMap(convertResponseJsonIntoJsonValuesBySchematicPathMap())
    }

    private fun attemptToCreateGraphQLQueryStringFromValuesByParameterPathsInput(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>,
        parameterPathsSetStr: String,
    ): Try<String> {
        return Try.attempt {
                queryOperationDefinitionFunction.invoke(
                    valuesByParameterPaths
                        .asSequence()
                        .filter { (parameterPath, _) -> parameterPath in parameterPaths }
                        .toPersistentMap()
                )
            }
            .map { queryOperationDefinition: OperationDefinition ->
                AstPrinter.printAst(queryOperationDefinition)
            }
            .mapFailure { t ->
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    """error occurred when transforming source and 
                       |parameter paths and values into graphql 
                       |query string
                       |[ parameter_paths: $parameterPathsSetStr ]""".flatten(),
                    t
                )
            }
    }

    private fun convertResponseJsonIntoThrowableIfErrorsPresent():
        (JsonNode) -> Deferred<JsonNode> {
        return { responseJson: JsonNode ->
            Deferred.fromAttempt(
                responseJson
                    .toOption()
                    .filter { jn ->
                        jn.has(ERRORS_KEY) &&
                            jn.path(ERRORS_KEY).isArray &&
                            !jn.path(ERRORS_KEY).isEmpty
                    }
                    .map { jn ->
                        jsonMapper
                            .fromJsonNode(jn)
                            .toKotlinObject(GraphqlErrorException::class)
                            .map { graphqlErrorException: GraphqlErrorException ->
                                GQLDataSourceException(
                                    GQLDataSourceErrorResponse.Companion.GQLSpecificErrorResponse(
                                        graphqlErrorException
                                    )
                                )
                            }
                            .mapFailure { _: Throwable ->
                                // ignore error if unable to deserialize graphql_error_json into
                                // graphql_error instance
                                val firstErrorJSONAsString =
                                    jsonMapper
                                        .fromJsonNode(jn.path(ERRORS_KEY).first())
                                        .toJsonString()
                                        .orElse("<NA>")
                                GQLDataSourceException(
                                    GQLDataSourceErrorResponse.CLIENT_ERROR,
                                    "error received: [ $firstErrorJSONAsString ]"
                                )
                            }
                    }
                    .fold(
                        {
                            // treat as success if no error could be created from response_json
                            // input
                            Try.success(responseJson)
                        },
                        { errorJsonAsExceptionTry ->
                            // take either the deserialized error response or the synthetic one with
                            // the graphql_error as a json_string
                            errorJsonAsExceptionTry
                                .either()
                                .fold(Try.Companion::failure, Try.Companion::failure)
                        }
                    )
            )
        }
    }

    private fun convertResponseJsonIntoJsonValuesBySchematicPathMap():
        (JsonNode) -> Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        TODO("Not yet implemented")
    }
}
