package com.solanki.pocketledger.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solanki.pocketledger.data.Transaction
import com.solanki.pocketledger.viewmodel.PersonDetailViewModel
import com.solanki.pocketledger.viewmodel.PersonDetailViewModelFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

enum class TransactionSortType {
    RECENT,
    OLDEST,
    AMOUNT_HIGH,
    AMOUNT_LOW
}

enum class TransactionFilterType {
    ALL,
    RECEIVED,
    SENT
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    personName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application

    val factory = remember(personName) {
        PersonDetailViewModelFactory(application, personName)
    }

    val viewModel: PersonDetailViewModel = viewModel(factory = factory)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var recentlyDeleted by remember { mutableStateOf<Transaction?>(null) }

    val totalReceived by viewModel.totalReceived.collectAsState()
    val totalSent by viewModel.totalSent.collectAsState()
    val netBalance by viewModel.netBalance.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    var selectedSort by remember { mutableStateOf(TransactionSortType.RECENT) }
    var selectedFilter by remember { mutableStateOf(TransactionFilterType.ALL) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val transactions by viewModel.transactions.collectAsState()
    val filteredTransactions = transactions
        .filter {
            when (selectedFilter) {
                TransactionFilterType.ALL -> true
                TransactionFilterType.RECEIVED -> it.type == "RECEIVED"
                TransactionFilterType.SENT -> it.type == "SENT"
            }
        }
        .filter { transaction ->
            searchQuery.isBlank() ||
                    transaction.note?.contains(searchQuery, ignoreCase = true) == true ||
                    transaction.type.contains(searchQuery, ignoreCase = true) ||
                    transaction.amount.toString().contains(searchQuery)
        }

    val finalTransactions = when (selectedSort) {
        TransactionSortType.RECENT -> filteredTransactions.sortedByDescending { it.timestamp }
        TransactionSortType.OLDEST -> filteredTransactions.sortedBy { it.timestamp }
        TransactionSortType.AMOUNT_HIGH -> filteredTransactions.sortedByDescending { it.amount }
        TransactionSortType.AMOUNT_LOW -> filteredTransactions.sortedBy { it.amount }
    }

    val listState = rememberLazyListState()
    
    LaunchedEffect(transactions.size, selectedSort, selectedFilter) {
        if (transactions.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val positiveColor = Color(0xFF22C55E)
    val negativeColor = Color(0xFFEF4444)

    val formatter = remember {
        NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(personName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val sign = when {
                            netBalance > 0 -> "+"
                            netBalance < 0 -> "-"
                            else -> ""
                        }
                        val statusText = when {
                            netBalance > 0 -> "They owe you"
                            netBalance < 0 -> "You owe them"
                            else -> "Settled"
                        }
                        
                        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                        
                        val transactionLines = transactions.sortedBy { it.timestamp }.map { t ->
                            val date = dateFormat.format(Date(t.timestamp))
                            val typeLabel = if (t.type == "SENT") "Sent" else "Recv"
                            "• $date: $typeLabel ₹${formatter.format(t.amount)}${if (!t.note.isNullOrBlank()) " (${t.note})" else ""}"
                        }.joinToString("\n")

                        val shareText = """
                            Pocket Ledger Summary: $personName
                            ---------------------------
                            Total Sent: ₹${formatter.format(totalSent)}
                            Total Received: ₹${formatter.format(totalReceived)}
                            ---------------------------
                            Transactions:
                            $transactionLines
                            ---------------------------
                            Net Balance: ₹$sign${formatter.format(kotlin.math.abs(netBalance))}
                            Status: $statusText
                        """.trimIndent()

                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingTransaction = null
                    showSheet = true 
                },
                containerColor = positiveColor
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (netBalance >= 0) positiveColor.copy(alpha = 0.12f) else negativeColor.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val sign = when {
                        netBalance > 0 -> "+"
                        netBalance < 0 -> "-"
                        else -> ""
                    }
                    Text("Net Balance")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₹$sign${formatter.format(kotlin.math.abs(netBalance))}",
                        fontWeight = FontWeight.Bold,
                        color = if (netBalance >= 0) positiveColor else negativeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = negativeColor.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sent")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("₹${formatter.format(totalSent)}", color = negativeColor)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = positiveColor.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Received")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("₹${formatter.format(totalReceived)}", color = positiveColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Transactions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search note or amount...") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedFilter == TransactionFilterType.ALL, onClick = { selectedFilter = TransactionFilterType.ALL }, label = { Text("All") })
                    FilterChip(selected = selectedFilter == TransactionFilterType.RECEIVED, onClick = { selectedFilter = TransactionFilterType.RECEIVED }, label = { Text("Received") })
                    FilterChip(selected = selectedFilter == TransactionFilterType.SENT, onClick = { selectedFilter = TransactionFilterType.SENT }, label = { Text("Sent") })
                }

                Box {
                    Surface(onClick = { sortExpanded = true }, shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when (selectedSort) {
                                TransactionSortType.RECENT -> "Recent"
                                TransactionSortType.OLDEST -> "Oldest"
                                TransactionSortType.AMOUNT_HIGH -> "High Amount"
                                TransactionSortType.AMOUNT_LOW -> "Low Amount"
                            })
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        DropdownMenuItem(text = { Text("Recent") }, onClick = { selectedSort = TransactionSortType.RECENT; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Oldest") }, onClick = { selectedSort = TransactionSortType.OLDEST; sortExpanded = false })
                        DropdownMenuItem(text = { Text("High Amount") }, onClick = { selectedSort = TransactionSortType.AMOUNT_HIGH; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Low Amount") }, onClick = { selectedSort = TransactionSortType.AMOUNT_LOW; sortExpanded = false })
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (finalTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions found.")
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(finalTransactions, key = { it.id }) { transaction ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    recentlyDeleted = transaction
                                    viewModel.deleteTransaction(transaction)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar("Transaction deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) recentlyDeleted?.let { viewModel.addTransaction(it) }
                                        recentlyDeleted = null
                                    }
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                        Text(text = "Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            val isReceived = transaction.type == "RECEIVED"
                            val amountColor = if (isReceived) positiveColor else negativeColor
                            val date = remember(transaction.timestamp) {
                                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(transaction.timestamp))
                            }

                            Card(
                                onClick = {
                                    editingTransaction = transaction
                                    showSheet = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = amountColor.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(transaction.type)
                                        Text(date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        transaction.note?.let {
                                            Spacer(Modifier.height(4.dp))
                                            Text(it)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "₹${String.format(Locale.getDefault(), "%.2f", transaction.amount)}", color = amountColor, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSheet) {
            AddPersonTransactionSheet(
                existingTransaction = editingTransaction,
                onDismiss = { showSheet = false },
                onAdd = { amount, type, note, timestamp ->
                    if (editingTransaction != null) {
                        viewModel.updateTransaction(editingTransaction!!.copy(amount = amount, type = type, note = note, timestamp = timestamp))
                    } else {
                        viewModel.addTransaction(Transaction(personName = personName, amount = amount, type = type, note = note, timestamp = timestamp))
                    }
                    showSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonTransactionSheet(
    existingTransaction: Transaction? = null,
    onDismiss: () -> Unit,
    onAdd: (Double, String, String?, Long) -> Unit
) {
    var amountText by remember { mutableStateOf(existingTransaction?.amount?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(existingTransaction?.type ?: "SENT") }
    var note by remember { mutableStateOf(existingTransaction?.note ?: "") }
    var selectedTimestamp by remember { mutableStateOf(existingTransaction?.timestamp ?: System.currentTimeMillis()) }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets.ime
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (existingTransaction != null) "Edit Transaction" else "Add Transaction", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = amountText,
                onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}\$"))) amountText = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterChip(selected = selectedType == "SENT", onClick = { selectedType = "SENT" }, label = { Text("Sent") })
                FilterChip(selected = selectedType == "RECEIVED", onClick = { selectedType = "RECEIVED" }, label = { Text("Received") })
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null) onAdd(amount, selectedType, if (note.isBlank()) null else note, selectedTimestamp)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existingTransaction != null) "Update" else "Add")
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                        android.app.DatePickerDialog(context, { _, y, m, d ->
                            calendar.set(y, m, d)
                            selectedTimestamp = calendar.timeInMillis
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Date")
                }

                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                        android.app.TimePickerDialog(context, { _, h, min ->
                            calendar.set(Calendar.HOUR_OF_DAY, h)
                            calendar.set(Calendar.MINUTE, min)
                            selectedTimestamp = calendar.timeInMillis
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Time")
                }
            }
        }
    }
}
