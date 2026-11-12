# Lift

A slide on a motor that goes up and down to named heights. It can have a bottom switch. Learn it step by step:
[Robot.java, the other parts](../../five-files/01-robot-java.md#the-other-parts).

## In Robot.java

```java title="Robot.java"
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Lift.slide(name)` | **yes** | a lift on one motor. Its encoder counts the height | - |
| `.part(id)` | recommended | which motor it is, from the [parts catalog](../../guides/parts-catalog.md). When you do not write `kP`, `kD`, `slowZone` and `tolerance`, the library starts them from this motor, scaled to its counts per turn. INIT says so ([First run](../tunables.md#first-run-what-you-get-before-tuning)) | the library's numbers for a lift |
| `.bottomSwitch(name)` | recommended | a switch at the bottom that sets 0. Until the lift has pressed the switch once, the lift only goes down, slowly | 0 is where the lift is at INIT ([below](#where-0-is)) |
| `.alsoMotor(name)` | optional | a second motor, turning the same way ([Two motors](#two-motors-on-one-lift-or-arm)) | one motor |
| `.alsoMotorReversed(name)` | optional | a second motor, turning the other way ([Two motors](#two-motors-on-one-lift-or-arm)) | one motor |
| `.reversed()` | optional | the lift turns the other way. Use it when positive power moves the lift down. With two motors it turns both | - |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([below](#in-the-simulator)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

### Where 0 is

- **With a bottom switch:** the switch sets 0.
- **Without one, 0 is where the lift is at INIT.** The encoder starts from there.
- So press INIT with the lift all the way down. Do this for TeleOp too, after an autonomous that left the
  lift up. If you do not, `max` is too high by that much. INIT reminds you.

### In the simulator

- `.looks(Shape.slide().atMm(...).sizeMm(...).travelMm(421))`: `travelMm` is the stage's travel from `min`
  to `max`.
- A wrist or claw can ride on it: `Shape.arm().on(lift)` ([Shapes](../robot-java.md#shapes-and-parts)).

## Two motors on one lift or arm

Some lifts and arms have two motors: a slide with a motor on each side, or an arm driven from both ends.
Name the second motor on the same line.

```java title="Robot.java"
lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"));   // they face each other (usual)
lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotor("lift_right"));           // both turn the same way
arm = add(Arm.motor("arm_left", "gobilda-5203-117").alsoMotorReversed("arm_right"));
```

| Attribute | What it sets |
| --- | --- |
| `alsoMotor(name)` | a second motor, turning the same way as the first for the same power |
| `alsoMotorReversed(name)` | a second motor turning the other way (two motors that face each other, mirror images) |

- Both motors get exactly the same power at the same moment: every command, the stick, `stop()`, pause
  (BACK), the limits, interlock rules and actions.
- The library reads only the **first** motor's encoder. Plug the encoder cable into it.
- The Tunables section takes the first motor's name.
- `.reversed()` turns both.

**Pick `alsoMotor` or `alsoMotorReversed` by test.** Two motors that push against each other get hot and can
break a gearbox. So take the chain or belt OFF first. Push the lift up a little with the stick (or with the
Motor Finder, one motor at a time). Both motors must turn the way that lifts. If one turns the wrong way, use
the other method.

**What you see:**

- INIT shows `LiftLeft: two motors, "lift_left" (its encoder is read) and "lift_right" (turns the other way,
  same power)`.
- `:sim:fromRobot` writes `motor.lift_right = lift, part=..., with=lift_left, mirrored=true`. In the
  simulator both turn one slide.
- Inside, the lift's [`SafeMotor`](../../modules/safety.md#safemotor) sends every power to both
  (`alsoPowers`).

## In Tunables.java

<!-- tunables: LiftArm -->
The section's name is the device's name in CamelCase: `lift` reads `Lift`, `lift_left` reads `LiftLeft`, not
`Lift`. With two motors ([`alsoMotor`](#two-motors-on-one-lift-or-arm)) it is the FIRST motor's name. A
Lift's numbers are in encoder **counts** (the encoder's steps), an [Arm](arm.md)'s in **degrees**. This table is
for both.

- `min` and `max` must be in `Tunables.java` (from the [Motor Finder](../../guides/finders.md)), or INIT stops.
- Every other number has a default, so the lift works before you tune it.
- Every number that is not a setting is a named height.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)). The arm's lines are on the
[Arm](arm.md#in-tunablesjava) page.

<!-- recipe: tunables-lift -->
```java title="Tunables.java"
public static class Lift {                                      // "lift" in the configuration
    public static double min = MEASURE;                         // lowest safe point, counts (Motor Finder)
    public static double max = MEASURE;                         // highest safe point, counts (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (from the motor)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (from the motor)
    public static double kF = DEFAULT;                          // hold up against gravity (0.05)
    public static double down = MEASURE;                        // a named height, counts: any name you like
    public static double low = MEASURE;                         // a named height (Motor Finder)
    public static double high = MEASURE;                        // a named height (Motor Finder)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `min` | the bottom of the safe range: it never goes below | counts / degrees | REQUIRED | no | Motor Finder (X) | the part touches its bottom stop |
| `max` | the top of the safe range: it never goes above. Must be more than `min` | counts / degrees | REQUIRED | no | Motor Finder (B) | the part touches its top stop |
| `kP` | how hard it pushes toward its target, per unit off | power per unit | Lift 0.004 (with `.part(...)`: scaled to the motor, [First run](../tunables.md#first-run-what-you-get-before-tuning)), Arm 0.02 | yes, at once | test | slow to arrive (more) or shakes (less) |
| `kI` | a push that grows while it stays short | power | 0 | yes, at once | test | it stops a little short and stays there |
| `kD` | brakes it as it gets close | power | Lift 0.0002 (with `.part(...)`: scaled to the motor), Arm 0.001 | yes, at once | test | it overshoots (more) |
| `kF` | a steady push that holds it up against gravity | power | Lift 0.05, Arm 0 | yes, at once | test | the lift sags (more) |
| `maxPower` | the most power it ever gets | 0 to 1 | Lift 1.0, Arm 0.6 | no | test | it moves too violently |
| `slowZone` | it slows down within this distance of `min` and `max` | counts / degrees | Lift 250 (with `.part(...)`: about half a motor turn), Arm 15 | no | test | it slams into its ends (more) |
| `slowZonePower` | the power at the very end of the slow zone | 0 to 1 | 0.2 | no | test | it stalls near an end (more) |
| `tolerance` | how close counts as "arrived" | counts / degrees | Lift 20 (with `.part(...)`: 1/27 of a motor turn), Arm 3 | no | test | actions wait too long (more) |
| `<position>` | every other number is a named position (`down`, `low`, `high`); any name | counts / degrees | Lift: down 0, low 900, high 2400. Arm: stowed 0, score 90 (only when none are written) | yes, at once | Motor Finder (positions P1, P2...) | a new scoring height |

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.lift.goTo("high")`. An [Arm](arm.md) has the
same commands (they share `MotorPositions`).

| Command | What it does |
| --- | --- |
| `goTo(name)` | goes to a named height. Capitals do not matter. A typo shows a warning and does nothing |
| `stepUp()`, `stepDown()` | goes to the next height up or down |
| `manual(power)` | moves by hand. 0 holds it where the driver let go |
| `hold()` | holds where it is |
| `stop()` | stops |

| Question | What it tells you |
| --- | --- |
| `isAtTarget()` | whether it is at its target |
| `isAt(name)` | whether it is at that named height |
| `position()` | where it is now, in counts |
| `positions()` | the names of its heights |
| `part()` | the [`PositionMotor`](../../modules/mechanism.md#positionmotor) inside |
| `secondMotorName()` | the second motor's name, or null |
| `zoneAbove()`, `zoneBelow()` | its zone for an [interlock rule](interlock.md) |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-lift -->
```java title="Controls.java"
g2.a.press(() -> robot.lift.goTo("down")).says("down");
g2.x.press(() -> robot.lift.goTo("low")).says("low");
g2.y.press(() -> robot.lift.goTo("high")).says("high");
g2.dpadUp.press(robot.lift::stepUp).says("next height up");
g2.dpadDown.press(robot.lift::stepDown).says("next height down");
g2.leftStickY.use(robot.lift::manual).says("by hand");
```

| Button | Does |
| --- | --- |
| A | the `down` height |
| X | the `low` height |
| Y | the `high` height |
| D-pad up, down | the next height |
| left stick | by hand |

---

**Look it up:** [`Lift` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Lift.html)
and [`MotorPositions`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/MotorPositions.html)
(every method). **The other parts:** [Reference](../README.md).
