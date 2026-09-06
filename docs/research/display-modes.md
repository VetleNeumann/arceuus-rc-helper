# Display modes and coordinate spaces for Sightline — research

Researched 2026-09-07 against the RuneLite source at the version this plugin builds against
(`runelite/runelite` tag `runelite-parent-1.12.38`, commit
`b505980edd4576104368874d4597bca7b7c463a1`, "Release 1.12.38"), the RuneLite launcher
(`runelite/launcher` master, fetched 2026-09-07) and, where the public source stops at the
injected client, the bytecode of the cached `injected-client-1.12.38.jar` disassembled with
`javap -p -c`. Evidence grades: **[primary]** = source or bytecode read directly;
**[inference]** = my own reasoning from primary facts, not observed in game;
**[unverified]** = could not be checked from the sources available.

Ticket: #30 (part of #18). Builds on `docs/research/camera-model.md` (branch
`research/camera-model`) and `docs/research/hud-cover.md` (branch `research/hud-cover`).

## Question (verbatim)

> The engine predicts a clickbox rect in canvas pixels from the Camera Pose, viewport size and
> Landing Tile, then compares it with HUD widget rects and the viewport bounds. Establish, per
> RuneLite display mode, whether those coordinate spaces agree or whether the engine must
> translate: fixed, resizable classic, resizable modern, stretched mode (`isStretchedEnabled`,
> `getStretchedDimensions`, `getRealDimensions`, `getStretchedModeScale`), GPU plugin on and
> off, and DPI / UI scaling (`getScalingFactor`, custom cursor / overlay scaling). For each
> mode state which space `Perspective.localToCanvas`, `Widget.getBounds`,
> `getViewportXOffset/YOffset`, `getViewportWidth/Height` and `getCanvasWidth/Height` report
> in, and whether an overlay drawn from those values lands on the real pixels. Name the single
> translation (if any) the engine and the Camera Check overlay must apply, and the modes where
> nothing changes.

## TL;DR

- **One space, every mode.** `Perspective.localToCanvas`, `Widget.getBounds`,
  `getViewportXOffset/YOffset`, `getViewportWidth/Height`, `getCanvasWidth/Height`,
  `getRealDimensions` and `getMouseCanvasPosition` all report in the **game canvas space**:
  integer pixels of the game's software framebuffer (`MainBufferProvider.getImage()`,
  `getCanvasWidth` x `getCanvasHeight`), origin top-left. Overlay `Graphics2D` instances are
  created directly on that framebuffer, so overlay coordinates are the same space by
  construction [primary, S4 l.162-174]. Confidence: **high**.
- **The translation the engine and the Camera Check overlay must apply: none.** A rect
  predicted from the Camera Pose in canvas space is directly comparable with widget rects and
  the viewport rect, and drawing it with the overlay `Graphics2D` puts it on the same pixels
  the game drew the tile on, in every mode below. Confidence: **high** for fixed, resizable
  classic, resizable modern, GPU on/off and DPI scaling; **high** for stretched mode by
  primary reading of `Hooks.draw` and `GpuPlugin.drawUi`.
- **Stretched mode is the one mode where canvas pixels are not screen pixels**, but the
  overlay is stretched *together with* the game image (CPU: `Hooks.draw` scales the whole
  framebuffer, overlays included, to `getStretchedDimensions()` [primary, S4 l.417-480]; GPU:
  the framebuffer is uploaded as the "interface texture" at canvas size and drawn over the
  stretched scene at `getStretchedDimensions()` [primary, S7 l.1430-1466, l.1566-1577]).
  Nothing the engine reads or draws changes.
- **DPI / UI scaling never reaches the canvas.** The framebuffer is a plain `BufferedImage`
  with no transform. Java2D's HiDPI transform (`sun.java2d.uiScale`, set only by the launcher
  when a custom scale is configured [primary, S12]) is applied when the finished frame is
  blitted to the AWT canvas, and the GPU plugin re-applies it by scaling `glViewport` with
  `GraphicsConfiguration.getDefaultTransform()` [primary, S7 l.2143-2152]. The only code in
  core that converts canvas pixels to device pixels is screenshot code [primary, S4 l.489-502,
  S10 l.113-158].
