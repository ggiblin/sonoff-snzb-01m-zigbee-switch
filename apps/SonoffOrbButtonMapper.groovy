/*
 * SONOFF ORB SNZB-01M Button Mapper App for Hubitat
 *
 * PURPOSE
 *   A companion helper app for the SonoffOrbSNZB01M driver.  It provides a
 *   UI-driven way to map every button gesture on the SNZB-01M to an on/off/
 *   toggle command sent to one or more Hubitat switch devices — no Rule
 *   Machine rules needed for simple on/off/toggle actions.
 *
 * INSTALLATION
 *   1. In Hubitat, go to Apps Code -> New App.
 *   2. Paste the full contents of this file and click Save.
 *   3. Go to Apps -> Add User App and select "SONOFF ORB Button Mapper".
 *   4. Follow the UI to configure mappings (see CONFIGURATION below).
 *
 * REQUIREMENTS
 *   The SNZB-01M device must already be paired and using the
 *   SonoffOrbSNZB01M driver so that it publishes pushed / doubleTapped /
 *   held / lastAction events that this app subscribes to.
 *
 * CONFIGURATION
 *   Button Device   - Select your paired SNZB-01M device.
 *
 *   For each of the 4 buttons (endpoints) and each gesture, two inputs
 *   are shown:
 *     - Target switches  : one or more switch devices to control.
 *     - Command          : "on", "off", or "toggle".
 *   Leave any pair blank to take no action for that gesture.
 *
 *   Gestures per button:
 *     push    - single press  (Hubitat "pushed" event, presses=1)
 *     double  - double press  (Hubitat "doubleTapped" event)
 *     hold    - long press    (Hubitat "held" event)
 *     triple  - triple press  (Hubitat "pushed" presses=3 OR lastAction
 *                              "button N triple-pushed")
 *
 * ADVANCED OPTIONS
 *   debounceMs   - Ignore repeated presses of the same gesture/button
 *                  within this window (milliseconds).  Set to 0 to
 *                  disable.  Useful if physical switch bounce causes
 *                  double-firing.
 *
 * TRIPLE-TAP DEDUPLICATION
 *   The driver emits triple-tap as both a pushed(presses=3) event and a
 *   lastAction string.  This app deduplicates them: whichever arrives
 *   first triggers the mapping; the second is suppressed within a 1200 ms
 *   window stored in state.lastTripleTs.
 *
 * LOGGING
 *   enableInfo            - Logs each gesture-to-command execution.
 *   enableDebug           - Logs gesture resolution, debounce decisions,
 *                           and skipped events.
 *   enableRawEventDebug   - Logs the full raw Hubitat event payload.
 *                           Use only for troubleshooting; very verbose.
 */

import groovy.json.JsonSlurper

definition(
    name: "SONOFF ORB Button Mapper",
    namespace: "gerard",
    author: "Gerard",
    description: "Map SNZB-01M button gestures to switch commands",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "SONOFF ORB Button Mapper", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Button Device") {
            input "buttonDevice", "capability.pushableButton", title: "Button device", required: true, multiple: false
        }

        section("Logging") {
            input "enableInfo", "bool", title: "Enable info logging", defaultValue: true
            input "enableDebug", "bool", title: "Enable debug logging", defaultValue: false
            input "enableRawEventDebug", "bool", title: "Enable raw event payload logging (troubleshooting)", defaultValue: false
        }

        section("Advanced") {
            input "debounceMs", "number", title: "Optional per-button debounce in ms (0 disables)", defaultValue: 0, required: false
        }

        if (buttonDevice) {
            section("Instructions") {
                paragraph "Create mappings for each gesture and button. Leave any mapping blank to do nothing."
                paragraph "Gestures available: push, double, hold, triple"
            }

            Integer buttonCount = safeButtonCount(buttonDevice)
            (1..buttonCount).each { Integer b ->
                section("Button ${b}") {
                    buildGestureInputs("push", b)
                    buildGestureInputs("double", b)
                    buildGestureInputs("hold", b)
                    buildGestureInputs("triple", b)
                }
            }
        }
    }
}

