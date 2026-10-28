# Lesson 3: Buttons (Controls.java)

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**Goal:** know which buttons your robot already has, and give the buttons that are left your own jobs
in `Controls.java`.

**You need:** nothing at first: start in the simulator. Then your robot and gamepads 1 and 2.

**The code:** the lesson robot's [`Controls.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Controls.java)
(and the [`Actions.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Actions.java)
it uses). Every method: [the robot module, ControlMap](../../modules/robot.md#controlmap). Before this:
[Lesson 2: Drive](02-drive.md).

## 1. The whole TeleOp is this file

```java
@TeleOp(name = "Lesson Bot TeleOp", group = "Examples")
@Disabled
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.rb.press(Actions.scoreHigh(robot)).says("score high");     // lesson 5
        g2.b.toggle(robot.claw).says("claw").rumble();                 // 2 buzzes = closed, 1 = open
        g2.lb.tap(Actions.grabAndLift(robot)).says("grab and lift")    // a tap...
             .heldFor(1.0, () -> robot.lift.goTo("down")).says("lift all the way down");   // ...or held 1 s
    }
}
```

Its imports, at the top of the file:

```java
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;   // delete with @Disabled in your copy
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
```

Your own `Controls.java` from the Quick start already has them. A name in red? Alt+Enter (Option+Enter
on a Mac) on it adds the import.

`ControlMap<Robot>` is a whole TeleOp: driving, the match timer, every part updated each loop, and
everything stopped at the end.

**The loop:** a robot program runs a **loop**: many times a second, it reads the gamepads, runs the
button jobs and updates every part. Each time round is "a loop". The library runs this loop for you.
`buttons(...)` runs only **once**, at INIT: each line plugs a job into a button, and the loop runs the
job later, when the button is pressed. (Know Scratch or Blocks? [From Blocks to
Java](../../blocks-to-java.md) shows the same idea with blocks.) The `<Robot>` says which robot it runs: yours, from `Robot.java`.
`g1` is gamepad 1 (the driver), `g2` is gamepad 2 (the operator).

- `@TeleOp(name = ...)`: the name on the Driver Station.
- `@Disabled`: hides the example on a real robot. Delete it (and its import) in your copy, or the Driver Station will not list it.
- `@Override`: `buttons(...)` fills in a method the library class leaves to you; the library calls it
  to set up the buttons.

## 2. The buttons the parts bring

Each part in `Robot.java` brings its **standard buttons**. You write nothing for them. The lesson robot
gets this map (two drivers):

| Input | Gamepad 1 (driver) | Gamepad 2 (operator) |
| --- | --- | --- |
| Left stick | drive (and strafe) | lift by hand |
| Right stick X | turn | free |
| LT / RT | slow / free | intake out (hold) / intake in (hold) |
| A / X / Y | free / free / field-centric on/off | lift down / low / high |
| B | free | claw open / close |
| D-pad up / down | free | lift next height up / down |
| BACK | pause at the press / a tap: go on (held 1 s, field-centric only: also reset "forward") | free |
| START + BACK | hold START, then press BACK: debug lines (when let go) | free |
| GUIDE | tap: the button map; held 1 s: the tuning menu | free |

The lift's buttons come from its named heights in `Tunables.Lift` (`down = 0, low = 900, high = 2400`):
A is the lowest, Y the highest, X the second. Every part's buttons are in
[robot.md's standard map](../../modules/robot.md#controlmap). **Why:** most robots use the same buttons
for the same parts, so you only write what is different.

## 3. One driver or two

```java
public static boolean oneDriver = false;   // true: everything on gamepad 1
```

- `false`: two drivers, as in the table above.
- `true`: the operator's buttons move to gamepad 1. Slow becomes the left stick click (on/off) and
  field-centric the right stick click. A part whose button is taken by the driving gets none, and INIT
  says so, for example `Lift by hand: no button (G1 left stick Y is taken by library)`.
- Write it exactly like that: `public static boolean`. If it is not `static`, INIT stops with a message.

## 4. Add your own jobs

Inside `buttons(...)`, one line per job: the pad, the button, the kind of job.

`() -> robot.claw.open()` is a **lambda**: a small piece of code you hand over now, and the button runs
later. `robot.claw::open` is a shorter way to write the same thing (a **method reference**). A
`Runnable`, in the table below, is any such piece of code with no inputs.

| Kind | Example | Does |
| --- | --- | --- |
| `press(Runnable)` | `g2.dpadLeft.press(robot.claw::open)` | once per press |
| `press(Action)` | `g2.rb.press(Actions.scoreHigh(robot))` | starts the action; press again to cancel it |
| `hold(whileHeld)` | `g1.rt.hold(robot.intake::in)` | every loop while held |
| `hold(whileHeld, onRelease)` | `g1.rt.hold(robot.intake::in, robot.intake::stop)` | every loop while held, then once when let go |
| `release(Runnable)` | `g1.a.release(robot.claw::close)` | once when let go |
| `toggle(part)` | `g1.x.toggle(robot.intake)` | switches a part on/off (an intake, a claw, a launcher) |
| `toggle(onEnable, onDisable)` | `g2.dpadRight.toggle(robot.claw::open, robot.claw::close)` | each press flips between the two |
| `tap(a).doubleTap(b)` | `g2.rb.tap(robot.intake::toggle).doubleTap(robot.intake::out)` | a tap runs `a` (after waiting 0.35 s for a second tap); a double-tap runs only `b` |
| `tapOrDoubleTap(tap, doubleTap)` | `g2.rt.tapOrDoubleTap(robot.intake::toggle, robot.intake::out)` | the older way: a tap runs the first at once; a second tap within 0.35 s runs the second too |
| `slow()` | `g1.lt.slow()` | one driver only: slow driving (see below) |
| `tap`, `hold` (with other gestures on the same button: starts after 0.3 s), `doubleTap`, `tapThenHold`, `taps(n, ...)`, `heldFor(sec, ...)` | `g2.lb.tap(a).heldFor(1.0, b)` | several gestures on one button (section 5) |
| `with(other, job)` | `g2.lb.with(g2.y, job)` | a pair: hold LB, press Y |
| `rumble()` | `.toggle(robot.claw).rumble()` | a buzz when the job runs; on a toggle 2 = on, 1 = off |
| `free()` | `g2.dpadDown.free()` | no job, even if a part suggested one |
| `says(words)` | `.says("score high")` | the words on the button map |

Notes:

- **Which double-tap?** Use `tap(...).doubleTap(...)` when the tap must not run on a double-tap: the
  tap waits 0.35 s to be sure. Use `tapOrDoubleTap(tap, doubleTap)` when the tap should run at once and
  the two fit together: its first tap has already run `tap`, so tap = intake on/off, double-tap = push
  out. More: [the button menu](../../guides/buttons.md#which-double-tap).
- `slow()` works only with `oneDriver = true` (with two drivers, gamepad 1 LT is slow already, and
  INIT stops with a message). On a trigger, the further you pull, the slower, down to
  `Tunables.Drive.slowSpeed`. On a button, slow while it is held.
- A trigger counts as "pressed" past half way.
- Add `.says("...")` to every job. **Why:** the drivers read the map on the Driver Station, and
  "press" tells them nothing.

**Sticks** take `use(...)`: the stick's value every loop, from -1 to 1, up and right are +, with a small
dead zone and a gentle curve. To move the lift by hand on the right stick instead of the left:

```java
g2.rightStickY.use(robot.lift::manual).says("lift by hand");
g2.leftStickY.free();
```

A stick also takes `free()` and `says(...)`.

**Show a value on the Driver Station** with `show(words, value)`, a line of its own inside
`buttons(...)`. The library reads the value again every loop and shows it during
the match:

```java
show("Lift ticks", robot.lift::position);           // the lift's encoder count (a robot with a lift)
show("Intake on", robot.intake::isRunning);          // true or false
```

**Why:** you see what the robot "thinks" (a count, a yes/no) while you drive, without writing any
telemetry code. The value must be quick to read: a method reference like these, or a small lambda.

**A job must never wait.** `sleep(...)` or a `while` loop in a job compiles, but it **freezes the whole
robot**: the loop stops, so no driving, no other button, no part updates, until the job ends.

```java
g2.dpadLeft.press(() -> { robot.intake.out(); sleep(500); robot.intake.stop(); });   // WRONG
g2.dpadLeft.press(Actions.spitOut(robot)).says("spit out");                         // right: an Action waits safely
```

If a job takes too long, the Driver Station warns you: `... took 512 ms: the whole robot stopped that
long. No sleep() or while loop in a button job: use an Action with waitSeconds (lesson 5)`. To wait,
write an Action with `waitSeconds(...)` ([lesson 5](05-actions.md)).

## 5. One job per button (or several gestures)

Each button has ONE job. **Why:** a button that does two things depends on which runs first, and the
drivers cannot guess it.

The one exception is **gestures**: different ways of pressing the same button, each with its own job.
The lesson robot's LB: a tap grabs and lifts, holding it 1 s sends the lift all the way down.

```java
g2.lb.tap(Actions.grabAndLift(robot)).says("grab and lift")
     .heldFor(1.0, () -> robot.lift.goTo("down")).says("lift all the way down");
