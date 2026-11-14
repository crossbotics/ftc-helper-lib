# Several autos and choosing one

You can have one autonomous per plan, or one autonomous with an INIT menu. Both use the same pieces. For
the decision table, naming, simulator tests and a match-day checklist, see [Building autos 3: having multiple autos](../building-autos/03-multiple-autos.md). Each
file below is whole. Copy it into `autos/`, and change the `package` and `import` lines as its comment
says. The distances are plain numbers in the steps ([why](09-autonomous.md#how-do-i-change-a-distance-in-an-autonomous)).

**The questions on this page:**

- [How do I build several autos from the same pieces?](#how-do-i-build-several-autos-from-the-same-pieces)
- [How do I make two autos that differ by one piece?](#how-do-i-make-two-autos-that-differ-by-one-piece)
- [How do I let the drivers choose the start spot and the plan in INIT?](#how-do-i-let-the-drivers-choose-the-start-spot-and-the-plan-in-init)

## How do I build several autos from the same pieces?

**Where:** a new file `Routes.java` in `autos/`. It has no `@Autonomous`, so it is not an OpMode (a
program in the Driver Station's list). The autos below call its methods.

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

**Why:** you write each piece once, so a fix (or a new number in it) reaches every autonomous.
`public static Steps scorePreload(Steps s, Robot robot)` is a method that any file can call
(`public static`). It adds its steps to the list `s` and hands the list back (`return`).
**More:** [Build them from shared steps](../building-autos/08-advanced-autos.md#routes).

## How do I make two autos that differ by one piece?

**Where:** two new files in `autos/`, `NearPreloadPark.java` and `NearCycle.java` (delete `@Disabled`
in your copies, and write your TeleOp's name in `preselectTeleOp`).

<!-- recipe: autos-near-preload-park -->
```java title="autos/NearPreloadPark.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;
// (Routes is in the same autos folder: no import needed for it.)

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/** Score the preload, then park. preselectTeleOp: YOUR TeleOp's name. */
@Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public class NearPreloadPark extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ: the field is turned half a turn, one route for both alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("NEAR: against our wall, 2nd tile left of center, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.park(s);
    }
}
```

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

**Why:** the Driver Station shows two lines, named after the start spot and the plan, so the drivers pick fast.
Each line in `steps(...)` calls a piece, and the piece adds its steps to the same list `s`.
**More:** [Write each piece once](../building-autos/08-advanced-autos.md#routes), [names and groups](../building-autos/03-multiple-autos.md#names-on-the-driver-station).

## How do I let the drivers choose the start spot and the plan in INIT?

**Where:** one new file in `autos/`, `ChooseInInit.java`.

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

1. The pieces are Java methods, so a plain `if` picks them. `choice(...)` gives the text of the option (a String). `equals(...)` tests whether two texts are the same. `!` means "not".

**Why:** the library uses a `start(...)` inside an `onlyIf` only for its option. So the "Place robot"
note (and the simulator's robot) changes as the drivers change the menu. The first option of each line
is the default, so make it your safest plan. Your lines come after the library's two (`Alliance`,
`Start delay`), so the drivers press D-pad down twice to reach `Start`. The FAR route drives off the
wall first, because the FLOWER on your wall stands between the two start spots. A few Java words:
`String plan` keeps a text. `plan.equals("Park only")` is true when the two texts are the same. `!`
means "not".
**More:** [One file, the drivers choose in INIT](../building-autos/08-advanced-autos.md#a-whole-example-one-auto-the-drivers-choose), [what the menu can choose](../building-autos/08-advanced-autos.md#add-choices-to-the-init-menu).
