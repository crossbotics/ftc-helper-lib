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

## 1. Which button

Write the gamepad, a dot, then the button.

| Gamepad | Who holds it |
| --- | --- |
| `g1` | the driver |
| `g2` | the operator (with `oneDriver = true`, use `g1` for everything) |

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
drivers) and Y (field-centric, on a mecanum with an IMU). INIT stops with a message if you use one.

## 2. How it starts

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
| Held for a time | `.heldFor(1.0, job)` | once, after it is held 1 s (a safe "are you sure?") |

Example, the fire button:

```java
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

**When does a gesture's job run?** At once, unless the same button has a longer gesture that starts
the same way. Then the library waits only until it knows:

- A tap on a button that also has a hold runs when you let go (a press shorter than 0.3 s is a tap).
- A tap on a button that also has a double-tap (or tap-then-hold, or taps) waits 0.35 s for a second
  tap. **Why:** otherwise every double-tap would also run the tap job first.
- Two quick taps on a button with no double-tap job run the tap job twice.

The two times are numbers in `Tunables.java` (and in the tuning menu):

```java
public static class Buttons {
    public static double tapGapSec = 0.35;   // the most time between the taps of a double-tap
    public static double holdSec = 0.3;      // a press this long is a hold, not a tap
}
```

### Two buttons together

| You want | Write | When the job runs |
| --- | --- | --- |
| A pair: hold one, press another | `g2.back.with(g2.y, job)` | once, when Y is pressed while BACK is held |

The first button (`back` here) is the **shift key**: it gets no job of its own. One shift key can start
several pairs (`g2.back.with(g2.x, ...)`, `g2.back.with(g2.y, ...)`). The second button keeps its own
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
| A buzz from the robot | `g2.rumbleWhen(robot.launcher::isReady)` | a buzz each time it becomes true (`rumbleWhen(test, 2)`: two buzzes) |

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

- [Lesson 3: Buttons](../tutorials/start/03-buttons.md): the standard buttons each part brings.
- [The robot module: ControlMap](../modules/robot.md#controlmap): every method in one table.
- [Gamepad module](../modules/gamepad.md): the same jobs with `ControllerBindings`, without the five files.
