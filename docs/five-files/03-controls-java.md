# Controls.java: the buttons, and the TeleOp

**The five files, page 3 of 5.**

`Controls.java` is your TeleOp: the program the drivers pick on the Driver Hub. It says what each button
does. The library's own buttons (driving, pause, the button map) are always pre-assigned. Each part's
buttons are lines in this file, so you can read and change every one.

## The smallest Controls.java

This is a whole `Controls.java`. The robot has no parts yet, so it has no buttons of its own: it drives on
the library's buttons, which are written out so you can see each one, with a line for every free button. Click a **(+)** to read what a line does.

<!-- recipe: first-controls -->
```java title="Controls.java"
import com.crossbotics.ftchelper.robot.ControlMap;
import com.crossbotics.ftchelper.robot.Pad;
import com.crossbotics.ftchelper.teleop.LibraryButtons;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/** The five files, file 3 of 5: the buttons, and the TeleOp. */
@TeleOp(name = "First Robot TeleOp") // (1)!
public class Controls extends ControlMap<Robot> { // (2)!
    public static boolean oneDriver = true; // (3)!

    // Always there: the library's buttons, on gamepad 1. These lines are the defaults (one driver):
    // without this method the buttons are the same. Change a line to move a button; null = no button.
    @Override
    protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) { // (4)!
        lib.swapSticks(false);                   // left stick: drive and slide; right stick X: turn
        lib.slow(null);                          // slow while a trigger is pulled: none (the triggers are free)
        lib.slowToggle(g1.leftStickButton);      // left stick click: slow on / off
        lib.fieldCentric(g1.rightStickButton);   // right stick click, a double tap: field-centric on / off
        lib.pause(g1.back);                      // BACK: pause at the press; a tap goes on (5)!
        lib.resetForward(g1.back);               // BACK held 1 s: "forward" is where the robot faces now
        lib.debug(g1.start, g1.back);            // hold START, then press BACK: the debug lines
        lib.setPose(g1.start);                   // START held 1 s: set pose here (robots with odometry)
        lib.buttonMap(g1.guide);                 // GUIDE: the button map on the Driver Hub
        lib.tuningMenu(g1.guide);                // GUIDE held 1 s: the tuning menu (PRACTICE profile)
        lib.allianceInInit(g1.x, g1.b);          // in INIT: X = BLUE, B = RED
    }

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) { // (6)!
        // Every other button is a line here: each part's buttons, then your own. This robot has no parts yet.
        // The free buttons: replace free() with a job, e.g. .press(...), .hold(...), .toggle(...), .tap(...). (7)!
        g1.a.free();
        g1.b.free();                              // B and X choose the alliance in INIT only: free in the match
        g1.x.free();
        g1.y.free();
        g1.lb.free();
        g1.rb.free();
        g1.lt.free();                             // a trigger counts as pressed past half way
        g1.rt.free();
        g1.dpadUp.free();
        g1.dpadDown.free();
        g1.dpadLeft.free();
        g1.dpadRight.free();
        g1.rightStickY.free();                    // a stick: .use(...) gets its value every loop
    }
}
```

1. The TeleOp's name in the Driver Hub's list. Change it to your robot's name.
2. `ControlMap<Robot>`: a TeleOp for the robot in your `Robot.java`. The library runs the loop, drives,
   and keeps every part updated.
3. `true`: one driver does everything on gamepad 1. `false`: two drivers, gamepad 1 drives and
   gamepad 2 runs the parts.
4. **Always there:** the library's own buttons, one line each. These lines are the defaults: without this
   method, the buttons are the same. Change a line to move a button; `null` = no button.
