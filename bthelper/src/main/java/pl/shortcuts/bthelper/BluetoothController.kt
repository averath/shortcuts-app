package pl.shortcuts.bthelper

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock

data class SwitchResult(val ok: Boolean, val enabled: Boolean?, val error: String? = null)

/**
 * Switches Bluetooth with the classic BluetoothAdapter API.
 * Works only because this APK targets API 32 (see bthelper/build.gradle.kts).
 */
class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? get() =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun isEnabled(): Boolean? = if (hasPermission()) adapter?.isEnabled else null

    /**
     * Requests the new state and blocks (max [timeoutMs]) until the adapter reports it.
     * Call from a background/binder thread, never from the main thread.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun setEnabled(on: Boolean, timeoutMs: Long = 5_000): SwitchResult {
        val bt = adapter ?: return SwitchResult(false, null, "no Bluetooth adapter")
        if (!hasPermission()) return SwitchResult(false, null, "helper lacks BLUETOOTH_CONNECT – open the helper app once")
        if (bt.isEnabled == on) return SwitchResult(true, on)

        val accepted = try {
            if (on) bt.enable() else bt.disable()
        } catch (e: SecurityException) {
            return SwitchResult(false, bt.isEnabled, "SecurityException: ${e.message}")
        }
        if (!accepted) return SwitchResult(false, bt.isEnabled, "request rejected by the system")

        // enable()/disable() only start the transition – wait for STATE_ON / STATE_OFF.
        val target = if (on) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (bt.state == target) return SwitchResult(true, on)
            SystemClock.sleep(100)
        }
        return SwitchResult(false, bt.isEnabled, "timed out waiting for Bluetooth ${if (on) "on" else "off"}")
    }
}
