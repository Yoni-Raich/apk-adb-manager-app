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
import com.apkmanager.app.ui.components.GooglePlayManageHeader
import com.apkmanager.app.ui.components.GooglePlaySearchBar
import com.apkmanager.app.ui.components.GooglePlaySectionHeader
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Signature Google Play Search Bar Capsule with back button and actions
            GooglePlaySearchBar(
                placeholder = "Manage apps & updates",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.loadInstalledApps()
                                showAddDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add custom repository",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = viewModel::checkForUpdates,
                            enabled = !isRefreshing,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Check for updates",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Google Play "Manage apps & device" Header Banner
            GooglePlayManageHeader(
                updatableCount = updatableCount,
                isRefreshing = isRefreshing,
                onUpdateAllClick = viewModel::updateAll,
                onCheckUpdatesClick = viewModel::checkForUpdates
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Connection Status
            ConnectionStatusBar(
                connectionState = connectionState,
                onVerifyClick = viewModel::verifyConnection
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isRefreshing && trackedApps.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        ShimmerCardPlaceholder(height = 90.dp, shape = RoundedCornerShape(18.dp))
                    }
                }
            } else if (trackedApps.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No GitHub Apps Tracked",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Installed open-source apps from GitHub will appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.loadInstalledApps()
                        showAddDialog = true
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Track Custom Repository", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                val pendingApps = remember(trackedApps) {
                    trackedApps.filter { it.status is UpdateStatus.UpdateAvailable || it.status is UpdateStatus.Downloading || it.status is UpdateStatus.Installing }
                }
                val otherApps = remember(trackedApps) {
                    trackedApps.filter { it !in pendingApps }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    if (pendingApps.isNotEmpty()) {
                        item {
                            GooglePlaySectionHeader(
                                title = "Updates pending",
                                subtitle = "${pendingApps.size} apps ready to update"
                            )
                        }
                        items(pendingApps, key = { it.packageName }) { app ->
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

                    if (otherApps.isNotEmpty()) {
                        item {
                            GooglePlaySectionHeader(
                                title = "Up to date",
                                subtitle = "${otherApps.size} apps verified"
                            )
                        }
                        items(otherApps, key = { it.packageName }) { app ->
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
    var isExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 54dp Squircle App Icon
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconImage(
                            packageName = app.packageName,
                            modifier = Modifier.size(46.dp),
                            contentFallback = {
                                Text(
                                    text = app.appName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Version Information
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    when (val status = app.status) {
                        is UpdateStatus.UpdateAvailable -> {
                            Text(
                                text = "v${app.installedVersionName} → ${status.release.cleanVersion}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        is UpdateStatus.Downloading -> {
                            Text(
                                text = "Downloading update...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is UpdateStatus.Installing -> {
                            Text(
                                text = "Installing update...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is UpdateStatus.Error -> {
                            Text(
                                text = "Update check failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            Text(
                                text = "v${app.installedVersionName.ifBlank { "v${app.installedVersionCode}" }} • Up to date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = app.githubRepo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Google Play Action Pill or Progress
                when (val status = app.status) {
                    is UpdateStatus.UpdateAvailable -> {
                        Button(
                            onClick = onUpdate,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Update",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(76.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is UpdateStatus.Installing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is UpdateStatus.Error -> {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Expandable details (What's new, repo management)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Release notes if available
                    val updateStatus = app.status
                    if (updateStatus is UpdateStatus.UpdateAvailable && updateStatus.release.body.isNotBlank()) {
                        Text(
                            text = "What's new (${updateStatus.release.cleanVersion}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateStatus.release.body.take(300).trim() + if (updateStatus.release.body.length > 300) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Action buttons (Edit repo, remove, check single)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = onEdit,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onRemove,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
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
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Known",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
