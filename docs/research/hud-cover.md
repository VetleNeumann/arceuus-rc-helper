# HUD cover and draw distance for Sightline — research

Researched 2026-09-06 against the RuneLite source (`runelite/runelite` master at commit
`ac79ed8bd8926bec7bf172aa291574b4d944b0e7`, 2026-09-03), the Plugin Hub repository
(`runelite/plugin-hub` master at `4ca20dd4ee85700d5a8cd98c3997c0e57a3f72f4`) and, for the CPU
renderer only, the last public copy of RuneLite's scene mixin (tag `runelite-parent-1.5.0`,
2018-11-16; the mixins left the public repo later). Evidence grades used throughout:
**[primary]** = RuneLite/plugin-hub source read directly; **[primary, historical]** = the 2018
mixin, whose logic is corroborated by master where noted; **[secondary]** = third-party plugin
source; **[inference]** = my own reasoning, not observed in game.

Ticket: #20 (part of #18, blocks #25).

## Question (verbatim)

> For fixed, resizable classic and resizable modern layouts, which top-level widgets can cover a
> clickbox in the 3D viewport (minimap and orbs container, chat container, side panel and its
> stones, anything else RuneLite's own overlay system respects), what are their
> `net.runelite.api.gameval.InterfaceID` component ids, and how do RuneLite's
> `WidgetOverlay`/`OverlayManager`/`SnapCorners` enumerate them? Is `Widget.getBounds()` reliable
> for transparent or side-panel-collapsed states? Also: is reading another plugin's config (GPU
> draw distance via `ConfigManager.getConfiguration("gpu", ...)`) acceptable under Plugin Hub
> rules, and what draw-distance semantics apply for the GPU and CPU renderers (measured from the
> player or the camera, tiles or chunks, extended scene)? Deliver the widget list per layout and
> a rule for "not drawn".

## TL;DR

- **Layout** comes from `client.getTopLevelInterfaceId()`: `InterfaceID.TOPLEVEL` (548) fixed,
  `TOPLEVEL_OSRS_STRETCH` (161) resizable classic, `TOPLEVEL_PRE_EOC` (164) resizable modern.
  RuneLite itself branches on `client.isResized()` first and then on `TOPLEVEL_PRE_EOC`
  [primary, S3 l.454-483]. Confidence: **high**.
- **RuneLite's own notion of "the HUD"** is exactly three things per resizable layout: the chat
  container, the side panel (with its stones) and the minimap container. That is the list
  `WidgetOverlays.createOverlays()` opens with, "in render order" [primary, S2 l.57-66]. The
  renderer never point-tests widgets; overlays avoid the HUD only through snap corners anchored
  to `HUD_CONTAINER_FRONT` / `CHAT_CONTAINER` bounds and through drag collision with those three
  widget overlays [primary, S3 l.454-560, S4 l.365, S4 l.824]. Confidence: **high**.
- **Fixed layout has nothing to subtract**: the 3D view is the rectangle
  `(getViewportXOffset, getViewportYOffset, getViewportWidth, getViewportHeight)` and the chat,
  side and minimap sit outside it. RuneLite clips `ABOVE_SCENE`/`UNDER_WIDGETS` overlays to that
  rectangle in fixed mode and to the whole canvas otherwise [primary, S4 l.727-740].
  Confidence: **high**.
- **`Widget.getBounds()` and `isHidden()` are what RuneLite core uses** for the same purpose,
  every frame, on the client thread (`WidgetOverlay.getParentBounds`, `positionSnapcorners`).
  `isHidden()` walks the parents and "must be ran on the client thread" [primary, S2 l.208-230,
  S5 l.389-396]. The collapsed chat is `Chatbox.CHATAREA` going *self*-hidden while the container
  stays; core handles that case explicitly [primary, S3 l.486-489, l.550-556]. Transparency is
  `getOpacity()`, separate from bounds [primary, S5 l.223-228]; whether a transparent chat still
  swallows clicks is **[inference]** and needs one in-game check. Confidence: **high** for chat,
  **medium** for the collapsed side panel (the hidden node is inferred, see section 4).
