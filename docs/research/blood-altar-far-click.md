# Blood Altar "clickable from afar" — research

Researched 2026-09-06 against the OSRS Wiki, the RuneLite source (`runelite/runelite` at commit
`ac79ed8bd8926bec7bf172aa291574b4d944b0e7`, 2026-09-03) and the RuneLite release blog.
Evidence grades used throughout: **[primary]** = wiki page/revision data, RuneLite source or
RuneLite blog read directly; **[secondary]** = forum/reddit/server-emulator reconstruction;
**[inference]** = my own reasoning or simulation, not observed in game.

## Question (verbatim)

> The OSRS Wiki page "Pay-to-play Runecraft training" in the Blood runes section says:
> "Note: Before starting your run, go to the Dense Runestone area, log out and then log back in.
> This step is mandatory to make the Blood Altar clickable from afar."
> Is this still true today (2026-09)? The user cannot reproduce it: when they walk to the
> Dark Altar / Blood Altar it is NOT clickable from afar. If it IS a real thing, what is the game
> mechanic? Could our plugin detect the state?

## TL;DR

- **The note is six months old, single-author and unsourced.** It was added on 2026-03-09 by wiki
  user Magneto515 in one edit that created the "Maximising AFK Time" subsection [primary, S1–S3].
  Nobody has discussed, challenged or corroborated it on the talk page, reddit, or RuneLite's
  issue tracker as far as web search reaches [primary for the talk page, S4; searches S12–S14
  returned nothing]. No Jagex update since then touches the Blood Altar, Dark Altar, or scene
  loading [primary, S5–S7]. Confidence that it is a *documented, reproducible* mechanic: **low**.
- **There is a real mechanic underneath it, and the maths lines up.** The Blood Altar
  (1717, 3829) is 53 tiles from the Dark Altar (1718, 3882) and sits three tiles south of the
  scene edge you get when the scene is rebuilt with the player at the Dark Altar (base Y = 3832).
  It is only inside the 104x104 scene if the last rebuild fired while the player was still in
  chunk row 484 (y ≤ 3879). Where that rebuild fires is path-dependent, and a relog at the Mine
  moves the trigger line eight tiles east along the walk-in [inference, from the 16-tile rebuild
  rule S8–S9 and scene constants S10]. Confidence that this is the mechanism: **medium**.
- **The user's failure to reproduce is consistent with the mechanism**: it hinges on the exact
  walking line from the north Shortcut to the Dark Altar, not just on the relog [inference].
- **Yes, the plugin can show "altar in scene: yes/no" cheaply**, hub-clean, with two lines in
  `StatusOverlay` and one helper in `SceneTracker`, because `WorldPoint.isInScene(WorldView, x, y)`
  tests exactly the base 104x104 scene and the plugin already resets on `LOADING` and rescans on
  `LOGGED_IN` [primary, S10–S11; repo pointers below]. Confidence: **high** for the API; the
  correlation "in scene" = "clickable" needs one in-game check.

## Evidence: is it still a thing?

### The wiki text and its provenance [primary]

The full subsection, from the raw wikitext of the page (lines 845–855 as of 2026-09-06) [S1]:

> === Maximising AFK Time ===
> Chipping the dense stones can give you in the best case an AFK time of ~1 minute. This method
> can be made more "AFK" by removing the need for rotating your screen, and enabling a "one-click"
> method from the Dark Altar to the Blood Altar. In order to do that, please follow the steps below:
> * Mark all the relevant clickable objects during your run, such as: Dark Altar, Blood Altar and
>   climb-able rocks (see image on the right for reference). You will need the "Object Markers"
>   plugin for that.
> * In the Camera Plugin, expand the outer zoom limit to 399. In addition, activate the GPU plugin.
> * Change your client layout to "Resizable", zoom out as much as you can, and rotate the camera
>   as can be seen in the reference image. This way, all clickable objects will be visible at all
>   times.
> * Start your run as usual, and once you've used your second inventory of dense essence blocks on
>   the dark altar, click on tile "A" in the second image. Once you've moved to that tile, you
>   should see the clickable outline of the Blood Altar visible at the top of your screen.
> ** '''Note:''' Before starting your run, go to the Dense Runestone area, log out and then log
>    back in. This step is '''<u>mandatory</u>''' to make the Blood Altar clickable from afar.
> [[File:Tile to allow clickable blood altar.png|thumb|...|Click on tile A to make the Blood Altar
> Clickable from Afar]]

