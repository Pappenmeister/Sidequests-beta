# SideQuests – zusammengeführtes Projekt

Der Patch aus `SideQuests_Fertigpatch.zip` wurde auf den Quellstand aus `SideQuests-main.zip` angewendet. Das Skript meldete:
`Patch angewendet: Theme-Auswahl, Aktivitätschronik, Quest-Erinnerungen und Sicherheitskopie vor Import.`

## Branch manuell in GitHub anlegen
1. Öffne https://github.com/Pappenmeister/SideQuests
2. Wähle die Branch-Auswahl (aktuell `main`), tippe `feature/sidequests-patch`.
3. Wähle **Create branch: feature/sidequests-patch from main**.
4. Lade die Dateien aus diesem entpackten Projekt in den neuen Branch hoch (Add file → Upload files). Wenn GitHub wegen vieler Dateien oder Upload-Limits Probleme macht, verwende Git lokal.

## Empfohlener Weg mit Git (Projektordner)
Diese ZIP ist ein Projekt-Snapshot und enthält keinen `.git`-Ordner. Für einen sauberen Commit:
```bash
git clone https://github.com/Pappenmeister/SideQuests.git
cd SideQuests
git switch -c feature/sidequests-patch
```
Kopiere dann die Dateien aus dieser ZIP über die geklonte Projektstruktur, ohne `.git` zu überschreiben, und führe aus:
```bash
git add .
git diff --cached
git commit -m "Integrate SideQuests feature patch"
git push -u origin feature/sidequests-patch
```

## Prüfen
Öffne das Projekt in Android Studio, synchronisiere Gradle und führe einen Build aus. In der Umgebung, in der dieses ZIP erstellt wurde, war kein `gradle`-Programm und kein Gradle Wrapper im Repository vorhanden; deshalb konnte ich den Android-Build hier nicht ausführen. Bitte kontrolliere den Build vor dem Merge.
