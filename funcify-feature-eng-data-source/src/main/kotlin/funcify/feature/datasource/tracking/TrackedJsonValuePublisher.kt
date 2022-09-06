package funcify.feature.datasource.tracking

import com.fasterxml.jackson.databind.JsonNode

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
fun interface TrackedJsonValuePublisher {

    fun publishTrackedValue(trackableValue: TrackableValue<JsonNode>)

}
