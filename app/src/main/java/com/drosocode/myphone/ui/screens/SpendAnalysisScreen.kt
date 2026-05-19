package com.drosocode.myphone.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drosocode.myphone.data.MonthlyAnalysis
import com.drosocode.myphone.data.SpendRepository
import com.drosocode.myphone.data.TransactionDetail
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendAnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val spendRepository = remember { SpendRepository(context) }
    
    var analyses by remember { mutableStateOf(emptyList<MonthlyAnalysis>()) }
    var selectedAnalysis by remember { mutableStateOf<MonthlyAnalysis?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            analyses = spendRepository.getSpendAnalytics()
            if (analyses.isNotEmpty()) {
                selectedAnalysis = analyses[0]
            }
            isLoading = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Spend Analysis",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                spendRepository.clearDeletedTransactions()
                                analyses = spendRepository.getSpendAnalytics()
                                if (analyses.isNotEmpty()) {
                                    selectedAnalysis = analyses.find { it.monthName == selectedAnalysis?.monthName } ?: analyses[0]
                                } else {
                                    selectedAnalysis = null
                                }
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-analyze"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (analyses.isEmpty()) {
                EmptySpendView()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Months Selector
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(analyses) { analysis ->
                            val isSelected = selectedAnalysis?.monthName == analysis.monthName
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            val borderAccent = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(containerColor)
                                    .border(BorderStroke(1.dp, borderAccent), RoundedCornerShape(20.dp))
                                    .clickable { selectedAnalysis = analysis }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = analysis.monthName,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    selectedAnalysis?.let { analysis ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Month Summary
                            item {
                                MonthSummaryCard(analysis)
                            }

                            // Category Breakdown
                            item {
                                CategoryBreakdownCard(analysis.transactions)
                            }

                            // Transaction Header
                            item {
                                SectionHeader(text = "Transactions")
                            }

                            // Transaction list
                            if (analysis.transactions.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No Transactions Found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(analysis.transactions) { transaction ->
                                    TransactionItem(
                                        transaction = transaction,
                                        onDelete = {
                                            scope.launch {
                                                spendRepository.deleteTransaction(transaction.id)
                                                // Trigger refresh after deletion
                                                analyses = spendRepository.getSpendAnalytics()
                                                if (analyses.isNotEmpty()) {
                                                    selectedAnalysis = analyses.find { it.monthName == selectedAnalysis?.monthName } ?: analyses[0]
                                                } else {
                                                    selectedAnalysis = null
                                                }
                                            }
                                        },
                                        onRestore = {
                                            scope.launch {
                                                spendRepository.restoreTransaction(transaction.id)
                                                // Trigger refresh after restore
                                                analyses = spendRepository.getSpendAnalytics()
                                                if (analyses.isNotEmpty()) {
                                                    selectedAnalysis = analyses.find { it.monthName == selectedAnalysis?.monthName } ?: analyses[0]
                                                } else {
                                                    selectedAnalysis = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySpendView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Wallet,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Transaction Data",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No financial transaction logs were identified in SMS buffers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun MonthSummaryCard(analysis: MonthlyAnalysis) {
    val netBalance = analysis.totalCredited - analysis.totalSpent
    val balanceColor = if (netBalance >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

    OutlinedCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Net Balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "₹%,.2f", netBalance),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = balanceColor
                )
                Surface(
                    color = balanceColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (netBalance >= 0) "Surplus" else "Deficit",
                        color = balanceColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Income and Expenses Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total Income
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Inflow (Income)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.2f", analysis.totalCredited),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Total Spend
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Outflow (Spends)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.2f", analysis.totalSpent),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownCard(transactions: List<TransactionDetail>) {
    val debits = transactions.filter { !it.isDeleted && !it.isCredit }
    val totalDebit = debits.sumOf { it.amount }
    
    if (totalDebit <= 0.0) return

    val categoryGroups = debits.groupBy { it.category }
    val sortedCategories = categoryGroups.map { (cat, txs) ->
        val sum = txs.sumOf { it.amount }
        val percentage = (sum / totalDebit).toFloat()
        Triple(cat, sum, percentage)
    }.sortedByDescending { it.second }

    OutlinedCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Expense Distribution",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            sortedCategories.forEach { (cat, sum, pct) ->
                val progressColor = when (cat) {
                    "Food & Dining" -> Color(0xFFFBC02D)
                    "Shopping" -> Color(0xFFC2185B)
                    "Bills & Utilities" -> Color(0xFF0288D1)
                    "Travel & Fuel" -> Color(0xFFF57C00)
                    "Investment" -> Color(0xFF388E3C)
                    else -> MaterialTheme.colorScheme.secondary
                }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "₹%,.2f (%.1f%%)", sum, pct * 100),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = progressColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: TransactionDetail,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val amountSign = if (transaction.isCredit) "+" else "-"

    val categoryIcon = when (transaction.category) {
        "Salary", "Income" -> Icons.Default.AttachMoney
        "Investment" -> Icons.Default.TrendingUp
        "Food & Dining" -> Icons.Default.Restaurant
        "Shopping" -> Icons.Default.ShoppingCart
        "Bills & Utilities" -> Icons.Default.Receipt
        "Travel & Fuel" -> Icons.Default.DirectionsCar
        else -> Icons.Default.Payment
    }

    val iconColor = when (transaction.category) {
        "Salary", "Income" -> Color(0xFF388E3C)
        "Investment" -> Color(0xFF388E3C)
        "Food & Dining" -> Color(0xFFF57C00)
        "Shopping" -> Color(0xFFC2185B)
        "Bills & Utilities" -> Color(0xFF0288D1)
        "Travel & Fuel" -> Color(0xFFE64A19)
        else -> MaterialTheme.colorScheme.secondary
    }

    val cardContainerColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardBorderColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val amountColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    } else if (transaction.isCredit) {
        Color(0xFF2E7D32)
    } else {
        MaterialTheme.colorScheme.error
    }

    val displayIconColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    } else {
        iconColor
    }

    val textColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subTextColor = if (transaction.isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardContainerColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(displayIconColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = displayIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Description and Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(subTextColor.copy(alpha = 0.4f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(transaction.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Amount Column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.getDefault(), "%s₹%,.2f", amountSign, transaction.amount),
                        color = amountColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Button (Restore or Delete)
                IconButton(
                    onClick = {
                        if (transaction.isDeleted) onRestore() else onDelete()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (transaction.isDeleted) Icons.Default.Restore else Icons.Default.Delete,
                        contentDescription = if (transaction.isDeleted) "Restore transaction" else "Delete transaction",
                        tint = if (transaction.isDeleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded view displaying original SMS text
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "SMS TRANSACTION LOG",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = subTextColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = transaction.fullMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }
        }
    }
}
