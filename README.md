# Alec's NPC Debug Inspector

Standalone in-game NPC inspection mod for Hytale.

## Current scope
- `/npcdebug` command opens a detailed inspector page for the NPC in your crosshair.
- Optional `/npcdebug <uuid>` opens details for a specific NPC UUID.
- Inspector page currently includes overview, state, alarms, scope flags, component summary, and flock summary.
- Inspector tool roster now includes a `Debug Flags` menu per linked NPC for toggling built-in `/npc debug set <flag>` role debug features, with presets (`default`, `all`, `none`).

## Build
```bash
.\mvnw.cmd test
.\mvnw.cmd package
```

## Install plugin to local Hytale folders
```bash
.\mvnw.cmd package -Pinstall-plugin
```
Copies the built jar to:
- `${hytale.install.path}/Server/mods`
- `${hytale.userdata.path}/Mods`

## Package and run server
```bash
.\mvnw.cmd package -Prun-server
```
This builds the jar, copies it to both mod folders, and starts `HytaleServer.jar`.

## Pre-release install/run
```bash
.\mvnw.cmd package -Pinstall-plugin -Pprerelease
.\mvnw.cmd package -Prun-server -Pprerelease
```
Targets `C:/Users/22ale/AppData/Roaming/Hytale/install/pre-release/package/game/latest`.
