package funcify.feature.schema.directive.alias

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * Provide alternate names for the source indices referenced by specific paths in the
 * [SourceMetamodel]
 *
 * @author smccarron
 * @created 2022-07-21
 */
fun interface DataSourceAttributeAliasProvider {

    fun provideAnyAliasesForAttributePathsInDataSource(
        dataElementSource: DataElementSource
    ): Mono<ImmutableMap<GQLOperationPath, ImmutableSet<String>>>
}
