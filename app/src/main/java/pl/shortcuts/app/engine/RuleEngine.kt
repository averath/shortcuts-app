package pl.shortcuts.app.engine

import pl.shortcuts.app.data.RuleRepository
import pl.shortcuts.app.model.Rule
import pl.shortcuts.app.model.Trigger
import pl.shortcuts.app.model.TriggerEvent

class RuleEngine(
    private val repository: RuleRepository,
    val executor: ActionExecutor,
) {
    fun matching(event: TriggerEvent): List<Rule> = repository.rules.value.filter { rule ->
        when (event) {
            is TriggerEvent.Manual -> rule.id == event.ruleId
            is TriggerEvent.NfcTagScanned -> rule.enabled &&
                (rule.trigger as? Trigger.NfcTag)?.uid.equals(event.uid, ignoreCase = true)
        }
    }

    /** Executes every matching rule; returns a report that the UI can show / act upon. */
    suspend fun handle(event: TriggerEvent): ExecutionReport {
        val rules = matching(event)
        return executor.execute(rules.flatMap { it.actions }, rules.map { it.name })
    }
}
