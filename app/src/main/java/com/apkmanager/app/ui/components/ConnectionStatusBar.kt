package com.apkmanager.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.ui.theme.*

/**
 * Modern Google Play Protect style status card:
 * Rounded M3 surface, icon badge, clear status text and action hint.
 */
@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
    onVerifyClick: (() -> Unit)? = null
) {
    val indicatorColor by animateColorAsState(
        targetValue = when (connectionState) {
            is ConnectionState.Connected -> StatusConnected
            is ConnectionState.Connecting, is ConnectionState.Pairing -> StatusConnecting
            is ConnectionState.Error -> StatusError
            is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 250),
        label = "status_color"
    )

    val (titleText, subtitleText, icon) = when (connectionState) {
        is ConnectionState.Connected -> Triple("Wireless ADB Connected", "Tap to check connection health", Icons.Default.CheckCircle)
        is ConnectionState.Connecting -> Triple("Connecting to Wireless ADB...", "Establishing handshake with adbd", Icons.Default.Sync)
        is ConnectionState.Pairing -> Triple("Pairing Wireless ADB...", "Exchanging TLS certificates", Icons.Default.Sync)
        is ConnectionState.Error -> Triple("ADB Disconnected", "Tap to reconnect", Icons.Default.Error)
        is ConnectionState.Disconnected -> Triple("Wireless ADB Offline", "Tap to connect or pair device", Icons.Default.WifiOff)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onVerifyClick != null) Modifier.clickable(onClick = onVerifyClick)
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = indicatorColor.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = indicatorColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onVerifyClick != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (connectionState.isConnected) "Verify" else "Connect",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
