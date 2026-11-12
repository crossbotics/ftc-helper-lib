# The button menu: every way a button can work

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** anyone writing `Controls.java` (the robot in five files).
**You need:** [Lesson 3: Buttons](../tutorials/start/03-buttons.md) first, so you know where these lines go.

You build every button line from up to four choices, like ordering from a menu:

```java
//  1. which button   2. how it starts        3. what it does            4. feedback
    g2.lb             .tap(                   robot.launcher::fire)      .says("fire one").rumble();
```

Pick one item from each part below. Parts 1 to 3 are needed. Part 4 is optional, but always add
`.says(...)`: the drivers read it on the button map.

Every line goes inside `buttons(Robot robot, Pad g1, Pad g2) { ... }` in `Controls.java`. The examples
use `robot.launcher`, for a robot with a launcher. With the Quick start robot, try `robot.intake`. Its
jobs are `in`, `out`, `stop` and `toggle`.

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
| Triggers (they count as pressed past half way) | `lt`, `rt` |
| D-pad | `dpadUp`, `dpadDown`, `dpadLeft`, `dpadRight` |
| Stick clicks (press the stick down) | `leftStickButton`, `rightStickButton` |
| Small buttons | `back`, `start`, `guide` (the middle button) |
| Sticks (a number, not a button: see the end) | `leftStickX`, `leftStickY`, `rightStickX`, `rightStickY` |

