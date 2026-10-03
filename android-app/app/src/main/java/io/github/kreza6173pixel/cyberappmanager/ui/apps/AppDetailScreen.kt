package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.ActionResult
import io.github.kreza6173pixel.cyberappmanager.inventory.AppAction
import io.github.kreza6173pixel.cyberappmanager.inventory.AppActions
import io.github.kreza6173pixel.cyberappmanager.inventory.AppState
import io.github.kreza6173pixel.cyberappmanager.inventory.DetailsResult
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.inventory.Verdict
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(
    pkg: String,
    repository: InventoryRepository,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var entry by remember(pkg) { mutableStateOf(repository.entryFor(pkg)) }
    var details by remember(pkg) { mutableStateOf<DetailsResult?>(null) }
    var detailsKey by remember(pkg) { mutableStateOf(0) }
    var busy by remember(pkg) { mutableStateOf(false) }
    var lastResult by remember(pkg) { mutableStateOf<ActionResult?>(null) }
    var confirm by remember(pkg) { mutableStateOf<AppAction?>(null) }

    LaunchedEffect(pkg, connected, detailsKey) {
        if (connected) details = repository.details(pkg)
    }

    val run: (AppAction) -> Unit = { action ->
        busy = true
        scope.launch {
            lastResult = repository.perform(pkg, action)
            entry = repository.entryFor(pkg)
            detailsKey = detailsKey + 1
            busy = false
        }
    }

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(actionLabel(pending))) },
            text = { Text(stringResource(actionWarning(pending), entry?.label ?: pkg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    run(pending)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val e = entry
        Text(text = e?.label ?: pkg, style = MaterialTheme.typography.headlineSmall)
        LtrMonoText(pkg)

        if (e != null) {
            val stateRes = when (e.state) {
                AppState.ENABLED -> R.string.detail_state_enabled
                AppState.FROZEN -> R.string.detail_state_frozen
                AppState.REMOVED -> R.string.detail_state_removed
            }
            val kindRes = if (e.isSystem) R.string.tag_system else R.string.tag_user
            Text(stringResource(stateRes) + " \u00b7 " + stringResource(kindRes))
            val reason = e.protectedReason
            if (reason != null) {
                Text(
                    text = stringResource(R.string.detail_protected_format, reason),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val actions = AppActions.availableFor(e)
            if (actions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.actions_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                for (a in actions) {
                    OutlinedButton(
                        onClick = { if (AppActions.needsConfirmation(a)) confirm = a else run(a) },
                        enabled = connected && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(actionLabel(a))) }
                }
                if (e.state != AppState.REMOVED && !e.isSystem) {
                    Text(
                        text = stringResource(R.string.actions_user_remove_later),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        val r = lastResult
        if (r != null) {
            ResultCard(r)
        }

        when (val d = details) {
            null -> Text(stringResource(if (connected) R.string.apps_loading else R.string.apps_waiting))
            is DetailsResult.Error -> {
                Text(stringResource(R.string.detail_error), color = MaterialTheme.colorScheme.error)
                LtrMonoText(d.message)
                CopyShareButtons(d.message)
            }
            is DetailsResult.Ok -> {
                val p = d.details
                val none = stringResource(R.string.value_none)
                val version = when {
                    p.versionName != null && p.versionCode != null -> p.versionName + " (" + p.versionCode + ")"
                    p.versionName != null -> p.versionName
                    p.versionCode != null -> p.versionCode.toString()
                    else -> none
                }
                Field(stringResource(R.string.detail_version), version)
                Field(
                    stringResource(R.string.detail_sdk),
                    (p.minSdk?.toString() ?: none) + " / " + (p.targetSdk?.toString() ?: none),
                )
                Field(stringResource(R.string.detail_installer), p.installer ?: none)
                Field(stringResource(R.string.detail_first), p.firstInstall ?: none)
                Field(stringResource(R.string.detail_last), p.lastUpdate ?: none)
                Field(
                    stringResource(R.string.detail_flags),
                    if (p.userFlags.isEmpty()) none else p.userFlags.entries.joinToString(" ") { it.key + "=" + it.value },
                )
                Text(
                    text = stringResource(R.string.detail_raw),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LtrMonoText(d.raw)
                CopyShareButtons(d.raw)
            }
        }
    }
}

@Composable
private fun ResultCard(r: ActionResult) {
    val ok = r.verdict == Verdict.APPLIED
    val verdictRes = when (r.verdict) {
        Verdict.APPLIED -> R.string.verdict_applied
        Verdict.NOT_APPLIED -> R.string.verdict_not_applied
        Verdict.UNVERIFIABLE -> R.string.verdict_unverifiable
        Verdict.REFUSED -> R.string.verdict_refused
        Verdict.FAILED -> R.string.verdict_failed
    }
    val report = "$ " + r.command + "\n" + r.output + "\n\nread-back:\n" + r.readBack
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(actionLabel(r.action)) + ": " + stringResource(verdictRes),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            LtrMonoText(report)
            CopyShareButtons(report)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        LtrMonoText(value)
    }
}

private fun actionLabel(a: AppAction): Int = when (a) {
    AppAction.FREEZE -> R.string.action_freeze
    AppAction.UNFREEZE -> R.string.action_unfreeze
    AppAction.FORCE_STOP -> R.string.action_force_stop
    AppAction.REMOVE -> R.string.action_remove
    AppAction.RESTORE -> R.string.action_restore
    AppAction.CLEAR_DATA -> R.string.action_clear
}

private fun actionWarning(a: AppAction): Int = when (a) {
    AppAction.FREEZE -> R.string.warn_freeze
    AppAction.REMOVE -> R.string.warn_remove
    AppAction.CLEAR_DATA -> R.string.warn_clear
    else -> R.string.warn_generic
}
