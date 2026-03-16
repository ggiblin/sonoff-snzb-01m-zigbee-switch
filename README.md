# Hubitat SONOFF ORB SNZB-01M Driver

Custom Hubitat integration project for the SONOFF ORB 4-in-1 Zigbee Smart Scene Button (`SNZB-01M`).

## Features

- Supports 4 logical buttons (endpoints 1-4)
- Events: `pushed`, `doubleTapped`, `held`
- Triple-tap surfaced via `lastAction` text for Rule Machine matching
- Battery reporting (`%` and voltage-to-percent fallback)

## Project Layout

- `drivers/SonoffOrbSNZB01M.groovy` - Hubitat custom driver
- `apps/SonoffOrbButtonMapper.groovy` - Hubitat helper app for gesture-to-switch mapping
- `packageManifest.json` - Hubitat Package Manager (HPM) package metadata
- `docs/rule-machine-blueprint.md` - 16-gesture Rule Machine template
- `scripts/set-manifest-locations.sh` - helper to inject GitHub raw URLs into manifest

---

## Complete Setup Guide

No Rule Machine rules are needed for standard button-to-switch actions. The driver and the Button Mapper app together handle everything.

### Step 1 — Install the driver

The driver teaches Hubitat how to talk to the SNZB-01M hardware.

1. In your Hubitat UI, go to **Drivers Code** -> **New Driver**.
2. Paste the full contents of `drivers/SonoffOrbSNZB01M.groovy`.
3. Click **Save**.

### Step 2 — Install the Button Mapper app

The app provides the UI for assigning button actions to your switch devices.

1. Go to **Apps Code** -> **New App**.
2. Paste the full contents of `apps/SonoffOrbButtonMapper.groovy`.
3. Click **Save**.

### Step 3 — Pair the button

1. Go to **Devices** -> **Add Device** -> **Zigbee**.
2. Hold the SNZB-01M reset button until the LED flashes to put it into pairing mode.  
   Keep the button close to the hub during pairing.
3. Once paired, Hubitat will create a new device. Open it.
4. Change **Type** to `SONOFF ORB SNZB-01M Button`.
5. Click **Save Device**, then click **Configure**.

> **Tip:** If the device was already paired with a generic driver, just change the Type and click Configure — you do not need to re-pair.

### Step 4 — Create a Button Mapper instance

1. Go to **Apps** -> **Add User App**.
2. Select **SONOFF ORB Button Mapper**.
3. Under **Button Device**, select the SNZB-01M device you just paired.

### Step 5 — Configure your button actions

The app shows four sections, one per button segment on the ORB.  
Each section has four gesture rows: **Push**, **Double Tap**, **Hold**, **Triple Tap**.

For each gesture you want to use:

1. **Target switches** — pick one or more switch devices to control.
2. **Command** — choose `on`, `off`, or `toggle`.

Leave any row blank to do nothing for that gesture.

Click **Done** when finished. The app saves and activates immediately — no restart needed.

> **Example mapping for two lights:**
>
> | Button | Gesture | Targets | Command |
> |--------|---------|---------|---------|
> | 1 | Push | Front light | toggle |
> | 1 | Hold | Front light | off |
> | 2 | Push | Back light | toggle |
> | 2 | Hold | Back light | off |
> | 3 | Push | Front light, Back light | on |
> | 4 | Push | Front light, Back light | off |

### Step 6 — Verify

Press each button segment once. In **Logs**, you should see an info line like:

```
SONOFF ORB Button Mapper: push button 1 -> toggle (Front light)
```

If you see no log lines at all, enable **debug logging** in both the device preferences and the app, press a button, and check the logs for clues.

---

## Mapper Advanced Options

- `Enable raw event payload logging (troubleshooting)`: logs incoming Hubitat event payloads for diagnostics only.
- `Optional per-button debounce in ms (0 disables)`: ignores repeated presses for the same gesture/button when pressed again inside the configured window.


## packageManifest.json and Hubitat Package Manager (HPM)

