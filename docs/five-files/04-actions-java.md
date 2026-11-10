# Actions.java: several parts together

**The five files, page 4 of 5.**

An **action** is a list of steps that use several parts, with waits between them: "intake on, fire 3,
wait until the shots are done, intake off". You write it once in `Actions.java`. Then one button starts
it in TeleOp, and the same action is a step in an autonomous.

## Do you need it yet?

Maybe not. A job for one part is a button line in `Controls.java`, with no action. And some parts come
with **ready-made actions**: you use them with no line in `Actions.java`.

| Ready-made action | What it does | Use it |
| --- | --- | --- |
| Fire 1, Fire 2 ... | fires that many pieces; done when the last one has left | `robot.launcher.fireAction(2)` |
| Fire all | fires every piece the robot holds | `robot.launcher.fireAllAction()` |
| Fire at a speed | the same, at this flywheel speed (RPM), then back to the usual speed | `robot.launcher.fireAction(2, 2250)` |
| Ready to launch | spins up; done when the flywheel is at speed | `robot.launcher.readyAction(3)` (3 = at most 3 s) |
| Eject | one piece out at a low speed, like a wrong-colour piece | `robot.launcher.ejectAction()` |

**You need `Actions.java`** when one button or one autonomous step must do several things in order,
or must wait for one part before the next part moves.

## The smallest Actions.java

This is a whole `Actions.java`. The robot has no parts yet, so it has no actions. Copy it now: the
autonomous files on the next page use it.

<!-- recipe: first-actions -->
```java title="Actions.java"
import com.crossbotics.ftchelper.robot.Action; // (1)!

/** The five files, file 4 of 5: steps that use several parts together. */
public class Actions {
    // One method per action: public static Action name(Robot r) { ... }. Use it in Controls.java and in autos. (2)!
    // This robot has no parts yet, so it has no actions. A part's ready-made actions need no line here.
}
```

1. Every action is an `Action`. This line lets the file use that name.
2. Each action is one method in this class. The next section shows one, line by line.

**The first line of the file** (above the import) must be:

```text
package org.firstinspires.ftc.teamcode;
```

## Your first action

