# Camera model: world position from Camera Pose — research (#19)

Researched 2026-09-06 against the RuneLite source (`runelite/runelite` master at commit
`ac79ed8bd8926bec7bf172aa291574b4d944b0e7`, 2026-09-03), the RuneLite commit history, the OSRS
Wiki, a public deobfuscated OSRS client (open-osrs mirror, June 2022) and a public cs2 script dump
(RuneStar, September 2021).
Evidence grades used throughout: **[primary]** = RuneLite source/commits or wiki read directly;
**[secondary]** = deobfuscated client, cs2 dump or mixin code from a third-party mirror (dated
2021-2022, before the JAU14 change); **[inference]** = my own reasoning or arithmetic, not observed
in game.

## Question (verbatim)

> How does the client derive the camera world position (`Client.getCameraX/Y/Z`) from the focal
> point (player local position), pitch, yaw (JAU14) and the zoom varc
> (`VarClientID.CAMERA_ZOOM_BIG` = 74 / `CAMERA_ZOOM_SMALL` = 73)? What is `getScale()` relative
> to that zoom, and what is `get3dZoom()`? What are the pitch and yaw ranges with and without the
> Camera plugin pitch relaxer, and how does the camera lift when the player is on high ground?
> Deliver the formula and constants needed for a pure-Java projection engine that reproduces
> `Perspective.localToCanvas` from a Landing Tile and a Camera Pose alone, and list exactly which
> quantities must be measured in game because no primary source states them.

## TL;DR

- **The camera orbits a focal point on a fixed arm.** Each client cycle the client puts the
  camera at `focal - R(yaw) R(pitch) (0, 0, D)`: `D = (3·pitch + 600) · (1 + t/4)` local units,
  where `pitch` is in JAU (2048/turn), `t = clamp(viewportHeight − 334, 0, 100) / 100`, and the
  focal point is the player's eased local position at `tileHeight − followHeight`. The zoom varc
  does **not** move the camera; it only changes `followHeight` (25 + 25·zoom/256) and the
  projection scale [secondary, S11 §3, S14; confidence **medium-high** for the 2022 client].
- **`getScale()` is the projection focal length, and it is exponential in the varc.**
  `scale = viewportHeight · 2^(7 + zoom/256) / 334`, blended between varc 73 (fixed) and varc 74
  (resizable) over viewport heights 334..434. Varc 512 in a 334 px viewport gives the classic
  scale 512 [secondary S11 §2, S14; **high**, three independent code paths agree]. `get3dZoom()`
  is `Rasterizer3D_zoom`, a rasteriser global that only equals the scale *during* the scene draw
  and is otherwise 512; never use it for projection [secondary S12; **high**].
- **Projection is exactly `Perspective.localToCanvasCpu`** (verbatim in §4): yaw then pitch
  rotation, near plane `z1 >= 50`, `centre + x1·scale/z1`. Confirmed identical to the client's own
  `worldToScreen` and to the GPU plugin's matrix [primary S1, S3; **high**].
- **Pitch/yaw are JAU14 since RuneLite "api: update 239" (2026-06-22)** [primary S7]. Every
  angle constant below comes from the 2022 client in JAU2048 and must be multiplied by 8, which
  is exactly the kind of thing that needs measuring (§7).
- **Pitch range** without relaxer: target clamped to 128..383 JAU2048 (22.5°..67.3°); the relaxer
  lets the *target* reach 512 (90°, straight down) [secondary S13; **high** for 2022]. Yaw is a
  free 0..2047 [secondary S11].
- **High ground:** the camera does not "lift"; the client raises the *pitch floor* when terrain
  within 4 tiles of the focal tile is higher than the focal tile: `floor = clamp(0.75·rise, 128,
  383)`, smoothed (1/24 per cycle up, 1/80 down). `getCameraPitch()` already includes it
  [secondary S11 §3.4; **medium-high**]. Vertical position follows the terrain only through the
  focal height (bilinear tile height under the player).
- **A Camera Pose alone is not enough.** The world position also depends on the viewport size
  and on the tile heights at and around the Landing Tile (§5). The engine needs those as inputs.

## 1. Coordinate conventions

| Symbol | RuneLite name | Client field (deob) | Meaning |
|---|---|---|---|
| `x` | `LocalPoint.getX()`, `Client.getCameraX()` | `cameraX` | east, local units (128 per tile) |
| `y` | `LocalPoint.getY()`, `Client.getCameraY()` | `cameraZ` | north |
| `z` | height, `Client.getCameraZ()` | `cameraY` | **negative is up** (`getTileHeight` returns ≤ 0 on high ground) |

