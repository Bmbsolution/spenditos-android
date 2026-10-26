package com.spenditos.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spenditos.android.feature.home.HomeScreen
import com.spenditos.android.feature.receipt.ReceiptScanScreen
import com.spenditos.android.feature.plan.PlanHubScreen
import com.spenditos.android.feature.reports.DailyAggregatesScreen
import com.spenditos.android.feature.reports.MonthComparisonScreen
import com.spenditos.android.feature.reports.PaycheckPeriodsScreen
import com.spenditos.android.feature.reports.ReportsHubScreen
import com.spenditos.android.feature.capture.CaptureScreen
import com.spenditos.android.feature.progress.ProgressScreen
import com.spenditos.android.feature.settings.ChangePasswordScreen
import com.spenditos.android.feature.settings.ProfileScreen
import com.spenditos.android.feature.settings.SettingsScreen
import com.spenditos.android.feature.settings.categories.CategoriesManagementScreen
import com.spenditos.android.feature.sync.NotSyncedScreen
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.feature.transactions.AddTransactionSheet
import com.spenditos.android.feature.transactions.TransactionListScreen
import com.spenditos.android.ui.components.EnvBadge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.spenditos.android.ui.components.TabItem
import com.spenditos.android.ui.components.GradientFab
import com.spenditos.android.ui.components.FloatingTabBar
import com.spenditos.android.ui.components.FloatingTabBarHeight
import com.spenditos.android.ui.components.tabBarBottomPadding
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.ScopedViewModels
import kotlinx.coroutines.launch

/** iOS MainTabView symbols: outline when idle, filled when selected. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon) {
    HOME("home", "Home", SfSymbols.icon("house"), SfSymbols.icon("house.fill")),
    TRANSACTIONS("transactions", "Transactions", SfSymbols.icon("list.bullet"), SfSymbols.icon("list.bullet.rectangle.fill")),
    PLAN("plan", "Plan", SfSymbols.icon("calendar"), SfSymbols.icon("calendar.badge.checkmark")),
    REPORTS("reports", "Reports", SfSymbols.icon("chart.line.uptrend.xyaxis")),
    SETTINGS("settings", "Settings", SfSymbols.icon("gearshape"), SfSymbols.icon("gearshape.fill")),
}

private const val NOT_SYNCED_ROUTE = "not-synced"
private const val RECEIPT_SCAN_ROUTE = "receipt-scan"
private const val SCAN_REVIEW_ROUTE = "scan-review/{scanId}"
private fun scanReviewRoute(scanId: String) = "scan-review/${android.net.Uri.encode(scanId)}"
private const val CAPTURE_ROUTE = "capture?mode={mode}"
private fun captureRoute(mode: String) = "capture?mode=$mode"
private const val REPORTS_DAILY_ROUTE = "reports/daily"
private const val REPORTS_COMPARISON_ROUTE = "reports/comparison"
private const val REPORTS_PAYCHECK_ROUTE = "reports/paycheck-periods"
private const val PROFILE_ROUTE = "settings/profile"
private const val CATEGORIES_ROUTE = "settings/categories"
private const val CHANGE_PASSWORD_ROUTE = "settings/change-password"
private const val PROGRESS_ROUTE = "settings/progress"
private const val DOWNLOAD_DATA_ROUTE = "settings/download-data"
private const val MERCHANTS_ROUTE = "settings/merchant-names"
private const val PRO_ROUTE = "settings/pro"
private const val DEBT_ROUTE = "plan/debts/{debtId}"
private const val ENDED_BILLS_ROUTE = "plan/ended"
private fun debtRoute(debtId: String) = "plan/debts/${android.net.Uri.encode(debtId)}"

/**
 * Pro opened from the add sheet is shown over the sheet instead of closing it, so the form and its
 * prefill survive the paywall (iOS QuickAddView). It only exists while the sheet is open.
 */
