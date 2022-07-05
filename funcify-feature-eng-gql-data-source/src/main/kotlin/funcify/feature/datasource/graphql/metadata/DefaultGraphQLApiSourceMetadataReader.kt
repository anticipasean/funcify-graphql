package funcify.feature.datasource.graphql.metadata

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.or
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.control.RelationshipSpliterators
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentSetValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 4/8/22
 */
internal class DefaultGraphQLApiSourceMetadataReader(
    private val graphQLSourceIndexFactory: GraphQLSourceIndexFactory,
    private val graphQLApiSourceMetadataFilter: GraphQLApiSourceMetadataFilter
) : GraphQLApiSourceMetadataReader {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLApiSourceMetadataReader>()

        private data class GqlSourceContext(
            val dataSourceKey: DataSource.Key<GraphQLSourceIndex>,
            val rootSourceContainerType: GraphQLSourceContainerType,
            val graphQLFieldDefinitionToPath: PersistentMap<GraphQLFieldDefinition, SchematicPath> =
                persistentMapOf(),
            val indicesByPath: PersistentMap<SchematicPath, PersistentSet<GraphQLSourceIndex>> =
                persistentMapOf(),
            val sourceContainerTypeToAttributeTypes:
                PersistentMap<GraphQLSourceContainerType, PersistentSet<GraphQLSourceAttribute>> =
                persistentMapOf()
        )
    }

    override fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        input: GraphQLSchema
    ): SourceMetamodel<GraphQLSourceIndex> {
        logger.debug(
            """read_source_container_types_from_metadata: 
                |[ input.query_type.name: ${input.queryType.name}, 
                | query_type.field_definitions.size: ${input.queryType.fieldDefinitions.size} ]
                |""".flattenIntoOneLine()
        )
        if (input.queryType.fieldDefinitions.isEmpty()) {
            val message =
                """graphql_schema input for metadata on graphql 
                |source does not have any query type 
                |field definitions""".flattenIntoOneLine()
            logger.error(
                """read_source_container_types_from_metadata: 
                |[ error: ${message} ]
                |""".flattenIntoOneLine()
            )
            throw GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
        }
        return input
            .queryType
            .fieldDefinitions
            .parallelStream()
            .filter { gqlFieldDef: GraphQLFieldDefinition ->
                graphQLApiSourceMetadataFilter.includeGraphQLFieldDefinition(gqlFieldDef)
            }
            .reduce(
                createContextForRootQueryType(dataSourceKey, input.queryType),
                { gscAttempt: Try<GqlSourceContext>, fieldDef: GraphQLFieldDefinition ->
                    gscAttempt.map { gsc -> addFieldDefinitionToGqlContext(gsc, fieldDef) }
                },
                { gsc1Attempt, gsc2Attempt ->
                    gsc1Attempt.flatMap { gsc1 ->
                        gsc2Attempt.map { gsc2 -> combineTopLevelGqlSourceContexts(gsc1, gsc2) }
                    }
                }
            )
            .let { gscAttempt: Try<GqlSourceContext> ->
                gscAttempt.map { gsc -> extractSourceContainerTypeIterableFromFinalContext(gsc) }
            }
            .orElseThrow()
    }

    private fun createContextForRootQueryType(
        dataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        queryType: GraphQLObjectType
    ): Try<GqlSourceContext> {
        return Try.attempt {
            val graphQLSourceContainerType =
                graphQLSourceIndexFactory
                    .createRootSourceContainerTypeForDataSourceKey(dataSourceKey)
                    .forGraphQLQueryObjectType(queryType, graphQLApiSourceMetadataFilter)
                    .orElseThrow()
            GqlSourceContext(
                dataSourceKey = dataSourceKey,
                rootSourceContainerType = graphQLSourceContainerType,
                indicesByPath =
                    graphQLSourceContainerType
                        .sourceAttributes
                        .stream()
                        .map { gsa -> gsa.sourcePath to gsa }
                        .reducePairsToPersistentSetValueMap()
                        .combineWithPersistentSetValueMap(
                            persistentMapOf(
                                graphQLSourceContainerType.sourcePath to
                                    persistentSetOf<GraphQLSourceIndex>(graphQLSourceContainerType)
                            )
                        ),
                sourceContainerTypeToAttributeTypes =
                    persistentMapOf(
                        graphQLSourceContainerType to
                            graphQLSourceContainerType.sourceAttributes.toPersistentSet()
                    ),
                graphQLFieldDefinitionToPath =
                    graphQLSourceContainerType
                        .sourceAttributes
                        .stream()
                        .map { gsa -> gsa.schemaFieldDefinition to gsa.sourcePath }
                        .reducePairsToPersistentMap()
            )
        }
    }

    private fun addFieldDefinitionToGqlContext(
        gqlSourceContext: GqlSourceContext,
        graphQLFieldDefinition: GraphQLFieldDefinition
    ): GqlSourceContext {
        return Stream.of(graphQLFieldDefinition)
            .flatMap { gqlFieldDef: GraphQLFieldDefinition ->
                val traversalFunction: (GraphQLFieldDefinition) -> Stream<GraphQLFieldDefinition> =
                    { gqlf: GraphQLFieldDefinition ->
                        GraphQLOutputFieldsContainerTypeExtractor.invoke(gqlf.type)
                            .map { gqlfc: GraphQLFieldsContainer ->
                                gqlfc.fieldDefinitions.stream().filter { gfd: GraphQLFieldDefinition
                                    ->
                                    graphQLApiSourceMetadataFilter.includeGraphQLFieldDefinition(
                                        gfd
                                    )
                                }
                            }
                            .getOrElse { Stream.empty() }
                    }
                StreamSupport.stream(
                    RelationshipSpliterators.recursiveParentChildSpliterator(
                        rootParent = gqlFieldDef,
                        recursiveChildrenTraverser = traversalFunction
                    ),
                    false
                )
            }
            .reduce(
                gqlSourceContext,
                {
                    gqlSrcCtx: GqlSourceContext,
                    parentChildFieldDefPair: Pair<GraphQLFieldDefinition, GraphQLFieldDefinition> ->
                    addFieldDefinitionPairToGqlContext(
                        currentContext = gqlSrcCtx,
                        parentFieldDefinition = parentChildFieldDefPair.first,
                        childFieldDefinition = parentChildFieldDefPair.second
                    )
                },
                { _, gql2 -> // since sequential, either leaf node is the same
                    gql2
                }
            )
    }

    private fun addFieldDefinitionPairToGqlContext(
        currentContext: GqlSourceContext,
        parentFieldDefinition: GraphQLFieldDefinition,
        childFieldDefinition: GraphQLFieldDefinition
    ): GqlSourceContext {
        return when {
            /**
             * Case 0: UNNECESSARY to handle: Both parent and child field definitions have yet to be
             * assigned a schematic path. If
             * - the query type and its field definitions have been properly handled,
             * - their respective indices have been created and assigned to vars in the context,
             * - and the parent and child relationships are being processed in order, => then case 0
             * is not necessary
             */
            /** Case 1: Only the child field definition has yet to be assigned a schematic path */
            !currentContext.graphQLFieldDefinitionToPath.containsKey(childFieldDefinition) -> {
                val parentPath =
                    currentContext.graphQLFieldDefinitionToPath[parentFieldDefinition]!!
                val parentAsContainerType: GraphQLSourceContainerType =
                    currentContext.indicesByPath[parentPath]
                        .toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<GraphQLSourceContainerType>()
                        .firstOrNull()
                        .toOption()
                        .or(
                            graphQLSourceIndexFactory
                                .createSourceContainerTypeForDataSourceKey(
                                    currentContext.dataSourceKey
                                )
                                .forAttributePathAndDefinition(parentPath, parentFieldDefinition)
                                .orElseThrow()
                                .some()
                        )
                        .getOrElse {
                            throw GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                "parent_type not container type"
                            )
                        }
                val childAsAttributeType: GraphQLSourceAttribute =
                    graphQLSourceIndexFactory
                        .createSourceAttributeForDataSourceKey(currentContext.dataSourceKey)
                        .withParentPathAndDefinition(parentPath, parentFieldDefinition)
                        .forChildAttributeDefinition(childFieldDefinition)
                        .orElseThrow()
                val updatedAttributeSet: PersistentSet<GraphQLSourceAttribute> =
                    currentContext
                        .sourceContainerTypeToAttributeTypes
                        .getOrDefault(parentAsContainerType, persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap:
                    PersistentMap<
                        GraphQLSourceContainerType, PersistentSet<GraphQLSourceAttribute>> =
                    currentContext.sourceContainerTypeToAttributeTypes.put(
                        parentAsContainerType,
                        updatedAttributeSet
                    )
                val updatedPathMap: PersistentMap<GraphQLFieldDefinition, SchematicPath> =
                    currentContext.graphQLFieldDefinitionToPath.put(
                        childFieldDefinition,
                        childAsAttributeType.sourcePath
                    )
                val updatedIndicesByPath:
                    PersistentMap<SchematicPath, PersistentSet<GraphQLSourceIndex>> =
                    currentContext
                        .indicesByPath
                        .put(
                            parentPath,
                            currentContext
                                .indicesByPath
                                .getOrDefault(parentPath, persistentSetOf())
                                .add(parentAsContainerType)
                        )
                        .put(
                            childAsAttributeType.sourcePath,
                            currentContext
                                .indicesByPath
                                .getOrDefault(childAsAttributeType.sourcePath, persistentSetOf())
                                .add(childAsAttributeType)
                        )

                currentContext.copy(
                    sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                    graphQLFieldDefinitionToPath = updatedPathMap,
                    indicesByPath = updatedIndicesByPath
                )
            }
            else -> {
                /**
                 * Case 2: Both the parent and child field definitions already have schematic path
                 * mappings
                 */
                val parentPath =
                    currentContext.graphQLFieldDefinitionToPath[parentFieldDefinition]!!
                val childPath = currentContext.graphQLFieldDefinitionToPath[childFieldDefinition]!!
                val parentAsContainerType =
                    currentContext.indicesByPath[parentPath]
                        .toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<DefaultGraphQLSourceContainerType>()
                        .firstOrNull()
                        .toOption()
                        .or(
                            graphQLSourceIndexFactory
                                .createSourceContainerTypeForDataSourceKey(
                                    currentContext.dataSourceKey
                                )
                                .forAttributePathAndDefinition(parentPath, parentFieldDefinition)
                                .orElseThrow()
                                .some()
                        )
                        .getOrElse {
                            throw GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                "parent_type not container type"
                            )
                        }
                val childAsAttributeType =
                    currentContext.indicesByPath[childPath]
                        .toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<DefaultGraphQLSourceAttribute>()
                        .firstOrNull()
                        .toOption()
                        .getOrElse {
                            graphQLSourceIndexFactory
                                .createSourceAttributeForDataSourceKey(currentContext.dataSourceKey)
                                .withParentPathAndDefinition(parentPath, parentFieldDefinition)
                                .forChildAttributeDefinition(childFieldDefinition)
                                .orElseThrow()
                        }
                val updatedAttributeSet =
                    currentContext
                        .sourceContainerTypeToAttributeTypes
                        .getOrDefault(parentAsContainerType, persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap =
                    currentContext.sourceContainerTypeToAttributeTypes.put(
                        parentAsContainerType,
                        updatedAttributeSet
                    )
                val updatedIndicesByPath =
                    currentContext
                        .indicesByPath
                        .put(
                            parentPath,
                            currentContext
                                .indicesByPath
                                .getOrDefault(parentPath, persistentSetOf())
                                .add(parentAsContainerType)
                        )
                        .put(
                            childPath,
                            currentContext
                                .indicesByPath
                                .getOrDefault(childPath, persistentSetOf())
                                .add(childAsAttributeType)
                        )

                currentContext.copy(
                    sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                    indicesByPath = updatedIndicesByPath
                )
            }
        }
    }

    private fun combineTopLevelGqlSourceContexts(
        gqlSourceContext1: GqlSourceContext,
        gqlSourceContext2: GqlSourceContext
    ): GqlSourceContext {
        val updatedGraphQLFieldDefinitionToPath =
            gqlSourceContext1.graphQLFieldDefinitionToPath.putAll(
                gqlSourceContext2.graphQLFieldDefinitionToPath
            )
        val updatedIndicesByPath =
            gqlSourceContext2.indicesByPath.combineWithPersistentSetValueMap(
                gqlSourceContext1.indicesByPath
            )
        val updatedSourceContainerTypeToAttributeTypes =
            gqlSourceContext2.sourceContainerTypeToAttributeTypes.combineWithPersistentSetValueMap(
                gqlSourceContext1.sourceContainerTypeToAttributeTypes
            )
        return GqlSourceContext(
            dataSourceKey = gqlSourceContext1.dataSourceKey,
            rootSourceContainerType = gqlSourceContext1.rootSourceContainerType,
            graphQLFieldDefinitionToPath = updatedGraphQLFieldDefinitionToPath,
            indicesByPath = updatedIndicesByPath,
            sourceContainerTypeToAttributeTypes = updatedSourceContainerTypeToAttributeTypes
        )
    }

    private fun extractSourceContainerTypeIterableFromFinalContext(
        context: GqlSourceContext
    ): GraphQLSourceMetamodel {
        return GraphQLSourceMetamodel(
            sourceIndicesByPath =
                context
                    .sourceContainerTypeToAttributeTypes
                    .streamPairs()
                    .reduce(
                        context.indicesByPath,
                        { pm, pair ->
                            val updatedSourceContainerType =
                                graphQLSourceIndexFactory
                                    .updateSourceContainerType(pair.first)
                                    .withChildSourceAttributes(pair.second)
                                    .orElseThrow()
                            pm.put(
                                updatedSourceContainerType.sourcePath,
                                pm.getOrDefault(
                                        updatedSourceContainerType.sourcePath,
                                        persistentSetOf()
                                    )
                                    .map { gsi: GraphQLSourceIndex ->
                                        gsi.toOption()
                                            .filterIsInstance<GraphQLSourceContainerType>()
                                            .map { _ -> updatedSourceContainerType }
                                            .getOrElse { gsi }
                                    }
                                    .toPersistentSet()
                            )
                        },
                        { pm1, pm2 -> pm2.combineWithPersistentSetValueMap(pm1) }
                    )
        )
    }
}
