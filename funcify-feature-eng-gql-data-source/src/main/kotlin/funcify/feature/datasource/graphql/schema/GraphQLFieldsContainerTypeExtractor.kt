package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType

/**
 * Recursive function that determines whether unwrapped type is a [GraphQLFieldsContainer] type
 * since an SDL type definition like:
 * ```
 * List[MyObjectType!]!
 * ```
 * would be rendered into GraphQLType instances like:
 * ```
 * GraphQLNonNull(wrappedType = GraphQLList(wrappedType = GraphQLNonNull(wrappedType =
 * MyObjectType())))
 * ```
 */
object GraphQLFieldsContainerTypeExtractor : (GraphQLType) -> Option<GraphQLFieldsContainer> {

    override fun invoke(graphQLType: GraphQLType): Option<GraphQLFieldsContainer> {
        return when (graphQLType) {
            is GraphQLFieldsContainer -> {
                graphQLType.some()
            }
            is GraphQLList -> {
                invoke(graphQLType.wrappedType)
            }
            is GraphQLNonNull -> {
                invoke(graphQLType.wrappedType)
            }
            else -> {
                none<GraphQLFieldsContainer>()
            }
        }
    }
}
