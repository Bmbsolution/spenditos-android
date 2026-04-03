package com.bmbsolution.spenditos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bmbsolution.spenditos.ui.screens.budgets.BudgetsScreen
import com.bmbsolution.spenditos.ui.screens.dashboard.DashboardScreen
import com.bmbsolution.spenditos.ui.screens.login.LoginScreen
import com.bmbsolution.spenditos.ui.screens.onboarding.OnboardingScreen
import com.bmbsolution.spenditos.ui.screens.splash.SplashScreen
import com.bmbsolution.spenditos.ui.screens.transactions.TransactionsScreen

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions")
    data object Budgets : Screen("budgets")
    data object Gamification : Screen("gamification")
    data object Settings : Screen("settings")
    data object ImportData : Screen("import_data")
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
                }
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddTransaction = {
                    // TODO: Navigate to add transaction screen
                },
                onNavigateToEditTransaction = { transactionId ->
                    // TODO: Navigate to edit transaction screen with transactionId
                }
            )
        }

        composable(Screen.Budgets.route) {
            BudgetsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToAddBudget = {
                    // TODO: Navigate to add budget screen
                },
                onNavigateToEditBudget = { budgetId ->
                    // TODO: Navigate to edit budget screen with budgetId
                }
            )
        }
    }
}
