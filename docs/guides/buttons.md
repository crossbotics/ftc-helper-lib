# The button menu: every way a button can work

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** anyone writing `Controls.java` (the robot in five files).
**You need:** [Lesson 3: Buttons](../tutorials/start/03-buttons.md) first, so you know where these lines go.

Every button line is built from up to four choices, like ordering from a menu:

```java
//  1. which button   2. how it starts        3. what it does            4. feedback
    g2.lb             .tap(                   robot.launcher::fire)      .says("fire one").rumble();
```

Pick one item from each part below. Parts 1 to 3 are needed; part 4 is optional (but always add
`.says(...)`: the drivers read it on the button map).

Every line goes inside `buttons(Robot robot, Pad g1, Pad g2) { ... }` in `Controls.java`. The examples
use `robot.launcher` (if your robot has a launcher; with the Quick start robot, try `robot.intake`:
its jobs are `in`, `out`, `stop` and `toggle`).

## 1. Which button

Write the gamepad, a dot, then the button.

| Gamepad | Who holds it |
| --- | --- |
| `g1` | the driver |
| `g2` | the operator (with `oneDriver = true`, `g2` lines go on gamepad 1 too) |

| Buttons | Name in the code |
| --- | --- |
| Face buttons | `a`, `b`, `x`, `y` |
| Bumpers | `lb`, `rb` |
| Triggers (count as pressed past half way) | `lt`, `rt` |
| D-pad | `dpadUp`, `dpadDown`, `dpadLeft`, `dpadRight` |
| Stick clicks (press the stick down) | `leftStickButton`, `rightStickButton` |
| Small buttons | `back`, `start` |
| Sticks (a number, not a button: see the end) | `leftStickX`, `leftStickY`, `rightStickX`, `rightStickY` |

Gamepad 1's driving buttons are taken: the left stick, right stick X, BACK, START, LT (slow, with two
drivers) and Y (field-centric, on a mecanum with an IMU, with two drivers). With `oneDriver = true`,
also the left stick click (slow on/off) and the right stick click (field-centric on/off, on a mecanum
with an IMU). INIT stops with a message if you use one.

## 2. How it starts

"Every loop" below means: each time round the library's **loop**. The loop runs many times a second;
each time round it reads the gamepads, runs the jobs that are due and updates every part. You never
write it: `buttons(...)` runs once, at INIT, and only plugs the jobs into the buttons.

### One job on the button

| You want | Write | When the job runs |
| --- | --- | --- |
| Press once | `.press(job)` | once, the moment it is pressed |
| Let go | `.release(job)` | once, the moment it is let go |
| Hold | `.hold(job)` | every loop while it is held |
| Hold, then stop | `.hold(job, stopJob)` | every loop while held, then `stopJob` once when let go |
| On / off switch | `.toggle(part)` | each press switches the part (an intake, a launcher, a claw, a mode) |
| On / off with two jobs | `.toggle(onJob, offJob)` | presses take turns: on, off, on... |

### Several gestures on one button

A **gesture** is a way of pressing: a tap, a hold, two taps... One button can have several, chained
with dots. Last season's robot fired one ball on a tap, kept firing on a hold, and sprayed (fired
without waiting for speed) on a tap, then hold.

| Gesture | Write | When the job runs |
| --- | --- | --- |
| Tap (a quick press) | `.tap(job)` | once per tap |
| Hold | `.hold(job)` or `.hold(job, stopJob)` | every loop while held (with other gestures: after 0.3 s) |
| Double-tap | `.doubleTap(job)` | once, on the second quick tap |
| Tap, then hold | `.tapThenHold(job)` or `.tapThenHold(job, stopJob)` | a quick tap, then press again and hold: every loop while held |
| Several taps | `.taps(3, job)` (2 to 5) | once, on the last quick tap |
| Held for a time | `.heldFor(1.0, job)` | once, after it is held 1 s (a safe "are you sure?"). With a `hold` on the same button, both run: the hold from 0.3 s, `heldFor` once at its time |

