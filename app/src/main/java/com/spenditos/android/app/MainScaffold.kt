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
import androidx.compose.runtime.getValue
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
import com.spenditos.android.feature.placeholder.ComingSoonScreen
import com.spenditos.android.feature.settings.SettingsScreen
import com.spenditos.android.feature.sync.NotSyncedScreen
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

/** iOS MainTabView: five tabs; the FAB is the one global "add" entry point. */
@Composable
fun MainScaffold(envName: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
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
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                            modifier = Modifier.testTag("tab.${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Tab.TRANSACTIONS.route) {
                composable(Tab.HOME.route) { ComingSoonScreen("Home", "Your cash-flow status lands in Wave C.") }
                composable(Tab.TRANSACTIONS.route) { TransactionListScreen(onOpenNotSynced = { nav.navigate(NOT_SYNCED_ROUTE) }) }
                composable(Tab.PLAN.route) { ComingSoonScreen("Plan", "Income & bills and Month plan land in Waves B and C.") }
                composable(Tab.REPORTS.route) { ComingSoonScreen("Reports", "Reports land in Wave A.") }
                composable(Tab.SETTINGS.route) { SettingsScreen(onOpenNotSynced = { nav.navigate(NOT_SYNCED_ROUTE) }) }
                composable(NOT_SYNCED_ROUTE) { NotSyncedScreen(onBack = { nav.popBackStack() }) }
            }
            envName?.let { EnvBadge(it, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp)) }
        }
    }

    if (showAdd) {
        ScopedViewModels {
            AddTransactionSheet(
                onDismiss = { showAdd = false },
                onDone = { message ->
                    showAdd = false
                    scope.launch { snackbar.showSnackbar(message) }
                },
            )
        }
    }
}
