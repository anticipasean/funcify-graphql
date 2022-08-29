package funcify.feature.datasource.rest.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-29
 */
fun interface SwaggerRestApiJsonResponsePostProcessingStrategy {

    fun postProcessRestApiJsonResponse(
        context: SwaggerRestApiJsonResponsePostProcessingContext,
        responseJsonNode: JsonNode
    ): Deferred<ImmutableMap<SchematicPath, JsonNode>>
}
