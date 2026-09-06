# RuneLite Plugin Hub rules

Constraints the Plugin Hub enforces at review time. This fork is unpublished, but stays hub-clean so publishing remains a manifest PR away. Checkstyle catches the mechanical ones (reflection, `Thread.sleep`, external processes, `java.awt.Desktop`, `HttpURLConnection`); everything else is checked by reading this file at design time.

Sources: [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1), RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features), and the plugin-hub README.

## Language and runtime

- Java 11. `build.gradle` targets it with `options.release.set(11)`.
- Reflection, JNI/JNA, `Unsafe`, LWJGL, `Process`/`ProcessBuilder`, dynamic classloading, runtime code generation and Java (de)serialization are all rejected.

## Threading

- Schedule work; never sleep. Cancel every `ScheduledFuture` in `shutDown()` and call `executor.shutdownNow()` without awaiting termination.
- `startUp()` and `shutDown()` return promptly; nothing blocks in them.
- Blocking network or disk IO runs off the client thread. Use the OkHttp pool for network work and hop back with `clientThread.invoke()` when the result touches `client`.
- Batch async work with `CompletableFuture.allOf()`.

## Performance

- Track objects via spawn/despawn events into your own collections (see `SceneTracker`). Full scene scans happen only on `startUp()` and `GameState.LOGGED_IN`.
- Overlays run every frame: read precomputed state, draw, return.

## RuneLite API

- Use `net.runelite.api.gameval` constants (`ItemID`, `ObjectID`, `VarbitID`, `InterfaceID`) instead of numeric IDs.
- Look up widgets by gameval component ID: `client.getWidget(InterfaceID.Foo.BAR)`.
- Open URLs with `LinkBrowser`.

## HTTP and JSON

- `@Inject OkHttpClient` and `@Inject Gson` (derive with `.newBuilder()` if needed). Use `enqueue()`, never a synchronous call on the client thread.
- Do not declare RuneLite's transitive dependencies (gson, guice, okhttp) in `build.gradle`; they come with `net.runelite:client`.
- Any feature that talks to a third-party server is a `@ConfigItem` that is off by default and carries `warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`.

## Files

- Read and write only under `RuneLite.RUNELITE_DIR`, inside a plugin-specific subdirectory. `JFileChooser` is fine for user-initiated paths.

## Config

- Config group names are specific (`arceuus-rc-helper`).
- Renaming a config key or group needs a migration, otherwise users' settings silently reset. The one exception is recorded in `docs/adr/0002-config-rename-without-migration.md`.

## Packaging

- Match the example-plugin `build.gradle` shape; `runelite-plugin.properties` uses `build=standard`, so the hub builds with its own script and ignores extra Gradle plugins here.
- No `META-INF/services/net.runelite.client.plugins.Plugin` file. No committed build output.
- BSD-2 license. Icon PNGs are real PNGs, small in pixels (Java holds `width × height × 4` bytes in memory).
- Non-RuneLite runtime dependencies need hash verification by hub maintainers; avoid them.

## Cleanup

- Everything added in `startUp()` (overlays, listeners, subscriptions, scheduled tasks) is removed in `shutDown()`.
- Unused config items, fields and imports are deleted, not left behind.

## Feature restrictions

Combat and bosses (all bosses, raid sub-bosses, slayer bosses, demi-bosses, wave minigames):

- No next-attack prediction, projectile landing indicators, prayer-switch indicators, attack counters, automatic stand/avoid tiles, added boss-mechanic cues, advance hazard warnings, flinch timers, combat prayer recommendations, NPC focus identification or content simulation.
- New high-end PvM boss plugins are refused outright.

PvP:

- No removing or deprioritising attack/cast options, freeze timers, clan opponent identification, loot previews, opponent's-opponent identification, target scouting, group summaries, level-range highlighting or spell-targeting simplification.

Menus:

- No new menu entries that send actions to the server; no Construction or Blackjacking menu changes; no conditional removal of entries by NPC type, friend status or similar.

Interfaces:

- No unhiding hidden components (special attack bar, minimap), no moving or resizing click zones for 3D components, combat options, inventory, equipment, prayer book or spellbook; no click-through inventory background; no detached-camera world interaction.

Input:

- No injected mouse or keyboard events, no autotyping or chat input insertion, no rewriting outgoing chat.

Data:

- No exposing player information over HTTP, no crowdsourcing data about other players, no credential storage.

Content:

- No adult content, and no plugins whose whole function depends on player-provided IDs.
