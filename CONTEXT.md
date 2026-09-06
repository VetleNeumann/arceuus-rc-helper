# Arceuus Runecrafting

A RuneLite plugin that guides a player through the Arceuus blood/soul runecrafting loop: it infers where the player is in the loop and shows what to click next, the path there, and gear/idle reminders. It never acts for the player.

## Language

### The loop

**Rotation**:
The fixed sequence of Steps that turns two loads of Dense Blocks into runes: mine, venerate, chisel, mine, venerate, walk to altar, craft, chisel, craft.
_Avoid_: cycle, run, loop (when meaning the sequence)

**Step**:
The player's current position in the Rotation, inferred each tick from inventory contents and location rather than tracked as state. Also includes two positions outside the Rotation: returning to the Mine, and idle.
_Avoid_: state, phase, stage

**Trip**:
One completed pass through the Rotation, counted when the Step returns to the first mining Step from the last crafting Step or from returning to the Mine.
_Avoid_: cycle, lap, run

**Next Action**:
The single click the current Step implies: the target object, the Path to it, and a short instruction.
_Avoid_: helper action, hint, suggestion

**Venerate**:
Using the Dark Altar on Dense Blocks, turning them into Dark Blocks.
_Avoid_: go dark, bless, darken

**Batch**:
One crafting click at the Blood or Soul Altar. A Rotation contains two Batches per altar visit, separated by a chisel Step.

### Essence

**Dense Block**:
The item mined from a Dense Runestone at the Mine. Not stackable.
_Avoid_: dense essence, dense, block

**Dark Block**:
A Dense Block after Venerate. Chiselled into Fragments.
_Avoid_: dark essence, dark

**Fragment**:
The stackable item produced by chiselling a Dark Block. Crafted into runes at the Blood or Soul Altar. The game hides the stack size, so the plugin estimates it.
_Avoid_: dark essence fragments, frags, essence

**Full Stack**:
Enough Fragments held that the next load of Dense Blocks can be carried straight to the altar instead of being chiselled first.

**Blood Essence**:
The charged item that boosts blood rune output. Relevant only when the Rune is Blood; unrelated to Dense or Dark Blocks.
_Avoid_: essence (bare), active essence

### Mode

**Mode**:
The player's configured choice of Blood, Soul, or Auto.
_Avoid_: rune type, type

**Auto**:
The Mode that resolves to Soul once the player's Runecraft level reaches the Soul Altar requirement, and to Blood before that. Re-resolved continuously, so a level-up flips the Rune mid-Trip.

**Rune**:
The resolved target of the current Trip: Blood or Soul. Decides the altar, the lantern log check, and whether the Essence Reminder applies.
_Avoid_: mode (when meaning the resolved value), rune type

### Places

**Arceuus**:
The region of Great Kourend where the whole loop happens.
_Avoid_: Zeah (the continent; too broad)

**Mine**:
The Dense Runestone mining spot. Where every Trip starts and ends.
_Avoid_: quarry, runestones (when meaning the place)

**Dense Runestone**:
One of the mineable rocks at the Mine that yields Dense Blocks.
_Avoid_: rock, runestone (bare)

**Dark Altar**:
The altar where Venerate happens.

**Blood Altar** / **Soul Altar**:
The crafting altars. Which one is relevant follows from the Rune.

**Shortcut**:
One of the four agility obstacles between the Mine and the altars, each with an Agility level requirement. A Path may include a Shortcut the player is able to use.
_Avoid_: hop, obstacle, jump

**At Altar** / **Near Altar**:
Two distances from the crafting altar that gate Steps: At Altar means close enough to craft; Near Altar means close enough that the player is committed to the altar visit.

### Guidance

**Helper**:
The guidance layer: Next Action highlight, Path drawing, and Status Panel. Can be switched off as a unit. Reminders keep running and Trips keep counting when the Helper is off. Also the module that assembles the Next Action from an Observation.
_Avoid_: plugin (when meaning only this layer), overlay

**Path**:
The ordered tiles from the player to the Next Action target, possibly through a Shortcut.
_Avoid_: route, line, walk

**Path Source**:
Who computes the Path: Plugin Lines (this plugin's own pathfinding) or Shortest Path (the external Shortest Path plugin).
_Avoid_: path provider, pathfinder (when meaning the choice)

**Path Display**:
Where the Path is drawn: floor, minimap, both, or nowhere.

**Status Panel**:
The on-screen panel showing the current Step, item counts, Trips, and active Reminders.
_Avoid_: overlay panel, HUD

### Reading the game

**Observation**:
Everything the plugin reads from the game on one tick: whether the player is in Arceuus, the resolved Rune, the Position, the inventory, skill levels, animation state and tick. Built once per tick by the one module that talks to the client; every other module takes it as a value.
_Avoid_: game state, snapshot (when meaning the whole read), client

**Position**:
Where the player stands, reduced to what the Rotation needs: the tile plus whether it counts as at the Mine, At Altar or Near Altar for the current Rune.
_Avoid_: location, player location, coordinates

### Reminders

**Reminder**:
A warning shown when something outside the Rotation needs the player's attention. Four kinds below.
_Avoid_: alert, warning, notification

**Gear Reminder**:
Chisel, pickaxe, or lantern missing or not equipped.
_Avoid_: lantern reminder (when meaning all gear)

**Lantern Reminder**:
The lantern is equipped but unlit, or lit with the wrong logs for the Rune.

**Essence Reminder**:
Blood Essence missing or below the low-charge threshold. Blood only.

**Idle Reminder**:
The player has not moved for longer than the configured idle time. May also show an **Idle Tint**, a faint screen tint.
_Avoid_: AFK warning, idle flash
