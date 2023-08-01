package funcify.feature.datasource.rest.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-29
 */
fun interface SwaggerRestApiJsonResponsePostProcessingStrategy {

    fun postProcessRestApiJsonResponse(
        context: SwaggerRestApiJsonResponsePostProcessingContext,
        responseJsonNode: JsonNode
    ): Mono<ImmutableMap<GQLOperationPath, JsonNode>>
}
