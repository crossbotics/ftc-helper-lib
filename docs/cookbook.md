# Cookbook: "How do I...?"

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Short answers to the questions students ask most. Each recipe has one question, a few lines of code
to copy, one sentence on **why**, and links to the lesson that explains more.

**To find your question:** press **Ctrl+F** (on a Mac: **Cmd+F**) and type a word, like `lift`,
`double-tap` or `park`. Or use the list below.

**The code works.** Every code block on this page is a copy of real Java files in
[`examples/.../cookbook`](../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook). Each time
the library changes, we build those files and run them in the simulator. A check
(`tools/check_docs.py`) makes sure that this page and the files say the same thing.

**If you are new:** do the [Quick start](quick-start.md) first. It puts the five files in your robot
project, and these recipes go into those files. If you never coded before, read
[Words you will meet](#words-you-will-meet) first.

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
- [How do I put the launcher's fire button on another button?](#how-do-i-put-the-launchers-fire-button-on-another-button)
- [How do I let the parts add their usual buttons by themselves?](#how-do-i-let-the-parts-add-their-usual-buttons-by-themselves)
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
- [How do I run a claw on two servos?](#how-do-i-run-a-claw-on-two-servos)
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
- [How do I find game pieces with a HuskyLens?](#how-do-i-find-game-pieces-with-a-huskylens)
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
- [How do I run an action when the OpMode starts?](#how-do-i-run-an-action-when-the-opmode-starts)
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
- [How do I change a distance in an autonomous?](#how-do-i-change-a-distance-in-an-autonomous)
- [How do I share the same first steps in several autos?](#how-do-i-share-the-same-first-steps-in-several-autos)
- [How do I make the start delay shorter, or use a different number on RED?](#how-do-i-make-the-start-delay-shorter-or-use-a-different-number-on-red)

**Several autos and choosing one** (more: [Building autos 3: having multiple autos](building-autos/03-multiple-autos.md))
- [How do I build several autos from the same pieces?](#how-do-i-build-several-autos-from-the-same-pieces)
- [How do I make two autos that differ by one piece?](#how-do-i-make-two-autos-that-differ-by-one-piece)
- [How do I let the drivers choose the start spot and the plan in INIT?](#how-do-i-let-the-drivers-choose-the-start-spot-and-the-plan-in-init)

**Odometry and driveTo** (where is the robot on the field?)
- [How do I add a goBILDA Pinpoint?](#how-do-i-add-a-gobilda-pinpoint)
- [How do I add a SparkFun OTOS?](#how-do-i-add-a-sparkfun-otos)
- [How do I add dead wheels?](#how-do-i-add-dead-wheels)
- [How do I tune the odometry?](#how-do-i-tune-the-odometry)
- [How do I drive to a point on the field?](#how-do-i-drive-to-a-point-on-the-field)
- [How do I get around field elements on the way to a spot?](#how-do-i-get-around-field-elements-on-the-way-to-a-spot)
- [How do I show where the robot is?](#how-do-i-show-where-the-robot-is)
- [How do I use the robot's position in an action?](#how-do-i-use-the-robots-position-in-an-action)
- [How do I set the pose when autonomous did not run?](#how-do-i-set-the-pose-when-autonomous-did-not-run)

**Pedro paths** (smooth curves, mecanum only)
- [How do I add Pedro Pathing to Robot.java?](#how-do-i-add-pedro-pathing-to-robotjava)
- [How do I make a straight path and a curve?](#how-do-i-make-a-straight-path-and-a-curve)
- [How do I follow a path in an autonomous?](#how-do-i-follow-a-path-in-an-autonomous)
- [How do I drive two paths with no stop, slow down, or hold at the end?](#how-do-i-drive-two-paths-with-no-stop-slow-down-or-hold-at-the-end)
- [How do I keep facing the goal along a path?](#how-do-i-keep-facing-the-goal-along-a-path)
- [How do I drive first and turn at the end of a path (or turn first)?](#how-do-i-drive-first-and-turn-at-the-end-of-a-path-or-turn-first)
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
| a `repeat while opModeIsActive` loop | nothing: the library runs the loop for you, many times a second | - |
| drive forward, turn (in autonomous) | `.move("Forward", 24, 0, 0.5)`, `.turn("Left", 90, 0.4)` ([moves](#how-do-i-drive-forward-sideways-and-turn)) | a file in `autos/` |
| `if` a sensor or part says something, then ... | `.waitUntil(...)`: wait until it is true, then go on ([wait until](#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| `if` the drivers chose something before the match | `onlyIf(...)` with a menu choice ([choices](#how-do-i-let-the-drivers-choose-before-the-match)) | an autonomous |
| `repeat 3 times` | a `for` loop that adds the steps 3 times to an action ([repeat](#how-do-i-repeat-steps-several-times)) | `Actions.java` |
| set servo position | a named position: `robot.claw.open()`, `robot.intakeDeploy.goTo("deployed")` ([servo positions](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)) | `Controls.java`, `Actions.java` |
| `waitForStart` | nothing: the library waits for ▶ (start on the Driver Hub). For something right after ▶: `atStart(...)` ([at ▶](#how-do-i-run-an-action-when-the-opmode-starts)) | `Robot.java` |
| blocks before `waitForStart`, or after the loop | `atInit(...)`, `duringInit(...)`, `atStop(...)` ([your own code](guides/your-own-code.md)) | `Robot.java` |
| `if gamepad2.y then ...` inside the loop | one line, once: `g2.y.press(...)` ([buttons](#how-do-i-make-a-button-do-something-once)) | `Controls.java` |
| `if` the button is held | `g1.a.hold(...)` ([hold](#how-do-i-run-something-only-while-a-button-is-held)) | `Controls.java` |
| `sleep 500` | `.waitSeconds(0.5)` ([wait](#how-do-i-wait-half-a-second-like-sleep-in-blocks)) | `Actions.java` or an autonomous |
| a loop that waits until something is true | `.waitUntil(...)` ([wait until](#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| set motor direction REVERSE | drive motors: `.reversed("front_right", ...)` ([drive](#a-wheel-turns-the-wrong-way-how-do-i-fix-it)); a lift: `.reversed()` ([lift](#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)) | `Robot.java` |
| set motor power | a command of the part: `robot.intake.in()`, `robot.lift.goTo("high")` ([parts](#how-do-i-send-a-part-to-a-position-from-a-button)) | `Controls.java`, `Actions.java` |
| a variable for a number (a speed, a position) | a number in `Tunables.java` ([numbers](#where-do-the-numbers-go)) | `Tunables.java` |
| `telemetry.addData` | `show("Label", ...)` ([show](#how-do-i-show-a-value-on-the-driver-station-screen)) | `Controls.java` |
| drive blocks (sticks to motor powers) | nothing: driving is built in ([driving](#driving-that-is-already-built-in)) | - |

**Why there is no loop:** in Blocks, your code ran again and again in the loop. Here `buttons(...)` runs
**once**, at INIT, and each line plugs a job into a button. Then the library's loop runs the job each time
the driver presses the button. So you never write `if`, `while` or `sleep` for buttons. More:
[From Blocks to Java](blocks-to-java.md).

## Where does each line go? The files, whole

Each recipe gives a few lines. Put them INSIDE one of these files, at the spot the comment shows. Keep
every other line of the file. Your files also start with a `package` line and some `import` lines. They
are not shown here, and they stay at the very top, above `public class`. An import tells Java where a word
like `Lift` comes from. When Android Studio shows a word in red, click it and press **Alt+Enter** (Mac:
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

1. `.part(...)` says which motor or servo it is. It is optional, but we recommend it: with it, the library takes better starting values from the part's numbers, and the simulator moves like the real part. The ids here are examples. Write the ids of your parts, from the [parts catalog](guides/parts-catalog.md). An arm, a turret and a launcher need the motor's id, and take it in their brackets instead. Every recipe below has the same `.part(...)` comment, with this meaning.

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
new part, it shows the part's whole section (`public static class Lift { ... }`). Put it inside
`Tunables`, next to the other sections. If your file **already has** a section with that name, do not add
a second one, because two sections with one name do not compile. Copy only the lines inside it into
yours, and change the lines you already have. A recipe that adds to a section you already have says so
("In your `Tunables.Launcher` section") and shows only the lines.

**How a recipe's section reads:** one number per line, with a short comment.
- `MEASURE`: a number that only your robot can give (a lift's top, a servo's positions). INIT stops and
  names the tool that finds it. Write the number in its place.
- `DEFAULT`: the library's starting value, shown in brackets in the comment. Change it
  only when you see the problem it fixes ([Find a setting by what you want](reference/tunables.md#find-a-setting-by-what-you-want)).
- A real number: the point of the recipe (a slower robot, a spot on the field).
- These words need one line at the top of `Tunables.java`: `import static com.crossbotics.ftchelper.robot.Starting.*;`

**Names:** the name in quotes in `Robot.java` (`Lift.slide("lift")`) is exactly the device's name in the
robot configuration on the Driver Station. The section's name is that name in CamelCase (each word starts
with a capital letter, no spaces or `_`): `"lift"` reads `Lift`, `"intake_deploy"` reads `IntakeDeploy`.

## Words you will meet

| Word | Means |
| --- | --- |
| `Robot.java` | the file that says WHAT is on the robot: the drive and one line per part, inside `define()` |
| `Tunables.java` | the file with every number (speeds, positions), one section per part |
| `Controls.java` | the file that says which button does what, inside `buttons(...)`. It is also your TeleOp |
| `Actions.java` | moves that use several steps or parts (score, grab) |
| `autos/` | the folder with your autonomous programs, one file each |
| configuration name | the name of a motor or servo you typed on the Driver Station (`"lift"`). The code must use the same name |
| `robot.lift::position` | `::` hands over a method without running it. The library runs it later (when the driver presses the button) |
| `() -> robot.lift.goTo("high")` | a **lambda**: a small piece of code the library runs later. Use `::` when the job is one method with nothing in its brackets (`robot.claw::open`); use `() ->` when the brackets hold something (`goTo("high")`) or for several commands |
| `g1`, `g2` | gamepad 1 (the driver) and gamepad 2 (the operator) |
| operator | the second driver, on gamepad 2: runs the mechanisms while the driver drives |
| GUIDE | the center button of the gamepad. **Tap:** the button map (what each button does). **Hold 1 s** (PRACTICE profile): the tuning menu (change numbers while the robot runs) |
| "every loop" | the library's loop runs many times a second: it reads the gamepads, runs the jobs and updates every part. You never write it |
| power | from 0 (stopped) to 1 (full). A negative power runs the other way |
| `robot`, `g1`, `g2`, `s` | names the library hands you: `buttons(Robot robot, Pad g1, Pad g2)` and `steps(Steps s, Robot robot)` (see [the files, whole](#where-does-each-line-go-the-files-whole)) |
| `;` | ends a line of Java. A chain of steps (`.move(...)` then `.turn(...)`) is ONE line split over several lines: only its last line has `;` |
| `"lift"` (in quotes) | a name. Capitals and spelling must match exactly |
| class | one Java file's main block: `public class Robot ... { ... }`. "Inside the class" = between its `{` and `}` |
| method | a named piece of code, like `define()` or `scoreHigh(...)`. Its lines go between its `{` and `}` |
| `@TeleOp(...)`, `@Autonomous(...)` | puts the program in the Driver Station's list, with this name |
| `@Disabled` | hides the program from the list. The cookbook's programs have it. Delete it in your copy |
| `@Override`, `public`, `static`, `protected` | Java words the file needs. Copy them as they are |
| INIT, ▶ | the Driver Station's two buttons: INIT gets ready (the library moves nothing then), and ▶ (start) runs the program |
| PRACTICE, COMPETITION | the two profiles (ways to run): PRACTICE is slower and has the tuning menu |
| ticks (counts) | how far a motor has turned, from its encoder (a sensor in the motor) |
| `kP`, `kI`, `kD`, `kF` | the PID numbers: how hard a lift pushes toward its target. Start with the defaults |
| catalog id (`"gobilda-5203-312"`) | a motor's or servo's name in the [parts catalog](guides/parts-catalog.md): 5203 is goBILDA's motor series, 312 its RPM (on the motor's label) |
| `.part("gobilda-5203-435")` | which motor or servo a part uses. Optional, but recommended: with it, the library takes better starting values from the part's numbers, and the simulator moves like the real part. An arm, a turret and a launcher need the id, and take it in their brackets instead |
| tuning menu | a menu where you change numbers while the robot runs. The INIT choice menu of an autonomous is a different menu |
| terminal | a window where you type commands. In Android Studio: the **Terminal** tab at the bottom ([how](setup/09-the-terminal.md)) |

**One rule for every recipe:** a job never waits. A `sleep(...)` or a `while` loop in a button job or an
action freezes the whole robot, so never write one there. To wait, see
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
**More:** [Lesson 2: Drive](tutorials/start/02-drive.md), [Drive](reference/parts/drive.md).

### How do I set up a tank (standard) drive?

**Where:** `Robot.java`, inside `define()` (in place of the mecanum line).

<!-- recipe: drive-tank -->
```java title="Robot.java"
drive(Drive.tank("left_drive", "right_drive")
        .motor("gobilda-5203-312").wheelMm(96)
        .reversed("right_drive")                     // only the motors that run backward
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the motors on the two sides face opposite ways, so one side runs backward. List that side's
motors in `.reversed(...)`. With two motors per side, name all four: `Drive.tank("front_left", "front_right", "back_left", "back_right")`.
**More:** [Lesson 2](tutorials/start/02-drive.md#1-pick-the-drive-type-and-name-the-motors), [Drive](reference/parts/drive.md).

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

**Why:** the motors on the two sides face opposite ways. So one side must run reversed, or the robot does not go straight.
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

**Why:** the IMU (the hub's turn sensor) must know which way is up. If it does not, turns and field-centric driving go wrong.
**More:** [Lesson 2: the IMU](tutorials/start/02-drive.md#4-say-how-the-control-hub-is-mounted-the-imu).

### How do I make the robot slower (or faster)?

**Where:** `Tunables.java`, section `Drive`.

<!-- recipe: tunables-drive -->
```java title="Tunables.java"
public static class Drive {                                     // a fixed name: always Drive
    public static double maxPower = 0.6;                        // top wheel power: slow for first drives (1.0)
    public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (same as maxPower)
    public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
    public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
    public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
    public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
    public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
    public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
}
```

**Why:** `maxPower` is the top power in both profiles, so the drivers practice at match speed. Lower it
for the first drives, then set it back. To set a lower top power for PRACTICE only, use
`practiceMaxPower`, for example on a new driver's first day.
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

**Where:** `Controls.java`, inside `buttons(...)`. This is for one driver. With two drivers, LT on gamepad 1 is already slow mode.

<!-- recipe: slow-trigger -->
```java title="Controls.java"
// One driver: slow on LT. The further it is pulled, the slower (down to slowSpeed).
g1.lt.slow().says("slow");
```

**Why:** a trigger gives fine control: a small squeeze makes the robot a little slower.
**More:** [ControlMap: one driver](reference/controls.md#controlmap).

### Driving that is already built in

You write no code for these. With `Controls.java` (two drivers):

- **Drive:** the gamepad 1 left stick drives (and strafes), and right stick X turns. LT gives slow mode: the further you pull it, the slower the robot.
- **Field-centric** (mecanum): double-tap gamepad 1 **Y** to switch it on and off. Then "forward" is away from the drivers, whichever way the robot faces.
- **Pause:** tap gamepad 1 **BACK**, and everything stops on brake. Tap again to go on.
- **Reset "forward":** turn the robot to face away from you, then hold gamepad 1 **BACK** for 1 second.

**More:** [Lesson 2: the driving you get for free](tutorials/start/02-drive.md#6-the-driving-you-get-for-free), [the standard map](reference/controls.md#controlmap).

## Buttons and gestures

All button recipes go in `Controls.java`, inside `buttons(...)`. That method runs once, at INIT, and each
line plugs a job into a button. Every line ends with `;`. `.says("...")` is the text on the button map.

**Pick a free button.** Each part's usual buttons are lines in `Controls.java` too (see each part's
"buttons to add" in the [reference](reference/robot-java.md)). For example, the claw is on operator **B**,
and the lift is on **A**, **X** and **Y**. When two jobs are on one button, INIT stops and names the
button. INIT also lists the free buttons
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

**Why:** the second job stops the intake when you let go. Without it, the intake keeps running.
**More:** [The button menu](guides/buttons.md#one-job-on-the-button).

### How do I switch something on and off with one button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-toggle -->
```java title="Controls.java"
// Each press switches the intake on or off. rumble(): 2 buzzes = on, 1 buzz = off.
g1.x.toggle(robot.intake).rumble().says("intake on/off");
```

**Why:** `toggle` remembers on or off for you. The buzz tells the driver which one it is, so the driver does not have to look.
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

**Why:** a quick bump of the button does nothing, so nobody starts a big job by accident.
**More:** [The button menu: gestures](guides/buttons.md#several-gestures-on-one-button).

### How do I use two buttons together (like a shift key)?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: button-pair -->
```java title="Controls.java"
// Hold RB (the "shift key"), then press D-pad up: for a job that must not run by accident.
g1.rb.with(g1.dpadUp, () -> robot.lift.goTo("high")).says("lift to high");
```

**Why:** a pair is a safe place for a big job that you use only once or twice a match, like a climb. D-pad up alone still does its own job.
**More:** [The button menu: two buttons together](guides/buttons.md#two-buttons-together).

### How do I make the gamepad buzz?

**Where:** `Controls.java`, inside `buttons(...)`.

Add `.rumble()` after a job (see [the toggle recipe](#how-do-i-switch-something-on-and-off-with-one-button)).
Or make the gamepad buzz when the robot gets ready:

<!-- recipe: rumble-when -->
```java title="Controls.java"
// A buzz on gamepad 2 each time the flywheel gets to its speed.
g2.rumbleWhen(robot.launcher::isReady);
```

**Why:** the drivers watch the robot, not the Driver Station. A buzz tells them "ready", so they do not have to look away.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback).

### How do I control a part with a stick?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: stick-use -->
```java title="Controls.java"
// A stick is a number from -1 to 1 (up and right are +). Its value goes to the job every loop.
g2.rightStickY.use(robot.lift::manual).says("lift by hand");
```

**Why:** a stick gives a number every loop, so the part moves as far as you push the stick. When you let go, the part holds.
**More:** [The button menu: sticks](guides/buttons.md#sticks).

### How do I move a part's button to another button?

**Where:** `Controls.java`, inside `buttons(...)`.

A part's buttons are lines in `Controls.java`. For example, the intake's usual buttons are operator RT and
LT. To move them, change the button in their lines:

<!-- recipe: move-a-button -->
```java title="Controls.java"
// The intake's usual lines are on RT and LT: here the same jobs are on the bumpers.
g2.lb.hold(robot.intake::holdIn, robot.intake::stop).says("intake in (hold)");
g2.rb.hold(robot.intake::out, robot.intake::stop).says("intake out (hold)");
```

**Why:** a part's buttons are your own lines. Put its jobs on the buttons you want, and leave its usual buttons free.
**More:** [Lesson 3: the buttons the parts bring](tutorials/start/03-buttons.md#2-the-parts-buttons-written-out).

### How do I put the launcher's fire button on another button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: move-launcher-buttons -->
```java title="Controls.java"
robot.launcher.fireOn(g2.y);   // all of RB's firing moves to Y
robot.launcher.spinOn(g2.x);   // LB's spin up / down moves to X
```

**Why:** the fire button does more than one job. A tap fires one piece, quick taps line up more shots, a
hold keeps firing, and a tap then a hold sprays. `fireOn` moves all of these jobs in one line, and RB is
then free for something else. `fireOn(null)` leaves no fire button at all.
**More:** [Firing](reference/parts/launcher.md#firing-shot-modes-feed-stop-piece-path).

### How do I let the parts add their usual buttons by themselves?

**Where:** `Controls.java`, inside the class, as a method of its own: below the `}` that ends
`buttons(...)`, above the class's last `}`.

<!-- recipe: parts-add-their-buttons -->
```java title="Controls.java"
// true: each part adds its usual buttons by itself. A line in buttons(...) on one of them replaces it.
@Override
protected boolean useStandardButtons() {
    return true;
}
```

**Why:** code written before v0.39.0 needs it. In that code, each part put its usual buttons on the
gamepads with no line in `Controls.java`. Since v0.39.0, a part has no buttons until you write its lines
(see each part's "buttons to add" in the [reference](reference/robot-java.md)). So you can read every
button as a line.
**More:** [ControlMap](reference/controls.md#controlmap).

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

**Why:** the old button is then free for your own jobs in `buttons(...)`. When two library jobs are on
one button, INIT stops and names the button. A file has only one `libraryButtons(...)` method, so put
every `lib.` line in it.
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

**Why:** some drivers tap slower. These times are in the tuning menu too. When you tune a value there,
copy it back into `Tunables.java`, because the file is the record.
**More:** [The button menu](guides/buttons.md#several-gestures-on-one-button).

### How do I switch the alliance during TeleOp?

The drivers choose the alliance in INIT (gamepad 1: X = BLUE, B = RED). When they picked the wrong one
and the match has started, this switch fixes it. Turn it on first. It is off by default, so nobody
switches by accident.

**Where:** `Controls.java`, inside the class, as a method of its own (below the `}` that ends
`buttons(...)`). If you already have a `libraryButtons(...)` method, add only the `lib.switchAlliance` line to it.

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

**Why:** the alliance sets the goal that the camera and the turret aim at, and the light's color. With
the wrong alliance, the robot aims at the other alliance's goal all match. Press START first, then BACK.
BACK alone is the pause, so BACK first only pauses the robot. The 1.5 s hold means a bump does nothing.
**More:** [The button menu: moving the library's buttons](guides/buttons.md#moving-the-librarys-buttons).

### Buttons that are already built in

- **The button map:** INIT shows every button and what it does. During the match, tap **GUIDE**.
- **The tuning menu:** hold **GUIDE** for 1 second (PRACTICE profile). A tap shows the map, a hold opens the menu.
- **Two jobs on one button:** INIT stops and names the button, so you find the mistake before the match.
- **Pause:** press gamepad 1 **BACK** (the small button left of the center), and everything stops at once.
  Tap it again to go on.
- **Alliance:** choose it in INIT (gamepad 1: X = BLUE, B = RED). Switching during TeleOp is
  optional. [Turn it on](#how-do-i-switch-the-alliance-during-teleop). Then, on gamepad 1, hold the small
  **START** button, then press and hold **BACK** too, for 1.5 s. If you let go sooner, the alliance stays the
  same and the debug lines turn on or off.
- **Set pose here** (robots with odometry): put the robot on the reset spot, let go of the sticks, and hold gamepad 1 **START** for 1 s. This works in INIT or in the match ([below](#how-do-i-set-the-pose-when-autonomous-did-not-run)).

**More:** [Lesson 3: the button map](tutorials/start/03-buttons.md#6-the-button-map-init-and-guide).

## Mechanisms

Each part is one line in `Robot.java`'s `define()`. It also needs a field at the top of the class (for
example `public Lift lift;`) and a section with its numbers in `Tunables.java`. The section's name is
the configuration name in CamelCase: `intake_deploy` reads `IntakeDeploy`. For its buttons, copy its lines into `Controls.java` (see "buttons to add" in the [reference](reference/robot-java.md)).

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

**Why:** the intake reads its numbers from `Tunables.Intake`. Its buttons are lines in `Controls.java`: [Intake: buttons to add](reference/parts/intake.md#buttons-to-add).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Intake](reference/parts/intake.md).

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

1. Also add the field at the top of the class: `public Intake intake;`. The two servos face each other, so one must turn the other way. `.reversed(...)` names that servo.

**Why:** two servos that face each other must turn opposite ways to pull a piece in. The numbers are in `Tunables.Intake` too (`inPower`, `outPower`).
**More:** [Intake](reference/parts/intake.md).

### How do I make the intake run from the start?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-on-at-start -->
```java title="Robot.java"
// Runs in from ▶ until stopped. Its button (operator RT) then switches it on/off.
// .part(...): which motor it is (optional, recommended).
add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());
```

**Why:** many robots collect pieces all match. With this line, the driver does not have to switch the intake on.
**More:** [Intake](reference/parts/intake.md).

### How do I add a lift with set heights?

**Where:** `Robot.java` (the part) and `Tunables.java` (its numbers).

<!-- recipe: lift -->
```java title="Robot.java"
// At the top of the class: public Lift lift;   Here in define():
// "lift", "lift_bottom" = the motor's and the bottom switch's names in the robot configuration. (1)!
// .part(...): which motor it is (optional, recommended).
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

1. Each time the lift presses the bottom switch, the switch sets the lift's 0. With no switch, delete `.bottomSwitch(...)`.

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

**Why:** the lift never goes past `min` and `max`, so it cannot break itself. INIT stops if they are missing.
`MEASURE` stops INIT until you write the real number. Find yours with the [Motor Finder](#how-do-i-find-a-lifts-limits-and-heights).
`"lift"` must be the motor's name in your configuration. To send the lift to a height, see [from a button](#how-do-i-send-a-part-to-a-position-from-a-button).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#2-their-numbers-in-tunablesjava), [Lift](reference/parts/lift.md),
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
2. When the seller lists 4.8 V and 6 V, use the 4.8 V speed, because the hub gives a servo about 5 V.
3. `Double.NaN` means "I don't know this one". The part then uses general defaults, and INIT says so. A launcher or an arm needs at least the counts per turn: `Motors.custom("...", 28, Double.NaN)`.

**Why:** the library works out a part's defaults (a launcher's gains and speed, a lift's kP, a servo's
move time) from its motor's or servo's numbers. When you describe yours with the seller's numbers, it
works like a catalog part. INIT says `defaults from my-flywheel-motor (custom)`, and `:sim:fromRobot`
writes its numbers into the simulator's robot file. For a number you do not know, write `Double.NaN`
("I don't know this one"). Or write the whole part as `Motors.unknown` / `Parts.unknownServo`. The part
then uses general defaults, and INIT says so. Do not use a catalog motor that is "close": its numbers
would be wrong, and nothing would warn you.
**More:** [Parts not in the catalog](reference/robot-java.md#parts-not-in-the-catalog), [Parts catalog](guides/parts-catalog.md).

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
2. Both motors always get the same power, also when the lift stops or pauses. So one encoder is enough.
3. When both motors turn the same way for "up", use `.alsoMotor("lift_right")` instead.

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
loop. It does this also when the lift stops or pauses (BACK), or when an interlock rule (a rule that stops
two parts from hitting each other) holds the lift. Your own code that copies the power every loop would
miss pause and STOP. **Check the directions before the chain goes on.** With the chain or belt off, each
motor must turn the way that lifts. If one is wrong, swap `alsoMotorReversed` and `alsoMotor`. Two motors
that push against each other get hot and can break a gearbox. An arm works the same way:
`Arm.motor("arm_left", "gobilda-5203-117").alsoMotorReversed("arm_right")`. The simulator ties both
motors to one slide (`:sim:fromRobot` writes the second motor's line).
**More:** [Two motors on one lift or arm](reference/parts/lift.md#two-motors-on-one-lift-or-arm), [Tunables names](reference/parts/lift.md#in-tunablesjava).

### How do I add an arm that moves in degrees?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: arm -->
```java title="Robot.java"
// "arm" = the motor's name in the robot configuration. Its numbers: Tunables.Arm.
// "gobilda-5203-117" = the motor's catalog id (needed: it works in degrees). (1)!
// gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

1. The arm needs the motor's counts per turn to work in degrees. So, as for a turret or a launcher, the arm's motor id is required.

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

**Why:** degrees are easier to think about than encoder counts. Put the arm at its 0 before you press INIT.
**More:** [Arm](reference/parts/arm.md).

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

**Why:** a claw needs two numbers, open and closed, and the Servo Finder gives them. Its button is a line in `Controls.java`: [Claw: buttons to add](reference/parts/claw.md#buttons-to-add).
**More:** [Lesson 4](tutorials/start/04-lift-and-claw.md#1-three-lines-in-robotjava), [Claw](reference/parts/claw.md).

### How do I run a claw on two servos?

**Where:** `Robot.java`, inside `define()`, and `Tunables.java`.

<!-- recipe: claw-two-servos -->
```java title="Robot.java"
// "claw_left", "claw_right" = the servos' names in the robot configuration. Numbers: Tunables.ClawLeft.
// Find open and closed in Tune and check > Servo Finder on the FIRST servo: the second moves with it.
// The servos face each other, so the second turns the other way: alsoServoReversed.
claw = add(Claw.servo("claw_left").part("gobilda-2000-torque").alsoServoReversed("claw_right"));
```

<!-- recipe: tunables-claw-two-servos -->
```java title="Tunables.java"
// A claw on two servos, Claw.servo("claw_left").alsoServoReversed("claw_right"): its section is the
// FIRST servo's name in CamelCase.
public static class ClawLeft {                                  // "claw_left" in the configuration
    public static double open = MEASURE;                        // servo position, open (Servo Finder on claw_left)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
    public static double alsoTrim = DEFAULT;                    // the second servo: raw position added so the two meet (0; used at the next move)
}
```

**Why:** two servos on one part must never fight. The library sends both the same position at the same
moment, also when the robot pauses (BACK) or stops. Your own code that copies the position would miss
some of these moments. **Reversed or not:** servos that face each other (mirror images, the usual way)
turn opposite ways, so use `alsoServoReversed`. When both face the same way, use `.alsoServo("claw_right")`.
**When the fingers do not quite meet:** the horns may sit a spline tooth apart. Set `alsoTrim` (a small raw
position, like 0.02) for the second servo. The same line works on a Wrist, a Positioner and a servo feeder:
`Indexer.servo("feeder_left").alsoServoReversed("feeder_right")` (two arms that pinch a piece into a launcher).
**More:** [Two servos on one part](reference/parts/claw.md), [Tunables names](reference/parts/claw.md#in-tunablesjava).

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

**Why:** a wrist steps through its named positions. Its buttons are lines in `Controls.java`: [Wrist: buttons to add](reference/parts/wrist.md#buttons-to-add).
**More:** [Wrist](reference/parts/wrist.md).

### How do I add any servo with named positions (a gate, a fold-out)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: positioner -->
```java title="Robot.java"
// Any servo with named positions: a gate, a kicker, a fold-out intake.
// "intake_deploy" = the servo's name in the robot configuration. Numbers: Tunables.IntakeDeploy.
// .part(...): which servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque"));
// Right after ▶ (autonomous and TeleOp): fold the intake out.
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

**Why:** a `Positioner` has no usual buttons, so you choose when it moves. Here it moves at ▶.
**More:** [Positioner](reference/parts/positioner.md), [an action at ▶](#how-do-i-run-an-action-when-the-opmode-starts).

### How do I get my servo's full range?

**Where:** `Robot.java`, inside `define()`: add `.fullRange()` to the servo part.

<!-- recipe: servo-full-range -->
```java title="Robot.java"
// fullRange(): positions 0 to 1 = the servo's whole turn (300 degrees on a goBILDA 2000). (1)!
// Turn it on FIRST, then find the positions (Servo Finder: press Y while choosing the servo).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());
```

1. For positions 0 to 1, the hub normally sends 600-2400 us (microseconds). With that signal, a goBILDA 2000 servo turns only 270 of its 300 degrees. `fullRange()` sends the whole 500-2500 us.

**Why:** the FTC SDK's normal servo signal (600 to 2400 microseconds) is narrower than what most servos
take (500 to 2500). So the servos turn less than the seller says: 270 degrees for a goBILDA 2000, 243 for
a REV Smart Robot Servo. Add it only when you need the extra turn. It works the same on a Claw, a
Positioner, `Hood.servo`, `Turret.servo` and `Indexer.servo`. After the change, the positions you measured
before point somewhere else, so measure them again. Without `.fullRange()`, nothing changes.
**More:** [Full range](modules/hardware.md#full-range) (a table per servo), [Claw, Wrist, Positioner](reference/parts/claw.md).

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

**Why:** `goTo("name")` uses the position by its name, so the number is only in `Tunables.java`.
These lines use Y, A, X and B. The lift's and the claw's usual lines use the same buttons, so write one
or the other. See [pick a free button](#buttons-and-gestures).
**More:** [Lift](reference/parts/lift.md), [Claw](reference/parts/claw.md).

### My robot doesn't have this part yet. How do I keep its code?

You change three places: the part's line in `Robot.java`, the action in `Actions.java`, and its button in
`Controls.java`.

**`Robot.java`**, inside `define()`: add `.optional()` to the part's `add` line. Here the part is a deploy
servo that folds the intake out. The `atStart` line runs the action below at ▶.

<!-- recipe: optional-part -->
```java title="Robot.java"
// optional(): while "intake_deploy" is not in the robot configuration, the part is off (INIT goes on). (1)!
// .part(...): which motor or servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque").optional());
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
// At ▶: fold the intake out, then run it (the action is in Actions.java, below). (2)!
atStart(() -> Actions.deployIntake(this));
```

1. The deploy servo is not on the robot yet. INIT shows a note and goes on (see **Why**, below).
2. With no deploy servo, the action skips the servo and the wait for it, and only starts the intake.

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

**Why:** teams build robots in steps. Without `optional()`, INIT stops when the part's device is not in
the configuration (`No Servo named "intake_deploy" ...`). With it, INIT shows a note and goes on:
`"intake_deploy" not in the configuration: IntakeDeploy is off (optional part)`. In the COMPETITION
profile, the note is a warning at the top of the screen (`!! COMPETITION: ... IntakeDeploy is OFF ...`).
So nobody plays a match without a part by mistake. Everything that uses the part skips it:
- its commands
- its buttons (the button map says `G2 X: deploy intake - off: IntakeDeploy not on the robot`)
- the action steps (an autonomous keeps going, and its report says `skipped IntakeDeploy (not on the robot)`)

When you wire the servo and add it to the configuration, it works, with no code to change. You need no
`if (HAS_DEPLOY_SERVO)` switches. To try it in the simulator without the part, write `missing = intake_deploy`
in the robot file.
**More:** [Parts not on the robot yet](reference/robot-java.md#parts-not-on-the-robot-yet).

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

**Why:** the launcher feeds a piece only when the wheel is at speed, so every shot flies the same way.
**More:** [Lesson 9](tutorials/start/09-shooter-and-camera.md#1-a-flywheel-and-its-feeder), [Launcher](reference/parts/launcher.md).

### How do I add a turret that follows the goal?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: turret-follows-camera -->
```java title="Robot.java"
// The camera knows the goal of each alliance (BLUE first, then RED).
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
// "turret" = the motor's name in the robot configuration. 5 motor turns per turret turn.
// follows(camera): it follows the goal from ▶, but never past Tunables.Turret min and max.
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
**More:** [Lesson 9: the turret](tutorials/start/09-shooter-and-camera.md#6-a-turret-that-follows-it), [Turret](reference/parts/turret.md), [Camera](reference/parts/camera.md).

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
turret that follows the goal, `isAimed()` means on the goal NOW. When the camera loses the goal, it is false.
**More:** [Lesson 9](tutorials/start/09-shooter-and-camera.md#7-speed-from-distance-and-fire-only-when-aimed).

## Game pieces

A `Pieces` line in `Robot.java` counts the pieces the robot holds, and knows which kind fires next. It
uses a sensor at the intake, and (optional) one at the "ready to fire" slot. Measure each kind's color
(or size) with the Piece Tuner, and write it in `Tunables.java`. The full details: [Pieces and PieceSensor](reference/parts/pieces.md).

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

1. Put it above the intake line. Then, in each loop, the count is up to date before the intake asks "full?".
2. BIOBUZZ has yellow POLLEN, and red and blue NECTAR. Then come the most pieces the rules allow (`max`) and the preload (`startsWith`).

**Why:** each piece the intake sensor sees counts one in. Each feed of the indexer counts one out, the
oldest first, once the piece has gone. A jammed feed counts nothing. Until you tune a kind with the
Piece Tuner, it reads `"UNKNOWN"`. `robot.pieces.count()`, `robot.pieces.next()` ("POLLEN", "UNKNOWN" or "NONE") and
`robot.pieces.isFull()` work anywhere in your code.
**More:** [Pieces and PieceSensor](reference/parts/pieces.md).

### How do I stop the intake when the robot is full?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: intake-stops-when-full -->
```java title="Robot.java"
// ADD .stopsWhen(...) to your intake line (no second intake line); the Pieces line goes above it. (1)!
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150").stopsWhen(() -> pieces.isFull()));
```

1. The intake then does not pull in a 5th piece. `() ->` means that the intake asks the test in every loop. Pushing out still works.

<!-- recipe: launcher-needs-a-piece -->
```java title="Robot.java"
// Each feed counts one piece out. requireReady: no feed with nothing to fire.
// .part(...): the indexer's servo (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .requireReady("a piece to fire", () -> !pieces.isEmpty()));
```

**Why:** BIOBUZZ rule G407 says that a robot may not control more than 4 pieces. The rules ask for
"systems to prevent active pickup" of a 5th, and this is one. Put the intake sensor where the robot
already holds a piece, so the intake does not stop with a piece half in. The count can be wrong (two
pieces can read as one). So the drivers are responsible for G407, not the count. When the count says
full and the robot is not, hold RT for 1 s to pull in anyway. The Driver Station warns when the intake
is held back for more than 2 s.
**More:** [Intake](reference/parts/intake.md).

### How do I tell pieces apart by size, or with a second camera?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: pieces-other-sensors -->
```java title="Robot.java"
// A REV 2m Distance Sensor across the intake tells the sizes apart (not the colors). (1)!
// A second webcam on the ready slot: colors. (2)!
pieces = add(Pieces.kinds("POLLEN", "NECTAR")
        .comingIn(PieceSensor.distance("intake_distance"))
        .readyToFire(PieceSensor.camera("Webcam 2"))
        .max(4));
```

1. A NECTAR (3.6 in) comes closer to the sensor than a POLLEN (2.8 in). It sees size only, so it cannot tell a red NECTAR from a blue one.
2. The webcam reads the main color in a box of its picture.

**Why:** a distance sensor reads 20 mm closer with a NECTAR than with a POLLEN, but it sees no color.
Rule G408 forbids controlling the other alliance's NECTAR. A webcam sees colors from farther away, but
it cannot tell how far the piece is.
**More:** [the sensors compared](reference/parts/pieces.md).

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

**Why:** `whenFull()`, `whenEmpty()` and `whenNextIs("POLLEN")` (one kind) are conditions that any part
can watch: a buzz, a light (the [Lights](#lights) recipes), or an action that waits. For "either NECTAR",
the recipe asks `next()` (the next kind's name) whether it ends with `NECTAR`. Pieces brings a button
too. When the count is wrong (for example, after you take pieces out by hand), hold G2's left stick
button for 1 s to set the count to 0.
**More:** [hooks for other parts](reference/parts/pieces.md).

### How do I let the next piece pick the shot table?

**Where:** `Robot.java`, inside `define()`, on your launcher line (the Pieces line must be above it). Also
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
        .tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR"));    // both NECTAR colors use "nectar"
```

**Why:** POLLEN and NECTAR fly differently. With these lines, the drivers never switch tables by hand.
Each time the next piece changes, the launcher uses that piece's table. A kind with the same name as a
table needs no line (`POLLEN` uses `pollen`). `tableFor` puts both NECTAR colors on the `nectar` table.
`NONE` and `UNKNOWN` (a kind not tuned yet) never change the table. `startTable` is the table before the
robot knows the first piece. A button (D-pad right) can still pick another table until the next piece
changes.
**More:** [Launcher: shot tables](reference/parts/launcher.md), [Lesson 9: the next piece picks the table](tutorials/start/09-shooter-and-camera.md#12-extra-know-which-piece-fires-next).

### How do I tune the piece sensors?

**Where:** a new file next to `Robot.java`, `PieceTuner.java`. Below is the whole file. Its first line is
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

**Why:** each sensor and each robot reads colors a little differently. Hold each kind in front of the
sensor and press A. Then copy the lines that the tuner shows into `Tunables.java`.
**More:** [Piece Tuner](modules/finders.md#piecetuneropmode).
## Aiming and shooting

The robot does these jobs for the drivers, so they can focus on driving and firing. They all use one
idea: **the camera first, the odometry as a backup** ([Aim](reference/parts/aim.md)). Odometry is how
the robot tracks its own position on the field.

### How do I make the whole robot aim at the goal (no turret)?

**Where:** `Controls.java`, inside `buttons(...)` (and `Tunables.java` for the numbers, below). The
robot needs a camera with a [`goal(...)`](tutorials/start/09-shooter-and-camera.md#5-a-camera-that-knows-the-goal)
(or odometry with a goal point). Do not use it with a turret that follows the goal. Turret-following and
whole-robot aim are separate modes, so INIT stops with a message if both are there.

<!-- recipe: aim-whole-robot -->
```java title="Controls.java"
// Hold RB: the robot turns to face the goal by itself; the driver keeps forward and sideways. (1)!
g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off).says("hold: aim the robot at the goal");
// Or a toggle. Careful: while it is on, the robot turns by itself, even after you let go. (2)!
// g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
```

1. When you let go, the aim is off, and the driver turns again. To turn by hand while you hold RB, move the turn stick. Do not use it with a turret that follows the goal: these are separate modes (INIT stops with a message).
2. A press switches it on, and the next press switches it off. Two buzzes = on.

**Why:** the driver drives, and the robot keeps itself pointed at the goal. Start with hold-to-aim. When
you let go, the robot stops turning by itself, so it never surprises anyone. The stick always wins, so
the driver can take over at once. The toggle (commented out) keeps aiming after you let go.
**More:** [Lesson 9: aim the whole robot](tutorials/start/09-shooter-and-camera.md#10-no-turret-aim-the-whole-robot).

### How do I make the gamepad buzz when the aim is on target?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: aim-rumble -->
```java title="Controls.java"
// Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
// It works for a turret that follows the goal and for whole-robot aim.
g2.rumbleWhenAimed();
```

**Why:** the driver who fires watches the field, not the screen. The buzz tells the driver "now".
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
    public static double goalX = 13;                            // inches right of center (BIOBUZZ)
    public static double goalY = -13;                           // inches from center, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ: turned, not mirrored (true)
    public static double turnKp = DEFAULT;                      // whole-robot aim: push per degree off (0.02)
    public static double turnKd = DEFAULT;                      // brake near the goal (0.002)
    public static double maxTurnPower = DEFAULT;                // the most it turns by itself (0.5)
    public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
    public static double rumbleDeg = DEFAULT;                   // rumbleWhenAimed(): buzz within this (4)
    public static boolean aimOnFire = false;                    // no turret: a fire press turns to the goal first (2)!
}
```

1. With odometry, the aim goes on when the camera loses the goal. In BIOBUZZ, the goal is your HIVE's CELL that is up at the start, 13 in right of the center line and 13 in toward you.
2. Quick turn on fire, for a robot with no turret: with aim mode off and the goal in view, RB turns the robot to the goal, then fires (at most 0.5 s later: `aimOnFireSec`). It is off unless you set it to `true`, because it turns the robot.

**Why:** a robot or a game piece can block a tag, but the odometry still knows where the goal is. The
Driver Station says which one aims (`following: camera` or `following: odometry`). The backup needs the
pose (the robot's position and heading) from autonomous, or from
[set pose here](#how-do-i-set-the-pose-when-autonomous-did-not-run). Without it, the backup stays off,
and the Driver Station says so. You write `goalX`, `goalY` from your own drivers' wall, like every field
number in your code. Only the simulator's map and its scenario checks use the red wall. `mirrorForRed`
is ONE setting for the whole robot (the routes, the start and reset spots, this goal point). The library
takes the first one it finds of `Tunables.Aim.mirrorForRed`, `Tunables.Odometry.mirrorForRed` and your
autonomous's `mirrorForRed()`. If it finds none, it uses `true` (a mirrored field). BIOBUZZ's turned
field needs `false`: write it once, here. INIT shows the alliance and this choice. The numbers in
`Tunables.Aim` are in the tuning menu too, so copy tuned values back into `Tunables.java`.
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
    public static double goalX = 13;                            // inches right of center (BIOBUZZ)
    public static double goalY = -13;                           // inches from center, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ's field is turned, not mirrored
}
```

**Why:** you have no camera to buy, mount or tune. But the aim is only as good as the pose. It needs the
start spot from autonomous (or [set pose here](#how-do-i-set-the-pose-when-autonomous-did-not-run)). A
bump that slips the wheels moves the aim off a little. When you have a camera too, use
[the camera first, the odometry as backup](#how-do-i-keep-aiming-when-the-camera-loses-the-goal).
The library takes it that the turret turns about the robot's center. Whole-robot aim (no turret) works
from the odometry in the same way.
**More:** [Aim](reference/parts/aim.md), [Turret](reference/parts/turret.md).

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
The test runs in INIT (nothing moves) and takes about a second per setting. The Driver Station says
which setting it kept. Point the robot at the tags in INIT. Its numbers are optional. In
`Tunables.Limelight`, `autoLighting = false` skips the test and uses `pipeline`.
**For a camera tilted up:** add its tilt to the same section: `public static double pitchDeg = 35;`
(degrees up, measured on the robot). Then a goal off to the side reads the right angle, and distances
are along the floor.
**More:** [Vision: venue lighting](modules/vision.md#venue-lighting), [CameraTilt](modules/vision.md#cameratilt), [Camera](reference/parts/camera.md).

### How do I find game pieces with a HuskyLens?

**Where:** `Robot.java`, inside `define()`. First teach the camera, on the camera itself: dial to Color
Recognition, turn on Learn Multiple, point the `+` at a piece, and press the learning button.

<!-- recipe: huskylens -->
```java title="Robot.java"
// At the top of the class: public Camera pieceCamera;   Colors you taught it: ID1, ID2. (1)!
pieceCamera = add(Camera.huskyLens("huskylens").colors().label(1, "POLLEN").label(2, "NECTAR"));
// Or AprilTags: list which real tag each taught number is, and add goal(...). (2)!
// camera = add(Camera.huskyLens("huskylens").tag(1, 34).tag(2, 35).goal("BLUE SCORING", "RED SCORING"));
```

1. A HuskyLens does one job at a time: colors or tags, not both.
2. It says the number you taught it, never the tag's real number.

**Why:** the camera finds the piece itself, so the hub does no picture work. Then
`pieceCamera.target("POLLEN")` gives the bearing to the nearest box, or null. In `Tunables`, set `hfovDeg`
(the camera's side-to-side view angle: measure it). For a rough distance, also set `sizeIn` (the longest
side of the piece it sees). Nobody has tested it on a real robot yet.
**More:** [HuskyLensCamera](modules/vision.md#huskylenscamera), [Camera](reference/parts/camera.md).

### How do I shoot while the robot is driving?

**Where:** `Tunables.java` (the numbers, in your `Aim` section), and a button in `Controls.java`
(inside `buttons(...)`) to switch it on and off. The robot needs [odometry](reference/parts/odometry.md),
because the odometry gives the robot's speed. **It stays off until you tune it on the real robot**
(`shootOnMove = false`). Measure `flightSec` with a slow-motion video, and test shots while driving.
Only then write `true`.

<!-- recipe: tunables-moving-shot -->
```java title="Tunables.java"
// ADD these lines to your Tunables.Aim section (keep goalX, goalY and the others).
public static boolean shootOnMove = false;                  // true only after testing on the real robot
public static double flightSec = MEASURE;                   // seconds in the air (slow-motion video) (1)!
public static double flightSecPerIn = DEFAULT;              // longer shots fly longer: per inch (0)
public static double maxLeadIn = DEFAULT;                   // never lead more than this, inches (24)
// Where the shooter (a turret's pivot) and a camera on the body are from the robot's center. (2)!
public static double shooterForwardIn = MEASURE;            // inches forward of the center (a tape)
public static double shooterLeftIn = MEASURE;               // inches left of the center (a tape)
public static double cameraForwardIn = MEASURE;             // inches forward of the center (a tape)
public static double cameraLeftIn = MEASURE;                // inches left of the center (a tape)
```

1. Film a shot in slow motion. Count the frames from when the piece leaves the launcher to when it reaches the goal. Divide by the video's frames per second.
2. In inches: forward (+) and left (+). A camera on the turret sits at the turret's pivot.

Then a button, in `Controls.java` inside `buttons(...)`:

<!-- recipe: moving-shot-button -->
```java title="Controls.java"
// Shooting on the move on/off (on from ▶ when Tunables.Aim.shootOnMove = true): if
// the shots go wide while driving, switch it off and shoot standing still.
g2.a.toggle(robot.aim().movingShotMode()).says("shoot on the move on/off").rumble();
```

**Why:** a piece shot from a moving robot keeps the robot's speed, so it lands to the side (30 in/s for
0.8 s: 24 in). With `shootOnMove`, the turret (or the whole robot) aims at a point that far the other way,
and the launcher takes its speed from that point's distance. It also brings the camera's picture forward
to now, because the robot turned and moved since the camera took it. It works with a turret or
whole-robot aim, with a camera on the turret or on the body, or with the odometry alone. Tell it where
the shooter and the camera are. The Driver Station says `Aim : moving shot: lead 12.3 deg, 18 in` while
it leads.
**More:** [Lesson 9: shooting while moving](tutorials/start/09-shooter-and-camera.md#14-extra-shooting-while-moving), [Aim](reference/parts/aim.md).

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

**Why:** POLLEN (2.8 in) and NECTAR (3.6 in) fly differently, so each needs its own speeds. **The table
at ▶:** a plain `speedByDistance` table (named `main`) if there is one. If not, the first by name: here
`nectar` (n before p). To start on POLLEN, add `.startTable("pollen")` to the launcher line in
`Robot.java` ([like here](#how-do-i-keep-aiming-when-the-camera-loses-the-goal)). The Driver Station's
`Shot table` line says which one is in use. Fill each table with the
[Shot Tuner](guides/finders.md#shot-tuner-the-shot-table) ([its file](#how-do-i-build-a-shot-table-speed-for-each-distance)).
Press Y until `> Table`, then pick the table you tune with the D-pad. Then copy its line into
`Tunables.java`. To let the robot pick the table by itself, see
[the next piece picks it](#how-do-i-let-the-next-piece-pick-the-shot-table).
**More:** [Lesson 9: two shot tables](tutorials/start/09-shooter-and-camera.md#8-two-game-pieces-two-shot-tables), [Launcher](reference/parts/launcher.md).

### Every shot is a bit short today. How do I trim the speed (a low battery)?

**Where:** you write nothing for the D-pad, because the launcher brings it. The numbers go in your
`Tunables.Launcher` section. Your own buttons go in `Controls.java`, inside `buttons(...)`.

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

1. Hold the D-pad for 4 steps a second. Double-tap D-pad down to go back to 0. The launcher's first line on the Driver Station shows the trim: `Speed trim : +3%  READY`.

**Why:** a worn wheel or softer balls make every shot a little short. The trim fixes it for this match,
for every table. It moves +/- 1% a press, at most 15%, and never past the motor's top speed. It goes
back to 0 at every INIT. At INIT, the Driver Station offers last time's trim (`Trim last time : +3%`).
To use it, double-tap D-pad up after ▶ (or use your own button: `useLastTrim`, in the recipe). The
library never applies it by itself.

Battery compensation scales the F gain by `nominalVolts` / the battery (tuned at 12.6 V, at 11.5 V:
F x 1.10). Nobody has proven it on a real robot yet, so it is off: the box writes
`batteryCompensation = false`. Change it to `true` only when you want to test it. It needs the `kF` and
`nominalVolts` that the Flywheel Finder found together. That is why the box writes the gains as
`MEASURE`. Without battery compensation, `DEFAULT` gains (from the motor) are a good start.

When you need a trim every match, with a charged battery, the cause is the wheel or the balls, and the
table is off. Make the trim part of the table (+3%: every speed x 1.03, `{48, 2550}` becomes
`{48, 2627}`). Copy the lines into `Tunables.java`, and trim back to 0.
**More:** [Lesson 9: speed trim and a low battery](tutorials/start/09-shooter-and-camera.md#speed-trim-and-a-low-battery), [Launcher](reference/parts/launcher.md#speed-trim-and-battery-compensation).

### How do I add a hood to my launcher?

**Where:** `Robot.java`, inside `define()`: add `.hood(...)` to your launcher line. In `Tunables.java`, add
a new section `Hood` (its stops), and a third number in each row of your `Launcher` section's shot table.

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
    public static double startAngle = DEFAULT;                  // where it goes at ▶ (angle A)
}
```

Then the shot table:

<!-- recipe: tunables-hood-table -->
```java title="Tunables.java"
// IN PLACE OF your speedByDistance line: {inches to the goal, RPM, hood angle}, blended between rows. (1)!
public static double[][] speedByDistance = {{24, 2200, 3}, {48, 2550, 12}, {72, 2900, 20}};
```

1. This goes in your `Tunables.Launcher` section. The launcher blends the hood angle between rows, like the speed.

**With no measured angles:** leave out `angleA`, `angleB`. The hood then works in servo positions, and
the table's third number is a servo position.

<!-- recipe: tunables-hood-positions -->
```java title="Tunables.java"
// Tunables.Hood: only posA and posB (delete angleA, angleB). Tunables.Launcher, IN PLACE OF
// your speedByDistance line, {inches, RPM, servo position}: the Shot Tuner writes these for you.
public static double[][] speedByDistance = {{24, 2200, 0.24}, {48, 2550, 0.38}, {72, 2900, 0.50}};
```

**A hood on a linkage or cam:** equal servo steps do not make equal angles. Use this `Hood` section in
place of the one above. It has a calibration table instead of `posA`, `posB`, `angleA`, `angleB`.

<!-- recipe: tunables-hood-linkage -->
```java title="Tunables.java"
public static class Hood {                                      // "hood" in the configuration
    // {servo position, hood angle} rows, blended. The first and last rows are the stops (no posA / posB). (1)!
    // Example rows: write the Hood Finder's in their place.
    public static double[][] calibration = {{0.20, 0}, {0.32, 8}, {0.45, 18}, {0.65, 30}};
}
```

1. The angles must keep going one way: in servo-position order, the angles all go up, or all go down.

**A hood on a motor with an encoder** (it knows where it is): write `Hood.motor` in the launcher line, and
use this `Hood` section in place of the one above.

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

**Why:** close shots need a steep angle, and far ones a flatter one. So each distance gets a speed AND a
hood angle, blended between rows. `posA`, `posB` are hard limits: nothing sends the hood past a stop.
The launcher waits for the hood before it fires. For a servo hood, it waits a share of `travelSec`. For
a motor hood, it reads the encoder. While the camera sees a goal, the table sets the angle, and the
stick (gamepad 2 left Y) is a hood trim. With no goal seen, the stick moves the hood itself, so you
always see what it does. Double-tap D-pad down to clear both trims. A lift brings the same stick
(gamepad 2 left Y). With a hood AND a lift, give one of them another stick
([move a button](#how-do-i-move-a-parts-button-to-another-button)). INIT says which part has it. Find the stops with the [Hood Finder](#how-do-i-find-my-hoods-stops-and-angles),
then fill the table with the [Shot Tuner](#how-do-i-build-a-shot-table-speed-for-each-distance) (press Y until `> Hood`).
**More:** [Lesson 9: a hood](tutorials/start/09-shooter-and-camera.md#a-hood-the-shot-angle), [Hood](reference/parts/hood.md).

## Turrets and interlocks

Every kind of turret has its own lines: motor, servo or CR servo, with a switch or not, with any range.
See [Setting up a turret](guides/turret-setup.md). An interlock is a rule that stops two parts from
hitting each other. Code: [`InterlockRecipes.java`](../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook/InterlockRecipes.java).

### How do I home my turret with a switch?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-home-switch -->
```java title="Robot.java"
// "turret", "turret_home" = the motor's and the home switch's names in the robot configuration. (1)!
// At ▶ the turret turns slowly until it finds the switch. (2)!
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
        .homeSwitch("turret_home")
        .follows(camera));
```

1. The home switch is a touch sensor ("REV Touch Sensor" in the configuration) or a magnetic switch ("Digital Device", for example the REV Magnetic Limit Switch).
2. After that, each time the turret passes the switch, the switch sets its angle right again.

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

**Why:** a motor's encoder counts from where the turret was at INIT. With a switch, the turret finds its
real angle at ▶ by itself. It never does this during INIT, because nothing moves before ▶. Every time
the turret passes the switch, the switch fixes the error from a slipping belt. Until the turret is homed
(has found its real angle), it does not follow the goal and is never "aimed". You must write
`homeAngle`, or INIT stops. `min`, `max` default to -90 and 90. With no switch, put the turret at
`startAngle` (default 0, straight forward) by hand before INIT.
**More:** [Setting up a turret: how it knows its angle](guides/turret-setup.md#2-how-does-it-know-its-angle).

### How do I set up a turret on a servo?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-servo -->
```java title="Robot.java"
// "turret" = the servo's name in the robot configuration. Numbers: Tunables.Turret. (1)!
// fullRange(): the servo's whole turn. Turn it on BEFORE you measure posA and posB. (2)!
turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange().follows(camera));
```

1. A standard servo turns the turret to an angle by itself. It needs no encoder, no PID and no homing.
2. With it, a goBILDA 2000 servo turns 300 degrees, not 270. Find posA and posB with the Servo Finder: press Y while you choose the servo.

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
    public static double startAngle = DEFAULT;                  // sent here at ▶ (0)
}
```

**Why:** a servo knows how to go to a position, so the turret needs no encoder, no PID and no homing.
It is the cheapest turret. But a servo cannot say where it is. So at ▶, the library sends it to
`startAngle` (or the middle of its range), and "aimed" waits `travelSec` for it. You must write `posA`,
`angleA`, `posB`, `angleB`, or INIT stops. Find the positions with the [Servo Finder](#how-do-i-find-a-servos-open-and-closed-numbers),
and measure the angle at each with a phone angle app. Its range is between `angleA` and `angleB`. Put
hard stops a little past them.
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

1. The encoder is on an analog port and knows the angle at once, so the turret needs no homing. A slip ring (a turning joint that carries the wires) lets the turret turn with no limits, and it always takes the shortest way.

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

**Why:** with a slip ring, the wires never wind up. So the turret can always take the shortest way to
the goal, and is never "out of range". A motor turret can be `continuous()` too.
**More:** [Setting up a turret: how far can it turn?](guides/turret-setup.md#3-how-far-can-it-turn).

### How do I stop two parts from hitting each other?

**Where:** `Robot.java`, inside `define()`, one line below the parts' `add` lines. Also a new section in
`Tunables.java` for the zones, with the same name as the rule.

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
still meet. The library keeps the rule every loop, whatever moves the parts: buttons, sticks, actions, a
following turret or autonomous steps. The part that is in its zone first keeps it. The other part waits
at the edge of its own zone, and the Driver Station says so (`TurretLift : Lift held at 216 counts: Turret is between
130 and 218 deg`). The rule slows a fast part down as it comes near, so it brakes in time, even with
slow loops. To do this, the rule measures the part's speed every loop. The library reads the zones at
INIT only, so change them in `Tunables.java`.
**More:** [Two parts that must not collide](guides/interlocks.md), [Interlock](reference/parts/interlock.md).

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

**Why:** the order keeps the parts out of each other's way. The claw swings out before the lift rises,
and folds only after the lift is down. Each wait has a time limit, so a stuck part ends the action
instead of stopping the robot. With an interlock rule, the lift waits by itself while the turret is in the way.
**More:** the whole robot: [`v2bot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot/Actions.java).

## Actions

An action is a list of steps that use several parts, with waits between them. It never makes the robot
wait, because it does a little every loop. Actions go in `Actions.java`, one method each. The same
action works on a button and in an autonomous.

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
**More:** [Lesson 5: scoreHigh](tutorials/start/05-actions.md#2-scorehigh-line-by-line), [Action](reference/steps.md#action).

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

**Why:** the `for` loop runs once, while Java makes the action, and writes the steps 3 times. Then the action does them one after the other, and the robot does not wait.
**More:** [Action](reference/steps.md#action).

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

Search words: sleep, wait, pause, delay.

**In an action** (`Actions.java`):

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

**In an autonomous** (inside `steps(...)`), give the step a name first, for the report:

<!-- recipe: wait-in-auto -->
```java title="autos/YourAuto.java"
s.run("Open the claw", robot.claw::open)
 .waitSeconds("Let the piece drop", 0.5)    // a name for the report, then the seconds
 .run("Lift down", () -> robot.lift.goTo("down"));
```

**Why:** `sleep()` stops the whole robot: no driving, and no other part moves. `waitSeconds` waits while everything keeps running.
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

**Why:** every wait has a time limit, so a stuck part cannot freeze the action forever.
**More:** [Lesson 5: the rules](tutorials/start/05-actions.md#5-the-rules-every-action-follows).

### How do I start an action from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-button -->
```java title="Controls.java"
// Press Y: the action starts. Press Y again while it runs: it is canceled, safely.
g2.y.press(Actions.scoreHigh(robot)).says("score high");
g2.x.press(Actions.grabAndLift(robot)).says("grab and lift");
```

**Why:** the same button cancels it. A stick or button that moves the same part also stops it, so the driver always wins.
**More:** [Lesson 5: start it from a button](tutorials/start/05-actions.md#4-start-it-from-a-button).

### How do I stop every running action?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-cancel-all -->
```java title="Controls.java"
// Stop every running action at once (each stops what it started, safely).
g2.back.press(robot::cancelAll).says("stop all actions");
```

**Why:** a stopped action leaves each part safe: a lift holds its height, and an intake stops.
**More:** [RobotDefinition: for your code](reference/robot-java.md#robotdefinition).

### How do I run an action when the OpMode starts?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: action-at-start -->
```java title="Robot.java"
// In Robot.java's define(): this action runs once, right after ▶ (autonomous and TeleOp).
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

**Why:** the library never moves anything in INIT, so a fold-out waits for ▶. This is the library's
safety choice, because people are still next to the robot. The game rules would allow it (R103.B), but
the robot must be still once INIT is done (G304.H).
**More:** [Lesson 5: an action at ▶](tutorials/start/05-actions.md#7-an-action-when-the-opmode-starts).

### How do I use the same action in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: action-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .forward("To the goal", 24, 0.5)
 .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
 .back("Back up", 12, 0.5);
```

**Why:** you write the move once, and use it in TeleOp and autonomous. The report says if it timed out.
**More:** [Lesson 5: in an autonomous](tutorials/start/05-actions.md#6-the-same-action-in-an-autonomous).

## Autonomous

An autonomous is one file in `autos/`: a list of named steps. Distances are in **inches** (1 foot = 12
inches, so 2 feet = 24). Directions are in degrees, and power is from 0 to 1.

> **One rule for every angle:** angles count counterclockwise, like the IMU: 0 = forward, **90 = left**,
> 180 = back, -90 = right. The rule is for a move (`.move("Left", 12, 90, 0.5)`), a turn
> (`.turn("Left", 90, 0.4)`), `turnTo(...)`, and the heading in `start(...)` and `driveTo(...)`. Straight
> moves and turns can use words instead: `.forward`, `.back`, `.left`, `.right`, `.turnLeft`, `.turnRight`.

**The robot drives only after you run the Drive Tuner.** Until then, the autonomous shows "AUTONOMOUS IS OFF".
Run the [Drive Tuner](#how-do-i-tune-the-drive-for-autonomous) and copy its lines. Then set
`calibrated = true` in `Tunables.java`, section `Drive`. The simulator runs the autonomous anyway, so you
can try a route before tuning.

### How do I make my first autonomous (drive 2 feet)?

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
**More:** [Red and blue](building-autos/02-put-steps-together.md#red-and-blue).

### How do I drive forward, sideways and turn?

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
**More:** [Driving: move, turn, turnTo](building-autos/01-what-an-auto-can-do.md#drive).

### Does my autonomous need a RED version?

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
**More:** [Field numbers](building-autos/01-what-an-auto-can-do.md#field-numbers).

### How do I let the drivers choose before the match?

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
**More:** [The INIT menu](building-autos/06-advanced-autos.md#add-choices-to-the-init-menu), [Branches: onlyIf](building-autos/06-advanced-autos.md#add-choices-to-the-init-menu).

### How do I make sure there is time to park?

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
**More:** [Keep time to park](reference/steps.md#keep-time-to-park).

### How do I run a mechanism while the robot drives?

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
**More:** [Mechanisms: run and wait](building-autos/02-put-steps-together.md#start-something-or-wait-for-it). For whole actions while driving, use `alongside(...)` ([write each piece once](building-autos/06-advanced-autos.md#routes)).

### How do I wait for my alliance partner?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-wait-partner -->
```java title="autos/YourAuto.java"
s.menuYesNo("Wait for partner", false)
 .onlyIf("Wait for partner")
     .waitSeconds("Let our partner pass", 3)   // the robot keeps running while it waits
 .end();
```

**Why:** the drivers decide in INIT. For a wait before the first step, the menu already has a start delay.
**More:** [Write each piece once](building-autos/06-advanced-autos.md#routes).

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

**Why:** `ctx.waitFor` keeps every part updating and the timer working. `sleep()` would freeze them.
**More:** [Your own steps](building-autos/06-advanced-autos.md#write-your-own-step).

### How do I change a distance in an autonomous?

**Where:** the step itself, in your file in `autos/`. Change the number and build again.

```text title="autos/YourAuto.java"
.forward("To the goal", 30, 0.5)   // 30 = inches; change it here
```

**Why:** each autonomous keeps its own numbers, so a change to one auto does not change another. The
simulator's auto builder can read the auto, change a number, and give you the new file. `Tunables.java`
keeps the robot's numbers (speeds, positions, gains), which are the same in every auto.
**More:** [The numbers are in the step](five-files/05-autos.md#the-numbers-are-in-the-step).

### How do I share the same first steps in several autos?

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
**More:** [Write each piece once](building-autos/06-advanced-autos.md#routes).

### How do I make the start delay shorter, or use a different number on RED?

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
**More:** [What the INIT menu can choose](building-autos/06-advanced-autos.md#add-choices-to-the-init-menu).

## Several autos and choosing one

You can have one autonomous per plan, or one autonomous with an INIT menu. Both use the same pieces. For
the decision table, naming, simulator tests and a match-day checklist, see [Building autos 3: having multiple autos](building-autos/03-multiple-autos.md). Each
file below is whole. Copy it into `autos/`, and change the `package` and `import` lines as its comment
says. The distances are plain numbers in the steps ([why](#how-do-i-change-a-distance-in-an-autonomous)).

### How do I build several autos from the same pieces?

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
**More:** [Build them from shared steps](building-autos/06-advanced-autos.md#routes).

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
**More:** [Write each piece once](building-autos/06-advanced-autos.md#routes), [names and groups](building-autos/03-multiple-autos.md#names-on-the-driver-station).

### How do I let the drivers choose the start spot and the plan in INIT?

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
**More:** [One file, the drivers choose in INIT](building-autos/06-advanced-autos.md#a-whole-example-one-auto-the-drivers-choose), [what the menu can choose](building-autos/06-advanced-autos.md#add-choices-to-the-init-menu).

## Odometry and driveTo

Odometry tells the robot where it is on the field. **Field numbers:** x = inches right of the center
line, and y = inches away from your wall (negative = your half). Heading 0 = facing away from you, and
counterclockwise (left) is +. **Every number in your code is seen from your own drivers' wall**, whatever
your alliance: start spots, `driveTo`, the goal point, the reset spot. Only the simulator's map and its
scenario checks (`expect robot x ...`) use one fixed view, from the red wall. See the
[field map](tutorials/start/07-odometry.md#1-why-odometry).

### How do I add a goBILDA Pinpoint?

**Where:** `Robot.java`, inside `define()`, after the drive line.

<!-- recipe: odometry-pinpoint -->
```java title="Robot.java"
// After drive(...). The robot's length and width, then where each pod's wheel is, mm: (1)!
// the forward pod from the LEFT edge, the sideways pod from the FRONT edge.
odometry(Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(284, 383));
```

1. Measure with a tape across the frame, bumpers off. The library finds the robot's center from the size. Here it works out `offsetsMm(-84, -168)`: the forward pod is 84 mm right of the center, and the sideways pod is 168 mm behind it. The Odometry Tuner then measures the exact offsets, and you write `.offsetsMm(...)` in place of `.podsFromEdgesMm(...)`.

**Why:** the Pinpoint must know where the pods are, so a turn on the spot does not look like a move.
**More:** [Lesson 7: one line in Robot.java](tutorials/start/07-odometry.md#4-one-line-in-robotjava), [Pinpoint](modules/odometry.md#pinpoint).

### How do I add a SparkFun OTOS?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-otos -->
```java title="Robot.java"
// The robot's length and width, then the OTOS's middle from the FRONT and LEFT edges (mm), and its turn.
odometry(Odometry.otos("otos").robotSizeMm(430, 400).mountFromEdgesMm(126, 200, 0));
```

**Why:** the OTOS measures at its own spot. To give the place of the robot's center, it must know where it sits.
**More:** [OTOS](modules/odometry.md#otos).

### How do I add dead wheels?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-dead-wheels -->
```java title="Robot.java"
// The configuration names of the motor ports the pods plug into: left, right, sideways pod.
// trackWidthIn: left pod to right pod. perpForwardIn: the sideways pod, 6 in BEHIND the center.
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

**Why:** the tuner checks which way each pod counts, and measures the offsets. Then set `Tunables.Odometry.checked = true`, or `driveTo` does nothing on the robot.
It is its own small file, because each program in the Driver Station's list is one class. `robot()` makes
it use your `Robot.java`, so it measures the same odometry line that autonomous uses.
**More:** [Lesson 7: the Odometry Tuner](tutorials/start/07-odometry.md#5-the-odometry-tuner), [Finders](guides/finders.md#odometry-tuner-directions-offsets-and-a-loop-check).

### How do I drive to a point on the field?

**Where:** a file in `autos/`, inside `steps(...)`. It needs a mecanum drive.

<!-- recipe: drive-to -->
```java title="autos/YourAuto.java"
// The start spot (x, y, heading) must be where the robot really stands: the pose starts there.
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", -30, -30, 45, 0.6)
 .parkWithSeconds(4)
 // The last number: "close enough", in inches (default 1).
 .driveTo("Park", -56, -36, 0, 0.7, 2.0);
```

It replaces `.move(...)`: you give a place on the field, not a distance.
**Why:** `driveTo` keeps checking where the robot is. So a bump or a wheel slip does not matter, and the robot ends on the point.
On the robot, it works only after the [Odometry Tuner](#how-do-i-tune-the-odometry) and `checked = true` in `Tunables.java`, section `Odometry`.
**More:** [Lesson 7: drive to field points](tutorials/start/07-odometry.md#6-drive-to-field-points), [driveTo](building-autos/05-auto-builder-odometry-and-pedro.md#drive-to-a-point-driveto).

### How do I get around field elements on the way to a spot?

**Where:** `Robot.java`, inside `define()`, then a file in `autos/`, or `Controls.java`. It needs odometry and a mecanum drive.

<!-- recipe: field-map -->
```java title="Robot.java"
// After odometry(...). The margin: half the robot's corner to corner size, plus some for drift.
// Each box: two opposite corners, like driveTo's numbers. These are BIOBUZZ's two HIVE frames.
fieldMap(FieldMap.margin(13)
        .keepOut("HIVE frame, near", -19.5, -24.8, 19.5, -15.4)
        .keepOut("HIVE frame, far", -19.5, 15.4, 19.5, 24.8));
```

In an autonomous, `routeTo` is `driveTo` with a way around the boxes (the keep-out areas in the field map). It starts from wherever the park time stopped the robot:

<!-- recipe: route-to-park -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", -30, -30, 45, 0.6)
 .parkWithSeconds(6)    // the route slows at each corner: give it more time than a straight line
 .routeTo("Park", -56, -36, 0, 0.8);
```

In TeleOp, a button or a time starts it, and any drive stick stops it:

<!-- recipe: route-button -->
```java title="Controls.java"
// route(...) makes the job. It starts when the button or the time says so.
Runnable park = route("Park", -56, -36, 0, 0.8);
g1.dpadLeft.tap(park).says("drive to the park spot (a drive stick stops it)");
atSecondsLeft(10, park);
```

**Why:** a straight line from an unknown place can go through a field element. The robot works out the way when it sets off.
It uses only its pose, so it cannot see another robot. Go slow the first time.
**More:** [Keep-out boxes and routes](modules/odometry.md#keep-out-boxes-and-routes).

### How do I show where the robot is?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-pose -->
```java title="Controls.java"
show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in, heading %.0f",
        robot.pose().x, robot.pose().y, robot.pose().headingDeg));
```

**Why:** to check the odometry, push the robot by hand and watch the numbers.
**More:** [robot.pose()](modules/odometry.md#robotpose). (TeleOp also starts where autonomous ended: [lesson 7](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended).)

### How do I use the robot's position in an action?

**Where:** `Actions.java`, inside the class.

<!-- recipe: pose-in-action -->
```java title="Actions.java"
/** Runs the intake while the robot is on our half of the field (y below 0). */
public static Action intakeOnOurHalf(Robot r) {
    return Action.named("Intake on our half")
            .then(r.intake::in)
            .waitUntil("past the center line", () -> r.pose().y > 0, 10.0)
            .then(r.intake::stop);
}
```

**Why:** the library updates `robot.pose()` every loop, so a wait can test where the robot is.
**More:** [robot.pose()](modules/odometry.md#robotpose).

### How do I set the pose when autonomous did not run?

**Where:** `Tunables.java`, section `Odometry` (and `Controls.java` for more spots).

When autonomous stopped early or did not run, TeleOp does not know where the robot is. Park the robot on
the reset spot and let go of the drive sticks. Then hold gamepad 1 **START** for 1 second ("set pose
here"). This works in INIT or in the match. Write the spot for BLUE, like a start spot. Write
`mirrorForRed = false` because BIOBUZZ's field is turned half a turn (RED uses the same numbers from its
own wall). On a mirrored field, leave it out (default `true`, RED mirrored). INIT's `Reset spot for RED`
line shows which one the library uses.

<!-- recipe: tunables-reset-spot -->
```java title="Tunables.java"
public static class Odometry {                                  // a fixed name: always Odometry
    public static boolean checked = false;                      // true after the Odometry Tuner's Loop test
    // "Set pose here" (TeleOp: hold gamepad 1 START 1 s on this spot), written for BLUE. (1)!
    public static double resetX = -56;                          // inches right of center, - = left
    public static double resetY = -61;                          // inches from center, - = our half
    public static double resetHeading = 0;                      // degrees, 0 = facing the field
    // RED: false = the same numbers from the red wall; true (or left out) = mirrored left-right. (2)!
    public static boolean mirrorForRed = false;                 // BIOBUZZ: false
}
```

1. Inches and degrees seen from your wall, like a start spot. Here the spot is our left corner tile, facing the field.
2. BIOBUZZ's field is turned half a turn between the alliances, so write `false`. `true` is for a mirrored field.

For more spots, or to set the pose from an action, write this in `Controls.java`, inside `buttons(...)`:

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

**Why:** with a pose, the aim's odometry backup and field-centric "forward" work again. The 1 s hold
means a bump of the button does nothing. Write all the spots from your own drivers' wall, like every
field number in your code.
**More:** [Lesson 7: TeleOp starts where autonomous ended](tutorials/start/07-odometry.md#7-teleop-starts-where-autonomous-ended), [resetPose()](reference/robot-java.md#robotdefinition).

## Pedro paths

Pedro Pathing drives smooth curves. It needs the `pedro` folder in your project ([add Pedro](tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)),
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

**Why:** Pedro uses your drive and odometry lines, so you describe the robot only once.
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

**Why:** Java makes a path once (`static final`), because it is the same every match.
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

**Why:** each `follow` brakes to a stop at its end. Start the next path where the last one ended.
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

### How do I drive first and turn at the end of a path (or turn first)?

**Where:** your autonomous class, above `steps(...)`.

<!-- recipe: pedro-turn-when -->
```java title="autos/YourAuto.java"
// Turn from 0 to 90 only in the last 30% of the line (0.7 to 1): drive first, then turn.
static final PedroPath DRIVE_THEN_TURN = PedroPath.line(-56, -61, -30, -30).turnTo(0, 90, 0.7, 1);
```

**Why:** `turnTo(a, b)` turns all along the part. The two extra numbers say on which stretch it turns (0 = the
part's start, 1 = its end). `0, 0.3` turns first, then drives. `0.7, 1` drives first, then turns.
**More:** [PedroPath: the heading](modules/pedro.md#pedropath-paths-in-our-field-numbers).

### How do I start something partway along a path?

**Where:** your autonomous, `steps(...)`. Android Studio shows `PathAt` in red. Press Alt+Enter to add the import.

<!-- recipe: pedro-along -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 // PathAt: start things ALONG the path, once each, while the robot keeps driving.
 .follow("To the goal", Paths.IN_ONE_GO,
         PathAt.part(2, "Intake on", robot.intake::in),            // where part 2 starts
         PathAt.inchesLeft(6, "Intake off", robot.intake::stop))   // 6 in before the end
 .waitSeconds("Score (the robot holds still)", 0.5);
```

You can also use `PathAt.fraction(0.5, ...)` (halfway along the whole path), `PathAt.inchesDone(10, ...)`
and `PathAt.near(x, y, 6, ...)`, for example `PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp)`.
In place of a name and a command, you can give an action from your `Actions.java`
(`PathAt.inchesLeft(12, Actions.scoreHigh(robot))`: any action you wrote).

**Why:** the robot does not stop to start a mechanism, and Pedro has no "do this here" of its own. If the
path ends early, the report says which ones did not start (`STALLED, not fired: Intake off`).
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

**Why:** you see what the robot "thinks" while you drive. The value must be quick to read, with no waiting.
**More:** [The button menu: feedback](guides/buttons.md#4-feedback). For debug lines (loop time and more), hold gamepad 1 START, then press BACK. The lines turn on or off when you let go. BACK first only pauses the robot. When the alliance switch is on, let go before 1.5 s: a 1.5 s hold switches the alliance and does not change the debug lines.

### How do I round a number on the Driver Station?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-format -->
```java title="Controls.java"
// Round a number: %.0f = no decimals, %.1f = one decimal. Locale.US keeps the dot (not a comma).
show("Target RPM", () -> String.format(Locale.US, "%.0f", robot.launcher.targetRpm()));
```

**Why:** `%.0f` means "a number with 0 decimals", and `%.2f` gives 2.
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

**Why:** `ctx.loop()` keeps the robot running inside your loop. The time limit makes sure that the loop ends.
**More:** [Your own steps](building-autos/06-advanced-autos.md#write-your-own-step), [The report](building-autos/07-match-day.md#after-the-match-read-the-report).

## Lights

A colored light on the robot tells the drivers what the robot "knows" (full, ready, a mode on), so they
do not have to look at the Driver Station. The color names need `import com.crossbotics.ftchelper.robot.LightColor;`
at the top of each file that uses them: `Controls.java`, and `Actions.java` if an action flashes the
light. To add it, press Alt+Enter on a red `LightColor`. The full details: [Lights](reference/parts/lights.md).

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

1. `Controls.java` needs `import com.crossbotics.ftchelper.robot.LightColor;` for the colors. To add it, press Alt+Enter on a red `LightColor`.

**Why:** the light is one line, like any part. It has no numbers and no buttons of its own: the colors come from your rules.
**More:** [Lights](reference/parts/lights.md) (the parts and their color tables).

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

**Why:** each `LightColor` is one of the Blinkin's patterns (`BLUE` is its plain blue). LED strips differ.
When one color looks wrong on yours, give that color another pattern. Your rules still say
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

**Why:** any true/false works as a rule:
- a part's method (`robot.intake::isRunning`)
- a mode: `() -> robot.turret.followMode().isOn()` (the turret follows the goal), or on a robot without a turret, `() -> robot.aimMode().isOn()` (whole-robot aim is on)
- your own test (`() -> robot.lift.position() > 1000`, if your robot has a lift)

Blinking is at most 5 times a second, and the light never changes faster than every 0.1 s (FTC rule R202 J).
**More:** [Lights](reference/parts/lights.md), [lambdas](tutorials/start/05-actions.md).

### How do I show the alliance on the light before the match?

**Where:** you add nothing, because it is on by default. To change its colors or turn it off, write in
`Controls.java`, inside `buttons(...)`.

<!-- recipe: light-alliance -->
```java title="Controls.java"
// On by default: the alliance at INIT (solid RED or BLUE), flashed for 1 s at ▶. (1)!
// Your light shows red badly? Other colors for the alliance at INIT (BLUE, and PURPLE for RED):
robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);
// No alliance color at INIT at all? Delete the line above and write: (2)!
//     robot.lights.allianceAtInit(false);
```

1. Until the drivers choose an alliance, the light blinks white. You do not write anything for this.
2. Your rules then show from INIT on. Or, in `Tunables.java`, write this in the light's section: `public static class Light { public static boolean allianceAtInit = false; }`. The line in `Controls.java` wins.

**Why:** with a wrong alliance pick in INIT, the robot aims at the other alliance's goal all match. The
light is solid RED or BLUE before ▶ (white, blinking once a second, until the drivers pick). So the drive
team sees a wrong pick from the field. The game manual has no rule against a light in your alliance
color, but the ROBOT SIGNS are still required (R401, R402). Keep your own rules' colors away from red and blue.
**More:** [Lights](reference/parts/lights.md#the-alliance-at-init).

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

1. A flash wins, wherever you write it. The launcher counts the pieces it feeds. The flash is 0.2 s, so two quick shots are two flashes, not one long one.

**Why:** `whenCountGoesUp` flashes each time the number goes up, so each flash is a real shot.
`whenStarts(test)` flashes when a true/false turns from false to true (`whenStarts(robot.intake::isRunning)`:
the intake has started). A flash lasts 0.5 s. `.forSeconds(...)` before `when...` changes it. Here it is
shorter, so two shots a quarter second apart are two flashes, not one long one. `flash(color)` from a
button or an action flashes once.

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

**Why:** no action "claims" the light, so any action (or button) may flash it. A flash beats every rule.
But you cannot see a flash in the same color as the rule under it, so pick another color, or a longer time.

### How do I blink the light in the last 20 seconds?

**Where:** `Controls.java`, inside `buttons(...)`, before the other rules.

<!-- recipe: light-endgame -->
```java title="Controls.java"
// Yellow, blinking, in the last 20 seconds of TeleOp: time to park (above the other rules: it wins).
robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
```

**Why:** the time counts from ▶ (TeleOp's 2:00), like the gamepad buzzes. It is about a second off from
the field clock, which is good enough for a reminder.

## Tuning and finders

### Where do the numbers go?

In `Tunables.java`, one section per part (see [the lift recipe](#how-do-i-add-a-lift-with-set-heights)).
A finder (a program that measures a number on the robot) shows lines like `Tunables.Claw: P1 = 0.210, P2 = 0.770`.
Copy them into that section, keep the other lines, and give P1 and P2 real names. A number you leave out
uses the library's default (INIT says which). The exception is a lift's or arm's `min` and `max`: you must write those.

**More:** [Tunables.java](reference/tunables.md), [Finding your numbers](guides/finders.md).

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

**Use it:** put the robot on a block. Run "Cookbook Servo Finder" (your name for it). Choose the servo
(D-pad, A). A servo cannot say where it is, so choose a start number close to where it sits now (D-pad,
A). Nudge it with the D-pad. At each end of its safe travel (right before it pushes on the frame), press X,
then B. Then move it to each position you need and press A. For example: fully open, A (that is P1);
closed, A (P2). Copy the line it shows (`Tunables.Claw: P1 = 0.210, P2 = 0.770`)
into `Tunables.java` and rename P1, P2 (`open`, `closed`). For a part with
[`.fullRange()`](#how-do-i-get-my-servos-full-range) in Robot.java, press Y first, while you choose the servo.
**Why:** a servo pushed past its end hums, gets hot and breaks. The finder finds the safe numbers.
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

**Use it:** first put the lift all the way down, because the count starts at 0 there. Choose the motor
(D-pad, A). Move it slowly with the right stick. Press X a little above the bottom and B a little below the top.
Then press A at each height you need. Copy the line it shows (`Tunables.Lift: min = 0, max = 2580, P1 = 1200`)
into `Tunables.java` and rename P1 (`low`).
**Why:** a wrong top limit drives the lift into its hard stop. Measured limits keep it safe.
**More:** [Motor Finder](guides/finders.md#motor-finder-limits-positions-top-speed).

### How do I change a number while the robot runs?

With the five files, you write no code for this. In the TeleOp (PRACTICE profile), after ▶, **hold GUIDE
for 1 second**, and the tuning menu opens. D-pad up/down chooses a number, and right/left changes it.
**A** saves it on the hub, and **B** lists the changed values. **Then copy each good value into
`Tunables.java`.** A value saved only on the hub is lost with a new Control Hub, and your teammates cannot see it.

**Why:** you see the effect at once, and you do not have to build and install again.
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

**Why:** the tuner measures the real wheel size and strafe correction. Copy its lines, then set `Tunables.Drive.calibrated = true`.
**More:** [Tune the drive first](five-files/05-autos.md#tune-the-drive-first), [Drive Tuner](guides/finders.md#drive-tuner-wheel-size-and-strafe-correction).

### How do I set the flywheel numbers?

Before you have them, write `DEFAULT` in their place, or leave the lines out. The launcher then starts
from the parts catalog's numbers for its motor, and INIT says so. For a goBILDA 6000 RPM motor, these
are kF 11.7, kP 1.17, kI 0.117, and 60% of its top speed, 3600 RPM. Then run the **Flywheel Finder**
(in the [starter kit](starter-kit.md)) and copy its line into `Tunables.Launcher` (see
[the launcher recipe](#how-do-i-add-a-launcher-flywheel-and-feeder)). Do not copy another robot's gains,
because its team found them for its own wheel.

**Why:** the right `kP`..`kF` (the gains: how hard the motor pushes toward the target speed) make the
wheel get back to speed fast after each shot.
**More:** [Flywheel Finder](guides/finders.md#flywheel-finder-gains-and-recovery).

### How do I build a shot table (speed for each distance)?

**Where:** a new file in TeamCode, `ShotTuner.java`. Run the [Aim Tuner](guides/finders.md#aim-tuner-camera-offset) first.
Below is the whole file. Its first line is `package org.firstinspires.ftc.teamcode;` (Android Studio writes it
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

**Why:** the launcher needs a speed for each distance. At each distance, you shoot 3-5 times per speed
and mark each HIT (A) or MISS (B), all on gamepad 1. A left stick click takes the last mark back. X
keeps the speed with the best hit ratio (hits out of shots). The tuner fills
`Tunables.Launcher.speedByDistance` (one line per table to copy), so the launcher picks the speed for
each distance.
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

**Why:** the hood needs its stops and angles. Nudge the hood to each stop, back off a hair, and press X
and B. The finder writes `Tunables.Hood: posA = ..., posB = ...`. Before X / B, hold LB + D-pad to type
in the angle you measured (or press A for a point in between, for a linkage). Copy the lines into `Tunables.java`.
**More:** [Hood Finder](guides/finders.md#hood-finder).

## Simulator

The simulator runs your real code on a laptop, with the field in 3D. The easy way to start it is a
button in Android Studio (below). Or type the commands in a terminal:
in Android Studio, the **Terminal** tab at the bottom ([how](setup/09-the-terminal.md)).
First go into the `ftc-helper-lib` folder by typing `cd ftc-helper-lib`. It is the library's folder
inside your robot project, next to `TeamCode` in Android Studio's Project panel. If it is not there, ask
your mentor, or see [Add the library](setup/04-add-the-library.md). `./gradlew` runs Gradle, the tool
that builds the code, and `-P...` gives it a setting.

### How do I start the simulator?

In Android Studio, pick **Simulator** in the list next to the green Run button, and click Run. If
**Simulator** is not in the list, close the project and open it again ([the buttons](setup/04-add-the-library.md#the-simulator-buttons-you-do-nothing)).
Or, in the terminal:

```bash
./gradlew :sim:run
```

Then open **http://localhost:8780** in a browser. Choose an OpMode, press INIT, then ▶. To stop the
simulator, click the red **Stop** button (or press Ctrl+C in the terminal).

**Why:** you can try buttons and autonomous routes before the robot is built.
**More:** [Simulator: start it](guides/simulator.md#start-it).

### How do I see my own robot in the simulator?

Start the simulator (the **Simulator** button, or `./gradlew :sim:run`). It runs your TeamCode. When it
starts, it makes the simulator's robot file from your `Robot.java`, and prints `Robot file made
from Robot.java: simulator/robot.properties`.

**Why:** the robot file then always matches your code. The simulator keeps your own lines in it. When
you change the code, stop the simulator and start it again.
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

1. The real robot never reads `.looks(...)`. Measure with a tape, or shape the part in the simulator's Edit robot mode and copy the lines it shows.
2. The arm hinges at the front and rests on its stop. The roller rides on the arm's end: `on("intake_arm")`.

**One shape or several:** one shape goes inside `looks(...)` on the part's line. For two or more (an arm
and its roller), give each a name first (`Shape intakeArm = ...;`), then write `looks(intakeArm, intakeRoller)`
([how](reference/robot-java.md#shapes-and-parts)).
**Why:** without `.looks(...)`, the simulator still runs, with a guessed box for each part (marked
`TODO measure` in the robot file). With it, the 3D view shows your robot, and the size check (18 in start
cube, 29 in tall) is right. Pieces also touch the intake where the real one is. Name the part
(`.part(...)`) too, so a roller's or a wheel's size comes from the parts catalog. **The easy way:** use
the simulator's **Edit robot** mode. Drag and size each part on the 3D robot, then copy the
`.looks(...)` lines it shows into `Robot.java`. After a change, stop the simulator and start it again.
**More:** [Shapes and parts](reference/robot-java.md#shapes-and-parts) (every shape and setting),
[A robot file from Robot.java](guides/simulator.md#a-robot-file-from-robotjava).

### How do I test my code with a script?

A script (a scenario file) presses buttons and checks the robot for you. The easy way is to **record**
it. In the simulator, click **Tests > Record a test**, drive, and click **Stop recording**. Keep the
ticked checks and click **Save**. The simulator then writes `simulator/scenarios/NAME.sim` for you
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

To run every test in the folder, click **Tests > Run all tests** on the simulator page, or the
**Simulator tests** button in Android Studio. Or use the terminal (leave out `-Pfile` for every test):

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/intake.sim
```

It runs your own code (`../TeamCode`, next to the library in your robot project).
**Why:** a script checks the same things every time, with no clicking, and catches a mistake before a meeting.
A scenario uses the FTC button names, which can differ from the Java names. `g2.rt`, `g2.lb`, `g2.a` are
the same, but `g2.dpadUp` is `g2.dpad_up` and `g2.leftStickY` is `g2.left_stick_y`.

**More:** [Start lesson 10: Test your robot code](tutorials/start/10-test-your-robot-code.md), [Scenario files](guides/simulator.md#scenario-files-test-without-a-browser) (every command and button name).

## Not here?

- Every class and method: the [reference](reference/README.md) and the [module pages](../README.md#what-is-inside).
- Something goes wrong: [Troubleshooting](troubleshooting.md).
- Learn it step by step: the [lessons](tutorials/README.md).
- A question this page does not answer: [tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).
