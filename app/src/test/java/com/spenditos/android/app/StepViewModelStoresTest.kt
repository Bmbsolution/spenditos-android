package com.spenditos.android.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StepViewModelStoresTest {
    class FormViewModel : ViewModel() {
        var typed = ""
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private fun viewModelIn(store: ViewModelStore): FormViewModel =
        ViewModelProvider.create(
            object : ViewModelStoreOwner { override val viewModelStore = store },
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = FormViewModel() as T
            },
        )[FormViewModel::class.java]

    @Test
    fun `re-entering the same step (rotation) keeps its ViewModel`() {
        val stores = StepViewModelStores()
        val first = viewModelIn(stores.enter("forgot")).apply { typed = "code" }
        val again = viewModelIn(stores.enter("forgot"))
        assertSame(first, again)
        assertFalse(first.cleared)
    }

    @Test
    fun `changing step clears the previous step so a finished flow starts fresh next time`() {
        val stores = StepViewModelStores()
        val forgot = viewModelIn(stores.enter("forgot")).apply { typed = "secret" }
        viewModelIn(stores.enter("login"))
        assertTrue(forgot.cleared)
        val forgotAgain = viewModelIn(stores.enter("forgot"))
        assertNotSame(forgot, forgotAgain)
        assertTrue(forgotAgain.typed.isEmpty())
    }

    @Test
    fun `signing in clears every signed-out step`() {
        val stores = StepViewModelStores()
        val login = viewModelIn(stores.enter("login"))
        stores.clearAll()
        assertTrue(login.cleared)
        assertNotSame(login, viewModelIn(stores.enter("login")))
    }
}
