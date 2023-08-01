package funcify.feature.materializer.context.graph

import arrow.core.continuations.eagerEffect
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.context.graph.MaterializationGraphContext.Builder
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-10-08
 */
internal class DefaultMaterializationGraphContextFactory : MaterializationGraphContextFactory {

    companion object {
        internal class DefaultMaterializationGraphContextBuilder(
            private var materializationMetamodel: MaterializationMetamodel? = null,
            private var operationDefinition: OperationDefinition? = null,
            private var queryVariables: PersistentMap<String, Any?> = persistentMapOf(),
            private var requestParameterGraph:
                PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge> =
                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            private var materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode> =
                persistentMapOf(),
            private var parameterIndexPathsBySourceIndexPath:
                PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> =
                persistentMapOf(),
            private var retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<GQLOperationPath, RetrievalFunctionSpec> =
                persistentMapOf()
        ) : Builder {

            override fun materializationMetamodel(
                materializationMetamodel: MaterializationMetamodel
            ): Builder {
                this.materializationMetamodel = materializationMetamodel
                return this
            }

            override fun operationDefinition(operationDefinition: OperationDefinition): Builder {
                this.operationDefinition = operationDefinition
                return this
            }

            override fun queryVariables(queryVariables: PersistentMap<String, Any?>): Builder {
                this.queryVariables = queryVariables
                return this
            }

            override fun requestParameterGraph(
                requestParameterGraph:
                    PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>
            ): Builder {
                this.requestParameterGraph = requestParameterGraph
                return this
            }

            override fun addVertexToRequestParameterGraph(vertex: SchematicVertex): Builder {
                this.requestParameterGraph =
                    requestParameterGraph.putVertex(vertex, SchematicVertex::path)
                return this
            }

            override fun addEdgeToRequestParameterGraph(edge: RequestParameterEdge): Builder {
                eagerEffect<String, RequestParameterEdge> {
                        ensure(edge.id.first in requestParameterGraph.verticesByPath) {
                            "first path in edge.id does not have corresponding vertex in request_parameter_graph"
                        }
                        ensure(edge.id.second in requestParameterGraph.verticesByPath) {
                            "second path in edge.id does not have corresponding vertex in request_parameter_graph"
                        }
                        edge
                    }
                    .fold(
                        { message ->
                            throw MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                message
                            )
                        },
                        { e ->
                            this.requestParameterGraph =
                                this.requestParameterGraph.putEdge(e, RequestParameterEdge::id)
                        }
                    )
                return this
            }

            override fun removeEdgesFromRequestParameterGraph(
                edgeId: Pair<GQLOperationPath, GQLOperationPath>
            ): Builder {
                eagerEffect<String, Pair<GQLOperationPath, GQLOperationPath>> {
                        ensure(edgeId.first in requestParameterGraph.verticesByPath) {
                            "first path in edge.id does not have corresponding vertex in request_parameter_graph"
                        }
                        ensure(edgeId.second in requestParameterGraph.verticesByPath) {
                            "second path in edge.id does not have corresponding vertex in request_parameter_graph"
                        }
                        ensure(requestParameterGraph.getEdgesFromPathToPath(edgeId).isNotEmpty()) {
                            "no edges exist from [ path1: %s ] to [ path2: %s ]".format(
                                edgeId.first,
                                edgeId.second
                            )
                        }
                        edgeId
                    }
                    .fold(
                        { message ->
                            throw MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                message
                            )
                        },
                        { id ->
                            this.requestParameterGraph = this.requestParameterGraph.removeEdges(id)
                        }
                    )
                return this
            }

            override fun removeEdgesFromRequestParameterGraph(
                path1: GQLOperationPath,
                path2: GQLOperationPath
            ): Builder {
                return removeEdgesFromRequestParameterGraph(path1 to path2)
            }

