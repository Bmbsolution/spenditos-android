package com.bmbsolution.spenditos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bmbsolution.spenditos.ui.screens.budgets.AddBudgetScreen
import com.bmbsolution.spenditos.ui.screens.budgets.BudgetsScreen
import com.bmbsolution.spenditos.ui.screens.budgets.EditBudgetScreen
import com.bmbsolution.spenditos.ui.screens.dashboard.DashboardScreen
import com.bmbsolution.spenditos.ui.screens.import_data.CSVImportScreen
import com.bmbsolution.spenditos.ui.screens.import_data.StatementImportScreen
import com.bmbsolution.spenditos.ui.screens.login.LoginScreen
import com.bmbsolution.spenditos.ui.screens.onboarding.OnboardingScreen
import com.bmbsolution.spenditos.ui.subscription.PaywallScreen
import com.bmbsolution.spenditos.ui.screens.settings.SettingsScreen
import com.bmbsolution.spenditos.ui.screens.splash.SplashScreen
import com.bmbsolution.spenditos.ui.screens.transactions.AddTransactionScreen
import com.bmbsolution.spenditos.ui.screens.transactions.TransactionsScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions")
    data object AddTransaction : Screen("transactions/add")
    data object EditTransaction : Screen("transactions/edit/{transactionId}") {
        fun createRoute(transactionId: String) = "transactions/edit/$transactionId"
    }
    data object Budgets : Screen("budgets")
    data object AddBudget : Screen("budgets/add")
    data object EditBudget : Screen("budgets/edit/{budgetId}") {
        fun createRoute(budgetId: String) = "budgets/edit/$budgetId"
    }
    data object Gamification : Screen("gamification")
    data object Settings : Screen("settings")
    data object CSVImport : Screen("csv_import")
    data object StatementImport : Screen("statement_import")
    data object Paywall : Screen("paywall")
}

@Composable
fun SpenditosNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route)
                },
                onNavigateToBudgets = {
                    navController.navigate(Screen.Budgets.route)
                },
                onNavigateToGamification = {
                    navController.navigate(Screen.Gamification.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPaywall = {
                    navController.navigate(Screen.Paywall.route)
                },
                onNavigateToAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                }
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onNavigateToEditTransaction = { transactionId ->
                    navController.navigate(Screen.EditTransaction.createRoute(transactionId))
                }
            )
        }

        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveComplete = { _ ->
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditTransaction.route,
            arguments = listOf(
                navArgument("transactionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            // Note: Since we don't have a getById API endpoint, we pass the transaction data
            // through shared ViewModel or fetch from local cache
            // For now, we navigate back after save
            AddTransactionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveComplete = { _ ->
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Budgets.route) {
            BudgetsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddBudget = {
                    navController.navigate(Screen.AddBudget.route)
                },
                onNavigateToEditBudget = { budgetId ->
                    navController.navigate(Screen.EditBudget.createRoute(budgetId))
                }
            )
        }

        composable(Screen.AddBudget.route) {
            AddBudgetScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditBudget.route,
            arguments = listOf(
                navArgument("budgetId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val budgetId = backStackEntry.arguments?.getString("budgetId") ?: ""
            // Similar to transactions, we navigate with the budget data from the list
            // or pass through shared state
            AddBudgetScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Gamification.route) {
            // GamificationScreen placeholder - will be implemented later
            Text("Gamification Screen - Coming Soon")
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPaywall = {
                    navController.navigate(Screen.Paywall.route)
                },
                onNavigateToCSVImport = {
                    navController.navigate(Screen.CSVImport.route)
                },
                onNavigateToStatementImport = {
                    navController.navigate(Screen.StatementImport.route)
                },
                onLogoutComplete = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CSVImport.route) {
            CSVImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.StatementImport.route) {
            StatementImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Paywall.route) {
            PaywallScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPurchaseComplete = {
                    navController.popBackStack()
                }
            )
        }
    }
}