private void buildGestureInputs(String gesture, Integer button) {
    String prefix = "${gesture}_${button}"
    input "${prefix}_targets", "capability.switch", title: "${gestureLabel(gesture)}: target switches", required: false, multiple: true
    input "${prefix}_command", "enum", title: "${gestureLabel(gesture)}: command", required: false, defaultValue: "on", options: ["on", "off", "toggle"]
}

void installed() {
    initialize()
}

void updated() {
    // Re-subscribe whenever preferences are saved so that a device change
    // takes effect immediately without having to reinstall the app.
    unsubscribe()
    initialize()
}

void initialize() {
    if (!buttonDevice) return

    // pushed and doubleTapped/held are standard Hubitat button capability events.
    // lastAction is a custom string attribute used to surface triple-tap because
    // Hubitat does not expose triple as a native button trigger in all apps.
    subscribe(buttonDevice, "pushed", buttonEventHandler)
    subscribe(buttonDevice, "doubleTapped", buttonEventHandler)
    subscribe(buttonDevice, "held", buttonEventHandler)
    subscribe(buttonDevice, "lastAction", lastActionHandler)

    if (enableInfo) {
        log.info "${app.label}: subscribed to ${buttonDevice.displayName}"
    }
}

// Handles pushed, doubleTapped, and held events from the button device.
// Resolves the logical button number and gesture name, applies debounce,
// then delegates to runMapping().
void buttonEventHandler(evt) {
    debugRawEvent("buttonEventHandler", evt)

    Integer button = extractButtonFromEvent(evt)
    if (button == null) return

    String gesture = normalizeGestureFromEvent(evt)
    if (!gesture) return

    if (isDebounced(gesture, button)) {
        if (enableDebug) {
            log.debug "${app.label}: skipped ${gesture} on button ${button} due to debounce"
        }
        return
    }

    // Debounce duplicate triple actions when both pushed(presses=3) and lastAction fire.
    if (gesture == "triple") {
        Long ts = now()
        state.lastTripleTs = state.lastTripleTs ?: [:]
        state.lastTripleTs[button.toString()] = ts
    }

    if (enableDebug) {
        log.debug "${app.label}: ${gesture} on button ${button} from ${evt.name}"
    }

    runMapping(gesture, button)
}

// Handles the lastAction custom attribute event, which is the only reliable
// source of triple-tap in some Hubitat app contexts.  Ignores any value that
// is not a triple-pushed string so that normal push/hold/double info log
// changes do not trigger spurious mappings.  Suppresses duplicates if
// buttonEventHandler already processed the same triple within 1200 ms.
void lastActionHandler(evt) {
    debugRawEvent("lastActionHandler", evt)

    String value = (evt.value ?: "").toString()
    if (!value.contains("triple-pushed")) return

    Integer button = extractButtonNumber(value)
    if (button == null) return

    Long recentTripleTs = (state.lastTripleTs ?: [:])[button.toString()] as Long
    if (recentTripleTs != null && now() - recentTripleTs < 1200) {
        if (enableDebug) {
            log.debug "${app.label}: skipped duplicate triple via lastAction for button ${button}"
        }
        return
    }

    if (enableDebug) {
        log.debug "${app.label}: triple on button ${button} from lastAction"
    }

    runMapping("triple", button)
}

// Looks up the configured targets and command for the given gesture/button
// key, then executes on()/off()/toggle() on each target switch device.
// Logs a warning if no mapping is configured and does nothing further.
private void runMapping(String gesture, Integer button) {
    String key = "${gesture}_${button}"
    def targets = settings["${key}_targets"]
    String cmd = (settings["${key}_command"] ?: "on").toString()

    if (!targets) {
        if (enableInfo || enableDebug) {
            log.warn "${app.label}: no mapping configured for ${gesture} button ${button}"
        }
        return
    }

    List devices = (targets instanceof List) ? targets : [targets]
    devices.each { dev ->
        try {
            switch (cmd) {
                case "on":
                    dev.on()
                    break
                case "off":
                    dev.off()
                    break
                case "toggle":
                    toggleSwitch(dev)
                    break
                default:
                    log.warn "${app.label}: unsupported command '${cmd}' for ${dev.displayName}"
                    break
            }
        } catch (Exception e) {
            log.error "${app.label}: failed to run ${cmd} on ${dev?.displayName ?: 'unknown device'} (${e.message})"
        }
    }

    if (enableInfo) {
        log.info "${app.label}: ${gesture} button ${button} -> ${cmd} (${devices*.displayName.join(', ')})"
    }
}

