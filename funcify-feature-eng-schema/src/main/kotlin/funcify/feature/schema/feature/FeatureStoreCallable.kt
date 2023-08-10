package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface FeatureStoreCallable :
    (TrackableValue.PlannedValue<JsonNode>) -> Mono<TrackableValue<JsonNode>> {

    val featureStoreName: String

    val featurePath: GQLOperationPath

    val argumentPaths: ImmutableSet<GQLOperationPath>
}
