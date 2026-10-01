# Play-Store-Eintrag – Entwurf

*Sprache: Deutsch (de-DE) · Kategorie: Produktivität · Inhaltsfreigabe: USK 0 / PEGI 3 ·
Datenschutzerklärung: [URL zu docs/privacy_policy.md, z. B. GitHub Pages]*

## App-Name (max. 30 Zeichen)

> **DevNotch – Dynamic Notch**

## Kurzbeschreibung (max. 80 Zeichen)

> **Dynamic-Island-Notch für Entwickler: Musik, Fokus-Timer, Notizen & KI-Kosten**

## Vollständige Beschreibung

> **Deine Kamera-Aussparung wird zur Kommandozentrale.**
>
> DevNotch verwandelt das Punch-Hole deines Displays in eine lebendige Notch im Stil von
> „Dynamic Island“. Eingeklappt ist sie eine schlanke Pille, die zeigt, was gerade läuft.
> Ein Tipp, und sie öffnet sich zum Dashboard für alles, was du beim Entwickeln brauchst.
>
> **🎵 Musik immer im Griff**
> • Titel, Interpret und Cover direkt an der Kamera, mit Lauftext
> • Play/Pause, Weiter und Zurück, ohne die App zu wechseln
> • Funktioniert mit Spotify, YouTube Music, YouTube, Deezer, Podcasts und allen anderen Apps,
>   die eine Android-Mediensitzung anbieten
> • Edge-Player am Bildschirmrand, der sich nach ein paar Sekunden zur kleinen Bubble einklappt
> • Themes und Farben, die sich dem Cover anpassen
>
> **⏱ Focus Timer**
> • Pomodoro 25/5 mit Fortschritt direkt in der Pille
>
> **📝 Notizen & 📋 Zwischenablage**
> • Schnelle Notizen mit Markdown-Hilfen und Export als .md
> • Verlauf deiner kopierten Texte, ein Tipp kopiert sie erneut
>
> **✋ Gesten & Layouts**
> • Wischen zum Öffnen und Schließen, haptisches Feedback
> • Punch-Hole-Notch oder schwebende Edge-Leiste, frei verschiebbar
> • Auf Tablets und im Querformat ein Split-Drawer mit zwei Spalten
>
> **✦ DevNotch Pro**
> • **GitHub-Heatmap:** dein Beitragskalender der letzten 16 Wochen
> • **KI-Token-Tracker:** Monatskosten und Tokens von OpenAI, Anthropic und OpenRouter,
>   Status von Gemini und Ollama, auf Wunsch als Betrag neben dem Timer
> • **Unbegrenzte Zwischenablage** (Free: die letzten 5 Einträge)
> • **Unbegrenzte Projekt-Shortcuts** für Apps und URLs (Free: 3)
>
> **🔒 Datenschutz**
> Kein Konto, keine Werbung, kein Tracking. Notizen, Zwischenablage und Schlüssel bleiben auf
> deinem Gerät und sind vom Cloud-Backup ausgeschlossen. Verbindungen zu GitHub oder KI-Anbietern
> entstehen nur, wenn du sie selbst einrichtest.
>
> **Warum DevNotch diese Berechtigungen braucht**
>
> **Über anderen Apps einblenden:** DevNotch zeichnet die Notch, das aufklappbare
> Floating-Dashboard und den Edge-Player als Overlay über dem aktuellen Bildschirm. Ohne diese
> Berechtigung kann die Notch nicht über anderen Apps erscheinen. Das ist die Kernfunktion
> der App.
>
> **Benachrichtigungszugriff:** wird ausschließlich genutzt, um Mediensitzungen zu steuern
> (Play/Pause, Weiter, Zurück) und zu erkennen, welcher Player gerade läuft (Titel, Interpret,
> Cover). Android gibt Mediensitzungen anderer Apps nur an Apps mit diesem Zugriff heraus.
> Andere Benachrichtigungen wie Nachrichten oder E-Mails werden weder gelesen noch gespeichert
> oder übertragen. Ohne den Zugriff funktioniert alles außer der Musiksteuerung.
>
> Abos verlängern sich automatisch und sind jederzeit im Play Store kündbar.

