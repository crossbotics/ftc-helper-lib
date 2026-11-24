# Advanced autos

What the auto builder cannot write: waits for a part, choices in INIT,
shared steps in `Routes.java`, a number for RED, and your own steps.

## What this page is for

Each part below adds one thing to an auto. Use only the parts you need.

| Part | What it does for you |
| --- | --- |
| [Wait until a part is ready](#wait-until-a-part-is-ready) | The robot drives while a slow part moves. It waits for the part only when it must. |
| [One auto with choices, or several autos?](#one-auto-with-choices-or-several-autos) | Pick how to offer plans: one file per plan, or one file with choices in INIT. |
| [Add choices to the INIT menu](#add-choices-to-the-init-menu) | Your own lines in the INIT menu, like "Then: Park / Stay". Some steps run only for one choice. |
| [Routes](#routes) | Steps that several autos share, written once in `Routes.java`. |
| [A whole example](#a-whole-example-one-auto-the-drivers-choose) | One auto file: the drivers choose the start spot and the plan. |
| [A different number for RED](#a-different-number-for-red) | One number (a wait, a distance) that is different on RED. |
| [Keep code inside the steps](#keep-code-inside-the-steps) | Why a line between two steps runs in INIT, and the fix. |
| [Write your own step](#write-your-own-step) | Your own Java code as one step, for what no other step can do. |

> **The auto builder cannot write anything on this page.** Build the route in the builder first. Copy the
> Java into your file, then add these by hand. **Note:** after you add them, the builder's **Load** may not
> read the file again. Keep a copy of the plain plan, so you can change it in the builder later.

## Wait until a part is ready

**What it does:** the robot does not stop for a slow part. It drives on, and waits only when it needs the
part.

**Start early, wait late.** Start a slow part with `run` as early as you can. Then wait for it with
`waitUntil` right before you need it:

```java
         .run("Lift up", () -> robot.lift.goTo("high"))     // starts the lift
         .forward("To the goal", 24, 0.6)                   // drives while the lift rises
         .waitUntil("Lift at the top", robot.lift::isAtTarget, 2)
```

**Why:** the robot does two things at once. That saves time in a 30-second match. The checks you can wait
for: [page 1, wait](01-what-an-auto-can-do.md#wait).

## One auto with choices, or several autos?

**What it means:** you can give the drivers more than one plan in two ways.

- **Several autos:** one file per plan ([page 3](03-multiple-autos.md)). Each plan is its own line in the
  Driver Hub's Autonomous list. The auto builder can make each one.
- **One auto with choices:** one file with your own lines in the INIT menu. The drivers pick the auto,
  press INIT, then pick the plan with the D-pad. The steps for every choice are in the same file.

One auto with choices can replace several autos that are almost the same. Pick by the difference between
the plans:

| The difference between the plans | Use |
| --- | --- |
| Red or blue | nothing: the Alliance line is in every menu |
| Wait for a partner at the start | nothing: the Start delay line is in every menu |
| Wait for a partner in the middle | `menuYesNo` + `onlyIf` |
| Park near or far, leave or stay | `menuChoice` + `onlyIf` |
| Score 1 piece or 2 | `menuChoice`, or two files |
| Another start spot, the same idea | `menuChoice` for the start ([page 3](03-multiple-autos.md#near-and-far-starts)) |
| A different route, nothing shared | a new file |
| A test routine (drive a square) | a new file, in its own group, so nobody runs it in a match |

**A rule of thumb:** use a menu line for what the drivers decide at the field. Use a new file for what
the programmers decide in the pits.

Keep the menu small. Two or three of your own lines are fine. Six lines make too many mixes to test.

## Add choices to the INIT menu

**What it does:** adds your own lines to the INIT menu. Some steps then run only when the drivers pick
one option.

Every auto has an **INIT menu** with Alliance, Start delay and Profile ([page 2](02-put-steps-together.md#the-init-menu)).
You can add your own lines.

**Add your own line,** then mark the steps that run only for one choice:

```java
        s.start("Against our wall, facing the field", 0)
         .menuChoice("Then", "Park", "Stay")          // a line with two options
         .forward("To the goal", 24, 0.6)
         .action(Actions.scoreHigh(robot), 5)
         .onlyIf("Then", "Park")                      // only when "Park" is chosen
             .right("Park", 20, 0.7)
         .end();                                      // the end of the onlyIf
```

- `menuChoice(name, option, option, ...)`: a line with options. **INIT starts on the first option.**
  Make it your safest plan.
- `menuYesNo(name, true)`: a YES/NO line. `true` = it starts at YES. Use it with `onlyIf(name)`.
- `onlyIf(name, option)` ... `end()`: the steps in between run only for that choice. Every `onlyIf` needs
  its own `end()`.
- Spell the options **exactly** the same in `menuChoice` and `onlyIf`, capitals too. A typo makes a branch
  that never runs.
- **For an "else",** write a second `onlyIf` with the other option. An `onlyIf` can be inside another one: each
  `end()` closes the last one opened.
- `choice(name)` and `isYes(name)` read a line in your own Java, with a plain `if` ([below](#a-whole-example-one-auto-the-drivers-choose)).
- **A number line:** `menuNumber(name, min, max, step, start)` lets the drivers choose a number, for
  example `menuNumber("Distance (in)", 0, 96, 1, 24)`. `menuAngle(name, step, start)` chooses an angle in
  degrees. Tap the D-pad for one step. Hold it to step fast. Read it with `number(name)`:

  ```java
          s.menuNumber("Distance (in)", 0, 96, 1, 24);
          s.forward("Out of the way", s.number("Distance (in)"), 0.5);
  ```

The indents inside `onlyIf` are only for people. Java does not read them.

## Routes

**What it does:** puts the steps that several autos share in one file, `autos/Routes.java`. Each auto then
uses them by name.

**You do not need it.** One file per plan, each made in the auto builder, is fine
([page 3](03-multiple-autos.md#one-file-per-plan)). **Pick Routes when** many autos share the same steps,
and you fix the same number in many files.

| | Copies (one file per plan) | Routes |
| --- | --- | --- |
| The auto builder | works: **Load** reads each file | does not work: **Load** cannot read an auto made from Routes |
| Fix a number | in every file that has the step | once |
| Good for | a few plans, made in the builder | many plans that share steps |

Each **piece** of a route is a **method** in `Routes.java` (a named group of lines that you run by its
name). You build every auto from the pieces. When you fix a piece once, every auto that uses it gets the
fix.

The library has this example as a compiled file, for the cookbook's robot (a mecanum drive, a lift and a
claw):

<!-- recipe: autos-routes -->
```java title="autos/Routes.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first two imports below to:  import org.firstinspires.ftc.teamcode.Actions;
//   import org.firstinspires.ftc.teamcode.Robot;

import com.crossbotics.ftchelper.examples.cookbook.Actions;
import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Steps;

/** The pieces every autonomous is made from. Write each piece once. No @Autonomous: not an OpMode. */
public class Routes {
    /** Score the piece the robot starts with: hold it, drive to the goal, score. */
    public static Steps scorePreload(Steps s, Robot robot) {
        return s.run("Hold the preload", robot.claw::close)
                .forward("To the goal", 30, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** One more piece: back to the pieces, grab one, forward to the goal, score. */
    public static Steps cycle(Steps s, Robot robot) {
        return s.back("To the pieces", 20, 0.6)
                .run("Grab", robot.claw::close)
                .waitSeconds("Let the claw close", 0.3)
                .forward("Back to the goal", 20, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** The park: back toward our wall. It keeps the last 3 seconds for itself, so it always runs. */
    public static Steps park(Steps s) {
        return s.parkWithSeconds(3)
                .back("Park", 20, 0.7);
    }
}
```

- `public static Steps scorePreload(Steps s, Robot robot)`: a method that adds its steps to the list `s`,
  and gives the list back.
- `Routes.java` has no `@Autonomous` line. It only holds pieces, so it is not an auto.
- The numbers are in the steps, like in any auto. You write the piece once, so you write each number once
  too.

**An auto made from pieces** is only a few lines. Each line adds one piece to the same list:

<!-- recipe: autos-near-cycle -->
```java title="autos/NearCycle.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/** Score the preload, one more piece, then park. Only the Routes.cycle line is new. */
@Autonomous(name = "Near: Preload + Cycle + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public class NearCycle extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("NEAR: against our wall, 2nd tile left of center, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

Here the lines do not chain the steps with dots. Each line calls a piece, and the piece adds its steps to the
same list `s`. The result is the same list as one long chain.

A second file, "Near: Preload + Park", is the same without the `Routes.cycle` line. The drivers pick it
when the partner needs the pieces.

**To copy these files,** change the `package` and `import` lines as the comments say, and delete
`@Disabled` ([Copy example files into TeamCode](../pick-your-start/04-copy-into-teamcode.md)). In
`preselectTeleOp`, write **your** TeleOp's name.

## A whole example: one auto, the drivers choose

**What it does:** one auto file holds the plans from [Routes](#routes), a "Park only" plan, and two start
spots. The drivers choose the start spot and the plan in INIT:

<!-- recipe: autos-choose-in-init -->
```java title="autos/ChooseInInit.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/** ONE autonomous: the drivers choose the start and the plan in INIT. */
@Autonomous(name = "Choose in INIT", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public class ChooseInInit extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        // Your menu lines (under the library's Alliance and Start delay lines). The FIRST option of
        // each is chosen at INIT: make it your safest plan.
        s.menuChoice("Start", "Near", "Far")
         .menuChoice("Plan", "Preload + Park", "Preload + Cycle + Park", "Park only");

        // The start spot follows the "Start" line: the note and the simulator's robot change with it.
        s.onlyIf("Start", "Near")
             .start("NEAR: against our wall, 2nd tile left of center, facing the field", -36, -61, 0)
         .end()
         .onlyIf("Start", "Far")
             .start("FAR: against our wall, 2nd tile right of center, facing the field", 36, -61, 0)
             // Off the wall first: the FLOWER on our wall stands between the two start spots.
             .forward("Off the wall", 12, 0.5)
             .left("Across to the near side", 72, 0.7)
         .end();

        // The plan: plain Java "if". choice(...) = the option chosen, as text. (1)!
        String plan = s.choice("Plan");
        if (!plan.equals("Park only")) Routes.scorePreload(s, robot);
        if (plan.equals("Preload + Cycle + Park")) Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

1. `s.choice("Plan")` gives the option the drivers chose, as text. `plan.equals("Park only")` asks "is it
   the same text?". `!` means "not". So the first `if` reads: if the plan is not "Park only", score the preload.

What the drivers see after INIT:

```text
Place robot : NEAR: against our wall, 2nd tile left of center, facing the field
--- Choose (D-pad), then ▶ ---
> Alliance: BLUE
  Start delay: 0 s
  Start: Near
  Plan: Preload + Park
  Profile: COMPETITION
X = BLUE   B = RED
```

Change **Start** to Far, and the first line at once says `Place robot : FAR: ...`. **Why:** the library
reads the steps again each time a menu line changes. So the note always matches the start the drivers
chose.

## A different number for RED

**What it does:** gives one number a different value on RED.

Most autos need nothing more: the library turns or mirrors the steps
([page 2](02-put-steps-together.md#red-and-blue)). Sometimes one thing is different on RED: a wait, a
distance, an AprilTag number. `alliance().pick(blueValue, redValue)` gives the right
one for the alliance the drivers chose:

```java
    @Override
    protected void steps(Steps s, Robot robot) {
        double parkInches = alliance().pick(20.0, 26.0);   // BLUE 20 in, RED 26 in

        s.start("Against our wall, facing the field", 0)
         .forward("To the goal", 24, 0.6)
         .action(Actions.scoreHigh(robot), 5)
         .parkWithSeconds(3)
         .right("Park", parkInches, 0.7);
    }
}
```

- `alliance()` is BLUE or RED, from the INIT menu.
- `pick(20.0, 26.0)`: the first number for BLUE, the second for RED. Write both with a `.0`.
- When the drivers change the Alliance line in INIT, the library reads the steps again. So the number
  always matches the alliance.

**RED differs only** when the field is not the same for both alliances. Check the field
drawing. Then run the auto as BLUE and as RED in the simulator.

**The auto builder** shows both: **Run as** RED or BLUE. It cannot write `alliance().pick`. Add it in
your file.

## Keep code inside the steps

**What it means:** every line in `steps(...)` must be a step. A line between two steps runs at the wrong
time.

The library reads your list of steps **more than once**:

1. In INIT, to learn the menu, the start note and the park time. **No step runs then.**
2. In INIT again, each time the drivers change a menu line.
3. After ▶, to make the real list. Then it runs.

So put only steps in `steps(...)`. A line like `robot.claw.close();` between two steps runs in INIT,
several times. Put it in a step: `.run("Close the claw", robot.claw::close)`.

## Write your own step

**What it does:** runs your own Java code as one step, for something no other step can do. The lines
between `{` and `}` are your code:

```java
         .step("Clear the intake", ctx -> {
             robot.intake.out();
             ctx.waitFor(0.3);       // wait 0.3 s; every part keeps working
             robot.intake.stop();
         })
```

`ctx` is your step's helper (short for "context"). It gives you:

| Inside your step | What it does |
| --- | --- |
| `ctx.waitFor(seconds)` | waits. Every part keeps working |
| `ctx.waitUntil(check, seconds)` | waits until the check is true. It gives false if the time ran out |
| `ctx.isActive()` | false when the auto is over or the park time came: stop then |
| `ctx.secondsLeft()` | the seconds left in the auto |
| `ctx.note(text)` | your words in the report, in place of `done` |
| `ctx.loop()` | updates every part once: call it in your own loop |
| `ctx.telemetry()` | add your own lines to the Driver Hub screen |

**Never use `sleep()`.** It freezes every part, and the 30 s timer cannot stop the robot. Use
`ctx.waitFor`. ([Why](../guides/non-blocking.md).)

## Done when

- One auto file offers a choice in INIT, and each option runs in the simulator.
- Each plan runs in the simulator, as BLUE and as RED.

## Problems

| Problem | Why, and the fix |
| --- | --- |
| A menu choice does nothing | The option has a different spelling in `menuChoice` and in `onlyIf` or `equals`. |
| The robot ran the wrong plan | Nobody changed the menu, so the first option ran. Read the menu out loud before ▶. |
| Every step after an `onlyIf` runs only for one choice | The `end()` is missing. |
| `end() without an onlyIf` | One `end()` too many. |
| Something moves in INIT | Code is between the steps. Put it in a `run(...)` or a `step(...)`. |
| One auto is fixed, another is not | Someone copied the steps. Fix each copy, or move the steps into `Routes.java`. |
| `cannot find symbol: Routes` | `Routes.java` must be in the same `autos` folder, with the same `package` line. |
| The builder's **Load** cannot read the file | It reads only plain steps. Load the plain copy, or change this file by hand. |

**What each word in the report means** (`done`, `STALLED`, ...): [Match day](09-match-day.md#after-the-match-read-the-report).

---

**Next:** [Match day](09-match-day.md).

**Look it up:** [every step](../reference/steps.md#auto). **Go deeper:**
[cookbook: several autos and choosing one](../cookbook/10-several-autos.md),
[the cookbook's autonomous recipes](../cookbook/09-autonomous.md).
