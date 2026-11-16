package pl.shortcuts.app

import android.app.Application
import pl.shortcuts.app.data.RuleRepository
import pl.shortcuts.app.engine.ActionExecutor
import pl.shortcuts.app.engine.Feedback
import pl.shortcuts.app.engine.RuleEngine
import pl.shortcuts.app.helper.BluetoothHelperClient
import pl.shortcuts.app.shizuku.ShizukuShell

/** Tiny manual DI container – enough for an app of this size. */
class ShortcutsApp : Application() {
    lateinit var repository: RuleRepository private set
    lateinit var shell: ShizukuShell private set
    lateinit var btHelper: BluetoothHelperClient private set
    lateinit var engine: RuleEngine private set
    lateinit var feedback: Feedback private set

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository(this)
        shell = ShizukuShell(this)
        btHelper = BluetoothHelperClient(this)
        engine = RuleEngine(repository, ActionExecutor(this, shell, btHelper))
        feedback = Feedback(this).also { it.createChannel() }
    }
}

val android.content.Context.app: ShortcutsApp get() = applicationContext as ShortcutsApp
