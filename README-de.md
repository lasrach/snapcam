# Snapcam

Snapcam fügt Minecraft eine platzierbare Kamera hinzu. Die Kamera auf eine Szene richten und dann im passenden Stil aufnehmen:

- **Aus der Hand fotografieren** — Kamera-Item in der Hand, Schleichen halten und Benutzen zum Aktivieren, dann jederzeit beim Schleichen aufnehmen. Schleichen loslassen zum Beenden.
- **Live-Ansicht** — Kamera in der Welt platzieren, herangehen und eintreten: Sicht frei drehen und nach Belieben aufnehmen.
- **Selbstauslöser** — Kamera platzieren, aus kurzer Distanz mit Schleichen+Benutzen einen 5-Sekunden-Countdown starten, dann ins Bild stellen.

Keine Befehle. Keine eigenen Tastenkürzel — nur Benutzen und Schleichen. Funktioniert mit Controller und Steam Deck.

Inspiriert von der Kamera aus Minecraft: Education Edition. Benötigt **Fabric** für **Minecraft: Java Edition 1.21.1**, installiert auf Server und Client.

## Features

### Kamera platzieren

Das **Kamera**-Item (Kreativmenü → Werkzeuge & Hilfsmittel) in die Hand nehmen und auf einer beliebigen Blockfläche Benutzen drücken:

- **Auf einer ebenen Fläche** — stellt eine Stativkamera auf dem Boden auf, Linse zeigt zum Spieler hin.
- **An einer Wand** — befestigt die Kamera an der Wand, Linse zeigt nach außen.

Die Kamera ist eine serverseitige Entität: alle Spieler auf dem Server sehen sie.

### Kameraperspektive einnehmen

Benutzen auf einer freien Kamera wechselt die Sicht auf diese Kamera. Während man darin ist:

- Die eigene Spielfigur bleibt an ihrem Platz (für andere Spieler und auf eigenen Screenshots sichtbar).
- Maus- bzw. Stick-Eingabe dreht die Kamerasicht.
- Bewegungseingaben werden unterdrückt — die Kamera ist fest montiert.
- Das normale HUD (Schnellleiste, Fadenkreuz, Statusanzeigen) wird ausgeblendet.
- **Schleichen** beendet die Kamerasicht und kehrt zur eigenen Spielfigur zurück.

Jede Kamera kann nur von einem Spieler gleichzeitig genutzt werden.

### Screenshot aufnehmen

| Situation | Eingabe | Ergebnis |
|-----------|---------|----------|
| In der Kameraperspektive | Benutzen / Abzug | Screenshot aus der Kameraperspektive |
| Außerhalb, Kamera-Item in der Hand | Schleichen halten + Benutzen (nicht auf eine Kamera gerichtet) | Aktiviert den Modus „aus der Hand" — Benutzen zum Aufnehmen, Schleichen loslassen zum Beenden |
| Außerhalb, nah an einer platzierten Kamera | Schleichen + Benutzen an der Kamera | 5-Sekunden-Countdown, dann automatische Aufnahme |

Screenshots werden vom normalen Minecraft-Screenshot-System gespeichert (Ordner `screenshots/`, fortlaufende Nummerierung). Ein weißer Blitz bestätigt jede Aufnahme.

### Selbstauslöser

Nah an eine platzierte Kamera herantreten, in der man sich **nicht** befindet, und Schleichen + Benutzen. Eine große Countdown-Zahl (5 → 1) erscheint auf dem Bildschirm. Nach fünf Sekunden wechselt der Mod kurz in die Kameraperspektive, macht den Screenshot und kehrt automatisch zum normalen Spiel zurück — so hat man Zeit, sich vor der Kamera zu positionieren.

Der Timer bricht automatisch ab, wenn die Zielkamera entfernt oder von einem anderen Spieler belegt wird.

### Kamera entfernen

Die Kamera-Entität angreifen, um sie zu zerstören. Sie lässt das Kamera-Item fallen, unabhängig davon, wer sie platziert hat.

## Kompatibilität

| Mod | Status |
|-----|--------|
| Xaero's Minimap | Unterstützt — Minimap wird in der Kameraperspektive ausgeblendet |
| Mod Menu | Unterstützt — Mod erscheint in der Liste (noch kein Konfigurationsmenü) |
| Controlify | Unterstützt — Schleichen in den Controlify-Einstellungen auf **Halten** stellen (nicht Umschalten) |
| Andere HUD-Mods | Können über die Kamerasicht hinausragen; MIT-Lizenz, gerne selbst patchen |

