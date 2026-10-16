package de.questkeep.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A lightweight, local-only audit trail. Does not alter the existing Quest JSON format. */
data class QuestActivity(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val questId: String,
    val questTitle: String,
    val action: String,
    val detail: String = ""
) {
    fun toJson() = JSONObject()
        .put("id", id).put("timestamp", timestamp).put("questId", questId)
        .put("questTitle", questTitle).put("action", action).put("detail", detail)

    companion object {
        fun fromJson(o: JSONObject) = QuestActivity(
            id = o.optString("id", UUID.randomUUID().toString()),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            questId = o.optString("questId"),
            questTitle = o.optString("questTitle"),
            action = o.optString("action"),
            detail = o.optString("detail")
        )
    }
}

class QuestActivityHistory(context: Context) {
    private val prefs = context.getSharedPreferences("sidequests_data", Context.MODE_PRIVATE)
    private val key = "activity_history"

    @Synchronized
    fun all(): List<QuestActivity> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until array.length()).map { QuestActivity.fromJson(array.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(questId: String, title: String, action: String, detail: String = "") {
        val next = (listOf(QuestActivity(questId = questId, questTitle = title, action = action, detail = detail)) + all())
            .take(MAX_ENTRIES)
        prefs.edit().putString(key, JSONArray().also { a -> next.forEach { a.put(it.toJson()) } }.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val MAX_ENTRIES = 1000
        fun formatTimestamp(timestamp: Long): String =
            SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(timestamp))
    }
}