The library uses some of gamepad 1's buttons for its own jobs: the left stick, right stick X, BACK, START,
GUIDE, LT (slow, with two drivers) and Y (field-centric, with two drivers, on a mecanum with an IMU or
odometry). With `oneDriver = true`, the left stick click (slow on/off) and the right stick click
(field-centric on/off) take the place of LT and Y. Field-centric on/off needs a **double tap**: two presses
within 0.35 s. This time is fixed, and `Buttons.tapGapSec` does not change it. The reason: one tap by
accident would turn the sticks around. When you use one of these buttons, INIT stops with a message. You
can move or free every one of them: [Moving the library's buttons](#moving-the-librarys-buttons).

## 2. How it starts

"Every loop" below means each time round the library's **loop**. The loop runs many times a second. Each
time round, it reads the gamepads, runs the jobs that are due and updates every part. You never write the
loop. `buttons(...)` runs once, at INIT, and only plugs the jobs into the buttons.

### One job on the button

| You want | Write | When the job runs |
| --- | --- | --- |
| Press once | `.press(job)` | once, the moment you press it |
| Let go | `.release(job)` | once, the moment you let go |
| Hold | `.hold(job)` | every loop while you hold it |
| Hold, then stop | `.hold(job, stopJob)` | every loop while you hold it, then `stopJob` once when you let go |
| On / off switch | `.toggle(part)` | each press switches the part (an intake, a launcher, a claw, a mode) |
| On / off with two jobs | `.toggle(onJob, offJob)` | presses take turns: on, off, on... |

### Several gestures on one button

A **gesture** is a way of pressing: a tap, a hold, two taps... One button can have several gestures,
chained with dots. Last season's robot fired one ball on a tap and kept firing on a hold. On a tap, then
hold, it sprayed: it fired without waiting for speed.

| Gesture | Write | When the job runs |
| --- | --- | --- |
| Tap (a quick press) | `.tap(job)` | once per tap |
| Hold | `.hold(job)` or `.hold(job, stopJob)` | every loop while you hold it (with other gestures: after 0.3 s) |
| Double-tap | `.doubleTap(job)` | once, on the second quick tap |
| Tap, then hold | `.tapThenHold(job)` or `.tapThenHold(job, stopJob)` | after a quick tap, press again and hold: every loop while you hold it |
| Several taps | `.taps(3, job)` (2 to 5) | once, on the last quick tap |
| Held for a time | `.heldFor(1.0, job)` | once, after you hold it 1 s (a safe "are you sure?"). With a `hold` on the same button, both run: the hold from 0.3 s, and `heldFor` once at its time |

Example, the fire button:

```java
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

One line can also put the launcher's firing on a button. `robot.launcher.fireOn(g2.y)` puts all of its
firing (taps, hold, tap then hold) on Y. `spinOn(...)` puts its spin up / down on a button
([cookbook](../cookbook.md#how-do-i-put-the-launchers-fire-button-on-another-button)).

Taps on `fire` (and on the launcher's own RB) add up. At most `maxFiringQueue` shots wait (Tunables,
default 4), so many nervous taps do not fire many pieces later
([Tunables](../reference/parts/launcher.md#in-tunablesjava)). With `maxFiringQueue = 0` there is no queue: a
press while a shot is still on its way does nothing. The launcher's LB (or the button you move its on/off to)
switches the wheel off. A fire press then spins the wheel up, fires, and switches it off again.

**When a gesture's job runs:** at once, unless the same button has a longer gesture that starts the same
way. Then the library waits only until it knows which gesture it is:

- On a button that also has a hold, a tap runs when you let go. A tap is a press that you let go before
  the hold starts (`Tunables.Buttons.holdSec`, 0.3 s). With `heldFor` and no hold, any press that you let go
  before the `heldFor` time is a tap.
- On a button that also has a double-tap (or tap-then-hold, or taps), a tap waits 0.35 s for a second
  tap. **Why:** without the wait, every double-tap would also run the tap job first.
- On a button with no double-tap job, two quick taps run the tap job twice.
- On a button with a double-tap (or taps), a second press within 0.35 s of a tap counts as the second
  tap, not as a hold. To hold, wait a moment after the tap. Or give the button `tapThenHold` for "tap, then hold".

The same thing on the Quick start robot's intake:

```java
g2.rb.tap(robot.intake::toggle).says("intake on/off")
     .doubleTap(robot.intake::out).says("push out");
```

**The two times are optional.** When you leave `Tunables.Buttons` out, the library uses the defaults below.
Add the section to `Tunables.java` only to change them. Then they are in the tuning menu too. When you tune a
value there, copy it back into `Tunables.java`: the file is the record.

```java
public static class Buttons {
    public static double tapGapSec = 0.35;   // the most time between the taps of a double-tap
    public static double holdSec = 0.3;      // a press this long is a hold, not a tap
}
```

### Which double-tap?

You can put a tap and a double-tap on one button in two ways:

| Write | A tap | A double-tap | Use it when |
| --- | --- | --- | --- |
| `.tap(a).doubleTap(b)` | runs `a`, 0.35 s after you let go (it waits to be sure no second tap comes) | runs only `b` | `a` must not run on a double-tap |
| `.tapOrDoubleTap(a, b)` (the older way) | runs `a` at once | runs `a` (first tap), then `b` (second tap) | both fit together, like intake on/off (tap), then push out (double-tap) |

`tapOrDoubleTap` always uses 0.35 s, not `Tunables.Buttons`. It is one job, so you cannot chain it with
other gestures.

### Two buttons together

| You want | Write | When the job runs |
| --- | --- | --- |
| A pair: hold one, press another | `g2.lb.with(g2.y, job).says("climb")` | once, when you press Y while you hold LB |

The first button (`lb` here) is the **shift key**: it gets no job of its own. One shift key can start
several pairs (`g2.lb.with(g2.x, ...)`, `g2.lb.with(g2.y, ...)`). The second button keeps its own job
when you press it alone. While the shift key is down, the second button's own job does not run, and a hold
that it started stops. Then the second button does nothing more until you let it go. Neither button may be
one of the library's buttons. **Why:** a pair is a safe place for big jobs that you use only once in a
while, like a climb. These jobs must not run by accident.

## 3. What it does

The job is any of these:

| Kind | Example | Notes |
| --- | --- | --- |
| A method of a part | `robot.claw::open` | `::` hands over the method, and the button runs it later |
| A few commands | `() -> { robot.claw.open(); robot.intake.stop(); }` | `() ->` starts a small piece of code (a **lambda**) |
| An action (several steps) | `Actions.scoreHigh(robot)` | for `press`, `tap`, `doubleTap`, `taps`, `heldFor`, `with`: the gesture starts it, and the same gesture again cancels it ([lesson 5](../tutorials/start/05-actions.md)) |
| A part to switch | `robot.intake` | for `toggle(...)` only |

**A job must never wait.** Put no `sleep(...)` and no `while` loop in a job. The code compiles, but it
freezes the **whole robot** until the job ends: no driving, no other button, no part updates. The Driver
Station then warns: `... took 512 ms: the whole robot stopped that long. No sleep() or while loop in a
button job: use an Action with waitSeconds (lesson 5)`. To wait between steps, make an action with
`waitSeconds(...)` or `waitUntil(...)` ([lesson 5](../tutorials/start/05-actions.md)), and put the action
on the button.

A tap of gamepad 1 BACK pauses the robot. While the robot is paused, no button job runs, with one
exception. A hold's stop job (the second job in `hold(job, stopJob)`) still runs when you let the button
go, or when the pause starts in the middle of a gesture's hold. **Why:** so the library tells a part that a
hold started to stop, and the part stays stopped when the pause ends. Of the library's own buttons,
field-centric on/off and slow on/off wait too. Pause, "forward", the menus, the debug lines, set pose and
the alliance switch still work.

## 4. Feedback

| You want | Write | What the driver gets |
| --- | --- | --- |
| Words on the button map | `.says("fire one")` | the line `G2 RB: fire one` at INIT and when you tap GUIDE |
| A buzz when the job runs | `.rumble()` | one short buzz (a hold: when it starts) |
| More buzzes | `.rumble(2)` (1 to 3) | that many short buzzes |
| On / off you can feel | `.toggle(part).rumble()` | two buzzes = on, one buzz = off (a claw: two = closed) |
| A buzz from the robot | `g2.rumbleWhen(robot.launcher::isReady);` | a buzz each time the test becomes true (`rumbleWhen(test, 2)`: two buzzes) |
| A color on the robot | `robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);` | a light the drivers see while the test is true ([Lights](../reference/parts/lights.md)) |

`rumbleWhen` is not tied to a button. It is a line of its own inside `buttons(...)`, like the others. It
ends with `;`, nothing chains after it, and it is not on the button map. With the Quick start robot,
`g2.rumbleWhen(robot.intake::isRunning);` buzzes when the intake starts.

**Show a value on the Driver Station:** `show("Lift ticks", robot.lift::position);` (or
`show("Intake on", robot.intake::isRunning);` with the Quick start robot). Like `rumbleWhen`, it is a line
of its own inside `buttons(...)`, not tied to a button. The library reads the value again every loop and
shows it during the match. **Why:** you see what the robot "thinks" while you drive. The value must be
fast to read: use a method reference or a small lambda.

`.says(...)` and `.rumble()` belong to the gesture right before them.
`g2.rb.tap(a).says("one").doubleTap(b).says("three").rumble()` buzzes on the double-tap only.

**Why a buzz:** the drivers watch the robot, not the Driver Station. A buzz tells them "done" or "ready",
and they do not look away. In the simulator, the gamepad picture shakes, and a real controller or a phone
buzzes.

## Sticks

A stick is a number from -1 to 1 (up and right are +), not a button:

| You want | Write |
| --- | --- |
| Its value every loop | `g2.leftStickY.use(robot.lift::manual).says("lift by hand")` |
| No job (it stays free) | `g2.rightStickX.free()` |

## Moving the library's buttons

The library's own jobs (driving, pause, menus...) have default buttons. You can move every one to another
button, or free it (`null`: no button). Do it in one method next to `buttons(...)` in `Controls.java`:

```java
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.a);                  // field-centric on/off: A instead of Y
    lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
    lib.pause(g1.x);                         // pause on X (BACK still resets forward; lib.resetForward(null) frees it)
    lib.switchAlliance(g1.start, g1.back);   // turn ON the alliance switch: hold START, then BACK, 1.5 s
}
```

Add `import com.crossbotics.ftchelper.teleop.LibraryButtons;` at the top. In your own `EasyTeleOp`, write
`libraryButtons(LibraryButtons lib)`, with `Key.g1(Btn.X)` instead of `g1.x`.

| Job | Default (two drivers) | One driver | How it works | Move it with |
| --- | --- | --- | --- | --- |
| Drive / strafe, turn | left stick, right stick X | same | every loop | `lib.swapSticks(true)`: right stick drives, left stick X turns |
| Slow | G1 LT | none | the further you pull it, the slower (a button: slow while you hold it) | `lib.slow(key)` |
| Slow on/off | none | G1 left stick click | a press | `lib.slowToggle(key)` |
| Field-centric on/off | G1 Y | G1 right stick click | a press (mecanum with an IMU or odometry) | `lib.fieldCentric(key)` |
| Pause / go on | G1 BACK | same | pauses at the press, and a tap goes on | `lib.pause(key)` |
| Reset "forward" | G1 BACK | same | held 1 s, field-centric only (a tap if its button has no other quick job) | `lib.resetForward(key)` |
| Debug lines | G1 START + BACK | same | hold START, then press BACK. It runs when you let go | `lib.debug(key, with)` (`with` null: one button) |
| Switch alliance | **off** | off | START, then BACK, both held 1.5 s, after you turn it on | `lib.switchAlliance(g1.start, g1.back)` turns it on |
| Set pose here | G1 START | same | held 1 s with the drive sticks let go (robots with odometry). It also works in INIT | `lib.setPose(key)` |
| Button map | G1 GUIDE | same | a tap | `lib.buttonMap(key)` |
| Tuning menu | G1 GUIDE | same | held 1 s (a tap if its button has no other quick job) | `lib.tuningMenu(key)` |
| Choose the alliance (INIT only) | G1 X = BLUE, G1 B = RED | same | a press, before ▶ (start on the Driver Hub) | `lib.allianceInInit(blue, red)` |

**BACK, step by step.** A press of BACK pauses the robot **at once**: every motor stops on brake. So a
driver who grabs BACK in a panic gets the stop. When you let go fast, the robot stays paused, and a second
tap goes on. When you hold BACK 1 s, it also resets "forward" (only with field-centric on; robot-centric
shows a note instead). The robot then stays paused until the next tap. Going on is always a tap, at the
release, so holding BACK never starts the robot.

**START + BACK: press START first.** Hold START, then press BACK: when you let go, you get the debug lines.
Held 1.5 s, the pair switches the alliance, if the switch is on. The robot does not pause. **BACK first is
always a plain pause.** BACK stops the robot the moment it goes down. When START joins a moment later, it
does nothing, so the robot stays paused (a tap of BACK goes on). The reason: the pause has to act at the
press. A pair that came later would have to un-pause the robot. Then the robot would stop for a moment and
drive on, and that is a surprise. While the pair is down, START's "set pose" does nothing. When you press
both in the same instant, it counts as START first. This applies only to pairs with the pause button in
them. Any other pair works in either order.

**START + A or START + B** pairs a gamepad on the Driver Station. The library cancels START's own job
("set pose here") for that press, so pairing a gamepad never moves the pose.

**Set pose here** works only with the drive sticks let go and whole-robot aim off. Otherwise the note says
"Pose NOT set: stop first". It also works in INIT: hold START 1 s before the match, so the pose is set
before the buzzer.

### Switching the alliance in TeleOp

The switch is off until you turn it on. Without it, you choose the alliance in INIT with X = BLUE,
B = RED. Turn it on with one line in `libraryButtons(...)`:

```java
lib.switchAlliance(g1.start, g1.back);   // hold START, then BACK, 1.5 s: BLUE <-> RED
```

A fast press of both (START first) still gives the debug lines. Use the switch when autonomous ran as the
wrong alliance. When the alliance switches, the goal flips, and so does the odometry's pose:

- On a mirrored field (the reset spot's `mirrorForRed` true), the library mirrors the pose in place: x and
  the heading flip.
- On a field turned half a turn (`mirrorForRed = false`, BIOBUZZ), the library cannot fix the pose. The
  odometry aim goes off ("Alliance now RED: odometry aim OFF until the pose is set"). It stays off until you
  park on the reset spot and hold START 1 s ("set pose here").

INIT's `Switch alliance` line says whether the switch is on.

**INIT names the jobs you freed.** No pause button gives a warning: nothing on the gamepads pauses the
robot. No slow at all gives a warning in `Controls.java`. No field-centric button gives a line: driving
stays as it starts. With one driver, a library job that you put on `g2` goes on gamepad 1.

**INIT checks these** (the message names the button):

- two library jobs on one button. One quick job and one held job may share a button, like BACK's pause and
  reset.
- the slow trigger with another job.
- a pair on two gamepads.
- the tuning menu on a button that the menu itself uses (D-pad, A, B, X, Y, LB, RB).
- a job of yours on a library button.

**Why:** some drivers want slow on the right trigger, or need Y for a part. When you move the library's job,
the button is free for `buttons(...)`.

## The rules (checked at INIT)

- One job per button, or several gestures. A button with `press(...)` and `tap(...)` stops INIT:
  `Controls.java: G2 B has two jobs`. The same gesture twice stops INIT too: `G2 A has two "tap" jobs`.
- A part has no buttons until `Controls.java` has its lines (each part's "buttons to add" in the
  [reference](../reference/robot-java.md)). INIT names each usual button of a part that is still free:
  `Intake: usual buttons not in Controls.java: G2 RT (in (hold)), ...`. `.free()` keeps a button empty.
- A shift key has no job of its own. `.free()` on a shift key removes its pairs too.
- When a button is down as you press ▶ (or as the tuning menu closes), it does nothing until you let it go.

## Before this library: how it was done

To make the same gestures by hand, you need timers and "was it down last loop?" flags for each button. Last
season's fire button (tap, hold, tap then hold) took about 40 lines in the TeleOp loop. Here it is one
line per gesture, and the button map lists every gesture.

## See also

- [Cookbook: buttons and gestures](../cookbook.md#buttons-and-gestures): one question, a few tested lines, per job.

- [Lesson 3: Buttons](../tutorials/start/03-buttons.md): each part's usual buttons, written out.
- [The robot module: ControlMap](../reference/controls.md#controlmap): every method in one table.
- [Gamepad module](../modules/gamepad.md): the same jobs with `ControllerBindings`, without the five files.
