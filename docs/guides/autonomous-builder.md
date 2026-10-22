# The autonomous builder: the complete guide

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** teams with the [robot in five files](../tutorials/start/01-robot-in-five-files.md) who write
autonomous modes. **You need:** a tuned drive (the Drive Tuner, [finders](finders.md#drive-tuner-wheel-size-and-strafe-correction));
the [first autonomous lesson](../tutorials/start/06-first-autonomous.md) is the short version of this page,
and the [cookbook](../cookbook.md#autonomous) has short "how do I...?" answers.

An autonomous is a **list of steps, read top to bottom**. You write only the list. The library does
everything around it: the INIT menu, mirroring for the other alliance, the 30 s timer, keeping time to
park, stopping everything at the end, and a report of what happened to each step.

This guide covers every part of the builder (`Auto` and `Steps`, from the
[robot in five files](../tutorials/start/01-robot-in-five-files.md)). The example used all the way through is
one real, tested program:
[`AutoCookbook.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot/autos/AutoCookbook.java).
A simulator test runs it with each menu choice, so the code here always works.

Contents: [the whole example](#the-whole-example) · [what you get for free](#what-you-get-for-free) ·
[how the list is read](#how-the-list-is-read) · [placing the robot](#1-placing-the-robot) ·
[the INIT menu](#2-the-init-menu) · [driving](#driving-move-turn-turnto) ·
[mechanisms](#3-mechanisms-run-and-wait) · [actions](#4-actions-several-parts-together) ·
[branches](#5-branches-onlyif) · [your own steps](#6-your-own-steps) · [parking](#7-parking) ·
[the report](#the-report) · [testing](#testing-an-autonomous) · [recipes](#recipes) ·
[every method](#every-method) · [mistakes](#common-mistakes)

## The whole example

```java
@Autonomous(name = "goBILDA Bot Auto Cookbook", group = "Examples", preselectTeleOp = "goBILDA Bot TeleOp")
public class AutoCookbook extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;              // BIOBUZZ's field is turned half a turn: the same route for both
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Back against our goal, facing away from it", -56, -61.1, 0)   // 1
         .menuChoice("Then", "Leave", "Collect more", "Stay")                  // 2
         .menuYesNo("Spin up early", true)
         .onlyIf("Spin up early")
             .run("Spin up the flywheel", robot.launcher::spinUp)              // 3
         .end()
         .action(Actions.fireAllWithIntake(robot), 8)                         // 4
         .onlyIf("Then", "Collect more")                                      // 5
             .run("Intake in", robot.intake::in)
             .move("To the pieces", Tunables.Auto.collectInches, 0, 0.5)
             .waitSeconds("Let them in", 0.5)
             .move("Back to the goal", Tunables.Auto.collectInches, 180, 0.5)
             .run("Intake off", robot.intake::stop)
             .action(Actions.fireAllWithIntake(robot), 8)
         .end()
         .step("Clear the intake", ctx -> {                                   // 6
             robot.intake.out();
             ctx.waitFor(0.3);
             robot.intake.stop();
         })
         .parkWithSeconds(3)                                                  // 7
         .onlyIf("Then", "Leave")
             .move("Leave the launch zone", Tunables.Auto.leaveInches, 0, 0.7)
         .end();
    }
}
```

The numbers 1 to 7 match the sections below. Each method adds one step and hands the list back, so
they chain with dots. `extends Auto<Robot>` means: this class is an `Auto` (it gets everything below
for free), made for your `Robot` class. `@Override` marks a method that replaces one `Auto` already has. Indenting the steps inside `onlyIf ... end()` is only for people: Java ignores it.

## What you get for free

Every autonomous built this way gets, with no code:

| | What it does |
| --- | --- |
| **INIT menu** | alliance (X = blue, B = red), a start delay (0 to 10 s), your own choices, and the PRACTICE/COMPETITION profile. D-pad up/down chooses a line, left/right changes it. |
| **Where to place the robot** | your note on the Driver Station in INIT; in the simulator the robot is put on the spot |
| **Mirroring** | routes are written for BLUE and mirrored left/right for RED (or not, on a turned field) |
| **The 30 s timer** | the steps stop when autonomous is over |
| **Time to park** | the park steps always get their seconds ([7](#7-parking)) |
| **Safety** | it refuses to drive until the drive is tuned (`Tunables.Drive.calibrated = true`); a step naming a part that is not on the robot stops it in INIT; everything stops at the end, even after an error |
| **The report** | what happened to each step, on the Driver Station, in the robot log, and again in TeleOp's INIT |
| **To TeleOp** | the alliance and the robot's heading go to TeleOp; `preselectTeleOp` makes the Driver Station choose your TeleOp next; autonomous ends as soon as its steps are done |

## How the list is read

`steps(...)` is called more than once:

1. **In INIT**, to learn the menu, the start note and spot, and the park time. **No step runs then.**
2. **In INIT again, each time the drivers change a menu line.** So a `start(...)` or a
   `parkWithSeconds(...)` inside an `onlyIf` follows the menu: the "Place robot" note changes with the
   choice ([choose the start in INIT](autos.md#example-2-one-opmode-the-drivers-choose-in-init)).
   Still no step runs.
3. **After START**, with the drivers' choices, to build the real list. Then it runs.

So put only steps in `steps(...)`. Code with side effects (moving a servo, printing, counting) placed
between the steps would run several times, already in INIT. Put such code inside a step instead
([`run`](#3-mechanisms-run-and-wait) or [`step`](#6-your-own-steps)).

## 1. Placing the robot

```java
s.start("Back against our goal, facing away from it", -56, -61.1, 0)
```

- **The note** is for the drive team: it is shown in INIT. Write it so a nervous driver gets it right.
- **The heading** (last number) is the way the robot faces, in degrees from "straight away from your
  drivers", counterclockwise +. TeleOp's field-centric "forward" uses it, so get it right.
- **The spot** (`x`, `y`, inches) is optional. `s.start(note, heading)` leaves it out. It is seen from
  your drivers' wall and written for BLUE: `x` to the right of the field's centre line, `y` from the
  centre toward the far wall (negative = your half). The simulator puts the robot there in INIT.

**Find the numbers in the simulator:** before START, drag the robot on the field map (or double-click
it in the 3D view, then drag it and turn its ring). The spot is written under the map, ready to copy:
`s.start("...", -56.0, -61.1, 0.0)`.

**RED:** with `mirrorForRed()` true (the default), RED uses the spot mirrored left/right, like the
routes. On a field turned half a turn between the alliances (BIOBUZZ), return false: RED uses the same
numbers from the red wall. On a field where both alliances' drivers stand at the same wall (DECODE),
also return true from `alliancesOnOneWall()`.

## 2. The INIT menu

```java
.menuChoice("Then", "Leave", "Collect more", "Stay")
.menuYesNo("Spin up early", true)
```

- `menuChoice(label, options...)`: a choice between options. The first is chosen at the start.
- `menuYesNo(label, startYes)`: a yes/no choice, starting at `startYes`.

They appear after the alliance and the start delay, in the order you write them, then the profile.
Read them with [`onlyIf`](#5-branches-onlyif) (most of the time), or in your own code with
`s.isYes(label)` and `s.choice(label)`.

**One autonomous with choices, or many files?** Choices keep one route that the team tests. Separate
files are clearer when the routes have nothing in common. A good rule: a choice for anything a driver
decides at the field (which pieces, leave or stay, a partner in the way, near or far start); a new file
for a route that shares nothing. The decision table, naming, and worked examples:
[Several autonomous modes](autos.md).

## Driving: move, turn, turnTo

> **Two different 90s.** A **move's direction** is measured clockwise, like a compass: 0 = forward,
> **90 = right**, 180 = backward, 270 = left. A **turn or a heading** (`turn`, `turnTo`, the heading in
> `start(...)` and `driveTo(...)`) is counterclockwise, like math class: **90 = left**, -90 = right.
> So `.move("Right", 12, 90, 0.5)` slides right, and `.turn("Left", 90, 0.4)` turns left. Read every 90 twice.

```java
.move("To the pieces", 24, 0, 0.5)      // 24 in forward, at most half power
.move("Back to the goal", 24, 180, 0.5) // 180 = backward
.turn("Face the goal", 45, 0.5)         // 45 degrees left (counterclockwise +)
.turnTo("Square up", 0, 0.5)            // back to the heading it started with
```

| | Direction | Notes |
| --- | --- | --- |
| `move(label, inches, directionDeg, maxPower)` | degrees **clockwise** from forward: 0 forward, 90 right, 180 back, 270 left | mecanum strafes at any angle; a tank drive only 0 and 180 (any other angle stops with "Turn first, then move") |
| `turn(label, degrees, maxPower)` | **counterclockwise +** | by an angle from where it is now |
| `turnTo(label, headingDeg, maxPower)` | **counterclockwise +** | to a heading; 0 = the way it faced at the start (the IMU's heading, not the field's: see [driveTo](#drive-to-a-field-point-driveto)) |

Each waits until it is done. A move slows down near its end so it does not overshoot. It does not
correct the heading on the way: after a bump or a long drive, add a `turnTo` to square up. A move that
cannot finish (pushed into a wall, stuck) ends by itself, and the report says `STALLED` or
`TIMED_OUT`. **Tune the drive first** with the Drive Tuner ([lesson 6](../tutorials/start/06-first-autonomous.md#1-tune-the-drive-first); by hand:
[under-the-hood tutorial 09](../tutorials/09-first-auto.md)):
the inches are only right on a tuned robot.

**Put route numbers in `Tunables.java`** (`Tunables.Auto.collectInches`): every number is in one place,
named, and easy to find at an event. A section that is not a part, like `Auto`, is in the tuning menu
too: change `Auto/collectInches` in the TeleOp (PRACTICE profile, after START hold GUIDE 1 s), save with A, and run
the autonomous again with no rebuild. Its INIT lists the values that differ from the file: copy the
good ones back ([how](../modules/robot.md#tunablesjava)).

## Drive to a field point: driveTo

With an `odometry(...)` line in `Robot.java` ([odometry](../modules/odometry.md),
[lesson 7](../tutorials/start/07-odometry.md)), a step can drive to a **place on the field** instead of
a distance:

```java
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", -30, -30, 45, 0.6)     // x, y, heading, max power
 .parkWithSeconds(4)
 .driveTo("Park", -56, -36, 0, 0.7, 2.0);       // last number: close enough, inches (default 1)
```

**Field points, not moves.** `move` and `turn` are robot-relative: "24 in forward from wherever the
robot is now". `driveTo` uses field numbers, the same as the start spot: x inches right of the centre
line, y inches away from your drivers' wall, heading 0 = facing away, counterclockwise +. It drives
and turns at the same time (mecanum only), checks the odometry's pose every loop and keeps correcting,
so a bump or wheel slip does not matter: it ends at the point. Changing one step does not shift the
steps after it.

| | |
| --- | --- |
| `driveTo(label, xIn, yIn, headingDeg, maxPower)` | done within 1 inch and 2 degrees (for 3 loops in a row) |
| `driveTo(label, xIn, yIn, headingDeg, maxPower, toleranceIn)` | your own "close enough", inches: larger for a quick pass-through point, never below what the robot can reach |

It slows down in proportion to the distance in the last 8 inches, so it stops on the point. Like a
move, it can end `STALLED`, `TIMED_OUT` (10 s) or `STOPPED`. Stuck means: farther than 8 inches from
the point, the usual move rule; in the last 8 inches (slow on purpose), less than 0.1 in closer in 2 s.
If the odometry stops working on the way (a Pinpoint fault, an OTOS that stops answering), the robot
holds still and waits up to 0.2 s (a short glitch is fine); still not ready, the step ends with
`NO_ODOMETRY`: driving on a frozen pose, it would never arrive.

**After a `driveTo` that did not finish, the next steps still run**, from wherever the robot is. Put
a mechanism action that needs the right place right after the `driveTo` that gets it there, and read
the report after practice runs.

**Its heading is the field's, not `turnTo`'s.** `turn` and `turnTo` use the IMU, with 0 = the way the
robot faced at the start. `driveTo` uses the odometry's field heading, with 0 = facing away from your
wall. With `s.start(note, x, y, 90)`, `turnTo(..., 0, ...)` faces field heading 90 (the start
direction), but `driveTo(..., 0, ...)` faces away from your wall. With odometry, give headings in
`driveTo`; if you also use `turnTo`, remember its 0 is the start.

**Mirroring rules** (written for BLUE, like the start spot):

- `mirrorForRed()` true (the default, a mirrored field): RED flips x and the heading. `(-30, -30, 45)`
  becomes `(30, -30, -45)`; y stays.
- `mirrorForRed()` false (a field turned half a turn, BIOBUZZ): RED uses the same numbers, seen from
  the red wall.
- The odometry starts at the start spot, flipped the same way, so the points and the start always
  agree. Give the spot (`start(note, x, y, heading)`): without one the pose starts at (0, 0).

**The rules it checks:**

- No `odometry(...)` line: INIT stops with `Steps: driveTo("To the goal") needs odometry: add an odometry(...) line to Robot.java (docs/modules/odometry.md)`.
- `Tunables.Odometry.checked` false, on a real robot: the step does nothing and the report says
  `To the goal: skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)`. The
  simulator runs it anyway. Set `checked = true` after the Odometry Tuner's Loop test passes
  ([finders](finders.md#odometry-tuner-directions-offsets-and-a-loop-check)).
- A tank drive: INIT stops with `Steps: driveTo("...") needs a mecanum drive (it drives sideways)`.
  Use `turnTo`, then `move`.

Put the points in `Tunables.Auto` (`goalX`, `goalY`, `goalHeading`), like the move distances: they are
in the tuning menu too.


## Follow a curved path: follow (pedro module)

With the [pedro module](../modules/pedro.md) and a `follower(Pedro.follower(...))` line in
`Robot.java`, a step can follow a whole path, curves included, with Pedro Pathing. **Mecanum drives
only** (Pedro has no tank drive; a tank robot uses `turnTo` and `move`):

```java
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .follow("To the goal", PedroPath.line(-56, -61, -30, -30).turnTo(0, 45))
 .follow("Park", PedroPath.curve(-30, -30, -50, -24, -56, -36).keepHeading(0));
```

A path can have several parts with no stop between them (`.then(...)`), a speed limit per part
(`.maxSpeed(0.5)`), and can hold its end point while the next steps run (`.holdEnd()`, at most 5 s; the next
`follow` or driving step ends the hold; `run`, `waitSeconds` and `waitUntil` do not). The same field numbers and mirroring as `driveTo`, and the same "skipped until
`Tunables.Odometry.checked`" rule. It ends `done`, `timed out` (10 s, or your own limit), `STALLED`
or `NO_ODOMETRY`, and the next steps still run. Pedro decides when it is close enough (2.5 in by
default). Details, tuning and the report words: [pedro module](../modules/pedro.md#follow-the-autonomous-step).

**Start things along the path** (the robot keeps driving): add `PathAt` things after the path. Each
starts once, at its place:

```java
.follow("To the goal", TO_GOAL, 15,                           // at most 15 s
        PathAt.fraction(0.5, "Intake on", robot.intake::in),     // halfway along the whole path
        PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp))   // 12 in before the end
