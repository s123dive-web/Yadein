package com.yaadein.core.ui.util

import androidx.compose.runtime.Stable
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * Minimal read-only view over a paged list so screens can be driven by Paging in the app and by a
 * plain list in previews, screenshot tests and UI tests.
 */
@Stable
interface PagedItems<T : Any> {
    val itemCount: Int
    operator fun get(index: Int): T?
    fun peek(index: Int): T?
    val isRefreshing: Boolean
    val isAppending: Boolean
}

fun <T : Any> LazyPagingItems<T>.asPagedItems(): PagedItems<T> = object : PagedItems<T> {
    override val itemCount: Int get() = this@asPagedItems.itemCount
    override fun get(index: Int): T? = this@asPagedItems[index]
    override fun peek(index: Int): T? = this@asPagedItems.peek(index)
    override val isRefreshing: Boolean get() = this@asPagedItems.loadState.refresh is LoadState.Loading
    override val isAppending: Boolean get() = this@asPagedItems.loadState.append is LoadState.Loading
}

fun <T : Any> List<T>.asPagedItems(): PagedItems<T> = object : PagedItems<T> {
    override val itemCount: Int get() = size
    override fun get(index: Int): T? = getOrNull(index)
    override fun peek(index: Int): T? = getOrNull(index)
    override val isRefreshing: Boolean get() = false
    override val isAppending: Boolean get() = false
}

/** Stable key for a paged row: the item's own key when loaded, else a placeholder keyed by index. */
fun <T : Any> PagedItems<T>.keyAt(index: Int, keyOf: (T) -> Any): Any = peek(index)?.let(keyOf) ?: "placeholder-$index"
