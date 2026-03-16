/*
 * SONOFF ORB SNZB-01M Zigbee Button Driver for Hubitat
 *
 * PURPOSE
 *   Provides full Hubitat integration for the SONOFF ORB 4-in-1 Zigbee Smart
 *   Scene Button (model SNZB-01M).  Parses Zigbee messages from the device
 *   and translates them into standard Hubitat capability events so the button
 *   can be used in Rule Machine, the Button Controller app, and the companion
 *   SonoffOrbButtonMapper helper app.
 *
 * INSTALLATION
 *   1. In Hubitat, go to Drivers Code -> New Driver.
 *   2. Paste the full contents of this file and click Save.
 *   3. Pair the SNZB-01M under Devices -> Add Device -> Zigbee.
 *   4. Open the newly paired device page and set Type to
 *      "SONOFF ORB SNZB-01M Button".
 *   5. Click Save Device, then click Configure.
 *
 * CAPABILITIES & EVENTS PRODUCED
 *   PushableButton    - pushed      (value = button 1-4, data.presses = 1)
 *   DoubleTapableButton - doubleTapped (value = button 1-4)
 *   HoldableButton    - held        (value = button 1-4)
 *   Battery           - battery     (value = 0-100 %)
 *
 *   Custom attributes:
 *     lastAction    (string) - human-readable last gesture, e.g.
 *                              "button 2 triple-pushed".  Use this as a
 *                              custom attribute trigger in Rule Machine to
 *                              detect triple-tap (see TRIPLE TAP below).
 *     driverVersion (string) - version string from DRIVER_VERSION constant.
 *     driverDate    (string) - release date from DRIVER_DATE constant.
 *
 * ZIGBEE PROTOCOL
 *   Cluster 0x0001 (Power Configuration)
 *     Attribute 0x0021 - BatteryPercentageRemaining (raw / 2 = %)
 *     Attribute 0x0020 - BatteryVoltage (raw / 10 = V, converted via
 *                        voltageToPercent() using a 2.6-3.0 V range)
 *
 *   Cluster 0xFC12 (SONOFF manufacturer-specific), command 0x0A
 *     The endpoint field identifies the physical button segment (1-4).
 *     The value field encodes the gesture:
 *       0x01 = single press  -> pushed (presses=1)
 *       0x02 = double press  -> doubleTapped
 *       0x03 = long press    -> held
 *       0x04 = triple press  -> pushed (presses=3) + lastAction
 *
 * TRIPLE TAP
 *   Hubitat does not expose triple-tap as a native trigger in all app
 *   contexts.  Triple press is therefore reported two ways:
 *     1. As a pushed event with data.presses = 3  (readable by apps that
 *        inspect event data, e.g. SonoffOrbButtonMapper).
 *     2. As a lastAction string "button N triple-pushed"  (readable by
 *        Rule Machine via a custom attribute trigger with operator
 *        "contains" and value "button N triple-pushed").
 *
 * PREFERENCES
 *   Enable debug logging       - Logs the full parsed Zigbee descriptor map
 *                                and unknown action codes.
 *   Enable info logging        - Logs human-readable gesture and battery
 *                                events to the Hubitat log (recommended on).
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"
@Field static final String DRIVER_DATE = "2026-03-11"

metadata {
    definition(
        name: "SONOFF ORB SNZB-01M Button",
        namespace: "gerard",
        author: "Gerard"
    ) {
        capability "PushableButton"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "Battery"
        capability "Configuration"
        capability "Initialize"

        attribute "lastAction", "string"
        attribute "driverVersion", "string"
        attribute "driverDate", "string"

        fingerprint profileId: "0104",
            inClusters: "0000,0001,0003,0020,FC12",
            outClusters: "0003,0004,0005,0006,0008,0019,1000",
            manufacturer: "SONOFF",
            model: "SNZB-01M",
            deviceJoinName: "SONOFF ORB SNZB-01M"
    }

    preferences {
        input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "enableDescriptionText", type: "bool", title: "Enable info logging", defaultValue: true
    }
}

// Called once when the driver is first assigned to a device.
// Seeds the version attributes and sets numberOfButtons so that
// capability-aware apps know how many button inputs to show.
void installed() {
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "driverDate", value: DRIVER_DATE)
    initialize()
}

// Called whenever preferences are saved.  Re-seeds version attributes so
// they stay current if the driver is updated in place.
void updated() {
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "driverDate", value: DRIVER_DATE)
    if (enableDebug) {
        log.debug "${device.displayName}: preferences updated"
    }
}

// Sets numberOfButtons to 4 (one per physical segment of the ORB).
// Called by installed() and also available as a manual command on the
// device page if the attribute ever needs to be reset.
void initialize() {
    sendEvent(name: "numberOfButtons", value: 4)
}

// Hubitat calls this when the user clicks Configure on the device page.
// The SNZB-01M is a sleepy end-device; no Zigbee binding or reporting
// configuration is required because it uses unsolicited manufacturer
// cluster reports.  This method is a no-op beyond logging.
void configure() {
    if (enableDescriptionText) {
        log.info "${device.displayName}: configure requested"
    }
}

// Hubitat entry point for all incoming Zigbee messages.
// Dispatches to handleBattery() for cluster 0x0001 (Power Configuration)
// or handleButton() for cluster 0xFC12 command 0x0A (button gestures).
// All other clusters are silently ignored.
void parse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (!descMap) return

    if (enableDebug) {
        log.debug "${device.displayName}: parsed ${descMap}"
    }

    if (descMap.clusterInt == 0x0001) {
        handleBattery(descMap)
        return
    }

    if (descMap.clusterInt == 0xFC12 && descMap.command == "0A") {
        handleButton(descMap)
    }
}

// Processes cluster 0x0001 (Power Configuration) attribute reports.
//   Attr 0x0021 BatteryPercentageRemaining: raw value is twice the
//               percentage, so divide by 2 and clamp to 0-100.
//   Attr 0x0020 BatteryVoltage: raw value is tenths of a volt; converted
//               to percentage by voltageToPercent().
private void handleBattery(Map descMap) {
    if (descMap.attrInt == null || descMap.value == null) return

    Integer rawValue = Integer.parseInt(descMap.value, 16)

    if (descMap.attrInt == 0x0021) {
        Integer pct = Math.max(0, Math.min(100, Math.round(rawValue / 2)))
        sendEvent(name: "battery", value: pct, unit: "%")
        if (enableDescriptionText) {
            log.info "${device.displayName}: battery ${pct}%"
        }
        return
    }

    if (descMap.attrInt == 0x0020) {
        BigDecimal volts = rawValue / 10.0
        Integer pct = voltageToPercent(volts)
        sendEvent(name: "battery", value: pct, unit: "%")
        if (enableDescriptionText) {
            log.info "${device.displayName}: battery ${pct}% (${volts} V)"
        }
    }
}

// Linear interpolation of battery voltage to percentage.
// Range is 2.6 V (0 %) to 3.0 V (100 %), which matches the typical
// discharge curve of a CR2032 cell used in the SNZB-01M.
private Integer voltageToPercent(BigDecimal volts) {
    BigDecimal minV = 2.6
    BigDecimal maxV = 3.0

    if (volts <= minV) return 0
    if (volts >= maxV) return 100

    return ((volts - minV) / (maxV - minV) * 100)
        .setScale(0, BigDecimal.ROUND_HALF_UP)
        .intValue()
}

// Processes cluster 0xFC12 command 0x0A (SONOFF manufacturer button report).
// The Zigbee endpoint (1-4) identifies which physical segment was pressed.
// The value byte encodes the gesture (0x01-0x04).
// isStateChange: true is set unconditionally so repeated presses of the
// same button always fire an event even if the value has not changed.
private void handleButton(Map descMap) {
    Integer button = (descMap.endpoint != null) ? Integer.parseInt(descMap.endpoint, 16) : 1
    Integer action = (descMap.value != null) ? Integer.parseInt(descMap.value, 16) : null
    if (action == null) return

    Map evt = [value: button, isStateChange: true]
    String actionText

    switch (action) {
        case 0x01:
            evt.name = "pushed"
            evt.data = [presses: 1]   // presses=1 lets the mapper app distinguish from triple
            actionText = "button ${button} pushed"
            break
        case 0x02:
            evt.name = "doubleTapped"
            actionText = "button ${button} double-tapped"
            break
        case 0x03:
            evt.name = "held"
            actionText = "button ${button} held"
            break
        case 0x04:
            // Triple press is sent as pushed(presses=3) so that apps like
            // SonoffOrbButtonMapper can detect it via event data inspection.
            // lastAction is also set to "button N triple-pushed" for Rule
            // Machine custom attribute triggers.
            evt.name = "pushed"
            evt.data = [presses: 3]
            actionText = "button ${button} triple-pushed"
            break
        default:
            if (enableDebug) {
                log.warn "${device.displayName}: unknown action ${action} from endpoint ${button}"
            }
            return
    }

    evt.descriptionText = "${device.displayName} ${actionText}"
    sendEvent(name: "lastAction", value: actionText)
    sendEvent(evt)

    if (enableDescriptionText) {
        log.info evt.descriptionText
    }
}
