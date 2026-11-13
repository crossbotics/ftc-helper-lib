# Make parts work together

One button moves the lift, the arm and the claw in order, and a safety rule keeps the arm and the lift from
hitting each other.

**You need:**

- A lift, an arm and a claw, each added and checked on its own:
  [Add a lift](01-lift.md), [Add an arm](02-arm.md), [Add a claw](03-claw.md).
- Their named positions measured: lift `down` and `high`, arm `stowed` and `score`, claw `open` and
  `closed`. Use your own names in place of these.

## 1. What it is

Two things join the parts:

- **An action** puts the moves in order: lift up, arm out, claw open, arm in, lift down. Each move waits
  until the part before it is there. One button starts it, and the same button cancels it.
- **A safety rule** (an **interlock**: a rule that keeps two parts apart) stops the arm from swinging out
  while the lift is low. The action has the right order. The rule also covers a driver who moves one part
  by hand, and an action that you cancel half way.

**Why both:** the action makes the score fast and the same each time. The rule makes a crash impossible,
whoever moves the parts.

## 2. Add it to the five files

### Robot.java: the safety rule

**Where:** `Robot.java`, in `define()`, after the lift's, the arm's and the claw's lines.

```java title="Robot.java"
// The arm stays below its line while the lift is low, and the lift stays up while the arm is out.
keepApart("ArmLift", arm.zoneAbove(), lift.zoneBelow());
```

- `arm.zoneAbove()` is the arm's zone: from an angle up to the top of its swing (the arm is out).
- `lift.zoneBelow()` is the lift's zone: from a height down to the bottom (the lift is low).
- The two parts are never in their zones at the same time. **The part that is in its zone first keeps
  it. The other part waits outside its own zone**, with its target kept. It goes on when the first part
  leaves.

For comparison, the cookbook's rule for a turret and a lift has the same shape:

<!-- recipe: interlock-rule -->
```java title="Robot.java"
// AFTER the parts' add lines: the turret stays out of its zone while the lift is in its
// zone, and the other way round. Whoever is in its zone first keeps it.
keepApart("TurretLift", turret.zone(), lift.zoneAbove());
```

### Tunables.java: where each zone starts

**Where:** `Tunables.java`, a new section. Its name is the rule's name, `"ArmLift"`.

```java title="Tunables.java"
public static class ArmLift {                                   // keepApart("ArmLift", ...) in Robot.java
    public static double armAbove = MEASURE;                    // degrees: the arm is out from here up
    public static double liftBelow = MEASURE;                   // counts: the lift is low from here down
    public static double armStopSec = 0.15;                     // how long the arm takes to stop
    public static double liftStopSec = 0.2;                     // a heavy slide takes longer to stop
}
```

- The field names come from the part's section name with a small first letter: `Arm` -> `armAbove`,
  `Lift` -> `liftBelow`. A lift called `lift_left` gives `liftLeftBelow`. INIT stops on a wrong name.
- Each zone is in its part's own units: degrees for the arm, counts for the lift.
- The rule reads these numbers once, at INIT. They are never in the tuning menu. **Why:** a zone that
  changes while the parts move could let them hit.

The cookbook's section for the turret and lift rule:

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

**Find the two numbers with the Motor Finder** ([Motor Finder](../tuning/05-motor-finder.md)). Keep a hand
near STOP:

1. Choose `lift`. Raise the slide slowly until the arm can swing out with room to spare. Press **A** there.
   That count is `liftBelow`.
