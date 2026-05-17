# Changelog

## 0.3.0

### New features
- **Zoom** — scroll wheel or W/S while in placed-camera view zooms in and out
  (12–200 mm equivalent focal length, logarithmic stepping for even feel across
  the range). Current focal length shown in the viewfinder HUD.
- **Zoom persistence** — zoom level is stored in the camera entity and synced to
  the server. Survives chunk unloads and world reloads; timed shots always use the
  focal length the camera was left at.
- **Animated lens barrel** — the camera body is a three-part assembly: fixed body,
  sliding lens plate, and an inner barrel that extends out as you zoom in. The
  viewpoint tracks the lens face so it is never rendered from inside the glass.
- **Ceiling-mounted camera** — place on the underside of any block (aim up and
  press Use). A centred plate grips the block above; a rod hangs down to the
  pan/tilt head. Supports full 360° pan and ±70/80° pitch.
- **Extended interaction range** — holding the Camera item raises entity reach
  to ~20 blocks, so timed shots can be triggered from across a room.

### Bug fixes
- Wall-mounted camera hitbox was the same tall box as the tripod; now correctly
  sized (0.9 × 0.9 in the attachment plane, 0.85 deep) and orientation-aware.
- Tripod hitbox raised by 0.25 blocks to match the physical model height.
- Wall camera debug line (F3+B) was pointing the wrong way and too high; moved
  down 1 block and rotated 180°.
- Handheld mode: pressing Use while aimed at a valid block face was placing an
  additional camera entity at the same time as shooting. Fixed.
- Handheld mode: holding Shift in creative flight caused the player to fly
  downward while in photo mode. Fixed by suppressing `shiftKeyDown` in the
  custom input handler installed on enter.

---

## 0.2.0

### New features
- Placed camera entity (tripod on flat surfaces, wall-mount on side faces).
- Hand-held photo mode (Sneak + Use while holding Camera item).
- Self-timer / timed shot — Sneak + Use on a placed camera starts a 5-second
  countdown; the mod briefly enters the camera POV, takes the screenshot, and
  returns control automatically.
- Viewfinder HUD overlay: L-corner brackets, focus brackets, side tick scales,
  centre crosshair, ±3 EV exposure bar, focal length / shutter / aperture / ISO
  readout.
- White flash effect confirms each screenshot.
- Xaero's Minimap: minimap hidden while in camera view.
- Mod Menu: mod appears in the installed-mods list.
