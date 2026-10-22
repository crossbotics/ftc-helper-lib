# Cookbook: "How do I...?"

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Short answers to the questions students ask most. Each recipe is one question, a few lines of code
to copy, one sentence on **why**, and links to the lesson that explains more.

**Find your question fast:** press **Ctrl+F** (on a Mac: **Cmd+F**) and type a word, like `lift`,
`double-tap` or `park`. Or use the list just below.

**The code always works.** Every code block on this page is copied from real Java files in
[`examples/.../cookbook`](../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook). Those
files are built and run in the simulator each time the library changes, and a check
(`tools/check_docs.py`) makes sure this page and the files say the same thing.

**New here?** Do the [Quick start](quick-start.md) first: it puts the five files in your robot project.
These recipes go into those files. Never coded before? Read [Words you will meet](#words-you-will-meet)
first.

## The questions

**Start here**
- [Coming from Blocks: where did my blocks go?](#coming-from-blocks-where-did-my-blocks-go) (if, loop, sleep, REVERSE, variables)
- [Where does each line go? The files, whole](#where-does-each-line-go-the-files-whole)
- [Words you will meet](#words-you-will-meet)

**Driving**
- [How do I set up a mecanum drive?](#how-do-i-set-up-a-mecanum-drive)
- [How do I set up a tank (standard) drive?](#how-do-i-set-up-a-tank-standard-drive)
- [A wheel turns the wrong way. How do I fix it?](#a-wheel-turns-the-wrong-way-how-do-i-fix-it)
- [How do I tell the robot how the Control Hub is mounted?](#how-do-i-tell-the-robot-how-the-control-hub-is-mounted)
- [How do I make the robot slower (or faster)?](#how-do-i-make-the-robot-slower-or-faster)
- [How do I let one driver do everything?](#how-do-i-let-one-driver-do-everything)
- [How do I put slow mode on a trigger?](#how-do-i-put-slow-mode-on-a-trigger)
- [Driving that is already built in](#driving-that-is-already-built-in) (field-centric, pause, "forward")

**Buttons and gestures**
- [How do I make a button do something once?](#how-do-i-make-a-button-do-something-once)
- [How do I run something only while a button is held?](#how-do-i-run-something-only-while-a-button-is-held)
- [How do I switch something on and off with one button?](#how-do-i-switch-something-on-and-off-with-one-button)
- [How do I put a tap, a hold and a double-tap on one button?](#how-do-i-put-a-tap-a-hold-and-a-double-tap-on-one-button)
- [How do I make a button safe ("hold for 1 second")?](#how-do-i-make-a-button-safe-hold-for-1-second)
- [How do I use two buttons together (like a shift key)?](#how-do-i-use-two-buttons-together-like-a-shift-key)
- [How do I make the gamepad buzz?](#how-do-i-make-the-gamepad-buzz)
- [How do I control a part with a stick?](#how-do-i-control-a-part-with-a-stick)
- [How do I move a part's button to another button?](#how-do-i-move-a-parts-button-to-another-button)
- [How do I turn off all the standard buttons?](#how-do-i-turn-off-all-the-standard-buttons)
- [How do I move the library's own buttons (slow, field-centric, pause)?](#how-do-i-move-the-librarys-own-buttons-slow-field-centric-pause)
- [How do I change the double-tap and hold times?](#how-do-i-change-the-double-tap-and-hold-times)
- [Buttons that are already built in](#buttons-that-are-already-built-in) (button map, pause, switch alliance, set pose)

**Mechanisms**
- [How do I add an intake on a motor?](#how-do-i-add-an-intake-on-a-motor)
- [How do I add an intake on CR servos?](#how-do-i-add-an-intake-on-cr-servos)
- [How do I make the intake run from the start?](#how-do-i-make-the-intake-run-from-the-start)
- [How do I add a lift with set heights?](#how-do-i-add-a-lift-with-set-heights)
- [My lift goes down when it should go up. How do I fix it?](#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)
- [How do I add an arm that moves in degrees?](#how-do-i-add-an-arm-that-moves-in-degrees)
- [How do I add a claw?](#how-do-i-add-a-claw)
- [How do I add a wrist?](#how-do-i-add-a-wrist)
- [How do I add any servo with named positions (a gate, a fold-out)?](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)
- [How do I send a part to a position from a button?](#how-do-i-send-a-part-to-a-position-from-a-button)
- [How do I add a launcher (flywheel and feeder)?](#how-do-i-add-a-launcher-flywheel-and-feeder)
- [How do I add a turret that follows the goal?](#how-do-i-add-a-turret-that-follows-the-goal)
- [How do I fire only when aimed, at the right speed?](#how-do-i-fire-only-when-aimed-at-the-right-speed)

**Game pieces** (what is in the robot, and what fires next)
- [How do I know which piece is in the robot, and how many?](#how-do-i-know-which-piece-is-in-the-robot-and-how-many)
- [How do I stop the intake when the robot is full?](#how-do-i-stop-the-intake-when-the-robot-is-full)
- [How do I tell pieces apart by size, or with a second camera?](#how-do-i-tell-pieces-apart-by-size-or-with-a-second-camera)
- [How do I use the count (buzz, LEDs, the shot table)?](#how-do-i-use-the-count-buzz-leds-the-shot-table)
- [How do I tune the piece sensors?](#how-do-i-tune-the-piece-sensors)
**Aiming and shooting** (the robot helps the drivers)
- [How do I make the whole robot aim at the goal (no turret)?](#how-do-i-make-the-whole-robot-aim-at-the-goal-no-turret)
- [How do I make the gamepad buzz when the aim is on target?](#how-do-i-make-the-gamepad-buzz-when-the-aim-is-on-target)
- [How do I keep aiming when the camera loses the goal?](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)
- [How do I use one shot table per game piece?](#how-do-i-use-one-shot-table-per-game-piece)

**Actions** (several steps or parts together)
- [How do I do several things one after the other?](#how-do-i-do-several-things-one-after-the-other)
- [How do I repeat steps several times?](#how-do-i-repeat-steps-several-times)
- [How do I move two parts at the same time?](#how-do-i-move-two-parts-at-the-same-time)
- [How do I wait half a second (like sleep in Blocks)?](#how-do-i-wait-half-a-second-like-sleep-in-blocks)
- [How do I wait until something is true?](#how-do-i-wait-until-something-is-true)
- [How do I start an action from a button?](#how-do-i-start-an-action-from-a-button)
- [How do I stop every running action?](#how-do-i-stop-every-running-action)
- [How do I run an action right after START?](#how-do-i-run-an-action-right-after-start)
- [How do I use the same action in an autonomous?](#how-do-i-use-the-same-action-in-an-autonomous)

**Autonomous**
- [How do I make my first autonomous (drive 2 feet)?](#how-do-i-make-my-first-autonomous-drive-2-feet)
- [How do I drive forward, sideways and turn?](#how-do-i-drive-forward-sideways-and-turn)
- [Does my autonomous need a RED version?](#does-my-autonomous-need-a-red-version)
- [How do I let the drivers choose before the match?](#how-do-i-let-the-drivers-choose-before-the-match)
- [How do I make sure there is time to park?](#how-do-i-make-sure-there-is-time-to-park)
- [How do I run a mechanism while the robot drives?](#how-do-i-run-a-mechanism-while-the-robot-drives)
- [How do I wait for my alliance partner?](#how-do-i-wait-for-my-alliance-partner)
- [How do I write a step the list cannot say?](#how-do-i-write-a-step-the-list-cannot-say)
- [How do I change a distance without building the code again?](#how-do-i-change-a-distance-without-building-the-code-again)
- [How do I share the same first steps in several autos?](#how-do-i-share-the-same-first-steps-in-several-autos)

**Odometry and driveTo** (where is the robot on the field?)
- [How do I add a goBILDA Pinpoint?](#how-do-i-add-a-gobilda-pinpoint)
- [How do I add a SparkFun OTOS?](#how-do-i-add-a-sparkfun-otos)
- [How do I add dead wheels?](#how-do-i-add-dead-wheels)
- [How do I tune the odometry?](#how-do-i-tune-the-odometry)
- [How do I drive to a point on the field?](#how-do-i-drive-to-a-point-on-the-field)
- [How do I show where the robot is?](#how-do-i-show-where-the-robot-is)
- [How do I use the robot's position in an action?](#how-do-i-use-the-robots-position-in-an-action)
- [How do I set the pose when autonomous did not run?](#how-do-i-set-the-pose-when-autonomous-did-not-run)

**Pedro paths** (smooth curves, mecanum only)
- [How do I add Pedro Pathing to Robot.java?](#how-do-i-add-pedro-pathing-to-robotjava)
- [How do I make a straight path and a curve?](#how-do-i-make-a-straight-path-and-a-curve)
- [How do I follow a path in an autonomous?](#how-do-i-follow-a-path-in-an-autonomous)
- [How do I drive two paths with no stop, slow down, or hold at the end?](#how-do-i-drive-two-paths-with-no-stop-slow-down-or-hold-at-the-end)
- [How do I keep facing the goal along a path?](#how-do-i-keep-facing-the-goal-along-a-path)
- [How do I start something partway along a path?](#how-do-i-start-something-partway-along-a-path)

**Telemetry** (lines on the Driver Station)
- [How do I show a value on the Driver Station screen?](#how-do-i-show-a-value-on-the-driver-station-screen)
- [How do I round a number on the Driver Station?](#how-do-i-round-a-number-on-the-driver-station)
- [How do I show lines from my own autonomous step?](#how-do-i-show-lines-from-my-own-autonomous-step)

**Lights** (a color the drivers can see)
- [How do I add a colored light for the drivers?](#how-do-i-add-a-colored-light-for-the-drivers)
- [How do I show a color when something is true?](#how-do-i-show-a-color-when-something-is-true)
- [How do I flash the light when something happens?](#how-do-i-flash-the-light-when-something-happens)
- [How do I flash the light when an action is done?](#how-do-i-flash-the-light-when-an-action-is-done)
- [How do I blink the light in the last 20 seconds?](#how-do-i-blink-the-light-in-the-last-20-seconds)

**Tuning and finders** (getting the numbers right)
- [Where do the numbers go?](#where-do-the-numbers-go)
- [How do I find a servo's open and closed numbers?](#how-do-i-find-a-servos-open-and-closed-numbers)
- [How do I find a lift's limits and heights?](#how-do-i-find-a-lifts-limits-and-heights)
- [How do I change a number while the robot runs?](#how-do-i-change-a-number-while-the-robot-runs)
- [How do I tune the drive for autonomous?](#how-do-i-tune-the-drive-for-autonomous)
- [How do I set the flywheel numbers?](#how-do-i-set-the-flywheel-numbers)
- [How do I build a shot table (speed for each distance)?](#how-do-i-build-a-shot-table-speed-for-each-distance)

**Simulator** (try your code with no robot)
- [How do I start the simulator?](#how-do-i-start-the-simulator)
- [How do I see my own robot in the simulator?](#how-do-i-see-my-own-robot-in-the-simulator)
- [How do I test my code with a script?](#how-do-i-test-my-code-with-a-script)

## Coming from Blocks: where did my blocks go?

| In Blocks you used | Here you write | In which file |
| --- | --- | --- |
| a `repeat while opModeIsActive` loop | nothing: the library runs the loop for you, many times a second | |
| drive forward, turn (in autonomous) | `.move("Forward", 24, 0, 0.5)`, `.turn("Left", 90, 0.4)` ([moves](#how-do-i-drive-forward-sideways-and-turn)) | a file in `autos/` |
| `if` a sensor or part says something, then ... | `.waitUntil(...)`: wait until it is true, then go on ([wait until](#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| `if` the drivers chose something before the match | `onlyIf(...)` with a menu choice ([choices](#how-do-i-let-the-drivers-choose-before-the-match)) | an autonomous |
| `repeat 3 times` | a `for` loop that adds the steps 3 times to an action ([repeat](#how-do-i-repeat-steps-several-times)) | `Actions.java` |
| set servo position | a named position: `robot.claw.open()`, `robot.intakeDeploy.goTo("deployed")` ([servo positions](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)) | `Controls.java`, `Actions.java` |
| `waitForStart` | nothing: the library waits for START. For something right after START: `atStart(...)` ([at START](#how-do-i-run-an-action-right-after-start)) | `Robot.java` |
| `if gamepad2.y then ...` inside the loop | one line, once: `g2.y.press(...)` ([buttons](#how-do-i-make-a-button-do-something-once)) | `Controls.java` |
| `if` the button is held | `g1.a.hold(...)` ([hold](#how-do-i-run-something-only-while-a-button-is-held)) | `Controls.java` |
| `sleep 500` | `.waitSeconds(0.5)` ([wait](#how-do-i-wait-half-a-second-like-sleep-in-blocks)) | `Actions.java` or an autonomous |
| a loop that waits until something is true | `.waitUntil(...)` ([wait until](#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| set motor direction REVERSE | drive motors: `.reversed("front_right", ...)` ([drive](#a-wheel-turns-the-wrong-way-how-do-i-fix-it)); a lift: `.reversed()` ([lift](#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)) | `Robot.java` |
| set motor power | a command of the part: `robot.intake.in()`, `robot.lift.goTo("high")` ([parts](#how-do-i-send-a-part-to-a-position-from-a-button)) | `Controls.java`, `Actions.java` |
| a variable for a number (a speed, a position) | a number in `Tunables.java` ([numbers](#where-do-the-numbers-go)) | `Tunables.java` |
| `telemetry.addData` | `show("Label", ...)` ([show](#how-do-i-show-a-value-on-the-driver-station-screen)) | `Controls.java` |
| drive blocks (sticks to motor powers) | nothing: driving is built in ([driving](#driving-that-is-already-built-in)) | |

**Why no loop?** In Blocks your code ran again and again in the loop. Here `buttons(...)` runs **once**,
at INIT: each line plugs a job into a button. The library's loop then runs the job each time the button
is pressed. So you never write `if`, `while` or `sleep` for buttons. More: [From Blocks to Java](blocks-to-java.md).

## Where does each line go? The files, whole

Each recipe gives a few lines. They go INSIDE one of these files, at the spot the comment shows. Keep
every other line of the file. Your files also start with a `package` line and some `import` lines (not
shown here; they stay at the very top, above `public class`). An import tells Java where a word like
`Lift` comes from: when a word is red in Android Studio, click it and press **Alt+Enter** (Mac:
**Option+Enter**) to add its import.

**`Robot.java`**: the parts. Fields at the top, one line per part inside `define()`:

<!-- recipe: file-robot -->
```java
public class Robot extends RobotDefinition {
    public Intake intake;          // one field per part: "public", the kind of part, a name
    public Lift lift;
    public Claw claw;
    public Camera camera;
    public Launcher launcher;

    @Override
    protected void define() {      // the drive line, then one line per part
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        intake = add(Intake.motor("intake"));
        lift = add(Lift.slide("lift").bottomSwitch("lift_bottom"));
        claw = add(Claw.servo("claw"));
        camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
                .speedFromDistance(camera));
    }
}
```

**`Controls.java`**: the buttons, and your TeleOp. Button lines go inside `buttons(...)`:

<!-- recipe: file-controls -->
```java
@TeleOp(name = "Cookbook TeleOp", group = "Cookbook")   // the name on the Driver Station
@Disabled                                               // delete this line in your copy
public class Controls extends ControlMap<Robot> {
    public static boolean oneDriver = false;

    @Override
    protected void buttons(Robot robot, Pad g1, Pad g2) {
        // robot = your Robot.java, g1 = gamepad 1, g2 = gamepad 2. Button lines go here:
        g2.y.press(Actions.scoreHigh(robot)).says("score high");
        show("Lift ticks", robot.lift::position);
    }
}
```

**`Actions.java`**: one method per action:

<!-- recipe: file-actions -->
```java
public class Actions {
    // One method per action. r = your robot. Use it in Controls.java and in autos.
    public static Action scoreHigh(Robot r) {
        return Action.named("Score high")
                .then(() -> r.lift.goTo("high"))
                .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)
                .then(r.claw::open)
                .waitSeconds(0.5)              // wait half a second (this is "sleep", done safely)
                .then(() -> r.lift.goTo("down"));
    }
}
```

An autonomous file: see [your first autonomous](#how-do-i-make-my-first-autonomous-drive-2-feet).
The numbers file: see [Tunables.java](#where-do-the-numbers-go).

## Words you will meet

| Word | Means |
| --- | --- |
| `Robot.java` | the file that says WHAT is on the robot: the drive and one line per part, inside `define()` |
| `Tunables.java` | the file with every number (speeds, positions), one section per part |
| `Controls.java` | the file that says which button does what, inside `buttons(...)`. It is also your TeleOp |
| `Actions.java` | moves that use several steps or parts (score, grab) |
| `autos/` | the folder with your autonomous programs, one file each |
| configuration name | the name of a motor or servo you typed on the Driver Station (`"lift"`). The code must use the same name |
| `robot.lift::position` | `::` hands over a method without running it. The library runs it later (when the button is pressed) |
| `() -> robot.lift.goTo("high")` | a **lambda**: a small piece of code the library runs later. Use `::` when the job is one method with nothing in its brackets (`robot.claw::open`); use `() ->` when the brackets hold something (`goTo("high")`) or for several commands |
| `g1`, `g2` | gamepad 1 (the driver) and gamepad 2 (the operator) |
| operator | the second driver, on gamepad 2: runs the mechanisms while the driver drives |
| GUIDE | the centre button of the gamepad. **Tap:** the button map (what each button does). **Hold 1 s** (PRACTICE profile): the tuning menu |
| "every loop" | the library's loop runs many times a second: it reads the gamepads, runs the jobs and updates every part. You never write it |
| power | from 0 (stopped) to 1 (full); a negative power runs the other way |
| `robot`, `g1`, `g2`, `s` | names the library hands you: `buttons(Robot robot, Pad g1, Pad g2)` and `steps(Steps s, Robot robot)` (see [the files, whole](#where-does-each-line-go-the-files-whole)) |
| `;` | ends a line of Java. A chain of steps (`.move(...)` then `.turn(...)`) is ONE line split over several lines: only its last line has `;` |
| `"lift"` (in quotes) | a name. Capitals and spelling must match exactly |
| class | one Java file's main block: `public class Robot ... { ... }`. "Inside the class" = between its `{` and `}` |
| method | a named piece of code, like `define()` or `scoreHigh(...)`. Its lines go between its `{` and `}` |
| `@TeleOp(...)`, `@Autonomous(...)` | put the program in the Driver Station's list, with this name |
| `@Disabled` | hides the program from the list. The cookbook's programs have it; delete it in your copy |
| `@Override`, `public`, `static`, `protected` | Java words the file needs. Copy them as they are |
| INIT, START | the Driver Station's two buttons: INIT gets ready (nothing may move), START runs |
| PRACTICE, COMPETITION | the two profiles (ways to run): PRACTICE is slower and has the tuning menu |
| ticks (counts) | how far a motor has turned, from its encoder (a sensor in the motor) |
| `kP`, `kI`, `kD`, `kF` | the PID numbers: how hard a lift pushes toward its target. Start with the defaults |
| catalog id (`"gobilda-5203-312"`) | a motor's or servo's name in the [parts catalog](guides/parts-catalog.md) |
| tuning menu | change numbers while the robot runs (not the INIT choice menu of an autonomous) |
| terminal | a window where you type commands. In Android Studio: the **Terminal** tab at the bottom ([how](start-from-zero.md#6-the-terminal-tab)) |

**One rule for every recipe:** a job never waits. No `sleep(...)` and no `while` loop in a button job
or an action: they freeze the whole robot. To wait, see
[how do I wait half a second (like sleep)?](#how-do-i-wait-half-a-second-like-sleep-in-blocks).

## Driving

### How do I set up a mecanum drive?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: drive-mecanum -->
```java
// The four motor names, in this order: front left, front right, back left, back right.
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)       // the motor (catalog id) and wheel size
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the names must match the configuration, and the wheel size makes autonomous distances right.
**More:** [Lesson 2: Drive](tutorials/start/02-drive.md), [Drive](modules/robot.md#drive).

### How do I set up a tank (standard) drive?

**Where:** `Robot.java`, inside `define()` (in place of the mecanum line).

<!-- recipe: drive-tank -->
```java
drive(Drive.tank("left_drive", "right_drive")
        .motor("gobilda-5203-312").wheelMm(96)
        .reversed("right_drive")                     // only the motors that run backward
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the motors on the two sides face opposite ways, so one side runs backward: list that side's
motors in `.reversed(...)`. Two motors per side? Name all four: `Drive.tank("front_left", "front_right", "back_left", "back_right")`.
**More:** [Lesson 2](tutorials/start/02-drive.md#1-pick-the-drive-type-and-name-the-motors), [Drive](modules/robot.md#drive).

### A wheel turns the wrong way. How do I fix it?

**Where:** `Robot.java`, in the drive line, before `.imu(...)`.

<!-- recipe: drive-reversed -->
```java
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // List EVERY reversed drive motor. Without this line the left side is reversed.
        .reversed("front_right", "back_right")
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** motors on the two sides face opposite ways, so one side must run reversed for the robot to go straight.
**More:** [Lesson 2: check the motor directions](tutorials/start/02-drive.md#7-check-the-motor-directions), [Troubleshooting](troubleshooting.md#robot-behavior).

### How do I tell the robot how the Control Hub is mounted?

**Where:** `Robot.java`, the `.imu(...)` part of the drive line.

<!-- recipe: drive-imu -->
```java
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        // Which way the hub's LOGO faces, then which way its USB PORTS face, on the robot.
        .imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP));
```

**Why:** the IMU (the hub's turn sensor) must know which way is up, or turns and field-centric driving go wrong.
**More:** [Lesson 2: the IMU](tutorials/start/02-drive.md#4-say-how-the-control-hub-is-mounted-the-imu).

### How do I make the robot slower (or faster)?

**Where:** `Tunables.java`, section `Drive`.

<!-- recipe: tunables-drive -->
```java
public static class Drive {
    // Keep it slow for the first drives; raise maxPower when everything works.
    public static double maxPower = 0.6, practiceMaxPower = 0.4, slowSpeed = 0.3;
    public static double strafeCorrection = 1.15, turnKp = 0.012, turnKd = 0.003, moveMinPower = 0.30;
    // false until the Drive Tuner (lesson 6): until then no autonomous drives the robot.
    public static boolean calibrated = false;
}
```

**Why:** `practiceMaxPower` is used in the PRACTICE profile and `maxPower` in COMPETITION, so practice
is safe and matches are fast.
**More:** [Lesson 2: the numbers](tutorials/start/02-drive.md#5-the-numbers-tunablesdrive), [Profiles](guides/profiles.md).

### How do I let one driver do everything?

**Where:** `Controls.java`, at the top of the class.

<!-- recipe: one-driver -->
```java
// true: every button is on gamepad 1 (the operator's buttons move there).
public static boolean oneDriver = true;
```

**Why:** with one driver, the operator's buttons move to gamepad 1, and the stick clicks switch slow mode and field-centric.
**More:** [Lesson 3: one driver or two](tutorials/start/03-buttons.md#3-one-driver-or-two).

### How do I put slow mode on a trigger?

**Where:** `Controls.java`, inside `buttons(...)`. For one driver (with two drivers, LT on gamepad 1 is already slow).

<!-- recipe: slow-trigger -->
```java
// One driver: slow on LT. The further it is pulled, the slower (down to slowSpeed).
g1.lt.slow().says("slow");
```

**Why:** a trigger gives fine control: a little squeeze is a little slower.
**More:** [ControlMap: one driver](modules/robot.md#controlmap).

### Driving that is already built in

No code needed. With `Controls.java` (two drivers):

- **Drive:** gamepad 1 left stick drives (and strafes), right stick X turns. LT: slow (the further, the slower).
- **Field-centric** (mecanum): gamepad 1 **Y** switches it on/off. "Forward" is then away from the drivers.
- **Pause:** tap gamepad 1 **BACK**: everything stops on brake. Tap again to go on.
- **Reset "forward":** hold gamepad 1 **BACK** for 1 second with the robot facing away from you.

**More:** [Lesson 2: the driving you get for free](tutorials/start/02-drive.md#6-the-driving-you-get-for-free), [the standard map](modules/robot.md#controlmap).

## Buttons and gestures

All button recipes go in `Controls.java`, inside `buttons(...)`. That method runs once, at INIT: each
line plugs a job into a button. Every line ends with `;`. `.says("...")` is the text on the button map.

**Pick a free button.** Each part already has standard buttons ([the standard map](modules/robot.md#controlmap)):
for example the claw uses operator **B**, the lift **A**, **X**, **Y**. A job you write on one of them
takes that button from the part (INIT says so). Two jobs that YOU write on one button stop INIT with the
button's name. INIT lists the free buttons. To empty a button first: `g2.b.free();`
([move a button](#how-do-i-move-a-parts-button-to-another-button)).

### How do I make a button do something once?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-press -->
```java
// Once per press. "::" hands over the method; the button runs it later.
g2.y.press(robot.claw::open).says("open the claw");
// Several commands: "() ->" starts a small piece of code (a lambda).
g2.x.press(() -> robot.lift.goTo("low")).says("lift to low");
```

**Why:** `press` runs the job once per press, the moment the button goes down.
**More:** [Lesson 3: add your own jobs](tutorials/start/03-buttons.md#4-add-your-own-jobs), [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I run something only while a button is held?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-hold -->
```java
// Runs every loop while held; the second job runs once when it is let go.
g1.a.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
```

**Why:** without the second job, the intake would keep running after you let go.
**More:** [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I switch something on and off with one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-toggle -->
```java
// Each press switches the intake on or off. rumble(): 2 buzzes = on, 1 buzz = off.
g1.x.toggle(robot.intake).rumble().says("intake on/off");
```

**Why:** `toggle` remembers on or off for you, and the buzz tells the driver which one without looking.
**More:** [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I put a tap, a hold and a double-tap on one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-gestures -->
```java
// Three gestures on one button: tap, hold, double-tap.
g2.rb.tap(robot.launcher::fire).says("fire one")
     .hold(robot.launcher::fireWhileHeld, robot.launcher::ceaseFire).says("keep firing")
     .doubleTap(() -> robot.launcher.fire(3)).says("fire three");
```

**Why:** one button can do several jobs, so the drivers need fewer buttons to remember.
**More:** [The button menu: gestures](guides/buttons.md#several-gestures-on-one-button).

### How do I make a button safe ("hold for 1 second")?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-held-for -->
```java
// Runs once, after the button is held for 1 second: a safe "are you sure?".
g2.dpadLeft.heldFor(1.0, robot.launcher::stop).says("hold 1 s: flywheel off");
```

**Why:** a quick bump of the button does nothing, so a big job cannot start by accident.
**More:** [The button menu: gestures](guides/buttons.md#several-gestures-on-one-button).

### How do I use two buttons together (like a shift key)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-pair -->
```java
// Hold RB (the "shift key"), then press D-pad up. For big jobs that must not run by accident.
g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high")).says("lift to the top");
```

**Why:** a pair is a safe place for rare, big jobs (a climb); D-pad up alone still does its own job.
**More:** [The button menu: two buttons together](guides/buttons.md#two-buttons-together).

### How do I make the gamepad buzz?

**Where:** `Controls.java`, inside `buttons(...)`.

Add `.rumble()` after a job (see [the toggle recipe](#how-do-i-switch-something-on-and-off-with-one-button)),
or buzz when the robot gets ready:

<!-- recipe: rumble-when -->
```java
// A buzz on gamepad 2 each time the flywheel gets to its speed.
g2.rumbleWhen(robot.launcher::isReady);
```

**Why:** the drivers watch the robot, not the Driver Station, so a buzz says "ready" without looking away.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback).

### How do I control a part with a stick?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: stick-use -->
```java
// A stick is a number from -1 to 1 (up and right are +). Its value goes to the job every loop.
g2.rightStickY.use(robot.lift::manual).says("lift by hand");
```

**Why:** a stick gives a number every loop, so the part moves as far as you push it (and holds when you let go).
**More:** [The button menu: sticks](guides/buttons.md#sticks).

### How do I move a part's button to another button?

**Where:** `Controls.java`, inside `buttons(...)`.

Each part brings standard buttons (the intake: operator RT and LT). To move them:

<!-- recipe: move-a-button -->
```java
g2.rt.free();   // free(): no job on it, even the one the intake suggests
g2.lt.free();
g2.lb.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
```

**Why:** `free()` takes the standard job off a button; then you give the new buttons their jobs.
**More:** [Lesson 3: the buttons the parts bring](tutorials/start/03-buttons.md#2-the-buttons-the-parts-bring).

### How do I turn off all the standard buttons?

**Where:** `Controls.java`, inside the class (next to `buttons(...)`).

<!-- recipe: no-standard-buttons -->
```java
// false: the parts bring no buttons of their own. Only the jobs in buttons(...) are used.
@Override
protected boolean useStandardButtons() {
    return false;
}
```

**Why:** some teams want every button written in one place, with no surprises.
**More:** [ControlMap](modules/robot.md#controlmap).

### How do I move the library's own buttons (slow, field-centric, pause)?

**Where:** `Controls.java`, inside the class (next to `buttons(...)`). Import
`com.crossbotics.ftchelper.teleop.LibraryButtons`.

<!-- recipe: move-library-buttons -->
```java
// The library's own buttons (driving, pause, menus...): move them here; null = no button.
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.x);                  // field-centric on/off: X instead of Y
    lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
    lib.switchAlliance(g2.back, g2.start);   // switch alliance: the operator holds both 1.5 s
}
```

**Why:** the old button is then free for your own jobs in `buttons(...)`. Two library jobs on one
button stop INIT with the button's name.
**More:** [The button menu: moving the library's buttons](guides/buttons.md#moving-the-librarys-buttons).

### How do I change the double-tap and hold times?

**Where:** `Tunables.java` (optional: leave the section out for these defaults).

<!-- recipe: tunables-buttons -->
```java
/** Optional: the gesture times. Leave this section out to use these defaults. */
public static class Buttons {
    public static double tapGapSec = 0.35;   // the most time between the taps of a double-tap
    public static double holdSec = 0.3;      // a press this long is a hold, not a tap
}
```

**Why:** some drivers tap slower; these times are in the tuning menu too. A value you tune there,
copy back into `Tunables.java` (the file is the record).
**More:** [The button menu](guides/buttons.md#several-gestures-on-one-button).

### Buttons that are already built in

- **The button map:** INIT shows every button and what it does. During the match, tap **GUIDE**.
- **The tuning menu:** hold **GUIDE** for 1 second (PRACTICE profile). A tap shows the map, a hold opens the menu.
- **Two jobs on one button** stop INIT with the button's name, so a mistake is found before the match.
- **Pause:** tap gamepad 1 **BACK**.
- **Wrong alliance?** Hold gamepad 1 **BACK + START** together 1.5 s: it switches (both gamepads buzz twice).
- **Set pose here** (robots with odometry): hold gamepad 1 **START** 1 s on the reset spot ([below](#how-do-i-set-the-pose-when-autonomous-did-not-run)).

**More:** [Lesson 3: the button map](tutorials/start/03-buttons.md#6-the-button-map-init-and-guide).

## Mechanisms

Each part is one line in `Robot.java`'s `define()`, plus a field for it at the top of the class (for
example `public Lift lift;`) and a section with its numbers in `Tunables.java`. The section's name is
the configuration name in CamelCase (each word starts with a capital letter, no spaces or `_`): `intake_deploy` reads `IntakeDeploy`. Its standard buttons come with it.

### How do I add an intake on a motor?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-motor -->
```java
// At the top of the class: public Intake intake;   Here in define():
intake = add(Intake.motor("intake"));                  // Tunables.Intake: inPower, outPower
```

**Why:** the intake brings its buttons (operator RT in, LT out) and its numbers (`Tunables.Intake`).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Intake](modules/robot.md#intake).

### How do I add an intake on CR servos?

A CR (continuous rotation) servo spins all the way round, like a small motor, instead of going to a position.

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-cr-servos -->
```java
// At the top of the class: public Intake intake;   Here in define():
// two continuous-rotation (CR) servos. The one that spins the wrong way is reversed.
intake = add(Intake.crServos("left_intake", "right_intake").reversed("right_intake"));
```

**Why:** two servos that face each other must turn opposite ways to pull a piece in. The numbers are in `Tunables.Intake` too (`inPower`, `outPower`).
**More:** [Intake](modules/robot.md#intake).

### How do I make the intake run from the start?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-on-at-start -->
```java
// Runs in from START until stopped. Its button (operator RT) then switches it on/off.
add(Intake.motor("intake").onAtStart());
```

**Why:** many robots collect all match, so the driver should not have to switch it on.
**More:** [Intake](modules/robot.md#intake).

### How do I add a lift with set heights?

**Where:** `Robot.java` (the part) and `Tunables.java` (its numbers).

<!-- recipe: lift -->
```java
// At the top of the class: public Lift lift;   Here in define():
// a slide on a motor with a switch at the bottom (the switch sets 0 each time it is pressed).
// .part(...) is optional: the motor's catalog id, so the simulator moves it like the real
// motor (without it the robot works the same). No switch? Delete .bottomSwitch(...).
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

<!-- recipe: tunables-lift -->
```java
public static class Lift {
    public static double min = 0, max = 2600;              // the safe range, counts (Motor Finder)
    public static double kP = 0.004, kI = 0, kD = 0.0002, kF = 0.05;
    public static double down = 0, low = 900, high = 2400; // named heights: any names you like
}
```

**Why:** the lift never goes past `min` and `max`, so it cannot break itself; INIT stops if they are missing.
`.part(...)` is optional on a lift, intake, claw or wrist: it tells the simulator which motor or servo
it is. An arm, a turret and a launcher take the motor's id in their brackets instead, and need it.
The numbers here are examples: find yours with the [Motor Finder](#how-do-i-find-a-lifts-limits-and-heights).
`"lift"` must be the motor's name in your configuration. In `.part("gobilda-5203-435")`, 5203 is the
goBILDA motor series and 435 its RPM (on the motor's label); the [parts catalog](guides/parts-catalog.md) lists them. To send it to a height: [from a button](#how-do-i-send-a-part-to-a-position-from-a-button).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#2-their-numbers-in-tunablesjava), [Lift](modules/robot.md#lift),
[why the bottom switch matters](tutorials/start/04-lift-and-claw.md#4-why-the-bottom-switch-matters-homing).

### My lift goes down when it should go up. How do I fix it?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: lift-reversed -->
```java
// The lift goes DOWN on positive power (the Motor Finder says "add .reversed()"): flip it.
add(Lift.slide("lift").reversed());
```

**Why:** the lift's limits and heights count up as it goes up, so positive power must move it up.
**More:** [Motor Finder](guides/finders.md#motor-finder-limits-positions-top-speed), [Troubleshooting](troubleshooting.md#robot-behavior).

### How do I add an arm that moves in degrees?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: arm -->
```java
// An arm in degrees. Its motor's catalog id is in the brackets (not optional, like a turret's
// or launcher's): it needs the motor's counts per turn to work in degrees.
// gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

<!-- recipe: tunables-arm -->
```java
public static class Arm {
    public static double min = -10, max = 120;              // degrees, 0 = where it is at INIT
    public static double kP = 0.02, kI = 0, kD = 0.001, kF = 0.1;
    public static double stowed = 0, pickup = 10, score = 95;
}
```

**Why:** degrees are easier to think about than encoder counts; INIT the program with the arm at its 0.
**More:** [Arm](modules/robot.md#arm).

### How do I add a claw?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: claw -->
```java
// At the top of the class: public Claw claw;   Here in define():
claw = add(Claw.servo("claw"));                        // Tunables.Claw: open, closed
```

<!-- recipe: tunables-claw -->
```java
public static class Claw {
    // From the Servo Finder's line "Tunables.Claw: P1 = 0.210, P2 = 0.770", P1 and P2 renamed.
    public static double open = 0.210, closed = 0.770;
    public static double travelSec = 0.4;                   // about how long it takes to move
}
```

**Why:** the claw brings its button (operator B opens and closes), and the Servo Finder gives the two numbers.
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Claw](modules/robot.md#claw).

### How do I add a wrist?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: wrist -->
```java
// At the top of the class: public Wrist wrist;   Here in define():
wrist = add(Wrist.servo("wrist"));                     // Tunables.Wrist: any names (down, level, up)
```

**Why:** the wrist brings its buttons (operator D-pad left and right step through the positions).
**More:** [Wrist](modules/robot.md#wrist).

### How do I add any servo with named positions (a gate, a fold-out)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: positioner -->
```java
// Any servo with named positions: a gate, a kicker, a fold-out intake.
intakeDeploy = add(Positioner.servo("intake_deploy")); // Tunables.IntakeDeploy: stowed, deployed
// Right after START (autonomous and TeleOp): fold the intake out.
atStart(() -> Action.named("Deploy").then(() -> intakeDeploy.goTo("deployed")));
```

**Why:** a `Positioner` has no standard buttons, so you choose when it moves (here: at START).
**More:** [Positioner](modules/robot.md#positioner), [an action at START](#how-do-i-run-an-action-right-after-start).

### How do I send a part to a position from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: mechanism-commands -->
```java
// The names are the ones in Tunables.java (capitals do not matter).
g2.y.press(() -> robot.arm.goTo("score")).says("arm to score");
g2.a.press(() -> robot.arm.goTo("pickup")).says("arm to pick up");
g2.x.press(() -> robot.lift.goTo("high")).says("lift to high");
g2.b.press(robot.claw::toggle).says("claw open/close");
g2.back.press(() -> robot.intakeDeploy.goTo("stowed")).says("fold the intake in");
```

**Why:** `goTo("name")` uses the position by its name, so the number lives only in `Tunables.java`.
These lines take Y, A, X and B from the lift and the claw (their standard buttons), on purpose; see
[pick a free button](#buttons-and-gestures).
**More:** [Lift](modules/robot.md#lift), [Claw](modules/robot.md#claw).

### How do I add a launcher (flywheel and feeder)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher -->
```java
// A flywheel (in RPM) and the indexer that feeds it one piece at a time.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer")));
```

<!-- recipe: tunables-launcher -->
```java
public static class Launcher {
    public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
    public static double kP = 40, kI = 0, kD = 0, kF = 12.5;   // from the Flywheel Finder
    // {inches to the goal, RPM}: blended between rows (from the Shot Tuner).
    public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
}
```

**Why:** the launcher only feeds a piece when the wheel is at speed, so every shot flies the same way.
**More:** [Lesson 9](tutorials/start/09-shooter-and-camera.md#1-a-flywheel-and-its-feeder), [Launcher](modules/robot.md#launcher).

### How do I add a turret that follows the goal?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: turret-follows-camera -->
```java
// The camera knows the goal of each alliance (BLUE first, then RED).
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
// 5 motor turns per turret turn. follows(camera): it follows the goal from START,
// but never turns past Tunables.Turret min and max.
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
```

**Why:** the turret aims for the driver, and its limits keep the wires from twisting.
**More:** [Lesson 9: the turret](tutorials/start/09-shooter-and-camera.md#6-a-turret-that-follows-it), [Turret](modules/robot.md#turret), [Camera](modules/robot.md#camera).

### How do I fire only when aimed, at the right speed?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher-aimed -->
```java
// The speed comes from the distance to the goal (Tunables.Launcher.speedByDistance),
// and a piece is only fed when the turret points at the goal.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer"))
        .speedFromDistance(camera)
        .requireReady("turret aimed", () -> turret.isAimed()));
```

**Why:** a shot fired before the turret is on target, or at the wrong speed, is a lost piece. For a
turret that follows the goal, `isAimed()` means on the goal NOW: losing the goal makes it false.
**More:** [Lesson 9](tutorials/start/09-shooter-and-camera.md#7-speed-from-distance-and-fire-only-when-aimed).

## Game pieces

A `Pieces` line in `Robot.java` counts the pieces the robot holds and knows which kind fires next,
from a sensor at the intake and (optional) one at the "ready to fire" slot. Each kind's colour (or
size) goes in `Tunables.java`, measured with the Piece Tuner. All of it: [Pieces and PieceSensor](modules/robot.md#pieces-and-piecesensor).

### How do I know which piece is in the robot, and how many?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: pieces-count -->
```java
// At the top of the class: public Pieces pieces;   Here in define():
// the kinds of piece (BIOBUZZ: yellow POLLEN, red and blue NECTAR), a sensor where they
// come in, one at the "ready to fire" slot (optional), the most the rules allow, the preload.
pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
        .comingIn(PieceSensor.color("intake_color"))    // a REV Color Sensor V3
        .readyToFire(PieceSensor.color("ready_color"))  // another one
        .max(4)                                         // BIOBUZZ G407: no more than 4
        .startsWith(4, "POLLEN"));                      // the 4 POLLEN preload
```

**Why:** each piece the intake sensor sees counts one in; each feed of the indexer counts one out, the
oldest first, once the piece has gone (a jammed feed counts nothing). Until a kind is tuned with the
Piece Tuner it reads `"UNKNOWN"`. `robot.pieces.count()`, `robot.pieces.next()` ("POLLEN", "UNKNOWN" or "NONE") and
`robot.pieces.isFull()` work anywhere in your code.
**More:** [Pieces and PieceSensor](modules/robot.md#pieces-and-piecesensor).

### How do I stop the intake when the robot is full?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: intake-stops-when-full -->
```java
// ADD .stopsWhen(...) to the intake line you already have (do not write a second intake line).
// It does not pull in a 5th piece ("() ->": asked every loop). Pushing out still works.
intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));
```

<!-- recipe: launcher-needs-a-piece -->
```java
// Each feed counts one piece out. requireReady: no feed with nothing to fire.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer"))
        .requireReady("a piece to fire", () -> !pieces.isEmpty()));
```

**Why:** BIOBUZZ rule G407: a robot may not control more than 4 pieces. The rules ask for "systems to
prevent active pickup" of a 5th: this is one. Put the intake sensor where a piece is already held, so
the intake does not stop with a piece half in. The count can be wrong (two pieces read as one), so the
drivers, not the count, are responsible for G407: when it says full and the robot is not, holding RT
for 1 s pulls in anyway, and the Driver Station warns when the intake is held back for more than 2 s.
**More:** [Intake](modules/robot.md#intake).

### How do I tell pieces apart by size, or with a second camera?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: pieces-other-sensors -->
```java
// A REV 2m Distance Sensor across the intake: a NECTAR (3.6 in) comes closer than a
// POLLEN (2.8 in). Size only: it cannot tell a red NECTAR from a blue one.
// A second webcam on the ready slot: colours (the main colour in a box of its picture).
pieces = add(Pieces.kinds("POLLEN", "NECTAR")
        .comingIn(PieceSensor.distance("intake_distance"))
        .readyToFire(PieceSensor.camera("Webcam 2"))
        .max(4));
```

**Why:** a distance sensor reads 20 mm closer with a NECTAR than with a POLLEN, but it sees no colour,
and rule G408 forbids controlling the other alliance's NECTAR. A webcam sees colours from farther
away, but not how far the piece is.
**More:** [the sensors compared](modules/robot.md#pieces-and-piecesensor).

### How do I use the count (buzz, LEDs, the shot table)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: pieces-use -->
```java
// A buzz on gamepad 2 when the robot holds the most it may.
g2.rumbleWhen(robot.pieces::isFull);
// Two buzzes when a NECTAR is next to fire.
g2.rumbleWhen(robot.pieces.whenNextIs("RED_NECTAR"), 2);
// A light the drivers see: green = full, red = empty (needs a Lights line in Robot.java, and
// import com.crossbotics.ftchelper.robot.LightColor; at the top of this file).
robot.lights.show(LightColor.GREEN).when(robot.pieces.whenFull());
robot.lights.show(LightColor.RED).when(robot.pieces.whenEmpty());
// Pieces shows "3/4, next POLLEN" by itself; this adds what the intake sees now.
show("At the intake", robot.pieces::atIntake);
// The count went wrong? Already on the buttons: hold G2's left stick button 1 s = count 0,
// and hold the intake's RT 1 s to pull in although the count says full.
```

**Why:** `whenFull()`, `whenEmpty()` and `whenNextIs("POLLEN")` are conditions any part can watch: a
buzz, a light (the [Lights](#lights) recipes), an action that waits. Pieces brings a button too: hold
G2's left stick button 1 s to set the count to 0 when it went wrong (pieces taken out by hand). For a shot table per kind, give the launcher
`.tableFrom(pieces.nextKind()).tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR")` in `Robot.java`: the next
piece picks its table, and both NECTAR colours use the `nectar` table.
**More:** [hooks for other parts](modules/robot.md#pieces-and-piecesensor).

### How do I tune the piece sensors?

**Where:** a new file next to `Robot.java`, e.g. `PieceTuner.java`.

<!-- recipe: piece-tuner -->
```java
// The Piece Tuner: each sensor's readings, and the numbers for Tunables.java.
@TeleOp(name = "Cookbook Piece Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public static class PieceTuner extends PieceTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner finds its piece sensors
    }
}
```

**Why:** each sensor and each robot reads colours a little differently. Hold each kind in front of the
sensor, press A, then copy the lines it shows into `Tunables.java`.
**More:** [Piece Tuner](modules/finders.md#piecetuneropmode).
## Aiming and shooting

Things the robot does for the drivers, so they can focus on driving and firing. They all use one idea:
**the camera first, the odometry as a backup** ([Aim](modules/robot.md#aim)).

### How do I make the whole robot aim at the goal (no turret)?

**Where:** `Controls.java`, inside `buttons(...)` (and `Tunables.java` for the numbers, below). The
robot needs a camera with a [`goal(...)`](tutorials/start/09-shooter-and-camera.md#5-a-camera-that-knows-the-goal)
(or odometry with a goal point). Not with a turret that follows the goal: turret-following and
whole-robot aim are separate modes, so INIT stops with a message if both are there.

<!-- recipe: aim-whole-robot -->
```java
// RB: the robot turns to face the goal by itself; the driver keeps forward and sideways.
// Moving the turn stick still turns it by hand. Two buzzes = on, one = off.
// Not with a turret that follows the goal: separate modes (INIT stops with a message).
g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
```

**Why:** the driver drives; the robot keeps itself pointed at the goal. The stick always wins, so the
driver can take over at once.
**More:** [Lesson 9: aim the whole robot](tutorials/start/09-shooter-and-camera.md#10-no-turret-aim-the-whole-robot).

### How do I make the gamepad buzz when the aim is on target?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: aim-rumble -->
```java
// Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
// It works for a turret that follows the goal and for whole-robot aim.
g2.rumbleWhenAimed();
```

**Why:** the driver who fires watches the field, not the screen: the buzz says "now".
**More:** [Lesson 9: feel the aim](tutorials/start/09-shooter-and-camera.md#11-feel-the-aim-a-buzz-when-on-target).

### How do I keep aiming when the camera loses the goal?

**Where:** `Robot.java`, inside `define()`, and the goal's place in `Tunables.java`.

<!-- recipe: aim-odometry-backup -->
```java
// With an odometry line and Tunables.Aim goalX, goalY, the turret and the speed keep
// working when the camera loses the goal: from where the robot is on the field.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.servo("indexer"))
        .speedFromDistance(camera)
        .startTable("pollen"));                  // the table to start with
```

<!-- recipe: tunables-aim -->
```java
/** Aiming (every line is optional; leave out what you do not use). */
public static class Aim {
    // The goal on the field, in inches, seen from the BLUE drivers' wall (the same numbers as
    // driveTo): with odometry, aiming goes on when the camera loses the goal.
    public static double goalX = -58, goalY = 55;
    // Default true (RED mirrors x). BIOBUZZ's field is turned, not mirrored: false.
    public static boolean mirrorForRed = false;
    // Whole-robot aim: turn power per degree off, the most it turns by itself, "aimed" within.
    public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, toleranceDeg = 2;
    public static double rumbleDeg = 4;           // rumbleWhenAimed(): buzz within 4 degrees
}
```

**Why:** a tag gets blocked by a robot or a game piece; the odometry still knows where the goal is. The
Driver Station says which one aims (`following: camera` or `following: odometry`). It needs the pose
from autonomous (or your `setPose`): without it, the backup stays off and the Driver Station says so.
`mirrorForRed`: leave it out (default `true`) for a mirrored field; BIOBUZZ's turned field needs `false`.
INIT shows the alliance and this choice. The numbers in
`Tunables.Aim` are in the tuning menu too: copy tuned values back into `Tunables.java`.
**More:** [Lesson 9: when the camera loses the goal](tutorials/start/09-shooter-and-camera.md#9-when-the-camera-loses-the-goal-the-odometry), [Lesson 7: odometry](tutorials/start/07-odometry.md).

### How do I use one shot table per game piece?

**Where:** `Tunables.java` (the tables), then `Controls.java` or `Actions.java` to switch.

<!-- recipe: tunables-shot-tables -->
```java
public static class Launcher {
    public static double speedRpm = 2600, stepRpm = 100, readyBandRpm = 60, idleRpm = 1500;
    // One table per game piece ({inches to the goal, RPM}), from the Shot Tuner. The name
    // after "speedByDistance" is the table's name: "pollen" and "nectar".
    public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};
    public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
}
```

<!-- recipe: shot-table-buttons -->
```java
// One button per game piece (the launcher's D-pad right already picks the next table).
g2.x.press(() -> robot.launcher.useTable("pollen")).says("POLLEN shot table");
g2.b.press(() -> robot.launcher.useTable("nectar")).says("NECTAR shot table");
```

<!-- recipe: shot-table-action -->
```java
/** An action that picks the NECTAR table, then fires one (Actions.java). */
public static Action fireNectar(Robot r) {
    return Action.named("Fire NECTAR")
            .then(() -> r.launcher.useTable("nectar"))
            .then(r.launcher::fire);
}
```

**Why:** POLLEN (2.8 in) and NECTAR (3.6 in) fly differently, so each needs its own speeds. Fill each
table with the [Shot Tuner](guides/finders.md#shot-tuner-the-shot-table) ([its file](#how-do-i-build-a-shot-table-speed-for-each-distance);
Y until `> Table`, then the D-pad picks the one you tune), then copy its line into `Tunables.java`.
**More:** [Lesson 9: two shot tables](tutorials/start/09-shooter-and-camera.md#8-two-game-pieces-two-shot-tables), [Launcher](modules/robot.md#launcher).

## Actions

An action is a list of steps that use several parts, with waits between them. It never makes the robot
wait: it does a little every loop. Actions go in `Actions.java`, one method each. The same action works
on a button and in an autonomous.

### How do I do several things one after the other?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-steps -->
```java
/** Lift up, open the claw, lift down: one step after the other. */
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // at most 2 s
            .then(r.claw::open)
            .waitSeconds(0.3)                                        // let the piece drop
            .then(() -> r.lift.goTo("down"));
}
```

**Why:** each step starts when the one before it is done, and the driver can still drive the whole time.
**More:** [Lesson 5: scoreHigh](tutorials/start/05-actions.md#2-scorehigh-line-by-line), [Action](modules/robot.md#action).

### How do I repeat steps several times?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-repeat -->
```java
/** Pulses the intake 3 times: the for loop adds the same steps 3 times to the action. */
public static Action pulseThree(Robot r) {
    Action a = Action.named("Pulse three");
    for (int i = 0; i < 3; i++) {   // runs once, when the action is made: it never waits
        a.then(r.intake::in)
         .waitSeconds(0.4)
         .then(r.intake::stop)
         .waitSeconds(0.3);
    }
    return a;
}
```

**Why:** the `for` loop runs once, while the action is made, and writes the steps 3 times; the action then does them one after the other without making the robot wait.
**More:** [Action](modules/robot.md#action).

### How do I move two parts at the same time?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-together -->
```java
/** Two parts at once: then(a, b) starts both in the same loop. */
public static Action grabAndLift(Robot r) {
    return Action.named("Grab and lift")
            .then(r.claw::close, r.intake::stop)                     // both at once
            .waitUntil("claw closed", r.claw::isDone, 1.0)           // isDone: it should be there
            .then(() -> r.lift.goTo("low"));
}
```

**Why:** `then(a, b)` starts both parts in the same loop, so neither waits for the other.
**More:** [Lesson 5: grabAndLift](tutorials/start/05-actions.md#3-grabandlift).

### How do I wait half a second (like sleep in Blocks)?

Search words: sleep, wait, pause, delay. **In an action** (`Actions.java`):

<!-- recipe: wait-in-action -->
```java
/** Opens the claw, waits half a second, then lowers the lift. */
public static Action dropAndLower(Robot r) {
    return Action.named("Drop and lower")
            .then(r.claw::open)
            .waitSeconds(0.5)                     // half a second; the robot keeps running
            .then(() -> r.lift.goTo("down"));
}
```

**In an autonomous** (inside `steps(...)`): the step gets a name first, for the report:

<!-- recipe: wait-in-auto -->
```java
s.run("Open the claw", robot.claw::open)
 .waitSeconds("Let the piece drop", 0.5)    // a name for the report, then the seconds
 .run("Lift down", () -> robot.lift.goTo("down"));
```

**Why:** `sleep()` stops the whole robot (no driving, no other part); `waitSeconds` waits while everything keeps running.
**More:** [Lesson 5: the rules](tutorials/start/05-actions.md#5-the-rules-every-action-follows), [Non-blocking code](guides/non-blocking.md).

### How do I wait until something is true?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-wait-until -->
```java
/** Lowers the lift, and opens the claw only once the lift is down. */
public static Action lowerAndRelease(Robot r) {
    return Action.named("Lower and release")
            .then(() -> r.lift.goTo("down"))
            // Waits until the test is true. If 2 s pass first, the action STOPS here and says why.
            .waitUntil("lift down", () -> r.lift.isAt("down"), 2.0)
            .then(r.claw::open);
}
```

**Why:** every wait has a time limit, so a stuck part can never freeze the action forever.
**More:** [Lesson 5: the rules](tutorials/start/05-actions.md#5-the-rules-every-action-follows).

### How do I start an action from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-button -->
```java
// Press Y: the action starts. Press Y again while it runs: it is cancelled, safely.
g2.y.press(Actions.scoreHigh(robot)).says("score high");
g2.x.press(Actions.grabAndLift(robot)).says("grab and lift");
```

**Why:** the same button cancels it, and a stick or button that moves the same part also stops it: the driver always wins.
**More:** [Lesson 5: start it from a button](tutorials/start/05-actions.md#4-start-it-from-a-button).

### How do I stop every running action?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-cancel-all -->
```java
// Stop every running action at once (each stops what it started, safely).
g2.back.press(robot::cancelAll).says("stop all actions");
```

**Why:** a stopped action leaves each part safe: a lift holds its height, an intake stops.
**More:** [RobotDefinition: for your code](modules/robot.md#robotdefinition).

### How do I run an action right after START?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: action-at-start -->
```java
// In Robot.java's define(): this action runs once, right after START (autonomous and TeleOp).
atStart(() -> Actions.deployIntake(this));
```

**Why:** nothing may move in INIT (a rule), so a fold-out waits for START.
**More:** [Lesson 5: an action at START](tutorials/start/05-actions.md#7-an-action-at-start).

### How do I use the same action in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: action-in-auto -->
```java
s.start("Against our wall, facing the field", 0)
 .move("To the goal", 24, 0, 0.5)
 .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
 .move("Back up", 12, 180, 0.5);
```

**Why:** write the move once, use it in TeleOp and autonomous; the report says if it timed out.
**More:** [Lesson 5: in an autonomous](tutorials/start/05-actions.md#6-the-same-action-in-an-autonomous).

## Autonomous

An autonomous is one file in `autos/`: a list of named steps. Distances are in **inches** (1 foot = 12
inches, so 2 feet = 24), directions in degrees (0 = forward, 90 = right), power from 0 to 1.

**The robot only drives after the Drive Tuner.** Until then the autonomous shows "AUTONOMOUS IS OFF".
Run the [Drive Tuner](#how-do-i-tune-the-drive-for-autonomous), copy its lines, then set
`calibrated = true` in `Tunables.java`, section `Drive`. The simulator runs it anyway, so you can try a
route before tuning.

### How do I make my first autonomous (drive 2 feet)?

**Where:** a new file in `autos/` (in Android Studio: right-click the folder, New, Java Class). The whole file:

<!-- recipe: auto-first -->
```java
// The name on the Driver Station, and the TeleOp it picks next (use YOUR TeleOp's name).
@Autonomous(name = "Cookbook Leave the Start", group = "Cookbook", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy, or the Driver Station will not list it
public class LeaveTheStart extends Auto<Robot> {
    @Override
    protected void steps(Steps s, Robot robot) {
        // s = the list of steps, robot = your Robot.java. Step lines go here:
        s.start("Against our wall, facing the field", 0)        // the note the drive team reads
         .move("Leave the start", 24, 0, 0.4);                  // 24 inches = 2 feet, forward, 0.4 power
    }
}
```

**Why:** `preselectTeleOp` makes the Driver Station pick your TeleOp next, so the drivers only press INIT.
**More:** [Lesson 6: the file, line by line](tutorials/start/06-first-autonomous.md#2-the-file-line-by-line).

### How do I drive forward, sideways and turn?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-moves -->
```java
s.start("Against our wall, facing the field", 0)
 .move("Forward", 24, 0, 0.5)            // inches, direction (0 = forward), max power
 .move("Sideways to the right", 12, 90, 0.5)   // mecanum only
 .turn("Turn left 90", 90, 0.4)         // turns: counterclockwise (left) is +
 .turn("Turn right 90", -90, 0.4)       // so a right turn is minus
 .turnTo("Face the start way", 0, 0.4); // to a heading: 0 = the way it faced at the start
```

**Why:** each move is measured from where the robot is now, so the steps read like directions to a person.
**More:** [Driving: move, turn, turnTo](guides/autonomous-builder.md#driving-move-turn-turnto).

### Does my autonomous need a RED version?

No. Write it once, for BLUE. On BIOBUZZ's field (turned half a turn between the alliances) add this
to the autonomous class:

<!-- recipe: auto-turned-field -->
```java
// BIOBUZZ's field is turned half a turn between the alliances: one route works for both.
@Override
protected boolean mirrorForRed() {
    return false;
}
```

**Why:** RED then uses the same numbers, seen from the red wall; on a mirrored field (the default), RED is flipped for you.
**More:** [The autonomous builder: placing the robot](guides/autonomous-builder.md#1-placing-the-robot).

### How do I let the drivers choose before the match?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-menu -->
```java
s.start("Against our wall, facing the field", 0)
 .menuYesNo("Score", true)                  // the drivers choose in INIT with the D-pad
 .menuChoice("Park", "Near", "Far")
 .onlyIf("Score")                           // these steps run only if "Score" is yes
     .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.5)
     .action(Actions.scoreHigh(robot), 5)
 .end()                                     // never forget end()
```

**Why:** one file covers several plans, and the drivers choose in INIT with the D-pad (alliance and start delay come free).
**More:** [The INIT menu](guides/autonomous-builder.md#2-the-init-menu), [Branches: onlyIf](guides/autonomous-builder.md#5-branches-onlyif).

### How do I make sure there is time to park?

Add `parkWithSeconds` before the park steps (this follows the menu recipe above):

<!-- recipe: auto-park -->
```java
.parkWithSeconds(4)                        // the steps below always get the last 4 s
.onlyIf("Park", "Near")
    .move("Park near", Tunables.Auto.parkInches, 90, 0.7)
.end()
.onlyIf("Park", "Far")
    .move("Park far", Tunables.Auto.parkInches * 2, 90, 0.7)
.end();
```

**Why:** when 4 s are left, the main steps stop, so a slow step can never eat the parking points.
**More:** [Keep time to park](modules/robot.md#keep-time-to-park).

### How do I run a mechanism while the robot drives?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-run-while-driving -->
```java
s.start("Against our wall, facing the field", 0)
 .run("Intake on", robot.intake::in)        // starts it and goes on at once
 .run("Lift up", () -> robot.lift.goTo("high"))
 .move("To the pieces", 24, 0, 0.4)         // the intake and lift work while it drives
 .waitUntil("Lift at the top", robot.lift::isAtTarget, 2.0)
 .run("Intake off", robot.intake::stop);
```

**Why:** `run` starts a part and goes on at once; `waitUntil` waits only when the next step needs it.
**More:** [Mechanisms: run and wait](guides/autonomous-builder.md#3-mechanisms-run-and-wait). Whole actions while driving: `alongside(...)` ([recipes](guides/autonomous-builder.md#recipes)).

### How do I wait for my alliance partner?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-wait-partner -->
```java
s.menuYesNo("Wait for partner", false)
 .onlyIf("Wait for partner")
     .waitSeconds("Let our partner pass", 3)   // the robot keeps running while it waits
 .end();
```

**Why:** the drivers decide in INIT; for a wait before the first step, the menu already has a start delay.
**More:** [The autonomous builder: recipes](guides/autonomous-builder.md#recipes).

### How do I write a step the list cannot say?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-own-step -->
```java
// Your own step, for anything the list cannot say. Wait with ctx, never sleep().
s.step("Clear the intake", ctx -> {
    robot.intake.out();
    ctx.waitFor(0.3);          // every part keeps working while it waits
    robot.intake.stop();
});
```

**Why:** `ctx.waitFor` keeps every part updating and the timer working; `sleep()` would freeze them.
**More:** [Your own steps](guides/autonomous-builder.md#6-your-own-steps).

### How do I change a distance without building the code again?

**Where:** `Tunables.java`: your own section (no part reads it). Use it in a step as `Tunables.Auto.leaveInches`.

<!-- recipe: tunables-auto -->
```java
/** Your own numbers: a section no part reads. Every number here is in the tuning menu too. */
public static class Auto {
    public static double leaveInches = 24, toGoalInches = 30, parkInches = 20;
    public static double goalX = -30, goalY = -30, goalHeading = 45;
    public static double parkX = -56, parkY = -36;
}
```

**Why:** every number in your own section is in the tuning menu: change it in the TeleOp, save (A), and
the autonomous uses it from its next INIT. **Then copy the good value into `Tunables.java`**: a value
saved only on the hub is lost with a new Control Hub.
**More:** [Lesson 6: route numbers](tutorials/start/06-first-autonomous.md#3-route-numbers-live-in-tunablesauto), [Tunables.java](modules/robot.md#tunablesjava).

### How do I share the same first steps in several autos?

**Where:** your autonomous class (or a shared file), next to `steps(...)`.

<!-- recipe: auto-shared-opening -->
```java
/** The same first steps for several autos: a method that adds them to the list. */
static Steps scorePreload(Steps s, Robot robot) {
    return s.move("To the goal", Tunables.Auto.toGoalInches, 0, 0.5)
            .action(Actions.scoreHigh(robot), 5);
}
// in each autonomous:  scorePreload(s.start("...", 0), robot).move("Park", 20, 90, 0.7);
```

**Why:** fix the opening once and every autonomous gets the fix.
**More:** [The autonomous builder: recipes](guides/autonomous-builder.md#recipes).

## Odometry and driveTo

Odometry tells the robot where it is on the field. **Field numbers:** x = inches right of the centre
line, y = inches away from your wall (negative = your half), heading 0 = facing away from you,
counterclockwise (left) is +. See the [field map](tutorials/start/07-odometry.md#1-why-odometry).

### How do I add a goBILDA Pinpoint?

**Where:** `Robot.java`, inside `define()`, after the drive line.

<!-- recipe: odometry-pinpoint -->
```java
// After drive(...). The offsets, mm: first how far LEFT of the centre the forward pod is
// (right = minus: here 84 mm right), then how far FORWARD the sideways pod is (behind = minus).
// Not sure? Write offsetsMm(0, 0): the Odometry Tuner measures them.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
```

**Why:** the offsets tell it where the pods are, so a turn on the spot does not look like a move.
**More:** [Lesson 7: one line in Robot.java](tutorials/start/07-odometry.md#4-one-line-in-robotjava), [Pinpoint](modules/odometry.md#pinpoint).

### How do I add a SparkFun OTOS?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-otos -->
```java
// Where the OTOS sits: inches forward and left of the robot's centre, and its turn in degrees.
odometry(Odometry.otos("otos").mountIn(3.5, 0, 0));
```

**Why:** the OTOS measures at its own spot, so it must know where it sits to give the robot centre's place.
**More:** [OTOS](modules/odometry.md#otos).

### How do I add dead wheels?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-dead-wheels -->
```java
// The configuration names of the motor ports the pods plug into: left, right, sideways pod.
// trackWidthIn: left pod to right pod. perpForwardIn: the sideways pod, 6 in BEHIND the centre.
odometry(Odometry.threeWheels("par_left", "par_right", "perp")
        .trackWidthIn(13.2).perpForwardIn(-6));
```

**Why:** each pod's encoder plugs into a motor port, so the names are those ports' configuration names.
**More:** [Three dead wheels](modules/odometry.md#three-dead-wheels).

### How do I tune the odometry?

**Where:** a new file in TeamCode (one per robot). The whole file:

<!-- recipe: odometry-tuner -->
```java
// The Odometry Tuner: which way each pod counts, the offsets, and a loop check.
@TeleOp(name = "Cookbook Odometry Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class OdometryTuner extends OdometryTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner uses its parts and numbers
    }
}
```

**Why:** it checks which way each pod counts and measures the offsets; then set `Tunables.Odometry.checked = true`, or `driveTo` does nothing on the robot.
It is its own small file because each program in the Driver Station's list is one class; `robot()` makes
it use your `Robot.java`, so it measures the same odometry line that autonomous uses.
**More:** [Lesson 7: the Odometry Tuner](tutorials/start/07-odometry.md#5-the-odometry-tuner), [Finders](guides/finders.md#odometry-tuner-directions-offsets-and-a-loop-check).

### How do I drive to a point on the field?

**Where:** a file in `autos/`, inside `steps(...)`. Needs a mecanum drive.

<!-- recipe: drive-to -->
```java
// The start spot (x, y, heading) must be where the robot really stands: the pose starts there.
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
 .parkWithSeconds(4)
 // The last number: "close enough", in inches (default 1).
 .driveTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.7, 2.0);
```

It replaces `.move(...)`: you give a place on the field, not a distance.
**Why:** `driveTo` keeps checking where the robot is, so a bump or wheel slip does not matter: it ends on the point.
On the robot it only works after the [Odometry Tuner](#how-do-i-tune-the-odometry) and `checked = true` in `Tunables.java`, section `Odometry`.
**More:** [Lesson 7: drive to field points](tutorials/start/07-odometry.md#6-drive-to-field-points), [driveTo](guides/autonomous-builder.md#drive-to-a-field-point-driveto).

### How do I show where the robot is?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-pose -->
```java
show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in, heading %.0f",
        robot.pose().x, robot.pose().y, robot.pose().headingDeg));
```

**Why:** you can check the odometry by pushing the robot by hand and watching the numbers.
**More:** [robot.pose()](modules/odometry.md#robotpose). (TeleOp also starts where autonomous ended: [lesson 7](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended).)

### How do I use the robot's position in an action?

**Where:** `Actions.java`, inside the class.

<!-- recipe: pose-in-action -->
```java
/** Runs the intake while the robot is on our half of the field (y below 0). */
public static Action intakeOnOurHalf(Robot r) {
    return Action.named("Intake on our half")
            .then(r.intake::in)
            .waitUntil("past the centre line", () -> r.pose().y > 0, 10.0)
            .then(r.intake::stop);
}
```

**Why:** `robot.pose()` is fresh every loop, so a wait can test where the robot is.
**More:** [robot.pose()](modules/odometry.md#robotpose).

### How do I set the pose when autonomous did not run?

**Where:** `Tunables.java`, section `Odometry` (and `Controls.java` for more spots).

Autonomous was stopped early, or did not run: TeleOp does not know where the robot is. Park it on the
reset spot, then hold gamepad 1 **START** for 1 second ("set pose here"). The spot, written for BLUE
like a start spot (RED mirrors it):

<!-- recipe: tunables-reset-spot -->
```java
public static class Odometry {
    public static boolean checked = false;   // true after the Odometry Tuner's Loop test passes
    // "Set pose here" (TeleOp: hold gamepad 1 START 1 s with the robot on this spot). Inches and
    // degrees seen from your wall, written for BLUE like a start spot: our left corner tile, facing the field.
    public static double resetX = -56, resetY = -61, resetHeading = 0;
}
```

More spots, or from an action (inside `buttons(...)`):

<!-- recipe: reset-pose-button -->
```java
// A second spot of your own: hold D-pad down 1 s with the robot in the right corner tile.
// (x, y, heading for BLUE; RED mirrors it like the start spot.)
g1.dpadDown.heldFor(1.0, () -> robot.resetPose(56, -61, 0)).says("hold 1 s: pose = right corner tile");
// An action can set it too (here: the Tunables.Odometry spot, then the intake runs).
g1.dpadUp.heldFor(1.0, Actions.poseHereThenIntake(robot)).says("hold 1 s: pose here, intake on");
```

**Why:** with a pose, the aim's odometry backup and field-centric "forward" work again. Holding 1 s
means a bump of the button does nothing.
**More:** [Lesson 7: TeleOp starts where autonomous ended](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended), [resetPose()](modules/robot.md#robotdefinition).

## Pedro paths

Needs the `pedro` folder in your project ([add Pedro](tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)),
a mecanum drive and tuned odometry. Paths use the same field numbers as `driveTo`.

### How do I add Pedro Pathing to Robot.java?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: pedro-follower -->
```java
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // Pedro needs odometry
// Pedro drives the paths with the drive and odometry lines above. PedroConstants holds the
// numbers from Pedro's Foresight tuner (docs/modules/pedro.md#tuning-pedro).
follower(Pedro.follower(PedroConstants.foresight));
```

**Why:** Pedro uses your drive and odometry lines, so there is only one robot description.
**More:** [Lesson 8: Robot.java](tutorials/start/08-paths-with-pedro.md#4-robotjava-one-line-and-pedros-numbers), [Tuning Pedro](modules/pedro.md#tuning-pedro).

### How do I make a straight path and a curve?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-line-curve -->
```java
// Make each path once. A line: from (x1, y1) to (x2, y2). A curve: start, a control point
// that pulls the curve toward it (the robot does not drive through it), end.
static final PedroPath OUT = PedroPath.line(-56, -61, -56, -36).keepHeading(0);
static final PedroPath TO_GOAL = PedroPath.curve(-56, -36, -44, -36, -30, -30).turnTo(0, 45);
```

**Why:** a path is made once (`static final`) because it is the same every match.
**More:** [PedroPath](modules/pedro.md#pedropath-paths-in-our-field-numbers).

### How do I follow a path in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: pedro-follow -->
```java
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .follow("Out", OUT)                     // brakes to a stop at its end
 .follow("To the goal", TO_GOAL)
 .run("Intake on", robot.intake::in)
 .follow("Back", WATCH_GOAL)
 .parkWithSeconds(4)
 .driveTo("Park", -56, -36, 0, 0.7);     // driveTo works too (it ends any hold first)
```

**Why:** each `follow` brakes to a stop at its end; start the next path where the last one ended.
**More:** [Lesson 8: your first path](tutorials/start/08-paths-with-pedro.md#5-write-your-first-path), [follow](modules/pedro.md#follow-the-autonomous-step).

### How do I drive two paths with no stop, slow down, or hold at the end?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-chain -->
```java
// Two parts with NO stop between them (then), a slow part near the goal (maxSpeed),
// and hold the end point while it scores (holdEnd: at most 5 s).
static final PedroPath IN_ONE_GO = PedroPath.line(-56, -61, -48, -50).keepHeading(0)
        .then(PedroPath.curve(-48, -50, -44, -36, -30, -30)).turnTo(0, 45)
        .maxSpeed(0.6)
        .holdEnd();
```

**Why:** `then` saves the time of a stop, and `holdEnd` pushes back if another robot bumps yours while it scores.
**More:** [PedroPath](modules/pedro.md#pedropath-paths-in-our-field-numbers), [Hold the end point](modules/pedro.md#hold-the-end-point).

### How do I keep facing the goal along a path?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-face-point -->
```java
// Keep facing one field point the whole way (here x -60, y 60: put your goal's x and y).
static final PedroPath WATCH_GOAL = PedroPath.line(-30, -30, -56, -36).facePoint(-60, 60);
```

**Why:** the robot can slide along the path while it keeps the camera or launcher on the goal.
**More:** [PedroPath: the heading](modules/pedro.md#pedropath-paths-in-our-field-numbers).

### How do I start something partway along a path?

**Where:** your autonomous, `steps(...)`. Android Studio shows `PathAt` in red: Alt+Enter adds the import.

<!-- recipe: pedro-along -->
```java
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 // PathAt: start things ALONG the path, once each, while the robot keeps driving.
 .follow("To the goal", Paths.IN_ONE_GO,
         PathAt.part(2, "Intake on", robot.intake::in),            // where part 2 starts
         PathAt.inchesLeft(6, "Intake off", robot.intake::stop))   // 6 in before the end
 .waitSeconds("Score (the robot holds still)", 0.5);
```

Also `PathAt.fraction(0.5, ...)` (halfway along the whole path), `PathAt.inchesDone(10, ...)`,
`PathAt.near(x, y, 6, ...)`, e.g. `PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp)`, or an action from
your `Actions.java` instead of a name and a command (`PathAt.inchesLeft(12, Actions.scoreHigh(robot))`: any
action you wrote).

**Why:** the robot does not stop to start a mechanism, and Pedro has no "do this here" of its own. If the
path ends early, the report says which did not start (`STALLED, not fired: Intake off`).
**More:** [Do things along the path](modules/pedro.md#do-things-along-the-path).

## Telemetry

### How do I show a value on the Driver Station screen?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-value -->
```java
// A line of its own in buttons(...). The value is read again every loop.
show("Lift ticks", robot.lift::position);
show("Intake on", robot.intake::isRunning);                 // shows "Intake on : true"
show("Flywheel ready", () -> robot.launcher.isReady());
```

**Why:** you see what the robot "thinks" while you drive. The value must be quick to read (no waiting).
**More:** [The button menu: feedback](guides/buttons.md#4-feedback). Debug lines (loop time and more): press gamepad 1 START + BACK.

### How do I round a number on the Driver Station?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-format -->
```java
// Round a number: %.0f = no decimals, %.1f = one decimal. Locale.US keeps the dot (not a comma).
show("Target RPM", () -> String.format(Locale.US, "%.0f", robot.launcher.targetRpm()));
```

**Why:** `%.0f` means "a number with 0 decimals"; `%.2f` gives 2.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback).

### How do I show lines from my own autonomous step?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-telemetry -->
```java
// In your own step: lines on the Driver Station while it runs, and a note in the report.
s.step("Spin up", ctx -> {
    robot.launcher.spinUp();
    double giveUpAt = ctx.secondsLeft() - 3;    // a time limit: at most 3 s
    while (ctx.isActive() && !robot.launcher.isReady() && ctx.secondsLeft() > giveUpAt) {
        ctx.telemetry().line("Flywheel", "spinning up to %.0f RPM", robot.launcher.targetRpm());
        ctx.loop();          // updates every part once: the robot keeps running
    }
    ctx.note(robot.launcher.isReady() ? "at speed" : "not at speed after 3 s");
});
```

**Why:** `ctx.loop()` keeps the robot running inside your loop, and the time limit makes sure the loop ends.
**More:** [Your own steps](guides/autonomous-builder.md#6-your-own-steps), [The report](guides/autonomous-builder.md#the-report).

## Lights

A colored light on the robot tells the drivers what the robot "knows" (full, ready, a mode on) without
looking at the Driver Station. The color names need `import com.crossbotics.ftchelper.robot.LightColor;`
at the top of `Controls.java` (Alt+Enter on a red `LightColor` adds it). All of it: [Lights](modules/robot.md#lights).

### How do I add a colored light for the drivers?

**Where:** `Robot.java`, inside `define()` (and a field `public Lights lights;`).

<!-- recipe: light-add -->
```java
// A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
lights = add(Lights.gobilda("light"));
// Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
//   lights = add(Lights.blinkin("blinkin"));
```

**Why:** one line, like any part. It has no numbers and no buttons of its own: the colors come from your rules.
**More:** [Lights](modules/robot.md#lights) (the parts and their color tables).

### How do I show a color when something is true?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: light-rules -->
```java
// One rule per line. The first true rule wins: put the most important first.
robot.lights.show(LightColor.RED).blinking().when(robot.intake::isPushingOut);  // pushing out
robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);           // at speed: fire
robot.lights.show(LightColor.BLUE).when(robot.intake::isRunning);            // intake on
robot.lights.otherwise(LightColor.OFF);                                      // none true
```

**Why:** any true/false works, so a mode is `() -> robot.turret.followMode().isOn()` (blue while
whole-robot aim is on, on a robot without a turret: `() -> robot.aimMode().isOn()`) and your own test is
`() -> robot.lift.position() > 1000` (if your robot has a lift). Blinking stops at 5 times a second, and the
light never changes faster than every 0.1 s (FTC rule R202 J).
**More:** [Lights](modules/robot.md#lights), [lambdas](tutorials/start/05-actions.md).

### How do I flash the light when something happens?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: light-flash -->
```java
// A flash beats every rule for 0.5 s: each time a piece is fired (the launcher counts the
// pieces it feeds, so fire(2) flashes twice), and from a button.
robot.lights.show(LightColor.WHITE).whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
g2.a.press(() -> robot.lights.flash(LightColor.PURPLE)).says("flash the light (signal the partner)");
```

**Why:** `whenCountGoesUp` flashes each time the number goes up, so each flash is a real shot.
`whenStarts(test)` flashes when a true/false turns from false to true (`whenStarts(robot.intake::isRunning)`:
the intake just started). `.forSeconds(1)` before either makes the flash longer.

### How do I flash the light when an action is done?

**Where:** `Actions.java`.

<!-- recipe: light-action -->
```java
// The last step of an action: a green flash says "done" to the drivers.
public static Action shootTwo(Robot r) {
    return Action.named("Shoot two")
            .then(r.launcher::spinUp)
            .waitUntil("at speed", r.launcher::isReady, 3.0)
            .then(() -> r.launcher.fire(2))
            .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
            .then(() -> r.lights.flash(LightColor.GREEN));
}
```

**Why:** the light is never "claimed" by an action, so any action (or button) may flash it.

### How do I blink the light in the last 20 seconds?

**Where:** `Controls.java`, inside `buttons(...)`, before the other rules.

<!-- recipe: light-endgame -->
```java
// Yellow, blinking, in the last 20 seconds of TeleOp: time to park. Written first, so it beats
// every other rule.
robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
```

**Why:** the time counts from START (TeleOp's 2:00), like the gamepad buzzes: about a second off from
the field clock, fine for a reminder.

## Tuning and finders

### Where do the numbers go?

In `Tunables.java`, one section per part (see [the lift recipe](#how-do-i-add-a-lift-with-set-heights)).
A finder shows lines like `Tunables.Claw: P1 = 0.210, P2 = 0.770`: copy them into that section (keep the other lines) and give
P1 and P2 real names. A number you leave out uses the library's default (INIT says which), except a
lift's or arm's `min` and `max`: those must be written.

**More:** [Tunables.java](modules/robot.md#tunablesjava), [Finding your numbers](guides/finders.md).

### How do I find a servo's open and closed numbers?

**Where:** a new file in TeamCode (also in the [starter kit](starter-kit.md)). The whole file:

<!-- recipe: servo-finder -->
```java
// The Servo Finder: lists every servo in the configuration. No robot needed.
@TeleOp(name = "Cookbook Servo Finder", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class ServoFinder extends ServoFinderOpMode {
}
```

**Use it:** robot on a block. Run "Cookbook Servo Finder" (your name for it). Choose the servo (D-pad, A).
A servo cannot say where it is, so choose a start number close to where it sits now (D-pad, A). Nudge it
with the D-pad. At each end of its safe travel (just before it pushes on the frame) press X, then B. Then
move it to each position you need and press A: for example fully open, A (that is P1); closed, A (P2). Copy the line it shows (`Tunables.Claw: P1 = 0.210, P2 = 0.770`)
into `Tunables.java` and rename P1, P2 (`open`, `closed`).
**Why:** a servo pushed past its end hums, gets hot and breaks; the finder finds the safe numbers.
**More:** [Servo Finder](guides/finders.md#servo-finder-ends-and-positions), [the order](guides/finders.md#the-order).

### How do I find a lift's limits and heights?

**Where:** a new file in TeamCode (also in the [starter kit](starter-kit.md)). The whole file:

<!-- recipe: motor-finder -->
```java
// The Motor Finder: lists every motor in the configuration. No robot needed.
@TeleOp(name = "Cookbook Motor Finder", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class MotorFinder extends MotorFinderOpMode {
}
```

**Use it:** put the lift all the way down first (the count starts at 0 there). Choose the motor (D-pad,
A). Move it slowly with the right stick. Press X just above the bottom and B just below the top, then
A at each height you need. Copy the line it shows (`Tunables.Lift: min = 0, max = 2580, P1 = 1200`)
into `Tunables.java` and rename P1 (`low`).
**Why:** a wrong top limit drives the lift into its hard stop; measured limits keep it safe.
**More:** [Motor Finder](guides/finders.md#motor-finder-limits-positions-top-speed).

### How do I change a number while the robot runs?

No code needed with the five files. In the TeleOp (PRACTICE profile), after START, **hold GUIDE for
1 second**: the tuning menu opens. D-pad up/down chooses, right/left changes, **A** saves it on the
hub, **B** lists the changed values. **Then copy each good value into `Tunables.java`**: a value saved
only on the hub is lost with a new Control Hub, and your teammates cannot see it.

**Why:** you see the effect at once, with no rebuild.
**More:** [Tuning](guides/tuning.md#3-use-it), [Lesson 4: tune the lift live](tutorials/start/04-lift-and-claw.md#6-tune-the-lift-live).

### How do I tune the drive for autonomous?

**Where:** a new file in TeamCode. The whole file:

<!-- recipe: drive-tuner -->
```java
// The Drive Tuner: wheel size and strafe correction, for the drive in your Robot.java.
@TeleOp(name = "Cookbook Drive Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class DriveTuner extends DriveTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner uses its parts and numbers
    }
}
```

**Why:** it measures the real wheel size and strafe correction; copy its lines, then set `Tunables.Drive.calibrated = true`.
**More:** [Lesson 6: tune the drive first](tutorials/start/06-first-autonomous.md#1-tune-the-drive-first), [Drive Tuner](guides/finders.md#drive-tuner-wheel-size-and-strafe-correction).

### How do I set the flywheel numbers?

Run the **Flywheel Finder** (in the [starter kit](starter-kit.md)), then copy its line into
`Tunables.Launcher` (see [the launcher recipe](#how-do-i-add-a-launcher-flywheel-and-feeder)).

**Why:** the right `kP`..`kF` make the wheel get back to speed fast after each shot.
**More:** [Flywheel Finder](guides/finders.md#flywheel-finder-gains-and-recovery).

### How do I build a shot table (speed for each distance)?

**Where:** a new file in TeamCode. Run the [Aim Tuner](guides/finders.md#aim-tuner-camera-offset) first. The whole file:

<!-- recipe: shot-tuner -->
```java
// The Shot Tuner: fills Tunables.Launcher.speedByDistance from shots you mark HIT or MISS.
@TeleOp(name = "Cookbook Shot Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class ShotTuner extends ShotTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner uses its parts and numbers
    }
}
```

**Why:** at each distance you shoot 3-5 per speed and mark each HIT (A) or MISS (B); X keeps the speed
with the best hit ratio. It fills `Tunables.Launcher.speedByDistance` (one line per table to copy), so the
launcher picks the speed for each distance.
**More:** [Shot Tuner](guides/finders.md#shot-tuner-the-shot-table).

## Simulator

The simulator runs your real code on a laptop, with the field in 3D. Type these commands in a terminal:
in Android Studio, the **Terminal** tab at the bottom ([how](start-from-zero.md#6-the-terminal-tab)).
First go into the `ftc-helper-lib` folder by typing `cd ftc-helper-lib`. (It is the library's folder
inside your robot project, next to `TeamCode` in Android Studio's Project panel. Not there? Ask your
mentor, or see [Add it to a project](01-add-to-project.md).) `./gradlew` runs Gradle, the tool that builds the code; `-P...` gives it a setting.

### How do I start the simulator?

```bash
./gradlew :sim:run
```

Then open **http://localhost:8780** in a browser. Choose an OpMode, press INIT, then START.

**Why:** you can try buttons and autonomous routes before the robot is built.
**More:** [Simulator: start it](guides/simulator.md#start-it).

### How do I see my own robot in the simulator?

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

**Why:** the first command makes the simulator's robot file from your `Robot.java`; the second runs your TeamCode.
**More:** [A robot file from Robot.java](guides/simulator.md#a-robot-file-from-robotjava), [Your own robot code](guides/simulator.md#your-own-robot-code).

### How do I test my code with a script?

Write a scenario file in your robot project, for example `simulator/scenarios/intake.sim` (the library
runs this same file as a test: `sim/scenarios/cookbook-intake.sim`):

```text
init "Quick Start TeleOp"            # your TeleOp's name
start
hold g2.rt 1                         # hold RT (the intake runs while it is held)
expect within 1 motor intake power > 0.5
release g2.rt
expect within 1 motor intake power = 0 0.01
expect error none
```

Run it:

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/intake.sim -PteamCode=../TeamCode
```

`-PteamCode=../TeamCode` runs your own code (it is also the default when the library sits in your robot project).
**Why:** a script checks the same things every time, with no clicking, and catches a mistake before a meeting.
Button names in a scenario are the FTC names, not the Java ones: `g2.rt`, `g2.lb`, `g2.a` are the same,
but `g2.dpadUp` is `g2.dpad_up` and `g2.leftStickY` is `g2.left_stick_y`.

**More:** [Scenario files](guides/simulator.md#scenario-files-test-without-a-browser) (every command and button name).

## Not here?

- Every class and method: the [reference](reference.md) and the [module pages](../README.md#what-is-inside).
- Something goes wrong: [Troubleshooting](troubleshooting.md).
- Learn it step by step: the [lessons](tutorials/README.md).
- A question this page should answer? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).