Provenance from the MediaWiki API [S2, S3]:

| What | Value |
|---|---|
| Revision that added the subsection | revid 15145644, parent 15141875 |
| Timestamp | 2026-03-09T19:10:24Z |
| User | Magneto515 |
| Edit summary | "Added a section on how to maximize AFK time for Zeah RC for Blood Runes. It removes the need to rotate your screen, reduces number of clicks and makes it more AFK overall." |
| Image "Tile to allow clickable blood altar.png" | uploaded 2026-03-09T19:06:49Z by the same user, 1510x843 |

Later edits to the page (23 Aug 2026 lantern/blisterwood; 24 Aug sailing; 1 Sep Ourania seal)
did not touch the subsection [S2]. The subsection cites no source and no patch note.

The image "Tile to allow clickable blood altar.png" shows the player standing on a tile marked
"A" a few tiles from the Dark Altar (marked with a yellow Object Markers outline next to the
player), the camera pitched flat and zoomed fully out, and a yellow-outlined "Clickable Altar!"
at the very top edge of the viewport with the menu tooltip "Bind Blood Altar / 2 more options"
[primary, viewed S3]. So the claim is specifically: standing next to the Dark Altar, the Blood
Altar 53 tiles away is rendered and its "Bind" option is on the menu.

### What is not there [primary]

- **Talk page**: 31 sections, none about the Blood Altar, logging out, or clicking from afar [S4].
- **Blood Altar (Kourend) page** (object id 27978, map 1717,3829): no note about logging out or
  clicking from afar [S5]. (The plain `Blood_Altar` page is the *true* Blood Altar from Guardians
  of the Rift, id 43479, at 3231,4831 — a different object. Do not confuse the two when reading
  the wiki.) [S5, S5b]
- **Dark Altar page** (id 27979, map 1716,3883): nothing relevant; last change 10 Jan 2024
  (favour removal) [S6].
- **Dense runestone page**: nothing relevant; last mechanical change 18 Oct 2018 (click box)
  [S7]. Runestone locations listed as 1762,3856 and 1762,3844 [S7].
- **Jagex news**: a domain-restricted search of secure.runescape.com for 2026 Blood/Dark Altar or
  Arceuus updates returned only the 2015 Blood & Soul Altars dev blog [S12].
