package com.apkmanager.app.ui.screens.pairing

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.animation.pressScaleEffect
import com.apkmanager.app.ui.components.GlassCard
import com.apkmanager.app.ui.components.GradientButton
import com.apkmanager.app.ui.theme.*
import com.apkmanager.app.util.PairingNotificationHelper
import com.apkmanager.app.util.WirelessDebuggingNavigator

/**
 * Premium Pairing Screen for wireless debugging setup.
 * Clear 3-method guide with notification pairing, PiP, and split-screen inputs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    onPairingComplete: () -> Unit,
    viewModel: PairingViewModel = viewModel(factory = PairingViewModel.Factory(adbRepository))
) {
    val pairingPort by viewModel.pairingPort.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val isPairing by viewModel.isPairing.collectAsStateWithLifecycle()
    val pairingResult by viewModel.pairingResult.collectAsStateWithLifecycle()
    val pairingServices by viewModel.pairingServices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Pairing-mode mDNS discovery restarts on every resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPairingDiscovery()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPairingDiscovery()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPairingDiscovery()
        }
    }

    var notificationSent by remember { mutableStateOf(false) }

    // Permission launcher for POST_NOTIFICATIONS on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PairingNotificationHelper.showPairingNotification(context)
            notificationSent = true
            WirelessDebuggingNavigator.openDeveloperSettings(context)
        }
    }

    LaunchedEffect(pairingResult) {
        if (pairingResult is PairingViewModel.PairingState.Success) {
            PairingNotificationHelper.dismissNotification(context)
            kotlinx.coroutines.delay(1500)
            onPairingComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Pair Device",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Establish secure ADB TLS trust",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.pressScaleEffect()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 18.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Method 1 (Recommended): Notification Pairing
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, AppGradients.purpleToPink)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppGradients.purpleToPink),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Notification Pairing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = SecondaryCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "RECOMMENDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryCyan,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Never lose the pairing dialog",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Android closes the pairing dialog when switching full-screen apps. Notification reply allows you to enter Port & Code directly from the notification shade!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    GradientButton(
                        text = "Start Notification Pairing",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PairingNotificationHelper.showPairingNotification(context)
                                notificationSent = true
                                WirelessDebuggingNavigator.openDeveloperSettings(context)
                            }
                        },
                        icon = Icons.Default.Notifications,
                        gradient = AppGradients.purpleToPink
                    )

                    if (notificationSent) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = StatusConnected.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusConnected.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "✓ Notification active! Open 'Pair device with pairing code' in Settings, swipe down notification shade and reply with Port & Code.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusConnected,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Method 2: Floating Window (PiP)
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SecondaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = SecondaryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Method 2: Floating Window (PiP)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Overlay this app on top of Developer Options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            (context as? Activity)?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().build()
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .pressScaleEffect()
                ) {
                    Icon(Icons.Default.PictureInPicture, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Floating Window Mode", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Method 3: Manual Direct Input
            GlassCard {
                Text(
                    text = "Method 3: Direct / Split-Screen Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (pairingServices.isNotEmpty()) {
                        "Pairing mode detected nearby (${pairingServices.size} service(s)). Enter the Port and Code shown on your device below:"
                    } else {
                        "Open 'Pair device with pairing code' in Wireless Debugging settings, then enter values here:"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = viewModel::updatePort,
                    label = { Text("Pairing Port") },
                    placeholder = { Text("e.g. 37215") },
                    leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null, tint = SecondaryCyan) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isPairing
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = viewModel::updateCode,
                    label = { Text("6-Digit Pairing Code") },
                    placeholder = { Text("e.g. 482916") },
                    leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, tint = SecondaryCyan) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isPairing
                )

                Spacer(modifier = Modifier.height(14.dp))

                GradientButton(
                    text = if (isPairing) "Pairing in progress..." else "Start Pairing",
                    onClick = viewModel::startPairing,
                    enabled = pairingPort.isNotEmpty() && pairingCode.length == 6 && !isPairing,
                    icon = Icons.Default.Key,
                    gradient = AppGradients.primary
                )

                // Result feedback
                when (pairingResult) {
                    is PairingViewModel.PairingState.Success -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusConnected.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, StatusConnected.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusConnected)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Pairing successful! Returning to Dashboard...",
                                    color = StatusConnected,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    is PairingViewModel.PairingState.Failed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusError.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = StatusError)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = (pairingResult as PairingViewModel.PairingState.Failed).error,
                                    color = StatusError,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