- **API names in the question that do not exist** in `net.runelite.api.Client` 1.12.38:
  `getStretchedModeScale` and `getScalingFactor`. There is only `setScalingFactor(int)`, a
  stretched-mode input ("Sets the scaling factor when scaling resizable mode") [primary, S1
  l.1501-1506]. There is no "custom cursor / overlay scaling" option in `RuneLiteConfig`
  [primary, S13]. Confidence: **high**.
- **Two things that are not in canvas space** and must not be mixed in: raw
  `java.awt.event.MouseEvent` coordinates from the AWT canvas (component space = stretched
  size; core's `StretchedModePlugin` rescales them before other listeners see them [primary,
  S8 l.87-103]) and anything derived from `GraphicsConfiguration.getDefaultTransform()`.
  The engine uses neither.

## 1. What "canvas space" is [primary]

The client owns a software framebuffer, `MainBufferProvider`, whose `getImage()` is a
`BufferedImage` of `getCanvasWidth() x getCanvasHeight()` pixels. Every overlay layer is
rendered with a `Graphics2D` obtained from that image:

```java
// Hooks.java l.162-174
private static Graphics2D getGraphics(MainBufferProvider mainBufferProvider)
{
	...
	lastGraphics = (Graphics2D) mainBufferProvider.getImage().getGraphics();
```

`Hooks.draw` (the end-of-frame hook, [S4 l.388-486]) renders `ALWAYS_ON_TOP` with that
graphics, then `clientUi.paintOverlays(graphics2d)`, and only *then* decides how the finished
framebuffer reaches the screen (§3, §4). `Hooks.drawScene` / `drawAboveOverheads` /
`drawInterface` render the other layers with the same graphics ([S4 l.508-560]). So overlay
coordinates are framebuffer pixel coordinates, with no transform, in every mode.

The game fills that same framebuffer: the 3D scene into the rectangle
`(getViewportXOffset, getViewportYOffset, getViewportWidth, getViewportHeight)` and the widgets
at their `getBounds()`. `Perspective.localToCanvasCpu` returns exactly

```java
// Perspective.java l.236-245
final int pointX = client.getViewportWidth() / 2 + x1 * scale / z1;
final int pointY = client.getViewportHeight() / 2 + y2 * scale / z1;
return new Point(pointX + client.getViewportXOffset(), pointY + client.getViewportYOffset());
```

i.e. framebuffer pixels; the GPU variant ([S2 l.252-289]) and the sub-world-view variant
([S2 l.183-210]) add the same viewport offset. `Widget.getBounds()` is documented as "the area
where the widget is drawn on the canvas" and `getCanvasLocation()` as "the location the widget
is being drawn on the canvas ... accounts for the relative coordinates and bounds of any parent
widgets" [S3 l.418-426, l.465-470]. The injected `getViewportXOffset` / `getCanvasWidth` are
plain reads of static ints in the game (no scaling arithmetic) [S11 `getViewportXOffset`,
`getCanvasWidth`].

`OverlayRenderer` treats that space as the canvas too: overlay clipping uses the viewport
rectangle for `ABOVE_SCENE` / `UNDER_WIDGETS` in fixed mode and
`(0, 0, getCanvasWidth, getCanvasHeight)` otherwise [S5 l.727-739]; overlay drag, snap and
clamp use `client.getRealDimensions()` as "the canvas" [S5 l.521, l.853; S6 l.373, l.498,
l.595; S9 l.220].

### 1.1 `getRealDimensions()` vs `getCanvasWidth/Height()` [primary, bytecode]

Outside stretched mode `getRealDimensions()` returns a field that the injected client sets to
`canvas.getSize()` (on start-up and in a `ComponentListener.componentResized`) [S11
`getRealDimensions` l.0-11, `tq.lx` assignment]. In stretched mode it returns the *unstretched*
size (§3.1). Core uses it interchangeably with `getCanvasWidth/Height` as the canvas rectangle,
and the GPU plugin's `scaleFactorX = stretchedDimensions.width / canvasWidth` [S7 l.961-962]
only makes sense if `getCanvasWidth()` is the unstretched width. **[inference]** The two are
the same size in every mode; `getRealDimensions()` is the one core reaches for when it needs a
`Dimension`.

