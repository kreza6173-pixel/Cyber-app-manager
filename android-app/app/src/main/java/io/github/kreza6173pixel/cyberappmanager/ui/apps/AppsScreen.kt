package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.*
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

@Composable
fun AppsScreen(repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier, onOpenApp: (String) -> Unit) {
    var result by remember { mutableStateOf(repository.cached) }; var loading by remember { mutableStateOf(false) }; var reload by remember { mutableStateOf(0) }; var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf(AppFilter.ALL) }
    LaunchedEffect(reload, connected) { if (connected && (reload > 0 || result == null)) { loading = true; result = repository.load(); loading = false } }
    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.apps_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val current = result; val counts = (current as? InventoryResult.Ok)?.counts
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in AppFilter.entries) { val label = stringResource(filterLabel(f)) + (counts?.let { " ${f.count(it)}" } ?: ""); if (f == filter) Button({ filter = f }) { Text(label) } else OutlinedButton({ filter = f }) { Text(label) } }
        }
        when {
            !connected -> Text(stringResource(R.string.apps_waiting))
            current == null -> Text(stringResource(R.string.apps_loading))
            current is InventoryResult.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.apps_error), color = MaterialTheme.colorScheme.error); LtrMonoText(current.message); CopyShareButtons(current.message); OutlinedButton({ reload++ }, enabled = !loading) { Text(stringResource(R.string.action_refresh)) } }
            current is InventoryResult.Ok -> { val shown = remember(current, filter, query) { filterApps(current.entries, filter, query) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.apps_shown_format, shown.size, current.entries.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton({ reload++ }, enabled = !loading) { Text(stringResource(if (loading) R.string.apps_loading else R.string.action_refresh)) } }; LazyColumn(Modifier.fillMaxSize()) { items(shown, key = { it.pkg }) { e -> AppRow(e) { onOpenApp(e.pkg) }; HorizontalDivider() } } }
        }
    }
}

@Composable private fun AppRow(e: AppEntry, onClick: () -> Unit) { val tags = mutableListOf(stringResource(if (e.isSystem) R.string.tag_system else R.string.tag_user)); when (e.state) { AppState.FROZEN -> tags += stringResource(R.string.tag_frozen); AppState.SUSPENDED -> tags += stringResource(R.string.tag_suspended); AppState.REMOVED -> tags += stringResource(R.string.tag_removed); AppState.ENABLED -> Unit }; e.protectedReason?.let { tags += stringResource(R.string.tag_protected_format, it) }; Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 8.dp, bottom = 8.dp)) { if (e.label != e.pkg) Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); LtrMonoText(e.pkg); Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = if (e.protectedReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
private fun filterLabel(f: AppFilter): Int = when (f) { AppFilter.ALL -> R.string.filter_all; AppFilter.USER -> R.string.filter_user; AppFilter.SYSTEM -> R.string.filter_system; AppFilter.FROZEN -> R.string.filter_frozen; AppFilter.SUSPENDED -> R.string.filter_suspended; AppFilter.REMOVED -> R.string.filter_removed; AppFilter.PROTECTED -> R.string.filter_protected }
