package de.questkeep.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Ink = Color(0xFFE9D9B5)
private val Parchment = Color(0xFFF0E3C5)
private val Leather = Color(0xFF211A14)
private val Panel = Color(0xFF33271D)
private val Gold = Color(0xFFC69A48)
private val Muted = Color(0xFFB8A17B)
private val Green = Color(0xFF8DAD79)

data class Objective(val id: String = UUID.randomUUID().toString(), val text: String, val done: Boolean = false)
data class JournalEntry(val id: String = UUID.randomUUID().toString(), val date: Long = System.currentTimeMillis(), val text: String)
data class Quest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val main: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val due: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val category: String = "Allgemein",
    val recurring: Boolean = false,
    val objectives: List<Objective> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val progress: Int = 0
)

private fun Quest.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("description", description); put("main", main)
    put("created", created); put("due", due ?: JSONObject.NULL); put("completed", completed)
    put("completedAt", completedAt ?: JSONObject.NULL); put("category", category); put("recurring", recurring)
    put("progress", progress)
    put("objectives", JSONArray().also { a -> objectives.forEach { o -> a.put(JSONObject().put("id",o.id).put("text",o.text).put("done",o.done)) } })
    put("journal", JSONArray().also { a -> journal.forEach { j -> a.put(JSONObject().put("id",j.id).put("date",j.date).put("text",j.text)) } })
}
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.toQuest(): Quest {
    val os = mutableListOf<Objective>()
    val oa = optJSONArray("objectives") ?: JSONArray()
    for (i in 0 until oa.length()) { val o = oa.getJSONObject(i); os += Objective(o.optString("id", UUID.randomUUID().toString()), o.optString("text"), o.optBoolean("done")) }
    val js = mutableListOf<JournalEntry>()
    val ja = optJSONArray("journal") ?: JSONArray()
    for (i in 0 until ja.length()) { val j = ja.getJSONObject(i); js += JournalEntry(j.optString("id", UUID.randomUUID().toString()), j.optLong("date"), j.optString("text")) }
    return Quest(optString("id", UUID.randomUUID().toString()), optString("title"), optString("description"), optBoolean("main"),
        optLong("created", System.currentTimeMillis()), longOrNull("due"), optBoolean("completed"), longOrNull("completedAt"),
        optString("category", "Allgemein"), optBoolean("recurring"), os, js, optInt("progress"))
}
private fun stamp(time: Long?): String = time?.let { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(it)) } ?: "Kein Termin"

private class QuestStore(context: Context) {
    private val prefs = context.getSharedPreferences("sidequests_data", Context.MODE_PRIVATE)
    fun load(): List<Quest> {
        val raw = prefs.getString("quests", null) ?: return emptyList()
        return try { val a = JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).toQuest() } } catch (_: Exception) { emptyList() }
    }
    fun save(quests: List<Quest>) { prefs.edit().putString("quests", JSONArray().also { a -> quests.forEach { a.put(it.toJson()) } }.toString()).apply() }
    fun categories(): List<String> = prefs.getString("categories", "Allgemein|Arbeit|Persönlich|Gesundheit|Lernen").orEmpty().split("|").filter { it.isNotBlank() }
    fun saveCategories(c: List<String>) { prefs.edit().putString("categories", c.distinct().joinToString("|")).apply() }
    fun dark(): Boolean = prefs.getBoolean("dark", true)
    fun setDark(v: Boolean) { prefs.edit().putBoolean("dark", v).apply() }
    fun selectedTheme(): QuestTheme = QuestTheme.fromKey(prefs.getString("selected_theme", null)
        ?: if (dark()) QuestTheme.PARCHMENT_NIGHT.storageKey else QuestTheme.CLASSIC_PARCHMENT.storageKey)
    fun setSelectedTheme(theme: QuestTheme) { prefs.edit().putString("selected_theme", theme.storageKey).putBoolean("dark", theme.isDark).apply() }
}

