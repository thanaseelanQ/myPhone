package com.drosocode.myphone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Message
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.drosocode.myphone.util.CallUtils
import com.drosocode.myphone.data.ContactRepository
import com.drosocode.myphone.data.model.Contact


import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onNavigateToSms: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { ContactRepository(context) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        try {
            contacts = repository.fetchContacts()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredContacts = remember(searchQuery, contacts) {
        contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phoneNumber.contains(searchQuery)
        }.sortedBy { it.name }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                // Reduced top and bottom padding for the title
                Text(
                    text = "Contacts",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 0.dp)
                )
                // Adjusted SearchBar padding to move it closer to the title
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    active = false,
                    onActiveChange = { },
                    placeholder = { Text("Search contacts") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 0.dp)
                ) { }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(filteredContacts, key = { it.id + "_" + it.phoneNumber }) { contact ->
                ContactItem(
                    contact = contact,
                    onCall = { CallUtils.makeCall(context, it) },
                    onClick = { editingContact = contact },
                    onNavigateToSms = onNavigateToSms
                )
            }
        }
    }

    if (editingContact != null) {
        ContactEditDialog(
            contact = editingContact!!,
            onDismiss = { editingContact = null },
            onSave = { name, number ->
                if (repository.updateContact(editingContact!!.id, name, number)) {
                    try { contacts = repository.fetchContacts() } catch (e: Exception) {}
                }
                editingContact = null
            },
            onDelete = {
                if (repository.deleteContact(editingContact!!.id)) {
                    try { contacts = repository.fetchContacts() } catch (e: Exception) {}
                }
                editingContact = null
            }
        )
    }

    if (showAddDialog) {
        ContactAddDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, number ->
                if (repository.addContact(name, number)) {
                    try { contacts = repository.fetchContacts() } catch (e: Exception) {}
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onCall: (String) -> Unit,
    onClick: () -> Unit,
    onNavigateToSms: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(contact.name) },
        supportingContent = { Text(contact.phoneNumber) },
        leadingContent = {
            val avatarColors = listOf(
                Color(0xFF1ABC9C), Color(0xFF2ECC71), Color(0xFF3498DB), Color(0xFF9B59B6),
                Color(0xFF34495E), Color(0xFF16A085), Color(0xFF27AE60), Color(0xFF2980B9),
                Color(0xFF8E44AD), Color(0xFF2C3E50), Color(0xFFF1C40F), Color(0xFFE67E22),
                Color(0xFFE74C3C), Color(0xFFF39C12), Color(0xFFD35400), Color(0xFFC0392B)
            )
            val color = remember(contact.name) { 
                avatarColors[Math.abs(contact.name.hashCode()) % avatarColors.size] 
            }

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = color
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNavigateToSms(contact.phoneNumber) }) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = "Message",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onCall(contact.phoneNumber) }) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun ContactEditDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var number by remember { mutableStateOf(contact.phoneNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, number) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ContactAddDialog(
    initialNumber: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(initialNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && number.isNotBlank()) onSave(name, number) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
