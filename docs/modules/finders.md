# Finders module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.finders`

Learn it hands-on: [21 Find your numbers](../tutorials/21-finders.md). Step by step on the robot:
[Finders guide](../guides/finders.md). Quick answers: [cookbook: tuning and finders](../cookbook.md#tuning-and-finders).

Ready-made programs that **measure** the numbers your code needs (servo ends, lift limits, flywheel
gains, wheel size, odometry offsets, shot speeds, camera offset) and show them as **lines of code to copy**. Use one
whenever a part is new or was rebuilt, before you tune by hand. They also save those lines on the
Control Hub in `/sdcard/FIRST/ftc-helper/finder-results.txt` when you press STOP, so nobody copies
numbers from the screen by hand. Start with tutorial 21, or [lesson 4](../tutorials/start/04-lift-and-claw.md) for the robot in five files.

Each finder shows its numbers in two forms: code for the older helpers (`PositionServo.create(...)`,
`PositionMotor.create(...)`, `c.wheelDiameterIn = ...`) and lines for the
[robot in five files](robot.md): `Tunables.Lift: min = 0, max = 2580, P1 = 1200` means "in
`Tunables.java`, section `Lift`, set `min = 0`, `max = 2580`, `P1 = 1200`". The section is the
device's name in CamelCase (`lift` -> `Lift`, `intake_deploy` -> `IntakeDeploy`,
[`RobotDefinition.sectionName`](robot.md)), unless you gave the part its own section with
`.tunables("Name")` in Robot.java: then copy the numbers into that section. Copy the lines that fit your code.

