# Testen auf Emulator und Gerät

Paketname: **`com.frezzybuilds.devnotch`**. Mit einem falschen Namen (z. B. `com.devnotch`)
zeigt `dumpsys` einfach nichts an, und das sieht aus wie ein beendeter Service.

---

## 1. Emulator mit Kamera-Aussparung

1. Lege im AVD Manager ein **Pixel 7** oder **Pixel 8** an (Image: API 34 oder 35,
   „Google Play“ für Käufe).
2. DevNotch installieren, Overlay-Berechtigung erteilen und die Notch einschalten.
3. Entwickleroptionen aktivieren: *Einstellungen → Über das Telefon →* 7× auf *Build-Nummer*.
4. *Einstellungen → System → Entwickleroptionen → Display-Aussparung simulieren*
   („Display cutout“) und nacheinander die Varianten wählen. Alternativ geht das per ADB, ohne
   sich durch die Menüs zu klicken:

```bash
# Verfügbare Varianten anzeigen (Namen unterscheiden sich je nach Android-Version leicht)
adb shell cmd overlay list | grep cutout.emulation

# Eine Variante aktivieren (exklusiv, schaltet die vorherige ab)
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.hole
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.corner
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.double
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.tall

# Zurück zum Gerätestandard: die aktive Variante wieder abschalten
adb shell cmd overlay disable com.android.internal.display.cutout.emulation.corner
```

Die Notch reagiert ohne Neustart, weil sie über `WindowInsets` neu ausgerichtet wird.

### Erwartetes Verhalten

| Variante | Erwartung |
|---|---|
| **Punch Hole** (Pixel-Standard) | Pille liegt symmetrisch um das Loch, links/rechts und oben/unten gleicher Rand |
| **Double cutout** (oben + unten) | Nur die obere Aussparung zählt; die untere wird ignoriert |
| **Tall / Wide cutout** (breite Notch) | Pille ist so breit wie die ganze Aussparung plus 24 dp Rand pro Seite; Inhalte (Timer, Musik) sitzen links/rechts **neben** der Notch, nicht dahinter |
| **Corner cutout** (Ecke oben links) | Eine zentrierte Pille würde aus dem Bild ragen → Pille sitzt **oben in der Mitte**, wie auf Geräten ohne Aussparung |
| Punch-Hole nah am Rand (z. B. Galaxy S10) | Wie Corner: Rückfall auf die bildschirmzentrierte Pille |
| Keine Aussparung | 120 × 35 dp, oben zentriert, 8 dp Abstand |

Prüfe jede Variante im **Hoch- und Querformat** sowie im aufgeklappten Dashboard. Die Logik
und alle Fälle oben sind in `NotchGeometryTest` abgedeckt.

---

## 2. Bildschirmtastatur und Fokus

Das Overlay ist standardmäßig `FLAG_NOT_FOCUSABLE`: Tasten und Tastatur bleiben bei der App
dahinter. Fokussierbar wird es **nur**, wenn die Notch aufgeklappt ist *und* der sichtbare
Inhalt Fokus braucht (`RequestOverlayFocus`):

| Ansicht | Fokussierbar | Grund |
|---|---|---|
| Eingeklappt | nie | Eingaben gehen an die App dahinter |
| Dev, Timer, AI | nein | keine Textfelder |
| **Notizen** | ja | Bildschirmtastatur für Titel/Text |
| „+ Shortcut“ (App-Suche, URL) | ja | Textfelder |
| **Clip** | ja | Android 10+ gibt die Zwischenablage nur mit Fensterfokus heraus |

Beim Einklappen wird das Flag **sofort** wieder gesetzt, nicht erst nach der nächsten
Recomposition.

### Manuell prüfen

1. In einer anderen App (z. B. Chrome-Suchfeld) die Tastatur öffnen.
2. Notch aufklappen (Dev-Tab) → **die Tastatur der App bleibt offen**, Tippen geht weiter.
3. Tab *Notizen* → ins Textfeld tippen → **die Bildschirmtastatur erscheint**, Text kommt an.
   Im Edge-Modus schiebt sich das Fenster über die Tastatur (`SOFT_INPUT_ADJUST_PAN`).