## 2. Fixed, resizable classic, resizable modern [primary]

Nothing in the display-mode switch changes coordinate spaces; it changes *values*:

| Mode | `isResized()` | Viewport rect (canvas px) | Canvas | Overlay clip for `ABOVE_SCENE` |
|------|---------------|---------------------------|--------|--------------------------------|
| Fixed | false | `(4, 4, 512, 334)` from `getViewport*` (values [inference] from the fixed layout; the *API* is what matters) | `765 x 503` (`Constants.GAME_FIXED_SIZE`) | the viewport rect [S5 l.729-734] |
| Resizable classic (`TOPLEVEL_OSRS_STRETCH`) | true | offset `(0,0)`, size = canvas | AWT canvas size | whole canvas [S5 l.738] |
| Resizable modern (`TOPLEVEL_PRE_EOC`) | true | offset `(0,0)`, size = canvas | AWT canvas size | whole canvas [S5 l.738] |

`localToCanvas`, `Widget.getBounds`, the viewport getters and the canvas getters are all in
canvas space and an overlay `Graphics2D` lands on those pixels 1:1. The only mode-specific
rule the engine inherits is the one `hud-cover.md` already states: in fixed mode the 3D view is
the viewport rect and HUD widgets sit outside it; in resizable modes the viewport is the whole
canvas and HUD widgets sit on top of it.

**Rounding.** `localToCanvas` truncates on CPU and rounds on GPU (`camera-model.md` §
projection); that is a one-pixel effect inside canvas space, not a space change.

## 3. Stretched mode [primary]

### 3.1 What stretched mode does

`StretchedModePlugin` calls `client.setStretchedEnabled(true)` and pushes four inputs:
`setStretchedIntegerScaling`, `setStretchedKeepAspectRatio`, `setStretchedFast`,
`setScalingFactor(config.scalingFactor())` [S8a l.68-75, l.105-113]. The config item is
documented as "Resizable scaling — In resizable mode, the game is reduced in size this much
before it's stretched" (percent) [S8b l.66-72].

The injected `getRealDimensions()` (bytecode, [S11]) then is:

- not stretched: the AWT canvas size (`tq.lx`);
- stretched and `isResized()`: parent container size divided by the scaling factor, and if
  that is smaller than `765 x 503`, the container size divided by
  `min(w / 765, h / 503)` instead;
- stretched and fixed: `Constants.GAME_FIXED_SIZE` (`765 x 503`).

`getStretchedDimensions()` is the parent container (the `ClientPanel`) size, reduced to keep
the real aspect ratio when `keepAspectRatio` is set and rounded down to an integer multiple of
the real size when `integerScaling` is set [S11 `getStretchedDimensions`]. The game renders
(and the client reports `getCanvasWidth/Height`, viewport values and widget bounds) at the
**real** dimensions; the AWT `Canvas` component fills the container at the **stretched**
dimensions **[inference** from: `Hooks.draw` draws the stretched image at `(0,0)` onto the
canvas graphics [S4 l.480]; `TranslateMouseListener` divides raw component coordinates by
`stretched / real` [S8 l.87-103]; `getRealDimensions` ignores `canvas.getSize()` while
stretched [S11]**]**.

### 3.2 CPU path (GPU plugin off)

```java
// Hooks.java l.417-480 (abridged)
Image image = mainBufferProvider.getImage();          // canvas-size framebuffer, overlays already drawn
if (client.isStretchedEnabled())
{
	Dimension stretchedDimensions = client.getStretchedDimensions();
	... stretchedImage = gc.createCompatibleVolatileImage(stretchedDimensions.width, stretchedDimensions.height);
	stretchedGraphics.setRenderingHint(KEY_INTERPOLATION, isStretchedFast() ? NEAREST_NEIGHBOR : BILINEAR);
	stretchedGraphics.drawImage(image, 0, 0, stretchedDimensions.width, stretchedDimensions.height, null);
	finalImage = stretchedImage;
}
graphics.drawImage(finalImage, 0, 0, client.getCanvas());
```

