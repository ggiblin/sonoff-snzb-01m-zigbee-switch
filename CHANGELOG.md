# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project follows Semantic Versioning.

## [1.0.0] - 2026-03-11

### Added

- Initial Hubitat custom driver for SONOFF ORB SNZB-01M (`drivers/SonoffOrbSNZB01M.groovy`).
- Button action parsing for endpoint-based 4-button behavior.
- Gesture support for `pushed`, `doubleTapped`, and `held` events.
- Triple-tap handling using `pushed` with `presses=3` plus `lastAction` text for Rule Machine compatibility.
- Battery reporting from Zigbee battery cluster attributes with voltage fallback conversion.
- Initial Hubitat helper app for gesture-to-switch mapping (`apps/SonoffOrbButtonMapper.groovy`).
- Mapping UI for all buttons and gestures with `on`/`off`/`toggle` command options.
- Duplicate triple-event debouncing between `pushed` and `lastAction` paths in helper app.
- HPM package manifest including driver and app entries (`packageManifest.json`).
- Rule Machine setup blueprint for all 16 gestures (`docs/rule-machine-blueprint.md`).
- Publish helper script to set manifest raw GitHub locations (`scripts/set-manifest-locations.sh`).
- Project documentation and setup runbook (`README.md`).
- MIT license (`LICENSE`).
