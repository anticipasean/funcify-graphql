package funcify.naming.charseq.template


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceOperationContextTemplate<CTX> {

    fun filterLeadingCharacters(context: CTX,
                                filter: (Char) -> Boolean): CTX

    fun filterTrailingCharacters(context: CTX,
                                 filter: (Char) -> Boolean): CTX

    fun filterAnyCharacters(context: CTX,
                            filter: (Char) -> Boolean): CTX

    fun mapCharacters(context: CTX,
                      mapper: (Char) -> Char): CTX

    fun mapCharactersWithIndex(context: CTX,
                               mapper: (Int, Char) -> Char): CTX

    fun groupCharactersByDelimiter(context: CTX,
                                   delimiter: Char): CTX

    fun filterLeadingCharacterSequence(context: CTX,
                                       filter: (CharSequence) -> Boolean): CTX

    fun filterTrailingCharacterSequence(context: CTX,
                                        filter: (CharSequence) -> Boolean): CTX

    fun filterCharacterSequence(context: CTX,
                                filter: (CharSequence) -> Boolean): CTX

    fun mapCharacterSequence(context: CTX,
                             mapper: (CharSequence) -> CharSequence): CTX

    fun mapCharacterSequenceWithIndex(context: CTX,
                                      mapper: (Int, CharSequence) -> CharSequence): CTX

}