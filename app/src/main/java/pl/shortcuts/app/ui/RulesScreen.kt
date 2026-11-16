package pl.shortcuts.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.SharedFlow
import pl.shortcuts.app.data.RuleRepository
import pl.shortcuts.app.model.Action
import pl.shortcuts.app.model.Preset
import pl.shortcuts.app.model.Rule
import pl.shortcuts.app.model.Toggle
import pl.shortcuts.app.model.Trigger
import pl.shortcuts.app.model.describe
import pl.shortcuts.app.shizuku.ShizukuShell
import pl.shortcuts.app.shizuku.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    repository: RuleRepository,
    shell: ShizukuShell,
    scannedTags: SharedFlow<String>,
    nfcState: NfcState,
    onRun: (String) -> Unit,
) {
    val rules by repository.rules.collectAsState()
    val shizuku by shell.state.collectAsState()
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Shortcuts") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New rule") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (nfcState != NfcState.OK) item {
                StatusCard(
                    if (nfcState == NfcState.UNSUPPORTED) "This device has no NFC." else "NFC is turned off. Enable it in system settings.",
                )
            }
            item { ShizukuCard(shizuku, shell::requestPermission) }
            if (rules.isEmpty()) item {
                Text(
                    "No rules yet. Tap “New rule”, scan a tag and pick the actions to run – or use the Sleep preset.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { repository.setEnabled(rule.id, it) },
                    onRun = { onRun(rule.id) },
                    onEdit = { editing = rule },
                    onDelete = { repository.delete(rule.id) },
                )
            }
        }
    }

    if (creating || editing != null) {
        RuleEditor(
            initial = editing,
            scannedTags = scannedTags,
            onDismiss = { creating = false; editing = null },
            onSave = { repository.upsert(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun StatusCard(text: String) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    modifier = Modifier.fillMaxWidth(),
) { Text(text, Modifier.padding(16.dp)) }

@Composable
private fun ShizukuCard(state: ShizukuState, onGrant: () -> Unit) = Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Silent mode (Shizuku)", style = MaterialTheme.typography.titleMedium)
        Text(
            when (state) {
                ShizukuState.READY -> "Active – Wi-Fi, Bluetooth and mobile data are switched instantly."
                ShizukuState.NO_PERMISSION -> "Shizuku is running but this app is not authorised yet."
                ShizukuState.NOT_RUNNING ->
                    "Not running. Without it Android shows a confirmation panel for each toggle, " +
                        "and mobile data can only be switched from that panel. Install Shizuku and start it via Wireless debugging."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state == ShizukuState.NO_PERMISSION) FilledTonalButton(onClick = onGrant) { Text("Grant access") }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) = Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(rule.trigger.describe(), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.padding(4.dp))
        Text("→ " + rule.actions.joinToString(" · ") { it.describe() }, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, "Run now") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun RuleEditor(
    initial: Rule?,
    scannedTags: SharedFlow<String>,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: Preset.CONNECT.ruleName) }
    var uid by remember { mutableStateOf((initial?.trigger as? Trigger.NfcTag)?.uid ?: "") }
    // Toggle -> desired state; absent = action not selected. New rules default to the "turn everything on" example.
    val selected = remember {
        mutableStateMapOf<Toggle, Boolean>().apply {
            if (initial == null) putAll(Preset.CONNECT.toggles)
            else initial.actions.filterIsInstance<Action.SetToggle>().forEach { put(it.target, it.enabled) }
        }
    }
    // Apps opened (in this order) after the toggles.
    val apps = remember {
        mutableStateListOf<Action.LaunchApp>().apply { initial?.actions?.filterIsInstance<Action.LaunchApp>()?.let(::addAll) }
    }
    var picking by remember { mutableStateOf(false) }

    fun applyPreset(p: Preset) {
        name = p.ruleName
        selected.clear(); selected.putAll(p.toggles)
        apps.clear(); apps.addAll(p.apps)
    }

    LaunchedEffect(Unit) { scannedTags.collect { uid = it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Preset", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset.entries.forEach { p ->
                        AssistChip(
                            onClick = { applyPreset(p) },
                            label = { Text(p.label) },
                            leadingIcon = {
                                Icon(if (p == Preset.SLEEP) Icons.Default.Bedtime else Icons.Default.Wifi, null, Modifier.size(18.dp))
                            },
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text("Tag UID") },
                    placeholder = { Text("Hold a tag to the phone") },
                    leadingIcon = { Icon(Icons.Default.Nfc, null) },
                    singleLine = true,
                )
                Text("Actions", style = MaterialTheme.typography.titleSmall)
                Toggle.entries.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = t in selected, onCheckedChange = { if (it) selected[t] = true else selected.remove(t) })
                        Text(t.label, Modifier.weight(1f))
                        if (t in selected) {
                            SingleChoiceSegmentedButtonRow(Modifier.width(140.dp)) {
                                listOf(true, false).forEachIndexed { i, on ->
                                    SegmentedButton(
                                        selected = selected[t] == on,
                                        onClick = { selected[t] = on },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    ) { Text(if (on) "On" else "Off") }
                                }
                            }
                        }
                    }
                }
                Text("Then open app", style = MaterialTheme.typography.titleSmall)
                apps.forEachIndexed { index, app ->
                    val installed = remember(app.packageName) { isInstalled(context, app.packageName) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, Modifier.padding(horizontal = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(
                                if (installed) app.packageName else "Not installed – install it or pick another app",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (installed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = { apps.removeAt(index) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
                OutlinedButton(onClick = { picking = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add app")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && uid.length >= 4 && (selected.isNotEmpty() || apps.isNotEmpty()),
                onClick = {
                    val actions: List<Action> =
                        Toggle.entries.filter { it in selected }.map { Action.SetToggle(it, selected.getValue(it)) } + apps
                    onSave(
                        (initial ?: Rule(name = name, trigger = Trigger.NfcTag(uid), actions = actions))
                            .copy(name = name.trim(), trigger = Trigger.NfcTag(uid), actions = actions),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        AppPickerDialog(
            onDismiss = { picking = false },
            onPick = { app -> if (apps.none { it.packageName == app.packageName }) apps += app; picking = false },
        )
    }
}