```

Use your own parts' names. An action from your `Actions.java` works in place of a name and a command:
`PathAt.inchesLeft(12, Actions.scoreHigh(robot))` (or any action you wrote).

Also `PathAt.inchesDone(in, ...)`, `PathAt.part(n, ...)` (where part n of a `then` path starts) and
`PathAt.near(x, y, withinIn, ...)`. `driveTo` takes them too (its "path" is the straight line to the
point). If the step ends early, the report says which did not start: `STALLED, not fired: Intake on`.
More: [pedro module](../modules/pedro.md#do-things-along-the-path).
## 3. Mechanisms: run and wait

```java
.run("Spin up the flywheel", robot.launcher::spinUp)
.waitSeconds("Let them in", 0.5)
.waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
```

- `run(label, command)` **starts** something and goes on at once. The part keeps working during the
  next steps: a lift rises while the robot drives, a flywheel spins up on the way.
- `waitSeconds(label, s)` waits. Every part keeps working (nothing freezes).
- `waitUntil(label, condition, timeoutSec)` waits until something is true, at most `timeoutSec`. If
  it times out, the report says `timed out` and the list goes on.

`robot.launcher::spinUp` is a "method reference": the method, to run later. `() -> robot.lift.goTo("high")`
is a small piece of code (a "lambda") for a command that needs inputs.

## 4. Actions: several parts together

```java
.action(Actions.fireAllWithIntake(robot), 8)   // run it and wait, at most 8 s
.alongside(Actions.fireAll(robot))             // start it and go on: it runs during the next steps
```

An [`Action`](../modules/robot.md#action) from `Actions.java` is the same thing a TeleOp button runs,
so a sequence is written once and used in both. `action(a, timeoutSec)` waits until it is done (10 s
without a timeout). If it runs out of time it is stopped safely (a lift holds its height, waiting shots
are dropped) and the report says why. `alongside(a)` starts it and goes on: drive while it runs.

## 5. Branches: onlyIf

```java
.onlyIf("Then", "Collect more")        // a menuChoice: only when this option was chosen
    .move(...)
