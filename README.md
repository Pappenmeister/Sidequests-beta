# SideQuests v0.2.0 — Quellcode-Patch

## Enthalten
- Name und Versionsnummer auf SideQuests 0.2.0
- lokale Speicherung über SharedPreferences/JSON
- Hauptquests, Nebenquests, abgeschlossene Quests, Suche und Kategorien
- Quest-Details, Ziele/Teilaufgaben, Fortschritts-/Journal-Einträge mit Datum
- optionale Fälligkeit mit Datum und Uhrzeit
- wiederkehrende Quests: beim Abschluss wird eine neue offene Kopie angelegt
- Bearbeiten, löschen, abschließen und wieder öffnen
- JSON-Backup exportieren und importieren
- dunkles Leder-/Pergament-Farbschema, umschaltbares helles Thema

## Einbau
Kopiere die Dateien in dein bestehendes Android-Projekt und überschreibe die gleichnamigen Dateien:
- `app/src/main/java/de/questkeep/app/MainActivity.kt`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`

Projektname und applicationId bleiben kompatibel mit v0.1 (`de.questkeep.app`), damit Android die bestehende App als Update behandeln kann. Danach in deiner vorhandenen Umgebung bauen.

## Wichtige Hinweise
- Das ist ein Quellcode-Patch, kein bereits gebautes oder auf einem Gerät getestetes APK.
- JSON-Import ersetzt den lokalen Questbestand. Vor dem Import sichern.
- Wiederkehrende Quests werden beim Abschluss als neue offene Quest angelegt; komplexe Kalenderregeln sind noch nicht implementiert.
- Erinnerungs-Benachrichtigungen und Markdown/ZIP-Export sind in dieser v0.2-Datei noch nicht enthalten.
