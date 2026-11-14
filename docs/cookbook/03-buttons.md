# Buttons and gestures

All button recipes go in `Controls.java`, inside `buttons(...)`. That method runs once, at INIT, and each
line plugs a job into a button. Every line ends with `;`. `.says("...")` is the text on the button map.

**Pick a free button.** Each part's usual buttons are lines in `Controls.java` too (see each part's
"buttons to add" in the [reference](../reference/robot-java.md)). For example, the claw is on operator **B**,
and the lift is on **A**, **X** and **Y**. When two jobs are on one button, INIT stops and names the
button. INIT also lists the free buttons
([move a button](#how-do-i-move-a-parts-button-to-another-button)).

**The questions on this page:**

- [How do I make a button do something once?](#how-do-i-make-a-button-do-something-once)
- [How do I run something only while a button is held?](#how-do-i-run-something-only-while-a-button-is-held)
- [How do I switch something on and off with one button?](#how-do-i-switch-something-on-and-off-with-one-button)
- [How do I put a tap, a hold and a double-tap on one button?](#how-do-i-put-a-tap-a-hold-and-a-double-tap-on-one-button)
- [How do I make a button safe ("hold for 1 second")?](#how-do-i-make-a-button-safe-hold-for-1-second)
- [How do I use two buttons together (like a shift key)?](#how-do-i-use-two-buttons-together-like-a-shift-key)
- [How do I make the gamepad buzz?](#how-do-i-make-the-gamepad-buzz)
- [How do I control a part with a stick?](#how-do-i-control-a-part-with-a-stick)
- [How do I move a part's button to another button?](#how-do-i-move-a-parts-button-to-another-button)
- [How do I put the launcher's fire button on another button?](#how-do-i-put-the-launchers-fire-button-on-another-button)
- [How do I let the parts add their usual buttons by themselves?](#how-do-i-let-the-parts-add-their-usual-buttons-by-themselves)
- [How do I move the library's own buttons (slow, field-centric, pause)?](#how-do-i-move-the-librarys-own-buttons-slow-field-centric-pause)
- [How do I change the double-tap and hold times?](#how-do-i-change-the-double-tap-and-hold-times)
- [How do I switch the alliance during TeleOp?](#how-do-i-switch-the-alliance-during-teleop)
- [Buttons that are already built in](#buttons-that-are-already-built-in)

## How do I make a button do something once?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-press -->
```java title="Controls.java"
// Once per press. "::" hands over the method; the button runs it later.
g2.y.press(robot.claw::open).says("open the claw");
// Several commands: "() ->" starts a small piece of code (a lambda).
g2.x.press(() -> robot.lift.goTo("low")).says("lift to low");
```

**Why:** `press` runs the job once per press, the moment the button goes down.
**More:** [Controls.java: add your own jobs](../five-files/03-controls-java.md#add-your-own-button), [Press types](../controls/02-press-types.md#every-press-type).

## How do I run something only while a button is held?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-hold -->
```java title="Controls.java"
// Runs every loop while held; the second job runs once when it is let go.
g1.a.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
```

**Why:** the second job stops the intake when you let go. Without it, the intake keeps running.
**More:** [Press types](../controls/02-press-types.md#every-press-type).

## How do I switch something on and off with one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-toggle -->
```java title="Controls.java"
// Each press switches the intake on or off. rumble(): 2 buzzes = on, 1 buzz = off.
g1.x.toggle(robot.intake).rumble().says("intake on/off");
```

**Why:** `toggle` remembers on or off for you. The buzz tells the driver which one it is, so the driver does not have to look.
**More:** [Press types](../controls/02-press-types.md#every-press-type).

## How do I put a tap, a hold and a double-tap on one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-gestures -->
```java title="Controls.java"
// Three gestures on one button: tap, hold, double-tap.
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

**Why:** one button can do several jobs, so the drivers need fewer buttons to remember.
**More:** [Controls: gestures](../controls/02-press-types.md#several-press-types-on-one-button).

## How do I make a button safe ("hold for 1 second")?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-held-for -->
```java title="Controls.java"
// Runs once, after the button is held for 1 second: a safe "are you sure?".
g2.dpadLeft.heldFor(1.0, robot.launcher::stop).says("hold 1 s: flywheel off");
```

**Why:** a quick bump of the button does nothing, so nobody starts a big job by accident.
**More:** [Controls: gestures](../controls/02-press-types.md#several-press-types-on-one-button).

## How do I use two buttons together (like a shift key)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-pair -->
```java title="Controls.java"
// Hold RB (the "shift key"), then press D-pad up: for a job that must not run by accident.
g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high")).says("lift to high");
```

**Why:** a pair is a safe place for a big job that you use only once or twice a match, like a climb. D-pad up alone still does its own job.
**More:** [Controls: two buttons together](../controls/02-press-types.md#two-buttons-together).

## How do I make the gamepad buzz?

**Where:** `Controls.java`, inside `buttons(...)`.

Add `.rumble()` after a job (see [the toggle recipe](#how-do-i-switch-something-on-and-off-with-one-button)).
Or make the gamepad buzz when the robot gets ready:

<!-- recipe: rumble-when -->
```java title="Controls.java"
// A buzz on gamepad 2 each time the flywheel gets to its speed.
g2.rumbleWhen(robot.launcher::isReady);
```

**Why:** the drivers watch the robot, not the Driver Station. A buzz tells them "ready", so they do not have to look away.
**More:** [Controls: feedback](../controls/03-see-and-feel.md#feedback-on-a-button).

## How do I control a part with a stick?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: stick-use -->
```java title="Controls.java"
// A stick is a number from -1 to 1 (up and right are +). Its value goes to the job every loop.
g2.rightStickY.use(robot.lift::manual).says("lift by hand");
```

**Why:** a stick gives a number every loop, so the part moves as far as you push the stick. When you let go, the part holds.
**More:** [Controls: sticks](../controls/02-press-types.md#every-press-type).

## How do I move a part's button to another button?

**Where:** `Controls.java`, inside `buttons(...)`.

A part's buttons are lines in `Controls.java`. For example, the intake's usual buttons are operator RT and
LT. To move them, change the button in their lines:

<!-- recipe: move-a-button -->
```java title="Controls.java"
// The intake's usual lines are on RT and LT: here the same jobs are on the bumpers.
g2.lb.hold(robot.intake::holdIn, robot.intake::stop).says("intake in (hold)");
g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
```

**Why:** a part's buttons are your own lines. Put its jobs on the buttons you want, and leave its usual buttons free.
**More:** [How buttons work: the buttons the parts bring](../controls/01-how-buttons-work.md#a-parts-buttons).

## How do I put the launcher's fire button on another button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: move-launcher-buttons -->
```java title="Controls.java"
robot.launcher.fireOn(g2.y);   // all of RB's firing moves to Y
robot.launcher.spinOn(g2.x);   // LB's spin up / down moves to X
```

**Why:** the fire button does more than one job. A tap fires one piece, quick taps line up more shots, a
hold keeps firing, and a tap then a hold sprays. `fireOn` moves all of these jobs in one line, and RB is
then free for something else. `fireOn(null)` leaves no fire button at all.
**More:** [Firing](../reference/parts/launcher.md#firing-shot-modes-feed-stop-piece-path).

## How do I let the parts add their usual buttons by themselves?

**Where:** `Controls.java`, inside the class, as a method of its own: below the `}` that ends
`buttons(...)`, above the class's last `}`.

<!-- recipe: parts-add-their-buttons -->
```java title="Controls.java"
// true: each part adds its usual buttons by itself. A line in buttons(...) on one of them replaces it.
@Override
protected boolean useStandardButtons() {
    return true;
}
```

**Why:** code written before v0.39.0 needs it. In that code, each part put its usual buttons on the
gamepads with no line in `Controls.java`. Since v0.39.0, a part has no buttons until you write its lines
(see each part's "buttons to add" in the [reference](../reference/robot-java.md)). So you can read every
button as a line.
**More:** [ControlMap](../reference/controls.md#controlmap).

## How do I move the library's own buttons (slow, field-centric, pause)?

**Where:** `Controls.java`, inside the class, as a method of its own: below the `}` that ends
`buttons(...)`, above the class's last `}`. At the top of the file, with the other imports:
`import com.crossbotics.ftchelper.teleop.LibraryButtons;`

<!-- recipe: move-library-buttons -->
```java title="Controls.java"
// The library's own buttons (driving, pause, menus...): move them here; null = no button.
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.x);                  // field-centric on/off: X instead of Y
    lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
    lib.switchAlliance(g1.start, g1.back);   // optional, off by default: hold START, then BACK, 1.5 s
}
```

**Why:** the old button is then free for your own jobs in `buttons(...)`. When two library jobs are on
one button, INIT stops and names the button. A file has only one `libraryButtons(...)` method, so put
every `lib.` line in it.
**More:** [Controls: move the library's buttons](../controls/04-move-library-buttons.md).

## How do I change the double-tap and hold times?

**Where:** `Tunables.java` (optional: leave the section out for the defaults).

<!-- recipe: tunables-buttons -->
```java title="Tunables.java"
/** Optional: slower gestures, e.g. for new drivers. Leave the section out for the defaults (in brackets). */
public static class Buttons {                                   // a fixed name: always Buttons
    public static double tapGapSec = 0.45;                      // most time between the taps of a double-tap (0.35)
    public static double holdSec = 0.4;                         // a press this long is a hold, not a tap (0.3)
}
```

**Why:** some drivers tap slower. These times are in the tuning menu too. When you tune a value there,
copy it back into `Tunables.java`, because the file is the record.
**More:** [Press types](../controls/02-press-types.md#several-press-types-on-one-button).

## How do I switch the alliance during TeleOp?

The drivers choose the alliance in INIT (gamepad 1: X = BLUE, B = RED). When they picked the wrong one
and the match has started, this switch fixes it. Turn it on first. It is off by default, so nobody
switches by accident.

**Where:** `Controls.java`, inside the class, as a method of its own (below the `}` that ends
`buttons(...)`). If you already have a `libraryButtons(...)` method, add only the `lib.switchAlliance` line to it.

<!-- recipe: switch-alliance -->
```java title="Controls.java"
// Controls.java, inside the class, next to buttons(...) (import
// com.crossbotics.ftchelper.teleop.LibraryButtons at the top). Off by default.
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    // Gamepad 1: hold START, then press and hold BACK too, for 1.5 s: BLUE <-> RED.
    lib.switchAlliance(g1.start, g1.back);
}
```

**Why:** the alliance sets the goal that the camera and the turret aim at, and the light's color. With
the wrong alliance, the robot aims at the other alliance's goal all match. Press START first, then BACK.
BACK alone is the pause, so BACK first only pauses the robot. The 1.5 s hold means a bump does nothing.
**More:** [Controls: move the library's buttons](../controls/04-move-library-buttons.md).

## Buttons that are already built in

- **The button map:** INIT shows every button and what it does. During the match, tap **GUIDE**.
- **The tuning menu:** hold **GUIDE** for 1 second (PRACTICE profile). A tap shows the map, a hold opens the menu.
  Copy each number you change there into `Tunables.java` ([Live tuning](../tuning/16-live-tuning.md)).
- **Two jobs on one button:** INIT stops and names the button, so you find the mistake before the match.
- **Pause:** press gamepad 1 **BACK** (the small button left of the center), and everything stops at once.
  Tap it again to go on.
- **Alliance:** choose it in INIT (gamepad 1: X = BLUE, B = RED). Switching during TeleOp is
  optional. [Turn it on](#how-do-i-switch-the-alliance-during-teleop). Then, on gamepad 1, hold the small
  **START** button, then press and hold **BACK** too, for 1.5 s. If you let go sooner, the alliance stays the
  same and the debug lines turn on or off.
- **Set pose here** (robots with odometry): put the robot on the reset spot, let go of the sticks, and hold gamepad 1 **START** for 1 s. This works in INIT or in the match ([how](11-odometry.md#how-do-i-set-the-pose-when-autonomous-did-not-run)).

**More:** [What the drivers see and feel: the button map](../controls/03-see-and-feel.md#the-button-map-and-the-teleops-init-screen).