private enum class Page(val label: String) { JOURNAL("Chronik"), MAIN("Hauptquests"), SIDE("Nebenquests"), DONE("Abgeschlossen"), SETTINGS("Einstellungen") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SideQuestsApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideQuestsApp() {
    val context = LocalContext.current
    val store = remember { QuestStore(context) }
    var quests by remember { mutableStateOf(store.load()) }
    var categories by remember { mutableStateOf(store.categories()) }
    var dark by remember { mutableStateOf(store.dark()) }
    var selectedTheme by remember { mutableStateOf(store.selectedTheme()) }
    val history = remember { QuestActivityHistory(context) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(Page.JOURNAL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editQuest by remember { mutableStateOf<Quest?>(null) }
    var categoryFilter by remember { mutableStateOf("Alle") }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var categoryDraft by remember { mutableStateOf(categories.joinToString(", ")) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        snackbar = if (granted) "Quest-Erinnerungen aktiviert" else "Benachrichtigungen wurden nicht freigegeben"
    }

    LaunchedEffect(quests) {
        quests.forEach { q ->
            if (!q.completed && q.due != null) QuestReminderScheduler.schedule(context, q.id, q.title, q.due)
            else QuestReminderScheduler.cancel(context, q.id)
        }
    }

    fun update(next: List<Quest>) {
        val before = quests.associateBy { it.id }
        val after = next.associateBy { it.id }
        next.forEach { q ->
            val old = before[q.id]
            when {
                old == null -> history.record(q.id, q.title, "created", "Quest erstellt")
                !old.completed && q.completed -> history.record(q.id, q.title, "completed", "Quest abgeschlossen")
                old.completed && !q.completed -> history.record(q.id, q.title, "reopened", "Quest wieder geöffnet")
                old.title != q.title || old.description != q.description || old.due != q.due || old.category != q.category || old.main != q.main ->
                    history.record(q.id, q.title, "updated", "Quest geändert")
            }
            if (old != null) {
                val oldDone = old.objectives.filter { it.done }.map { it.id }.toSet()
                q.objectives.filter { it.done && it.id !in oldDone }.forEach { history.record(q.id, q.title, "objective_completed", it.text) }
            }
            if (q.completed || q.due == null) QuestReminderScheduler.cancel(context, q.id)
            else QuestReminderScheduler.schedule(context, q.id, q.title, q.due)
        }
        (before.keys - after.keys).forEach { id ->
            before[id]?.let { history.record(id, it.title, "deleted", "Quest gelöscht") }
            QuestReminderScheduler.cancel(context, id)
        }
        quests = next
        store.save(next)
        historyVersion++
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w -> w.write(JSONObject().put("format","SideQuests").put("version",2).put("quests",JSONArray().also { a -> quests.forEach { a.put(it.toJson()) } }).put("categories",JSONArray(categories)).toString(2)) }; snackbar = "Backup exportiert" } catch (e: Exception) { snackbar = "Export fehlgeschlagen: ${e.message}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Datei nicht lesbar")
            val validation = SafeQuestBackup.validate(raw)
            if (!validation.valid) error(validation.error ?: "Ungültiges Backup")
            val safetyPayload = JSONObject().put("format", "SideQuests").put("version", 2)
                .put("quests", JSONArray().also { a -> quests.forEach { a.put(it.toJson()) } })
                .put("categories", JSONArray(categories)).toString(2)
            SafeQuestBackup.createSafetyCopy(context, safetyPayload)
            val root = validation.root ?: error("Backup konnte nicht gelesen werden")
            val arr = root.getJSONArray("quests")
            val imported = (0 until arr.length()).map { arr.getJSONObject(it).toQuest() }
            update(imported)
            val ca = root.optJSONArray("categories")
            if (ca != null) { categories = (0 until ca.length()).map { ca.getString(it) }.ifEmpty { listOf("Allgemein") }; store.saveCategories(categories) }
            snackbar = "${imported.size} Quests importiert · Sicherung vor Import angelegt"
        } catch (e: Exception) { snackbar = "Import fehlgeschlagen: ${e.message}" }
    }