Example, the fire button:

```java
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

**When does a gesture's job run?** At once, unless the same button has a longer gesture that starts
the same way. Then the library waits only until it knows:

- A tap on a button that also has a hold runs when you let go. A tap is a press let go before the
  hold starts (`Tunables.Buttons.holdSec`, 0.3 s). With `heldFor` and no hold, any press let go before
  the `heldFor` time is a tap.
- A tap on a button that also has a double-tap (or tap-then-hold, or taps) waits 0.35 s for a second
  tap. **Why:** otherwise every double-tap would also run the tap job first.
- Two quick taps on a button with no double-tap job run the tap job twice.
- On a button with a double-tap (or taps), a second press within 0.35 s of a tap counts as the second
  tap, not a hold. To hold, wait a moment after the tap, or give the button `tapThenHold` for "tap, then hold".

The same thing on the Quick start robot's intake:

```java
g2.rb.tap(robot.intake::toggle).says("intake on/off")
     .doubleTap(robot.intake::out).says("push out");
```

**The two times are optional.** Leave `Tunables.Buttons` out and the defaults below are used. Add the
section to `Tunables.java` only to change them. They are then in the tuning menu too: a value you tune
there, copy back into `Tunables.java` (the file is the record):

```java
public static class Buttons {
    public static double tapGapSec = 0.35;   // the most time between the taps of a double-tap
    public static double holdSec = 0.3;      // a press this long is a hold, not a tap
}
```

### Which double-tap?

There are two ways to put a tap and a double-tap on one button:

| Write | A tap | A double-tap | Use it when |
| --- | --- | --- | --- |
| `.tap(a).doubleTap(b)` | runs `a`, 0.35 s after you let go (it waits to be sure no second tap comes) | runs only `b` | `a` must not run on a double-tap |
| `.tapOrDoubleTap(a, b)` (the older way) | runs `a` at once | runs `a` (first tap), then `b` (second tap) | both fit together, like intake on/off (tap), then push out (double-tap) |

`tapOrDoubleTap` always uses 0.35 s (not `Tunables.Buttons`), and it is one job: it cannot be chained
with other gestures.

### Two buttons together

| You want | Write | When the job runs |
| --- | --- | --- |
| A pair: hold one, press another | `g2.lb.with(g2.y, job).says("climb")` | once, when Y is pressed while LB is held |

The first button (`lb` here) is the **shift key**: it gets no job of its own. One shift key can start
several pairs (`g2.lb.with(g2.x, ...)`, `g2.lb.with(g2.y, ...)`). (Not gamepad 1's BACK:
it is a driving button, and INIT refuses it.) The second button keeps its own
job when it is pressed alone. While the shift key is down, the second button's own job does not run
(a hold it started stops), and the button does nothing more until it is let go. Neither button may be
one of gamepad 1's driving buttons. **Why:** a pair is a safe place for rare, big jobs (like a climb) that must
not run by accident.

## 3. What it does

The job is any of these:

| Kind | Example | Notes |
| --- | --- | --- |
| A method of a part | `robot.claw::open` | `::` hands over the method; the button runs it later |
| A few commands | `() -> { robot.claw.open(); robot.intake.stop(); }` | `() ->` starts a small piece of code (a **lambda**) |
| An action (several steps) | `Actions.scoreHigh(robot)` | for `press`, `tap`, `doubleTap`, `taps`, `heldFor`, `with`: starts it; the same gesture again cancels it ([lesson 5](../tutorials/start/05-actions.md)) |
| A part to switch | `robot.intake` | for `toggle(...)` only |

**A job must never wait.** No `sleep(...)` and no `while` loop in a job. It compiles, but it freezes
the **whole robot** (no driving, no other button, no part updates) until the job ends. The Driver
Station then warns: `... took 512 ms: the whole robot stopped that long. No sleep() or while loop in a
button job: use an Action with waitSeconds (lesson 5)`. To wait between steps, make an action with
`waitSeconds(...)` or `waitUntil(...)` ([lesson 5](../tutorials/start/05-actions.md)), and put the action
on the button.

While the robot is paused (a tap of gamepad 1 BACK), no button job runs, with one exception: a hold's
stop job (the second job in `hold(job, stopJob)`) still runs when the button is let go, or when the
pause starts in the middle of a gesture's hold. **Why:** so a part a hold started is told to stop, and
stays stopped when the pause ends.

## 4. Feedback

| You want | Write | What the driver gets |
| --- | --- | --- |
| Words on the button map | `.says("fire one")` | the line `G2 RB: fire one` at INIT and when GUIDE is tapped |
| A buzz when the job runs | `.rumble()` | one short buzz (a hold: when it starts) |
| More buzzes | `.rumble(2)` (1 to 3) | that many short buzzes |
| On / off you can feel | `.toggle(part).rumble()` | two buzzes = on, one buzz = off (a claw: two = closed) |
| A buzz from the robot | `g2.rumbleWhen(robot.launcher::isReady);` | a buzz each time it becomes true (`rumbleWhen(test, 2)`: two buzzes) |
| A color on the robot | `robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);` | a light the drivers see while it is true ([Lights](../modules/robot.md#lights)) |

`rumbleWhen` is not tied to a button: it is a line of its own inside `buttons(...)`, like the others
(it ends with `;`, nothing chains after it), and it is not on the button map. With the Quick start
robot: `g2.rumbleWhen(robot.intake::isRunning);` buzzes when the intake starts.

**Show a value on the Driver Station:** `show("Lift ticks", robot.lift::position);` (or
`show("Intake on", robot.intake::isRunning);` with the Quick start robot). Like `rumbleWhen`, it is a
line of its own inside `buttons(...)`, not tied to a button. The library reads the value again every
loop and shows it during the match. **Why:** you see what the robot "thinks" while you drive. The
value must be quick to read (a method reference or a small lambda).

`.says(...)` and `.rumble()` belong to the gesture just before them:
`g2.rb.tap(a).says("one").doubleTap(b).says("three").rumble()` buzzes on the double-tap only.

**Why buzz?** The drivers watch the robot, not the Driver Station. A buzz says "done" or "ready"
without looking away. In the simulator, the gamepad picture shakes (and a real controller or a phone
buzzes).

## Sticks

A stick is a number from -1 to 1 (up and right are +), not a button:

| You want | Write |
| --- | --- |
| Its value every loop | `g2.leftStickY.use(robot.lift::manual).says("lift by hand")` |
| No job (even if a part suggested one) | `g2.rightStickX.free()` |

## The rules (checked at INIT)

- One job per button, or several gestures. A button with `press(...)` and `tap(...)` stops INIT:
  `Controls.java: G2 B has two jobs`. The same gesture twice stops too: `G2 A has two "tap" jobs`.
- Your job on a button a part suggested replaces the suggestion. `.free()` clears a button.
- A shift key has no job of its own. `.free()` on a shift key removes its pairs too.
- A button held when START is pressed (or when the tuning menu closes) does nothing until it is let go.

## Before this library: how it was done

The same gestures by hand take timers and "was it down last loop?" flags for each button. Last
season's fire button (tap, hold, tap then hold) took about 40 lines in the TeleOp loop. Here it is one
line per gesture, and the button map lists every gesture.

## See also

- [Cookbook: buttons and gestures](../cookbook.md#buttons-and-gestures): one question, a few tested lines, per job.

- [Lesson 3: Buttons](../tutorials/start/03-buttons.md): the standard buttons each part brings.
- [The robot module: ControlMap](../modules/robot.md#controlmap): every method in one table.
- [Gamepad module](../modules/gamepad.md): the same jobs with `ControllerBindings`, without the five files.
