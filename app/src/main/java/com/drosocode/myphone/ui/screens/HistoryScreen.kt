package com.drosocode.myphone.ui.screens

import android.provider.CallLog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drosocode.myphone.data.CallLogRepository
import com.drosocode.myphone.data.model.CallLogEntry
import com.drosocode.myphone.util.CallUtils

data class MergedCallLogEntry(
    val primaryLog: CallLogEntry,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onNavigateToSms: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    var callLogs by remember { mutableStateOf(emptyList<CallLogEntry>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        callLogs = repository.fetchCallLogs()
        isLoading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Recent Calls", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (callLogs.isEmpty()) {
                EmptyHistoryView()
            } else {
                val mergedLogs = remember(callLogs) { groupCallLogs(callLogs, repository) }
                val groupedByDate = mergedLogs.groupBy { repository.getDateCategory(it.primaryLog.date) }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedByDate.forEach { (category, logs) ->
                        item {
                            CategoryHeader(category)
                        }
                        items(logs) { merged ->
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                PremiumCallLogItem(
                                    merged = merged, 
                                    onCallClick = { CallUtils.makeCall(context, merged.primaryLog.number) },
                                    onMessageClick = { onNavigateToSms(merged.primaryLog.number) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Your call history is empty",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun CategoryHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun PremiumCallLogItem(merged: MergedCallLogEntry, onCallClick: () -> Unit, onMessageClick: () -> Unit) {
    val log = merged.primaryLog
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    
    val typeIcon = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade
        CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed
        else -> Icons.Default.Call
    }
    
    val typeColor = when (log.type) {
        CallLog.Calls.MISSED_TYPE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCallClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-quality Avatar with gradient
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                getAvatarColor(log.name ?: log.number),
                                getAvatarColor(log.name ?: log.number).copy(alpha = 0.7f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (log.name ?: log.number).take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.name ?: log.number,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (log.type == CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (merged.count > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "×${merged.count}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = typeColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${repository.formatTime(log.date)} • ${repository.formatDuration(log.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Modern Integrated Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMessageClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Message,
                        contentDescription = "Message",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onCallClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = "Call",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun groupCallLogs(logs: List<CallLogEntry>, repository: CallLogRepository): List<MergedCallLogEntry> {
    val result = mutableListOf<MergedCallLogEntry>()
    if (logs.isEmpty()) return result
    
    var current = MergedCallLogEntry(logs[0], 1)
    for (i in 1 until logs.size) {
        val next = logs[i]
        val sameCategory = repository.getDateCategory(current.primaryLog.date) == repository.getDateCategory(next.date)
        if (next.number == current.primaryLog.number && sameCategory) {
            current = current.copy(count = current.count + 1)
        } else {
            result.add(current)
            current = MergedCallLogEntry(next, 1)
        }
    }
    result.add(current)
    return result
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
