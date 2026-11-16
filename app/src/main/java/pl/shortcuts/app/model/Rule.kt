package pl.shortcuts.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A rule = "when [trigger] happens, run [actions] in order".
 * Triggers and actions are sealed hierarchies so new kinds (time, location, charger,
 * app launch, volume, DND…) can be added without touching the storage format of existing ones.
 */
@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val trigger: Trigger,
    val actions: List<Action>,
)

@Serializable
sealed interface Trigger {
    /** Matches a physical tag by its hardware UID (hex, upper-case, no separators). */
    @Serializable
    @SerialName("nfc_tag")
    data class NfcTag(val uid: String) : Trigger
}

/** Events coming into the engine; matched against [Trigger]s. */
sealed interface TriggerEvent {
    data class NfcTagScanned(val uid: String) : TriggerEvent
    data class Manual(val ruleId: String) : TriggerEvent
}

@Serializable
sealed interface Action {
    @Serializable
    @SerialName("set_toggle")
    data class SetToggle(val target: Toggle, val enabled: Boolean) : Action

    /** Opens another installed app (its launcher activity). Runs after all toggles of the rule. */
    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(val packageName: String, val label: String = packageName) : Action
}

@Serializable
enum class Toggle(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi"),
    MOBILE_DATA("Mobile data"),
}

fun Trigger.describe(): String = when (this) {
    is Trigger.NfcTag -> "NFC tag ${uid.chunked(2).joinToString(":")}"
}

fun Action.describe(): String = when (this) {
    is Action.SetToggle -> "${target.label} ${if (enabled) "ON" else "OFF"}"
    is Action.LaunchApp -> "Open $label"
}

/** Ready-made rule setups offered in the editor. */
enum class Preset(val label: String, val ruleName: String, val toggles: Map<Toggle, Boolean>, val apps: List<Action.LaunchApp>) {
    CONNECT("Connect everything", "Connect everything", Toggle.entries.associateWith { true }, emptyList()),
    SLEEP(
        "Sleep", "Sleep",
        Toggle.entries.associateWith { false },
        listOf(Action.LaunchApp(STORYTEL_PACKAGE, "Storytel")),
    ),
}

const val STORYTEL_PACKAGE = "grit.storytel.app"