- **reddit r/2007scape / RuneLite GitHub**: four searches ("blood altar log out", "arceuus log
  out trick", "blood altar not clickable", "click from far") returned no matching threads
  [S13, S14]. Direct reddit fetches are blocked from this environment, so absence here is weak
  evidence.

### Related, older wiki wording [primary]

Line 773 of the same page (Soul runes subsection, older) already says: "Using the increased draw
distance of approved, non-standard clients such as RuneLite, it is possible to one-click from the
cave entrance to the altar" [S1]. The plugin's own `ArceuusRcArea.SOUL_APPROACH` comment says
"Last tile is where the altar typically comes into the loaded scene"
(`src/main/java/com/vetle/arceuusrc/ArceuusRcArea.java:30-33`). So the *scene boundary* effect
on the Soul Altar approach is already known to both the wiki and this codebase; the March 2026
note is the first to claim the Blood Altar can be brought into scene at the Dark Altar, and the
first to tie it to a relog.

## Mechanic

### The scene model [primary]

- The client renders and lets you click only what is in the loaded scene. The scene is
  `SCENE_SIZE = 104` tiles square, "13 chunks x 8 tiles"; a chunk is 8x8 and a map region is
  64x64 (`runelite-api/src/main/java/net/runelite/api/Constants.java:60-73`) [S10].
- `WorldView.getBaseX()/getBaseY()` are "the world coordinate of tile (0, 0) in the current
  scene (ie. the bottom-left most coordinates in the scene)"
  (`runelite-api/.../WorldView.java:126-144`) [S10].
- `WorldPoint.isInScene(WorldView wv, int x, int y)` returns
  `x >= baseX && x < baseX + wv.getSizeX() && y >= baseY && y < baseY + wv.getSizeY()`
  (`runelite-api/.../coords/WorldPoint.java:147-156`), and `LocalPoint.fromWorld(...)` returns
  `null` when that fails (`LocalPoint.java:103-110`) [S10]. `Scene.getTiles()` is documented as
  "a 4x104x104 array" (`Scene.java:32-37`) [S10].
- Scene rebuilds surface to plugins as `GameStateChanged` with `GameState.LOADING` ("The game
  is being loaded", `GameState.java:55-58`); the GPU plugin forces one by
  `client.setGameState(GameState.LOADING)` when its map-loading config changes
  (`runelite-client/.../gpu/GpuPlugin.java:509-516`) [S10].

### When the server rebuilds the scene

- **[secondary]** Rune-Server thread on client/server map-region communication: "The server
  actually sends it when the player approaches the edge of the 104x104 region that is loaded -
  when it is within 16 tiles horizontally or vertically of an edge. It knows to do this as it
  knows the player's position, and it also knows what chunk is at the center 7,7 as it sent this
  previously (e.g. upon login or the previous time the player got close to an edge.)" [S8].
- **[primary, corroborating]** RuneLite 1.10.12 release blog, on extended map loading: "The gap
  before new map areas load has been increased from 16 tiles to 56 tiles" [S9]. 56 = 16 + 40, and
  40 is `(EXTENDED_SCENE_SIZE - SCENE_SIZE) / 2` with `EXTENDED_SCENE_SIZE = 184`
  (`Constants.java:75-79`) [S10]. RuneLite cannot change the server's rebuild trigger, so this
  wording confirms the 16-tile figure from the RuneLite maintainers' side.
- **[inference]** On a rebuild the new scene is centred on the player's chunk: base =
  ((tile >> 3) - 6) * 8 on each axis, giving the player's chunk index 6 of 0..12. This is the
  standard reading of "centre chunk (7,7)" in S8 counting from 1, and of `SCENE_SIZE = 104 = 13
  chunks`. Not verified against the game client itself (RuneLite's public repo does not contain
  the packet handler).

Together: **scene placement is path-dependent.** The rebuild fires when the player's scene-local
coordinate drops below 16 or reaches 88, and the *new* base is centred on wherever the player
happens to be at that moment. Two players standing on the same tile can have scenes offset by
one or more chunks depending on where their last rebuild fired, and that chains back to where
they logged in.

### The chunk maths for this Rotation [primary coordinates, inference on placement]

Coordinates: Blood Altar 1717,3829 (wiki map [S5]; identical in `ArceuusRcArea.java:20`);
Dark Altar 1718,3882 (`ArceuusRcArea.java:19`; wiki 1716,3883 [S6]); Mine stand tile 1762,3854
(`ArceuusRcArea.java:18`; wiki runestones 1762,3856 and 1762,3844 [S7]).

| Place | Chunk (x>>3, y>>3) | Region id | Chebyshev distance to Blood Altar |
|---|---|---|---|
| Blood Altar (1717,3829) | (214, 478) | 6715 | 0 |
| Dark Altar (1718,3882) | (214, 485) | 6716 | 53 |
| Mine (1762,3854) | (220, 481) | 6972 | 45 |

Both distances are under the 104-tile scene width, so the Blood Altar *can* share a scene with
either place; what matters is where the base lands.

- Scene built with the player at the **Dark Altar** (chunk row 485): base Y = (485-6)*8 =
  **3832**. Blood Altar y = 3829 is **3 tiles outside**. Not clickable.
- Scene built with the player anywhere in chunk row **484** (y 3872..3879): base Y = **3824**.
  Blood Altar (3829) inside; Dark Altar (3882) at local y = 58, so standing at the Dark Altar
  does not trigger another rebuild. **Clickable** (if rendered).
- Scene built at the **Mine** (login there): base = (1712, 3800). Blood Altar inside; but the
  Dark Altar at x = 1718 is local x = 6, so walking to it forces a rebuild, and the outcome
  depends on the player's y at the moment local x drops below 16, i.e. when x < 1728.
- If the player instead **arrived at the Mine from the Blood Altar** through the 73 Shortcut
  without relogging, the last rebuild on that walk gives base (1704, 3800); the next rebuild then
  fires at x < 1720, eight tiles closer to the Dark Altar, where the walk-in from the north
  Shortcut is more likely to have already climbed to y ≥ 3880.

I simulated the 16/88 rule along the plugin's own waypoints (`ArceuusRcArea.BLOOD_APPROACH`,
`NORTH_SHORTCUT`, `MINE_STAND`, `SHORTCUT`) with straight-line interpolation between them
(script and output in the session scratchpad, `sim.py`) [inference]:

- Login at the Mine, walk north Shortcut → Dark Altar: rebuild fired at (1727, 3882) on the
  straight-line path → base (1672, 3832) → Blood Altar **out** of scene at the Dark Altar.
- Arrive from the Blood Altar via the 73 Shortcut, then the same walk: rebuild at (1719, 3882)
  → base (1664, 3832) → **out**.
- Login at the Dark Altar: **out**.
- In every scenario the Blood Altar comes **into** scene on the standard west-around-the-ridge
  Path at roughly (1680, 3879) (first rebuild) and stays in through the second rebuild near
  (1712..1720, 3856). That matches the wiki's older "1705:3866, Click here" map tip on line 858
  [S1] and the user's experience that the altar is clickable only once they are well along the
  Path.

The straight-line interpolation is the weak link: the real walking line from (1760, 3873) to
(1718, 3882) is decided by the server's pathfinder and the terrain. If the player crosses
x = 1727 (relogged at Mine) while still at y ≤ 3879, base becomes (1672, 3824) and the trick
works; if they cross at y ≥ 3880 it does not. With the non-relog base the crossing is at x = 1719,
almost at the altar, where y ≥ 3880 is much more likely. **That is a coherent reason the relog
matters and a coherent reason it is fragile enough that the user cannot reproduce it.** Only
in-game measurement of `client.getBaseX()/getBaseY()` at the Dark Altar settles it.

### Rendering, draw distance and the extended scene

- Even when in scene, 53 tiles exceeds the GPU plugin's default draw distance of 50
  (`GpuPluginConfig.java:42-54`, max `MAX_DISTANCE = 184`, `GpuPlugin.java:102`) [S10]; the
  non-GPU renderer draws less. This is why the wiki step says "activate the GPU plugin" and zoom
  fully out [S1]. Draw distance must be raised to ≥ 53 for the altar to appear at all.
- RuneLite's extended map loading (GPU plugin, "Extended map loading", default 3 chunks, max 5;
  `GpuPluginConfig.java:67-80`) renders up to 40 extra tiles per side, so the Blood Altar 3 tiles
  outside the base scene *is drawn* with defaults [S9, S10]. Extended tiles do carry
  `GameObject`s (the Interact Highlight plugin looks interacted objects up in
  `scene.getExtendedTiles()`, `InteractHighlightPlugin.java:292-308`; Roof Removal walks all
  184x184 extended tiles, `RoofRemovalPlugin.java:276-291`) [S10].
