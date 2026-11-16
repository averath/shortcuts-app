package pl.shortcuts.bthelper

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Minimal, dependency-free setup screen: grant BLUETOOTH_CONNECT once and test the switch.
 * After that the helper works invisibly – NFC Shortcuts calls it in the background.
 */
class SetupActivity : Activity() {

    private val controller by lazy { BluetoothController(this) }
    private lateinit var status: TextView
    private lateinit var grant: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f }
        grant = Button(this).apply {
            text = "Grant Bluetooth permission"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ) }
        }
        val off = Button(this).apply { text = "Test: Bluetooth OFF"; setOnClickListener { test(false) } }
        val on = Button(this).apply { text = "Test: Bluetooth ON"; setOnClickListener { test(true) } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            addView(TextView(this@SetupActivity).apply {
                text = "NFC Shortcuts – Bluetooth helper"
                textSize = 20f
            })
            addView(status)
            addView(grant)
            addView(off)
            addView(on)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render(message: String? = null) {
        val ready = controller.hasPermission()
        grant.visibility = if (ready || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Button.GONE else Button.VISIBLE
        status.text = buildString {
            append(if (ready) "\nReady. NFC Shortcuts can now switch Bluetooth silently.\n"
                   else "\nNeeds the \"Nearby devices\" (Bluetooth) permission.\n")
            controller.isEnabled()?.let { append("Bluetooth is currently ${if (it) "ON" else "OFF"}.\n") }
            message?.let { append("\n$it\n") }
        }
    }

    private fun test(on: Boolean) {
        render("Switching…")
        thread {
            val r = controller.setEnabled(on)
            runOnUiThread { render(if (r.ok) "OK – Bluetooth ${if (on) "ON" else "OFF"}" else "Failed: ${r.error}") }
        }
    }

    private companion object { const val REQ = 1 }
}
