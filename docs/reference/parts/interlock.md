# Interlock

A rule that keeps two parts apart. Two parts that share space (a turret over the back and a lift at the
back; an arm and an intake) must never be in their zones at the same time. You write one line in
`Robot.java` and the numbers in `Tunables.java`. The library checks the rule every loop, whoever moves the parts.
Learn it with examples: [Two parts that must not collide](../../guides/interlocks.md).

## In Robot.java

```java title="Robot.java"
// Robot.java, after the parts:
turretLift = keepApart("TurretLift", turret.zone(), lift.zoneAbove());
```

`keepApart(name, zone, zone)` makes the rule. The name is its `Tunables` section. Write it after the two
parts.

| Zone | What it is | Its Tunables fields (in the rule's section) |
| --- | --- | --- |
| `part.zone()` | between two numbers (a turret's repeats every 360 degrees) | `<part>From`, `<part>To` |
| `part.zoneAbove()` | from there to the top | `<part>Above` |
| `part.zoneBelow()` | from there to the bottom | `<part>Below` |
| `Zone.when(words, condition)` | a condition for something the library cannot move (your own sensor) | none |

- Parts with a zone: [Turret](turret.md), [Lift](lift.md), [Arm](arm.md), [Claw](claw.md),
  [Wrist](wrist.md), [Positioner](positioner.md), [Hood](hood.md).
- A rule with a part that is not on the robot yet ([`optional()`](../robot-java.md#parts-not-on-the-robot-yet))
  is off: `Interlock TurretLift: off (Lift is not on the robot)`. It then does nothing and needs no Tunables.

## In Tunables.java

<!-- tunables: Interlock -->
The section's name is the rule's name: `keepApart("TurretLift", turret.zone(), lift.zoneAbove())` reads
`TurretLift`.

- `<part>` is the part's section name (its device's name in CamelCase) with a small first letter: `Turret`
  -> `turretFrom`.
- `LiftLeft` (device `lift_left`) -> `liftLeftAbove`, not `liftAbove`. INIT stops on it and says "did you
  mean liftLeftAbove?".
- Each part's own units: degrees (turret, arm), counts (lift), servo position 0 to 1 (claw, wrist,
  positioner).
- The rule reads them once, at INIT. They are never in the tuning menu: a zone that changed while the parts
  move could let them hit.

**Copy into `Tunables.java`.** The section's name is the rule's name. Change only what you need. `DEFAULT`
keeps the starting value. `MEASURE` means you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-interlock -->
```java title="Tunables.java"
// The rule's own section, named like the rule in Robot.java. Each zone in its part's units.
public static class TurretLift {                                // keepApart("TurretLift", ...) in Robot.java
    public static double turretFrom = MEASURE;                  // degrees: where the turret's zone starts
    public static double turretTo = MEASURE;                    // degrees: where it ends (robot's back)
    public static double liftAbove = MEASURE;                   // counts: the slide is up from here
    public static double liftStopSec = 0.2;                     // a heavy slide takes longer to stop (0.15)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `<part>From`, `<part>To` | `part.zone()`: the zone between two numbers | the part's units | REQUIRED with `zone()` | no | measure: move the part by hand to the edges | the parts or their places changed |
| `<part>Above` | `part.zoneAbove()`: the zone from there to the top | the part's units | REQUIRED with `zoneAbove()` | no | measure | the same |
| `<part>Below` | `part.zoneBelow()`: the zone from there to the bottom | the part's units | REQUIRED with `zoneBelow()` | no | measure | the same |
| `<part>Margin` | the extra safety distance around the zone (the stop distance already covers coasting) | the part's units | turret: the larger of 3 deg and 2 x its `tolerance`; lift, arm: the larger of 2 x its `tolerance` and 1/50 of its range; hood: 1/50 of its range; claw, wrist, positioner: 0.02 | no | test | the parts come too close (more) |
| `<part>StopSec` | a turret, lift or arm: how long it takes to stop from full speed once its power is cut. Its stop distance is its speed x (2 x the slowest of the last 25 loops + half this) ([interlocks](../../guides/interlocks.md)) | seconds, 0 to 2 | 0.15 | no | test: full stick toward the zone, see where it stops | it still ends up past its waiting point (more), or it waits too far out (less) |

**INIT lists each rule.** It stops, and gives the reason, for:

- a missing section or number,
- an empty zone,
- a zone outside the part's range, or one that covers all of it,
- a negative margin,
- a `StopSec` outside 0 to 2 s,
- a part with no position.

## How it works

**Who yields:**

- **Whoever is in its zone first keeps it. The other part waits outside its own zone**, two margins away,
  with its target kept. It goes on when the first one leaves.
- When both want in at once, the first zone in `keepApart` goes first. But if one moves too fast to stop
  before its zone, that one goes first.
- When both are in their zones (pushed there, or found there at ▶), each may only move out.

**Stop distance:**

- Every loop the rule measures each motor part's speed (its encoder). It works out how far the part would
  still go if told to stop now: speed x (2 x the slowest of the last 25 loops + half its `StopSec`).
- A part kept out may only use the power that lets it stop in time. So it slows down as it comes near its
  waiting point, and brakes when it must, even with slow loops (a camera, a busy hub).
- The margin is extra safety on top.
- A servo needs none: the rule never sends it past the fence (the zone's edge).

**More rules:**

- A turret whose way to its target crosses its zone goes the other way round if its range allows (a slip
  ring, or -270 to 270). If not, it waits at the edge.
- When the rule does not know a part's position, it counts the part as in its zone. For example: a turret
  not homed yet, or a servo still moving. A moving servo counts as anywhere between where it was and where it was sent, until its
  `travelSec` is over.
- Every command goes through it: buttons, sticks, actions, autonomous steps, a following turret, shooting on
  the move.
- A held turret is never `isAimed()`.

**On the Driver Station:** one line per rule while it holds something.

- `TurretLift : Lift held at 110 counts: Turret is between 130 and 218 deg`
- Held because the other part's position is not known:
  `TurretLift : Lift held at 110 counts: Turret angle unknown (not homed yet)`

## Commands

An interlock has no commands. You can ask it these questions:

| Question | What it tells you |
| --- | --- |
| `name()` | the rule's name (its Tunables section) |
| `isHolding()` | whether a part is held back now |
| `status()` | the Driver Station line ("" when nothing is held) |
| `words()` | the rule in words: `Turret between 130 and 218 deg / Lift above 270 counts` |
| `isOff()` | whether one of its parts is not on the robot ([`optional()`](../robot-java.md#parts-not-on-the-robot-yet)). Then the rule does nothing and needs no Tunables |
| `Zone.part()` | the zone's part (null for a condition) |
| `Zone.words()` | the zone in words |

An interlock has no buttons.

---

**Look it up:** [`Interlock` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Interlock.html)
and [`Zone`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Zone.html)
(every method). **The other parts:** [Reference](../README.md).
