package com.spenditos.android.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import com.spenditos.android.feature.statements.StatementImportScreen
import com.spenditos.android.feature.settings.categories.CategoriesManagementScreen
import com.spenditos.android.feature.sync.NotSyncedScreen
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.feature.transactions.AddTransactionSheet
import com.spenditos.android.feature.transactions.TransactionListScreen
import com.spenditos.android.ui.components.EnvBadge
import com.spenditos.android.ui.components.ScopedViewModels
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.AutoMirrored.Filled.List),
    PLAN("plan", "Plan", Icons.Filled.CalendarMonth),
    REPORTS("reports", "Reports", Icons.AutoMirrored.Filled.ShowChart),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

private const val NOT_SYNCED_ROUTE = "not-synced"
private const val RECEIPT_SCAN_ROUTE = "receipt-scan"
private const val CAPTURE_ROUTE = "capture"
private const val REPORTS_DAILY_ROUTE = "reports/daily"
private const val REPORTS_COMPARISON_ROUTE = "reports/comparison"
private const val REPORTS_PAYCHECK_ROUTE = "reports/paycheck-periods"
private const val PROFILE_ROUTE = "settings/profile"
private const val CATEGORIES_ROUTE = "settings/categories"
private const val CHANGE_PASSWORD_ROUTE = "settings/change-password"
private const val STATEMENT_IMPORT_ROUTE = "settings/import-statement"
private const val PROGRESS_ROUTE = "settings/progress"
private const val EXPORT_ROUTE = "settings/export"
private const val MERCHANTS_ROUTE = "settings/merchant-names"

/** iOS MainTabView: five tabs; the FAB is the one global "add" entry point. */
@Composable
fun MainScaffold(envName: String?, deepLinks: com.spenditos.android.data.push.DeepLinkHandler) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (link) {
            com.spenditos.android.data.push.DeepLink.ADD_TRANSACTION -> showAdd = true
            com.spenditos.android.data.push.DeepLink.PROGRESS -> nav.navigate(PROGRESS_ROUTE)
        }
        deepLinks.consume(link)
    }
    // Values read from a receipt; the add form opens with them and the user reviews before saving.
    var receiptPrefill by remember { mutableStateOf<AddTransactionPrefill?>(null) }
    val onTab = backStack?.destination?.route in Tab.entries.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (onTab && backStack?.destination?.route != Tab.SETTINGS.route) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab.add"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                            selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            // Tab labels stay on one line, so cap their font scale (system bar pattern) instead of clipping them.
                            label = {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.0f))) {
                                    Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }
                            },
                            modifier = Modifier.testTag("tab.${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Tab.TRANSACTIONS.route) {
                composable(Tab.HOME.route) {
                    val toPlan = {
                        nav.navigate(Tab.PLAN.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    HomeScreen(onReviewPlan = toPlan, onAddIncome = toPlan)
                }
                composable(Tab.TRANSACTIONS.route) { TransactionListScreen(onOpenNotSynced = { nav.navigate(NOT_SYNCED_ROUTE) }) }
                composable(Tab.PLAN.route) { PlanHubScreen() }
                composable(CAPTURE_ROUTE) {
                    CaptureScreen(
                        onBack = { nav.popBackStack() },
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
                            nav.popBackStack()
                            showAdd = true
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Tab.REPORTS.route) {
                    ReportsHubScreen(
                        onOpenDaily = { nav.navigate(REPORTS_DAILY_ROUTE) },
                        onOpenComparison = { nav.navigate(REPORTS_COMPARISON_ROUTE) },
                        onOpenPaycheckPeriods = { nav.navigate(REPORTS_PAYCHECK_ROUTE) },
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
                        onOpenStatementImport = { nav.navigate(STATEMENT_IMPORT_ROUTE) },
                        onOpenProgress = { nav.navigate(PROGRESS_ROUTE) },
                        onOpenExport = { nav.navigate(EXPORT_ROUTE) },
                        onOpenMerchants = { nav.navigate(MERCHANTS_ROUTE) },
                    )
                }
                composable(PROFILE_ROUTE) { ProfileScreen(onBack = { nav.popBackStack() }) }
                composable(CATEGORIES_ROUTE) { CategoriesManagementScreen(onBack = { nav.popBackStack() }) }
                composable(CHANGE_PASSWORD_ROUTE) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
                composable(MERCHANTS_ROUTE) { com.spenditos.android.feature.settings.merchants.MerchantMappingsScreen(onBack = { nav.popBackStack() }) }
                composable(EXPORT_ROUTE) { com.spenditos.android.feature.export.ExportScreen(onBack = { nav.popBackStack() }) }
                composable(PROGRESS_ROUTE) { ProgressScreen(onBack = { nav.popBackStack() }) }
                composable(STATEMENT_IMPORT_ROUTE) { StatementImportScreen(onBack = { nav.popBackStack() }) }
                composable(NOT_SYNCED_ROUTE) { NotSyncedScreen(onBack = { nav.popBackStack() }) }
            }
            envName?.let { EnvBadge(it, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp)) }
        }
    }

    if (showAdd) {
        ScopedViewModels {
            AddTransactionSheet(
                onDismiss = {
                    showAdd = false
                    receiptPrefill = null
                },
                onDone = { message ->
                    showAdd = false
                    receiptPrefill = null
                    scope.launch { snackbar.showSnackbar(message) }
                },
                onScanReceipt = {
                    showAdd = false
                    nav.navigate(RECEIPT_SCAN_ROUTE)
                },
                onCapture = {
                    showAdd = false
                    nav.navigate(CAPTURE_ROUTE)
                },
                prefill = receiptPrefill,
            )
        }
    }
}