RuneLite deliberately swaps the client's Y/Z: `RSClient` maps `getCameraY()` to the client's
`cameraZ` and `getCameraZ()` to `cameraY`, each annotated "This is correct!" [secondary S12].
`Perspective.localToCanvasCpu` subtracts `getCameraX/Y/Z` from local `x`, `y`, `z` in that order
[primary S1 l.219-221], and the client's own `worldToScreen` subtracts `cameraX`, `cameraY`
(height), `cameraZ` (north) from the same three inputs [secondary S11 §5]. Scene bounds:
`SCENE_SIZE = 104`, `EXTENDED_SCENE_SIZE = 184`, so the extended scene spans
`-40·128 .. 144·128` local units on each axis [primary S9, S1 l.216-218].

Angles: `Perspective.UNIT = 2π/2048` (JAU) and `UNIT14 = 2π/16384` (JAU14); `SINE14/COSINE14`
are `sin·65536` as ints, `SINEF14/COSINEF14` floats [primary S1 l.55-99]. Yaw 0 = camera south of
the player looking north; yaw grows counter-clockwise seen from above (yaw 512 JAU puts the camera
east, facing west) [inference from §3 with the sine sign, and the compass convention].

## 2. Zoom: varcs → `getScale()`

### 2.1 What the varc drives

The zoom slider writes the pair `(varc 73, varc 74)` = `(CAMERA_ZOOM_SMALL, CAMERA_ZOOM_BIG)`
[primary S8 l.80-81]. The cs2 proc `camera_do_zoom(small, big)` (script 42,
`ScriptID.CAMERA_DO_ZOOM`) does, in order [secondary S14]:

```
small = clamp(small, 128, 896); big = clamp(big, 128, 896)     // 2021 defaults; today varcs 1338-1341
viewport_setfov(small, big)
(w, h) = viewport_geteffectivesize
t100  = clamp(h - 334, 0, 100)
zoomEff = small + (big - small) * t100 / 100
cam_setfollowheight(25 + 25 * zoomEff / 256)                    // integer division
varc73, varc74 = small, big
```

`viewport_setfov(a, b)` (opcode 6200) stores `fovSmall = 2^(7 + a/256)` and
`fovBig = 2^(7 + b/256)` as shorts, defaulting to 256 and 205 [secondary S11 §2, `class20.method255`
= `(int) Math.pow(2, 7 + x/256f)`; opcode names S15]. The layout script `toplevel_resize` calls
`camera_do_zoom(varc73, varc74)` on every resize when both are in 128..896, else `(512, 512)`; in
the cutscene/fixed modes (`varbit 4606 != 0`) it calls `viewport_setfov` with constants and
`cam_setfollowheight(50)` [secondary S14].

Therefore, for a legal varc `v`:

| varc `v` | `2^(7+v/256)` | comment |
|---|---|---|
| −272 | 60 | RuneLite Camera plugin "outer limit" −400 below the 2018 default of 128 [primary S5 l.121] |
| 128 | 181 | default most-zoomed-out (2021 script) |
| 512 | 512 | default; the classic `<< 9` focal length |
| 896 | 1448 | default most-zoomed-in (2021 script) |
| 1400 | 5701 | RuneLite `INNER_ZOOM_LIMIT` [primary S4] |

Today the limits are read from varcs 1338-1341 (`CAMERA_ZOOM_*_MIN/MAX`) and re-applied by
script 605 (`CAMERA_SET_ZOOM_LIMITS`); the Camera plugin overwrites them with
`default − outerLimit` and `1400` [primary S4 `applyConfigs`, S8 l.1345-1348]. RuneLite's own
slider script converts between varc and slider position through the `zoomLinToExp`/`zoomExpToLin`
callbacks with exponent 2, which only affects where the slider sits, not the varc semantics
[primary S4 l.246-265, S6].

### 2.2 `getScale()`

`getScale()` is `@Import("viewportZoom")` [secondary S12 l.380-382]; the GPU plugin multiplies
its projection matrix by `Mat4.scale(client.getScale(), client.getScale(), 1)` [primary S3
l.1004]; `Perspective` divides by `z1` and multiplies by it [primary S1 l.240-243]. The client
computes it in `setViewportShape(x, y, w, h)` every frame [secondary S11 §2]:

