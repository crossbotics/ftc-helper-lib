# Hood

A launcher's hood: the part that tilts to make the shot leave steeper or flatter. Any design fits: a servo
or a motor with an encoder, a straight gear or a linkage, any range, either direction. It is part of a
[Launcher](launcher.md).

## In Robot.java

```java title="Robot.java"
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .hood(Hood.servo("hood").part("gobilda-2000-torque"))   // or Hood.motor("hood", "gobilda-5203-117")
        .speedFromDistance(camera));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `.hood(...)` on the launcher | **yes** | the launcher's hood. The launcher takes its angle from the shot table's third column, and waits for it before it feeds | no hood |
| `Hood.servo(name)` | **yes**, one of the two | a hood on a standard servo | - |
| `Hood.motor(name, motorId)` | **yes**, one of the two | a hood on a motor with an encoder. Its encoder counts (ticks) start from where the hood is at INIT, so start with the hood on stop A | - |
| `.part(id)` | recommended (servo) | which servo it is, from the [parts catalog](../../guides/parts-catalog.md). The library takes `travelSec` from its speed | `travelSec` 0.5 |
| `.reversed()` | optional | it turns the other way | - |
| `.fullRange()` | optional, servo only | the servo's whole turn ([full range](claw.md)), at INIT and in the Hood Finder. Find `posA`/`posB` after you turn it on | the normal range |
| `.shotAngles(atA, atB)` | optional | for the simulator: the launcher's shot angle with the hood at `angleA` and at `angleB`. The simulated shot follows the hood servo | - |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

With a hood, the shot table gets a third column: the hood angle ([Launcher](launcher.md)):

```java
public static double[][] speedByDistance = {{24, 2200, 4}, {48, 2550, 12}, {72, 2900, 18}};   // Tunables.Launcher
```

The library has no hood on a CR servo with an absolute encoder yet. One can fit the same way: anything that
is an [`AngleDevice`](../../modules/mechanism.md#angledevice) can be a hood of the
[plain launcher](../../modules/launcher.md#launcher) (`new Launcher(...).hood(myHood)`).

## In Tunables.java

<!-- tunables: Hood -->
The section's name is the device's name in CamelCase (`hood` reads `Hood`, `shooter_hood` reads
`ShooterHood`). A servo hood's numbers are in servo positions, a motor hood's in encoder counts. When you
write `angleA` and `angleB` (or a `calibration`), "angle" below is your angle, in degrees or any scale you
chose. Without them, the angle is the position itself. Every default that says "of the range" fits degrees, positions and counts.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE`
means you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-hood -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // posA and posB: the servo positions a hair off the hood's two hard stops. Never past them.
    public static double posA = MEASURE;                        // servo position at stop A (Hood Finder)
    public static double posB = MEASURE;                        // servo position at stop B (Hood Finder)
    // Optional: the hood's angle at each stop (any scale, e.g. 0 = steep end, 30 = flat end).
    public static double angleA = MEASURE;                      // the angle at stop A (an angle app)
    public static double angleB = MEASURE;                      // the angle at stop B (an angle app)
    public static double travelSec = DEFAULT;                   // stop to stop, seconds (0.5, or the servo's)
    public static double startAngle = DEFAULT;                  // where it goes at ▶ (angle A)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `posA` | the position a hair off one hard stop: a hard limit | 0 to 1 / counts | REQUIRED, unless `calibration` | yes, at once | Hood Finder | the hood or its servo was moved |
