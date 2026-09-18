package com.apkmanager.app.ui.screens.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.updater.TrackedApp
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.AppUpdateRepository
import com.apkmanager.app.ui.components.ConnectionStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    appUpdateRepository: AppUpdateRepository,
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    viewModel: UpdaterViewModel = viewModel(
        factory = UpdaterViewModel.Factory(appUpdateRepository, adbRepository)
    )
) {
    val trackedApps by viewModel.trackedApps.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val connectionState by adbRepository.connectionState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<TrackedApp?>(null) }

    val updatableCount = trackedApps.count { it.status is UpdateStatus.UpdateAvailable }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub App Updater") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadInstalledApps()
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add GitHub Repository")
                    }
                    IconButton(
                        onClick = viewModel::checkForUpdates,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status
            ConnectionStatusBar(connectionState = connectionState)
            Spacer(modifier = Modifier.height(12.dp))

            // Update All Banner if updates are available
            if (updatableCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$updatableCount update${if (updatableCount > 1) "s" else ""} available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Install silently via ADB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Button(
                            onClick = viewModel::updateAll,
                            enabled = connectionState.isConnected
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update All")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (trackedApps.isEmpty() && !isRefreshing) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No GitHub Apps Tracked",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Installed open-source apps from GitHub (e.g. Streamflix, Hey Mike, Termux, NewPipe) will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.loadInstalledApps()
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add GitHub Repository")
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(trackedApps, key = { it.packageName }) { app ->
                        TrackedAppItem(
                            app = app,
                            isAdbConnected = connectionState.isConnected,
                            onUpdate = { viewModel.updateApp(app) },
                            onEdit = { editingApp = app },
                            onRemove = { viewModel.removeTrackedApp(app) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRepoDialog(
            installedApps = installedApps,
            onDismiss = { showAddDialog = false },
            onConfirm = { pkg, repo ->
                viewModel.addCustomRepo(pkg, repo)
                showAddDialog = false
            }
        )
    }

    editingApp?.let { app ->
        EditRepoDialog(
            app = app,
            onDismiss = { editingApp = null },
            onConfirm = { newRepo ->
                viewModel.editRepo(app, newRepo)
                editingApp = null
            }
        )
    }
}

@Composable
fun TrackedAppItem(
    app: TrackedApp,
    isAdbConnected: Boolean,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.githubRepo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Repository",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Versions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installed: ${app.installedVersionName.ifBlank { "v${app.installedVersionCode}" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (val status = app.status) {
                    is UpdateStatus.UpdateAvailable -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "New: ${status.release.cleanVersion}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    is UpdateStatus.UpToDate -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Up to date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is UpdateStatus.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Checking...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is UpdateStatus.Success -> {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is UpdateStatus.Error -> {
                        Text(
                            text = "Check failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }

            // Action / Progress bar
            when (val status = app.status) {
                is UpdateStatus.UpdateAvailable -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isAdbConnected
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAdbConnected) "Update via ADB" else "Connect ADB to Update")
                    }
                }
                is UpdateStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading update: ${(status.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateStatus.Installing -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is UpdateStatus.Error -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fix Repository / Edit")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun EditRepoDialog(
    app: TrackedApp,
    onDismiss: () -> Unit,
    onConfirm: (newRepo: String) -> Unit
) {
    var repoInput by remember { mutableStateOf(app.githubRepo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit GitHub Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "App: ${app.appName} (${app.packageName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repo") },
                    placeholder = { Text("owner/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = repoInput.trim().removePrefix("https://github.com/").trim('/')
                    if (clean.isNotBlank()) {
                        onConfirm(clean)
                    }
                },
                enabled = repoInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddRepoDialog(
    installedApps: List<com.apkmanager.app.data.updater.InstalledAppOption>,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String, repo: String) -> Unit
) {
    var repoInput by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }
    var selectedAppName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track GitHub App") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pick an installed app or enter repository details:",
                    style = MaterialTheme.typography.bodySmall
                )

                // App Picker Header
                OutlinedCard(
                    onClick = { showAppPicker = !showAppPicker },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedAppName.isNotBlank()) selectedAppName else "Choose installed app...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedAppName.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedAppName.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            if (showAppPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                if (showAppPicker) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search installed apps") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        LazyColumn {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedAppName = app.appName
                                            packageInput = app.packageName
                                            if (app.suggestedRepo.isNotBlank()) {
                                                repoInput = app.suggestedRepo
                                            }
                                            showAppPicker = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (app.suggestedRepo.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Known",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repo") },
                    placeholder = { Text("owner/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    label = { Text("Package Name (optional)") },
                    placeholder = { Text("e.g. com.streamflix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanRepo = repoInput.trim().removePrefix("https://github.com/").trim('/')
                    val pkg = packageInput.trim().ifBlank { cleanRepo.substringAfterLast('/') }
                    if (cleanRepo.isNotBlank()) {
                        onConfirm(pkg, cleanRepo)
                    }
                },
                enabled = repoInput.isNotBlank()
            ) {
                Text("Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
