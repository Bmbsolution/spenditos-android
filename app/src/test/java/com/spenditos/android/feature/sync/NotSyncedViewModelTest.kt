package com.spenditos.android.feature.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.FailureCode
import com.spenditos.android.core.db.MutationStatus
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.transactions.NewTransaction
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class NotSyncedViewModelTest {
    private val server = MockWebServer()
    private val online = MutableStateFlow(false)
    private lateinit var db: SpenditosDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var viewModel: NotSyncedViewModel

    private val draft = NewTransaction(9.0, "cat-food", "Coffee", LocalDate.of(2026, 10, 6))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpenditosDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(TransactionsApi::class.java)
        val session = SessionManager(FakeTokenStore.signedIn("user-1"), { RefreshedTokens("a", "b") }, EpochClock { NOW }, AppEnvironment())
        repo = TransactionRepository(
            api, db, session,
            object : ConnectivityStatus { override val isOnline = online },
            SyncRequester {},
            FinanceInvalidationBus(),
        )
        viewModel = NotSyncedViewModel(repo, FakeSettings())
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun queue() = db.mutations().observeAll("user-1").first()

    @Test
    fun `discard of a create being sent is refused with a calm notice and keeps it`() = runTest {
        repo.create(draft)
        val row = queue().single()
        db.mutations().claim(row.id, System.currentTimeMillis())

        viewModel.discard(row.id)
        val state = viewModel.state.first { it.notice != null }
        assertEquals("This change is being sent right now, so it can't be discarded yet. Try again in a moment.", state.notice)
        assertEquals(MutationStatus.IN_FLIGHT, queue().single().status)
        assertEquals(1, state.items.size)

        viewModel.dismissNotice()
        // The combine that clears the notice is asynchronous; wait for the cleared value.
        val cleared = viewModel.state.first { it.notice == null && it.items.isNotEmpty() }
        assertNull(cleared.notice)
    }

    @Test
    fun `pending and failed entries discard normally without a notice`() = runTest {
        repo.create(draft)
        repo.create(draft.copy(description = "Second"))
        val (first, second) = queue().sortedBy { it.createdAt }.let { it[0] to it[1] }

        viewModel.discard(first.id)
        assertNull(viewModel.state.first { it.items.size == 1 }.notice)

        db.mutations().release(second.id, MutationStatus.FAILED, FailureCode.VALIDATION, "rejected")
        viewModel.discard(second.id)
        assertTrue(viewModel.state.first { it.items.isEmpty() }.items.isEmpty())
        assertNull(viewModel.state.first { it.items.isEmpty() }.notice)
        assertTrue(queue().isEmpty())
    }

    private class FakeSettings : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("USD")
        override suspend fun refresh(): UserSettings = error("not used")
        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings = error("not used")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = error("not used")
    }
}
