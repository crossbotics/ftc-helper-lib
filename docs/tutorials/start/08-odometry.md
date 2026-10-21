# Lesson 8: Odometry: where am I on the field?

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** your robot knows where it is on the field. Its autonomous drives to field points ("go to
x -30, y -30, facing 45 degrees") instead of distances, and keeps correcting on the way. TeleOp starts
where autonomous ended.

**You need:** lessons 1 to 6 (the last one: [Your first autonomous](06-first-autonomous.md), with the
Drive Tuner done). A **mecanum** drive (`driveTo` drives sideways). An odometry device (section 2). A
tape measure, some tape, and about 5 ft of clear floor. About an hour and a half, most of it mounting.

**The code:** the odometry robot, [`examples/.../odometrybot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot):
[`Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/Robot.java),
[`Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/Tunables.java),
[`Controls.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/Controls.java),
[`autos/ToTheGoal.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/autos/ToTheGoal.java)
and [`OdometryTuner.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/OdometryTuner.java).
It is the lesson robot's drive and intake, plus one odometry line.
**Reference:** [Odometry](../../modules/odometry.md).

---

## 1. Why odometry

Your lesson 6 autonomous says "drive 24 inches forward". The robot only knows how far its **drive
wheels** turned. But drive wheels slip: they spin when another robot pushes you, and skid when the
robot speeds up. After a bump, every later step is off, and the robot does not know it.

**Odometry** measures where the robot really went. Small unpowered wheels, called **pods**, are
pressed lightly onto the tiles. They carry no weight and drive nothing, so they do not slip: they just
roll and count. One pod counts forward and back, one counts sideways, and a turn sensor (an **IMU**)
counts turning. Add up every small move, many times a second, and the robot knows its **pose**: where
it is (x and y, in inches) and which way it faces (the heading, in degrees).

**Why not AprilTags this season?** In BIOBUZZ the tags are on the HIVE cells, and the cells move. A
tag tells you where you are compared with the tag, not with the field. So this season, odometry tells
you where you are; the camera is for aiming.

**The frame** (which way the numbers go). Stand at your drivers' wall and look at the field. It is the
same frame as the start spot in lesson 6:

```text
                    far wall
     +-----------------------------------+
     |                 y +               |
     |                 ^                 |
     |   x -  <--------+-------->  x +   |    (0, 0) = the middle of the field
     |                 v                 |
     |                 y -               |
     +-----------------------------------+
           your drivers' wall (you)
```

x is inches to the right, y is inches away from you. Heading 0 = facing away from you, and
counterclockwise is + (90 = facing left).

## 2. Which one to buy

| Choice | What it is | Parts and price (checked 2026-10-21) |
| --- | --- | --- |
| **goBILDA Pinpoint + 2 pods** (the lesson's choice) | a small odometry computer with its own IMU; two pods plug into it; it plugs into an I2C port | Pinpoint Odometry Computer V2, goBILDA 3110-0002-0002, $79.99, in stock on goBILDA's site. Two 4-bar odometry pods, goBILDA 3110-0001-0002, about $99.99 each (that price is from search results, **not checked on goBILDA's page**: check it before you order) |
| **SparkFun OTOS** | an optical sensor under the robot that watches the floor, like a computer mouse. No pods | SparkFun SEN-24904, $84.95, in stock on SparkFun's site |
| **Dead wheels** | three pods (or two and the Control Hub's IMU), each plugged into a motor port's encoder connector. No extra computer | the pods only; but they use three (or two) encoder ports |

**Why the lesson uses a Pinpoint:** it does the math itself about 1500 times a second, and the robot
reads the answer once per loop. Your loop speed does not change how good the pose is, and it uses no
encoder ports. The library expects goBILDA 4-bar pods unless you say otherwise.

## 3. Mount it, wire it, name it

1. **Mount the two pods** under the robot, springs pressing the wheels onto the tiles. One rolls
   **forward/back** (the **X pod**), one rolls **sideways** (the **Y pod**). They can sit anywhere;
   section 5 measures where.
2. **Mount the Pinpoint** flat, anywhere on the robot (goBILDA's guide shows the details).
3. **Plug the X pod into the Pinpoint's X port and the Y pod into its Y port.** Why it matters: the
   Pinpoint treats X as forward. Swapped, the robot thinks forward is sideways.
4. **Plug the Pinpoint into an I2C port** on the Control Hub. Port 0 already has the hub's own IMU, so
   port 1 is a good choice.
5. **Add it to the robot configuration** on the Driver Station: Configure Robot, edit your
   configuration, Control Hub, the I2C bus you used (e.g. I2C Bus 1), Add. Choose the type
   **goBILDA® Pinpoint Odometry Computer** and name it `pinpoint`. Save, and restart the robot.

An OTOS goes on an I2C port the same way: type **SparkFun OTOS**, name `otos`. Mount it 10 to 12 mm
above the tiles. Dead wheels: each pod's encoder cable goes into a motor port's encoder connector, and
the name is that motor port's configuration name ([odometry module](../../modules/odometry.md#three-dead-wheels)).

## 4. One line in Robot.java

This is the odometry robot's `Robot.java`. The odometry line is the only new thing:

```java
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(104)
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
// The Pinpoint on an I2C port, named "pinpoint" in the configuration. The offsets: the forward pod
// 84 mm RIGHT of the centre (-84), the sideways pod 168 mm BEHIND it (-168). The Odometry Tuner
// measures them.
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));
intake = add(Intake.motor("intake"));
```

**In your own `Robot.java`**, add two lines:

1. With the other `import` lines at the top:

   ```java
   import com.crossbotics.ftchelper.robot.Odometry;
   ```

2. In `define()`, right after your `drive(...)` line. Start with `0, 0`: the tuner measures the real
   numbers in section 5.

   ```java
   odometry(Odometry.pinpoint("pinpoint").offsetsMm(0, 0));
   ```

- `Odometry.pinpoint("pinpoint")`: a Pinpoint, named `pinpoint` in the configuration (the name must
  match exactly).
- `.offsetsMm(x, y)`: where the pods sit, in millimetres, from the robot's **centre** (the point it
  turns around). The first number: how far **left** of the centre the X pod is (right = negative).
  The second: how far **forward** of the centre the Y pod is (behind = negative). Why the library needs
  them: when the robot turns in place, a pod away from the centre rolls too, and the library must take
  that roll away.
- An OTOS: `odometry(Odometry.otos("otos").mountIn(forward, left, heading));`. Dead wheels:
  `Odometry.threeWheels(...)` or `twoWheels(...)`. All of them:
  [the odometry module](../../modules/odometry.md#robotjava-the-odometry-line).

**And one section in `Tunables.java`:**

```java
public static class Odometry {
    public static boolean checked = false;
}
```

`checked` is the same idea as `Tunables.Drive.calibrated` in lesson 6: a switch you flip **by hand**
after the tuner's last test passes. **Why:** a pod that counts the wrong way would send the robot the
wrong way at speed. Until `checked` is true, the `driveTo` steps of section 6 do nothing on the robot.

Build and run your TeleOp. The Driver Station now shows a `Pose` line. Push the robot forward by hand:
y should go up. Push it left: x should go down. (Wrong? Section 5 fixes it.)

## 5. The Odometry Tuner

The **Odometry Tuner** checks which way each pod counts and measures the offsets. Make a new Java file
`OdometryTuner.java` next to `Robot.java` in your `teamcode` folder ([how to make a file](../../start-from-zero.md#make-a-new-file-or-folder)),
and replace everything in it with this whole file (like the Drive Tuner in lesson 6, it only hands
the tuner your robot):

```java
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.finders.OdometryTunerOpMode;
import com.crossbotics.ftchelper.robot.RobotDefinition;

@TeleOp(name = "Odometry Tuner", group = "Finders")
public class OdometryTuner extends OdometryTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();   // your Robot.java, with its odometry(...) line
    }
}
```

The odometry robot's copy:
[OdometryTuner.java](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/OdometryTuner.java).
Put a 48 in strip of tape on the floor, and a tape mark at its start. Run the tuner. The sticks drive
slowly (30%); you can also push the robot by hand. Choose a test with the D-pad, start it with **A**,
end it with **B**. Each test starts the pose at 0.

| Test | You do | It tells you |
| --- | --- | --- |
| 1 Forward 48 in | drive or push the robot 48 in straight forward along the tape, then **B** | "Forward OK" and how far off it is, or that the X pod is reversed |
| 2 Left 24 in | drive or push it 24 in straight to its **left**, then **B** | "Left OK", or that the Y pod is reversed |
| 3 Spin 3+ turns | turn it in place at least 3 times (right stick), then **B** | the offsets: the numbers to change in your odometry line |
| 4 Loop and back | start on the tape mark, drive anywhere, come back onto the mark facing the same way, then **B** | within 1 in and 2 degrees: the odometry is good |

**What to copy, in this order:**

1. **Forward and Left** say "counted BACKWARD" (or RIGHT)? Add that pod to `.reversed(...)` in the
   odometry line, e.g. `odometry(Odometry.pinpoint("pinpoint").offsetsMm(0, 0).reversed("x"));`
   (`"y"` for the Y pod). Rebuild and run them again until both say OK. "More than 2% off": check that
   the pods are 4-bar pods (swingarm pods need `.podsSwingarm()`) and that the tape is 48 in.
2. **Spin** shows the numbers to change, like
   `Robot.java: in the odometry(...) line, .offsetsMm(-84.2, -167.5)`. Put those numbers in your own odometry line, in place of the `0, 0`. Everything else in
   the line (a `.reversed(...)`, the pod type) stays as you wrote it. Rebuild.
3. **Loop.** When it says "Back on the mark ... the odometry is good", it shows
   `Tunables.Odometry: checked = true`. Set it in `Tunables.java`:

   ```java
   public static boolean checked = true;
   ```

   Off by more? Run Forward, Left and Spin again, copy their lines, rebuild, try Loop again.

The lines are also saved in `finder-results.txt` on the Control Hub when you press STOP
([the results file](../../guides/finders.md#the-results-file)). An OTOS's Forward test gives
`Tunables.Odometry: linearScalar = ...` to copy instead, and its turn scale is
[measured by hand](../../modules/odometry.md#otos-turn-scale-by-hand).

## 6. Drive to field points

This is the odometry robot's autonomous, [`autos/ToTheGoal.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/autos/ToTheGoal.java):

```java
@Autonomous(name = "Odometry Bot To the Goal", group = "Examples", preselectTeleOp = "Odometry Bot TeleOp")
@Disabled
public class ToTheGoal extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .run("Intake on", robot.intake::in)
         .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         .driveTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.7);
    }
}
```

- `start("...", -56, -61, 0)`: the note for the drive team, and **the spot**: x -56 (left of the
  centre line), y -61 (near your wall), heading 0 (facing away). With odometry the spot matters: the
  pose starts there after START. **Why:** the points below are field places, so the robot must know
  where it starts. Find a spot by dragging the robot on the simulator's map before START (the line to
  copy is under the map, [how](../../guides/autonomous-builder.md#1-placing-the-robot)).
- `driveTo("To the goal", x, y, heading, 0.6)`: drive to that field point and turn to that heading at
  the same time, at most 60% power. It checks the pose every loop and keeps correcting. In the last
  8 inches it slows down in proportion to the distance left, so it stops on the point. It is done
  within 1 inch and 1 degree.
- `driveTo("Park", ..., 0, 0.7)`: to the park point, facing away from you again.
- **The points live in `Tunables.Auto`**, like lesson 6's distances, so you can change them in the
  tuning menu without a rebuild:

  ```java
  public static class Auto {
      public static double goalX = -30, goalY = -30, goalHeading = 45;
      public static double parkX = -56, parkY = -36;
  }
  ```

**Why `driveTo` and not `move`?** Three reasons:

1. **It corrects bumps and slip.** A `move` counts the drive wheels. If a partner bumps you, the move
   still "finishes", in the wrong place. `driveTo` sees the real pose, so it drives back on course and
   ends at the point.
2. **It ends at a field place.** "Go to (-30, -30)" is the same spot from any start. Change one step
   and the next ones still go to the same places. With `move`, every later step shifts too.
3. **It drives and turns at once,** straight at the point, even sideways.

**Your own file, whole.** Make a new Java file `ToTheGoal.java` in your `autos` folder, and replace
everything in it with this. No intake? Delete the two `run(...)` lines.

```java
package org.firstinspires.ftc.teamcode.autos;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.Tunables;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

// preselectTeleOp: your TeleOp's name, exactly as in @TeleOp(name = ...) in Controls.java
@Autonomous(name = "To the Goal", group = "Autos", preselectTeleOp = "Quick Start TeleOp")
public class ToTheGoal extends Auto<Robot> {

    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ's field is turned half a turn between the alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
         .run("Intake on", robot.intake::in)
         .driveTo("To the goal", Tunables.Auto.goalX, Tunables.Auto.goalY, Tunables.Auto.goalHeading, 0.6)
         .run("Intake off", robot.intake::stop)
         .parkWithSeconds(4)
         .driveTo("Park", Tunables.Auto.parkX, Tunables.Auto.parkY, 0, 0.7);
    }
}
```

And add the five points to the `Auto` section of your `Tunables.java` (keep lesson 6's numbers too):

```java
public static double goalX = -30, goalY = -30, goalHeading = 45;
public static double parkX = -56, parkY = -36;
```

**Before the tuner is done** (`checked = false`), each `driveTo` does nothing on the robot, and the
report says why: `To the goal: skipped: Tunables.Odometry.checked is false (run the Odometry Tuner first)`.

**RED:** with `mirrorForRed()` false (BIOBUZZ), RED uses the same numbers, seen from the red wall. On a
mirrored field (`mirrorForRed()` true), RED flips x and the heading. More, and the tolerance option
(`driveTo(label, x, y, heading, power, inches)`):
[the autonomous builder](../../guides/autonomous-builder.md#drive-to-a-field-point-driveto).

## 7. TeleOp starts where autonomous ended

You write nothing for this:

- When autonomous ends, its pose (where the robot is, and which way it faces) goes to TeleOp.
- TeleOp's INIT starts the odometry there (if autonomous ran in the last 5 minutes). So the `Pose`
  line carries on from autonomous.
- Field-centric "forward" comes from the odometry's heading. Holding BACK (with field-centric on)
  makes "forward" the way the robot faces now, and keeps x and y.

To show the pose your own way, add one line inside `buttons(...)` in `Controls.java`:

```java
show("Where", robot::pose);   // e.g. "(-30.0 in, -30.0 in, 45.0 deg)"
```

`robot.pose()` is the pose now: `robot.pose().x`, `.y` and `.headingDeg`. The odometry robot's
[`Controls.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot/Controls.java)
shows only x and y, with no decimals.

## Try it in the simulator

From the `ftc-helper-lib` folder, write a simulator robot file from the odometry robot's `Robot.java`,
then start the simulator with the library's examples:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.odometrybot.Robot -Pout=build/odometrybot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/odometrybot.properties
```

Open http://localhost:8780, choose **"Odometry Bot To the Goal"**, INIT, START. Watch it drive
diagonally to the goal while it turns to 45 degrees, then park. The `Pose` line shows where it is.
(The simulator runs `driveTo` even with `checked = false`.) When it ends, choose "Odometry Bot TeleOp":
the pose carries on from where autonomous stopped.

Then see what a badly tuned odometry does. Add this line at the end of `build/odometrybot.properties`
and run it again:

```properties
odometry.scaleError = 0.03   # the pods measure 3% too far
```

The robot now stops a little short of each point: odometry can only be as good as its tuning. On the
real robot, the tuner's Forward test finds this kind of error.
`odometry.driftDegPerMin` makes the heading creep instead ([simulator lines](../../modules/odometry.md#the-simulator)).

**Your own robot.** From the `ftc-helper-lib` folder:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

`:sim:fromRobot` writes the odometry lines into your `simulator/robot.properties` from the odometry
line in `Robot.java`. Choose "To the Goal", INIT, START, and read the report at the end. Run your
"Odometry Tuner" there too, to learn the tests before you try them on the robot.

## Check it on the robot

1. Push the robot by hand with the TeleOp running: forward = y up, left = x down, turning left =
   heading up. If not, the tuner's Forward and Left tests first.
2. The tuner: Forward, Left, Spin, copy, rebuild, then Loop until it passes. Set `checked = true`.
3. In INIT of "To the Goal", choose the **PRACTICE** profile. Set `Tunables.Drive.practiceMaxPower`
   low (0.4) for the first runs.
4. Put the robot exactly on the start spot. Keep a hand on STOP. Run it.
5. Measure where it ended against the point you wrote (`goalX`, `goalY`): within about an inch? Then
   run it again and gently push the robot sideways during the drive: it should come back on course.
6. Then run TeleOp: the `Pose` line should carry on from where autonomous ended.

## If something goes wrong

| What you see | Why, and what to do |
| --- | --- |
| `Pinpoint: X pod not found: check its cable` (or Y, or both) | a pod cable is out of the Pinpoint |
| `Pinpoint: calibrating (keep the robot still)` does not go away | the robot moved in INIT: keep it still and INIT again |
| INIT stops: `No GoBildaPinpointDriver named "pinpoint" in the active robot configuration` | the name in the configuration must match `Robot.java` exactly, with the Pinpoint type: section 3, step 5 |
| `skipped: Tunables.Odometry.checked is false` in the report | the tuner's Loop test, then `checked = true` |
| The pose goes the wrong way | a pod is reversed: Forward and Left tests, then `.reversed("x")` or `("y")` |
| The pose drifts when the robot only turns in place | the offsets: the Spin test |
| x and y look swapped | the X and Y pods are in each other's ports |
| The report says `NO_ODOMETRY` | the odometry stopped working during a `driveTo` (a pod or I2C cable): the robot stopped at once. Read the warning on top |
| It drives to the mirror spot on RED | `mirrorForRed()` must be false on BIOBUZZ's field |
| INIT stops: driveTo needs mecanum wheels | `driveTo` drives sideways: a tank robot uses `turnTo` and `move` |
| It circles the point or stops just short | `Tunables.Drive.turnKp`/`turnKd`, or `moveMinPower` (lesson 6) |
| TeleOp's field-centric is wrong after autonomous | the heading in `s.start(...)` is wrong |

## Next

You have finished the main track. From here:

- [The odometry module](../../modules/odometry.md): every kind (OTOS, dead wheels), every setting, the frame.
- [The autonomous builder](../../guides/autonomous-builder.md): everything an autonomous can do.
- [The finders](../../guides/finders.md): measure every number instead of guessing.
- [The tutorials page](../README.md): the under-the-hood tutorials.
