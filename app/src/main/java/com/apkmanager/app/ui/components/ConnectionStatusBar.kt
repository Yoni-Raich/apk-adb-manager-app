package com.apkmanager.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.apkmanager.app.data.ConnectionState
import com.apkmanager.app.ui.theme.*

/**
 * A status bar that displays the current ADB connection state.
 * Shows a colored indicator dot and status text.
 */
@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = connectionState.statusText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
