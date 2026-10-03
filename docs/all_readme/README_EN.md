# ArchWeaver

English | [简体中文](../../README.md)

A utility mod for Minecraft 26.1.2 / NeoForge, currently featuring fake player management and chunk loading. It supports spawning fake players, managing their inventories, movement, interactions, and presets.

![ArchWeaver icon](../../common/src/main/resources/icon.png)

## Motivation

Recent NeoForge versions lack a Carpet-like fake player mod, and keeping chunks loaded while building technical Minecraft contraptions can be inconvenient. ArchWeaver was created to address these needs.

Future ideas include connecting fake players to AI and adding world editing tools such as a building wand. The name ArchWeaver suggests weaving architecture and brings "Architect" to mind.

## Overview

- Press `G` by default to open the control center, including the fake player list, chunk map, presets, and groups.
- Press `M` by default to open the chunk loading map.

## Screenshots

<img src="../image/英文_假人背包页面.png" alt="Fake player inventory and controls" width="720">
<img src="../image/英文_主页面.png" alt="Control center" width="720">
<img src="../image/英文_地图页面.png" alt="Chunk map" width="720">

## Usage

### Fake Players

1. Simulation loading: player mode and doll mode.
2. View direction, rotation, movement, flight, hotbar selection, continuous actions, item dropping, and riding.
3. Fake player information, including game mode, name, and coordinates.
4. Inventory, armor, offhand, and ender chest management.
5. Possession: take control of a fake player's inventory and viewpoint. This does not transfer everything, such as advancements.
6. Automation: these features run on the server and apply only to fake players, unlike client mods such as Tweakeroo.

| Setting               | Behavior                                                                                                                                                |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Auto replenishment    | When a stackable item in hand has at most one eighth of a stack remaining, refill it to half a stack from the 36-slot main inventory.                   |
| Shulker replenishment | Also search shulker boxes in the main inventory when replenishing. Requires ordinary replenishment to be enabled.                                       |
| Auto tool replacement | When a tool in either hand has at most 10 durability remaining, replace it with the same item type with the most remaining durability in the inventory. |
| Auto fishing          | Reel in about 5 ticks after a vanilla fishing bobber gets a bite, then wait 10 ticks before casting again.                                              |

### Chunk Loading

1. Select chunks on the map to force-load them.
2. Manage loading regions and enable or disable them individually.

## Building

```powershell
.\gradlew.bat build
```

The built JAR is automatically copied into the root `build` directory and renamed to:

- `build/v<mod-version>/archweaver-v<mod-version>-mc<minecraft-version>-<loader>.jar`

Run the development client with:

```powershell
.\gradlew.bat :neoforge:runClient
```

On Windows, you can also run [`tools/1.一键启动mc脚本.ps1`](../../tools/1.一键启动mc脚本.ps1).

## Reference

### Commands

In command syntax, `<name>` indicates a required argument and `[option]` indicates an optional argument. Commands are grouped by function below.

#### `/fakeplayer`

`/fakeplayer` is the unified fake player control command.

##### Spawning and Removal

- `/fakeplayer`: spawn a fake player with an automatically generated name at the command source's position.
- `/fakeplayer <name>`: spawn a fake player with the specified name at the command source's position.
- `/fakeplayer spawn <name>`: equivalent to the command above.
- `/fakeplayer kill <name>`: save and remove a fake player without death drops.

##### Possession and Interfaces

- `/fakeplayer possess <name>`: possess the specified fake player.
- `/fakeplayer unpossess`: exit the current possession.
- `/fakeplayer list`: list all current fake players.
- `/fakeplayer gui [name]`: open the control center, or go directly to the specified fake player's inventory management screen.
- `/fakeplayer setting [name]`: alias for `gui`.
- `/fakeplayer player <name> <action>`: equivalent to `/player <name> <action>`; see the action list below.

Spawn position, dimension, and rotation come from the command source. The game mode is inherited from the player running the command, or defaults to creative when run from the console.

#### Presets and Groups

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

#### `/player`

All `/player` commands use `/player <name> <action>`. The form `/fakeplayer player <name> <action>` is equivalent.

##### Spawning and Removal

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

##### Interfaces and Inventory

- `/player <name> gui` / `/player <name> gui bag`: open the full inventory: 36 main inventory slots, 4 armor slots, and 1 offhand slot.
- `/player <name> gui enderchest`: open the fake player's ender chest.
- `/player <name> setting`: open the fake player's inventory management screen.
- `/player <name> setting default` / `/player <name> setting reset`: reset action and input settings to defaults without changing inventory or other data.
- `/player <name> possess`: possess the fake player.
- `/player <name> unpossess`: exit possession if currently possessing this fake player.

Automation command:

- `/player <name> automation <autoReplenishment|shulkerReplenishment|autoReplaceTools|autoFishing> [on|off|toggle]`: control replenishment, shulker replenishment, tool replacement, or fishing. Defaults to `toggle` when the final argument is omitted.

