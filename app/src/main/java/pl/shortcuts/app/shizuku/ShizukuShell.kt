package pl.shortcuts.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.shortcuts.app.BuildConfig
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuState { NOT_RUNNING, NO_PERMISSION, READY }

data class ShellResult(val ok: Boolean, val output: String)

class ShizukuShell(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var service: IShellService? = null
    private val bindLock = Mutex()

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { service = null; refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun refresh() {
        _state.value = when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
    }

    /** On a cold start (NFC wakes the app) the Shizuku binder arrives asynchronously – wait briefly. */
    suspend fun awaitReady(timeoutMs: Long = 1500): Boolean {
        refresh()
        return withTimeoutOrNull(timeoutMs) {
            state.first { it == ShizukuState.READY }
        } != null
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val raw = requireService().exec(command)
            val code = raw.substringBefore('\n').toIntOrNull() ?: -1
            ShellResult(code == 0, raw.substringAfter('\n'))
        }.getOrElse { ShellResult(false, it.message ?: it.javaClass.simpleName) }
    }

    private suspend fun requireService(): IShellService = bindLock.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(5000) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val s = binder?.takeIf { it.pingBinder() }?.let(IShellService.Stub::asInterface)
                            service = s
                            if (cont.isActive && s != null) cont.resume(s)
                        }
                        override fun onServiceDisconnected(name: ComponentName?) { service = null }
                    }
                    Shizuku.bindUserService(args, conn)
                }
            }
        } ?: error("Shizuku shell service did not start")
    }

    companion object { const val REQUEST_CODE = 4711 }
}
