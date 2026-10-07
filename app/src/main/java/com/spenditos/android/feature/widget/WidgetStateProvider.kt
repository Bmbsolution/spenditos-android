package com.spenditos.android.feature.widget

import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsActions
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Small ports keep the provider testable with in-memory repository fakes. */
interface WidgetTransactions {
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>
}

interface WidgetCategories {
    fun observe(): Flow<List<Category>>
}

@Module
@InstallIn(SingletonComponent::class)
object WidgetRepositoryBindings {
    @Provides
    @Singleton
    fun widgetTransactions(repository: TransactionRepository): WidgetTransactions = object : WidgetTransactions {
        override fun observeRange(start: LocalDate, end: LocalDate) = repository.observeRange(start, end)
    }

    @Provides
    @Singleton
    fun widgetCategories(repository: CategoryRepository): WidgetCategories = object : WidgetCategories {
        override fun observe() = repository.observe()
    }
}

/** Reads Home's current-month Room cache only; it never requests network data. */
@Singleton
class WidgetStateProvider @Inject constructor(
    private val transactions: WidgetTransactions,
    private val categories: WidgetCategories,
    private val settings: UserSettingsActions,
    private val session: SessionManager,
    private val clock: Clock,
    private val streak: com.spenditos.android.data.gamification.StreakCache? = null,
) {
    suspend fun snapshot(): WidgetSnapshot {
        val userId = session.currentUserId ?: return WidgetSnapshot.placeholder()
        val today = LocalDate.now(clock)
        val rows = transactions.observeRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            .firstOrNull().orEmpty()
        val recentRows = transactions.observeRange(today.minusDays(6), today).firstOrNull().orEmpty()
        val categoryRows = categories.observe().firstOrNull().orEmpty()
        val streakDays = runCatching { streak?.get() }.getOrNull() ?: 0
        val currency = settings.currency().first()

        // The repository flows are user-scoped when created. Recheck across the reads so a
        // sign-out or account switch during collection cannot publish a prior user's cache.
        if (session.currentUserId != userId) return WidgetSnapshot.placeholder(currency)

        val snapshot = WidgetSnapshot.from(
            transactions = rows.filter { it.userId == userId }.map { it.toModel() },
            categories = categoryRows.filter { it.userId == userId },
            today = today,
            currency = currency,
            userId = userId,
            recent = recentRows.filter { it.userId == userId }.map { it.toModel() },
            streakDays = streakDays,
        )
        return if (session.currentUserId == userId) snapshot else WidgetSnapshot.placeholder(currency)
    }

    private fun TransactionEntity.toModel() = Transaction(
        id = id,
        userId = userId,
        categoryId = categoryId,
        templateId = templateId,
        description = description,
        amount = amount,
        date = date,
    )
}