```
t100 = clamp(h - 334, 0, 100)
fov  = t100 <= 0 ? fovSmall : t100 >= 100 ? fovBig : fovSmall + (fovBig - fovSmall) * t100 / 100
// viewport_clampfov letterboxing: no-op in normal play (clamps are 1..32767 after clampfov(0,0,0,0))
viewportZoom = h * fov / 334                                   // integer division
```

So in fixed mode (`h = 334`) `getScale() = 2^(7 + varc73/256)`; in resizable with `h >= 434`
`getScale() = h · 2^(7 + varc74/256) / 334`; between 334 and 434 px both varcs blend. This is
why the same zoom looks the same at any window height: scale grows linearly with `h`. The
clamp/letterbox path (`field752..755`) only runs in the cutscene modes [secondary S11 §2, S14].

### 2.3 `get3dZoom()`

`get3dZoom()` is `@Import("Rasterizer3D_zoom")` [secondary S12 l.765-767]. That static is
initialised to 512, set to `viewportZoom` immediately before `scene.draw(...)` and restored
straight after [secondary S11 §4, `drawEntities` L4593-4596]; RuneLite's item-sprite code
temporarily overrides it with a `scale` argument [secondary S13 `RSClientMixin.createItemSprite`].
It is the rasteriser's current focal length, not the camera's zoom. **Use `getScale()`.**

## 3. Camera placement (normal play)

Everything in this section is from the June 2022 client [secondary S11 §3] and the 2021 scripts
[secondary S14], with angles in JAU2048; see §7 for what the JAU14 change may have moved.

### 3.1 Focal point

Per client cycle (`doCycle`, 20 ms) when `oculusOrbState == 0` (`Client.getCameraMode() == 0`):

```
P = localPlayer (x, y)                        // local units
if |F - P| > 500 on either axis: F = P        // snap after a teleport
F += (P - F) / 16                             // per axis, integer division toward zero
focalHeight = getTileHeight(P.x, P.y, plane) - camFollowHeight
```

