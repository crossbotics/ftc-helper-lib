# Wrist

A servo with named positions that tilts a claw. Learn it step by step:
[Robot.java, the other parts](../../five-files/01-robot-java.md#the-other-parts).

A Wrist shares most of its options, numbers and commands with the [Claw](claw.md). The Claw page lists the
shared ones.

## In Robot.java

```java title="Robot.java"
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed"));   // Tunables.Wrist: any positions, e.g. down, level, up
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Wrist.servo(name)` | **yes** | a wrist on one servo | - |
| `.part(id)` | recommended | which servo it is, from the [parts catalog](../../guides/parts-catalog.md). When you do not write `travelSec`, the library takes it from the servo's catalog speed | `travelSec` is 0.3 s |
| `.alsoServo(name)`, `.alsoServoReversed(name)` | optional | a second servo ([Two servos](claw.md#two-servos-on-one-part)) | one servo |
| `.reversed()` | optional | the servo turns the other way | - |
| `.fullRange()` | optional | the servo's whole turn ([full range](claw.md#a-servos-full-range)) | the FTC SDK's normal range |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([below](#in-the-simulator)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

### In the simulator

- A wrist swings an arm shape: `.looks(Shape.arm().on(lift)...angle("folded", -90).angle("flower", -213))`.
- A claw can ride on it: `Claw ... .looks(Shape.claw().on(wrist)...)`.
- More: [Shapes](../robot-java.md#shapes-and-parts).

## In Tunables.java

The section's name is the device's name in CamelCase: `wrist` reads `Wrist`. Every number except
`travelSec` and `alsoTrim` is a named position (0 to 1). You choose the names. The table of every number, and
how to time `travelSec`, is on the [Claw](claw.md#in-tunablesjava) page.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-wrist -->
```java title="Tunables.java"
public static class Wrist {                                     // "wrist" in the configuration
    public static double down = MEASURE;                        // servo position: any name you like
    public static double level = MEASURE;                       // servo position (Servo Finder)
    public static double up = MEASURE;                          // servo position (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.wrist.goTo("level")`.

| Command | What it does |
| --- | --- |
| `goTo(name)` | goes to a named position |
| `next()` | goes to the next position, in order of value. After the last comes the first |
| `previous()` | goes to the previous position, in order of value |

The questions are the [Claw's](claw.md#commands): `isDone()`, `isAt(name)`, `positions()`, `part()`,
`secondServoName()`, `zone()`, `isPresent()`.

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-wrist -->
```java title="Controls.java"
g2.dpadLeft.press(robot.wrist::previous).says("previous position");
g2.dpadRight.press(robot.wrist::next).says("next position");
```

The operator's D-pad left and right go to the previous and the next position.

---

**Look it up:** [`Wrist` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Wrist.html)
and [`ServoPositions`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/ServoPositions.html)
(every method). **The other parts:** [Reference](../README.md).
