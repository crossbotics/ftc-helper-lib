# Tutorial 21: Find your numbers

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** only small OpModes that start the library's ready-made finders. The finders measure
your robot and print the code lines to copy.
The main track uses the same finders: [An intake, a lift and a claw](start/04-lift-and-claw.md).

**You will learn:** how to measure the numbers your code needs (servo ends, lift limits, flywheel gains,
wheel size, camera offset, shot table), and how to move the results into your code. You measure with
the **finders**: library OpModes that measure one kind of number each.

**You need:** a robot with the parts you want to measure. Do Tutorials 14 (mechanisms) and 19 (Hardware
Check) first.

**Programs:** the starter kit's
[`ServoFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/ServoFinder.java),
[`MotorFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/MotorFinder.java),
[`FlywheelFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/FlywheelFinder.java),
[`DriveTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/DriveTuner.java),
and the examples
[`ExampleShotTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/ExampleShotTuner.java) and
[`ExampleAimTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/ExampleAimTuner.java).
**Reference:** [finders.md](../modules/finders.md). Step by step: [Finders guide](../guides/finders.md).

---

## 1. Why not just guess?

A guessed servo position can push the claw into the frame until the servo burns out. A guessed lift
limit can pull the lift off its rails. A guessed wheel size makes every autonomous move the wrong
distance. The finders measure the real numbers safely, and write the code line for you.

## 2. The smallest finder

```java
@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode {
}
```

Like the Hardware Check (tutorial 19), the library wrote the whole OpMode. It reads the robot
configuration, so you give it no servo name: you choose one from a list. In any finder, press **GUIDE**
(the center button, or hold the gamepad's START button for 1 second). It shows a help page with every
button and the steps.

## 3. Use the Servo Finder

1. Run the Servo Finder. Choose the claw servo with the D-pad, and press A. Choose a start point near
   where the claw sits now (D-pad up/down; nothing moves yet). Press A: the servo moves there.
2. Nudge the servo with D-pad up/down until the claw is fully open without straining. Press **X**.
   D-pad left/right changes the step (shown on the screen). Start big (0.05) to get close. Then go down
   to 0.005 or 0.001 for the exact spot.
3. Nudge the servo to fully closed (on a game piece). Press **B**.
4. Nudge it to "open just enough" and press **A**. The finder saves it as P1 ("position 1").

The screen shows:

```java
PositionServo.create(hardwareMap, "claw").limits(0.180, 0.770)
    .position("P1", 0.210)
Tunables.Claw: P1 = 0.210
    // rename P1, P2... (open, closed); keep them between 0.180 and 0.770
```

Copy it into your code and rename P1 to "OPEN". With the [robot in five files](start/01-robot-in-five-files.md),
copy the `Tunables.Claw` line instead: `open = 0.210` in `Tunables.Claw`. When you press STOP, the finder
saves the same lines in `/sdcard/FIRST/ftc-helper/finder-results.txt` on the hub.

## 4. The finders that need your code

The Drive Tuner needs your `RobotSettings` (one line, like `EasyTeleOp`). The Shot Tuner needs your
launcher and camera. The Aim Tuner needs your camera, and a turret or the drive to turn:

```java
public class ShotTuner extends ShotTunerOpMode {
    @Override protected Launcher createLauncher() { return new Robot(hardwareMap).launcher; }
    @Override protected TargetSource createCamera() { return WebcamTags.create(hardwareMap, "Webcam 1"); }
    @Override protected RobotConfig robotConfig() { return new RobotSettings(); }   // drive between spots
}
```

This code uses the [starter kit](../starter-kit.md)'s `Robot` and `RobotSettings`. With the tutorials'
`RobotSettings`, write `RobotSettings.config()`.

Use the SAME launcher set-up as your TeleOp (best: from your `Robot` class). Then the numbers you find
are the numbers your TeleOp uses.

## 5. The order

Run them in this order: Hardware Check, then the Servo and Motor finders, then Flywheel, then Drive
Tuner, then Aim Tuner, then Shot Tuner. Each one builds on the one before. For example, the Shot Tuner
needs a flywheel that holds its speed and a camera that aims straight. The [guide](../guides/finders.md)
explains each step.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| The list is empty | The robot configuration has no devices of that kind. Check the Driver Station configuration. |
| The servo jumps when it first moves | The start point was far from where the servo sat. Choose a start point near its real position before you press A. |
| The motor does not move past a mark | Hold LB to move past, then mark again. |
| "Speed test" does nothing | Mark both ends first (X and B). |
| Drive Tuner: "counts went DOWN" | A wheel motor direction is wrong. Fix it in `RobotSettings` (Hardware Check). |
| Shot Tuner: "no target" | The camera does not see the target. Check its status in INIT, the light, and the tag in view. |
| Shot Tuner: "No shot to mark" | A (HIT) and B (MISS) mark shots already fired. Shoot first (RB). |
| Shot Tuner: "the flywheel ... sagged" | The feeder pushed the piece in while the flywheel was slow. Wait for Ready, charge the battery, and check the Flywheel Finder gains. |

## Try this

1. Run the Motor Finder on the lift. Replace the limits in your code (five files: `Tunables.Lift`) with
   the measured ones.
2. Run the Flywheel Finder twice, with a full and a half-charged battery. Compare the top speeds.

## Next

The [starter kit](../starter-kit.md): put the numbers you found into a whole robot program.
