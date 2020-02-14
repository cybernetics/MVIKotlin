package com.arkivanov.mvikotlin.extensions.coroutines

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.utils.assertOnMainThread
import com.arkivanov.mvikotlin.core.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

fun bind(mainContext: CoroutineContext = Dispatchers.Main, builder: BindingsBuilder.() -> Unit): Binder =
    BuilderBinder(mainContext = mainContext)
        .also(builder)

interface BindingsBuilder {
    infix fun <T> Flow<T>.bindTo(consumer: suspend (T) -> Unit)

    infix fun <T> Flow<T>.bindTo(view: View<T, *>)

    infix fun <T : Any> Flow<T>.bindTo(store: Store<T, *, *>)
}

interface Binder {
    fun start()

    fun stop()
}

private class BuilderBinder(
    private val mainContext: CoroutineContext
) : BindingsBuilder, Binder {
    private val bindings = ArrayList<Binding<*>>()
    private var job: Job? = null

    override fun <T> Flow<T>.bindTo(consumer: suspend (T) -> Unit) {
        bindings += Binding(this, consumer)
    }

    override fun <T> Flow<T>.bindTo(view: View<T, *>) {
        this bindTo {
            assertOnMainThread()
            view.render(it)
        }
    }

    override fun <T : Any> Flow<T>.bindTo(store: Store<T, *, *>) {
        this bindTo { store.accept(it) }
    }

    override fun start() {
        job =
            GlobalScope.launch(mainContext) {
                bindings.forEach { binding ->
                    launch { start(binding) }
                }
            }
    }

    private suspend fun <T> start(binding: Binding<T>) {
        binding.source.collect {
            binding.consumer(it)
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}

private class Binding<T>(
    val source: Flow<T>,
    val consumer: suspend (T) -> Unit
)