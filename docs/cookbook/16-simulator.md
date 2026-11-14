# Simulator

The simulator runs your real code on a laptop, with the field in 3D. The easy way to start it is a
button in Android Studio (below). Or type the commands in a terminal:
in Android Studio, the **Terminal** tab at the bottom ([how](../setup/09-the-terminal.md)).
First go into the `ftc-helper-lib` folder by typing `cd ftc-helper-lib`. It is the library's folder
inside your robot project, next to `TeamCode` in Android Studio's Project panel. If it is not there, ask
your mentor, or see [Add the library](../setup/04-add-the-library.md). `./gradlew` runs Gradle, the tool
that builds the code, and `-P...` gives it a setting.

**The questions on this page:**

- [How do I start the simulator?](#how-do-i-start-the-simulator)
- [How do I see my own robot in the simulator?](#how-do-i-see-my-own-robot-in-the-simulator)
- [How do I make the simulator draw my part?](#how-do-i-make-the-simulator-draw-my-part)
- [How do I test my code with a script?](#how-do-i-test-my-code-with-a-script)

## How do I start the simulator?

In Android Studio, pick **Simulator** in the list next to the green Run button, and click Run. If
**Simulator** is not in the list, close the project and open it again ([the buttons](../setup/04-add-the-library.md#the-simulator-buttons-you-do-nothing)).
Or, in the terminal:

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
./gradlew :sim:run
```

Then open **http://localhost:8780** in a browser. Choose an OpMode, press INIT, then ▶. To stop the
simulator, click the red **Stop** button (or press Ctrl+C in the terminal).

**Why:** you can try buttons and autonomous routes before the robot is built.
**More:** [Simulator: start it](../simulator/01-start-and-drive.md).

## How do I see my own robot in the simulator?

Start the simulator (the **Simulator** button, or `./gradlew :sim:run`). It runs your TeamCode. When it
starts, it makes the simulator's robot file from your `Robot.java`, and prints `Robot file made
from Robot.java: simulator/robot.properties`.

**Why:** the robot file then always matches your code. The simulator keeps your own lines in it. When
you change the code, stop the simulator and start it again.
**More:** [A robot file from Robot.java](../simulator/02-your-robot-file.md#2-the-simulator-makes-it-from-robotjava), [Your own robot code](../reference/robot-file.md#the-commands).

## How do I make the simulator draw my part?

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
([how](../reference/robot-java.md#shapes-and-parts)).
**Why:** without `.looks(...)`, the simulator still runs, with a guessed box for each part (marked
`TODO measure` in the robot file). With it, the 3D view shows your robot, and the size check (18 in start
cube, 29 in tall) is right. Pieces also touch the intake where the real one is. Name the part
(`.part(...)`) too, so a roller's or a wheel's size comes from the parts catalog. **The easy way:** use
the simulator's **Edit robot** mode. Drag and size each part on the 3D robot, then copy the
`.looks(...)` lines it shows into `Robot.java`. After a change, stop the simulator and start it again.
**More:** [Shapes and parts](../reference/robot-java.md#shapes-and-parts) (every shape and setting),
[A robot file from Robot.java](../simulator/02-your-robot-file.md#2-the-simulator-makes-it-from-robotjava).

## How do I test my code with a script?

A script (a scenario file) presses buttons and checks the robot for you. The easy way is to **record**
it. In the simulator, click **Tests > Record a test**, drive, and click **Stop recording**. Keep the
ticked checks and click **Save**. The simulator then writes `simulator/scenarios/NAME.sim` for you
([Record a test](../tests/01-record-a-test.md)). Or write one by hand, for example
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

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
./gradlew :sim:scenario -Pfile=../simulator/scenarios/intake.sim
```

It runs your own code (`../TeamCode`, next to the library in your robot project).
**Why:** a script checks the same things every time, with no clicking, and catches a mistake before a meeting.
A scenario uses the FTC button names, which can differ from the Java names. `g2.rt`, `g2.lb`, `g2.a` are
the same, but `g2.dpadUp` is `g2.dpad_up` and `g2.leftStickY` is `g2.left_stick_y`.

**More:** [Record a test](../tests/01-record-a-test.md), [Scenario files](../reference/scenario-files.md) (every command and button name).
