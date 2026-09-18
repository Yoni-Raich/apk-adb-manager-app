package com.apkmanager.app.ui.screens.pairing

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.theme.StatusConnected
import com.apkmanager.app.ui.theme.StatusError
import com.apkmanager.app.util.PairingNotificationHelper
import com.apkmanager.app.util.WirelessDebuggingNavigator

/**
 * Pairing screen for wireless debugging setup.
 * Guides the user through entering the pairing port and code,
 * supporting Notification reply and Picture-in-Picture to prevent
 * Android from resetting the pairing dialog.
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

    // Pairing-mode mDNS discovery restarts on every resume so the
    // indicator reflects whether the system pairing UI is advertising.
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
                title = { Text("Pair Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Method 1 (Recommended): Notification Pairing
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Method 1: Notification (Recommended)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Android closes the code dialog when switching full-screen apps. Use the notification shade to enter the code without closing the dialog!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                PairingNotificationHelper.showPairingNotification(context)
                                notificationSent = true
                                WirelessDebuggingNavigator.openDeveloperSettings(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Notification Pairing")
                    }

                    if (notificationSent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ Notification active! In Settings, open 'Pair with pairing code', swipe down the top bar & reply with your Port and Code.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Method 2: Floating Window (PiP)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Method 2: Floating Window (PiP)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Shrink this app into a floating window over Developer Options.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                (context as? Activity)?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().build()
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Floating Window Mode")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Method 3: Manual Direct Input (For Split-Screen)
            Text(
                text = "Method 3: Direct / Split-Screen Input",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    WirelessDebuggingNavigator.openDeveloperSettings(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Developer Options (Split-Screen)")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pairing-mode advertisement indicator: visible only while the
            // system "Pair with pairing code" UI is active. Informational
            // only — the pairing port is never used for connecting.
            Text(
                text = if (pairingServices.isNotEmpty()) {
                    "Pairing mode detected nearby " +
                        "(${pairingServices.size} service${if (pairingServices.size == 1) "" else "s"}). " +
                        "Enter the port and code shown on the device dialog below."
                } else {
                    "No pairing advertisement detected. Open 'Pair device " +
                        "with pairing code' in Wireless Debugging first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = pairingPort,
                onValueChange = viewModel::updatePort,
                label = { Text("Pairing Port") },
                placeholder = { Text("e.g. 37215") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPairing
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = pairingCode,
                onValueChange = viewModel::updateCode,
                label = { Text("Pairing Code (6 digits)") },
                placeholder = { Text("e.g. 482916") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPairing
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pair button
            Button(
                onClick = viewModel::startPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = pairingPort.isNotEmpty() && pairingCode.length == 6 && !isPairing
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairing...")
                } else {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Pairing")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Result feedback
            when (pairingResult) {
                is PairingViewModel.PairingState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StatusConnected.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusConnected)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pairing successful! You can now connect.", color = StatusConnected)
                        }
                    }
                }
                is PairingViewModel.PairingState.Failed -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StatusError.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (pairingResult as PairingViewModel.PairingState.Failed).error,
                                color = StatusError
                            )
                        }
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