---

## Begründungen der sensiblen Berechtigungen (für Review und Play-Console-Erklärungen)

Diese Texte sind für *App-Inhalte → Berechtigungen/Vordergrunddienste* und für Rückfragen der
Play-Prüfung gedacht. Google prüft in englischer Sprache, deshalb steht die englische Fassung
jeweils darunter.

### `SYSTEM_ALERT_WINDOW` – Über anderen Apps einblenden

**Wofür:** Die gesamte Oberfläche von DevNotch ist ein Overlay. Der `NotchOverlayService`
legt über den `WindowManager` ein Fenster vom Typ `TYPE_APPLICATION_OVERLAY` an. Darin rendert
er drei Ansichten:
1. die eingeklappte Pille passgenau um die Kamera-Aussparung (Position über `DisplayCutout`),
2. das aufgeklappte **Floating-Dashboard** mit Tabs (Dev, Timer, Notizen, Clip, KI),
3. den **Edge-Player** bzw. die Griffleiste am Bildschirmrand.

**Warum nötig:** Nur mit dieser Berechtigung kann eine App dauerhaft sichtbare, interaktive
Elemente über anderen Apps darstellen. Bildschirm-Widgets oder Benachrichtigungen können
weder an der Kamera-Aussparung positioniert werden noch sich animiert öffnen.

**Umgang:** Das Fenster ist nur so groß wie die sichtbare Pille bzw. das Dashboard und fängt
keine Eingaben außerhalb davon ab. Bildschirminhalte anderer Apps werden nicht gelesen. Der
Nutzer erteilt die Berechtigung selbst in den Systemeinstellungen, und der Dienst lässt sich
jederzeit über die Benachrichtigung („Beenden“) oder den Schalter in der App stoppen.

> **EN:** DevNotch's core feature is a "dynamic notch" overlay. `NotchOverlayService` adds a
> `TYPE_APPLICATION_OVERLAY` window that draws (1) a pill around the camera cutout, (2) an
> expandable floating dashboard (timer, notes, clipboard, stats) and (3) an edge media player.
> No other API can render persistent, interactive UI anchored to the display cutout on top of
> other apps. The window only covers the visible pill/dashboard, never reads other apps'
> screen content, and can be stopped at any time from the notification or the in-app switch.

### `BIND_NOTIFICATION_LISTENER_SERVICE` – Benachrichtigungszugriff

**Wofür:** Der Zugriff dient **ausschließlich** zwei Dingen: Mediensitzungen zu steuern und den
laufenden Player zu erkennen. Android liefert die aktiven Mediensitzungen anderer Apps
(`MediaSessionManager.getActiveSessions(...)`) nur an Apps, deren `NotificationListenerService`
vom Nutzer freigegeben ist. Über die erhaltenen `MediaController` liest DevNotch Titel,
Interpret, Cover und Wiedergabestatus und sendet Play/Pause, Weiter und Zurück.

**Was nicht passiert:** `MediaNotificationListener` prüft jede eingehende Benachrichtigung
nur darauf, ob sie eine Mediensitzung enthält (`Notification.EXTRA_MEDIA_SESSION`). Alle
anderen Benachrichtigungen werden sofort verworfen, nicht ausgewertet und nicht gespeichert.
Medieninformationen bleiben im Arbeitsspeicher, werden nie auf den Datenträger geschrieben und
nie übertragen. DevNotch beantwortet, schließt oder verändert keine Benachrichtigungen.

**Warum nötig:** Ohne diesen Zugriff gibt Android die Mediensitzungen fremder Apps nicht heraus.
Die Musiksteuerung in der Notch und im Edge-Player wäre dann unmöglich. Alle anderen Funktionen
laufen auch ohne den Zugriff.

