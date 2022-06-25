package funcify.feature.datasource.sdl

import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.Builder
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.NamedNode
import graphql.language.Node
import graphql.language.ScalarTypeDefinition
import graphql.language.Type
import graphql.language.TypeName
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
internal class DefaultSchematicVertexSDLDefinitionCreationContextFactory :
    SchematicVertexSDLDefinitionCreationContextFactory {

    companion object {

        private val logger: Logger =
            loggerFor<DefaultSchematicVertexSDLDefinitionCreationContextFactory>()

        internal class DefaultSchematicSDLDefinitionCreationContextBuilder<V : SchematicVertex>(
            private var scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            private var namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            private var sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            private var sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            private var metamodelGraph: MetamodelGraph,
            private var currentVertex: V
        ) : Builder<V> {

            override fun addSDLDefinitionForSchematicPath(
                schematicPath: SchematicPath,
                sdlDefinition: Node<*>,
            ): Builder<V> {
                when (sdlDefinition) {
                    is NamedNode -> {
                        namedSDLDefinitionsByName =
                            namedSDLDefinitionsByName.put(sdlDefinition.name, sdlDefinition)
                        sdlDefinitionsBySchematicPath =
                            sdlDefinitionsBySchematicPath.put(schematicPath, sdlDefinition)
                    }
                    else -> {
                        sdlDefinitionsBySchematicPath =
                            sdlDefinitionsBySchematicPath.put(schematicPath, sdlDefinition)
                    }
                }
                return this
            }

            override fun addNamedNonScalarSDLType(namedNonScalarType: TypeName): Builder<V> {
                if (namedNonScalarType.name !in namedSDLDefinitionsByName) {
                    namedSDLDefinitionsByName =
                        namedSDLDefinitionsByName.put(namedNonScalarType.name, namedNonScalarType)
                }
                return this
            }

            override fun <SV : SchematicVertex> nextVertex(nextVertex: SV): Builder<SV> {
                return DefaultSchematicSDLDefinitionCreationContextBuilder<SV>(
                    scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                    namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                    sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                    sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                    metamodelGraph = metamodelGraph,
                    currentVertex = nextVertex
                )
            }

            override fun build(): SchematicVertexSDLDefinitionCreationContext<V> {
                @Suppress("UNCHECKED_CAST") //
                return when (val nextVertex: V = currentVertex) {
                    is SourceRootVertex -> {
                        DefaultSourceRootVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceJunctionVertex -> {
                        DefaultSourceJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceLeafVertex -> {
                        DefaultSourceLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterJunctionVertex -> {
                        DefaultParameterJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterLeafVertex -> {
                        DefaultParameterLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    else -> {
                        val expectedGraphVertexTypeNamesSet: String =
                            sequenceOf(
                                    SourceRootVertex::class,
                                    SourceJunctionVertex::class,
                                    SourceLeafVertex::class,
                                    ParameterJunctionVertex::class,
                                    ParameterLeafVertex::class
                                )
                                .map { kcls -> kcls.simpleName }
                                .joinToString(", ", "{ ", " }")
                        val message =
                            """current/next_vertex not instance of 
                                |graph vertex type: [ expected: 
                                |one of ${expectedGraphVertexTypeNamesSet}, 
                                |actual: ${currentVertex::class.qualifiedName} ]
                                |""".flattenIntoOneLine()
                        logger.error(message)
                        throw SchemaException(SchemaErrorResponse.INVALID_INPUT, message)
                    }
                } as
                    SchematicVertexSDLDefinitionCreationContext<V>
            }
        }

        internal abstract class DefaultBaseSchematicSDLDefinitionCreationContext<
            V : SchematicVertex>(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: V
        ) : SchematicVertexSDLDefinitionCreationContext<V> {

            override fun <SV : SchematicVertex> update(
                updater: Builder<V>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder: DefaultSchematicSDLDefinitionCreationContextBuilder<V> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<V>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }

        internal data class DefaultSourceRootVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceRootVertex
        ) :
            DefaultBaseSchematicSDLDefinitionCreationContext<SourceRootVertex>(
                scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                metamodelGraph = metamodelGraph,
                currentVertex = currentVertex
            ),
            SourceRootVertexSDLDefinitionCreationContext {}

        internal data class DefaultSourceJunctionVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceJunctionVertex
        ) :
            DefaultBaseSchematicSDLDefinitionCreationContext<SourceJunctionVertex>(
                scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                metamodelGraph = metamodelGraph,
                currentVertex = currentVertex
            ),
            SourceJunctionVertexSDLDefinitionCreationContext {}

        internal data class DefaultSourceLeafVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceLeafVertex
        ) :
            DefaultBaseSchematicSDLDefinitionCreationContext<SourceLeafVertex>(
                scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                metamodelGraph = metamodelGraph,
                currentVertex = currentVertex
            ),
            SourceLeafVertexSDLDefinitionCreationContext {}

        internal data class DefaultParameterJunctionVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: ParameterJunctionVertex
        ) :
            DefaultBaseSchematicSDLDefinitionCreationContext<ParameterJunctionVertex>(
                scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                metamodelGraph = metamodelGraph,
                currentVertex = currentVertex
            ),
            ParameterJunctionVertexSDLDefinitionCreationContext {}

        internal data class DefaultParameterLeafVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath: PersistentMap<SchematicPath, Node<*>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: ParameterLeafVertex
        ) :
            DefaultBaseSchematicSDLDefinitionCreationContext<ParameterLeafVertex>(
                scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                metamodelGraph = metamodelGraph,
                currentVertex = currentVertex
            ),
            ParameterLeafVertexSDLDefinitionCreationContext {}
    }

    override fun createInitialContextForRootSchematicVertexSDLDefinition(
        metamodelGraph: MetamodelGraph
    ): SchematicVertexSDLDefinitionCreationContext<SourceRootVertex> {
        logger.debug(
            """create_initial_context_for_root_schematic_vertex_sdl_definition: 
               |[ metamodel_graph.vertices_by_path.size: 
               |${metamodelGraph.verticesByPath.size} ]
               |""".flattenIntoOneLine()
        )
        return when (val rootVertex: SchematicVertex? =
                metamodelGraph.verticesByPath[SchematicPath.getRootPath()]
        ) {
            is SourceRootVertex -> {
                DefaultSourceRootVertexSDLDefinitionCreationContext(
                    metamodelGraph = metamodelGraph,
                    currentVertex = rootVertex
                )
            }
            else -> {
                throw SchemaException(
                    SchemaErrorResponse.SCHEMATIC_INTEGRITY_VIOLATION,
                    "root_vertex missing in metamodel_graph"
                )
            }
        }
    }
}