- **Reading `gpu.drawDistance` is not against any hub rule**, and a hub plugin (RelicScape) was
  merged binding `GpuPluginConfig` through `ConfigManager` [primary, S9, S10; secondary, S12]. It
  is still the wrong signal: it is meaningless with the GPU plugin off and wrong under 117 HD.
  **Use `client.isGpu()` and `client.getTopLevelWorldView().getScene().getDrawDistance()`**,
  which every GPU renderer (core GPU, 117 HD, RelicScape) writes every frame before drawing
  [primary, S6 l.878-881, S7; secondary, S12, S13]. Confidence: **high**.
- **"Not drawn" rule**: a tile is drawn when it lies in the square
  `[cameraTile - D, cameraTile + D)` on both axes, where `D` is the GPU draw distance (default 50,
  max 184, tiles) or **25 on the CPU renderer**, and the centre is the **camera's** tile, not the
  player's [primary, historical, S8 l.51, l.91, l.142-172; corroborated on master by the GPU fog
  box `cameraX ± drawDistance` in S11 l.114-117]. The CPU renderer additionally culls by a
  pitch/yaw visibility map, which the on-screen projection already approximates. Confidence:
  **medium-high** (CPU value from a 2018 source, shape confirmed on master).

## 1. Layout detection [primary]

`OverlayManager.getHudContainer()` / `getChatContainer()` [S3 l.454-483]:

```java
if (client.isResized()) {
    if (client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_PRE_EOC) { /* modern */ }
    else { /* classic */ }
}
/* fixed */
```

`InterfaceID.TOPLEVEL = 548`, `TOPLEVEL_OSRS_STRETCH = 161`, `TOPLEVEL_PRE_EOC = 164`,
`CHATBOX = 162`, `ORBS = 160` [S1 l.154-171, l.554]. The plugin can drop `isResized()` and
switch on the three ids directly; core keeps both because `ABOVE_WIDGETS` overlays are
registered against all three top-level interfaces [S3 l.349-353].

## 2. What can cover a clickbox, per layout [primary for ids and the core list]

All ids are `net.runelite.api.gameval.InterfaceID.<Class>.<NAME>` [S1]. "Core list" marks the
components RuneLite's `WidgetOverlays` treats as the HUD [S2 l.57-66]; "children" are the
sub-components worth testing separately when a container stays visible but a part collapses.

### Fixed — `Toplevel` (548) [S1 l.19729-19826]

The 3D view is `Toplevel.VIEWPORT = 0x0224_001a`, and RuneLite treats the viewport rectangle
from `Client.getViewport*()` as the scene area in this layout [S4 l.727-735]. Chat, side and
minimap are outside it, so the **only cover is from in-viewport HUD elements**:

| Role | Component | Note |
|---|---|---|
| minimap + orbs | `MAPCONTAINER = 0x0224_0009` (children `MINIMAP = 0x0224_0016`, `ORBS = 0x0224_0019`, `COMPASSCLICK = 0x0224_0018`) | outside viewport |
| chat | `CHAT_CONTAINER = 0x0224_000b` | outside viewport |
| side panel + stones | `SIDE = 0x0224_0011` (`SIDE_TOP = 0x0224_003f` with `STONE0..6 = 0x0224_0040..0046`, `SIDE_BOTTOM = 0x0224_002f` with `STONE7..13 = 0x0224_0030..0036`, `SIDE_PANELS = 0x0224_0050`) | outside viewport |
| HUD frame (snap-corner origin) | `OVERLAY_HUD = 0x0224_0021` [S3 l.467] | inside viewport; a container, not itself opaque |
| in-viewport HUD | `XP_DROPS = 0x0224_0022`, `HPBAR_HUD = 0x0224_001c`, `BUFF_BAR = 0x0224_0020`, `STAT_BOOSTS_HUD = 0x0224_001f`, `PVP_ICONS = 0x0224_001d`, `MULTIWAY_ICON = 0x0224_0025` (core list, "MULTICOMBAT_FIXED"), `PM_CONTAINER = 0x0224_0024`, `NOTIFICATIONS = 0x0224_002c`, `HELPER = 0x0224_001e`, `GRAVESTONE = 0x0224_0028`, `FLOATER = 0x0224_002b` | test `isHidden()` per element |
| open interface | `MAINMODAL = 0x0224_0029` | any modal (bank, deposit box) covers the centre |

