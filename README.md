# Snapcam

Snapcam adds a placeable camera to Minecraft. Point it at a scene, then shoot in
whichever style suits the moment:

- **Handheld** — hold the camera item, crouch and press Use to enter handheld
  mode, then shoot at any time while crouching. Release crouch to exit.
- **Live view** — place the camera in the world, walk up to it, and step inside:
  look around freely and shoot whenever you're ready.
- **Self-timer** — place the camera, crouch-click it from close range to start a
  5-second countdown, then get into the shot before it fires.

No commands. No custom keybinds — only Use and Sneak. Works on controllers and Steam Deck.

Inspired by the Minecraft: Education Edition camera. Requires **Fabric** on
**Minecraft: Java Edition 1.21.1**, installed on both server and client.

## Features

### Placing a camera

Hold the **Camera** item (creative inventory → Tools & Utilities) and
press Use on any block face:

- **On a flat surface** — places a tripod camera on the ground, lens facing toward
  you.
- **On a side face** — mounts the camera to the wall, lens pointing outward.

The camera is a server-side entity: every player on the server sees it.

### Entering camera view

Use an unoccupied camera to switch your POV to it. While inside:

- Your character stays at its original position (visible to other players and in
  your own screenshots).
- Mouse / stick input rotates the camera view.
- All movement input is suppressed — the camera is fixed.
- The vanilla HUD (hotbar, crosshair, health bar) is hidden.
- Press **Sneak** to exit and return to your own body.

Only one player can occupy a camera at a time.

### Taking a screenshot

| Situation | Input | Result |
|-----------|-------|--------|
| Inside camera view | Use / trigger | Screenshot from camera POV |
| Outside, holding Camera item | Hold Shift + Use (not aimed at a camera) | Enters hand-held mode — Use to shoot, release Shift to exit |
| Outside, close to a placed camera | Shift + Use the camera | 5-second countdown, then auto-shot from that camera |

Screenshots are saved by Minecraft's normal screenshot system
(`screenshots/` folder, sequential numbering). A white flash confirms each
shot.

### Timed shot (self-timer)

Step close to a placed camera you are **not** inside and Shift + Use it. A large countdown
number (5 → 1) appears on screen. After five seconds the mod briefly enters the
camera's POV, takes the screenshot, and returns you to normal play — so you can
step in front of the camera before it fires.

The timer cancels automatically if the targeted camera is removed or claimed by
another player.

### Removing a camera

Hit the camera entity to break it. It drops the Camera item regardless of who
placed it.

## Compatibility

| Mod | Status |
|-----|--------|
| Xaero's Minimap | Supported — minimap is hidden while in camera view |
| Mod Menu | Supported — mod appears in the list (no config screen yet) |
| Controlify | Supported — set Sneak to **held** mode (not toggle) in Controlify settings |
| Other HUD mods | May render on top of camera view; MIT licensed, feel free to patch |

Requires the mod on **both server and client**. The POV switch is coordinated
server-side; clients without the mod cannot accidentally enter camera view on a
modded server.

## Building

**Requirements:** Java 21, internet access for first build (Gradle downloads
Minecraft and Fabric).

```bash
git clone <repo>
cd snapcam
./gradlew build
# → build/libs/snapcam-0.2.0.jar
```

Copy the jar to the `mods/` folder on the server and every client.

### Running tests

```bash
./gradlew test
```

Unit tests cover the timed-shot countdown logic and hand-held camera state
machine. No game launch required.

### IDE setup

```bash
./gradlew genSources   # generates Minecraft sources for navigation
```

Import as a Gradle project. The `runClient` task launches a dev client with the
mod loaded if you want to iterate without copying jars.

## Dependencies

| Dependency | Side | Required |
|------------|------|----------|
| Fabric Loader ≥ 0.15 | Both | Yes |
| Fabric API 0.116.x + 1.21.1 | Both | Yes |
| Mod Menu | Client | No (recommended) |

## Architecture

```
src/main/java/net/snapcam/
├── SnapcamMod.java                 # Server init: entities, items, network, creative tab
├── SnapcamClient.java              # Client init: input handling, screenshot, HUD overlay
├── entity/
│   ├── CameraEntity.java           # Server entity — owns OWNER/VIEWER/PLACED_ON_GROUND data
│   └── ModEntities.java
├── item/
│   ├── CameraItem.java             # Place-on-right-click behaviour
│   └── ModItems.java               # Also registers internal rendering-only items
├── client/
│   ├── EntityCameraController.java # Manages POV attachment to a placed camera
│   ├── HandCameraController.java   # Manages hand-held photo mode
│   ├── TimedShotController.java    # 5-second countdown state machine
│   ├── CameraViewEntity.java       # Fake client-side entity that holds camera position/rotation
│   ├── CameraEntityRenderer.java   # Two-pass renderer: fixed legs + rotating body
│   └── ModMenuIntegration.java
├── mixin/
│   ├── CameraMixin.java            # Overrides Camera.setup() to position at CameraViewEntity
│   ├── EntityTurnMixin.java        # Redirects mouse-look to CameraViewEntity while active
│   ├── GuiMixin.java               # Hides hotbar and crosshair in camera view
│   ├── MinecraftMixin.java         # Cleans up camera state on disconnect
│   └── XaeroHudMixin.java          # Suppresses Xaero minimap in camera view
└── network/
    ├── AttachCameraPacket.java     # Server→Client: enter POV for entity id
    ├── DetachCameraPacket.java     # Client→Server: release claim
    └── SnapcamNetwork.java
```

### How POV switching works

1. Player right-clicks a `CameraEntity` on the server.
2. Server sets `VIEWER_UUID`, sends `AttachCameraPacket` to that client.
3. Client creates a `CameraViewEntity` (invisible fake entity) at the camera's
   position and calls `MC.setCameraEntity(viewEntity)`.
4. `CameraMixin` intercepts `Camera.setup()` — positions the render camera at
   the view entity's coordinates and sets `camera.entity = MC.player` so that
   `LevelRenderer`'s local-player render guard allows the player body to appear
   in screenshots.
5. On sneak or disconnect, client sends `DetachCameraPacket`; server clears
   `VIEWER_UUID`.

## License

MIT — © 2025 Lasrach <lasrach@gmail.com>