The overlays were rendered into `image` *before* this block (`renderOverlayLayer(...,
ALWAYS_ON_TOP)` at l.399, other layers in the earlier hooks), so they are scaled with the game.
An overlay rect drawn at the canvas-space coordinates of a tile lands on the same *screen*
pixels as the tile, both scaled by `stretched / real` (with the same interpolation).

### 3.3 GPU path (GPU plugin on)

`Hooks.draw` returns early when `client.isGpu()` [S4 l.409-414]; `GpuPlugin.draw` composites:

1. `prepareInterfaceTexture(canvasWidth, canvasHeight)` uploads
   `client.getBufferProvider().getPixels()` (the same framebuffer, overlays included) into a
   texture sized `getCanvasWidth x getCanvasHeight` [S7 l.1430-1466, l.1493-1496].
2. The scene FBO is sized to the stretched canvas when stretched [S7 l.911-935], and the scene
   viewport is the canvas viewport rect multiplied by `stretched / canvas` with floor/ceil and
   1 px padding "because having ints for our viewport dimensions can introduce off-by-one
   errors" [S7 l.951-977].
3. `drawUi` draws the interface texture over the whole target: `glDpiAwareViewport(0, 0,
   dim.width, dim.height)` with `dim = getStretchedDimensions()` when stretched, else
   `(canvasWidth, canvasHeight)`; `uniTexSourceDimensions = (canvasWidth, canvasHeight)`
   [S7 l.1546-1577].

So on GPU the overlay layer is stretched by the same factor as the scene. The scene edge may
differ from a naive scaling by up to the 1 px padding [S7 l.966-975] — a sub-pixel screen
effect at the viewport border, invisible in canvas space.

### 3.4 Mouse in stretched mode

Raw AWT events arrive in component (stretched) coordinates. `StretchedModePlugin` registers
`TranslateMouseListener` at position 0 of `MouseManager` [S8a l.70; S8c l.60-63], and
`MouseManager.processMouse*` feeds each listener the event returned by the previous one
[S8c l.210-222], so every later listener, and the game itself, sees

```java
// TranslateMouseListener.java l.87-96
int newX = (int) (e.getX() / (stretchedDimensions.width / realDimensions.getWidth()));
int newY = (int) (e.getY() / (stretchedDimensions.height / realDimensions.getHeight()));
```

`client.getMouseCanvasPosition()` reads the game's own mouse fields [S11
`getMouseCanvasPosition`], i.e. the translated values; core overlays compare it with widget
bounds and tile polygons directly (`TooltipOverlay`, `InteractHighlightOverlay`,
`DevToolsOverlay` and others [S14]). **[inference]** The Camera Check overlay may compare
`getMouseCanvasPosition()` with its canvas-space rect in any mode. Do not read
`MouseEvent.getPoint()` from a listener registered ahead of the stretched-mode translator.

One oddity, recorded and not relied on: `ClientUI.paintOverlays` adds
`getViewportXOffset/YOffset` to `getMouseCanvasPosition()` before hit-testing its sidebar
button [S15 l.1037-1040]. Every other core use treats `getMouseCanvasPosition()` as canvas
space without an offset [S14]; the offset is 0 in resizable modes, where the sidebar button is
drawn, so it has no visible effect there. **[unverified]** which of the two is the intended
semantics in fixed mode.

## 4. DPI / UI scaling [primary]

- The framebuffer is a `BufferedImage`; its `Graphics2D` has the identity transform. Nothing
  in `Hooks`, `OverlayRenderer` or `OverlayManager` applies a DPI transform to overlay drawing
  (grep of `getDefaultTransform` / `getScaleX` across `runelite-client` [S16]: hits only in
  `GpuPlugin`, `Hooks.screenshot`, `ImageCapture`, `ContainableFrame` and a debug log in
  `ClientUI`).
