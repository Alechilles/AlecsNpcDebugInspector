# Alec's NPC Inspector!

Standalone in-game NPC debugging tool for Hytale.

This plugin is independent from Alec's Tamework. It can be used alongside other mods and is designed to inspect live NPC behavior with UI tools instead of command spam.

## What It Does
- Adds an in-game **NPC Debug Inspector Tool** item that can:
  - Link/unlink multiple NPCs
  - Open a linked NPC roster
  - Inspect linked NPCs quickly
  - Toggle built-in role debug flags per NPC
- Opens a full NPC inspector from command:
  - `/npcdebug`
  - `/npcdebug <uuid>`
- Includes a **Pinned Overlay** so selected inspector fields stay visible while you keep playing.
- Tracks **recent state/event transitions** in the inspector data.

## Quick Start
1. Install the mod jar into your mods folders.
2. Start server and join.
3. Spawn and use the item `Npc_Debug_Inspector_Tool` on an NPC.
    - Optional: Or, use `/npcdebug` while looking at an NPC to open the inspector without an item.

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
  - text search
- Per-NPC actions:
  - `Inspect`
  - `Debug Flags`
  - `Highlight`
  - `Copy UUID` (copies into in-UI buffer field for manual Ctrl+C)
  - `Unlink`

### 3) Debug Flags Page
- Built-in role debug flag toggles grouped by category.
- Bulk actions:
  - Enable All
  - Disable All
- Works on the selected linked NPC.

### 4) Pinned Overlay HUD
- Separate non-blocking overlay view that remains visible while interacting with the world.
- Shows only pinned fields from the currently pinned NPC.
- Keeps section grouping/order for readability.
- Includes optional pinned Events Log stream.

## Lait's Entity Inspector Shoutout
If you are interested in external tools, check out [Lait's Entity Inspector](https://www.curseforge.com/hytale/mods/laits-entity-inspector) for an even more powerful entity inspection tool!
- View similar data in a web-based GUI
- Live-edit assets in the interface
- Detects Hytalor for live asset patching