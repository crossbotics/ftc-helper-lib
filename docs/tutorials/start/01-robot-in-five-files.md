# Lesson 1: The robot in five files

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You learn:** to write a whole robot program the short way: what is on the robot (`Robot.java`), its
numbers (`Tunables.java`), its buttons (`Controls.java`), combo moves (`Actions.java`) and the
autonomous programs (`autos/`). Then to start your own robot from its configuration, and to try it
in the simulator.

**Robot needed:** none to start (the simulator), then your robot. **Time:** about 45 minutes.

This is **lesson 1** of the [main track](../README.md#start-here-the-main-track). **Before this:** the
[Quick start](../../quick-start.md), or at least [Add it to a project](../../01-add-to-project.md). New to
Java? [Under-the-hood tutorial 01](../01-opmode-basics.md) (not this lesson) explains imports, fields and lambdas.

**Which robot:** you build your own robot, in the five files the Quick start put in your TeamCode.
This lesson explains with a worked example: goBILDA's BIOBUZZ starter bot, rewritten in this style, in
[`examples/.../gobildabot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot)
(a two-motor drive, an intake and a launcher). Lessons 2 to 6 use the **lesson robot**
([`examples/.../lessonbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot)) the
same way: read its lines, make the same change in your own files, and use its files as the answer key.
The simulator commands with `-PteamCode=none` run the library's examples; for your own robot use
`-PteamCode=../TeamCode`.
Three more complete robots are next to it:
[`flywheelbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot)
(an intake that runs from the start, a flywheel the driver speeds up and down, an indexer servo, a
deploy servo at START) and
[`turretbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot)
(a turret with a Limelight that follows the goal inside its range, speed from distance, a folding claw),
and [`v2bot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot) (a turret shooter
plus a lift with a wrist and a claw, kept apart by an interlock rule).
The other BIOBUZZ kit bots (REV, AndyMark, Studica) are there too, and more example robots: to pick
the one most like your robot, see [Pick your template](../../templates.md).
The reference for every method: [the robot module](../../modules/robot.md).

## Java you will meet

The five files use a few Java ideas over and over. You do not need more than this to start:

- `public class Robot extends RobotDefinition`: **extends** means "built on": your class gets
  everything the library class already does. The library class is **abstract**: an unfinished class
  that leaves a method or two for you to fill in (here `define()`), marked `@Override` in your file.
- `ControlMap<Robot>`, `Auto<Robot>`: the name in angle brackets (**generics**) says which class the
  library class works with: your `Robot`.
- `Drive.tank(...).motor(...).wheelMm(96)`: **method chaining**. Each method hands the same thing back,
  so the next one adds to it with another dot. Read it left to right as one sentence.
- `() -> r.launcher.fire(3)`: a **lambda**, a small piece of code you hand over now, to run later (when
  a button is pressed, or an action gets to that step). `() ->` means "no inputs, run this".
- `r.intake::in`: a **method reference**, the short way to write the lambda `() -> r.intake.in()`:
  "the intake's `in` method".
- `Runnable`: Java's name for "a piece of code with no inputs that can be run later". A lambda or a
  method reference like the two above is a `Runnable`, so the methods that take one (`press(...)`,
  `then(...)`) take either.

**Android Studio tip:** a name in red that it cannot find usually needs an `import` line at the top.
Click it and press **Alt+Enter** (**Option+Enter** on a Mac): it adds the missing import. If it offers
two, pick the one in `com.crossbotics.ftchelper.robot` for the five files' parts.

## Why five files

goBILDA's starter TeleOp is one file of about 200 lines: the motor names, the directions, the
launcher speed, the buttons and the driving are all mixed together. To change the launcher speed you
must find it among the rest; to add an arm you change the whole file.

Here each file answers ONE question:

| File | Answers |
| --- | --- |
| `Robot.java` | What is on the robot, and its name on the hub? |
| `Tunables.java` | What are the numbers? |
| `Controls.java` | Which button does what? (it is also the TeleOp) |
| `Actions.java` | Which steps use several parts together? |
| `autos/` | What does the robot do on its own? |

And each mechanism is a **module** (here: a ready-made part class from the library, like `Intake` or
`Lift`; not a Gradle module, which is a part of the build) that already knows how to work: an `Intake` can run in and out,
a `Launcher` spins up and feeds one piece when the wheel is at speed. A module brings its numbers,
its standard buttons and its simulator lines with it.

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

        intake = add(Intake.motor("intake")
                .withCrServos("left_intake_servo", "right_intake_servo")
                .reversed("right_intake_servo"));

        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.crServo("windmill").reversed()));
    }
}
```

- `extends RobotDefinition`: your `Robot` is built on a library class, so it gets all its work for
  free ([Java you will meet](#java-you-will-meet)). `@Override` marks a method that fills in one the library class leaves to you; the library calls
  it for you: here `define()`.
- The names in quotes are the names in the robot configuration on the Driver Station. They must
  match exactly (capitals too). A wrong name stops at INIT with the names that DO exist.
- `"gobilda-5203-312"` is the motor, by its id in the [parts catalog](../../guides/parts-catalog.md). The
  library works out encoder counts and speeds from it. (The starter bot's launcher is a 312 RPM motor
  with its gearbox taken off, so it has the 6000 RPM motor's numbers.)
- `.reversed("right_drive")`: the right motor is mounted facing the other way. If your robot drives
  backward or spins when you push the stick forward, change which motors are reversed.
- `add(...)` hands the module back, so `intake = add(...)` keeps it in a field (a variable that
  belongs to the class, like `public Intake intake;` at the top). Controls, Actions and the autos
  reach it as `robot.intake`.
- `define()` only DESCRIBES the robot. Nothing moves in INIT; the modules move only after START.
- **A part that is not built yet?** Write its line anyway and add `.optional()`:
  `intakeDeploy = add(Positioner.servo("intake_deploy").optional());`. Until "intake_deploy" is in the
  configuration, INIT says the part is off instead of stopping, and its buttons and actions skip it.
  The day it is wired and configured, it just works ([parts not on the robot yet](../../modules/robot.md#parts-not-on-the-robot-yet)).

## 2. Tunables.java: the numbers

```java
public class Tunables {
    public static class Drive {
        public static double maxPower = 1.0, practiceMaxPower = 0.6, slowSpeed = 0.3;
        public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
        public static boolean calibrated = false;   // true after the Drive Tuner
    }
    public static class Intake {
        public static double inPower = 1.0, outPower = 1.0;
    }
    public static class Launcher {
        public static double speedRpm = 2679, stepRpm = 50, readyBandRpm = 107, idleRpm = 0;
        public static double kP = 40, kI = 0, kD = 0, kF = 12.5;
    }
    public static class Windmill {
        public static double power = 1.0, feedSec = 0.3;
    }
    public static class Auto {
        public static double backInches = 4.7, backPower = 0.4;   // for the autonomous
    }
}
```

- `static` means the number belongs to the class itself, not to one object. So any file can read it
  as `Tunables.Drive.maxPower`, with no setup.
- One **section** per part, named after its configuration name in CamelCase (each word starts with a
  capital letter, no spaces or `_`): device `"windmill"` reads `Tunables.Windmill`, `"intake_deploy"`
  would read `IntakeDeploy`.
- Where these came from: goBILDA's code spins the launcher to 1250 counts per second (1250 / 28 counts
  per turn x 60 = 2679 RPM) and feeds above 1200 (that is 107 RPM less): `readyBandRpm = 107`.
- A number you leave out uses the library's default, and INIT tells you which
  (`Tunables.Intake: outPower not written: library defaults used (fine)`). A few safety numbers, like a
  lift's `min` and `max`, have no default: INIT stops and says which line to write.
- **Every number each part reads**, with its units, its default and the finder that measures it:
  [the Tunables reference](../../guides/tunables.md). A misspelled name is not read (capitals count),
  so after a change, check that INIT's "not written" note does not name a number you wrote.
- **Tune on the robot:** in the PRACTICE profile, after START, holding GUIDE for a second opens the tuning menu (D-pad to choose and
  change, A to save on the hub). At the next INIT the Driver Station lists every value that differs
  from this file, for example `Tunables.Launcher.speedRpm = 2750 (code 2679)`. Copy the good ones
  into this file: the file stays the record. The `Auto` section is in the menu too (`Auto/backInches`):
  tune it in the TeleOp, save, and the autonomous uses it at its next INIT. Copy those into this file
  too, every time.

## 3. Controls.java: the buttons (and the TeleOp)

```java
@TeleOp(name = "goBILDA Bot TeleOp")
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = true;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        g1.y.press(Actions.fireAll(robot)).says("fire all");
    }
}
```

`@TeleOp(name = ...)` is the name on the Driver Station. `ControlMap<Robot>` is a whole TeleOp from
the library; the `<Robot>` in angle brackets says which robot it is for: yours.

That is the whole TeleOp. The modules already have their **standard buttons**: the intake on the
triggers, the launcher on the bumpers, its speed on the D-pad. `oneDriver = true` puts them all on
gamepad 1, like goBILDA's code. You only write what is different: here, Y fires all three.

- **INIT shows the map** on the Driver Station: every button with its job, and the free ones. During the
  match, a tap of GUIDE shows it again, without the free ones (tap again to close).
- **BACK pauses the robot:** everything stops on **brake** (the motors hold still instead of coasting); BACK again and each part goes on as before
  (with field-centric on, hold BACK 1 s instead to reset "forward"; in robot-centric driving that does nothing). STOP brakes everything too.
- Each button takes ONE job. Two jobs on one button in this file, or a job on a driving button (the
  left stick, LT slow...), stop at INIT with the button's name. A job you put on a button a module
  suggested replaces the suggestion.
- The kinds of jobs: `press(...)`, `hold(whileHeld, onRelease)`, `toggle(robot.intake)`,
  `release(...)`, and for sticks `g2.leftStickY.use(robot.lift::manual)`. `robot.lift::manual` means
  "the lift's `manual` method": the library calls it later, every loop. Lesson 3 has them all.
- **The loop:** a robot program runs a **loop**: many times a second, it reads the gamepads, runs the
  button jobs and updates every part. Each time round is "a loop". The library runs this loop for
  you; `buttons(...)` runs only once, at INIT, and plugs a job into each button
  ([From Blocks to Java](../../blocks-to-java.md) explains it with blocks).

## 4. Actions.java: steps that use several parts

```java
public static Action fireAllWithIntake(Robot r) {
    return Action.named("Fire all")
            .then(r.intake::in, () -> r.launcher.fire(3))
            .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
            .then(r.intake::stop);
}
```

`r.intake::in` (a method reference) and `() -> r.launcher.fire(3)` (a lambda) are two ways to write a
small piece of code that runs later, when the action gets to that step
([Java you will meet](#java-you-will-meet)).

An action is a list of steps: do these (`then`), wait (`waitSeconds`, `waitUntil`). It **never
blocks** (never makes the program stop and wait): it does a little every loop, so the robot keeps driving and every other button keeps
working while it runs. Things to know:

- `then(a, b)` does both at once (here the intake helps push while the launcher fires).
- **Which parts an action uses matters.** The autonomous uses this one. For TeleOp the example has
  `fireAll`, which uses only the launcher: the driver holds RT to run the intake while it fires. An
  action that also ran the intake would stop the moment the driver touched RT (see the next point).
- A `waitUntil` that times out STOPS the action, and the Driver Station says why. (A lift that never
  got to the top must not open the claw.)
- **The driver always wins:** moving one of the action's parts by hand stops it. Pressing its button
  again cancels it. A stopped action stops what it started, safely: a lift holds its height, shots
  still waiting are dropped, the intake stops.
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
        s.start("Against the goal, facing it", 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Back up", Tunables.Auto.backInches, 180, Tunables.Auto.backPower);
    }
}
```

`extends Auto<Robot>`: an autonomous from the library, for your `Robot`. You fill in
`steps(...)`. `mirrorForRed()` you add only when the answer is false (it is explained just below).

One short file per autonomous, read top to bottom. The INIT menu (alliance, start delay, the profile)
comes for free; moves and turns are written for BLUE and mirrored for RED (or, on a field turned half a
turn like BIOBUZZ, the same for both: `mirrorForRed()` false); the report at the end
says what happened to each step. A new autonomous: copy the file, change its name and its steps.

Parking points come at the end, so a park step must not run out of time. Put `.parkWithSeconds(3)` just
before it: the steps above stop when 3 s are left, and the steps below always run
([Keep time to park](../../modules/robot.md#keep-time-to-park)). Everything else the steps can do (menus,
branches, actions, your own steps): [The autonomous builder](../../guides/autonomous-builder.md).

It refuses to run until `Tunables.Drive.calibrated = true`: tune the drive first with the Drive Tuner
([lesson 6](06-first-autonomous.md#1-tune-the-drive-first) shows how), then set it.

## Try it in the simulator

The simulator has goBILDA's starter bot, made from its CAD: `sim/robots/starter-bot-gobilda.properties`.
The examples are built into the simulator, so:

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

`-PteamCode=none` lists the library's examples instead of your own TeamCode's OpModes. (On a real robot
the examples are hidden: they are marked `@Disabled`.) Open http://localhost:8780, pick "goBILDA Bot TeleOp", INIT (read the button map), START. LB spins
up, RB fires (hold it to keep firing). ([Simulator guide](../../guides/simulator.md).)

**Your own robot** (the five files in your TeamCode), from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

The first line writes your robot's simulator file, `simulator/robot.properties` (see below). Open
http://localhost:8780, pick your TeleOp, INIT, START. Press **Ctrl+C** in the Terminal to stop the
simulator.

### Draw your robot from Robot.java

The simulator draws the robot from its **robot file**: a text file that describes the robot to the
simulator (its size, parts, motors and shapes). Your team's is `simulator/robot.properties` in the robot
project. `Robot.java` can write that file for you, if it also
says what each part is and how it looks ([Shapes and parts](../../modules/robot.md#shapes-and-parts)).
Name each part by its catalog id; give a size or a place only when the catalog does not know it.
(Shortened from [`flywheelbot/Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot/Robot.java).)

```java
chassisMm(400, 400, 100);
intake = add(Intake.motor("intake").part("gobilda-5203-1150").holds(3)
        .looks(Shape.roller("gobilda-gecko-48-rex").atMm(190, 0, 60).lengthMm(300)));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.crServo("windmill"))
        .looks(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 170)).shotAngle(50));
```

Then, from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
```

Then start it with `./gradlew :sim:run -PteamCode=../TeamCode` and open http://localhost:8780: the roller spins when the intake runs, a piece is picked up and fired, and the
size rules are checked on your shapes. The flywheel example (`examples/.../flywheelbot/Robot.java`) also
has an intake that folds up at the start and falls over at START. An arm on a servo names its angle at
two positions, `angle("stowed", 90).angle("deployed", 20)`: those names must be positions in that part's
Tunables section (here `Tunables.IntakeDeploy`).

## Your own robot

1. **Make the five files** from your robot configuration (copy the XML file from the Control Hub,
   `/sdcard/FIRST/<name>.xml`):

   ```bash
   ./gradlew :sim:newRobot -Pconfig=path/to/config.xml -PteamCode=../TeamCode
   ```

   Each device gets its module from its name and type ("lift" is a Lift, a servo called "claw" a
   Claw). Read every `TODO` in Robot.java: the drive motor and wheel size, how the hub is mounted,
   and anything it was not sure about. If one of the five files is already in TeamCode, it writes
   nothing and says which file: move that file away first.
2. **Build and drive.** The drive works on day one; each module has safe starting numbers.
3. **A simulator robot file** from the code:

   ```bash
   ./gradlew :sim:fromRobot -PteamCode=../TeamCode
   ```

   The names and the drive come from Robot.java; the sizes are guesses marked TODO until someone
   measures them (or the [robot-file skill](../../guides/robot-file-skill.md) does it from photos or CAD: a **skill** is a set
of written instructions an AI coding agent, like Claude Code, follows). Run it again whenever
   Robot.java changes: your measured lines are kept.
4. **Add a mechanism later:** one line in Robot.java (and its Tunables section). Its standard buttons
   and its simulator lines come with it.

## Check yourself

- Where would you change the launcher's speed? (Tunables.java, section `Launcher`.)
- The operator wants the claw on X instead of B. Which file? (Controls.java: `g2.x.toggle(robot.claw)`.)
- An action is lifting the lift and the operator moves the lift stick. What happens? (The action stops: the driver wins.)
- Why does `define()` not move anything? (It runs in INIT, and nothing may move before START.)

## Next

This is lesson 1 of the [main track](../README.md#start-here-the-main-track). Next:
[Lesson 2: Drive](02-drive.md). From there on you build **your own robot** in your five files,
one part at a time; the lesson robot is the worked example (and the answer key).

Commit your five files after each lesson that works, so you can always go back:
[Git and GitHub for FTC teams](../../guides/github.md).
