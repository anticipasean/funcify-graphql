package funcify.feature.schema.dataelement

import funcify.feature.schema.Source
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

interface DataElementSource : Source {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>
}