```

The library tells them apart for you: a tap is a press let go before the hold starts (0.3 s; with only
`heldFor`, before its time), a double-tap is two taps less than 0.35 s apart. Every gesture, pairs of buttons and buzzes: [the button menu](../../guides/buttons.md).

The rules, checked at INIT:

- A job of yours on a button a part suggested **replaces** the suggestion. The part then has no
  button for that job, and INIT says so, for example
  `Lift down: no button (G2 A is your "drop" in Controls.java)`. Use `free()` to clear a button
  without a new job.
- **A free button today may be a part's button tomorrow.** When a later lesson adds a part (a
  launcher in lesson 9 wants gamepad 2's LB and RB), your job on that button wins and the part gets
  none. After adding a part, read INIT's notes for `no button`.
- Two jobs of yours on one button stop at INIT: `Controls.java: G2 RB has two jobs. Give each button one
  job (put both commands in one lambda if you want both).`
- A job on one of the library's buttons (gamepad 1's left stick, right stick X, LT with two drivers, Y on
  a mecanum with an IMU, BACK, START, GUIDE) stops at INIT: `Controls.java: G1 LT is the library's "slow
  (squeeze: the further, the slower)" button. Pick another button, or move the library's job ...`.
  To use that button anyway, move the library's job first (section 7).

