# Cookbook: "How do I...?"

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

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
- Whole robots to start from: the [templates](templates.md) (ten whole example robots) and the
  [kit robots](guides/starter-bots.md) (goBILDA, REV, AndyMark, Studica starter bots)

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
- [How do I switch the alliance during TeleOp?](#how-do-i-switch-the-alliance-during-teleop)
- [Buttons that are already built in](#buttons-that-are-already-built-in) (button map, pause, switch alliance, set pose)

**Mechanisms**
- [How do I add an intake on a motor?](#how-do-i-add-an-intake-on-a-motor)
- [How do I add an intake on CR servos?](#how-do-i-add-an-intake-on-cr-servos)
- [How do I make the intake run from the start?](#how-do-i-make-the-intake-run-from-the-start)
- [How do I add a lift with set heights?](#how-do-i-add-a-lift-with-set-heights)
- [My lift goes down when it should go up. How do I fix it?](#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)
- [How do I run one lift on two motors?](#how-do-i-run-one-lift-on-two-motors)
- [My motor isn't in the catalog](#my-motor-isnt-in-the-catalog)
- [How do I add an arm that moves in degrees?](#how-do-i-add-an-arm-that-moves-in-degrees)
- [How do I add a claw?](#how-do-i-add-a-claw)
- [How do I add a wrist?](#how-do-i-add-a-wrist)
- [How do I add any servo with named positions (a gate, a fold-out)?](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)
- [How do I get my servo's full range?](#how-do-i-get-my-servos-full-range)
- [How do I send a part to a position from a button?](#how-do-i-send-a-part-to-a-position-from-a-button)
- [My robot doesn't have this part yet. How do I keep its code?](#my-robot-doesnt-have-this-part-yet-how-do-i-keep-its-code)
- [How do I add a launcher (flywheel and feeder)?](#how-do-i-add-a-launcher-flywheel-and-feeder)
- [How do I add a turret that follows the goal?](#how-do-i-add-a-turret-that-follows-the-goal)
- [How do I fire only when aimed, at the right speed?](#how-do-i-fire-only-when-aimed-at-the-right-speed)

**Game pieces** (what is in the robot, and what fires next)
- [How do I know which piece is in the robot, and how many?](#how-do-i-know-which-piece-is-in-the-robot-and-how-many)
- [How do I stop the intake when the robot is full?](#how-do-i-stop-the-intake-when-the-robot-is-full)
- [How do I tell pieces apart by size, or with a second camera?](#how-do-i-tell-pieces-apart-by-size-or-with-a-second-camera)
- [How do I use the count (buzz, LEDs, the shot table)?](#how-do-i-use-the-count-buzz-leds-the-shot-table)
- [How do I let the next piece pick the shot table?](#how-do-i-let-the-next-piece-pick-the-shot-table)
- [How do I tune the piece sensors?](#how-do-i-tune-the-piece-sensors)
**Aiming and shooting** (the robot helps the drivers)
- [How do I make the whole robot aim at the goal (no turret)?](#how-do-i-make-the-whole-robot-aim-at-the-goal-no-turret)
- [How do I make the gamepad buzz when the aim is on target?](#how-do-i-make-the-gamepad-buzz-when-the-aim-is-on-target)
- [How do I keep aiming when the camera loses the goal?](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)
- [How do I aim with the odometry only (no camera)?](#how-do-i-aim-with-the-odometry-only-no-camera)
- [How do I make the camera work in any venue's light?](#how-do-i-make-the-camera-work-in-any-venues-light)
- [How do I shoot while the robot is driving?](#how-do-i-shoot-while-the-robot-is-driving)
- [How do I use one shot table per game piece?](#how-do-i-use-one-shot-table-per-game-piece)
- [Every shot is a bit short today. How do I trim the speed (a low battery)?](#every-shot-is-a-bit-short-today-how-do-i-trim-the-speed-a-low-battery)
- [How do I add a hood to my launcher?](#how-do-i-add-a-hood-to-my-launcher)

**Turrets and interlocks** (any turret; parts that share space)
- [How do I home my turret with a switch?](#how-do-i-home-my-turret-with-a-switch)
- [How do I set up a turret on a servo?](#how-do-i-set-up-a-turret-on-a-servo)
- [How do I set up a slip-ring turret (round and round)?](#how-do-i-set-up-a-slip-ring-turret-round-and-round)
- [How do I stop two parts from hitting each other?](#how-do-i-stop-two-parts-from-hitting-each-other)
- [How do I pick up and score with a lift, a wrist and a claw?](#how-do-i-pick-up-and-score-with-a-lift-a-wrist-and-a-claw)

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
- [How do I make the start delay shorter, or use a different number on RED?](#how-do-i-make-the-start-delay-shorter-or-use-a-different-number-on-red)

**Several autos and choosing one** (more: [the guide](guides/autos.md))
- [How do I build several autos from the same pieces?](#how-do-i-build-several-autos-from-the-same-pieces)
- [How do I make two autos that differ by one piece?](#how-do-i-make-two-autos-that-differ-by-one-piece)
- [How do I let the drivers choose the start spot and the plan in INIT?](#how-do-i-let-the-drivers-choose-the-start-spot-and-the-plan-in-init)

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
- [How do I change a color on a REV Blinkin?](#how-do-i-change-a-color-on-a-rev-blinkin)
- [How do I show a color when something is true?](#how-do-i-show-a-color-when-something-is-true)
- [How do I show the alliance on the light before the match?](#how-do-i-show-the-alliance-on-the-light-before-the-match)
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
- [How do I find my hood's stops and angles?](#how-do-i-find-my-hoods-stops-and-angles)

**Simulator** (try your code with no robot)
- [How do I start the simulator?](#how-do-i-start-the-simulator)
- [How do I see my own robot in the simulator?](#how-do-i-see-my-own-robot-in-the-simulator)
- [How do I make the simulator draw my part?](#how-do-i-make-the-simulator-draw-my-part)
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
```java title="Robot.java"
public class Robot extends RobotDefinition {
    public Intake intake;          // one field per part: "public", the kind of part, a name
    public Lift lift;
    public Claw claw;
    public Camera camera;
    public Launcher launcher;

    @Override
    protected void define() {      // the drive line, then one line per part
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(96)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
        // .part(...) = which motor or servo it is: the id on its label (optional, recommended). (1)!
        intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
        lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
        claw = add(Claw.servo("claw").part("gobilda-2000-torque"));
        camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
                .speedFromDistance(camera));
    }
}
```

1. `.part(...)` says which motor or servo it is. It is optional, but we recommend it: the library then takes better starting values from the part's numbers, and the simulator moves like the real part. The ids here are examples: write the ids of your parts, from the [parts catalog](guides/parts-catalog.md). An arm, a turret and a launcher take the motor's id in their brackets instead, and need it. Every recipe below has the same `.part(...)` comment: this is what it means.

**`Controls.java`**: the buttons, and your TeleOp. Button lines go inside `buttons(...)`:

<!-- recipe: file-controls -->
```java title="Controls.java"
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
```java title="Actions.java"
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

**`Tunables.java`**: one section per part, inside `public class Tunables { ... }`. When a recipe adds a
new part, it shows the part's whole section (`public static class Lift { ... }`): put it inside
`Tunables`, next to the other sections. If your file **already has** a section with that name, do not add
a second one (two sections with one name do not compile): copy only the lines inside it into yours, and
change the lines you already have. A recipe that adds to a section you already have says so ("In your
`Tunables.Launcher` section") and shows only the lines.

**How a recipe's section reads:** one number per line, with a short comment.
- `MEASURE`: a number only your robot can give (a lift's top, a servo's positions). INIT stops and
  names the tool that finds it. Write the number in its place.
- `DEFAULT`: the library's starting value, shown in brackets in the comment. Change it
  only when you see the problem it fixes ([Find a setting by what you want](guides/tunables.md#find-a-setting-by-what-you-want)).
- A real number: the point of the recipe (a slower robot, a spot on the field).
- These words need one line at the top of `Tunables.java`: `import static com.crossbotics.ftchelper.robot.Starting.*;`

**Names:** the name in quotes in `Robot.java` (`Lift.slide("lift")`) is the device's name in the
robot configuration on the Driver Station, exactly. The section's name is that name in CamelCase:
`"lift"` reads `Lift`, `"intake_deploy"` reads `IntakeDeploy`.

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
| INIT, START | the Driver Station's two buttons: INIT gets ready (the library moves nothing then), START runs |
| PRACTICE, COMPETITION | the two profiles (ways to run): PRACTICE is slower and has the tuning menu |
| ticks (counts) | how far a motor has turned, from its encoder (a sensor in the motor) |
| `kP`, `kI`, `kD`, `kF` | the PID numbers: how hard a lift pushes toward its target. Start with the defaults |
| catalog id (`"gobilda-5203-312"`) | a motor's or servo's name in the [parts catalog](guides/parts-catalog.md): 5203 is goBILDA's motor series, 312 its RPM (on the motor's label) |
| `.part("gobilda-5203-435")` | which motor or servo a part uses. Optional, recommended: the library then takes better starting values from the part's numbers, and the simulator moves like the real part. An arm, a turret and a launcher take the id in their brackets instead, and need it |
| tuning menu | change numbers while the robot runs (not the INIT choice menu of an autonomous) |
| terminal | a window where you type commands. In Android Studio: the **Terminal** tab at the bottom ([how](start-from-zero.md#6-the-terminal-tab)) |

**One rule for every recipe:** a job never waits. No `sleep(...)` and no `while` loop in a button job
or an action: they freeze the whole robot. To wait, see
[how do I wait half a second (like sleep)?](#how-do-i-wait-half-a-second-like-sleep-in-blocks).

## Driving

### How do I set up a mecanum drive?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: drive-mecanum -->
```java title="Robot.java"
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
```java title="Robot.java"
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
```java title="Robot.java"
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
```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // Which way the hub's LOGO faces, then which way its USB PORTS face, on the robot.
        .imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP));
```

**Why:** the IMU (the hub's turn sensor) must know which way is up, or turns and field-centric driving go wrong.
**More:** [Lesson 2: the IMU](tutorials/start/02-drive.md#4-say-how-the-control-hub-is-mounted-the-imu).

### How do I make the robot slower (or faster)?

**Where:** `Tunables.java`, section `Drive`.

<!-- recipe: tunables-drive -->
```java title="Tunables.java"
public static class Drive {                                     // a fixed name: always Drive
    public static double maxPower = 0.6;                        // top wheel power: slow for first drives (1.0)
    public static double practiceMaxPower = 0.4;                // top wheel power in PRACTICE (0.6)
    public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
    public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
    public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
    public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
    public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
    public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
}
```

**Why:** `practiceMaxPower` is used in the PRACTICE profile and `maxPower` in COMPETITION, so practice
is safe and matches are fast.
**More:** [Lesson 2: the numbers](tutorials/start/02-drive.md#5-the-numbers-tunablesdrive), [Profiles](guides/profiles.md).

### How do I let one driver do everything?

**Where:** `Controls.java`, at the top of the class.

<!-- recipe: one-driver -->
```java title="Controls.java"
// true: every button is on gamepad 1 (the operator's buttons move there).
public static boolean oneDriver = true;
```

**Why:** with one driver, the operator's buttons move to gamepad 1, and the stick clicks switch slow mode and field-centric.
**More:** [Lesson 3: one driver or two](tutorials/start/03-buttons.md#3-one-driver-or-two).

### How do I put slow mode on a trigger?

**Where:** `Controls.java`, inside `buttons(...)`. For one driver (with two drivers, LT on gamepad 1 is already slow).

<!-- recipe: slow-trigger -->
```java title="Controls.java"
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
```java title="Controls.java"
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
```java title="Controls.java"
// Runs every loop while held; the second job runs once when it is let go.
g1.a.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
```

**Why:** without the second job, the intake would keep running after you let go.
**More:** [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I switch something on and off with one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-toggle -->
```java title="Controls.java"
// Each press switches the intake on or off. rumble(): 2 buzzes = on, 1 buzz = off.
g1.x.toggle(robot.intake).rumble().says("intake on/off");
```

**Why:** `toggle` remembers on or off for you, and the buzz tells the driver which one without looking.
**More:** [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I put a tap, a hold and a double-tap on one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-gestures -->
```java title="Controls.java"
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
```java title="Controls.java"
// Runs once, after the button is held for 1 second: a safe "are you sure?".
g2.dpadLeft.heldFor(1.0, robot.launcher::stop).says("hold 1 s: flywheel off");
```

**Why:** a quick bump of the button does nothing, so a big job cannot start by accident.
**More:** [The button menu: gestures](guides/buttons.md#several-gestures-on-one-button).

### How do I use two buttons together (like a shift key)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-pair -->
```java title="Controls.java"
// Hold RB (the "shift key"), then press D-pad up: for a job that must not run by accident.
g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high")).says("lift to high");
```

**Why:** a pair is a safe place for rare, big jobs (a climb); D-pad up alone still does its own job.
**More:** [The button menu: two buttons together](guides/buttons.md#two-buttons-together).

### How do I make the gamepad buzz?

**Where:** `Controls.java`, inside `buttons(...)`.

Add `.rumble()` after a job (see [the toggle recipe](#how-do-i-switch-something-on-and-off-with-one-button)),
or buzz when the robot gets ready:

<!-- recipe: rumble-when -->
```java title="Controls.java"
// A buzz on gamepad 2 each time the flywheel gets to its speed.
g2.rumbleWhen(robot.launcher::isReady);
```

**Why:** the drivers watch the robot, not the Driver Station, so a buzz says "ready" without looking away.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback).

### How do I control a part with a stick?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: stick-use -->
```java title="Controls.java"
// A stick is a number from -1 to 1 (up and right are +). Its value goes to the job every loop.
g2.rightStickY.use(robot.lift::manual).says("lift by hand");
```

**Why:** a stick gives a number every loop, so the part moves as far as you push it (and holds when you let go).
**More:** [The button menu: sticks](guides/buttons.md#sticks).

### How do I move a part's button to another button?

**Where:** `Controls.java`, inside `buttons(...)`.

Each part brings standard buttons (the intake: operator RT and LT). To move them:

<!-- recipe: move-a-button -->
```java title="Controls.java"
g2.rt.free();   // free(): no job on it, even the one the intake suggests
g2.lt.free();
g2.lb.hold(robot.intake::in, robot.intake::stop).says("intake in (hold)");
g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
```

**Why:** `free()` takes the standard job off a button; then you give the new buttons their jobs.
**More:** [Lesson 3: the buttons the parts bring](tutorials/start/03-buttons.md#2-the-buttons-the-parts-bring).

### How do I turn off all the standard buttons?

**Where:** `Controls.java`, inside the class, as a method of its own: below the `}` that ends
`buttons(...)`, above the class's last `}`.

<!-- recipe: no-standard-buttons -->
```java title="Controls.java"
// false: the parts bring no buttons of their own. Only the jobs in buttons(...) are used.
@Override
protected boolean useStandardButtons() {
    return false;
}
```

**Why:** some teams want every button written in one place, with no surprises.
**More:** [ControlMap](modules/robot.md#controlmap).

### How do I move the library's own buttons (slow, field-centric, pause)?

**Where:** `Controls.java`, inside the class, as a method of its own: below the `}` that ends
`buttons(...)`, above the class's last `}`. At the top of the file, with the other imports:
`import com.crossbotics.ftchelper.teleop.LibraryButtons;`

<!-- recipe: move-library-buttons -->
```java title="Controls.java"
// The library's own buttons (driving, pause, menus...): move them here; null = no button.
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    lib.fieldCentric(g1.x);                  // field-centric on/off: X instead of Y
    lib.slow(g1.rt);                         // slow on the right trigger: LT is free now
    lib.switchAlliance(g1.start, g1.back);   // optional, off by default: hold START, then BACK, 1.5 s
}
```

**Why:** the old button is then free for your own jobs in `buttons(...)`. Two library jobs on one
button stop INIT with the button's name. Only one `libraryButtons(...)` method per file: put every
`lib.` line in it.
**More:** [The button menu: moving the library's buttons](guides/buttons.md#moving-the-librarys-buttons).

### How do I change the double-tap and hold times?

**Where:** `Tunables.java` (optional: leave the section out for the defaults).

<!-- recipe: tunables-buttons -->
```java title="Tunables.java"
/** Optional: slower gestures, e.g. for new drivers. Leave the section out for the defaults (in brackets). */
public static class Buttons {                                   // a fixed name: always Buttons
    public static double tapGapSec = 0.45;                      // most time between the taps of a double-tap (0.35)
    public static double holdSec = 0.4;                         // a press this long is a hold, not a tap (0.3)
}
```

**Why:** some drivers tap slower; these times are in the tuning menu too. A value you tune there,
copy back into `Tunables.java` (the file is the record).
**More:** [The button menu](guides/buttons.md#several-gestures-on-one-button).

### How do I switch the alliance during TeleOp?

The alliance is chosen in INIT (gamepad 1: X = BLUE, B = RED). Picked the wrong one, and the match has
started? Turn on the switch (it is off by default, so nobody switches by accident).

**Where:** `Controls.java`, inside the class, as a method of its own (below the `}` that ends
`buttons(...)`). Already have a `libraryButtons(...)` method? Add only the `lib.switchAlliance` line to it.

<!-- recipe: switch-alliance -->
```java title="Controls.java"
// Controls.java, inside the class, next to buttons(...) (import
// com.crossbotics.ftchelper.teleop.LibraryButtons at the top). Off by default.
@Override
protected void libraryButtons(LibraryButtons lib, Pad g1, Pad g2) {
    // Gamepad 1: hold START, then press and hold BACK too, for 1.5 s: BLUE <-> RED.
    lib.switchAlliance(g1.start, g1.back);
}
```

**Why:** the alliance picks the goal the camera and the turret aim at, and the light's color. A wrong
pick aims at the other alliance's goal all match. START first, then BACK: BACK alone is the pause, so
pressing BACK first just pauses. Holding 1.5 s means a bump does nothing.
**More:** [The button menu: moving the library's buttons](guides/buttons.md#moving-the-librarys-buttons).

### Buttons that are already built in

- **The button map:** INIT shows every button and what it does. During the match, tap **GUIDE**.
- **The tuning menu:** hold **GUIDE** for 1 second (PRACTICE profile). A tap shows the map, a hold opens the menu.
- **Two jobs on one button** stop INIT with the button's name, so a mistake is found before the match.
- **Pause:** press gamepad 1 **BACK** (the small button left of the centre): everything stops at once.
  A tap again goes on.
- **Wrong alliance?** Choose it in INIT (gamepad 1: X = BLUE, B = RED). Switching during TeleOp is
  optional: [turn it on](#how-do-i-switch-the-alliance-during-teleop), then on gamepad 1 hold the small
  **START** button, then press and hold **BACK** too, 1.5 s.
- **Set pose here** (robots with odometry): hold gamepad 1 **START** 1 s on the reset spot, sticks let go, in INIT or in the match ([below](#how-do-i-set-the-pose-when-autonomous-did-not-run)).

**More:** [Lesson 3: the button map](tutorials/start/03-buttons.md#6-the-button-map-init-and-guide).

## Mechanisms

Each part is one line in `Robot.java`'s `define()`, plus a field for it at the top of the class (for
example `public Lift lift;`) and a section with its numbers in `Tunables.java`. The section's name is
the configuration name in CamelCase (each word starts with a capital letter, no spaces or `_`): `intake_deploy` reads `IntakeDeploy`. Its standard buttons come with it.

### How do I add an intake on a motor?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-motor -->
```java title="Robot.java"
// At the top of the class: public Intake intake;   Here in define():
// "intake" = the motor's name in the robot configuration. Its numbers: Tunables.Intake.
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
```

<!-- recipe: tunables-intake -->
```java title="Tunables.java"
public static class Intake {                                    // "intake" in the configuration
    public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
    public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
}
```

**Why:** the intake brings its buttons (operator RT in, LT out) and its numbers (`Tunables.Intake`).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Intake](modules/robot.md#intake).

### How do I add an intake on CR servos?

A CR (continuous rotation) servo spins all the way round, like a small motor, instead of going to a position.

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-cr-servos -->
```java title="Robot.java"
// Two continuous-rotation (CR) servos. The one that spins the wrong way is reversed. (1)!
// "left_intake", "right_intake" = the servos' names in the robot configuration. Numbers: Tunables.Intake.
// .part(...): which servo it is (optional, recommended).
intake = add(Intake.crServos("left_intake", "right_intake").part("gobilda-2000-speed")
        .reversed("right_intake"));
```

1. Also add the field at the top of the class: `public Intake intake;`. The two servos face each other, so one must turn the other way: `.reversed(...)` names that one.

**Why:** two servos that face each other must turn opposite ways to pull a piece in. The numbers are in `Tunables.Intake` too (`inPower`, `outPower`).
**More:** [Intake](modules/robot.md#intake).

### How do I make the intake run from the start?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-on-at-start -->
```java title="Robot.java"
// Runs in from START until stopped. Its button (operator RT) then switches it on/off.
// .part(...): which motor it is (optional, recommended).
add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());
```

**Why:** many robots collect all match, so the driver should not have to switch it on.
**More:** [Intake](modules/robot.md#intake).

### How do I add a lift with set heights?

**Where:** `Robot.java` (the part) and `Tunables.java` (its numbers).

<!-- recipe: lift -->
```java title="Robot.java"
// At the top of the class: public Lift lift;   Here in define():
// "lift", "lift_bottom" = the motor's and the bottom switch's names in the robot configuration. (1)!
// .part(...): which motor it is (optional, recommended).
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

1. The bottom switch sets the lift's 0 each time it is pressed. No switch? Delete `.bottomSwitch(...)`.

<!-- recipe: tunables-lift -->
```java title="Tunables.java"
public static class Lift {                                      // "lift" in the configuration
    public static double min = MEASURE;                         // lowest safe point, counts (Motor Finder)
    public static double max = MEASURE;                         // highest safe point, counts (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (from the motor)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (from the motor)
    public static double kF = DEFAULT;                          // hold up against gravity (0.05)
    public static double down = MEASURE;                        // a named height, counts: any name you like
    public static double low = MEASURE;                         // a named height (Motor Finder)
    public static double high = MEASURE;                        // a named height (Motor Finder)
}
```

**Why:** the lift never goes past `min` and `max`, so it cannot break itself; INIT stops if they are missing.
`MEASURE` stops INIT until you write the real number: find yours with the [Motor Finder](#how-do-i-find-a-lifts-limits-and-heights).
`"lift"` must be the motor's name in your configuration. To send it to a height: [from a button](#how-do-i-send-a-part-to-a-position-from-a-button).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#2-their-numbers-in-tunablesjava), [Lift](modules/robot.md#lift),
[why the bottom switch matters](tutorials/start/04-lift-and-claw.md#4-why-the-bottom-switch-matters-homing).

### My lift goes down when it should go up. How do I fix it?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: lift-reversed -->
```java title="Robot.java"
// The lift goes DOWN on positive power (the Motor Finder says "add .reversed()"): flip it.
// .part(...): which motor it is (optional, recommended).
add(Lift.slide("lift").part("gobilda-5203-435").reversed());
```

**Why:** the lift's limits and heights count up as it goes up, so positive power must move it up.
**More:** [Motor Finder](guides/finders.md#motor-finder-limits-positions-top-speed), [Troubleshooting](troubleshooting.md#robot-behavior).

### My motor isn't in the catalog

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: own-parts -->
```java title="Robot.java"
// A motor that is not in the catalog: its encoder counts per output turn, then its free speed (RPM). (1)!
launcher = add(Launcher.flywheel("launcher", Motors.custom("my-flywheel-motor", 28, 5800)));
// A servo: how far it turns (degrees), then its speed (seconds per 60 degrees, at 4.8 V). (2)!
claw = add(Claw.servo("claw").part(Parts.customServo("my-claw-servo", 270, 0.16)));
// Motors.unknown(...): "I know neither number" (a single unknown number: Double.NaN). (3)!
lift = add(Lift.slide("lift").part(Motors.unknown("old-lift-motor")));
```

1. Find the two numbers on the seller's page. goBILDA: "Encoder Resolution" (the number "at the Output Shaft") and "No-Load Speed". REV: "Encoder Counts per Revolution" (at the output) and "Free Speed". The name (`"my-flywheel-motor"`) then works like a catalog id, here and in `.part(...)`.
2. If the seller lists 4.8 V and 6 V, use the 4.8 V speed: the hub gives a servo about 5 V.
3. `Double.NaN` means "I don't know this one". The part then uses general defaults, and INIT says so. A launcher or an arm must have at least the counts per turn: `Motors.custom("...", 28, Double.NaN)`.

**Why:** the library works out a part's defaults (a launcher's gains and speed, a lift's kP, a servo's
move time) from its motor's or servo's numbers. Describe yours with the seller's numbers and it works like
a catalog part: INIT says `defaults from my-flywheel-motor (custom)`, and `:sim:fromRobot` writes its
numbers into the simulator's robot file. A number you do not know: write `Double.NaN` ("I don't know
this one"), or the whole part as `Motors.unknown` / `Parts.unknownServo`. The part then uses general
defaults, and INIT says so. Do not use a catalog motor that is "close": its
numbers would be quietly wrong.
**More:** [Parts not in the catalog](modules/robot.md#parts-not-in-the-catalog), [Parts catalog](guides/parts-catalog.md).

### How do I run one lift on two motors?

**Where:** `Robot.java`, inside `define()`, and `Tunables.java`.

<!-- recipe: lift-two-motors -->
```java title="Robot.java"
// "lift_left", "lift_right" = the motors' names in the robot configuration. Numbers: Tunables.LiftLeft. (1)!
// Plug the encoder cable into the FIRST motor: only its encoder is read. (2)!
// The motors face each other, so the second turns the other way: alsoMotorReversed. (3)!
lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"));
```

1. The numbers are in `Tunables.LiftLeft` (the FIRST motor's name in CamelCase), not in `Tunables.Lift`.
2. Both motors always get the same power (also when the lift stops or pauses), so one encoder is enough.
3. Both motors turn the same way for "up"? Use `.alsoMotor("lift_right")` instead.

<!-- recipe: tunables-lift-two-motors -->
```java title="Tunables.java"
// A lift on two motors, Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"):
// its section is the FIRST motor's name in CamelCase.
public static class LiftLeft {                                  // "lift_left" in the configuration
    public static double min = MEASURE;                         // lowest safe point, counts (first motor)
    public static double max = MEASURE;                         // highest safe point, counts (first motor)
    public static double kP = DEFAULT;                          // push toward the target (from the motor)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (from the motor)
    public static double kF = 0.06;                             // a heavier slide needs more hold (0.05)
    public static double down = MEASURE;                        // a named height (Motor Finder)
    public static double low = MEASURE;                         // a named height (Motor Finder)
    public static double high = MEASURE;                        // a named height (Motor Finder)
}
```

**Why:** two motors on one slide must never fight. The library sends both the very same power in the same
loop, also when it stops, pauses (BACK) or an interlock rule holds the lift; your own code copying the
power every loop would miss pause and STOP. **Check the directions before the chain goes on**: with the
chain or belt off, each motor must turn the way that lifts; if one is wrong, swap `alsoMotorReversed`
and `alsoMotor`. Two motors pushing against each other get hot and can break a gearbox. An arm works the
same: `Arm.motor("arm_left", "gobilda-5203-117").alsoMotorReversed("arm_right")`. The simulator ties both
motors to one slide (`:sim:fromRobot` writes the second motor's line).
**More:** [Two motors on one lift or arm](modules/robot.md#two-motors-on-one-lift-or-arm), [Tunables names](guides/tunables.md#lift-and-arm).

### How do I add an arm that moves in degrees?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: arm -->
```java title="Robot.java"
// "arm" = the motor's name in the robot configuration. Its numbers: Tunables.Arm.
// "gobilda-5203-117" = the motor's catalog id (needed: it works in degrees). (1)!
// gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

1. Like a turret's or a launcher's, the arm's motor id is not optional: the arm needs the motor's counts per turn to work in degrees.

<!-- recipe: tunables-arm -->
```java title="Tunables.java"
public static class Arm {                                       // "arm" in the configuration
    public static double min = MEASURE;                         // lowest safe angle, degrees, 0 = at INIT
    public static double max = MEASURE;                         // highest safe angle, degrees (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (0.02)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double kF = 0.1;                              // hold up against gravity (0)
    public static double stowed = MEASURE;                      // a named angle, degrees: any name you like
    public static double pickup = MEASURE;                      // a named angle (Motor Finder)
    public static double score = MEASURE;                       // a named angle (Motor Finder)
}
```

**Why:** degrees are easier to think about than encoder counts; INIT the program with the arm at its 0.
**More:** [Arm](modules/robot.md#arm).

### How do I add a claw?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: claw -->
```java title="Robot.java"
// At the top of the class: public Claw claw;   Here in define():
// "claw" = the servo's name in the robot configuration. Its numbers: Tunables.Claw.
// .part(...): which servo it is (optional, recommended).
claw = add(Claw.servo("claw").part("gobilda-2000-torque"));
```

<!-- recipe: tunables-claw -->
```java title="Tunables.java"
public static class Claw {                                      // "claw" in the configuration
    // The Servo Finder shows "Tunables.Claw: P1 = 0.210, P2 = 0.770": write P1 as open, P2 as closed.
    public static double open = MEASURE;                        // servo position, open (Servo Finder)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** the claw brings its button (operator B opens and closes), and the Servo Finder gives the two numbers.
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Claw](modules/robot.md#claw).

### How do I add a wrist?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: wrist -->
```java title="Robot.java"
// At the top of the class: public Wrist wrist;   Here in define():
// "wrist" = the servo's name in the robot configuration. Its numbers: Tunables.Wrist.
// .part(...): which servo it is (optional, recommended).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed"));
```

<!-- recipe: tunables-wrist -->
```java title="Tunables.java"
public static class Wrist {                                     // "wrist" in the configuration
    public static double down = MEASURE;                        // servo position: any name you like
    public static double level = MEASURE;                       // servo position (Servo Finder)
    public static double up = MEASURE;                          // servo position (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** the wrist brings its buttons (operator D-pad left and right step through the positions).
**More:** [Wrist](modules/robot.md#wrist).

### How do I add any servo with named positions (a gate, a fold-out)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: positioner -->
```java title="Robot.java"
// Any servo with named positions: a gate, a kicker, a fold-out intake.
// "intake_deploy" = the servo's name in the robot configuration. Numbers: Tunables.IntakeDeploy.
// .part(...): which servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque"));
// Right after START (autonomous and TeleOp): fold the intake out.
atStart(() -> Action.named("Deploy").then(() -> intakeDeploy.goTo("deployed")));
```

<!-- recipe: tunables-positioner -->
```java title="Tunables.java"
public static class IntakeDeploy {                              // "intake_deploy" in the configuration
    public static double stowed = MEASURE;                      // servo position, folded (Servo Finder)
    public static double deployed = MEASURE;                    // servo position, folded out (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** a `Positioner` has no standard buttons, so you choose when it moves (here: at START).
**More:** [Positioner](modules/robot.md#positioner), [an action at START](#how-do-i-run-an-action-right-after-start).

### How do I get my servo's full range?

**Where:** `Robot.java`, inside `define()`: add `.fullRange()` to the servo part.

<!-- recipe: servo-full-range -->
```java title="Robot.java"
// fullRange(): positions 0 to 1 = the servo's whole turn (300 degrees on a goBILDA 2000). (1)!
// Turn it on FIRST, then find the positions (Servo Finder: press Y while choosing the servo).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());
```

1. The hub normally sends 600-2400 us (microseconds) for positions 0 to 1: a goBILDA 2000 servo then turns only 270 of its 300 degrees. `fullRange()` sends the whole 500-2500 us.

**Why:** the FTC SDK's normal servo signal (600 to 2400 microseconds) is narrower than what most servos
take (500 to 2500), so they turn less than the seller says: 270 degrees for a goBILDA 2000, 243 for a REV
Smart Robot Servo. Only add it when you need the extra turn. It works the same on a Claw, a Positioner,
`Hood.servo`, `Turret.servo` and `Indexer.servo`. Positions you measured before point somewhere else after
the change: measure them again. Without `.fullRange()` nothing changes.
**More:** [Full range](modules/hardware.md#full-range) (a table per servo), [Claw, Wrist, Positioner](modules/robot.md#claw).

### How do I send a part to a position from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: mechanism-commands -->
```java title="Controls.java"
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

### My robot doesn't have this part yet. How do I keep its code?

Three places: the part's line in `Robot.java`, the action in `Actions.java`, its button in `Controls.java`.

**`Robot.java`**, inside `define()`: add `.optional()` to the part's `add` line (here a deploy servo that
folds the intake out). The `atStart` line runs the action below at START.

<!-- recipe: optional-part -->
```java title="Robot.java"
// optional(): while "intake_deploy" is not in the robot configuration, the part is off (INIT goes on). (1)!
// .part(...): which motor or servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque").optional());
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
// At START: fold the intake out, then run it (the action is in Actions.java, below). (2)!
atStart(() -> Actions.deployIntake(this));
```

1. The deploy servo is not on the robot yet. INIT goes on with a note (see **Why**, below).
2. With no deploy servo, the action skips the servo (and the wait for it) and only starts the intake.

**`Actions.java`**, inside `public class Actions { ... }`: the action. In your file the brackets say
`(Robot r)`.

<!-- recipe: optional-part-action -->
```java title="Actions.java"
/** Folds the intake out with the deploy servo, then runs the intake. */
public static Action deployIntake(NotOnTheRobotYet r) {   // in your Actions.java: (Robot r)
    return Action.named("Deploy intake")
            .then(() -> r.intakeDeploy.goTo("deployed"))
            .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
            .then(r.intake::in, () -> r.intakeDeploy.goTo("stowed"));
}
```

**`Controls.java`**, inside `buttons(...)`: the button, with `.needs(...)` at the end.

<!-- recipe: optional-part-button -->
```java title="Controls.java"
// needs(...): while the deploy servo is not on the robot, X does nothing and the
// button map at INIT says "off: IntakeDeploy not on the robot".
g2.x.press(Actions.deployIntake(robot)).says("deploy intake").needs(robot.intakeDeploy);
```

**Why:** robots are built in steps. Without `optional()`, a part whose device is not in the
configuration stops INIT (`No Servo named "intake_deploy" ...`). With it, INIT goes on with a note:
`"intake_deploy" not in the configuration: IntakeDeploy is off (optional part)`. In the COMPETITION
profile the note is a warning at the top of the screen (`!! COMPETITION: ... IntakeDeploy is OFF ...`),
so nobody plays a match without a part by mistake. Everything that uses the part skips it: its commands,
its buttons (the button map says `G2 X: deploy intake - off: IntakeDeploy not on the robot`), the action
steps (an autonomous keeps going and its report says `skipped IntakeDeploy (not on the robot)`). The day
the servo is wired and in the configuration, it just works, with no code to change. No
`if (HAS_DEPLOY_SERVO)` switches. To try it in the simulator without the part: `missing = intake_deploy`
in the robot file.
**More:** [Parts not on the robot yet](modules/robot.md#parts-not-on-the-robot-yet).

### How do I add a launcher (flywheel and feeder)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher -->
```java title="Robot.java"
// A flywheel (in RPM) and the indexer that feeds it one piece at a time.
// "launcher", "indexer" = names in the robot configuration. Numbers: Tunables.Launcher, .Indexer.
// The motor's catalog id is in the brackets (needed). The indexer's .part(...) is optional, recommended. (1)!
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
```

1. The launcher needs its motor's id for its speed control. The indexer's `.part(...)` is optional ([what it does](#words-you-will-meet)).

<!-- recipe: tunables-launcher -->
```java title="Tunables.java"
public static class Launcher {                                  // "launcher" in the configuration
    public static double speedRpm = DEFAULT;                    // shot speed without the table (60% of top)
    public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
    public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
    public static double idleRpm = 1500;                        // spins between shots: quicker first shot (0)
    public static double shotIntervalSec = DEFAULT;             // least time between two pieces (0.25)
    // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
    // Then write the Finder's numbers in their place.
    public static double kP = DEFAULT;                          // push per speed error (kF / 10)
    public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
    public static double kD = DEFAULT;                          // brake changes (0)
    public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    // {inches to the goal, RPM}, blended between rows. Example rows: write the Shot Tuner's instead.
    public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
}
```

<!-- recipe: tunables-indexer -->
```java title="Tunables.java"
public static class Indexer {                                   // "indexer" in the configuration
    public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
    public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
    public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
    public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
}
```

**Why:** the launcher only feeds a piece when the wheel is at speed, so every shot flies the same way.
**More:** [Lesson 9](tutorials/start/09-shooter-and-camera.md#1-a-flywheel-and-its-feeder), [Launcher](modules/robot.md#launcher).

### How do I add a turret that follows the goal?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: turret-follows-camera -->
```java title="Robot.java"
// The camera knows the goal of each alliance (BLUE first, then RED).
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
// "turret" = the motor's name in the robot configuration. 5 motor turns per turret turn.
// follows(camera): it follows the goal from START, but never past Tunables.Turret min and max.
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
```

<!-- recipe: tunables-turret -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double startAngle = DEFAULT;                  // where you set it by hand before INIT (0)
    public static double kP = DEFAULT;                          // push per degree off (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = DEFAULT;                    // the most power (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
}
```

**Why:** the turret aims for the driver, and its limits keep the wires from twisting.
**More:** [Lesson 9: the turret](tutorials/start/09-shooter-and-camera.md#6-a-turret-that-follows-it), [Turret](modules/robot.md#turret), [Camera](modules/robot.md#camera).

### How do I fire only when aimed, at the right speed?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher-aimed -->
```java title="Robot.java"
// The speed comes from the distance to the goal (Tunables.Launcher.speedByDistance),
// and a piece is only fed when the turret points at the goal.
// .part(...): which servo it is (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
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
```java title="Robot.java"
// At the top of the class: public Pieces pieces;   Here in define(), ABOVE the intake line. (1)!
// The kinds of piece, a sensor where they come in, one at the "ready to fire" slot (optional). (2)!
pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
        .comingIn(PieceSensor.color("intake_color"))    // a REV Color Sensor V3
        .readyToFire(PieceSensor.color("ready_color"))  // another one
        .max(4)                                         // BIOBUZZ G407: no more than 4
        .startsWith(4, "POLLEN"));                      // the 4 POLLEN preload
```

1. Above the intake line, the count is up to date in each loop before the intake asks "full?".
2. BIOBUZZ: yellow POLLEN, red and blue NECTAR. Then the most pieces the rules allow (`max`) and the preload (`startsWith`).

**Why:** each piece the intake sensor sees counts one in; each feed of the indexer counts one out, the
oldest first, once the piece has gone (a jammed feed counts nothing). Until a kind is tuned with the
Piece Tuner it reads `"UNKNOWN"`. `robot.pieces.count()`, `robot.pieces.next()` ("POLLEN", "UNKNOWN" or "NONE") and
`robot.pieces.isFull()` work anywhere in your code.
**More:** [Pieces and PieceSensor](modules/robot.md#pieces-and-piecesensor).

### How do I stop the intake when the robot is full?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: intake-stops-when-full -->
```java title="Robot.java"
// ADD .stopsWhen(...) to your intake line (no second intake line); the Pieces line goes above it. (1)!
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150").stopsWhen(() -> pieces.isFull()));
```

1. The intake then does not pull in a 5th piece. `() ->` means: the test is asked in every loop. Pushing out still works.

<!-- recipe: launcher-needs-a-piece -->
```java title="Robot.java"
// Each feed counts one piece out. requireReady: no feed with nothing to fire.
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
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
```java title="Robot.java"
// A REV 2m Distance Sensor across the intake tells the sizes apart (not the colours). (1)!
// A second webcam on the ready slot: colours. (2)!
pieces = add(Pieces.kinds("POLLEN", "NECTAR")
        .comingIn(PieceSensor.distance("intake_distance"))
        .readyToFire(PieceSensor.camera("Webcam 2"))
        .max(4));
```

1. A NECTAR (3.6 in) comes closer to the sensor than a POLLEN (2.8 in). Size only: it cannot tell a red NECTAR from a blue one.
2. The webcam reads the main colour in a box of its picture.

**Why:** a distance sensor reads 20 mm closer with a NECTAR than with a POLLEN, but it sees no colour,
and rule G408 forbids controlling the other alliance's NECTAR. A webcam sees colours from farther
away, but not how far the piece is.
**More:** [the sensors compared](modules/robot.md#pieces-and-piecesensor).

### How do I use the count (buzz, LEDs, the shot table)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: pieces-use -->
```java title="Controls.java"
// A buzz on gamepad 2 when the robot holds the most it may.
g2.rumbleWhen(robot.pieces::isFull);
// Two buzzes when a NECTAR (red or blue) is next to fire. For one kind only:
// robot.pieces.whenNextIs("RED_NECTAR").
g2.rumbleWhen(() -> robot.pieces.next().endsWith("NECTAR"), 2);
// A light the drivers see: green = full, orange = empty (needs a Lights line in Robot.java, and
// import com.crossbotics.ftchelper.robot.LightColor; at the top of this file).
robot.lights.show(LightColor.GREEN).when(robot.pieces.whenFull());
robot.lights.show(LightColor.ORANGE).when(robot.pieces.whenEmpty());
// Pieces shows "3/4, next POLLEN" by itself; this adds what the intake sees now.
show("At the intake", robot.pieces::atIntake);
// The count went wrong? Already on the buttons: hold G2's left stick button 1 s = count 0,
// and hold the intake's RT 1 s to pull in although the count says full.
```

**Why:** `whenFull()`, `whenEmpty()` and `whenNextIs("POLLEN")` (one kind) are conditions any part can
watch: a buzz, a light (the [Lights](#lights) recipes), an action that waits. For "either NECTAR", the
recipe asks `next()` (the next kind's name) whether it ends with `NECTAR`. Pieces brings a button too:
hold G2's left stick button 1 s to set the count to 0 when it went wrong (pieces taken out by hand).
**More:** [hooks for other parts](modules/robot.md#pieces-and-piecesensor).

### How do I let the next piece pick the shot table?

**Where:** `Robot.java`, inside `define()`, on your launcher line (the Pieces line must be above it), and
two tables in your `Tunables.Launcher` section ([one table per piece](#how-do-i-use-one-shot-table-per-game-piece)).

<!-- recipe: pieces-shot-tables -->
```java title="Robot.java"
// In define(), BELOW the Pieces line: your launcher line with three more lines. The tables
// are Tunables.Launcher.speedByDistancePollen and speedByDistanceNectar.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera)
        .startTable("pollen")                                 // before a piece is known
        .tableFrom(pieces.nextKind())                         // the next piece picks the table
        .tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR"));    // both NECTAR colours use "nectar"
```

**Why:** POLLEN and NECTAR fly differently. With these lines the drivers never switch tables by hand:
each time the next piece changes, its table is used. A kind with the same name as a table needs no line
(`POLLEN` uses `pollen`); `tableFor` puts both NECTAR colours on the `nectar` table. `NONE` and
`UNKNOWN` (a kind not tuned yet) never change the table, and `startTable` is the table before the first
piece is known. A button (D-pad right) can still pick another table until the next piece changes.
**More:** [Launcher: shot tables](modules/robot.md#launcher), [Lesson 9: the next piece picks the table](tutorials/start/09-shooter-and-camera.md#12-extra-know-which-piece-fires-next).

### How do I tune the piece sensors?

**Where:** a new file next to `Robot.java`, `PieceTuner.java`. The whole file: its first line is
`package org.firstinspires.ftc.teamcode;` (Android Studio writes it when you make the file), then:

<!-- recipe: piece-tuner -->
```java title="PieceTuner.java"
import com.crossbotics.ftchelper.finders.PieceTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

// The Piece Tuner: each sensor's readings, and the numbers for Tunables.java.
@TeleOp(name = "Cookbook Piece Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class PieceTuner extends PieceTunerOpMode {
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
```java title="Controls.java"
// Hold RB: the robot turns to face the goal by itself; the driver keeps forward and sideways. (1)!
g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off).says("hold: aim the robot at the goal");
// Or a toggle. Careful: while it is on, the robot turns by itself, even after you let go. (2)!
// g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
```

1. Let go: the aim is off, and the driver turns again. Move the turn stick while you hold RB to turn by hand. Not with a turret that follows the goal: these are separate modes (INIT stops with a message).
2. A press switches it on, the next press switches it off. Two buzzes = on.

**Why:** the driver drives; the robot keeps itself pointed at the goal. Hold-to-aim first: let go and
the robot stops turning by itself, so it never surprises anyone. The stick always wins, so the driver
can take over at once. The toggle (commented out) keeps aiming after you let go.
**More:** [Lesson 9: aim the whole robot](tutorials/start/09-shooter-and-camera.md#10-no-turret-aim-the-whole-robot).

### How do I make the gamepad buzz when the aim is on target?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: aim-rumble -->
```java title="Controls.java"
// Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
// It works for a turret that follows the goal and for whole-robot aim.
g2.rumbleWhenAimed();
```

**Why:** the driver who fires watches the field, not the screen: the buzz says "now".
**More:** [Lesson 9: feel the aim](tutorials/start/09-shooter-and-camera.md#11-feel-the-aim-a-buzz-when-on-target).

### How do I keep aiming when the camera loses the goal?

**Where:** `Robot.java`, inside `define()`, and the goal's place in `Tunables.java`.

<!-- recipe: aim-odometry-backup -->
```java title="Robot.java"
// With an odometry line and Tunables.Aim goalX, goalY, the turret and the speed keep
// working when the camera loses the goal: from where the robot is on the field.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera)
        .startTable("pollen"));                  // the table to start with
```

<!-- recipe: tunables-aim -->
```java title="Tunables.java"
/** Aiming (every line is optional; leave out what you do not use). */
public static class Aim {                                       // a fixed name: always Aim
    // The goal on the field, in inches, seen from your drivers' wall (the same numbers as driveTo). (1)!
    public static double goalX = 13;                            // inches right of centre (BIOBUZZ)
    public static double goalY = -13;                           // inches from centre, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ: turned, not mirrored (true)
    public static double turnKp = DEFAULT;                      // whole-robot aim: push per degree off (0.02)
    public static double turnKd = DEFAULT;                      // brake near the goal (0.002)
    public static double maxTurnPower = DEFAULT;                // the most it turns by itself (0.5)
    public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
    public static double rumbleDeg = DEFAULT;                   // rumbleWhenAimed(): buzz within this (4)
}
```

1. With odometry, the aim goes on when the camera loses the goal. BIOBUZZ: your HIVE's CELL that is up at the start, 13 in right of the centre line and 13 in toward you.

**Why:** a tag gets blocked by a robot or a game piece; the odometry still knows where the goal is. The
Driver Station says which one aims (`following: camera` or `following: odometry`). It needs the pose
from autonomous (or [set pose here](#how-do-i-set-the-pose-when-autonomous-did-not-run)): without it, the
backup stays off and the Driver Station says so. `goalX`, `goalY` are written from your own drivers'
wall, like every field number in your code (only the simulator's map and its scenario checks use the red
wall). `mirrorForRed` is ONE setting for the whole robot (the routes, the start and reset spots, this goal
point): the first one found of `Tunables.Aim.mirrorForRed`, `Tunables.Odometry.mirrorForRed`, your
autonomous's `mirrorForRed()`, else `true` (a mirrored field). BIOBUZZ's turned field needs `false`: write
it once, here. INIT shows the alliance and this choice. The numbers in `Tunables.Aim` are in the tuning
menu too: copy tuned values back into `Tunables.java`.
**More:** [Lesson 9: when the camera loses the goal](tutorials/start/09-shooter-and-camera.md#9-when-the-camera-loses-the-goal-the-odometry), [Lesson 7: odometry](tutorials/start/07-odometry.md).

### How do I aim with the odometry only (no camera)?

**Where:** `Robot.java`, inside `define()` (the odometry, turret and launcher lines), and a section `Aim`
in `Tunables.java`.

<!-- recipe: aim-odometry-only -->
```java title="Robot.java"
// No camera: the odometry says where the robot is, Tunables.Aim where the goal is. The
// turret follows that direction and the launcher's speed comes from that distance.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).followsOdometry());
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromOdometry()
        .requireReady("turret aimed", () -> turret.isAimed()));
```

<!-- recipe: tunables-aim-odometry-only -->
```java title="Tunables.java"
public static class Aim {                                       // a fixed name: always Aim
    // Where the goal is, seen from YOUR drivers' wall (inches; the same numbers as driveTo).
    // BIOBUZZ: your HIVE's CELL that is up at the start.
    public static double goalX = 13;                            // inches right of centre (BIOBUZZ)
    public static double goalY = -13;                           // inches from centre, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ's field is turned, not mirrored
}
```

**Why:** no camera to buy, mount or tune. But the aim is only as good as the pose: it needs the start spot
from autonomous (or [set pose here](#how-do-i-set-the-pose-when-autonomous-did-not-run)), and a bump that
slips the wheels moves the aim off a little. With a camera too, use
[the camera first, the odometry as backup](#how-do-i-keep-aiming-when-the-camera-loses-the-goal).
The turret is taken to turn about the robot's centre. Whole-robot aim (no turret) works from the odometry
in the same way.
**More:** [Aim](modules/robot.md#aim), [Turret](modules/robot.md#turret).

### How do I make the camera work in any venue's light?

**Where:** `Robot.java`, inside `define()`: the camera line.

<!-- recipe: camera-lighting -->
```java title="Robot.java"
// In INIT the Limelight tries these pipelines (your usual one FIRST) and keeps the best one. (1)!
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
// A webcam: Camera.webcam("Webcam 1").autoExposure().goal(...) tries the camera's own
// exposure, then 3, 6 and 12 ms.
```

1. Each venue's lights are different. Set up each pipeline on the Limelight's web page for one kind of light. The Limelight keeps the pipeline that sees the most AprilTags, and the Driver Station says which one.

**Why:** bright stage lights or a dark corner can make a camera miss the AprilTags it saw in your shop.
The test runs in INIT (nothing moves), takes about a second per setting, and the Driver Station says what
it kept. Point the robot at the tags in INIT. Its numbers are optional (`Tunables.Limelight`:
`autoLighting = false` skips the test and uses `pipeline`).
**More:** [Vision: venue lighting](modules/vision.md#venue-lighting), [Camera](modules/robot.md#camera).

### How do I shoot while the robot is driving?

**Where:** `Tunables.java` (the numbers, in your `Aim` section), and a button in `Controls.java`
(inside `buttons(...)`) to switch it on and off. The robot needs [odometry](modules/robot.md#odometry):
it gives the robot's speed. **Off until you tune it on the real robot** (`shootOnMove = false`): measure
`flightSec` with a slow-motion video, test shots while driving, and only then write `true`.

<!-- recipe: tunables-moving-shot -->
```java title="Tunables.java"
// ADD these lines to your Tunables.Aim section (keep goalX, goalY and the others).
public static boolean shootOnMove = false;                  // true only after testing on the real robot
public static double flightSec = MEASURE;                   // seconds in the air (slow-motion video) (1)!
public static double flightSecPerIn = DEFAULT;              // longer shots fly longer: per inch (0)
public static double maxLeadIn = DEFAULT;                   // never lead more than this, inches (24)
// Where the shooter (a turret's pivot) and a camera on the body are from the robot's centre. (2)!
public static double shooterForwardIn = MEASURE;            // inches forward of the centre (a tape)
public static double shooterLeftIn = MEASURE;               // inches left of the centre (a tape)
public static double cameraForwardIn = MEASURE;             // inches forward of the centre (a tape)
public static double cameraLeftIn = MEASURE;                // inches left of the centre (a tape)
```

1. Film a shot in slow motion. Count the frames from when the piece leaves the launcher to when it reaches the goal. Divide by the video's frames per second.
2. In inches: forward (+) and left (+). A camera on the turret sits at the turret's pivot.

Then a button, in `Controls.java` inside `buttons(...)`:

<!-- recipe: moving-shot-button -->
```java title="Controls.java"
// Shooting on the move on/off (on from START when Tunables.Aim.shootOnMove = true): if
// the shots go wide while driving, switch it off and shoot standing still.
g2.a.toggle(robot.aim().movingShotMode()).says("shoot on the move on/off").rumble();
```

**Why:** a piece shot from a moving robot keeps the robot's speed, so it lands to the side (30 in/s for
0.8 s: 24 in). With `shootOnMove` the turret (or the whole robot) aims at a point that far the other way,
and the launcher's speed comes from that point's distance. It also brings the camera's picture forward
to now (the robot turned and moved since it was taken). It works with a turret or whole-robot aim, a
camera on the turret or on the body, or the odometry alone; tell it where the shooter and the camera
are. The Driver Station says `Aim : moving shot: lead 12.3 deg, 18 in` while it leads.
**More:** [Lesson 9: shooting while moving](tutorials/start/09-shooter-and-camera.md#14-extra-shooting-while-moving), [Aim](modules/robot.md#aim).

### How do I use one shot table per game piece?

**Where:** `Tunables.java` (the tables, in your `Launcher` section), then `Controls.java` (inside
`buttons(...)`) or `Actions.java` (inside the class) to switch.

<!-- recipe: tunables-shot-tables -->
```java title="Tunables.java"
// IN PLACE OF the speedByDistance line: one table per game piece, {inches to the goal, RPM}. (1)!
// The POLLEN table (example rows: write the Shot Tuner's).
public static double[][] speedByDistancePollen = {{24, 2200}, {48, 2550}, {72, 2900}};
// The NECTAR table.
public static double[][] speedByDistanceNectar = {{24, 2400}, {48, 2800}, {72, 3200}};
```

1. Write this in your `Tunables.Launcher` section. The name after `speedByDistance` is the table's name: `"pollen"` and `"nectar"`. Fill both from the Shot Tuner.

<!-- recipe: shot-table-buttons -->
```java title="Controls.java"
// One button per game piece (the launcher's D-pad right already picks the next table).
g2.x.press(() -> robot.launcher.useTable("pollen")).says("POLLEN shot table");
g2.b.press(() -> robot.launcher.useTable("nectar")).says("NECTAR shot table");
```

<!-- recipe: shot-table-action -->
```java title="Actions.java"
/** An action that picks the NECTAR table, then fires one (Actions.java). */
public static Action fireNectar(Robot r) {
    return Action.named("Fire NECTAR")
            .then(() -> r.launcher.useTable("nectar"))
            .then(r.launcher::fire);
}
```

**Why:** POLLEN (2.8 in) and NECTAR (3.6 in) fly differently, so each needs its own speeds. **Which
table at START?** A plain `speedByDistance` table (named `main`) if there is one, else the first by name:
here `nectar` (n before p). To start on POLLEN, add `.startTable("pollen")` to the launcher line in
`Robot.java` ([like here](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)). The Driver Station's
`Shot table` line says which one is in use. Fill each table with the
[Shot Tuner](guides/finders.md#shot-tuner-the-shot-table) ([its file](#how-do-i-build-a-shot-table-speed-for-each-distance);
Y until `> Table`, then the D-pad picks the one you tune), then copy its line into `Tunables.java`. Let
the robot pick the table by itself: [the next piece picks it](#how-do-i-let-the-next-piece-pick-the-shot-table).
**More:** [Lesson 9: two shot tables](tutorials/start/09-shooter-and-camera.md#8-two-game-pieces-two-shot-tables), [Launcher](modules/robot.md#launcher).

### Every shot is a bit short today. How do I trim the speed (a low battery)?

**Where:** nothing to write for the D-pad (the launcher brings it); your `Tunables.Launcher` section for
the numbers; `Controls.java` (inside `buttons(...)`) for your own buttons.

<!-- recipe: tunables-speed-trim -->
```java title="Tunables.java"
// In your Tunables.Launcher section (change the lines you already have, add the others).
public static double kP = MEASURE;                          // speed control (Flywheel Finder)
public static double kI = MEASURE;                          // speed control (Flywheel Finder)
public static double kD = DEFAULT;                          // speed control (0)
public static double kF = MEASURE;                          // steady power (Flywheel Finder)
public static double nominalVolts = MEASURE;                // battery volts for kF (Flywheel Finder)
public static double trimStepPercent = DEFAULT;             // one D-pad press (1%)
public static double trimMaxPercent = DEFAULT;              // never more than this each way (15%)
// Battery compensation: true = F follows the battery. Try it on a real robot and tell us.
public static boolean batteryCompensation = false;          // (off until proven on a real robot)
```

<!-- recipe: speed-trim -->
```java title="Controls.java"
// The launcher's own buttons already trim: gamepad 2 D-pad up/down = +/- 1%. (1)!
// Your own: a bigger step on a button, and a far shot that sets the trim first.
g2.y.press(() -> robot.launcher.trimBy(+3)).says("speed trim +3%");
g2.a.press(Action.named("Far shot +5%")
        .then(() -> robot.launcher.setTrim(+5), robot.launcher::fire)
        // The launcher fires or gives up within 3 s, so 4 s is always enough.
        .waitUntil("fired", () -> !robot.launcher.isShooting(), 4.0)
        .then(robot.launcher::resetTrim));
// Last match's trim (INIT offers it as "Trim last time"): from your own button too.
g2.x.press(robot.launcher::useLastTrim).says("use last match's trim");
```

1. Hold the D-pad for 4 steps a second. Double-tap D-pad down = back to 0. The launcher's first line on the Driver Station shows the trim: `Speed trim : +3%  READY`.

**Why:** a worn wheel or softer balls make every shot a little short. The trim (+/- 1% a press, at most
15% and never past the motor's top speed, back to 0 at every INIT) fixes it for this match, for every
table. At INIT the Driver Station offers last time's trim (`Trim last time : +3%`): double-tap D-pad up
after START to use it (or your own button: `useLastTrim`, in the recipe); it is never applied by itself. Battery compensation scales the F gain by `nominalVolts` / the battery
(tuned at 12.6 V, at 11.5 V: F x 1.10). It is not yet proven on a real robot, so it is off: the
box writes `batteryCompensation = false`. Change it to `true` only when you want to test it. It needs the `kF` and
`nominalVolts` the Flywheel Finder found together, which is why the box writes the gains as `MEASURE`;
without battery compensation, `DEFAULT` gains (from the motor) are a fine start. A trim you need every match, with a charged battery, points at the wheel
or the balls: the table is off. Make the trim part of it (+3%: every
speed x 1.03, `{48, 2550}` becomes `{48, 2627}`), copy the lines into `Tunables.java`, and trim back to 0.
**More:** [Lesson 9: speed trim and a low battery](tutorials/start/09-shooter-and-camera.md#speed-trim-and-a-low-battery), [Launcher](modules/robot.md#speed-trim-and-battery-compensation).

### How do I add a hood to my launcher?

**Where:** `Robot.java`, inside `define()`: add `.hood(...)` to your launcher line. `Tunables.java`: a new
section `Hood` (its stops), and a third number in each row of your `Launcher` section's shot table.

<!-- recipe: robot-hood -->
```java title="Robot.java"
// The hood goes on the launcher; its numbers are in Tunables.Hood.
// .part(...): which servos they are (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .hood(Hood.servo("hood").part("gobilda-2000-torque"))
        .speedFromDistance(camera));
```

A new section in `Tunables.java`:

<!-- recipe: tunables-hood -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // posA and posB: the servo positions a hair off the hood's two hard stops. Never past them.
    public static double posA = MEASURE;                        // servo position at stop A (Hood Finder)
    public static double posB = MEASURE;                        // servo position at stop B (Hood Finder)
    // Optional: the hood's angle at each stop (any scale, e.g. 0 = steep end, 30 = flat end).
    public static double angleA = MEASURE;                      // the angle at stop A (an angle app)
    public static double angleB = MEASURE;                      // the angle at stop B (an angle app)
    public static double travelSec = DEFAULT;                   // stop to stop, seconds (0.5, or the servo's)
    public static double startAngle = DEFAULT;                  // where it goes at START (angle A)
}
```

Then the shot table:

<!-- recipe: tunables-hood-table -->
```java title="Tunables.java"
// IN PLACE OF your speedByDistance line: {inches to the goal, RPM, hood angle}, blended between rows. (1)!
public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
```

1. This goes in your `Tunables.Launcher` section. The hood angle is blended between rows like the speed.

Never measured an angle? Leave out `angleA`, `angleB`: the hood works in servo positions, and the table's
third number is a servo position.

<!-- recipe: tunables-hood-positions -->
```java title="Tunables.java"
// Tunables.Hood: only posA and posB (delete angleA, angleB). Tunables.Launcher, IN PLACE OF
// your speedByDistance line, {inches, RPM, servo position}: the Shot Tuner writes these for you.
public static double[][] speedByDistance = {{24, 2200, 0.24}, {48, 2550, 0.38}, {72, 2900, 0.50}};
```

A hood on a linkage or cam (equal servo steps do not make equal angles): this `Hood` section in place of
the one above (a calibration table instead of `posA`, `posB`, `angleA`, `angleB`).

<!-- recipe: tunables-hood-linkage -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // {servo position, hood angle} rows, blended. The first and last rows are the stops (no posA / posB). (1)!
    // Example rows: write the Hood Finder's in their place.
    public static double[][] calibration = {{0.20, 0}, {0.32, 8}, {0.45, 18}, {0.65, 30}};
}
```

1. The angles must keep going one way: in servo-position order, the angles all go up, or all go down.

A hood on a motor with an encoder (it knows where it is): `Hood.motor` in the launcher line, and this
`Hood` section in place of the one above.

<!-- recipe: robot-hood-motor -->
```java title="Robot.java"
add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .hood(Hood.motor("hood", "gobilda-5203-117"))   // a motor with an encoder
        .speedFromDistance(camera));
```

<!-- recipe: tunables-hood-motor -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // Encoder ticks at the two stops, counted from where the hood sits at INIT (start the robot
    // with the hood on stop A), and the angles there.
    public static double posA = MEASURE;                        // ticks at stop A, usually 0 (Hood Finder)
    public static double posB = MEASURE;                        // ticks at stop B (Hood Finder)
    public static double angleA = MEASURE;                      // the angle at stop A (an angle app)
    public static double angleB = MEASURE;                      // the angle at stop B (an angle app)
    public static double kP = DEFAULT;                          // power per tick off (0.005)
    public static double maxPower = DEFAULT;                    // the most power (0.5)
    public static double tolerance = DEFAULT;                   // ready within this (1/50 of the range)
}
```

Your own hood buttons, in `Controls.java` inside `buttons(...)`:

<!-- recipe: hood-buttons -->
```java title="Controls.java"
// The launcher's hood already has gamepad 2's left stick Y (up = a bigger angle): a trim
// while the table sets the angle, else the angle itself. Your own: steps and the start angle.
g2.y.press(() -> robot.launcher.hood().step(+1)).says("hood +1 step");
g2.a.press(() -> robot.launcher.hood().step(-1)).says("hood -1 step");
g2.x.press(() -> robot.launcher.hood().goToStart()).says("hood to its start angle");
```

**Why:** close shots need a steep angle, far ones a flatter one, so each distance gets a speed AND a
hood angle, blended between rows. `posA`, `posB` are hard limits: nothing sends the hood past a stop.
The launcher waits for the hood before it fires (a servo hood: a share of `travelSec`; a motor hood: its
encoder). The stick (gamepad 2 left Y) is a hood trim while the table sets the angle (a goal is seen);
with no goal seen it moves the hood itself, so you always see what it does. Double-tap D-pad down clears
both trims. A lift brings the same stick (gamepad 2 left Y): with a hood AND a lift, give one of them
another stick ([move a button](#how-do-i-move-a-parts-button-to-another-button)); INIT says which part has it. Find the stops with the [Hood Finder](#how-do-i-find-my-hoods-stops-and-angles),
then fill the table with the [Shot Tuner](#how-do-i-build-a-shot-table-speed-for-each-distance) (Y until `> Hood`).
**More:** [Lesson 9: a hood](tutorials/start/09-shooter-and-camera.md#a-hood-the-shot-angle), [Hood](modules/robot.md#hood).

## Turrets and interlocks

Every kind of turret (motor, servo, CR servo; switch or not; any range) has its own lines: see
[Setting up a turret](guides/turret-setup.md). Code: [`InterlockRecipes.java`](../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook/InterlockRecipes.java).

### How do I home my turret with a switch?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-home-switch -->
```java title="Robot.java"
// "turret", "turret_home" = the motor's and the home switch's names in the robot configuration. (1)!
// At START the turret turns slowly until it finds the switch. (2)!
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
        .homeSwitch("turret_home")
        .follows(camera));
```

1. The home switch: a touch sensor ("REV Touch Sensor" in the configuration) or a magnetic switch ("Digital Device", for example the REV Magnetic Limit Switch).
2. After that, each time the turret passes the switch, its angle is set right again.

<!-- recipe: tunables-turret-switch -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double homeAngle = MEASURE;                   // where the switch closes (turret-setup.md)
    public static double homingPower = DEFAULT;                 // how slowly it looks for the switch (0.2)
    public static double homingTimeout = DEFAULT;               // the most seconds it looks (5)
}
```

**Why:** a motor's encoder counts from where the turret was at INIT. With a switch the turret finds its
real angle at START by itself (never during INIT: nothing moves before START), and a slipping belt is
fixed every time it passes the switch. Until it is homed it does not follow the goal and is never "aimed".
`homeAngle` must be written (INIT stops without it); `min`, `max` default to -90 and 90. No switch? Put the
turret at `startAngle` (default 0, straight forward) by hand before INIT.
**More:** [Setting up a turret: how it knows its angle](guides/turret-setup.md#2-how-does-it-know-its-angle).

### How do I set up a turret on a servo?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-servo -->
```java title="Robot.java"
// "turret" = the servo's name in the robot configuration. Numbers: Tunables.Turret. (1)!
// fullRange(): the servo's whole turn. Turn it on BEFORE you measure posA and posB. (2)!
turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange().follows(camera));
```

1. A standard servo turns the turret to an angle by itself: no encoder, no PID, no homing.
2. A goBILDA 2000 servo then turns 300 degrees, not 270. Find posA and posB with the Servo Finder: press Y while you choose the servo.

<!-- recipe: tunables-turret-servo -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    // Two servo positions and the turret's angle at each: 0 = straight forward, counterclockwise
    // (left) +. Its range is between the two angles.
    public static double posA = MEASURE;                        // servo position at one end (Servo Finder)
    public static double angleA = MEASURE;                      // the turret's angle there (an angle app)
    public static double posB = MEASURE;                        // servo position, other end (Servo Finder)
    public static double angleB = MEASURE;                      // the turret's angle there (an angle app)
    public static double travelSec = DEFAULT;                   // posA to posB, seconds (0.6, or the servo's)
    public static double startAngle = DEFAULT;                  // sent here at START (0)
}
```

**Why:** a servo knows how to go to a position, so the turret needs no encoder, no PID and no homing: the
cheapest turret. But a servo cannot say where it is, so at START it is sent to `startAngle` (or the
middle of its range), and "aimed" waits `travelSec` for it. `posA`, `angleA`, `posB`, `angleB` must be
written (INIT stops without them): find the positions with the [Servo Finder](#how-do-i-find-a-servos-open-and-closed-numbers)
and measure the angle at each with a phone angle app. Its range is between `angleA` and `angleB`. Put
hard stops just past them.
**More:** [Setting up a turret](guides/turret-setup.md#1-what-turns-it), [Full range](#how-do-i-get-my-servos-full-range).

### How do I set up a slip-ring turret (round and round)?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-slip-ring -->
```java title="Robot.java"
// A CR servo turns it, an absolute encoder says where it points, continuous(): no limits. (1)!
// "turret", "turret_pos" = the servo's and the encoder's names in the robot configuration.
// .part(...): which servo it is (optional, recommended).
turret = add(Turret.crServo("turret").part("axon-max-mk2").analogEncoder("turret_pos").continuous()
        .follows(camera));
```

1. The encoder is on an analog port and knows the angle at once: no homing. A slip ring carries the wires, so the turret has no limits and always takes the shortest way.

<!-- recipe: tunables-turret-slip-ring -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    // No min and max: it turns round and round.
    public static double kP = 0.015;                            // push per degree off, CR servo (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = 1.0;                        // a CR servo's full speed (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double encoderOffset = MEASURE;               // encoder reading, pointing forward
}
```

**Why:** with a slip ring the wires never wind up, so the turret can always take the shortest way to the
goal and is never "out of range". A motor turret can be `continuous()` too.
**More:** [Setting up a turret: how far can it turn?](guides/turret-setup.md#3-how-far-can-it-turn).

### How do I stop two parts from hitting each other?

**Where:** `Robot.java`, inside `define()`, one line below the parts' `add` lines; and a new section in
`Tunables.java` (the zones), named like the rule.

<!-- recipe: interlock-rule -->
```java title="Robot.java"
// AFTER the parts' add lines: the turret stays out of its zone while the lift is in its
// zone, and the other way round. Whoever is in its zone first keeps it.
keepApart("TurretLift", turret.zone(), lift.zoneAbove());
```

<!-- recipe: tunables-interlock -->
```java title="Tunables.java"
// The rule's own section, named like the rule in Robot.java. Each zone in its part's units.
public static class TurretLift {                                // keepApart("TurretLift", ...) in Robot.java
    public static double turretFrom = MEASURE;                  // degrees: where the turret's zone starts
    public static double turretTo = MEASURE;                    // degrees: where it ends (robot's back)
    public static double liftAbove = MEASURE;                   // counts: the slide is up from here
    public static double liftStopSec = 0.2;                     // a heavy slide takes longer to stop (0.15)
}
```

**Why:** each part is safe inside its own limits, but a turret over the back and a lift at the back can
still meet. The rule is kept every loop, whoever moves the parts (buttons, sticks, actions, a following
turret, autonomous steps): the part that is in its zone first keeps it, the other waits at the edge of
its own zone, and the Driver Station says so (`TurretLift : Lift held at 216 counts: Turret is between
130 and 218 deg`). A fast part is slowed down as it comes near and brakes in time, even with slow loops:
the rule measures its speed every loop. The zones are read at INIT only: change them in `Tunables.java`.
**More:** [Two parts that must not collide](guides/interlocks.md), [Interlock](modules/robot.md#interlock).

### How do I pick up and score with a lift, a wrist and a claw?

**Where:** `Actions.java`, inside the class; then a button in `Controls.java`, inside `buttons(...)`:
`g2.y.press(Actions.scoreInFlower(robot)).says("score in a FLOWER");`

<!-- recipe: lifter-sequence -->
```java title="Actions.java"
/** Lifts a held ball over a FLOWER, lets it go and comes back folded (Actions.java). */
public static Action scoreInFlower(Robot r) {
    return Action.named("Score in FLOWER")
            .then(() -> r.wrist.goTo("carry"))                  // 1. swing the claw out first
            .waitUntil("wrist out", r.wrist::isDone, 1.0)
            .then(() -> r.lift.goTo("flower"))                  // 2. lift (the interlock may make it wait)
            .waitUntil("lift up", r.lift::isAtTarget, 4.0)
            .then(() -> r.wrist.goTo("flower"))                 // 3. tip it over the FLOWER
            .waitUntil("over the FLOWER", r.wrist::isDone, 1.0)
            .then(r.claw::open)                                 // 4. let go
            .waitUntil("let go", r.claw::isDone, 1.0)
            .then(() -> r.wrist.goTo("carry"))                  // 5. back the same way
            .waitUntil("wrist back", r.wrist::isDone, 1.0)
            .then(() -> r.lift.goTo("stowed"))
            .waitUntil("lift down", r.lift::isAtTarget, 3.0)
            .then(() -> r.wrist.goTo("folded"), r.claw::close);
}
```

**Why:** the order keeps the parts out of each other's way: the claw swings out before the lift rises and
folds only after it is down. Each wait has a time limit, so a stuck part ends the action instead of
stopping the robot. With an interlock rule the lift waits by itself while the turret is in the way.
**More:** the whole robot: [`v2bot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot/Actions.java).

## Actions

An action is a list of steps that use several parts, with waits between them. It never makes the robot
wait: it does a little every loop. Actions go in `Actions.java`, one method each. The same action works
on a button and in an autonomous.

### How do I do several things one after the other?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-steps -->
```java title="Actions.java"
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
```java title="Actions.java"
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
```java title="Actions.java"
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
```java title="Actions.java"
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
```java title="autos/YourAuto.java"
s.run("Open the claw", robot.claw::open)
 .waitSeconds("Let the piece drop", 0.5)    // a name for the report, then the seconds
 .run("Lift down", () -> robot.lift.goTo("down"));
```

**Why:** `sleep()` stops the whole robot (no driving, no other part); `waitSeconds` waits while everything keeps running.
**More:** [Lesson 5: the rules](tutorials/start/05-actions.md#5-the-rules-every-action-follows), [Non-blocking code](guides/non-blocking.md).

### How do I wait until something is true?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-wait-until -->
```java title="Actions.java"
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
```java title="Controls.java"
// Press Y: the action starts. Press Y again while it runs: it is cancelled, safely.
g2.y.press(Actions.scoreHigh(robot)).says("score high");
g2.x.press(Actions.grabAndLift(robot)).says("grab and lift");
```

**Why:** the same button cancels it, and a stick or button that moves the same part also stops it: the driver always wins.
**More:** [Lesson 5: start it from a button](tutorials/start/05-actions.md#4-start-it-from-a-button).

### How do I stop every running action?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-cancel-all -->
```java title="Controls.java"
// Stop every running action at once (each stops what it started, safely).
g2.back.press(robot::cancelAll).says("stop all actions");
```

**Why:** a stopped action leaves each part safe: a lift holds its height, an intake stops.
**More:** [RobotDefinition: for your code](modules/robot.md#robotdefinition).

### How do I run an action right after START?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: action-at-start -->
```java title="Robot.java"
// In Robot.java's define(): this action runs once, right after START (autonomous and TeleOp).
atStart(() -> Actions.deployIntake(this));
```

The action it runs, in `Actions.java` inside the class (`intakeDeploy` is a
[servo with named positions](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)):

<!-- recipe: action-deploy-intake -->
```java title="Actions.java"
/** Folds the intake out (the atStart line in Robot.java runs it). */
public static Action deployIntake(Robot r) {
    return Action.named("Deploy intake").then(() -> r.intakeDeploy.goTo("deployed"));
}
```

**Why:** the library never moves anything in INIT (its safety choice: people are still next to the robot), so a
fold-out waits for START. The game rules would allow it (R103.B), but the robot must be still once INIT is done (G304.H).
**More:** [Lesson 5: an action at START](tutorials/start/05-actions.md#7-an-action-at-start).

### How do I use the same action in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: action-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .move("To the goal", 24, 0, 0.5)
 .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
 .move("Back up", 12, 180, 0.5);
```

**Why:** write the move once, use it in TeleOp and autonomous; the report says if it timed out.
**More:** [Lesson 5: in an autonomous](tutorials/start/05-actions.md#6-the-same-action-in-an-autonomous).

## Autonomous

An autonomous is one file in `autos/`: a list of named steps. Distances are in **inches** (1 foot = 12
inches, so 2 feet = 24), directions in degrees, power from 0 to 1.

> **Two different 90s.** A **move's direction** is measured clockwise, like a compass: 0 = forward,
> **90 = right**, 180 = backward, 270 = left (`.move("Right", 12, 90, 0.5)`). A **turn or a heading**
> is counterclockwise, like math class: **90 = left**, -90 = right (`.turn("Left", 90, 0.4)`,
> `turnTo(...)`, the heading in `start(...)` and `driveTo(...)`). Read every 90 twice.

**The robot only drives after the Drive Tuner.** Until then the autonomous shows "AUTONOMOUS IS OFF".
Run the [Drive Tuner](#how-do-i-tune-the-drive-for-autonomous), copy its lines, then set
`calibrated = true` in `Tunables.java`, section `Drive`. The simulator runs it anyway, so you can try a
route before tuning.

### How do I make my first autonomous (drive 2 feet)?

**Where:** a new file in `autos/` (in Android Studio: right-click the `autos` folder, New, Java Class,
name `LeaveTheStart`). The whole file; change the `package` and `import ...Robot` lines as the comment says:

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
         .move("Leave the start", 24, 0, 0.4);                  // 24 inches = 2 feet, forward, 0.4 power
    }
}
```

**Why:** `preselectTeleOp` makes the Driver Station pick your TeleOp next, so the drivers only press INIT.
**More:** [Lesson 6: the file, line by line](tutorials/start/06-first-autonomous.md#2-the-file-line-by-line).

### How do I drive forward, sideways and turn?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-moves -->
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="Tunables.java"
/** Your own numbers: a section no part reads. Every number here is in the tuning menu too. */
public static class Auto {
    public static double leaveInches = 24;                      // drive off the start line
    public static double toGoalInches = 30;                     // drive to the goal
    public static double parkInches = 20;                       // drive into the park zone
    public static double goalX = -30, goalY = -30;              // scoring spot: inches from centre (- y = our half)
    public static double goalHeading = 45;                      // facing there, degrees
    public static double parkX = -56, parkY = -36;              // park spot: inches from centre (- y = our half)
    // Several autos (guides/autos.md): the cycle, and the FAR start's way to the near side.
    public static double pickupInches = 20;                     // drive onto a piece
    public static double offWallInches = 12;                    // back off the wall
    public static double farToNearInches = 72;                  // the far start to the near side
}
```

**Why:** every number in your own section is in the tuning menu: change it in the TeleOp, save (A), and
the autonomous uses it from its next INIT. **Then copy the good value into `Tunables.java`**: a value
saved only on the hub is lost with a new Control Hub.
**More:** [Lesson 6: route numbers](tutorials/start/06-first-autonomous.md#3-route-numbers-live-in-tunablesauto), [Tunables.java](modules/robot.md#tunablesjava).

### How do I share the same first steps in several autos?

**Where:** your autonomous class (or a shared file), next to `steps(...)`.

<!-- recipe: auto-shared-opening -->
```java title="autos/YourAuto.java"
/** The same first steps for several autos: a method that adds them to the list. */
static Steps scorePreload(Steps s, Robot robot) {
    return s.move("To the goal", Tunables.Auto.toGoalInches, 0, 0.5)
            .action(Actions.scoreHigh(robot), 5);
}
// in each autonomous:  scorePreload(s.start("...", 0), robot).move("Park", 20, 90, 0.7);
```

**Why:** fix the opening once and every autonomous gets the fix.
**More:** [The autonomous builder: recipes](guides/autonomous-builder.md#recipes).

### How do I make the start delay shorter, or use a different number on RED?

Every autonomous's INIT menu already has `Alliance` and `Start delay` (0 to 10 s, D-pad left/right).
A shorter menu line: **where:** your autonomous class, as a method of its own next to `steps(...)`:

<!-- recipe: auto-max-start-delay -->
```java title="autos/YourAuto.java"
// Inside your autonomous class, next to steps(...): the INIT menu's "Start delay" line
// offers 0 to 5 s (the default is 0 to 10 s).
@Override
protected double maxStartDelaySec() {
    return 5;
}
```

One number that is different on RED (most routes need none: RED is mirrored or turned for you):
**where:** inside `steps(...)`, above the steps.

<!-- recipe: auto-alliance-pick -->
```java title="autos/YourAuto.java"
// Inside steps(...): alliance() is the alliance chosen in INIT. pick(blue, red) gives the
// first value on BLUE and the second on RED (here: RED waits 2 s for its partner).
double waitSec = alliance().pick(0.0, 2.0);
s.start("Against our wall, facing the field", 0)
 .waitSeconds("Let our partner go first", waitSec)
 .move("Leave the start", 24, 0, 0.4);
```

**Why:** the start delay waits before the first step, so a partner can go first; 10 s is a lot to scroll
through. `alliance().pick(blue, red)` works for any kind of value: a number, a name, an AprilTag id.
**More:** [What the INIT menu can choose](guides/autos.md#what-the-init-menu-can-choose).

## Several autos and choosing one

One autonomous per plan, or one autonomous with an INIT menu? Both, built from the same pieces. The
decision table, naming, simulator tests and a match-day checklist: [the guide](guides/autos.md). Each
file below is whole: copy it into `autos/` and change the `package` and `import` lines as its comment
says. The numbers come from your `Tunables.Auto` section ([these lines](#how-do-i-change-a-distance-without-building-the-code-again):
`toGoalInches`, `pickupInches`, `parkInches`, `offWallInches`, `farToNearInches`).

### How do I build several autos from the same pieces?

**Where:** a new file `Routes.java` in `autos/`. It is not an OpMode (no `@Autonomous`): the autos
below call its methods.

<!-- recipe: autos-routes -->
```java title="autos/Routes.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first three imports below to:  import org.firstinspires.ftc.teamcode.Actions;
//   import org.firstinspires.ftc.teamcode.Robot;   import org.firstinspires.ftc.teamcode.Tunables;

import com.crossbotics.ftchelper.examples.cookbook.Actions;
import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
import com.crossbotics.ftchelper.robot.Steps;

/** The pieces every autonomous is made from. Write each piece once. No @Autonomous: not an OpMode. */
public class Routes {
    /** Score the piece the robot starts with: hold it, drive to the goal, score. */
    public static Steps scorePreload(Steps s, Robot robot) {
        return s.run("Hold the preload", robot.claw::close)
                .move("To the goal", Tunables.Auto.toGoalInches, 0, 0.6)          // 0 = forward
                .action(Actions.scoreHigh(robot), 5);
    }

    /** One more piece: back to the pieces, grab one, forward to the goal, score. */
    public static Steps cycle(Steps s, Robot robot) {
        return s.move("To the pieces", Tunables.Auto.pickupInches, 180, 0.6)    // 180 = backward
                .run("Grab", robot.claw::close)
                .waitSeconds("Let the claw close", 0.3)
                .move("Back to the goal", Tunables.Auto.pickupInches, 0, 0.6)
                .action(Actions.scoreHigh(robot), 5);
    }

    /** The park: back toward our wall. It keeps the last 3 seconds for itself, so it always runs. */
    public static Steps park(Steps s) {
        return s.parkWithSeconds(3)
                .move("Park", Tunables.Auto.parkInches, 180, 0.7);
    }
}
```

**Why:** each piece is written once, so a fix (or a new number in `Tunables.Auto`) reaches every
autonomous. `public static Steps scorePreload(Steps s, Robot robot)`: a method any file can call
(`public static`) that adds its steps to the list `s` and hands the list back (`return`).
**More:** [Build them from shared steps](guides/autos.md#build-them-from-shared-steps).

### How do I make two autos that differ by one piece?

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
        s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
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
        s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

**Why:** two lines on the Driver Station, named after the start spot and the plan, so the drivers pick fast.
Each line in `steps(...)` calls a piece; the piece adds its steps to the same list `s`.
**More:** [Example 1](guides/autos.md#example-1-two-opmodes-from-the-same-pieces), [names and groups](guides/autos.md#names-and-groups).

### How do I let the drivers choose the start spot and the plan in INIT?

**Where:** one new file in `autos/`, `ChooseInInit.java`.

<!-- recipe: autos-choose-in-init -->
```java title="autos/ChooseInInit.java"
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first two imports below to:  import org.firstinspires.ftc.teamcode.Robot;
//   import org.firstinspires.ftc.teamcode.Tunables;

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.examples.cookbook.Tunables;
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
             .start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0)
         .end()
         .onlyIf("Start", "Far")
             .start("FAR: against our wall, 2nd tile right of centre, facing the field", 36, -61, 0)
             // Off the wall first: the FLOWER on our wall stands between the two start spots.
             .move("Off the wall", Tunables.Auto.offWallInches, 0, 0.5)
             .move("Across to the near side", Tunables.Auto.farToNearInches, 270, 0.7)   // 270 = left
         .end();

        // The plan: plain Java "if". choice(...) = the option chosen, as text. (1)!
        String plan = s.choice("Plan");
        if (!plan.equals("Park only")) Routes.scorePreload(s, robot);
        if (plan.equals("Preload + Cycle + Park")) Routes.cycle(s, robot);
        Routes.park(s);
    }
}
```

1. The pieces are Java methods, so a plain `if` picks them. `choice(...)` gives the text of the option (a String). `equals(...)` asks "the same text?". `!` means "not".

**Why:** a `start(...)` inside an `onlyIf` is used only for its option, so the "Place robot" note (and
the simulator's robot) changes as the drivers change the menu. The first option of each line is the
default: make it your safest plan. Your lines come after the library's two (`Alliance`, `Start delay`),
so the drivers press D-pad down twice to reach `Start`. The FAR route drives off the wall first: the
FLOWER on your wall stands between the two start spots. A few Java words: `String plan` keeps a text;
`plan.equals("Park only")` is true when the two texts are the same; `!` means "not".
**More:** [Example 2](guides/autos.md#example-2-one-opmode-the-drivers-choose-in-init), [what the menu can choose](guides/autos.md#what-the-init-menu-can-choose).

## Odometry and driveTo

Odometry tells the robot where it is on the field. **Field numbers:** x = inches right of the centre
line, y = inches away from your wall (negative = your half), heading 0 = facing away from you,
counterclockwise (left) is +. **Always seen from your own drivers' wall**, whatever your alliance: every
number in your code (start spots, `driveTo`, the goal point, the reset spot). Only the simulator's map
and its scenario checks (`expect robot x ...`) use one fixed view, from the red wall. See the
[field map](tutorials/start/07-odometry.md#1-why-odometry).

### How do I add a goBILDA Pinpoint?

**Where:** `Robot.java`, inside `define()`, after the drive line.

<!-- recipe: odometry-pinpoint -->
```java title="Robot.java"
// After drive(...). Offsets, mm: how far LEFT the forward pod is, how far FORWARD the sideways pod is. (1)!
// Not sure? Write offsetsMm(0, 0): the Odometry Tuner measures them.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
```

1. Both from the robot's centre. Right and behind are minus: here the forward pod is 84 mm right of the centre, and the sideways pod is 168 mm behind it.

**Why:** the offsets tell it where the pods are, so a turn on the spot does not look like a move.
**More:** [Lesson 7: one line in Robot.java](tutorials/start/07-odometry.md#4-one-line-in-robotjava), [Pinpoint](modules/odometry.md#pinpoint).

### How do I add a SparkFun OTOS?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-otos -->
```java title="Robot.java"
// Where the OTOS sits: inches forward and left of the robot's centre, and its turn in degrees.
odometry(Odometry.otos("otos").mountIn(3.5, 0, 0));
```

**Why:** the OTOS measures at its own spot, so it must know where it sits to give the robot centre's place.
**More:** [OTOS](modules/odometry.md#otos).

### How do I add dead wheels?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-dead-wheels -->
```java title="Robot.java"
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
```java title="OdometryTuner.java"
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
```java title="autos/YourAuto.java"
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
```java title="Controls.java"
show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in, heading %.0f",
        robot.pose().x, robot.pose().y, robot.pose().headingDeg));
```

**Why:** you can check the odometry by pushing the robot by hand and watching the numbers.
**More:** [robot.pose()](modules/odometry.md#robotpose). (TeleOp also starts where autonomous ended: [lesson 7](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended).)

### How do I use the robot's position in an action?

**Where:** `Actions.java`, inside the class.

<!-- recipe: pose-in-action -->
```java title="Actions.java"
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
reset spot, then hold gamepad 1 **START** for 1 second ("set pose here"), in INIT or in the match, with
the drive sticks let go. The spot, written for BLUE like a start spot. `mirrorForRed = false` because
BIOBUZZ's field is turned half a turn (RED uses the same numbers from its own wall); leave it out
(default `true`, RED mirrored) on a mirrored field. INIT's `Reset spot for RED` line shows which is used.

<!-- recipe: tunables-reset-spot -->
```java title="Tunables.java"
public static class Odometry {                                  // a fixed name: always Odometry
    public static boolean checked = false;                      // true after the Odometry Tuner's Loop test
    // "Set pose here" (TeleOp: hold gamepad 1 START 1 s on this spot), written for BLUE. (1)!
    public static double resetX = -56;                          // inches right of centre, - = left
    public static double resetY = -61;                          // inches from centre, - = our half
    public static double resetHeading = 0;                      // degrees, 0 = facing the field
    // RED: false = the same numbers from the red wall; true (or left out) = mirrored left-right. (2)!
    public static boolean mirrorForRed = false;                 // BIOBUZZ: false
}
```

1. Inches and degrees seen from your wall, like a start spot. Here: our left corner tile, facing the field.
2. BIOBUZZ's field is turned half a turn between the alliances: write `false`. `true` is for a mirrored field.

More spots, or from an action: in `Controls.java`, inside `buttons(...)`:

<!-- recipe: reset-pose-button -->
```java title="Controls.java"
// A second spot of your own: hold D-pad down 1 s with the robot in the right corner tile. (1)!
g1.dpadDown.heldFor(1.0, () -> robot.resetPose(56, -61, 0)).says("hold 1 s: pose = right corner tile");
// An action can set it too (the action is in Actions.java, below). (2)!
g1.dpadUp.heldFor(1.0, Actions.poseHereThenIntake(robot)).says("hold 1 s: pose here, intake on");
```

1. x, y and heading are written for BLUE. RED uses them like the reset spot in `Tunables.Odometry`.
2. Here the action sets the `Tunables.Odometry` spot, then runs the intake.

The action, in `Actions.java` inside the class:

<!-- recipe: pose-here-action -->
```java title="Actions.java"
/** Sets the pose to the reset spot in Tunables.Odometry, then runs the intake. */
public static Action poseHereThenIntake(Robot r) {
    return Action.named("Pose here, intake on")
            .then(r::resetPose)            // resetPose() with no numbers: the Tunables.Odometry spot
            .then(r.intake::in);
}
```

**Why:** with a pose, the aim's odometry backup and field-centric "forward" work again. Holding 1 s
means a bump of the button does nothing. All the spots are written from your own drivers' wall, like
every field number in your code.
**More:** [Lesson 7: TeleOp starts where autonomous ended](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended), [resetPose()](modules/robot.md#robotdefinition).

## Pedro paths

Needs the `pedro` folder in your project ([add Pedro](tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)),
a mecanum drive and tuned odometry. Paths use the same field numbers as `driveTo`.

### How do I add Pedro Pathing to Robot.java?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: pedro-follower -->
```java title="Robot.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
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
```java title="autos/YourAuto.java"
// Keep facing one field point the whole way (here x -60, y 60: put your goal's x and y).
static final PedroPath WATCH_GOAL = PedroPath.line(-30, -30, -56, -36).facePoint(-60, 60);
```

**Why:** the robot can slide along the path while it keeps the camera or launcher on the goal.
**More:** [PedroPath: the heading](modules/pedro.md#pedropath-paths-in-our-field-numbers).

### How do I start something partway along a path?

**Where:** your autonomous, `steps(...)`. Android Studio shows `PathAt` in red: Alt+Enter adds the import.

<!-- recipe: pedro-along -->
```java title="autos/YourAuto.java"
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
```java title="Controls.java"
// A line of its own in buttons(...). The value is read again every loop.
show("Lift ticks", robot.lift::position);
show("Intake on", robot.intake::isRunning);                 // shows "Intake on : true"
show("Flywheel ready", () -> robot.launcher.isReady());
```

**Why:** you see what the robot "thinks" while you drive. The value must be quick to read (no waiting).
**More:** [The button menu: feedback](guides/buttons.md#4-feedback). Debug lines (loop time and more): hold gamepad 1 START, then press BACK (BACK first is just a pause).

### How do I round a number on the Driver Station?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-format -->
```java title="Controls.java"
// Round a number: %.0f = no decimals, %.1f = one decimal. Locale.US keeps the dot (not a comma).
show("Target RPM", () -> String.format(Locale.US, "%.0f", robot.launcher.targetRpm()));
```

**Why:** `%.0f` means "a number with 0 decimals"; `%.2f` gives 2.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback).

### How do I show lines from my own autonomous step?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-telemetry -->
```java title="autos/YourAuto.java"
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
at the top of each file that uses them: `Controls.java`, and `Actions.java` if an action flashes the
light (Alt+Enter on a red `LightColor` adds it). All of it: [Lights](modules/robot.md#lights).

### How do I add a colored light for the drivers?

**Where:** `Robot.java`, inside `define()` (and a field `public Lights lights;`).

<!-- recipe: light-add -->
```java title="Robot.java"
// At the top of Robot.java: import com.crossbotics.ftchelper.robot.Lights; (1)!
// A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
lights = add(Lights.gobilda("light"));
// Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
//   lights = add(Lights.blinkin("blinkin"));
```

1. `Controls.java` needs `import com.crossbotics.ftchelper.robot.LightColor;` for the colors (Alt+Enter on a red `LightColor` adds it).

**Why:** one line, like any part. It has no numbers and no buttons of its own: the colors come from your rules.
**More:** [Lights](modules/robot.md#lights) (the parts and their color tables).

### How do I change a color on a REV Blinkin?

**Where:** `Robot.java`, inside `define()`, below the lights line.

<!-- recipe: light-blinkin -->
```java title="Robot.java"
// At the top of Robot.java: import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
// A REV Blinkin LED Driver ("REV Blinkin LED Driver" in the robot configuration):
lights = add(Lights.blinkin("blinkin"));
// Plain blue too dark on your strip? Show another Blinkin pattern for BLUE. (1)!
lights.blinkinPattern(LightColor.BLUE, RevBlinkinLedDriver.BlinkinPattern.BLUE_VIOLET);
```

1. The pattern names are on REV's Blinkin page. Other lights ignore this line.

**Why:** each `LightColor` is one of the Blinkin's patterns (`BLUE` is its plain blue). LED strips differ:
when one color looks wrong on yours, give that color another pattern. Your rules keep saying
`LightColor.BLUE`. A goBILDA light ignores the line.

### How do I show a color when something is true?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: light-rules -->
```java title="Controls.java"
// One rule per line. The first true rule wins: put the most important first.
// Red and blue are left for the alliance (shown at INIT), so no rule looks like one.
robot.lights.show(LightColor.ORANGE).blinking().when(robot.intake::isPushingOut);  // pushing out
robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);              // at speed: fire
robot.lights.show(LightColor.PURPLE).when(robot.intake::isRunning);             // intake on
robot.lights.otherwise(LightColor.OFF);                                         // none true
```

**Why:** any true/false works as a rule: a part's method (`robot.intake::isRunning`), a mode
(`() -> robot.turret.followMode().isOn()`: the turret follows the goal; on a robot without a turret,
`() -> robot.aimMode().isOn()`: whole-robot aim is on), or your own test
(`() -> robot.lift.position() > 1000`, if your robot has a lift). Blinking stops at 5 times a second, and
the light never changes faster than every 0.1 s (FTC rule R202 J).
**More:** [Lights](modules/robot.md#lights), [lambdas](tutorials/start/05-actions.md).

### How do I show the alliance on the light before the match?

**Where:** nothing to add: it is on by default. To change its colors or turn it off, `Controls.java`,
inside `buttons(...)`.

<!-- recipe: light-alliance -->
```java title="Controls.java"
// On by default: the alliance at INIT (solid RED or BLUE), flashed for 1 s at START. (1)!
// Your light shows red badly? Other colors for the alliance at INIT (BLUE, and PURPLE for RED):
robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);
// No alliance color at INIT at all? Delete the line above and write: (2)!
//     robot.lights.allianceAtInit(false);
```

1. Until an alliance is chosen, the light blinks white. You do not write anything for this.
2. Your rules then show from INIT on. Or, in `Tunables.java`, in the light's section: `public static class Light { public static boolean allianceAtInit = false; }`. The line in `Controls.java` wins.

**Why:** a wrong alliance pick in INIT aims at the other alliance's goal all match. With the light solid
RED or BLUE before START (white blinking once a second until the drivers pick), the drive team sees a wrong
pick from the field. The game manual has no rule against a light in your alliance color; the ROBOT SIGNS
are still required (R401, R402). Keep your own rules' colors away from red and blue.
**More:** [Lights](modules/robot.md#the-alliance-at-init).

### How do I flash the light when something happens?

**Where:** `Controls.java`, inside `buttons(...)`, above your other light rules.

<!-- recipe: light-flash -->
```java title="Controls.java"
// A flash beats every rule while it lasts (write it first: it reads that way). (1)!
// A short white flash for each piece fired, and one from a button.
robot.lights.show(LightColor.WHITE).forSeconds(0.2)
        .whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
g2.a.press(() -> robot.lights.flash(LightColor.YELLOW)).says("flash the light (signal the partner)");
```

1. A flash wins wherever it is written. The launcher counts the pieces it feeds. The flash is 0.2 s, so two quick shots are two flashes, not one long one.

**Why:** `whenCountGoesUp` flashes each time the number goes up, so each flash is a real shot.
`whenStarts(test)` flashes when a true/false turns from false to true (`whenStarts(robot.intake::isRunning)`:
the intake just started). A flash lasts 0.5 s; `.forSeconds(...)` before `when...` changes it (shorter here,
so two shots a quarter second apart are two flashes, not one long one). `flash(color)` from a button or
an action flashes once.

### How do I flash the light when an action is done?

**Where:** `Actions.java`, inside the class.

<!-- recipe: light-action -->
```java title="Actions.java"
// In Actions.java (it needs import com.crossbotics.ftchelper.robot.LightColor; at the top).
// The last step: a 1 s white flash says "done" to the drivers. Pick a color your rules do not use. (1)!
public static Action shootTwo(Robot r) {
    return Action.named("Shoot two")
            .then(r.launcher::spinUp)
            .waitUntil("at speed", r.launcher::isReady, 3.0)
            .then(() -> r.launcher.fire(2))
            .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
            .then(() -> r.lights.flash(LightColor.WHITE, 1.0));
}
```

1. A GREEN flash over the GREEN "ready" rule cannot be seen. Pick another color, or a longer time.

**Why:** the light is never "claimed" by an action, so any action (or button) may flash it. A flash
beats every rule, but a flash in the same color as the rule under it cannot be seen: pick another color,
or a longer time.

### How do I blink the light in the last 20 seconds?

**Where:** `Controls.java`, inside `buttons(...)`, before the other rules.

<!-- recipe: light-endgame -->
```java title="Controls.java"
// Yellow, blinking, in the last 20 seconds of TeleOp: time to park (above the other rules: it wins).
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
```java title="ServoFinder.java"
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
into `Tunables.java` and rename P1, P2 (`open`, `closed`). A part with
[`.fullRange()`](#how-do-i-get-my-servos-full-range) in Robot.java: press Y while choosing the servo first.
**Why:** a servo pushed past its end hums, gets hot and breaks; the finder finds the safe numbers.
**More:** [Servo Finder](guides/finders.md#servo-finder-ends-and-positions), [the order](guides/finders.md#the-order).

### How do I find a lift's limits and heights?

**Where:** a new file in TeamCode (also in the [starter kit](starter-kit.md)). The whole file:

<!-- recipe: motor-finder -->
```java title="MotorFinder.java"
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
```java title="DriveTuner.java"
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

Before you have them, write `DEFAULT` in their place (or leave the lines out): the launcher
starts from the parts catalog's numbers for its motor
(a goBILDA 6000 RPM motor: kF 11.7, kP 1.17, kI 0.117, and 60% of its top speed, 3600 RPM) and INIT
says so. Then run the **Flywheel Finder** (in the [starter kit](starter-kit.md)) and copy its line into
`Tunables.Launcher` (see [the launcher recipe](#how-do-i-add-a-launcher-flywheel-and-feeder)). Do not copy
another robot's gains: they were found for its wheel.

**Why:** the right `kP`..`kF` make the wheel get back to speed fast after each shot.
**More:** [Flywheel Finder](guides/finders.md#flywheel-finder-gains-and-recovery).

### How do I build a shot table (speed for each distance)?

**Where:** a new file in TeamCode, `ShotTuner.java`. Run the [Aim Tuner](guides/finders.md#aim-tuner-camera-offset) first.
The whole file: its first line is `package org.firstinspires.ftc.teamcode;` (Android Studio writes it
when you make the file), then:

<!-- recipe: shot-tuner -->
```java title="ShotTuner.java"
import com.crossbotics.ftchelper.finders.ShotTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

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

**Why:** at each distance you shoot 3-5 per speed and mark each HIT (A) or MISS (B), all on gamepad 1
(left stick click takes the last mark back); X keeps the speed with the best hit ratio (hits out of
shots). It fills `Tunables.Launcher.speedByDistance` (one line per table to copy), so the
launcher picks the speed for each distance.
**More:** [Shot Tuner](guides/finders.md#shot-tuner-the-shot-table).

### How do I find my hood's stops and angles?

**Where:** a new file in TeamCode, `HoodFinder.java` (first line `package org.firstinspires.ftc.teamcode;`):

<!-- recipe: hood-finder -->
```java title="HoodFinder.java"
import com.crossbotics.ftchelper.finders.HoodFinderOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

// The Hood Finder: the hood's two stops (and its angles) for Tunables.Hood. It reads the hood
// from Robot.java (servo or motor), so it works before Tunables.Hood has numbers.
@TeleOp(name = "Cookbook Hood Finder", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class HoodFinder extends HoodFinderOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the finder finds its hood
    }
}
```

**Why:** nudge the hood to each stop, back off a hair, X and B: it writes `Tunables.Hood: posA = ..., posB = ...`.
Hold LB + D-pad to type in the angle you measured before X / B (or A for a point in between, for a
linkage). Copy the lines into `Tunables.java`.
**More:** [Hood Finder](guides/finders.md#hood-finder).

## Simulator

The simulator runs your real code on a laptop, with the field in 3D. The easy way to start it is a
button in Android Studio (below). Or type the commands in a terminal:
in Android Studio, the **Terminal** tab at the bottom ([how](start-from-zero.md#6-the-terminal-tab)).
First go into the `ftc-helper-lib` folder by typing `cd ftc-helper-lib`. (It is the library's folder
inside your robot project, next to `TeamCode` in Android Studio's Project panel. Not there? Ask your
mentor, or see [Add it to a project](01-add-to-project.md).) `./gradlew` runs Gradle, the tool that builds the code; `-P...` gives it a setting.

### How do I start the simulator?

In Android Studio, pick **Simulator** in the list next to the green Run button, and click Run. (No
**Simulator** in the list? Close the project and open it again: [the buttons](01-add-to-project.md#step-7-the-simulator-buttons-once).)
Or, in the terminal:

```bash
./gradlew :sim:run
```

Then open **http://localhost:8780** in a browser. Choose an OpMode, press INIT, then START. The red
**Stop** button (or Ctrl+C in the terminal) stops it.

**Why:** you can try buttons and autonomous routes before the robot is built.
**More:** [Simulator: start it](guides/simulator.md#start-it).

### How do I see my own robot in the simulator?

Start the simulator (the **Simulator** button, or `./gradlew :sim:run`). It runs your TeamCode, and
when it starts it makes the simulator's robot file from your `Robot.java`: it prints `Robot file made
from Robot.java: simulator/robot.properties`.

**Why:** the robot file then always matches your code. Your own lines in it are kept. Changed the
code? Stop the simulator and start it again.
**More:** [A robot file from Robot.java](guides/simulator.md#a-robot-file-from-robotjava), [Your own robot code](guides/simulator.md#your-own-robot-code).

### How do I make the simulator draw my part?

**Where:** `Robot.java`: add `.looks(...)` to the part's line (optional).

<!-- recipe: looks-parts -->
```java title="Robot.java"
// For the simulator only. All in mm. atMm(forward, left, up): from the middle of the chassis, on the tiles. (1)!
// sizeMm(front to back, side to side, up).
chassisMm(400, 400, 100);                    // the chassis box
heightMm(380);                               // the whole robot, folded
// TWO shapes on one part: name each shape, then looks(intakeArm, intakeRoller).
// passive(): nothing in the code moves the arm. range: its two hard stops, degrees. (2)!
Shape intakeArm = Shape.arm().name("intake_arm").atMm(150, 0, 80).sizeMm(80, 300, 20)
        .passive().range(-20, 0).startAt(0);
Shape intakeRoller = Shape.roller("gobilda-gecko-72").on("intake_arm").atMm(80, 0, 0).lengthMm(300);
intake = add(Intake.motor("intake").part("gobilda-5203-1150").looks(intakeArm, intakeRoller));
// ONE shape: write it inside looks(...). A slide at the back; travelMm: how far it rises.
lift = add(Lift.slide("lift").part("gobilda-5203-435")
        .looks(Shape.slide().atMm(-170, 0, 20).sizeMm(40, 40, 300).travelMm(600)));
// A claw on top of the slide: on(lift) makes it go up with the slide.
claw = add(Claw.servo("claw").part("gobilda-2000-torque")
        .looks(Shape.claw().on(lift).atMm(60, 0, 300).sizeMm(80, 120, 50)));
// A flywheel: its size comes from the wheel's catalog id. Only its place is yours.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .looks(Shape.flywheel("gobilda-hogback-96").atMm(-40, 0, 200)));
```

1. The real robot never reads `.looks(...)`. Measure with a tape, or shape the part in the simulator's Edit robot mode and copy its lines.
2. The arm hinges at the front and rests on its stop. The roller rides on the arm's end: `on("intake_arm")`.

**One shape or several:** one shape goes inside `looks(...)` on the part's line. Two or more (an arm
and its roller): give each a name first (`Shape intakeArm = ...;`), then write `looks(intakeArm, intakeRoller)`
([how](modules/robot.md#shapes-and-parts)).
**Why:** without `.looks(...)` the simulator still runs, with a guessed box for each part (marked
`TODO measure` in the robot file). With it, the 3D view shows your robot, the size check (18 in start
cube, 29 in tall) is right, and pieces touch the intake where the real one is. Name the part
(`.part(...)`) too: a roller's or a wheel's size then comes from the parts catalog. **The easy way:**
the simulator's **Edit robot** mode: drag and size each part on the 3D robot, then copy the
`.looks(...)` lines it shows into `Robot.java`. After a change, stop the simulator and start it again.
**More:** [Shapes and parts](modules/robot.md#shapes-and-parts) (every shape and setting),
[A robot file from Robot.java](guides/simulator.md#a-robot-file-from-robotjava).

### How do I test my code with a script?

The easy way: **record** it. In the simulator, **Tests > Record a test**, drive, **Stop recording**,
keep the ticked checks, **Save**: the simulator writes `simulator/scenarios/NAME.sim` for you
([start lesson 10](tutorials/start/10-test-your-robot-code.md)). Or write one by hand, for example
`simulator/scenarios/intake.sim` (the library runs this same file as a test: `sim/scenarios/cookbook-intake.sim`):

```text
init "Quick Start TeleOp"            # your TeleOp's name
start
hold g2.rt 1                         # hold RT (the intake runs while it is held)
expect within 1 motor intake power > 0.5
release g2.rt
expect within 1 motor intake power = 0 0.01
expect error none
```

Run every test in the folder: **Tests > Run all tests** on the simulator page, or the **Simulator tests**
button in Android Studio. Or, in the terminal (leave out `-Pfile` for every test):

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/intake.sim
```

It runs your own code (`../TeamCode`, next to the library in your robot project).
**Why:** a script checks the same things every time, with no clicking, and catches a mistake before a meeting.
Button names in a scenario are the FTC names, not the Java ones: `g2.rt`, `g2.lb`, `g2.a` are the same,
but `g2.dpadUp` is `g2.dpad_up` and `g2.leftStickY` is `g2.left_stick_y`.

**More:** [Start lesson 10: Test your robot code](tutorials/start/10-test-your-robot-code.md), [Scenario files](guides/simulator.md#scenario-files-test-without-a-browser) (every command and button name).

## Not here?

- Every class and method: the [reference](reference.md) and the [module pages](../README.md#what-is-inside).
- Something goes wrong: [Troubleshooting](troubleshooting.md).
- Learn it step by step: the [lessons](tutorials/README.md).
- A question this page should answer? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).
