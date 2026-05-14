package com.drosocode.myphone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drosocode.myphone.ui.screens.ContactsScreen
import com.drosocode.myphone.ui.screens.DialerScreen
import com.drosocode.myphone.ui.screens.HistoryScreen
import com.drosocode.myphone.ui.screens.InCallScreen
import com.drosocode.myphone.ui.screens.MessageListScreen
import com.drosocode.myphone.ui.screens.ConversationScreen
import android.provider.Telephony
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.drosocode.myphone.ui.theme.MyPhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Default: Do not show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
        }

        setContent {
            MyPhoneTheme {
                MainApp(this)
            }
        }
    }
}

@Composable
fun MainApp(activity: ComponentActivity) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    
    var hasPermissions by remember {
        mutableStateOf(
            listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
            ).all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        }
    }

    var isDefaultDialer by remember { mutableStateOf(telecomManager.defaultDialerPackage == context.packageName) }
    val defaultDialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
    }

    var isDefaultSmsApp by remember { mutableStateOf(checkIsDefaultSmsApp(context)) }
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSmsApp = checkIsDefaultSmsApp(context)
    }

    val currentCall = com.drosocode.myphone.service.CallService.currentCall
    val callActive = currentCall != null
    val callState = com.drosocode.myphone.service.CallService.currentCallState
    val isCallGoing = callActive && callState != android.telecom.Call.STATE_DISCONNECTED && callState != android.telecom.Call.STATE_DISCONNECTING
    
    var dialedNumber by remember { mutableStateOf("") }

    // Manage screen lock: Keep screen on and show over lock screen ONLY during call
    LaunchedEffect(callActive) {
        if (callActive) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(false)
                activity.setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    // Auto-navigate to call screen when call starts, and clear number when it ends
    LaunchedEffect(callActive) {
        if (callActive) {
            navController.navigate(Screen.InCall.route) {
                launchSingleTop = true
            }
        } else {
            // Clear the dialer when a call ends (transition from active to inactive)
            dialedNumber = ""
        }
    }

    // Handle incoming intents for deep linking
    LaunchedEffect(Unit) {
        val handleIntent: (Intent) -> Unit = { intent ->
            val dest = intent.getStringExtra("start_destination")
            if (dest != null) {
                intent.removeExtra("start_destination")
                navController.navigate(dest) {
                    launchSingleTop = true
                }
            }
        }
        handleIntent(activity.intent)
        
        val listener = androidx.core.util.Consumer<Intent> { intent -> 
            handleIntent(intent) 
        }
        activity.addOnNewIntentListener(listener)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!hasPermissions) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Permissions Required", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Scaffold(
                topBar = {
                    // Top bar is now empty or can be used for other things
                },
                bottomBar = {
                    Column {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        
                        if (isCallGoing && currentDestination?.route != Screen.InCall.route) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate(Screen.InCall.route) { launchSingleTop = true } },
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Return to active call", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        
                        if (!isDefaultDialer) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                                                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                                            }
                                            defaultDialerLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Set as default phone app",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        if (!isDefaultSmsApp) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                                                if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                                                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                                                    defaultSmsLauncher.launch(intent)
                                                }
                                            } else {
                                                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                                }
                                                defaultSmsLauncher.launch(intent)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            // Fallback to settings
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (ignored: Exception) {}
                                        }
                                    },
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Message,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Set as default SMS app",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            val items = listOf(
                                Screen.History,
                                Screen.Dialer,
                                Screen.Contacts,
                                Screen.Messages
                            )

                            items.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = null) },
                                    label = { Text(screen.label) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dialer.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.History.route) { HistoryScreen(
                        onNavigateToSms = { address -> 
                            val threadId = android.provider.Telephony.Threads.getOrCreateThreadId(context, address).toString()
                            navController.navigate("conversation/$threadId/$address")
                        }
                    ) }
                    composable(Screen.Dialer.route) { DialerScreen(
                        phoneNumber = dialedNumber,
                        onPhoneNumberChange = { dialedNumber = it },
                        onNavigateToSms = { address -> 
                            val threadId = Telephony.Threads.getOrCreateThreadId(context, address).toString()
                            navController.navigate("conversation/$threadId/$address")
                        }
                    ) }
                    composable(Screen.Contacts.route) { ContactsScreen(
                        onNavigateToSms = { address -> 
                            val threadId = Telephony.Threads.getOrCreateThreadId(context, address).toString()
                            navController.navigate("conversation/$threadId/$address")
                        }
                    ) }
                    composable(Screen.Messages.route) { MessageListScreen(
                        onConversationClick = { threadId, address ->
                            navController.navigate("conversation/$threadId/$address")
                        }
                    ) }
                    composable(Screen.InCall.route) {
                        InCallScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "conversation/{threadId}/{address}",
                        arguments = listOf(
                            navArgument("threadId") { type = NavType.StringType },
                            navArgument("address") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                        val address = backStackEntry.arguments?.getString("address") ?: ""
                        ConversationScreen(
                            threadId = threadId, 
                            address = address, 
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object History : Screen("history", "Recents", Icons.Default.History)
    object Dialer : Screen("dialer", "Keypad", Icons.Default.Dialpad)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.Person)
    object Messages : Screen("messages", "Messages", Icons.Default.Message)
    object InCall : Screen("in_call", "Call", Icons.Default.Call)
}

fun checkIsDefaultSmsApp(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
    } else {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}