`F` is what the API exposes as `getCameraFocalPointX/Y` (the deob's `oculusOrbFocalPointX/Y`)
[primary S2 l.1734-1739 "Typically this is the player position"]. Consequences:

- The focal point lags a moving player and, because of integer division, **can rest up to 15
  local units (about 1/8 tile) off the player's position** [inference from `/ 16`]. The API now
  returns floats [primary S2, S7], so this residual may have gone; measure (§7 item 6).
- The height uses the *player's* exact position, bilinearly interpolated inside the tile
  (`SceneTilePaint.getTileHeight`, the same algorithm as `Perspective.getTileHeight` including
  the bridge-plane bump [primary S1 l.628-700]).
- `camFollowHeight` defaults to 50 and is set by `cam_setfollowheight` (opcode 5530) to
  `25 + 25·zoomEff/256` on every zoom change [secondary S11 §3.1, S14]: 37 at zoom 128, 75 at 512,
  112 at 896. This is the "camera height now moves up slightly as you zoom in to keep the
  character in the correct position" of the 20 August 2015 update [primary S10].

### 3.2 Pitch and yaw

```
pitch = camAngleX            // input target; getCameraPitchTarget()
pitch = max(pitch, terrainFloor / 256)          // §3.4
pitch = max(pitch, shake[4] + 128) if camera-shake effect 4 is active
yaw   = camAngleY & 2047
```

Input clamps the target to `128 <= camAngleX <= 383` [secondary S11 §3.2 L3555-3556] and the
`CAM_FORCEANGLE` cs2 op sets it directly when the camera is not locked [secondary S11 §3.2].
`Scene.draw` clamps its copy again to 128..383 to index the visibility map [secondary S11 §6].
After `KeyHandler.method301` runs, `cameraPitch == pitch` and `cameraYaw == yaw`, so
`getCameraPitch()` **already includes the terrain floor**; `getCameraPitchTarget()` does not
[primary S2 l.1387-1395 for the target/actual distinction; secondary S11 §3.3].

**Relaxer.** `setCameraPitchRelaxerEnabled(true)` is a RuneLite mixin, not a client feature: field
hooks on `camAngleX` and `cameraPitch` notice the vanilla clamp snapping the value back to 383 and
re-apply the previous value capped at `NEW_PITCH_MAX = 512`; `RSSceneMixin` still clamps its copy
to 383 for the visibility map but feeds the real pitch to the trig tables [secondary S13
`CameraMixin`, `RSSceneMixin` l.65-190]. So: without relaxer 128..383, with relaxer 128..512
(JAU2048). The Camera plugin toggles it from `relaxCameraPitch` and resets it on shutdown
[primary S4 l.156, 197].

### 3.3 Position

`drawEntities` calls `method301(F.x, focalHeight, F.y, pitch, yaw, 3·pitch + 600, viewportHeight)`
[secondary S11 §3.3 L4525-4529; `GZipDecompressor.method8314(p) = p*3 + 600`], which does:

```
t100 = clamp(viewportHeight - 334, 0, 100)
zf   = zoomHeight + (zoomWidth - zoomHeight) * t100 / 100      // 256 + 64*t100/100; never changed by any script
D    = (3*pitch + 600) * zf / 256
a = (2048 - pitch) & 2047;  b = (2048 - yaw) & 2047             // rotate (0,0,D)
dy = (-SINE[a] * D) >> 16            =  D*sin(pitch)            // >= 0
dz = ( COSINE[a] * D) >> 16          =  D*cos(pitch)
dx = ( dz * SINE[b]) >> 16           = -D*cos(pitch)*sin(yaw)
dz = ( dz * COSINE[b]) >> 16         =  D*cos(pitch)*cos(yaw)
cameraX      = F.x - dx  = F.x + D*cos(pitch)*sin(yaw)
cameraHeight = focalHeight - dy                                  // more negative = higher
cameraNorth  = F.y - dz  = F.y - D*cos(pitch)*cos(yaw)
```

`zoomHeight = 256`, `zoomWidth = 320` are the `viewport_setzoom` (6201) defaults and no script in
the 2021 dump calls that op [secondary S11 §2, S14 search]; so `D` grows by up to 25 % as the
viewport grows from 334 to 434 px and is otherwise a pure function of pitch: 984 at pitch 128,
1749 at 383, 2136 at 512 (relaxed). **The zoom varc never enters `D`** [inference from the code
paths; confidence medium-high].

Worked check (integer tables, JAU2048), focal `(6464, −75, 6464)`, i.e. tile (50,50) at ground 0
with follow height 75 [inference, computed]:

| pitch | yaw | vpH | `D` | `getCameraX` | `getCameraZ` (height) | `getCameraY` (north) |
|---|---|---|---|---|---|---|
| 128 | 0 | 334 | 984 | 6464 | −451 | 5555 |
| 383 | 0 | 334 | 1749 | 6464 | −1688 | 5790 |
| 256 | 512 | 503 | 1710 | 7673 | −1284 | 6464 |
| 128 | 1024 | 503 | 1230 | 6464 | −545 | 7600 |

### 3.4 High ground: the terrain pitch floor

Also per cycle, for the focal tile `(fx, fy) = F >> 7` when `3 < fx, fy < 100` [secondary S11 §3.4
L3462-3480]:

```
h0   = getTileHeight(F.x, F.y, plane)
rise = max over tiles (fx±4, fy±4) of (h0 - height[plane'][tx][ty])   // plane' = plane+1 on bridge tiles
                                                                     // positive when the neighbour is higher
target = clamp(rise * 192, 32768, 98048)                             // = 256 * clamp(0.75*rise, 128, 383)
floor += (target - floor) / 24  if target > floor, else / 80         // smoothed
pitch  = max(camAngleX, floor / 256)
```

So standing beside a cliff that is 200 units (about 1.5 tiles' worth of height) higher than your
tile forces pitch ≥ 150; 340 units or more forces the maximum 383. The camera does not otherwise
react to slopes: `cameraHeight` follows `focalHeight`, which follows the ground under the player.
The smoothing means the pitch reported a few frames after a Hop is not yet the steady-state pitch
[inference].

### 3.5 Modes where this model does not hold

Cutscene lock (`isCameraLocked`, server packets that set a camera position and look-at target
with interpolation), the oculus orb / free camera (`oculusOrbState`, `getCameraMode() == 1`,
where WASD moves the focal point and `focalHeight` is clamped ≤ 0), camera-shake effects, and the
`varbit 4606` fixed-FOV interface modes [secondary S11 §3.2, §3.5, S14]. An engine should refuse
to predict when `getCameraMode() != 0`.

## 4. Projection: `localToCanvasCpu` verbatim, and its two twins

`Perspective.localToCanvasCpu(client, x, y, z)` [primary S1 l.214-250]:

```
if x, y within [-40*128, 144*128]:
    x -= cameraX; y -= cameraY; z -= cameraZ
    pitchSin = SINE14[pitch]; pitchCos = COSINE14[pitch]; yawSin = SINE14[yaw]; yawCos = COSINE14[yaw]
    x1 = x*yawCos + y*yawSin >> 16
    y1 = y*yawCos - x*yawSin >> 16
    y2 = z*pitchCos - y1*pitchSin >> 16
    z1 = y1*pitchCos + z*pitchSin >> 16
    if z1 >= 50:
        scale = getScale()
        return (viewportXOffset + viewportWidth/2 + x1*scale/z1,
                viewportYOffset + viewportHeight/2 + y2*scale/z1)     // int division, toward zero
return null
```

- The client's own `worldToScreen` is line-for-line the same with `SINE[2048]`, `viewportZoom`
  and the same `>= 50` test, writing −1 instead of null [secondary S11 §5]. So RuneLite's copy is
  faithful and the near plane `z1 >= 50` is the client's [primary+secondary; **high**].
- The GPU variant uses `getCameraFpX/Y/Z`, `getCameraFpPitch/Yaw` as **radians** (`Math.sin` on
  them) in floats, and `Math.round` at the end [primary S1 l.252-286]. The GPU plugin's matrix
  `scale(S,S,1) · projection(w, h, 50) · rotateX(pitch) · rotateY(yaw) · translate(−camera)`
  reduces to the same `S·x1/z1` in pixels: `projection` puts `2/w`, `−2/h` on the diagonal and
  the eye-space `z` into clip `w` [primary S3 l.1003-1009, Mat4 l.99-108]. Note the `−2/h`: canvas
  y grows downwards, and `y2` is positive for points below the camera's line of sight because
  height is negative-up [inference].
- **Only two null conditions**: outside the extended scene, or `z1 < 50`. Nothing tests the
  viewport rectangle; a point can be returned far outside it. Test against
  `Rectangle(viewportXOffset, viewportYOffset, viewportWidth, viewportHeight)` yourself
  [primary S1; **high**].
- Which branch is live is `client.isGpu()` [primary S1 l.180]; the two differ only by rounding.

Viewport centre: `viewportWidth/2`, `viewportHeight/2` integer halves plus the offsets; the
client's `Rasterizer3D_clipMidX/Y` (`getCenterX/Y`) are the same centres in canvas space
[secondary S12 l.788-790, S11 §4].

## 5. Engine specification: `Sightline` projection from a Landing Tile and a Camera Pose

Inputs the engine must take (with where each comes from):

| Input | Source | Note |
|---|---|---|
| Landing Tile (local x, y, plane) | Rotation data | focal point at tile centre `(tx*128+64, ty*128+64)` |
| Camera Pose: yaw14, pitch14 (target), varc73/varc74 | `getCameraYaw()`, `getCameraPitchTarget()`, `getVarcIntValue(73/74)` | Pose as defined in `CONTEXT.md`; the effective pitch is derived, not part of the Pose |
| Viewport: xOff, yOff, w, h | `getViewport*()` | changes `D`, `scale`, follow height and the centre |
| Tile heights `heights[plane][x][y]` for the 9×9 around the Landing Tile and under the target | `getTopLevelWorldView().getTileHeights()` (+ bridge flags from `getTileSettings()`) | terrain floor and focal height; must be read while that scene is loaded |
| Target: local x, y, z (= `getTileHeight − heightOffset`) | scene object / clickbox vertices | as `Perspective.localToCanvas(client, LocalPoint, plane, heightOffset)` builds it |

Pure-Java algorithm (constants from §2-§4; angles in JAU2048 until measured, see §7):

```java
// 1. scale
int t100 = clamp(h - 334, 0, 100);
double fovS = Math.pow(2, 7 + varc73 / 256.0), fovB = Math.pow(2, 7 + varc74 / 256.0);
int fov = (int)(t100 <= 0 ? fovS : t100 >= 100 ? fovB : fovS + (fovB - fovS) * t100 / 100);
int scale = h * fov / 334;
// 2. focal point (player at rest on the Landing Tile)
int zoomEff = varc73 + (varc74 - varc73) * t100 / 100;
int followHeight = 25 + 25 * zoomEff / 256;
int focalHeight = tileHeight(fx, fy, plane) - followHeight;          // bilinear, bridge bump
// 3. effective pitch
int rise = maxRiseWithin4Tiles(fx >> 7, fy >> 7, plane);              // 0 if none higher
int floor = clamp(rise * 192, 32768, 98048) / 256;                    // steady state
int pitch = Math.max(pitchTarget, floor);                             // and <= 383 or 512 (relaxer)
// 4. camera position
int D = (3 * pitch + 600) * (256 + 64 * t100 / 100) / 256;
int camX = fx + (int)(D * cos(pitch) * sin(yaw));
int camZ = focalHeight - (int)(D * sin(pitch));                        // height
int camY = fy - (int)(D * cos(pitch) * cos(yaw));                      // north
// 5. project exactly as localToCanvasCpu (§4) with camX/camY/camZ, pitch, yaw, scale, viewport
```

Use the client's integer tables and `>> 16` in step 4 if bit-exactness matters; a floating-point
version is within a local unit, i.e. far below a pixel at these distances [inference]. For the
Sightline verdict itself, project the target's clickbox vertices (`Model.getVerticesX/Y/Z`,
`Perspective.getClickbox` logic) with the same synthetic camera rather than a single point; the
API projection helpers all read the live camera, so they cannot be reused for a predicted Pose.

Two practical shortcuts, both [inference]:
- When the player *is* standing on the Landing Tile, read `getCameraX/Y/Z`, `getCameraPitch()`
  and `getScale()` directly and skip steps 1-4; this is also how §7 gets measured.
- The Pose in `CONTEXT.md` is "yaw, pitch and zoom, set once and left alone". The engine should
  store the *target* pitch (`getCameraPitchTarget()`) because the effective pitch varies per
  Landing Tile through the terrain floor.

## 6. Confidence per claim

| Claim | Grade | Confidence |
|---|---|---|
| `localToCanvasCpu` formula, near plane 50, no viewport clipping, integer rounding | primary S1 | high |
| GPU path = same maths in floats/radians with `Math.round` | primary S1, S3 | high |
| Pitch/yaw now JAU14 (`SINE14`, 16384/turn), since 2026-06-22 | primary S7 | high |
| `getScale()` = client `viewportZoom`; `get3dZoom()` = `Rasterizer3D_zoom`, only equal during the scene draw | secondary S12, S11 | high |
| `scale = h · 2^(7 + varc/256) / 334`, blended over 334..434 px | secondary S11, S14; consistent with S5 (128 default, negative outer limits) | high for 2022, medium that it is unchanged today |
| Camera arm `D = (3·pitch + 600)·(1 + t/4)`, zoom not involved | secondary S11 | medium-high (single code path, clear) |
| Follow height `25 + 25·zoomEff/256` | secondary S14 (2021 script), corroborated by S10 patch note | medium |
| Focal point eases `/16` with ≤ 15-unit residual | secondary S11 | medium (API now floats, S7) |
| Pitch limits 128..383, relaxer 512 (JAU2048) | secondary S11, S13 | high for 2022; the JAU14 equivalents are unmeasured |
| Terrain pitch floor `clamp(0.75·rise, 128, 383)`, smoothed | secondary S11 | medium-high |
| Camera-shake and cutscene overrides exist and break the model | secondary S11 | high that they exist; details not needed |

## 7. Quantities no primary source states — must be measured in game (feeds #23)

Each is a number the engine needs and that only [secondary] 2021-2022 sources or nothing gives.
For every item the measurement is: stand still on a known tile, log `getCameraX/Y/Z`,
`getCameraPitch()`, `getCameraPitchTarget()`, `getCameraYaw()`, `getScale()`, varcs 73/74/1338-1341,
`getViewport*()`, `getCameraFocalPointX/Y/Z` and the tile heights, per `BeforeRender` or
`ClientTick`, and fit the constants below.

1. **Pitch limits in JAU14** — min, max without relaxer, max with relaxer. Expected 1024 / 3064 or
   3072 / 4096 (×8 of 128 / 383 / 512), but 383 is not a round number and Jagex may have redefined
   the cap when they added the bits. Also confirm the relaxer still works after update 239 (the
   Camera plugin still calls it [primary S4]).
2. **Arm length `D` vs pitch** — fit `D = a·pitch14 + b` (expected `a = 3/8`, `b = 600`) from
   `|camera − focal|` at several pitches; confirm `D` does not change with the zoom varc.
3. **Viewport-height factor** — confirm `D` and `scale` scale as `(256 + 64·t100/100)/256` and
   `h/334` by measuring at fixed mode (334 px), 434 px and a tall resizable window; confirm the
   blend uses varc 73 below 334 and varc 74 above 434.
4. **`scale` vs varc** — verify `getScale() = h · 2^(7 + v/256) / 334` at v ∈ {min, 512, max};
   read the login-time values of varcs 1338-1341 (defaults believed 128 / 896, expanded by the
   Camera plugin to `default − outerLimit` and 1400).
5. **Follow height** — `focalHeight − tileHeight` at several zooms (expected `25 + 25·zoomEff/256`,
   i.e. 75 at 512); this cannot be read from any RuneLite API and only shows up in
   `getCameraFocalPointZ()` or in `getCameraZ() + D·sin(pitch)`.
6. **Focal-point residual** — `getCameraFocalPointX/Y − player local x/y` at rest; expected 0
   now that the API is float, up to 15 units in the 2022 integer client. If non-zero, the engine
   needs the player's approach direction, which we do not want.
7. **Terrain pitch floor** — on a tile beside higher ground (the Dark Altar approach has some),
   compare `getCameraPitch()` with `getCameraPitchTarget()`; fit the slope (expected 0.75 JAU2048
   per height unit, i.e. 6 JAU14) and the smoothing rates.
8. **Rounding path** — whether `isGpu()` is true for this player (GPU plugin on), because the
   CPU path truncates and the GPU path rounds; a one-pixel difference at clickbox edges.
9. **Tile-height input** — confirm that `getTileHeights()` for the Landing Tiles of the Rotation
   is stable across the scene rebuilds the Rotation causes (the Blood Altar research showed the
   scene base moves along the route), i.e. whether heights must be cached per Landing Tile.

Not measurable but must be *decided*: what the engine does when `getCameraMode() != 0` or a
cutscene lock is active (recommend: no Sightline verdict).

## Sources

1. **S1** [primary] `runelite-api/src/main/java/net/runelite/api/Perspective.java` at
   `ac79ed8b` — constants l.55-99, `localToCanvas` l.112-199, `localToCanvasCpu` l.214-250,
   `localToCanvasGpu` l.252-286, `getTileHeight` l.628-700.
2. **S2** [primary] `runelite-api/src/main/java/net/runelite/api/Client.java` at `ac79ed8b` —
   `getCameraPitch/Yaw` javadoc (JAU14) l.277-302, `getScale` l.358-363, `getCameraPitchTarget`
   l.1387-1395, `setCameraPitchRelaxerEnabled` l.1424-1430, `getCameraFocalPointX` l.1734-1739,
   `get3dZoom` l.1837.
3. **S3** [primary] `runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java`
   l.838-1009 (`preSceneDraw`, projection matrix) and `Mat4.java` l.60-108 at `ac79ed8b`;
   `runelite-api/.../hooks/DrawCallbacks.java` l.144-157; `IntProjection.java`.
4. **S4** [primary] `runelite-client/.../plugins/camera/CameraPlugin.java` at `ac79ed8b` —
   l.156, 197 (relaxer), 205-221 (`applyConfigs`), 225-265 (callbacks), 270 (script 605);
   `CameraConfig` constants −400 / 400 / 1400.
5. **S5** [primary] same file at tag `runelite-parent-1.6.0` — l.121
   `int outerZoomLimit = 128 - outerLimit;` (the pre-varc default outer limit).
6. **S6** [primary] `runelite-client/src/main/scripts/OptionsPanelZoomUpdater.rs2asm` (script
   1049, reads varcs 74/73 against 1338-1341 and `viewport_geteffectivesize`) and
   `ScrollWheelZoomHandler.rs2asm` (script 39, `viewport_getfov`, invokes 42) at `ac79ed8b`.
7. **S7** [primary] commit `ce5918d33a` "api: update 239", 2026-06-22 — adds `UNIT14`,
   `SINE14/COSINE14/SINEF14/COSINEF14`, rewrites `getCameraPitch/Yaw` javadoc to JAU14, makes
   `getCameraFp*` float. Earlier: `7ce6cc2716` "api: update 232" (2025-08-04) and `6023e8352f`
   "api: restore 231 trig tables" (2025-08-10) for the float trig tables.
8. **S8** [primary] `runelite-api/.../gameval/VarClientID.java` at `ac79ed8b` — l.80-82
   (`CAMERA_ZOOM_SMALL = 73`, `CAMERA_ZOOM_BIG = 74`, `CAMERA_ZOOM_MOUSE_ENABLED = 75`),
   l.1345-1348 (`CAMERA_ZOOM_SMALL_MIN/MAX = 1338/1339`, `CAMERA_ZOOM_BIG_MIN/MAX = 1340/1341`).
9. **S9** [primary] `runelite-api/.../Constants.java` — `SCENE_SIZE = 104`,
   `EXTENDED_SCENE_SIZE = 184`.
10. **S10** [primary] OSRS Wiki "Settings" (revision 15272496): Display → Graphics "Camera zoom
    distance — A slider that adjusts the camera's zoom"; update history 20 August 2015 "The camera
    height now moves up slightly as you zoom in to keep the character in the correct position on
    the screen." The wiki pages "Camera" and "Zoom" are redirects (to "Oculus orb" and
    "Settings#Display") and carry no numbers.
