package com.apkmanager.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apkmanager.app.ApkManagerApplication
import com.apkmanager.app.ui.components.AnimatedNavBar
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
 * Main navigation graph with smooth transitions and floating bottom navigation bar.
 */
@Composable
fun NavGraph(
    incomingApkUris: List<Uri>? = null,
    onUrisConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as ApkManagerApplication

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelRoutes = listOf(Routes.HOME, Routes.STORE, Routes.UPDATER, Routes.PACKAGES)
    val shouldShowBottomBar = currentRoute in topLevelRoutes

    // Navigate to installer whenever incoming APK URIs are received
    LaunchedEffect(incomingApkUris) {
        if (!incomingApkUris.isNullOrEmpty()) {
            if (navController.currentDestination?.route != Routes.INSTALLER) {
                navController.navigate(Routes.INSTALLER)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                AnimatedNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            enterTransition = {
                fadeIn(animationSpec = tween(280)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(280))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(280)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(280))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) +
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(200))
            }
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
}