// Returns true if the same gesture/button combination fired within the
// configured debounce window, suppressing the duplicate.  Uses state map
// keyed by "gesture_button" to track timestamps independently per combo.
private boolean isDebounced(String gesture, Integer button) {
    Integer windowMs = safeInt(settings?.debounceMs)
    if (windowMs == null || windowMs <= 0) return false

    Long ts = now()
    state.lastActionTs = state.lastActionTs ?: [:]
    String key = "${gesture}_${button}"
    Long previous = state.lastActionTs[key] as Long
    state.lastActionTs[key] = ts

    if (previous == null) return false
    return (ts - previous) < windowMs
}

private void toggleSwitch(dev) {
    String current = (dev.currentValue("switch") ?: "off").toString()
    if (current == "on") {
        dev.off()
    } else {
        dev.on()
    }
}

private Integer safeButtonCount(dev) {
    Integer n = safeInt(dev.currentValue("numberOfButtons"))
    if (n == null || n <= 0) return 4
    return Math.min(n, 4)
}

private Integer safeInt(Object value) {
    try {
        if (value == null) return null
        String text = value.toString().trim()
        if (!text) return null
        if (text.isInteger()) return Integer.parseInt(text)
        BigDecimal n = new BigDecimal(text)
        return n.intValue()
    } catch (Exception ignored) {
        return null
    }
}

// Resolves the logical button number (1-4) from a Hubitat event using three
// fallback strategies: event value directly, JSON data map buttonNumber field,
// then regex on the descriptionText string.
private Integer extractButtonFromEvent(evt) {
    Integer button = safeInt(evt?.value)
    if (button != null) return button

    Map data = eventDataMap(evt)
    button = safeInt(data?.buttonNumber)
    if (button != null) return button

    String description = (evt?.descriptionText ?: "").toString()
    def m = description =~ /button\s+(\d+)/
    if (m && m.size() > 0) {
        return safeInt(m[0][1])
    }

    if (enableDebug) {
        log.debug "${app.label}: could not extract button from event: name=${evt?.name}, value=${evt?.value}, data=${evt?.data}"
    }

    return null
}

// Converts a Hubitat event name into the internal gesture key used as the
// settings prefix.  For "pushed" events, inspects the data.presses field
// (set by the driver) to distinguish single press from triple press.
private String normalizeGestureFromEvent(evt) {
    String name = (evt.name ?: "").toString()

    if (name == "doubleTapped") return "double"
    if (name == "held") return "hold"

    if (name == "pushed") {
        Map data = eventDataMap(evt)
        String presses = data?.presses?.toString()
        if (!presses) {
            presses = safeInt(evt?.value) == null ? null : "1"
        }
        if (presses == "3") return "triple"
        return "push"
    }

    return null
}

private Integer extractButtonNumber(String actionText) {
    def m = actionText =~ /button\s+(\d+)\s+triple-pushed/
    if (!m || m.size() == 0) return null
    return safeInt(m[0][1])
}

private Map eventDataMap(evt) {
    try {
        def raw = evt?.data
        if (raw instanceof Map) {
            return raw as Map
        }

        String text = (raw ?: "").toString().trim()
        if (!text) return [:]

        def parsed = new JsonSlurper().parseText(text)
        if (parsed instanceof Map) {
            return parsed as Map
        }
    } catch (Exception ignored) {
        // Some Hubitat events have non-JSON data payloads.
    }

    return [:]
}

private void debugRawEvent(String source, evt) {
    if (!enableRawEventDebug) return

    Map data = eventDataMap(evt)
    log.debug "${app.label}: ${source} raw -> name=${evt?.name}, value=${evt?.value}, description=${evt?.descriptionText}, data=${evt?.data}, parsedData=${data}"
}

private String gestureLabel(String gesture) {
    switch (gesture) {
        case "push":
            return "Push"
        case "double":
            return "Double Tap"
        case "hold":
            return "Hold"
        case "triple":
            return "Triple Tap"
        default:
            return gesture
    }
}
