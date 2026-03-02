# Alec's NPC Debug Inspector

Standalone in-game NPC debugging mod for Hytale.

This plugin is independent from Alec's Tamework. It can be used alongside other mods and is designed to inspect live NPC behavior with UI tools instead of command spam.

## Requirements
- Hytale server build: `2026.02.19-1A311A592` (from `manifest.json`)
- Java/JDK 25 for local builds
- Windows PowerShell examples below use `.\mvnw.cmd`

## What It Does
- Opens a full NPC inspector from command:
  - `/npcdebug`
  - `/npcdebug <uuid>`
- Adds an in-game **NPC Debug Inspector Tool** item that can:
  - Link/unlink multiple NPCs
  - Open a linked NPC roster
  - Inspect linked NPCs quickly
  - Toggle built-in role debug flags per NPC
- Supports a **Pinned Overlay** so selected inspector fields stay visible while you keep playing.
- Supports persistent **Highlight** visuals on linked NPCs (ring on NPC + line to player).
- Tracks **recent state/event transitions** in the inspector data.

## Quick Start
1. Install the mod jar into your mods folders (see Install section).
2. Start server and join.
3. Use `/npcdebug` while looking at an NPC to open the inspector.
4. Optional: obtain and use the item `Npc_Debug_Inspector_Tool` for multi-NPC workflows.

## Commands
### `/npcdebug`
Opens inspector for the NPC currently in your view.

### `/npcdebug <uuid>`
Opens inspector for a specific NPC UUID.  
If that NPC is currently unloaded, the page still opens but data availability is limited until loaded.

## Inspector Tool Behavior
Item asset: `Server/Item/Items/Debug/Npc_Debug_Inspector_Tool.json`

- **Primary use**: link/unlink targeted NPC.
- **Secondary use**: open linked NPC roster.
- Link capacity is currently capped at `50` NPCs per tool instance.

Each tool stores its own linked/highlighted NPC sets in item metadata, so multiple tools can track different test groups.

## UI Surfaces
### 1) Inspector Page
- Sectioned NPC details (overview, AI/state, sensors, pathing, timers, components, flock, etc.).
- Expand/collapse sections.
- Section reorder support.
- Refresh rate slider (`150ms` to `2000ms`, step `50ms`).
- Pin mode for selecting fields to mirror in overlay.

### 2) Linked Roster
- Card view of linked NPCs with basic live status.
- Filter controls:
  - loaded only
  - same flock
  - text search
- Per-NPC actions:
  - `Inspect`
  - `Debug Flags`
  - `Highlight`
  - `Copy UUID` (copies into in-UI buffer field for manual Ctrl+C)
  - `Unlink`

### 3) Debug Flags Page
- Built-in role debug flag toggles grouped by category.
- Presets:
  - Default
  - Enable All
  - Disable All
- Works on the selected linked NPC.

### 4) Pinned Overlay HUD
- Separate non-blocking overlay view that remains visible while interacting with the world.
- Shows only pinned fields from the currently pinned NPC.
- Keeps section grouping/order for readability.
- Includes optional pinned Events Log stream.

## Highlight System
- Highlight is persisted per tool.
- Render style:
  - ring around highlighted NPC
  - line from player to highlighted NPC
- Refresh cadence is high-frequency (`~50ms`) to reduce flicker.

## Build
```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

## Install To Local Hytale Folders
```powershell
.\mvnw.cmd package -Pinstall-plugin
```
Copies built jar to:
- `${hytale.install.path}/Server/mods`
- `${hytale.userdata.path}/Mods`

## Package + Run Server
```powershell
.\mvnw.cmd package -Prun-server
```
This:
1. builds the jar
2. copies it to both mods folders
3. starts `HytaleServer.jar`

## Pre-Release Install/Run
```powershell
.\mvnw.cmd package -Pinstall-plugin -Pprerelease
.\mvnw.cmd package -Prun-server -Pprerelease
```
Targets:
`C:/Users/22ale/AppData/Roaming/Hytale/install/pre-release/package/game/latest`

## Local Build Properties (pom.xml)
- `hytale.install.path.release`
- `hytale.install.path.prerelease`
- `hytale.userdata.path`
- `hytale.jdk.path`

Adjust these in `pom.xml` if your local Hytale/JDK paths differ.

## Troubleshooting
### `mvn` not recognized
Use wrapper instead:
```powershell
.\mvnw.cmd package -Prun-server
```

### Custom UI markup errors
Usually means mismatched jar/UI assets from different plugin versions. Rebuild and reinstall the same fresh jar into both mods locations.

### Copy UUID does not go directly to OS clipboard
Current behavior writes UUID to the roster copy field for manual copy (Ctrl+C).

## Notes
- This repo includes both plugin code and required UI/assets.
- Plugin entrypoint: `com.alechilles.alecsnpcdebuginspector.AlecsNpcDebugInspector`.