### Resizable classic — `ToplevelOsrsStretch` (161) [S1 l.6531-6633]

The 3D view is `VIEWPORT = 0x00a1_005b` and the overlay clip is the whole canvas
[S4 l.736-739], so the HUD **does** overlap the scene.

| Role | Component | Core list name [S2] |
|---|---|---|
| chat | `CHAT_CONTAINER = 0x00a1_0060`; collapse test on `Chatbox.CHATAREA = 0x00a2_0022` | `RESIZABLE_VIEWPORT_CHATBOX_PARENT` |
| side panel + stones | `SIDE_MENU = 0x00a1_0061`; children `SIDE_TOP = 0x00a1_003a` (`STONE0..6 = 0x00a1_003b..0041`), `SIDE_BOTTOM = 0x00a1_002a` (`STONE7..13 = 0x00a1_002b..0031`), `SIDE_CONTAINER = 0x00a1_0049`, `SIDE_PANELS = 0x00a1_004b` | `RESIZABLE_VIEWPORT_INVENTORY_PARENT` |
| minimap + orbs | `MAP_CONTAINER = 0x00a1_005f`; children `MAP_MINIMAP = 0x00a1_0016`, `MINIMAP = 0x00a1_001e`, `ORBS = 0x00a1_0021`, `COMPASSCLICK = 0x00a1_001f` | `RESIZABLE_MINIMAP_STONES_WIDGET` |
| HUD frame | `HUD_CONTAINER_FRONT = 0x00a1_000f` [S3 l.464], `HUD_CONTAINER_BACK = 0x00a1_0007`, `OVERLAY_HUD = 0x00a1_0008` | snap-corner origin |
| in-viewport HUD | `XP_DROPS = 0x00a1_0009`, `HPBAR_HUD = 0x00a1_0002`, `BUFF_BAR = 0x00a1_0006`, `STAT_BOOSTS_HUD = 0x00a1_0005`, `PVP_ICONS = 0x00a1_0003`, `MULTIWAY_ICON = 0x00a1_0014` (core list, "MULTICOMBAT_RESIZABLE_CLASSIC"), `PM_CONTAINER = 0x00a1_005d`, `NOTIFICATIONS = 0x00a1_000d`, `HELPER = 0x00a1_0004`, `GRAVESTONE = 0x00a1_0015`, `FLOATER = 0x00a1_0012` | |
| open interface | `MAINMODAL = 0x00a1_0010` | |

### Resizable modern — `ToplevelPreEoc` (164) [S1 l.7216-7315]

The 3D view is `VIEWPORT = 0x00a4_0058`. Core splits the side into **three** overlays, which is
the strongest hint that the pieces hide independently:

| Role | Component | Core list name [S2] |
|---|---|---|
| chat | `CHAT_CONTAINER = 0x00a4_005d`; collapse test on `Chatbox.CHATAREA = 0x00a2_0022` | `RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT` |
| bottom stone bar | `SIDE_STATIC_LAYER = 0x00a4_005e` (`SIDE_STATIC = 0x00a4_0025`, `STONE7..9 = 0x00a4_0026..0028`, `STONE10 = 0x00a4_0022`, `STONE11..13 = 0x00a4_0029..002b`) | `RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS1` |
| movable stone bar | `SIDE_MOVABLE_LAYER = 0x00a4_005f` (`SIDE_MOVABLE = 0x00a4_0033`, `STONE0..6 = 0x00a4_0034..003a`) | `RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS2` |
| side panel | `SIDE_CONTAINER = 0x00a4_0060` (`SIDE_PANELS = 0x00a4_0048`) | `RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_PARENT` |
| minimap + orbs | `MAP_CONTAINER = 0x00a4_005c`; children `MAP_MINIMAP = 0x00a4_0016`, `MINIMAP = 0x00a4_001e`, `ORBS = 0x00a4_0021`, `COMPASSCLICK = 0x00a4_001f` | `RESIZABLE_MINIMAP_WIDGET` |
| HUD frame | `HUD_CONTAINER_FRONT = 0x00a4_000f` [S3 l.460], `HUD_CONTAINER_BACK = 0x00a4_0007`, `OVERLAY_HUD = 0x00a4_0008` | snap-corner origin |
| in-viewport HUD | `XP_DROPS = 0x00a4_0009`, `HPBAR_HUD = 0x00a4_0002`, `BUFF_BAR = 0x00a4_0006`, `STAT_BOOSTS_HUD = 0x00a4_0005`, `PVP_ICONS = 0x00a4_0003`, `MULTIWAY_ICON = 0x00a4_0014` (core list, "MULTICOMBAT_RESIZABLE_MODERN"), `PM_CONTAINER = 0x00a4_005a`, `NOTIFICATIONS = 0x00a4_000d`, `HELPER = 0x00a4_0004`, `GRAVESTONE = 0x00a4_0015`, `FLOATER = 0x00a4_0012` | |
| open interface | `MAINMODAL = 0x00a4_0010` | |

