package com.solanki.pocketledger.ui

import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.solanki.pocketledger.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.solanki.pocketledger.viewmodel.HomeViewModel
import com.solanki.pocketledger.data.Transaction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import com.solanki.pocketledger.data.PersonSummary
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.toMutableStateList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPersonClick: (String) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    viewModel: HomeViewModel = viewModel()
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var searchQuery by remember { mutableStateOf("") }

    val totalReceived by viewModel.totalReceived.collectAsState()
    val totalSent by viewModel.totalSent.collectAsState()
    val netBalance by viewModel.netBalance.collectAsState()

    val people by viewModel.people.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Google Sign In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    var signedInAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    signedInAccount = account
                    viewModel.initDriveHelper(account)
                    Toast.makeText(context, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    )

    remember(signedInAccount) {
        signedInAccount?.let { viewModel.initDriveHelper(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportDatabase(it) { success ->
                    Toast.makeText(context, if (success) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.importDatabase(it) { success ->
                    Toast.makeText(context, if (success) "Import successful. Please restart app." else "Import failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val filteredPeople = people.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }
    
    val activePeople = filteredPeople.filter { !it.isArchived }
    val archivedPeople = filteredPeople.filter { it.isArchived }

    val currency = stringResource(id = R.string.currency_symbol)
    val formatter = remember {
        NumberFormat.getNumberInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
    }

    val positiveColor = Color(0xFF22C55E)
    val negativeColor = Color(0xFFEF4444)

    val netCardColor = if (netBalance >= 0) positiveColor.copy(alpha = 0.15f) else negativeColor.copy(alpha = 0.15f)
    val netTextColor = if (netBalance >= 0) positiveColor else negativeColor

    var expanded by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(SortType.RECENT) }
    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    
    LaunchedEffect(people.size, selectedSort) {
        if (people.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Drag and Drop State
    val activePeopleList = activePeople.toMutableStateList()
    LaunchedEffect(activePeople) {
        activePeopleList.clear()
        activePeopleList.addAll(activePeople)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme(it) }, modifier = Modifier.padding(end = 8.dp))
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (signedInAccount == null) {
                                DropdownMenuItem(text = { Text("Sign in with Google") }, onClick = { showMenu = false; googleSignInLauncher.launch(googleSignInClient.signInIntent) })
                            } else {
                                DropdownMenuItem(text = { Text("Sync to Drive") }, onClick = { showMenu = false; viewModel.syncToDrive { success -> Toast.makeText(context, if (success) "Synced to Drive" else "Sync failed", Toast.LENGTH_SHORT).show() } })
                                DropdownMenuItem(text = { Text("Restore from Drive") }, onClick = { showMenu = false; viewModel.restoreFromDrive { success -> Toast.makeText(context, if (success) "Restored from Drive. Please restart." else "Restore failed", Toast.LENGTH_LONG).show() } })
                                DropdownMenuItem(text = { Text("Sign Out (${signedInAccount?.email})") }, onClick = { showMenu = false; googleSignInClient.signOut().addOnCompleteListener { signedInAccount = null; Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show() } })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Export Data (Local)") }, onClick = { showMenu = false; exportLauncher.launch("PocketLedger_Backup.db") })
                            DropdownMenuItem(text = { Text("Import Data (Local)") }, onClick = { showMenu = false; importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3")) })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }, containerColor = positiveColor, contentColor = Color.White) { Text("+") }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = netCardColor)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val sign = if (netBalance > 0) "+" else if (netBalance < 0) "-" else ""
                            Text(stringResource(id = R.string.net_balance))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "$currency$sign${formatter.format(kotlin.math.abs(netBalance))}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = netTextColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = negativeColor.copy(alpha = 0.1f))) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(id = R.string.sent))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("$currency${formatter.format(totalSent)}", color = negativeColor)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = positiveColor.copy(alpha = 0.1f))) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(id = R.string.received))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("$currency${formatter.format(totalReceived)}", color = positiveColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(25.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search people") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = "" }) { Text("✕") } } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = R.string.people), style = MaterialTheme.typography.titleMedium)
                        Box {
                            Surface(onClick = { expanded = true }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = when (selectedSort) {
                                        SortType.RECENT -> "Recent"
                                        SortType.BALANCE_HIGH -> "High Balance"
                                        SortType.BALANCE_LOW -> "Low Balance"
                                        SortType.NAME -> "Name"
                                        SortType.MANUAL -> "Manual"
                                    })
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Recent") }, onClick = { selectedSort = SortType.RECENT; viewModel.setSortType(SortType.RECENT); expanded = false })
                                DropdownMenuItem(text = { Text("High Balance") }, onClick = { selectedSort = SortType.BALANCE_HIGH; viewModel.setSortType(SortType.BALANCE_HIGH); expanded = false })
                                DropdownMenuItem(text = { Text("Low Balance") }, onClick = { selectedSort = SortType.BALANCE_LOW; viewModel.setSortType(SortType.BALANCE_LOW); expanded = false })
                                DropdownMenuItem(text = { Text("Name") }, onClick = { selectedSort = SortType.NAME; viewModel.setSortType(SortType.NAME); expanded = false })
                                DropdownMenuItem(text = { Text("Manual") }, onClick = { selectedSort = SortType.MANUAL; viewModel.setSortType(SortType.MANUAL); expanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (filteredPeople.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isEmpty()) stringResource(id = R.string.no_people) else "No results found")
                    }
                }
            } else {
                itemsIndexed(activePeopleList, key = { _, it -> it.name }) { index, person ->
                    var showEditDialog by remember { mutableStateOf(false) }
                    
                    PersonCard(
                        person = person,
                        onClick = { onPersonClick(person.name) },
                        onArchive = { viewModel.archivePerson(person.name) },
                        onDelete = { viewModel.deletePerson(person.name) },
                        onEdit = { showEditDialog = true },
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, _ ->
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        viewModel.updateDisplayOrder(activePeopleList.map { it.name })
                                    }
                                )
                            }
                    )
                    
                    if (showEditDialog) {
                        EditNameDialog(oldName = person.name, onDismiss = { showEditDialog = false }, onConfirm = { newName -> viewModel.editPersonName(person.name, newName); showEditDialog = false })
                    }
                }

                if (archivedPeople.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = "Archived", style = MaterialTheme.typography.titleMedium, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(archivedPeople, key = { it.name }) { person ->
                        var showEditDialog by remember { mutableStateOf(false) }
                        PersonCard(person = person, onClick = { onPersonClick(person.name) }, onRestore = { viewModel.restorePerson(person.name) }, onDelete = { viewModel.deletePerson(person.name) }, onEdit = { showEditDialog = true }, faded = true)
                        if (showEditDialog) {
                            EditNameDialog(oldName = person.name, onDismiss = { showEditDialog = false }, onConfirm = { newName -> viewModel.editPersonName(person.name, newName); showEditDialog = false })
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
                }
            }
        }

        if (showSheet) {
            AddPersonSheet(onDismiss = { showSheet = false }, onAdd = { name -> viewModel.addPerson(name); showSheet = false })
        }
    }
}

