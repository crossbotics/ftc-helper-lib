# Finders module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.finders`

Learn it hands-on: [21 Find your numbers](../tutorials/21-finders.md). Step by step on the robot:
[Finders guide](../tuning/README.md). Quick answers: [cookbook: tuning and finders](../cookbook/15-tuning.md).

Finders are ready-made programs. Each one **measures** numbers your code needs (servo ends, lift limits,
flywheel gains, wheel size, odometry offsets, shot speeds, camera offset). It shows them as **lines of code
to copy**. Use a finder whenever a part is new or was rebuilt, before you tune by hand. When you press STOP,
the finder also saves those lines on the Control Hub in `/sdcard/FIRST/ftc-helper/finder-results.txt`. So
nobody copies numbers from the screen by hand. Start with tutorial 21, or with [Add a lift](../add-to-robot/01-lift.md) for the robot in five files.

Each finder shows its numbers in two forms. The first form is code for the older helpers (`PositionServo.create(...)`,
`PositionMotor.create(...)`, `c.wheelDiameterIn = ...`). The second form is lines for the
[robot in five files](../reference/robot-java.md). For example, `Tunables.Lift: min = 0, max = 2580, P1 = 1200` means "in
`Tunables.java`, section `Lift`, set `min = 0`, `max = 2580`, `P1 = 1200`". The section is the
device's name in CamelCase (`lift` -> `Lift`, `intake_deploy` -> `IntakeDeploy`,
[`RobotDefinition.sectionName`](../reference/robot-java.md)). When you gave the part its own section with
`.tunables("Name")` in Robot.java, copy the numbers into that section instead. Copy the lines that fit your code.

