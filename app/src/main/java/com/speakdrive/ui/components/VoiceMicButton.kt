package com.speakdrive.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class MicState {
    IDLE, LISTENING, AI_SPEAKING
}

@Composable
fun VoiceMicButton(
    state: MicState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == MicState.LISTENING) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when (state) {
                    MicState.IDLE -> MaterialTheme.colorScheme.primary
                    MicState.LISTENING -> MaterialTheme.colorScheme.error
                    MicState.AI_SPEAKING -> MaterialTheme.colorScheme.secondary
                }
            )
            .clickable(onClick = onClick)
    ) {
        if (state == MicState.AI_SPEAKING) {
            // Placeholder for waveform
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