.end()
.onlyIf("Spin up early")               // a menuYesNo: only when "yes"
    .run(...)
.end()
```

The steps up to `end()` are added only when the choice matches. `onlyIf` blocks can be nested: each
`end()` closes the latest one. An `end()` without an `onlyIf` stops in INIT with a message.

Need an "else"? Write a second `onlyIf` with the other option. For more than this, use plain Java with
`s.isYes(label)` or `s.choice(label)`:

```java
if (s.choice("Then").equals("Stay")) s.waitSeconds("Stay out of the way", 5);
```

## 6. Your own steps

```java
.step("Clear the intake", ctx -> {
    robot.intake.out();
    ctx.waitFor(0.3);
    robot.intake.stop();
})
```

For anything the list cannot say. Inside, `ctx` gives you:

| | |
| --- | --- |
| `ctx.waitFor(seconds)` | wait; every part keeps working |
| `ctx.waitUntil(condition, timeoutSec)` | wait until true; returns false if it timed out |
| `ctx.isActive()` | false once autonomous is over (or the park time came): stop then |
| `ctx.secondsLeft()` | seconds left in autonomous |
| `ctx.loop()` | update every part once (in your own loop) |
| `ctx.telemetry()` | add your own lines to the Driver Station |
| `ctx.note(text)` | a note in the report instead of "done" |

**Never `sleep()`** in a step: the parts stop updating and the timer cannot stop the robot. Use
`ctx.waitFor` and `ctx.waitUntil` ([why](non-blocking.md)).

## 7. Parking

```java
.parkWithSeconds(3)
.move("Leave the launch zone", 20, 0, 0.7)
```

Parking points come at the very end, so the steps that park must never run out of time. The steps
**before** `parkWithSeconds(3)` stop when only 3 s are left: the one running stops at once and the rest
are skipped. The steps **after** it always run. Choose the seconds from a timed run, plus about a
second. More: [Keep time to park](../modules/robot.md#keep-time-to-park).

## The report

When the steps are done, the Driver Station shows "Autonomous finished" and one line per step:

```text
Start: done
Spin up the flywheel: done
Fire all: done (3 fired)
Clear the intake: done
Leave the launch zone: done
```

| Word | Means |
| --- | --- |
| `done` | it finished |
| `timed out` | a wait or an action ran out of time; the list went on |
| `STALLED`, `TIMED_OUT`, `STOPPED` | a move could not finish (stuck, too long, stopped) |
| `NO_ODOMETRY` | a `driveTo` stopped because the odometry was not ready for more than 0.2 s (see its warning); the list went on |
| `cut short (time for park)` | stopped early so the park steps had time |
| `skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)` | a `driveTo` on a real robot before the Odometry Tuner ([driveTo](#drive-to-a-field-point-driveto)) |
| `skipped (time for park)` | not started: no time left before parking |
| your own words | from `ctx.note(...)` or an action's reason |

The same report is in the robot log and on TeleOp's INIT screen, so the drive team can read it after
the match while they set up.

## Testing an autonomous

**Or draw it first:** the simulator's [auto builder](simulator.md#the-auto-builder) builds the list on the
field, runs it with your code, and writes this Java for you (the start note and a setup card for the drive
team too).

1. **In the simulator** ([guide](simulator.md)): INIT it, choose in the menu with the D-pad (on screen, or the
   keyboard after you set gamepad 1 to Keyboard), START, and watch. The report shows at the end. An untuned drive still runs there, with a
   warning, so you can try routes before tuning.
2. **A scenario file** checks it every time, with no clicking:

   ```text
   init "goBILDA Bot Auto Cookbook"
   start
   expect within 30 screen has "Autonomous finished"
   expect screen has "Leave the launch zone: done"
   expect error none
   ```

3. **On the robot, in the PRACTICE profile:** run it on blocks first, then on the field at low power,
   then at match power. Read the report after every run.

## Recipes

**The same opening in several autos:** put it in a method that returns the list.

```java
static Steps scorePreload(Steps s, Robot robot) {
    return s.run("Spin up", robot.launcher::spinUp)
            .action(Actions.fireAllWithIntake(robot), 8);
}
// in each autonomous:  scorePreload(s.start("...", 0), robot).move(...)...;
```

**Stay out of a partner's way:** the start delay in the INIT menu waits before the first step. For a
wait in the middle, add `.waitSeconds("Let our partner pass", 3)` inside an `onlyIf`.

**Shoot while driving:** `.alongside(Actions.fireAll(robot))`, then the move.

**A choice of start position:** a `menuChoice("Start", "Near", "Far")` and one `start(...)` inside each
`onlyIf("Start", ...)`: the note and the spot follow the menu. Or a separate file per start position.
Both ways: [Several autonomous modes](autos.md#example-3-near-and-far-starts).

## Every method

`Steps` (in the order you would use them):

| Method | Does | Waits? |
| --- | --- | --- |
| `start(note, headingDeg)` | the note for the drive team and the start heading | — |
| `start(note, xIn, yIn, headingDeg)` | the same, plus the spot (for the simulator) | — |
| `menuYesNo(label, startYes)` | a yes/no choice in the INIT menu | — |
| `menuChoice(label, options...)` | a choice between options in the INIT menu | — |
| `isYes(label)`, `choice(label)` | read a choice in your own code | — |
| `onlyIf(label)`, `onlyIf(label, option)`, `end()` | steps that run only for a choice | — |
| `move(label, inches, directionDeg, maxPower)` | drive (clockwise from forward) | yes |
| `turn(label, degrees, maxPower)` | turn by (counterclockwise +) | yes |
| `turnTo(label, headingDeg, maxPower)` | turn to a heading (0 = start) | yes |
| `driveTo(label, xIn, yIn, headingDeg, maxPower[, toleranceIn][, PathAt...])` | drive to a field point and heading with odometry (mecanum) | yes |
| `follow(label, path[, timeoutSec][, PathAt...])` | follow a path (pedro module); `PathAt` starts things along it | yes |
| `run(label, command)` | start something | no |
| `waitSeconds(label, seconds)` | wait | yes |
| `waitUntil(label, condition, timeoutSec)` | wait until true | yes |
| `action(action[, timeoutSec])` | run an action (default 10 s) | yes |
| `alongside(action)` | start an action | no |
| `step(label, ctx -> ...)` | your own step | your code |
| `parkWithSeconds(seconds)` | the steps after it get the last seconds | — |

`Auto` settings you can change (write a method with the same name):

| Method | Default | |
| --- | --- | --- |
| `mirrorForRed()` | true | false on a field turned half a turn between alliances (BIOBUZZ) |
| `alliancesOnOneWall()` | false | true when both alliances' drivers stand at one wall (DECODE) |
| `maxStartDelaySec()` | 10 | the longest start delay in the menu |

Every class and method of the library: the [reference](../reference.md).

## Common mistakes

- **Code between steps runs several times** (already in INIT). Put it in `run(...)` or `step(...)`.
- **`sleep()` in a step** freezes every part and the timer. Use `ctx.waitFor`.
- **Forgetting `end()`**: everything after the `onlyIf` becomes part of the branch.
- **Wrong turn direction**: turns are counterclockwise + (left), moves are clockwise from forward.
- **A route that works on BLUE but not RED**: check `mirrorForRed()` against the field drawing.
- **Untuned drive**: the inches are wrong until the Drive Tuner is done; the autonomous refuses to run
  on the robot until `Tunables.Drive.calibrated = true`.
- **driveTo does nothing on the robot**: `Tunables.Odometry.checked` is still false (the report says
  so). Run the Odometry Tuner first.
- **No park time**: without `parkWithSeconds`, a slow step can eat the time for parking.
