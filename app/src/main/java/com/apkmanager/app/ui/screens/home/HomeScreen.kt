package com.apkmanager.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.animation.pressScaleEffect
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.ui.components.GlassCard
import com.apkmanager.app.ui.components.GradientButton
import com.apkmanager.app.ui.theme.*
import com.apkmanager.app.util.WirelessDebuggingNavigator

/**
 * Premium Home Screen — Dashboard with hero connection monitor,
 * modern self-update card, and a 2x2 grid of feature modules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    adbRepository: AdbRepository,
    selfUpdateRepository: com.apkmanager.app.repository.SelfUpdateRepository? = null,
    onNavigateToPairing: () -> Unit,
    onNavigateToInstaller: () -> Unit,
    onNavigateToPackages: () -> Unit,
    onNavigateToUpdater: () -> Unit,
    onNavigateToStore: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(adbRepository, selfUpdateRepository))
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val selfUpdateInfo by viewModel.selfUpdateInfo.collectAsStateWithLifecycle()
    val connectPort by viewModel.connectPort.collectAsStateWithLifecycle()
    val discoveredServices by viewModel.discoveredServices.collectAsStateWithLifecycle()
    val discoveryError by viewModel.discoveryError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var navMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Restart mDNS discovery on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startDiscovery()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopDiscovery()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppGradients.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "APK Manager",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = PrimaryPurple.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "PRO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryCyan,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { WirelessDebuggingNavigator.openDeveloperSettings(context) },
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Developer Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status Pill
            ConnectionStatusBar(connectionState = connectionState)

            // Auto-discovered wireless debugging endpoint hint
            if (!connectionState.isConnected) {
                val discovered = discoveredServices.firstOrNull()
                AnimatedVisibility(visible = discovered != null || discoveryError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (discovered != null) {
                                    Modifier.clickable {
                                        viewModel.updateConnectPort(discovered.port.toString())
                                    }
                                } else Modifier
                            ),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (discovered != null) Icons.Default.Sensors else Icons.Default.WifiTetheringError,
                                contentDescription = null,
                                tint = if (discovered != null) SecondaryCyan else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (discovered != null) "Detected Port: ${discovered.port} (Tap to auto-fill)"
                                    else "Discovery: $discoveryError",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Self-Update Banner
            val selfInfo = selfUpdateInfo
            if (selfInfo != null && (selfInfo.status is UpdateStatus.UpdateAvailable || selfInfo.status is UpdateStatus.Downloading || selfInfo.status is UpdateStatus.Installing)) {
                Spacer(modifier = Modifier.height(10.dp))
                SelfUpdateCard(
                    selfInfo = selfInfo,
                    isConnected = connectionState.isConnected,
                    onUpdateClick = viewModel::installSelfUpdate
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Wireless ADB Connection Card
            AdbConnectionCard(
                connectionState = connectionState,
                connectPort = connectPort,
                onPortChange = viewModel::updateConnectPort,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onNavigateToPairing = onNavigateToPairing,
                navMessage = navMessage,
                onOpenWirelessSettings = {
                    when (WirelessDebuggingNavigator.openWirelessDebugging(context)) {
                        WirelessDebuggingNavigator.OpenResult.FAILED ->
                            navMessage = "Could not open Settings."
                        else -> navMessage = null
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Quick Actions Dashboard Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Modules & Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "4 AVAILABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Feature Modules Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardModuleCard(
                    title = "Install APK",
                    subtitle = "Local & split files",
                    icon = Icons.Default.InstallMobile,
                    gradient = AppGradients.purpleToPink,
                    enabled = connectionState.isConnected,
                    onClick = onNavigateToInstaller,
                    modifier = Modifier.weight(1f)
                )

                DashboardModuleCard(
                    title = "App Store",
                    subtitle = "Curated GitHub apps",
                    icon = Icons.Default.Storefront,
                    gradient = AppGradients.accent,
                    enabled = true,
                    onClick = onNavigateToStore,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardModuleCard(
                    title = "Packages",
                    subtitle = "Inspect & uninstall",
                    icon = Icons.Default.Apps,
                    gradient = AppGradients.fire,
                    enabled = connectionState.isConnected,
                    onClick = onNavigateToPackages,
                    modifier = Modifier.weight(1f)
                )

                DashboardModuleCard(
                    title = "Updater",
                    subtitle = "Track GitHub releases",
                    icon = Icons.Default.Update,
                    gradient = AppGradients.primary,
                    enabled = true,
                    onClick = onNavigateToUpdater,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * High-end module card in the 2x2 dashboard grid.
 */
@Composable
private fun DashboardModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.height(138.dp),
        onClick = if (enabled) onClick else null,
        backgroundColor = if (enabled) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
        borderColor = if (enabled) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) gradient else Brush.linearGradient(listOf(Color(0xFF3B4054), Color(0xFF262B3F)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Self-update alert card with gradient borders and integrated install CTA.
 */
@Composable
private fun SelfUpdateCard(
    selfInfo: com.apkmanager.app.repository.SelfUpdateRepository.SelfUpdateInfo,
    isConnected: Boolean,
    onUpdateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, AppGradients.purpleToPink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppGradients.purpleToPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APK Manager ${selfInfo.latestRelease?.cleanVersion ?: "Update"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Installed: v${selfInfo.currentVersionName} • Silent ADB upgrade",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val status = selfInfo.status) {
                is UpdateStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrimaryPurpleLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading update: ${(status.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateStatus.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SecondaryCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> {
                    GradientButton(
                        text = if (isConnected) "Update App Now via ADB" else "Connect ADB to Update",
                        onClick = onUpdateClick,
                        enabled = isConnected,
                        icon = Icons.Default.Download,
                        gradient = AppGradients.purpleToPink
                    )
                }
            }
        }
    }
}

/**
 * Wireless ADB Connection management card.
 */
@Composable
private fun AdbConnectionCard(
    connectionState: ConnectionState,
    connectPort: String,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToPairing: () -> Unit,
    navMessage: String?,
    onOpenWirelessSettings: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = SecondaryCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADB Connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (connectionState.isConnected) {
                Surface(
                    color = StatusConnected.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = StatusConnected,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!connectionState.isConnected) {
            OutlinedTextField(
                value = connectPort,
                onValueChange = onPortChange,
                label = { Text("Wireless Debugging Port") },
                placeholder = { Text("e.g. 37271") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Lan,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToPairing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pair", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = connectPort.isNotEmpty() && !connectionState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenWirelessSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Wireless Debugging Settings")
            }

            if (navMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = navMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect ADB", fontWeight = FontWeight.Bold)
            }
        }

        if (connectionState is ConnectionState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (connectionState as ConnectionState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
