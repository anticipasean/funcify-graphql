package funcify.feature.datasource.retrieval

/**
 *
 * @author smccarron
 * @created 2022-09-05
 */
interface TrackableValueFactory {

    fun <V> builder(): TrackableValue.PlannedValue.Builder<V>

}