### What RuneLite's own overlays do *not* avoid

Nothing else. `OverlayRenderer` draws `ABOVE_SCENE` and `UNDER_WIDGETS` overlays before the
native interface paints over them [S4 l.250-272 plus the seed facts on `Hooks`], and the only
geometry it consults is the viewport clip in fixed mode [S4 l.727-740]. RuneLite overlays
themselves never intercept a click on the game (the renderer only tracks the mouse for
dragging, menu building and hover, [S4 l.381-383, l.421-428]), so the Status Panel and other
plugins' overlays do not cover a clickbox in the sense that matters for a Sightline
[primary for the renderer; inference for "never"].

The "core list" is the HUD RuneLite's snap corners and overlay drag collision avoid; the extra
rows (xp drops, buff bar, hp bar, multiway icon, modal) are listed because they draw inside the
viewport and are real widgets with bounds, not because core avoids them. Whether each blocks a
click when it overlaps a model is **[inference]** until seen in game; the safe default is to
count any non-hidden one as cover.

## 3. How core enumerates them [primary]

- `WidgetOverlays.createOverlays()` [S2 l.54-66]: one `WidgetOverlay` per HUD component, layer
  `UNDER_WIDGETS`, priority `PRIORITY_HIGHEST`, snappable. `render()` reads
  `client.getWidget(componentId)`, returns `null` when the widget is `null` or `isHidden()`
  (bounds set to `0,0,0,0`), otherwise copies `widget.getBounds()` into the overlay bounds
  [S2 l.171-206, l.208-230]. Off the client thread it returns the cached bounds [S2 l.232-244].
- `OverlayManager.convertOriginToAbsolute()` [S3 l.496-560]: snap-corner origins are the HUD
  container bounds (`HUD_CONTAINER_FRONT` resizable, `OVERLAY_HUD` fixed) and the chat container
  bounds; `CHATBOX_TOP` adds the `Chatbox.CHATAREA` height when that part is self-hidden, because
  "this part gets hidden" while the container does not [S3 l.486-489, l.550-556].
- `OverlayRenderer.positionSnapcorners()` runs on `BeforeRender`, so bounds are re-read every
  frame on the client thread [S4 l.230-238, l.824].
- The component names in `WidgetOverlays` (`RESIZABLE_VIEWPORT_CHATBOX_PARENT` etc.) are the
  old `WidgetInfo` names, retained so users' saved overlay positions keep resolving [S2].

## 4. Reliability of `getBounds()` / `isHidden()`

- `Widget.getBounds()` "gets the area where the widget is drawn on the canvas"; `contains(Point)`
  tests a canvas point against it; `getCanvasLocation()` "accounts for the relative coordinates
  and bounds of any parent widgets" [primary, S5 l.418-426, l.465-470, l.500-507].
- `isHidden()` checks the widget *and its parents* and "must be ran on the client thread";
  `isSelfHidden()` ignores parents [primary, S5 l.389-404]. Overlay `render()` runs on the client
  thread, so a Sightline computed in an overlay or on `BeforeRender` is safe; anything computed
  on the EDT or a scheduled executor must use `clientThread.invoke()` first (hub rule, threading).
