package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession

/**
 *
 * @author smccarron
 * @created 2022-09-13
 */
fun interface MaterializedTrackableValuePublishingService {

    fun publishMaterializedTrackableJsonValueIfApplicable(
        session: SingleRequestFieldMaterializationSession,
        materializedTrackableJsonValue: TrackableValue<JsonNode>,
        materializedValue: Any
    )
}
