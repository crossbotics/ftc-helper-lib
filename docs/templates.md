# Pick your template

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** a team that starts its robot code. **You need:** the library in your robot project
([Add the library](setup/04-add-the-library.md)). If you are new to the library, start at
[Pick your start](pick-your-start/README.md): short pages for the same choice, one per start.

A **template** is robot code you copy and then change. Start from the one that looks most like your
robot: you delete less and type less. Most of them (all but the last row) are a robot in five files:
`Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java` and an `autos` folder
([The five files](five-files/README.md) explains them).

Some have extra files: a finder that knows the robot (`lessonbot/DriveTuner.java`,
`odometrybot/OdometryTuner.java`, `turretbot/HoodFinder.java` and `ShotTuner.java`), or Pedro's
numbers (`pedrobot/PedroConstants.java`). Copy them with the rest.

## Your robot looks like this: start from that

| Your robot looks like... | Start from | What it has |
| --- | --- | --- |
| **anything, and its configuration is on the Driver Station** | `./gradlew :sim:newRobot` ([Blank slate, way A](pick-your-start/03-blank-slate.md#way-a-from-your-configuration)) | five files made for you from your configuration, with each part's line and TODO marks, and every setting in `Tunables.java` as `DEFAULT` or `MEASURE` ([the two words](reference/tunables.md#default-and-measure)) |
| a mecanum drive and an intake, no configuration yet | [`quickstart`](../examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart) ([Blank slate, way B](pick-your-start/03-blank-slate.md#way-b-copy-the-smallest-files)) | the smallest robot, with STEP marks on every line to change |
| a mecanum drive, an intake, a lift and a claw | [`lessonbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot) | the example robot of The five files and Add to your robot, and its Drive Tuner |
| **goBILDA's FTC StarterBot** (BIOBUZZ) | [`gobildabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot) | tank drive, intake with two corner wheels, flywheel and "windmill" feeder; goBILDA's own names |
| **goBILDA's FTC StarterBot with Mecanum Wheels** (BIOBUZZ, Strafer chassis) | [`gobildastraferbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildastraferbot) | mecanum drive, intake with two corner wheels, flywheel and "windmill" feeder; goBILDA's own names |
| **REV's DUO FTC Starter Bot** (BIOBUZZ) | [`revbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot) | tank drive, intake, flywheel and CR-servo feeder, the pollen pusher ("poker"); REV's own names |
| **AndyMark's Robits BIOBUZZ StarterBot** | [`andymarkbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) | tank drive, roller intake, the catapult (see the note below); AndyMark's own names |
| **Studica's FTC Starter Bot** (BIOBUZZ) | [`studicabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot) | tank drive, intake, flywheel and servo flap (see the note below); Studica's own names |
| a tank drive and a flywheel shooter | [`flywheelbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot) | intake on from the start, a deploy servo, a flywheel the driver speeds up and down, a servo feeder, a webcam and whole-robot aim (no turret) |
| a mecanum drive with a turret and a camera | [`turretbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot) | a Limelight that the turret follows, speed and hood angle from distance with a POLLEN and a NECTAR table (a hood servo, Hood Finder), a buzz when on target, a folding claw, a Shot Tuner ([Add a launcher](add-to-robot/05-launcher.md)) |
| a turret shooter AND a lift with a wrist and a claw (two parts that share space) | [`v2bot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot) | team 8938's 2026-27 designs: a turret on a magnetic home switch (+/-190 degrees), a hood, odometry, a piece count, a Viper-Slide lift with a wrist and a claw, the lifter's steps as actions, and an [interlock rule](guides/interlocks.md) that keeps the turret out of the back while the wrist holds the claw low behind the robot |
| a mecanum drive with odometry (Pinpoint, OTOS or dead wheels) | [`odometrybot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot) | `driveTo` field points, the Odometry Tuner ([Add odometry](add-to-robot/09-odometry.md)) |
| a mecanum drive with odometry and Pedro Pathing | [`pedrobot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/pedrobot) | `odometrybot` plus paths ([Add Pedro paths](building-autos/05-pedro-paths.md)); add Pedro to the project first ([Add Pedro paths](building-autos/05-pedro-paths.md#your-project-add-the-pedro-add-on-once)) |
| you already use the older six-file starter kit | [`starter`](../examples/src/main/java/com/crossbotics/ftchelper/examples/starter) ([Starter kit](starter-kit.md)) | `EasyTeleOp` and `EasyAuto`; kept for teams that use it. New teams: use a row above |

**If you are not sure:** use `:sim:newRobot` if your configuration is ready, and `quickstart` if it is not. You can
copy single lines from the other templates later (for example the launcher line from `flywheelbot`).

**The kit bots** use the same configuration names as each seller's own code, so the files run on that
kit as it is. Where each number comes from, and what each kit bot's code does differently (buttons,
guessed numbers): [The BIOBUZZ starter bots](guides/starter-bots.md) ([REV](guides/starter-bots.md#rev-bot-code),
[goBILDA](guides/starter-bots.md#gobilda-bot-code), [AndyMark](guides/starter-bots.md#andymark-bot-code), [Studica](guides/starter-bots.md#studica-bot-code)).
Two kit mechanisms are not parts of the library, so their templates use the closest part and say so
in `Robot.java`:

- **AndyMark's catapult:** the library has no catapult. The template drives its motor as an `Indexer`
  that runs for a set time per press. Tune that time so one press is one throw. The library cannot
  tell when the catapult is wound, so it cannot wait for "ready" as a flywheel does.
- **Studica's flywheel:** the launcher cannot take the 2:1 gear between the motor and the wheels. The
  flywheel speed in `Tunables.java` is the motor's (the wheels turn at half of it). Tune it by watching
  shots. The Maverick motors and the servo are not in the parts catalog: `Robot.java` describes them
  with Studica's numbers ([Parts not in the catalog](reference/robot-java.md#parts-not-in-the-catalog)).

## Copy a template into TeamCode

If you used `:sim:newRobot`, skip this part: it writes the files into TeamCode for you. For every other template,
follow [Copy example files into TeamCode](pick-your-start/04-copy-into-teamcode.md): the `package` lines,
the imports, the `@Disabled` lines and the OpMode names.

Then build. Check the motor directions with the wheels off the ground
([Robot.java](five-files/01-robot-java.md)). Check every part with the Hardware Check:
[Check the hardware](tutorials/19-testing-procedures.md).

**Numbers:** every number in `Tunables.java` is a starting value. Find the real ones with the
[finders](tuning/README.md). The hub saves a number you change live on the robot (in the tuning menu).
It keeps using that saved value, even after you edit the number in `Tunables.java`, and another hub does
not have it. So copy each new value back into `Tunables.java`. INIT lists the values that differ.

**Try it first in the simulator.** The kit bots have robot files. Run one, then pick
"REV Bot TeleOp" ([Simulator guide](simulator/README.md)). `-PteamCode=none` runs the **library's**
example, not your copy.

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](setup/09-the-terminal.md#which-folder))

`./gradlew :sim:run -Probot=sim/robots/starter-bot-rev.properties -PteamCode=none`

**Your copy in the simulator.** Run your TeamCode with the kit's measured robot file:

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](setup/09-the-terminal.md#which-folder))

`./gradlew :sim:run -Probot=sim/robots/starter-bot-rev.properties -PteamCode=../TeamCode`

You can also copy that file to `simulator/robot.properties` in your robot project. Then you do not need
`-Probot`. Use the kit's file. A file from `:sim:fromRobot` guesses an 18 x 18 in robot. Then the
REV, AndyMark and Studica bots start "overlapping the field wall" on BLUE.

**What the simulator cannot show yet** (so you know the problem is not in your copy):

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

[The five files](five-files/README.md): what each file is for.
Then [Add to your robot](add-to-robot/README.md): one page per part.
