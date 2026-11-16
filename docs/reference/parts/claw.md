# Claw

A servo that opens and closes. Learn it step by step:
[Robot.java, the other parts](../../five-files/01-robot-java.md#the-other-parts).

A Claw, a [Wrist](wrist.md) and a [Positioner](positioner.md) share most of their options, numbers and
commands (`ServoPositions`). This page lists the shared ones.

## In Robot.java

```java title="Robot.java"
claw = add(Claw.servo("claw").part("gobilda-2000-torque"));   // Tunables.Claw: open, closed (both needed)
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Claw.servo(name)` | **yes** | a claw on one servo | - |
| `.part(id)` | recommended | which servo it is, from the [parts catalog](../../guides/parts-catalog.md). When you do not write `travelSec`, the library takes it from the servo's catalog speed | `travelSec` is 0.3 s |
| `.alsoServo(name)` | optional | a second servo, turning the same way as the first for the same position ([Two servos](#two-servos-on-one-part)) | one servo |
| `.alsoServoReversed(name)` | optional | a second servo turning the other way (two servos that face each other, mirror images) ([Two servos](#two-servos-on-one-part)) | one servo |
| `.reversed()` | optional | the servo turns the other way | - |
| `.fullRange()` | optional | the servo's whole turn ([below](#a-servos-full-range)) | the FTC SDK's normal range |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)). It can ride on a wrist: `Shape.claw().on(wrist)` | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

- A servo does not move until the first command after ▶.
- When you write no positions, INIT says that the library's positions are guesses. Find yours with the
  Servo Finder.

## Two servos on one part

Some parts have two servos: a claw with a servo for each finger, or an arm with a servo on each side. Name
the second servo on the same line, like [`alsoMotor`](lift.md#two-motors-on-one-lift-or-arm).

```java title="Robot.java"
claw = add(Claw.servo("claw_left").alsoServoReversed("claw_right"));   // they face each other (usual)
arm = add(Positioner.servo("arm_left").alsoServo("arm_right"));        // both turn the same way
```

- Both servos get the same position at the same moment: from every command, pause, stop and interlock rule.
- The Tunables section takes the first servo's name.
- Two horns (the arms on the servo shafts) can sit a spline tooth apart. `alsoTrim` (raw position, -0.2 to
  0.2, default 0) fixes that: the library adds it for the second servo, so the two still meet.
- It works on a Claw, a Wrist, a Positioner and a servo [Indexer](indexer.md).
- On a CR servo [Indexer](indexer.md) (two feed wheels), both servos get the same power. There is no `alsoTrim`.
- No standard says which way a bigger number turns a servo. It depends on the servo, so test it:
  [Signs and units](../../conventions.md#servos-and-motors-which-way-is-bigger).

## A servo's full range

`.fullRange()` gives the servo its whole turn.

- The hub sends a servo a pulse: 600 microseconds (us) for position 0, and 2400 us for 1. That is the FTC
  SDK's normal range, and the library keeps it.
- Most FTC servos take 500 to 2500 us. So on the normal range they turn a bit less than the seller says. For
  example, a goBILDA 2000 servo turns 270 degrees over 0 to 1, and its seller says 300.
- At INIT, `.fullRange()` sends the servo's whole pulse range. It takes the range of the catalog part in
  `part(...)`, or else 500 to 2500 us. Then 0 to 1 turns all 300 degrees.
- It works on a Claw, Wrist or Positioner, and on `Hood.servo`, `Turret.servo` and `Indexer.servo`.
- **Measure your positions after you turn it on.** When the range changes, the same number points somewhere
  else. The Servo Finder has the same switch: press Y while you choose.
- A servo that cannot change its pulse (not on a REV hub) keeps its range. INIT says so.
- The hub keeps a pulse range until the next INIT.

```java title="Robot.java"
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());   // 0 to 1 = 300 degrees
```

Recipe: [How do I get my servo's full range?](../../cookbook/04-mechanisms.md#how-do-i-get-my-servos-full-range)

## In Tunables.java

<!-- tunables: ServoPositions -->
The section's name is the device's name in CamelCase: `claw` reads `Claw`, `intake_deploy` reads
`IntakeDeploy`. This table is for a Claw, a [Wrist](wrist.md) and a [Positioner](positioner.md). Every
number except `travelSec` and `alsoTrim` is a named position (0 to 1).

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)). The wrist's and the positioner's
lines are on their pages.

<!-- recipe: tunables-claw -->
```java title="Tunables.java"
public static class Claw {                                      // "claw" in the configuration
    // The Servo Finder shows "Tunables.Claw: P1 = 0.210, P2 = 0.770": write P1 as open, P2 as closed.
    public static double open = MEASURE;                        // servo position, open (Servo Finder)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `travelSec` | how long the servo takes for a full move, from position 0 to 1 (a shorter move takes its share). A servo cannot say when it has arrived, so the library uses this as its guess | seconds | 0.3 (with `.part(...)`: from the servo's catalog speed, [First run](../tunables.md#first-run-what-you-get-before-tuning)) | no | test: time a move, divide by its distance (below) | actions go on before it arrives (more) |
| `alsoTrim` | with a second servo (`alsoServo`, `alsoServoReversed`): a raw position the library adds for it, so the two meet | raw position, -0.2 to 0.2 | 0 | yes, at the next move | test: watch both sides | the two sides do not line up |
| `<position>` | every other number is a named position, 0 to 1. A Claw needs `open` and `closed` | servo position 0 to 1 | Claw: open 0.20, closed 0.75. Wrist: down 0.15, level 0.5, up 0.85. Positioner: stowed 0.0, deployed 0.5 (only when none are written; INIT says they are guesses) | yes, at once | Servo Finder (P1, P2...) | it does not open, close or reach far enough |

### Timing travelSec

- `travelSec` is the time for a FULL move, 0 to 1. The library scales it by how far each move goes.
- A mounted servo usually cannot go from 0 to 1. So time a move between two of your positions, and divide:
  `travelSec = measured seconds / |positionB - positionA|`.
- Example: 0.6 s from `stowed = 0.20` to `deployed = 0.74` gives 0.6 / 0.54 = 1.1. If you write the 0.6
  itself, the part counts as "arrived" after about half of the real move.
- Or leave it `DEFAULT`. With `.part(...)`, the library takes it from the servo's catalog speed.
- Some parts keep falling after the servo has moved, like a fold-out that drops by gravity. `travelSec` does
  not count that time. Add `.waitSeconds(...)` to the action for it.

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.claw.close()`.

| Command | What it does |
| --- | --- |
| `open()`, `close()` | opens, closes |
| `toggle()` | open or closed |
| `goTo(name)` | goes to a named position |

| Question | What it tells you |
| --- | --- |
| `isClosed()` | whether it is closed |
| `isDone()` | whether it should have arrived: a guess from `travelSec` |
| `isAt(name)` | whether it is at that named position |
| `positions()` | the names of its positions |
| `part()` | the [`PositionServo`](../../modules/mechanism.md#positionservo) inside |
| `secondServoName()` | the second servo's name, or null |
| `zone()` | its zone for an [interlock rule](interlock.md) |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

`goTo(name)`, `isDone()`, `isAt(name)`, `positions()`, `part()` and `secondServoName()` are on a Wrist and a
Positioner too.

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-claw -->
```java title="Controls.java"
g2.b.toggle(robot.claw).says("open / close");
```

The operator's B opens and closes the claw.

---

**Look it up:** [`Claw` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Claw.html)
and [`ServoPositions`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/ServoPositions.html)
(every method). **The other parts:** [Reference](../README.md).
