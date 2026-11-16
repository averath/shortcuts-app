package pl.shortcuts.app.ui

import android.Manifest
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import pl.shortcuts.app.app

class MainActivity : ComponentActivity() {

    /** Tags read while the app is in the foreground (used by the "Scan tag" button in the editor). */
    private val scannedTags = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val nfc: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Bluetooth state checks (12+) and the start/finish notifications (13+).
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.takeIf { it.isNotEmpty() }?.let { permissions.launch(it.toTypedArray()) }
        val app = app
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                RulesScreen(
                    repository = app.repository,
                    shell = app.shell,
                    scannedTags = scannedTags,
                    nfcState = when {
                        nfc == null -> NfcState.UNSUPPORTED
                        nfc?.isEnabled == false -> NfcState.DISABLED
                        else -> NfcState.OK
                    },
                    onRun = { NfcTriggerActivity.runRule(this, it) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.shell.refresh()
        // While the app is open, read tags directly instead of letting the system dispatch them.
        nfc?.enableReaderMode(
            this,
            { tag -> scannedTags.tryEmit(tag.id.toHex()) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
    }
}

enum class NfcState { OK, DISABLED, UNSUPPORTED }
