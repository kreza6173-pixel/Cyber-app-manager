package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.AppEntry
import io.github.kreza6173pixel.cyberappmanager.inventory.AppFilter
import io.github.kreza6173pixel.cyberappmanager.inventory.AppState
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryResult
import io.github.kreza6173pixel.cyberappmanager.inventory.filterApps
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

@Composable
fun AppsScreen(
    repository: InventoryRepository,
    connected: Boolean,
    modifier: Modifier = Modifier,
    onOpenApp: (String) -> Unit,
) {
    var result by remember { mutableStateOf(repository.cached) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }

    LaunchedEffect(reload, connected) {
        if (connected && (reload > 0 || result == null)) {
            loading = true
            result = repository.load()
            loading = false
        }
    }

    Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val current = result
        val counts = (current as? InventoryResult.Ok)?.counts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in AppFilter.entries) {
                val base = stringResource(filterLabel(f))
                val label = if (counts != null) base + " " + f.count(counts) else base
                if (f == filter) {
                    Button(onClick = { filter = f }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { filter = f }) { Text(label) }
                }
            }
        }

        when {
            !connected -> Text(stringResource(R.string.apps_waiting))
            current == null -> Text(stringResource(R.string.apps_loading))
            current is InventoryResult.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.apps_error), color = MaterialTheme.colorScheme.error)
                LtrMonoText(current.message)
                CopyShareButtons(current.message)
                OutlinedButton(onClick = { reload = reload + 1 }, enabled = !loading) {
                    Text(stringResource(R.string.action_refresh))
                }
            }
            current is InventoryResult.Ok -> {
                val shown = remember(current, filter, query) { filterApps(current.entries, filter, query) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.apps_shown_format, shown.size, current.entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { reload = reload + 1 }, enabled = !loading) {
                        Text(stringResource(if (loading) R.string.apps_loading else R.string.action_refresh))
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.pkg }) { entry ->
                        AppRow(entry = entry, onClick = { onOpenApp(entry.pkg) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, onClick: () -> Unit) {
    val kind = stringResource(if (entry.isSystem) R.string.tag_system else R.string.tag_user)
    val frozen = stringResource(R.string.tag_frozen)
    val removed = stringResource(R.string.tag_removed)
    val guarded = entry.protectedReason?.let { stringResource(R.string.tag_protected_format, it) }
    val tags = ArrayList<String>()
    tags.add(kind)
    when (entry.state) {
        AppState.FROZEN -> tags.add(frozen)
        AppState.REMOVED -> tags.add(removed)
        AppState.ENABLED -> Unit
    }
    if (guarded != null) tags.add(guarded)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        if (entry.label != entry.pkg) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LtrMonoText(entry.pkg)
        Text(
            text = tags.joinToString(" \u00b7 "),
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.protectedReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun filterLabel(f: AppFilter): Int = when (f) {
    AppFilter.ALL -> R.string.filter_all
    AppFilter.USER -> R.string.filter_user
    AppFilter.SYSTEM -> R.string.filter_system
    AppFilter.FROZEN -> R.string.filter_frozen
    AppFilter.REMOVED -> R.string.filter_removed
    AppFilter.PROTECTED -> R.string.filter_protected
}
