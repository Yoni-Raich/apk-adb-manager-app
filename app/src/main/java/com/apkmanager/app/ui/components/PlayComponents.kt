package com.apkmanager.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.util.AppIconImage

/** Material icon for a Store catalog entry, used when the real app icon is unavailable. */
fun categoryIcon(iconKey: String?, category: String?): ImageVector = when (iconKey?.lowercase()) {
    "video", "play" -> Icons.Default.PlayArrow
    "movie", "film" -> Icons.Default.Movie
    "smart_toy", "ai", "bot" -> Icons.Default.SmartToy
    "download" -> Icons.Default.Download
    "code", "dev" -> Icons.Default.Code
    "game", "games" -> Icons.Default.SportsEsports
    else -> when (category?.lowercase()) {
        "media" -> Icons.Default.PlayArrow
        "entertainment" -> Icons.Default.Movie
        "productivity" -> Icons.Default.SmartToy
        "games" -> Icons.Default.SportsEsports
        else -> Icons.Default.Apps
    }
}

@Composable
private fun categoryColors(category: String?): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (category?.lowercase()) {
        "media" -> scheme.errorContainer to scheme.onErrorContainer
        "productivity" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "games" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
}

/**
 * The app's real launcher icon when [packageName] is installed (or [uri] is an APK),
 * otherwise a tonal tile with the category's Material icon.
 */
@Composable
fun AppIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    uri: android.net.Uri? = null,
    iconKey: String? = null,
    category: String? = null
) {
    val shape = RoundedCornerShape(size * 0.25f)
    val (container, content) = categoryColors(category)
    AppIconImage(
        packageName = packageName,
        uri = uri,
        shape = shape,
        modifier = modifier.size(size),
        contentFallback = {
            Box(
                modifier = modifier
                    .size(size)
                    .background(container, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon(iconKey, category), contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
            }
        }
    )
}

/** Rounded search field in the Play style. */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

/** Section title with an optional subtitle and a trailing arrow when clickable. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Filled green pill for Install / Update. */
@Composable
fun InstallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        )
    ) { Text(text) }
}

/** Outlined pill for Open / secondary actions. */
@Composable
fun SecondaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) { Text(text) }
}

/** Standard app row: icon, title, supporting lines, trailing action. */
@Composable
fun AppListRow(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.labelMedium, color = supportingColor, maxLines = 2)
            }
        }
        trailing?.invoke()
    }
}

/** Round ADB status button for top bars: green dot when connected. */
@Composable
fun AdbStatusButton(state: ConnectionState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, dot) = when {
        state.isConnected -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
        state.isLoading -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, scheme.primary)
        else -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, scheme.error)
    }
    Box(Modifier.size(48.dp)) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Adb,
                        contentDescription = if (state.isConnected) "Wireless ADB connected" else "Wireless ADB not connected",
                        tint = content
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(12.dp)
                .background(scheme.surface, CircleShape)
                .padding(2.dp)
                .background(dot, CircleShape)
        )
    }
}

/** Tonal card with a leading round icon, used for status summaries. */
@Composable
fun StatusBanner(
    title: String,
    body: String,
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(iconContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

/** Centered empty / info state. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        action?.invoke()
    }
}
