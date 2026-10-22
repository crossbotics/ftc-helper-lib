# Setting up a turret

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Turrets are built in many ways. This page asks you seven questions about yours. Each answer gives
you the exact line for `Robot.java` and the lines for `Tunables.java`. At the end: a summary table,
four complete examples, how to find each number, what the INIT screen shows, and common mistakes.

Reference: [Turret in the robot module](../modules/robot.md#turret). The lesson that builds a shooter
with a turret: [Lesson 9](../tutorials/start/09-shooter-and-camera.md). Signs: angles are in degrees,
**0 = straight forward, counterclockwise (to the left, seen from above) is +** ([conventions](../conventions.md)).

```mermaid
flowchart TD
    Q1["1. What turns it?<br/>motor / servo / CR servo + encoder"] --> Q2["2. How does it know its angle?<br/>start position / home switch / absolute encoder / servo"]
    Q2 --> Q3["3. How far can it turn?<br/>+/-90, +/-180, +/-190, round and round"]
    Q3 --> Q4["4. Where is the camera?<br/>on the turret / on the body / none"]
    Q4 --> Q5["5. A hood?<br/>none / servo / motor"]
    Q5 --> Q6["6. Can it hit another part?<br/>interlock rules"]
    Q6 --> Q7["7. Is it off the robot's centre?<br/>shooting on the move"]
```

## 1. What turns it?

| What turns it | `Robot.java` | Notes |
| --- | --- | --- |
| A motor (its own encoder counts the angle) | `Turret.motor("turret", "gobilda-5203-117").gearRatio(5)` | `gearRatio` = motor turns per turret turn (gears or belt). The motor id is from the [parts catalog](parts-catalog.md). |
| A standard servo (goes to an angle by itself) | `Turret.servo("turret")` | No encoder, no PID, no homing. A servo turns about 300 degrees at most: gear it for a smaller turret range. |
| A continuous-rotation (CR) servo + an encoder on a motor port (e.g. REV Through Bore) | `Turret.crServo("turret").encoder("turret_enc", 8192)` | 8192 = the Through Bore's counts per turn. The port may also run another part's motor: its count is only read, never reset. `gearRatio` = ENCODER turns per turret turn (1 = on the turret's axis). |
| A CR servo + an absolute encoder on an analog port (e.g. an Axon's feedback wire) | `Turret.crServo("turret").analogEncoder("turret_pos")` | It knows its angle at once, at INIT. |

Options for every kind: `.reversed()` (positive power turns it clockwise: flip it), and for a CR servo
`.encoderReversed()` (the encoder counts down while the turret turns counterclockwise).

**Tunables for a motor or CR servo turret** (the PID that turns it to an angle):

```java
public static class Turret {
    public static double kP = 0.02, kI = 0, kD = 0.001;          // the turning (tutorial 07 explains PID)
    public static double maxPower = 0.8, tolerance = 1.0;        // tolerance: "aimed" within this many degrees
    public static double slowZone = 10, slowZonePower = 0.25;    // slow down within 10 deg of a limit
}
```

**Tunables for a servo turret** (two measured points and the travel time):

```java
public static class Turret {
    public static double posA = 0.10, angleA = -90;   // servo position 0.10 points the turret at -90
    public static double posB = 0.90, angleB = 90;    // servo position 0.90 points it at +90
    public static double travelSec = 0.6;             // seconds from posA to posB (time it)
    public static double tolerance = 1.0;
}
```

## 2. How does it know its angle?

A motor's encoder counts from where it was at INIT, so the turret must learn where "0" is. Pick one:

| How | `Robot.java` | `Tunables.Turret` | Homing at START? |
| --- | --- | --- | --- |
| **Start position** (no switch): you put it there by hand before INIT | nothing extra | `startAngle = 0` (the default: straight forward) | no |
| **Home switch**: a touch sensor or a magnetic switch | `.homeSwitch("turret_home")` | `homeAngle = 45` (needed), optional `homeDirection = 1`, `homingPower = 0.2`, `homingTimeout = 5` | yes, slowly |
| **Absolute encoder** (CR servo turret) | `.analogEncoder("turret_pos")` | `encoderOffset = 117` (its reading, 0 to 360, with the turret at 0) | no |
| **Servo** | `Turret.servo(...)` | `posA`, `angleA`, `posB`, `angleB` | no |

**Start position.** Make a mark (or a small printed jig) that holds the turret at `startAngle` and put it
there before every INIT. If it was somewhere else, every angle is off by the difference, and the
turret will happily aim 20 degrees wide. The INIT screen says `Turret : startAngle 0 deg: put the turret
there by hand before INIT, or every angle is off`.

**Home switch.** Either kind works, and the library finds which:

- a touch sensor (e.g. REV Touch Sensor) configured as **REV Touch Sensor**;
- a magnetic switch (e.g. REV Magnetic Limit Switch, with a magnet on the turret) configured as a
  **Digital Device**. It reads LOW near the magnet; the library knows.

At **START** (never in INIT: the game rules want a robot standing still after INIT, and the library never
moves anything before START) the turret turns slowly (`homingPower`) toward the switch: from
`startAngle` toward `homeAngle`, or the way `homeDirection` says (+1 = counterclockwise, -1 = clockwise).
When the switch closes, the turret is at `homeAngle`. Following the goal and "aimed" wait until then.
If the switch is not found within `homingTimeout` seconds it stops and the Driver Station says
`homing failed: switch not found` (a broken switch must not wind up the wires). After homing, **every time
the turret passes the switch** the angle is set right again, so encoder drift (a slipping belt) is fixed
during the match. A switch missing from the configuration: the INIT screen says `NO home switch` and the
start angle is used.

**How to find `homeAngle`:** without the `homeSwitch` line, put the turret at `startAngle` (straight
forward), INIT, START, turn it by hand with the stick until the switch clicks (or the magnetic switch's
light comes on), and read the angle on the Driver Station (`Turret : 43 deg`). That is `homeAngle`.
Then add the line.

**Absolute encoder.** `encoderOffset`: put the turret straight forward, INIT, and read the encoder's
degrees (or set `encoderOffset = 0` and read the turret's angle: that number is the offset). A geared
encoder (`gearRatio` more than 1) repeats every `360 / gearRatio` turret degrees: the turret must start
within half of that of `startAngle`.

### From autonomous to TeleOp

TeleOp's INIT starts the encoder again, but the turret is wherever autonomous left it. So the library
remembers each turret's angle at the end of every program (until the robot app restarts, and for at most
5 minutes) and **TeleOp starts from that angle** (and does not home again). The INIT screen says
`Turret : -37 deg, carried from the last program (12 s ago ...)`. Autonomous never uses it (the robot
was placed by hand). This works for a TeleOp started again too (stopped and INIT again, no autonomous
between). Turned the turret by hand between programs? Restart the robot app, or set
`public static boolean keepAngle = false;` in `Tunables.Turret`.

**Nothing carried over** (the robot app restarted, more than 5 minutes, or a program whose turret never
finished homing): with no switch the turret starts from `startAngle`, and TeleOp's INIT says so plainly:
`no angle from a program before: turn it to startAngle 0 deg by hand and INIT again`. Do it: otherwise
the angle on the Driver Station is off by however far the turret was turned (in a test, it said 4 degrees
while the turret pointed at -12). A home switch avoids this: it finds the real angle at START.

## 3. How far can it turn?

**Any range works.** Write your own `min` and `max` in degrees: the only rule is that `max` is more
than `min`. They do not have to match: `min = -30, max = 250` is fine, and so is a range of more than
one turn. The rows below are only examples.

| Example range | `Robot.java` | `Tunables.Turret` | What it does |
| --- | --- | --- | --- |
| A quarter turn each way | nothing extra | `min = -90, max = 90` | a goal past a limit: it waits at the limit and the Driver Station says `goal out of turret range` |
| Half a turn each way | nothing extra | `min = -180, max = 180` | every direction once; it never turns through the back to get there |
| A bit more than a turn | nothing extra | `min = -190, max = 190` | **the safe way round**: two ways to point at 175 (175 or -185); it picks the one closest to where it points that does not pass a limit |
| Round and round (a slip ring carries the wires) | `.continuous()` | no `min`/`max` | no limits, always the shortest way, never "out of range"; the Driver Station shows -180 to 180 |

`min` and `max` are your wires' limits: never more than the turret can turn without pulling a cable.
Put a hard stop there too (software is not a hard stop). A servo turret's range is between `angleA` and
`angleB` (or `min`/`max`, if narrower).

## 4. Where is the camera?

| Camera | `Robot.java` |
| --- | --- |
| On the turret (turns with it) | `.follows(camera)` |
| On the robot's body | `.follows(camera).cameraOnBody()` |
| None: aim from where the robot is ([odometry](../tutorials/start/07-odometry.md)) | `.followsOdometry()` with `Tunables.Aim.goalX, goalY` |

`follows` turns follow mode on at START (operator BACK switches it). With odometry and the goal point,
the odometry takes over while the camera does not see the goal ([Aim](../modules/robot.md#aim)).
`cameraOffset` (Tunables.Turret): the goal's bearing in the camera's picture when the launcher points
straight at it (a camera beside the launcher). Find it with the Aim Tuner ([finders](finders.md)).

## 5. A hood?

| Hood | `Robot.java` (in the launcher line) | Tunables |
| --- | --- | --- |
| None | nothing | |
| A servo | `.hood(Hood.servo("hood"))` | `Tunables.Hood`: `posA`, `posB` (hard stops), optional `angleA`, `angleB`, `travelSec` |
| A motor | `.hood(Hood.motor("hood", "gobilda-5203-117"))` | `posA`, `posB` in ticks, ... |

The shot table's third column is the hood angle ([Hood](../modules/robot.md#hood)); the Hood Finder finds
its stops. The hood belongs to the launcher, not to the turret: it works the same with or without one.

## 6. Can it hit another part?

A lift, an arm or a climber in the turret's path: write an **interlock rule** in `Robot.java`, with the
zones in `Tunables.java`. While the lift is up, the turret stays out of the back (and the other way
round). See [Two parts that must not collide](interlocks.md).

```java
keepApart("TurretLift", turret.zone(), lift.zoneAbove());          // Robot.java

public static class TurretLift {                                    // Tunables.java
    public static double turretFrom = 130, turretTo = 218;          // degrees: the turret's zone
    public static double liftAbove = 270;                           // counts: the lift's zone
}
```

## 7. Is it off the robot's centre?

Shooting while driving leads the shot for the robot's speed. If the turret (or the camera) is not on
the robot's centre, give the offsets in `Tunables.Aim` ([Aim: shooting on the move](../modules/robot.md#aim)).
A turret that follows the odometry assumes it turns about the robot's centre.

## Summary

| You have | `Robot.java` | `Tunables.Turret` must have | Homing |
| --- | --- | --- | --- |
| Motor, no switch | `Turret.motor(name, motorId).gearRatio(r)` | `min`, `max`, (`startAngle`) | none: start it at `startAngle` |
| Motor + touch or magnetic switch | `... .homeSwitch("turret_home")` | `min`, `max`, `homeAngle` | at START |
| Motor, slip ring | `... .continuous()` | (`startAngle`) | none, or a switch |
| Servo | `Turret.servo(name)` | `posA`, `angleA`, `posB`, `angleB`, `travelSec` | none |
| CR servo + Through Bore | `Turret.crServo(name).encoder(port, 8192)` | `min`, `max`, (`startAngle`) | none, or a switch |
| CR servo + absolute encoder | `Turret.crServo(name).analogEncoder(port)` | `min`, `max`, `encoderOffset` | none |

## Four complete examples

**A. Team 8938's turret (motor, magnetic home switch, servo hood).** A 312 RPM goBILDA motor turns a
goBILDA Gear-Driven Turret bearing (2.75 motor turns per turret turn); a REV Magnetic Limit Switch marks
one angle; the hood is a servo. (The [v2bot example](../../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot/Robot.java)
has the whole robot.)

```java
// Robot.java
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75).homeSwitch("turret_home").follows(camera));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
        .hood(Hood.servo("hood")).speedFromDistance(camera).requireReady("turret aimed", () -> turret.isAimed()));

// Tunables.java
public static class Turret {
    public static double min = -190, max = 190, homeAngle = -45;   // homeAngle: example, measure your own
    public static double kP = 0.02, kI = 0, kD = 0.001, maxPower = 0.8, tolerance = 1.5;
}
public static class Hood {
    public static double posA = 0.20, posB = 0.65, angleA = 0, angleB = 25, travelSec = 0.45;
}
```

**B. A servo turret, +/-90, no hood.** Light and simple: no encoder and no homing.

```java
// Robot.java
turret = add(Turret.servo("turret").follows(camera));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
        .speedFromDistance(camera).requireReady("turret aimed", () -> turret.isAimed()));

// Tunables.java
public static class Turret {
    public static double posA = 0.17, angleA = -90, posB = 0.83, angleB = 90, travelSec = 0.5;
}
```

**C. A slip-ring turret with an absolute encoder.** A CR servo turns it round and round; an encoder on
its axis says where it is at once.

```java
// Robot.java
turret = add(Turret.crServo("turret").analogEncoder("turret_pos").continuous().follows(camera));

// Tunables.java
public static class Turret {
    public static double encoderOffset = 117;                       // the reading with the turret forward
    public static double kP = 0.015, kI = 0, kD = 0, maxPower = 1.0, tolerance = 2;
}
```

**D. No turret: the whole robot turns.** A fixed shooter; the drive turns the robot toward the goal while
a button is held ([Aim](../modules/robot.md#aim)).

```java
// Robot.java: no turret line
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
        .speedFromDistance(camera).requireReady("aimed", () -> aimMode().isOn() && aim().isAimed()));

// Controls.java
g1.rb.toggle(robot.aimMode()).says("aim at the goal");
```

## How to find each number

| Number | How |
| --- | --- |
| `gearRatio` | count the teeth: turret gear / motor gear (a 20T pinion on a 100T ring = 5) |
| `min`, `max` | turn the turret by hand to where the wires are tight, both ways; take a few degrees less |
| `startAngle` | where your mark or jig holds it (0 = straight forward is easiest) |
| `homeAngle` | turn it to the switch and read the angle ([above](#2-how-does-it-know-its-angle)) |
| `posA`, `posB`, `angleA`, `angleB` (servo) | the [Servo Finder](finders.md): two positions, and the angle at each (a phone angle app) |
| `travelSec` (servo) | time a move from posA to posB |
| `kP`, `kD`, `tolerance` | the tuning menu ([tuning](tuning.md)); copy the values back into `Tunables.java` |
| `cameraOffset` | the Aim Tuner ([finders](finders.md)) |
| `encoderOffset` | the reading with the turret straight forward |

## What the INIT screen shows

- `Turret : startAngle 0 deg: put the turret there by hand before INIT, or every angle is off` (autonomous)
- `Turret : no angle from a program before: turn it to startAngle 0 deg by hand and INIT again, or every
  angle is off` (a TeleOp with nothing carried over: the robot app restarted, more than 5 minutes, or
  `keepAngle = false`)
- `Turret : home switch at 0 deg: found at START` and `Turret homing : at START it turns clockwise slowly to find the switch`
- `Turret : -37 deg, carried from the last program (12 s ago; keepAngle = false to turn this off)`
- `Turret : absolute encoder: 31 deg (encoderOffset 117)`
- After START, while homing: `Turret : homing: looking for the switch`; then `Turret : 12 deg following: camera`.
- With interlock rules: `Interlock TurretLift: Turret between 130 and 218 deg / Lift above 270 counts`.

## Common mistakes

- **The turret turns the wrong way** (+ power turns it clockwise): add `.reversed()`. A CR servo's
  encoder counting the wrong way: `.encoderReversed()`. Check with the stick: right = clockwise.
- **Every angle is off by the same amount**: it was not at `startAngle` at INIT. Use a mark or a jig,
  or add a home switch.
- **Homing turns the wrong way and stops with "switch not found"**: set `homeDirection` (+1 or -1).
- **`homeAngle` outside `min`..`max`**: the switch must be inside the range the turret may turn.
- **The angle drifts during a match**: a belt slips. A home switch fixes it at every pass.
- **A slip-ring turret with `min`/`max`**: `continuous()` ignores them; remove them to avoid confusion.
- **The turret is aimed but the shots go wide by the same angle**: a camera beside the launcher needs
  `cameraOffset`.
- **TeleOp starts with the wrong angle after someone turned the turret by hand**: restart the robot app,
  or `keepAngle = false`.

## In the simulator

`./gradlew :sim:fromRobot` writes every kind into the simulator robot file: a motor turret (with its home
switch as `touch.turret_home = turret, at=..., width=...`), a slip-ring turret (`motor ... = free`), a servo
turret (a `turret` shape on the servo), and a CR servo turret (its encoder is a `motor.... drivenBy=turret`
line that the CR servo turns; an absolute encoder is `analog.turret_pos = ...`). See the
[simulator guide](simulator.md).
