package com.apkmanager.app.ui.screens.store

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmanager.app.data.store.StoreAppItem
import com.apkmanager.app.data.updater.UpdateStatus
import com.apkmanager.app.repository.AdbRepository
import com.apkmanager.app.repository.StoreRepository
import com.apkmanager.app.ui.animation.pressScaleEffect
import com.apkmanager.app.ui.components.ConnectionStatusBar
import com.apkmanager.app.ui.components.GooglePlayFilterChips
import com.apkmanager.app.ui.components.GooglePlaySearchBar
import com.apkmanager.app.ui.components.GooglePlaySectionHeader
import com.apkmanager.app.ui.components.ShimmerCardPlaceholder
import com.apkmanager.app.ui.theme.*
import com.apkmanager.app.util.AppIconImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    storeRepository: StoreRepository,
    adbRepository: AdbRepository,
    onNavigateBack: () -> Unit,
    viewModel: StoreViewModel = viewModel(
        factory = StoreViewModel.Factory(storeRepository, adbRepository)
    )
) {
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val connectionState by adbRepository.connectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val categoryChipList = remember(categories) {
        listOf("All") + categories
    }
    val currentChipSelection = selectedCategory ?: "All"

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

            // Google Play Signature Search Capsule with back button and refresh
            GooglePlaySearchBar(
                query = searchQuery,
                placeholder = "Search apps & repositories...",
                onQueryChange = viewModel::updateSearchQuery,
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
                    IconButton(
                        onClick = { viewModel.loadCatalog(forceRefresh = true) },
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
                                contentDescription = "Refresh catalog",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Google Play Category Filter Chips
            if (categories.isNotEmpty()) {
                GooglePlayFilterChips(
                    categories = categoryChipList,
                    selectedCategory = currentChipSelection,
                    onCategorySelected = { chip ->
                        viewModel.selectCategory(if (chip == "All") null else chip)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Google Play Protect Style ADB Status
            ConnectionStatusBar(
                connectionState = connectionState,
                onVerifyClick = viewModel::verifyConnection
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Google Play Section Header
            GooglePlaySectionHeader(
                title = if (selectedCategory == null) "Top Open-Source Charts" else selectedCategory!!,
                subtitle = "${items.size} curated open-source apps"
            )

            // Content: Loading, Empty, or Google Play List
            if (isRefreshing && items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        ShimmerCardPlaceholder(height = 90.dp, shape = RoundedCornerShape(16.dp))
                    }
                }
            } else if (items.isEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) "No matching apps found" else "No apps available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Try clearing your search query or refreshing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.app.id }) { index, item ->
                        StoreAppCard(
                            item = item,
                            rank = if (selectedCategory == null && searchQuery.isBlank()) index + 1 else null,
                            isAdbConnected = connectionState.isConnected,
                            onInstallOrUpdate = { viewModel.installOrUpdate(item) },
                            onOpenApp = {
                                val pkg = item.installedPackage ?: item.app.primaryPackage
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreAppCard(
    item: StoreAppItem,
    rank: Int? = null,
    isAdbConnected: Boolean,
    onInstallOrUpdate: () -> Unit,
    onOpenApp: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            // Main Google Play App Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank number if in top charts
                if (rank != null) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(26.dp)
                    )
                }

                // 56dp Squircle App Icon
                val pkgForIcon = item.installedPackage ?: item.app.packageNames.firstOrNull()
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
                            packageName = pkgForIcon,
                            modifier = Modifier.size(46.dp),
                            contentFallback = {
                                Icon(
                                    imageVector = getCategoryIcon(item.app.icon, item.app.category),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title and Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.app.category} • ${item.app.githubRepo.substringAfter('/')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isUpdateAvailable) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${item.installedVersionName} → ${item.latestRelease?.cleanVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (item.isInstalled) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Installed • v${item.installedVersionName ?: "latest"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00875E), // Google Play Green
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Google Play Action Button
                when (val status = item.status) {
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Installing",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is UpdateStatus.Error -> {
                        OutlinedButton(
                            onClick = onInstallOrUpdate,
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
                        if (!item.isInstalled) {
                            Button(
                                onClick = onInstallOrUpdate,
                                enabled = item.latestAsset != null,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Install",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else if (item.isUpdateAvailable) {
                            Button(
                                onClick = onInstallOrUpdate,
                                enabled = item.latestAsset != null,
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
                        } else {
                            FilledTonalButton(
                                onClick = onOpenApp,
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Expandable details: Description, GitHub Link, Reinstall & Alternative options
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

                    // App Description
                    Text(
                        text = item.app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Info & Links Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // GitHub repo link
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.clickable {
                                val url = "https://github.com/${item.app.githubRepo}"
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.app.githubRepo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Version info
                        Text(
                            text = if (item.isInstalled) {
                                "v${item.installedVersionName ?: "?"} (latest: ${item.latestRelease?.cleanVersion ?: "—"})"
                            } else {
                                "Latest: ${item.latestRelease?.cleanVersion ?: "—"}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Extra Actions for installed apps (Reinstall / Direct installer)
                    if (item.isInstalled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onInstallOrUpdate,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reinstall")
                            }

                            FilledTonalButton(
                                onClick = onOpenApp,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Launch")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(iconKey: String, category: String): ImageVector {
    return when (iconKey.lowercase()) {
        "video", "play" -> Icons.Default.PlayArrow
        "movie", "film" -> Icons.Default.Movie
        "smart_toy", "ai", "bot" -> Icons.Default.SmartToy
        "download" -> Icons.Default.Download
        "code", "dev" -> Icons.Default.Code
        else -> when (category.lowercase()) {
            "media" -> Icons.Default.PlayArrow
            "entertainment" -> Icons.Default.Movie
            "productivity" -> Icons.Default.SmartToy
            else -> Icons.Default.Apps
        }
    }
}
