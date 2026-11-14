# Add a claw and other servo parts

A servo that goes to named positions: a claw that opens and closes, a wrist that tilts, or a fold-out.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: the servo, plugged into a servo port. In the robot configuration, call it `claw` (or
  `wrist`).

## 1. What it is

A servo part holds **named positions**. A position is a number from 0 to 1. You send the servo to a
position by its name.

| Part | What it does | Its positions |
| --- | --- | --- |
| **Claw** | opens and closes | `open` and `closed` (both needed) |
| **Wrist** | tilts a claw, for example at the end of an [arm](02-arm.md) | any names, for example `down`, `level`, `up` |
| **Positioner** | any other servo with named positions: a gate, a kicker, a fold-out intake | any names, for example `stowed`, `deployed` ([a fold-out intake](04-intake.md#a-fold-out-intake)) |

- **A servo cannot say where it is.** The library guesses when it has arrived, from `travelSec` (the time
  for a full move).
- **A servo does not move until the first command after ▶.**
- **Never let a servo push on something.** At a hard stop it gets hot and can strip its gears. The finder
  helps you find the positions just before the stops.

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class. The line goes in `define()`, after the
drive.

<!-- recipe: claw -->
```java title="Robot.java"
// At the top of the class: public Claw claw;   Here in define():
// "claw" = the servo's name in the robot configuration. Its numbers: Tunables.Claw.
// .part(...): which servo it is (optional, recommended).
claw = add(Claw.servo("claw").part("gobilda-2000-torque"));
```

- `.part(...)` names the servo from the [parts catalog](../guides/parts-catalog.md). **Why:** the library
  then takes `travelSec` from the servo's speed.
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

**A wrist** is the same line with `Wrist`:

<!-- recipe: wrist -->
```java title="Robot.java"
// At the top of the class: public Wrist wrist;   Here in define():
// "wrist" = the servo's name in the robot configuration. Its numbers: Tunables.Wrist.
// .part(...): which servo it is (optional, recommended).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed"));
```

**A claw on two servos**, one for each finger: name the second servo on the same line.

<!-- recipe: claw-two-servos -->
```java title="Robot.java"
// "claw_left", "claw_right" = the servos' names in the robot configuration. Numbers: Tunables.ClawLeft.
// Find open and closed in Tune and check > Servo Finder on the FIRST servo: the second moves with it.
// The servos face each other, so the second turns the other way: alsoServoReversed.
claw = add(Claw.servo("claw_left").part("gobilda-2000-torque").alsoServoReversed("claw_right"));
```

Two servos that turn the same way use `.alsoServo(...)`
([Two servos on one part](../reference/parts/claw.md#two-servos-on-one-part)).

**The servo's whole turn:** a servo turns a little less than its seller says, on the FTC normal range.
`.fullRange()` gives it its whole turn. Add it **before** you find the positions: the same number points
somewhere else after the range changes.

<!-- recipe: servo-full-range -->
```java title="Robot.java"
// fullRange(): positions 0 to 1 = the servo's whole turn (300 degrees on a goBILDA 2000).
// Turn it on FIRST, then find the positions (Servo Finder: press Y while choosing the servo).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());
```

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The section's name is the device's name in CamelCase: `"claw"`
reads `Tunables.Claw`.

<!-- recipe: tunables-claw -->
```java title="Tunables.java"
public static class Claw {                                      // "claw" in the configuration
    // The Servo Finder shows "Tunables.Claw: P1 = 0.210, P2 = 0.770": write P1 as open, P2 as closed.
    public static double open = MEASURE;                        // servo position, open (Servo Finder)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

The wrist's section has its own names. Every number except `travelSec` is a position:

<!-- recipe: tunables-wrist -->
```java title="Tunables.java"
public static class Wrist {                                     // "wrist" in the configuration
    public static double down = MEASURE;                        // servo position: any name you like
    public static double level = MEASURE;                       // servo position (Servo Finder)
    public static double up = MEASURE;                          // servo position (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

A claw on two servos takes the **first** servo's name, and has one more number, `alsoTrim`:

<!-- recipe: tunables-claw-two-servos -->
```java title="Tunables.java"
// A claw on two servos, Claw.servo("claw_left").alsoServoReversed("claw_right"): its section is the
// FIRST servo's name in CamelCase.
public static class ClawLeft {                                  // "claw_left" in the configuration
    public static double open = MEASURE;                        // servo position, open (Servo Finder on claw_left)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
    public static double alsoTrim = DEFAULT;                    // the second servo: raw position added so the two meet (0; used at the next move)
}
```

- `MEASURE` means "find this on the robot". INIT stops until you write a number.
- `DEFAULT` means "use the library's starting value" (the number in brackets).

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-claw -->
```java title="Controls.java"
g2.b.toggle(robot.claw).says("open / close");
```

<!-- recipe: buttons-wrist -->
```java title="Controls.java"
g2.dpadLeft.press(robot.wrist::previous).says("previous position");
g2.dpadRight.press(robot.wrist::next).says("next position");
```

- The operator's B opens and closes the claw.
- D-pad left and right move the wrist to the next position, in order of value. To send it to one
  position, use `goTo`: `g2.x.press(() -> robot.wrist.goTo("level")).says("level");`.

With one gamepad (`oneDriver = true`), these `g2` lines work on gamepad 1
([One gamepad, and a second one when you want it](../controls/01-how-buttons-work.md#one-gamepad-and-a-second-one-when-you-want-it)).

### Actions.java and autos: use it in steps

You need no line here for the claw alone. When the claw works with another part, an action puts them in
order. For example, lift up, open the claw, lift down
([the lift's example](01-lift.md#actionsjava-and-autos-use-it-in-steps)). A servo cannot say when it has
arrived. So wait for it with `.waitUntil("claw done", r.claw::isDone, 1.0)`, or a short `.waitSeconds(...)`.

## 3. Find its numbers

INIT stops on `MEASURE`. The finders still run, because they do not read `Tunables.java`.

1. **Servo Finder** ([how](../tuning/03-servo-finder.md)): choose the servo. Find the two ends: press **X**
   just before one hard stop, and **B** just before the other.
2. Move to each position you need (open, closed) and press **A**.
3. Put the numbers in the servo's section. Give each P a real name: `P1` -> `open`, `P2` -> `closed`.

**Two servos:** choose the **first** servo. The second moves with it. When the two sides do not meet,
change `alsoTrim` in TeleOp, a little at a time.

## 4. Check it, then tune it

**Check it:**

1. Keep fingers out of the claw. Press INIT. Check that no warning names the part. Press ▶.
2. Press B. The claw must open. Press B again. It must close.
3. Listen at each position. **A servo that hums or gets hot is pushing on something.** Move that position
   0.01 away from the stop.
4. For a wrist: press D-pad right and left. It must go to each position in turn.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs. The positions change at
once:

1. **`closed`:** close the claw on a game piece. It must hold the piece without humming.
2. **`open`:** wide enough to let the piece go, and no wider. A smaller move is a faster move.
3. **`travelSec`:** when an action goes on before the servo arrives, make it longer
   ([Timing travelSec](../reference/parts/claw.md#timing-travelsec)).

Then put the tuned numbers in the servo's section, in place of the old ones.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Claw.open = MEASURE` | Not measured yet. Do step 3 |
| The servo does not move at all | No command after ▶ yet. Press its button. Still nothing? Check the port and the name in the robot configuration |
| The servo hums or gets hot at a position | It pushes on a stop. Move that position 0.01 away |
| The two fingers do not meet | Change `alsoTrim` in Live tuning |
| The second servo fights the first | Change `.alsoServoReversed(...)` to `.alsoServo(...)`, or the other way |
| Every position is a little off after a repair | The arm on the servo went on one tooth off ([Put an arm back on](../tuning/03-servo-finder.md#put-an-arm-back-on)) |
| Every position is off after you added `.fullRange()` | The range changed. Find the positions again |
| An action goes on before the claw has closed | `travelSec` is too short |

## Done when

- INIT shows no warning for the part.
- Each button sends the servo to its position. No servo hums at a position.
- Every position in its section is a number, not `MEASURE`.

---

**Next:** [Add an intake](04-intake.md).

**Look it up:** [Claw](../reference/parts/claw.md) (every setting, number and command for all servo parts),
[Wrist](../reference/parts/wrist.md), [Positioner](../reference/parts/positioner.md).
