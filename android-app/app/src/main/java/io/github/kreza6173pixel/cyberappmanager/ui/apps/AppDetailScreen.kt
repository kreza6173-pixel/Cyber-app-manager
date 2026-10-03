package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.*
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(pkg: String, repository: InventoryRepository, connected: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope(); var entry by remember(pkg) { mutableStateOf(repository.entryFor(pkg)) }; var details by remember(pkg) { mutableStateOf<DetailsResult?>(null) }; var busy by remember(pkg) { mutableStateOf(false) }; var last by remember(pkg) { mutableStateOf<ActionResult?>(null) }; var confirm by remember(pkg) { mutableStateOf<AppAction?>(null) }; var reload by remember(pkg) { mutableStateOf(0) }; var pinned by remember(pkg) { mutableStateOf(pkg in repository.pins()) }
    LaunchedEffect(pkg, connected, reload) { if (connected) { details = repository.details(pkg); entry = repository.entryFor(pkg); pinned = pkg in repository.pins() } }
    val pending = confirm
    if (pending != null) AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(actionLabel(pending))) }, text = { Text(stringResource(actionWarning(pending), entry?.label ?: pkg)) }, confirmButton = { TextButton({ confirm = null; busy = true; scope.launch { last = repository.perform(pkg, pending); entry = repository.entryFor(pkg); busy = false; reload++ } }) { Text(stringResource(R.string.action_confirm)) } }, dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.action_cancel)) } })
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val e = entry; Text(e?.label ?: pkg, style = MaterialTheme.typography.headlineSmall); LtrMonoText(pkg)
        if (e != null) { Text(stringResource(stateLabel(e.state)) + " · " + stringResource(if (e.isSystem) R.string.tag_system else R.string.tag_user)); e.protectedReason?.let { Text(stringResource(R.string.detail_protected_format, it), color = MaterialTheme.colorScheme.error) }; if (e.protectedReason == null) OutlinedButton(onClick = { pinned = repository.setPinned(pkg, !pinned).contains(pkg) }, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (pinned) R.string.action_unpin else R.string.action_pin)) }; val actions = AppActions.availableFor(e); if (actions.isNotEmpty()) { Text(stringResource(R.string.actions_title), style = MaterialTheme.typography.titleSmall); actions.forEach { a -> OutlinedButton({ if (AppActions.needsConfirmation(a)) confirm = a else { busy = true; scope.launch { last = repository.perform(pkg, a); entry = repository.entryFor(pkg); busy = false; reload++ } } }, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(actionLabel(a))) } } } }
        last?.let { ResultCard(it) }
        when (val d = details) { null -> Text(stringResource(if (connected) R.string.apps_loading else R.string.apps_waiting)); is DetailsResult.Error -> { Text(stringResource(R.string.detail_error), color = MaterialTheme.colorScheme.error); LtrMonoText(d.message); CopyShareButtons(d.message) }; is DetailsResult.Ok -> { val p = d.details; val none = stringResource(R.string.value_none); Field(stringResource(R.string.detail_version), when { p.versionName != null && p.versionCode != null -> "${p.versionName} (${p.versionCode})"; p.versionName != null -> p.versionName; p.versionCode != null -> p.versionCode.toString(); else -> none }); Field(stringResource(R.string.detail_sdk), "${p.minSdk ?: none} / ${p.targetSdk ?: none}"); Field(stringResource(R.string.detail_installer), p.installer ?: none); Field(stringResource(R.string.detail_first), p.firstInstall ?: none); Field(stringResource(R.string.detail_last), p.lastUpdate ?: none); Field(stringResource(R.string.detail_flags), if (p.userFlags.isEmpty()) none else p.userFlags.entries.joinToString(" ") { "${it.key}=${it.value}" }); Text(stringResource(R.string.detail_raw), style = MaterialTheme.typography.titleSmall); LtrMonoText(d.raw); CopyShareButtons(d.raw) } }
    }
}

@Composable private fun ResultCard(r: ActionResult) { val res = when (r.verdict) { Verdict.APPLIED -> R.string.verdict_applied; Verdict.NOT_APPLIED -> R.string.verdict_not_applied; Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable; Verdict.REFUSED -> R.string.verdict_refused; Verdict.FAILED -> R.string.verdict_failed }; val report = "$ ${r.command}\n${r.output}\n\nread-back:\n${r.readBack}"; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(stringResource(actionLabel(r.action)) + ": " + stringResource(res), color = if (r.verdict == Verdict.APPLIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); LtrMonoText(report); CopyShareButtons(report) } } }
@Composable private fun Field(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium); LtrMonoText(value) } }
private fun stateLabel(s: AppState) = when (s) { AppState.ENABLED -> R.string.detail_state_enabled; AppState.FROZEN -> R.string.detail_state_frozen; AppState.SUSPENDED -> R.string.detail_state_suspended; AppState.REMOVED -> R.string.detail_state_removed }
private fun actionLabel(a: AppAction) = when (a) { AppAction.SUSPEND -> R.string.action_suspend; AppAction.UNSUSPEND -> R.string.action_unsuspend; AppAction.FREEZE -> R.string.action_freeze; AppAction.UNFREEZE -> R.string.action_unfreeze; AppAction.FORCE_STOP -> R.string.action_force_stop; AppAction.REMOVE -> R.string.action_remove; AppAction.RESTORE -> R.string.action_restore; AppAction.CLEAR_DATA -> R.string.action_clear }
private fun actionWarning(a: AppAction) = when (a) { AppAction.SUSPEND -> R.string.warn_suspend; AppAction.FREEZE -> R.string.warn_suspend; AppAction.REMOVE -> R.string.warn_remove; AppAction.CLEAR_DATA -> R.string.warn_clear; else -> R.string.warn_generic }