This robot has the two common parts from the [Robot.java page](01-robot-java.md#add-a-part): an intake
and a launcher. Its action fires 3 pieces while the intake runs, to push each piece in. Click a **(+)**
to read what a line does.

<!-- recipe: action-fire-with-intake -->
```java title="Actions.java"
/** Fires 3 pieces with the intake running, to push each one in. Then the intake stops. */
public static Action fireWithIntake(Robot r) { // (1)!
    return Action.named("Fire with intake") // (2)!
            .then(r.intake::in, () -> r.launcher.fire(3)) // (3)!
            .waitUntil("the shots", () -> !r.launcher.isShooting(), 9.0) // (4)!
            .then(r.intake::stop); // (5)!
}
```

1. `public static Action`: a method that makes the action. `r` is your robot, so the steps can use
   `r.intake` and `r.launcher`. You start it with the class name: `Actions.fireWithIntake(robot)`.
2. `Action.named(...)` starts a new action. The Driver Hub shows the name while it runs.
3. `.then(...)` does these now, in the same loop, then goes to the next step. Two commands, so the
   intake and the launcher start together.
   - `r.intake::in` is a **method reference**: "run `r.intake.in()` when the action gets here".
   - `() -> r.launcher.fire(3)` is a **lambda**: the same, for a command with inputs.
4. `.waitUntil(...)` waits until the test is true: the launcher is not shooting any more.
   - `"the shots"`: the words on the Driver Hub while it waits.
   - `9.0`: the **time limit**, in seconds. If the shots are not done in 9 s, the action stops and the
     Driver Hub says why. A wait never goes on for ever.
5. The last step: the intake stops. Then the action is done.

## The steps

Every action is built from four steps:

| Step | What it does | Example |
| --- | --- | --- |
| `.then(command)` | does it now, then goes to the next step | `.then(r.claw::open)` |
| `.then(command, command, ...)` | does them all now, in the same loop: the parts move together | `.then(r.intake::in, r.claw::close)` |
| `.waitSeconds(s)` | waits this many seconds. The robot keeps running: this is `sleep`, done safely | `.waitSeconds(0.5)` |
| `.waitUntil("words", test, s)` | waits until the test is true, at most `s` seconds. Too long: the action stops | `.waitUntil("lift up", r.lift::isAtTarget, 2.0)` |

**What you can wait for:** each part answers yes / no questions.

| Part | Questions |
| --- | --- |
| Launcher | `isReady()` (at speed), `isShooting()`, `isSpinning()`, `isEjecting()` |
| Intake | `isRunning()`, `isPushingOut()` |
| Lift, Arm (a motor) | `isAtTarget()`, `isAt("high")` |
| Claw, Wrist, Positioner (a servo) | `isDone()`, `isAt("open")`. A servo cannot say where it is: `isDone()` turns true after its travel time, `travelSec` ([Tunables](../guides/tunables.md#claw-wrist-and-positioner)) |
| Turret | `isAimed()`, `isHomed()` |
| Pieces | `isFull()`, `isEmpty()` |

Not the answer you want? Put a `!` in front: `() -> !r.launcher.isShooting()` = "not shooting".

## Start it from a button

In `Controls.java`, inside `buttons(...)`. Use the button's line, in place of its `free()` line:

<!-- recipe: action-fire-button -->
```java title="Controls.java"
g2.y.press(Actions.fireWithIntake(robot)).says("fire 3 with the intake");
g2.x.press(robot.launcher.fireAllAction()).says("fire all");   // a ready-made action
```

- `press(action)` starts the action. **Press again while it runs, and it stops** (cancelled, safely).
- The other press types start an action too: `tap`, `doubleTap`, `heldFor`, `with`
  ([the press types](03-controls-java.md#2-how-it-starts-the-press-types)).

## Use it in an autonomous

The same action is a step. The next page explains autonomous files: here is only the action's line.

<!-- recipe: action-fire-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .alongside(robot.launcher.readyAction(3))          // spin up while it drives
 .move("To the shooting spot", 24, 0, 0.5)
 .action(Actions.fireWithIntake(robot), 9)          // waits until it is done (at most 9 s)
 .move("Park", 12, 90, 0.5);
```

| Step | What it does |
| --- | --- |
| `.action(action, s)` | starts the action and waits until it is done, at most `s` seconds (10 if you leave it out). Too long: it is cancelled, the report says so, and the autonomous goes on |
| `.alongside(action)` | starts the action and goes on at once: it runs during the next steps |

**Why write it once:** fix the action in `Actions.java`, and TeleOp and every autonomous get the fix.

The simulator's **auto builder** lists your `Actions.java` actions too, next to the ready-made ones
([the auto builder](../guides/simulator.md#the-auto-builder)).

## The rules every action follows

- **It never makes the robot wait.** An action does a little every loop. The robot keeps driving and
  every other button works while it runs. **Why:** a `sleep()` or a `while` loop in a button job freezes
  the whole robot until it ends. Put the wait in an action: `.waitSeconds(...)`, `.waitUntil(...)`.
- **The driver always wins.** The driver moves a part the action uses: the action stops.
- **One part, one action.** A second action commands the same part: the first action stops.
- **A wait that runs out of time stops the action.** The Driver Hub says why, for example
  `timed out waiting for the shots`.
- **A stopped action leaves its parts safe.** An intake stops, a lift holds its height, shots still
  waiting are dropped. A finished action keeps its last commands.
- **A part that is not on the robot yet** (`.optional()` in Robot.java) is skipped. The action goes on with
  the other parts.

## Check it

**In the simulator:** pick **Simulator** in the run list next to Android Studio's **Run** button, and
click **Run**. Pick your TeleOp, INIT, ▶. Press the action's button.

**On the robot:**

1. Put pieces in the robot. Stand clear of the launcher. Keep a hand near STOP.
2. INIT, ▶. Press the action's button.
3. Watch the Driver Hub: `Fire with intake: step 2 of 3 (the shots)`. After it ends: `done`.
4. Press the button, then press it again: the Driver Hub says `cancelled`, and the intake stops.

**Done when:** one button runs every step in order, and a second press stops it.

## If something goes wrong

| Problem | Why, and the fix |
| --- | --- |
| `timed out waiting for ...` | The part needs more time than the limit, or it never got there. Tune the part, or give the wait more seconds. |
| The action stops at once | Something else commands the same part: the driver's stick or button, or another action. |
| Nothing happens on the button | Another line uses the same button (INIT stops and names it), or this press cancelled the action. |
| The robot freezes while a button job runs | The job has a `sleep()` or a `while` loop. Make it an action with `.waitSeconds(...)`. |
| `cannot find symbol: Actions` | The file is not in the `teamcode` folder, or its first line is not `package org.firstinspires.ftc.teamcode;`. |

---

**Next:** `autos/` (coming). Until then: [lesson 6, your first autonomous](../tutorials/start/06-first-autonomous.md).
**Look it up:** [Action: every step](../modules/robot.md#action). **Go deeper:**
[lesson 5, actions](../tutorials/start/05-actions.md) and [the cookbook's action recipes](../cookbook.md#actions).
