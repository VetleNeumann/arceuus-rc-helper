# Target geometry for Sightline — research

Researched 2026-09-06 against the RuneLite source (`runelite/runelite` master at commit
`ac79ed8bd8926bec7bf172aa291574b4d944b0e7`, 2026-09-03), the live OSRS cache as archived by
OpenRS2 (cache 2686, build 240, 2026-09-02, for loc definitions and models; cache 2499,
build 236, 2026-03-18, for map placements, the newest one OpenRS2 publishes XTEA keys for),
decoded with RuneLite's own `net.runelite:cache:1.12.38`, the OSRS Wiki, and a projection
simulation that re-implements `Perspective.modelToCanvasCpu`.
Evidence grades: **[primary]** = RuneLite source, cache data decoded with RuneLite's cache
module, or wiki page read directly; **[secondary]** = third-party cache dump or remembered
client internals not verifiable from the public repo; **[inference]** = my own reasoning or
simulation, not observed in game.

## Question (verbatim, #21)

> The real `TileObject.getClickbox()` only exists for the current camera. For a predictive
> Sightline the engine needs each Blood target's geometry (Blood Altar 4x4, Dark Altar, two Dense
> Runestones, the 69 and 73 scramble rocks): can the plugin read the model's AABB or vertices via
> `GameObject.getRenderable().getModel()` while the object is in scene, cache them keyed by object
> id, and re-project them from a Landing Tile? Are AABB extents in local units around the object
> centre, and does orientation matter for these objects? What is the fallback when the object has
> never been in scene since login (footprint tiles plus a fixed height?), and is a footprint box
> accurate enough for the 90 % / 15 % thresholds? Deliver a decision: model AABB, full mesh, or
> footprint box, with evidence.

## TL;DR

- **Decision: full mesh, loaded from the game cache by model id at runtime, projected with
  RuneLite's public `Perspective.modelToCanvas`; the AABB as the cheap first pass; the footprint
  box only as a sanity check.** The object never has to be in scene. `client.loadModelData(id)`
  is public API, RuneLite's own Ground Items plugin uses it, and every target is a static loc
  (animation id −1), so the cache mesh is the scene mesh once rotated by the loc orientation
  [primary, S1 S4 S6].
- **Yes to all of question 1.** A static `GameObject`'s `getRenderable()` is the `Model` itself
  (`DynamicObject` only for animated locs, none here). `Model.getAABB(orientation)` returns a
  centre and half-extents in model space, in 1/128-tile units, relative to the object's origin,
  which is its centre `getLocalLocation()` at height `getZ()`; `Perspective.calculateAABB` is
  eight lines and reproducible offline [primary, S1 S2]. Orientation: the scene mesh is
  pre-rotated at load, `getModelOrientation()` is 0, so `getAABB(0)` is already in scene frame.
  Both altars sit at orientation 0; the north runestone at 3 (extents swap by ≤ 25 units)
  [primary, S6].
- **Re-projection from a Landing Tile needs no new maths.** Under a fixed Camera Pose the camera
  is a rigid offset from the player, so the view from Landing Tile L equals the current view of
  the object translated by (player − L), heights included. Feed that translated origin and the
  cached vertices to `modelToCanvas`, take `Jarvis.convexHull` [primary API, inference on
  rigidity, S1 S2 S3].
- **Fallback accuracy.** A footprint box with the *cache* height reproduces the AABB
  (projected area ratio 0.99–1.02). A footprint box with a *guessed* one-tile height is off by
  8–17× for the Blood Altar, 4–8× for the Dark Altar, 2× for the runestone and 2.5× the other
  way for the rocks: useless. The AABB itself overstates the projected mesh by 1.3–3.4×
  (Blood Altar 0.29–0.50, Dark Altar 0.56–0.76, runestone 0.64–0.79, rocks ≈ 0.6) because the
  altar is a slender 8.3-tile crystal on a 4-tile base. With a viewport edge across the Blood
  Altar, an AABB-based on-screen fraction reads 0.84 where the mesh is at 0.97 and 0.27 where
  the mesh is at 0.08: **both the 90 % and the 15 % verdicts flip inside a ±0.15 band for the
  altars.** For the runestone the two agree within 0.03 [inference, simulation S8].
