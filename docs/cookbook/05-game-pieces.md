# Game pieces

A `Pieces` line in `Robot.java` counts the pieces the robot holds, and knows which kind fires next. It
uses a sensor at the intake, and (optional) one at the "ready to fire" slot. Measure each kind's color
(or size) with the Piece Tuner, and write it in `Tunables.java`. The full details: [Pieces and PieceSensor](../reference/parts/pieces.md).

**The questions on this page:**

- [How do I know which piece is in the robot, and how many?](#how-do-i-know-which-piece-is-in-the-robot-and-how-many)
- [How do I stop the intake when the robot is full?](#how-do-i-stop-the-intake-when-the-robot-is-full)
- [How do I tell pieces apart by size, or with a second camera?](#how-do-i-tell-pieces-apart-by-size-or-with-a-second-camera)
- [How do I use the count (buzz, LEDs, the shot table)?](#how-do-i-use-the-count-buzz-leds-the-shot-table)
- [How do I let the next piece pick the shot table?](#how-do-i-let-the-next-piece-pick-the-shot-table)
- [How do I tune the piece sensors?](#how-do-i-tune-the-piece-sensors)

## How do I know which piece is in the robot, and how many?

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
**More:** [Pieces and PieceSensor](../reference/parts/pieces.md).

## How do I stop the intake when the robot is full?

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
**More:** [Intake](../reference/parts/intake.md).

## How do I tell pieces apart by size, or with a second camera?

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
**More:** [the sensors compared](../reference/parts/pieces.md).

## How do I use the count (buzz, LEDs, the shot table)?

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
// The count went wrong? Hold G2's left stick button 1 s = count 0 (its line: below),
// and hold the intake's RT 1 s to pull in although the count says full.
```

**Why:** `whenFull()`, `whenEmpty()` and `whenNextIs("POLLEN")` (one kind) are conditions that any part
can watch: a buzz, a light (the [Lights](14-lights.md) recipes), or an action that waits. For "either NECTAR",
the recipe asks `next()` (the next kind's name) whether it ends with `NECTAR`. When the count is wrong (for example, after you take pieces out by hand), a button sets it to 0. Add
this line to `Controls.java`, inside `buttons(...)`. Then hold G2's left stick button for 1 s:

<!-- recipe: buttons-pieces -->
```java title="Controls.java"
g2.leftStickButton.heldFor(1.0, robot.pieces::clear).says("piece count = 0");
```
**More:** [hooks for other parts](../reference/parts/pieces.md).

## How do I let the next piece pick the shot table?

**Where:** `Robot.java`, inside `define()`, on your launcher line (the Pieces line must be above it). Also
two tables in your `Tunables.Launcher` section ([one table per piece](06-aiming-and-shooting.md#how-do-i-use-one-shot-table-per-game-piece)).

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
**More:** [Launcher: shot tables](../reference/parts/launcher.md), [Add a launcher: the next piece picks the table](../add-to-robot/05-launcher.md#know-which-piece-fires-next).

## How do I tune the piece sensors?

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
**More:** [Piece Tuner](../modules/finders.md#piecetuneropmode).