@Composable
fun EditNameDialog(oldName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newName by remember { mutableStateOf(oldName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Name") }, text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) onConfirm(newName.trim()) }) { Text("Update") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonCard(
    person: PersonSummary,
    onClick: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    faded: Boolean = false
) {
    val positiveColor = Color(0xFF22C55E)
    val negativeColor = Color(0xFFEF4444)
    val neutralColor = Color.Gray

    val balanceColor = when {
        person.netBalance > 0 -> positiveColor
        person.netBalance < 0 -> negativeColor
        else -> neutralColor
    }

    val statusText = when {
        person.netBalance > 0 -> "You owe them"
        person.netBalance < 0 -> "They owe you"
        else -> "Settled"
    }

    val currency = "₹"
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (faded) 0.5f else 1f),
        colors = CardDefaults.cardColors(containerColor = balanceColor.copy(alpha = 0.12f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = person.name, fontWeight = FontWeight.Bold)
                Text(statusText, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$currency${String.format("%.2f", kotlin.math.abs(person.netBalance))}", color = balanceColor, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onEdit != null) DropdownMenuItem(text = { Text("Edit Name") }, onClick = { onEdit(); menuExpanded = false })
                        if (onArchive != null) DropdownMenuItem(text = { Text("Archive") }, onClick = { onArchive(); menuExpanded = false })
                        if (onRestore != null) DropdownMenuItem(text = { Text("Restore") }, onClick = { onRestore(); menuExpanded = false })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Add Person", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Person Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { if (name.isNotBlank()) onAdd(name.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
