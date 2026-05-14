package com.drosocode.myphone.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.drosocode.myphone.util.CallUtils

@Composable
fun DialerScreen(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onNavigateToSms: (String) -> Unit = {}
) {
    var showAddContactDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { com.drosocode.myphone.data.ContactRepository(context) }
    var contacts by remember { mutableStateOf(emptyList<com.drosocode.myphone.data.model.Contact>()) }

    LaunchedEffect(Unit) {
        contacts = repository.fetchContacts()
    }

    val matchedContact = remember(phoneNumber, contacts) {
        if (phoneNumber.length >= 3) {
            val normalizedInput = phoneNumber.filter { it.isDigit() }
            contacts.find { contact ->
                val normalizedContact = contact.phoneNumber.filter { it.isDigit() }
                normalizedContact.startsWith(normalizedInput) || normalizedContact.endsWith(normalizedInput)
            }
        } else null
    }

    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to ""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = phoneNumber,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            
            if (matchedContact != null) {
                Text(
                    text = matchedContact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (phoneNumber.isNotEmpty()) {
                Text(
                    text = "Add to contacts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showAddContactDialog = true }
                )
            }
        }

        // Keypad
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val chunkedKeys = keys.chunked(3)
            chunkedKeys.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowKeys.forEach { (num, label) ->
                        DialerKey(
                            number = num,
                            letters = label,
                            onClick = { onPhoneNumberChange(phoneNumber + it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Message Button (Left)
            if (phoneNumber.isNotEmpty()) {
                IconButton(
                    onClick = { onNavigateToSms(phoneNumber) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = "Message",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }

            // Primary Call Button (Center)
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        CallUtils.makeCall(context, phoneNumber)
                    }
                },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Backspace (Right)
            IconButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        onPhoneNumberChange(phoneNumber.dropLast(1))
                    }
                },
                modifier = Modifier.size(56.dp)
            ) {
                if (phoneNumber.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddContactDialog) {
        ContactAddDialog(
            initialNumber = phoneNumber,
            onDismiss = { showAddContactDialog = false },
            onSave = { name, number ->
                repository.addContact(name, number)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
fun DialerKey(
    number: String,
    letters: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(number) },
        modifier = modifier.aspectRatio(1.2f),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
