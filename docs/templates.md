# Pick your template

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** a team that starts its robot code. **You need:** the library in your robot project
([Add it to a project](01-add-to-project.md)). New to the library? Start at
[Pick your road](quick-start.md#pick-your-road) in the Quick start: it uses this page in its place and
says what comes after (the configuration, the Hardware Check, the finders, the first autonomous).

A **template** is robot code you copy and then change. Start from the one that looks most like your
robot: you delete less and type less. Most of them (all but the last row) are a robot in five files:
`Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java` and an `autos` folder
([Lesson 1](tutorials/start/01-robot-in-five-files.md) explains them). The exceptions:

- `odometrybot` has no `Actions.java` (it needs none), and `pedrobot` has no `Controls.java` (so no
  TeleOp of its own) and no `Actions.java`. Use `odometrybot`'s `Controls.java`, or your own, with it.
- Some have extra files: a finder that knows the robot (`lessonbot/DriveTuner.java`,
  `odometrybot/OdometryTuner.java`, `turretbot/HoodFinder.java` and `ShotTuner.java`), or Pedro's
  numbers (`pedrobot/PedroConstants.java`). Copy them with the rest.

## Your robot looks like this: start from that

| Your robot looks like... | Start from | What it has |
| --- | --- | --- |
| **anything, and its configuration is on the Driver Station** | `./gradlew :sim:newRobot` ([Quick start, way A](quick-start.md#a-from-your-robot-configuration-best-if-it-is-already-set-up)) | five files made for you from your configuration, with each part's line and TODO marks, and every setting in `Tunables.java` as `DEFAULT` or `MEASURE` ([the two words](guides/tunables.md#default-and-measure)) |
| a mecanum drive and an intake, no configuration yet | [`quickstart`](../examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart) ([Quick start, way B](quick-start.md#b-copy-the-template-no-configuration-yet-or-you-want-to-see-every-line)) | the smallest robot, with STEP marks on every line to change |
| a mecanum drive, an intake, a lift and a claw | [`lessonbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot) | the lesson robot of lessons 2 to 6, and its Drive Tuner |
| **goBILDA's FTC StarterBot** (BIOBUZZ) | [`gobildabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot) | tank drive, intake with two corner wheels, flywheel and "windmill" feeder; goBILDA's own names |
| **REV's DUO FTC Starter Bot** (BIOBUZZ) | [`revbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot) | tank drive, intake, flywheel and CR-servo feeder, the pollen pusher ("poker"); REV's own names |
| **AndyMark's Robits BIOBUZZ StarterBot** | [`andymarkbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) | tank drive, roller intake, the catapult (see the note below); AndyMark's own names |
| **Studica's FTC Starter Bot** (BIOBUZZ) | [`studicabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot) | tank drive, intake, flywheel and servo flap (see the note below); Studica's own names |
| a tank drive and a flywheel shooter | [`flywheelbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot) | intake on from the start, a deploy servo, a flywheel the driver speeds up and down, a servo feeder, a webcam and whole-robot aim (no turret) |
| a mecanum drive with a turret and a camera | [`turretbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot) | a Limelight that the turret follows, speed and hood angle from distance with a POLLEN and a NECTAR table (a hood servo, Hood Finder), a buzz when on target, a folding claw, a Shot Tuner ([lesson 9](tutorials/start/09-shooter-and-camera.md)) |
| a turret shooter AND a lift with a wrist and a claw (two parts that share space) | [`v2bot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot) | team 8938's 2026-27 designs: a turret on a magnetic home switch (+/-190 degrees), a hood, odometry, a piece count, a Viper-Slide lift with a wrist and a claw, the lifter's steps as actions, and an [interlock rule](guides/interlocks.md) that keeps the turret out of the back while the wrist holds the claw low behind the robot |
| a mecanum drive with odometry (Pinpoint, OTOS or dead wheels) | [`odometrybot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot) | `driveTo` field points, the Odometry Tuner ([lesson 7](tutorials/start/07-odometry.md)) |
| a mecanum drive with odometry and Pedro Pathing | [`pedrobot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/pedrobot) | `odometrybot` plus paths ([lesson 8](tutorials/start/08-paths-with-pedro.md)); add Pedro to the project first ([lesson 8, section 3](tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)) |
| you already use the older six-file starter kit | [`starter`](../examples/src/main/java/com/crossbotics/ftchelper/examples/starter) ([Starter kit](starter-kit.md)) | `EasyTeleOp` and `EasyAuto`; kept for teams that use it. New teams: use a row above |

Not sure? Use `:sim:newRobot` if your configuration is ready, and `quickstart` if it is not. You can
copy single lines from the other templates later (for example the launcher line from `flywheelbot`).

**The kit bots** use the same configuration names as each seller's own code, so the files run on that
kit as it is. Where each number comes from, and what each kit bot's code does differently (buttons,
guessed numbers): [The four BIOBUZZ starter bots](guides/starter-bots.md) ([REV](guides/starter-bots.md#rev-bot-code),
[goBILDA](guides/starter-bots.md#gobilda-bot-code), [AndyMark](guides/starter-bots.md#andymark-bot-code), [Studica](guides/starter-bots.md#studica-bot-code)).
Two kit mechanisms are not parts of the library, so their templates use the closest part and say so
in `Robot.java`:

- **AndyMark's catapult:** the library has no catapult. The template drives its motor as an `Indexer`
  that runs for a set time per press. Tune that time so one press is one throw. The library cannot
  tell when the catapult is wound, so it cannot wait for "ready" as a flywheel does.
- **Studica's flywheel:** the launcher cannot take the 2:1 gear between the motor and the wheels. The
  flywheel speed in `Tunables.java` is the motor's (the wheels turn at half of it). Tune it by watching
  shots. The Maverick motors and the servo are not in the parts catalog: `Robot.java` describes them
  with Studica's numbers ([Parts not in the catalog](modules/robot.md#parts-not-in-the-catalog)).

## Copy a template into TeamCode

`:sim:newRobot` writes the files into TeamCode for you: skip this part. For every other template:

1. Copy the template's `.java` files into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
   Make a folder `autos` there (right-click `teamcode`, **New > Package**, `autos`) and copy the files
   from the template's `autos` folder into it.
2. **Change the first line of each file** (the `package` line) to the folder it is in now:
   - files next to `Robot.java`: `package org.firstinspires.ftc.teamcode;`
   - files in `autos`: `package org.firstinspires.ftc.teamcode.autos;`

   **Why:** the `package` line must match the folder the file is in, or it does not build.
3. In the files in `autos` (and any other file that imports them), change the imports of the
   template's classes to your package. For example, in `gobildabot/autos/LaunchAndBackUp.java`:

   ```java
   import com.crossbotics.ftchelper.examples.gobildabot.Robot;   // before
   import org.firstinspires.ftc.teamcode.Robot;                 // after
   ```

   Do the same for `Actions` and `Tunables`. Android Studio marks every line it cannot find in red.
4. **Delete the `@Disabled` line** in every OpMode file you copied: `Controls.java`, each autonomous,
   and the finder files (`DriveTuner.java`, `OdometryTuner.java`, `HoodFinder.java`, `ShotTuner.java`).
   Delete its `import com.qualcomm.robotcore.eventloop.opmode.Disabled;` line too. **Why:** the examples are hidden on
   the Driver Station with `@Disabled`, so they do not fill the list on a real robot.
5. Change the names in `@TeleOp(name = ...)` and `@Autonomous(name = ..., preselectTeleOp = ...)` to
   your own; `preselectTeleOp` must be the TeleOp's name exactly.
6. Build. Then check the motor directions with the wheels off the ground
   ([Lesson 2](tutorials/start/02-drive.md)), and every part with the Hardware Check:
   [Check the hardware (lesson 2b)](tutorials/19-testing-procedures.md).

**Numbers:** every number in `Tunables.java` is a starting value. Find the real ones with the
[finders](guides/finders.md). If you change a number live on the robot (the tuning menu), copy the new
value back into `Tunables.java`: INIT lists the values that differ. If you do not, the next build loses it.

**Try it first in the simulator.** The kit bots have robot files: for example
`./gradlew :sim:run -Probot=sim/robots/starter-bot-rev.properties -PteamCode=none`, then pick
"REV Bot TeleOp" ([Simulator guide](guides/simulator.md)). `-PteamCode=none` runs the **library's**
example, not your copy.

**Your copy in the simulator.** Run your TeamCode with the kit's measured robot file:
`./gradlew :sim:run -Probot=sim/robots/starter-bot-rev.properties -PteamCode=../TeamCode` (or copy
that file to `simulator/robot.properties` in your robot project, and then `-Probot` is not needed).
Use the kit's file, not one from `:sim:fromRobot`: that one guesses an 18 x 18 in robot, and the
REV, AndyMark and Studica bots then start "overlapping the field wall" on BLUE.

**What the simulator cannot show yet** (so you know it is not your copy):

- The REV, AndyMark and Studica bots do not shoot in the simulator: their robot files have no shot
  line. Their flywheels may also never say `READY`, because the example speeds are higher than the
  simulated motor reaches. Tune the speed on the real robot.
- An autonomous whose `start(...)` gives no spot (only a note and a heading) starts where the last
  program left the robot. Press **Back to start** (Control Hub panel, Robot tab) before you run it.
- Some INIT lines are normal in the simulator: `!! Drive not tuned: runs in the simulator only`
  (on the robot, the autonomous waits for the Drive Tuner), a turret's
  `Turret : no angle from a program before: ...` (turn it to its start angle on the robot), and a
  camera's `limelight lighting : trying ...` until it sees a tag.

## Next

[Lesson 1: The robot in five files](tutorials/start/01-robot-in-five-files.md): what each file is for.
Then the rest of the [main track](tutorials/README.md#start-here-the-main-track).
