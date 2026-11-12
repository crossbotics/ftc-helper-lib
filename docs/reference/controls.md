# Controls.java

Every button line, press type and gamepad name; the library's own buttons; one or two drivers.
`Controls.java` is the TeleOp ([`EasyTeleOp`](../modules/teleop.md) underneath). Learn it step by step:
[Controls.java](../five-files/03-controls-java.md). [The button menu](../guides/buttons.md) has every option
as a menu.

## ControlMap

```java
@TeleOp(name = "TeleOp")
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;   // true: everything on gamepad 1

    @Override protected void buttons(Robot robot, Pad g1, Pad g2) {
        g2.y.press(Actions.scoreHigh(robot)).says("score high");
    }
}
```

- The library's own buttons (driving, pause, menus) are always there.
- Every other button, a part's too, is a line in `buttons(...)` ([Buttons to add](#buttons-to-add-every-part)).
- A `Pad` has every input by name. Buttons: `a b x y lb rb lt rt back start guide dpadUp dpadDown
  dpadLeft dpadRight leftStickButton rightStickButton` (a trigger counts as pressed past half way).
  Sticks: `leftStickX leftStickY rightStickX rightStickY` (up and right +).

| In `Controls.java` | What it does |
| --- | --- |
| `buttons(Robot robot, Pad g1, Pad g2)` | your button lines |
| `useStandardButtons()` | override it to return true: each part then adds its usual buttons by itself, as before v0.39.0. The default is false, and INIT names a part's usual button that is still free |
| `libraryButtons(LibraryButtons lib, Pad g1, Pad g2)` | moves or frees the library's own buttons ([Moving the library's buttons](#moving-the-librarys-buttons)) |
| `robot()`, `oneDriver()` | reads the robot and the one-driver switch |

## Reading a button line

Every button line has the same four pieces:

```java
//  1. which button   2. how it starts                       3. what it does    4. its words
    g2.rt             .hold(robot.intake::holdIn,            robot.intake::stop) .says("in (hold)");
```

1. **Which button:** `g1` is gamepad 1 (the driver), `g2` is gamepad 2 (the operator), then the button:
   `rt` is the right trigger. With one driver (`oneDriver = true`), `g2` lines go on gamepad 1.
2. **How it starts**, and 3. **what it does:**

   | Written | The job runs |
   | --- | --- |
   | `.press(job)` | once, when the button goes down |
   | `.hold(job, stopJob)` | `job` every loop while the button is held, then `stopJob` once when it is let go |
   | `.toggle(part)` | each press switches the part: on, off, on... A claw: open, closed. A launcher: spinning, stopped. With `.rumble()`: two buzzes = on, one = off |
   | `.use(job)` | a stick: `job` gets the stick's value (-1 to 1) every loop |
   | `.heldFor(1.0, job)` | once, after the button is held 1 s (a safe "are you sure?") |
   | `.tap(job)`, `.doubleTap(job)` | once per quick tap; once on two quick taps. Chain them on one button |

   `robot.intake::stop` hands over the intake's `stop` command, for the button to run later.
   `() -> robot.lift.goTo("high")` is a small piece of code with its own words in it (a **lambda**).
4. **`.says("...")`:** the words on the button map. INIT shows the map (every button and its words), and a
   tap of GUIDE shows it during the match. Without `.says`, the map shows only the press type, like
   `G2 RT: hold`, so the drivers cannot tell what it does.

[The button menu](../guides/buttons.md) has every press type.

## Buttons to add (every part)

A part has **no buttons** until you add its lines to `buttons(...)` in `Controls.java`. Each part's page
has its usual lines under **Buttons to add**. Copy them as they are, or change a button or the words.

- `g2` is gamepad 2, the operator's. The driver on gamepad 1 drives, and the operator runs the parts.
- **To use another button:** change `g2.rt` to, for example, `g2.lb`.
- **With one driver:** set `oneDriver = true` in `Controls.java`. Then the `g2` lines go on gamepad 1. You do
  not change them. A line on a button gamepad 1 already uses (its sticks, the stick clicks, BACK) stops
  INIT, and INIT names the button: give the line a free one.
- **On gamepad 1, with two drivers:** write `g1` and a free button, for example `g1.rb`. Gamepad 1's LT is slow
  mode, so do not use it. If you pick a button the library uses, INIT stops and names it.
- Each line has: which button, how it starts (`.hold`, `.toggle`, `.press`...), what it does, and
  `.says("...")` ([Reading a button line](#reading-a-button-line)).

## Every job on a button

| On a button | What it does |
| --- | --- |
| `press(Runnable)` | once per press |
| `press(Action)` | starts it; a second press cancels it |
| `release(Runnable)` | once when the driver lets go |
| `hold(whileHeld)`, `hold(whileHeld, onRelease)` | every loop while held (and once on release) |
| `toggle(Toggleable)` | switches a part on/off (an intake, a launcher, a claw, a mode: a `Toggleable`, anything one button switches on and off) |
| `toggle(onEnable, onDisable)` | each press flips between the two |
| `tapOrDoubleTap(tap, doubleTap)` | the "no waiting" double-tap: a tap runs `tap` at once; a second tap within 0.35 s (`Pad.DOUBLE_TAP_SEC`) runs `doubleTap` after it (the first tap has already run `tap`: e.g. tap = intake on/off, double-tap = push out). For a tap that must not run on a double-tap, use `tap(...).doubleTap(...)` (the tap waits 0.35 s) |
| `tap(job)`, `doubleTap(job)`, `taps(n, job)`, `heldFor(sec, job)` | **gestures**, several on one button: once per quick tap; on the second quick tap; on the n-th (2 to 5); once after held `sec` s. Each also takes an `Action` |
| `hold(...)` with other gestures | every loop while held, from 0.3 s on (`Tunables.Buttons.holdSec`), so a quick tap stays a tap |
| `tapThenHold(whileHeld)`, `tapThenHold(whileHeld, onRelease)` | a quick tap, then press again and hold: every loop while held |
| `with(other, job)` | a pair: hold this button (the shift key, no job of its own) and press `other` (same gamepad) to run `job` once. Pressed alone, `other` keeps its own job |
| `rumble()`, `rumble(n)` | feedback: buzzes when the job (or the gesture just before) runs; a toggle buzzes 2 = on, 1 = off |
| `slow()` | one driver (or two after `lib.slow(null)`): on a trigger, drives slower the further it is pulled (down to `slowSpeed`); on a button, slow while held |
| `free()` | no job: the button stays free |
| `says(words)` | the words on the help page |
| `needs(part...)` | the job needs these parts: while one is not on the robot ([`optional()`](robot-java.md#parts-not-on-the-robot-yet), not in the configuration) the button does nothing and the map says `- off: IntakeDeploy not on the robot`. A part's own buttons and `toggle(part)` know their part already |
| on a pad: `g2.rumbleWhen(test)`, `rumbleWhen(test, n)` | feedback from the robot: a buzz each time `test` becomes true |
| on a pad: `g2.rumbleWhenAimed()` | buzzes while the aim is close, the closer the stronger ([Aim](parts/aim.md)) |
| on a stick: `use(DoubleConsumer)` | gets its value every loop (small dead zone, gentle curve) |
| on a stick: `free()`, `says(words)`, `needs(part...)` | as on a button |
| in `buttons(...)`: `show(label, value)` | your own value on the Driver Station, read again every loop, e.g. `show("Lift ticks", robot.lift::position);` or `show("Ready", () -> robot.launcher.isReady());`. The Driver Station hides it while the tuning menu or the button map is open. `value` must not wait. An error in it shows as `error: ...` and does not stop the OpMode |

- **While the robot is paused** (BACK), no button or stick job runs, with one exception: a hold's stop job
  (the second job of `hold(job, stopJob)`) still runs when the button is let go, or when the pause starts
  during a gesture's hold. So the part stays stopped.
- **A gesture's job runs at once,** unless the button has a longer gesture that starts the same way. Then
  it waits until it knows which one the driver means (the driver lets go, or `Tunables.Buttons.tapGapSec`,
  0.35 s, passes).
- Both times are in `Tunables.Buttons` (optional: leave it out for the defaults;
  [Buttons in Tunables.java](#buttons-in-tunablesjava)) and the tuning menu. Copy a tuned value back into
  `Tunables.java`.

## Moving the library's buttons

Every library job can go on another button, or none (`null`):

```java
@Override protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.x).slow(g1.rt);       // Y and LT are free for buttons(...)
    lib.setPose(g1.dpadDown);                 // hold D-pad down 1 s: set pose here
    lib.switchAlliance(g1.start, g1.back);    // optional: turn on the alliance switch in TeleOp
}
```

| [`LibraryButtons`](../modules/teleop.md#moving-the-librarys-buttons) | Default |
| --- | --- |
| `pause(key)`, `resetForward(key)` | G1 BACK (a press pauses at once, a tap goes on; held 1 s also resets "forward", field-centric only) |
| `debug(key, with)` | G1 START + BACK (hold START, then press BACK: debug lines, when let go; BACK first is only a pause) |
| `switchAlliance(key, with)` | **off**; `switchAlliance(g1.start, g1.back)` turns it on (START, then BACK, both held 1.5 s) |
| `setPose(key)` | G1 START held 1 s, drive sticks let go, in INIT or the match (robots with odometry: [`resetPose()`](robot-java.md#robotdefinition)); START + A/B (pairing a gamepad) cancels it |
| `slow(key)`, `slowToggle(key)` | G1 LT (one driver: left stick click, on/off) |
| `fieldCentric(key)` | G1 Y (one driver: right stick click) |
| `buttonMap(key)`, `tuningMenu(key)` | G1 GUIDE (a tap: the map; held 1 s: the tuning menu) |
| `allianceInInit(blue, red)` | G1 X = BLUE, G1 B = RED (INIT only) |
| `swapSticks(true)` | false (the left stick drives, the right stick X turns) |

The whole table, with how to move each: [the button menu](../guides/buttons.md#moving-the-librarys-buttons).

**INIT names freed library jobs:** no pause button (a warning), no slow at all (a warning), no
field-centric button (a line).

## The standard map

This is the map for two drivers. With `oneDriver = true`, the operator's buttons move to gamepad 1.

| Input | Gamepad 1 (driver) | Gamepad 2 (operator) |
| --- | --- | --- |
| Left stick | drive (and strafe) | lift by hand (Y), or a hood by hand (Y): see below |
| Right stick | turn (X) | arm by hand (Y), turret by hand (X) |
| LT / RT | slow (the further, the slower) / free | intake out (hold) / intake in (hold, or on/off) |
| LB / RB | free | launcher spin up/down / fire one (hold: keep firing) |
| A / X / Y / B | free / free / double-tap: field-centric on/off (mecanum) / free | lift lowest / second / highest / claw |
| D-pad up/down | free | lift next height, or launcher speed |
| D-pad left/right | free | wrist |
| BACK | press: **pause** everything at once (stopped on brake), a tap again: go on; held 1 s (field-centric on): also "forward" = away from you now (with odometry: the pose stays, only the driver's "forward" changes), still paused | turret following on/off (with a turret; else free) |
| START (OPTIONS on a PS controller) | hold 1 s: **set pose here** (with odometry: the pose becomes `Tunables.Odometry`'s reset spot) | free |
| START + BACK | hold START, then press BACK: debug lines (when let go); with `lib.switchAlliance(g1.start, g1.back)`, both held 1.5 s: **switch the alliance**. BACK first is only a pause | - |
| GUIDE | tap: the button map, without the free buttons (during the match too); hold 1 s: the tuning menu (PRACTICE) | free |

**Two parts, one stick:** a lift and a launcher's hood both usually use gamepad 2's left stick Y. Two
lines on one stick stop INIT ("two jobs"). Give one of them another stick, e.g.
`g2.rightStickY.use(robot.lift::manual).says("lift by hand");`.

**What INIT checks:** when a module's button is taken, the module gets none, and INIT notes it. Two jobs on one button in
Controls.java, or a job on one of the library's buttons, stop INIT with the button's name. INIT shows the
whole map and the free buttons.

## One driver

- The left stick click switches slow on/off, and the right stick click switches field-centric.
- To put slow on a trigger instead, give it `.slow()`: `g1.lt.slow();` slows the robot the further LT is
  pulled (down to `slowSpeed` at a full squeeze, like the two-driver LT). On a button, `.slow()` is slow
  while held.
- Write the switch exactly as `public static boolean oneDriver = true;`. INIT stops with a message if it
  is not static.

## Reset "forward"

Reset "forward" works only with field-centric on. In robot-centric driving, the held BACK shows a note instead
("forward" means nothing there).

## Switch the alliance

It is optional, and off by default. Turn it on with `lib.switchAlliance(g1.start, g1.back)`.

1. On gamepad 1, hold the small START button.
2. Then press and hold BACK too. Hold both for 1.5 s. (BACK first is only a pause.)
3. Both gamepads buzz twice. The Driver Station says `Alliance now RED (was BLUE)`.

- Everything that reads `robot.alliance()` follows at once: the camera's goal tag (`Camera.goal()`), the
  aim's goal point (mirrored for RED), light rules you wrote with it, and `MatchMemory` (a TeleOp started
  again gets it).
- The pose follows too, because autonomous ran as the wrong alliance. On a mirrored field (the reset
  spot's `mirrorForRed` true), the library mirrors it in place (x and heading flip). On a turned field
  (`mirrorForRed = false`, BIOBUZZ), the pose counts as not set, so the aim's odometry backup is off until
  you use "set pose here" (the note says so).
- INIT's `Switch alliance` line says whether it is on.

## Set pose here

For robots with odometry, when autonomous was stopped early or did not run.

1. Park the robot on the reset spot.
2. Let go of the drive sticks (and turn whole-robot aim off).
3. Hold gamepad 1 START for 1 s, in INIT (before the match) or during it.
4. Gamepad 1 buzzes twice. The Driver Station says `Pose set: x -56, y -61 in, heading 0 (BLUE)`. While
   the robot moves, it says `Pose NOT set: stop first`.

- START + A or B (pairing a gamepad on the Driver Station) never sets it.
- INIT shows the button, the spot and the mirroring used for RED (`Reset spot for RED: ...`).
- The spot: `Tunables.Odometry.resetX, resetY, resetHeading, mirrorForRed`
  ([`resetPose()`](robot-java.md#robotdefinition)).

## Drive to a spot by itself

`route(...)` in `buttons(...)` makes a job that drives the robot to a field spot, around the boxes of
`fieldMap(...)`. Any drive stick stops it. How it works, and what it cannot do, is in
[keep-out boxes and routes](../modules/odometry.md#keep-out-boxes-and-routes).

```java
Runnable park = route("Park", -56, -36, 0, 0.8);
g1.dpadLeft.tap(park).says("drive to the park spot (a drive stick stops it)");
atSecondsLeft(10, park);
```

| In `Controls.java` | What it does |
| --- | --- |
| `route(label, xIn, yIn, headingDeg, power[, toleranceIn])` | makes the job (a `Runnable`). Mistakes stop INIT. It starts when a button or a time runs it |
| `atSecondsLeft(seconds, job)` | runs a job once when this many seconds of TeleOp are left. A `route(...)` job waits up to 5 s for the driver to let go of the drive sticks |
| `routeRunning()` | whether a route drives the robot now |
| `stopRoute()` | ends a running route |

## Buttons in Tunables.java

<!-- tunables: Buttons -->
Section `Buttons` (a fixed name), for button gestures (double tap, hold). Module: [ControlMap](#controlmap).

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value.
`MEASURE` means you must measure it. A few lines have example numbers.

<!-- recipe: tunables-buttons -->
```java title="Tunables.java"
/** Optional: slower gestures, e.g. for new drivers. Leave the section out for the defaults (in brackets). */
public static class Buttons {                                   // a fixed name: always Buttons
    public static double tapGapSec = 0.45;                      // most time between the taps of a double-tap (0.35)
    public static double holdSec = 0.4;                         // a press this long is a hold, not a tap (0.3)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `tapGapSec` | the most time between the two taps of a double tap (not the library's field-centric double tap: always 0.35 s) | seconds | 0.35 | yes, at once | test | double taps are missed (more) or single taps wait too long (less) |
| `holdSec` | how long a press is held before it counts as a hold | seconds | 0.3 | yes, at once | test | holds start by accident (more) |

---

**Look it up:** [`ControlMap` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/ControlMap.html)
(every method). **The other pages:** [Reference](README.md).
