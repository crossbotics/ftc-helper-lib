# Turret

A part that turns to aim, inside its limits, by hand or following the camera's goal. Learn it step by step:
[Setting up a turret](../../guides/turret-setup.md). That page has every way to build a turret (what turns
it, how it knows its angle, its range, homing, a hood, a camera), with the exact lines for each.

## In Robot.java

```java title="Robot.java"
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));     // a motor
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75).homeSwitch("turret_home"));
turret = add(Turret.servo("turret").part("gobilda-2000-speed").follows(camera));          // a servo
turret = add(Turret.crServo("turret").part("axon-max-mk2").encoder("turret_enc", 8192));  // CR servo + Through Bore
turret = add(Turret.crServo("turret").part("axon-max-mk2").analogEncoder("turret_pos").continuous());   // slip ring + absolute encoder
```

**What turns it.** Write one of these:

| Made with | What it is |
| --- | --- |
| `Turret.motor(name, motorId)` | a motor. Its encoder counts the angle |
| `Turret.servo(name)` | a standard servo (it goes to an angle by itself). It uses `posA`, `angleA`, `posB`, `angleB` and `travelSec` in Tunables, with no PID control and no homing. A servo cannot say where it is, so **at ▶ the library sends it to `startAngle`** (or to the middle of its range when there is none). In TeleOp after autonomous, it goes where autonomous left it, so it does not jump. From then on the library knows its angle: the stick, following and interlock rules work from the first loop |
| `Turret.crServo(name)` + `encoder(port, countsPerRev)` | a CR servo with an encoder on a motor port (REV Through Bore: 8192). The port may also run another part's motor: the turret reads only its count |
| `Turret.crServo(name)` + `analogEncoder(port)` | a CR servo with an absolute encoder on an analog port. It knows its angle at once (`encoderOffset`) |

**The options:**

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `.gearRatio(r)` | recommended | how many turns the counting device makes per turret turn: the motor (a motor turret) or the encoder (a CR servo turret) | 1 |
| `.part(id)` | recommended | which servo it is, from the [parts catalog](../../guides/parts-catalog.md) | - |
| `.reversed()` | optional | the turret turns the other way. Use it when + power turns it clockwise | - |
| `.encoderReversed()` | optional | flips a CR servo's encoder. Use it when the encoder counts the wrong way | - |
| `.homeSwitch(name)` | optional | a touch sensor or a magnetic switch (a "Digital Device", active low). The turret homes (finds its angle on the switch) at ▶, slowly (`homeAngle`, `homeDirection`, `homingPower`, `homingTimeout`). It sets its angle again each time it passes the switch. When the switch is missing from the configuration, INIT shows a note and the turret uses `startAngle` | no homing: it starts at `startAngle` |
| `.continuous()` | optional | a slip ring: no limits, always the shortest way, never "out of range". The angle shows -180 to 180 | it stays inside `min` and `max` |
| `.follows(camera)` | optional | follows the camera's goal. With odometry and `Tunables.Aim.goalX, goalY`, the odometry takes over while the camera does not see it ([Aim](aim.md)) | it does not follow |
| `.followsOdometry()` | optional | follows the goal with no camera: from the odometry and `Tunables.Aim`'s goal point | it does not follow |
| `.cameraOnBody()` | optional | the camera is on the robot, not on the turret | the camera is on the turret |
| `.fullRange()` | optional | a servo turret: the servo's whole turn (500-2500 us, a goBILDA 2000 servo 300 degrees instead of 270). Measure `posA`/`posB` after you turn it on ([full range](claw.md#a-servos-full-range)) | the FTC SDK's normal range |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

A following turret and whole-robot aim are separate modes: see [Aim](aim.md).

## In Tunables.java

<!-- tunables: Turret -->
The section's name is the device's name in CamelCase: `turret` reads `Turret`. Its angles are in degrees:
0 = straight forward, counterclockwise +. [Setting up a turret](../../guides/turret-setup.md) says which lines
your turret needs.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-turret -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double startAngle = DEFAULT;                  // where you set it by hand before INIT (0)
    public static double kP = DEFAULT;                          // push per degree off (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = DEFAULT;                    // the most power (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `min`, `max` | the range: it never turns past them (`continuous()` ignores them) | degrees | min -90, max 90 | no | measure (turn by hand, read the angle) | the wires or the robot allow more or less |
