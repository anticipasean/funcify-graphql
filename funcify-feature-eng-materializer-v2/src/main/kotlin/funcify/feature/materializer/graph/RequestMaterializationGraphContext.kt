package funcify.feature.materializer.graph

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.path.GQLOperationPath
import graphql.language.Document
import graphql.language.Node
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface RequestMaterializationGraphContext {

    val materializationMetamodel: MaterializationMetamodel

    val variableKeys: ImmutableSet<String>

    val requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>

    interface RawInputProvidedStandardQuery :
        RequestMaterializationGraphContext, StandardQueryTarget, RawInputProvided {

        override val document: Document

        override val rawInputContextShape: RawInputContextShape
    }

    interface StandardQuery : RequestMaterializationGraphContext, StandardQueryTarget {

        override val document: Document
    }

    interface RawInputProvidedTabularQuery :
        RequestMaterializationGraphContext, RawInputProvided, TabularQueryTarget {

        override val rawInputContextShape: RawInputContextShape

        override val outputColumnNames: ImmutableSet<String>
    }

    interface TabularQuery : RequestMaterializationGraphContext, TabularQueryTarget {

        override val outputColumnNames: ImmutableSet<String>
    }
}