- **Ids and footprints** (cache 2686/2499 [primary S6]): Blood Altar `ARCHEUS_ALTAR_BLOOD`
  27978, 4x4, SW (1715, 3828), model 30835, 1058 units tall; Dark Altar `ARCHEUS_ALTAR_DARK`
  27979, 3x3, SW (1715, 3882), model 30837, 562 tall; Dense Runestones are **5x5**, not 1x1:
  `ARCEUUS_RUNESTONE_BASE_1` 8981 at SW (1762, 3856) and `ARCEUUS_RUNESTONE_BASE_2` 10796 at
  SW (1762, 3844), impostor models 30836 (mineable) / 31000 (depleted), 348 tall; 69 north
  rocks 34741 at (1761, 3873); 73 west rocks: clickable ends `ARCHEUUS_RUNESTONE_SHORTCUT_GREY_TOP`
  27984 at (1743, 3854) and `..._GREY_BOTTOM` 27985 at (1751, 3854); the seven `..._GREY_MIDDLE`
  27986 tiles between have no action. All rocks are loc type 22, ground decorations, i.e.
  `GroundObject` not `GameObject`, 1x1, model 9237 / 9235, 48–53 units tall.
- **Confidence:** high on the API facts and cache geometry; medium on the translation trick and
  the simulation numbers (heights assumed flat, camera offset assumed); one open discrepancy
  (Blood Altar SW tile: cache (1715, 3828) vs in-game reading (1716, 3829)) that one log line
  settles.

## 1. Reading a static GameObject's model in scene

### Which type comes back from `getRenderable()` [primary]

- `GameObject.getRenderable()` returns a `Renderable`; `Model extends Mesh<Model>, Renderable`
  and `DynamicObject extends Renderable` (animated locs; `getModel()` is the current frame,
  `getModelZbuf()` is "threadsafe and doesn't support animations")
  (`runelite-api/.../GameObject.java`, `Model.java`, `DynamicObject.java`) [S1].
- RuneLite's own consumers use one idiom for both cases:
  `Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();`
  (`ModelOutlineRenderer.java:998-1006`), and the GPU plugin uploads `instanceof Model` directly
  and `DynamicObject.getModelZbuf()` otherwise (`gpu/SceneUploader.java:370-376, 408-415`)
  [S3].
- Every Blood target is static: `animationID = -1` for 27978, 27979, 8975, 8976, 8981, 10796,
  27984–27986 and 34741 in the cache loc definitions decoded with `ObjectLoader` [S6]. So
  `getRenderable()` is a `Model` for all of them, and the vertex arrays are not animation
  frames [primary for the cache, inference for the runtime type].
- Vertex access is on `Mesh`: `float[] getVerticesX/Y/Z()`, `int getVerticesCount()`,
  `int[] getFaceIndices1/2/3()`; `Model` adds `getAABB(int)`, `useBoundingBox()`,
  `getBottomY()`, `getRadius()`, `getXYZMag()` [S1].
- Runestone caveat: the scene object id is the multiloc parent (8981 / 10796, matching the
  `ObjectID` names `SceneTracker` already switches on) while the rendered model is the
  impostor's (`ObjectComposition.getImpostorIds()/getImpostor()`, varbits 4927 / 4928 =
  `VarbitID.ARCEUUS_RUNESTONE_1/2`) [S1 S6]. Mineable and depleted models differ by ≤ 11
  units in plan and not at all in height (30836: x −314..337, z −306..312, 348 tall;
  31000: x −303..337, z −306..308, 348 tall) [S6]. Whether the varbit change re-fires
  `GameObjectSpawned` is not verifiable from the public repo [inference]; key any live cache by
  object id **and** the impostor id or model identity, and refresh on spawn.
- The runestone visual is three stacked locs: base 5x5 on plane 0 (the only one with the
  "Chip" op), middle 7x7 on plane 1 (10797/10798, model 30838, no ops), top 5x5 on plane 2
  (10799/10800, model 30834, no ops) [S6]. The click target is the base alone, 2.7 tiles tall.
