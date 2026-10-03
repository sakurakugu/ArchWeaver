# Command Reference

In command syntax, `<name>` indicates a required argument and `[option]` indicates an optional argument. Commands are grouped by function below.

## `/fakeplayer`

`/fakeplayer` is the unified fake player control command.

### Spawning and Removal

- `/fakeplayer`: spawn a fake player with an automatically generated name at the command source's position.
- `/fakeplayer <name>`: spawn a fake player with the specified name at the command source's position.
- `/fakeplayer spawn <name>`: equivalent to the command above.
- `/fakeplayer kill <name>`: save and remove a fake player without death drops.

### Possession and Interfaces

- `/fakeplayer possess <name>`: possess the specified fake player.
- `/fakeplayer unpossess`: exit the current possession.
- `/fakeplayer list`: list all current fake players.
- `/fakeplayer gui [name]`: open the control center, or go directly to the specified fake player's inventory management screen.
- `/fakeplayer setting [name]`: alias for `gui`.
- `/fakeplayer player <name> <action>`: equivalent to `/player <name> <action>`; see the action list below.

Spawn position, dimension, and rotation come from the command source. The game mode is inherited from the player running the command, or defaults to creative when run from the console.

## Presets and Groups

Running `/fakeplayer preset` opens preset management; `/fakeplayer group` opens group management. Both are also accessible from the map's bottom bar. Use the `preset` and `group` subcommands to manage them.

A preset saves the name, UUID, a vanilla player data snapshot (including dimension, position, rotation, inventory, experience, game mode, and abilities), continuous actions, and an optional description. Saving a preset does not bring it online; a fake player is spawned only when the preset is loaded.

- `/fakeplayer preset list [page]`: list presets by page, with load and delete buttons.
- `/fakeplayer preset save <preset> <online-fake-player> [description]`: save the fake player's current state, creating or overwriting a preset.
- `/fakeplayer preset load <preset>`: load a single preset.
- `/fakeplayer preset remove <preset>`: delete a preset and remove it from all groups.

- `/fakeplayer group create <group>`: create an empty group.
- `/fakeplayer group list [page]`: list groups and member counts by page, with quick actions.
- `/fakeplayer group add <group> <preset>`: add a preset to a group.
- `/fakeplayer group remove <group> <preset>`: remove a single preset from a group.
- `/fakeplayer group load <group>`: load the group, reporting successful and failed loads separately.
- `/fakeplayer group unload <group>`: remove online fake players corresponding to the group's presets.
- `/fakeplayer group info <group> [page]`: view group members by page, with options to load or remove members.
- `/fakeplayer group remove <group>`: delete the group without deleting its presets.

The resident list stores fake player identities (UUIDs and names) in the world's `data/archweaver/fake_players.dat`, without continuous actions. Presets store their complete vanilla player data snapshots in the same file.

Live state such as inventory, position, abilities, and experience is saved and restored through vanilla `playerdata/<UUID>.dat` files. Normal removal or death removes a fake player from the resident list. A normal server shutdown retains resident records so they can be restored on the next startup. Records are also retained when a real player logs in and takes over an identity. On the next startup, a resident fake player is restored only if neither its name nor its UUID is in use. Operators must set continuous actions again after each startup.

Before reading the data on each startup, the existing file is copied to a timestamped `fake_players.*.dat.bak` backup. This preserves a copy for troubleshooting if parsing fails and an empty save later replaces the original.

## `/player`

All `/player` commands use `/player <name> <action>`. The form `/fakeplayer player <name> <action>` is equivalent.

### Spawning and Removal

- `/player <name> spawn`: spawn the named fake player at the command source's position, with optional position, rotation, dimension, and game mode.
- `/player <name> kill`: save and remove the fake player without death drops.
- `/player <name> shadow`: disconnect the online real player with that name and spawn a fake player at their position, inheriting their state. Non-administrators can only replace themselves.

Supported `spawn` syntax:

```text
/player <name> spawn
/player <name> spawn gamemode <game-mode>
/player <name> spawn at <position>
/player <name> spawn at <position> facing <rotation>
/player <name> spawn at <position> facing <rotation> in <dimension>
/player <name> spawn at <position> facing <rotation> in <dimension> gamemode <game-mode>
```

Use `gamemode` for the game mode argument. Carpet uses `in` for this argument, but that syntax is currently unsupported here.

Example:

```text
/player Steve spawn at ~ ~1 ~ facing 0 90 in minecraft:the_nether gamemode creative
```

> Omitted arguments inherit the command source's information (player or command block). If saved player data exists and no game mode is specified, the saved game mode is used.

### Interfaces and Inventory

- `/player <name> gui` / `/player <name> gui bag`: open the full inventory: 36 main inventory slots, 4 armor slots, and 1 offhand slot.
- `/player <name> gui enderchest`: open the fake player's ender chest.
- `/player <name> setting`: open the fake player's inventory management screen.
- `/player <name> setting default` / `/player <name> setting reset`: reset action and input settings to defaults without changing inventory or other data.
- `/player <name> possess`: possess the fake player.
- `/player <name> unpossess`: exit possession if currently possessing this fake player.

Automation command:

- `/player <name> automation <autoReplenishment|shulkerReplenishment|autoReplaceTools|autoFishing> [on|off|toggle]`: control replenishment, shulker replenishment, tool replacement, or fishing. Defaults to `toggle` when the final argument is omitted.

### Item Actions

- `/player <name> drop [option]`: drop one item, using the main hand by default.
- `/player <name> dropStack [option]`: drop a full stack, using the main hand by default.
- `/player <name> hotbar <slot>`: select a hotbar slot from `1` to `9`.
- `/player <name> swapHands`: swap the main-hand and offhand items.