    SideQuestsTheme(selectedTheme) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(
                title = { Column { Text("SIDEQUESTS", fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified); Text("DEIN QUEST-JOURNAL · v0.2", style = MaterialTheme.typography.labelSmall, color = Gold) } },
                actions = { IconButton(onClick = { page = Page.SETTINGS }) { Icon(Icons.Default.Settings, "Einstellungen") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Page.JOURNAL, Page.MAIN, Page.SIDE, Page.DONE).forEach { p ->
                    NavigationBarItem(selected = page == p, onClick = { page = p; selected = null }, icon = {
                        Icon(when(p) { Page.JOURNAL -> Icons.Default.Book; Page.MAIN -> Icons.Default.Star; Page.SIDE -> Icons.Default.Home; else -> Icons.Default.CheckCircle }, p.label)
                    }, label = { Text(p.label, maxLines = 1) })
                }
            }
        }, floatingActionButton = {
            if (page != Page.SETTINGS && selected == null) FloatingActionButton(onClick = { editQuest = null; showEditor = true }, containerColor = Gold, contentColor = Leather) { Icon(Icons.Default.Add, "Quest erstellen") }
        }, snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (page == Page.SETTINGS) {
                    SettingsPage(selectedTheme, { theme ->
                        selectedTheme = theme
                        dark = theme.isDark
                        store.setSelectedTheme(theme)
                    }, categories, { categoryDraft = categories.joinToString(", "); showCategoryEditor = true },
                        { exportLauncher.launch("sidequests-backup.json") }, { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        { if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                          else snackbar = "Erinnerungen werden bei Quest-Terminen geplant" })
                } else if (selected != null) {
                    val q = quests.firstOrNull { it.id == selected }
                    if (q == null) selected = null else QuestDetail(q, categories,
                        onBack = { selected = null },
                        onEdit = { editQuest = q; showEditor = true },
                        onUpdate = { changed -> update(quests.map { if (it.id == changed.id) changed else it }) },
                        onDelete = { update(quests.filterNot { it.id == q.id }); selected = null },
                        onComplete = { done ->
                            val changed = q.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null)
                            update(quests.map { if (it.id == q.id) changed else it })
                            if (done && q.recurring) {
                                update(quests + q.copy(id = UUID.randomUUID().toString(), created = System.currentTimeMillis(), completed = false, completedAt = null, objectives = q.objectives.map { it.copy(id = UUID.randomUUID().toString(), done = false) }, journal = emptyList(), progress = 0))
                            }
                        })
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Gold)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Quests durchsuchen…") }, singleLine = true)
                    }
                    if (page == Page.MAIN || page == Page.SIDE || page == Page.JOURNAL) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = categoryFilter == "Alle", onClick = { categoryFilter = "Alle" }, label = { Text("Alle") })
                            categories.take(3).forEach { c -> FilterChip(selected = categoryFilter == c, onClick = { categoryFilter = c }, label = { Text(c) }) }
                        }
                    }
                    val visible = quests.filter { q ->
                        val pageOk = when(page) { Page.MAIN -> q.main && !q.completed; Page.SIDE -> !q.main && !q.completed; Page.DONE -> q.completed; else -> !q.completed }
                        pageOk && (categoryFilter == "Alle" || q.category == categoryFilter) &&
                            (query.isBlank() || listOf(q.title,q.description,q.category).any { it.contains(query, true) } || q.journal.any { it.text.contains(query,true) })
                    }.sortedWith(compareBy<Quest> { it.due ?: Long.MAX_VALUE }.thenByDescending { it.created })
                    if (page == Page.JOURNAL) {
                        val openCount = quests.count { !it.completed }
                        val doneCount = quests.count { it.completed }
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("DEIN ABENTEUER", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("$openCount offene Quests · $doneCount abgeschlossen", style = MaterialTheme.typography.titleMedium)
                                val mainCount = quests.count { it.main && !it.completed }
                                Text("$mainCount Hauptquests warten auf dich.", color = Muted)
                            }
                        }
                    }
                    if (page == Page.JOURNAL) {
                        val events = remember(historyVersion, query) { history.all().filter { query.isBlank() || it.questTitle.contains(query, true) || it.detail.contains(query, true) } }
                        if (events.isEmpty()) Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Deine Chronik beginnt hier", style = MaterialTheme.typography.titleMedium)
                                Text("Erstellen, Bearbeiten, Ziele und Abschlüsse erscheinen mit Zeitstempel.", color = Muted)
                            }
                        } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(events, key = { it.id }) { event ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(QuestActivityHistory.formatTimestamp(event.timestamp), color = Gold, style = MaterialTheme.typography.labelSmall)
                                        Text(event.questTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(when (event.action) {
                                            "created" -> "Quest erstellt"; "updated" -> "Quest bearbeitet"; "completed" -> "Quest abgeschlossen"
                                            "reopened" -> "Quest wieder geöffnet"; "deleted" -> "Quest gelöscht"; "objective_completed" -> "Ziel abgeschlossen"
                                            else -> event.action
                                        }, color = MaterialTheme.colorScheme.secondary)
                                        if (event.detail.isNotBlank()) Text(event.detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else if (visible.isEmpty()) Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (query.isBlank()) "Noch keine Quests in diesem Kapitel" else "Keine Treffer", style = MaterialTheme.typography.titleMedium)
                            Text("Tippe auf +, um dein nächstes Abenteuer anzulegen.", color = Muted)
                        }
                    } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visible, key = { it.id }) { q ->
                            QuestCard(q, onOpen = { selected = q.id }, onToggle = { done ->
                                val changed = q.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null)
                                update(quests.map { if (it.id == q.id) changed else it })
                                if (done && q.recurring) update(quests + q.copy(id = UUID.randomUUID().toString(), created = System.currentTimeMillis(), completed = false, completedAt = null, objectives = q.objectives.map { it.copy(id = UUID.randomUUID().toString(), done = false) }, journal = emptyList(), progress = 0))
                            })
                        }
                    }
                }
            }
        }
        if (showEditor) QuestEditorDialog(editQuest, categories, onDismiss = { showEditor = false }, onSave = { q ->
            val next = if (editQuest == null) quests + q else quests.map { if (it.id == q.id) q else it }
            update(next); showEditor = false
        }, onAddCategory = { new -> if (new.isNotBlank() && new !in categories) { categories = categories + new; store.saveCategories(categories) } })
        if (showCategoryEditor) AlertDialog(onDismissRequest = { showCategoryEditor = false }, title = { Text("Kategorien verwalten") },
            text = { OutlinedTextField(categoryDraft, { categoryDraft = it }, label = { Text("Kommagetrennte Kategorien") }) },
            confirmButton = { TextButton(onClick = { categories = categoryDraft.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Allgemein") }.distinct(); store.saveCategories(categories); showCategoryEditor = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { showCategoryEditor = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun QuestCard(q: Quest, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .35f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(q.completed, onToggle, colors = CheckboxDefaults.colors(checkedColor = Green))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (q.main) "✦ " else "◇ ", color = Gold)
                    Text(q.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (q.description.isNotBlank()) Text(q.description, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(q.category.uppercase(Locale.GERMANY), style = MaterialTheme.typography.labelSmall, color = Gold)
                    if (q.due != null) Text("⌛ ${stamp(q.due)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    if (q.recurring) Icon(Icons.Default.Refresh, "Wiederkehrend", tint = Green, modifier = Modifier.size(15.dp))
                }
                if (q.objectives.isNotEmpty()) {
                    val finished = q.objectives.count { it.done }
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(progress = { finished.toFloat() / q.objectives.size }, modifier = Modifier.fillMaxWidth(), color = Gold)
                    Text("$finished / ${q.objectives.size} Ziele", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun QuestDetail(q: Quest, categories: List<String>, onBack: () -> Unit, onEdit: () -> Unit, onUpdate: (Quest) -> Unit, onDelete: () -> Unit, onComplete: (Boolean) -> Unit) {
    var entryText by remember(q.id) { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück zum Journal") }
        Text(if (q.main) "HAUPTQUEST" else "NEBENQUEST", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(q.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Erstellt: ${stamp(q.created)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Termin: ${stamp(q.due)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (q.description.isNotBlank()) Text(q.description, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Bearbeiten") }
            Button(onClick = { onComplete(!q.completed) }, colors = ButtonDefaults.buttonColors(containerColor = if (q.completed) Green else Gold)) { Text(if (q.completed) "Wieder öffnen" else "Abschließen") }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Gold.copy(alpha = .45f))
        Text("ZIELE", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        if (q.objectives.isEmpty()) Text("Noch keine Ziele hinzugefügt.", color = Muted)
        q.objectives.forEach { o -> Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(o.done, { done -> onUpdate(q.copy(objectives = q.objectives.map { if (it.id == o.id) it.copy(done = done) else it })) })
            Text(o.text, modifier = Modifier.weight(1f), color = if (o.done) Muted else MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = { onUpdate(q.copy(objectives = q.objectives.filterNot { it.id == o.id })) }) { Icon(Icons.Default.Delete, "Ziel löschen", tint = Muted) }
        } }
        var objectiveDraft by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(objectiveDraft, { objectiveDraft = it }, Modifier.weight(1f), label = { Text("Neues Ziel") }, singleLine = true)
            IconButton(onClick = { if (objectiveDraft.isNotBlank()) { onUpdate(q.copy(objectives = q.objectives + Objective(text = objectiveDraft.trim()))); objectiveDraft = "" } }) { Icon(Icons.Default.Add, "Ziel hinzufügen", tint = Gold) }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Gold.copy(alpha = .45f))
        Text("FORTSCHRITT & CHRONIK", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedTextField(entryText, { entryText = it }, Modifier.fillMaxWidth(), label = { Text("Was hast du erreicht oder gelernt?") }, minLines = 2)
        Button(onClick = { if (entryText.isNotBlank()) { onUpdate(q.copy(journal = q.journal + JournalEntry(text = entryText.trim()), progress = (q.progress + 1).coerceAtMost(100))); entryText = "" } }, modifier = Modifier.padding(top = 6.dp)) { Text("Eintrag hinzufügen") }
        if (q.journal.isEmpty()) Text("Deine Einträge erscheinen hier mit Datum und Uhrzeit.", color = Muted, modifier = Modifier.padding(vertical = 10.dp))
        q.journal.sortedByDescending { it.date }.forEach { j ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) { Text(stamp(j.date), color = Gold, style = MaterialTheme.typography.labelSmall); Text(j.text) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Gold.copy(alpha = .45f))
        TextButton(onClick = { showDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE07A68))) { Icon(Icons.Default.Delete, null); Text("Quest löschen") }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Quest löschen?") }, text = { Text("Diese Quest und ihre Chronik werden lokal entfernt.") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("Löschen") } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } })
}

@Composable
private fun QuestEditorDialog(existing: Quest?, categories: List<String>, onDismiss: () -> Unit, onSave: (Quest) -> Unit, onAddCategory: (String) -> Unit) {
    val context = LocalContext.current
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var main by remember(existing?.id) { mutableStateOf(existing?.main ?: false) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: categories.firstOrNull() ?: "Allgemein") }
    var recurring by remember(existing?.id) { mutableStateOf(existing?.recurring ?: false) }
    var due by remember(existing?.id) { mutableStateOf(existing?.due) }
    var objectives by remember(existing?.id) { mutableStateOf(existing?.objectives?.joinToString("\n") { it.text } ?: "") }
    var newCategory by remember { mutableStateOf("") }
    var showCategories by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Neue Quest schmieden" else "Quest bearbeiten") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Titel *") }, singleLine = true)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Beschreibung / Auftrag") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = main, onClick = { main = true }, label = { Text("✦ Hauptquest") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !main, onClick = { main = false }, label = { Text("◇ Nebenquest") })
                }
                Text("Kategorie", color = Gold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
                    FilterChip(selected = showCategories, onClick = { showCategories = !showCategories }, label = { Text("+") })
                }
                if (showCategories) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCategory, { newCategory = it }, Modifier.weight(1f), label = { Text("Neue Kategorie") }, singleLine = true)
                    TextButton(onClick = { if (newCategory.isNotBlank()) { onAddCategory(newCategory.trim()); category = newCategory.trim(); newCategory = ""; showCategories = false } }) { Text("Hinzufügen") }
                }
                OutlinedButton(onClick = {
                    val initial = due?.let { Date(it) } ?: Date()
                    DatePickerDialog(context, { _, y, m, d ->
                        val cal = java.util.Calendar.getInstance().apply { time = due?.let { Date(it) } ?: Date(); set(y, m, d) }
                        TimePickerDialog(context, { _, h, min -> cal.set(java.util.Calendar.HOUR_OF_DAY, h); cal.set(java.util.Calendar.MINUTE, min); due = cal.timeInMillis }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
                    }, initial.year + 1900, initial.month, initial.date).show()
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text(due?.let { stamp(it) } ?: "Termin und Uhrzeit festlegen") }
                if (due != null) TextButton(onClick = { due = null }) { Text("Termin entfernen") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(recurring, { recurring = it }); Text("Wiederkehrende Quest (nach Abschluss neu anlegen)") }
                OutlinedTextField(objectives, { objectives = it }, Modifier.fillMaxWidth(), label = { Text("Ziele (ein Ziel pro Zeile)") }, minLines = 3)
            }
        },
        confirmButton = { Button(enabled = title.isNotBlank(), onClick = {
            val old = existing
            val objs = objectives.lines().map { it.trim() }.filter { it.isNotBlank() }.mapIndexed { i, s ->
                old?.objectives?.getOrNull(i)?.copy(text = s) ?: Objective(text = s)
            }
            onSave(Quest(id = old?.id ?: UUID.randomUUID().toString(), title = title.trim(), description = description.trim(), main = main,
                created = old?.created ?: System.currentTimeMillis(), due = due, completed = old?.completed ?: false, completedAt = old?.completedAt,
                category = category, recurring = recurring, objectives = objs, journal = old?.journal ?: emptyList(), progress = old?.progress ?: 0))
        }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun SettingsPage(theme: QuestTheme, onTheme: (QuestTheme) -> Unit, categories: List<String>, onCategories: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onEnableReminders: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AUSRÜSTUNG & EINSTELLUNGEN", style = MaterialTheme.typography.titleLarge, color = Gold, fontWeight = FontWeight.Black)
        Text("Passe dein Quest-Journal an. Alle Questdaten bleiben auf diesem Gerät.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("APP-THEMA", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                QuestTheme.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(option) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == option, onClick = { onTheme(option) })
                        Column(Modifier.weight(1f)) {
                            Text(option.label, fontWeight = FontWeight.SemiBold)
                            Text(option.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(color = Gold.copy(alpha = .25f))
                Row(Modifier.fillMaxWidth().clickable(onClick = onCategories).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Settings, null, tint = Gold); Spacer(Modifier.width(10.dp)); Column { Text("Kategorien"); Text("${categories.size} Kategorien verwalten", color = Muted, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        Text("ERINNERUNGEN", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onEnableReminders, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Quest-Erinnerungen aktivieren") }
        Text("Erinnerungen werden 15 Minuten vor dem Termin geplant. Android kann sie im Energiesparmodus verzögern.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("DATEN & SICHERUNG", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp)); Text("Backup als JSON exportieren") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Backup importieren / wiederherstellen") }
        Text("Der Import ersetzt die aktuell gespeicherten Quests. Bewahre eine Sicherung auf, bevor du importierst.", color = Muted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Gold.copy(alpha = .35f))
        Text("SIDEQUESTS · VERSION 0.2.0", color = Gold, fontWeight = FontWeight.Bold)
        Text("Offline-first · keine Konten · keine Telemetrie · lokale Speicherung", color = Muted)
        Text("Inspiriert vom Quest-Journal-Gefühl mittelalterlicher Rollenspiele.", color = Muted)
    }
}