- **Collapsed chat** (clicking the active tab): `Chatbox.CHATAREA = 0x00a2_0022` becomes
  self-hidden; `CHAT_CONTAINER` keeps its full bounds. Core corrects for this by hand
  [primary, S3 l.550-556]. Rule: cover = `CHAT_CONTAINER` bounds minus the `CHATAREA` bounds when
  `CHATAREA.isSelfHidden()`; what remains is the tab row. Confidence: **high**.
- **Transparent chat** (in-game "Transparent chatbox"): opacity is a separate property
  (`getOpacity()`, 0 opaque .. 255 transparent) and does not change bounds [primary, S5 l.223-228].
  Whether a transparent chat area passes clicks to the scene is **[inference: no]**; RuneLite's
  hub rules forbid click-through inventory backgrounds precisely because the game does not offer
  it, which suggests the transparent chat still swallows the click. Verify in game.
- **Collapsed side panel** (no tab selected): core keeps the modern layout's `SIDE_CONTAINER`
  separate from the two stone layers, and the classic layout's `SIDE_MENU` as one parent
  [primary, S2 l.62-66]. The hidden node when the panel closes is not stated anywhere in
  core; **[inference]**: `SIDE_CONTAINER` (modern) / `SIDE_PANELS` or `SIDE_CONTAINER` (classic)
  goes hidden while the stones stay. Rule: test the panel container and the stone bars as
  separate rectangles, never the aggregate parent. Confidence: **medium**; one in-game check
  with each layout settles it.
- **Hidden minimap**: `MAP_CONTAINER.isHidden()` is the test; the hub forbids *unhiding* it, not
  reading it [primary, hub rules]. Confidence: **high**.
- **Client-thread and null caveats**: `client.getWidget()` can return `null` while the layout is
  switching (`ResizeableChanged`, and the first frames after `LOGGED_IN`); core treats null as
  empty bounds [primary, S2 l.210-214]. Do the same.

## 5. Reading another plugin's config — verdict

- `ConfigManager.getConfiguration(String groupName, String key)` is a public method with no
  ownership check; it resolves to the current profile's `<group>.<key>` [primary, S9 l.806-815].
  Core itself reads foreign groups by string (`Updater` reads `"runelite"`) [primary, code search
  S14].
- Hub rules: the README's review criteria are "isn't malicious, doesn't break Jagex's rules, or
  isn't one of our previously Rejected/Rolledback features"; nothing about config groups
  [primary, S10 l.108-111]. `docs/RUNELITE-RULES.md` in this repo says the same.
- Precedent: **RelicScape** (on the hub, manifest now `disabled=unmaintained`) injects
  `GpuPluginConfig` via `configManager.getConfig(GpuPluginConfig.class)` and listens on
  `GpuPluginConfig.GROUP` [primary for the manifest, S10; secondary for the code, S12]. The typed
  form is cleaner than a string key because the key name stays a compiler-checked reference.
  Counter-example: Trouble-Brewing-Rum reads GPU config by iterating `pluginManager.getPlugins()`
  and uses reflection for 117 HD; it is **not** on the hub [primary for the 404 manifest;
  secondary, S15]. Reflection would fail our checkstyle anyway.
- **Verdict**: permitted, but a poor signal. `gpu.drawDistance` says nothing when the GPU
  plugin is disabled (CPU renderer draws 25), and under 117 HD the live distance lives in the
  `hd` group. The hub-clean, renderer-agnostic read is:

  ```java
  boolean gpu = client.isGpu();                                   // Client.java l.1831
  Scene scene = client.getTopLevelWorldView().getScene();          // WorldView.java l.51; Client.getScene() is @Deprecated
  int drawDistanceTiles = gpu ? scene.getDrawDistance() : 25;     // Scene.java l.49
  ```

  Every GPU renderer writes `scene.setDrawDistance(...)` at the start of each top-level scene
  draw: core `GpuPlugin.preSceneDrawToplevel` [primary, S6 l.878-881], 117 HD `ZoneRenderer` and
  `LegacyRenderer`, RelicScape [secondary, S13, S12]. Core clamps to `0..MAX_DISTANCE (184)`
  [primary, S6 l.102, l.2154-2157]. The value is **stale after the GPU plugin is turned off**
  (nothing resets it), hence the `isGpu()` gate. `Scene.getDrawDistance()` carries no javadoc
  [primary, S7 l.49-50]. Confidence: **high**.

