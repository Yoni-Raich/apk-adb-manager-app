package com.apkmanager.app.ui.screens.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.updater.TrackedApp
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.AppUpdateRepository
import com.apkmanager.app.ui.animation.pressScaleEffect
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.ui.components.GlassCard
import com.apkmanager.app.ui.components.GradientButton
import com.apkmanager.app.ui.components.ShimmerCardPlaceholder
import com.apkmanager.app.ui.theme.*
import com.apkmanager.app.util.AppIconImage

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "GitHub Updater",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (updatableCount > 0) {
                                    Surface(
                                        color = TertiaryPink.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, TertiaryPink.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "$updatableCount NEW",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TertiaryPink,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Automated release tracking for open-source apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.pressScaleEffect()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.loadInstalledApps()
                            showAddDialog = true
                        },
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add GitHub Repository", tint = SecondaryCyan)
                    }
                    IconButton(
                        onClick = viewModel::checkForUpdates,
                        enabled = !isRefreshing,
                        modifier = Modifier.pressScaleEffect()
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SecondaryCyan
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                        }
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status
            ConnectionStatusBar(
                connectionState = connectionState,
                onVerifyClick = viewModel::verifyConnection
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Update All Banner if updates are available
            if (updatableCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, AppGradients.purpleToPink)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AppGradients.purpleToPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "$updatableCount update${if (updatableCount > 1) "s" else ""} available",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (connectionState.isConnected) "Batch silent update via ADB" else "Batch update via Package Installer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = viewModel::updateAll,
                            enabled = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            modifier = Modifier.pressScaleEffect()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (connectionState.isConnected) "Update All" else "Update All (Installer)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isRefreshing && trackedApps.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        ShimmerCardPlaceholder(height = 140.dp)
                    }
                }
            } else if (trackedApps.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No GitHub Apps Tracked",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Installed open-source apps from GitHub (Streamflix, Hey Mike, YouTube Downloader, etc.) will appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                GradientButton(
                    text = "Add Custom GitHub Repo",
                    onClick = {
                        viewModel.loadInstalledApps()
                        showAddDialog = true
                    },
                    icon = Icons.Default.Add,
                    gradient = AppGradients.primary,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(trackedApps, key = { it.packageName }) { app ->
                        TrackedAppItem(
                            app = app,
                            isAdbConnected = connectionState.isConnected,
                            onUpdate = { viewModel.updateApp(app) },
                            onEdit = { editingApp = app },
                            onRemove = { viewModel.removeTrackedApp(app) },
                            onRetry = { viewModel.checkSingleApp(app) }
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
    onRemove: () -> Unit,
    onRetry: () -> Unit = {}
) {
    GlassCard(
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Authentic App Icon with stylized initial letter fallback
                AppIconImage(
                    packageName = app.packageName,
                    modifier = Modifier.size(46.dp),
                    contentFallback = {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(PrimaryPurple.copy(alpha = 0.8f), SecondaryCyan.copy(alpha = 0.6f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.appName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .pressScaleEffect()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Repository",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .pressScaleEffect()
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Versions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Installed: ${app.installedVersionName.ifBlank { "v${app.installedVersionCode}" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                when (val status = app.status) {
                    is UpdateStatus.UpdateAvailable -> {
                        Surface(
                            color = PrimaryPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "NEW: ${status.release.cleanVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = SecondaryCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    is UpdateStatus.UpToDate -> {
                        Surface(
                            color = StatusConnected.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StatusConnected.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusConnected,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "UP TO DATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusConnected
                                )
                            }
                        }
                    }
                    is UpdateStatus.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SecondaryCyan
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
                            color = StatusConnected,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is UpdateStatus.Error -> {
                        val isUpdateFailure = status.message.contains("download", ignoreCase = true) ||
                                status.message.contains("install", ignoreCase = true) ||
                                status.message.contains("space", ignoreCase = true)
                        Text(
                            text = if (isUpdateFailure) "Update failed" else "Check failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {}
                }
            }

            // Action / Progress bar
            when (val status = app.status) {
                is UpdateStatus.UpdateAvailable -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    GradientButton(
                        text = if (isAdbConnected) "Update via ADB" else "Update (Package Installer)",
                        onClick = onUpdate,
                        enabled = true,
                        icon = Icons.Default.SystemUpdate,
                        gradient = AppGradients.purpleToPink
                    )
                }
                is UpdateStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SecondaryCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading update: ${(status.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateStatus.Installing -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SecondaryCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is UpdateStatus.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleEffect()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Repo")
                        }
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
        shape = RoundedCornerShape(22.dp),
        title = { Text("Edit GitHub Repository", fontWeight = FontWeight.Bold) },
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
                    shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(12.dp),
                enabled = repoInput.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(22.dp),
        title = { Text("Track GitHub App", fontWeight = FontWeight.Bold) },
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
                    shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        shape = RoundedCornerShape(14.dp),
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
                                            color = SecondaryCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Known",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SecondaryCyan,
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
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    label = { Text("Package Name (optional)") },
                    placeholder = { Text("e.g. com.streamflix") },
                    shape = RoundedCornerShape(14.dp),
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
                shape = RoundedCornerShape(12.dp),
                enabled = repoInput.isNotBlank()
            ) {
                Text("Track", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
