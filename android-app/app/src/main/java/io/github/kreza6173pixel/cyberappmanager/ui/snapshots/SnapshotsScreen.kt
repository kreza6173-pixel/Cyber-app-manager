package io.github.kreza6173pixel.cyberappmanager.ui.snapshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.Snapshot
import java.text.DateFormat
import java.util.Date

@Composable
fun SnapshotsScreen(repository: InventoryRepository, modifier: Modifier = Modifier) {
    var snapshots by remember { mutableStateOf(repository.snapshots()) }
    var selected by remember { mutableStateOf<Snapshot?>(null) }
    var deleteTarget by remember { mutableStateOf<Snapshot?>(null) }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.snapshot_delete_title)) },
            text = { Text(stringResource(R.string.snapshot_delete_warning, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteSnapshot(target.id)
                    snapshots = repository.snapshots()
                    if (selected?.id == target.id) selected = null
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.snapshots_title), style = MaterialTheme.typography.headlineSmall)
        if (snapshots.isEmpty()) {
            Text(stringResource(R.string.snapshots_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshots, key = { it.id }) { snapshot ->
                    Card(onClick = { selected = snapshot }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(snapshot.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.snapshot_meta, snapshot.entries.size, formatDate(snapshot.createdAtMs)))
                            OutlinedButton(onClick = { deleteTarget = snapshot }) { Text(stringResource(R.string.action_delete)) }
                        }
                    }
                }
            }
        }
    }

    selected?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(snapshot.name) },
            text = { Text(snapshot.entries.joinToString("\n") { "${it.pkg} · ${it.state.name.lowercase()}" }) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

private fun formatDate(timeMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timeMs))
