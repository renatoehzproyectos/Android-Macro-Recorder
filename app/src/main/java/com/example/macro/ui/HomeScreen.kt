package com.example.macro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.macro.model.Macro

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MacroViewModel, onOpen: (String) -> Unit) {
    val macros by viewModel.macros.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Macro Recorder") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreateDialog = true }, text = { Text("New Macro") }, icon = {})
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Recent Macros", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (macros.isEmpty()) {
                Text("No macros yet. Tap \"New Macro\" to record your first one.")
            }
            LazyColumn {
                items(macros, key = { it.id }) { macro ->
                    MacroCard(macro, onClick = { onOpen(macro.id) }, onDelete = {
                        viewModel.repository.delete(macro.id)
                        viewModel.refreshMacros()
                    })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Macro") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Macro name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val macro = viewModel.createMacro(name)
                    showCreateDialog = false
                    onOpen(macro.id)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MacroCard(macro: Macro, onClick: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(macro.name, style = MaterialTheme.typography.titleMedium)
                val seconds = macro.durationNs / 1_000_000_000.0
                Text("${macro.events.size} actions • %.2f seconds".format(seconds), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Text("✕") }
        }
    }
}
