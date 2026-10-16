#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path.cwd()
main=root/'app/src/main/java/de/questkeep/app/MainActivity.kt'
manifest=root/'app/src/main/AndroidManifest.xml'
if not main.exists() or not manifest.exists():
    sys.exit('Bitte im Stammordner des SideQuests-Repositories ausführen.')
s=main.read_text(encoding='utf-8')
orig=s
def rep(a,b,label):
    global s
    n=s.count(a)
    if n!=1: sys.exit(f'Patch abgebrochen bei {label}: erwartete 1 Fundstelle, gefunden {n}; keine Änderungen geschrieben.')
    s=s.replace(a,b,1)

rep('''    fun dark(): Boolean = prefs.getBoolean("dark", true)
    fun setDark(v: Boolean) { prefs.edit().putBoolean("dark", v).apply() }
''','''    fun dark(): Boolean = prefs.getBoolean("dark", true)
    fun setDark(v: Boolean) { prefs.edit().putBoolean("dark", v).apply() }
    fun selectedTheme(): QuestTheme = QuestTheme.fromKey(prefs.getString("selected_theme", null)
        ?: if (dark()) QuestTheme.PARCHMENT_NIGHT.storageKey else QuestTheme.CLASSIC_PARCHMENT.storageKey)
    fun setSelectedTheme(theme: QuestTheme) { prefs.edit().putString("selected_theme", theme.storageKey).putBoolean("dark", theme.isDark).apply() }
''','store theme')
rep('''    var dark by remember { mutableStateOf(store.dark()) }
    var page by remember { mutableStateOf(Page.JOURNAL) }
''','''    var dark by remember { mutableStateOf(store.dark()) }
    var selectedTheme by remember { mutableStateOf(store.selectedTheme()) }
    val history = remember { QuestActivityHistory(context) }
    var historyVersion by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(Page.JOURNAL) }
''','state')
rep('''    var snackbar by remember { mutableStateOf<String?>(null) }

    fun update(next: List<Quest>) { quests = next; store.save(next) }
''','''    var snackbar by remember { mutableStateOf<String?>(null) }
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
''','update/history/reminders')
rep('''            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Datei nicht lesbar")
            val root = JSONObject(raw); val arr = root.getJSONArray("quests")
            val imported = (0 until arr.length()).map { arr.getJSONObject(it).toQuest() }
''','''            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Datei nicht lesbar")
            val validation = SafeQuestBackup.validate(raw)
            if (!validation.valid) error(validation.error ?: "Ungültiges Backup")
            val safetyPayload = JSONObject().put("format", "SideQuests").put("version", 2)
                .put("quests", JSONArray().also { a -> quests.forEach { a.put(it.toJson()) } })
                .put("categories", JSONArray(categories)).toString(2)
            SafeQuestBackup.createSafetyCopy(context, safetyPayload)
            val root = validation.root ?: error("Backup konnte nicht gelesen werden")
            val arr = root.getJSONArray("quests")
            val imported = (0 until arr.length()).map { arr.getJSONObject(it).toQuest() }
''','safe import')
rep('''            snackbar = "${imported.size} Quests importiert"
''','''            snackbar = "${imported.size} Quests importiert · Sicherung vor Import angelegt"
''','import message')
rep('''    val scheme = if (dark) darkColorScheme(primary = Gold, secondary = Green, background = Leather, surface = Panel, onPrimary = Leather, onBackground = Ink, onSurface = Ink) else lightColorScheme(primary = Color(0xFF6D4A20), secondary = Color(0xFF526B42), background = Parchment, surface = Color(0xFFFFF6E3), onBackground = Leather, onSurface = Leather)
    MaterialTheme(colorScheme = scheme) {
''','''    SideQuestsTheme(selectedTheme) {
''','theme wrapper')
rep('''                    SettingsPage(dark, { dark = it; store.setDark(it) }, categories, { categoryDraft = categories.joinToString(", "); showCategoryEditor = true },
                        { exportLauncher.launch("sidequests-backup.json") }, { importLauncher.launch(arrayOf("application/json", "text/*")) })
''','''                    SettingsPage(selectedTheme, { theme ->
                        selectedTheme = theme
                        dark = theme.isDark
                        store.setSelectedTheme(theme)
                    }, categories, { categoryDraft = categories.joinToString(", "); showCategoryEditor = true },
                        { exportLauncher.launch("sidequests-backup.json") }, { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        { if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                          else snackbar = "Erinnerungen werden bei Quest-Terminen geplant" })
''','settings call')
rep('''                    if (visible.isEmpty()) Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (query.isBlank()) "Noch keine Quests in diesem Kapitel" else "Keine Treffer", style = MaterialTheme.typography.titleMedium)
                            Text("Tippe auf +, um dein nächstes Abenteuer anzulegen.", color = Muted)
                        }
                    } else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
''','''                    if (page == Page.JOURNAL) {
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
''','chronicle feed')
rep('''private fun SettingsPage(dark: Boolean, onDark: (Boolean) -> Unit, categories: List<String>, onCategories: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
''','''private fun SettingsPage(theme: QuestTheme, onTheme: (QuestTheme) -> Unit, categories: List<String>, onCategories: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onEnableReminders: () -> Unit) {
''','settings signature')
rep('''                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = Gold); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Pergament bei Nacht"); Text("Dunkles Leder- und Pergamentthema", color = Muted, style = MaterialTheme.typography.bodySmall) }; Switch(dark, onDark) }
                HorizontalDivider(color = Gold.copy(alpha = .25f))
''','''                Text("APP-THEMA", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
''','theme selector')
rep('''        Text("DATEN & SICHERUNG", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp)); Text("Backup als JSON exportieren") }
''','''        Text("ERINNERUNGEN", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onEnableReminders, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Quest-Erinnerungen aktivieren") }
        Text("Erinnerungen werden 15 Minuten vor dem Termin geplant. Android kann sie im Energiesparmodus verzögern.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("DATEN & SICHERUNG", style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp)); Text("Backup als JSON exportieren") }
''','reminder settings')
if s==orig: sys.exit('Keine Änderungen; Abbruch.')
m=manifest.read_text(encoding='utf-8')
if 'android:name=".QuestReminderReceiver"' not in m:
    m=m.replace('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">''','''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />''',1)
    m=m.replace('''    </application>''','''        <receiver android:name=".QuestReminderReceiver" android:exported="false" />
    </application>''',1)
# Write only after every source anchor validated.
main.write_text(s,encoding='utf-8')
manifest.write_text(m,encoding='utf-8')
print('Patch angewendet: Theme-Auswahl, Aktivitätschronik, Quest-Erinnerungen und Sicherheitskopie vor Import.')
print('Jetzt Gradle-Sync und Build/Funktionstest ausführen.')
