# Troubleshooting

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Find your problem below. Each answer says what is wrong and what to do. Tip: copy the exact error
text and search this page for it (Ctrl+F, or Cmd+F on a Mac).

## Build problems

**`Project directory '.../ftc-helper-lib/library' does not exist`**
The submodule folder is empty: the project was cloned (copied from GitHub) without the library in
it. Run in the Terminal tab:
```bash
git submodule update --init
```

**`Cannot resolve symbol 'crossbotics'` / red imports**
Gradle does not know about the library yet. Check steps 2 and 3 in
[Add it to a project](01-add-to-project.md), then **File > Sync Project with Gradle Files**.

**`Plugin [id: 'com.android.library'] was not found`**
Your project's top `build.gradle` (the one in the project folder, not in TeamCode) must list the
Android library plugin. Every FtcRobotController
project does (`id 'com.android.library' version '...' apply false`). If yours is very old, update the SDK.

**Errors in the library after an FTC SDK update**
See [A new FTC SDK version](02-keep-up-to-date.md#a-new-ftc-sdk-version).

## Driver Station errors

**`No DcMotorEx named "..." in the active robot configuration`**
The name in code and the name in the Driver Station configuration differ. The message lists the
names that do exist. Fix the spelling, or pick the right configuration on the Driver Station.

**`AutoDriveConfig.calibrationConfirmed is false`**, or **"AUTONOMOUS IS OFF: the drive is not tuned yet"** (EasyAuto, Auto)
On purpose: a robot with untuned numbers can drive much too far. Tune `AutoDrive`, then set it to `true`: [Tune AutoDrive](modules/drive.md#tune-autodrive). With the
robot in five files: the Drive Tuner, then `Tunables.Drive.calibrated = true`.

## Warnings on the Driver Station (`!! ...` at the top)

| Warning | Meaning |
| --- | --- |
| `lift: push it DOWN to the switch to home it` | homing (finding where 0 is): drive it down to the switch once |
| `lift: no switch "lift_bottom" in the configuration` | the switch name is wrong or missing: it runs without the switch |
| `claw: no position called "OPNE"` | a typo in a position name: the command was ignored |
| `homing failed: switch not found` (turret) | the switch never closed: wire, magnet gap, or the wrong direction in `home(...)` |
| `follower encoder not reading: follower switched off` (flywheel) | the second flywheel motor has no encoder cable: plug it in (or use one motor) |
| `Launcher gave up: flywheel` (or `hood`, `Turret`...) | it was not ready in time: that part is the problem |
| `PRACTICE profile` | set COMPETITION at events |
| `N tuned value(s) differ from the code` | saved tuning values are in use: copy them into the code, then reset |

**`EasyTeleOp needs your robot settings` / `EasyAuto needs your robot settings`**
Add `robotConfig()` to the OpMode: `return new RobotSettings();` (starter kit) or
`return RobotSettings.config();` (tutorials). See [RobotConfig](modules/core.md#robotconfig).

**`Autonomous needs the IMU (turn sensor)`**
`RobotSettings.imu()` returned null. Return `ImuSetup.get(...)` there: every Control Hub has an IMU.

## Robot behavior

**Y does nothing (no field-centric) in an EasyTeleOp**
Field-centric needs mecanum wheels and the IMU. A tank drivetrain, or `imu()` returning null, keeps
it off on purpose.

**Field-centric "forward" is wrong at the start of TeleOp**
The robot did not start autonomous facing straight away from its driver. Set `startHeadingDeg()` in
the autonomous (written for BLUE), or press BACK after turning the robot to face away from you (in a
Controls.java TeleOp, hold BACK for 1 s: a tap pauses the robot).

**The robot drives diagonally, spins, or a wheel fights the others**
A motor direction or a motor name is wrong. [Check motor directions](modules/drive.md#check-motor-directions).

**Pushing the stick up drives backward**
You forgot the minus: forward is `-gamepad1.left_stick_y`. (The gamepad reports stick up as a
negative number.) With the robot in five files, every direction is flipped: reverse the other side ([Lesson 2](tutorials/start/02-drive.md#7-check-the-motor-directions)).

**Field-centric goes the wrong way**
1. The IMU orientation (logo/USB) does not match the hub on the robot ([ImuSetup](modules/hardware.md#imusetup)).
2. The heading was not 0 while facing away from the driver. Point the robot away and press your reset button.

**A button action runs over and over**
You used `bindHold` (runs every loop while held) where you wanted `bindPress` (runs once per press).
In `Controls.java`: `hold(...)` instead of `press(...)`.

**A button does nothing**
- Is `controls.update(gamepad1, gamepad2)` in the loop?
- Is it the right pad? `Pad.G1` = gamepad1, `Pad.G2` = gamepad2.
- Was the button held through START? After `ignoreHeldButtons`, let it go and press again.

**The robot freezes when I press a button**
An action has a `sleep()` or a waiting loop. Actions must be quick. **Why:** the program does one
thing at a time, so while it waits, nothing else runs (not even driving). Set a target or a state
variable instead and let the loop do the work ([Non-blocking code](guides/non-blocking.md)).

**A lift only goes down, and slowly, at the start**
It is not homed yet. Push it down until the lower switch is pressed; then it works normally.
See [Homing](modules/safety.md#homing-with-a-lower-switch).

**A lift will not move**
- `getAppliedPower()` is 0 while you push the stick: a limit stopped it. Check `getPosition()` and
  your limits; it may think it is at the bottom or top.
- The motor was turned on with the lift half up, so 0 is in the wrong place. Lower it and restart,
  or use a lower limit switch for homing.
- The motor direction is wrong (positive power must go up). Check with a low power.

**The lift stops early or goes past the top**
Re-measure the top position with `lift.getPosition()` on telemetry. A slipping belt or string
changes the count: add a lower limit switch so it re-zeroes each time it reaches the bottom.

**AutoDrive moves the wrong distance**
Tune `wheelDiameterIn` and `strafeCorrection`: [Tune AutoDrive](modules/drive.md#tune-autodrive).

**AutoDrive turns overshoot or stop short**
Overshoot: raise `turnKd` or lower `turnKp`. Stop short: raise `turnKp` a little or set `turnMinPower`.

**AutoDrive stops early with "robot stuck"**
It was blocked, or it moved too slowly. If it was not blocked, raise `moveMinPower` or `stallTimeSec`.

**The loop is slow (telemetry shows under ~30 Hz, that is, under 30 loops per second)**
Turn on [bulk reads](modules/hardware.md#bulkreads), send fewer telemetry lines, and remove any `sleep()`.

## Still stuck?

Write down what you did, what you expected and what happened, and ask the programming lead.
If it is a library bug, add a failing test for it: [Contributing](contributing.md).