- On the CPU path the final `graphics.drawImage(finalImage, 0, 0, canvas)` is on the AWT
  canvas graphics, which carries the Java2D HiDPI transform; Java2D scales the blit. On the
  GPU path `glDpiAwareViewport` and `initFbo` / `blitSceneFbo` multiply canvas-space values by
  `GraphicsConfiguration.getDefaultTransform().getScaleX/Y()` [S7 l.774-780, l.1091-1096,
  l.2138-2152]. Either way the client APIs stay in logical (canvas) pixels and the plugin never
  sees device pixels.
- Screenshots are the only consumers of device pixels: `Hooks.screenshot` scales the frame by
  the default transform [S4 l.489-502]; `ImageCapture.addClientFrame` scales the frame and the
  canvas offset the same way [S10 l.113-158].
- Where the transform comes from: the launcher leaves the process DPI-*unaware* by default
  (Windows scales the whole window as a bitmap; Java sees scale 1), and only when the launcher
  setting "scale" is set does it pass `-Dsun.java2d.dpiaware=true -Dsun.java2d.uiScale=<scale>`
  [S12]. `RuneLiteLAF` disables FlatLaf's own UI scaling for the Swing sidebar
  (`FlatSystemProperties.UI_SCALE_ENABLED = false`) [S17 l.52]; that does not touch the canvas.
  **[unverified]** the transform actually observed on Linux/macOS HiDPI setups; irrelevant to
  the engine either way because the canvas APIs are pre-transform.
- `Client.setScalingFactor(int)` is *not* DPI: it is the stretched-mode "resizable scaling"
  percentage (§3.1). There is no `getScalingFactor()`, `getStretchedModeScale()`, cursor
  scaling or overlay scaling option in the 1.12.38 API or `RuneLiteConfig` [S1, S13].

## 5. GPU plugin on vs off (unstretched) [primary]

- Off: scene and widgets are software-rendered into the framebuffer; overlays go into the
  same image; the image is blitted to the AWT canvas [S4 l.480].
- On: the scene is rendered by OpenGL into an FBO sized to the canvas (DPI-scaled) [S7
  l.774-780, l.911-935] and blitted; widgets and overlays are still software-rendered into the
  framebuffer and composited on top as a full-canvas texture [S7 l.1430-1466, l.1576-1577].
  The scene viewport in GL is `(viewportXOffset, canvasHeight - viewportHeight -
  viewportYOffset, viewportWidth, viewportHeight)` — the same canvas-space rect, y flipped for
  GL [S7 l.951-977].
- `Perspective.localToCanvas` switches between the integer CPU projection and the float GPU
  projection on `client.isGpu()` [S2 l.178-181]; both return canvas pixels. The GPU projection
  is documented in `camera-model.md`; the difference is rounding, not space.

## 6. Rule for the Sightline engine and the Camera Check overlay

1. **Work in canvas space only.** Inputs: `getCameraX/Y/Z`, `getCameraPitch/Yaw`,
   `getScale`, `getViewportXOffset/YOffset/Width/Height`, `getCanvasWidth/Height` (or
   `getRealDimensions()`), `Widget.getBounds()`, `getMouseCanvasPosition()`. Output: the
   predicted clickbox rect, compared with widget rects and the viewport rect, and drawn with
   the overlay `Graphics2D` as-is. **No translation in any mode.**
2. **Viewport rect for "inside the 3D view":** `(getViewportXOffset, getViewportYOffset,
   getViewportWidth, getViewportHeight)` in every mode; it equals the whole canvas in
   resizable modes. This is what `OverlayRenderer.clipBounds` uses too [S5 l.727-739].
3. **Do not** consume raw `MouseEvent` coordinates, `Canvas.getSize()`,
   `getStretchedDimensions()` or `GraphicsConfiguration.getDefaultTransform()` in the
   projection or the comparison; they are the only values not in canvas space.
   `getStretchedDimensions()` / `isStretchedEnabled()` are worth *logging* in the measurement
   build so a stretched-mode session can be told apart, nothing more.
