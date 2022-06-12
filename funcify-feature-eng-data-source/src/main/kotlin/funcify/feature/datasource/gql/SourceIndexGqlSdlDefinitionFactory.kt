package funcify.feature.datasource.gql

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try
import graphql.language.Node
import kotlin.reflect.KClass

/**
 * Note: Kotlin's generic type system doesn't currently enable the API to be created in such a way
 * that the compiler can verify that both SCT (SourceContainerType type parameters) and SA
 * (SourceAttribute type parameters) belong to the same SI (SourceIndex type parameter), only that
 * SA must be of the type SCT expects within its single type parameter, SA, on
 * SourceContainerType<SA>. Consequently, if the input sourceIndex type <SI> is not a supertype of
 * SCT then all casts of sourceIndices to SCT or SA will fail. Hopefully, some type parameter union
 * set logic will be added to Kotlin at some point making the compiler capable of asserting joint
 * membership of source container types and source attribute types to the same source index type
 */
interface SourceIndexGqlSdlDefinitionFactory<SI : SourceIndex> {

    companion object {

        fun defaultFactoryBuilder(): StepBuilder {
            return DefaultSourceIndexGqlSdlDefinitionFactory.builder()
        }
    }

    val sourceIndexType: KClass<SI>

    val dataSourceType: DataSourceType

    fun createGraphQLSDLNodeForSourceIndex(sourceIndex: SI): Try<Node<*>>

    interface StepBuilder {

        fun <SI : SourceIndex> sourceIndexType(sourceIndexType: KClass<SI>): DataSourceTypeStep<SI>
    }

    interface DataSourceTypeStep<SI : SourceIndex> {

        fun dataSourceType(dataSourceType: DataSourceType): SourceContainerMapperStep<SI>
    }

    interface SourceContainerMapperStep<SI : SourceIndex> {

        fun <SC : SourceContainerType<SA>, SA : SourceAttribute> sourceContainerTypeMapper(
            sourceContainerTypeMapper: SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC>
        ): SourceAttributeMapperStep<SI, SC, SA>
    }

    interface SourceAttributeMapperStep<
        SI : SourceIndex, SC : SourceContainerType<SA>, SA : SourceAttribute> {

        fun sourceAttributeMapper(
            sourceAttributeMapper: SourceAttributeGqlSdlFieldDefinitionMapper<SA>
        ): BuildStep<SI>
    }

    interface BuildStep<SI : SourceIndex> {

        fun build(): SourceIndexGqlSdlDefinitionFactory<SI>
    }
}
