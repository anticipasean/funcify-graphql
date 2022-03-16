package funcify.naming

import funcify.naming.encoder.URICompatibleStringEncoder
import kotlinx.collections.immutable.ImmutableList
import java.util.stream.Collectors


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
interface ConventionalName {

    companion object {
        const val EMPTY_STRING_DELIMITER: String = ""
    }

    val namingConventionKey: Any

    val nameComponents: ImmutableList<NameComponent>

    val delimiter: String
        get() = EMPTY_STRING_DELIMITER

    /**
     * Display form of the name derived from interspersing the delimiter with name components
     * and their respective qualified string names
     * @implementation_note: This form should likely be lazily initialized and interned, and as such,
     * this default implementation likely should be overridden
     */
    val qualifiedForm: String
        get() = nameComponents.stream()
                .map { cn -> cn.value }
                .collect(Collectors.joining(delimiter))

    /**
     * Qualified form with any URI reserved characters encoded
     * @implementation_note: This form should likely be lazily initialized and interned, and as such,
     * this default implementation likely should be overridden
     */
    val uriForm: String
        get() = nameComponents.stream()
                .map { cn -> cn.value }
                .collect(Collectors.joining(delimiter))
                .let { s -> URICompatibleStringEncoder.invoke(s) }

    /**
     * Should override and make equivalent to calling the {@link #qualifiedForm} or {@link #uriForm}
     * but this method cannot be overridden at the interface level
     */
    override fun toString(): String

}