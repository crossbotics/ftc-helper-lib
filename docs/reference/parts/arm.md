# Arm

An arm that swings on a hinge. A motor turns it to named angles, in degrees. It works like a
[Lift](lift.md), but in degrees. Learn it step by step: [Robot.java, the other parts](../../five-files/01-robot-java.md#the-other-parts).

## In Robot.java

```java title="Robot.java"
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Arm.motor(name, motorId)` | **yes** | an arm on one motor. `motorId` is the motor from the [parts catalog](../../guides/parts-catalog.md) | - |
| `.gearRatio(r)` | recommended | motor turns per arm turn | 1 |
| `.bottomSwitch(name)` | optional | a switch at the bottom, like the [lift's](lift.md#where-0-is) | 0 degrees is where the arm is when the program starts |
| `.alsoMotor(name)` | optional | a second motor, turning the same way ([Two motors](lift.md#two-motors-on-one-lift-or-arm)) | one motor |
| `.alsoMotorReversed(name)` | optional | a second motor, turning the other way ([Two motors](lift.md#two-motors-on-one-lift-or-arm)) | one motor |
| `.reversed()` | optional | the arm turns the other way. Use it when positive power moves the arm down | - |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

**Where 0 is:** without a bottom switch, 0 degrees is where the arm is when the program starts.

## In Tunables.java

The section's name is the device's name in CamelCase: `arm` reads `Tunables.Arm`. An arm's numbers are in
**degrees**. The table of every number, with the arm's defaults, is on the [Lift](lift.md#in-tunablesjava) page.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value. `MEASURE` means
you must measure it ([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-arm -->
```java title="Tunables.java"
public static class Arm {                                       // "arm" in the configuration
    public static double min = MEASURE;                         // lowest safe angle, degrees, 0 = at INIT
    public static double max = MEASURE;                         // highest safe angle, degrees (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (0.02)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double kF = 0.1;                              // hold up against gravity (0)
    public static double stowed = MEASURE;                      // a named angle, degrees: any name you like
    public static double pickup = MEASURE;                      // a named angle (Motor Finder)
    public static double score = MEASURE;                       // a named angle (Motor Finder)
}
```

## Commands

The same as the [Lift's](lift.md#commands), in degrees: `goTo(name)`, `stepUp()`, `stepDown()`,
`manual(power)`, `hold()`, `stop()`, and the questions `isAtTarget()`, `isAt(name)`, `position()`,
`positions()`, `part()`, `secondMotorName()`, `isPresent()`. Its zone for an
[interlock rule](interlock.md): `arm.zoneAbove()` or `arm.zoneBelow()`.

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-arm -->
```java title="Controls.java"
g2.rightStickY.use(robot.arm::manual).says("by hand");
```

The operator's right stick Y moves the arm by hand. For named angles, add lines like the
[Lift's](lift.md#buttons-to-add): `g2.y.press(() -> robot.arm.goTo("score")).says("score");`.

---

**Look it up:** [`Arm` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Arm.html)
and [`MotorPositions`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/MotorPositions.html)
(every method). **The other parts:** [Reference](../README.md).
