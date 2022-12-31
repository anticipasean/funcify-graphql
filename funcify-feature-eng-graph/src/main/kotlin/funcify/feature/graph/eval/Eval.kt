package funcify.feature.graph.eval

import kotlin.reflect.jvm.isAccessible

/**
 *
 * @author smccarron
 * @created 2022-12-31
 */
sealed interface Eval<out V> {

    companion object {

        private data class Done<out V>(val result: V) : Eval<V> {}

        private data class ComputedAlways<out V>(val computation: () -> V) : Eval<V> {}

        private data class ComputedOnce<out V>(val computation: () -> V) : Eval<V> {
            val computedValue: V by lazy(computation)

            fun hasBeenComputed(): Boolean {
                val isComputedValueFieldAccessible: Boolean = this::computedValue.isAccessible
                val hasBeenComputed: Boolean =
                    try {
                        when (
                            val computedValueDelegate: Any? =
                                this::computedValue.apply { isAccessible = true }.getDelegate()
                        ) {
                            is Lazy<*> -> {
                                computedValueDelegate.isInitialized()
                            }
                            else -> {
                                false
                            }
                        }
                    } catch (e: Exception) {
                        false
                    }
                if (!isComputedValueFieldAccessible) {
                    this::computedValue.isAccessible = false
                }
                return hasBeenComputed
            }
        }

        private data class Nested<out V>(val computation: () -> Eval<V>) : Eval<V> {}

        fun <V> done(result: V): Eval<V> {
            return Done<V>(result)
        }

        fun <V> computeAlways(computation: () -> V): Eval<V> {
            return ComputedAlways<V>(computation)
        }

        fun <V> computeOnce(computation: () -> V): Eval<V> {
            return ComputedOnce<V>(computation)
        }

        fun <V> defer(computation: () -> Eval<V>): Eval<V> {
            return Nested<V>(computation)
        }

        private fun <V> unnestEvalContainer(): (Eval<V>) -> Either<Eval<V>, V> {
            return { e: Eval<V> ->
                when (e) {
                    is Done -> {
                        Either.right(e.result)
                    }
                    is ComputedAlways -> {
                        Either.right(e.computation())
                    }
                    is ComputedOnce -> {
                        Either.right(e.computedValue)
                    }
                    is Nested -> {
                        Either.left(e.computation())
                    }
                }
            }
        }
    }

    fun isDone(): Boolean {
        return when (this) {
            is ComputedAlways -> {
                false
            }
            is ComputedOnce -> {
                this.hasBeenComputed()
            }
            is Done -> {
                true
            }
            is Nested -> {
                false
            }
        }
    }

    fun get(): V {
        return when (this) {
            is Done -> {
                this.result
            }
            is ComputedAlways -> {
                this.computation()
            }
            is ComputedOnce -> {
                this.computedValue
            }
            is Nested -> {
                Either.recurse(this.computation(), unnestEvalContainer())
            }
        }
    }

    fun <R> map(mapper: (V) -> R): Eval<R> {
        return when (this) {
            is Done -> {
                ComputedOnce { mapper(this.result) }
            }
            is ComputedAlways -> {
                ComputedAlways { mapper(this.computation()) }
            }
            is ComputedOnce -> {
                ComputedOnce { mapper(this.computedValue) }
            }
            is Nested -> {
                Nested { this.computation().map(mapper) }
            }
        }
    }

    fun <R> flatMap(mapper: (V) -> Eval<R>): Eval<R> {
        return when (this) {
            is Done -> {
                Nested<R> { mapper(this.result) }
            }
            is ComputedAlways -> {
                Nested<R> { mapper(computation()) }
            }
            is ComputedOnce -> {
                Nested<R> { mapper(this.computedValue) }
            }
            is Nested -> {
                Nested<R> { mapper(Either.recurse(this.computation(), unnestEvalContainer())) }
            }
        }
    }
}