| `posB` | the position a hair off the other hard stop | 0 to 1 / counts | REQUIRED, unless `calibration` | yes, at once | Hood Finder | the same |
| `angleA`, `angleB` | the hood's angle at `posA` and `posB` (both, or neither) | your angle unit | none: the angle is the position | no | Hood Finder | you want the table in degrees |
| `calibration` | instead of `posA`/`posB`: `{position, angle}` rows for a linkage. The first and last rows are the stops | position, angle | none | no | Hood Finder | the hood is not a straight gear |
| `startAngle` | where it goes at ▶ | angle | angle A | no | none | another start angle |
| `stepAngle` | one step of `step(n)` | angle | 1/25 of the range | no | none | the drivers want bigger steps |
| `byHandPerSec` | the speed with the stick pushed all the way | angle per second | the range in 2.5 s | no | test | the stick is too fast |
| `trimMax` | the most hood trim | angle | 1/5 of the range | no | none | you need more trim |
| `tolerance` | a motor hood: how close counts as arrived | angle | 1/50 of the range | no | test | the launcher waits too long (more) |
| `travelSec` | a servo hood: time from stop to stop (`posA` to `posB`) | seconds | 0.5 (with `.part(...)`: from the servo's catalog speed) | yes, at once | test (time it) | it fires before the hood arrives (more) |
| `kP` | a motor hood: power per count off | power per count | 0.005 | yes, at once | test | slow (more) or shakes (less) |
| `maxPower` | a motor hood: the most power | 0 to 1 | 0.5 | yes, at once | test | it hits its stops hard |

**Calibration.** `posA` and `posB` are the servo positions (encoder ticks for `Hood.motor`) a hair off the
hood's two hard stops. They are the hood's **hard limits**: the library keeps every angle (a table row, a trim, a
stick) between them. Both must be in `Tunables.java`, or INIT stops. The
[Hood Finder](../../tuning/04-hood-finder.md) finds them. Then pick one of these:

| You write | The hood's "angle" is | The table's third column holds |
| --- | --- | --- |
| `posA`, `posB` only | the servo position (or ticks) itself: you measure nothing more | servo positions, `{48, 2550, 0.42}` |
| `posA`, `posB`, `angleA`, `angleB` | your angle, on a straight line between the two stops (any scale, either way round) | angles, `{48, 2550, 12}` |
| `calibration = {{0.20, 0}, {0.32, 8}, {0.45, 18}, {0.65, 30}}` | a linkage or cam: {position, angle} rows, and the library blends between them. The first and last rows are the stops | angles |

A servo hood with angles:

```java
public static class Hood {                                   // Tunables.java
    public static double posA = 0.20, posB = 0.65;           // a hair off its two stops (Hood Finder)
    public static double angleA = 0, angleB = 25;            // optional: its angle there
    public static double travelSec = 0.5;                    // stop to stop (a servo cannot say where it is)
}
```

A motor hood never pushes further past a stop. The hood does not move in INIT. It goes to `startAngle` at ▶.

## Commands

Use them through the launcher: `robot.launcher.hood().setAngle(12)`.

| Command | What it does |
| --- | --- |
| `setAngle(a)` | goes to an angle (kept inside the stops) |
| `goToStart()` | goes to `startAngle` |
| `step(n)` | moves `n` x `stepAngle`. While the table sets the angle, this is a trim |
| `byHand(stick)` | moves with the stick, every loop |
| `trimBy(a)`, `setTrim(a)`, `resetTrim()` | change the hood trim, which the library adds to the table's angle |

| Question | What it tells you |
| --- | --- |
| `angle()` | where it is now: the encoder, or the time estimate (like `Turret.angle()`) |
| `targetAngle()` | where it is going |
| `isAtTarget()` | whether it is there (a servo: whether it should be there by now) |
| `followsTable()` | whether the shot table sets the angle now |
| `trim()` | the hood trim |
| `minAngle()`, `maxAngle()`, `startAngle()` | its range; its start |
| `usesAngles()` | whether `angleA`/`angleB` or `calibration` are written |
| `isMotor()`, `isReversed()` | whether it is a motor hood; whether it turns the other way |
| `pulseRangeUs()` | `{500, 2500}` with `fullRange()`, or null without it |
| `part()` | the [`AngleDevice`](../../modules/mechanism.md#angledevice) the launcher drives |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

**Ready.** The launcher feeds only when the hood has arrived. Until then, its why-not-ready list says "hood".
A motor hood knows from its encoder when it is within `tolerance`. A servo hood uses a time estimate:
`travelSec` for the whole range, a share of it for a shorter move. The library does not send changes under
1/100 of the range, so a distance that jitters a little does not move the hood. Then the hood never stays
"moving".

The Driver Station shows `Hood : 12.5 deg (table, trim +1.0 deg)` (`moving` until it should be there).

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them. A line on a button gamepad 1 already uses (its sticks, the stick clicks, BACK) stops INIT.
  INIT names the button: give the line a free one.
- **On gamepad 1, with two drivers:** write `g1` and a free button or stick. If you pick a button the library
  uses, INIT stops and names it.

<!-- recipe: buttons-hood -->
```java title="Controls.java"
g2.leftStickY.use(robot.launcher.hood()::byHand).says("hood angle (up = bigger angle)");
```

**By hand and the trim.** The usual operator stick is left stick Y: up = a bigger angle.

- While the shot table sets the angle, the stick is a **hood trim**. The library adds it to the table's
  angle, at most +/- `trimMax`. The launcher's double-tap of D-pad down sets it back to 0, and so does every
  INIT.
- With a fixed speed, a table without hood angles, or before the camera has seen the goal, the table does not
  set the angle. Then the stick moves the hood itself.
- The speed trim fixes shots that land short or long. The hood trim fixes shots that arrive at the wrong
  slope: they bounce off the rim, or worn balls launch flatter. Use the speed trim first.

---

**Look it up:** [`Hood` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Hood.html)
(every method). **The other parts:** [Reference](../README.md).