- The scramble rocks are loc type 22 (ground decoration) [S6 S7], which the client hands out as
  `GroundObject` (`getRenderable()`, `getConvexHull()`, `getConfig()` with orientation in bits
  6–7; no `sizeX/sizeY`, no `getOrientation()`) [S1]. `SceneTracker.scanTile` already reads
  `tile.getGroundObject()` and the plugin subscribes to `GroundObjectSpawned`
  (`ArceuusRcHelperPlugin.java:162`), the same way RuneLite's Agility plugin tracks shortcut
  rocks (`AgilityPlugin.java:372-381`) [S3 S9]. Type 22 = ground decoration is the standard loc
  type table [secondary]; the in-scene type is what `SceneTracker` already receives, so it
  needs no assumption.

### AABB semantics [primary]

`AABB` is six ints: `getCenterX/Y/Z()`, `getExtremeX/Y/Z()` (`runelite-api/.../AABB.java`).
`Perspective.calculateAABB` (`Perspective.java:1017-1059`) builds the eight corners as
`centre ± extreme` per axis, maps model `(X, Z, Y)` onto the projection's `(east, north, up)`
and projects them with `modelToCanvas(client, wv, 8, x, y, z, /*rotate*/ 0, ...)`, where
`(x, y, z)` are the object's `getX()`, `getY()`, `getZ()` [S2]:

- **Units:** model space is 1/128 tile (`Mesh.translate` javadoc: "1/128ths of a tile") and the
  same axes as local coordinates, X east, Z north, Y vertical with negative = up
  (`Model.getBottomY()`; every decoded mesh has y ∈ [−height, 0]) [S1 S6].
- **Origin:** the object's local point. `TileObject.getX()/getY()` are "in local context",
  `getLocalLocation()` "is the center point of the object", `getZ()` "the vertical coordinate"
  [S1]. `SceneUploader` uploads game objects at exactly
  `(gameObject.getX(), gameObject.getZ(), gameObject.getY())` with
  `gameObject.getModelOrientation()` (`SceneUploader.java:351-352`) [S3]. So the extents are
  relative to the centre, as the ticket assumed; for the 4x4 altar that centre sits on the tile
  corner `SW*128 + 256` on both axes [inference from the sizes].
- **Orientation:** `getAABB(int orientation)` takes a JAU angle and bounds the rotated
  vertices; `GameObject.getModelOrientation()` "is typically 0 for non-actors, since most
  object's models are oriented prior to lighting during scene loading" [S1]. Hence
  `getAABB(gameObject.getModelOrientation())` (= `getAABB(0)`) is in scene frame already. From
  the map data both altars are placed at orientation 0, the north runestone at 3 and the south
  at 0, the rocks at 1/2/3 [S6]. For a rotation the x and z extents swap, which for the north
  runestone changes the box by at most 25 units (0.2 tile) and for the square altars by 0–13
  units. **Orientation matters for rotating a cache-loaded mesh into scene frame; it barely
  matters for the extents.**
- **`useBoundingBox()`**: `getClickbox` returns the projected AABB hull outright when it is
  true, otherwise the per-face rectangle union intersected with that hull
  (`Perspective.java:985-1015`) [S2]. It is only readable from a live `Model`; log it once per
  target in game and cache it. It is not in the cache definition [S6].

### Caching keyed by object id [inference on top of primary facts]

