package pl.shortcuts.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.shortcuts.app.model.Rule

/** Rules persisted as one JSON document in SharedPreferences; exposed as a StateFlow. */
class RuleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    fun upsert(rule: Rule) = mutate { list ->
        if (list.any { it.id == rule.id }) list.map { if (it.id == rule.id) rule else it } else list + rule
    }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun setEnabled(id: String, enabled: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    private fun mutate(block: (List<Rule>) -> List<Rule>) {
        _rules.update(block)
        prefs.edit().putString(KEY, json.encodeToString(_rules.value)).apply()
    }

    private fun load(): List<Rule> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<Rule>>(it) }.getOrNull() }
            ?: emptyList()

    private companion object { const val KEY = "rules_json" }
}