| Class | Use it to | Needs code from you? |
| --- | --- | --- |
| [`ServoFinderOpMode`](#servofinderopmode) | find a servo's safe ends and positions | no |
| [`MotorFinderOpMode`](#motorfinderopmode) | find a lift's, arm's or turret's limits, positions and top speed | no |
| [`FlywheelFinderOpMode`](#flywheelfinderopmode) | find a flywheel's top speed, speed-control gains (the tuning numbers) and recovery time | no |
| [`DriveTunerOpMode`](#drivetuneropmode) | find the wheel size and strafe correction for autonomous | `robot()` (five files) or `robotConfig()` |
| [`OdometryTunerOpMode`](#odometrytuneropmode) | check which way each odometry pod counts and measure where it sits (the offsets); a loop check sets `checked` | `robot()` (five files, with an `odometry(...)` line) |
| [`PieceTunerOpMode`](#piecetuneropmode) | see what each game-piece sensor reads, and measure each kind's colour (or size) and the "empty" line | `robot()` (five files, with a `Pieces` or `PieceSensor` line) |
| [`HoodFinderOpMode`](#hoodfinderopmode) | find a launcher hood's two stops (`posA`, `posB`) and, if you measure them, its angles (a linkage's calibration table) | `robot()` (five files) or `hoodName()` |
| [`ShotTunerOpMode`](#shottuneropmode) | build the shot table: distance -> speed (and hood angle) | `robot()` (five files), or your launcher (+ camera) |
| [`AimTunerOpMode`](#aimtuneropmode) | find the camera offset so shots go in the middle | your camera (+ turret or drive) |
| [`CharacterizerOpMode`](#characterizeropmode) | measure how the drive, lifts and flywheels really move (feedforward numbers, and the simulator's robot file) | `robotConfig()`, `mechanisms()` |
| [`FinderOpMode`](#finderopmode) | the shared frame of every finder (for writing your own) | |
| [`FinderResults`](#finderresults) | the code lines a finder shows and saves | |
| [`FinderMath`](#findermath) | the arithmetic behind the finders | |
| [`Chooser`](#chooser) | a list to choose from with the D-pad | |
| [`ShotPoints`](#shotpoints) | the saved good shots of the shot tuner | |
| [`CharacterizeMath`](#characterizemath) | the math behind the Characterizer (it fits numbers to the measurements) | |

Each finder is a TeleOp. To use one, make a tiny file in TeamCode (the [starter kit](../starter-kit.md)
has them ready):

```java
@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode { }
```

**GUIDE = help**, in every finder, in INIT or while it runs: a page with every button and the steps.
GUIDE is the center button (Logitech/Xbox logo, PlayStation PS button). Some controllers have none,
or the Driver Hub keeps it for itself: then **hold START for 1 second**. While help is open, the
finder's buttons pause and its motors stop (servos stay where they were sent; a camera keeps running).
After START, and after help closes, **let go of every stick, trigger and button on gamepad 1** once:
until then the finder ignores them, so a finger that was already down cannot start anything.

Safe in every finder:

- Nothing moves in INIT.
- The loop never waits (except the drive tuner's test moves, like autonomous).
- Everything it moved is stopped at the end, even after an error.

---

## TuneAndCheckOpMode

`TuneAndCheckOpMode` is the library's own OpMode, **"Tune and check"** (group "Finders"): a menu in INIT with the Hardware Check
and the finders that fit Robot.java. A opens a tool (its INIT screen), START runs it, BACK in the tool's
INIT goes back to the menu. It finds the robot classes in `org.firstinspires.ftc.teamcode` (on the robot:
the app's classes, as the SDK finds OpModes; in the simulator: the class path). Not in it: the Aim Tuner
and the Characterizer. Steps: [the finders guide](../guides/finders.md#tune-and-check-all-of-them-in-one-opmode).

| Member | What it does |
| --- | --- |
| `NAME` | `"Tune and check"`, the Driver Station name |
| `useRobots(list)` | uses these robot classes instead of looking (the simulator's tests); `null` = look again |

`Hosting` runs a finder inside it: a finder's `inInit()` and `running()` are the host's INIT and START.
A finder of your own that loops by itself uses `running()`, not `opModeIsActive()`.

## ServoFinderOpMode

Lists every servo in the configuration. Nudge the chosen one and mark what you find.

| Button | Does |
| --- | --- |
| D-pad up/down (list) | choose a servo; **A** = use it |
| **Y** (list) | full range on/off: 500-2500 us instead of the SDK's normal 600-2400 (a goBILDA 2000 servo turns 300 degrees, not 270). Turn it on when the part has [`.fullRange()`](robot.md#claw) in Robot.java, so the numbers fit. The lines then say `.fullRange()` |
| D-pad, then **A** (first) | choose a START POINT near where the servo sits now (it does not move), then **A** moves it there |
| D-pad right/left | +/- 0.01 (hold **RB**: 10 times finer) |
| D-pad up/down | +/- 0.05 |
| **X** / **B** | mark one END / the other END (where it just stops pushing on the frame). Then it stays between them (hold **LB** to go past, e.g. to mark again) |
| **A** | save this as a position (P1, P2...; rename them in your code) |
| **Y** | undo the last position |
| **BACK** | choose another servo (its lines stay) |

A servo cannot report where it is, so nothing moves until you press A at the start point. A
position saved outside the marked ends gets a note in its line. Output:

```java
PositionServo.create(hardwareMap, "claw").limits(0.180, 0.770)
    .position("P1", 0.210)
    .position("P2", 0.740)
Tunables.Claw: P1 = 0.210, P2 = 0.740
    // rename P1, P2... (open, closed); keep them between 0.180 and 0.770
```

In five files: `public static double open = 0.210, closed = 0.740;` in `Tunables.Claw`. A servo
module has no ends of its own, so the ends become a reminder.

## MotorFinderOpMode

Lists every motor. **The count starts at 0 when you choose the motor: put the mechanism at its bottom first.**

| Button | Does |
| --- | --- |
| D-pad up/down (list) | choose a motor; **A** = use it (brake, count from 0) |
| right stick Y | move, at most "max power" |
| D-pad up/down | max power +/- 0.05 (0.1 to 0.6; starts at 0.3) |
| **X** / **B** | mark the LOW end / the HIGH end. Then it stops at them (hold **LB** to go past) |
| **A** / **Y** | save a position / undo the last one |
| hold **RT** | speed test: full power toward the HIGH end, stops 15% of the range before it |
| **BACK** | choose another motor |

The screen shows the count, speed (counts/s) and current (amps: a stalled motor draws a lot).
If the HIGH end has a smaller count than the LOW end, the motor counts down as it goes up: the
output adds `.reversed()` and flips the signs. Output:

```java
PositionMotor.create(hardwareMap, "lift").limits(0, 2580)
    .position("P1", 1200)
    // top speed up: 2650 counts/s
Tunables.Lift: min = 0, max = 2580, P1 = 1200
    // encoder counts: right for a Lift; an Arm's numbers are degrees
```

In five files: `min = 0, max = 2580` and a named height such as `low = 1200` in `Tunables.Lift`.
A reversed motor also gets `Robot.java: add .reversed() to the "lift" part`. An `Arm` reads degrees
from `Tunables.java`, so divide its counts by the counts per degree (motor counts per turn x gear
ratio / 360).

## FlywheelFinderOpMode

Lists every motor. Keep hands away; eye protection on.

| Step | Button | Does |
| --- | --- | --- |
| 1 | hold **A** | full power (at most 5 s): measures the top speed, works out starting gains. (The A press that chose the motor does not count: let go and press again.) |
| 2 | **B** | hold a speed on/off (the library's `Flywheel`, with those gains) |
| 2 | D-pad up/down, right/left | target +/- 100 / 25 RPM (starts at 60% of top speed) |
| 2 | **RB** / **LB** | F gain +2% / -2% (F does most of the work: too slow everywhere = more F) |
| 1, 2 | **X** | the wheel spins the wrong way: reverse it (adds `.reversed()`) |

If the encoder counts BACKWARD at forward power, it warns (check the encoder cable and port) and step
2 is not allowed: speed control with a backward encoder would run away.

Feed a piece in step 2 (with the robot's feeder or a push stick, never fingers near the wheel): it shows
the **recovery time** (shot -> ready again).
The gains: F = 32767 / top speed (counts/s), P = F / 10, I = P / 10, D = 0 (a common starting
point for REV hubs). `countsPerRev()` is 28 (the motor's own encoder, wheel on the motor shaft);
override it for a geared wheel. Output:

```java
Flywheel.create(hardwareMap, "flywheel").countsPerRev(28.0).pidf(1.17, 0.117, 0, 11.70).maxRpm(5400)
    // top speed 6000 RPM; maxRpm = 90% of it
    // recovery after a shot at 3600 RPM: 0.38 s
    // tuned at 12.6 V: add .batteryCompensation(Hw.batteryReader(hardwareMap), 12.6)
Tunables.Flywheel: kP = 1.17, kI = 0.117, kD = 0, kF = 11.70, nominalVolts = 12.6, shotIntervalSec = 0.45
```

The `Tunables` line is for a [`Launcher`](robot.md) module on that motor (here a motor named
`flywheel`; the lessons call it `launcher`, so `Tunables.Launcher`). `shotIntervalSec` is only there
when the recovery is longer than the default 0.25 s. `nominalVolts` is the battery voltage while the
top speed was measured (the screen shows `Battery: 12.61 V (gains for 12.6 V)`): the gains are right for
that voltage, and the launcher's [battery compensation](launcher.md#battery-compensation) (off unless turned on) scales F from
it. Step 2 runs with that compensation, so an F you fine-tune there is for the same voltage. Under
11.5 V it warns: charge the battery first.

## DriveTunerOpMode

Needs your robot. The robot in five files (next to `Robot.java`; the lesson robot's
[DriveTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/DriveTuner.java)):

```java
@TeleOp(name = "Drive Tuner", group = "Finders")
public class DriveTuner extends DriveTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

With a `RobotSettings` class instead: `@Override protected RobotConfig robotConfig() { return new RobotSettings(); }`.

D-pad chooses a test, **A** starts it.

| Test | You do | It finds |
| --- | --- | --- |
| Push | roll the robot BY HAND exactly 48 in along a tape, press **B** | the wheel size, from the counts. Every wheel must count up about the same; if not, it says so (a wrong motor direction or encoder cable) |
| Drive | it drives 48 in; measure, set the real distance (D-pad +/- 1 / 0.25 in), **A** | a corrected wheel size |
| Strafe (mecanum) | it drives 48 in right; measure, set it, **A** | a corrected strafe correction |
| Turn | it turns 90 left; check with a square | (the IMU's heading, to compare) |

The Drive, Strafe and Turn tests WAIT while the robot moves, like autonomous (STOP still works).
A move that ends STALLED or TIMED_OUT is not measured: clear the path and run it again. GUIDE (or
START) stops a moving test at once and opens help; that test is not measured either.
Each new number is used at once inside the tuner (no rebuild): run Drive and Strafe again until
they measure 48, then copy the lines into your code.
`testDistanceIn()` changes the 48. Output:

```java
c.wheelDiameterIn = 3.812;   // RobotSettings.auto()
Robot.java: .wheelMm(96.8)   // in the drive(...) line
c.strafeCorrection = 1.180;   // RobotSettings.auto()
Tunables.Drive: strafeCorrection = 1.180
```

Five files: the wheel size goes in the drive line of `Robot.java` (`.wheelMm(96.8)`, millimetres),
the strafe correction in `Tunables.Drive`. Then `Tunables.Drive.calibrated = true`.

## OdometryTunerOpMode

Needs your robot in five files, with an `odometry(...)` line ([odometry](odometry.md)). Next to
`Robot.java` (the odometry robot's
[OdometryTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/OdometryTuner.java)):

```java
@TeleOp(name = "Odometry Tuner", group = "Finders")
public class OdometryTuner extends OdometryTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

The sticks drive slowly (30%) in every test. D-pad up/down chooses a test, **A** starts it (the pose
is set to 0, 0, 0), **B** ends it and shows the result.

| Test | You do | It finds |
| --- | --- | --- |
| 1 Forward 48 in | push or drive 48 in straight forward along a tape | the forward pod's direction, how far off the distance is; OTOS: `linearScalar` |
| 2 Left 24 in | push or drive 24 in to the left | the sideways pod's direction |
| 3 Spin 3+ turns | turn in place at least 3 times | the offsets (Pinpoint), `trackWidthIn` + `perpForwardIn` (three wheels), `parallelLeftIn` + `perpForwardIn` (two). Not for an OTOS |
| 4 Loop and back | from a tape mark, drive anywhere and back onto it | within 1 in and 2 degrees: `checked = true` |

The Spin test divides each pod's roll by the turn: the Pinpoint's own heading (not wrapped), or the
Control Hub's IMU for dead wheels. It prints only the numbers to change (dead wheels:
`.trackWidthIn(..).perpForwardIn(..)` or `.parallelLeftIn(..).perpForwardIn(..)`), so `.reversed(...)` and
the pod type stay as written. Output:

```java
Robot.java: in the odometry(...) line, .offsetsMm(-84.2, -167.5)
Tunables.Odometry: linearScalar = 1.012   // times the one in use now  (OTOS only)
Tunables.Odometry: checked = true
```

A reversed pod: the result says which name to add to (or take out of) `.reversed(...)`. While a test
runs, the screen shows the pose and the raw pod counts since it began. Walk-through:
[finders guide](../guides/finders.md#odometry-tuner-directions-offsets-and-a-loop-check).

## PieceTunerOpMode

Needs your robot in five files with a [`Pieces` or `PieceSensor`](robot.md#pieces-and-piecesensor)
line. Next to `Robot.java`:

```java
@TeleOp(name = "Piece Tuner", group = "Finders")
public class PieceTuner extends PieceTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

Nothing moves. **D-pad up/down** chooses the sensor, **D-pad left/right** what is in front of it (each
kind, then EMPTY), **A** measures it (20 readings, about half a second: hold the piece still, where it really sits on
the robot), **B** forgets that sensor's measurements. The screen shows the live reading (hue and
saturation, distance) and the kind it sees now. Until a kind's line is in `Tunables.java`, the
sensor reads that kind as `UNKNOWN`. Measure every kind and EMPTY; the lines to copy:

```java
Tunables.IntakeColor: POLLEN = 74   // its colour (hue, degrees)
Tunables.IntakeColor: RED_NECTAR = 6   // its colour (hue, degrees)
Tunables.IntakeColor: hueTolerance = 34
Tunables.IntakeColor: nearMm = 52   // a piece is there below this
```

It warns when two kinds read too close together (under 15 degrees, or 10 mm for a distance sensor),
or when EMPTY looks like a piece (aim a distance sensor at a far wall; put a grey background behind a
camera's spot). Copy the lines into `Tunables.java` and rebuild: a value changed only in the tuning
menu is lost at the next INIT.

## HoodFinderOpMode

Finds `Tunables.Hood`'s numbers for a launcher's [hood](robot.md#hood). With the robot in five files it
reads the hood from Robot.java (its name, servo or motor, `reversed()`, `fullRange()`), so it works before
`Tunables.Hood` has any numbers (the turret robot's
[HoodFinder.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/HoodFinder.java)):

```java
@TeleOp(name = "Hood Finder", group = "Finders")
public class HoodFinder extends HoodFinderOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

Without five files it uses the servo (or motor) called `hoodName()` ("hood").

| Button | Does |
| --- | --- |
| D-pad (servo) | before the first move: a start point (nothing moves), **A** moves there. Then right/left +/- 0.01, up/down +/- 0.05, hold RB 10x finer |
| (motor) | nothing is powered: move the hood by hand and watch the encoder (0 = where it was at INIT, as in a match) |
| **X** / **B** | this is stop A / stop B (a hair off it: a straining servo hums) |
| hold **LB** + D-pad | the angle you measured here (up/down +/- 1, right/left +/- 0.1), before X, B or A |
| **A** | save a calibration point here {position, angle} |
| **Y** | undo the last point |

Output: `Tunables.Hood: posA = 0.205, posB = 0.648` (and `angleA = 0, angleB = 25` when you gave the
angle at both stops); with 3 or more points, `public static double[][] calibration = {{0.205, 0}, {0.33, 9}, ...};`
for a linkage (it replaces posA/posB). Copy them into `Tunables.java`: the robot uses the file, not this
screen. A number changed in the tuning menu (`Hood/posA`) is also lost at the next restart unless you
copy it back into `Tunables.java`.

## ShotTunerOpMode

Builds the shot table. The robot in five files needs one method (the turret robot's
[ShotTuner.java](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/ShotTuner.java)):

```java
@TeleOp(name = "Shot Tuner", group = "Finders")
public class ShotTuner extends ShotTunerOpMode {
    @Override protected RobotDefinition robot() { return new Robot(); }
}
```

It sets the robot up at INIT and uses its first `Launcher` (with its numbers from `Tunables.java`),
its first `Camera`, its odometry and its drive. **The way it tunes**: at each distance, shoot 3-5 at a
speed and mark each shot HIT (A) or MISS (B); try another speed; X saves the speed with the best hit
ratio (hits out of shots) there. Every button is on gamepad 1. **The distance** (averaged over the last half second of fresh readings): the camera's, or, when it does not
see the goal, the odometry's (with an `odometry(...)` line and `Tunables.Aim.goalX, goalY`; the robot
starts at `startSpotIn()`, default the middle of the field facing away from you; see
[robot.md: Aim](robot.md#aim)), or set by hand with neither. The screen says which, and shows both when
both are there (far apart: move the goal point). **Several tables** (`speedByDistancePollen`,
`speedByDistanceNectar`): Y until `> Table`, D-pad left/right picks the one you tune; it starts on the launcher's
start table. Each table keeps its own points and gets its own line. Its other parts are set up but not moved: a turret stays still, and
the launcher's `requireReady(...)` checks (like "turret aimed") are off, because you aim by hand here.

Without five files, fill in:

| Method | Required? | |
| --- | --- | --- |
| `robot()` | no | the robot in five files: gives the three below |
| `createLauncher()` | without `robot()` | your [`Launcher`](launcher.md), as in TeleOp (with a hood, it tunes the hood too) |
| `createCamera()` | no | the camera gives the distance; null = set the distance by hand |
| `findTarget(camera)` | no | which target; default: the first with a distance |
| `robotConfig()` | no | drive with the sticks between distances |
| `createSubsystems()` | no | other mechanisms to keep updated (an intake that loads it) |
| `startRpm()` | no | the starting speed; default (NaN) = the table's speed at the distance seen, else `Tunables.Launcher.speedRpm`, else 3000 |
| `startHoodDeg()` | no | the starting hood angle; default (NaN) = the table's hood at the distance seen (until you change it), else the robot hood's `startAngle`, else 0 |
| `startSpotIn()` | no | where the robot is at INIT, for the odometry: {x, y, heading} from your drivers' wall; default {0, 0, 0} |
| `tableNames()` | no | the tables to tune; default the launcher's (`pollen`, `nectar`...), or one table `main` |

| Button | Does |
| --- | --- |
| **Y** | what the D-pad changes: SPEED, HOOD (with a hood), DISTANCE (no camera, no odometry), TABLE (two or more tables) |
| D-pad up/down, right/left | big / small step (100 / 10 RPM; the hood: a fifth / a fiftieth of its range, 5 / 0.5 deg for 0-25 deg; 6 / 1 in) |
| **LB** | flywheel on/off (it is OFF at START) |
| **RB** / hold **RT** | shoot one / keep shooting (let go to stop). Keep shooting also stops after a jam or a "gave up": press RT again to go on |
| **A** / **B** | the oldest shot not marked yet was a HIT / a MISS (at the speed, hood angle and distance it was fired with) |
| left stick click | undo the last HIT / MISS: that shot waits to be marked again |
| right stick click | drop the last shot not marked yet (a "shot" with no piece: the wheel dipped by itself) |
| **X** | save: the speed with the best hit ratio at this distance (replaces one within 1 in); with no shots marked, the speed now |
| hold **BACK** 1 s | delete the saved point here (a hold, so it is not pressed by mistake) |
| sticks, left trigger | drive, slow (with `robotConfig()`) |

The speed stays at or below the flywheel's `maxRpm`, and the hood inside its limits (a warning shows
when the D-pad reaches one), so a saved point is what was really tested. The screen shows the target and measured speed, ready (or why not), shots fired (and how many
are still to mark), `Hits at 2550: 3/4`, every speed tried at this spot, every table's point count
(`Points: POLLEN 3, NECTAR 0  (4-6 per table)`), the recovery time, the distance and bearing, and the
saved points. The launcher's speed trim is never used here (the table holds the real speeds); its battery
compensation stays on. Each shot keeps the flywheel speed **measured** as the piece was fed; one fed more than
`sagLimitRpm` (60 RPM or 3%) under the speed asked for is flagged "sagged" (it had not got back to
speed after the last shot, or the battery is low). With a hood, each speed and hood angle is its own
try, and X saves the winner with the hood angle it was fired with. The hit counts start again when the
distance moves more than `SPOT_MOVE_IN` (4 in), after a save, and on another table; shots fired at the
old spot and not marked are dropped. Output:

```java
new ShotTable()
    .add(24.0, 2600, 6.0)   // 4/5 in, measured 2588 RPM
    .add(48.5, 3100, 14.0)   // 3/4 in, measured 3079 RPM
    .add(72.0, 3700, 21.0)   // 4/4 in, measured 3672 RPM
```

It also shows the line for five files (the section is the launcher's), one per table; with a hood
each row has the hood as its third number (in the hood's scale: degrees, or a servo position):

```java
public static double[][] speedByDistance = {{24, 2600}, {48.5, 3100}, {72, 3700}};   // in Tunables.Launcher
public static double[][] speedByDistanceNectar = {{24, 2800, 4}, {48.5, 3300, 12.5}};   // a named table, with a hood
```

Copy each line into `Tunables.java` (replace the line with the same name): the robot uses the file,
not this screen. The lines are also saved on the Control Hub at STOP. The screen says what to do next
(`Next: LB to spin up the flywheel.`).

## AimTunerOpMode

Finds the camera offset: where the target sits in the camera's view when the LAUNCHER points
straight at it (degrees, left = +). Fill in:

| Method | Required? | |
| --- | --- | --- |
| `createCamera()` | yes | your camera |
| `createTurret()` / `cameraOnTurret()` | no | aim with a turret (default: turn the robot) |
| `robotConfig()` | without a turret | to turn the robot, and drive |
| `createAimController()` | no | the aiming gains (use your TeleOp's) |
| `createLauncher()`, `shotRpm()` | no | for test shots (with a table: the camera's distance) |
| `startOffsetDeg()` | no | the offset in your code now |

| Button | Does |
| --- | --- |
| hold **A** | aim at the target |
| D-pad right/left, up/down | aim 0.5 / 2 deg more right / left. **Shots go left of the middle? press RIGHT.** |
| **RB** / **LB** | shoot one / flywheel off |
| **X** | home the turret (a turret with a home switch does not aim before it is homed; `homeDirection()`, default -1) |
| sticks | drive when not aiming |

A camera to the SIDE of the launcher needs a different offset at each distance (it sees the target
from a different angle). If the offset that works changes with distance, move the camera onto the
launcher's line, or tune at the distance you shoot from most.

Output: `new AimController(...).offset(-1.5)` or `turret.cameraOffset(-1.5)`; with a turret also
`Tunables.Turret: cameraOffset = -1.5` (the section is the turret motor's name).

---

## CharacterizerOpMode

Measures how the real robot moves (this is called **characterizing** it), for two uses:
**feedforward** in your code (the power a motor needs for a speed, see [Feedforward](control.md#feedforward))
and **real numbers in the simulator** (its robot file). Needs `robotConfig()` and, for lifts, arms and
flywheels, `mechanisms()`:

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

D-pad chooses a test, **A** runs it, **B** stops it at once.

| Test | It does | Space and safety |
| --- | --- | --- |
| Drive | power ramps up slowly forward (to 0.4) and back, then quick steps (0.5, 0.6 s) | about 5 ft clear in front and behind: each roll ends at `maxDriveCounts()` (default 2500 counts, about 5 ft with 312 rpm motors and 104 mm wheels; 4.6 ft with 96 mm); `driveRampMax()` (at most 0.5) lowers the speed |
| Turn (needs the IMU) | the same, turning in place | room to turn |
| Lift/arm | slowly up to 60% of its range, slowly down, quick steps up and down, then back to the bottom | start it at the BOTTOM (the soft limits count from there); it stops and says why if it goes past them; `.reversed()` if positive power goes down |
| Flywheel | ramps up to full power, coasts, full power, coasts | hands and pieces away |

It stops itself and says why if: a wheel or motor turns the wrong way for its power (a direction
setting is wrong; each wheel is checked); a lift goes past a soft limit, or counts down while going up;
something pushes but does not move for 0.7 s (jammed, against a hard stop, or an encoder cable in
another motor's port); GUIDE or START is pressed. A drive roll that reaches `maxDriveCounts()` ends early,
and the test goes on with the next part.
What each part of a test measures:

- A slow ramp: the power just to start moving (kS) and the power per speed (kV).
- A quick step: the power for speeding up (kA). Spinning wheels spoil this number, so run the drive
  test on tiles, not on a smooth floor.
- A lift: also the power that holds it against gravity (kG).
- A flywheel's coasting: how long it takes to slow down.

Results (also saved with the other finders' results):

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

Each test's log is saved in the results folder as `characterizer/<test>[-<motor>]-<yyyyMMdd-HHmmss>.csv`
(for example `lift-lift-20261021-101500.csv`); a test stopped early saves its log too, marked "(stopped early)". The simulator's
fit tool reads them and writes the robot file for you (`./gradlew :sim:fit -Plog=... -Pwrite`, see
the [simulator guide](../guides/simulator.md#real-numbers-the-characterizer)). Run each test 2-3 times
on a charged battery; the fit tool fits the logs of the same test together. It also gives r2, a
score from 0 to 1 for how well the numbers match the measurements. Below 0.9 it
says "poor: run it again"; below 0.5 it says "Could not fit". Arms are measured like lifts (the pull of gravity is taken as the same at
every angle), so the numbers are a middle value for an arm. **For an arm, set the high limit below
straight up**, so gravity always pulls it back down (past straight up, gravity pulls it on to the
far side, and a motor's brake does not hold a heavy arm).

## CharacterizeMath

The fitting math, pure (no hardware): `fit(t, volts, power, speed, gravity)` gives kS, kV, kA (kG) and
r2 by least squares over the moments the motor was moving and powered (speeds smoothed over 30 ms
each side, or 2.5 loops on a slower loop; moments within that window plus 20 ms of a jump in power left out); `coastSec(...)`,
`turnDegPerCount(...)`, `yawRates(...)`, `accelerations(...)`, `smoothed(...)`, `leastSquares(...)`.
Powers are scaled to 12.8 V with the logged battery voltage.

## FinderOpMode

The shared frame: INIT (`setup()`, `showInit()`), the loop (`bind()` once, then `step()` and
`show()` every loop, plus the results), and `stopAll()` at the end, always. The results are saved to
the file when it stops, and the screen says if that worked. `runOpMode()` is `final`. Write your own
finder by extending it.

| Field / method | |
| --- | --- |
| `t` | the [RobotTelemetry](telemetry.md) |
| `results` | the [FinderResults](#finderresults) |
| `finderName()`, `setup()`, `bind(controls)`, `step()`, `show(t)`, `stopAll()` | fill these in |
| `helpLines()` | optional (please write it): the help page, at most 13 lines of about 50 characters. Default: "No help page for this finder yet." |
| `showInit(t)` | optional: what INIT shows |
| `pause()` | optional: what stops while help is open (default `stopAll()`; override when `stopAll()` also closes a camera) |
| `inputsReady()` | false after START and after help, until gamepad 1 was let go: check it before acting on a stick, trigger or held button you read directly |
| `openHelp()` | opens the help page (e.g. when a help key stopped a test) |
| `helpKeyDown()` | true while GUIDE or START is down (e.g. as a stop condition for a moving test) |

GUIDE and START are the help keys in every finder, so a finder must not bind them itself.

## FinderResults

| Method | |
| --- | --- |
| `set(key, line)` | sets or replaces the line with this key |
| `remove(key)`, `removeStartingWith(start)` | removes lines |
| `lines()`, `isEmpty()` | read |
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
count, NaN if the wheels disagree), `servoPosition(p)` (kept in 0..1).

## Chooser

A D-pad list: `new Chooser(names)` (sorted A to Z), `next()`, `previous()`, `current()`,
`isEmpty()`, `addTelemetry(t, emptyText)`.

## ShotPoints

The shot tuner's saved points: `new ShotPoints(hasHood)` (or `new ShotPoints(hasHood, hoodDecimals)`: 1
for degrees, 3 for servo positions, 0 for ticks), `save(distance, rpm, hoodDeg)` (replaces
one within `SAME_DISTANCE` = 1), `removeNear(distance)`, `points()` (nearest first),
`codeLines()` (the `ShotTable` code), `tunablesLine(section)` (the five-files line,
`public static double[][] speedByDistance = {{48, 2550}, ...};   // in Tunables.Launcher`, with a hood
`{{48, 2550, 12.5}, ...}`; empty if nothing is saved),
`tunablesLine(section, field)` (the same for a named table, e.g. `speedByDistanceNectar`).
`save(distance, rpm, hoodDeg, measuredRpm, hits, shots)` keeps how many went in and the measured speed
(`saved()`, `size()`, `note(saved)`; `codeLines()` writes them as a comment).

`ShotPoints.Tally`: hits and shots per try (a speed, and a hood angle with a hood) at one spot.
`mark(rpm, hit, measuredRpm)`, `mark(rpm, hoodDeg, hit, measuredRpm)`, `unmark(...)` (an undo),
`hits(rpm)` / `hits(rpm, hoodDeg)`, `shots(...)`, `totalShots()`, `measuredRpm(...)`, `best()`
({rpm, hood}, null with no hit), `bestRpm()` (best hit ratio; a tie: more shots, then the lower speed;
NaN with no hit), `tries()`, `summary()` (`2450: 1/3, 2550: 3/4 (best)`; with a hood
`2550 @ hood 10: 3/4`), `spotIn()`, `setSpot(in)`, `isEmpty()`, `clear()`.

`ShotPoints.Trials`: the shots of one spot waiting to be marked, each with the speed, hood and distance
it was fired with. `fired(rpm, hoodDeg, measuredRpm, distanceIn)`, `mark(hit)` (the oldest waiting
shot), `undo()` (the last mark; that shot waits again), `dropLast()` (the newest waiting shot),
`check(distanceIn)` (moved more than `SPOT_MOVE_IN`: marks start again, old shots dropped),
`unmarkedCount()`, `tally()`, `clear()`. Each returns the words the tuner shows.

`ShotPoints.DistanceAverage`: the distance over the last `WINDOW_SEC` (0.5 s) of fresh readings.
`add(nowSec, distance, from)` (another `from` starts again), `average(nowSec)` (NaN with none),
`count(nowSec)`, `source()`.