## 6. Draw-distance semantics

### GPU [primary]

- Config: `gpu.drawDistance`, default **50**, `@Range(max = MAX_DISTANCE)` = 184, unit is tiles
  (multiplied by `Perspective.LOCAL_TILE_SIZE` = 128 before upload) [S6 l.42-54, l.988].
- Extended scene: `expandedMapLoadingChunks`, default **3**, max 5, "extra map area to load, in
  8 tile chunks" [S6 config l.66-79]; `client.setExpandedMapLoading()` [S6 l.374].
  `Constants.SCENE_SIZE = 104`, `EXTENDED_SCENE_SIZE = 184` = 104 + 2·5·8 [S7b l.73-79]. The
  loaded scene is therefore 104 + 16·chunks tiles wide (152 by default) and a target outside it
  does not exist client-side at all; `SceneTracker` never sees it.
- The fog box in `vert.glsl` is `cameraX ± drawDistance` and `cameraZ ± drawDistance`, each
  clamped to the loaded scene edge `(-chunks·8 + 1) … (104 + chunks·8 - 1)` tiles [S11 l.38-39,
  l.114-117]. Fog only colours; it does not remove geometry.
- Measured from the **camera**, as world-unit distances along each axis (a square, Chebyshev
  metric), not a radius from the player.

### CPU [primary, historical]

`RSSceneMixin.rl$drawScene` (tag 1.5.0) [S8]:

```java
private static final int DEFAULT_DISTANCE = 25;                          // l.51
final int distance = isGpu ? rl$drawDistance : DEFAULT_DISTANCE;         // l.91
int screenCenterX = cameraX / Perspective.LOCAL_TILE_SIZE;               // l.142  (camera, not player)
int screenCenterZ = cameraZ / Perspective.LOCAL_TILE_SIZE;
int minTileX = screenCenterX - distance;  /* clamped to 0 */             // l.150-158
int maxTileX = screenCenterX + distance;  /* clamped to maxX */          // l.161-165
for (x = minTileX; x < maxTileX; ++x) for (y = minTileZ; y < maxTileZ; ++y)   // l.183-186
    draw = tile.getPhysicalLevel() <= plane
        && (renderArea[x - screenCenterX + MAX_DISTANCE][y - screenCenterZ + MAX_DISTANCE]
            || tileHeights[z][x][y] - cameraY >= 2000
            || isGpu);                                                   // l.191-196
```

- The CPU renderer draws a **25-tile square around the camera tile** (`-25 ≤ dx ≤ 24`, the upper
  bound is exclusive), then culls with `client.getVisibilityMaps()[(pitch-128)/32][yaw/64]`, a
  pitch/yaw lookup that approximates the view frustum, with an exception for tiles 2000 units
  above the camera [S8 l.135, l.191-196]. On GPU every tile in the square is drawn (`|| isGpu`).
- `PITCH_LOWER_LIMIT = 128`, `PITCH_UPPER_LIMIT = 383` are the CPU visibility-map pitch bounds
  (in the 2048-unit JAU of 2018; the API now reports JAU14) [S8 l.54-55]. This is where the
  "128..383" figure in the seed notes comes from.
- The mixin's `cameraZ` is the API's `getCameraY()` (north axis) and its `cameraY` is height.
- This source is seven years old. The master GPU plugin still uploads the same camera-centred
  square [S11], and `Scene.setDrawDistance` still exists with the same contract [S7], so the
  loop shape is corroborated; the CPU constant 25 is not re-verifiable from public code today.
  Confidence in "25, camera-centred": **medium-high**.

## 7. Proposed Sightline rule

