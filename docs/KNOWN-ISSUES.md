# Known issues

Open problems seen in the dev client that have no fix yet. Remove an entry in the PR that fixes it. GitHub issues are disabled on this repository, so this file is the tracker.

## NextClickOverlay throws NPE from `getClickbox()` on a stale object

Seen 2026-09-06 in `dev-client.log`:

```
WARN  n.r.c.ui.overlay.OverlayRenderer - Error during overlay rendering
java.lang.NullPointerException: Cannot read field "oy" because "this.zn" is null
    at com.vetle.arceuusrc.overlay.NextClickOverlay.renderObject(NextClickOverlay.java:206)
    at com.vetle.arceuusrc.overlay.NextClickOverlay.render(NextClickOverlay.java:74)
```

`renderObject` calls `TileObject.getClickbox()` on the highlighted object. The client throws when that object's model is gone, which points at the scene tracker handing the overlay a `TileObject` that has already despawned or whose scene was reloaded. RuneLite swallows the exception and skips the overlay for that frame, so the symptom is a flicker of the next-click outline, not a crash.

Suggested fix: have the scene tracker drop objects on `GameStateChanged` (LOADING) as well as on the despawn events, and guard the clickbox call so a null model falls back to the tile polygon.
