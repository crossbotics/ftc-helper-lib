# Tutorial 17: Launcher

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a shooter that spins a flywheel, sets the hood angle from the distance, and feeds a piece only when everything is ready.
The main track builds a shooter with less code: [A shooter, a camera and a turret](start/09-shooter-and-camera.md).

**You will learn:** how the code controls a flywheel shooter (speed, ready, feeding), how to build a
shot table from real shots, and how `Launcher` feeds a piece only when everything is ready.

**You need:** motor `flywheel` (with its encoder cable), servo `hood`, motor `feeder`. Do Tutorials 03 and
14 first. Wear safety glasses: a flywheel throws things.

**Full program:** [`Tut17Launcher.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut17Launcher.java)
**Reference:** [launcher.md](../modules/launcher.md)

---

## 1. How a flywheel shot works

A spinning wheel grabs the game piece and throws it. Two things decide where the piece lands: the
wheel's **speed** (RPM: turns per minute) and the **angle** the piece leaves at (the hood). Both depend on
distance. One rule matters more than any other: **never feed a piece into a wheel that is not up to
speed.** Otherwise the piece falls short.

## 2. The flywheel

```java
Flywheel flywheel = Flywheel.create(hardwareMap, "flywheel")
        .countsPerRev(28)       // encoder counts per ONE flywheel turn
        .maxRpm(6000)           // the motor's top speed
        .startingGains()        // the hub's speed control (a start)
        .tolerance(75)          // "at speed" = within 75 rpm...
        .settleTime(0.15);      // ...for 0.15 s
```

The hub holds the speed itself, fast and smooth. It needs four gains for this: P, I, D and F (like
Tutorial 07). `startingGains()` calculates them from the motor's top speed (`maxRpm`) and `countsPerRev`.
This is REV's formula, and it works for any motor. Set `maxRpm` to your motor's speed: 6000 for a goBILDA
6000 RPM motor. Later, the [Flywheel Finder](../guides/finders.md) measures your wheel and gives better
gains. Then put them in `.pidf(p, i, d, f)` in place of `startingGains()`.

"Ready" needs the speed to STAY in the band for 0.15 s. So one lucky reading during spin-up does not count.

**countsPerRev:** a bare goBILDA/REV motor encoder gives 28 counts per motor turn.

- **Flywheel turns at the motor's speed:** 28.
- **Geared:** multiply 28 by the motor turns per flywheel turn.
- **Encoder on the flywheel shaft** (plugged into this motor's encoder port): that encoder's own counts
  per turn.

**Check the reading:** run at 3000 rpm. Compare with a tachometer (a tool that measures turning speed),
or count turns in a slow-motion video. When the two do not match, countsPerRev is wrong.

## 3. The hood and the feeder

```java
PositionServo hood = PositionServo.create(hardwareMap, "hood")
        .degrees(0.10, 0, 0.90, 25)     // measured: raw 0.10 = 0 deg, raw 0.90 = 25 deg (1)!
        .travelTime(0.4); // (2)!
MotorFeeder feeder = MotorFeeder.create(hardwareMap, "feeder").power(1.0).feedTime(0.25); // (3)!
```

1. The hood works in degrees: two measured points and a straight line in between.
2. The servo needs about 0.4 s for a full move. A servo cannot report where it is. So the launcher
   uses this number to know when the hood has arrived.
3. Full power (1.0) for 0.25 seconds pushes one piece into the flywheel.

To measure the hood, move the servo with the Hardware Check. Write down the raw position at the lowest
and the highest angle. Then measure those angles (a phone level app works). Any part that goes to an
angle can be the hood (an [`AngleDevice`](../modules/mechanism.md#angledevice)). In the robot in five
files, the [`Hood`](../reference/parts/hood.md) module does all this from `Tunables.Hood`, for a servo or
a motor, with a Hood Finder ([lesson 9](start/09-shooter-and-camera.md#a-hood-the-shot-angle)).

## 4. Fill the shot table

Measure these numbers with the **Shot Tuner**, and the flywheel gains with the **Flywheel Finder**
([tutorial 21](21-finders.md)). The Shot Tuner shows the `new ShotTable()` lines to copy.

```java
ShotTable table = new ShotTable()
        .add(24, 2600, 6)
        .add(48, 3100, 14)
        .add(72, 3700, 21);
```

To get YOUR numbers:

1. Press X on gamepad 1: **manual rpm** mode. D-pad left/right = rpm -/+ 100.
2. Put the robot 24 in from the target. Use a tape measure, to the same point on the robot every time.
3. Find the rpm and hood angle where 5 shots in a row score. Write them down.
4. Repeat at 3-5 distances, across the area you will shoot from.
5. Put the points in the table. Between them, the table draws straight lines (36 in is halfway between
   24 and 48). Outside them, it uses the nearest end. So it never guesses past what you tested, and
   telemetry says "(outside the table)".

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

`shoot()` does not wait: it only asks for shots. `launcher.update()` feeds each piece when everything is
ready, so the robot keeps driving in the meantime. `shotInterval(0.3)` gives the flywheel 0.3 s to get
back to speed between pieces. `shoot(3)` does these steps:

1. Spin up, when the flywheel is not spinning yet.
2. Wait until the flywheel is ready AND the hood has arrived (AND any `requireReady` checks, like an
   aimed turret).
3. Feed one piece.
4. Wait `shotInterval`, then repeat.

When it is not ready within `readyTimeout`, it gives up and says why: "gave up: flywheel".

## Run it

1. Put on safety glasses. Start with no game piece. Press ▶ (start on the Driver Hub).
2. Press A: the flywheel spins up, and telemetry shows about "2850 / 2850 rpm READY". The program starts
   at 36 in, halfway between the 24 and 48 in rows of the table.
3. Press RB without a piece: the feeder runs once. Press LB: it runs three times, 0.3 s apart.
4. With pieces, D-pad up/down changes the distance, and the hood moves. Shoot.
5. Stop the OpMode and unplug the flywheel's encoder cable. Run again and `shoot(1)`. The flywheel can
   never read "ready", so the launcher waits, then shows "gave up: flywheel". That is the safety rule at
   work. Plug the cable back in.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Never READY | countsPerRev is wrong (the speed reads wrong), or the F gain is too low (the wheel never reaches the speed). |
| Speed overshoots then settles slowly | Lower P. Tune F first, so the speed gets close by itself. |
| Shots fall short when shooting fast | `shotInterval` is too short: the wheel has not recovered. |
| Hood never "arrives" | `travelTime` is too long. Measure it. |
| "gave up: hood" | The hood's `travelTime` is longer than `readyTimeout`. |

## Try this

1. Add `.requireReady("Driver", () -> gamepad2.right_trigger > 0.5)`: then the launcher shoots only while
   the driver holds the trigger.
2. Add `.idleRpm(1500)`: a slow idle makes spin-up faster. Measure the difference.
3. Add a second table for another game piece, and switch tables with a button.

## Next

[Tutorial 18: Vision aiming](18-vision-aim.md)
