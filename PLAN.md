# Bedroom MF Nightlight Controller (Hubitat app) plan

## Status (2026-10-08)

- Plan approved.
- First draft of the app is written: `BedroomMFNightlightController.groovy`. It follows this plan as written.
- Nothing has been tested. The code has not been installed on the hub or even compiled, so every item in the Verification section is still open.

## Context

The Thirdreality Multi-function Nightlight has a firmware "local routine" mode that re-enables itself 12 hours after the last hub command. We want the hub to be the only thing controlling the light. This app drives the light from two switches, a lux sensor, and the system mode, and it sends a keep-alive command before the 12 hour window closes. Source spec: `Bedroom MF Nightlight App Design.md`.

Decisions confirmed with Tom:
- Device uses a built-in Hubitat driver, so the app relies only on standard `on()`, `off()`, and `setColor([hue, saturation, level])`.
- Colors are entered as hue, saturation, level (0 to 100) per mode.
- `luxSensor` is a separate device, and a hysteresis band is wanted.
- First keep-alive experiment: re-send the current desired state about 11 hours after the last command.

Assumptions (flag if wrong):
- `btnOnOffSwitch` / `btnAutoSwitch` in the spec table mean `bnlOnOffSwitch` / `bnlAutoSwitch`.
- Single standalone app, no parent/child.
- "Night" is matched against `location.mode` by name, held in one constant (`NIGHT_MODE`).

## File

`BedroomMFNightlightController.groovy` (paste into Apps Code on the hub).

## Settings page (single page)

| Input | Type | Notes |
| --- | --- | --- |
| bnlDevice | capability.colorControl | required |
| bnlOnOffSwitch | capability.switch | required |
| bnlAutoSwitch | capability.switch | required |
| luxSensor | capability.illuminanceMeasurement | required |
| normalHue / normalSat / normalLevel | number 0..100 | normalColor |
| nightHue / nightSat / nightLevel | number 0..100 | nightColor |
| luxLimit | number | on threshold |
| luxHysteresis | number, default 5 | dead band above luxLimit |
| keepAliveHours | decimal, default 11 | must be under 12 |
| logEnable | bool | debug logging |

## Structure

- `installed()` / `updated()` call `initialize()`: `unsubscribe()`, `unschedule()`, subscribe to `bnlOnOffSwitch.switch`, `bnlAutoSwitch.switch`, `luxSensor.illuminance`, and `location` `mode`, then `evaluate(true)`.
- Event handlers log the event and call `evaluate()`.
- `desiredState()`: the single consolidated decision routine (the spot for the future mode-based on/off enhancement). Returns `[on: Boolean, color: Map]`.
  - OnOff switch off: off.
  - OnOff on, Auto off: on.
  - OnOff on, Auto on: on when lux <= luxLimit, off when lux > luxLimit + luxHysteresis, otherwise keep the previous lux decision (`state.luxOn`, maintained by `luxDecision()`).
  - Color: night color when mode is Night, else normal color.
- `evaluate(force = false)`: compares the desired state to `state.lastSent`; sends only on a change, or when forced.
- `sendToDevice(desired)`: the only place that talks to the device. Sends `off()` or `setColor(...)`, stores `state.lastSent` and `state.lastCommandTime`, then `runIn(keepAliveHours * 3600, "keepAlive", [overwrite: true])` so the timer always counts from the most recent command.
- `keepAlive()`: isolated keep-alive strategy. Version 1 is `evaluate(true)` (re-send current state). Swapping in a toggle or color nudge later touches only this method.

Deliberate deviation from the spec: with hysteresis, the light turns off above `luxLimit + luxHysteresis`, not strictly above `luxLimit`. Setting `luxHysteresis` to 0 gives the literal spec behavior.

## Open items to settle by experiment

- Whether re-sending the same state actually resets the 12 hour timer (unknown, this is the experiment).
- Whether `setColor` alone turns the light on with this driver. This is an inference from how Hubitat's generic color drivers behave, not confirmed for this device. If it does not, add `on()` inside `sendToDevice`.

## Verification

1. Install the app, pick devices, enable debug logging.
2. Walk the switch table: OnOff off (light off for both Auto states), OnOff on + Auto off (light on), OnOff on + Auto on (light follows lux).
3. Lux: move the reading below luxLimit, into the dead band, and above the band, confirming on, no change, off. A virtual illuminance sensor makes this quick.
4. Change system mode to Night and back with the light on, confirm the color changes; with the light off, confirm it stays off.
5. Confirm in logs that no command is sent when nothing changed, and that the `keepAlive` job is rescheduled after every command (visible on the app status page).
6. Keep-alive experiment: temporarily set keepAliveHours low to confirm it fires, then restore to 11 and leave the light untouched for over 12 hours in both the off and on states, checking whether local routine mode comes back.
