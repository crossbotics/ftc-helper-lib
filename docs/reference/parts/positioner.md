# Positioner

Any servo with named positions: a deploy, a fold-out, a gate. Learn it step by step:
[Robot.java, the other parts](../../five-files/01-robot-java.md#the-other-parts).

A Positioner shares most of its options, numbers and commands with the [Claw](claw.md). The Claw page lists
the shared ones.

## In Robot.java

```java title="Robot.java"
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque"));   // Tunables.IntakeDeploy: stowed, deployed
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Positioner.servo(name)` | **yes** | a positioner on one servo | - |
| `.part(id)` | recommended | which servo it is, from the [parts catalog](../../guides/parts-catalog.md). When you do not write `travelSec`, the library takes it from the servo's catalog speed | `travelSec` is 0.3 s |
| `.alsoServo(name)`, `.alsoServoReversed(name)` | optional | a second servo ([Two servos](claw.md#two-servos-on-one-part)) | one servo |
| `.reversed()` | optional | the servo turns the other way | - |
| `.fullRange()` | optional | the servo's whole turn ([full range](claw.md#a-servos-full-range)) | the FTC SDK's normal range |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([below](#in-the-simulator)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

### In the simulator

- It swings an arm shape: `.looks(Shape.arm()...angle("stowed", 90).angle("deployed", 20))`.
- It can push a passive part over: `Shape.dropsWhen` ([Shapes](../robot-java.md#shapes-and-parts)).

## In Tunables.java

The section's name is the device's name in CamelCase: `intake_deploy` reads `IntakeDeploy`. Every number
except `travelSec` and `alsoTrim` is a named position (0 to 1). You choose the names. The table of every
number, and how to time `travelSec`, is on the [Claw](claw.md#in-tunablesjava) page.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

A positioner (a fold-out):

<!-- recipe: tunables-positioner -->
```java title="Tunables.java"
public static class IntakeDeploy {                              // "intake_deploy" in the configuration
    public static double stowed = MEASURE;                      // servo position, folded (Servo Finder)
    public static double deployed = MEASURE;                    // servo position, folded out (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.intakeDeploy.goTo("deployed")`.

| Command | What it does |
| --- | --- |
| `goTo(name)` | goes to a named position |
| `next()` | goes to the next position, in the order written in `Tunables.java`. After the last comes the first |

The questions are the [Claw's](claw.md#commands): `isDone()`, `isAt(name)`, `positions()`, `part()`,
`secondServoName()`, `zone()`, `isPresent()`.

## Buttons to add

A positioner has no usual buttons: you choose when it moves. For example, put it on a button in
`Controls.java`:

```java title="Controls.java"
g2.x.press(() -> robot.intakeDeploy.goTo("deployed")).says("deploy");
```

You can also move it at ▶ in `Robot.java`, or in an action. To read a button line, see
[Controls.java](../controls.md#reading-a-button-line).

---

**Look it up:** [`Positioner` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Positioner.html)
and [`ServoPositions`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/ServoPositions.html)
(every method). **The other parts:** [Reference](../README.md).
