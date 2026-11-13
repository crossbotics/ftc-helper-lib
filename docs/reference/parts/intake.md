# Intake

A roller that takes game pieces in and pushes them out. It runs on a motor, on continuous-rotation (CR)
servos, or on both. Learn it step by step: [Robot.java, add a part](../../five-files/01-robot-java.md#intake).

## In Robot.java

```java title="Robot.java"
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
intake = add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());
intake = add(Intake.crServos("intake", "intake_right").part("gobilda-2000-speed").reversed("intake_right"));
intake = add(Intake.motor("intake").part("gobilda-5203-117")
        .withCrServos("left_wheel", "right_wheel").reversed("right_wheel"));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Intake.motor(name)` | **yes**, one of the two | an intake on one motor | - |
| `Intake.crServos(names...)` | **yes**, one of the two | an intake on CR servos that run together | - |
| `.part(id)` | recommended | which motor or servo it is, from the [parts catalog](../../guides/parts-catalog.md) | the simulator guesses its speed |
| `.holds(n)` | recommended | how many pieces it carries. Count them by hand: load pieces until no more fit | 4 (the simulator's guess) |
| `.withCrServos(names...)` | optional | CR servos that run with the motor, like side wheels next to a roller | - |
| `.reversed(names...)` | optional | these devices run the other way, like the second of two facing servos | all run the same way |
| `.reversed()` | optional | the whole intake runs the other way | - |
| `.holds(n, "KIND")` | optional | how many of one kind it carries. 0 = it cannot take that kind | every kind counts the same |
| `.onAtStart()` | optional | runs in from ▶ until your code stops it | off: a button starts it |
| `.stopsWhen(test)` | optional | never runs in while the test is true, like "the robot is full" ([Pieces](pieces.md)). Pushing out still works | it runs whenever asked |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.looks(shapes...)` | optional | how it looks in the simulator ([Shapes](../robot-java.md#shapes-and-parts)) | a plain box |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

**About `holds`:**

- The simulator uses it, and so does [Pieces](pieces.md): the robot is "full" at this number.
- INIT warns when it is above the game's limit. BIOBUZZ: no more than 4 at a time (G407).
- The simulator takes as many as you write, also above the limit. Then it gives the G407 foul: 6 at once, or 5
  for more than 3 s twice ([the simulator's rules](../../guides/how-the-simulator-works.md#game-rules-and-the-score)).
- It does not stop the real intake by itself. To stop at a count, use [Pieces](pieces.md) and
  `.stopsWhen(...)` ([cookbook](../../cookbook.md#how-do-i-stop-the-intake-when-the-robot-is-full)).
- A name matches every kind that has it in its name: "NECTAR" is red and blue NECTAR.
- With `holds(n)` too, it is a mix: `.holds(4).holds(1, "NECTAR")` = 4 pieces, at most 1 NECTAR.
- goBILDA's starter bot cannot take a NECTAR: `.holds(4).holds(0, "NECTAR")`.

## In Tunables.java

<!-- tunables: Intake -->
The section's name is the device's name in CamelCase: `intake` reads `Intake`, `front_roller` reads
`FrontRoller`.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-intake -->
```java title="Tunables.java"
public static class Intake {                                    // "intake" in the configuration
    public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
    public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `inPower` | power while it takes pieces in | 0 to 1 | 1.0 | yes, at once | test | it throws pieces, or they jam (less) |
| `outPower` | power while it pushes pieces out | 0 to 1 | 0.7 | yes, at once | test | pieces do not come out (more) |

## Commands

Use them in `Controls.java`, `Actions.java` and autos: `robot.intake.in()`.

| Command | What it does |
| --- | --- |
| `in()` | runs in until you stop it |
| `out()` | pushes out until you stop it |
| `stop()` | stops |
| `toggle()` | on or off |
| `reverse()`, `endReverse()` | pushes out while the driver holds a button, then goes back to what it was doing |
| `holdIn()` | in while the driver holds a button. With `stopsWhen`: hold it 1 s = in anyway. For `g2.rt.hold(robot.intake::holdIn, robot.intake::stop)` |
| `inAnyway()` | runs in even though `stopsWhen` says no, until `stop()` or `out()`. Use it when the count is wrong |
| `inOn(button)`, `outOn(button)` | put "in" and "out" on a button the right way for this intake ([Buttons to add](#buttons-to-add)) |

| Question | What it tells you |
| --- | --- |
| `isRunning()` | whether it runs, in or out |
| `isPushingOut()` | whether it pushes out now: `out()`, or a held `reverse()` |
| `isHeldBack()`, `heldBackSec()` | whether `stopsWhen` keeps it from running in, and for how many seconds |
| `holdsInAll()` | the `holds(n)` number |
| `limitFor("KIND")` | how many of that kind it can carry |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

In an auto: `.run("Intake in", robot.intake::in)` ([what an auto can do](../../building-autos/01-what-an-auto-can-do.md)).

## Buttons to add

A part has **no buttons** until you add its lines to `Controls.java`. These are the usual ones. Copy
them, or change the button ([how to read a button line](../controls.md#reading-a-button-line)).

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.

<!-- recipe: buttons-intake -->
```java title="Controls.java"
robot.intake.inOn(g2.rt);
robot.intake.outOn(g2.lt);
```

| Your Robot.java | `inOn(g2.rt)` does | `outOn(g2.lt)` does |
| --- | --- | --- |
| plain | in while held | out while held |
| `.onAtStart()` | a press switches it on and off | out while held |
| `.stopsWhen(...)` | in while held; held 1 s = in anyway | out while held |
| both | a press switches; held 1 s = in anyway | out while held |

With `stopsWhen`, the Driver Station warns when `stopsWhen` has kept the intake from running in for more than
2 s. So the drivers notice when the count says "full" too soon.

---

**Look it up:** [`Intake` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Intake.html)
(every method). **The other parts:** [Reference](../README.md).