11. **S11** [secondary] Deobfuscated OSRS client in `open-osrs/runelite` master
    (`915fb55c`, 2022-06-28), `runescape-client/src/main/java/`:
    §1 `Client.java` l.235-252 (`viewportZoom`, `zoomHeight = 256`, `zoomWidth = 320`), l.667-674
    (`camAngleX/Y`), l.709-710 + l.1515 (`camFollowHeight = 50`), l.1694-1701 (`fov` defaults
    256/205, clamp defaults);
    §2 `class201.setViewportShape` l.114-175 (fov blend, `viewportZoom = h*fov/334`, letterbox
    clamps), `class20.method255` (`2^(7 + x/256)`), `class387` l.455-512 (`VIEWPORT_SETFOV`,
    `VIEWPORT_SETZOOM`, `VIEWPORT_CLAMPFOV`);
    §3 `Client.java` l.3869-3920 (focal easing, terrain rise, `field681` smoothing, focal height),
    l.4026-4034 (target pitch clamp 128..383), `ServerPacket.drawEntities` l.737-747 (pitch floor
    `field681/256`, shake `+128`, call to `method301` with `method8314(pitch)`),
    `GZipDecompressor.method8314` (`p*3 + 600`), `KeyHandler.method301` l.290-335 (arm rotation
    and camera assignment), `class118` l.337-364 (`CAM_FORCEANGLE`, `CAM_SETFOLLOWHEIGHT`);
    §4 `ServerPacket` l.832-835 (`Rasterizer3D_zoom = viewportZoom` around `scene.draw`);
    §5 `class220.worldToScreen` l.44-70; §6 `Scene.draw` l.1059-1100 (pitch clamp 128..383,
    visibility map). Opcode names from `runescape-api/.../rs/ScriptOpcodes.java` l.477-488.
