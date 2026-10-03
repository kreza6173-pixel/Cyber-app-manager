package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.R
import io.github.kreza6173pixel.cyberappmanager.inventory.AppState
import io.github.kreza6173pixel.cyberappmanager.inventory.DetailsResult
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

@Composable
fun AppDetailScreen(
    pkg: String,
    repository: InventoryRepository,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val entry = remember(pkg) { repository.entryFor(pkg) }
    var details by remember(pkg) { mutableStateOf<DetailsResult?>(null) }

    LaunchedEffect(pkg, connected) {
        if (connected && details == null) details = repository.details(pkg)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = entry?.label ?: pkg, style = MaterialTheme.typography.headlineSmall)
        LtrMonoText(pkg)

        if (entry != null) {
            val stateRes = when (entry.state) {
                AppState.ENABLED -> R.string.detail_state_enabled
                AppState.FROZEN -> R.string.detail_state_frozen
                AppState.REMOVED -> R.string.detail_state_removed
            }
            val kindRes = if (entry.isSystem) R.string.tag_system else R.string.tag_user
            Text(stringResource(stateRes) + " \u00b7 " + stringResource(kindRes))
            val reason = entry.protectedReason
            if (reason != null) {
                Text(
                    text = stringResource(R.string.detail_protected_format, reason),
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
private fun Field(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        LtrMonoText(value)
    }
}
