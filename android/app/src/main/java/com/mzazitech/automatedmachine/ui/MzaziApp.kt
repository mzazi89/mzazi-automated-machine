package com.mzazitech.automatedmachine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mzazitech.automatedmachine.MzaziApplication
import com.mzazitech.automatedmachine.ui.screens.admin.AdminScreen
import com.mzazitech.automatedmachine.ui.screens.auth.LoginScreen
import com.mzazitech.automatedmachine.ui.screens.catalogue.BundleCatalogueScreen
import com.mzazitech.automatedmachine.ui.screens.dashboard.DashboardScreen
import com.mzazitech.automatedmachine.ui.screens.history.HistoryScreen
import com.mzazitech.automatedmachine.ui.screens.networks.NetworkSelectScreen
import com.mzazitech.automatedmachine.ui.screens.purchase.PurchaseScreen
import com.mzazitech.automatedmachine.ui.screens.settings.SettingsScreen
import com.mzazitech.automatedmachine.ui.screens.splash.SplashScreen
import com.mzazitech.automatedmachine.ui.screens.ussd.UssdExecutionScreen
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.MzaziBlack
import com.mzazitech.automatedmachine.ui.theme.MzaziBorder
import com.mzazitech.automatedmachine.ui.theme.MzaziSurface
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextSecondary

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val NETWORKS = "networks"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADMIN = "admin"
    const val CATALOGUE = "catalogue"
    const val PURCHASE = "purchase"
    const val USSD = "ussd"

    fun catalogue(networkCode: String) = "$CATALOGUE/$networkCode"
    fun purchase(bundleId: String? = null) =
        if (bundleId.isNullOrBlank()) "$PURCHASE?bundleId=" else "$PURCHASE?bundleId=$bundleId"
    fun ussd(transactionId: String) = "$USSD/$transactionId"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    TabItem(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    TabItem(Routes.NETWORKS, "Sell", Icons.Filled.ShoppingCart),
    TabItem(Routes.HISTORY, "History", Icons.Filled.List),
    TabItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun MzaziApp(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val container = (context.applicationContext as MzaziApplication).container
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MzaziBlack,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MzaziSurface,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                Text(tab.label, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                selectedTextColor = ElectricBlue,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = ElectricBlue.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MzaziBlack)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH
            ) {
                composable(Routes.SPLASH) {
                    SplashScreen(
                        onFinished = {
                            val destination = if (container.session.isLoggedIn()) {
                                Routes.DASHBOARD
                            } else {
                                Routes.LOGIN
                            }
                            navController.navigate(destination) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.LOGIN) {
                    LoginScreen(
                        container = container,
                        onAuthenticated = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                        onContinueOffline = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        container = container,
                        onNewSale = { navController.navigate(Routes.NETWORKS) },
                        onOpenTransaction = { navController.navigate(Routes.ussd(it)) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenAdmin = { navController.navigate(Routes.ADMIN) }
                    )
                }

                composable(Routes.NETWORKS) {
                    NetworkSelectScreen(
                        container = container,
                        onNetworkSelected = { navController.navigate(Routes.catalogue(it.code)) },
                        onBrowseAll = { navController.navigate(Routes.catalogue("ALL")) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.CATALOGUE}/{network}",
                    arguments = listOf(navArgument("network") { type = NavType.StringType })
                ) { entry ->
                    val network = entry.arguments?.getString("network") ?: "ALL"
                    BundleCatalogueScreen(
                        container = container,
                        networkCode = network,
                        onBuy = { bundleId -> navController.navigate(Routes.purchase(bundleId)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${Routes.PURCHASE}?bundleId={bundleId}",
                    arguments = listOf(
                        navArgument("bundleId") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val bundleId = entry.arguments?.getString("bundleId").orEmpty()
                    PurchaseScreen(
                        container = container,
                        initialBundleId = bundleId,
                        onBack = { navController.popBackStack() },
                        onTrackDelivery = { transactionId ->
                            navController.navigate(Routes.ussd(transactionId))
                        }
                    )
                }

                composable(
                    route = "${Routes.USSD}/{transactionId}",
                    arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
                ) { entry ->
                    val transactionId = entry.arguments?.getString("transactionId").orEmpty()
                    UssdExecutionScreen(
                        container = container,
                        transactionId = transactionId,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        container = container,
                        onOpenTransaction = { navController.navigate(Routes.ussd(it)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                        onSignedOut = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.ADMIN) {
                    AdminScreen(
                        container = container,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
