package com.focuslamp.app.data.family

import android.content.Context
import android.os.Build
import android.util.Log
import com.focuslamp.app.BuildConfig
import com.focuslamp.app.data.rules.BlockDecision
import com.focuslamp.app.data.rules.Grant
import com.focuslamp.app.data.rules.ParentPinManager
import com.focuslamp.app.data.rules.RulesStore
import com.focuslamp.app.data.rules.Schedule
import com.focuslamp.app.data.tracking.AppInfoCache
import com.focuslamp.app.data.tracking.UsageSnapshot
import com.focuslamp.app.util.DayClock
import com.focuslamp.app.util.Format
import com.focuslamp.app.util.Notifications
import com.focuslamp.app.util.SettingsManager
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date

/**
 * Connects a student's phone and a parent's phone through Firebase.
 *
 * Data layout (see firestore.rules):
 *   pairingCodes/{code}                       short-lived 6-digit join codes
 *   families/{f}/members/{uid}                role, name, push tokens
 *   families/{f}/children/{uid}               status the student's phone reports
 *   families/{f}/children/{uid}/control/rules parent limits, schedules, PIN hash
 *   families/{f}/requests/{id}                "more time" requests and answers
 */
class FamilyRepository(
    private val context: Context,
    private val settings: SettingsManager,
    private val rules: RulesStore,
    private val pin: ParentPinManager,
    private val appInfo: AppInfoCache
) {

    /** False until a Firebase project is added (app/google-services.json). */
    val isAvailable: Boolean by lazy {
        BuildConfig.FAMILY_BACKEND && runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    val isPaired: Boolean get() = settings.familyId != null

    private val _myRequests = MutableStateFlow<List<TimeRequest>>(emptyList())
    val myRequests: StateFlow<List<TimeRequest>> = _myRequests.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val childListeners = mutableListOf<ListenerRegistration>()

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private fun family(id: String = settings.familyId ?: error("Not paired")) =
        db.collection("families").document(id)

    suspend fun uid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid ?: error("Sign-in failed")
    }

    // =========================== Parent side ===========================

    suspend fun createFamily(parentName: String): Result<String> = runCatching {
        check(isAvailable) { NOT_SET_UP }
        val me = uid()
        val ref = db.collection("families").document()
        ref.set(mapOf("createdBy" to me, "createdAt" to System.currentTimeMillis())).await()
        ref.collection("members").document(me)
            .set(mapOf("role" to "parent", "name" to parentName, "tokens" to emptyList<String>())).await()
        settings.familyId = ref.id
        settings.displayName = parentName
        registerToken(null)
        ref.id
    }

    /** A fresh 6-digit code, valid for 15 minutes. */
    suspend fun createPairingCode(): Result<String> = runCatching {
        val familyId = settings.familyId ?: error("Create your family first")
        val me = uid()
        val random = SecureRandom()
        var lastError: Exception? = null
        repeat(5) {
            val code = (100_000 + random.nextInt(900_000)).toString()
            try {
                db.collection("pairingCodes").document(code).set(
                    mapOf(
                        "familyId" to familyId,
                        "createdBy" to me,
                        "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 15 * 60_000L))
                    )
                ).await()
                return@runCatching code
            } catch (e: Exception) {
                lastError = e // code already taken by someone else; try another
            }
        }
        throw lastError ?: IllegalStateException("Couldn't create a code")
    }

    fun observeChildren(): Flow<List<ChildStatus>> {
        val f = settings.familyId ?: return emptyFlow()
        return callbackFlow {
            val reg = family(f).collection("children").addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "children listener", err); return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { childFrom(it) }?.sortedBy { it.name } ?: emptyList())
            }
            awaitClose { reg.remove() }
        }
    }

    fun observeRequests(): Flow<List<TimeRequest>> {
        val f = settings.familyId ?: return emptyFlow()
        return callbackFlow {
            val reg = family(f).collection("requests").addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "requests listener", err); return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { requestFrom(it) }?.sortedByDescending { it.createdAt } ?: emptyList())
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun resolve(requestId: String, approve: Boolean, minutes: Int): Result<Unit> = runCatching {
        uid()
        family().collection("requests").document(requestId).update(
            mapOf(
                "status" to if (approve) "approved" else "denied",
                "grantedMinutes" to if (approve) minutes else 0,
                "resolvedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    /** Parent unblocks an app without waiting for a request. */
    suspend fun unblock(childUid: String, childName: String, packageName: String, label: String, minutes: Int): Result<Unit> =
        runCatching {
            uid()
            val now = System.currentTimeMillis()
            family().collection("requests").document().set(
                mapOf(
                    "childUid" to childUid, "childName" to childName, "pkg" to packageName, "appLabel" to label,
                    "minutes" to minutes, "reason" to "", "status" to "approved", "grantedMinutes" to minutes,
                    "createdAt" to now, "resolvedAt" to now, "byParent" to true
                )
            ).await()
            Unit
        }

    fun observeRules(childUid: String): Flow<RemoteRules?> {
        val f = settings.familyId ?: return emptyFlow()
        return callbackFlow {
            val reg = rulesDoc(f, childUid).addSnapshotListener { snap, _ ->
                trySend(snap?.let { rulesFrom(it) })
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun saveRules(childUid: String, r: RemoteRules): Result<Unit> = runCatching {
        uid()
        val f = settings.familyId ?: error("Not paired")
        rulesDoc(f, childUid).set(
            mapOf(
                "parentLimits" to r.parentLimits,
                "schedules" to r.schedules.map { scheduleToMap(it) },
                "pinHash" to r.pinHash,
                "pinSalt" to r.pinSalt,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    private fun rulesDoc(f: String, childUid: String) =
        family(f).collection("children").document(childUid).collection("control").document("rules")

    // =========================== Student side ===========================

    suspend fun join(code: String, name: String): Result<Unit> = runCatching {
        check(isAvailable) { NOT_SET_UP }
        val me = uid()
        val codeDoc = db.collection("pairingCodes").document(code.trim()).get().await()
        val familyId = codeDoc.getString("familyId") ?: error("That code doesn't exist. Check the number on the parent's phone.")
        val expires = codeDoc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        if (expires < System.currentTimeMillis()) error("That code has expired. Ask for a new one.")
        val f = family(familyId)
        f.collection("members").document(me)
            .set(mapOf("role" to "child", "name" to name, "joinCode" to code.trim(), "tokens" to emptyList<String>())).await()
        f.collection("children").document(me).set(
            mapOf("name" to name, "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
        val parent = f.collection("members").whereEqualTo("role", "parent").get().await().documents.firstOrNull()
        settings.familyId = familyId
        settings.displayName = name
        settings.parentName = parent?.getString("name") ?: "your parent"
        settings.lastInstalledPublishDay = ""
        rules.managedRemotely = true
        registerToken(null)
        startChildSync()
    }

    /** Listens for rule changes and answers to requests. Safe to call repeatedly. */
    fun startChildSync() {
        if (!isAvailable || !isPaired || childListeners.isNotEmpty()) return
        val f = settings.familyId ?: return
        val me = auth.currentUser?.uid ?: return
        childListeners += rulesDoc(f, me).addSnapshotListener { snap, err ->
            _connected.value = err == null
            val r = snap?.let { rulesFrom(it) } ?: return@addSnapshotListener
            rules.setParentRules(r.parentLimits, r.schedules)
            rules.managedRemotely = true
            pin.setFromRemote(r.pinHash, r.pinSalt)
        }
        childListeners += family(f).collection("requests").whereEqualTo("childUid", me)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { requestFrom(it) }?.sortedByDescending { it.createdAt } ?: emptyList()
                _myRequests.value = list
                list.forEach { applyAnswer(it) }
            }
    }

    fun stopChildSync() {
        childListeners.forEach { it.remove() }
        childListeners.clear()
    }

    suspend fun requestTime(packageName: String, label: String, minutes: Int, reason: String): Result<String> = runCatching {
        check(isAvailable && isPaired) { "No parent phone is linked" }
        val me = uid()
        val ref = family().collection("requests").document()
        ref.set(
            mapOf(
                "childUid" to me, "childName" to settings.displayName.ifBlank { "Student" },
                "pkg" to packageName, "appLabel" to label, "minutes" to minutes, "reason" to reason,
                "status" to "pending", "createdAt" to System.currentTimeMillis(),
                "resolvedAt" to 0L, "grantedMinutes" to 0, "byParent" to false
            )
        ).await()
        ref.id
    }

    suspend fun publishStatus(
        snapshot: UsageSnapshot,
        budgetMinutes: Int,
        blocks: List<BlockDecision>,
        issues: List<String>
    ) {
        if (!isAvailable || !isPaired) return
        runCatching {
            val me = uid()
            val today = DayClock.dayKey()
            val data = mutableMapOf<String, Any>(
                "name" to settings.displayName,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "lastSeen" to System.currentTimeMillis(),
                "totalMs" to snapshot.totalMs,
                "distractionMs" to snapshot.distractionMs,
                "budgetMinutes" to budgetMinutes,
                "pickups" to snapshot.pickups,
                "protectionOk" to issues.isEmpty(),
                "issues" to issues,
                "apps" to snapshot.apps.take(30).map {
                    mapOf("pkg" to it.packageName, "label" to it.label, "ms" to it.ms, "distracting" to it.distracting)
                },
                "blocked" to blocks.map {
                    mapOf("pkg" to it.packageName, "label" to appInfo.label(it.packageName),
                        "byParent" to it.reason.setByParent, "reason" to it.reason.name)
                }
            )
            // The full app list (for the parent's rule editor) only once a day.
            if (settings.lastInstalledPublishDay != today) {
                data["installed"] = appInfo.launchableApps().values.sortedBy { it.label.lowercase() }
                    .filter { it.packageName != context.packageName }
                    .map { mapOf("pkg" to it.packageName, "label" to it.label) }
            }
            family().collection("children").document(me).set(data, SetOptions.merge()).await()
            if (data.containsKey("installed")) settings.lastInstalledPublishDay = today
            settings.lastStatusPublish = System.currentTimeMillis()
        }.onFailure { Log.w(TAG, "publishStatus", it) }
    }

    /** Called when a push says a request was answered, in case the listener isn't running. */
    fun applyAnswerFromPush(data: Map<String, String>) {
        val id = data["requestId"] ?: return
        val status = when (data["status"]) {
            "approved" -> RequestStatus.APPROVED
            "denied" -> RequestStatus.DENIED
            else -> return
        }
        val minutes = data["minutes"]?.toIntOrNull() ?: 0
        applyAnswer(
            TimeRequest(
                id, "", "", data["pkg"] ?: return, data["appLabel"] ?: "", minutes, "", status,
                0L, System.currentTimeMillis(), minutes, data["byParent"] == "true"
            )
        )
    }

    private fun applyAnswer(r: TimeRequest) {
        if (r.status == RequestStatus.PENDING || rules.isRequestApplied(r.id)) return
        // Don't replay old answers (e.g. after reinstalling) as fresh notifications.
        val fresh = System.currentTimeMillis() - r.resolvedAt < 12 * 60 * 60_000L
        val label = r.appLabel.ifBlank { appInfo.label(r.packageName) }
        if (r.status == RequestStatus.APPROVED && fresh) {
            val applied = rules.applyGrant(
                Grant(r.packageName, r.grantedMinutes, DayClock.dayKey(), System.currentTimeMillis(), r.id)
            )
            if (applied) {
                Notifications.post(
                    context, r.id.hashCode(), Notifications.CHANNEL_FAMILY,
                    "$label is open again",
                    "${settings.parentName.ifBlank { "Your parent" }} gave you ${Format.minutes(r.grantedMinutes)} more."
                )
            }
        } else {
            rules.markRequestApplied(r.id)
            if (r.status == RequestStatus.DENIED && fresh && !r.byParent) {
                Notifications.post(
                    context, r.id.hashCode(), Notifications.CHANNEL_FAMILY,
                    "Request for $label not approved",
                    "${settings.parentName.ifBlank { "Your parent" }} said not this time."
                )
            }
        }
    }

    // =========================== Both ===========================

    suspend fun registerToken(token: String?) {
        if (!isAvailable || !isPaired) return
        runCatching {
            val t = token ?: FirebaseMessaging.getInstance().token.await()
            family().collection("members").document(uid())
                .update("tokens", FieldValue.arrayUnion(t)).await()
        }.onFailure { Log.w(TAG, "registerToken", it) }
    }

    /** Unlinks this phone. Parent rules stay until a parent removes them locally with the PIN. */
    suspend fun leave() {
        stopChildSync()
        if (isAvailable && isPaired) {
            runCatching {
                family().collection("members").document(uid()).delete().await()
            }
        }
        settings.familyId = null
        settings.parentName = ""
        rules.managedRemotely = false
        _myRequests.value = emptyList()
    }

    // =========================== Mapping ===========================

    private fun requestFrom(d: DocumentSnapshot): TimeRequest? {
        val pkg = d.getString("pkg") ?: return null
        return TimeRequest(
            id = d.id,
            childUid = d.getString("childUid") ?: "",
            childName = d.getString("childName") ?: "",
            packageName = pkg,
            appLabel = d.getString("appLabel") ?: pkg,
            minutes = d.getLong("minutes")?.toInt() ?: 0,
            reason = d.getString("reason") ?: "",
            status = when (d.getString("status")) {
                "approved" -> RequestStatus.APPROVED
                "denied" -> RequestStatus.DENIED
                else -> RequestStatus.PENDING
            },
            createdAt = d.getLong("createdAt") ?: 0L,
            resolvedAt = d.getLong("resolvedAt") ?: 0L,
            grantedMinutes = d.getLong("grantedMinutes")?.toInt() ?: 0,
            byParent = d.getBoolean("byParent") ?: false
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun childFrom(d: DocumentSnapshot): ChildStatus? {
        val name = d.getString("name") ?: return null
        fun apps(field: String) = (d.get(field) as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val pkg = m["pkg"] as? String ?: return@mapNotNull null
            RemoteApp(pkg, m["label"] as? String ?: pkg, (m["ms"] as? Number)?.toLong() ?: 0L, m["distracting"] as? Boolean ?: false)
        }
        val blocked = (d.get("blocked") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val pkg = m["pkg"] as? String ?: return@mapNotNull null
            BlockedApp(pkg, m["label"] as? String ?: pkg, m["byParent"] as? Boolean ?: false, m["reason"] as? String ?: "")
        }
        return ChildStatus(
            uid = d.id,
            name = name,
            device = d.getString("device") ?: "",
            lastSeen = d.getLong("lastSeen") ?: 0L,
            totalMs = d.getLong("totalMs") ?: 0L,
            distractionMs = d.getLong("distractionMs") ?: 0L,
            budgetMinutes = d.getLong("budgetMinutes")?.toInt() ?: 0,
            pickups = d.getLong("pickups")?.toInt() ?: 0,
            protectionOk = d.getBoolean("protectionOk") ?: true,
            issues = (d.get("issues") as? List<String>).orEmpty(),
            apps = apps("apps"),
            installed = apps("installed"),
            blocked = blocked
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun rulesFrom(d: DocumentSnapshot): RemoteRules? {
        if (!d.exists()) return null
        val limits = (d.get("parentLimits") as? Map<String, Any?>).orEmpty()
            .mapNotNull { (k, v) -> (v as? Number)?.toInt()?.let { k to it } }.toMap()
        val schedules = (d.get("schedules") as? List<Map<String, Any?>>)?.mapNotNull { scheduleFromMap(it) }
            ?: Schedule.defaults()
        return RemoteRules(limits, schedules, d.getString("pinHash"), d.getString("pinSalt"), d.getLong("updatedAt") ?: 0L)
    }

    private fun scheduleToMap(s: Schedule) = mapOf(
        "id" to s.id, "name" to s.name, "enabled" to s.enabled,
        "start" to s.startMinute, "end" to s.endMinute, "days" to s.days.sorted()
    )

    private fun scheduleFromMap(m: Map<String, Any?>): Schedule? {
        val id = m["id"] as? String ?: return null
        return Schedule(
            id, m["name"] as? String ?: id, m["enabled"] as? Boolean ?: false,
            (m["start"] as? Number)?.toInt() ?: 0, (m["end"] as? Number)?.toInt() ?: 0,
            (m["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
        )
    }

    companion object {
        private const val TAG = "FamilyRepo"
        const val NOT_SET_UP = "Family features need a Firebase project. See FAMILY_SETUP.md."
    }
}
