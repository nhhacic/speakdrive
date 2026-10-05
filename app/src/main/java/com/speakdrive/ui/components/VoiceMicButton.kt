package com.speakdrive.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.speakdrive.R
import com.speakdrive.ui.theme.AppColors

enum class MicState {
    /** Connecting or reconnecting to the AI live session. */
    BUSY,

    /** The lesson is running and AI is waiting for the learner to speak. */
    LISTENING,

    /** The learner is actively speaking into the microphone. */
    USER_SPEAKING,

    /** The AI is processing/thinking before responding. */
    AI_THINKING,

    /** The AI is talking (the learner can still interrupt). */
    AI_SPEAKING,

    /** Paused; tap to continue. */
    PAUSED
}

/**
 * Living Voice Orb: Nút điều khiển âm thanh AI đa tầng phát sáng,
 * trực quan hóa sinh động các trạng thái: AI đang nói, AI đang đợi học viên nói,
 * AI đang suy nghĩ, học viên đang nói, và tạm dừng hội thoại.
 */
@Composable
fun VoiceMicButton(
    state: MicState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice_orb")

    val pulseDuration = when (state) {
        MicState.USER_SPEAKING -> 750
        MicState.AI_SPEAKING -> 900
        MicState.AI_THINKING -> 1100
        MicState.LISTENING -> 1600
        else -> 1400
    }

    val targetOuterScale = when (state) {
        MicState.AI_SPEAKING -> 1.30f
        MicState.USER_SPEAKING -> 1.25f
        MicState.AI_THINKING -> 1.20f
        MicState.LISTENING -> 1.15f
        else -> 1.0f
    }

    // Pulse scale for outer ripple
    val outerScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = targetOuterScale,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_pulse"
    )

    // Outer alpha fading
    val outerAlpha by transition.animateFloat(
        initialValue = when (state) {
            MicState.USER_SPEAKING -> 0.40f
            MicState.AI_THINKING -> 0.35f
            MicState.AI_SPEAKING -> 0.35f
            MicState.LISTENING -> 0.30f
            else -> 0.0f
        },
        targetValue = when (state) {
            MicState.USER_SPEAKING -> 0.10f
            MicState.AI_THINKING -> 0.10f
            MicState.AI_SPEAKING -> 0.08f
            MicState.LISTENING -> 0.06f
            else -> 0.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_alpha"
    )

    // Continuous rotation for busy and AI speaking aura
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Faster thinking shimmer ring rotation
    val thinkingRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_rotation"
    )

    // Thinking icon pulse
    val thinkingPulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pulse"
    )

    val (icon, label) = when (state) {
        MicState.LISTENING -> Icons.Filled.Mic to stringResource(R.string.mic_state_listening)
        MicState.USER_SPEAKING -> Icons.Filled.Mic to stringResource(R.string.mic_state_user_speaking)
        MicState.AI_THINKING -> Icons.Filled.AutoAwesome to stringResource(R.string.mic_state_ai_thinking)
        MicState.AI_SPEAKING -> Icons.Filled.GraphicEq to stringResource(R.string.mic_state_ai_speaking)
        MicState.PAUSED -> Icons.Filled.PlayArrow to stringResource(R.string.mic_state_resume)
        MicState.BUSY -> Icons.Filled.Sync to stringResource(R.string.mic_state_busy)
    }

    val coreBrush = when (state) {
        MicState.LISTENING -> Brush.linearGradient(
            listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))
        )
        MicState.USER_SPEAKING -> Brush.linearGradient(
            listOf(Color(0xFF0F766E), Color(0xFF10B981))
        )
        MicState.AI_THINKING -> Brush.linearGradient(
            listOf(Color(0xFF6D28D9), Color(0xFF9333EA))
        )
        MicState.AI_SPEAKING -> Brush.linearGradient(
            listOf(AppColors.AiViolet, AppColors.AiCyan)
        )
        MicState.PAUSED -> Brush.linearGradient(
            listOf(Color(0xFFD97706), Color(0xFFF59E0B))
        )
        MicState.BUSY -> Brush.linearGradient(
            listOf(Color(0xFF475569), Color(0xFF64748B))
        )
    }

    val haloColor = when (state) {
        MicState.LISTENING -> Color(0xFF3B82F6)
        MicState.USER_SPEAKING -> Color(0xFF10B981)
        MicState.AI_THINKING -> Color(0xFFA855F7)
        MicState.AI_SPEAKING -> AppColors.AiViolet
        MicState.PAUSED -> Color(0xFFF59E0B)
        MicState.BUSY -> Color(0xFF94A3B8)
    }

    val thinkingAuraBrush = Brush.sweepGradient(
        listOf(
            Color.Transparent,
            Color(0xFF8B5CF6),
            Color(0xFFC084FC),
            Color(0xFFF472B6),
            Color(0xFFFBBF24),
            Color.Transparent
        )
    )

    val borderColor = when (state) {
        MicState.PAUSED -> Color(0xFFFDE68A).copy(alpha = 0.7f)
        MicState.AI_THINKING -> Color(0xFFE9D5FF).copy(alpha = 0.6f)
        MicState.USER_SPEAKING -> Color(0xFFA7F3D0).copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(108.dp)
    ) {
        // Layer 1: Ambient pulsating glow / ripple halo
        if (state == MicState.LISTENING || state == MicState.USER_SPEAKING ||
            state == MicState.AI_THINKING || state == MicState.AI_SPEAKING
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(outerScale)
                    .clip(CircleShape)
                    .background(haloColor.copy(alpha = outerAlpha))
            )
        }

        // Layer 2: Rotating gradient sweep for AI speaking & AI thinking
        if (state == MicState.AI_SPEAKING) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .rotate(rotation)
                    .clip(CircleShape)
                    .background(AppColors.LiveAuraBrush)
            )
        } else if (state == MicState.AI_THINKING) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .rotate(thinkingRotation)
                    .clip(CircleShape)
                    .background(thinkingAuraBrush)
            )
        }

        // Layer 3: Tactile Core Orb Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(coreBrush)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .clickable(
                    enabled = state != MicState.BUSY,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick
                )
        ) {
            val iconModifier = Modifier
                .size(36.dp)
                .then(
                    when (state) {
                        MicState.BUSY -> Modifier.rotate(rotation)
                        MicState.AI_THINKING -> Modifier.scale(thinkingPulse)
                        else -> Modifier
                    }
                )

            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = iconModifier
            )
        }
    }
}