5. BACK pauses the robot at once: every motor stops. Keep it on a button: it is the drivers' safety stop.
6. **Every other button is a line here.** A part has no buttons until you add its lines: each part's
   usual lines are in the reference ([Intake: buttons to add](../modules/robot.md#intake-buttons-to-add),
   and the same for every part).
7. **Every free button has a line,** so you see what you can use. Replace `free()` with a job. When you add
   a part's lines, delete the `free()` lines of the buttons they use: one line per button.

**The first line of the file** (above the imports) must be:

```text
package org.firstinspires.ftc.teamcode;
```

## Drive it

With `Robot.java`, `Tunables.java` and this file, the robot drives.

1. **Build and install:** **Run** in Android Studio ([the buttons](../setup/06-build-and-install.md#the-buttons)).
2. Put the robot on a block, with its wheels off the floor.
3. On the Driver Hub, pick **First Robot TeleOp** and press **INIT**. The screen shows the button map.
4. Press **▶**. Push the left stick forward: all four wheels turn forward.
5. Take it off the block and drive. `Tunables.Drive.maxPower = 0.5` keeps it slow.

**In the simulator:** pick **Simulator** in the run list and click **Run**. Then pick the TeleOp on the
simulator's Driver Hub.

**Done when:** the robot drives forward, back, sideways (mecanum) and turns, the way the sticks say.

A wheel turns the wrong way? Fix it in `Robot.java`: [a wheel turns the wrong way](01-robot-java.md#a-wheel-turns-the-wrong-way).

## Two kinds of buttons

**Always pre-assigned: the library's own**, on gamepad 1. You write nothing for them, and they are there
in every TeleOp. **Why:** BACK must always pause the robot, even in a file with a mistake.

| Job | Two drivers | One driver (`oneDriver = true`) |
| --- | --- | --- |
| Drive and slide | left stick | left stick |
| Turn | right stick, left and right | right stick, left and right |
| Slow | LT (the further, the slower) | left stick click: slow on/off |
| Field-centric on/off (mecanum) | Y (a double tap) | right stick click (a double tap) |
| Pause / go on | BACK (a tap) | BACK (a tap) |
| Button map on the screen | GUIDE (a tap) | GUIDE (a tap) |
| Tuning menu | GUIDE (held 1 s) | GUIDE (held 1 s) |
| Choose the alliance (in INIT) | X = BLUE, B = RED | X = BLUE, B = RED |

To move one: [Moving the library's buttons](../guides/buttons.md#moving-the-librarys-buttons).

**Every other button is a line in `buttons(...)`.** A part has no buttons until you add its lines, so
every button the robot has is a line you can read in this file. Each part's usual lines, ready to copy:

| Part | Usual buttons | The lines |
| --- | --- | --- |
| Intake | RT: in (hold). LT: out (hold) | [Intake: buttons to add](../modules/robot.md#intake-buttons-to-add) |
| Launcher | LB: spin up / down. RB: fire one (tap), keep firing (hold). D-pad up and down: speed | [Launcher: buttons to add](../modules/robot.md#launcher-buttons-to-add) |
| Lift | A, X, Y: heights. D-pad up and down: next height. Left stick: by hand | [Lift: buttons to add](../modules/robot.md#lift-buttons-to-add) |
| Arm | right stick: by hand | [Arm: buttons to add](../modules/robot.md#arm-buttons-to-add) |
| Claw | B: open / close | [Claw: buttons to add](../modules/robot.md#claw-buttons-to-add) |
| Wrist | D-pad left and right: positions | [Wrist: buttons to add](../modules/robot.md#wrist-buttons-to-add) |
| Turret | right stick, left and right: by hand. BACK: follow the goal on / off | [Turret: buttons to add](../modules/robot.md#turret-buttons-to-add) |

**INIT tells you what is missing.** A part whose usual buttons are not in `Controls.java` gets a line at INIT,
for example `Intake: usual buttons not in Controls.java: G2 RT (in (hold)), G2 LT (out (hold))`. A button you
used for something else counts as your choice.

## A whole robot's buttons

The example [`allbuttons/Controls.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/allbuttons/Controls.java)
is a robot with a mecanum drive, an intake and a launcher, and two drivers: every library button in
`libraryButtons(...)`, the intake's and the launcher's usual lines, and a `free()` line for every free
button on both gamepads. A test checks that its lines are the parts' usual ones.

## Add your own button

Each button line has up to four pieces:

```java
//  1. which button   2. how it starts        3. what it does            4. what the drivers see
    g2.y              .press(                 robot.claw::open)          .says("open the claw");
```

For example, two buttons for a claw and a lift:

<!-- recipe: button-press -->
```java title="Controls.java"
// Once per press. "::" hands over the method; the button runs it later.
g2.y.press(robot.claw::open).says("open the claw");
// Several commands: "() ->" starts a small piece of code (a lambda).
g2.x.press(() -> robot.lift.goTo("low")).says("lift to low");
```

**Always add `.says(...)`.** The words go on the button map, so the drivers know the button.

### 1. Which button

| Buttons | Name in the code |
| --- | --- |
| Face buttons | `a`, `b`, `x`, `y` |
| Bumpers | `lb`, `rb` |
| Triggers (pressed past half way) | `lt`, `rt` |
| D-pad | `dpadUp`, `dpadDown`, `dpadLeft`, `dpadRight` |
| Stick clicks | `leftStickButton`, `rightStickButton` |
| Small buttons | `back`, `start`, `guide` |
| Sticks (a number from -1 to 1) | `leftStickX`, `leftStickY`, `rightStickX`, `rightStickY` |

### 2. How it starts: the press types

| Press type | Write | The job runs |
| --- | --- | --- |
| Press | `.press(job)` | once, the moment it is pressed |
| Let go | `.release(job)` | once, the moment it is let go |
| Hold | `.hold(job)` | every loop while it is held |
| Hold, then stop | `.hold(job, stopJob)` | every loop while held, then `stopJob` once when let go |
| On / off | `.toggle(part)` | each press switches the part on or off |
| On / off, two jobs | `.toggle(onJob, offJob)` | presses take turns |
| Tap | `.tap(job)` | once per quick press |
| Double tap | `.doubleTap(job)` | once, on the second quick tap |
| Several taps | `.taps(3, job)` | once, on the last of 2 to 5 quick taps |
| Tap, then hold | `.tapThenHold(job)` | a tap, then press again and hold: every loop while held |
| Held for a time | `.heldFor(1.0, job)` | once, after it is held 1 s: a safe "are you sure?" |
| Two buttons | `g2.lb.with(g2.y, job)` | once, when Y is pressed while LB is held |
| A stick | `g2.leftStickY.use(robot.lift::manual)` | every loop, with the stick's value |

**Gestures can share a button.** `tap`, `doubleTap`, `taps`, `tapThenHold`, `heldFor` and `hold` chain on
one button, each with its own job:

```java
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing");
```

`press`, `toggle` and `release` take a button for themselves. When each gesture's job runs, and the
two times you can change: [the button menu](../guides/buttons.md#2-how-it-starts).

### 3. What it does

| The job | Example |
| --- | --- |
| A part's command | `robot.claw::open` |
| A few commands | `() -> { robot.claw.open(); robot.intake.stop(); }` |
| An action: several steps, like "score" | `Actions.scoreHigh(robot)` (the next page: `Actions.java`) |
| A part to switch on and off | `robot.intake` (with `.toggle(...)`) |

**A job must never wait.** No `sleep(...)` and no `while` loop: it freezes the whole robot. To do steps
one after another, use an action.

### 4. What the drivers see and feel

| You want | Write |
| --- | --- |
| Words on the button map | `.says("open the claw")` |
| A buzz when the job runs | `.rumble()`, or `.rumble(2)` for two buzzes |
| A buzz when something becomes true | `g2.rumbleWhen(robot.launcher::isReady);` (a line of its own) |
| A value on the Driver Hub | `show("Lift", robot.lift::position);` (a line of its own) |

## Change a part's button

A part's buttons are your lines: change the button in its line. For example, the intake on the bumpers in
place of the triggers:

<!-- recipe: move-a-button -->
```java title="Controls.java"
// The intake's usual lines are on RT and LT: here the same jobs are on the bumpers.
g2.lb.hold(robot.intake::holdIn, robot.intake::stop).says("intake in (hold)");
g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
```

## Check it

**Build and install**, then INIT the TeleOp.

**Done when:** INIT shows your button map with your words, and each button does its job.

INIT stops with a message when a button has two jobs, or when your job is on one of the library's
buttons. The message names the button.

---

**Next:** [Actions.java](04-actions-java.md).
**Look it up:** [every press type and option](../guides/buttons.md). **Go deeper:**
[the cookbook's button recipes](../cookbook.md#buttons-and-gestures).
