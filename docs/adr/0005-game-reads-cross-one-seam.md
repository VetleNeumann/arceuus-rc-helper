# Game reads cross one seam: logic modules take an Observation, never the Client

Before this decision six modules each injected the RuneLite `Client` and fetched the player tile, skill levels, animation state or tick count on their own. Step inference, Trip counting and the Idle Reminder therefore could not be tested without mocking RuneLite types, and the same fact (the player's tile) was read five times per tick.

Now `ClientObserver` in the `game` package reads the client once per tick into an `Observation` value (area membership, resolved Rune, Position, inventory, agility, animation flags, tick, scene handle). Everything in the root package takes that value. The `game` package is the seam: it holds the adapters that touch `Client` and `EventBus` (`ClientObserver`, `SceneTracker`, `InventoryReader`, `ShortestPathBridge`); the root package holds logic that takes values; `overlay` draws values.

## Consequences

- A `net.runelite.api.Client` import in the root package fails the build: checkstyle's `ImportControl` (`config/checkstyle/import-control.xml`) allows it only under `game` and `overlay`.
- Tests for logic modules construct an `Observation` by hand; there is no Mockito on RuneLite types outside the `game` package.
- Scene-object tracking and varbit reads stay in `SceneTracker`; they are scene state, not per-tick player state, and are queried with a `WorldPoint` argument rather than reading the player themselves.
- `WorldView` rides along inside the Observation because pathfinding needs the collision map. It is the only non-value field and only `RcPathRouter` reads it.