4. **Modes where nothing changes:** all of them — fixed, resizable classic, resizable modern,
   stretched (any scaling factor, integer scaling, keep-aspect), GPU on, GPU off, any DPI
   scale. What changes between modes is the *values* (viewport size and offset, canvas size,
   which top-level interface owns the HUD), never the space.

## 7. Confidence

| Claim | Grade | Basis |
|-------|-------|-------|
| Overlay `Graphics2D` is created on the game framebuffer, no transform | primary | S4 l.162-174 |
| `localToCanvas`, widget bounds, viewport and canvas getters share that space | primary | S2, S3, S5, S11 |
| Stretched mode scales the finished framebuffer, overlays included (CPU) | primary | S4 l.417-480 |
| Stretched mode composites the framebuffer as a full-target texture (GPU) | primary | S7 l.1430-1466, l.1566-1577 |
| `getRealDimensions` = unstretched size; formula per mode | primary (bytecode) | S11 |
| AWT canvas component is stretched-size while the game runs at real size | inference | S4 l.480, S8 l.87-103, S11 |
| DPI transform applied after the framebuffer, never to overlay coordinates | primary | S4, S7, S10, S12, S16 |
| No `getScalingFactor`, `getStretchedModeScale`, cursor/overlay scaling API | primary | S1, S13 |
| `getCanvasWidth/Height` equals `getRealDimensions` in every mode | inference | S7 l.961-962, S5/S6 usage |
| `ClientUI.paintOverlays` mouse + viewport offset semantics in fixed mode | unverified | S15 l.1037-1040 |
| Actual HiDPI transform on Linux/macOS | unverified | not in the sources read |

## Open items for in-game verification

Nothing here changes the engine's design, but the measurement build (#23) can confirm the
inference cheaply: log `isStretchedEnabled()`, `getStretchedDimensions()`,
`getRealDimensions()`, `getCanvasWidth/Height()` once per session. Expected: real == canvas in
every mode; stretched != real only with the Stretched Mode plugin on. A Camera Check rect that
sits on the tile in a stretched-mode session (any scaling factor) closes the last inference.

## Sources

- **S1** `runelite-api/src/main/java/net/runelite/api/Client.java` @ `runelite-parent-1.12.38`
  — `getCanvasHeight/Width` l.318-328, `getViewportHeight/Width/XOffset/YOffset` l.330-356,
  `getMouseCanvasPosition` l.365-370, `isResized` l.703-708, `isStretchedEnabled` l.1456-1461,
  `setStretchedFast/IntegerScaling/KeepAspectRatio` l.1468-1499, `setScalingFactor`
  l.1501-1506, `invalidateStretching` l.1508-1516, `getStretchedDimensions` l.1518-1523,
  `getRealDimensions` l.1525-1530, `isGpu` l.1831. No `getScalingFactor` / `getStretchedModeScale`.
  https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/Client.java
- **S2** `runelite-api/src/main/java/net/runelite/api/Perspective.java` — `localToCanvas`
  l.178-210, `localToCanvasCpu` l.212-250, `localToCanvasGpu` l.252-289, `getCanvasTilePoly`
  l.706-730.
- **S3** `runelite-api/src/main/java/net/runelite/api/widgets/Widget.java` — `getCanvasLocation`
  l.418-426, `getBounds` l.465-470.
- **S4** `runelite-client/src/main/java/net/runelite/client/callback/Hooks.java` — `getGraphics`
  l.162-174, `mouseMoved` l.358-361, `draw` l.388-486, `screenshot` l.489-502, `drawScene`
  l.508-560, `drawAboveOverheads` l.524, `drawInterface` l.546.
  https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/callback/Hooks.java
- **S5** `runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayRenderer.java` —
  `canvasRect = new Rectangle(client.getRealDimensions())` l.521, `clipBounds` l.727-739,
  `clampOverlayLocation` l.847-870.
