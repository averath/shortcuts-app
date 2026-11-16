package pl.shortcuts.app.shizuku

import kotlin.system.exitProcess

/**
 * Runs inside a separate process started by Shizuku with shell (adb) privileges,
 * which is what allows `svc wifi|bluetooth|data` to work without root.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return "$code\n$output"
    }
}
