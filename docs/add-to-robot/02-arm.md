# Add an arm

An arm that swings on a hinge. A motor turns it to named angles, like "pickup" and "score".

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: an arm motor with an encoder, and hard stops at both ends of the swing. In the robot
  configuration, call the motor `arm`.

## 1. What it is

An **arm** works like a [lift](01-lift.md), but in **degrees**, not encoder counts. You send it to an angle
by its name, and it goes there and holds still.

- **0 degrees is where the arm is at INIT.** So put the arm on its bottom stop before you press INIT. Then
  0 is the same place each time ([Where 0 is](../reference/parts/arm.md#in-robotjava)).
- **It never goes past its safe range** (`min` to `max`). Set them just inside the hard stops. The hard
  stops are still necessary: software can be wrong, and the robot must stay inside its size limits
  without software (rule R105).
- **It needs its gear ratio.** The library changes motor turns to arm degrees with the motor from the
  catalog and `gearRatio`.

**A servo at the end of the arm** that tilts a claw is a **wrist**. Add it after the arm:
[Add a claw and other servo parts](03-claw.md).

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field (`public Arm arm;`) goes at the top of the class. The line goes in
`define()`, after the drive.

<!-- recipe: arm -->
```java title="Robot.java"
// "arm" = the motor's name in the robot configuration. Its numbers: Tunables.Arm.
// "gobilda-5203-117" = the motor's catalog id (needed: it works in degrees).
// gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

- `"gobilda-5203-117"` names the motor from the [parts catalog](../guides/parts-catalog.md). The number is
  on its label. **Why:** the library needs the motor's counts per turn to work in degrees.
- `.gearRatio(2)` means the motor turns 2 times for 1 turn of the arm. Count the teeth: the gear on the arm
  divided by the gear on the motor. With no gears between them, leave it out (1).
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The device `"arm"` reads `Tunables.Arm`.

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

- `MEASURE` means "find this on the robot". INIT stops until you write a number.
- `DEFAULT` means "use the library's starting value" (the number in brackets).
- `kF = 0.1` starts the arm with some push against gravity. An arm sags more than a lift.
- `stowed`, `pickup` and `score` are **named angles**. Use any names you like.

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-arm -->
```java title="Controls.java"
g2.rightStickY.use(robot.arm::manual).says("by hand");
```

The operator's right stick moves the arm by hand. When you let go, it holds there. For each named angle,
add a line on a free button, like the lift's:
`g2.y.press(() -> robot.arm.goTo("score")).says("score");`.

### Actions.java and autos: use it in steps

You need no line here for the arm alone. When the arm works with another part, an action puts them in
order. It is the same as the lift's action: `goTo(...)`, then `waitUntil(..., r.arm::isAtTarget, ...)`
([the lift's example](01-lift.md#actionsjava-and-autos-use-it-in-steps)).

## 3. Find its numbers

INIT stops on `MEASURE`. The finders still run, because they do not read `Tunables.java`.

1. **Motor Finder** ([how](../tuning/05-motor-finder.md)): put the arm on its bottom stop, then choose
   `arm`. Press **X** just above the bottom stop (`min`) and **B** just before the top stop (`max`). Press
   **A** at each angle you want.
2. **Change counts to degrees.** The Motor Finder shows encoder counts. `Tunables.Arm` takes degrees.
   Divide each count by the counts per degree:
   - counts per degree = the motor's counts per turn x `gearRatio` / 360. The counts per turn are in the
     [parts catalog](../guides/parts-catalog.md).
   - Example: `gobilda-5203-117` (1425.1 counts per turn) with `gearRatio(2)`: 1425.1 x 2 / 360 = 7.92.
     So 712 counts is 712 / 7.92 = 90 degrees.
3. Put the degrees in `Tunables.Arm`. Give each P a real name (`P1` -> `pickup`, `P2` -> `score`).
4. When the Motor Finder says to add `.reversed()` to the part, add it to the arm's line in `Robot.java`.
   It means the motor counts down as the arm goes up.

## 4. Check it, then tune it

**Check it:**

1. Keep hands away from the arm's swing, and keep a hand near STOP. Put the arm on its bottom stop.
2. Press INIT. Check that no warning names the arm. Press ▶.
3. Push gamepad 2's right stick up a little. The arm must go **up**. If it goes down, add `.reversed()`.
4. Press each button for a named angle. The arm must reach and hold each angle.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs. Change one number, then
send the arm between its angles:

1. **kF:** raise it until the arm holds still at a level angle, with no sag. Do this first: gravity pulls an
   arm hardest when it is level.
2. **kP:** raise it until the arm moves quickly to each angle. If it shakes, go back a step.
3. **kD:** if it goes past the angle and comes back, raise it a little at a time.
4. **kI:** only if a small error stays, a very small number.

Then put the tuned numbers in `Tunables.Arm`, in place of `DEFAULT`.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Arm.max = MEASURE` | Not measured yet. Do step 3 |
| Every angle is wrong by the same amount | The arm was not on its bottom stop at INIT. Put it there, and INIT again |
| Every angle is too big or too small | A count was not changed to degrees, or `gearRatio` is wrong. Do step 3 again |
| The arm goes down when it should go up | Add `.reversed()` to the arm in `Robot.java` |
| It sags below an angle | `kF` is too small |
| It shakes at an angle | `kP` or `kD` is too big |

**Two motors on one arm:** name the second motor on the same line, `.alsoMotorReversed("arm_right")`
([Two motors](../reference/parts/lift.md#two-motors-on-one-lift-or-arm)).

## Done when

- INIT shows no warning for the arm.
- Each button sends the arm to its angle, and it holds still there.
- `min`, `max` and every angle in `Tunables.Arm` are numbers in degrees, not `MEASURE`.

---

**Next:** [Add a claw and other servo parts](03-claw.md).

**Look it up:** [Arm](../reference/parts/arm.md): every setting, number and command.
