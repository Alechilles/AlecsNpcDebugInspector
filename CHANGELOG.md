# Changelog

All notable changes to this project are documented in this file.

## Unreleased
### Added
- Added HStats telemetry bootstrap for Alec's NPC Inspector, including server-owner opt-out support through `hstats-server-uuid.txt`.
- Added optional runtime override for the HStats plugin UUID via `-Dalecsnpcdebuginspector.hstats.uuid` or `ALECS_NPC_INSPECTOR_HSTATS_UUID`.

## 1.2.0 - Roster Pause Signal + Release Pipeline Updates - 2026-03-11
### Added
- Added a roster warning when world game time is paused, to clarify when timer/cooldown-driven debug values are not advancing.
- Added CurseForge update-check metadata in `manifest.json` (`UpdateChecker.CurseForge = 1476193`) so supported clients can detect newer versions.

### Changed
- Bumped project version to `1.2.0` in `pom.xml`.
- Updated README shields/badges.
- Aligned release/build automation with the Hytale downloader packaging flow (`publish.yml` and release scripts).

## 1.1.1 - 2026-03-07
### Added
- Added Recent Events category filters (`Core`, `Targeting`, `Timers`, `Alarms`, `Needs`, `Flock`) to control event log noise.

### Changed
- Moved Recent Events filters into the `Recent Events` section for a more consistent inspector layout.
- Improved state transition rendering to mark component-local fallback substates with `[L]` (for example `NeedSeekWater.[L]Start`) while preserving normal role substates when available.

### Fixed
- Fixed roster-to-page navigation timing to prevent intermittent CustomUI missing-selector errors when opening Inspector or Debug Flags.
- Fixed Recent Events filter selector update order so filter values are applied only after the filter row exists.

## 1.1.0 - 2026-03-05
- Added an optional Tamework inspector extension that appears only when Alec's Tamework is detected.
- Added Tamework-specific debug fields for key systems (owner/tamed/hook, name, links, happiness, needs, breeding, traits, attachments, and life stage).
- Added a custom icon for the NPC Inspector tool.
- Fixed Creative Builder Tools menu integration so the NPC Inspector tool shows its display name correctly.

## 1.0.0 - 2026-03-02
- First stable release of Alec's NPC Inspector! as a standalone mod.
- Added the in-game NPC Debug Inspector tool with link/unlink roster management.
- Added inspector, pinned overlay, and debug flags UI surfaces.
- Added release automation pipeline (`publish.yml`, release scripts, and publish config).

