# Tutorial 09: Autonomous by hand with AutoDrive

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a settings file, a tuner that measures your drivetrain, and an autonomous that drives a square.
The main track does this with less code: [Your first autonomous](start/06-first-autonomous.md).

**You will learn:** how to keep all robot settings in one place, how to tune the drivetrain so that
"24 inches" really is 24 inches, and how to write an autonomous with `AutoDrive`.

**You need:** a drivetrain that drives correctly in TeleOp (Tutorial 04 or 05), the IMU, a tape measure
and tape.

**Full programs:** [`RobotSettings.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/RobotSettings.java),
[`Tut09DriveTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut09DriveTuner.java)
(the library's **Drive Tuner** finder does the same job with a push test, and gives the numbers to copy: [tutorial 21](21-finders.md)),
[`Tut09FirstAuto.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut09FirstAuto.java)
**Reference:** [drive.md: AutoDrive](../modules/drive.md#autodrive)

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

`static` means you call the method on the class itself (`RobotSettings.drive()`), without making a
`RobotSettings` object first. `final` on the class means no other class can extend it. The real file
also has a `private` constructor, so nobody can make a `RobotSettings` object by mistake. The class is
not an OpMode (no `@TeleOp`), so it never shows on the Driver Station.

Each call makes a fresh set of settings, so one program cannot change another's by mistake.
Mecanum wheels slip sideways: the robot moves less sideways than the wheels turn. `strafeCorrection`
makes up for that. `calibrationConfirmed` is a safety switch: autonomous refuses to run while it is
false. Then an untuned robot does not charge across the field. The IMU directions must match YOUR hub
(Tutorial 04, section 7). Wrong directions give wrong turns.

Now `new MecanumDrive(hardwareMap, RobotSettings.drive())` in TeleOp and `RobotSettings.drive()` in
autonomous always match. From now on, use this class in your TeleOp too.

`ticksPerRev` is how many encoder ticks make one turn of the motor's output shaft. Find it on your
motor's product page ("encoder resolution at the output shaft"): goBILDA 312 RPM = 537.7,
435 RPM = 384.5. Check yours.

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
That is for mecanum: a standard drive only does 0 and 180. For turns, positive = left, like the IMU.

## 3. Tune it

An untuned `AutoDrive` drives the wrong distances. So it does not run until
`calibrationConfirmed = true`. Use the tuner program:

```java
while (opModeInInit()) {
    if (gamepad1.a) test = "forward 48";
    if (gamepad1.b) test = "right 48";
    ...
}
```

The tuner is an **Autonomous**. You choose the test with the gamepad during INIT (the robot does not
move in INIT). Then you press ▶ (start on the Driver Hub). The tuner runs that one test and shows the
result.

- The tuner sets `calibrationConfirmed = true` for itself only, because you use it to check the
  calibration.
- Every test runs at half power (0.5). The power is steady, so the measurements repeat well.
- The tuner works out "ticks per inch" from `ticksPerRev` and `wheelDiameterIn`.
- "Result" says how the move ended: reached the target, stuck, out of time, or stopped.
- "Wheel ticks" are the four encoder counts (front-left, front-right, back-left, back-right).
  When the counts are very different, look for a slipping wheel or a wrong motor setting.

**Distance (all robots):**

1. Mark the floor at the front wheel. Choose A (forward 48), then press ▶.
2. Measure how far the robot really went.
3. New `wheelDiameterIn` = old x (real distance / 48).
   Example: the robot went 50 in with 3.78 -> 3.78 x 50/48 = 3.94.
4. Repeat until the distance is right within half an inch.

**Strafe (mecanum only):** choose B (right 48). New `strafeCorrection` = old x (48 / real distance).

**Turns:** choose X and Y (90 left and right). `turnKp` and `turnKd` are the P and D gains of the turn
(Tutorial 07 explains gains). Use a corner of a field tile to check the angle.
**When the turn overshoots:** raise `turnKd` (try +0.001) or lower `turnKp`. **When it stops short:**
raise `turnKp` a little (+0.002), or set `turnMinPower = 0.06`.
**With a robot in five files:** the [Move Tuner](../tuning/10-move-tuner.md)
in **Tune and check** runs these turns for you, and also finds where moves stop (`slowDownIn`).

Write every final number in `RobotSettings` and in your engineering notebook.

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

The robot drives a square. It is a good test: with good tuning, the robot ends where it started.

`turn` is "turn BY this much". `turnTo` is "turn TO this heading" (0 = the direction at the start).
`turnTo` fixes small errors that build up from many turns.

Power 0.6 for driving and 0.5 for turning is quick enough, but slow enough to stop where you want.
At the end, the program waits with `while (opModeIsActive()) idle();`, so the heading stays on the
screen. `idle()` lets the robot app do its own work. The program needs no motor stop: every `AutoDrive`
move stops the wheels when it ends.

## Run it

1. Put the robot on a field tile corner, facing along a seam.
2. Press INIT, then ▶. The robot drives the square.
3. Measure how far from the start the robot ends. Under 2 inches is good.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "calibrationConfirmed is false" | This stop is on purpose. Tune, then set it to true in `RobotSettings.auto()`. |
| Goes the wrong distance | Tune `wheelDiameterIn` (section 3). |
| Turns the wrong way or spins forever | The IMU directions do not match the hub (Tutorial 04, section 7), or a motor direction is wrong. |
| Stops early: "robot stuck" | Something blocked the robot, or `moveMinPower` is too low to move it. Raise it a little. |
| Drifts sideways on long moves | A little drift is normal. Use `turnTo` between moves to fix the heading. |
| Ends far from the start | Tune turns first, then distance. Check that the wheels do not slip (slow down if they do). |

## Try this

1. Drive a triangle (turns of 120 degrees).
2. With mecanum, drive a square WITHOUT turning: use directions 0, 90, 180, -90.
3. Print the `AutoDrive.Result` of each move on telemetry. Push the robot against a wall in one move,
   and look for `STALLED`.

## Next

[Tutorial 10: Autonomous with mechanisms](10-auto-with-mechanisms.md)