12. **S12** [secondary] `open-osrs/runelite` `runescape-api/.../rs/api/RSClient.java` — l.43-50
    (`cameraX`, `cameraZ`→`getCameraY`, `cameraY`→`getCameraZ`), l.380-382 (`viewportZoom`→
    `getScale`), l.747 (`camAngleX`→`getCameraPitchTarget`), l.765-767 (`Rasterizer3D_zoom`→
    `get3dZoom`), l.788-790 (`Rasterizer3D_clipMidX`→`getCenterX`).
13. **S13** [secondary] `open-osrs/runelite` `runelite-mixins/.../CameraMixin.java` (constants
    128 / 383 / 512, field hooks), `RSSceneMixin.java` l.62-190 (visibility-map clamp vs real
    pitch), `RSClientMixin.java` l.1285-1295 (`get3dZoom` swap for item sprites), l.2825-2860
    (`interpolateCamera`, unlocked-FPS pitch clamp with relaxer).
14. **S14** [secondary] `RuneStar/cs2-scripts` at `7da6c1fb` (2021-09-16, revision 199):
    `[proc,camera_do_zoom]` (script 42), `[proc,settings_camera_do_zoom]` (3899),
    `[proc,toplevel_resize]` (909). Code search of that repo: `cam_setfollowheight` appears only
    in those three; `viewport_setzoom` in none.
15. **S15** [secondary] `zwyz/osrs-cache` `data/commands/6200_viewport.txt` — opcode names and
    signatures 6200-6205 (`viewport_setfov(int,int)`, `viewport_setzoom(int,int)`,
    `viewport_clampfov(int,int,int,int)`, `viewport_geteffectivesize()(int,int)`, ...).
16. **S16** [primary] Seed facts gathered earlier this session from the same RuneLite commit
    (API signatures, `getCanvasTileAreaPoly`, `getClickbox`, overlay layers) — reused, not
    re-derived here.
