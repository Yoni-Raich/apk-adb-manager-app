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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.ui.animation.rememberPulseAnimation
import com.apkmanager.app.ui.theme.*

/**
 * Minimalist ADB status pill: solid dot, muted tonal surface,
 * monospace state text, tap-to-verify. Shizuku / Linear style.
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
        animationSpec = tween(durationMillis = 250),
        label = "status_color"
    )

    val isPending = connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.Pairing
    val pulseScale = if (isPending) {
        rememberPulseAnimation(min = 1f, max = 1.6f, durationMillis = 900)
    } else 1f

    val stateLabel = when (connectionState) {
        is ConnectionState.Connected -> "CONNECTED"
        is ConnectionState.Connecting -> "CONNECTING"
        is ConnectionState.Pairing -> "PAIRING"
        is ConnectionState.Error -> "ERROR"
        is ConnectionState.Disconnected -> "DISCONNECTED"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onVerifyClick != null) Modifier.clickable(onClick = onVerifyClick)
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot with optional soft halo while pending
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPending) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(indicatorColor.copy(alpha = 0.22f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stateLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (connectionState.isConnected) "TAP TO VERIFY"
                else "TAP TO RETRY",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
