# Autonomous

An autonomous is one file in `autos/`: a list of named steps. Distances are in **inches** (1 foot = 12
inches, so 2 feet = 24). Directions are in degrees, and power is from 0 to 1.

> **One rule for every angle:** angles count counterclockwise, like the IMU: 0 = forward, **90 = left**,
> 180 = back, -90 = right. The rule is for a move (`.move("Left", 12, 90, 0.5)`), a turn
> (`.turn("Left", 90, 0.4)`), `turnTo(...)`, and the heading in `start(...)` and `driveTo(...)`. Straight
> moves and turns can use words instead: `.forward`, `.back`, `.left`, `.right`, `.turnLeft`, `.turnRight`.

**The robot drives only after you run the Drive Tuner.** Until then, the autonomous shows "AUTONOMOUS IS OFF".
Run the [Drive Tuner](15-tuning.md#how-do-i-tune-the-drive-for-autonomous) and copy its lines. Then set
`calibrated = true` in `Tunables.java`, section `Drive`. The simulator runs the autonomous anyway, so you
can try a route before tuning.

**The questions on this page:**

- [How do I make my first autonomous (drive 2 feet)?](#how-do-i-make-my-first-autonomous-drive-2-feet)
- [How do I drive forward, sideways and turn?](#how-do-i-drive-forward-sideways-and-turn)
- [Does my autonomous need a RED version?](#does-my-autonomous-need-a-red-version)
- [How do I let the drivers choose before the match?](#how-do-i-let-the-drivers-choose-before-the-match)
- [How do I make sure there is time to park?](#how-do-i-make-sure-there-is-time-to-park)
- [How do I run a mechanism while the robot drives?](#how-do-i-run-a-mechanism-while-the-robot-drives)
- [How do I wait for my alliance partner?](#how-do-i-wait-for-my-alliance-partner)
- [How do I write a step the list cannot say?](#how-do-i-write-a-step-the-list-cannot-say)
- [How do I change a distance in an autonomous?](#how-do-i-change-a-distance-in-an-autonomous)
- [How do I share the same first steps in several autos?](#how-do-i-share-the-same-first-steps-in-several-autos)
- [How do I make the start delay shorter, or use a different number on RED?](#how-do-i-make-the-start-delay-shorter-or-use-a-different-number-on-red)

## How do I make my first autonomous (drive 2 feet)?

**Where:** a new file in `autos/`. In Android Studio, right-click the `autos` folder, then New, Java Class,
and name it `LeaveTheStart`. Below is the whole file. Change the `package` and `import ...Robot` lines as the comment says:

<!-- recipe: auto-first -->
```java title="autos/LeaveTheStart.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

// The name on the Driver Station, and the TeleOp it picks next (use YOUR TeleOp's name).
@Autonomous(name = "Cookbook Leave the Start", group = "Cookbook", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy, or the Driver Station will not list it
public class LeaveTheStart extends Auto<Robot> {
    @Override
    protected void steps(Steps s, Robot robot) {
        // s = the list of steps, robot = your Robot.java. Step lines go here:
        s.start("Against our wall, facing the field", 0)        // the note the drive team reads
         .forward("Leave the start", 24, 0.4);                  // 24 inches = 2 feet, forward, 0.4 power
    }
}
```

**Why:** `preselectTeleOp` makes the Driver Station pick your TeleOp next, so the drivers only have to press INIT.
**More:** [Red and blue](../building-autos/02-put-steps-together.md#red-and-blue).

## How do I drive forward, sideways and turn?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-moves -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .forward("Forward", 24, 0.5)                 // inches, max power
 .right("Sideways to the right", 12, 0.5)     // mecanum only; also back(...), left(...)
 .turnLeft("Turn left 90", 90, 0.4)           // also turnRight(...)
 .move("At an angle", 12, 45, 0.5)            // angles count counterclockwise: 45 = forward and left
 .turn("Turn right 90", -90, 0.4)             // the same rule: a right turn is minus
 .moveWithTurn("Back, turning", 12, 180, 90, 0.4)   // mecanum only: drive and turn at once
 .turnTo("Face the start way", 0, 0.4); // to a heading: 0 = the way it faced at the start
```

**Why:** each move starts from where the robot is now, so the steps read like directions to a person.
`moveWithTurn` is the one exception. It drives and turns at once, so its direction is fixed on the
field. It counts like `turnTo`'s heading, from the way the robot faced at the start (180 = the opposite way).
**More:** [Driving: move, turn, turnTo](../building-autos/01-what-an-auto-can-do.md#drive).

## Does my autonomous need a RED version?

No. Write it once, for BLUE. BIOBUZZ's field is turned half a turn between the alliances. For it, add
this to the autonomous class:

<!-- recipe: auto-turned-field -->
```java title="autos/YourAuto.java"
// BIOBUZZ's field is turned half a turn between the alliances: one route works for both.
@Override
protected boolean mirrorForRed() {
    return false;
}
```

**Why:** RED then uses the same numbers, seen from the red wall. On a mirrored field (the default), the library flips RED for you.
**More:** [Field numbers](../building-autos/01-what-an-auto-can-do.md#field-numbers).

## How do I let the drivers choose before the match?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-menu -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .menuYesNo("Score", true)                  // the drivers choose in INIT with the D-pad
 .menuChoice("Park", "Near", "Far")
 .onlyIf("Score")                           // these steps run only if "Score" is yes
     .forward("To the goal", 30, 0.5)
     .action(Actions.scoreHigh(robot), 5)
 .end()                                     // never forget end()
```

**Why:** one file covers several plans, and the drivers choose in INIT with the D-pad. The alliance and start delay choices come with it, with no code.
**More:** [The INIT menu](../building-autos/08-advanced-autos.md#add-choices-to-the-init-menu), [Branches: onlyIf](../building-autos/08-advanced-autos.md#add-choices-to-the-init-menu).

## How do I make sure there is time to park?

Add `parkWithSeconds` before the park steps. This code follows the menu recipe above:

<!-- recipe: auto-park -->
```java title="autos/YourAuto.java"
.parkWithSeconds(4)                        // the steps below always get the last 4 s
.onlyIf("Park", "Near")
    .right("Park near", 20, 0.7)
.end()
.onlyIf("Park", "Far")
    .right("Park far", 40, 0.7)
.end();
```

**Why:** when 4 s are left, the main steps stop, so a slow step cannot cost you the parking points.
**More:** [Keep time to park](../reference/steps.md#keep-time-to-park).

## How do I run a mechanism while the robot drives?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-run-while-driving -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .run("Intake on", robot.intake::in)        // starts it and goes on at once
 .run("Lift up", () -> robot.lift.goTo("high"))
 .forward("To the pieces", 24, 0.4)         // the intake and lift work while it drives
 .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
 .run("Intake off", robot.intake::stop);
```

**Why:** `run` starts a part and goes on at once. `waitUntil` waits only when the next step needs it.
**More:** [Mechanisms: run and wait](../building-autos/02-put-steps-together.md#start-something-or-wait-for-it). For whole actions while driving, use `alongside(...)` ([write each piece once](../building-autos/08-advanced-autos.md#routes)).

## How do I wait for my alliance partner?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-wait-partner -->
```java title="autos/YourAuto.java"
s.menuYesNo("Wait for partner", false)
 .onlyIf("Wait for partner")
     .waitSeconds("Let our partner pass", 3)   // the robot keeps running while it waits
 .end();
```

**Why:** the drivers decide in INIT. For a wait before the first step, the menu already has a start delay.
**More:** [Write each piece once](../building-autos/08-advanced-autos.md#routes).

## How do I write a step the list cannot say?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-own-step -->
```java title="autos/YourAuto.java"
// Your own step, for anything the list cannot say. Wait with ctx, never sleep().
s.step("Clear the intake", ctx -> {
    robot.intake.out();
    ctx.waitFor(0.3);          // every part keeps working while it waits
    robot.intake.stop();
});
```

**Why:** `ctx.waitFor` keeps every part updating and the timer working. `sleep()` would freeze them.
**More:** [Your own steps](../building-autos/08-advanced-autos.md#write-your-own-step).

## How do I change a distance in an autonomous?

**Where:** the step itself, in your file in `autos/`. Change the number and build again.

```text title="autos/YourAuto.java"
.forward("To the goal", 30, 0.5)   // 30 = inches; change it here
```

**Why:** each autonomous keeps its own numbers, so a change to one auto does not change another. The
simulator's auto builder can read the auto, change a number, and give you the new file. `Tunables.java`
keeps the robot's numbers (speeds, positions, gains), which are the same in every auto.
**More:** [The numbers are in the step](../five-files/05-autos.md#the-numbers-are-in-the-step).

## How do I share the same first steps in several autos?

**Where:** your autonomous class (or a shared file), next to `steps(...)`.

<!-- recipe: auto-shared-opening -->
```java title="autos/YourAuto.java"
/** The same first steps for several autos: a method that adds them to the list. */
static Steps scorePreload(Steps s, Robot robot) {
    return s.forward("To the goal", 30, 0.5)
            .action(Actions.scoreHigh(robot), 5);
}
// in each autonomous:  scorePreload(s.start("...", 0), robot).right("Park", 20, 0.7);
```

**Why:** when you fix the opening once, every autonomous gets the fix.
**More:** [Write each piece once](../building-autos/08-advanced-autos.md#routes).

## How do I make the start delay shorter, or use a different number on RED?

Every autonomous's INIT menu already has `Alliance` and `Start delay` (0 to 10 s, D-pad left/right).

**For a shorter menu line, where:** your autonomous class, as a method of its own next to `steps(...)`:

<!-- recipe: auto-max-start-delay -->
```java title="autos/YourAuto.java"
// Inside your autonomous class, next to steps(...): the INIT menu's "Start delay" line
// offers 0 to 5 s (the default is 0 to 10 s).
@Override
protected double maxStartDelaySec() {
    return 5;
}
```

Most routes need no number that is different on RED, because the library mirrors or turns RED for you.

**For one number that is different on RED, where:** inside `steps(...)`, above the steps.

<!-- recipe: auto-alliance-pick -->
```java title="autos/YourAuto.java"
// Inside steps(...): alliance() is the alliance chosen in INIT. pick(blue, red) gives the
// first value on BLUE and the second on RED (here: RED waits 2 s for its partner).
double waitSec = alliance().pick(0.0, 2.0);
s.start("Against our wall, facing the field", 0)
 .waitSeconds("Let our partner go first", waitSec)
 .forward("Leave the start", 24, 0.4);
```

**Why:** the start delay waits before the first step, so a partner can go first. 10 s is a lot to scroll
through. `alliance().pick(blue, red)` works for any kind of value: a number, a name, an AprilTag id.
**More:** [What the INIT menu can choose](../building-autos/08-advanced-autos.md#add-choices-to-the-init-menu).
