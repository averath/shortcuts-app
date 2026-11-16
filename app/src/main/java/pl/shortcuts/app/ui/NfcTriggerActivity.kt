package pl.shortcuts.app.ui

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pl.shortcuts.app.app
import pl.shortcuts.app.engine.ExecutionReport
import pl.shortcuts.app.model.TriggerEvent

/**
 * Invisible activity launched by the NFC dispatcher (or by "Run now" in the UI).
 * Executes matching rules, then shows any required system panels one after another and finishes.
 */
class NfcTriggerActivity : ComponentActivity() {

    private val queue = ArrayDeque<Intent>()
    private val launches = ArrayDeque<Intent>()
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent) else finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val event = eventFrom(intent) ?: return finish()
        val engine = app.engine
        if (engine.matching(event).isEmpty()) {
            val uid = (event as? TriggerEvent.NfcTagScanned)?.uid
            toast("No rule for tag ${uid?.chunked(2)?.joinToString(":")} – open NFC Shortcuts to add one")
            return finish()
        }
        lifecycleScope.launch {
            val report = engine.handle(event)
            toast(summary(report))
            queue.addAll(report.pendingIntents)
            launches.addAll(report.launchIntents)
            next()
        }
    }

    private fun next() {
        val intent = queue.removeFirstOrNull() ?: return launchAppsAndFinish()
        runCatching { launcher.launch(intent) }.onFailure { next() }
    }

    /** Opened last, so the app (e.g. Storytel) ends up in front after any system panels were confirmed. */
    private fun launchAppsAndFinish() {
        while (launches.isNotEmpty()) runCatching { startActivity(launches.removeFirst()) }
        finish()
    }

    private fun summary(r: ExecutionReport) = buildString {
        append(r.ruleNames.joinToString(", "))
        if (r.done.isNotEmpty()) append("\n✓ ").append(r.done.joinToString(", "))
        if (r.failed.isNotEmpty()) append("\n✗ ").append(r.failed.joinToString(", "))
        if (r.pendingIntents.isNotEmpty()) append("\nConfirm remaining toggles (enable Shizuku for silent mode)")
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_RULE_ID = "rule_id"

        fun runRule(activity: Activity, ruleId: String) = activity.startActivity(
            Intent(activity, NfcTriggerActivity::class.java).putExtra(EXTRA_RULE_ID, ruleId),
        )

        fun eventFrom(intent: Intent): TriggerEvent? {
            intent.getStringExtra(EXTRA_RULE_ID)?.let { return TriggerEvent.Manual(it) }
            val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
            return TriggerEvent.NfcTagScanned(tag.id.toHex())
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
