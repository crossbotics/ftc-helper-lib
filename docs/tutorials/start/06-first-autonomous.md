# Lesson 6: Your first autonomous

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**Goal:** your robot holds its piece, drives to the goal, scores, and always parks, on its own. The
lesson robot's autonomous is the worked example: read its lines, then write your own in your `autos`
folder.

**You need:** your own robot from the earlier lessons (it drives, and the action you wrote in
[lesson 5](05-actions.md) works), the IMU (the turn sensor in the Control Hub), a tape measure and some tape, and about 5 ft of clear floor. About an hour, most of it tuning.

**The program:** [`autos/ScoreAndPark.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/autos/ScoreAndPark.java),
with its numbers in [`Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/Tunables.java).
**Reference:** [Auto](../../modules/robot.md#auto).

---

## 1. Tune the drive first

An autonomous says "drive 24 inches". The robot only knows how far its wheels turned. If the wheel size
or the strafe correction is a little wrong, every move is a little wrong, and the errors add up. So the
library has a safety rule: **no autonomous runs until you say the drive is tuned.**

The **Drive Tuner** measures the numbers for you. Make a new Java file `DriveTuner.java` next to
`Robot.java` in your `teamcode` folder ([how to make a file](../../start-from-zero.md#make-a-new-file-or-folder)),
and replace everything in it with this whole file (it only hands the Drive Tuner your robot's drive):

```java
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.DriveTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

@TeleOp(name = "Drive Tuner", group = "Finders")
public class DriveTuner extends DriveTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();   // your Robot.java, in the same folder
    }
}
```

The `import` lines say where `TeleOp`, `DriveTunerOpMode` and `RobotDefinition` are; `Robot` needs no
import because it is in the same folder. The lesson robot's copy:
[DriveTuner.java](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot/DriveTuner.java).
Run it, choose a test with the D-pad, start it with **A**:

| Test | You do | It finds |
| --- | --- | --- |
| Push | roll the robot BY HAND exactly 48 in along the tape, then press **B** | the wheel size, from the encoder counts |
| Drive | it drives 48 in; measure, set the real distance with the D-pad, press **A** | a better wheel size (wheels slip a little on tiles) |
| Strafe (mecanum) | it drives 48 in to the right; measure, set it, press **A** | the strafe correction |
| Turn | it turns 90 degrees left; check it against a tile seam | nothing to copy: a check of the turn |

Run Drive and Strafe again until they measure 48. Each new number is used at once inside the tuner.
Details and the results file: [Drive Tuner](../../guides/finders.md#drive-tuner-wheel-size-and-strafe-correction).

**Where the numbers go.** The tuner shows these lines (it also saves them in `finder-results.txt`):

```text
Robot.java: .wheelMm(100.0)   // in the drive(...) line
Tunables.Drive: strafeCorrection = 1.120
```

- the wheel size goes in the drive line of `Robot.java`: `.wheelMm(96)` becomes `.wheelMm(100.0)`.
- the strafe correction goes in `Tunables.Drive`: `strafeCorrection = 1.120`.
- the lines that start with `c.` are for the building-block classes of the "under the hood"
  tutorials, not the five files: skip them.
- a turn that overshoots: raise `turnKd` a little (+0.001) or lower `turnKp`. A turn that stops short:
  raise `turnKp` a little (+0.002). Both are in `Tunables.Drive`.

Then, and only then, flip the switch:

```java
public static boolean calibrated = true;
```

**Why a switch you set by hand?** A robot with wrong numbers can drive much too far or the wrong way.
Until `calibrated` is true, the autonomous shows **"AUTONOMOUS IS OFF: the drive is not tuned yet"** on
the Driver Station and does not move. The simulator is the one exception: there it runs anyway, with the
warning "Drive not tuned: runs in the simulator only", so you can try routes before the robot is tuned.

## 2. The file, line by line

```java
@Autonomous(name = "Lesson Bot Score and Park", group = "Examples", preselectTeleOp = "Lesson Bot TeleOp")
@Disabled
public class ScoreAndPark extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, facing the field", 0)
         .run("Hold the piece", robot.claw::close)
         .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)
         .action(Actions.scoreHigh(robot), 5)
         .parkWithSeconds(3)
         .move("Park", Tunables.Auto.parkInches, 90, 0.7);
    }
}
```

**Your own file, whole.** Make a new Java file `ScoreAndPark.java` in your `autos` folder (right-click
`autos`, **New > Java Class**), and replace everything in it with this. It is the lesson robot's file
with your own `package` and `import` lines, and no `@Disabled`:

```java
package org.firstinspires.ftc.teamcode.autos;

import org.firstinspires.ftc.teamcode.Actions;
import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

// preselectTeleOp: your TeleOp's name, exactly as in @TeleOp(name = ...) in Controls.java
@Autonomous(name = "Score and Park", group = "Autos", preselectTeleOp = "Quick Start TeleOp")
public class ScoreAndPark extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, facing the field", 0)
         .run("Hold the piece", robot.claw::close)
         .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)
         .action(Actions.scoreHigh(robot), 5)
         .parkWithSeconds(3)
         .move("Park", Tunables.Auto.parkInches, 90, 0.7);
    }
}
```

- **The `package` line** ends in `.autos`: the file is in the `autos` folder.
- **Three `import` lines of your own** (`Actions`, `Robot`, `Tunables`): they are in the folder above
  (`teamcode`), so this file must say where they are. The other three come from the library and the
  FTC SDK.
- **Your parts and action:** use the names from your own robot (lessons 4 and 5). No claw? Delete the
  `run("Hold the piece", ...)` line. Your action has another name? Use it in `action(...)`.
- **`Tunables.Auto`** must be in your `Tunables.java` (section 3 below).

- `@Autonomous(name = ...)` is the name on the Driver Station. `preselectTeleOp` must be the exact
  name of your TeleOp: when autonomous ends, the Driver Station has the TeleOp chosen and ready.
- `@Disabled` hides the example on a real robot. Delete it (and its import) in your copy, or you will not see it.
- `extends Auto<Robot>`: an autonomous for the lesson robot. `Auto` is the library's autonomous; the
  `<Robot>` in angle brackets says which robot it drives. Every part from `Robot.java` is in `robot`.
- `mirrorForRed()` false. Most routes are written for BLUE and mirrored left/right for RED. BIOBUZZ's
  field is turned half a turn between the alliances instead: from your own wall the field looks the
  same for both, so RED drives the same route. (A mirrored field would return true, the default.)
- `steps(...)` is a **list of steps, read top to bottom**. Each method adds one step and hands the
  list back, so they chain with dots.
- `start("Against our wall, facing the field", 0)`: the note tells the drive team where to put the
  robot (it is shown in INIT, so write it for a nervous driver). The `0` is the heading (the way the
  robot faces, in degrees): 0 = straight away from your drivers. TeleOp's field-centric "forward" uses it, so get it right.
- `run("Hold the piece", robot.claw::close)` **starts** something and goes on at once. The claw closes
  while the next step already drives.
- `move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)`: drive this many inches, in direction 0
  (forward), at most 60% power. Directions go **clockwise** from forward: 90 right, 180 back, 270 left.
  A move waits until it is done, and slows down near the end so it does not overshoot.
- `action(Actions.scoreHigh(robot), 5)`: the same "score high" action a button runs in TeleOp, written
  once in `Actions.java`. The list waits until it is done, at most 5 s. If the lift never reaches the
  top, the action stops safely (the claw stays shut) and the report says why.
- `parkWithSeconds(3)` splits the list in two. Parking points come at the very end, so the park
  must never run out of time. The steps **above** it stop when only 3 s are left; the steps **below**
  it always run.
- `move("Park", Tunables.Auto.parkInches, 90, 0.7)`: 90 = to the right (a mecanum robot strafes).

> **Two different 90s.** A **move's direction** goes clockwise from the robot's front: 90 = to the
> robot's **right**. A **heading** (`start(note, 0)`, `turnTo(...)`, and lesson 7's field points) goes
> counterclockwise: heading 90 = turned to the **left**. **Why two rules?** A move's direction is like
> the stick (pushed right = go right); a heading is the IMU's own rule (turning left counts up). The
> word tells you which: *direction* in a move, *heading* everywhere else
> ([signs and units](../../conventions.md)).

## 3. Route numbers live in Tunables.Auto

Add the two numbers to the `Auto` section of your `Tunables.java`. **Keep the lines that are already
there** (the Quick start's `leaveInches` is read by its `FirstAuto`: delete it and that file no longer
builds):

```java
public static class Auto {
    public static double leaveInches = 12;                      // drive off the start line
    // New: ScoreAndPark uses these two.
    public static double toGoalInches = 24;                     // drive to the goal
    public static double parkInches = 20;                       // drive into the park zone
}
```

Why not write `24` in the step? Because route numbers change at every practice. In `Tunables.java`
they sit with the robot's other numbers, under names that say what they are, and the file stays the
record of what worked.

**Tune them without a rebuild.** Every number in `Tunables.Auto` is in the tuning menu too:

1. INIT the **TeleOp** with the PRACTICE profile, press START, and hold **GUIDE** for 1 second (the menu
   from lesson 4; it opens only after START).
2. Choose `Auto/parkInches`, change it, and press **A** to save it on the Control Hub.
3. Run the autonomous again. It uses the saved value. Its INIT screen lists every value that is
   different from the code, like `Tunables.Auto.parkInches = 18.0 (code 20.0)`.
4. When the route works, copy those values into `Tunables.java`: the code is the record. A value
   saved only on the hub is lost with a new Control Hub.

## 4. What you get for free

You wrote only the list. With no more code, every `Auto` gets:

- **An INIT menu:** the alliance (**X** = blue, **B** = red), a start delay (0 to 10 s, to stay out of a
  partner's way), and the PRACTICE / COMPETITION profile. D-pad up/down chooses a line, left/right changes it.
- **The 30 s timer:** the steps stop when autonomous is over, and everything stops at the end, even after an error.
- **The report:** one line per step on the Driver Station when it ends (`done`, `timed out`,
  `cut short (time for park)`, ...). It is also in the robot log and on TeleOp's INIT screen, so the drive
  team can read it while they set up. [What each word means](../../guides/autonomous-builder.md#the-report).
- **Handing over to TeleOp:** the alliance and the robot's heading go to TeleOp (field-centric stays
  right), and thanks to `preselectTeleOp` the Driver Station has the TeleOp chosen. Autonomous ends as
  soon as its steps are done.

## Try it in the simulator

From the `ftc-helper-lib` folder, write a simulator robot file from the lesson robot's `Robot.java`, then
start the simulator with the library's examples:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.lessonbot.Robot -Pout=build/lessonbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/lessonbot.properties
```

Open http://localhost:8780, choose "Lesson Bot Score and Park", and press INIT. Read the note and the
menu; you will see "Drive not tuned: runs in the simulator only". Press START and watch it drive, lift,
drop and strafe. Read the report at the end. ([Simulator guide](../../guides/simulator.md).)

**Run it again? Press "Back to start" first** (on the page: Control Hub panel, Robot tab). This autonomous's
`start(note, 0)` gives no spot on the field (lesson 7 adds one), so the simulator leaves the robot
where the last program stopped it. "Back to start" puts the robot and the game pieces back.

**Your own robot:** pick **Simulator** in the list next to Android Studio's green Run button, and click Run. Or, from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:run
```

Choose your own autonomous, INIT, START, and read its report at the end. The simulator runs it even
before the Drive Tuner (`calibrated = false`); a real robot does not. Changed a step? Stop the
simulator (the red Stop button, or **Ctrl+C** in the Terminal) and start it again.

**See the park rule work.** In **your own** `autos/ScoreAndPark.java`, change `parkWithSeconds(3)` to
`parkWithSeconds(28)`, and run it again: the score steps are "cut short" or "skipped", and the park
still runs. Then change it back. (Do it in your file, not the lesson robot's: that one is inside the
library folder, and the next library update would undo it.)

**Or build the steps on the field.** Press **Auto builder** over the 3D view. Tap the field to add drives,
add the other steps from the list, write the note for the drive team ("Write it for me"), and press
**Run**: the simulator runs the steps with your `Robot.java` and `Actions.java` and shows where your robot
left the plan, with fixes to apply. When it works, **Copy the Java** gives you this lesson's file with your
steps in it. Why still learn the file? You change it at events, and the builder does not do everything
(menus, your own steps). ([The auto builder](../../guides/simulator.md#the-auto-builder).)

## Check it on the robot

1. In INIT, choose the **PRACTICE** profile in the menu. Every wheel is then held to
   `Tunables.Drive.practiceMaxPower` (0.6). For the very first runs, set it lower (0.4).
2. First run on blocks, wheels off the floor: do the wheels turn the right way, does the lift rise?
3. Then on the field. Put the robot exactly where the note says. Keep a hand on STOP.
4. Read the report after every run. Measure where it really ended and fix `Tunables.Auto`, not the steps.
5. When it works every time, run it in the **COMPETITION** profile, at match power.

## If something goes wrong

| What you see | Why, and what to do |
| --- | --- |
| "AUTONOMOUS IS OFF: the drive is not tuned yet" | `Tunables.Drive.calibrated` is false. Do step 1, then set it true. |
| It drives the wrong distance | the wheel size is off: run the Drive Tuner's Drive test again. |
| It strafes too far or too short | `Tunables.Drive.strafeCorrection`: the Strafe test again. |
| It turns the wrong way at the start, or field-centric is wrong in TeleOp | the heading in `start(...)`, or how the hub is mounted (`imu(...)` in `Robot.java`). |
| The report says `STALLED` or `TIMED_OUT` for a move | it was pushed into something, or the route is longer than the field. |
| "Score high" says `timed out` | the lift did not reach "high" in 2 s: check the lift's numbers (lesson 4). |
| RED drives into the wall | check `mirrorForRed()` against the field drawing. |
| TeleOp is not chosen after autonomous | `preselectTeleOp` must match the TeleOp's name exactly. |

## Next

The main track ends here: lessons 1 to 6. After that, do only the lessons your robot needs:

- [Lesson 7: Odometry: where am I on the field?](07-odometry.md): for a mecanum robot with an odometry
  device. Autonomous drives to field points.
- [Lesson 8: Paths with Pedro Pathing](08-paths-with-pedro.md) (optional, after lesson 7): smooth curves
  and lines.
- [Lesson 9: A shooter, a camera and a turret](09-shooter-and-camera.md): for robots that shoot.

When your robot works: [Lesson 10: Test your robot code](10-test-your-robot-code.md) (advanced). The
simulator records a test of this autonomous and checks it after every change.

**Everything else the builder can do** (menu choices, branches, your own steps, recipes):
[The autonomous builder](../../guides/autonomous-builder.md).

**More than one autonomous** (near and far starts, a safe plan and a risky one, choosing in INIT, a
match-day checklist): [Several autonomous modes](../../guides/autos.md). Save your work with git after
each run that works: [Git and GitHub for FTC teams](../../guides/github.md).

**Under the hood:** [09 Autonomous by hand with AutoDrive](../09-first-auto.md) (`AutoDrive` and tuning by hand),
[10 Autonomous with mechanisms](../10-auto-with-mechanisms.md) (the loop hook, mirroring, handing over),
[16 Easy autonomous](../16-easy-autos.md) (`EasyAuto`, the layer under `Auto`).
