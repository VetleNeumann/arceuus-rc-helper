# Changelog

All notable changes to this plugin. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Far Bind: the Status Panel says whether the Blood Altar can be clicked from where you stand, highlights the Far Bind Area when you are just outside it, and the Path to the Dark Altar passes the tile that keeps the altar loaded. New toggle "Far Bind" under Helper, on by default.

### Changed

- Forked from JamsRepos/zeah-rc-helper and renamed to Arceuus RC Helper: package `com.vetle.arceuusrc`, config group `arceuus-rc-helper`, the gear reminder key is now `gearReminder`. Settings reset once on upgrade.
- Release notes live in this file and on GitHub Releases instead of in game chat.
- Trips keep counting while the Helper is switched off; only the Next Action is suppressed.
- The Status Panel lists every active Reminder as a warning line: gear, lantern, Blood Essence (missing, inactive or low) and Idle. The Essence row itself now shows plain state (charges, active, inactive, none) without colouring it as a warning.

### Fixed

- The second Batch at the crafting altar shows "click the altar again for the second batch" instead of repeating the first-batch instruction. The Step after chiselling at the altar was never inferred, so a Trip only counted when the player walked back with an empty inventory.

### Removed

- In-game changelog announcement and the migration for the pre-1.0.4 path checkboxes.

### Internal

- Checkstyle with RuneLite's style rules plus Plugin Hub forbidden-API checks; the build fails on violations.
- CI on every push and PR, tag-driven GitHub Releases, a weekly build against RuneLite snapshots, Dependabot.
- The version is read from `runelite-plugin.properties` only.
- Logic modules take a per-tick `Observation` value instead of reading the RuneLite `Client` (ADR-0005); Step inference and Trip counting live in `Rotation` with a table of unit tests.
- `Helper` decides once per tick what is drawn (`Guidance`: Next Action, Reminders, highlight, Path Display, Status Panel, Idle Tint); overlays read that value and no longer read config. `HelperAction` renamed `NextAction`.
- `FragmentTracker` estimates the hidden Fragment stack from a `RawInventory` value read by `InventoryReader`; the estimate is unit-tested as a sequence of reads. Replaces `InventoryChecker`.
- `Reminders` returns typed `Reminder` values from one `evaluate` call that takes the clock; all four kinds and the idle timer are unit-tested. Replaces `ReminderService`.
- Tracked dev loop (`dev.sh`, `tools/dev.ps1`), glossary (`CONTEXT.md`), standards and ADRs under `docs/`.

## [1.0.4] - 2026-09-05

### Changed

- The two path checkboxes are now one Path display dropdown: floor & minimap, floor only, minimap only, or off.
- Shortest Path uses walkable stand tiles and no longer restarts every few ticks.

### Added

- Path source dropdown so the Shortest Path plugin can draw the route instead, coloured by the current step.

## [1.0.3] - 2026-09-01

### Changed

- Renamed from Zeah RC Helper to Jam's Arceuus Runecrafting in the plugin panel and Hub.

## [1.0.2] - 2026-08-23

### Changed

- Status panel is one compact overlay with labeled Dense, Dark, Fragments, Trips, and Essence.
- Gear reminders appear in that panel instead of a second overlay.
- Bloods and Souls use their rune colours on the method name and altar path.
- Exact shortcut hops in the path.

### Added

- Update notes shown in chat after an upgrade.

## [1.0.1] - 2026-08-21

### Fixed

- Blood altar pathing, essence charge reading, and the jeweller's chisel counting as a chisel.

## [1.0.0] - 2026-08-20

### Added

- Initial release: pathfinding with agility shortcuts, scene tracking, runestone highlights, rotation guidance, gear and idle reminders.

[Unreleased]: https://github.com/VetleNeumann/arceuus-rc-helper/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/VetleNeumann/arceuus-rc-helper/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/VetleNeumann/arceuus-rc-helper/releases/tag/v1.0.0
