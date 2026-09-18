package com.apkmanager.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.ui.animation.rememberPulseAnimation
import com.apkmanager.app.ui.theme.*

/**
 * A sleek status pill that displays the current ADB connection state.
 * Features an animated pulsing indicator ring, status glow, and clean typography.
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
            is ConnectionState.Disconnected -> StatusDisconnected
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_color"
    )

    val isPending = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Pairing
    val pulseScale = if (isPending) rememberPulseAnimation(min = 0.9f, max = 1.35f, durationMillis = 700) else 1f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = indicatorColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, indicatorColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onVerifyClick != null) Modifier.clickable(onClick = onVerifyClick)
                    else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow pulse ring
                if (isPending || connectionState is ConnectionState.Connected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(indicatorColor.copy(alpha = if (isPending) 0.35f else 0.2f))
                    )
                }

                // Core solid dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connectionState.statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onVerifyClick != null && !connectionState.isConnected) {
                    Text(
                        text = "Tap to verify / reconnect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Connection state badge
            Surface(
                color = indicatorColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "ONLINE"
                        is ConnectionState.Connecting -> "CONNECTING"
                        is ConnectionState.Pairing -> "PAIRING"
                        is ConnectionState.Error -> "ALERT"
                        is ConnectionState.Disconnected -> "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = indicatorColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
