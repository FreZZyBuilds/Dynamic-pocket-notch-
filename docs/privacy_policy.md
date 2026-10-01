# Datenschutzerklärung – DevNotch

*Stand: 1. Oktober 2026 · gilt für die Android-App „DevNotch“ (`com.frezzybuilds.devnotch`)*

> **Vor der Veröffentlichung ausfüllen:** alle Stellen in `[eckigen Klammern]`. Diese Vorlage
> beschreibt den technischen Stand der App; sie ist keine Rechtsberatung. Die Erklärung muss
> unter einer öffentlich erreichbaren URL liegen (z. B. GitHub Pages) und in der Play Console
> unter *App-Inhalte → Datenschutzerklärung* eingetragen werden.

## 1. Verantwortlicher

[Vor- und Nachname / Firma]
[Straße, Hausnummer]
[PLZ, Ort, Land]
E-Mail: [kontakt@beispiel.de]

## 2. Grundsatz: Alles bleibt auf deinem Gerät

DevNotch hat **keinen eigenen Server**, **kein Nutzerkonto**, **keine Werbung**, **kein Tracking**
und **keine Analyse- oder Crash-Reporting-Dienste**. Notizen, Zwischenablage-Verlauf,
Projekt-Shortcuts, Einstellungen und Zugangsschlüssel werden ausschließlich lokal im privaten
App-Speicher abgelegt.

Diese Daten sind **von Android-Cloud-Backups und der Gerät-zu-Gerät-Übertragung
ausgeschlossen**. Sie werden beim Deinstallieren der App oder über *Einstellungen → Apps →
DevNotch → Speicher → Daten löschen* vollständig entfernt.

Daten verlassen dein Gerät nur in den unten genannten Fällen. Das geschieht jeweils nur, weil
du die zugehörige Funktion selbst eingerichtet hast.

## 3. Verarbeitung auf dem Gerät

| Daten | Zweck | Speicherung |
|---|---|---|
| **Wiedergabeinformationen** (Titel, Interpret, Cover, Wiedergabestatus, App des Players) aus aktiven Mediensitzungen | Anzeige und Steuerung (Play/Pause, Weiter, Zurück) in der Notch bzw. im Edge-Player; Farben passend zum Cover | Nur im Arbeitsspeicher, wird nicht gespeichert |
| **Zwischenablage** (kopierte Texte) | Verlauf im Clip-Tab zum erneuten Kopieren | Lokale Datenbank. Free: die letzten 5 Einträge, Pro: bis du den Verlauf leerst |
| **Notizen** | Notizen-Tab, Markdown-Export über das Android-Teilen-Menü | Lokale Datenbank |
| **Projekt-Shortcuts** (App-Paketnamen, URLs) | Schnellstart von Apps und Webseiten | Lokale Datenbank |
| **Liste installierter Apps** (nur Apps mit Startbildschirm-Symbol) | Auswahl beim Anlegen eines App-Shortcuts | Wird nicht gespeichert, nur der gewählte Eintrag |
| **Zugangsdaten** (GitHub-Token, API-Schlüssel der KI-Anbieter, Ollama-Adresse) | Abruf der jeweiligen Statistiken (siehe 4) | App-privater Speicher, vom Backup ausgeschlossen |
| **Zwischengespeicherte Statistiken** (KI-Kosten des Monats, GitHub-Profilbild) | Schnelle Anzeige ohne erneuten Abruf | App-privater Speicher bzw. App-Cache |
| **Pro-Status** | Freischaltung der Pro-Funktionen | App-privater Speicher |

Wiedergabeinformationen werden über den Android-Benachrichtigungszugriff gelesen. DevNotch
wertet **ausschließlich Medien-Benachrichtigungen** aus, also Benachrichtigungen, die eine
Mediensitzung enthalten. Andere Benachrichtigungen (Nachrichten, E-Mails usw.) werden sofort
verworfen, nicht gelesen und nicht gespeichert.

Die Zwischenablage liest DevNotch nur, wenn Android den Zugriff erlaubt. Ab Android 10 ist das
nur der Fall, solange DevNotch im Vordergrund ist.

## 4. Übermittlung an Dritte (nur bei Nutzung der jeweiligen Funktion)

Alle Verbindungen sind verschlüsselt (HTTPS). Die einzige Ausnahme ist eine von dir selbst
eingetragene lokale Ollama-Adresse (`http://` im Heimnetz).

### 4.1 GitHub (Pro-Funktion „GitHub-Heatmap“)
Wenn du ein GitHub-Token hinterlegst, ruft DevNotch über die GitHub-GraphQL-API
(`api.github.com`) dein Profil (Name, Profilbild) und deinen Beitragskalender ab. Dabei werden
dein Token, der Benutzername und technische Verbindungsdaten (z. B. IP-Adresse) an GitHub, Inc.
(USA) übermittelt. Das Profilbild wird von `avatars.githubusercontent.com` geladen.
Datenschutz: <https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement>

### 4.2 KI-Anbieter (Pro-Funktion „KI-Token-Tracker“)
Für jeden Anbieter, für den du einen Schlüssel hinterlegst, fragt DevNotch **nur
Abrechnungs- und Nutzungsdaten deines eigenen Kontos** ab, z. B. Kosten und Tokens des
laufenden Monats. Es werden **keine Prompts, Chats oder Inhalte** gesendet oder gelesen.

