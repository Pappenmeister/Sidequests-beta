# SideQuests – integrierter Feature-Patch

Dieser Patch ergänzt die vorhandenen Dateien im Repository `Pappenmeister/SideQuests` lokal, ohne den `applicationId`, die Quest-Datenstruktur oder den bestehenden JSON-Export zu ändern.

## Anwenden

1. ZIP entpacken.
2. `apply_patch.py` in den Stammordner einer frischen lokalen Kopie des SideQuests-Repositories kopieren.
3. Im Repository-Stamm ausführen: `python3 apply_patch.py`
4. Die mitgelieferten Dateien unter `app/src/main/...` in die gleichnamigen Projektpfade kopieren/mergen (bei ZIP-Entpacken in den Repo-Stamm die Pfade direkt übernehmen).
5. Android Studio: Gradle Sync, dann Build > Make Project.

Der Patch bricht ab, wenn ein erwarteter Codeabschnitt nicht exakt einmal gefunden wird. Er schreibt `MainActivity.kt` erst, nachdem alle Code-Anker geprüft wurden. Lege trotzdem vor dem Anwenden einen Git-Commit oder eine Kopie des Projektordners an.

## Was wird integriert?

- Vier Themes in Einstellungen: Pergament bei Nacht, klassisches Pergament, Waldpfad, Mitternachtsstahl. Auswahl wird lokal gespeichert; bisherige `dark`-Einstellung wird als Migrations-Fallback verwendet.
- Chronik mit Zeitstempel für Quest-Erstellung, Änderungen, Abschlüsse, Wiederöffnen, Löschen und abgeschlossene Ziele.
- Lokale Android-Erinnerungen 15 Minuten vor Quest-Terminen. Termine vorhandener Quests werden beim Start ebenfalls eingeplant; abgeschlossene/terminlose/gelöschte Quests werden abgemeldet.
- Android-13+-Berechtigungsanfrage für Benachrichtigungen in Einstellungen.
- Import validiert JSON vor Änderungen und speichert den aktuellen Bestand vorher als private Sicherung unter `files/backups/`.
- Ressourcenverzeichnisse `drawable`, `font` und `mipmap-anydpi-v26` vorbereitet, ohne nicht freigegebene Grafiken zu erfinden.

## Testhinweis

Ein Android/Gradle-Build konnte in dieser Umgebung nicht ausgeführt werden. Bitte nach dem Anwenden mindestens testen:
1. App kompiliert und startet.
2. Theme auswählen, App beenden/neustarten, Auswahl bleibt erhalten.
3. Quest erstellen/bearbeiten/abschließen, Chronik kontrollieren.
4. Quest mit Termin anlegen, Benachrichtigungsberechtigung erlauben und Alarm prüfen.
5. Gültiges Backup importieren und sicherstellen, dass vor dem Import eine Datei in `files/backups/` entsteht.
6. Ungültige JSON-Datei importieren und prüfen, dass die vorhandenen Quests unverändert bleiben.

Die AlarmManager-Zustellung kann durch Android-Energiesparregeln verzögert werden. Die App fordert keine exakte Alarmberechtigung an.
