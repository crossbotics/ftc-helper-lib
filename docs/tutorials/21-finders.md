# Tutorial 21: Find your numbers

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** only small OpModes that start the library's ready-made finders, which measure your robot and print the code lines to copy.
The main track uses the same finders: [An intake, a lift and a claw](start/04-lift-and-claw.md).

**You will learn:** how to measure the numbers your code needs (servo ends, lift limits, flywheel
gains, wheel size, camera offset, shot table) with the **finders** (library OpModes that measure one kind of number each), and how to move the results into
your code.

**You need:** a robot with the parts you want to measure. Tutorials 14 (mechanisms) and 19 (Hardware Check).

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

A guessed servo position can push the claw into the frame until the servo burns out. A guessed
lift limit can pull the lift off its rails. A guessed wheel size makes every autonomous move the
wrong distance. The finders measure the real numbers, safely, and write the code line for you.

## 2. The smallest finder

```java
@TeleOp(name = "Servo Finder", group = "Finders")
public class ServoFinder extends ServoFinderOpMode {
}
```

Like the Hardware Check (tutorial 19), the library wrote the whole OpMode. It reads the robot
configuration, so you do not even give it a servo name: you choose one from a list. Press **GUIDE**
(the center button, or hold START for 1 second) in any finder for a help page with every button and
the steps.

## 3. Use the Servo Finder

1. Run it, choose the claw servo with the D-pad, press A. Choose a start point near where the claw
   sits now (D-pad; nothing moves yet), press A: it moves there.
2. Nudge it with the D-pad until the claw is fully open without straining. Press **X**.
3. Nudge it to fully closed (on a game piece). Press **B**.
4. Nudge it to "open just enough" and press **A** (saved as P1, "position 1").

The screen shows:

```java
PositionServo.create(hardwareMap, "claw").limits(0.180, 0.770)
    .position("P1", 0.210)
Tunables.Claw: P1 = 0.210
    // rename P1, P2... (open, closed); keep them between 0.180 and 0.770
```

Copy it into your code and rename P1 to "OPEN". With the [robot in five files](22-robot-in-five-files.md),
copy the `Tunables.Claw` line instead: `open = 0.210` in `Tunables.Claw`. Press STOP: the same lines are saved in
`/sdcard/FIRST/ftc-helper/finder-results.txt` on the hub.

## 4. The finders that need your code

The Drive Tuner needs your `RobotSettings` (one line, like `EasyTeleOp`). The Shot Tuner needs your
launcher and camera, and the Aim Tuner your camera (and a turret, or the drive to turn):

```java
public class ShotTuner extends ShotTunerOpMode {
    @Override protected Launcher createLauncher() { return new Robot(hardwareMap).launcher; }
    @Override protected TargetSource createCamera() { return WebcamTags.create(hardwareMap, "Webcam 1"); }
    @Override protected RobotConfig robotConfig() { return new RobotSettings(); }   // drive between spots
}
```

(This is the [starter kit](../starter-kit.md)'s `Robot` and `RobotSettings`. With the tutorials'
`RobotSettings`, write `RobotSettings.config()`.)

Use the SAME launcher set-up as your TeleOp (best: from your `Robot` class), so the numbers you find
are the numbers it uses.

## 5. The order

Hardware Check, then Servo and Motor finders, then Flywheel, then Drive Tuner, then Aim Tuner, then
Shot Tuner. Each one builds on the one before (the Shot Tuner, for example, needs a flywheel that
holds its speed and a camera that aims straight): the [guide](../guides/finders.md) explains each step.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| The list is empty | the robot configuration has no devices of that kind: check the Driver Station configuration |
| The servo jumps when it first moves | the start point was far from where it sat: choose a start point near its real position before pressing A |
| The motor will not move past a mark | hold LB to move past, then mark again |
| "Speed test" does nothing | mark both ends first (X and B) |
| Drive Tuner: "counts went DOWN" | a wheel motor direction is wrong: fix it in `RobotSettings` (Hardware Check) |
| Shot Tuner: "no target" | the camera does not see the target: check its status in INIT, light, and the tag in view |

## Try this

1. Run the Motor Finder on the lift and replace the limits in your code (five files: `Tunables.Lift`) with the measured ones.
2. Run the Flywheel Finder twice, with a full and a half-charged battery: compare the top speeds.

## Next

The [starter kit](../starter-kit.md): put the numbers you found into a whole robot program.
