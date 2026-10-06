package com.spenditos.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives [content] (a sheet or dialog shown outside the NavHost) its own ViewModelStore,
 * cleared when [content] leaves composition. Without it, `hiltViewModel()` there is
 * Activity-scoped: the form would reopen with stale input and outlive a sign-out.
 */
@Composable
fun ScopedViewModels(content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" }
    val owner = remember(parent) { ScopedOwner(parent) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class ScopedOwner(parent: ViewModelStoreOwner) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    private val parentFactory = parent as HasDefaultViewModelProviderFactory
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = parentFactory.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras(parentFactory.defaultViewModelCreationExtras).apply { set(VIEW_MODEL_STORE_OWNER_KEY, this@ScopedOwner) }
}
