# Tutorial 09: Autonomous by hand with AutoDrive

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, an auto is a list of steps, and the Drive Tuner finds the numbers: [Building autos](../building-autos/README.md).
Build it by hand to see how a move measures distance and turns, and why an untuned robot must not drive.

**You will build:** a settings file, a tuner that measures your drivetrain, and an autonomous that drives
a square.

**You need:** a drivetrain that drives correctly in TeleOp (Tutorial 04 or 05), the IMU, a tape measure
and tape.

**Full programs:** [`RobotSettings.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/RobotSettings.java),
[`Tut09DriveTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut09DriveTuner.java),
[`Tut09FirstAuto.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut09FirstAuto.java)

---

## 1. One place for the settings

TeleOp and autonomous must use the same motor names, directions and IMU setup. When you write them
twice, one day someone changes only one copy. So make one class that every program uses:

```java
public final class RobotSettings {
    public static DriveConfig drive() {
        DriveConfig c = DriveConfig.mecanum();     // or tank() / tankTwoMotor()
        c.powerLimit = 0.8;              // easier to control while learning
        return c;
    }

    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;          // goBILDA 312 RPM motor
        c.wheelDiameterIn = 3.78;       // 96 mm wheels; tuned below
        c.strafeCorrection = 1.15;      // tuned below (mecanum only)
        c.calibrationConfirmed = false; // change to true after tuning (section 3)
        return c;
    }

    public static IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD);
    }
}
```

- `static` means you call the method on the class itself: `RobotSettings.drive()`. Each call makes fresh
  settings, so one program cannot change another's by mistake.
- `ticksPerRev` is how many encoder ticks make one turn of the motor's output shaft. Find it on your
  motor's product page ("encoder resolution at the output shaft"): goBILDA 312 RPM = 537.7,
  435 RPM = 384.5.
- Mecanum wheels slip sideways: the robot moves less sideways than the wheels turn. `strafeCorrection`
  makes up for that.
- **Safety:** `calibrationConfirmed` is a safety switch. Autonomous refuses to run while it is false, so
  an untuned robot does not charge across the field.
- The IMU directions must match YOUR hub (Tutorial 04, section 7). Wrong directions give wrong turns.

From now on, use this class in your TeleOp too: `new MecanumDrive(hardwareMap, RobotSettings.drive())`.

## 2. How AutoDrive works

`AutoDrive` uses the wheel encoders to measure distance and the IMU to measure turns:

```java
AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), RobotSettings.imu(hardwareMap));
waitForStart();
auto.move(24, 0, 0.6);     // 24 inches, direction 0 (forward), up to 60% power
auto.turn(-90, 0.5);       // turn 90 degrees to the RIGHT (negative = right)
```

Each call **waits until the move is done**, then the next line runs. That is fine in autonomous, and
only there. A move also stops by itself when it takes too long (10 s), or when the robot is stuck.

Directions for `move` count counterclockwise, like a heading: 0 forward, 90 left, 180 back, -90 right.
A standard drive only does 0 and 180. For turns, positive = left, like the IMU
([Signs and units](../conventions.md)).

## 3. Tune it

An untuned `AutoDrive` drives the wrong distances, so it does not run until `calibrationConfirmed = true`.
The tuner program runs one test and shows the result:

```java
while (opModeInInit()) {
    if (gamepad1.a) test = "forward 48";
    if (gamepad1.b) test = "right 48";
    ...
}
```

- You choose the test with the gamepad during INIT (nothing moves in INIT), then press ▶.
- The tuner sets `calibrationConfirmed = true` for itself only, because it is how you check calibration.
- Every test runs at half power, so the measurements repeat well.
- "Wheel ticks" are the four encoder counts. When they are very different, look for a slipping wheel or
  a wrong motor setting.

The math for each test:

- **Distance (A, forward 48):** new `wheelDiameterIn` = old x (real distance / 48). Example: the robot
  went 50 in with 3.78, so 3.78 x 50/48 = 3.94. Repeat until it is right within half an inch.
- **Strafe (B, right 48, mecanum only):** new `strafeCorrection` = old x (48 / real distance).
- **Turns (X and Y, 90 left and right):** `turnKp` and `turnKd` are the P and D of the turn (Tutorial 07).
  When the turn overshoots, raise `turnKd` or lower `turnKp`. When it stops short, raise `turnKp` a little.

The library's finders do the same jobs, step by step: the [Drive Tuner](../tuning/08-drive-tuner.md) for
distance and strafe, and the [Move Tuner](../tuning/10-move-tuner.md) for turns. Write every final number
in `RobotSettings`.

## 4. The first real autonomous

```java
waitForStart();

auto.move(24, 0, 0.6);     // forward 24 in
auto.turn(-90, 0.5);       // right 90
auto.move(24, 0, 0.6);
auto.turn(-90, 0.5);
auto.move(24, 0, 0.6);
auto.turn(-90, 0.5);
auto.move(24, 0, 0.6);
auto.turnTo(0, 0.5);       // face exactly where we started
```

The robot drives a square. With good tuning, it ends where it started.

- `turn` is "turn BY this much". `turnTo` is "turn TO this heading" (0 = the direction at the start).
  `turnTo` fixes small errors that build up from many turns.
- Every `AutoDrive` move stops the wheels when it ends, so the program needs no motor stop.

## Run it

1. Put the robot on a field tile corner, facing along a seam.
2. Press INIT, then ▶. The robot drives the square.
3. Measure how far from the start the robot ends. Under 2 inches is good.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "calibrationConfirmed is false" | This stop is on purpose. Tune, then set it to true in `RobotSettings.auto()`. |
| Turns the wrong way or spins forever | The IMU directions do not match the hub (Tutorial 04, section 7), or a motor direction is wrong. |
| Stops early: "robot stuck" | Something blocked the robot, or `moveMinPower` is too low to move it. Raise it a little. |
| Drifts sideways on long moves | A little drift is normal. Use `turnTo` between moves to fix the heading. |

## Try this

1. Drive a triangle (turns of 120 degrees).
2. With mecanum, drive a square WITHOUT turning: use directions 0, 90, 180, -90.
3. Print the `AutoDrive.Result` of each move on telemetry. Push the robot against a wall in one move,
   and look for `STALLED`.

---

**Next:** [Tutorial 10: Autonomous with mechanisms](10-auto-with-mechanisms.md)

**Look it up:** [drive.md: AutoDrive](../modules/drive.md#autodrive), [drive.md: AutoDriveConfig](../modules/drive.md#autodriveconfig)