| Anbieter | Abgerufene Daten | Datenschutz |
|---|---|---|
| OpenAI (`api.openai.com`) | Kosten und Token-Nutzung der Organisation | <https://openai.com/policies/privacy-policy> |
| Anthropic (`api.anthropic.com`) | Kosten- und Nutzungsbericht der Organisation | <https://www.anthropic.com/legal/privacy> |
| OpenRouter (`openrouter.ai`) | Monatsverbrauch des Schlüssels | <https://openrouter.ai/privacy> |
| Google Gemini (`generativelanguage.googleapis.com`) | Nur Gültigkeitsprüfung des Schlüssels | <https://policies.google.com/privacy> |
| Ollama (deine Adresse, z. B. im Heimnetz oder `ollama.com`) | Version, installierte und laufende Modelle | <https://ollama.com/privacy> |

Übermittelt werden dabei dein Schlüssel und technische Verbindungsdaten (z. B. IP-Adresse).
Abgerufen wird beim Öffnen des KI-Tabs, beim Antippen von „Aktualisieren“ und, falls du die
Kostenanzeige in der Notch aktiviert hast, höchstens alle 15 Minuten.

### 4.3 Käufe: Google Play und RevenueCat (DevNotch Pro)
Käufe und Abonnements werden über **Google Play Billing** abgewickelt. Zahlungsdaten erhält
DevNotch nie. Es gilt die Datenschutzerklärung von Google: <https://policies.google.com/privacy>.

Für die Verwaltung des Pro-Status nutzt DevNotch **RevenueCat** (RevenueCat, Inc., USA) als
Auftragsverarbeiter. Beim App-Start, bei Käufen und beim Wiederherstellen werden übermittelt:

- eine zufällig erzeugte, anonyme App-Nutzer-ID (kein Name, keine E-Mail),
- Kaufinformationen (Produkt, Kaufzeitpunkt, Kaufbeleg/Token von Google Play, Preis, Währung),
- technische Daten (App-Version, Android-Version, Gerätesprache und -land, IP-Adresse).

Zweck ist die Prüfung, ob ein gültiges Pro-Abo oder ein Einmalkauf besteht (Vertragserfüllung,
Art. 6 Abs. 1 lit. b DSGVO). Datenschutz: <https://www.revenuecat.com/privacy>.
[Grundlage der Übermittlung in die USA laut RevenueCat-DPA prüfen und eintragen, z. B.
EU-U.S. Data Privacy Framework oder Standardvertragsklauseln.]

Wenn du die RevenueCat-Paywall-Variante nutzt, werden deren Inhalte (Texte, Bilder, Preise)
ebenfalls von RevenueCat geladen.

## 5. Berechtigungen

| Berechtigung | Wofür |
|---|---|
| Über anderen Apps einblenden (`SYSTEM_ALERT_WINDOW`) | Die Notch, das Floating-Dashboard und den Edge-Player über dem aktuellen Bildschirm zeichnen |
| Benachrichtigungszugriff (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Ausschließlich Mediensitzungen steuern und den laufenden Player erkennen (siehe 3) |
| Vordergrunddienst (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Die Notch dauerhaft anzeigen, ohne dass Android sie beendet |
| Benachrichtigungen (`POST_NOTIFICATIONS`) | Die vorgeschriebene, dezente Dienst-Benachrichtigung mit „Beenden“-Knopf |
| Nach dem Neustart starten (`RECEIVE_BOOT_COMPLETED`) | Die Notch nach einem Neustart oder App-Update wieder einblenden, falls sie aktiv war |
| Akku-Optimierung ignorieren (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Auf Wunsch den Systemdialog öffnen, damit Android die Notch nicht im Leerlauf beendet |
| Internet, Netzwerkstatus (`INTERNET`, `ACCESS_NETWORK_STATE`) | Nur für die Funktionen in Abschnitt 4. Der Netzwerkstatus wird von Google Play Billing und RevenueCat genutzt |
| Zahlungen (`com.android.vending.BILLING`) | Kauf von DevNotch Pro über Google Play |

DevNotch fordert **keine** Werbe-ID (`AD_ID`), keinen Standort, keine Kontakte, kein Mikrofon
und keine Kamera an.

Jede dieser Berechtigungen kannst du in den Android-Einstellungen jederzeit widerrufen.

## 6. Rechtsgrundlagen und Speicherdauer

- **Lokale Funktionen:** Die Verarbeitung findet nur auf deinem Gerät statt. Wir erhalten
  diese Daten nicht.
- **Abrufe bei GitHub und KI-Anbietern:** Sie erfolgen auf deine Veranlassung, um die gewünschte
  Funktion bereitzustellen (Art. 6 Abs. 1 lit. b DSGVO).
- **Käufe:** Grundlage ist die Vertragserfüllung (Art. 6 Abs. 1 lit. b DSGVO). Hinzu kommen
  gesetzliche Aufbewahrungspflichten bei Google bzw. RevenueCat (Art. 6 Abs. 1 lit. c DSGVO).
- **Speicherdauer lokal:** bis du die Daten in der App löschst, die App-Daten löschst oder die
  App deinstallierst. Schlüssel und Tokens entfernst du, indem du das Feld leerst und speicherst.
- **Speicherdauer bei Dritten:** nach den Datenschutzerklärungen der jeweiligen Anbieter.

## 7. Deine Rechte

Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung,
Datenübertragbarkeit und Widerspruch (Art. 15–21 DSGVO). Außerdem kannst du dich bei einer
Datenschutz-Aufsichtsbehörde beschweren. Da wir selbst keine personenbezogenen Daten
speichern, betreffen Anfragen zu Käufen in der Regel Google bzw. RevenueCat. Wir helfen dir
gern dabei: [kontakt@beispiel.de].

## 8. Kinder

DevNotch richtet sich nicht an Kinder unter 13 Jahren.

## 9. Änderungen

Bei neuen Funktionen, die Daten anders verarbeiten, wird diese Erklärung aktualisiert. Das
Datum oben zeigt den aktuellen Stand.
