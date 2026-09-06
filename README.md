# Arceuus RC Helper

[![CI](https://github.com/VetleNeumann/arceuus-rc-helper/actions/workflows/ci.yml/badge.svg)](https://github.com/VetleNeumann/arceuus-rc-helper/actions/workflows/ci.yml)

A click-here helper for Arceuus blood and soul runecrafting. It highlights what to click next, draws a path there, and keeps your inventory counts and gear reminders in one small panel.

This plugin conflicts with [Easy Arceuus Runecrafting](https://github.com/poi56iop/easy-arceuus-runecrafting). Only one can be enabled at a time.

## What you see

- **Next click** — the runestone, shortcut, or altar you should click is highlighted.
- **Path** — a line on the floor and/or minimap to where you are going. Shortcuts you qualify for are used automatically. Choose where it draws (or turn it off) under Path display. Optionally hand the destination to the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin under Path source.
- **Status panel** — what to do next, plus:
  - **Dense** — uncharged blocks from the mine
  - **Dark** — blocks after the Dark Altar
  - **Fragments** — chiselled essence ready to craft
  - **Trips** — finished runs this session
  - **Essence** — blood essence status (bloods only)
  - **Far Bind** — whether the Blood Altar can be clicked from where you stand (bloods only). After the second Dark Altar visit, step onto the marked tiles south of it and click the altar once; your character walks the rest. The path to the Dark Altar already passes that spot so the altar stays loaded.
- **Fragment estimate** — the game never numbers the fragment stack, so the plugin draws its own count on the stack in your inventory: yellow while short of a full stack, cyan once it is full, like the essence pouch overlay. Chiselling is deterministic (4 fragments per block), so the count is exact whenever the plugin watched the stack being made; after logging in mid-trip use Count on the stack to sync it.
- **Reminders** — the same panel tells you if you are missing a chisel, pickaxe, lantern, or blood essence, or if you have stood still too long.

Bloods show in red and Souls in teal. Auto uses souls at 90 Runecraft, otherwise bloods.

## The rotation

Same loop for bloods (77 Runecraft) and souls (90). Only the final altar changes.

1. Mine a full inventory of dense essence.
2. Venerate at the Dark Altar.
3. Chisel into fragments while running back to the mine.
4. Mine a second inventory (keep the fragment stack).
5. Venerate the second inventory.
6. Run to the Blood or Soul Altar.
7. Craft the fragments.
8. Chisel the remaining dark blocks.
9. Craft the second batch, then return to the mine.

## What to bring

- A **chisel** (jeweller's chisel is fine) and a **pickaxe**
- An **abyssal lantern** in the shield slot
  - Bloods: blisterwood (best), magic, or redwood
  - Souls: magic or redwood (willow also works). Blisterwood only helps bloods.
- **Blood essence**, activated, if you are doing bloods

## Settings

Everything is on by default except the idle screen tint.

| Setting | What it does |
| --- | --- |
| Rune type | Blood, Soul, or Auto (souls at 90 Runecraft) |
| Enable helper | Turns the highlights, path, and panel on or off |
| Highlight next click | Outline on the next object |
| Path display | Where the path is drawn: floor & minimap, floor only, minimap only, or off |
| Path source | Plugin lines, or hand the destination to the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin coloured by the current step |
| Show status panel | Step, counts, and reminders |
| Far Bind | Bloods only: say when the Blood Altar can be clicked from where you stand, and mark the tiles to step to when you are just outside that range |
| Fragment estimate | Draw the estimated stack size on the fragments in your inventory: yellow while short of a full stack, cyan once it is full |
| Gear reminders | Warn if chisel, pickaxe, or lantern is missing |
| Check lantern logs | Warn if the lantern is unlit or using logs that do not help this method |
| Blood essence reminder | Warn to bring / activate essence, and when charges are low (bloods only) |
| Low essence charges | Charge count that counts as low (default 100) |
| Idle reminder | How long you can stand still before a warning |
| Idle screen tint | Optional faint tint when idle |

The idle reminder never sends a RuneLite notification: no sound, no tray popup, no taskbar flash. Its only output is the panel warning and the optional tint. If RuneLite flashes the taskbar or plays a sound while you are mining or chiseling, that comes from the built-in Idle Notifier plugin. Its "High energy threshold" alert (default 100) fires every time run energy climbs back to full while you stand still, which happens once per trip at the runestone and again at the altar. Set that threshold to 0 to keep only the real idle alerts.

---

## Development

Hard fork of [JamsRepos/zeah-rc-helper](https://github.com/JamsRepos/zeah-rc-helper); not on the Plugin Hub. Builds on JDK 21 and targets Java 11 (see `docs/adr/0006-build-on-jdk-21-target-java-11.md`).

```bash
./gradlew build   # compile, checkstyle, tests
./dev.sh          # build and restart the dev client (see docs/DEV-LOOP.md)
```

- `CONTEXT.md` — glossary of the domain terms used in code and docs
- `docs/STANDARDS.md` — coding, testing, commit and release conventions
- `docs/RUNELITE-RULES.md` — Plugin Hub restrictions this fork stays within
- `docs/adr/` — decisions and why
- `CHANGELOG.md` — release notes
- `docs/KNOWN-ISSUES.md` — open bugs seen in the dev client (issues are disabled on GitHub)

## License

BSD-2-Clause. See [LICENSE](LICENSE).
