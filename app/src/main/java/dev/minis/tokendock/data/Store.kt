package dev.minis.tokendock.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

val Context.dockDataStore by preferencesDataStore("tokendock")

private val KEY_OC_KEY = stringPreferencesKey("opencode_key")
private val KEY_GLM_KEY = stringPreferencesKey("glm_key")
private val KEY_INTERVAL = intPreferencesKey("interval_minutes")
private val KEY_OC_SNAPSHOT = stringPreferencesKey("oc_snapshot_json")
private val KEY_GLM_SNAPSHOT = stringPreferencesKey("glm_snapshot_json")

object Store {

    suspend fun read(context: Context): DockState = with(context.dockDataStore.data.first()) {
        DockState(
            opencodeKey = this[KEY_OC_KEY] ?: "",
            glmKey = this[KEY_GLM_KEY] ?: "",
            intervalMinutes = this[KEY_INTERVAL] ?: 60,
            opencode = this[KEY_OC_SNAPSHOT]?.let { decodeSnapshot(it) },
            glm = this[KEY_GLM_SNAPSHOT]?.let { decodeSnapshot(it) },
        )
    }

    suspend fun saveKeys(context: Context, ocKey: String, glmKey: String) {
        context.dockDataStore.edit {
            it[KEY_OC_KEY] = ocKey.trim()
            it[KEY_GLM_KEY] = glmKey.trim()
        }
    }

    suspend fun saveInterval(context: Context, minutes: Int) {
        context.dockDataStore.edit { it[KEY_INTERVAL] = minutes.coerceIn(15, 720) }
    }

    suspend fun saveSnapshot(context: Context, snapshot: ProviderSnapshot) {
        context.dockDataStore.edit {
            val key = if (snapshot.providerId == "opencode") KEY_OC_SNAPSHOT else KEY_GLM_SNAPSHOT
            it[key] = encodeSnapshot(snapshot)
        }
    }
}

/** 手工 JSON 序列化（org.json），避免序列化插件依赖 */
internal fun encodeSnapshot(s: ProviderSnapshot): String {
    val root = JSONObject()
    root.put("providerId", s.providerId)
    root.put("ok", s.ok)
    s.errorMessage?.let { root.put("errorMessage", it) }
    root.put("fetchedAtMillis", s.fetchedAtMillis)
    fun progress(name: String, p: Progress?) {
        p?.let {
            root.put(name, JSONObject().put("percent", it.percent).put("resetsAtMillis", it.resetsAtMillis ?: JSONObject.NULL))
        }
    }
    progress("ocRolling", s.ocRolling)
    progress("ocWeekly", s.ocWeekly)
    progress("ocMonthly", s.ocMonthly)
    s.glmLevel?.let { root.put("glmLevel", it) }
    progress("glmTokens5h", s.glmTokens5h)
    progress("glmMcpMonthly", s.glmMcpMonthly)
    if (s.glmMcpDetails.isNotEmpty()) {
        root.put("glmMcpDetails", org.json.JSONArray().apply {
            s.glmMcpDetails.forEach { (code, used) -> put(JSONObject().put("code", code).put("used", used)) }
        })
    }
    return root.toString()
}

internal fun decodeSnapshot(json: String): ProviderSnapshot? = runCatching {
    val root = JSONObject(json)
    fun progress(name: String): Progress? = root.optJSONObject(name)?.let {
        Progress(
            percent = it.optInt("percent"),
            resetsAtMillis = if (it.isNull("resetsAtMillis")) null else it.optLong("resetsAtMillis"),
        )
    }
    ProviderSnapshot(
        providerId = root.optString("providerId"),
        ok = root.optBoolean("ok"),
        errorMessage = root.optString("errorMessage").takeIf { it.isNotBlank() },
        fetchedAtMillis = root.optLong("fetchedAtMillis"),
        ocRolling = progress("ocRolling"),
        ocWeekly = progress("ocWeekly"),
        ocMonthly = progress("ocMonthly"),
        glmLevel = root.optString("glmLevel").takeIf { it.isNotBlank() },
        glmTokens5h = progress("glmTokens5h"),
        glmMcpMonthly = progress("glmMcpMonthly"),
        glmMcpDetails = root.optJSONArray("glmMcpDetails")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                o.optString("code").takeIf { it.isNotBlank() }?.let { it to o.optInt("used") }
            }
        } ?: emptyList(),
    )
}.getOrNull()