Der Mod muss auf **Server und Client** installiert sein. Der Perspektivwechsel wird serverseitig koordiniert; Clients ohne den Mod können auf einem modifizierten Server nicht versehentlich die Kameransicht aktivieren.

## Bauen

**Voraussetzungen:** Java 21, Internetverbindung für den ersten Build (Gradle lädt Minecraft und Fabric herunter).

```bash
git clone <repo>
cd snapcam
./gradlew build
# → build/libs/snapcam-0.2.0.jar
```

Die JAR-Datei in den `mods/`-Ordner auf dem Server und bei jedem Client kopieren.

### Tests ausführen

```bash
./gradlew test
```

Unit-Tests decken die Countdown-Logik des Selbstauslösers und die Zustandsmaschine der Hand-Kamera ab. Kein Spielstart erforderlich.

### IDE-Einrichtung

```bash
./gradlew genSources   # erzeugt Minecraft-Quellen zur Navigation
```

Als Gradle-Projekt importieren. Mit dem Task `runClient` lässt sich ein Entwicklungs-Client mit geladenem Mod starten, ohne JARs manuell kopieren zu müssen.

## Abhängigkeiten

| Abhängigkeit | Seite | Erforderlich |
|--------------|-------|--------------|
| Fabric Loader ≥ 0.15 | Beide | Ja |
| Fabric API 0.116.x + 1.21.1 | Beide | Ja |
| Mod Menu | Client | Nein (empfohlen) |

## Architektur

```
src/main/java/net/snapcam/
├── SnapcamMod.java                 # Server-Init: Entitäten, Items, Netzwerk, Kreativtab
├── SnapcamClient.java              # Client-Init: Eingaben, Screenshot, HUD-Overlay
├── entity/
│   ├── CameraEntity.java           # Server-Entität — verwaltet OWNER/VIEWER/PLACED_ON_GROUND
│   └── ModEntities.java
├── item/
│   ├── CameraItem.java             # Platzier-Verhalten per Rechtsklick
│   └── ModItems.java               # Registriert auch interne Render-Only-Items
├── client/
│   ├── EntityCameraController.java # Verwaltet POV-Bindung an eine platzierte Kamera
│   ├── HandCameraController.java   # Verwaltet den Handheld-Fotomodus
│   ├── TimedShotController.java    # Zustandsmaschine für den 5-Sekunden-Countdown
│   ├── CameraViewEntity.java       # Clientseitige Hilfs-Entität für Position/Rotation der Kamera
│   ├── CameraEntityRenderer.java   # Zweistufiger Renderer: feste Beine + drehender Körper
│   └── ModMenuIntegration.java
├── mixin/
│   ├── CameraMixin.java            # Überschreibt Camera.setup() für die CameraViewEntity-Position
│   ├── EntityTurnMixin.java        # Leitet Mausbewegung an CameraViewEntity weiter
│   ├── GuiMixin.java               # Blendet Schnellleiste und Fadenkreuz in der Kamerasicht aus
│   ├── MinecraftMixin.java         # Räumt Kamerazustand bei Disconnect auf
│   └── XaeroHudMixin.java          # Unterdrückt Xaero-Minimap in der Kamerasicht
└── network/
    ├── AttachCameraPacket.java     # Server→Client: Perspektive für Entitäts-ID übernehmen
    ├── DetachCameraPacket.java     # Client→Server: Kamera freigeben
    └── SnapcamNetwork.java
```

### Wie der Perspektivwechsel funktioniert

1. Spieler klickt eine `CameraEntity` auf dem Server an.
2. Server setzt `VIEWER_UUID` und schickt `AttachCameraPacket` an den Client.
3. Client erstellt eine `CameraViewEntity` (unsichtbare Hilfs-Entität) an der Kameraposition und ruft `MC.setCameraEntity(viewEntity)` auf.
4. `CameraMixin` fängt `Camera.setup()` ab — positioniert die Renderkamera an den Koordinaten der View-Entität und setzt `camera.entity = MC.player`, damit die Spielfigur auf Screenshots sichtbar bleibt.
5. Bei Schleichen oder Disconnect schickt der Client `DetachCameraPacket`; der Server löscht `VIEWER_UUID`.

## Lizenz

MIT — © 2025 Lasrach <lasrach@gmail.com>
