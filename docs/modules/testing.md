# Testing module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.testing`

Learn it hands-on: [19 Testing procedures](../tutorials/19-testing-procedures.md). Full guide: [Testing procedures](../guides/testing.md).

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
