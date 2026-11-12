# Lesson 1: The robot in five files

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You learn:** how to write a whole robot program in five short files. They hold what is on the robot
(`Robot.java`), its numbers (`Tunables.java`), its buttons (`Controls.java`), combo moves (`Actions.java`)
and the autonomous programs (`autos/`). Then you start your own robot from its configuration and try it
in the simulator.

**Robot needed:** none to start (the simulator), then your robot.

This is **lesson 1** of the [main track](../README.md#start-here-the-main-track). **Before this:** the
[Quick start](../../quick-start.md), or at least [Add the library](../../setup/04-add-the-library.md). New to
Java: [Under-the-hood tutorial 01](../01-opmode-basics.md) (not this lesson) explains imports, fields and lambdas.

**Which robot:** you build your own robot, in the five files the Quick start put in your TeamCode.
This lesson uses a worked example: goBILDA's BIOBUZZ starter bot, rewritten in this style, in
[`examples/.../gobildabot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot).
It has a two-motor drive, an intake and a launcher. Lessons 2 to 6 use the **lesson robot**
([`examples/.../lessonbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot)) the
same way: read its lines, make the same change in your own files, and use its files as the answer key.
The simulator commands with `-PteamCode=none` run the library's examples. For your own robot, use the
**Simulator** button in Android Studio (pick it in the list next to the green Run button, click Run), or
`./gradlew :sim:run`. Both build your `../TeamCode`.
Three more complete robots are next to it:
[`flywheelbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot)
(an intake that runs from the start, a flywheel the driver speeds up and down, an indexer servo, a
deploy servo at ▶ (start on the Driver Hub)) and
[`turretbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot)
(a turret with a Limelight that follows the goal inside its range, speed from distance, a folding claw),
and [`v2bot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot) (a turret shooter
plus a lift with a wrist and a claw, kept apart by an interlock rule).
The other BIOBUZZ kit bots (REV, AndyMark, Studica) and more example robots are there too. To pick
the one most like your robot, see [Pick your template](../../templates.md).
The reference for every method: [the robot module](../../reference/robot-java.md).

## Java you will meet

The five files use a few Java ideas over and over. You need no more than this to start, and you
need not understand every word yet. Read the list once, and come back to it when a lesson uses one.

- `public class Robot extends RobotDefinition`: **extends** means "built on". Your class gets
  everything the library class already does. The library class is **abstract**: an unfinished class
  that leaves a method or two for you to fill in (here `define()`). Your file marks each one `@Override`.
- `ControlMap<Robot>`, `Auto<Robot>`: the name in angle brackets (**generics**) says which class the
  library class works with: your `Robot`.
- `Drive.tank(...).motor(...).wheelMm(96)`: **method chaining**. Each method hands the same thing back,
  so the next one adds to it with another dot. Read it left to right as one sentence.
- `() -> r.launcher.fire(4)`: a **lambda**, a small piece of code you hand over now, to run later (when
  a button is pressed, or an action gets to that step). `() ->` means "no inputs, run this".
- `r.intake::in`: a **method reference**, the short way to write the lambda `() -> r.intake.in()`:
  "the intake's `in` method".
- `Runnable`: Java's name for "a piece of code with no inputs that can be run later". A lambda or a
  method reference like the two above is a `Runnable`, so the methods that take one (`press(...)`,
  `then(...)`) take either.

**Android Studio tip:** when Android Studio shows a name in red, the file often needs an `import` line
at the top. Click the name and press **Alt+Enter** (**Option+Enter** on a Mac), and Android Studio adds the
missing import. If it offers two, pick the one in `com.crossbotics.ftchelper.robot` for the five files' parts.

## Why five files

goBILDA's starter TeleOp is one file of about 200 lines. The motor names, the directions, the
launcher speed, the buttons and the driving are all mixed together. To change the launcher speed, you
must find it among the rest. To add an arm, you change the whole file.

Here each file answers ONE question:

| File | Answers |
| --- | --- |
| `Robot.java` | What is on the robot, and its name on the hub? |
| `Tunables.java` | What are the numbers? |
| `Controls.java` | Which button does what? (it is also the TeleOp) |
| `Actions.java` | Which steps use several parts together? |
| `autos/` | What does the robot do on its own? |

`autos/` is a folder: one file per autonomous.

Each mechanism is a **module**: a ready-made part class from the library, like `Intake` or `Lift`.
(A Gradle module is a different thing: a part of the build.) A module already knows how to work. An
`Intake` can run in and out. A `Launcher` spins up and feeds one piece when the wheel is at speed. A
module brings its numbers and its simulator lines with it. You write its buttons as lines in `Controls.java`.

## 1. Robot.java: what is on the robot

```java
public class Robot extends RobotDefinition {
    public Intake intake;
    public Launcher launcher;

    @Override
    protected void define() {
        drive(Drive.tank("left_drive", "right_drive")
                .motor("gobilda-5203-312").wheelMm(96)
                .reversed("right_drive")
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));

        intake = add(Intake.motor("intake").part("gobilda-5203-117")
                .withCrServos("left_intake_servo", "right_intake_servo")
                .reversed("right_intake_servo")
                .holds(4).holds(0, "NECTAR"));

        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.crServo("windmill").part("gobilda-2000-torque").reversed()));
    }
}
```

- `extends RobotDefinition`: your `Robot` is built on a library class, so it gets all its work for
  free ([Java you will meet](#java-you-will-meet)). `@Override` marks a method that fills in one the
  library class leaves to you, here `define()`. The library calls it for you.
- The names in quotes are the names in the robot configuration on the Driver Station. They must
  match exactly, capitals too. When a name is wrong, INIT stops and lists the names that DO exist.
- `"gobilda-5203-312"` is the motor, by its id in the [parts catalog](../../guides/parts-catalog.md). The
  library works out encoder counts and speeds from it. (The starter bot's launcher is a 312 RPM motor
  with its gearbox taken off, so it has the 6000 RPM motor's numbers.)
- `.part("gobilda-5203-117")`: which motor or servo the part is, by the id on its label. It is
  optional but recommended. With it, the starting values are that part's real numbers, and the simulator
  moves it like the real one. A launcher has its motor's id in its brackets instead.
- `.holds(4).holds(0, "NECTAR")`: it carries 4 POLLEN and no NECTAR. A NECTAR is too big for it.
- `.reversed("right_drive")`: the right motor is mounted facing the other way. If your robot drives
  backward or spins when you push the stick forward, change which motors are reversed.
- `add(...)` hands the module back, so `intake = add(...)` keeps it in a field (a variable that
  belongs to the class, like `public Intake intake;` at the top). Controls, Actions and the autos
  reach it as `robot.intake`.
- `define()` only DESCRIBES the robot. Nothing moves in INIT. The modules move only after ▶.
- **A part that is not built yet:** write its line anyway and add `.optional()`:
  `intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque").optional());`.
  Until "intake_deploy" is in the configuration, INIT says the part is off and goes on. Its buttons and
  actions skip it. When you wire and configure it, it works with no change
  ([parts not on the robot yet](../../reference/robot-java.md#parts-not-on-the-robot-yet)).

## 2. Tunables.java: the numbers

```java
import static com.crossbotics.ftchelper.robot.Starting.*;   // DEFAULT (1)!

public class Tunables {
    public static class Drive {
        public static double maxPower = DEFAULT;                    // top wheel power (1.0)
        public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (same as maxPower)
        public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
        public static double strafeCorrection = DEFAULT;            // not used: a tank drive cannot strafe (1.15)
        public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
        public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
        public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
        public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
    }

    public static class Intake {
        public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
        public static double outPower = 1.0;                        // power pushing pieces out: goBILDA's code
    }

    public static class Launcher {
        public static double speedRpm = 2679;                       // shot speed: goBILDA's 1250 counts per second
        public static double stepRpm = 50;                          // speed change per D-pad press
        public static double readyBandRpm = 107;                    // "at speed" this close: goBILDA feeds above 1200
        public static double idleRpm = DEFAULT;                     // speed between shots, 0 = stop (0)
        // goBILDA's tested gains for this kit bot (its code: P 40, F 12.5). (2)!
        public static double kP = 40;                               // push per speed error: goBILDA's number
        public static double kI = 0;                                // fix a steady error: goBILDA's number
        public static double kD = 0;                                // brake changes: goBILDA's number
        public static double kF = 12.5;                             // steady power for a speed: goBILDA's number
        public static double nominalVolts = 12.8;                   // battery volts the gains were found at
        public static double shotIntervalSec = DEFAULT;             // least time between two pieces, s (0.25)
    }

    public static class Windmill {
        public static double power = DEFAULT;                       // feeder power, 0 to 1 (1.0)
        public static double feedSec = 0.3;                         // seconds per piece: a starting guess (3)!
    }
}
```

1. `DEFAULT` and `MEASURE` come from this one import line. The two words are explained below, and in
   [the Tunables guide](../../reference/tunables.md#default-and-measure).
2. Without these four lines, the library starts from the parts catalog's numbers for this motor
   (kF 11.7, kP 1.17, kI 0.117). goBILDA tested its own numbers on this robot, so we keep them.
3. goBILDA's code runs the windmill for as long as the driver holds the button. Here it runs `feedSec` per
   piece. 0.3 s is a first guess: watch it feed, and tune it.

- **One number per line**, each with a short comment (`// ...`) that says what it is. The number in
  brackets at the end is the library's default.
- `static` means the number belongs to the class itself, not to one object. So any file can read it
  as `Tunables.Drive.maxPower`, with no setup.
- One **section** per part. Its name is the part's configuration name in CamelCase: each word starts
  with a capital letter, with no spaces or `_`. Device `"windmill"` reads `Tunables.Windmill`, and
  `"intake_deploy"` would read `IntakeDeploy`.
- Where these came from: goBILDA's code spins the launcher to 1250 counts per second (1250 / 28 counts
  per turn x 60 = 2679 RPM). It feeds above 1200, which is 107 RPM less: `readyBandRpm = 107`.
  `kP` to `kF` are goBILDA's tested numbers too. `DEFAULT` means "use the library's default"
  (more below). Here it marks the numbers where the library's default is the right one.
- A number you leave out uses the library's default. In PRACTICE, INIT's `Library starting values` line
  names the ones written `DEFAULT`. The robot log has every value, and the tuning menu shows each one. A
  few safety numbers, like a lift's `min` and `max`, have no default: INIT stops and says which line to write.
- **Numbers you do not have yet:** write a word in their place, so every setting is in the file from day
  one. Which one to write:

  | The number | Write |
  | --- | --- |
  | You know it | the number |
  | A safety limit (a lift's `min`/`max`, a stop) you do not know yet | `MEASURE` |
  | A tuning number (`kP`, a speed...) you do not know yet | `DEFAULT` |

  `DEFAULT` means "use the library's default". Where it can, the library works it out from the motor
  or servo that Robot.java names. INIT lists each one and where it came from: `Launcher: defaults from the catalog
  (gobilda-5203-6000): kF 11.7, ...`. It does the same as leaving the line out, but with the word the
  file shows what is still to tune. So write the word. `MEASURE` means "a number to find on the robot":
  INIT stops on it and names the finder. Use them only on `public static double` lines. They need one
  import line, under the `package` line and above `public class Tunables`:

  ```java
  package org.firstinspires.ftc.teamcode;

  import static com.crossbotics.ftchelper.robot.Starting.*;

  public class Tunables {
      public static class Lift {
          public static double min = MEASURE;                       // lowest safe point (Motor Finder)
          public static double max = MEASURE;                       // highest safe point (Motor Finder)
          public static double kP = DEFAULT;                        // push toward the target (from the motor)
      }
  }
  ```

  Without that line the build fails with `cannot find symbol` and `symbol: variable DEFAULT`
  ([the two words](../../reference/tunables.md#default-and-measure)). The goBILDA bot's
  file has that line at the top, and no `MEASURE`. Every number is known: goBILDA published it, or
  the library's default is right.
- **Every number each part reads**, with its units, its default and the finder that measures it:
  [the Tunables reference](../../reference/tunables.md). **A misspelled name stops INIT** (capitals
  count). INIT names the closest real name, for example
  `Tunables.Intake.inPowr is not a setting of Intake (did you mean inPower?): the robot would quietly ignore it. ...`.
  Fix the name, or delete the line. **Why INIT stops:** otherwise the part would quietly use its default,
  and you would never know that it did not use your number. An autonomous's distances are in its steps,
  not here ([why](../../five-files/05-autos.md#the-numbers-are-in-the-step)).
- **Tune on the robot:** in the PRACTICE profile, after ▶, hold GUIDE for a second to open the tuning
  menu. The D-pad chooses and changes, and A saves on the hub. At the next INIT the Driver Station lists
  every value that differs from this file, for example `Tunables.Launcher.speedRpm = 2750 (code 2679)`.
  Copy the good ones into this file, so the file stays the record.

## 3. Controls.java: the buttons (and the TeleOp)

```java
@TeleOp(name = "goBILDA Bot TeleOp")
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    // Always pre-assigned on gamepad 1 (the library's): sticks: drive; left stick click: slow; BACK: pause;
    // GUIDE: button map (held: tuning menu); START + BACK: debug lines; X / B in INIT: alliance.

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // The intake's own buttons.
        g2.rt.hold(robot.intake::holdIn, robot.intake::stop).says("in (hold)");
        g2.lt.hold(robot.intake::out, robot.intake::stop).says("out (hold)");

        // The launcher's own buttons.
        g2.lb.toggle(robot.launcher).says("spin up / down");
        g2.rb.hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("fire one; hold: keep firing");
        // (the D-pad's speed buttons: see the file)

        // Ours.
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
```

`@TeleOp(name = ...)` is the name on the Driver Station. `ControlMap<Robot>` is a whole TeleOp from
the library. The `<Robot>` in angle brackets says which robot it is for: yours.

The file has two kinds of buttons:

- **Always pre-assigned: the library's own**, on gamepad 1. The sticks drive, BACK pauses, and GUIDE shows
  the button map. You write nothing for them, and `libraryButtons(...)` can move them.
- **Each part's buttons, written out:** the intake on the triggers, the launcher on the bumpers. A part has
  no buttons until its lines are here, so you can read, change, move or delete each one. `oneDriver = true`
  puts the `g2` lines on gamepad 1 too, like goBILDA's code. **Ours** is the button the team added: Y fires all four.

- **INIT shows the map** on the Driver Station: every button with its job, and the free ones. During the
  match, a tap of GUIDE shows it again, without the free ones. Tap again to close it.
- **BACK pauses the robot:** everything stops on **brake** (the motors hold still instead of coasting).
  Press BACK again, and each part goes on as before. With field-centric on, hold BACK 1 s instead to
  reset "forward". In robot-centric driving, the hold does nothing. STOP brakes everything too.
- Each button takes ONE job. INIT stops and names the button when this file puts two jobs on one button,
  or a job on a driving button (the left stick, LT slow...).
- The kinds of jobs: `press(...)`, `hold(whileHeld, onRelease)`, `toggle(robot.intake)`,
  `release(...)`, and for sticks `g2.leftStickY.use(robot.lift::manual)`. `robot.lift::manual` means
  "the lift's `manual` method": the library calls it later, every loop. Lesson 3 has them all.
- **The loop:** a robot program runs a **loop**. Many times a second, it reads the gamepads, runs the
  button jobs and updates every part. Each time round is "a loop". The library runs this loop for
  you. `buttons(...)` runs only once, at INIT, and plugs a job into each button
  ([From Blocks to Java](../../blocks-to-java.md) explains it with blocks).

## 4. Actions.java: steps that use several parts

```java
public static Action fireAllWithIntake(Robot r) {
    return Action.named("Fire all")
            .then(r.intake::in, () -> r.launcher.fire(4))
            .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
            .then(r.intake::stop);
}
```

`r.intake::in` (a method reference) and `() -> r.launcher.fire(4)` (a lambda) are two ways to write a
small piece of code that runs later, when the action gets to that step
([Java you will meet](#java-you-will-meet)).

An action is a list of steps: do these (`then`), wait (`waitSeconds`, `waitUntil`). It **never
blocks**: it never makes the program stop and wait. It does a little every loop, so the robot keeps
driving and every other button keeps working while it runs.

- `then(a, b)` does both at once. Here the intake helps push while the launcher fires.
- **Which parts an action uses matters.** The autonomous uses this one. For TeleOp the example has
  `fireAll`, which uses only the launcher, and the driver holds RT to run the intake while it fires. An
  action that also ran the intake would stop the moment the driver touched RT (see the next point).
- A `waitUntil` that times out STOPS the action, and the Driver Station says why. (A lift that never
  got to the top must not open the claw.)
- **The driver always wins:** when the driver moves one of the action's parts by hand, the action stops.
  Pressing its button again cancels it. A stopped action stops what it started, safely: a lift holds its
  height, the action drops the shots still waiting, and the intake stops.
- The same action works in an autonomous: `s.action(Actions.fireAll(robot))`.

## 5. autos/: the autonomous programs

```java
@Autonomous(name = "goBILDA Bot Launch and Back Up", preselectTeleOp = "goBILDA Bot TeleOp")
public class LaunchAndBackUp extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;     // BIOBUZZ's field is turned half a turn: the same route for both alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Back against our wall, left of center, facing the field", -56, -63, 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .forward("Away from the wall", 4.7, 0.4);              // goBILDA's auto: 120 mm, power 0.4
    }
}
```

`extends Auto<Robot>`: an autonomous from the library, for your `Robot`. You fill in
`steps(...)`. You add `mirrorForRed()` only when its answer is false (the next paragraph explains it).
Every angle in an autonomous counts counterclockwise, and 90 = left. That holds for a move's direction,
a turn, and the heading (the last number of `start(...)`). Straight moves can use words instead: `forward`, `back`,
`left`, `right` ([the autos/ page](../../five-files/05-autos.md#more-steps)).

Each autonomous is one short file that you read top to bottom. The INIT menu (alliance, start delay, the
profile) comes for free. You write moves and turns for BLUE, and the library mirrors them for RED. On a
field turned half a turn, like BIOBUZZ, the route is the same for both: `mirrorForRed()` false. The report
at the end says what happened to each step. For a new autonomous, copy the file and change its name and its steps.

Parking points come at the end, so a park step must not run out of time. Put `.parkWithSeconds(3)` right
before it. The steps above it stop when 3 s are left, and the steps below it always run
([Keep time to park](../../reference/steps.md#keep-time-to-park)). Everything else the steps can do (menus,
branches, actions, your own steps): [Building autos](../../building-autos/README.md).

The autonomous refuses to run until `Tunables.Drive.calibrated = true`. Tune the drive first with the Drive
Tuner ([tune the drive first](../../five-files/05-autos.md#tune-the-drive-first) shows how), then set it.

## Try it in the simulator

The simulator has goBILDA's starter bot, made from its CAD: `sim/robots/starter-bot-gobilda.properties`.
The examples are built into the simulator.

**Where:** the Terminal, in the `ftc-helper-lib` folder.

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

`-PteamCode=none` lists the library's examples instead of your own TeamCode's OpModes. (On a real robot
the examples are hidden, because they are marked `@Disabled`.) Open http://localhost:8780, pick
"goBILDA Bot TeleOp", press INIT (read the button map), then ▶. LB spins up, and RB fires (hold it to
keep firing). ([Simulator guide](../../guides/simulator.md).)

**Your own robot** (the five files in your TeamCode): in Android Studio, pick **Simulator** in the list
next to the green Run button, and click Run. When the list has no **Simulator**, close the project and
open it again ([the buttons](../../setup/04-add-the-library.md#the-simulator-buttons-you-do-nothing)).
Or use the Terminal.

**Where:** the Terminal, in the `ftc-helper-lib` folder.

```bash
./gradlew :sim:run
```

When it starts, the simulator writes your robot's simulator file, `simulator/robot.properties`, from
`Robot.java` (see below). Open http://localhost:8780, pick your TeleOp, press INIT, then ▶. To stop the
simulator, click the red **Stop** button (or press **Ctrl+C** in the Terminal). When you change your code,
stop the simulator and start it again.

### Draw your robot from Robot.java

The simulator draws the robot from its **robot file**: a text file that describes the robot to the
simulator (its size, parts, motors and shapes). Your team's is `simulator/robot.properties` in the robot
project. `Robot.java` can write that file for you, if it also
says what each part is and how it looks ([Shapes and parts](../../reference/robot-java.md#shapes-and-parts)).
Name each part by its catalog id. Give a size or a place only when the catalog does not know it.
(The code is shortened from [`flywheelbot/Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot/Robot.java),
inside `define()`. Adapt it to YOUR parts: keep your own lines, and add only the `.part(...)`,
`.looks(...)` and `.shotAngle(...)` pieces to the parts you have. With no launcher, leave its line out.)

```java
chassisMm(400, 400, 100);
intake = add(Intake.motor("intake").part("gobilda-5203-1150").holds(3)
        .looks(Shape.roller("gobilda-gecko-48-rex").atMm(190, 0, 60).lengthMm(300)));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.crServo("windmill").part("gobilda-2000-torque"))
        .looks(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 170)).shotAngle(50));
```

`Shape` needs one more import at the top of `Robot.java`: `import com.crossbotics.ftchelper.robot.Shape;`
(Alt+Enter on the red word adds it).

`holds(3)` only tells the simulator how many pieces fit. It does not stop the real intake at 3. To do that,
count the pieces and use `stopsWhen(...)` ([cookbook: stop the intake when the robot is full](../../cookbook.md#how-do-i-stop-the-intake-when-the-robot-is-full)).
In BIOBUZZ a robot may not hold more than 4 pieces at a time (G407).

Then start the simulator again (the **Simulator** button, or `./gradlew :sim:run`). It writes the robot
file from `Robot.java` first. Open http://localhost:8780. The roller spins when the intake runs, the robot
picks up a piece and fires it, and the simulator checks the size rules on your shapes. The flywheel example
(`examples/.../flywheelbot/Robot.java`) also has an intake that folds up at the start and falls over at ▶.
An arm on a servo names its angle at two positions, `angle("stowed", 90).angle("deployed", 20)`. Those
names must be positions in that part's Tunables section (here `Tunables.IntakeDeploy`).

## Your own robot

1. **Make the five files** from your robot configuration (copy the XML file from the Control Hub,
   `/sdcard/FIRST/<name>.xml`).

   **Where:** the Terminal, in the `ftc-helper-lib` folder.

   ```bash
   ./gradlew :sim:newRobot -Pconfig=path/to/config.xml -PteamCode=../TeamCode
   ```

   The command picks each device's module from its name and type: "lift" is a Lift, and a servo
   called "claw" is a Claw. Read every `TODO` in Robot.java: the drive motor and wheel size, how the hub
   is mounted, and anything the command was not sure about. If one of the five files is already in
   TeamCode, the command writes nothing and names that file. Move that file away first. (Gradle then also
   prints `BUILD FAILED`. That is expected: nothing broke, and your files are untouched.) The command also
   adds the **Simulator** and **Simulator tests** buttons to Android Studio. The first time, close the
   project and open it again to see them.
2. **Build and drive.** The drive works on day one, and each module has safe starting numbers.
3. **A simulator robot file** from the code: start the simulator (the **Simulator** button, or
   `./gradlew :sim:run`). Each time it starts, it writes `simulator/robot.properties` from Robot.java.
   The names and the drive come from Robot.java. The sizes are guesses marked TODO until someone
   measures them. The [robot-file skill](../../guides/robot-file-skill.md) can also do it from photos or
   CAD. (A **skill** is a set of written instructions that an AI coding agent, like Claude Code, follows.)
   The simulator keeps your measured lines each time.
4. **Add a mechanism later:** one line in Robot.java (and its Tunables section), and its button lines in
   Controls.java (its "buttons to add" in the [reference](../../reference/robot-java.md)). Its simulator lines come with it.

## Check yourself

- Where would you change the launcher's speed? (Tunables.java, section `Launcher`.)
- The operator wants the claw on X instead of B. Which file? (Controls.java: `g2.x.toggle(robot.claw)`.)
- An action is lifting the lift and the operator moves the lift stick. What happens? (The action stops: the driver wins.)
- Why does `define()` not move anything? (It runs in INIT, and the library moves nothing before ▶: its safety choice, see Lesson 5.)

## Next

This is lesson 1 of the [main track](../README.md#start-here-the-main-track). Next:
[Lesson 2: Drive](02-drive.md). From there on you build **your own robot** in your five files,
one part at a time. The lesson robot is the worked example and the answer key.

Commit your five files after each lesson that works, so you can always go back:
[Git and GitHub for FTC teams](../../guides/github.md).