- Whether objects in the extended-only band are **clickable** is not stated anywhere I found
  [S9 says nothing; S14]. The wiki author had GPU on and still needed the relog, which only makes
  sense if extended-band objects are not interactable [inference]. If they were, the relog would
  be unnecessary and the note would be wrong for a different reason. Either way the
  base-104 test below is the conservative one.

## Can the plugin detect it?

**Yes.** The plugin already has every input, and hub rules are satisfied.

### What exists today (repo pointers)

- `SceneTracker` keeps `bloodAltar` from spawn/despawn events and a one-shot scan
  (`src/main/java/com/vetle/arceuusrc/SceneTracker.java:38-39, 73-103, 105-147, 320-322`);
  `scanScene()` iterates `scene.getTiles()`, which is the base 104x104 array [S10].
- The plugin resets the tracker on `GameState.LOADING` and rescans on `LOGGED_IN`
  (`src/main/java/com/vetle/arceuusrc/ArceuusRcHelperPlugin.java:185-194`), which is exactly the
  scene-rebuild lifecycle.
- `ArceuusRcArea.BLOOD_ALTAR = (1717, 3829)` (`ArceuusRcArea.java:20`) is the world point to
  test.
- The Status Panel is built in `StatusOverlay.render`
  (`src/main/java/com/vetle/arceuusrc/overlay/StatusOverlay.java:55-108`) from `LineComponent`s;
  adding a line is one `panelComponent.getChildren().add(line(...))` call.
- `NextClickOverlay` already uses `LocalPoint.fromWorld(worldView, tile)` and
  `object.getClickbox()` (`overlay/NextClickOverlay.java:125, 187, 206`), so the null-on-out-of-
  scene idiom is already in the codebase.

### Sketch

Add to `SceneTracker`:

```java
/** True when the Blood Altar's tile lies inside the base 104x104 scene (not the extended band). */
public boolean isBloodAltarInScene()
{
	WorldView wv = client.getTopLevelWorldView();
	if (wv == null)
	{
		return false;
	}
	WorldPoint p = ArceuusRcArea.BLOOD_ALTAR;
	return WorldPoint.isInScene(wv, p.getX(), p.getY());
}
```

