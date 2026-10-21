# Lesson 5: Actions: several parts together

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** write a move that uses the lift and the claw together (in your own `Actions.java`; the lesson
robot's is the worked example), start it from one button, and use
the same move in an autonomous.

**You need:**

- **Your own robot** with the mechanisms you added in [lesson 4](04-lift-and-claw.md), tuned so they
  reach their positions. The lesson robot (intake, lift and claw) is the worked example: read its
  lines, then write the same kind of action for your parts.
- About 30 minutes.

The finished files: [`Actions.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Actions.java)
and [`Controls.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Controls.java).

## 1. What an action is

Scoring takes several steps: lift up, wait until it is at the top, open the claw, wait for the piece
to drop, lift down. The operator could press four buttons in the right order every time. An
**action** is that list of steps, written once in `Actions.java`, started by one button.

## 2. scoreHigh, line by line

`public static Action scoreHigh(Robot r)` is a method that builds the action. `static` means you call it
by the class name, `Actions.scoreHigh(robot)`, with no setup. `r` is your robot, so the steps can reach
`r.lift` and `r.claw`.

```java
/** Lift up, open the claw at the top, then lift down. */
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // gives up after 2 s: the claw stays shut
            .then(r.claw::open)
            .waitSeconds(0.3)
            .then(() -> r.lift.goTo("down"));
}
```

`Actions.java` needs one import at the top: `import com.crossbotics.ftchelper.robot.Action;` (your own
from the Quick start has it already).

- `Action.named("Score high")` starts a new action. The name shows on the Driver Station.
- `.then(() -> r.lift.goTo("high"))` does something now, then goes to the next step.
  `() -> ...` is a **lambda**: a small piece of code the action runs later, when it gets to this step.
- `.waitUntil("lift at the top", r.lift::isAtTarget, 2.0)` waits until the lift says it is at its
  target. `r.lift::isAtTarget` is a **method reference**: the same as a lambda, for a method with no
  inputs. The words are for the Driver Station.
  - **The 2.0 is a time limit, in seconds.** If the lift is not at the top in 2 s, the action
    **stops**. Why: a lift that never got to the top (stuck, or not homed yet) must not open the
    claw and drop the piece in the wrong place.
  - `isAtTarget` is "is the lift at the height it was sent to?" (within `tolerance`).
- `.then(r.claw::open)` opens the claw.
- `.waitSeconds(0.3)` gives the piece time to fall out.
- `.then(() -> r.lift.goTo("down"))` sends the lift down. That is the last step: the action is done.

## 3. grabAndLift

```java
/** Close the claw on a piece, then lift it a little off the tiles. */
public static Action grabAndLift(Robot r) {
    return Action.named("Grab and lift")
            .then(r.claw::close)
            .waitUntil("claw closed", r.claw::isDone, 1.0)
            .then(() -> r.lift.goTo("low"));
}
```

A servo cannot report where it is. `isDone()` is a guess: it turns true after `travelSec` (default
0.3 s, set it in `Tunables.Claw`). So the lift only rises once the claw has had time to close.

To move two parts **at the same time**, give `then` several commands:
`.then(() -> r.lift.goTo("low"), r.claw::open)`.

## 4. Start it from a button

In `Controls.java`:

```java
@Override
protected void buttons(Robot robot, Pad g1, Pad g2) {
    g2.rb.press(Actions.scoreHigh(robot)).says("score high");     // lesson 5
    g2.b.toggle(robot.claw).says("claw").rumble();                 // 2 buzzes = closed, 1 = open
    g2.lb.tap(Actions.grabAndLift(robot)).says("grab and lift")    // a tap...
         .heldFor(1.0, () -> robot.lift.goTo("down")).says("lift all the way down");   // ...or held 1 s
}
```

- `press(action)` starts the action. **Pressing again while it runs cancels it.** The same goes for a
  gesture: `tap(action)` starts it, another tap cancels it.
- `.says(...)` is the words on the button map.
- RB and LB on gamepad 2 are free on this robot, so nothing is replaced.

## 5. The rules every action follows

These come from the `Action` class itself:

- **It never waits.** An action does a little every loop, then lets the loop go on. The robot keeps
  driving and every other button keeps working while it runs.
  **This is why you need actions:** a button job must never wait. `sleep(500)` or a `while` loop in a
  job compiles, but it freezes the whole robot (no driving, no other button) until it ends. The Driver
  Station then warns: `... took 512 ms: the whole robot stopped that long. No sleep() or while loop in a
  button job: use an Action with waitSeconds (lesson 5)`. Put the wait in an action
  (`.waitSeconds(0.5)`, `.waitUntil(...)`) instead.
- **Parts are claimed.** A part belongs to the action from the step that first commands it. If
  another action commands that part, the first action stops.
- **The driver always wins.** If the operator moves a claimed part (a lift button, the lift stick),
  the action stops. Example: during "Score high", pressing A stops it and the lift goes down.
- **A wait that times out stops the action**, and the Driver Station says why:
  `timed out waiting for lift at the top`.
- **A stopped action leaves its parts safe.** Cancelled, taken over or timed out: a lift holds its
  height, an intake stops. An action that finishes keeps its last commands (here: the lift goes down).

While it runs, the Driver Station shows a line like `Score high: step 2 of 5 (lift at the top)`.
After it ends, the result shows for 3 seconds: `done`, `cancelled`, or the reason it stopped.

## 6. The same action in an autonomous

The autonomous (lesson 6) uses the same method as a step:

```java
.action(Actions.scoreHigh(robot), 5)
```

`s.action(...)` starts the action and waits until it ends. The 5 is the most seconds it may take
(10 if you leave it out). If it runs out of time, it is cancelled and the end-of-match report says so;
a timed-out wait also shows in the report. Then the autonomous goes on to its next step.
`s.alongside(action)` starts one and goes on at once, so it runs during the next steps (for example,
lift up while driving).

**Why write it once:** fix the scoring in `Actions.java`, and TeleOp and autonomous both get the fix.

## 7. An action at START

An action can also run once, right after START, in autonomous and TeleOp. Add it in `Robot.java`'s
`define()`. The [flywheel robot](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot/Robot.java)
uses this to fold its intake down:

```java
atStart(() -> Actions.deployIntake(this));
```

Never in INIT: nothing may move before START.

## Try it in the simulator

From the `ftc-helper-lib` folder (the first line once, as in lesson 4):

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

Open http://localhost:8780, pick "Lesson Bot TeleOp", INIT, START. Then try each rule:

1. Gamepad 2 RB: the lift rises, the claw opens at the top, the lift comes down. Watch the
   `Score high: step ...` line.
2. RB, then RB again while it rises: `cancelled`, and the lift holds where it was.
3. RB, then A while it rises: `stopped: Lift moved by hand`.

To check it without a browser, save this as `build/score-high.sim`:

```text
init "Lesson Bot TeleOp"
start
press g2.rb                                  # score high
expect within 5 motor lift ticks > 1500      # the lift rises
expect within 5 servo claw target = 0.20 0.01  # the claw opens (Tunables.Claw.open)
expect within 5 motor lift ticks < 100       # and the lift comes back down
expect error none
stop
```

and run `./gradlew :sim:scenario -PteamCode=none -Probot=build/lessonbot.properties -Pfile=build/score-high.sim`.
If the claw line fails and the screen says `timed out waiting for lift at the top`, the lift is too
slow for the 2 s limit (see the table below). ([Scenario files](../../guides/simulator.md#scenario-files-test-without-a-browser).)

**Your own robot.** From the `ftc-helper-lib` folder, write your simulator file and start it:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

Then try your own action's button, as in steps 1 to 3 above. To test it without a browser, write a
scenario file **in your robot project**, in a folder `simulator/scenarios/` (next to `TeamCode`), for
example `simulator/scenarios/my-test.sim`. **Why there:** it is saved and committed with your code, and
the simulator's automatic tests run every scenario in that folder. (The library's `build/` folder is
wiped by a clean build.) This one checks the Quick start robot's intake (G2 RT) and its `spitOut` action
(G2 D-pad left):

```text
init "Quick Start TeleOp"                    # your TeleOp's name, exactly as in Controls.java
start
hold g2.rt 1                                 # hold RT: the intake runs in
expect within 1 motor intake power > 0.5
release g2.rt                                # let go: it stops
expect within 1 motor intake power = 0
press g2.dpad_left                           # spit out: out for 0.5 s, then stop
expect within 1 motor intake power < -0.5
expect within 2 motor intake power = 0
expect error none
stop
```

Run it from the `ftc-helper-lib` folder (the path is from that folder):

```bash
./gradlew :sim:scenario -PteamCode=../TeamCode -Pfile=../simulator/scenarios/my-test.sim
```

The report shows each line as `ok` or `FAIL`, and ends with `PASSED` or `FAILED`. Change the names to
your own (`intake` is the motor's name in the configuration), then add lines for your action.

## Check it on the robot

1. Lift down, a piece in the claw, hand near STOP. INIT, START.
2. RB: the lift goes up, the claw opens only at the top, the lift comes down.
3. RB, then move the lift stick: the action stops and the lift holds.
4. LB with the claw over a piece: it closes, then lifts to `low`.

## If something goes wrong

| Problem | Why, and the fix |
| --- | --- |
| `timed out waiting for lift at the top`, the claw stays shut | The lift needs more than 2 s, or it is not homed. Tune it faster (lesson 4), home it, or give the wait more time. |
| The action stops as soon as it starts | Something else commands the same part: the operator is touching the lift stick, or another action uses the lift. |
| Nothing happens on RB | Another job is on G2 RB in `Controls.java` (INIT stops and names it), or the action is already running and this press cancelled it. |
| The lift rises before the claw is closed | Raise `travelSec` in `Tunables.Claw`. |
| The claw opens and the piece misses | Tune the `high` height (lesson 4), or wait longer before opening. |

## Under the hood

How the steps run without waiting: [Why commands never wait](../../guides/non-blocking.md). The same
idea built by hand: [tutorial 10](../10-auto-with-mechanisms.md) and [tutorial 16](../16-easy-autos.md).
Every method: [Action](../../modules/robot.md#action).

## Next

[Lesson 6: Your first autonomous](06-first-autonomous.md).