### What the manifest is

`packageManifest.json` is the metadata file read by [Hubitat Package Manager (HPM)](https://hubitatpackagemanager.hubitatcommunity.com/).  
HPM uses it to install, update, and uninstall this package directly from your Hubitat hub without manual copy-pasting.

### Field reference

| Field | Description |
|---|---|
| `packageName` | Display name shown in HPM search results. |
| `author` | Package author name. |
| `version` | Semantic version of this release. Increment this when publishing an update so HPM detects and offers the upgrade. |
| `minimumHEVersion` | Minimum Hubitat firmware version required. |
| `dateReleased` | ISO date of this release (`YYYY-MM-DD`). |
| `licenseFile` | Raw GitHub URL to `LICENSE`. HPM displays this during install. |
| `documentationLink` | Raw GitHub URL to `README.md`. HPM links to this as the package docs. |
| `communityLink` | Optional URL to a Hubitat community forum thread. |
| `releaseNotes` | Short plain-text description of what changed in this version. |
| `drivers[].id` | Unique stable identifier for this driver entry. Never change after publishing. |
| `drivers[].name` | Must exactly match the `name` field inside the driver's `metadata { definition(...) }` block. |
| `drivers[].namespace` | Must exactly match the `namespace` field inside the driver's `metadata { definition(...) }` block. |
| `drivers[].location` | Raw GitHub URL to the `.groovy` driver file. HPM fetches and installs this file. |
| `drivers[].required` | `true` — HPM will always install this driver. |
| `apps[].id` | Unique stable identifier for this app entry. Never change after publishing. |
| `apps[].name` | Must exactly match the `name` field inside the app's `definition(...)` block. |
| `apps[].namespace` | Must exactly match the `namespace` field inside the app's `definition(...)` block. |
| `apps[].location` | Raw GitHub URL to the `.groovy` app file. HPM fetches and installs this file. |
| `apps[].required` | `false` — HPM treats the mapper app as optional during install. |

### REPLACE_ME placeholders

The `location`, `licenseFile`, and `documentationLink` fields ship with  
`https://raw.githubusercontent.com/REPLACE_ME/REPLACE_ME/main/...` as  
placeholder URLs. These must be replaced with your real GitHub repository  
paths before submitting to HPM.

### Publish workflow

1. Push this repository to GitHub.
2. From the repo root, run:

```bash
chmod +x scripts/set-manifest-locations.sh
./scripts/set-manifest-locations.sh <github-owner> <github-repo>
```

This replaces all `REPLACE_ME` placeholders in `packageManifest.json` with  
real `raw.githubusercontent.com` URLs pointing to your repo.

3. Commit and push the updated manifest:

```bash
git add packageManifest.json
git commit -m "Set HPM manifest URLs"
git push
```

4. In HPM on your Hubitat hub, go to **Install** -> **From a URL** and paste  
   the raw GitHub URL to `packageManifest.json`, e.g.:

```
https://raw.githubusercontent.com/<owner>/<repo>/main/packageManifest.json
```

### Publishing updates

When releasing a new version:

1. Update `DRIVER_VERSION` / `DRIVER_DATE` in the driver source.
2. Increment `version` in `packageManifest.json`.
3. Update `dateReleased` and `releaseNotes`.
4. Add a new entry to `CHANGELOG.md`.
5. Commit and push — HPM will detect the version bump and offer the update to users.

## Rule Machine Triple Tap Trigger

Hubitat does not always expose triple tap as a native trigger type for custom button drivers. Use `lastAction` as a custom attribute trigger:

- Attribute: `lastAction`
- Operator: `contains`
- Value example: `button 1 triple-pushed`

## Troubleshooting

- If paired but no events: run Zigbee discovery again and re-pair without deleting the device first.
- Keep the button close to the hub during first pairing.
- Press a button after saving preferences to wake this sleepy battery device.

## Rule Machine Template

A full trigger matrix for all 16 gestures is in `docs/rule-machine-blueprint.md`.
