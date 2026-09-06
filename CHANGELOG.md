# Changelog

All notable changes to this plugin. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Forked from JamsRepos/zeah-rc-helper and renamed to Arceuus RC Helper: package `com.vetle.arceuusrc`, config group `arceuus-rc-helper`, the gear reminder key is now `gearReminder`. Settings reset once on upgrade.
- Release notes live in this file and on GitHub Releases instead of in game chat.
- Trips keep counting while the Helper is switched off; only the Next Action is suppressed.

### Removed

- In-game changelog announcement and the migration for the pre-1.0.4 path checkboxes.

### Internal

- Checkstyle with RuneLite's style rules plus Plugin Hub forbidden-API checks; the build fails on violations.
- CI on every push and PR, tag-driven GitHub Releases, a weekly build against RuneLite snapshots, Dependabot.
- The version is read from `runelite-plugin.properties` only.
- Logic modules take a per-tick `Observation` value instead of reading the RuneLite `Client` (ADR-0005); Step inference and Trip counting live in `Rotation` with a table of unit tests.
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
