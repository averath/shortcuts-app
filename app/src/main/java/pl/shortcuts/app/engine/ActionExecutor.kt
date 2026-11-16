package pl.shortcuts.app.engine

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import pl.shortcuts.app.model.Action
import pl.shortcuts.app.model.Toggle
import pl.shortcuts.app.shizuku.ShizukuShell

data class ExecutionReport(
    val ruleNames: List<String>,
    val done: List<String>,
    val failed: List<String>,
    /** Screens the user must confirm because Android forbids silent toggling without Shizuku. */
    val pendingIntents: List<Intent>,
    /** Apps to open once all toggles/panels are handled (started from the foreground trigger activity). */
    val launchIntents: List<Intent> = emptyList(),
)

/**
 * Two execution strategies:
 *  1. Shizuku available + permitted → silent `svc …` shell commands (works for all three toggles).
 *  2. Otherwise → public API fallback: system dialog/panel the user taps once.
 *     (Since Android 10 / API 29+ targets, apps may not toggle Wi-Fi/Bluetooth silently,
 *     and mobile data has never been toggleable by normal apps.)
 */
class ActionExecutor(private val context: Context, private val shell: ShizukuShell) {

    suspend fun execute(actions: List<Action>, ruleNames: List<String>): ExecutionReport {
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val intents = linkedMapOf<String, Intent>() // de-duplicated by key, kept in order

        val launches = mutableListOf<Intent>()
        val needsShell = actions.any { it is Action.SetToggle }
        val useShell = needsShell && shell.awaitReady()
        for (action in actions) {
            when (action) {
                is Action.SetToggle -> {
                    val label = "${action.target.label} ${if (action.enabled) "on" else "off"}"
                    if (isAlready(action.target, action.enabled)) { done += "$label (already)"; continue }
                    if (useShell) {
                        val result = shell.exec(shellCommand(action.target, action.enabled))
                        if (result.ok) done += label else failed += "$label: ${result.output.trim().take(80)}"
                    } else {
                        fallbackIntent(action.target, action.enabled)?.let { (key, intent) -> intents[key] = intent }
                            ?: run { failed += "$label: not possible without Shizuku" }
                    }
                }
                is Action.LaunchApp -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    if (intent == null) failed += "${action.label}: not installed"
                    else {
                        launches += intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        done += "open ${action.label}"
                    }
                }
            }
        }
        return ExecutionReport(ruleNames, done, failed, intents.values.toList(), launches)
    }

    private fun shellCommand(target: Toggle, on: Boolean): String {
        val v = if (on) "enable" else "disable"
        return when (target) {
            Toggle.WIFI -> "svc wifi $v"
            // `cmd bluetooth_manager` is the modern path (Android 13+); `svc bluetooth` covers older releases.
            Toggle.BLUETOOTH -> "cmd bluetooth_manager $v || svc bluetooth $v"
            Toggle.MOBILE_DATA -> "svc data $v"
        }
    }

    /** Best-effort current-state check so we don't run needless commands or open needless dialogs. */
    private fun isAlready(target: Toggle, on: Boolean): Boolean = when (target) {
        Toggle.WIFI -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled == on
        Toggle.BLUETOOTH -> bluetoothAdapter()?.isEnabled == on
        Toggle.MOBILE_DATA -> mobileDataEnabled() == on
    }

    private fun fallbackIntent(target: Toggle, on: Boolean): Pair<String, Intent>? = when (target) {
        Toggle.BLUETOOTH ->
            if (on && hasBtConnect()) "bt" to Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            else "bt" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        Toggle.WIFI -> "wifi" to Intent(Settings.Panel.ACTION_WIFI)
        // The Internet panel exposes both the Wi-Fi and mobile data switches.
        Toggle.MOBILE_DATA -> "internet" to Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    }

    /** Needs only ACCESS_NETWORK_STATE, but some OEM builds still throw – treat that as "unknown". */
    private fun mobileDataEnabled(): Boolean? = try {
        context.getSystemService(TelephonyManager::class.java)?.isDataEnabled
    } catch (_: SecurityException) {
        null
    }

    private fun hasBtConnect() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    private fun bluetoothAdapter(): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
}
