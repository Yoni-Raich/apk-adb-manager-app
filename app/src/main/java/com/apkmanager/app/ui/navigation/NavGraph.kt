package com.apkmanager.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apkmanager.app.ApkManagerApplication
import com.apkmanager.app.ui.screens.home.HomeScreen
import com.apkmanager.app.ui.screens.installer.InstallerScreen
import com.apkmanager.app.ui.screens.packages.PackagesScreen
import com.apkmanager.app.ui.screens.pairing.PairingScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val HOME = "home"
    const val PAIRING = "pairing"
    const val INSTALLER = "installer"
    const val PACKAGES = "packages"
    const val UPDATER = "updater"
    const val STORE = "store"
}

/**
 * Main navigation graph for the app.
 */
@Composable
fun NavGraph(
    incomingApkUris: List<Uri>? = null,
    onUrisConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as ApkManagerApplication

    // Navigate to installer whenever incoming APK URIs are received
    LaunchedEffect(incomingApkUris) {
        if (!incomingApkUris.isNullOrEmpty()) {
            if (navController.currentDestination?.route != Routes.INSTALLER) {
                navController.navigate(Routes.INSTALLER)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (!incomingApkUris.isNullOrEmpty()) Routes.INSTALLER else Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                adbRepository = app.adbRepository,
                selfUpdateRepository = app.selfUpdateRepository,
                onNavigateToPairing = { navController.navigate(Routes.PAIRING) },
                onNavigateToInstaller = { navController.navigate(Routes.INSTALLER) },
                onNavigateToPackages = { navController.navigate(Routes.PACKAGES) },
                onNavigateToUpdater = { navController.navigate(Routes.UPDATER) },
                onNavigateToStore = { navController.navigate(Routes.STORE) }
            )
        }

        composable(Routes.PAIRING) {
            PairingScreen(
                adbRepository = app.adbRepository,
                onNavigateBack = { navController.popBackStack() },
                onPairingComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.INSTALLER) {
            InstallerScreen(
                adbRepository = app.adbRepository,
                incomingUris = incomingApkUris ?: emptyList(),
                onIncomingUrisHandled = onUrisConsumed,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.INSTALLER) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.PACKAGES) {
            PackagesScreen(
                adbRepository = app.adbRepository,
                packageRepository = app.packageRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPDATER) {
            com.apkmanager.app.ui.screens.updater.UpdaterScreen(
                appUpdateRepository = app.appUpdateRepository,
                adbRepository = app.adbRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STORE) {
            com.apkmanager.app.ui.screens.store.StoreScreen(
                storeRepository = app.storeRepository,
                adbRepository = app.adbRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
