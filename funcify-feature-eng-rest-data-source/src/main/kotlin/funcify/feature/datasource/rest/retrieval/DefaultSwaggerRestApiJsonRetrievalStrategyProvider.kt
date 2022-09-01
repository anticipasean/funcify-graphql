package funcify.feature.datasource.rest.retrieval

import arrow.core.Either
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.json.JsonMapper
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.concurrent.Executor
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

internal class DefaultSwaggerRestApiJsonRetrievalStrategyProvider(
    private val asyncExecutor: Executor,
    private val jsonMapper: JsonMapper,
    private val postProcessingStrategy: SwaggerRestApiJsonResponsePostProcessingStrategy
) : SwaggerRestApiJsonRetrievalStrategyProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestApiJsonRetrievalStrategyProvider>()
    }

    override fun providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean {
        return dataSourceKey.sourceIndexType.isSubclassOf(RestApiSourceIndex::class)
    }

    override fun createMultipleSourceIndicesJsonRetrievalFunctionFor(
        dataSource: DataSource<RestApiSourceIndex>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    ): Try<MultipleSourceIndicesJsonRetrievalFunction> {
        logger.debug(
            """create_schematic_path_based_json_retrieval_function_for: [ 
            |data_source: ${dataSource.key}, 
            |source_vertices.size: ${sourceVertices.size}, 
            |parameter_vertices.size: ${parameterVertices.size} 
            |]""".flatten()
        )
        return Try.attempt {
            DefaultSwaggerRestDataSourceJsonRetrievalStrategy(
                asyncExecutor = asyncExecutor,
                jsonMapper = jsonMapper,
                dataSource = dataSource as RestApiDataSource,
                parameterVertices = parameterVertices,
                sourceVertices = sourceVertices,
                postProcessingStrategy = postProcessingStrategy
            )
        }
    }
}
