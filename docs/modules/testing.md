# Testing module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.testing`

Learn it hands-on: [Check the hardware (lesson 2b, tutorial 19)](../tutorials/19-testing-procedures.md). Full guide: [Testing procedures](../guides/testing.md).

Checks that every motor, servo and sensor is wired and named right, before you write robot code.
Run it on a new robot, after rewiring, and whenever a part acts strangely. It needs no code from
you, only the tiny file below.

## HardwareCheckOpMode

`HardwareCheckOpMode` is a ready-made OpMode that lists every device in the active robot configuration and lets you test
each one. Make this tiny file in TeamCode:

```java
@TeleOp(name = "Hardware Check", group = "Test")
// "extends": it IS the library's hardware check; the empty { } adds nothing of our own
public class HardwareCheck extends HardwareCheckOpMode { }
```

It finds: motors, continuous-rotation (CR) servos that spin like a motor, servos, the IMU, touch sensors, digital channels
(magnetic switches, beam breaks), color and distance sensors, analog inputs, webcams and Limelights.
It also shows the battery voltage and warns below 12.0 V.

**With your robot in five files**, give it the robot, and INIT also checks Robot.java against the
configuration: one line for each device Robot.java names that is NOT there, and what that means.

```java
public class HardwareCheck extends HardwareCheckOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

```
Robot.java devices not in this configuration:
"lift" (Lift): NOT in the configuration: INIT will stop. Add it, or add .optional() to Lift in Robot.java
"intake_deploy" (IntakeDeploy): not in the configuration: optional, so the part is off (fine)
```

A part marked `optional()` ([parts not on the robot yet](robot.md#parts-not-on-the-robot-yet)) is
listed, not an error. With every device there: `Robot.java: every device is in the configuration`.

| Gamepad 1 | Does |
| --- | --- |
| D-pad up / down | choose a device (changing stops the last one) |
| Motor or CR servo: right stick Y | power, max 50% (hold right bumper for 100%) |
| Motor: A | zero the encoder |
| Servo: D-pad right / left | +/- 0.01 |
| Servo: bumpers | +/- 0.1 |
| Servo: X | go to 0.5 |

A servo does not move until you press a servo button. For each device it shows what to check,
for example "positive power turns the way you expect, and the encoder counts UP".

**Put the robot on a block** before testing drive motors.
