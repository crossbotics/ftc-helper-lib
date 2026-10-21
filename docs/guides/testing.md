# Testing procedures

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Most robot problems at events are found in the pits by a 10-minute check. These procedures say
what to check, when, and how. Print the checklists.

```mermaid
flowchart LR
    A["Code change"] --> B["Laptop tests<br/>./gradlew ... test"]
    B --> C["Bench check<br/>robot on a block"]
    C --> D["Floor check<br/>low power"]
    D --> E["Practice runs<br/>read reports"]
    E --> F["Event: pre-match check"]
```

## 1. Laptop tests (every code change)

```bash
./gradlew :TeamCode:testDebugUnitTest
```

Fast, no robot. Write tests for your own logic ([tutorial 13](../tutorials/13-testing.md)).

## 2. Hardware check (after any wiring or configuration change)

Run the **Hardware Check** OpMode ([starter kit](../starter-kit.md), or
[module page](../modules/testing.md)). Robot on a block.

| Device | Check | If wrong |
| --- | --- | --- |
| every motor | moves with positive power the way you expect; encoder counts UP | `.reversed()`; check the encoder cable port |
| drive motors | each wheel rolls FORWARD with positive power | flip its `...Reversed` in `DriveConfig` |
| servos | find and write down each position you need | use them in `position(...)` / `range(...)` |
| CR servos | spin both ways | configuration must say "Continuous Rotation Servo" |
| touch / magnetic switches | change when pressed / magnet near | wiring; magnetic: false = near |
| IMU | yaw goes UP when the robot turns LEFT | logo/USB directions in `RobotSettings.imu` |
| distance / color | sensible numbers | wiring, I2C port, configuration type |
| camera | listed; Limelight "connected" | USB / Ethernet cable, configuration name |
| battery | 12.5 V or more when charged | charge or replace; under 12.0 V never play a match |

## 3. Mechanism checks (after building or changing a mechanism)

| Mechanism | Procedure | Pass |
| --- | --- | --- |
| Lift / arm (`PositionMotor`) | 1. Hard stops in place. 2. Low `maxPower`. 3. Home it (push down to the switch). 4. Drive by hand to each end: it slows and stops before the hard stop. 5. Each named position. | stops before hard stops; reaches each position in ~1 s; holds |
| Intake (`SpinMotor`) | in, out, stop; block it by hand (with jam detection) | backs out and goes on; jam count goes up |
| Servo parts | each named position; toggle; slow move | no buzzing at the ends (if it buzzes, the range is too wide) |
| Flywheel | spin to 3 speeds; compare telemetry RPM with a tachometer or slow-motion video | readings match within ~3%; READY within ~1 s |
| Launcher | shoot 1, then 3; block the turret check | each piece only when READY; `whyNotReady` names what is missing |
| Turret | home; aim at 0, 90, -90, 180 and back; manual to each limit | short way round; never past a limit; cables slack |
| Vision | point at a tag; read bearing (positive = left), distance with a tape measure | bearing sign right; distance within ~5% |

## 4. Floor check (before practice runs)

1. `powerLimit` 0.4 (or the PRACTICE profile).
2. Drive: forward, strafe, turn, field-centric.
3. Every button on the driver's sheet does what it says.
4. The match timer buzzes (test with a short `new MatchTimer(30)`).

## 5. Autonomous check

1. Run at low speed; read the step report.
2. Run as RED once.
3. Run with a start delay once.
4. Full speed: under ~28 s, parks.
5. Put a box in the way: the move ends STALLED and the robot stops pushing.

## 6. Event: before EVERY match (2 minutes)

- [ ] Battery 12.5 V+ (hardware check shows it), a spare on the cart
- [ ] Robot starts inside 18 in; everything folded; hard stops tight
- [ ] Lift/turret at their start positions (home switches in reach)
- [ ] Profile: COMPETITION, no "tuned values differ" warning
- [ ] Right autonomous selected, right alliance and delay in the menu
- [ ] Robot placed exactly as the INIT screen's "Place robot" says
- [ ] After autonomous, TeleOp's INIT screen says "Forward: from autonomous"
- [ ] Gamepads: 1 = driver (Start+A), 2 = operator (Start+B)

## Without a robot: the simulator

Many problems show up before you touch the robot: a crash at INIT, a wrong button, a screen that does
not fit. Run the OpMode in the [simulator](simulator.md) first.

## 7. After a problem

Write it down: what you did, what you expected, what happened, and the telemetry (debug ON). Then
reproduce it on the bench before changing code. If it is a library bug, add a failing test first
([Contributing](../contributing.md)).