4. **Zurück** → schließt erst die Tastatur, ein zweites Zurück **klappt die Notch ein**.
5. Danach in der App dahinter tippen und *Zurück* drücken: beides funktioniert sofort wieder.
6. Text kopieren, Notch aufklappen, Tab *Clip* → der Eintrag steht oben.

Per ADB den Fokus prüfen:

```bash
# Welches Fenster hat den Fokus? Eingeklappt darf es NICHT DevNotch sein.
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

# Flags des Overlay-Fensters (NOT_FOCUSABLE muss eingeklappt gesetzt sein)
adb shell dumpsys window windows | grep -A3 "com.frezzybuilds.devnotch" | grep -i "fl="
```

Automatisiert: `OverlayFocusTest` (Tabs, Zurück-Taste, Flag-Logik).

---

## 3. Hintergrund-Stabilität: Doze und App-Standby

DevNotch läuft als **Vordergrunddienst** (`specialUse`). Solche Dienste beendet Android im
Doze-Modus nicht, Doze drosselt nur Netzwerk und Alarme. Das zeigt sich nur an der
KI-Kostenaktualisierung, die dann später läuft.

```bash
PKG=com.frezzybuilds.devnotch

# Ausgangszustand: Service läuft im Vordergrund?
adb shell dumpsys activity services $PKG | grep -E "ServiceRecord|isForeground"
#   → ServiceRecord{… com.frezzybuilds.devnotch/.service.NotchOverlayService}
#   → isForeground=true foregroundId=… types=0x40000000   (specialUse)

# Akku-Ausnahme gesetzt? (Karte „Akku-Optimierung“ in der App)
adb shell dumpsys deviceidle whitelist | grep $PKG

# --- Doze erzwingen ---------------------------------------------------------------
adb shell dumpsys battery unplug          # Gerät „vom Strom trennen“
adb shell input keyevent KEYCODE_SLEEP    # Bildschirm aus
adb shell dumpsys deviceidle force-idle   # sofort in Deep Doze
adb shell dumpsys deviceidle get deep     # → IDLE

# … ein paar Minuten warten, dann prüfen:
adb shell pidof $PKG                      # Prozess lebt noch (PID ausgegeben)
adb shell dumpsys activity services $PKG | grep -E "ServiceRecord|isForeground"

# Bildschirm an → die Notch muss ohne Neustart sofort da sein
adb shell input keyevent KEYCODE_WAKEUP

# --- App-Standby-Bucket (gilt unabhängig von Doze) ---------------------------------
adb shell am get-standby-bucket $PKG      # 5/10 = ACTIVE/WORKING_SET ist ideal
adb shell am set-inactive $PKG true       # Standby simulieren …
adb shell am get-inactive $PKG
adb shell am set-inactive $PKG false

# --- Speicherdruck: Hintergrundprozesse beenden ------------------------------------
adb shell am kill $PKG                    # beendet nur Hintergrund-Prozesse;
adb shell pidof $PKG                      # mit Vordergrunddienst muss die PID bleiben

# --- Alles zurücksetzen ------------------------------------------------------------
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

### Neustart und Update

```bash
adb reboot          # nach dem Boot startet der BootReceiver die Notch wieder, wenn sie aktiv war
adb install -r app-debug.apk   # MY_PACKAGE_REPLACED: Notch läuft nach dem Update weiter
```

`BOOT_COMPLETED` lässt sich auf aktuellen Android-Versionen nicht per
`am broadcast` aus der Shell senden (geschützter Broadcast). Deshalb `adb reboot` verwenden.

### Hersteller-Akkusparer

Doze im Emulator entspricht Stock-Android. Xiaomi (MIUI/HyperOS), Samsung (One UI), Huawei
und Oppo beenden Apps zusätzlich mit eigenen Mechanismen, die sich per ADB nicht simulieren
lassen. Dafür zeigt die App die Herstellerhinweis-Karte. Auf solchen Geräten den Test
über Nacht mit ausgeschaltetem Bildschirm wiederholen.
