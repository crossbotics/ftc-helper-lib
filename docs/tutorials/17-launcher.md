# Tutorial 17: Launcher

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** a shooter that spins a flywheel, sets the hood angle from the distance, and feeds a piece only when everything is ready.
The main track builds a shooter with less code: [A shooter, a camera and a turret](start/09-shooter-and-camera.md).

**You will learn:** how a flywheel shooter is controlled (speed, ready, feeding), how to build a
shot table from real shots, and how `Launcher` makes sure a piece is only fed when everything is ready.

**You need:** motor `flywheel` (with its encoder cable), servo `hood`, motor `feeder`. Tutorials 03, 14.
Safety glasses: a flywheel throws things.

**Full program:** [`Tut17Launcher.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut17Launcher.java)
**Reference:** [launcher.md](../modules/launcher.md)

---

## 1. How a flywheel shot works

A spinning wheel grabs the game piece and throws it. Two things decide where it lands: the
wheel's **speed** (RPM: turns per minute) and the **angle** it leaves at (the hood). Both depend on distance.
And one rule matters more than any other: **never feed a piece into a wheel that is not up to speed**,
or it falls short.

## 2. The flywheel

```java
Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
        .countsPerRev(28)       // encoder counts per ONE flywheel turn
        .pidf(10, 3, 0, 12)     // the hub's speed control
        .tolerance(75)          // "at speed" = within 75 rpm...
        .settleTime(0.15);      // ...for 0.15 s
```

`pidf` sets the gains of the hub's own speed control (P, I, D and F, like Tutorial 07). The hub
holds the speed itself (fast and smooth). "Ready" needs the speed to STAY in the band for
0.15 s, so one lucky reading during spin-up does not count.

**countsPerRev:** a bare goBILDA/REV motor encoder gives 28 counts per motor turn. If the flywheel
turns at the motor's speed, that is 28. Geared: multiply by motor turns per flywheel turn. An
encoder on the flywheel shaft (plugged into this motor's encoder port): its own counts per turn.

**Check the reading:** run at 3000 rpm and compare with a tachometer (a tool that measures turning speed) (or count turns in a
slow-motion video). If they disagree, countsPerRev is wrong.

## 3. The hood and the feeder

```java
PositionServo hood = PositionServo.create(hardwareMap, "hood")
        .degrees(0.10, 0, 0.90, 25)     // two measured points
        .travelTime(0.4);
MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25);
```

Measure the hood: move the servo with the Hardware Check, write down the raw position at the lowest
and the highest angle, and measure those angles (a phone level app works).

## 4. Fill the shot table

Measure these numbers with the **Shot Tuner** and the flywheel gains with the **Flywheel Finder**
([tutorial 21](21-finders.md)): it shows the `new ShotTable()` lines to copy.

```java
ShotTable table = new ShotTable()
        .add(24, 2600, 6)
        .add(48, 3100, 14)
        .add(72, 3700, 21);
```

How to get YOUR numbers:

1. Press X on gamepad 1: **manual rpm** mode. D-pad left/right = rpm -/+ 100.
2. Put the robot 24 in from the target (tape measure, to the same point on the robot every time).
3. Find the rpm and hood angle where 5 shots in a row score. Write them down.
4. Repeat at 3-5 distances across where you will shoot from.
5. Put the points in the table. Between them the table draws straight lines.

Different game pieces (2026-27: POLLEN and NECTAR) may need their own tables.

## 5. The launcher

```java
Launcher launcher = new Launcher(flywheel, feeder)
        .hood(hood)
        .table(table)
        .shotInterval(0.3)
        .readyTimeout(2.5);
...
launcher.setDistance(distanceIn);    // every loop: rpm + hood from the table
launcher.update();
```

```java
.bindPress(Pad.G2, Btn.A, launcher::spinUp)
.bindPress(Pad.G2, Btn.RB, () -> launcher.shoot(1))
.bindPress(Pad.G2, Btn.LB, () -> launcher.shoot(3))
.bindPress(Pad.G2, Btn.B, launcher::idle)
```

`shoot(3)` does: spin up if needed; wait until the flywheel is ready AND the hood has arrived (AND
any `requireReady` checks, like an aimed turret); feed one; wait `shotInterval`; repeat. If it is
not ready within `readyTimeout`, it gives up and says why: "gave up: flywheel".

## Run it

1. Safety glasses. No game piece first. START.
2. A: the flywheel spins up; telemetry shows about "2850 / 2850 rpm READY". (The program starts at
   36 in, halfway between the 24 and 48 in rows of the table.)
3. RB without a piece: the feeder runs once. LB: three times, 0.3 s apart.
4. With pieces: D-pad up/down changes the distance; the hood moves; shoot.
5. Stop the OpMode and unplug the flywheel's encoder cable. Run again and `shoot(1)`: the flywheel
   can never read "ready", so it waits, then shows "gave up: flywheel". That is the safety rule working.
   Plug the cable back in.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Never READY | countsPerRev wrong (speed reads wrong), or F gain too low (never reaches the speed) |
| Speed overshoots then settles slowly | lower P; tune F first so it gets close by itself |
| Shots fall short when shooting fast | `shotInterval` too short: the wheel has not recovered |
| Hood never "arrives" | `travelTime` too long; measure it |
| "gave up: hood" | the hood's `travelTime` is longer than `readyTimeout` |

## Try this

1. Add `.requireReady("Driver", () -> gamepad2.right_trigger > 0.5)`: shots only while the trigger is held.
2. Add `.idleRpm(1500)`: a slow idle makes spin-up faster. Measure the difference.
3. A second table for another game piece, switched with a button.

## Next

[Tutorial 18: Vision aiming](18-vision-aim.md)