> **EN:** Used **exclusively** to control media sessions and detect the currently playing
> player. Android only exposes other apps' active media sessions
> (`MediaSessionManager.getActiveSessions`) to an app whose `NotificationListenerService` the
> user has enabled. DevNotch reads title, artist, artwork and playback state through the
> returned `MediaController`s and sends play/pause/skip commands. Every posted notification is
> checked only for `EXTRA_MEDIA_SESSION`; all other notifications are discarded immediately,
> never parsed, stored or transmitted. Media metadata stays in memory only. The app never
> replies to, dismisses or modifies notifications. All features except media control work
> without this access.

### `FOREGROUND_SERVICE_SPECIAL_USE` – Vordergrunddienst (Erklärung in der Play Console)

**Subtyp im Manifest:** „Persistent dynamic notch overlay showing media controls and status“.

**Begründung:** Das Overlay muss dauerhaft sichtbar bleiben, solange der Nutzer es
eingeschaltet hat. Keiner der Standardtypen passt: `mediaPlayback` ist falsch, weil DevNotch
nichts selbst abspielt; die übrigen Typen beschreiben andere Dienste. Der Dienst zeigt eine
dezente Benachrichtigung mit „Beenden“-Aktion und startet nur nach ausdrücklicher Aktivierung
in der App bzw. nach einem Neustart oder App-Update, wenn er vorher aktiv war.

> **EN:** Keeps the user-enabled notch overlay alive. No standard FGS type fits: the app
> doesn't play media itself, it only displays and controls other apps' sessions. Started only
> after the user turns the overlay on (or after a reboot or app update if it was on). It shows a low-priority
> notification with a "Stop" action. The Play Console declaration requires a short video:
> enable the overlay, expand the dashboard, play music, then stop it from the notification.

### `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

**Begründung:** Die Berechtigung öffnet nur auf Wunsch des Nutzers (Karte „Akku-Optimierung“
in der App) den Systemdialog. Ohne Ausnahme beenden manche Hersteller (Xiaomi, Samsung u. a.)
das Overlay im Leerlauf. **Hinweis:** Google lässt diese Berechtigung nur in begrenzten Fällen
zu. Lehnt die Prüfung sie ab, kann die App stattdessen die Einstellungsliste
(`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) öffnen. Dafür braucht sie keine Berechtigung.

---

## Datensicherheit (Play-Console-Formular) – Vorschlag

| Frage | Antwort |
|---|---|
| Werden Nutzerdaten erhoben oder geteilt? | **Ja:** Kaufdaten über RevenueCat |
| Kaufverlauf | Erhoben, nicht geteilt · Zweck: App-Funktionalität (Pro-Freischaltung) · erforderlich für Käufe |
| Geräte- oder andere IDs | Erhoben (anonyme RevenueCat-App-Nutzer-ID) · Zweck: App-Funktionalität |
| Notizen, Zwischenablage, Medieninfos | **Nicht erhoben** (verlassen das Gerät nicht) |
| GitHub-/KI-Abrufe | Gehen direkt vom Gerät an den vom Nutzer gewählten Dienst und werden vom Nutzer selbst ausgelöst. **Vor dem Ausfüllen prüfen**, ob das unter die Play-Ausnahme „vom Nutzer initiierte Übertragung“ fällt; sonst als „App-Aktivität / sonstige“ angeben |
| Verschlüsselung bei der Übertragung | Ja (HTTPS). Ausnahme: eine vom Nutzer eingetragene lokale `http://`-Ollama-Adresse. Deshalb zur Sicherheit „Nein“ angeben oder Ollama-HTTP entfernen |
| Löschung möglich? | Ja: App-Daten löschen bzw. deinstallieren. Ein Konto gibt es nicht |

## Grafiken – Checkliste

- [ ] App-Symbol 512 × 512 px (PNG, 32-Bit)
- [ ] Vorstellungsgrafik 1024 × 500 px
- [ ] Mindestens 4 Smartphone-Screenshots (Vorlagen in `docs/screenshots/`: Pille mit Musik,
      Dashboard-Tabs, Edge-Player, Paywall); Tablet-Screenshots aus `split_drawer.png`
- [ ] Video für die Vordergrunddienst-Erklärung (siehe oben)
