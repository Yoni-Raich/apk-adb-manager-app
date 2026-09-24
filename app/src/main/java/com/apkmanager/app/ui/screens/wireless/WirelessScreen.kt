package com.apkmanager.app.ui.screens.wireless

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.screens.wireless.WirelessViewModel.Step
import com.apkmanager.app.util.WirelessDebuggingNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessScreen(
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    onNavigateToPairing: () -> Unit,
    viewModel: WirelessViewModel = viewModel(factory = WirelessViewModel.Factory(adbRepository))
) {
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val paired by viewModel.isPaired.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wireless ADB") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (connection.isConnected) {
                ConnectedContent(viewModel, connection)
            } else {
                SetupContent(viewModel, connection, viewModel.currentStep(status, paired), paired, onNavigateToPairing)
            }
        }
    }
}

@Composable
private fun SetupContent(
    viewModel: WirelessViewModel,
    connection: ConnectionState,
    step: Step,
    paired: Boolean,
    onNavigateToPairing: () -> Unit
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val discoveredPort by viewModel.discoveredPort.collectAsStateWithLifecycle()
    var showManual by remember { mutableStateOf(false) }
    val doneCount = listOf(status.developerOptions, status.wifi, status.wirelessDebugging, paired).count { it }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            when {
                connection.isLoading -> "Connecting…"
                step == Step.CONNECT -> "Almost there"
                4 - doneCount == 1 -> "One step left"
                else -> "${4 - doneCount} steps left"
            },
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "APK Manager connects the moment Wireless debugging is on — no port to type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { doneCount / 4f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {}
        )
    }

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ChecklistItem(
                number = 1, title = "Developer options", done = status.developerOptions, active = step == Step.DEVELOPER_OPTIONS,
                doneText = "On",
                todoText = "Settings › About phone › tap Build number 7 times",
                actionLabel = "Open About phone",
                onAction = { openSettings(context, Settings.ACTION_DEVICE_INFO_SETTINGS) }
            )
            ChecklistItem(
                number = 2, title = "Wi-Fi", done = status.wifi, active = step == Step.WIFI,
                doneText = "Connected",
                todoText = "Wireless debugging only works on Wi-Fi",
                actionLabel = "Wi-Fi settings",
                onAction = { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }
            )
            ChecklistItem(
                number = 3, title = "Wireless debugging", done = status.wirelessDebugging, active = step == Step.WIRELESS_DEBUGGING,
                doneText = "On",
                todoText = if (status.canSelfEnable) "Off — APK Manager can turn it on for you" else "Off — Android turns it off after Wi-Fi changes",
                actionLabel = "Turn on",
                onAction = {
                    if (!viewModel.enableWirelessDebugging(context)) {
                        WirelessDebuggingNavigator.openWirelessDebugging(context)
                    }
                }
            )
            ChecklistItem(
                number = 4, title = "Pair this phone", done = paired, active = step == Step.PAIR,
                doneText = "APK Manager is trusted",
                todoText = "One time only — enter the 6-digit code",
                actionLabel = "Pair",
                onAction = onNavigateToPairing
            )
        }
    }

    if (step == Step.CONNECT) {
        val error = (connection as? ConnectionState.Error)?.message
        StatusCard(
            icon = if (error != null) Icons.Default.ErrorOutline else Icons.Default.FlashOn,
            title = when {
                connection.isLoading -> "Connecting…"
                error != null -> "Couldn't connect"
                discoveredPort != null -> "Found Wireless debugging on port $discoveredPort"
                else -> "Looking for Wireless debugging…"
            },
            body = error ?: "This usually takes a second or two.",
            isError = error != null
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::retry, enabled = !connection.isLoading) { Text("Connect") }
                if (error != null) TextButton(onClick = onNavigateToPairing) { Text("Pair again") }
            }
        }
    } else {
        StatusCard(
            icon = Icons.Default.FlashOn,
            title = "Skip this next time",
            body = "Once connected, keep \"Keep Wireless debugging on\" enabled and APK Manager switches it back on by itself.",
            isError = false
        )
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { WirelessDebuggingNavigator.openWirelessDebugging(context) }) {
            Text("Open Wireless debugging")
            Spacer(Modifier.size(6.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = { showManual = !showManual }) { Text("Enter port") }
    }

    if (showManual) {
        var port by remember { mutableStateOf(discoveredPort?.toString().orEmpty()) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { port.toIntOrNull()?.let(viewModel::connect) },
                enabled = port.length >= 4 && !connection.isLoading,
                modifier = Modifier.height(52.dp)
            ) { Text("Connect") }
        }
    }
}

@Composable
private fun ChecklistItem(
    number: Int,
    title: String,
    done: Boolean,
    active: Boolean,
    doneText: String,
    todoText: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (done) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = scheme.tertiary)
            } else {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(if (active) scheme.primary else scheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (done) doneText else todoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                if (active) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
    if (active) {
        Surface(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            shadowElevation = 1.dp
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    isError: Boolean,
    actions: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isError) scheme.errorContainer else scheme.surface,
        border = if (isError) null else BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = if (isError) scheme.onErrorContainer else scheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (isError) scheme.onErrorContainer else scheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant)
                if (actions != null) {
                    Spacer(Modifier.height(4.dp))
                    actions()
                }
            }
        }
    }
}

@Composable
private fun ConnectedContent(viewModel: WirelessViewModel, connection: ConnectionState) {
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val keepOn by viewModel.keepWirelessOn.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val port = (connection as? ConnectionState.Connected)?.port
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(88.dp).background(scheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(44.dp))
        }
        Text("Connected", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Silent installs and uninstalls are ready",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoTile("Port", port?.toString() ?: "—", Modifier.weight(1f))
        InfoTile("Found by", "mDNS", Modifier.weight(1f))
        InfoTile("Security", "TLS", Modifier.weight(1f))
    }

    Surface(shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            SwitchRow(
                title = "Reconnect automatically",
                body = "When the app opens and after Wi-Fi changes",
                checked = autoConnect,
                onCheckedChange = viewModel::setAutoConnect
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = scheme.outlineVariant)
            SwitchRow(
                title = "Keep Wireless debugging on",
                body = if (status.canSelfEnable) "Turns it back on by itself when it switches off"
                else "Turns it back on by itself. Needs a one-time secure-settings grant over ADB.",
                checked = keepOn,
                onCheckedChange = viewModel::setKeepWirelessOn
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.weight(1f).height(48.dp)) { Text("Disconnect") }
        TextButton(onClick = viewModel::forgetPairing, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("Forget pairing", color = scheme.error)
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openSettings(context: android.content.Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (_: Exception) {
        WirelessDebuggingNavigator.openDeveloperSettings(context)
    }
}