            override fun materializedParameterValuesByPath(
                materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>
            ): Builder {
                this.materializedParameterValuesByPath = materializedParameterValuesByPath
                return this
            }

            override fun addMaterializedParameterValueForPath(
                path: GQLOperationPath,
                value: JsonNode
            ): Builder {
                this.materializedParameterValuesByPath =
                    materializedParameterValuesByPath.put(path, value)
                return this
            }

            override fun parameterIndexPathsBySourceIndexPath(
                parameterIndexPathsBySourceIndexPath:
                    PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>
            ): Builder {
                this.parameterIndexPathsBySourceIndexPath = parameterIndexPathsBySourceIndexPath
                return this
            }

            override fun addParameterIndexPathForSourceIndexPath(
                path: GQLOperationPath,
                parameterIndexPath: GQLOperationPath
            ): Builder {
                this.parameterIndexPathsBySourceIndexPath =
                    parameterIndexPathsBySourceIndexPath.put(
                        path,
                        parameterIndexPathsBySourceIndexPath
                            .getOrElse(path) { persistentSetOf() }
                            .add(parameterIndexPath)
                    )
                return this
            }

            override fun retrievalFunctionSpecsByTopSourceIndexPath(
                retrievalFunctionSpecsByTopSourceIndexPath:
                    PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
            ): Builder {
                this.retrievalFunctionSpecByTopSourceIndexPath =
                    retrievalFunctionSpecsByTopSourceIndexPath
                return this
            }

            override fun addRetrievalFunctionSpecForTopSourceIndexPath(
                path: GQLOperationPath,
                spec: RetrievalFunctionSpec
            ): Builder {
                this.retrievalFunctionSpecByTopSourceIndexPath =
                    this.retrievalFunctionSpecByTopSourceIndexPath.put(path, spec)
                return this
            }

            override fun build(): MaterializationGraphContext {
                return eagerEffect<String, MaterializationGraphContext> {
                        ensure(materializationMetamodel != null) {
                            "materialization_metamodel has not been provided"
                        }
                        ensure(operationDefinition != null) {
                            "operation_definition has not been provided"
                        }
                        DefaultMaterializationGraphContext(
                            materializationMetamodel!!,
                            operationDefinition!!,
                            queryVariables,
                            requestParameterGraph,
                            materializedParameterValuesByPath,
                            parameterIndexPathsBySourceIndexPath,
                            retrievalFunctionSpecByTopSourceIndexPath,
                        )
                    }
                    .fold(
                        { message ->
                            throw MaterializerException(
                                MaterializerErrorResponse.METAMODEL_GRAPH_CREATION_ERROR,
                                "materialization_graph_context could not be built: [ message: %s ]".format(
                                    message
                                )
                            )
                        },
                        { context -> context }
                    )
            }
        }

        internal data class DefaultMaterializationGraphContext(
            override val materializationMetamodel: MaterializationMetamodel,
            override val operationDefinition: OperationDefinition,
            override val queryVariables: PersistentMap<String, Any?>,
            override val requestParameterGraph:
                PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>,
            override val materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>,
            override val parameterIndexPathsBySourceIndexPath:
                PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
            override val retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
        ) : MaterializationGraphContext {

            override fun update(transformer: Builder.() -> Builder): MaterializationGraphContext {
                return transformer(
                        DefaultMaterializationGraphContextBuilder(
                            materializationMetamodel = materializationMetamodel,
                            operationDefinition = operationDefinition,
                            queryVariables = queryVariables,
                            requestParameterGraph = requestParameterGraph,
                            materializedParameterValuesByPath = materializedParameterValuesByPath,
                            parameterIndexPathsBySourceIndexPath =
                                parameterIndexPathsBySourceIndexPath,
                            retrievalFunctionSpecByTopSourceIndexPath =
                                retrievalFunctionSpecByTopSourceIndexPath
                        )
                    )
                    .build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultMaterializationGraphContextBuilder()
    }
}
