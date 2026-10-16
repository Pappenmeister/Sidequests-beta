package de.questkeep.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Helpers for import safety. Validate imported JSON before replacing the live dataset,
 * and keep a timestamped copy of the current backup payload in app-private storage.
 */
object SafeQuestBackup {
    data class Validation(val valid: Boolean, val error: String? = null, val root: JSONObject? = null)

    fun validate(input: String): Validation = try {
        val root = JSONObject(input)
        if (!root.has("quests") || root.optJSONArray("quests") == null) {
            Validation(false, "Die Datei enthält kein gültiges 'quests'-Array.")
        } else {
            Validation(true, root = root)
        }
    } catch (e: Exception) {
        Validation(false, "Die JSON-Datei ist beschädigt oder ungültig: ${e.message ?: "unbekannter Fehler"}")
    }

    fun createSafetyCopy(context: Context, currentExportJson: String): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "sidequests-before-import-${System.currentTimeMillis()}.json")
        file.writeText(currentExportJson, Charsets.UTF_8)
        return file
    }

    fun readUtf8(input: InputStream): String = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
