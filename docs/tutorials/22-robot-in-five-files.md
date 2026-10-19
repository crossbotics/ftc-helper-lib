# 22 The robot in five files

**You learn:** to write a whole robot program the short way: what is on the robot (`Robot.java`), its
numbers (`Tunables.java`), its buttons (`Controls.java`), combo moves (`Actions.java`) and the
autonomous programs (`autos/`). Then to start your own robot from its configuration, and to try it
in the simulator.

**Robot needed:** none to start (the simulator), then your robot. **Time:** about 45 minutes.

The program: goBILDA's BIOBUZZ starter bot, rewritten in this style, in
[`examples/.../gobildabot`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/gobildabot).
Two more complete robots are next to it:
[`flywheelbot`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/flywheelbot)
(an intake that runs from the start, a flywheel the driver speeds up and down, an indexer servo, a
deploy servo at START) and
[`turretbot`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/turretbot)
(a turret with a Limelight that follows the goal inside its range, speed from distance, a folding claw).
The reference for every method: [the robot module](../modules/robot.md).

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

And each mechanism is a **module** that already knows how to work: an `Intake` can run in and out,
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

- The names in quotes are the names in the robot configuration on the Driver Station. They must
  match exactly (capitals too). A wrong name stops at INIT with the names that DO exist.
- `"gobilda-5203-312"` is the motor, by its id in the [parts catalog](../guides/parts-catalog.md). The
  library works out encoder counts and speeds from it. (The starter bot's launcher is a 312 RPM motor
  with its gearbox taken off, so it has the 6000 RPM motor's numbers.)
- `.reversed("right_drive")`: the right motor is mounted facing the other way. If your robot drives
  backward or spins when you push the stick forward, change which motors are reversed.
- `add(...)` hands the module back, so `intake = add(...)` keeps it in a field. Controls, Actions and
  the autos reach it as `robot.intake`.
- `define()` only DESCRIBES the robot. Nothing moves in INIT; the modules move only after START.

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

- One **section** per part, named after its configuration name in CamelCase: device `"windmill"`
  reads `Tunables.Windmill`, `"intake_deploy"` would read `IntakeDeploy`.
- Where these came from: goBILDA's code spins the launcher to 1250 counts per second (1250 / 28 counts
  per turn x 60 = 2679 RPM) and feeds above 1200 (that is 107 RPM less): `readyBandRpm = 107`.
- A number you leave out uses the library's default, and INIT tells you which.
- **Tune on the robot:** in the PRACTICE profile, GUIDE opens the tuning menu (D-pad to choose and
  change, A to save on the hub). At the next INIT the Driver Station lists every value that differs
  from this file, for example `Tunables.Launcher.speedRpm = 2750 (code 2679)`. Copy the good ones
  into this file: the file stays the record.

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

That is the whole TeleOp. The modules already have their **standard buttons**: the intake on the
triggers, the launcher on the bumpers, its speed on the D-pad. `oneDriver = true` puts them all on
gamepad 1, like goBILDA's code. You only write what is different: here, Y fires all three.

- **INIT shows the map** on the Driver Station: every button with its job, and the free ones.
- Each button takes ONE job. Two jobs on one button in this file, or a job on a driving button (the
  left stick, LT slow...), stop at INIT with the button's name. A job you put on a button a module
  suggested replaces the suggestion.
- The kinds of jobs: `press(...)`, `hold(whileHeld, onRelease)`, `toggle(robot.intake)`,
  `release(...)`, and for sticks `g2.leftStickY.use(robot.lift::manual)`.

## 4. Actions.java: steps that use several parts

```java
public static Action fireAllWithIntake(Robot r) {
    return Action.named("Fire all")
            .then(r.intake::in, () -> r.launcher.fire(3))
            .waitUntil("the shots", () -> !r.launcher.isShooting(), 8.0)
            .then(r.intake::stop);
}
```

An action is a list of steps: do these (`then`), wait (`waitSeconds`, `waitUntil`). It **never
blocks**: it does a little every loop, so the robot keeps driving and every other button keeps
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
    protected void steps(Steps s, Robot robot) {
        s.start("Against the goal, facing it", 0)
         .action(Actions.fireAllWithIntake(robot), 12)
         .move("Back up", Tunables.Auto.backInches, 180, Tunables.Auto.backPower);
    }
}
```

One short file per autonomous, read top to bottom. The INIT menu (alliance, start delay, the profile)
comes for free; moves and turns are written for BLUE and mirrored for RED; the report at the end
says what happened to each step. A new autonomous: copy the file, change its name and its steps.

It refuses to run until `Tunables.Drive.calibrated = true`: tune the drive first with the Drive Tuner
([tutorial 09](09-first-auto.md), [21](21-finders.md)), then set it.

## Try it in the simulator

The simulator has goBILDA's starter bot, made from its CAD: `sim/robots/starter-bot-gobilda.properties`.
The examples are built into the simulator, so:

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

`-PteamCode=none` lists the library's examples instead of your own TeamCode's OpModes. (On a real robot
the examples are hidden: they are marked `@Disabled`.) Open http://localhost:8780, pick "goBILDA Bot TeleOp", INIT (read the button map), START. LB spins
up, RB fires. ([Simulator guide](../guides/simulator.md).)

## Your own robot

1. **Make the five files** from your robot configuration (copy the XML file from the Control Hub,
   `/sdcard/FIRST/<name>.xml`):

   ```bash
   ./gradlew :sim:newRobot -Pconfig=path/to/config.xml -PteamCode=../TeamCode
   ```

   Each device gets its module from its name and type ("lift" is a Lift, a servo called "claw" a
   Claw). Read every `TODO` in Robot.java: the drive motor and wheel size, how the hub is mounted,
   and anything it was not sure about.
2. **Build and drive.** The drive works on day one; each module has safe starting numbers.
3. **A simulator robot file** from the code:

   ```bash
   ./gradlew :sim:fromRobot -PteamCode=../TeamCode
   ```

   The names and the drive come from Robot.java; the sizes are guesses marked TODO until someone
   measures them (or the robot-file skill does it from photos or CAD). Run it again whenever
   Robot.java changes: your measured lines are kept.
4. **Add a mechanism later:** one line in Robot.java (and its Tunables section). Its standard buttons
   and its simulator lines come with it.

## Check yourself

- Where would you change the launcher's speed? (Tunables.java, section `Launcher`.)
- The operator wants the claw on X instead of B. Which file? (Controls.java: `g2.x.toggle(robot.claw)`.)
- An action is lifting the lift and the operator moves the lift stick. What happens? (The action stops: the driver wins.)
- Why does `define()` not move anything? (It runs in INIT, and nothing may move before START.)
