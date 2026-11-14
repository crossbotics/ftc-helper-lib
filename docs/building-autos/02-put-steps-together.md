# Put steps together

Make parts work while the robot drives, keep time to park, and drive
the right way for red and blue.

> **The auto builder writes everything on this page:** `run`, `action`, `alongside`, `parkWithSeconds`
> and `mirrorForRed()`. [Advanced autos](08-advanced-autos.md) covers what it cannot write (menus, your own
> steps, ...).

## The example

The example robot's (`lessonbot`) auto holds the piece, drives to the goal, scores, and parks. The library has it as a
compiled file:
[`lessonbot/autos/ScoreAndPark.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/autos/ScoreAndPark.java).

```java
@Autonomous(name = "Lesson Bot Score and Park", group = "Examples", preselectTeleOp = "Lesson Bot TeleOp")
public class ScoreAndPark extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() { // (1)!
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, facing the field", 0)
         .run("Hold the piece", robot.claw::close) // (2)!
         .forward("To the goal", 24, 0.6)
         .action(Actions.scoreHigh(robot), 5) // (3)!
         .parkWithSeconds(3) // (4)!
         .right("Park", 20, 0.7);
    }
}
```

1. `@Override` marks a method that replaces one `Auto` already has. Copy these lines as they are, and
   change only `false` or `true`: [red and blue, below](#red-and-blue).
2. `run` starts the claw and goes on at once. The claw closes while the robot already drives.
3. `action` runs the "score high" action from `Actions.java`, and waits until it is done (at most 5 s).
4. The steps after this line always get the last 3 seconds: [below](#keep-time-to-park).

**The first lines of the file** (above `@Autonomous`), in your own copy:

```text
package org.firstinspires.ftc.teamcode.autos;

import org.firstinspires.ftc.teamcode.Actions;
import org.firstinspires.ftc.teamcode.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
```

**Why the `Actions` line:** `Actions.java` is in the folder above `autos`, like `Robot.java`.

## Start something, or wait for it

You can use a part in three ways. To pick one, decide whether **the next step must wait for it**.

| You write | The next step starts | Use it when |
| --- | --- | --- |
| `.run("Intake in", robot.intake::in)` | at once | one command, and the robot can go on: an intake on, a claw closed, a flywheel spun up |
| `.action(Actions.scoreHigh(robot), 5)` | when the action is done | the robot must not move until it is done: a score, a shot |
| `.alongside(Actions.fireAll(robot))` | at once, and the action runs during the next steps | several steps that can happen while the robot drives |

**Start early, wait late.** Start a slow part with `run` as early as you can. The robot then does two
things at once. That saves time in a 30-second match. To wait for the part right before you need it, use
`waitUntil` ([Advanced autos](08-advanced-autos.md#wait-until-a-part-is-ready)).

## Keep time to park

Parking points come at the very end of the 30 seconds. The park must never run out of time.

`.parkWithSeconds(3)` splits the list in two:

- **The steps before it** stop when only 3 s are left. The step that is running then stops at once, and
  the auto skips the rest.
- **The steps after it** always run.

The report says which steps were `cut short (time for park)` or `skipped (time for park)`.

**To choose the seconds,** run the auto. Measure how long the park steps take, and add about 1 second.

**Try it:** change `parkWithSeconds(3)` to `parkWithSeconds(28)`, and run the auto in the simulator. The
auto cuts the score steps short, and the park still runs. Then change it back.

## The INIT menu

Every auto already has an **INIT menu** on the Driver Hub. The drivers use gamepad 1: D-pad up/down picks
a line, D-pad left/right changes it.

| Line | What it is |
| --- | --- |
| Alliance | BLUE or RED. **X** = BLUE, **B** = RED. |
| Start delay | 0 to 10 s. The auto waits this long before the first step. **Why:** to stay out of a partner's way. |
| Profile | PRACTICE (for the shop: debug lines and the tuning menu) or COMPETITION (for matches): [what they change](../five-files/05-autos.md#practice-and-competition) |

**The menu remembers:** Alliance starts at the last auto's alliance, and the hub saves the Profile.
A longer start delay: [`maxStartDelaySec()`](../cookbook/09-autonomous.md).

**Your own lines** (park or stay, near or far): [Advanced autos](08-advanced-autos.md#add-choices-to-the-init-menu).
Or make one auto file per plan: [Having multiple autos](03-multiple-autos.md).

## Red and blue

You write the auto **for BLUE**. The library changes it for RED. How it changes it depends on the season's field.

> **This season, BIOBUZZ (2026-27): the field is ROTATED.** The red half is the blue half turned half a
> turn. Write `return false;` in `mirrorForRed()`. RED then drives the same steps, seen from
> the red wall.
>
> Last season, DECODE (2025-26), was **mirrored**. Old autos and old videos from DECODE use `true`. Do not
> copy that.

| The field | Write | RED then |
| --- | --- | --- |
| The red half is the blue half **turned half a turn** (BIOBUZZ) | `mirrorForRed()` returns `false` | drives the same steps, seen from the red wall |
| The red half is the blue half **mirrored** left to right (DECODE) | `mirrorForRed()` returns `true` | drives the steps mirrored: left becomes right, and turns go the other way |

**Write `mirrorForRed()` in every auto.** Without it, the auto uses the robot's setting:
`mirrorForRed` in `Tunables.Aim`, else in `Tunables.Odometry`, else `true`. That setting is easy to miss.
The auto builder writes `mirrorForRed()` for you (**Red's side** in [step 1](06-auto-builder-steps.md#1-robot-and-alliance)).

**Where the drivers stand.** In BIOBUZZ and in DECODE, each alliance's drivers stand at their own wall.
You need nothing more. A field where both alliances' drivers stand at one wall needs one more method:
[`alliancesOnOneWall()`](../modules/auto.md).

**One number different on RED** (a wait, a distance): `alliance().pick(blue, red)`, on
[Advanced autos](08-advanced-autos.md#a-different-number-for-red).

## The first run on the robot

1. **On blocks,** wheels off the floor. Check that the wheels turn the right way and the parts move.
2. **On the field, in the PRACTICE profile** ([PRACTICE and COMPETITION](../five-files/05-autos.md#practice-and-competition)).
   For the very first runs, set `maxPower` in `Tunables.Drive` lower (0.4), and back to `DEFAULT` when it
   works. Keep a hand near STOP.
3. Read the report after every run. Measure where the robot ended, and fix the numbers in the steps.
4. When it works every time, run it in the **COMPETITION** profile.

## Done when

- Your auto starts a part with `run`, and drives while the part works.
- It has a `parkWithSeconds(...)` before its park steps.
- It runs as BLUE and as RED in the simulator, and the robot goes the right way both times.

## Problems

| Problem | Why, and the fix |
| --- | --- |
| `cannot find symbol: Actions` | Add `import org.firstinspires.ftc.teamcode.Actions;` at the top of the file. |
| RED drives into a wall | `mirrorForRed()` is wrong for this field. Check the field drawing. |
| The park did not run | It was before `parkWithSeconds(...)`. Put the park steps after it. |

**What each word in the report means** (`done`, `STALLED`, ...): [Match day](09-match-day.md#after-the-match-read-the-report).

---

**Next:** [Having multiple autos](03-multiple-autos.md).

**Look it up:** [every step](../reference/steps.md#auto), [keep time to park](../reference/steps.md#keep-time-to-park).
**Go deeper:** [the cookbook's autonomous recipes](../cookbook/09-autonomous.md).
