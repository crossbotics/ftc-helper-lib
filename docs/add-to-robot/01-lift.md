# Add a lift

A slide on a motor that goes up and down to named heights, like "low" and "high".

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: a lift motor with an encoder, and hard stops at the top and the bottom. A touch switch at
  the bottom is recommended. In the robot configuration, call them `lift` and `lift_bottom`.

## 1. What it is

A **lift** holds named heights. You send it to a height by its name, and it goes there and holds still.

- **Heights are encoder counts.** An encoder is a sensor on the motor that counts how far the shaft has
  turned. 0 is the bottom, and bigger numbers are higher.
- **It never goes past its safe range** (`min` to `max`). The library slows it down near both ends.
- **It needs a known 0** (homing). An encoder counts only changes, so it does not know where the bottom is.
  - **With a switch at the bottom:** a press of the switch sets 0. Until the first press, the lift goes
    only down, at most 0.3 power, and the screen says `Lift: push it DOWN to the switch to home it`. Each
    later press fixes drift from a slipped belt or a skipped tooth.
  - **Without a switch:** 0 is where the lift is at INIT. Press INIT with the lift all the way down, every
    time, also in TeleOp after an autonomous that left it up
    ([Where 0 is](../reference/parts/lift.md#where-0-is)).

**Hard stops and software limits:** `min` and `max` are software limits. The program stops the motor there.
Software can be wrong (a bad number, a slipped encoder). So the lift also needs physical blocks at both
ends. Set `min` and `max` just inside them. The rules also require this: the robot must stay inside its size
limits without software (rule R105).

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class. The line goes in `define()`, after the
drive.

<!-- recipe: lift -->
```java title="Robot.java"
// At the top of the class: public Lift lift;   Here in define():
// "lift", "lift_bottom" = the motor's and the bottom switch's names in the robot configuration.
// .part(...): which motor it is (optional, recommended).
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

- `.part(...)` names the motor from the [parts catalog](../guides/parts-catalog.md). The number is on its
  label. **Why:** the library then starts the lift's numbers from that motor, and the simulator moves it
  like the real one.
- `.bottomSwitch(...)` is the switch at the bottom. Until the lift has pressed it once, the lift goes only
  down, slowly. Leave it out when your lift has no switch.
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The section's name is the device's name in CamelCase:
`"lift"` reads `Tunables.Lift`.

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

- `MEASURE` means "find this on the robot". INIT stops until you write a number. **Why:** a guessed `max`
  can pull the lift off its rails.
- `DEFAULT` means "use the library's starting value". The PID numbers (`kP` to `kF`) start from the motor
  in `.part(...)`. You tune them later (step 4).
- `down`, `low` and `high` are **named heights**. Use any names you like. Every number in the section that
  is not a setting is a height.

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-lift -->
```java title="Controls.java"
g2.a.press(() -> robot.lift.goTo("down")).says("down");
g2.x.press(() -> robot.lift.goTo("low")).says("low");
g2.y.press(() -> robot.lift.goTo("high")).says("high");
g2.dpadUp.press(robot.lift::stepUp).says("next height up");
g2.dpadDown.press(robot.lift::stepDown).says("next height down");
g2.leftStickY.use(robot.lift::manual).says("by hand");
```

With one gamepad (`oneDriver = true`), these `g2` lines work on gamepad 1
([One gamepad, and a second one when you want it](../controls/01-how-buttons-work.md#one-gamepad-and-a-second-one-when-you-want-it)).
The left stick moves the lift by hand. When you let go, it holds there.

### Actions.java and autos: use it in steps

You need no line here for the lift alone. When the lift works with another part, an action puts them in
order. For example, with a claw ([Claw](../reference/parts/claw.md)):

**Where:** `Actions.java`, one method per action.

<!-- recipe: action-steps -->
```java title="Actions.java"
/** Lift up, open the claw, lift down: one step after the other. */
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // at most 2 s
            .then(r.claw::open)
            .waitSeconds(0.3)                                        // let the piece drop
            .then(() -> r.lift.goTo("down"));
}
```

An autonomous uses the same action as one step ([Actions.java](../five-files/04-actions-java.md)).

## 3. Find its numbers

INIT stops on `MEASURE`. The finders still run, because they do not read `Tunables.java`.

1. **Motor Finder** ([how](../tuning/05-motor-finder.md)): put the lift at the bottom. Press **X** just
   above the bottom block (`min`) and **B** just below the top block (`max`). Press **A** at each height you
   want.
2. Put the numbers in `Tunables.Lift`. Give each P a real name (`P1` -> `low`, `P2` -> `high`).
3. When the Motor Finder says `Robot.java: add .reversed() to the "lift" part`, do that. It means the motor
   counts down as the lift goes up.

## 4. Check it, then tune it

**Check it:**

1. Lift the wheels off the floor, and keep a hand near STOP. Put the lift at the bottom, on its switch.
2. Press INIT. Check that no warning names the lift. Press ▶.
3. Push gamepad 2's left stick up a little. The lift must go **up**. If it goes down, add `.reversed()`.
4. Press X, then Y, then A. The lift must reach and hold each height.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs. Change one number, then
send the lift between its heights:

1. **kP:** raise it until the lift moves quickly to each height. If it shakes, go back a step.
2. **kD:** if it goes past the height and comes back, raise it a little at a time.
3. **kF:** if it stops a little low and sags, raise it until it holds still.
4. **kI:** only if a small error stays, a very small number.

Then put the tuned numbers in `Tunables.Lift`, in place of `DEFAULT`.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Lift.max = MEASURE` | Not measured yet. Do step 3 |
| The lift goes only down, slowly | Not homed yet. Push it down to the switch once |
| Every height is too high (no switch) | It was not at the bottom at INIT. Lower it, and INIT again |
| The lift goes down when it should go up | Add `.reversed()` to the lift in `Robot.java` |
| It sags below a height | `kF` is too small |
| It shakes at a height | `kP` or `kD` is too big |

**Two motors on one lift:** name the second motor on the same line, `.alsoMotorReversed("lift_right")`
([Two motors](../reference/parts/lift.md#two-motors-on-one-lift-or-arm)).

## Done when

- INIT shows no warning for the lift.
- Each button sends the lift to its height, and it holds still there.
- `min`, `max` and every height in `Tunables.Lift` are numbers, not `MEASURE`.

---

**Next:** [Add an arm](02-arm.md), or the part you need next ([Add to your robot](README.md)).

**Look it up:** [Lift](../reference/parts/lift.md): every setting, number and command.
