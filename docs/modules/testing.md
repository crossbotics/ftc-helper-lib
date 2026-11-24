# Testing module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.testing`

Learn it hands-on: [Check the hardware (tutorial 19)](../tutorials/19-testing-procedures.md). Full guide: [Checks before practice and matches](../tests/03-checks-before-practice-and-matches.md).

The hardware check tests that every motor, servo and sensor is wired and named right, before you write robot
code. Run it on a new robot, after you rewire, and whenever a part acts strangely. It needs no code from
you, only the tiny file below.

## HardwareCheckOpMode

`HardwareCheckOpMode` is a ready-made OpMode. It lists every device in the active robot configuration, and you
test each one. Make this tiny file in TeamCode:

```java
@TeleOp(name = "Hardware Check", group = "Test")
// "extends": it IS the library's hardware check; the empty { } adds nothing of our own
public class HardwareCheck extends HardwareCheckOpMode { }
```

It finds these devices: motors, continuous-rotation (CR) servos that spin like a motor, servos, the IMU and touch
sensors. It also finds digital channels (magnetic switches, beam breaks), color and distance sensors, analog inputs, webcams,
Limelights and HuskyLens cameras. For a HuskyLens, it shows each box the camera sees, with the taught number.
It also shows the battery voltage, and it warns below 12.0 V.
Slow reads (a HuskyLens, a distance sensor) run on a helper thread, so the buttons never wait. Because of
this, a distance can show one read late.

**With your robot in five files**, give the check your robot. Then INIT also checks Robot.java against the
configuration. It shows one line for each device that Robot.java names and the configuration does NOT have,
and what that means.

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

The check lists a part marked `optional()` ([parts not on the robot yet](../reference/robot-java.md#parts-not-on-the-robot-yet)),
but it is not an error. When every device is there, it shows `Robot.java: every part it names was found.`

| Gamepad 1 | What it does |
| --- | --- |
| D-pad up / down | choose a device. The device you leave stops |
| Motor or CR servo: right stick Y | power, at most 50% (hold the right bumper for 100%) |
| Motor: A | zero the encoder |
| Servo, before its first move: D-pad right / left, bumpers, X | choose the START POINT: +/- 0.01, +/- 0.1, 0.5 (nothing moves) |
| Servo, before its first move: A | move the servo to the start point |
| Servo: D-pad right / left | +/- 0.01 |
| Servo: bumpers | +/- 0.1 |
| Servo: X | go to 0.5 |
| GUIDE (or hold the gamepad's START button 1 s) | the help page with every button, like the finders. The motors stop while it is open. `helpLines()` gives its lines: override it to add your own |

**A servo does not move until you press A at its start point.** A servo cannot tell where it is, so
the code does not know where it sits. Before the first move, the servo buttons only change the start
point on the screen (it starts at 0.5). Set the start point near where the servo sits now, keep hands clear,
then press A: the servo moves there. After that, the buttons move it. The
[Servo Finder](finders.md#servofinderopmode) works the same way, and it also finds the safe ends and positions.
For each device, the hardware check shows what to check, for example "positive power turns the way you expect,
and the encoder counts UP".

**Put the robot on a block** before you test the drive motors.
