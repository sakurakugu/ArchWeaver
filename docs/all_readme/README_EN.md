# ArchWeaver

English | [简体中文](../../README.md)

A utility mod for Minecraft 26.1.2 / NeoForge, currently featuring fake player management and chunk loading. It supports spawning fake players, managing their inventories, movement, interactions, and presets.

![ArchWeaver icon](../../common/src/main/resources/icon.png)

## Motivation

Recent NeoForge versions lack a Carpet-like fake player mod, and keeping chunks loaded while building technical Minecraft contraptions can be inconvenient. ArchWeaver was created to address these needs.

Future ideas include connecting fake players to AI and adding world editing tools such as a building wand. The name ArchWeaver suggests weaving architecture and brings "Architect" to mind.

## Two Mods

This project produces two separate JARs:

| Mod                      | Mod ID                | Scope                                                                                                     | Dependency          |
| ------------------------ | --------------------- | --------------------------------------------------------------------------------------------------------- | ------------------- |
| **ArchWeaver**           | `archweaver`          | Fake players, chunk loading, and other utility features. Registers no blocks, items, or entities.         | None                |
| **ArchWeaver: Artifice** | `archweaver_artifice` | Content add-on for planned blocks and items, such as summoning blocks, chunk loader blocks, and effigies. | Requires ArchWeaver |

> ArchWeaver can be installed or removed without leaving registered content in a world save. Content that requires registry entries belongs in Artifice. The two mods have independent version numbers; Artifice declares a compatible range of ArchWeaver versions.

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
3. Fake player information, including game mode, name, alias, and coordinates.
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

The built JARs are automatically copied into the root `build` directory and renamed to:

- `build/v<mod-version>/archweaver-v<mod-version>-mc<minecraft-version>-<loader>.jar`
- `build/v<mod-version>/archweaver_artifice-v<artifice-version>-mc<minecraft-version>-<loader>.jar`

Run the development client with both mods, or with ArchWeaver alone:

```powershell
.\gradlew.bat :artifice:neoforge:runClient

.\gradlew.bat :neoforge:runClient
```

On Windows, you can also run [`tools/1.一键启动mc脚本.ps1`](../../tools/1.一键启动mc脚本.ps1).

## Reference

### Commands

See the separate [command reference](../commands_en.md).

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

Local names such as `robot-1` use offline profiles directly.

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
