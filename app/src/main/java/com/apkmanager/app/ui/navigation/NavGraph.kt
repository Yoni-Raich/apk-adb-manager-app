package com.apkmanager.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apkmanager.app.ApkManagerApplication
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.ui.screens.home.HomeScreen
import com.apkmanager.app.ui.screens.installer.InstallerScreen
import com.apkmanager.app.ui.screens.packages.PackagesScreen
import com.apkmanager.app.ui.screens.pairing.PairingScreen
import com.apkmanager.app.ui.screens.store.StoreScreen
import com.apkmanager.app.ui.screens.store.StoreViewModel
import com.apkmanager.app.ui.screens.updater.UpdaterScreen
import com.apkmanager.app.ui.screens.updater.UpdaterViewModel
import com.apkmanager.app.ui.screens.wireless.WirelessScreen

object Routes {
    const val HOME = "home"
    const val STORE = "store"
    const val UPDATES = "updates"
    const val APPS = "apps"
    const val WIRELESS = "wireless"
    const val PAIRING = "pairing"
    const val INSTALLER = "installer"
}

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Routes.STORE, "Store", Icons.Filled.LocalMall, Icons.Outlined.LocalMall),
    Tab(Routes.UPDATES, "Updates", Icons.Filled.SystemUpdate, Icons.Outlined.SystemUpdate),
    Tab(Routes.APPS, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps)
)

@Composable
fun NavGraph(
    incomingApkUris: List<Uri>? = null,
    onUrisConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ApkManagerApplication

    // Shared between Home and their own tabs, scoped to the activity.
    val storeViewModel: StoreViewModel = viewModel(factory = StoreViewModel.Factory(app.storeRepository))
    val updaterViewModel: UpdaterViewModel = viewModel(
        factory = UpdaterViewModel.Factory(app.appUpdateRepository, app.adbRepository)
    )
    val trackedApps by updaterViewModel.trackedApps.collectAsStateWithLifecycle()
    val updateCount = trackedApps.count { it.status is UpdateStatus.UpdateAvailable }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(incomingApkUris) {
        if (!incomingApkUris.isNullOrEmpty() && navController.currentDestination?.route != Routes.INSTALLER) {
            navController.navigate(Routes.INSTALLER)
        }
    }

    val openWireless = { navController.navigate(Routes.WIRELESS) { launchSingleTop = true } }

    Scaffold(
        bottomBar = {
            if (currentRoute in TABS.map { it.route }) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = {
                                BadgedBox(badge = {
                                    if (tab.route == Routes.UPDATES && updateCount > 0) Badge { Text("$updateCount") }
                                }) {
                                    Icon(if (selected) tab.selected else tab.unselected, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    adbRepository = app.adbRepository,
                    selfUpdateRepository = app.selfUpdateRepository,
                    storeViewModel = storeViewModel,
                    updaterViewModel = updaterViewModel,
                    onOpenWireless = openWireless,
                    onOpenStore = { navController.navigateToTab(Routes.STORE) },
                    onOpenUpdates = { navController.navigateToTab(Routes.UPDATES) },
                    onOpenInstaller = { navController.navigate(Routes.INSTALLER) }
                )
            }
            composable(Routes.STORE) {
                StoreScreen(viewModel = storeViewModel, adbRepository = app.adbRepository, onOpenWireless = openWireless)
            }
            composable(Routes.UPDATES) {
                UpdaterScreen(viewModel = updaterViewModel, adbRepository = app.adbRepository, onOpenWireless = openWireless)
            }
            composable(Routes.APPS) {
                PackagesScreen(
                    adbRepository = app.adbRepository,
                    packageRepository = app.packageRepository,
                    onOpenWireless = openWireless
                )
            }
            composable(Routes.WIRELESS) {
                WirelessScreen(
                    adbRepository = app.adbRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPairing = { navController.navigate(Routes.PAIRING) }
                )
            }
            composable(Routes.PAIRING) {
                PairingScreen(
                    adbRepository = app.adbRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onPairingComplete = { navController.popBackStack() }
                )
            }
            composable(Routes.INSTALLER) {
                InstallerScreen(
                    adbRepository = app.adbRepository,
                    incomingUris = incomingApkUris.orEmpty(),
                    onIncomingUrisHandled = onUrisConsumed,
                    onNavigateBack = {
                        if (!navController.popBackStack()) navController.navigateToTab(Routes.HOME)
                    },
                    onOpenWireless = openWireless
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
