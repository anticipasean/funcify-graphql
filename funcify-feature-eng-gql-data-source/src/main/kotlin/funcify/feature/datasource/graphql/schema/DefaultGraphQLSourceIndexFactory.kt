package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.Some
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.or
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.GraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLObjectType
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class DefaultGraphQLSourceIndexFactory : GraphQLSourceIndexFactory {

    override fun createRootSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): GraphQLSourceIndexFactory.RootSourceContainerTypeSpec {
        return DefaultRootContainerTypeSpec(key)
    }

    override fun createSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): GraphQLSourceIndexFactory.AttributeBase {
        return DefaultAttributeBase(key)
    }

    override fun updateSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): GraphQLSourceIndexFactory.SourceContainerTypeUpdateSpec {
        return DefaultSourceContainerTypeUpdateSpec(graphQLSourceContainerType)
    }

    override fun createSourceAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): GraphQLSourceIndexFactory.ParentDefinitionBase {
        return DefaultParentDefinitionBase(key)
    }
    class DefaultRootContainerTypeSpec(private val key: DataSource.Key<GraphQLSourceIndex>) :
        GraphQLSourceIndexFactory.RootSourceContainerTypeSpec {

        override fun forGraphQLQueryObjectType(
            queryObjectType: GraphQLObjectType,
            metadataFilter: GraphQLApiSourceMetadataFilter
        ): GraphQLSourceContainerType {
            return createRootSourceContainerType(
                queryObjectType,
                queryObjectType.fieldDefinitions
                    .filter { gfd: GraphQLFieldDefinition ->
                        metadataFilter.includeGraphQLFieldDefinition(gfd)
                    }
                    .fold(persistentSetOf()) { ps, fd ->
                        ps.add(createRootAttributeForFieldDefinition(fd))
                    }
            )
        }
        private fun createRootSourceContainerType(
            queryObjectType: GraphQLObjectType,
            graphQLSourceAttributes: PersistentSet<GraphQLSourceAttribute>
        ): DefaultGraphQLSourceContainerType {
            return DefaultGraphQLSourceContainerType(
                dataSourceLookupKey = key,
                name = StandardNamingConventions.SNAKE_CASE.deriveName("query"),
                sourcePath = SchematicPath.getRootPath(),
                dataType = queryObjectType,
                sourceAttributes = graphQLSourceAttributes
            )
        }
        private fun createRootAttributeForFieldDefinition(
            fieldDefinition: GraphQLFieldDefinition
        ): GraphQLSourceAttribute {
            val convPathName =
                GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(fieldDefinition)
            val convFieldName =
                GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(fieldDefinition)
            val sourcePath: SchematicPath =
                SchematicPath.getRootPath().transform { pathSegment(convPathName.qualifiedForm) }

            return DefaultGraphQLSourceAttribute(
                dataSourceLookupKey = key,
                sourcePath = sourcePath,
                name = convFieldName,
                schemaFieldDefinition = fieldDefinition
            )
        }
    }

    class DefaultAttributeBase(private val key: DataSource.Key<GraphQLSourceIndex>) :
        GraphQLSourceIndexFactory.AttributeBase {

        override fun forAttributePathAndDefinition(
            attributePath: SchematicPath,
            attributeDefinition: GraphQLFieldDefinition
        ): Option<GraphQLSourceContainerType> {
            when (GraphQLFieldsContainerTypeExtractor.invoke(attributeDefinition.type)) {
                is Some -> {
                    return DefaultGraphQLSourceContainerType(
                            dataSourceLookupKey = key,
                            sourcePath = attributePath,
                            name =
                                GraphQLSourceNamingConventions
                                    .getFieldNamingConventionForGraphQLFieldDefinitions()
                                    .deriveName(attributeDefinition),
                            dataType = attributeDefinition.type
                        )
                        .some()
                }
                else -> {
                    return none<DefaultGraphQLSourceContainerType>()
                }
            }
        }
    }

    class DefaultParentDefinitionBase(private val key: DataSource.Key<GraphQLSourceIndex>) :
        GraphQLSourceIndexFactory.ParentDefinitionBase {

        override fun withParentPathAndDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): GraphQLSourceIndexFactory.ChildAttributeSpec {
            return DefaultChildAttributeSpec(
                dataSourceLookupKey = key,
                parentPath = parentPath,
                parentDefinition = parentDefinition
            )
        }
    }

    class DefaultChildAttributeSpec(
        private val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
        private val parentPath: SchematicPath,
        private val parentDefinition: GraphQLFieldDefinition,
    ) : GraphQLSourceIndexFactory.ChildAttributeSpec {

        override fun forChildAttributeDefinition(
            childDefinition: GraphQLFieldDefinition
        ): GraphQLSourceAttribute {
            if (parentPath.pathSegments.isEmpty() ||
                    parentPath
                        .pathSegments
                        .lastOrNone()
                        .filter { s ->
                            s !=
                                GraphQLSourceNamingConventions
                                    .getPathNamingConventionForGraphQLFieldDefinitions()
                                    .deriveName(parentDefinition)
                                    .qualifiedForm
                        }
                        .isDefined()
            ) {
                val message =
                    """
                    |parent_path [ path: ${parentPath.toURI()} ] does not match expected requirements 
                    |for parent_definition input [ qualified_name: ${
                    GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                            .deriveName(parentDefinition).qualifiedForm
                }
                    |""".flattenIntoOneLine()
                throw IllegalArgumentException(message)
            }
            if (parentDefinition
                    .type
                    .toOption()
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .or(
                        parentDefinition
                            .type
                            .toOption()
                            .filterIsInstance<GraphQLList>()
                            .map { gqll: GraphQLList -> gqll.wrappedType }
                            .filterIsInstance<GraphQLFieldsContainer>()
                    )
                    .filter { gqlObjType -> !gqlObjType.fieldDefinitions.contains(childDefinition) }
                    .isDefined()
            ) {
                val message =
                    """
                    |child_definition [ name: ${childDefinition.name} ] is not found in child_field_definitions 
                    |of parent_definition object_type or wrapped object_type [ name: ${
                    parentDefinition.type.toOption()
                            .filterIsInstance<GraphQLNamedOutputType>()
                            .map(GraphQLNamedOutputType::getName)
                            .getOrElse { "<NA>" }
                } ]
                    """.flattenIntoOneLine()
                throw GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
            }
            val childConvPathName =
                GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(childDefinition)
            val childConvFieldName =
                GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(childDefinition)
            val childPath = parentPath.transform { pathSegment(childConvPathName.qualifiedForm) }
            return DefaultGraphQLSourceAttribute(
                dataSourceLookupKey = dataSourceLookupKey,
                sourcePath = childPath,
                name = childConvFieldName,
                schemaFieldDefinition = childDefinition
            )
        }
    }

    class DefaultSourceContainerTypeUpdateSpec(
        private val graphQLSourceContainerType: GraphQLSourceContainerType
    ) : GraphQLSourceIndexFactory.SourceContainerTypeUpdateSpec {
        override fun withChildSourceAttributes(
            graphQLSourceAttributes: ImmutableSet<GraphQLSourceAttribute>
        ): GraphQLSourceContainerType {
            val validatedAttributeSet =
                graphQLSourceAttributes
                    .stream()
                    .reduce(
                        persistentSetOf<GraphQLSourceAttribute>(),
                        { ps, gsa ->
                            if (gsa.sourcePath
                                    .getParentPath()
                                    .filter { sp -> sp != graphQLSourceContainerType.sourcePath }
                                    .isDefined()
                            ) {
                                throw GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """source path for attribute [ source_path: ${gsa.sourcePath} ] 
                                    |is not child path of source_container_type 
                                    |source_path [ source_path: ${graphQLSourceContainerType.sourcePath} ]
                                    |""".flattenIntoOneLine()
                                )
                            } else {
                                ps.add(gsa)
                            }
                        },
                        { ps1, ps2 -> ps1.addAll(ps2) }
                    )
            return (graphQLSourceContainerType as? DefaultGraphQLSourceContainerType)?.copy(
                sourceAttributes = validatedAttributeSet
            )
                ?: DefaultGraphQLSourceContainerType(
                    dataSourceLookupKey = graphQLSourceContainerType.dataSourceLookupKey,
                    sourcePath = graphQLSourceContainerType.sourcePath,
                    dataType = graphQLSourceContainerType.dataType,
                    name = graphQLSourceContainerType.name,
                    sourceAttributes = validatedAttributeSet
                )
        }
    }
}