Inputs per Hop: the target object (from `SceneTracker`), the Landing Tile of the previous Hop,
and the Camera Pose. Evaluate on the client thread, once per tick for the Camera Check.

1. **Not drawn** (Hidden, cause "out of draw distance"):
   `D = isGpu ? scene.getDrawDistance() : 25`; camera tile
   `(cx, cy) = (client.getCameraX() >> 7, client.getCameraY() >> 7)` in scene-local units;
   the target's tile span `getSceneMinLocation()..getSceneMaxLocation()`. Hidden if no tile of
   the span satisfies `cx - D ≤ x < cx + D` and `cy - D ≤ y < cy + D`. Also Hidden if the target
   is not in the loaded scene at all (no `SceneTracker` entry). **[inference]** for using the
   span rather than one anchor tile: which tile the client draws a multi-tile object from is not
   in public code; the span is the conservative choice.
2. **Off screen** (Hidden, cause "outside viewport"): `Perspective.getClickbox()` returns null,
   or its bounds do not intersect `(getViewportXOffset, getViewportYOffset, getViewportWidth,
   getViewportHeight)` [primary for the rectangle, S4 l.731-734; the seed facts note that
   `localToCanvas` never clips].
3. **Covered**: subtract, for the active layout, every non-hidden rectangle from section 2
   (chat with the `CHATAREA` correction, stone bars, side panel, minimap container, in-viewport
   HUD elements, `MAINMODAL`). Clear if the clickbox area is untouched; Obstructed if partly
   covered; Hidden (cause "covered by <widget name>") if fully covered.
4. **Camera position at the Landing Tile** [inference]: the camera tile is not the player tile;
   at outer zoom it sits several tiles behind the player. Under a fixed Camera Pose the offset
   (camera tile − player tile) is constant, so measure it once during the Camera Check and add it
   to each Landing Tile to predict `(cx, cy)` there. Terrain can nudge the real camera; treat a
   verdict within one tile of the `D` edge as Obstructed rather than Clear.

Confidence per claim:

| Claim | Grade | Confidence |
|---|---|---|
| Layout ids and detection | primary | high |
| Per-layout core HUD list (chat, side, minimap) with ids | primary | high |
| In-viewport extras block clicks when overlapping | inference | medium |
| Fixed mode: nothing outside the viewport rect matters | primary | high |
| `getBounds()`/`isHidden()` usable per frame on the client thread | primary | high |
| Collapsed chat = `CHATAREA` self-hidden | primary | high |
| Collapsed side panel = panel container hidden, stones stay | inference | medium |
| Transparent chat still swallows clicks | inference | low-medium |
| Config read is hub-legal | primary + precedent | high |
| `Scene.getDrawDistance()` is the right source under GPU / 117 HD | primary + secondary | high |
| Draw square is camera-centred, GPU `D` in tiles | primary | high |
| CPU `D` = 25 | primary, historical | medium-high |
| Multi-tile objects: any tile in the square suffices | inference | medium |

## Open items for in-game verification

1. Each layout: close the side panel and read `isHidden()` on `SIDE_CONTAINER`, `SIDE_PANELS`,
   `SIDE_MENU` / `SIDE_STATIC_LAYER` / `SIDE_MOVABLE_LAYER` (a one-off debug line in the Camera
   Check is enough).
2. Transparent chatbox: does a click on the transparent chat area reach a Runestone behind it?
3. CPU renderer: stand with a Dense Runestone at 24, 25 and 26 tiles from the *camera* tile and
   confirm the 25-tile edge (the Camera Check can draw the predicted edge).
4. Whether a Runestone whose span straddles the draw edge is clickable (item 1 of section 7).

## Sources