`WorldPoint.isInScene(WorldView, x, y)` uses `wv.getSizeX()`, which is the base scene size
(Perspective adds the extended offset separately, `Perspective.java:763-768`) [S10], so this
answers "in the clickable scene" rather than "somewhere in the 184x184 render". A stricter variant
is `bloodAltar != null && WorldPoint.isInScene(wv, bloodAltar.getWorldLocation()...)`, which also
requires the object to have spawned; use it if in-game testing shows `GameObjectSpawned` firing
for extended-band objects (unknown; the event is raised from RuneLite's injected client, which is
not in the public repo).

Then in `StatusOverlay.render`, next to the "Next" line, when the Rune is Blood and the Step is
between Venerate and At Altar:

```java
boolean loaded = sceneTracker.isBloodAltarInScene();
panelComponent.getChildren().add(line("Altar", loaded ? "in scene" : "not loaded", loaded ? LABEL : WARN));
```

Optionally show `client.getBaseX()`/`getBaseY()` in a debug line while testing; that is what
proves or disproves the path-dependence above.

### Hub-rule check (`docs/RUNELITE-RULES.md`)

- Performance: no new per-frame scans; `isInScene` is four integer compares against values the
  client already holds, and object tracking stays event-driven (rule "Track objects via
  spawn/despawn events", RUNELITE-RULES.md:21) — compliant.
- Threading/network/files: none touched.
- Menus, input, interfaces: nothing changed; the plugin only reports, it does not make the altar
  clickable or click it — compliant with the Input and Menus sections and with the "never acts
  for the player" stance in `CONTEXT.md`.
- Config: if a toggle is added, it is a new key, no migration needed.
- The rule "Use `net.runelite.api.gameval` constants" is already followed:
  `ObjectID.ARCHEUS_ALTAR_BLOOD = 27978`, `ARCHEUS_ALTAR_DARK = 27979`,
  `ARCHEUS_ALTAR_SOUL = 27980` (`runelite-api/.../gameval/ObjectID.java:86053-86063`),
  `ARCEUUS_ALTAR = 28455` (`:88294`) [S10].

## Open questions / what only in-game testing can settle

1. **Base coordinates at the Dark Altar.** Log `client.getBaseX()/getBaseY()` on every
   `LOGGED_IN` after a `LOADING`. Prediction: base Y = 3832 when the altar is not clickable,
   3824 when it is. This single number confirms or kills the mechanism.
2. **Does the relog move the trigger?** Do two runs: (a) relog at the Mine, then north Shortcut →
   Dark Altar; (b) arrive at the Mine from the Blood Altar via the 73 Shortcut, no relog, same
   walk. Record base Y at the Dark Altar for each and the tile where `LOADING` fired. Then try the
   walk-in hugging the south side (y ≤ 3879) until x < 1728.
3. **Are extended-band objects clickable?** With GPU draw distance ≥ 60 and Extended map loading
   ≥ 1, stand at the Dark Altar with base Y = 3832 and hover the Blood Altar. If "Bind" appears,
   the base-104 test is too conservative and the relog note is superfluous rather than wrong.
4. **Does `GameObjectSpawned` fire for extended-band objects?** Log spawns of
   `ARCHEUS_ALTAR_BLOOD` alongside base coordinates; decides which variant of
   `isBloodAltarInScene` to keep.
5. **Multi-tile footprint.** The wiki map marker uses `r=4`; the altar's exact `sizeX/sizeY`
   (`GameObject.sizeX()`, `GameObject.java:42-49`) was not checked. If the object's SW tile is
   not (1717, 3829), shift the test point to the tile the plugin actually gets from
   `bloodAltar.getWorldLocation()`.
6. **Runestone coordinates.** The wiki lists runestones at 1762,3856 and 1762,3844 [S7]; the
   plugin has 1761,3853 and 1761,3873 (`ArceuusRcArea.java:14-16`). The north one differs by
   29 tiles. Unrelated to this question but worth a look next time someone is at the Mine.

## Sources

1. OSRS Wiki, *Pay-to-play Runecraft training*, raw wikitext lines 773, 845–858
   (fetched 2026-09-06): https://oldschool.runescape.wiki/w/Pay-to-play_Runecraft_training?action=raw
   — rendered: https://oldschool.runescape.wiki/w/Pay-to-play_Runecraft_training
2. OSRS Wiki page history (18 Mar 2026 – 1 Sep 2026 window) and MediaWiki API revision query for
   user Magneto515 (revid 15145644, 2026-03-09T19:10:24Z):
   https://oldschool.runescape.wiki/w/Pay-to-play_Runecraft_training?action=history and
   https://oldschool.runescape.wiki/api.php?action=query&prop=revisions&titles=Pay-to-play_Runecraft_training&rvuser=Magneto515&rvprop=ids|timestamp|user|comment|size&format=json
3. OSRS Wiki file *Tile to allow clickable blood altar.png* (uploaded 2026-03-09T19:06:49Z by
   Magneto515) and *Zeah afk setup.png*:
   https://oldschool.runescape.wiki/w/File:Tile_to_allow_clickable_blood_altar.png
4. OSRS Wiki, *Talk:Pay-to-play Runecraft training*:
   https://oldschool.runescape.wiki/w/Talk:Pay-to-play_Runecraft_training
5. OSRS Wiki, *Blood Altar (Kourend)* (id 27978, map 1717,3829):
   https://oldschool.runescape.wiki/w/Blood_Altar_(Kourend)?action=raw
   5b. OSRS Wiki, *Blood Altar* (true altar, id 43479): https://oldschool.runescape.wiki/w/Blood_Altar
6. OSRS Wiki, *Dark Altar* (id 27979, map 1716,3883): https://oldschool.runescape.wiki/w/Dark_Altar
7. OSRS Wiki, *Dense runestone* (locations 1762,3856 / 1762,3844; changes list):
   https://oldschool.runescape.wiki/w/Dense_runestone
   and *Dense essence mine*: https://oldschool.runescape.wiki/w/Arceuus_essence_mine
8. Rune-Server, *How the server & client communicate about map regions/tiles/etc* (secondary;
   16-tiles-from-edge rebuild rule, centre chunk):
   https://rune-server.org/threads/how-the-server-client-communicate-about-map-regions-tiles-etc.417879/
9. RuneLite blog, *1.10.12 Release* (2023-09-09): extended map loading, "up to 184x184 tiles",
   "gap before new map areas load has been increased from 16 tiles to 56 tiles":
   https://runelite.net/blog/show/2023-09-09-1.10.12-Release/
10. RuneLite source, commit ac79ed8bd8926bec7bf172aa291574b4d944b0e7 (2026-09-03),
    https://github.com/runelite/runelite — files cited by path:line above:
    `runelite-api/src/main/java/net/runelite/api/Constants.java`,
    `.../WorldView.java`, `.../Scene.java`, `.../GameState.java`, `.../GameObject.java`,
    `.../Perspective.java`, `.../coords/WorldPoint.java`, `.../coords/LocalPoint.java`,
    `.../gameval/ObjectID.java`,
    `runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java`,
    `.../gpu/GpuPluginConfig.java`,
    `.../interacthighlight/InteractHighlightPlugin.java`,
    `.../roofremoval/RoofRemovalPlugin.java`,
    `.../objectindicators/ObjectIndicatorsPlugin.java`.
11. This repo: `src/main/java/com/vetle/arceuusrc/SceneTracker.java`,
    `ArceuusRcArea.java`, `ArceuusRcHelperPlugin.java`, `overlay/StatusOverlay.java`,
    `overlay/NextClickOverlay.java`, `docs/RUNELITE-RULES.md`, `CONTEXT.md`.
12. Web search, secure.runescape.com restricted, "Blood Altar / Dark Altar / Arceuus 2026": only
    the 2015 dev blog https://secure.runescape.com/m=forum/sl=0/forums?380,381,239,65706092
13. Web searches for reddit/RuneLite GitHub ("blood altar log out", "blood altar not clickable",
    "arceuus log out trick", "blood altar click from far"): no matching results; direct
    old.reddit.com fetch blocked from this environment.
14. Web search "runelite extended map loading objects click/interact": only S9 and the 1.10.15
    blog https://runelite.net/blog/show/2023-11-05-1.10.15-Release/ ; neither addresses
    interaction in the extended band.

## Results (in-game, 2026-09-06)

Measured with a throwaway prototype (branch `proto/altar-loaded`, commit `3b26a97`) that put the
scene base, the in-scene flag, the spawn-tracked flag and a `MenuEntryAdded` "Bind offered" latch
on the Status Panel and logged every scene rebuild. Client settings: GPU on, draw distance 65,
extended map loading 3. The "Bind offered" line matched what could actually be clicked every time.

### Scene rebuilds

Every rebuild observed (seven in two full Rotations plus a relog at the Dark Altar) matched
`base = ((tile >> 3) - 6) * 8` per axis, triggered when the player came within 16 tiles of a
scene edge.

| event | fired at (player) | new base | altar local | in scene | Bind offered |
|---|---|---|---|---|---|
| login at Mine | – | 1712,3800 | 5,29 | yes | yes at 1762,3855 (dist 45) and 1758,3851 (dist 41) |
| rebuild walking to Dark Altar | 1727,3879 | 1672,3824 | 45,5 | yes | no while at the Dark Altar (1723,3879), both rounds |
| logout+login at Dark Altar | – | 1664,3832 | 53,-3 | no | no |
| rebuild walking Dark Altar → Mine | 1751,3873 | 1704,3824 | 13,5 | yes | yes at 1761,3853 (dist 44) |
| rebuild on the west ridge | 1687,3880 | 1632,3832 | 85,-3 | no | no |
| rebuild turning south past the ridge | 1721,3856 | 1672,3808 | 45,21 | yes | yes at 1723,3856 (dist 27) |

Answers to the open questions above:

1. Base Y at the Dark Altar is 3824 after a relog at the Mine (altar inside), 3832 after a login
   at the Dark Altar (altar outside).
2. The relog moves the trigger: relog at the Mine gives base X 1712, so the walk-in rebuild fires
   at x = 1727 (y 3879); arriving from the Blood Altar without relog gives base X 1704, so the
   next trigger is x < 1720, right at the Dark Altar, and the altar drops out.
3. Extended-band objects are not clickable: no Bind while the altar sat at local y −3.
4. `GameObjectSpawned` never fired for extended-band objects; the spawn-tracked flag and the
   coordinate flag agreed at every rebuild. The coordinate test alone is enough.
5. The Blood Altar object's SW tile is (1716, 3829), size 4x4 (x 1716..1719, y 3829..3832).
   `ArceuusRcArea.BLOOD_ALTAR` (1717, 3829) lies inside it.

### In scene is necessary but not sufficient

At the Dark Altar the altar was inside the base scene, spawn-tracked, drawn and had a clickbox,
and the client still never offered "Bind". Ruled out by direct test: camera-tile distance, zoom,
pitch, GPU draw distance (65 and 90), ridge occlusion. Stationary hover samples with the altar
inside the scene:

| player tile | Chebyshev to SW tile 1717,3829 | Chebyshev to nearest footprint tile | Bind |
|---|---|---|---|
| 1765,3855 | 48 | 46 | yes |
| 1718,3878 | 49 | 46 | yes |
| 1723,3878 | 49 | 46 | yes |
| 1766,3855 | 49 | 47 | no |
| 1723,3879 | 50 | 47 | no |
| 1718,3882 | 53 | 50 | no |

Rule that fits every sample: the client offers an object's menu only when the player is within
46 tiles (Chebyshev) of the nearest tile of the object's footprint. Where that constant lives in
the client was not found; treat it as measured, not derived.

**Far Bind** (see `CONTEXT.md`) = altar inside the base scene AND player within 46 tiles of the
footprint.

### Wiki verdict: true but under-specified

The relog trick works, but needs two things the wiki only half states: the last rebuild before
the Dark Altar must fire at y ≤ 3879 (a relog anywhere in chunk x 1760..1767, y 3848..3855 does
that), and the player must stand at y ≤ 3878, four tiles south of the Dark Altar. From the Dark
Altar tile itself (footprint distance 50) it never works. Earlier non-reproduction was at y 3879
with correct placement: one tile short.

### No relog needed

Confirmed over two consecutive Trips without any relog: reaching (1718, 3878) before touching
the Dark Altar makes the rebuild fire there (base 1664, 3824, altar inside at local 53,5).
Venerating four tiles north causes no rebuild, and stepping back to y ≤ 3878 offers Bind. The
plugin therefore routes the Path to the Dark Altar through (1718, 3878) instead of asking for a
relog.

### Not settled

- Runestone coordinates: plugin has (1761, 3853) and (1761, 3873); wiki lists (1762, 3856) and
  (1762, 3844). Unrelated to Far Bind, unchecked.
- Whether the Soul Altar has the same problem. Not researched; Far Bind is Blood only.