Supported targets for `drop` and `dropStack`:

- `mainhand`: the main-hand item.
- `offhand`: the offhand item.
- `head`, `chest`, `legs`, `feet`: a specific armor slot.
- `armor`: all four armor slots.
- `0` to `35`: a specific inventory slot; `0` is the first hotbar slot.
- `slot <slot>`: a specific inventory slot from `0` to `35`; the number can also be used directly.
- `all`: the main inventory, armor, and offhand. `drop` drops one item per slot; `dropStack` drops everything.

An action mode can follow the target:

- `continuous`: repeat every tick.
- `interval <ticks>`: repeat at the specified interval, which must be greater than `0`.
- `once`: cancel any existing continuous or interval schedule for this drop action and perform it once.

Examples:

```text
/player robot-1 drop offhand
/player robot-1 dropStack 0
/player robot-1 drop all interval 20
```

### Riding

- `/player <name> mount`: mount a nearby vehicle or rideable mob.
- `/player <name> mount <any-text>`: force-mount the nearest entity of any type. Repeating this can be used to attempt to tame mobs.
- `/player <name> dismount`: leave the current mount.

### Movement and View Direction

- `/player <name> look <direction>`: look `north`, `south`, `west`, `east`, `up`, or `down`.
- `/player <name> look at <x> <y> <z>`: look at the specified coordinates.
- `/player <name> move <direction>`: continuously move `forward`, `backward`, `left`, or `right`.
- `/player <name> jump [mode]`: jump once or repeat according to the specified mode.
- `/player <name> fly` / `/player <name> unfly`: start or stop flying.
- `/player <name> sneak` / `/player <name> unsneak`: start or stop sneaking.
- `/player <name> sprint` / `/player <name> unsprint`: start or stop sprinting.
- `/player <name> turn back`: turn around 180 degrees.
- `/player <name> turn left` / `/player <name> turn right`: turn left or right 90 degrees.
- `/player <name> turn <pitch> <yaw>`: set the absolute view direction; pitch ranges from `-90` to `90`.

### Attacking and Using

- `/player <name> attack [mode]`: attack an entity or break a block in sight, equivalent to left-clicking.
- `/player <name> use [mode]`: interact with an entity, block, or held item, equivalent to right-clicking.
- `/player <name> stop`: stop attacking, using, dropping, moving, and jumping, and cancel sneaking and sprinting.

Modes for `attack`, `use`, and `jump` are `continuous`, `interval <ticks>`, and `once`. Omitting the mode performs the action once. `once` also cancels any existing continuous or interval schedule for that action.

- `continuous` holds the input down, suitable for continuous mining, charging, or sustained item use.
- `interval 1` releases and presses the input again every tick; it behaves differently from `continuous`. Longer intervals keep the input released between pulses.
- Stopping or switching attack targets sends an abort-mining action; completing mining sends a stop-mining action. Stopping use releases the item being used.
- Use interactions try the main hand first, then the offhand only if the main-hand interaction did not consume the action.
- Entity attacks, entity interactions, and block hits use the player's current vanilla attack or interaction range, with world border and block interaction permission checks.
- Movement passes forward and sideways input through vanilla player physics. Sneaking reduces input to 30%; horizontal velocity is not directly overridden.

## `/chunkloader`

Manual loading regions created by commands are centered on the command source's dimension and position. Use vanilla `/execute in ... positioned ... run ...` to create them in other dimensions or at other coordinates. Radius `0` contains only the center chunk; radius `r` contains `(2r+1)^2` chunks. The built-in map also supports painting non-rectangular regions.

> Loading currently takes place after world creation and before ender pearl loading.

- `/chunkloader`: open the chunk loading map.
- `/chunkloader list`: open the manual loading region management view within the map.
- `/chunkloader gui [map]`: open the GUI.
- `/chunkloader backup`: immediately create a JSON backup of the chunk loading configuration.
- `/chunkloader restore confirm`: restore configuration from the latest readable backup and rebuild this mod's chunk tickets.
- `/chunkloader info <name>`: view a region's dimension, chunk count, and enabled state.
- `/chunkloader add <name> <radius>`: create and enable a square force-loaded region at the current position.
- `/chunkloader disable <name>`: remove the region's tickets while keeping its configuration.
- `/chunkloader enable <name>`: recreate tickets from the saved configuration.
- `/chunkloader remove <name>`: remove the tickets and delete the region's configuration.
- `/chunkloader fake <fake-player> info`: view the fake player's simulation loading status and distance.
- `/chunkloader fake <fake-player> mode player`: switch to player mode, using vanilla player loading and spawning behavior.
- `/chunkloader fake <fake-player> mode doll <distance>`: switch to doll mode and set the simulation distance in chunks.

Each chunk in a manual region receives a level 31 force-loading ticket. Tickets propagate outward according to vanilla rules: the first surrounding ring is level 32, which continues block ticking but does not tick entities.

Each region has a separate owner, so removing one overlapping region does not remove another region's tickets. The server validates names, dimensions, chunk counts, budgets, and edit revisions.

Open the built-in map with `M` or `/chunkloader`. It starts in browse mode and supports panning, mouse-wheel zoom, a force-loading brush, erasing, undo, and batch application. You can switch to loading region management on the same screen; `/chunkloader list` opens that view directly.

The map displays enabled manual regions, named markers for players and fake players, and the ranges of fake players' active loading policies. Map settings allow marker name scaling, and hovering over a marker shows the player's head and name. The map only reads chunks already loaded by the client and does not request new chunk generation from the server for rendering. JourneyMap integration, box selection, a location menu, and a complete terrain tile cache are still in development.