| Class | Use it to | Needs code from you? |
| --- | --- | --- |
| [`TuneAndCheckOpMode`](#tuneandcheckopmode) | every check and finder that fits Robot.java, in one menu (the library adds it) | no |
| [`MotorDirectionCheckOpMode`](#motordirectioncheckopmode) | spin one drive motor at a time, with Robot.java's directions: each wheel must roll forward | `robot()` (five files) |
| [`EncoderCheckOpMode`](#encodercheckopmode) | push each motor a little and find an encoder cable that is missing, in another motor's port, or counting backward | `robot()` (five files) |
| [`ServoFinderOpMode`](#servofinderopmode) | find a servo's safe ends and positions | no |
| [`HoodFinderOpMode`](#hoodfinderopmode) | find a launcher hood's two stops (`posA`, `posB`) and, if you measure them, its angles (a linkage's calibration table) | `robot()` (five files) or `hoodName()` |
| [`MotorFinderOpMode`](#motorfinderopmode) | find a lift's, arm's or turret's limits, positions and top speed | no |
| [`FlywheelFinderOpMode`](#flywheelfinderopmode) | find a flywheel's top speed, speed-control gains (the tuning numbers) and recovery time | no |
| [`FeedStopFinderOpMode`](#feedstopfinderopmode) | find a feed stop's hold and release positions | `robot()` (five files, with `.feedStop(...)`) |
| [`DriveTunerOpMode`](#drivetuneropmode) | find the wheel size and strafe correction for autonomous | `robot()` (five files) or `robotConfig()` |
| [`OdometryTunerOpMode`](#odometrytuneropmode) | check which way each odometry pod counts and measure where it sits (the offsets); a loop check sets `checked` | `robot()` (five files, with an `odometry(...)` line) |
| [`MoveTunerOpMode`](#movetuneropmode) | measure how exactly autonomous moves stop and turns end, and find `moveMinPower`, `slowDownIn`, the turn numbers and `moveWithTurn`'s numbers | `robot()` (five files) or `robotConfig()` |
| [`AimTunerOpMode`](#aimtuneropmode) | find the camera offset so shots go in the middle | your camera (+ turret or drive) |
| [`ShotTunerOpMode`](#shottuneropmode) | build the shot table: distance -> speed (and hood angle) | `robot()` (five files), or your launcher (+ camera) |
| [`CameraFixCheckOpMode`](#camerafixcheckopmode) | ALPHA: find the camera fix's turn for each alliance | `robot()` (five files, with `cameraFix(camera)`) |
| [`PieceTunerOpMode`](#piecetuneropmode) | see what each game-piece sensor reads, and measure each kind's color (or size) and the "empty" line | `robot()` (five files, with a `Pieces` or `PieceSensor` line) |
| [`CharacterizerOpMode`](#characterizeropmode) | measure how the drive, lifts and flywheels move on your robot (feedforward numbers, and the simulator's robot file) | `robotConfig()`, `mechanisms()` |
| [`FinderOpMode`](#finderopmode) | the shared frame of every finder (for writing your own) | - |
| [`FinderResults`](#finderresults) | the code lines a finder shows and saves | - |
| [`FinderMath`](#findermath) | the arithmetic behind the finders | - |
| [`Chooser`](#chooser) | a list to choose from with the D-pad | - |
| [`NudgeStep`](#nudgestep) | the step size for nudging a servo (D-pad left/right changes it) | - |
| [`ShotPoints`](#shotpoints) | the saved good shots of the shot tuner | - |
| [`CharacterizeMath`](#characterizemath) | the math behind the Characterizer (it fits numbers to the measurements) | - |

Each finder is a TeleOp. To use one, make a tiny file in TeamCode. The [starter kit](../starter-kit.md)
has them ready:

```java
@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode { }
```

**GUIDE = help** in every finder, in INIT or while it runs. It opens a page with every button and the steps.
GUIDE is the center button (the Logitech or Xbox logo, the PS button on a PlayStation controller). Some
controllers have no GUIDE button, or the Driver Hub keeps it for itself. Then **hold the gamepad's START button
for 1 second** (OPTIONS on a PS controller). While help is open, the finder's buttons pause and its motors
stop. Servos stay where they were sent, and a camera keeps running.

After ▶ (start on the Driver Hub), and after help closes, **let go of every stick, trigger and button on
gamepad 1** once. Until then the finder ignores them, so a finger that was already down cannot start anything.

Every finder keeps these safety rules:

- Nothing moves in INIT.
- The loop never waits (except the drive tuner's test moves, like autonomous).
- At the end, the finder stops everything it moved, even after an error.

---

## TuneAndCheckOpMode

`TuneAndCheckOpMode` is the library's own OpMode, **"Tune and check"** (group "Finders"). In INIT it shows
a menu with the Hardware Check and the finders that fit Robot.java. A opens a tool (its INIT screen), ▶ runs
it, and BACK in the tool's INIT goes back to the menu. It looks for the robot classes in
`org.firstinspires.ftc.teamcode`. On the robot, it searches the app's classes, the same way the SDK finds
OpModes. In the simulator, it searches the class path. The menu does not have the Aim Tuner or the
Characterizer. Steps: [the finders guide](../tuning/01-tune-and-check.md#the-tune-and-check-menu).

| Member | What it does |
| --- | --- |
| `NAME` | `"Tune and check"`, the Driver Station name |
| `useRobots(list)` | uses these robot classes instead of searching (for the simulator's tests). `null` = search again |

`Hosting` runs a finder inside the menu. For a hosted finder, `inInit()` means the host's INIT, and
`running()` means the time after ▶. In a finder of your own that loops by itself, use `running()`, not
`opModeIsActive()`.

## MotorDirectionCheckOpMode

Use it to check that each drive wheel rolls forward. Hold **X**, **Y**, **A** or **B** for front-left,
front-right, back-left or back-right (a tank drive: X left, Y right). That drive motor runs at 30% while you
hold the button. The check sets it up as Robot.java says (`reversed(...)` on the drive line). Each wheel must
roll forward. Tune and check has it for every robot in five files. To use it on its own:

```java
@TeleOp(name = "Motor Direction Check", group = "Finders")
public class MotorDirectionCheck extends MotorDirectionCheckOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

Steps: [Motor Direction Check](../tuning/02-wiring-checks.md#motor-direction-check-each-wheel-rolls-forward). For a
robot without five files (it has its own `DriveConfig`), copy `examples/.../MotorDirectionCheck.java` instead.

## EncoderCheckOpMode

Use it to find an encoder cable that is missing, in another motor's port, or counting backward. Hold **A**.
Once every port is still, each motor in Robot.java gets a short push (30% for 0.3 s), one at a time. The
check sets each motor up as Robot.java says, and reads every motor port's encoder. Each motor shows OK,
NO COUNTS, `counts on "X" instead` or `counts BACKWARD`. The check pushes a lift, arm, turret or hood toward
the side of its range with more room. It finds that side from the limits in Tunables.java (`min` and `max`).
A lift goes up from its bottom stop, so watch that a lift moves up. Let go of **A**, and everything stops. **Y** = check again.
Tune and check has it for every robot in five files. To use it on its own:

```java
@TeleOp(name = "Encoder Check", group = "Finders")
public class EncoderCheck extends EncoderCheckOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

| Method | Does |
| --- | --- |
| `robot()` | your robot (`return new Robot();`): its motors and their directions ([`encoderMotors()`](../reference/robot-java.md#robotdefinition)) |

Steps and what each result means: [Encoder Check](../tuning/02-wiring-checks.md#encoder-check-each-encoder-cable-in-its-own-port).

## ServoFinderOpMode

Use it to find a servo's safe ends and positions. It lists every servo in the configuration. Nudge the one
you choose, and mark what you find.

| Button | Does |
| --- | --- |
| D-pad up/down (list) | choose a servo; **A** = use it |
| **Y** (list) | full range on or off: 500-2500 us (microseconds) instead of the SDK's normal 600-2400. A goBILDA 2000 servo turns 300 degrees, not 270. Turn it on when the part has [`.fullRange()`](../reference/parts/claw.md) in Robot.java, so the numbers fit. Then the lines say `.fullRange()` |
| D-pad, then **A** (first) | choose a START POINT near where the servo sits now (it does not move). Then **A** moves it there |
| D-pad up/down | move by the step (before the first move, it moves the start point) |
| D-pad left/right | a smaller / bigger step: 0.1, 0.05, 0.01, 0.005, 0.001 ([`NudgeStep`](#nudgestep)). It starts at 0.05, and the screen shows it |
| **X** / **B** | mark one END / the other END (the point where it stops pushing on the frame). After that, it stays between them. Hold **LB** to go past, for example to mark again |
| **A** | save this as a position (P1, P2...; rename them in your code) |
| **Y** | undo the last position |
| **BACK** | choose another servo (its lines stay) |

A servo cannot report where it is, so nothing moves until you press A at the start point. When you save a
position outside the marked ends, its line gets a note. The output:

```java
PositionServo.create(hardwareMap, "claw").limits(0.180, 0.770)
    .position("P1", 0.210)
    .position("P2", 0.740)
Tunables.Claw: P1 = 0.210, P2 = 0.740
    // rename P1, P2... (open, closed); keep them between 0.180 and 0.770
```

In five files, the positions become `public static double open = 0.210, closed = 0.740;` in `Tunables.Claw`.
A servo module has no ends of its own, so the ends become a reminder.

## HoodFinderOpMode

Use it to find the numbers in `Tunables.Hood` for a launcher's [hood](../reference/parts/hood.md). With the
robot in five files, it reads the hood from Robot.java (its name, servo or motor, `reversed()`,
`fullRange()`). So it works before `Tunables.Hood` has any numbers. An example is the turret robot's
[HoodFinder.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/HoodFinder.java):

```java
@TeleOp(name = "Hood Finder", group = "Finders")
public class HoodFinder extends HoodFinderOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

Without five files, it uses the servo (or motor) called `hoodName()` ("hood").

| Button | Does |
| --- | --- |
| D-pad (servo) | before the first move, it sets a start point (nothing moves), and **A** moves there. After that, up/down moves it by the step, and left/right makes the step smaller / bigger ([`NudgeStep`](#nudgestep)) |
| (motor) | the finder powers nothing: move the hood by hand and watch the encoder (0 = where it was at INIT, as in a match) |
| **X** / **B** | mark stop A / stop B. Mark it a hair off the stop, because a servo that strains against it hums |
| hold **LB** + D-pad | set the angle you measured here (up/down +/- 1, right/left +/- 0.1). Do it before X, B or A |
| **A** | save a calibration point here {position, angle} |
| **Y** | undo the last point |

The output is `Tunables.Hood: posA = 0.205, posB = 0.648`. When you gave the angle at both stops, it adds
`angleA = 0, angleB = 25`. With 3 or more points, it gives `public static double[][] calibration = {{0.205, 0}, {0.33, 9}, ...};`
for a linkage, and this replaces posA/posB. Copy them into `Tunables.java`, because the robot reads the file,
not this screen. A number you change in the tuning menu (`Hood/posA`) is lost at the next restart, unless you
save it with **A**. A saved number stays on this hub and wins over `Tunables.java`. So copy it into
`Tunables.java`, then put the menu back to the code values (**Y**).

## MotorFinderOpMode

Use it to find a lift's, arm's or turret's limits, positions and top speed. It lists every motor. **The count
starts at 0 when you choose the motor, so put the mechanism at its bottom first.**

| Button | Does |
| --- | --- |
| D-pad up/down (list) | choose a motor; **A** = use it (brake, count from 0) |
| right stick Y | move it, at most at "max power" |
| D-pad up/down | max power +/- 0.05 (0.1 to 0.6; starts at 0.3) |
| **X** / **B** | mark the LOW end / the HIGH end. After that, it stops at them (hold **LB** to go past) |
| **A** / **Y** | save a position / undo the last one |
| hold **RT** | speed test: full power toward the HIGH end. It stops 15% of the range before that end |
| **BACK** | choose another motor |

The screen shows the count, the speed (counts/s) and the current (amps). A stalled motor draws a lot of
current. When the HIGH end has a smaller count than the LOW end, the motor counts down as it goes up. Then
the output adds `.reversed()` and flips the signs. The output:

```java
PositionMotor.create(hardwareMap, "lift").limits(0, 2580)
    .position("P1", 1200)
    // top speed up: 2650 counts/s
Tunables.Lift: min = 0, max = 2580, P1 = 1200
    // encoder counts: right for a Lift; an Arm's numbers are degrees
```

In five files, put `min = 0, max = 2580` and a named height such as `low = 1200` in `Tunables.Lift`. For a
reversed motor, the output also says `Robot.java: add .reversed() to the "lift" part`. An `Arm` reads degrees
from `Tunables.java`. So divide its counts by the counts per degree (motor counts per turn x gear
ratio / 360).

## FlywheelFinderOpMode

Use it to find a flywheel's top speed, speed-control gains and recovery time. It lists every motor. Keep your
hands away, and wear eye protection.

| Step | Button | Does |
| --- | --- | --- |
| 1 | hold **A** | runs full power (at most 5 s), measures the top speed and works out starting gains. The A press that chose the motor does not count: let go and press again |
| 2 | **B** | turns speed holding on or off (the library's `Flywheel`, with those gains) |
| 2 | D-pad up/down, right/left | target +/- 100 / 25 RPM (starts at 60% of top speed) |
| 2 | **RB** / **LB** | F gain +2% / -2%. F does most of the work: when the wheel is too slow at every speed, add F |
| 1, 2 | **X** | reverses the wheel when it spins the wrong way (adds `.reversed()`) |

When the encoder counts BACKWARD at forward power, the finder warns you to check the encoder cable and port.
It also blocks step 2, because speed control with a backward encoder would run away.

In step 2, feed a piece with the robot's feeder or a push stick, never with fingers near the wheel. The
finder then shows the **recovery time** (from the shot until the wheel is ready again), "Shots seen" (the
drops of more than 150 RPM) and "Shot drop": every drop out of "Ready", counted or not, with the last
drop, the smallest drop and how many (for `shotDropRpm`). The screen shows the drop; the results file does not.
It works out the gains as F = 32767 / top speed (counts/s), P = F / 10, I = P / 10, D = 0. This is a common
starting point for REV hubs. `countsPerRev()` is 28 (the motor's own encoder, wheel on the motor shaft).
Override it for a geared wheel. The output:

```java
Flywheel.create(hardwareMap, "flywheel").countsPerRev(28.0).pidf(1.17, 0.117, 0, 11.70).maxRpm(5400)
    // top speed 6000 RPM; maxRpm = 90% of it
    // recovery after a shot at 3600 RPM: 0.38 s
    // tuned at 12.6 V: add .batteryCompensation(Hw.batteryReader(hardwareMap), 12.6)
Tunables.Flywheel: kP = 1.17, kI = 0.117, kD = 0, kF = 11.70, nominalVolts = 12.6, shotIntervalSec = 0.45
```

The `Tunables` line is for a [`Launcher`](../reference/robot-java.md) module on that motor. Here the motor is
named `flywheel`. The docs pages call it `launcher`, so their section is `Tunables.Launcher`. The line has
`shotIntervalSec` only when the recovery is longer than the default 0.25 s. `nominalVolts` is the battery
voltage during the top-speed test (the screen shows `Battery: 12.61 V (gains for 12.6 V)`). The gains are
right for that voltage. The launcher's [battery compensation](launcher.md#battery-compensation) (off until you
turn it on) scales F from it. Step 2 runs with that compensation, so an F you fine-tune there fits the same
voltage. Under 11.5 V, the finder warns you to charge the battery first.

## FeedStopFinderOpMode

`FeedStopFinderOpMode` finds a launcher's feed stop positions: `Tunables.Launcher: feedStopHold = ...,
feedStopRelease = ...`. It needs `robot()` (the robot in five files, with a launcher that has
`.feedStop(...)`). Tune and check adds it by itself. Steps: [Feed Stop Finder](../tuning/07-feed-stop-finder.md).

With both positions saved, it also finds the two times: `Tunables.Launcher: feedStopMoveSec = ...,
feedStopOpenSec = ...`.

- `feedStopMoveSec`: from the servo's catalog speed and the angle between hold and release, plus 25%,
  rounded up to 0.05 s. The 25% is there because the seller's speed is with no load. Only with the part in Robot.java:
  `.feedStop("feed_stop", "gobilda-2000-speed")`.
- `feedStopOpenSec`: LB spins the wheel up to `speedRpm`, RB fires a test shot. The longest test shot plus
  0.2 s, rounded up to 0.05 s.

### FeedTest

`FeedTest` is the test shot (`robot.launcher.feedTest(hold, release, moveSec, backupSec)`). It uses the
launcher's own feed: the stop opens, the indexer and the pushing parts run like a real shot. It is for the
finder, not for TeleOp or autonomous code.

| Method | What it does |
| --- | --- |
| `spin(on)` | spins the wheel up to `speedRpm`, or stops it (a shot on its way is canceled, the stop holds) |
| `isSpinning()`, `isReady()` | the wheel is on; it is on and at speed |
| `rpmNow()`, `targetRpm()` | the wheel's speed now; the speed it spins to (RPM) |
| `fire()` | one test shot (only when ready and not busy) |
| `update()` | runs the wheel, the stop, the indexer and the pushers: call it every loop |
| `isBusy()` | a test shot is on its way, or it still watches for a second piece |
| `lastSec()` | the last shot's time to the shot, seconds, counted like `feedStopOpenSec` (NaN: no shot seen) |
| `lastEnd()` | `shot seen` or `backup time` |
| `lastPieces()` | the speed drops in the last test shot: 1 is right, 2 or more = pieces slipped through |

```java
FeedTest test = robot.launcher.feedTest(0.150, 0.550, 0.20, 3.0);
test.spin(true);
// every loop:
test.update();
if (test.isReady() && !test.isBusy() && gamepad1.right_bumper) test.fire();
```

## FeedTimeFinderOpMode

`FeedTimeFinderOpMode` finds how long a launcher's feeder must run per shot: `Tunables.Feeder: feedSec = ...`.
It is for a feeder that runs for a time (`Indexer.crServo(...)` or `Indexer.motor(...)`) with no feed stop,
pushing parts or feed wheel (`Launcher.hasTimedFeeder()`). It needs `robot()` (the robot in five files).
Tune and check adds it by itself. Steps: [Feed Time Finder](../tuning/07-feed-time-finder.md).

LB spins the wheel up to `speedRpm`. RB fires a test shot: the feeder runs at its `power` until the wheel sees
the shot (at most 3 s). X clears the times. It shows the shortest, average and longest time, and
`feedSec` = the longest plus 0.1 s, rounded up to 0.05 s. Without `stopOnShot`, it warns when the times
differ by more than 0.15 s.

```java
@TeleOp(name = "Feed Time Finder", group = "Finders")
public class FeedTimeFinder extends FeedTimeFinderOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

### FeedTimeTest

`FeedTimeTest` is the test shot (`robot.launcher.feedTimeTest(limitSec)`). It runs the launcher's own
feeder. It is for the finder, not for TeleOp or autonomous code.

| Method | What it does |
| --- | --- |
| `works()` | true for a launcher with a timed feeder that is on the robot |
| `spin(on)` | spins the wheel up to `speedRpm`, or stops it (a test feed going on stops too) |
| `isSpinning()`, `isReady()` | the wheel is on; it is on and at speed |
| `rpmNow()`, `targetRpm()` | the wheel's speed now; the speed it spins to (RPM) |
| `fire()` | one test shot (only when ready and not busy) |
| `update()` | runs the wheel, and ends a test feed at the shot or the time limit: call it every loop |
| `isBusy()` | a test feed runs |
| `lastSec()` | the last test shot's time from the start of the feed to the shot, seconds (NaN: no shot seen) |
| `lastEnd()` | `shot seen` or `time limit` |
| `limitSec()` | the time limit of one test feed, seconds |
| `feederSection()`, `feedSecNow()`, `stopsOnShot()` | the feeder's Tunables section, its `feedSec` now, its `stopOnShot` |

## DriveTunerOpMode

Use it to find the wheel size and strafe correction for autonomous. It needs your robot. For the robot in
five files, put this file next to `Robot.java`. An example is `lessonbot`'s
[DriveTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/DriveTuner.java):

```java
@TeleOp(name = "Drive Tuner", group = "Finders")
public class DriveTuner extends DriveTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

For a robot with a `RobotSettings` class instead, write `@Override protected RobotConfig robotConfig() { return new RobotSettings(); }`.

D-pad up/down chooses a test, and **A** starts it. D-pad left/right sets the power for the Drive and Strafe
tests (0.3 to 1.0, starts at 0.5). A faster robot slides farther when it stops, so use the power of your
autonomous moves. After you change the power, the tests must measure right again before the tuner shows
`Tunables.Drive: calibrated = true`. The results file says which power the numbers fit.

| Test | You do | It finds |
| --- | --- | --- |
| Push | roll the robot BY HAND exactly 48 in along a tape, then press **B** | the wheel size, from the counts. Every wheel must count up about the same. If not, it says so (a wrong motor direction or encoder cable) |
| Drive | it drives 48 in. Measure, set the real distance (D-pad +/- 1 / 0.25 in), and press **A** | a corrected wheel size |
| Strafe (mecanum) | it drives 48 in right. Measure, set it, and press **A** | a corrected strafe correction |
| Strafe left (mecanum, if needed) | it drives 48 in left. Measure, set it, and press **A** | `strafeCorrectionLeft`, for a robot that slips differently to the left |
| Turn | it turns 90 degrees left. Check it with a square | (the IMU's heading, to compare) |

The Drive, Strafe and Turn tests WAIT while the robot moves, like autonomous. STOP still works.
The tuner does not measure a move that ends STALLED or TIMED_OUT: clear the path and run it again. GUIDE (or
the gamepad's START button) stops a moving test at once and opens help. The tuner does not measure that test
either. The tuner uses each new number at once, with no rebuild. Run Drive and Strafe again until they
measure 48, then copy the lines into your code.
`testDistanceIn()` changes the 48. The output:

```java
c.wheelDiameterIn = 3.812;   // RobotSettings.auto()
Robot.java: .wheelMm(96.8)   // in the drive(...) line
c.strafeCorrection = 1.180;   // RobotSettings.auto()
Tunables.Drive: strafeCorrection = 1.180
// measured at power 0.6: these numbers fit autonomous moves at that power
```

In five files, the wheel size goes in the drive line of `Robot.java` (`.wheelMm(96.8)`, in millimeters). The
strafe correction goes in `Tunables.Drive`. When the Drive test (and on a mecanum the Strafe test) measures
48 within 0.5 in, the tuner shows `Tunables.Drive: calibrated = true`. You copy that line into
`Tunables.java` yourself. Autonomous does not run until it is true.

## OdometryTunerOpMode

Use it to check which way each odometry pod counts and to measure where each pod sits (the offsets). A pod
is a dead wheel: an unpowered wheel with an encoder. The tuner needs your robot in five files, with an
`odometry(...)` line ([odometry](odometry.md)). Put this file next to `Robot.java`. An example is the odometry
robot's
[OdometryTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/OdometryTuner.java):

```java
@TeleOp(name = "Odometry Tuner", group = "Finders")
public class OdometryTuner extends OdometryTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

In every test, the sticks drive slowly (30%). D-pad up/down chooses a test. **A** starts it and sets the pose
(the robot's x, y and heading) to 0, 0, 0. **B** ends it and shows the result.

| Test | You do | It finds |
| --- | --- | --- |
| 1 Forward 48 in | push or drive 48 in straight forward along a tape | the forward pod's direction, and how far off the distance is. For an OTOS: `linearScalar` |
| 2 Left 24 in | push or drive 24 in to the left | the sideways pod's direction |
| 3 Spin 3+ turns | turn in place at least 3 times | the offsets (Pinpoint), `trackWidthIn` + `perpForwardIn` (three wheels), `parallelLeftIn` + `perpForwardIn` (two). Not for an OTOS |
| 4 Loop and back | from a tape mark, drive anywhere and back onto it | `checked = true` when it ends within 1 in and 2 degrees |

The Spin test divides each pod's roll by the turn. It measures the turn with the Pinpoint's own heading, or
with the Control Hub's IMU for dead wheels. The Pinpoint's heading is not wrapped: it keeps counting past 360 degrees. The test prints only the numbers to change (for dead wheels: `.trackWidthIn(..).perpForwardIn(..)` or
`.parallelLeftIn(..).perpForwardIn(..)`). So `.reversed(...)` and the pod type stay as you wrote them. The
output:

```java
Robot.java: in the odometry(...) line, .offsetsMm(-84.2, -167.5)
Tunables.Odometry: linearScalar = 1.012   // times the one in use now  (OTOS only)
Tunables.Odometry: checked = true
```

For a reversed pod, the result says which name to add to (or take out of) `.reversed(...)`. While a test
runs, the screen shows the pose and the raw pod counts since the test began. Walk-through:
[finders guide](../tuning/09-odometry-tuner.md).

## MoveTunerOpMode

Use it after the Drive Tuner, to see how exactly autonomous moves stop and turns end. It suggests the
numbers in `Tunables.Drive` and uses each new number at once. It needs your robot and
`Tunables.Drive.calibrated = true`. The **Tune and check** menu has it. For your own file, put this next to
`Robot.java`:

```java
@TeleOp(name = "Move Tuner", group = "Finders")
public class MoveTuner extends MoveTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

For a robot with a `RobotSettings` class instead, write `@Override protected RobotConfig robotConfig() { return new RobotSettings(); }`.

D-pad up/down chooses a test, and **A** starts it. D-pad left/right sets the power of the moves and turns
(0.3 to 1.0, starts at 0.5). Use the power of your autonomous moves: a faster robot slides farther.
With an `odometry(...)` line, the tuner measures with the odometry. The odometry sees a wheel that skids.
Without it, the tuner measures with the wheel counts.

| Test | It does | It finds |
| --- | --- | --- |
| 1 Creep | raises the power slowly until the robot rolls (forward, then right on a mecanum) | `moveMinPower`: that power x 1.3, at least 0.10 |
| 2 Stops | moves of 6, 12 and 24 in, out and back, with `slowDownIn` 0, 3, 6, 9 and 12, until every move slides 0.5 in or less past its end | `slowDownIn`: the smallest that is good (or the best one) |
| 3 Turns | turns 90, 135 and 45 degrees left and back | one change per round: `turnMinPower` (0.05, 0.08, 0.11), then `turnKp`, when it stops off target; `turnToleranceDeg` + 0.5 when it rocks a little outside it; `turnKd` or less `turnKp` when it swings past; `turnKp` when it is slow |
| 4 Drift | 48 in forward and back (and right and left) | how much the heading changes on a straight move. A report, nothing to copy |
| 5 Move and turn (mecanum) | 24 in sideways while it turns 90 degrees (`moveWithTurn`), four times | `moveTurnScale` or `moveMaxTurnPower` |

Each move and turn WAITS while the robot moves, like autonomous. STOP still works. GUIDE (or the gamepad's
START button) stops a test at once and opens help. The tuner does not use that test's numbers. Between the
moves, nothing waits: the tuner waits for the robot to stand still loop by loop, then measures. Run a test
again until it says OK, then copy the lines. The output:

```java
c.slowDownIn = 6;   // RobotSettings.auto()
Tunables.Drive: slowDownIn = 6
c.turnKd = 0.0039;   // RobotSettings.auto()
Tunables.Drive: turnKd = 0.0039
// measured at power 0.6: these numbers fit autonomous moves at that power
```

## AimTunerOpMode

Use it to find the camera offset, so shots go in the middle. The offset is where the target sits in the
camera's view when the LAUNCHER points straight at it (degrees, left = +). Fill in these methods:

| Method | Required? | What it gives |
| --- | --- | --- |
| `createCamera()` | yes | your camera |
| `createTurret()` / `cameraOnTurret()` | no | aim with a turret (default: turn the robot) |
| `robotConfig()` | without a turret | to turn the robot, and to drive |
| `createAimController()` | no | the aiming gains (use your TeleOp's) |
| `createLauncher()`, `shotRpm()` | no | for test shots (with a table, it uses the camera's distance) |
| `startOffsetDeg()` | no | the offset in your code now |

| Button | Does |
| --- | --- |
| hold **A** | aim at the target |
| D-pad right/left, up/down | aim 0.5 / 2 degrees more right / left. **When shots go left of the middle, press RIGHT.** |
| **RB** / **LB** | shoot one / flywheel off |
| **X** | home the turret. A turret with a home switch does not aim before it is homed (`homeDirection()`, default -1) |
| sticks | drive when not aiming |

A camera to the SIDE of the launcher sees the target from a different angle at each distance. So it needs a
different offset at each distance. When the offset that works changes with distance, move the camera onto the
launcher's line. Or tune at the distance you shoot from most.

The output is `new AimController(...).offset(-1.5)` or `turret.cameraOffset(-1.5)`. With a turret, it also
gives `Tunables.Turret: cameraOffset = -1.5` (the section is the turret motor's name).

---

## ShotTunerOpMode

Use it to build the shot table: the speed (and hood angle) for each distance. For the robot in five files, it
needs one method. An example is the turret robot's
[ShotTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/ShotTuner.java):

```java
@TeleOp(name = "Shot Tuner", group = "Finders")
public class ShotTuner extends ShotTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

At INIT, it sets the robot up. It uses the robot's first `Launcher` (with its numbers from `Tunables.java`),
its first `Camera`, its odometry and its drive. Every button is on gamepad 1.

**The way it tunes:** at each distance, shoot 3-5 pieces at one speed, and mark each shot HIT (A) or MISS (B).
Then try another speed. X saves the speed with the best hit ratio (hits out of shots) at that distance.

**The distance** is an average over the last half second of fresh readings. It comes from the camera. When the
camera does not see the goal, it comes from the odometry (with an `odometry(...)` line and
`Tunables.Aim.goalX, goalY`). For the odometry, the robot starts at `startSpotIn()`, by default the middle of
the field facing away from you (see [Aim](../reference/parts/aim.md)). With neither, you set the distance by
hand. The screen says which one it uses. When it has both, it shows both. When they are far apart, move the
goal point.

**Several tables** (`speedByDistancePollen`, `speedByDistanceNectar`): press Y until `> Table`, then D-pad
left/right picks the one you tune. It starts on the launcher's start table. Each table keeps its own points
and gets its own line.

The tuner sets up the robot's other parts but does not move them. A turret stays still. The launcher's
`requireReady(...)` checks (like "turret aimed") are off, because you aim by hand here.

Without five files, fill in these methods:

| Method | Required? | What it gives |
| --- | --- | --- |
| `robot()` | no | the robot in five files: gives the three below |
| `createLauncher()` | without `robot()` | your [`Launcher`](launcher.md), as in TeleOp (with a hood, it tunes the hood too) |
| `createCamera()` | no | the camera gives the distance; null = set the distance by hand |
| `findTarget(camera)` | no | which target to use. Default: the first one with a distance |
| `robotConfig()` | no | lets you drive with the sticks between distances |
| `createSubsystems()` | no | other mechanisms to keep updated (an intake that loads it) |
| `startRpm()` | no | the starting speed; default (NaN) = the table's speed at the distance seen, else `Tunables.Launcher.speedRpm`, else 3000 |
| `startHoodDeg()` | no | the starting hood angle; default (NaN) = the table's hood at the distance seen (until you change it), else the robot hood's `startAngle`, else 0 |
| `startSpotIn()` | no | where the robot is at INIT, for the odometry: {x, y, heading} from your drivers' wall; default {0, 0, 0} |
| `tableNames()` | no | the tables to tune; default the launcher's (`pollen`, `nectar`...), or one table `main` |

| Button | Does |
| --- | --- |
| **Y** | what the D-pad changes: SPEED, HOOD (with a hood), DISTANCE (no camera, no odometry), TABLE (two or more tables) |
| D-pad up/down, right/left | big / small step (100 / 10 RPM; the hood: a fifth / a fiftieth of its range, 5 / 0.5 deg for 0-25 deg; 6 / 1 in) |
| **LB** | flywheel on/off (it is OFF at ▶) |
| **RB** / hold **RT** | shoot one / keep shooting (let go to stop). Keep shooting also stops after a jam or a "gave up": press RT again to go on |
| **A** / **B** | the oldest shot not marked yet was a HIT / a MISS (at the speed, hood angle and distance it was fired with) |
| left stick click | undo the last HIT / MISS: that shot waits to be marked again |
| right stick click | drop the last shot not marked yet (a "shot" with no piece: the wheel dipped by itself) |
| **X** | save the speed with the best hit ratio at this distance. It replaces a saved point within 1 in. With no shots marked, it saves the speed now |
| hold **BACK** 1 s | delete the saved point here. It needs a hold, so nobody deletes it by mistake |
| sticks, left trigger | drive, slow (with `robotConfig()`) |

The speed stays at or below the flywheel's `maxRpm`, and the hood stays inside its limits. A warning shows
when the D-pad reaches one. So a saved point is what the robot tested. The screen shows:

- the target and measured speed, and whether it is ready (or why not)
- the shots fired, and how many are still to mark
- `Hits at 2550: 3/4`, and every speed tried at this spot
- every table's point count (`Points: POLLEN 3, NECTAR 0  (4-6 per table)`)
- the recovery time, the distance and bearing, and the saved points.

The tuner never uses the launcher's speed trim, because the table holds the real speeds. The launcher's
battery compensation stays on. Each shot keeps the flywheel speed **measured** when the piece was fed. When
that speed is more than `sagLimitRpm` under the speed asked for, the tuner flags the shot
"sagged". `sagLimitRpm` is 60 RPM or 3% of the speed asked for, whichever is larger. A sagged shot means
that the wheel had not got back to speed after the last shot, or that the battery is low. With a hood,
each speed and hood angle is its own try. X saves the winner with the hood angle it was fired with. The hit
counts start again after a save, on another table, and when the distance moves more than `SPOT_MOVE_IN`
(4 in). The tuner drops shots fired at the old spot that are not marked. The output:

```java
new ShotTable()
    .add(24.0, 2600, 6.0)   // 4/5 in, measured 2588 RPM
    .add(48.5, 3100, 14.0)   // 3/4 in, measured 3079 RPM
    .add(72.0, 3700, 21.0)   // 4/4 in, measured 3672 RPM
```

It also shows the line for five files, one per table, in the launcher's section. With a hood, each row has the
hood as its third number, in the hood's scale (degrees, or a servo position):

```java
public static double[][] speedByDistance = {{24, 2600}, {48.5, 3100}, {72, 3700}};   // in Tunables.Launcher
public static double[][] speedByDistanceNectar = {{24, 2800, 4}, {48.5, 3300, 12.5}};   // a named table, with a hood
```

Copy each line into `Tunables.java`, in place of the line with the same name. The robot reads the file, not
this screen. At STOP, the tuner also saves the lines on the Control Hub. The screen says what to do next
(`Next: LB to spin up the flywheel.`).

## CameraFixCheckOpMode

`CameraFixCheckOpMode` (**alpha**) is the standing test for the [camera fix](odometry.md#camera-fix-alpha). It
finds `Tunables.CameraFix` `turnDegBlue` / `turnDegRed`. It also shows where the camera puts the robot, so you
can check it with a tape. It needs `robot()`. Tune and check has it for a robot with `cameraFix(camera)`.

## PieceTunerOpMode

Use it to see what each game-piece sensor reads, and to measure each kind's color (or size) and the "empty"
line. It needs your robot in five files, with a [`Pieces` or `PieceSensor`](../reference/parts/pieces.md)
line. Put this file next to `Robot.java`:

```java
@TeleOp(name = "Piece Tuner", group = "Finders")
public class PieceTuner extends PieceTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

Nothing moves.

- **D-pad up/down** chooses the sensor.
- **D-pad left/right** chooses what is in front of it: each kind, then EMPTY.
- **A** measures it: 20 readings, about half a second. Hold the piece still, where it sits on the robot.
- **B** forgets that sensor's measurements.

The screen shows the live reading and the kind the sensor sees now. The reading is the hue (the color, as an
angle in degrees), the saturation (how strong the color is) and the distance. Until a kind's line is in
`Tunables.java`, the sensor reads that kind as `UNKNOWN`. Measure every kind and EMPTY. The lines to copy:

```java
Tunables.IntakeColor: POLLEN = 74   // its color (hue, degrees)
Tunables.IntakeColor: RED_NECTAR = 6   // its color (hue, degrees)
Tunables.IntakeColor: hueTolerance = 34
Tunables.IntakeColor: nearMm = 52   // a piece is there below this
```

The tuner warns when two kinds read too close together (under 15 degrees, or 10 mm for a distance sensor). It
also warns when EMPTY looks like a piece. Then aim a distance sensor at a far wall, or put a gray background
behind a camera's spot. Copy the lines into `Tunables.java` and rebuild. A value changed only in the tuning
menu and saved there (**A**) stays on this hub, and it wins over a later edit in `Tunables.java`. After you
copy it into `Tunables.java`, put the menu back to the code values (**Y**).

## CharacterizerOpMode

Use it to measure how the real robot moves. This is called **characterizing** the robot. The numbers have two
uses. In your code, they give **feedforward** (the power a motor needs for a speed, see
[Feedforward](control.md#feedforward)). In the simulator, they are the **real numbers** in its robot file. It needs `robotConfig()`, and `mechanisms()` for
lifts, arms and flywheels:

```java
@TeleOp(name = "Characterizer", group = "Finders")
public class Characterizer extends CharacterizerOpMode {
    protected RobotConfig robotConfig() { return new RobotSettings(); }
    protected List<Mechanism> mechanisms() {
        return Arrays.asList(Mechanism.lift("lift", 0, 2600),   // soft limits: counts from the bottom
                Mechanism.lift("arm", 0, 1100), Mechanism.flywheel("flywheel"));
    }
}
```

The D-pad chooses a test, **A** runs it, and **B** stops it at once.

| Test | It does | Space and safety |
| --- | --- | --- |
| Drive | power ramps up slowly forward (to 0.4) and back, then quick steps (0.5, 0.6 s) | about 5 ft clear in front and behind. Each roll ends at `maxDriveCounts()` (default 2500 counts: about 5 ft with 312 rpm motors and 104 mm wheels, 4.6 ft with 96 mm wheels). `driveRampMax()` (at most 0.5) lowers the speed |
| Turn (needs the IMU) | the same, turning in place | room to turn |
| Lift/arm | slowly up to 60% of its range, slowly down, quick steps up and down, then back to the bottom | start it at the BOTTOM, because the soft limits (the limits in your code) count from there. It stops and says why when it goes past them. Add `.reversed()` when positive power moves it down |
| Flywheel | ramps up to full power, coasts, full power, coasts | keep hands and pieces away |

The Characterizer stops itself and says why when:

- a wheel or motor turns the wrong way for its power (a direction setting is wrong; it checks each wheel)
- a lift goes past a soft limit, or counts down while it goes up
- something pushes but does not move for 0.7 s (jammed, against a hard stop, or an encoder cable in another
  motor's port)
- someone presses GUIDE or the gamepad's START.

A drive roll that reaches `maxDriveCounts()` ends early, and the test goes on with the next part.

What each part of a test measures:

- A slow ramp: the power needed to start moving (kS) and the power per speed (kV).
- A quick step: the power for speeding up (kA). Spinning wheels spoil this number, so run the drive
  test on tiles, not on a smooth floor.
- A lift: also the power that holds it against gravity (kG).
- A flywheel's coasting: how long it takes to slow down.

The results (the Characterizer saves them with the other finders' results):

```text
# simulator robot file:
drive.kS = 0.052
drive.maxTicksPerSec = 2776
drive.accelSec = 0.176
Feedforward driveFf = new Feedforward(0.05230, 0.0003602, 6.329e-05);   // wheels, counts per second
drive.turnDegPerCount = 0.08012
# add to motor.lift in the robot file:
kS=0.041, maxTicksPerSec=2792, accelSec=0.087, gravity=0.060
```

The Characterizer saves each test's log in the results folder as `characterizer/<test>[-<motor>]-<yyyyMMdd-HHmmss>.csv`
(for example `lift-lift-20261021-101500.csv`). A test stopped early saves its log too, marked "(stopped
early)". The simulator's fit tool reads the logs and writes the robot file for you
(`./gradlew :sim:fit -Plog=... -Pwrite`, see the [simulator guide](../simulator/03-make-it-match-your-robot.md#6-real-speeds-optional)).
Run each test 2-3 times on a charged battery. The fit tool fits the logs of the same test together. It also
gives r2, a score from 0 to 1 for how well the numbers match the measurements. Below 0.9 it says "poor: run it
again"; below 0.5 it says "Could not fit". The Characterizer measures arms like lifts: it takes the pull of
gravity as the same at every angle. So the numbers are a middle value for an arm. **For an arm, set the high
limit below straight up**, so gravity always pulls it back down. Past straight up, gravity pulls it on to the
far side, and a motor's brake does not hold a heavy arm.

## CharacterizeMath

The fitting math, with no hardware. `fit(t, volts, power, speed, gravity)` gives kS, kV, kA (and kG) and r2.
It uses least squares (it finds the numbers with the smallest total error) over the moments when the motor
was moving and powered. It smooths the speeds over 30 ms on each side, or over 2.5 loops on a slower loop. It
leaves out the moments within that window plus 20 ms of a jump in power. The other methods: `coastSec(...)`,
`turnDegPerCount(...)`, `yawRates(...)`, `accelerations(...)`, `smoothed(...)`, `leastSquares(...)`.
It scales the powers to 12.8 V with the logged battery voltage.

## FinderOpMode

To write your own finder, extend `FinderOpMode`. It is the shared frame of every finder. INIT: `setup()`,
`showInit()`. The loop: `bind()` once, then `step()` and `show()` every loop, plus the results. The end:
`stopAll()`, always. When the finder stops, it saves the results to the file, and the screen says
whether that worked. `runOpMode()` is `final`.

| Field / method | What it is |
| --- | --- |
| `t` | the [RobotTelemetry](telemetry.md) |
| `results` | the [FinderResults](#finderresults) |
| `finderName()`, `setup()`, `bind(controls)`, `step()`, `show(t)`, `stopAll()` | fill these in |
| `helpLines()` | optional, but please write it: the help page, at most 13 lines of about 50 characters. Default: "No help page for this finder yet." |
| `showInit(t)` | optional: what INIT shows |
| `pause()` | optional: what stops while help is open (default `stopAll()`). Override it when `stopAll()` also closes a camera |
| `inputsReady()` | false after ▶ and after help, until gamepad 1 was let go. Check it before you act on a stick, trigger or held button that you read directly |
| `openHelp()` | opens the help page (for example, when a help key stopped a test) |
| `helpKeyDown()` | whether the gamepad's GUIDE or START is down (for example, as a stop condition for a moving test) |

The gamepad's GUIDE and START are the help keys in every finder, so a finder must not bind them itself.

## FinderResults

| Method | What it does |
| --- | --- |
| `set(key, line)` | sets or replaces the line with this key |
| `remove(key)`, `removeStartingWith(start)` | removes lines |
| `lines()`, `isEmpty()` | read the lines |
| `addTelemetry(t)` | shows them under "Copy into your code" |
| `saveNow()` | adds them to `/sdcard/FIRST/ftc-helper/finder-results.txt` now (FinderOpMode does this at STOP and shows the result) |
| `saveInBackground(ok -> ...)` | the same on a background thread |
| `FinderResults.num(value, decimals)` | a number as text, always with "." |
| `FinderResults.tunablesLine(deviceName, fields)` | a line for five files: `tunablesLine("lift", Arrays.asList("min = 0", "max = 2580"))` gives `Tunables.Lift: min = 0, max = 2580` |

## FinderMath

`flywheelPidf(topTicksPerSec)`, `ticksPerSecToRpm(ticksPerSec, countsPerRev)`,
`wheelDiameterFromPush(ticks, inches, ticksPerRev, gearRatio)`,
`correctedDiameter(old, asked, measured)`, `correctedStrafe(old, asked, measured)`,
`limits(lowEndTicks, highEndTicks)` (-> min, max, reversed), `pushTicks(deltas)` (the push test's
count, NaN when the wheels disagree), `servoPosition(p)` (kept in 0..1).

## Chooser

A D-pad list: `new Chooser(names)` (sorted A to Z), `next()`, `previous()`, `current()`,
`isEmpty()`, `addTelemetry(t, emptyText)`.

## NudgeStep

The step size that the servo finders (Servo, Feed Stop, Hood) nudge by. D-pad up/down moves the servo by the
step, and D-pad left/right makes the step smaller / bigger. The sizes (`NudgeStep.SIZES`) are 0.1, 0.05, 0.01,
0.005 and 0.001. It starts at 0.05. A big step gets close fast. The smallest step finds an exact spot:
0.001 is about a quarter of a degree on a 270 degree servo.

| Method | Does |
| --- | --- |
| `size()` | the step now |
| `smaller()` / `bigger()` | the next step size (stops at the ends of the list) |
| `reset()` | back to 0.05 |
| `move(position, direction)` | the position one step up (`+1`) or down (`-1`), rounded to 0.001, kept in 0..1 |
| `label()` | the step as text, for example `"0.005"` |

```java
NudgeStep step = new NudgeStep();   // 0.05
step.smaller();                     // 0.01
double p = step.move(0.500, +1);    // 0.510
```

## ShotPoints

The shot tuner's saved points. Make it with `new ShotPoints(hasHood)`, or with
`new ShotPoints(hasHood, hoodDecimals)`: 1 for degrees, 3 for servo positions, 0 for ticks.
`save(distance, rpm, hoodDeg)` replaces a point within `SAME_DISTANCE` = 1. The other methods:
`removeNear(distance)`, `points()` (sorted by distance, shortest first), `codeLines()` (the `ShotTable` code), and
`tunablesLine(section)`. That gives the five-files line,
`public static double[][] speedByDistance = {{48, 2550}, ...};   // in Tunables.Launcher`, or with a hood
`{{48, 2550, 12.5}, ...}`. It is empty when nothing is saved.
`tunablesLine(section, field)` does the same for a named table, for example `speedByDistanceNectar`.
`save(distance, rpm, hoodDeg, measuredRpm, hits, shots)` also keeps how many went in and the measured speed
(`saved()`, `size()`, `note(saved)`; `codeLines()` writes them as a comment).

`ShotPoints.Tally` counts hits and shots per try at one spot. A try is a speed (and a hood angle, with a hood).
Its methods: `mark(rpm, hit, measuredRpm)`, `mark(rpm, hoodDeg, hit, measuredRpm)`, `unmark(...)` (an undo),
`hits(rpm)` / `hits(rpm, hoodDeg)`, `shots(...)`, `totalShots()`, `measuredRpm(...)`, `best()`
({rpm, hood}, null with no hit), `bestRpm()` (the best hit ratio; on a tie, more shots wins, then the lower
speed; NaN with no hit), `tries()`, `summary()` (`2450: 1/3, 2550: 3/4 (best)`; with a hood
`2550 @ hood 10: 3/4`), `spotIn()`, `setSpot(in)`, `isEmpty()`, `clear()`.

`ShotPoints.Trials` holds the shots at one spot that wait to be marked. Each shot keeps the speed, hood and
distance it was fired with. Its methods: `fired(rpm, hoodDeg, measuredRpm, distanceIn)`, `mark(hit)` (the
oldest waiting shot), `undo()` (the last mark; that shot waits again), `dropLast()` (the newest waiting shot),
`check(distanceIn)` (when the distance moved more than `SPOT_MOVE_IN`, marks start again and old shots are
dropped), `unmarkedCount()`, `tally()`, `clear()`. Each one returns the words the tuner shows.

`ShotPoints.DistanceAverage` averages the distance over the last `WINDOW_SEC` (0.5 s) of fresh readings.
Its methods: `add(nowSec, distance, from)` (a different `from` starts the average again), `average(nowSec)`
(NaN with no readings), `count(nowSec)`, `source()`.
