package com.apkmanager.app.ui.screens.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.screens.pairing.PairingViewModel.PairingState
import com.apkmanager.app.util.PairingNotificationHelper
import com.apkmanager.app.util.WirelessDebuggingNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    onPairingComplete: () -> Unit,
    viewModel: PairingViewModel = viewModel(factory = PairingViewModel.Factory(adbRepository))
) {
    val context = LocalContext.current
    val code by viewModel.code.collectAsStateWithLifecycle()
    val manualPort by viewModel.manualPort.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discoveredPort by viewModel.discoveredPort.collectAsStateWithLifecycle()
    var editPort by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.startDiscovery()
        onPauseOrDispose { viewModel.stopDiscovery() }
    }

    LaunchedEffect(state) {
        if (state == PairingState.Connected || state == PairingState.PairedOnly) {
            PairingNotificationHelper.dismissNotification(context)
            onPairingComplete()
        }
    }

    val startNotificationPairing = {
        PairingNotificationHelper.showPairingNotification(context)
        WirelessDebuggingNavigator.openWirelessDebugging(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startNotificationPairing()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair this phone") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val scheme = MaterialTheme.colorScheme
            val found = discoveredPort != null
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (found) scheme.tertiaryContainer else scheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (found) Icons.Default.Wifi else Icons.Default.WifiFind,
                        contentDescription = null,
                        tint = if (found) scheme.onTertiaryContainer else scheme.onSurfaceVariant
                    )
                    Text(
                        if (found) "Pairing screen found — port filled in for you"
                        else "Open \"Pair device with pairing code\" in Wireless debugging",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (found) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Enter the 6-digit code", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "It's shown under \"Pair device with pairing code\" in Wireless debugging.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }

                CodeInput(code = code, onCodeChange = viewModel::updateCode, isError = state is PairingState.Failed)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            manualPort.isNotEmpty() -> "Pairing port $manualPort"
                            found -> "Pairing port $discoveredPort"
                            else -> "Waiting for the pairing port…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    TextButton(onClick = { editPort = !editPort }) { Text("change") }
                }
                if (editPort) {
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = viewModel::updateManualPort,
                        label = { Text("Pairing port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                (state as? PairingState.Failed)?.let {
                    Text(it.error, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                }

                Button(
                    onClick = viewModel::pair,
                    enabled = code.length == 6 && state != PairingState.InProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.tertiary, contentColor = scheme.onTertiary)
                ) {
                    if (state == PairingState.InProgress) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onTertiary)
                        Spacer(Modifier.size(10.dp))
                        Text("Pairing…")
                    } else {
                        Text("Pair and connect")
                    }
                }
            }

            HorizontalDivider(color = scheme.outlineVariant)

            Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(40.dp).background(scheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Notifications, contentDescription = null, tint = scheme.onPrimaryContainer) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Code keeps disappearing?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Stay in Settings and reply to our notification with just the code. The port is found for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else startNotificationPairing()
                            },
                            border = BorderStroke(1.dp, scheme.outline)
                        ) { Text("Pair from notification") }
                    }
                }
            }

            TextButton(
                onClick = { WirelessDebuggingNavigator.openWirelessDebugging(context) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Open Wireless debugging")
                Spacer(Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Six boxes backed by one text field, so paste and IME autofill work. */
@Composable
private fun CodeInput(code: String, onCodeChange: (String) -> Unit, isError: Boolean) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Pairing code" },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val char = code.getOrNull(index)?.toString().orEmpty()
                    val focused = index == code.length
                    Surface(
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surface,
                        border = BorderStroke(
                            if (focused) 2.dp else 1.dp,
                            when {
                                isError -> scheme.error
                                focused -> scheme.primary
                                else -> scheme.outline
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(char, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    )
}