Want two things on one button? Put them in one lambda:
`g2.dpadLeft.press(() -> { robot.claw.open(); robot.intake.stop(); }).says("drop")`.

## 6. The button map: INIT and GUIDE

- **At INIT** the Driver Station shows the whole map, one line per input, with the part that gave it,
  for example `G2 A: down [Lift]` and `G2 RB: score high`, then the free buttons:
  `G2 free: ...`. Read it before every match.
- **During the match**, tap GUIDE: the map again, without the free buttons. The robot still drives.
  Tap GUIDE again to close it. (Holding GUIDE 1 s opens the tuning menu, PRACTICE profile only.)
- In TeleOp's INIT (before START), gamepad 1 X and B choose the alliance: X = BLUE, B = RED. After an
  autonomous, its alliance is already set.

## 7. Pause with BACK, and the other library buttons

Press gamepad 1 BACK and the robot pauses **at once**: every part stops on brake, the wheels hold
still, actions wait, and no button or stick job runs. The Driver Station says PAUSED. Tap BACK again
and every part goes on with what it was doing (a lift goes on to its height, the intake runs again).
**Why:** a safe stop for a jam or a dropped piece, without ending the program; it acts at the press,
so a driver who grabs BACK in a panic and holds it gets the stop. Holding BACK 1 s also resets
"forward" with field-centric on (lesson 2) and the robot stays paused; in robot-centric driving a held
BACK shows a note ("forward" is only reset in field-centric driving). Going on is always a tap.

**START + BACK: START first.** The debug lines (and the alliance switch below) use gamepad 1's small
START and BACK buttons, one after the other: hold START, then press BACK while you still hold START. If BACK goes down first, it is just a pause (it acts at the
press, and START joining later does not undo it): tap BACK to go on.

**Wrong alliance?** Choose it in INIT (gamepad 1 X = BLUE, B = RED, section 6). Switching during the
match is off unless you turn it on with one line, `lib.switchAlliance(g1.start, g1.back);` in
`libraryButtons` (below). Then holding START, then BACK, 1.5 s switches it (BLUE to RED or back), both
gamepads buzz twice and the Driver Station says `Alliance now RED (was BLUE)`. The camera's goal and
the aim follow at once. It takes 1.5 s on purpose, so it never happens by accident.

**Move the library's buttons.** Every library button above (drive sticks, slow, field-centric, pause,
debug, GUIDE's map and menu, the INIT alliance buttons) can move to another button or be freed, and the
alliance switch can be turned on. It is one more method in `Controls.java`: inside the class, after
the `}` that closes `buttons(...)`. The whole file, with it:

```java
package org.firstinspires.ftc.teamcode;

import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Quick Start TeleOp")
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.dpadLeft.press(Actions.spitOut(robot)).says("spit out");
    }

    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
        lib.fieldCentric(g1.x);                  // field-centric on X: Y is free for your own job now
        lib.slow(g1.rt);                         // slow on the right trigger
        lib.switchAlliance(g1.start, g1.back);   // optional: hold START, then BACK, 1.5 s to switch the alliance
    }
}
```

(Your `buttons(...)` keeps your own lines; the new parts are the `LibraryButtons` import and the
`libraryButtons` method.) INIT checks it like your own buttons:
two library jobs on one button stop with the button's name. A job you free is said at INIT too: with
`lib.pause(null)` INIT warns that nothing pauses the robot. Every job and how to move it:
[the button menu](../../guides/buttons.md#moving-the-librarys-buttons).

## 8. The lesson robot's two actions, and a buzz

The lesson robot uses gamepad 2's bumpers, which no part suggested:

- **RB, "score high":** lift up, open the claw at the top, lift down.
- **LB, "grab and lift"** (a tap): close the claw, then lift the piece a little. Held 1 s: the lift
  goes all the way down.

It also replaces the claw's own B button with the same toggle plus `.rumble()`: gamepad 2 buzzes twice
when the claw closes and once when it opens. **Why:** the operator feels it without looking away from
the robot.

An action runs a little every loop, so the robot keeps driving. Press the same button again to cancel
it. If the operator moves one of its parts by hand (say, the lift stick), the action stops: the driver
always wins. Lesson 5 shows how to write them.

## Try it in the simulator

From the `ftc-helper-lib` folder (skip the first line if you did it in lesson 2):

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

Open http://localhost:8780, pick "Lesson Bot TeleOp" and INIT: read the map. START. Use the on-screen
gamepad 2, or the keyboard (set gamepad 2's list to "Keyboard" first; A/B/X/Y are K L J I, LB/RB are Q E, LT/RT are Z C, GUIDE is M, BACK is
Backspace). Press RB and watch the lift rise. Press RB again while it moves: it cancels. Tap GUIDE.
Then add one job of your own to Controls.java, and one on a button that already has one, to see the
INIT message.

**Your own robot:** pick **Simulator** in the list next to Android Studio's green Run button, and click Run. Or, from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:run
```

Pick your own TeleOp, INIT, and read your map. Add a job and a `show(...)` line, stop the simulator
(the red Stop button, or **Ctrl+C**) and start it again: it builds your code when it starts, and check the button and the new line on the
Driver Station. ([Simulator guide](../../guides/simulator.md).)

## Check it on the robot

1. INIT: read the map. Is every job on the button you planned? Any notes about parts with no button?
2. START. Press each of your buttons once. Does each do what its words say?
3. Tap GUIDE: the map shows. Tap again: it closes.
4. Start an action, then press its button again: it stops, and the lift holds where it is.
5. Press BACK: everything stops at once (before you let go). Tap again: everything goes on.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| INIT stops: "has two jobs" | Two of your lines use one button. Pick another, or put both in one lambda. |
| INIT stops: "is the library's ... button" | That button is the library's (driving, pause, menus). Pick a free one (INIT lists them), or move the library's job with `libraryButtons` (section 7). |
| INIT stops: "libraryButtons: Two library features are on ..." | You moved a library feature onto a button that has one already. The message names the line to change: pick another button, or turn the other one off (`null`). |
| INIT stops: "slow() is for one driver" | With two drivers, G1 LT is slow already. Delete the line, or free the library's slow first (`lib.slow(null)`). |
| A part's button does nothing | Your job replaced its suggestion, or INIT says it has no button (one driver). |
| No button does anything | The robot is paused: tap BACK. |
| A button held at START does nothing | Let it go and press again: a button held through START does not count. |
| The help says "press" | Add `.says("...")` to that job. |
| Warning: `... took N ms: the whole robot stopped that long` | A job has `sleep(...)` or a `while` loop. Use an Action with `waitSeconds` (lesson 5). |

## Under the hood

How buttons work inside (presses, holds, toggles, rumble), built by hand:
[Tutorial 03: Buttons](../03-buttons.md).

## Next

[Lesson 4: An intake, a lift and a claw](04-lift-and-claw.md)