- **S6** `runelite-client/src/main/java/net/runelite/client/ui/overlay/OverlayManager.java` —
  `getRealDimensions` as canvas l.373, l.498, l.595.
- **S7** `runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java` —
  `initFbo` l.774-780, scene draw / FBO / stretched viewport l.902-977, `blitSceneFbo`
  l.1087-1096, `prepareInterfaceTexture` l.1430-1466, `draw` l.1468-1520, `drawUi` l.1546-1590,
  `screenshot` l.1602-1625, `getScaledValue` l.2138-2141, `glDpiAwareViewport` l.2143-2152.
  https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java
- **S8** `runelite-client/src/main/java/net/runelite/client/plugins/stretchedmode/` —
  (S8a) `StretchedModePlugin.java` l.68-113; (S8b) `StretchedModeConfig.java` l.66-72;
  `TranslateMouseListener.java` l.87-103 (`translateEvent`); (S8c)
  `runelite-client/src/main/java/net/runelite/client/input/MouseManager.java` l.60-63,
  l.210-222.
- **S9** `runelite-client/src/main/java/net/runelite/client/ui/overlay/WidgetOverlay.java` l.220.
- **S10** `runelite-client/src/main/java/net/runelite/client/util/ImageCapture.java`
  `addClientFrame` l.113-158.
- **S11** `injected-client-1.12.38.jar` (Gradle cache,
  `net.runelite:injected-client:1.12.38`, sha1 dir `eded2cde...`), `javap -p -c client.class`:
  `getRealDimensions` (branches on `isStretchedEnabled`, `isResized`, divides the parent
  container size by the static double scaling factor, falls back to `min(w/765, h/503)` or
  `Constants.GAME_FIXED_SIZE`), `getStretchedDimensions` (parent container size, keep-aspect
  and integer-scaling adjustments), `getCanvasWidth` / `getViewportXOffset` (static int reads),
  `getMouseCanvasPosition` (two static int reads); `tq.lx` assigned from
  `Canvas.getSize()` in `tq.ad()` and in `rl12.componentResized`. Obfuscated names are as of
  this jar only.
- **S12** `runelite/launcher` master, `src/main/java/net/runelite/launcher/Launcher.java`
  (fetched 2026-09-07): option `scale` "Custom scale factor for Java 2D"; when set, JVM
  properties `sun.java2d.dpiaware=true` and `sun.java2d.uiScale=<scale>`, with the comment that
  the `RuneLite.exe` manifest is DPI unaware and Windows scales the application by default.
  https://github.com/runelite/launcher/blob/master/src/main/java/net/runelite/launcher/Launcher.java
- **S13** `runelite-client/src/main/java/net/runelite/client/config/RuneLiteConfig.java` — no
  item matching `scal` (case-insensitive grep).
- **S14** grep `getMouseCanvasPosition` across `runelite-client/src/main`: `TooltipOverlay`
  l.93-99 (clamped to `getCanvasWidth/Height`), `InteractHighlightOverlay` l.220,
  `DevToolsOverlay` l.341/360 (`poly.contains(mouse)` on canvas tile polys),
  `InfoBoxOverlay` l.173, `CombatLevelOverlay` l.85 and 14 others, all offset-free.
- **S15** `runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java` —
  `getCanvasOffset` l.999-1008, `paintOverlays` l.1019-1046, `logGraphicsEnvironment`
  l.762-770.
- **S16** grep `getDefaultTransform|getScaleX\(|uiScale|dpiaware` across
  `runelite-client/src/main` and `runelite-api/src/main`: hits only in `ImageCapture`,
  `ContainableFrame` (JDK-8221452 min-size workaround), `ClientUI` (debug log), `GpuPlugin`,
  `Hooks.screenshot`.
- **S17** `runelite-client/src/main/java/net/runelite/client/ui/laf/RuneLiteLAF.java` l.52.
- Repo context: `docs/research/camera-model.md` (branch `research/camera-model`) for the
  projection maths and CPU/GPU rounding; `docs/research/hud-cover.md` (branch
  `research/hud-cover`) for the per-layout HUD widget list and the fixed-mode viewport rule.
