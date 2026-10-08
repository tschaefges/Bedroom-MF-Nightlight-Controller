/* groovylint-disable DuplicateMapLiteral */
/*
 * Bedroom MF Nightlight Controller
 *
 * Controls a Thirdreality Multi-function Nightlight from two switches, a lux
 * sensor, and the system mode, and keeps the device's firmware "local routine"
 * mode from re-enabling (it returns 12 hours after the last hub command).
 */

import groovy.transform.Field

@Field static final String NIGHT_MODE = "Night"

definition(
    name: "Bedroom MF Nightlight Controller",
    namespace: "schaeford",
    author: "Tom Schaefges",
    description: "Controls a Thirdreality Multi-function Nightlight and suppresses its local routine mode",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Bedroom MF Nightlight Controller", install: true, uninstall: true) {
        section("Devices") {
            input "bnlDevice", "capability.colorControl", title: "Nightlight to control", required: true
            input "bnlOnOffSwitch", "capability.switch", title: "On/Off switch (enables the nightlight function)", required: true
            input "bnlAutoSwitch", "capability.switch", title: "Auto switch (enables lux based control)", required: true
            input "luxSensor", "capability.illuminanceMeasurement", title: "Lux sensor", required: true
        }
        section("Normal color (any mode other than ${NIGHT_MODE})") {
            input "normalHue", "number", title: "Hue (0-100)", range: "0..100", required: true
            input "normalSat", "number", title: "Saturation (0-100)", range: "0..100", required: true
            input "normalLevel", "number", title: "Level (0-100)", range: "0..100", required: true
        }
        section("Night color (${NIGHT_MODE} mode)") {
            input "nightHue", "number", title: "Hue (0-100)", range: "0..100", required: true
            input "nightSat", "number", title: "Saturation (0-100)", range: "0..100", required: true
            input "nightLevel", "number", title: "Level (0-100)", range: "0..100", required: true
        }
        section("Lux control") {
            input "luxLimit", "number", title: "Lux limit (light turns on at or below this value)", required: true
            input "luxHysteresis", "number", title: "Hysteresis (light turns off above lux limit plus this value)", defaultValue: 5, range: "0..*", required: true
        }
        section("Local routine suppression") {
            input "keepAliveHours", "decimal", title: "Hours after the last command to send a keep-alive (must be under 12)", defaultValue: 11, range: "0.01..11.9", required: true
        }
        section("Logging") {
            input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    unsubscribe()
    unschedule()
    subscribe(bnlOnOffSwitch, "switch", switchHandler)
    subscribe(bnlAutoSwitch, "switch", switchHandler)
    subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(location, "mode", modeHandler)
    // Force a send so the device matches the new settings and the keep-alive timer starts
    evaluate(true)
}

def switchHandler(evt) {
    logDebug "${evt.displayName} switch is ${evt.value}"
    evaluate()
}

def luxHandler(evt) {
    logDebug "${evt.displayName} illuminance is ${evt.value}"
    evaluate()
}

def modeHandler(evt) {
    logDebug "mode is ${evt.value}"
    evaluate()
}

// Sends the desired state to the device when it differs from what was last sent, or always when forced
def evaluate(Boolean force = false) {
    Map desired = desiredState()
    String signature = stateSignature(desired)
    if (force || signature != state.lastSent) {
        sendToDevice(desired)
    } else {
        logDebug "no change (${signature})"
    }
}

// All on/off and color decisions live here
Map desiredState() {
    Boolean luxOn = luxDecision()
    Boolean on
    if (bnlOnOffSwitch.currentValue("switch") != "on") {
        on = false
    } else if (bnlAutoSwitch.currentValue("switch") != "on") {
        on = true
    } else {
        on = luxOn
    }

    Map color = (location.mode == NIGHT_MODE) ?
        [hue: nightHue as Integer, saturation: nightSat as Integer, level: nightLevel as Integer] :
        [hue: normalHue as Integer, saturation: normalSat as Integer, level: normalLevel as Integer]

    return [on: on, color: color]
}

// On at or below luxLimit, off above luxLimit + luxHysteresis, unchanged inside the dead band
Boolean luxDecision() {
    /* groovylint-disable-next-line VariableTypeRequired */
    /* groovylint-disable-next-line VariableTypeRequired */
    def lux = luxSensor.currentValue("illuminance")
    if (lux == null) {
        log.warn "${luxSensor.displayName} has no illuminance value, keeping previous lux decision"
    } else if (lux <= luxLimit) {
        state.luxOn = true
    } else if (lux > luxLimit + (luxHysteresis ?: 0)) {
        state.luxOn = false
    }
    return state.luxOn ?: false
}

String stateSignature(Map desired) {
    return desired.on ? "on:${desired.color.hue}:${desired.color.saturation}:${desired.color.level}" : "off"
}

// The only place that sends commands to the device, so the keep-alive timer always counts from the last command
void sendToDevice(Map desired) {
    if (desired.on) {
        /* groovylint-disable-next-line UnnecessarySetter */
        bnlDevice.setColor(desired.color)
    } else {
        bnlDevice.off()
    }
    state.lastSent = stateSignature(desired)
    state.lastCommandTime = now()
    logDebug "sent ${state.lastSent}"
    runIn((keepAliveHours * 3600) as Integer, "keepAlive", [overwrite: true])
}

// Local routine suppression strategy. Version 1: re-send the current desired state.
def keepAlive() {
    logDebug "keep-alive"
    evaluate(true)
}

void logDebug(String msg) {
    if (logEnable) log.debug msg
}
