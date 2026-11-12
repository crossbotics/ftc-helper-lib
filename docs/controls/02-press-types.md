# Press types

Every way a button can start its job, with one line of code each.

A **press type** says when the job runs: at the press, while the driver holds the button, on a double tap,
and so on. Last season's fire button took about 40 lines of timers and "was it down last loop?" flags. Here
it is one line per press type.

"Every loop" below means each time round the library's loop, many times a second.

## Every press type

**Where:** `Controls.java`, inside `buttons(...)`.

| You want | Write | When the job runs |
| --- | --- | --- |
| Press once | `g2.y.press(robot.claw::open)` | once, the moment the driver presses it |
| Let go | `g2.y.release(robot.claw::close)` | once, the moment the driver lets go |
| Hold | `g1.a.hold(robot.intake::in)` | every loop while the driver holds it. The intake still runs after the release: add a stop job (next row) |
| Hold, then stop | `g1.a.hold(robot.intake::in, robot.intake::stop)` | every loop while held, then the stop job once at the release |
| On / off switch | `g1.x.toggle(robot.intake)` | each press switches the part (an intake, a launcher, a claw, a mode) |
| On / off, two jobs | `g2.b.toggle(robot.claw::close, robot.claw::open)` | presses take turns: on, off, on... |
| Tap (a quick press) | `g2.rb.tap(robot.launcher::fire)` | once per tap |
| Double tap | `g2.rb.doubleTap(() -> robot.launcher.fire(3))` | once, on the second quick tap |
| Several taps | `g2.x.taps(3, () -> robot.lift.goTo("high"))` (2 to 5) | once, on the last quick tap |
| Tap, then hold | `g2.lb.tapThenHold(robot.intake::out, robot.intake::stop)` | a tap, then press again and hold: every loop while held |
| Held for a time | `g2.dpadLeft.heldFor(1.0, robot.launcher::stop)` | once, after the driver holds it 1 s: a safe "are you sure?" |
| Two buttons together | `g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high"))` | once, when the driver presses Y while holding LB |
| A stick | `g2.rightStickY.use(robot.lift::manual)` | every loop, with the stick's value (-1 to 1) |

The method names (`open`, `in`, `fire`...) depend on the part. Each part's page lists its commands
([part pages](../reference/README.md)).

## Several press types on one button

A **gesture** is a way of pressing: a tap, a hold, two taps. One button can have several gestures, chained
with dots, each with its own job. `tap`, `doubleTap`, `taps`, `tapThenHold`, `heldFor` and `hold` can share
a button. `press`, `release` and `toggle` each need a button of their own.

<!-- recipe: button-gestures -->
```java title="Controls.java"
// Three gestures on one button: tap, hold, double-tap.
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

`.says(...)` and `.rumble()` belong to the gesture right before them.

**One line puts the launcher's firing on a button.** `robot.launcher.fireOn(g2.y)` puts all of its firing
(taps, hold, tap then hold) on Y. `spinOn(...)` puts its spin up / down on a button
([cookbook](../cookbook.md#how-do-i-put-the-launchers-fire-button-on-another-button)).

## When a gesture's job runs

A job runs at once, unless the same button has a longer gesture that starts the same way. Then the library
waits only until it knows which gesture it is:

- **A button with a hold:** a tap runs when the driver lets go. A tap is a press that ends before the hold
  starts (`Tunables.Buttons.holdSec`, 0.3 s). With `heldFor` and no hold, any press that ends before the
  `heldFor` time is a tap.
- **A button with a double tap** (or tap-then-hold, or taps): a tap waits 0.35 s for a second tap.
  **Why:** without the wait, every double tap would also run the tap job first.
- **A button with no double tap:** two quick taps run the tap job twice.
- **A button with a double tap (or taps):** a second press within 0.35 s counts as the second tap, not as a
  hold. To hold, wait a moment after the tap. Or use `tapThenHold` for "tap, then hold".
- **`heldFor` and `hold` on one button:** both run. The hold starts at 0.3 s, and `heldFor` runs once at its
  time.

### Change the double-tap time and the hold time

You can change two times:

- **The double-tap time** (`tapGapSec`, 0.35 s): the most time between the two taps of a double tap.
- **The hold time** (`holdSec`, 0.3 s): a press this long is a hold, not a tap.

Without a `Buttons` section in `Tunables.java`, the library uses these defaults. Add the section only to
change them. Then they are in the tuning menu too. When you tune a
value there, copy it back into `Tunables.java`: the file is the record.

**Where:** `Tunables.java`, inside `public class Tunables { ... }`.

```java title="Tunables.java"
public static class Buttons {
    public static double tapGapSec = 0.35;   // the most time between the taps of a double tap
    public static double holdSec = 0.3;      // a press this long is a hold, not a tap
}
```

The library's field-centric double tap always uses 0.35 s. `tapGapSec` does not change it.

## Tap and double tap: two ways

| Write | A tap | A double tap | Use it when |
| --- | --- | --- | --- |
| `.tap(a).doubleTap(b)` | runs `a`, 0.35 s after the driver lets go (it waits to be sure no second tap comes) | runs only `b` | `a` must not run on a double tap |
| `.tapOrDoubleTap(a, b)` (the older way) | runs `a` at once | runs `a` (first tap), then `b` (second tap) | both fit together, like intake on/off (tap), then push out (double tap) |

`tapOrDoubleTap` always uses 0.35 s, not `Tunables.Buttons`. It is one job, so you cannot chain it with
other gestures.

## Two buttons together

`g1.rb.with(g1.dpadUp, job)` runs the job once, when the driver presses D-pad up while holding RB. **Why:** a pair is a
safe place for big jobs that the drivers use only once in a while, like a climb. These jobs must not run by
accident.

- The first button (`rb` here) is the **shift key**. It gets no job of its own.
- One shift key can start several pairs: `g1.rb.with(g1.dpadUp, ...)`, `g1.rb.with(g1.dpadDown, ...)`.
- The second button keeps its own job when the driver presses it alone. While the shift key is down, the
  second button's own job does not run, and a hold that it started stops.
- Neither button may be one of the library's buttons. Both must be on the same gamepad.
- `.free()` on a shift key removes its pairs too.

## While the robot is paused

A tap of gamepad 1 BACK pauses the robot. While it is paused, no button job runs, with one exception: a
hold's stop job (the second job in `hold(job, stopJob)`) still runs at the release. It also runs when the
pause starts in the middle of a hold. **Why:** a part that a hold started must stop, and it must stay
stopped when the pause ends.

Of the library's own buttons, field-centric on/off and slow on/off wait too. Pause, "forward", the menus, the
debug lines, set pose and the alliance switch still work.

## Done when

- Each button in your `Controls.java` uses the press type the drivers expect.
- In the simulator or on the robot, each gesture runs its own job. A tap does not also run the double tap's job.

---

**Next:** [What the drivers see and feel](03-see-and-feel.md).

**Look it up:** [Reading a button line](../reference/controls.md#reading-a-button-line). **Go deeper:**
[the cookbook's button recipes](../cookbook.md#buttons-and-gestures).
