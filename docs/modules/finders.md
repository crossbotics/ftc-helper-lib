# Finders module

Package: `io.github.jasoncross.ftchelper.finders`

Learn it hands-on: [21 Find your numbers](../tutorials/21-finders.md). Step by step on the robot:
[Finders guide](../guides/finders.md).

Ready-made programs that **measure** the numbers your code needs (servo ends, lift limits, flywheel
gains, wheel size, shot speeds, camera offset) and show them as **lines of code to copy**. They also
save those lines on the Control Hub in `/sdcard/FIRST/ftc-helper/finder-results.txt` when you press
STOP, so nobody copies numbers from the screen by hand.

| Class | Use it to | Needs code from you? |
| --- | --- | --- |
| [`ServoFinderOpMode`](#servofinderopmode) | find a servo's safe ends and positions | no |
| [`MotorFinderOpMode`](#motorfinderopmode) | find a lift's, arm's or turret's limits, positions and top speed | no |
| [`FlywheelFinderOpMode`](#flywheelfinderopmode) | find a flywheel's top speed, speed-control gains and recovery time | no |
| [`DriveTunerOpMode`](#drivetuneropmode) | find the wheel size and strafe correction for autonomous | `robotConfig()` |
| [`ShotTunerOpMode`](#shottuneropmode) | build the shot table: distance -> speed (and hood angle) | your launcher (+ camera) |
| [`AimTunerOpMode`](#aimtuneropmode) | find the camera offset so shots go in the middle | your camera (+ turret or drive) |
| [`FinderOpMode`](#finderopmode) | the shared frame of every finder (for writing your own) | |
| [`FinderResults`](#finderresults) | the code lines a finder shows and saves | |
| [`FinderMath`](#findermath) | the arithmetic behind the finders | |
| [`Chooser`](#chooser) | a list to choose from with the D-pad | |
| [`ShotPoints`](#shotpoints) | the saved good shots of the shot tuner | |

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

Every finder: nothing moves in INIT; the loop never waits (except the drive tuner's test moves,
like autonomous); everything it moved is stopped at the end, even after an error.

---

## ServoFinderOpMode

Lists every servo in the configuration. Nudge the chosen one and mark what you find.

| Button | Does |
| --- | --- |
| D-pad up/down (list) | choose a servo; **A** = use it |
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
```

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
```

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

Feed a piece by hand in step 2: it shows the **recovery time** (shot -> ready again).
The gains: F = 32767 / top speed (counts/s), P = F / 10, I = P / 10, D = 0 (a common starting
point for REV hubs). `countsPerRev()` is 28 (the motor's own encoder, wheel on the motor shaft);
override it for a geared wheel. Output:

```java
Flywheel.create(hardwareMap, "flywheel").countsPerRev(28.0).pidf(1.17, 0.117, 0, 11.70).maxRpm(5400)
    // top speed 6000 RPM; maxRpm = 90% of it
    // recovery after a shot at 3600 RPM: 0.38 s
```

## DriveTunerOpMode

Needs `robotConfig()` (your `RobotSettings`). D-pad chooses a test, **A** starts it.

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
they measure 48, then copy the lines into `RobotSettings.auto()`.
`testDistanceIn()` changes the 48. Output:

```java
c.wheelDiameterIn = 3.812;   // RobotSettings.auto(), from the drive test
c.strafeCorrection = 1.180;   // RobotSettings.auto()
```

## ShotTunerOpMode

Builds the shot table. Fill in:

| Method | Required? | |
| --- | --- | --- |
| `createLauncher()` | yes | your [`Launcher`](launcher.md), as in TeleOp (with a hood, it tunes the hood too) |
| `createCamera()` | no | the camera gives the distance; null = set the distance by hand |
| `findTarget(camera)` | no | which target; default: the first with a distance |
| `robotConfig()` | no | drive with the sticks between distances |
| `createSubsystems()` | no | other mechanisms to keep updated (an intake that loads it) |
| `startRpm()` | no | the starting speed, default 3000 |
| `startHoodDeg()` | no | the starting hood angle if the hood was never moved, default 0 |

| Button | Does |
| --- | --- |
| **Y** | what the D-pad changes: SPEED, HOOD (with a hood), DISTANCE (no camera) |
| D-pad up/down, right/left | big / small step (100 / 10 RPM, 5 / 0.5 deg, 6 / 1 in) |
| **LB** | flywheel on/off (it is OFF at START) |
| **RB** / hold **RT** / **B** | shoot one / keep shooting / stop shooting. Keep shooting stops after B, a jam or a "gave up": press RT again to go on |
| **A** / **X** | save this point (replaces one within 1 in) / delete the point here |
| sticks, left trigger | drive, slow (with `robotConfig()`) |

The speed stays at or below the flywheel's `maxRpm`, and the hood inside its limits (a warning shows
when the D-pad reaches one), so a saved point is what was really tested. The screen shows the target and measured speed, ready (or why not), shots fired, the recovery
time, the distance and bearing, and the saved points. Output:

```java
new ShotTable()
    .add(24.0, 2600, 6.0)
    .add(48.5, 3100, 14.0)
    .add(72.0, 3700, 21.0)
```

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

Output: `new AimController(...).offset(-1.5)` or `turret.cameraOffset(-1.5)`.

---

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

The shot tuner's saved points: `new ShotPoints(hasHood)`, `save(distance, rpm, hoodDeg)` (replaces
one within `SAME_DISTANCE` = 1), `removeNear(distance)`, `points()` (nearest first),
`codeLines()` (the `ShotTable` code).
