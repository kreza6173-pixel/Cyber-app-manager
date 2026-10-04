package io.github.kreza6173pixel.cyberappmanager.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.inventory.AutostartAudit
import io.github.kreza6173pixel.cyberappmanager.ui.common.CopyShareButtons
import io.github.kreza6173pixel.cyberappmanager.ui.common.LtrMonoText

/** A5 remains read-only until component and AppOps writes are phone-probed with read-back. */
@Composable
fun AutostartAuditCard(result: Result<AutostartAudit>?, onRetry: (() -> Unit)? = null) {
    when (result) {
        null -> Text("Autostart / boot audit: reading…", style = MaterialTheme.typography.titleSmall)
        else -> result.fold(
            onSuccess = { audit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Autostart / boot", style = MaterialTheme.typography.titleSmall)
                        Text("Read-only audit. No component or background setting was changed.", style = MaterialTheme.typography.labelSmall)
                        if (audit.receivers.isEmpty()) Text("No boot receiver found in the exposed package dump.", style = MaterialTheme.typography.bodySmall)
                        audit.receivers.forEach { receiver ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(receiver.component, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(receiver.action, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (audit.backgroundOps.isNotEmpty()) {
                            Text("Background execution", style = MaterialTheme.typography.labelMedium)
                            audit.backgroundOps.forEach { op -> Text("${op.op}: ${op.mode}", style = MaterialTheme.typography.bodySmall) }
                        }
                        Text("Source output", style = MaterialTheme.typography.labelMedium)
                        LtrMonoText(audit.raw)
                        CopyShareButtons(audit.raw)
                    }
                }
            },
            onFailure = { error ->
                Text("Autostart / boot audit unavailable: ${error.message ?: "unknown error"}", color = MaterialTheme.colorScheme.error)
                onRetry?.let { androidx.compose.material3.TextButton(onClick = it) { Text("Retry") } }
            },
        )
    }
}