Copy, do not reference: `getVerticesX/Y/Z()` are the client's own arrays; RuneLite's javadoc on
`loadModelData` warns the returned model "shares data ... with other models" [S1]. A copy is
about 3 × 316 floats for the altar, 3 × 733 for the Dark Altar, trivial. Capture on
`GameObjectSpawned`/`GroundObjectSpawned` (once per scene rebuild, rule "Track objects via
spawn/despawn events", `docs/RUNELITE-RULES.md:21`) [S9]. Key: `(objectId, orientation)`;
the rocks share model 9237 across ids 27984, 27987 and 34741 at different orientations [S6].

## 2. Re-projecting from a Landing Tile

### The projection is public and reproducible [primary]

`Perspective.modelToCanvas(Client, WorldView, int end, int x3dCenter, int y3dCenter,
int z3dCenter, int rotate, float[] x3d, float[] y3d, float[] z3d, int[] x2d, int[] y2d)`
(`Perspective.java:300`) is plain public API; only `getClickbox` carries
`@ApiStatus.Internal` ("You don't want this", `Perspective.java:984`) [S2]. It dispatches to a
GPU or CPU variant (`:377`, `:442`) whose inputs are just `getCameraX/Y/Z` (or the `Fp`
variants), `getCameraPitch/Yaw` (JAU14), `getScale()`, viewport size and offset, and writes
`Integer.MIN_VALUE` for vertices closer than 50 units to the camera plane. The CPU maths, verbatim:

```
x1 = x*yawCos + y*yawSin;  y1 = y*yawCos - x*yawSin;
y2 = z*pitchCos - y1*pitchSin;  z1 = y1*pitchCos + z*pitchSin;
viewX = viewportW/2 + x1*scale/z1 + viewportXOffset;  viewY = viewportH/2 + y2*scale/z1 + viewportYOffset;
```

`Jarvis.convexHull(int[] xs, int[] ys)` returning a `SimplePolygon` is public too
(`runelite-api/.../model/Jarvis.java:79`) [S2]. My Python re-implementation of the CPU path
(`sim.py`, scratchpad) reproduces the eight-corner projection; the numbers in section 3 come
from it [inference].

### Translating instead of moving the camera [inference]

`Client.getCameraFocalPointX/Y/Z()` are "Typically the player position" [S1]. Under a fixed
Camera Pose (yaw, pitch, zoom held) the camera is therefore a rigid offset **v** from the
player's focal point. The screen position of any point **O** seen from Landing Tile **L** depends
only on **O − (L + v)**; seen from the current player tile **P** it is **O − (P + v)**. So
projecting the object at **O + (P − L)** with the *current* camera gives the Landing Tile view,
exactly, including the GPU/CPU difference and the near-plane rule, without touching the camera
or re-deriving its placement. Concretely:

```java
int dx = playerLocal.getX() - landingLocal.getX();
int dy = playerLocal.getY() - landingLocal.getY();
int dz = heightAt(player) - heightAt(landing);          // Perspective.getTileHeight, cached per Landing Tile
Perspective.modelToCanvas(client, wv, n, objX + dx, objY + dy, objZ + dz, 0,
    cachedX, cachedZ, cachedY, x2d, y2d);               // note the (X, Z, Y) argument order, as calculateAABB does
SimplePolygon hull = Jarvis.convexHull(x2d, y2d);       // after dropping Integer.MIN_VALUE entries
```

Caveats, all unverified in game:

1. Heights. `getTileHeight` returns 0 outside the base scene (`Perspective.java:628-651`) [S2],
   so the Landing Tile's height and the object's `getZ()` must be cached when seen. The area is
   fairly flat but the mine sits on a ridge; a 1-tile (128-unit) height error moves a 50-tile
   target by roughly `128 * scale / z1` ≈ 10 px at scale 512, a tenth of the altar's projected
   height [inference].
2. The client's own camera clipping against terrain and the pitch relaxer are not modelled.
3. `modelToCanvas` has no extended-scene bounds check, unlike `localToCanvas` [S2]; the object
   being outside the base scene is handled by Far Bind separately anyway.
4. The non-hull branch of `getClickbox` (per-face rectangles padded by 5 px, unioned, viewport
   culled with the `vpY1 = getViewportXOffset()` bug, then intersected with the hull) is
   `@Internal`; the convex hull of the projected mesh is the honest public proxy. It is a
   superset of the true clickbox by the hull's concavities only [S2, inference].

## 3. Fallback when the object has not been in scene

### Better than a fallback: load the mesh from the cache at runtime [primary]

`Client.loadModelData(int id)` "Loads an unlit model from the cache ... null if it is loading
or nonexistent"; `loadModel(int id)` is `loadModelData(id).light()`; `Mesh.rotateY90Ccw()/
rotateY180Ccw()/rotateY270Ccw()` rotate "around the vertical axis" and require
`ModelData.cloneVertices()` first (`Client.java:1105-1134`, `Mesh.java`, `ModelData.java`)
[S1]. RuneLite's Ground Items plugin does exactly this on the client thread
(`grounditems/Lootbeam.java:52-75`, `clientThread.invoke` at `:134`) [S3], so the pattern is
hub-clean; it is cache IO on the client thread, not network or disk IO
(`docs/RUNELITE-RULES.md:16`) [S9]. The model ids are integers like the `ObjectID`s
(table below); nothing from the cache is bundled with the plugin.

With that, the only in-scene-only facts are `useBoundingBox()`, the live centre/height, and
tile heights. The mesh, AABB and footprint never depend on a spawn event. Load once per target
on `LOGGED_IN` (retrying while `null`), rotate by the loc orientation from the table, keep the
float arrays.

### If a table-only fallback is still wanted: footprint box with the cache height

Box = footprint ± `size*64` in plan, 0 to −height vertically, centred on `SW*128 + size*64`.
The cache says what the height is, so "fixed height" becomes "per-target height":

| target | footprint half-extent | AABB half-extents x / z (scene frame) | height (units / tiles) |
|---|---|---|---|
| Blood Altar 30835 | ±256 | ±260 / −247..248 | 1058 / 8.3 |
| Dark Altar 30837 | ±192 | ±192 / ±192 | 562 / 4.4 |
| Dense Runestone base 30836 (orient 0) | ±320 | −314..337 / −306..312 | 348 / 2.7 |
| Rocks 9237 (top, ids 27984, 34741) | ±64 | ±63 / −64..63 | 48 / 0.4 |
| Rocks 9235 (bottom, id 27985) | ±64 | ±64 / ±64 | 53 / 0.4 |

[primary S6]. In plan the meshes never leave the footprint by more than 17 units (0.13 tile).

### Simulation: projected areas from real Landing Tiles [inference]

Set-up (`sim.py`): real placements and meshes from the cache, flat terrain, camera 600 units
from the focal point along the Pose direction (the classic client constant [secondary]), yaw
aimed at the target, pitch 128 / 256 / 383 JAU, scale 256 / 512, Landing Tiles from the current
Rotation. Areas are of the convex hull of the projected points; `mesh` = all vertices,
`AABB` = the eight corners, `box` = footprint box with the cache height, `naive` = footprint
box with a guessed one-tile (128) height. Selected rows (pitch 256, scale 512):

| target, Landing Tile, distance | mesh / AABB | box / AABB | naive / AABB | AABB on screen (h × w px) |
|---|---|---|---|---|
| Blood Altar from (1718, 3878), 50 | 0.48 | 0.99 | 0.11 | 177 × 65 |
| Blood Altar from (1731, 3856), 28 | 0.35 | 1.00 | 0.10 | 312 × 142 |
| Dark Altar from (1761, 3874), 46 | 0.76 | 1.00 | 0.23 | 104 × 63 |
| Dark Altar from (1718, 3878), 4 | 0.60 | 1.00 | 0.19 | 602 × 451 |
| Runestone N from (1761, 3872), 16 | 0.79 | 1.02 | 0.48 | 164 × 225 |
| Runestone S from (1752, 3854), 10 | 0.68 | 1.02 | 0.47 | 224 × 377 |
| Rocks 69 from (1718, 3882), 43 | 0.61 | 1.01 | 2.49 | 10 × 20 |
| Rocks 73 from (1717, 3826), 28 | 0.60 | 1.01 | 2.52 | 11 × 25 |

Across all pitches and scales the ranges are: mesh/AABB 0.29–0.50 Blood Altar, 0.56–0.76 Dark
Altar, 0.64–0.79 runestone, 0.59–0.67 rocks; box/AABB 0.99–1.02 everywhere; naive/AABB
0.06–0.12 Blood Altar, 0.13–0.23 Dark Altar, 0.41–0.48 runestone, 2.3–2.6 rocks.

### Is a box accurate enough for 90 % / 15 %? [inference]

The verdict is an on-screen fraction, so what matters is how the AABB fraction tracks the mesh
fraction while a viewport (or HUD) edge sweeps across the target (`cut.py`, pitch 256,
scale 512, `AABB fraction -> mesh fraction`):

| target | edge from the top (spire first) | edge from the bottom (base first) |
|---|---|---|
| Blood Altar, 50 tiles | 0.84→0.97, 0.73→0.92, 0.53→0.77, 0.23→0.41, 0.14→0.26 | 0.16→0.03, 0.27→0.08, 0.47→0.23, 0.86→0.74 |
| Dark Altar, 46 tiles | 0.84→0.95, 0.64→0.78, 0.24→0.30, 0.14→0.18 | 0.16→0.05, 0.36→0.22, 0.86→0.82 |
| Runestone N, 16 tiles | 0.84→0.88, 0.63→0.67, 0.21→0.22, 0.11→0.10 | 0.16→0.12, 0.37→0.33, 0.89→0.90 |
| Rocks 69, 43 tiles | 0.87→0.98, 0.66→0.84, 0.23→0.35, 0.13→0.19 | 0.13→0.02, 0.34→0.16, 0.87→0.81 |

- **Blood Altar:** the AABB says Obstructed (0.84) while the mesh says Clear (0.97); it says
  Obstructed (0.27) while the mesh says Hidden (0.08). Both thresholds flip in a band about
  ±0.15 wide. A box or AABB is **not** accurate enough here; and this is the Hop the whole
  feature exists for (Far Bind from Dark Approach, the altar's spire at the top of the screen in
  the wiki screenshot, `docs/research/blood-altar-far-click.md`).
- **Dark Altar and rocks:** flips inside roughly ±0.1; borderline.
- **Runestone:** AABB and mesh agree within 0.03; the box is fine.
- The naive fixed-height box is wrong by a factor for every target and must not ship.

Hence the decision above: mesh for the verdict, AABB (cache extents) as the quick reject when
the whole box is on screen (fraction 1.0 means Clear whatever the shape) or wholly off screen,
box only to cross-check the cache numbers in game.

## 4. Object ids, footprints and placements

Sizes, models and animation ids from cache 2686 (build 240) [S6]; placements from cache 2499
(build 236; the region group ids changed by 2686 and OpenRS2 has no keys for it, but the wiki
lists no map change to these objects since 2016 and the runestone positions match the wiki's
cache-derived list exactly) [S6 S7]; names from `net.runelite.api.gameval.ObjectID` at the
RuneLite commit above [S1].

| target | gameval constant | id | loc type | size | SW tile, plane | orient | model(s) | ops |
|---|---|---|---|---|---|---|---|---|
| Blood Altar | `ARCHEUS_ALTAR_BLOOD` | 27978 | 10 (GameObject) | 4x4 | (1715, 3828, 0) | 0 | 30835 | Bind |
| Dark Altar | `ARCHEUS_ALTAR_DARK` | 27979 | 10 | 3x3 | (1715, 3882, 0) | 0 | 30837 | Venerate |
| Dense Runestone north, base | `ARCEUUS_RUNESTONE_BASE_1` | 8981 | 10 | 5x5 | (1762, 3856, 0) | 3 | impostor 8975 `ARCEUUS_RUNESTONE_BASE_MINE` → 30836; 8976 `..._DEPLETED` → 31000 | Chip / Check |
| Dense Runestone south, base | `ARCEUUS_RUNESTONE_BASE_2` | 10796 | 10 | 5x5 | (1762, 3844, 0) | 0 | same impostors | Chip / Check |
| runestone middle (no ops) | `ARCEUUS_RUNESTONE_MIDDLE_1/2` | 10797 / 10798 | 10 | 7x7 | (1761, 3855, 1) / (1761, 3843, 1) | 3 / 0 | 30838 / 30997 | none |
| runestone top (no ops) | `ARCEUUS_RUNESTONE_TOP_1/2` | 10799 / 10800 | 10 | 5x5 | (1762, 3856, 2) / (1762, 3844, 2) | 3 / 0 | 30834 / 30999 | none |
| Rocks, 69 north scramble | none on master (config `archeuus_runestone_shortcut_grey_shortcut_north`) | 34741 | 22 (GroundObject) | 1x1 | (1761, 3873, 0) | 2 | 9237 | Climb |
| Rocks, 73 west, west end | `ARCHEUUS_RUNESTONE_SHORTCUT_GREY_TOP` | 27984 | 22 | 1x1 | (1743, 3854, 0) | 1 | 9237 | Climb |
| Rocks, 73 west, east end | `ARCHEUUS_RUNESTONE_SHORTCUT_GREY_BOTTOM` | 27985 | 22 | 1x1 | (1751, 3854, 0) | 3 | 9235 | Climb |
| Rocks, 73 west, middle ×7 | `ARCHEUUS_RUNESTONE_SHORTCUT_GREY_MIDDLE` | 27986 | 22 | 1x1 | (1744..1750, 3854, 0) | 2,3,0,1,2,3,0 | 9236 | none |

Wiki cross-checks [S7]: Blood Altar (Kourend) page id 27978, marker (1717, 3829) r=4; Dark
Altar page id 27979, pin (1716, 3883) = the centre tile of a 3x3 at SW (1715, 3882); Dense
runestone page ids 8975/8977/8979 (mineable) and 8976/8978/8980 (depleted), locations
(1762, 3856) and (1762, 3844); "Rocks (dense essence mine, north)" page id 34741, map
(1760, 3873) r=3, level 69.

Things this table changes in the repo:

- `ArceuusRcArea.BLOOD_ALTAR_FOOTPRINT_SW = (1716, 3829)` was read in game; the cache says
  (1715, 3828), one tile south-west on both axes. Every other cache placement matches the wiki
  and the plugin's stand tiles, so the likeliest explanation is that the in-game reading came
  from `getWorldLocation()` of an even-sized object (the centre lies on a tile corner and the
  rounding convention is not in the public API) rather than from `getSceneMinLocation()`
  [inference]. One log line of `getSceneMinLocation()` + `getWorldLocation()` +
  `getLocalLocation()` for the altar settles it; the Far Bind 46-tile rule in
  `blood-altar-far-click.md` was derived against the in-game SW and may shift by one.
- `ArceuusRcArea.RUNESTONE_NORTH = (1761, 3873)` is the 69 rocks tile, not a runestone (already
  flagged in `blood-altar-far-click.md`). The stones are at SW (1762, 3856) and (1762, 3844),
  five tiles square each.
- `AgilityShortcut.WEST_73` names `ARCHEUUS_RUNESTONE_SHORTCUT_GREY_MIDDLE` (27986) as its hop
  object; those seven tiles have no action. The clickable rocks are 27984 (west end, the Blood
  Altar side) and 27985 (east end, the mine side). `SceneTracker` happens to pick 27984 today
  because it prefers the object nearest `ArceuusRcArea.SHORTCUT` (1743, 3854).
- The ticket's "1x1 runestone" is 5x5.

## Decision and what to cache

**Geometry source:** the cache mesh per model id, loaded with `client.loadModelData(id)` on the
client thread, `cloneVertices()`, rotated by the loc orientation with `rotateY*Ccw()`,
vertices copied into the plugin's own float arrays. Fall back to the spawn-captured `Model`
only for `useBoundingBox()` and for cross-checking (its `getAABB(0)` should equal the table).

**Per target (static, from this document):** object id(s), impostor ids, model id, footprint
size, SW tile, plane, loc orientation, AABB extents and height.

**Per target (learned in game, cached until `LOADING`):** centre `LocalPoint`/`getZ()` when in
scene, `useBoundingBox()`, and the tile heights of each Landing Tile and of the object's centre.

**Per frame (Camera Check on; nothing during play):** translate by (player − Landing Tile),
`modelToCanvas` on the AABB corners first (whole box on screen → Clear; whole box off → Hidden),
else on the mesh, `Jarvis.convexHull`, clip against the viewport and the HUD widget bounds,
area fraction → Sightline. A few hundred vertices per Hop is well under the overlay budget
(`docs/RUNELITE-RULES.md:22`).

**Not chosen:** AABB-only (flips both thresholds on the altars); footprint box with a guessed
height (wrong by a factor); waiting for the object to be in scene (never true for the Blood
Altar Hop until Far Bind holds, which is the moment the verdict is wanted).

## What only in-game testing can settle

1. `useBoundingBox()` for each of the five targets (expected false for all; if true for the
   rocks, the AABB hull *is* the clickbox and the mesh step is skipped for them).
2. Blood Altar `getSceneMinLocation()` vs `getWorldLocation()` vs `getLocalLocation()`; decides
   the SW-tile convention and whether the Far Bind constant moves.
3. Cache mesh vs scene mesh: compare `loadModelData(30835)` bounds with the live
   `getAABB(0)` for the altar, and `loadModelData(30836)` rotated 270° with the north
   runestone's live AABB (expected: identical extents, x/z swapped for the runestone).
4. The translation trick: with the Camera Pose fixed, project the Blood Altar from
   `DARK_APPROACH` (1718, 3878) while standing on it and compare the hull with
   `bloodAltar.getClickbox()` drawn by `NextClickOverlay`; then step one tile and check the
   translated prediction still matches the live clickbox.
5. Whether the runestone's varbit change re-fires `GameObjectSpawned` (decides cache
   invalidation for the impostor swap).
6. Tile heights along the Path (is the flat-terrain assumption good to within a tile?).

## Sources

1. RuneLite source, commit `ac79ed8bd8926bec7bf172aa291574b4d944b0e7` (2026-09-03),
   https://github.com/runelite/runelite — `runelite-api/src/main/java/net/runelite/api/`:
   `Model.java`, `Mesh.java`, `AABB.java`, `GameObject.java`, `GroundObject.java`,
   `TileObject.java`, `Renderable.java`, `DynamicObject.java`, `ModelData.java`,
   `ObjectComposition.java`, `Client.java` (`getObjectDefinition` :967, `loadModelData` :1117,
   `loadModel` :1134, `getCameraFocalPointX/Y/Z` :1739), `gameval/ObjectID.java`
   (:29898-29908, :35539-35543, :86053-86093).
2. Same commit, `runelite-api/src/main/java/net/runelite/api/Perspective.java`:
   `modelToCanvas` :292-316, `modelToCanvasGpu` :377, `modelToCanvasCpu` :442-507,
   `getTileHeight` :628-651, `getFootprintTileHeight` :654-700, `getClickbox` :973-1015,
   `calculateAABB` :1017-1059, `calculate2DBounds` :1061-1131; `model/Jarvis.java` :51, :79.
3. Same commit, `runelite-client/src/main/java/net/runelite/client/`:
   `ui/overlay/outline/ModelOutlineRenderer.java` :523-560, :985-1010;
   `plugins/gpu/SceneUploader.java` :267-268, :351-353, :368-420;
   `plugins/grounditems/Lootbeam.java` :52-75, :134;
   `plugins/objectindicators/ObjectIndicatorsOverlay.java` :146, :173-189;
   `plugins/agility/AgilityPlugin.java` :360-399.
4. RuneLite Maven artifact `net.runelite:cache:1.12.38` from https://repo.runelite.net
   (classes `fs.Container`, `fs.ArchiveFiles`, `index.IndexData`,
   `definitions.loaders.ObjectLoader/ModelLoader/LocationsLoader`), used to decode S5.
5. OpenRS2 Archive, https://archive.openrs2.org/caches.json — cache 2686 (oldschool live,
   build 240, 2026-09-02T10:30Z): archive 255 group 2, archive 2 group 6 (loc definitions),
   archive 7 groups 30835, 30836, 30837, 30838, 30834, 31000, 9235, 9236, 9237 (models);
   cache 2499 (build 236, 2026-03-18T11:45Z): `keys.json` and archive 5 groups 5254 (`l26_59`),
   3979 (`l26_60`), 3190 (`l27_59`), 1432 (`l27_60`).
6. Decoded output of S4 over S5 (tool `Dump.java` and its output, session scratchpad
   `cache/tool`, `cache/rs/model_*.json`): loc definitions (sizes, animation ids, model ids,
   loc types), model vertex bounds, map placements with orientation.
7. OSRS Wiki raw pages, fetched 2026-09-06: *Blood Altar (Kourend)*
   https://oldschool.runescape.wiki/w/Blood_Altar_(Kourend)?action=raw ; *Dark Altar*
   https://oldschool.runescape.wiki/w/Dark_Altar?action=raw ; *Dense runestone*
   https://oldschool.runescape.wiki/w/Dense_runestone?action=raw ; *Dense essence mine*
   https://oldschool.runescape.wiki/w/Dense_essence_mine?action=raw ; *Rocks (dense essence
   mine, north)* https://oldschool.runescape.wiki/w/Rocks_(dense_essence_mine,_north)?action=raw.
   Secondary: Chisel loc dump https://chisel.weirdgloop.org/moid/data_files/objectsmin.js
   (agrees with S6 on every size, model and type; carries no animation field).
8. Simulation scripts `sim.py` and `cut.py` (session scratchpad `cache/tool`), Python
   re-implementation of `modelToCanvasCpu` over the S6 meshes; camera 600 units from the focal
   point is the classic client constant, not verified against the current client [secondary].
9. This repo: `src/main/java/com/vetle/arceuusrc/game/SceneTracker.java`,
   `ArceuusRcArea.java`, `AgilityShortcut.java`, `ArceuusRcHelperPlugin.java` :138-181,
   `docs/RUNELITE-RULES.md` :16-22, `docs/research/blood-altar-far-click.md`, `CONTEXT.md`.
