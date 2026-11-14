# The basics

The files, where each line goes, and the words you will meet. Read this first if you are new to Java.

**The questions on this page:**

- [Coming from Blocks: where did my blocks go?](#coming-from-blocks-where-did-my-blocks-go)
- [Where does each line go? The files, whole](#where-does-each-line-go-the-files-whole)
- [Words you will meet](#words-you-will-meet)

## Coming from Blocks: where did my blocks go?

| In Blocks you used | Here you write | In which file |
| --- | --- | --- |
| a `repeat while opModeIsActive` loop | nothing: the library runs the loop for you, many times a second | - |
| drive forward, turn (in autonomous) | `.move("Forward", 24, 0, 0.5)`, `.turn("Left", 90, 0.4)` ([moves](09-autonomous.md#how-do-i-drive-forward-sideways-and-turn)) | a file in `autos/` |
| `if` a sensor or part says something, then ... | `.waitUntil(...)`: wait until it is true, then go on ([wait until](08-actions.md#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| `if` the drivers chose something before the match | `onlyIf(...)` with a menu choice ([choices](09-autonomous.md#how-do-i-let-the-drivers-choose-before-the-match)) | an autonomous |
| `repeat 3 times` | a `for` loop that adds the steps 3 times to an action ([repeat](08-actions.md#how-do-i-repeat-steps-several-times)) | `Actions.java` |
| set servo position | a named position: `robot.claw.open()`, `robot.intakeDeploy.goTo("deployed")` ([servo positions](04-mechanisms.md#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)) | `Controls.java`, `Actions.java` |
| `waitForStart` | nothing: the library waits for ▶ (start on the Driver Hub). For something right after ▶: `atStart(...)` ([at ▶](08-actions.md#how-do-i-run-an-action-when-the-opmode-starts)) | `Robot.java` |
| blocks before `waitForStart`, or after the loop | `atInit(...)`, `duringInit(...)`, `atStop(...)` ([your own code](../guides/your-own-code.md)) | `Robot.java` |
| `if gamepad2.y then ...` inside the loop | one line, once: `g2.y.press(...)` ([buttons](03-buttons.md#how-do-i-make-a-button-do-something-once)) | `Controls.java` |
| `if` the button is held | `g1.a.hold(...)` ([hold](03-buttons.md#how-do-i-run-something-only-while-a-button-is-held)) | `Controls.java` |
| `sleep 500` | `.waitSeconds(0.5)` ([wait](08-actions.md#how-do-i-wait-half-a-second-like-sleep-in-blocks)) | `Actions.java` or an autonomous |
| a loop that waits until something is true | `.waitUntil(...)` ([wait until](08-actions.md#how-do-i-wait-until-something-is-true)) | `Actions.java` or an autonomous |
| set motor direction REVERSE | drive motors: `.reversed("front_right", ...)` ([drive](02-driving.md#a-wheel-turns-the-wrong-way-how-do-i-fix-it)); a lift: `.reversed()` ([lift](04-mechanisms.md#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)) | `Robot.java` |
| set motor power | a command of the part: `robot.intake.in()`, `robot.lift.goTo("high")` ([parts](04-mechanisms.md#how-do-i-send-a-part-to-a-position-from-a-button)) | `Controls.java`, `Actions.java` |
| a variable for a number (a speed, a position) | a number in `Tunables.java` ([numbers](15-tuning.md#where-do-the-numbers-go)) | `Tunables.java` |
| `telemetry.addData` | `show("Label", ...)` ([show](13-telemetry.md#how-do-i-show-a-value-on-the-driver-station-screen)) | `Controls.java` |
| drive blocks (sticks to motor powers) | nothing: driving is built in ([driving](02-driving.md#driving-that-is-already-built-in)) | - |

**Why there is no loop:** in Blocks, your code ran again and again in the loop. Here `buttons(...)` runs
**once**, at INIT, and each line plugs a job into a button. Then the library's loop runs the job each time
the driver presses the button. So you never write `if`, `while` or `sleep` for buttons. More:
[From Blocks to Java](../blocks-to-java.md).

## Where does each line go? The files, whole

Each recipe gives a few lines. Put them INSIDE one of these files, at the spot the comment shows. Keep
every other line of the file. Your files also start with a `package` line and some `import` lines. The recipes leave them out. They stay at the very top, above `public class`. An import tells Java where a word
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

1. `.part(...)` says which motor or servo it is. It is optional, but we recommend it: with it, the library takes better starting values from the part's numbers, and the simulator moves like the real part. The ids here are examples. Write the ids of your parts, from the [parts catalog](../guides/parts-catalog.md). An arm, a turret and a launcher need the motor's id, and take it in their brackets instead. Every recipe below has the same `.part(...)` comment, with this meaning.

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

An autonomous file: see [your first autonomous](09-autonomous.md#how-do-i-make-my-first-autonomous-drive-2-feet).
The numbers file: see [Tunables.java](15-tuning.md#where-do-the-numbers-go).

**`Tunables.java`**: one section per part, inside `public class Tunables { ... }`. When a recipe adds a
new part, it shows the part's whole section (`public static class Lift { ... }`). Put it inside
`Tunables`, next to the other sections. If your file **already has** a section with that name, do not add a second one. Two sections with one name do not compile. Copy only the lines inside it into
yours, and change the lines you already have. A recipe that adds to a section you already have says so
("In your `Tunables.Launcher` section") and shows only the lines.

**How a recipe's section reads:** one number per line, with a short comment.
- `MEASURE`: a number that only your robot can give (a lift's top, a servo's positions). INIT stops and
  names the tool that finds it. Write the number in its place.
- `DEFAULT`: the library's starting value, shown in brackets in the comment. Change it
  only when you see the problem it fixes ([Find a setting by what you want](../reference/tunables.md#find-a-setting-by-what-you-want)).
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
| catalog id (`"gobilda-5203-312"`) | a motor's or servo's name in the [parts catalog](../guides/parts-catalog.md): 5203 is goBILDA's motor series, 312 its RPM (on the motor's label) |
| `.part("gobilda-5203-435")` | which motor or servo a part uses. Optional, but recommended: with it, the library takes better starting values from the part's numbers, and the simulator moves like the real part. An arm, a turret and a launcher need the id, and take it in their brackets instead |
| tuning menu | a menu where you change numbers while the robot runs. The INIT choice menu of an autonomous is a different menu |
| terminal | a window where you type commands. In Android Studio: the **Terminal** tab at the bottom ([how](../setup/09-the-terminal.md)) |

**One rule for every recipe:** a job never waits. A `sleep(...)` or a `while` loop in a button job or an
action freezes the whole robot, so never write one there. To wait, see
[how do I wait half a second (like sleep)?](08-actions.md#how-do-i-wait-half-a-second-like-sleep-in-blocks).
