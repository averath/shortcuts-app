package pl.shortcuts.app.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import pl.shortcuts.app.shizuku.ShellResult

enum class HelperState { NOT_INSTALLED, NO_PERMISSION, REJECTED, READY }

/**
 * Talks to the companion "BT helper" APK (module :bthelper, targetSdk 32), which may still use
 * BluetoothAdapter.enable()/disable(). Works without Shizuku and survives reboots.
 */
class BluetoothHelperClient(private val context: Context) {

    private val _state = MutableStateFlow(HelperState.NOT_INSTALLED)
    val state: StateFlow<HelperState> = _state.asStateFlow()

    /** Cheap IPC – fine to call from onResume. */
    fun refresh(): HelperState = call(METHOD_STATUS).let { (state, bundle) ->
        val s = when {
            state != HelperState.READY -> state
            bundle?.getBoolean(KEY_HAS_PERMISSION) != true -> HelperState.NO_PERMISSION
            else -> HelperState.READY
        }
        _state.value = s
        s
    }

    /** Blocks until Bluetooth reached the requested state (helper waits up to 5 s). */
    suspend fun setEnabled(on: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val (state, bundle) = call(METHOD_SET_ENABLED, Bundle().apply { putBoolean(KEY_ENABLED, on) })
        when {
            state == HelperState.NOT_INSTALLED -> ShellResult(false, "BT helper not installed")
            state == HelperState.REJECTED -> ShellResult(false, "BT helper rejected the call (different signing key?)")
            bundle?.getBoolean(KEY_OK) == true -> ShellResult(true, "")
            else -> ShellResult(false, bundle?.getString(KEY_ERROR) ?: "BT helper failed")
        }
    }

    /** Opens the helper's setup screen (grant permission / test). */
    fun setupIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(HELPER_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun call(method: String, extras: Bundle? = null): Pair<HelperState, Bundle?> = try {
        val result = context.contentResolver.call(AUTHORITY_URI, method, null, extras)
        if (result == null) HelperState.NOT_INSTALLED to null else HelperState.READY to result
    } catch (_: SecurityException) {
        HelperState.REJECTED to null
    } catch (_: IllegalArgumentException) {
        // Thrown by the framework when no provider with this authority exists.
        HelperState.NOT_INSTALLED to null
    } catch (_: Exception) {
        HelperState.NOT_INSTALLED to null
    }

    companion object {
        const val HELPER_PACKAGE = "pl.shortcuts.bthelper"
        private val AUTHORITY_URI: Uri = Uri.parse("content://pl.shortcuts.bthelper.control")
        // Mirrors pl.shortcuts.bthelper.BluetoothControlProvider.
        private const val METHOD_STATUS = "status"
        private const val METHOD_SET_ENABLED = "set_enabled"
        private const val KEY_OK = "ok"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ERROR = "error"
        private const val KEY_HAS_PERMISSION = "has_permission"
    }
}
