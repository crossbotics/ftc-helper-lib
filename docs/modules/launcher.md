# Launcher module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.launcher`

Learn it hands-on: [17 Launcher](../tutorials/17-launcher.md), [18 Vision aiming](../tutorials/18-vision-aim.md).

Parts for a robot that shoots game pieces: a **flywheel** (a fast-spinning wheel that throws the
piece), a **hood** (a curved plate a servo tilts to set the shot angle), a **feeder** (pushes one
piece into the flywheel) and a **turret** (turns the shooter to aim). Each part works alone; the
`Launcher` joins them. With the [robot in five files](robot.md#launcher), its `Launcher` is
built on these, so start there ([lesson 9](../tutorials/start/09-shooter-and-camera.md)). Use this
page to build a shooter by hand (tutorial 17). Speeds are in **RPM** (turns per minute).

| Class | Use it to |
| --- | --- |
| [`Flywheel`](#flywheel) | hold a speed in RPM, say "ready", notice shots |
| [`ShotTable`](#shottable) | look up the RPM and hood angle for a distance, from a few measured shots |
| [`Feeder`](#feeder) | push ONE piece into the flywheel (an interface) |
| [`MotorFeeder`](#motorfeeder) | run a roller or belt feeder |
| [`ServoFeeder`](#servofeeder) | run a kicker-arm feeder |
| [`Turret`](#turret) | aim in degrees, never past its limits, hold a field direction |
| [`Launcher`](#launcher) | spin up, wait until EVERYTHING is ready, feed, repeat |

A hood is any [`AngleDevice`](mechanism.md#angledevice): a [`PositionServo`](mechanism.md#positionservo)
with `degrees(...)`, the five-files [`Hood`](robot.md#hood) (servo or motor, a linkage table), or your own.

```mermaid
flowchart LR
    T["ShotTable<br/>distance -> rpm, hood"] --> L
    L["Launcher"] --> F["Flywheel<br/>(speed)"]
    L --> H["Hood<br/>(PositionServo)"]
    L --> FD["Feeder<br/>(one piece)"]
    C["Ready checks<br/>turret aimed? target seen?"] --> L
```

## How a shot happens

```mermaid
stateDiagram-v2
    [*] --> OFF
    OFF --> SPINNING: spinUp() / shoot(n)
    OFF --> IDLE: idle()
    IDLE --> SPINNING: spinUp() / shoot(n)
    SPINNING --> WAITING_READY: shots left > 0
    WAITING_READY --> FEEDING: flywheel ready AND hood arrived AND every check true
    FEEDING --> WAITING_READY: piece through, wait shotInterval, shots left > 0
    FEEDING --> SPINNING: no shots left
    WAITING_READY --> SPINNING: not ready within readyTimeout (gives up, says why)
    WAITING_READY --> SPINNING: stopShooting()
    FEEDING --> SPINNING: feeder jammed (shots dropped, says why) / stopShooting()
    WAITING_READY --> IDLE: idle()
    SPINNING --> IDLE: idle()
    SPINNING --> OFF: stop()
    IDLE --> OFF: stop()
```

The two safety rules: **never feed into a slow flywheel**, and **never wait forever**.

---

## Flywheel

```java
Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
        .countsPerRev(28)          // encoder counts per ONE flywheel turn
        .pidf(10, 3, 0, 12)        // the hub's speed control
        .tolerance(75)             // ready within 75 rpm...
        .settleTime(0.15);         // ...for 0.15 s
```

| Method | What it does |
| --- | --- |
| `create(hardwareMap, name)` | coast (a flywheel must spin down freely), speed control on |
| `.reversed()` | spin the other way |
| `.follower(hardwareMap, name, reversed)` | a second motor on the same wheel (same speed command). **It needs its own encoder cable.** If its encoder reads nothing while the wheel spins, it is switched off with a warning. It gets the `pidf` gains whether you call `pidf` before or after it |
| `.countsPerRev(c)` | counts per FLYWHEEL turn: bare motor 1:1 = 28; with gears multiply by motor turns per wheel turn; a shaft encoder on this motor's port = its counts (REV Through Bore 8192) |
| `.pidf(p, i, d, f)` | the hub's speed gains (PID plus F, a push that grows with the speed; see [PID](control.md#pidcontroller)). Start 10, 3, 0, 12; tune F first (with a charged battery) |
| `.batteryCompensation(volts, nominalVolts)`, `.noBatteryCompensation()` | F follows the battery ([below](#battery-compensation)); off (the default) |
| `getBatteryVolts()`, `getFScale()`, `hasBatteryCompensation()` | the battery F is scaled for (the highest reading of the last second; NaN if none), what F is multiplied by now (1 = as tuned) |
| `.tolerance(rpm)`, `.settleTime(s)` | when "ready" |
| `.maxRpm(r)` | highest speed allowed |
| `.shotDrop(rpm)` | a drop this big after ready = a shot was seen |
| `setRpm(r)`, `stop()`, `update()` | use. `stop()` and speed 0 COAST (no hard braking of a fast wheel) |
| `isReady()`, `getRpm()`, `getTargetRpm()`, `getMaxRpm()`, `getShotCount()`, `getFault()` | read |
| `spinUpStep(rpm, timeout)` | autonomous step |

**Check your speed reading** before trusting it: the RPM on telemetry must match a tachometer (a
tool that measures spin speed) or a slow-motion video. Very high count rates (a fine encoder at high speed) can read wrong.

### Battery compensation

> **Battery compensation: on by default, easy to turn off, not yet proven on a robot.**
> The RPM control already fixes a low battery by itself, but slowly (the small I gain). Compensation
> only makes that faster: it helps the first shot after spin-up and quick shots in a row. It cannot help
> when the battery is too low for the speed you ask (the motor is already at full power).
> - **Turn it off:** `batteryCompensation = false` in `Tunables.Launcher` (five files), or leave out `.batteryCompensation(...)` here.
> - **Test it on your robot:** with a battery at about 11.5 V, fire 4 shots quickly and watch the
>   launcher's measured RPM against its target on the Driver Station. Do it with compensation on, then
>   off. If you see no difference, leave it off; if the wheel overshoots after a battery swap, turn it
>   off and tell us.


```java
Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel").pidf(1.17, 0.117, 0, 11.70)
        .batteryCompensation(Hw.batteryReader(hardwareMap), 12.6);   // 12.6 V: from the Flywheel Finder
```

**Why.** The hub's speed control works in power: F is "this much power for each count per second of
speed" (the Flywheel Finder sets F = 32767 / the top speed in counts per second, as FIRST's motor
PIDF tuning guide does; 32767 = full power). Power is a share of the battery's voltage, so the same power on a weaker battery pushes less
and the wheel runs slower. The P and I gains notice the missing speed and add power, but I builds up
slowly, so a low battery means a slow spin-up and a slow recovery after each shot (shots fired before it
is back go short).

**What it does.** Every 0.25 s it reads the battery and sends F x (`nominalVolts` / volts): tuned at
12.6 V, at 11.5 V F is x 1.10 (12.6 / 11.5). Then the wheel gets the same push as when it was tuned.
"volts" is the **highest reading of the last second** (`BATTERY_FILTER_SEC`): while the drive or the
flywheel pulls hard, the battery dips for a moment and comes back; a dip is not a weaker battery, and
raising F for it would push the wheel past its speed as the load ends. A battery that is really lower
reads low the whole second, and then F is scaled. The scale stays between 0.8 and 1.3 (a bad reading
cannot run the wheel away), a reading outside 7 to 16 V (or NaN: no reading yet, or too old) is skipped,
and a new F is sent only when it changes by more than 2% (each send costs loop time). P and I stay as
tuned. `nominalVolts` is the battery voltage when you found the gains: the Flywheel Finder shows it.
Without it, 12.8 V (`Flywheel.DEFAULT_NOMINAL_VOLTS`, a charged battery under load). Below 11.5 V the
Driver Station warns: `battery 11.2 V: low`. Debug mode shows `flywheel battery: 11.50 V (tuned at 12.6 V), F x1.10`.

**Where the volts come from.** `Hw.batteryReader(hardwareMap)` never waits: it answers with the latest
reading and asks the hubs again (at most every 0.25 s) on the library's helper thread, so the loop never
waits for them ([non-blocking](../guides/non-blocking.md)). `() -> Hw.batteryVolts(hardwareMap)` works
too, but each reading then holds up the loop a few milliseconds.
The robot in five files turns it on for you when `kF` is set ([robot.md](robot.md#speed-trim-and-battery-compensation)).

## ShotTable

```java
ShotTable pollen = new ShotTable()
        .add(24, 2600, 6)        // distance in, rpm, hood degrees
        .add(48, 3100, 14)
        .add(72, 3700, 21);
ShotTable.Shot s = pollen.lookup(36);     // 2850 rpm, 10 deg (halfway between 24 and 48)
```

Straight lines between your points (the speed AND the hood); outside them, the nearest end (it never
guesses beyond what you tested). `add(distance, rpm)` (no hood angle) makes a table that never moves the
hood: `hasHood()` is true only once a point has a hood angle. The hood number is in the hood's own scale
(degrees, or a servo position if your hood works in positions). `covers(distance)` says if you are inside. One table per game piece if they fly differently
(BIOBUZZ: POLLEN is a 2.8 in ball, NECTAR a 3.6 in ball); swap them with `launcher.table(other)`.
How to fill it: [tutorial 17](../tutorials/17-launcher.md#4-fill-the-shot-table). In the robot in five
files, each `speedByDistance...` line in `Tunables.Launcher` is one table, picked by name with
`robot.launcher.useTable("nectar")` ([robot.md: Launcher](robot.md#launcher)); the
[Shot Tuner](finders.md) tunes one at a time.

## Feeder

An interface: `feedOne()`, `isBusy()`, `update()`, `stop()`, and optional `isJammed()` and `clearJam()`.
The launcher decides WHEN; the feeder only knows HOW. Next season's new feeding mechanism? Write a
class with these methods and the launcher works with it. See [Extending](../guides/extending.md).

## MotorFeeder

```java
MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder")
        .power(1.0).feedTime(0.25)        // run 0.25 s per piece
        // optional: stop when a sensor sees the piece leave. beam = a beam-break sensor
        // (a DigitalChannel); "!" = not: it reads false while something blocks the beam
        .stopWhen(() -> !beam.getState())
        .jamDetection(60, 0.2);           // optional (encoder cable)
```

A jam stops the launcher's remaining shots. The next `shoot(...)` clears it and tries again (a new
jam is detected again), so the driver just presses shoot after clearing the piece.

## ServoFeeder

```java
ServoFeeder kicker = ServoFeeder.create(hardwareMap, "kicker")
        .positions(0.15, 0.60)            // rest, push
        .times(0.15, 0.15);               // push time, return time
```

`goToRest()` moves it to rest (only call it in INIT if that is safe).

## Turret

```java
Turret turret = Turret.create(hardwareMap, "turret")
        .countsPerDegree(1479 / 360.0)    // encoder counts per turret turn / 360. ".0": a
                                          // decimal result (1479 / 360 alone would drop it)
        .limits(-190, 190)                // degrees; it never turns past these (cables!)
        .homeSwitch(hardwareMap, "turret_home", 45)   // the switch closes at 45 degrees
        .pid(0.02, 0, 0.001, 0)           // kP, kI, kD, kF on the angle in degrees
        .tolerance(1.0);                  // within 1 degree = aimed
```

![Turret angles and limits](../images/turret-limits.svg)

Angles: 0 = robot forward, **counterclockwise positive** (like the IMU). Positive power must turn it
counterclockwise (else `.reversed()`).

| Method | What it does |
| --- | --- |
| `.countsPerDegree(c)` | encoder counts per turret degree (motor counts x gear ratio / 360) |
| `.limits(min, max)` | allowed range; may be more than 360 in total (e.g. -190..190) |
| `.startAngle(deg)` | the angle at power-on (no switch). It reads the encoder when called: call it after `countsPerDegree` and `reversed`. A servo turret: where it counts from before its first command (a servo cannot say where it is): send it there at START with `aimAt(servoStartAngle())` |
| `servoStartAngle()` | a servo turret: `startAngle`, or the middle of its range when none was given (0 with no limits); others: `getAngle()`. The stick and `aimBy` start from it before the first command |
| `.homeSwitch(hardwareMap, name, angle)` / `.homeSwitch(reader, angle)` | a switch that closes at a known angle |
| `.homingPower(p)`, `.pid(...)`, `.tolerance(deg)`, `.maxPower(p)`, `.slowZone(deg, power)` | tuning. Before homing, manual power is kept to the homing power |
| `.homingTimeout(s)` | homing stops with a fault if the switch is not found in time (default 5 s) |
| `.maxTargetAgeMs(ms)` | how old a camera picture may be for `aimAtTarget` (default 150) |
| `.cameraOffset(deg)` | where the target sits in the camera's view when the launcher points at it (left = +), for a camera not centered on the launcher. The [Aim Tuner](finders.md#aimtuneropmode) finds it. |
| `aimAt(robotDeg)` | turn to a robot-relative angle, **the safe way round** |
| `aimField(fieldDeg, headingDeg)` | point at a field direction; call every loop while the robot turns |
| `aimBy(delta)` | turn by an amount |
| `aimAtTarget(target, cameraOnTurret)` | aim at a camera target: fresh pictures only, each picture once; holds when there is none |
| `manual(power)` | a stick; 0 holds where it is |
| `home(direction)` | turn slowly toward the switch until it closes (at once if already on it; fault after the timeout) |
| `addWarnings(t)` | only the warnings of `addTelemetry` (a fault, not homed, out of range), for code that writes its own line (the five files' [Turret](robot.md#turret) does) |
| `getAngle()`, `getTargetAngle()`, `isAimed()`, `isOutOfRange()`, `isHomed()`, `isHoming()`, `getFault()` | read. `isAimed()` is false for an out-of-range target. It only says the turret reached its LAST target: if the goal can be lost (a camera), also check that the goal is seen now (the robot in five files' [`Turret.isAimed()`](robot.md#turret) does) |
| `getTolerance()`, `degreesOutOfRange(deg)` | the aimed tolerance; how far past the limits a direction is (0 = it can point there) |
| `aimStep(deg, timeout)` | autonomous step |
| `fence(lo, hi)`, `fence(lo, hi, upPower, downPower)`, `clearFence()`, `isHeld()` | set every loop by an [interlock rule](robot.md#interlock): a target past `lo`..`hi` goes the other way round or waits at its edge; the caps (0 to 1) limit the power toward each side so it brakes in time |

**The safe way round:** with limits -190..190, 170 and -190 are the same direction. The turret
picks the one inside the limits closest to where it is, and turns there without passing a limit,
so cables never wind up. A direction outside the limits goes to the nearest limit and
`isOutOfRange()` is true.

**Homing** means finding a known angle by turning until a switch closes. With a switch, aiming waits until the switch was seen once (`home(direction)`). If the
switch is not found within `homingTimeout`, homing stops and `getFault()` says so (a broken wire never
spins the turret forever). Without a switch, the turret trusts `startAngle` (default 0): turn the robot
on with the turret facing forward.

## Launcher

```java
Launcher launcher = new Launcher(flywheel, feeder)
        .hood(hood)
        .table(pollen)
        .requireReady("Turret", turret::isAimed)   // never feed before the turret is aimed
        .shotInterval(0.25)                        // at least 0.25 s between pieces
        .readyTimeout(3);                          // not ready in 3 s: give up, say why
```

| Method | What it does |
| --- | --- |
| `.hood(device)`, `.table(t)` | optional parts: a hood is any [`AngleDevice`](mechanism.md#angledevice) |
| `.hoodDeadband(a)` | the hood is sent a new angle only when the table's changes by more than this (default 0.25): a jittery distance must not keep it "moving" |
| `hoodOffset(a)`, `getHoodOffset()` | added to the table's hood angle by `setDistance` (a hood trim) |
| `.requireReady(name, check)` | another condition before each piece (aim, sensor, anything) |
| `.clearReadyChecks()` | removes every `requireReady` check (the Shot Tuner does this: you aim by hand there) |
| `.shotInterval(s)` | least time between pieces |
| `.readyTimeout(s)` | give up the remaining shots after this long not ready |
| `.idleRpm(r)` | speed when not shooting (a low idle makes spin-up faster) |
| `setRpm(r)` / `setDistance(d)` | the shot by hand / from the table (sets the hood too, when the table has hood angles) |
| `speedScale(s)`, `getSpeedScale()` | the shot speed x `s` (1.03 = +3%), a live trim for a match; not the idle speed. 1 = as set |
| `getShotRpm()` | the shot speed before `speedScale` (from `setRpm` or the table) |
| `getHood()`, `getTable()`, `hasTable()` | the hood (or null); the table (or null); true if a table was given |
| `spinUp()`, `shoot(n)`, `stopShooting()` | commands |
| `idle()` | back to the idle speed |
| `stop()` | fully OFF, even the idle speed, until `spinUp()`, `shoot()` or `idle()` |
| `update()` | every loop (updates flywheel, feeder and hood for you) |
| `isReady()`, `whyNotReady()` | e.g. "flywheel, Turret": your first debug clue |
| `getState()` (OFF, IDLE, SPINNING, WAITING_READY, FEEDING), `isShooting()`, `getShotsLeft()`, `getShotsFired()`, `getLastProblem()` | read (the last problem shows as a warning for `PROBLEM_SHOW_SEC`, 5 s; a jam while jammed) |
| `spinUpStep()`, `shootStep(n)` | autonomous steps |
