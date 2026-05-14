package com.drosocode.myphone.ui.screens

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import com.drosocode.myphone.service.CallService
import com.drosocode.myphone.data.ContactRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InCallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val call = CallService.currentCall
    val repository = remember { ContactRepository(context) }
    
    var callerName by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    val callState = CallService.currentCallState
    val duration = CallService.callDuration
    val isActive = callState == Call.STATE_ACTIVE
    val isRinging = callState == Call.STATE_RINGING
    val isOutgoing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    fun normalize(num: String) = num.filter { it.isDigit() }.takeLast(10)

    LaunchedEffect(call) {
        val number = call?.details?.handle?.schemeSpecificPart
        if (number != null) {
            val normalizedTarget = normalize(number)
            val contacts = repository.fetchContacts()
            callerName = contacts.find { normalize(it.phoneNumber) == normalizedTarget }?.name
        }
    }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED || (call == null && callState == null)) {
            onBack()
        }
    }

    LaunchedEffect(CallService.currentAudioState) {
        val audioState = CallService.currentAudioState
        isMuted = audioState?.isMuted ?: false
        isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER
    }

    val backgroundBrush = remember(isOutgoing) {
        if (isOutgoing) {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1A237E).copy(alpha = 0.2f), Color(0xFF121212))
            )
        } else {
            Brush.verticalGradient(colors = listOf(Color(0xFF121212), Color(0xFF121212)))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                val name = callerName ?: call?.details?.handle?.schemeSpecificPart ?: "Unknown"
                
                val pulseScale = remember { Animatable(1f) }
                LaunchedEffect(isOutgoing, isRinging) {
                    if (isOutgoing || isRinging) {
                        pulseScale.animateTo(
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                    } else {
                        pulseScale.animateTo(1f)
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer { 
                            scaleX = pulseScale.value
                            scaleY = pulseScale.value
                        },
                    shape = CircleShape,
                    color = remember(name) { getAvatarColor(name) },
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                
                Text(
                    text = if (isActive) formatDuration(duration) else callStateToString(callState),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Middle/Bottom Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRinging) {
                    IncomingCallSwipeButton(
                        onAnswer = { call?.answer(VideoProfile.STATE_AUDIO_ONLY) },
                        onReject = { call?.reject(false, null) }
                    )
                } else {
                    val controls = listOf(
                        Triple(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", isMuted),
                        Triple(Icons.Default.Dialpad, "Keypad", false),
                        Triple(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown, "Speaker", isSpeakerOn),
                        Triple(Icons.Default.Add, "Add call", false),
                        Triple(Icons.Default.Pause, "Hold", false),
                        Triple(Icons.Default.Videocam, "Video", false)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        controls.chunked(3).forEach { rowControls ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowControls.forEach { (icon, label, isActiveControl) ->
                                    CallControlButton(
                                        icon = icon,
                                        label = label,
                                        isActive = isActiveControl,
                                        onClick = {
                                            when (label) {
                                                "Mute" -> CallService.mute(!isMuted)
                                                "Speaker" -> CallService.toggleSpeaker(!isSpeakerOn)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    if (callState != Call.STATE_DISCONNECTED && call != null) {
                        FloatingActionButton(
                            onClick = { 
                                call.disconnect()
                            },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
        
        if (!isRinging) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
    }
}

@Composable
fun IncomingCallSwipeButton(onAnswer: () -> Unit, onReject: () -> Unit) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // Limits for the button to stay inside the path
    // Track height is 300.dp * 0.8 = 240.dp
    // Button height is 80.dp
    // Max offset = (240 - 80) / 2 = 80.dp
    val maxOffset = 80f
    
    val arrowAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arrowAnim.animateTo(
            targetValue = 15f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(100.dp, 300.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value <= -maxOffset + 10f) {
                                    onAnswer()
                                } else if (offsetY.value >= maxOffset - 10f) {
                                    onReject()
                                }
                                offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetY.value + dragAmount).coerceIn(-maxOffset, maxOffset)
                                offsetY.snapTo(newOffset)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Track path
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .width(70.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE57373))),
                        shape = RoundedCornerShape(35.dp)
                    )
                    .alpha(0.12f)
            )

            // The Swipe Button
            Surface(
                modifier = Modifier
                    .offset(y = offsetY.value.dp)
                    .size(80.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val iconColor = when {
                        offsetY.value < -20f -> Color(0xFF4CAF50)
                        offsetY.value > 20f -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(36.dp), tint = iconColor)
                }
            }

            // Directional Arrows
            Column(
                modifier = Modifier.fillMaxHeight(0.7f),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp, 
                    contentDescription = null,
                    modifier = Modifier.offset(y = (arrowAnim.value).dp).size(32.dp),
                    tint = Color(0xFF4CAF50).copy(alpha = 0.6f)
                )
                Icon(
                    Icons.Default.KeyboardArrowDown, 
                    contentDescription = null,
                    modifier = Modifier.offset(y = (-arrowAnim.value).dp).size(32.dp),
                    tint = Color(0xFFF44336).copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when {
                offsetY.value < -40f -> "Release to Answer"
                offsetY.value > 40f -> "Release to Decline"
                else -> "Swipe Up to Answer\nSwipe Down to Decline"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

private fun getAvatarColor(name: String): Color {
    val avatarColors = listOf(
        Color(0xFF1ABC9C), Color(0xFF2ECC71), Color(0xFF3498DB), Color(0xFF9B59B6),
        Color(0xFF34495E), Color(0xFF16A085), Color(0xFF27AE60), Color(0xFF2980B9),
        Color(0xFF8E44AD), Color(0xFF2C3E50), Color(0xFFF1C40F), Color(0xFFE67E22),
        Color(0xFFE74C3C), Color(0xFFF39C12), Color(0xFFD35400), Color(0xFFC0392B)
    )
    return avatarColors[Math.abs(name.hashCode()) % avatarColors.size]
}

private fun callStateToString(state: Int?): String {
    return when (state) {
        Call.STATE_ACTIVE -> "Active"
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING -> "Dialing"
        Call.STATE_CONNECTING -> "Connecting"
        Call.STATE_DISCONNECTED -> "Disconnected"
        Call.STATE_DISCONNECTING -> "Disconnecting"
        else -> "Calling..."
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
