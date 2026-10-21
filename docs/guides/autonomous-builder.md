# The autonomous builder: the complete guide

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

An autonomous is a **list of steps, read top to bottom**. You write only the list. The library does
everything around it: the INIT menu, mirroring for the other alliance, the 30 s timer, keeping time to
park, stopping everything at the end, and a report of what happened to each step.

This guide covers every part of the builder (`Auto` and `Steps`, from the
[robot in five files](../tutorials/22-robot-in-five-files.md)). The example used all the way through is
one real, tested program:
[`AutoCookbook.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot/autos/AutoCookbook.java).
A simulator test runs it with each menu choice, so the code here always works.

Contents: [the whole example](#the-whole-example) · [what you get for free](#what-you-get-for-free) ·
[how the list is read](#how-the-list-is-read-twice) · [placing the robot](#1-placing-the-robot) ·
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
they chain with dots. Indenting the steps inside `onlyIf ... end()` is only for people: Java ignores it.

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

## How the list is read (twice)

`steps(...)` is called **twice**:

1. **In INIT**, to learn the menu, the start note and spot, and the park time. **No step runs then.**
2. **After START**, with the drivers' choices, to build the real list. Then it runs.

So put only steps in `steps(...)`. Code with side effects (moving a servo, printing, counting) placed
between the steps would run twice, once in INIT. Put such code inside a step instead
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
decides at the field (which pieces, leave or stay, a partner in the way); a new file for a different
start position.

## Driving: move, turn, turnTo

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
| `turnTo(label, headingDeg, maxPower)` | **counterclockwise +** | to a heading; 0 = the way it faced at the start |

Each waits until it is done. A move slows down near its end so it does not overshoot. It does not
correct the heading on the way: after a bump or a long drive, add a `turnTo` to square up. A move that
cannot finish (pushed into a wall, stuck) ends by itself, and the report says `STALLED` or
`TIMED_OUT`. **Tune the drive first** with the Drive Tuner ([tutorial 09](../tutorials/09-first-auto.md)):
the inches are only right on a tuned robot.

**Put route numbers in `Tunables.java`** (`Tunables.Auto.collectInches`): every number is in one place,
named, and easy to find at an event. A section that is not a part, like `Auto`, is in the tuning menu
too: change `Auto/collectInches` in the TeleOp (PRACTICE profile, hold GUIDE 1 s), save with A, and run
the autonomous again with no rebuild. Its INIT lists the values that differ from the file: copy the
good ones back ([how](../modules/robot.md#tunablesjava)).

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
Fire all: done
Clear the intake: done
Leave the launch zone: done
```

| Word | Means |
| --- | --- |
| `done` | it finished |
| `timed out` | a wait or an action ran out of time; the list went on |
| `STALLED`, `TIMED_OUT`, `STOPPED` | a move could not finish (stuck, too long, stopped) |
| `cut short (time for park)` | stopped early so the park steps had time |
| `skipped (time for park)` | not started: no time left before parking |
| your own words | from `ctx.note(...)` or an action's reason |

The same report is in the robot log and on TeleOp's INIT screen, so the drive team can read it after
the match while they set up.

## Testing an autonomous

1. **In the simulator** ([guide](simulator.md)): INIT it, choose in the menu with the D-pad (or the
   keyboard), START, and watch. The report shows at the end. An untuned drive still runs there, with a
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

**A choice of start position:** use a separate file per start position (each has its own note and spot).

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

- **Code between steps runs twice** (once in INIT). Put it in `run(...)` or `step(...)`.
- **`sleep()` in a step** freezes every part and the timer. Use `ctx.waitFor`.
- **Forgetting `end()`**: everything after the `onlyIf` becomes part of the branch.
- **Wrong turn direction**: turns are counterclockwise + (left), moves are clockwise from forward.
- **A route that works on BLUE but not RED**: check `mirrorForRed()` against the field drawing.
- **Untuned drive**: the inches are wrong until the Drive Tuner is done; the autonomous refuses to run
  on the robot until `Tunables.Drive.calibrated = true`.
- **No park time**: without `parkWithSeconds`, a slow step can eat the time for parking.
