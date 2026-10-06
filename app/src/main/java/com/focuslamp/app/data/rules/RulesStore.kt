package com.focuslamp.app.data.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything that decides what gets blocked, stored on the phone so blocking keeps working offline.
 * Parent rules arrive here either from the parent's phone (family sync) or from
 * "Parent controls on this phone" behind the parent PIN.
 */
class RulesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_lamp_rules", Context.MODE_PRIVATE)

    /** Bumps on every change so screens and the blocker can react. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    @Synchronized
    fun snapshot(dayKey: String, budgetMinutes: Int) = RulesSnapshot(
        dayKey = dayKey,
        selfLimits = selfLimits(),
        parentLimits = parentLimits(),
        dailyBudgetMinutes = budgetMinutes,
        blockWhenBudgetUsed = blockWhenBudgetUsed,
        schedules = schedules(),
        grants = grants().filter { it.dayKey == dayKey },
        holds = holds()
    )

    // ---- Student's own rules ----

    fun selfLimits(): Map<String, Int> = readMap(KEY_SELF)

    @Synchronized
    fun setSelfLimit(packageName: String, minutes: Int?) {
        val m = selfLimits().toMutableMap()
        if (minutes == null || minutes <= 0) m.remove(packageName) else m[packageName] = minutes
        writeMap(KEY_SELF, m)
    }

    var blockWhenBudgetUsed: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_ON_BUDGET, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BLOCK_ON_BUDGET, value).apply(); bump()
        }

    // ---- Parent rules ----

    fun parentLimits(): Map<String, Int> = readMap(KEY_PARENT)

    fun schedules(): List<Schedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null) ?: return Schedule.defaults()
        return runCatching { schedulesFromJson(JSONArray(raw)) }.getOrDefault(Schedule.defaults())
    }

    /** True once a parent phone manages this student; local parent editing is then turned off. */
    var managedRemotely: Boolean
        get() = prefs.getBoolean(KEY_REMOTE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REMOTE, value).apply(); bump()
        }

    @Synchronized
    fun setParentRules(limits: Map<String, Int>, schedules: List<Schedule>) {
        writeMap(KEY_PARENT, limits.filterValues { it > 0 }, notify = false)
        prefs.edit().putString(KEY_SCHEDULES, schedulesToJson(schedules).toString()).apply()
        // A hold only makes sense while the app still has a parent limit.
        val keep = holds().filter { it in limits }.toSet()
        prefs.edit().putStringSet(KEY_HOLDS, HashSet(keep)).apply()
        bump()
    }

    // ---- Holds: parent-limited apps that stay blocked until a parent lifts them ----

    fun holds(): Set<String> = prefs.getStringSet(KEY_HOLDS, emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun addHold(packageName: String) {
        val h = holds()
        if (packageName in h) return
        prefs.edit().putStringSet(KEY_HOLDS, HashSet(h + packageName)).apply()
        bump()
    }

    @Synchronized
    fun removeHold(packageName: String) {
        val h = holds()
        if (packageName !in h) return
        prefs.edit().putStringSet(KEY_HOLDS, HashSet(h - packageName)).apply()
        bump()
    }

    // ---- Grants (approved extra time) ----

    fun grants(): List<Grant> {
        val raw = prefs.getString(KEY_GRANTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Grant(
                    o.getString("pkg"), o.getInt("min"), o.getString("day"), o.getLong("at"),
                    o.optString("req").ifEmpty { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isRequestApplied(requestId: String) =
        prefs.getStringSet(KEY_APPLIED, emptySet())?.contains(requestId) == true

    /**
     * Applies a parent's approval: adds the time for today and lifts the hold.
     * Idempotent per request id, so the same approval arriving by push and by sync counts once.
     * Returns false if it was already applied.
     */
    @Synchronized
    fun applyGrant(grant: Grant): Boolean {
        grant.requestId?.let { if (isRequestApplied(it)) return false }
        val list = grants().filter { it.dayKey == grant.dayKey } + grant
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("pkg", it.packageName).put("min", it.minutes).put("day", it.dayKey)
                .put("at", it.grantedAt).put("req", it.requestId ?: ""))
        }
        val edit = prefs.edit().putString(KEY_GRANTS, arr.toString())
        grant.requestId?.let {
            val applied = (prefs.getStringSet(KEY_APPLIED, emptySet()) ?: emptySet()).toMutableList()
            applied += it
            edit.putStringSet(KEY_APPLIED, HashSet(applied.takeLast(300)))
        }
        edit.putStringSet(KEY_HOLDS, HashSet(holds() - grant.packageName))
        edit.apply()
        bump()
        return true
    }

    @Synchronized
    fun markRequestApplied(requestId: String) {
        val applied = (prefs.getStringSet(KEY_APPLIED, emptySet()) ?: emptySet()).toMutableList()
        if (requestId in applied) return
        applied += requestId
        prefs.edit().putStringSet(KEY_APPLIED, HashSet(applied.takeLast(300))).apply()
    }

    @Synchronized
    fun clearParentState() {
        prefs.edit().remove(KEY_PARENT).remove(KEY_SCHEDULES).remove(KEY_HOLDS).remove(KEY_GRANTS)
            .putBoolean(KEY_REMOTE, false).apply()
        bump()
    }

    // ---- helpers ----

    private fun readMap(key: String): Map<String, Int> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    private fun writeMap(key: String, map: Map<String, Int>, notify: Boolean = true) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
        if (notify) bump()
    }

    private fun bump() {
        _version.value = _version.value + 1
    }

    companion object {
        private const val KEY_SELF = "self_limits"
        private const val KEY_PARENT = "parent_limits"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_GRANTS = "grants"
        private const val KEY_HOLDS = "holds"
        private const val KEY_APPLIED = "applied_requests"
        private const val KEY_REMOTE = "managed_remotely"
        private const val KEY_BLOCK_ON_BUDGET = "block_on_budget"

        fun schedulesToJson(list: List<Schedule>): JSONArray {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject().put("id", s.id).put("name", s.name).put("enabled", s.enabled)
                        .put("start", s.startMinute).put("end", s.endMinute)
                        .put("days", JSONArray(s.days.sorted()))
                )
            }
            return arr
        }

        fun schedulesFromJson(arr: JSONArray): List<Schedule> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val d = o.getJSONArray("days")
            Schedule(
                o.getString("id"), o.getString("name"), o.getBoolean("enabled"),
                o.getInt("start"), o.getInt("end"), (0 until d.length()).map { d.getInt(it) }.toSet()
            )
        }
    }
}
