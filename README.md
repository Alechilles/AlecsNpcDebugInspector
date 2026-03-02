# Alec's NPC Debug Inspector

Standalone in-game NPC inspection mod for Hytale.

## Current scope
- `/npcdebug` command opens a detailed inspector page for the NPC in your crosshair.
- Optional `/npcdebug <uuid>` opens details for a specific NPC UUID.
- Inspector page currently includes:
- overview
- state
- alarms
- scope flags
- component summary
- flock summary

## Build
- Run `mvn test` in this directory.
- Run `mvn package` to build the plugin jar.