1. [primary] `runelite-api/src/main/java/net/runelite/api/gameval/InterfaceID.java` @ ac79ed8 — top-level ids l.154-171, l.554; `ToplevelOsrsStretch` l.6531-6633; `Chatbox` from l.6634 (`CHATAREA` l.6670); `Toplevel` l.19729-19826; `ToplevelPreEoc` l.7216-7315.
2. [primary] `runelite-client/src/main/java/net/runelite/client/ui/overlay/WidgetOverlay.java` @ ac79ed8 — `createOverlays()` l.54-66 (HUD rows l.57-66), `render()` l.171-206, `getParentBounds()` l.208-244.
3. [primary] `runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayManager.java` @ ac79ed8 — layer registration l.330-356, `getHudContainer()`/`getChatContainer()`/`getChatbox()` l.454-489, `convertOriginToAbsolute()` l.496-560.
4. [primary] `runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayRenderer.java` @ ac79ed8 — `onBeforeRender` l.230-238, render entry points l.250-272, mouse handling l.381-383 and l.421-428, `clipBounds()` l.727-740, `positionSnapcorners()` l.824.
5. [primary] `runelite-api/src/main/java/net/runelite/api/widgets/Widget.java` @ ac79ed8 — `getOpacity()` l.223-228, `isHidden()` l.389-396, `isSelfHidden()` l.398-404, `getCanvasLocation()` l.418-426, `getBounds()` l.465-470, `contains()` l.500-507.
6. [primary] `runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java` @ ac79ed8 — `MAX_DISTANCE = 184` l.102, `setExpandedMapLoading` l.374, `preSceneDrawToplevel` l.878-881, uniforms l.982-989, `getDrawDistance()` l.2154-2157; and `GpuPluginConfig.java` — `drawDistance` l.42-54, `expandedMapLoadingChunks` l.66-79.
7. [primary] `runelite-api/src/main/java/net/runelite/api/Scene.java` @ ac79ed8 — `getDrawDistance()`/`setDrawDistance()` l.49-50; `WorldView.java` l.48-51 `getScene()`; `Client.java` l.1831 `isGpu()`, l.1834-1835 `set/getExpandedMapLoading`, l.2171-2175 deprecated `getScene()`. 7b: `Constants.java` l.70-79 (`SCENE_SIZE`, `EXTENDED_SCENE_SIZE`).
8. [primary, historical] `runelite-mixins/src/main/java/net/runelite/mixins/RSSceneMixin.java` @ tag `runelite-parent-1.5.0` (tag object `4930206c…`, 2018-11-16) — constants l.51-55, `rl$drawScene` l.74-215, `get/setDrawDistance` l.658-670.
9. [primary] `runelite-client/src/main/java/net/runelite/client/config/ConfigManager.java` @ ac79ed8 — `getConfiguration` overloads l.806-840.
10. [primary] `runelite/plugin-hub` @ 4ca20dd — `README.md` "Reviewing" l.108-111, "Third party dependencies" l.117-123; manifest `plugins/relicscape` (`repository=https://github.com/IdylRS/RelicScape.git`, `disabled=unmaintained`); `plugins/trouble-brewing-rum` and `plugins/visibility-enhancer` return 404.
11. [primary] `runelite-client/src/main/resources/net/runelite/client/plugins/gpu/vert.glsl` @ ac79ed8 — fog edge defines l.38-39, uniforms l.61-62, fog box l.114-117.
12. [secondary] `IdylRS/RelicScape` `src/main/java/com/relicscape/gpu/RelicScapeGpuPlugin.java` — `GpuPluginConfig` injected via `configManager.getConfig(GpuPluginConfig.class)` (l.593-595), `GpuPluginConfig.GROUP` listener (l.601), `scene.setDrawDistance` (l.965).
13. [secondary] GitHub code search `scene.setDrawDistance` — `117HD/RLHD` `ZoneRenderer.java` and `LegacyRenderer.java` call `scene.setDrawDistance(...)`.
14. [primary] GitHub code search `configManager.getConfiguration("` in `runelite/runelite` — `Updater.java` reads group `"runelite"`; others read their own group.
15. [secondary] `Yiaro222/Trouble-Brewing-Rum` `src/main/java/com/RumRunning/Utils.java` l.199-230 — iterates `pluginManager.getPlugins()`, casts the GPU plugin's config proxy, uses reflection for 117 HD; not on the hub.
16. [primary] Seed facts for this ticket (`Perspective`, `Client`, `Hooks`, `OverlayLayer` pointers) gathered 2026-09-06 against the same master commit; not repeated here.
