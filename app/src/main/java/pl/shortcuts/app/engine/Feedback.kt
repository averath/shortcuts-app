package pl.shortcuts.app.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.shortcuts.app.R

/**
 * Tells the user that a rule started and finished, even with the screen on another app:
 *  - start: one short buzz + ongoing "Running …" notification (toast if notifications are blocked)
 *  - end:   two short buzzes (one long buzz on failure) + the same notification updated with the result,
 *           auto-dismissed after [DISMISS_AFTER_MS].
 */
class Feedback(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Rule runs", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shows when an NFC rule starts and finishes"
            setSound(null, null)      // the vibration pattern is the signal
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun started(ruleNames: List<String>) {
        vibrate(longArrayOf(0, 60))
        val title = "Running ${ruleNames.joinToString(", ")}…"
        if (!notify(base(title).setOngoing(true).setProgress(0, 0, true).setContentText("Started"))) toast("▶ $title")
    }

    fun finished(r: ExecutionReport, waitingForPanels: Boolean = false) {
        val ok = r.failed.isEmpty()
        vibrate(if (ok) longArrayOf(0, 60, 120, 60) else longArrayOf(0, 400))
        val title = "${r.ruleNames.joinToString(", ")} ${if (ok) "finished" else "finished with errors"}"
        val text = buildString {
            if (r.done.isNotEmpty()) append("✓ ").append(r.done.joinToString(", "))
            if (r.failed.isNotEmpty()) { if (isNotEmpty()) append("\n"); append("✗ ").append(r.failed.joinToString(", ")) }
            if (waitingForPanels) { if (isNotEmpty()) append("\n"); append("Toggles confirmed in system panels (enable Shizuku for silent mode)") }
        }
        val shown = notify(
            base(title).setContentText(text.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setTimeoutAfter(DISMISS_AFTER_MS),
        )
        if (!shown) toast("■ $title\n$text")
    }

    private fun base(title: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_nfc)
        .setContentTitle(title)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)

    /** @return false when the user hasn't allowed notifications, so the caller can fall back to a toast. */
    private fun notify(builder: NotificationCompat.Builder): Boolean {
        val nm = NotificationManagerCompat.from(context)
        val allowed = nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!allowed) return false
        return runCatching { nm.notify(NOTIFICATION_ID, builder.build()) }.isSuccess
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val CHANNEL = "rule_runs"
        const val NOTIFICATION_ID = 1
        const val DISMISS_AFTER_MS = 8_000L
    }
}
