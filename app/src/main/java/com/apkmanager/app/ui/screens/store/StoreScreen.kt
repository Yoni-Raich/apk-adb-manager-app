package com.apkmanager.app.ui.screens.store

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkmanager.app.data.store.StoreAppItem
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.ui.components.AppIcon
import com.apkmanager.app.ui.components.AppListRow
import com.apkmanager.app.ui.components.EmptyState
import com.apkmanager.app.ui.components.InstallButton
import com.apkmanager.app.ui.components.ProgressAppIcon
import com.apkmanager.app.ui.components.SearchPill
import com.apkmanager.app.ui.components.SecondaryPillButton
import com.apkmanager.app.ui.components.isBusy
import com.apkmanager.app.ui.components.progressText
import com.apkmanager.app.ui.components.rememberAdbGate

@Composable
fun StoreScreen(
    viewModel: StoreViewModel,
    adbRepository: AdbRepository,
    onOpenWireless: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val connection by adbRepository.connectionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val requireAdb = rememberAdbGate(connection.isConnected, snackbarHostState, onOpenWireless)
    val featured = if (query.isBlank() && selectedCategory == null) items.firstOrNull { !it.isInstalled } else null

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchPill(query, viewModel::updateSearchQuery, "Search the Store", Modifier.weight(1f))
                    IconButton(onClick = { viewModel.loadCatalog(forceRefresh = true) }, enabled = !isRefreshing) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh catalog")
                    }
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { CategoryChip("All", selectedCategory == null) { viewModel.selectCategory(null) } }
                    items(categories, key = { it }) { cat ->
                        CategoryChip(cat, selectedCategory == cat) { viewModel.selectCategory(cat) }
                    }
                }
            }

            if (featured != null) {
                item {
                    FeaturedCard(
                        item = featured,
                        onInstall = { requireAdb { viewModel.installOrUpdate(featured) } },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
            }

            item {
                Text(
                    if (query.isBlank()) "All apps" else "Results",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (items.isEmpty() && !isRefreshing) {
                item {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No apps found",
                        body = "Try another search or category."
                    )
                }
            }

            items(items, key = { it.app.id }) { item ->
                StoreRow(
                    item = item,
                    onInstall = { requireAdb { viewModel.installOrUpdate(item) } },
                    onOpen = { openApp(context, item.installedPackage) }
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
private fun StoreRow(item: StoreAppItem, onInstall: () -> Unit, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = item.status
    val owner = item.app.githubRepo.substringBefore('/')
    val supporting = status.progressText()?.takeIf { status !is UpdateStatus.Checking }
        ?: when {
            item.isUpdateAvailable -> "Update: ${item.installedVersionName.orEmpty()} → ${item.latestRelease?.cleanVersion.orEmpty()}"
            item.isInstalled -> "Installed · ${item.installedVersionName.orEmpty()}"
            else -> null
        }
    AppListRow(
        title = item.app.name,
        subtitle = "${item.app.category} · $owner",
        supporting = supporting,
        supportingColor = when {
            status is UpdateStatus.Error -> scheme.error
            item.isUpdateAvailable || status.isBusy -> scheme.primary
            else -> scheme.onSurfaceVariant
        },
        icon = {
            ProgressAppIcon(status = status, size = 64.dp) {
                AppIcon(size = 64.dp, packageName = item.installedPackage, iconKey = item.app.icon, category = item.app.category)
            }
        },
        trailing = { StoreAction(item, onInstall, onOpen) }
    )
}

@Composable
private fun StoreAction(item: StoreAppItem, onInstall: () -> Unit, onOpen: () -> Unit) {
    when {
        item.status.isBusy -> Unit
        item.status is UpdateStatus.Error -> InstallButton("Retry", onClick = onInstall, enabled = item.latestAsset != null)
        item.isUpdateAvailable -> InstallButton("Update", onClick = onInstall, enabled = item.latestAsset != null)
        item.isInstalled -> SecondaryPillButton("Open", onClick = onOpen)
        else -> InstallButton("Install", onClick = onInstall, enabled = item.latestAsset != null)
    }
}

@Composable
private fun FeaturedCard(item: StoreAppItem, onInstall: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("FEATURED", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressAppIcon(status = item.status, size = 72.dp) {
                    AppIcon(size = 72.dp, packageName = item.installedPackage, iconKey = item.app.icon, category = item.app.category)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.app.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.app.githubRepo, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Text(item.app.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            if (!item.status.isBusy) {
                InstallButton("Install", onClick = onInstall, enabled = item.latestAsset != null, modifier = Modifier.fillMaxWidth())
            } else {
                Text(item.status.progressText().orEmpty(), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
        }
    }
}

private fun openApp(context: Context, packageName: String?) {
    val intent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return
    runCatching { context.startActivity(intent) }
}