| `startAngle` | where it points at INIT with no switch and no angle from the program before | degrees | 0 (the middle of `min` to `max` when 0 is outside it, with an INIT note). A servo turret: the middle of the range | no | put it there by hand | your start mark is not straight forward |
| `kP` | how hard it turns toward the goal, per degree off | power per degree | 0.02 | yes, at once | test | slow to aim (more) or shakes (less) |
| `kI` | a push that grows while it stays short | power | 0 | yes, at once | test | it stops a degree short |
| `kD` | brakes it as it gets close | power | 0.001 | yes, at once | test | it overshoots (more) |
| `maxPower` | the most power | 0 to 1 | 0.8 | no | test | it swings too hard |
| `tolerance` | how close counts as aimed | degrees | 1.0 | no | test | the launcher waits too long (more) |
| `slowZone` | it slows down within this of `min` and `max` | degrees | 10 | no | test | it hits its ends hard (more) |
| `slowZonePower` | the power at the very end of the slow zone | 0 to 1 | 0.25 | no | test | it stalls near an end (more) |
| `cameraOffset` | the camera is turned this much from the turret's forward (the turret's own; whole-robot aim uses `Tunables.Aim.cameraOffset`) | degrees | 0 | no | Aim Tuner | shots go a little to one side |
| `outOfRangeHold` | an odometry goal this far past a limit, behind the robot, makes it hold where it points | degrees | 10 | no | test | it swings limit to limit when the goal is behind |
| `keepAngle` | TeleOp starts from the angle the last program ended at (within 5 minutes) | true/false | true | no | none | you turn the turret by hand between programs: `false` |
| `homeAngle` | with a home switch: the turret's angle where the switch closes | degrees | REQUIRED with `homeSwitch(...)` | no | measure ([how](../../guides/turret-setup.md)) | the switch moved |
| `homeDirection` | with a home switch: which way it turns to find it (+1 counterclockwise, -1 clockwise) | +1 / -1 | from `startAngle` toward `homeAngle` | no | none | it looks the long way round |
| `homingPower` | with a home switch: the power while it looks for it | 0 to 1 | 0.2 | no | test | homing is too slow (more) or hits hard |
| `homingTimeout` | with a home switch: it stops with a fault when it does not find the switch in time | seconds | 5 | no | test | a slow turret needs longer |
| `encoderOffset` | an absolute encoder: its reading with the turret straight forward | degrees | 0 | no | read it at INIT ([how](../../guides/turret-setup.md)) | the encoder was moved |
| `posA`, `angleA` | a servo turret: one servo position and the turret's angle there | 0 to 1, degrees | REQUIRED for `Turret.servo` | no | Servo Finder | the servo or linkage changed |
| `posB`, `angleB` | a servo turret: a second position and its angle | 0 to 1, degrees | REQUIRED for `Turret.servo` | no | Servo Finder | the same |
| `travelSec` | a servo turret: time from `posA` to `posB` (not 0 to 1) | seconds | 0.6 (with `.part(...)`: from the servo's catalog speed) | no | test (time it) | it fires before it arrives (more) |

### What each number does, in more words

- **`min`, `max`:** degrees, counterclockwise +, 0 = forward. It never turns past them. When the goal is outside,
  the turret waits at the limit, and the Driver Station says "goal out of turret range". Past +/-180 it picks the way round
  that stays inside them.
- **`startAngle`:** where it points at INIT with no switch. For a motor or CR servo turret, the default is 0.
  When 0 is outside `min` to `max`, the default is the middle of the range, and INIT shows a note. For a servo
  turret, it is where the turret goes at ▶, inside its range. It is optional, and its default is always the
  middle of the range (not 0).
- **`kP`, `kI`, `kD`, `maxPower`, `tolerance`, `slowZone`, `slowZonePower`:** a motor or CR servo turret only.
  A servo turret reads none of these.
- **`cameraOffset`:** the turret's own. The library adds these degrees to where the turret points. Whole-robot aim has its own
  `Tunables.Aim.cameraOffset` ([Aim](aim.md)).
- **`outOfRangeHold`:** default 10. An odometry goal this many degrees past a limit, behind the robot, makes
  it HOLD where it points. So it does not swing from limit to limit as the heading wobbles. It follows
  again once the goal is back within half of that.
- **With a switch:** `homeAngle` (needed), `homeDirection`, `homingPower`, `homingTimeout`.
- **An absolute encoder:** `encoderOffset`.
- **A servo turret:** `posA`, `angleA`, `posB`, `angleB`, `travelSec`, and `startAngle`.
- **`keepAngle`** (default true): TeleOp starts from the angle the last program ended at (within 5 minutes,
  the same robot app run). So it does not home again after autonomous. The library sends a servo turret
  there at ▶, instead of to `startAngle`.
- **When someone turned the turret by hand since the last program:** an INIT stopped before ▶ forgets the
  carried angle. So turn the turret to `startAngle`, stop the OpMode in INIT (before ▶), and press INIT again.

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.turret.aimAt(45)`.

| Command | What it does |
| --- | --- |
| `aimAt(deg)` | turns to an angle (it stops following) |
| `manual(power)` | moves it by hand. Following pauses while the stick moves |
| `followMode()` | the mode that follows the goal. It is on from ▶ with `follows` or `followsOdometry`. Switch it with a button ([below](#buttons-to-add)) |
| `stop()` | stops |
| `static forgetAngles()` | forgets the angle carried from the last program (tests; turning it by hand between programs) |

| Question | What it tells you |
| --- | --- |
| `isAimed()` | whether it is at the target within the tolerance. While FOLLOWING: whether it is at the goal NOW (a goal seen this moment, homed, in range, not held by an interlock, error within the larger of `tolerance` and `Tunables.Aim.toleranceDeg`). So a launcher that waits for it never fires after the goal is lost. A held turret is never `isAimed()` |
| `isHomed()` | whether it knows its angle (homed on its switch, or no switch) |
| `isHeld()` | whether an [interlock rule](interlock.md) holds it back from where it should point |
| `isOutOfRange()` | whether the goal is past its range (at a limit, or holding while it is behind) |
| `angle()` | the angle |
| `aimErrorDeg()` | degrees from the goal now (NaN = unknown) |
| `aimSource()` | where the goal came from: CAMERA, ODOMETRY, NONE |
| `part()` | the `launcher.Turret` inside |
| `zone()` | its zone for an [interlock rule](interlock.md) (degrees, repeats every 360) |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

**On the Driver Station:**

- One line: `Turret : 12 deg  AIMED  following: camera`. `AIMED` shows only when `isAimed()`: while
  following, at a goal seen now. Instead of `camera` it can say `odometry`, or `no goal` (it holds where it
  points).
- After ▶: `homing: looking for the switch`.
- A servo turret before ▶: `not sent anywhere yet (at ▶: 0 deg)`.

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-turret -->
```java title="Controls.java"
g2.rightStickX.use(v -> robot.turret.manual(-v)).says("by hand (right = clockwise)");
g2.rightStickButton.toggle(robot.turret.followMode()).rumble().says("follow the goal on/off");   // only with follows(...)
```

The operator's right stick X turns the turret by hand. A click of the same stick switches following on and
off: 2 buzzes = on, 1 buzz = off. Robot 1 uses the same click for its auto-aim.

**With one driver:** this line goes on gamepad 1's right stick click, which is also the library's field-centric
button. Move field-centric first, as Robot 1 does. In `libraryButtons(...)` in `Controls.java`, write
`lib.fieldCentric(null);` and, in `buttons(...)`, put field-centric on hold RT, then click the right stick:

```java title="Controls.java"
g1.rt.with(g1.rightStickButton, () -> driving().toggleFieldCentric()).says("field-centric on/off");
```

---

**Look it up:** [`Turret` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Turret.html)
(every method). **The other parts:** [Reference](../README.md).