2. Put the lift at the bottom. Choose `arm`. Swing the arm out slowly until it almost touches the slide or
   the frame. Press **A** there. Change that count to degrees, as in
   [Add an arm, step 3](02-arm.md#3-find-its-numbers). A number a little below it is `armAbove`.

### Actions.java: the score action

**Where:** `Actions.java`, one method per action.

```java title="Actions.java"
/** Lift up, arm out, claw open, arm in, lift down: each move waits for the one before it. */
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)    // at most 2 s
            .then(() -> r.arm.goTo("score"))
            .waitUntil("arm out", r.arm::isAtTarget, 1.5)
            .then(r.claw::open)
            .waitUntil("claw open", r.claw::isDone, 1.0)
            .waitSeconds(0.3)                                         // let the piece drop
            .then(() -> r.arm.goTo("stowed"))
            .waitUntil("arm in", r.arm::isAtTarget, 1.5)
            .then(() -> r.lift.goTo("down"));
}
```

- `waitUntil(words, check, seconds)` waits until the check is true, at most that many seconds. When the
  time runs out, the action stops, and the Driver Station says `timed out waiting for` and the words.
  **Why:** a lift that never reached the top must not open the claw.
- `isAtTarget` is true when a motor part (lift, arm) is at its named position. `isDone` is true when a
  servo part (claw) has had the time to get there.
- The safety rule still works inside the action. If the arm is sent out too early, it waits at its line.

### Controls.java: the buttons

**Where:** `Controls.java`, inside `buttons(...)`.

```java title="Controls.java"
// Press Y: the action starts. Press Y again while it runs: it is canceled, safely.
g2.y.press(Actions.scoreHigh(robot)).says("score high");
```

Add a button that stops every action at once:

<!-- recipe: action-cancel-all -->
```java title="Controls.java"
// Stop every running action at once (each stops what it started, safely).
g2.back.press(robot::cancelAll).says("stop all actions");
```

A canceled action stops what it started, safely: the lift holds its height and the arm holds its angle.
The safety rule still holds. Move the parts back with their own buttons.

**Use free buttons.** The part pages put the lift's heights on A, X and Y. Move one of them, or use another
button for the action. INIT lists the free buttons (`G2 free: ...`).

### Autos: the same action as one step

**Where:** your autonomous file in `autos/`, in its steps.

<!-- recipe: action-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .forward("To the goal", 24, 0.5)
 .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
 .back("Back up", 12, 0.5);
```

`.action(action, seconds)` starts the action and waits until it is done. When it takes too long, the
autonomous cancels it, and goes on with the next step.

## 3. Check it

1. Lift the wheels off the floor, and keep a hand near STOP. Put the lift at the bottom and the arm in.
2. Press INIT. Check that no warning names `ArmLift`. Press ▶.
3. **Check the rule first, by hand:** with the lift at the bottom, send the arm out with its own button.
   The arm must stop below its line and wait. The Driver Station says which part waits, and why.
4. Raise the lift. The arm must go on to its position by itself.
5. With the arm out, send the lift down. The lift must wait until the arm comes back in.
6. **Then the action:** put the parts back. Press Y once and watch each move. Press Y again during a move:
   everything must stop where it is.
7. Press Y and let it run to the end. Each move must start only when the one before it is done.

**Slow first:** for the first runs, lower `maxPower` in `Tunables.Lift` and `Tunables.Arm`. Put it back
when the order is right.

## 4. Tune it

- **Wait times:** each `waitUntil` has a time limit. Set it a little longer than the move takes on the
  robot. A limit that runs out stops the action. Watch the Driver Station for the wait's name.
- **The drop time:** `waitSeconds(0.3)` gives the piece time to leave the claw. Make it longer if the arm
  pulls the piece back in.
- **Order:** some moves can start before the one before them ends. For example, the arm can start out when
  the lift is above `liftBelow`. Change one wait at a time, and check again.
- **The rule's stop time:** if a part still goes past its waiting point, raise its `StopSec`
  ([Interlock](../reference/parts/interlock.md)). If it waits too far out, lower it.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.ArmLift.armAbove = MEASURE` | Not measured yet. Find the two numbers (step 2) |
| INIT stops on a field name | The name does not match the part. INIT says "did you mean ...?". Use that name |
| The arm never swings out | The lift never leaves its zone: `liftBelow` is above the `high` height. Lower `liftBelow` |
| The lift never goes down after a score | The arm is still in its zone: `armAbove` is below the `stowed` angle. Raise `armAbove` |
| The action stops half way | A `waitUntil` ran out of time: `timed out waiting for ...`. Give it more time, or tune that part |
| The claw opens before the arm is out | The arm's wait is missing or too short. Wait on `r.arm::isAtTarget` |
| The parts still touch | The zones are too small. Move `armAbove` down or `liftBelow` up, or add a margin ([Interlock](../reference/parts/interlock.md)) |

## Done when

- With the lift low, the arm will not swing out, from any button.
- With the arm out, the lift will not go down.
- One press of the button scores, in order, and a second press stops it.
- The autonomous runs the same action as one step.

---

**Next:** [Controls](../controls/README.md): more ways to use the buttons.

**Look it up:** [Actions.java](../five-files/04-actions-java.md) for every action step, and
[Interlock](../reference/parts/interlock.md) for every rule setting. Examples:
[Two parts that must not collide](../guides/interlocks.md).
