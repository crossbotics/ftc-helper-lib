# Tuning and finders

**The questions on this page:**

- [Where do the numbers go?](#where-do-the-numbers-go)
- [How do I find a servo's open and closed numbers?](#how-do-i-find-a-servos-open-and-closed-numbers)
- [How do I find a lift's limits and heights?](#how-do-i-find-a-lifts-limits-and-heights)
- [How do I change a number while the robot runs?](#how-do-i-change-a-number-while-the-robot-runs)
- [How do I tune the drive for autonomous?](#how-do-i-tune-the-drive-for-autonomous)
- [How do I set the flywheel numbers?](#how-do-i-set-the-flywheel-numbers)
- [How do I build a shot table (speed for each distance)?](#how-do-i-build-a-shot-table-speed-for-each-distance)
- [How do I find my hood's stops and angles?](#how-do-i-find-my-hoods-stops-and-angles)

## Where do the numbers go?

In `Tunables.java`, one section per part (see [the lift recipe](04-mechanisms.md#how-do-i-add-a-lift-with-set-heights)).
A finder (a program that measures a number on the robot) shows lines like `Tunables.Claw: P1 = 0.210, P2 = 0.770`.
Copy them into that section, keep the other lines, and give P1 and P2 real names. A number you leave out
uses the library's default (INIT says which). The exception is a lift's or arm's `min` and `max`: you must write those.

**More:** [Tunables.java](../reference/tunables.md), [Finding your numbers](../tuning/README.md).

## How do I find a servo's open and closed numbers?

**With the five files:** you add nothing. Run **Tune and check** (the library puts it on the Driver Hub) and pick the Servo Finder ([Tune and check](../tuning/01-tune-and-check.md)).

**Without the five files:** a new file in TeamCode (also in the [starter kit](../starter-kit.md)). The whole file:

<!-- recipe: servo-finder -->
```java title="ServoFinder.java"
// The Servo Finder: lists every servo in the configuration. No robot needed.
@TeleOp(name = "Cookbook Servo Finder", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class ServoFinder extends ServoFinderOpMode {
}
```

**Use it:** put the robot on a block. Run the Servo Finder. Choose the servo
(D-pad, A). A servo cannot say where it is, so choose a start number close to where it sits now (D-pad,
A). Nudge it with the D-pad. At each end of its safe travel (right before it pushes on the frame), press X,
then B. Then move it to each position you need and press A. For example: fully open, A (that is P1);
closed, A (P2). Copy the line it shows (`Tunables.Claw: P1 = 0.210, P2 = 0.770`)
into `Tunables.java` and rename P1, P2 (`open`, `closed`). For a part with
[`.fullRange()`](04-mechanisms.md#how-do-i-get-my-servos-full-range) in Robot.java, press Y first, while you choose the servo.
**Why:** a servo pushed past its end hums, gets hot and breaks. The finder finds the safe numbers.
**More:** [Servo Finder](../tuning/03-servo-finder.md), [the order](../tuning/README.md#the-tuning-order).

## How do I find a lift's limits and heights?

**With the five files:** you add nothing. Run **Tune and check** (the library puts it on the Driver Hub) and pick the Motor Finder ([Tune and check](../tuning/01-tune-and-check.md)).

**Without the five files:** a new file in TeamCode (also in the [starter kit](../starter-kit.md)). The whole file:

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
**More:** [Motor Finder](../tuning/05-motor-finder.md).

## How do I change a number while the robot runs?

With the five files, you write no code for this. In the TeleOp (PRACTICE profile), after ▶, **hold GUIDE
for 1 second**, and the tuning menu opens. D-pad up/down chooses a number, and right/left changes it.
**A** saves it on the hub, and **B** lists the changed values. **Then copy each good value into
`Tunables.java`.** A value saved only on the hub is lost with a new Control Hub, and your teammates cannot see it.

**Why:** you see the effect at once, and you do not have to build and install again.
**More:** [Tuning](../tuning/16-live-tuning.md#open-it-and-change-a-number), [Add a lift: tune the lift live](../add-to-robot/01-lift.md#4-check-it-then-tune-it).

## How do I tune the drive for autonomous?

**With the five files:** you add nothing. Run **Tune and check** (the library puts it on the Driver Hub) and pick the Drive Tuner ([Tune and check](../tuning/01-tune-and-check.md)).

**Without the five files:** a new file in TeamCode. The whole file:

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
**More:** [Tune the drive first](../five-files/05-autos.md#tune-the-drive-first), [Drive Tuner](../tuning/08-drive-tuner.md).

## How do I set the flywheel numbers?

Before you have them, write `DEFAULT` in their place, or leave the lines out. The launcher then starts
from the parts catalog's numbers for its motor, and INIT says so. For a goBILDA 6000 RPM motor, these
are kF 11.7, kP 1.17, kI 0.117, and 60% of its top speed, 3600 RPM. Then run the **Flywheel Finder**
(in the [starter kit](../starter-kit.md)) and copy its line into `Tunables.Launcher` (see
[the launcher recipe](04-mechanisms.md#how-do-i-add-a-launcher-flywheel-and-feeder)). Do not copy another robot's gains,
because its team found them for its own wheel.

**Why:** the right `kP`..`kF` (the gains: how hard the motor pushes toward the target speed) make the
wheel get back to speed fast after each shot.
**More:** [Flywheel Finder](../tuning/06-flywheel-finder.md).

## How do I build a shot table (speed for each distance)?

**With the five files:** you add nothing. Run **Tune and check** (the library puts it on the Driver Hub) and pick the Shot Tuner ([Tune and check](../tuning/01-tune-and-check.md)).

**Without the five files:** a new file in TeamCode, `ShotTuner.java`. Run the [Aim Tuner](../tuning/12-aim-tuner.md) first.
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
**More:** [Shot Tuner](../tuning/13-shot-tuner.md).

## How do I find my hood's stops and angles?

**With the five files:** you add nothing. Run **Tune and check** (the library puts it on the Driver Hub) and pick the Hood Finder ([Tune and check](../tuning/01-tune-and-check.md)).

**Without the five files:** a new file in TeamCode, `HoodFinder.java` (first line `package org.firstinspires.ftc.teamcode;`):

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
**More:** [Hood Finder](../tuning/04-hood-finder.md).
