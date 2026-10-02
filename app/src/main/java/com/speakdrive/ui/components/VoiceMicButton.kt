package com.speakdrive.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class MicState {
    /** Connecting or reconnecting. */
    BUSY,

    /** The lesson is running and the learner can talk. */
    LISTENING,

    /** The AI is talking (the learner can still interrupt). */
    AI_SPEAKING,

    /** Paused; tap to continue. */
    PAUSED
}

/** Large, glanceable control: tap to pause or continue the lesson. */
@Composable
fun VoiceMicButton(
    state: MicState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = if (state == MicState.LISTENING || state == MicState.AI_SPEAKING) pulse else 1f
    val color = when (state) {
        MicState.LISTENING -> MaterialTheme.colorScheme.primary
        MicState.AI_SPEAKING -> MaterialTheme.colorScheme.tertiary
        MicState.PAUSED -> MaterialTheme.colorScheme.secondary
        MicState.BUSY -> MaterialTheme.colorScheme.outline
    }
    val (icon, label) = when (state) {
        MicState.LISTENING -> Icons.Filled.Mic to "Tạm dừng bài học"
        MicState.AI_SPEAKING -> Icons.Filled.GraphicEq to "Tạm dừng bài học"
        MicState.PAUSED -> Icons.Filled.PlayArrow to "Tiếp tục bài học"
        MicState.BUSY -> Icons.Filled.Sync to "Đang kết nối"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(112.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = state != MicState.BUSY, role = Role.Button, onClickLabel = label, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(52.dp))
    }
}
