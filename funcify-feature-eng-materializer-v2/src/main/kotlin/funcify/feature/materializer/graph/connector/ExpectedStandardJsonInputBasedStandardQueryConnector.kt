package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.ExpectedStandardJsonInputStandardQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedStandardJsonInputBasedStandardQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedStandardJsonInputStandardQuery> {

    private val logger: Logger = loggerFor<ExpectedStandardJsonInputBasedStandardQueryConnector>()

    override fun connectOperationDefinition(
        connectorContext: ExpectedStandardJsonInputStandardQuery
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }
}
