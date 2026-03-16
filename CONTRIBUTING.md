# Contributing

Thanks for contributing.

## Scope

This repository contains:

- Hubitat driver code for SONOFF ORB SNZB-01M
- Hubitat helper app code for gesture mapping
- Package metadata for Hubitat Package Manager (HPM)

## Development Guidelines

- Keep Groovy code compatible with Hubitat runtime expectations.
- Preserve existing event names and data payload structure when possible.
- Document behavior changes in `CHANGELOG.md`.
- Keep README installation and usage steps current.

## Testing Checklist

Before submitting changes:

1. Install or update driver in Hubitat `Drivers Code`.
2. Install or update app in Hubitat `Apps Code` (if changed).
3. Verify button gestures: push, double tap, hold, and triple tap.
4. Verify `lastAction` emits expected values for Rule Machine matching.
5. Verify mapper app actions execute correctly for `on`/`off`/`toggle`.
6. Confirm no duplicate triple actions fire unexpectedly.

## Pull Request Notes

- Include a short summary of user-visible behavior changes.
- Mention any migration steps if preferences, attributes, or event names change.
- Update `packageManifest.json` version/release notes for release-ready PRs.