##### Item Actions

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

##### Riding

- `/player <name> mount`: mount a nearby vehicle or rideable mob.
- `/player <name> mount <any-text>`: force-mount the nearest entity of any type. Repeating this can be used to attempt to tame mobs.
- `/player <name> dismount`: leave the current mount.

##### Movement and View Direction

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

##### Attacking and Using

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

#### `/chunkloader`

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

## Configuration

Server and client configuration files are generated automatically on first load. Boolean values must use `true` or `false` without quotation marks.

### Server Configuration

File: `<world-directory>/serverconfig/archweaver-server.toml`

Single-player location: `config/archweaver-server.toml`

```toml
[commands]
permissionLevel = 2

[profiles]
allowOfflineProfiles = true
strategy = "ONLINE_PREFERRED"

[persistence]
restoreFakePlayers = true

[chunkloading]
maxRadius = 8
maxForcedChunks = 2048
maxTickingChunks = 512
maxPlayerLoadingChunks = 65536

[ui]
enableContainerTransferButtons = true
```

| Setting                               | Description                                                                                                             |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `commands.permissionLevel`            | Minimum vanilla permission level for `/fakeplayer` and `/player`, from `0` to `4`.                                      |
| `profiles.allowOfflineProfiles`       | Allow offline UUIDs when neither cached nor online profiles are available.                                              |
| `profiles.strategy`                   | Profile resolution strategy: `ONLINE_PREFERRED`, `CACHE_ONLY`, or `OFFLINE_ONLY`.                                       |
| `persistence.restoreFakePlayers`      | Restore fake players that were still online when the server last shut down.                                             |
| `chunkloading.maxRadius`              | Maximum radius for a single loading region, from `0` to `32`; `0` means only the center chunk.                          |
| `chunkloading.maxForcedChunks`        | Total force-loaded chunk budget for all enabled manual regions, from `1` to `65536`.                                    |
| `chunkloading.maxTickingChunks`       | Total fully simulated chunk budget for all manual force-loading regions, from `1` to `16384`.                           |
| `chunkloading.maxPlayerLoadingChunks` | Deduplicated simulation chunk budget for all online doll-mode fake players, from `-1` to `65536`; `-1` means unlimited. |
| `ui.enableContainerTransferButtons`   | Show item transfer buttons in ordinary containers. Fake player inventories always show them.                            |

Profile strategies:

| Strategy           | Behavior                                                                                                                                             |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ONLINE_PREFERRED` | Reuse trusted online-account cache entries first, otherwise fetch profiles from Mojang. If identity lookup fails, fallback depends on configuration. |
| `CACHE_ONLY`       | Use only the server cache without online queries. Cache misses fall back according to `allowOfflineProfiles`.                                        |
| `OFFLINE_ONLY`     | Always generate a stable offline UUID from the name.                                                                                                 |

After profile resolution, the mod checks for online players with the same name or UUID, bans, and the whitelist. Even on an offline-mode server, `ONLINE_PREFERRED` first queries online-account profiles in the background. Only names matching the online account naming format proceed to UUID-based skin lookup; local names such as `robot-1` use offline profiles directly.

If only the skin service fails, the confirmed online UUID is retained and a default skin is used; the identity does not switch to an offline UUID. Skin properties are saved with resident records and presets.

### Client Configuration

File: `config/archweaver-client.toml`

```toml
[chunkMap]
markerNameScale = 1.0
showWeakLoading = true
```

| Setting                    | Description                                                                           |
| -------------------------- | ------------------------------------------------------------------------------------- |
| `chunkMap.markerNameScale` | Display scale for player and fake player names on the map, from `0.5` to `2.0`.       |
| `chunkMap.showWeakLoading` | Show the weakly loaded area around force-loaded regions caused by ticket propagation. |

### Persistent Data

Fake player and chunk loading data is stored in the following locations, relative to the world directory:

| Data                                                    | Location                                                               | Description                                                                                                                                                               |
| ------------------------------------------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Identity (UUID and name)                                | `data/archweaver/fake_players.dat`                                     | Resident list used to restore fake players by identity after a server restart.                                                                                            |
| Automation settings (four toggles)                      | `data/archweaver/fake_players.dat`                                     | Saved with resident records and presets, and restored after restart.                                                                                                      |
| Continuous actions, movement input, sneaking, etc.      | Preset snapshots                                                       | Saved with presets. Resident restoration does not carry these settings; operators must set them again after startup.                                                      |
| Inventory, position, abilities, experience, etc.        | `playerdata/<UUID>.dat`                                                | Saved and restored by vanilla player data handling.                                                                                                                       |
| Manual loading regions and fake player loading policies | `data/archweaver/chunk_loaders.dat` (backups in `archweaver/backups/`) | Saves region UUIDs, names, dimensions, non-rectangular chunk sets, fake player UUIDs, enabled states, and simulation distances. Active tickets are rebuilt after startup. |
