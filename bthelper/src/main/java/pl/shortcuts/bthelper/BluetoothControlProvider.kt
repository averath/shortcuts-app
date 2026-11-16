package pl.shortcuts.bthelper

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * IPC endpoint used by NFC Shortcuts via ContentResolver.call(). Providers are started on demand
 * by the system (even when the helper process is dead), so no background-start restrictions apply.
 *
 * Methods (constants are mirrored in the main app's BluetoothHelperClient):
 *  - "status"                      → { ok, has_permission, enabled? }
 *  - "set_enabled" extras{enabled} → { ok, enabled?, error? }
 */
class BluetoothControlProvider : ContentProvider() {

    private lateinit var controller: BluetoothController

    override fun onCreate(): Boolean {
        controller = BluetoothController(context!!)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        enforceTrustedCaller()
        return when (method) {
            METHOD_STATUS -> Bundle().apply {
                putBoolean(KEY_OK, true)
                putBoolean(KEY_HAS_PERMISSION, controller.hasPermission())
                controller.isEnabled()?.let { putBoolean(KEY_ENABLED, it) }
            }
            METHOD_SET_ENABLED -> {
                require(extras?.containsKey(KEY_ENABLED) == true) { "missing '$KEY_ENABLED' extra" }
                val result = controller.setEnabled(extras!!.getBoolean(KEY_ENABLED))
                Bundle().apply {
                    putBoolean(KEY_OK, result.ok)
                    result.enabled?.let { putBoolean(KEY_ENABLED, it) }
                    result.error?.let { putString(KEY_ERROR, it) }
                }
            }
            else -> throw IllegalArgumentException("unknown method '$method'")
        }
    }

    /** Only NFC Shortcuts, signed with the same certificate as this helper, may switch Bluetooth. */
    private fun enforceTrustedCaller() {
        val caller = callingPackage
        val pm = context!!.packageManager
        val sameSigner = pm.checkSignatures(Binder.getCallingUid(), Process.myUid()) == PackageManager.SIGNATURE_MATCH
        if (caller !in TRUSTED_CALLERS || !sameSigner) {
            throw SecurityException("caller $caller is not allowed to control Bluetooth")
        }
    }

    // Unused ContentProvider CRUD surface.
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        val TRUSTED_CALLERS = setOf("pl.shortcuts.app")
        const val METHOD_STATUS = "status"
        const val METHOD_SET_ENABLED = "set_enabled"
        const val KEY_OK = "ok"
        const val KEY_ENABLED = "enabled"
        const val KEY_ERROR = "error"
        const val KEY_HAS_PERMISSION = "has_permission"
    }
}