internal fun proOverAddVisible(addSheetOpen: Boolean, proRequested: Boolean): Boolean = addSheetOpen && proRequested

/**
 * iOS MainTabView: five tabs; the FAB is the one global "add" entry point.
 * [linksEnabled] is false while the app lock is up: pending widget links wait, and the add sheet
 * (a ModalBottomSheet in its own window, which would render above the lock screen) is not shown.
 */
@Composable
fun MainScaffold(
    envName: String?,
    deepLinks: com.spenditos.android.data.push.DeepLinkHandler,
    proAvailable: Boolean = false,
    linksEnabled: Boolean = true,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // iOS presents the paywall as a sheet over the add form; the form (and its prefill) stays underneath.
    var showProOverAdd by rememberSaveable { mutableStateOf(false) }
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (link) {
            com.spenditos.android.data.push.DeepLink.ADD_TRANSACTION -> showAdd = true
            com.spenditos.android.data.push.DeepLink.PROGRESS -> nav.navigate(PROGRESS_ROUTE)
        }
        deepLinks.consume(link)
    }
    // Widget pills deep-link like iOS (spenditos://add-expense, spenditos://scan-receipt).
    // While the lock is up the link stays pending and only acts once unlocked.
    val widgetLink by WidgetDeepLinks.pending.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(widgetLink, linksEnabled) {
        if (widgetLink == null || !linksEnabled) return@LaunchedEffect
        when (widgetLink) {
            WidgetDeepLinks.ADD_EXPENSE -> showAdd = true
            WidgetDeepLinks.SCAN_RECEIPT -> nav.navigate(RECEIPT_SCAN_ROUTE) { launchSingleTop = true }
        }
        WidgetDeepLinks.consume()
    }
    androidx.compose.runtime.LaunchedEffect(showAdd) { if (!showAdd) showProOverAdd = false }
    // Values read from a receipt; the add form opens with them and the user reviews before saving.
    var receiptPrefill by remember { mutableStateOf<AddTransactionPrefill?>(null) }
    var editTarget by remember { mutableStateOf<com.spenditos.android.feature.transactions.EditTarget?>(null) }
    var transactionsEditing by remember { mutableStateOf(false) }
    // Home's Bills left: the Transactions tab applies it once, then clears it.
    var upcomingRequest by remember { mutableStateOf<com.spenditos.android.feature.transactions.UpcomingExpensesRequest?>(null) }
    val onTab = backStack?.destination?.route in Tab.entries.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.spenditos.android.feature.pro.LocalOpenPro provides (if (proAvailable) ({ nav.navigate(PRO_ROUTE) }) else null),
            ) {
            NavHost(nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) {
                    val toPlan = {
                        nav.navigate(Tab.PLAN.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    HomeScreen(
                        onReviewPlan = toPlan,
                        onAddIncome = toPlan,
                        onLogFirst = { showAdd = true },
                        onSeeAll = {
                            nav.navigate(Tab.TRANSACTIONS.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenScanReview = { scanId -> nav.navigate(scanReviewRoute(scanId)) },
                        onOpenScanReceipt = { values ->
                            receiptPrefill = values
                            editTarget = null
                            showAdd = true
                        },
                        onScanMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                        onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                        onOpenBillsLeft = { start, end ->
                            upcomingRequest = com.spenditos.android.feature.transactions.UpcomingExpensesRequest(start, end)
                            nav.navigate(Tab.TRANSACTIONS.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenTransaction = { target ->
                            receiptPrefill = null
                            editTarget = target
                            showAdd = true
                        },
                    )
                }
                composable(Tab.TRANSACTIONS.route) {
                    TransactionListScreen(
                        onOpenNotSynced = { nav.navigate(NOT_SYNCED_ROUTE) },
                        request = upcomingRequest,
                        onRequestHandled = { upcomingRequest = null },
                        onAdd = { showAdd = true },
                        onEdit = { target ->
                            receiptPrefill = null
                            editTarget = target
                            showAdd = true
                        },
                        onEditModeChange = { transactionsEditing = it },
                        onDuplicate = { copy ->
                            editTarget = null
                            receiptPrefill = copy
                            showAdd = true
                        },
                    )
                }
                composable(Tab.PLAN.route) {
                    PlanHubScreen(
                        onOpenDebt = { nav.navigate(debtRoute(it)) },
                        onOpenEnded = { nav.navigate(ENDED_BILLS_ROUTE) },
                    )
                }
                composable(ENDED_BILLS_ROUTE) { com.spenditos.android.feature.recurring.EndedBillsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    DEBT_ROUTE,
                    arguments = listOf(androidx.navigation.navArgument("debtId") { type = androidx.navigation.NavType.StringType }),
                ) { com.spenditos.android.feature.debts.DebtDetailScreen(onBack = { nav.popBackStack() }) }
                composable(
                    CAPTURE_ROUTE,
                    arguments = listOf(androidx.navigation.navArgument("mode") { type = androidx.navigation.NavType.StringType; defaultValue = "type" }),
                ) {
                    CaptureScreen(
                        onBack = { nav.popBackStack() },
                        onFillManually = { prefill ->
                            nav.popBackStack()
                            receiptPrefill = prefill
                            editTarget = null
                            showAdd = true
                        },
                        onSaved = { message ->
                            nav.popBackStack()
                            scope.launch { snackbar.showSnackbar(message) }
                        },
                    )
                }
                composable(RECEIPT_SCAN_ROUTE) {
                    ReceiptScanScreen(
                        onResult = { values ->
                            receiptPrefill = values
                            editTarget = null
                            nav.popBackStack()
                            showAdd = true
                        },
                        onOpenReview = { scanId ->
                            // The scan screen is done: the review replaces it, so Back from the review returns to where Scan was opened.
                            nav.navigate(scanReviewRoute(scanId)) { popUpTo(RECEIPT_SCAN_ROUTE) { inclusive = true } }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    SCAN_REVIEW_ROUTE,
                    arguments = listOf(androidx.navigation.navArgument("scanId") { type = androidx.navigation.NavType.StringType }),
                ) {
                    com.spenditos.android.feature.scan.ScanReviewScreen(
                        onClose = { nav.popBackStack() },
                        onScanMore = {
                            nav.navigate(RECEIPT_SCAN_ROUTE) { popUpTo(SCAN_REVIEW_ROUTE) { inclusive = true } }
                        },
                    )
                }
                composable(Tab.REPORTS.route) {
                    ReportsHubScreen(
                        onOpenDaily = { nav.navigate(REPORTS_DAILY_ROUTE) },
                        onOpenComparison = { nav.navigate(REPORTS_COMPARISON_ROUTE) },
                        onOpenPaycheckPeriods = { nav.navigate(REPORTS_PAYCHECK_ROUTE) },
                        onReviewTransactions = {
                            nav.navigate(Tab.TRANSACTIONS.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onLogTransaction = { showAdd = true },
                    )
                }
                composable(REPORTS_DAILY_ROUTE) { DailyAggregatesScreen(onBack = { nav.popBackStack() }) }
                composable(REPORTS_COMPARISON_ROUTE) { MonthComparisonScreen(onBack = { nav.popBackStack() }) }
                composable(REPORTS_PAYCHECK_ROUTE) { PaycheckPeriodsScreen(onBack = { nav.popBackStack() }) }
                composable(Tab.SETTINGS.route) {
                    SettingsScreen(
                        onOpenNotSynced = { nav.navigate(NOT_SYNCED_ROUTE) },
                        onOpenProfile = { nav.navigate(PROFILE_ROUTE) },
                        onOpenCategories = { nav.navigate(CATEGORIES_ROUTE) },
                        onOpenChangePassword = { nav.navigate(CHANGE_PASSWORD_ROUTE) },
                        onOpenProgress = { nav.navigate(PROGRESS_ROUTE) },
                        onOpenDownloadData = { nav.navigate(DOWNLOAD_DATA_ROUTE) },
                        onOpenMerchants = { nav.navigate(MERCHANTS_ROUTE) },
                        onOpenPro = { nav.navigate(PRO_ROUTE) },
                    )
                }
                composable(PROFILE_ROUTE) {
                    ProfileScreen(
                        onBack = { nav.popBackStack() },
                        onOpenChangePassword = { nav.navigate(CHANGE_PASSWORD_ROUTE) },
                    )
                }
                composable(CATEGORIES_ROUTE) { CategoriesManagementScreen(onBack = { nav.popBackStack() }) }
                composable(CHANGE_PASSWORD_ROUTE) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
                composable(PRO_ROUTE) { com.spenditos.android.feature.pro.ProScreen(onBack = { nav.popBackStack() }) }
                composable(MERCHANTS_ROUTE) { com.spenditos.android.feature.settings.rules.RulesScreen(onBack = { nav.popBackStack() }) }
                composable(DOWNLOAD_DATA_ROUTE) { com.spenditos.android.feature.export.DownloadDataScreen(onBack = { nav.popBackStack() }) }
                composable(PROGRESS_ROUTE) { ProgressScreen(onBack = { nav.popBackStack() }) }
                composable(NOT_SYNCED_ROUTE) { NotSyncedScreen(onBack = { nav.popBackStack() }) }
            }
            }
            val tabBarBottom = tabBarBottomPadding()
            if (onTab) {
                val currentRoute = Tab.entries.firstOrNull { tab -> backStack?.destination?.hierarchy?.any { it.route == tab.route } == true }?.route
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = tabBarBottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // iOS: Home and Transactions own the add-transaction button; Plan has its own add menu.
                    // iOS hides the add button while Transactions is in Edit mode (its Delete bar sits there).
                    if (currentRoute == Tab.HOME.route || (currentRoute == Tab.TRANSACTIONS.route && !transactionsEditing)) {
                        GradientFab(
                            onClick = { showAdd = true },
                            glyph = SfSymbols.icon("plus"),
                            contentDescription = "Add transaction",
                            modifier = Modifier.padding(end = 20.dp).testTag("fab.add"),
                        )
                    }
                    FloatingTabBar(
                        items = Tab.entries.map { TabItem(it.route, it.label, it.icon, it.selectedIcon) },
                        selectedRoute = currentRoute,
                        onSelect = { item ->
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
            envName?.let {
                val aboveBar = if (onTab) tabBarBottom + FloatingTabBarHeight + 8.dp else 12.dp
                EnvBadge(it, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = aboveBar))
            }
        }
    }

    if (showAdd) {
        ScopedViewModels {
            // The sheet lives in its own window and would render above the lock screen; keep
            // showAdd (and the scoped form) so it reappears once unlocked.
            if (linksEnabled) {
                AddTransactionSheet(
                    onDismiss = {
                        showAdd = false
                        receiptPrefill = null
                        editTarget = null
                    },
                    onDone = { message ->
                        showAdd = false
                        receiptPrefill = null
                        editTarget = null
                        scope.launch { snackbar.showSnackbar(message) }
                    },
                    onScanReceipt = {
                        showAdd = false
                        nav.navigate(RECEIPT_SCAN_ROUTE)
                    },
                    onVoice = {
                        showAdd = false
                        nav.navigate(captureRoute("speak"))
                    },
                    onSentence = {
                        showAdd = false
                        nav.navigate(captureRoute("type"))
                    },
                    onOpenPro = if (proAvailable) ({ showProOverAdd = true }) else null,
                    prefill = receiptPrefill,
                    editTarget = editTarget,
                )
                if (proOverAddVisible(showAdd, showProOverAdd)) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showProOverAdd = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                        ),
                    ) {
                        com.spenditos.android.feature.pro.ProScreen(onBack = { showProOverAdd = false })
                    }
                }
            }
        }
    }
}
