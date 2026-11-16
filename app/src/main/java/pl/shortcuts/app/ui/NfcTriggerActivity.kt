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
    private var report: ExecutionReport? = null
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
        app.feedback.started(engine.matching(event).map { it.name })
        lifecycleScope.launch {
            val r = engine.handle(event)
            report = r
            queue.addAll(r.pendingIntents)
            launches.addAll(r.launchIntents)
            next()
        }
    }

    /** Safety net: if the system closes us mid-way (e.g. user leaves a panel with Home), still signal the end. */
    override fun onDestroy() {
        if (isFinishing) report?.let { app.feedback.finished(it, waitingForPanels = true) }
        report = null
        super.onDestroy()
    }

    private fun next() {
        val intent = queue.removeFirstOrNull() ?: return launchAppsAndFinish()
        runCatching { launcher.launch(intent) }.onFailure { next() }
    }

    /**
     * Opened last, so the app (e.g. Storytel) ends up in front after any system panels were confirmed.
     * The "finished" signal fires here – only once everything, including the app launch, is done.
     */
    private fun launchAppsAndFinish() {
        val r = report
        val failedLaunches = mutableListOf<String>()
        while (launches.isNotEmpty()) {
            val intent = launches.removeFirst()
            runCatching { startActivity(intent) }.onFailure { failedLaunches += "open ${intent.`package` ?: "app"}: ${it.message?.take(60)}" }
        }
        r?.let { app.feedback.finished(it.copy(failed = it.failed + failedLaunches), waitingForPanels = it.pendingIntents.isNotEmpty()) }
        report = null
        finish()
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
