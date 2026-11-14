# Tutorial 21: Find your numbers

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

With the five files, every finder is already in the **Tune and check** menu: [Tuning](../tuning/README.md).
Build the finders by hand here to see how to make a finder OpMode. Or build one to run it as its own OpMode on a
robot without the five files.

**You will build:** small OpModes that start the library's ready-made finders. A **finder** is a library
OpMode that measures one kind of number on your robot and prints the code lines to copy.

**You need:** a robot with the parts you want to measure.

**Programs:** the starter kit's
[`ServoFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/ServoFinder.java),
[`MotorFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/MotorFinder.java),
[`FlywheelFinder.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/FlywheelFinder.java),
[`DriveTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/starter/DriveTuner.java),
and the examples
[`ExampleShotTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/ExampleShotTuner.java) and
[`ExampleAimTuner.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/ExampleAimTuner.java).

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

The steps, button by button: [Servo Finder](../tuning/03-servo-finder.md). At the end, the screen shows
the lines to copy:

```java
PositionServo.create(hardwareMap, "claw").limits(0.180, 0.770)
    .position("P1", 0.210)
Tunables.Claw: P1 = 0.210
    // rename P1, P2... (open, closed); keep them between 0.180 and 0.770
```

Without the five files, copy the first two lines into your code and rename P1 to "OPEN". With the five
files, copy the `Tunables.Claw` line instead: `open = 0.210` in `Tunables.Claw`. When you press STOP, the
finder saves the same lines in `/sdcard/FIRST/ftc-helper/finder-results.txt` on the hub.

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

Each finder builds on the ones before it. For example, the Shot Tuner needs a flywheel that holds its
speed and a camera that aims straight. The order, and one page per finder: [Tuning](../tuning/README.md).

## If something goes wrong

| Problem | Fix |
| --- | --- |
| The list is empty | The robot configuration has no devices of that kind. Check the Driver Station configuration. |
| The servo jumps when it first moves | The start point was far from where the servo sat. Choose a start point near its real position before you press A. |
| Drive Tuner: "counts went DOWN" | A wheel motor direction is wrong. Fix it in `RobotSettings` (Hardware Check). |
| Shot Tuner: "no target" | The camera does not see the target. Check its status in INIT, the light, and the tag in view. |

Each finder's own problems: the "Fix a mistake" table on its page in [Tuning](../tuning/README.md).

## Try this

1. Run the Motor Finder on the lift. Replace the limits in your code (five files: `Tunables.Lift`) with
   the measured ones.
2. Run the Flywheel Finder twice, with a full and a half-charged battery. Compare the top speeds.

---

**Next:** this is the last tutorial. Back to [the tutorials](README.md), or put the numbers into a whole
robot program with the [starter kit](../starter-kit.md).
**Look it up:** [finders.md](../modules/finders.md)
