package com.drosocode.myphone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drosocode.myphone.data.SmsRepository
import com.drosocode.myphone.data.model.Conversation
import com.drosocode.myphone.data.model.MessageCategory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageListScreen(onConversationClick: (String, String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    var conversations by remember { mutableStateOf(emptyList<Conversation>()) }
    var selectedCategory by remember { mutableStateOf(MessageCategory.ALL) } // Default to ALL
    var isLoading by remember { mutableStateOf(true) }
    var showNewMessageDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh(force: Boolean = false) {
        scope.launch {
            if (conversations.isEmpty()) isLoading = true
            conversations = repository.getConversations(force)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh(false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            repository.performCleanup()
                            refresh(true)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.AutoDelete, contentDescription = "Auto Cleanup")
                }
                
                FloatingActionButton(
                    onClick = { showNewMessageDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Message")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Category Selector
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MessageCategory.values()) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.name.lowercase().capitalize()) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Fix: Handle ALL filter correctly
                val filteredConversations = if (selectedCategory == MessageCategory.ALL) {
                    conversations
                } else {
                    conversations.filter { it.category == selectedCategory }
                }
                
                if (filteredConversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No messages in ${selectedCategory.name.lowercase()}", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredConversations) { conversation ->
                            ConversationItem(conversation) {
                                onConversationClick(conversation.threadId, conversation.address)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewMessageDialog) {
        NewMessageDialog(
            onDismiss = { showNewMessageDialog = false },
            onSend = { address, body ->
                repository.sendMessage(address, body)
                showNewMessageDialog = false
                refresh(true)
            }
        )
    }
}

@Composable
fun NewMessageDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Recipient") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (address.isNotBlank() && body.isNotBlank()) onSend(address, body) },
                enabled = address.isNotBlank() && body.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = getAvatarColor(conversation.contactName ?: conversation.address)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (conversation.contactName ?: conversation.address).take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (!conversation.read) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)
                ) {}
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (conversation.read) FontWeight.Normal else FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSmsDate(conversation.date),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (conversation.read) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (conversation.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = conversation.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = if (conversation.read) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatSmsDate(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val smsTime = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.DATE) == smsTime.get(Calendar.DATE) -> {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        }
        now.get(Calendar.DATE) - smsTime.get(Calendar.DATE) == 1 -> {
            "Yesterday"
        }
        now.get(Calendar.YEAR) == smsTime.get(Calendar.YEAR) -> {
            SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
        }
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
