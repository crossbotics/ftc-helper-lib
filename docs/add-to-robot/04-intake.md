# Add an intake

A roller that takes game pieces in and pushes them out. It runs on a motor, or on continuous-rotation
servos.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: the intake motor (call it `intake` in the robot configuration), or two continuous-rotation
  servos (call them `left_intake` and `right_intake`). An intake needs no encoder.

## 1. What it is

An **intake** has two jobs: **in** (take pieces) and **out** (push them back out).

- **It runs on power, not to a position.** So it has no limits and no positions to measure.
- **A continuous-rotation (CR) servo** spins all the way round, like a small motor. Set the servo to CR mode
  before you put it on (for a goBILDA servo, with its programmer).
- **It can stop by itself when the robot is full.** That needs a count of the pieces
  ([Stop when full](#stop-when-the-robot-is-full)).

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class. The line goes in `define()`, after the
drive.

**On a motor:**

<!-- recipe: intake-motor -->
```java title="Robot.java"
// At the top of the class: public Intake intake;   Here in define():
// "intake" = the motor's name in the robot configuration. Its numbers: Tunables.Intake.
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
```

**Or on CR servos (one, two or more):**

<!-- recipe: intake-cr-servos -->
```java title="Robot.java"
// Two continuous-rotation (CR) servos. The one that spins the wrong way is reversed.
// "intake", "intake_right" = the servos' names in the robot configuration. Numbers: Tunables.Intake (the 1st name).
// .part(...): which servo it is (optional, recommended).
intake = add(Intake.crServos("intake", "intake_right").part("gobilda-2000-speed")
        .reversed("intake_right"));
```

- `.part(...)` names the motor or servo from the [parts catalog](../guides/parts-catalog.md). **Why:** the
  simulator then spins it like the real one.
- **One CR servo:** `intake = add(Intake.crServos("intake").part("gobilda-2000-speed"));`. Leave out
  `.reversed(...)`, or reverse that one servo when it spins out.
- `.reversed("intake_right")`: two servos that face each other must spin opposite ways. Reverse the one that
  spins the wrong way.
- **Add `.holds(n)`**, the number of pieces it carries: `.holds(3)`. Count them by hand: load pieces until
  no more fit. The simulator and the piece count use it. INIT warns when it is above the game's limit.
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

**Run from ▶:** some teams keep the intake on all match. Add `.onAtStart()`:

<!-- recipe: intake-on-at-start -->
```java title="Robot.java"
// Runs in from ▶ until stopped. Its button (operator RT) then switches it on/off.
// .part(...): which motor it is (optional, recommended).
add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());
```

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The device `"intake"` reads `Tunables.Intake`. With CR servos, the
section comes from the **first** name: `crServos("intake", "intake_right")` reads `Tunables.Intake`.

<!-- recipe: tunables-intake -->
```java title="Tunables.java"
public static class Intake {                                    // "intake" in the configuration
    public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
    public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
}
```

Both numbers start at `DEFAULT`, so the intake works at once. Change them only when a test shows a problem
(step 4).

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-intake -->
```java title="Controls.java"
robot.intake.inOn(g2.rt);
robot.intake.outOn(g2.lt);
```

`inOn` and `outOn` put "in" and "out" on a button the right way for your intake:

| Your Robot.java | `inOn(g2.rt)` does | `outOn(g2.lt)` does |
| --- | --- | --- |
| plain | in while held | out while held |
| `.onAtStart()` | a press switches it on and off | out while held |
| `.stopsWhen(...)` | in while held; held 1 s = in anyway | out while held |

With one gamepad (`oneDriver = true`), these `g2` lines work on gamepad 1
([One gamepad, and a second one when you want it](../controls/01-how-buttons-work.md#one-gamepad-and-a-second-one-when-you-want-it)).

### Actions.java and autos: use it in steps

You need no line in `Actions.java` for the intake alone. In an auto, a step turns it on or off:
`.run("Intake in", robot.intake::in)`, and later `.run("Intake off", robot.intake::stop)`
([what an auto can do](../building-autos/01-what-an-auto-can-do.md)).

### A fold-out intake

Some intakes fold out after ▶, on a servo. That servo is a **Positioner**, a servo with named positions
([servo parts](03-claw.md)).

**Where:** `Robot.java`. The field (`public Positioner intakeDeploy;`) goes at the top of the class. The
lines go in `define()`, after the intake line.

<!-- recipe: positioner -->
```java title="Robot.java"
// Any servo with named positions: a gate, a kicker, a fold-out intake.
// "intake_deploy" = the servo's name in the robot configuration. Numbers: Tunables.IntakeDeploy.
// .part(...): which servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque"));
// Right after ▶ (autonomous and TeleOp): fold the intake out.
atStart(() -> Action.named("Deploy").then(() -> intakeDeploy.goTo("deployed")));
```

**Where:** `Tunables.java`, a new section. Find both positions with the
[Servo Finder](../tuning/03-servo-finder.md).

<!-- recipe: tunables-positioner -->
```java title="Tunables.java"
public static class IntakeDeploy {                              // "intake_deploy" in the configuration
    public static double stowed = MEASURE;                      // servo position, folded (Servo Finder)
    public static double deployed = MEASURE;                    // servo position, folded out (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

The robot must fit its size limit at the start. So the fold-out waits for ▶.

### Stop when the robot is full

The game limits how many pieces a robot holds (BIOBUZZ: 4, rule G407). The intake can stop by itself at
that count. It needs `Pieces`, which counts the pieces with color sensors
([Pieces](../reference/parts/pieces.md)). Then add `.stopsWhen(...)` to your intake line:

<!-- recipe: intake-stops-when-full -->
```java title="Robot.java"
// ADD .stopsWhen(...) to your intake line (no second intake line); the Pieces line goes above it.
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150").stopsWhen(() -> pieces.isFull()));
```

- Pushing out still works when it is full.
- When the count is wrong, the operator holds the in button for 1 s. Then it runs in anyway.
- The Driver Station warns when `stopsWhen` has kept the intake from running in for more than 2 s.

## 3. Find its numbers

An intake has no finder. Its two powers start at good values. Only `.holds(n)` needs a count: load pieces by
hand until no more fit.

## 4. Check it, then tune it

**Check it:**

1. Keep hands away from the rollers. Press INIT. Check that no warning names the intake. Press ▶.
2. Hold gamepad 2's RT. The rollers must pull a piece **in**. If they push it out, add `.reversed()` to the
   intake line in `Robot.java`.
3. Hold LT. The rollers must push the piece **out**.
4. Two CR servos: both must spin toward the inside. If one fights the other, reverse that one with
   `.reversed("its name")`.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs. Feed it pieces, and
change one number at a time:

1. **`inPower`:** lower it when pieces jam or bounce out. Keep it as high as you can for fast pickup.
2. **`outPower`:** raise it when pieces do not come out.

Then put the tuned numbers in `Tunables.Intake`, in place of `DEFAULT`.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| It pushes out when it should take in | Add `.reversed()` to the intake in `Robot.java` |
| One CR servo fights the other | Reverse that servo: `.reversed("intake_right")` |
| A CR servo goes to one place and stops | It is not in CR mode. Set it with the servo's programmer |
| Pieces jam or bounce out | `inPower` is too big |
| Pieces do not come out | `outPower` is too small |
| It does not run in, and the screen says it is full | `stopsWhen` says full. Hold the in button 1 s to run in anyway. Then check the piece count |
| INIT warns about `holds` | `.holds(n)` is above the game's limit |

## Done when

- INIT shows no warning for the intake.
- RT takes a piece in, and LT pushes it out.
- `.holds(n)` is on the intake line, with the number you counted.

---

**Next:** [Add a launcher](05-launcher.md).

**Look it up:** [Intake](../reference/parts/intake.md): every setting, number and command.
