# Tutorial 18: Vision aiming (webcam or Limelight 3A)

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** a drive program that turns the robot toward an AprilTag (a printed square code on the field) while the driver holds a button.
The main track adds a camera with less code: [A shooter, a camera and a turret](start/09-shooter-and-camera.md).

**You will learn:** how to find AprilTags with a webcam or a Limelight 3A through ONE interface,
what bearing and distance mean, and how to aim the drivetrain (or a turret) at a tag safely.

**You need:** the drivetrain and `RobotSettings` (tutorial 09), and a webcam named `Webcam 1` or a
Limelight named `limelight` (with an AprilTag pipeline set up on its web page). An AprilTag to point at.

**Full program:** [`Tut18VisionAim.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut18VisionAim.java)
**Reference:** [vision.md](../modules/vision.md)

---

## 1. What the camera tells you

```mermaid
flowchart LR
    C["camera picture"] --> O["TargetObservation"]
    O --> B["bearingDeg: how far to turn<br/>+ = target is LEFT"]
    O --> D["distanceIn: how far away<br/>NaN if unknown"]
    O --> T["timeNanos: when the picture was taken"]
```

`NaN` means "not a number": the camera could not tell the distance.

**Bearing** is the angle between "straight ahead" and the target. Positive = the target is to the
LEFT (counterclockwise, like the IMU). The Limelight reports the opposite sign; the library flips it
for you, so both cameras agree.

## 2. One interface, two cameras

An **interface** is a list of methods that several classes promise to have. `TargetSource` is one:
both camera classes have its methods, so your code can use either.

```java
TargetSource camera = menu.get("Camera").equals("Limelight")
        ? LimelightTags.create(hardwareMap, "limelight").pipeline(0)
        : WebcamTags.create(hardwareMap, "Webcam 1").resolution(640, 480);
camera.start();
```

After this line, the program never cares which camera it is: `camera.update()`, `camera.target("RED SCORING")`,
`camera.status()`. Next year's camera is a new class with the same methods.

The webcam needs 1-2 s to start, so start it in INIT. The INIT loop shows `camera.status()` and how
many tags it sees: check that before START.

## 3. Aim the drivetrain

`AimController` is a small PID for aiming: the two numbers are kP and kD (Tutorial 07).

```java
AimController aim = new AimController(0.02, 0.003).deadband(1.5).maxPower(0.5).maxAgeMs(150);
...
if (gamepad1.right_bumper) {
    turn = aim.turnPower(target);     // same sign as the drive's turn (clockwise +)
} else {
    aim.reset();
}
drive.drive(forward, strafe, turn);
```

While the driver holds RB, the robot turns toward the tag; they keep control of driving.

**Safety built in:** `turnPower` returns **0** when there is no target, when the picture is older than
150 ms, or when there is no bearing. The robot never turns toward a tag it cannot see NOW. (Without
this, a robot keeps spinning toward where a tag WAS.)

`turnPower` already uses the drive's sign (clockwise +), so no minus sign is needed. (Bearings are
counterclockwise +; `ccwPower` gives that sign if you ever need it. See [Signs and units](../conventions.md).)

## 4. Feel it

```java
AimRumble feel = new AimRumble(gamepad1).setThreshold(3);
feel.update(target.bearingDeg);       // buzzes inside 3 degrees, stronger when closer
```

## 5. With a turret instead

```java
turret.aimAtTarget(camera.target("RED SCORING"), true);    // true = the camera turns with the turret
```

`aimAtTarget` uses a picture only if it is fresh, and each picture only ONCE. (Turning "by the
bearing" with the same old picture again and again would push the turret further each loop.) No
fresh target: the turret holds where it is. And `launcher.setDistance(target.distanceIn)` picks the
shot from the table (tutorial 17); check `target.hasDistance()` first.

**In the robot in five files** all of this is built in: `g1.rb.toggle(robot.aimMode())` aims the whole
robot (the driver's turn stick still wins), `g2.rumbleWhenAimed()` buzzes, a turret's `follows(camera)`
follows (whole-robot aim and a following turret are separate modes: use one), and with odometry and `Tunables.Aim.goalX, goalY` they keep working from the odometry when the
camera loses the goal. See [Lesson 9](start/09-shooter-and-camera.md#9-when-the-camera-loses-the-goal-the-odometry)
and [robot.md: Aim](../modules/robot.md#aim).

## 2026-27 notes

- BIOBUZZ tags are on the HIVE cells, and the cells move: good for aiming, not for working out
  where the robot is on the field.
- SDK 12 "clusters": groups of tags with one aiming point. The SDK puts the point 7.2 in toward the
  tags' printed top and 5.6 in behind them: for BIOBUZZ about 9 in beyond the tag row and 1 in higher
  (not the CELL opening; this comes from the SDK's numbers and the manual, not yet seen on a real field).
  BIOBUZZ has 4: "RED SCORING", "RED AUDIENCE", "BLUE AUDIENCE", "BLUE SCORING" (the 4 tags under each
  CELL). `WebcamTags` reports each with id `TargetObservation.CLUSTER` and its name. `LimelightTags`
  reports the same groups by the same names (a Limelight sees single tags; the library groups them
  with `TagGroup`, aimed at the middle of the tag row), so `camera.target("RED SCORING")` works with
  either camera. With a Limelight, single tags also work by number (`camera.target(31)`). The webcam
  reports a tag that belongs to a cluster ONLY as part of its cluster, so `camera.target(31)` gives null
  there (to see single tags with a webcam, give `WebcamTags.library(...)` a tag library without clusters).

## Run it

1. INIT: choose the camera and the target with the D-pad, press A. Wait for "STREAMING" (webcam) or
   "pipeline 0" (Limelight) and "Targets seen: 1" or more (a Limelight shows each tag and the group).
2. START. Point the robot so the tag is off to the left. Hold RB: the robot turns left until aimed;
   a buzz; "Aimed: true".
3. Cover the camera while holding RB: the robot stops turning (no target = 0 power).

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Webcam "OPENING..." forever | USB cable / hub port; the configuration name must match |
| Limelight "not connected" | Ethernet/USB cable; its name in the configuration; restart the robot |
| Targets seen 0 | pipeline is not AprilTag (Limelight); tag family; too far: higher resolution or decimation 1 |
| Turns the wrong way | a minus sign was added in front of `aim.turnPower(...)`: remove it |
| Shakes around the tag | lower kP or raise the deadband |
| Distance NaN | webcam: the tag is not in the SDK's library; Limelight: no 3D pose in the pipeline (set tag size) |

## Try this

1. Aim at the nearest tag: pick the observation with the smallest `distanceIn`.
2. Show "LOCKED" in big letters at the top when `aim.isAimed()`.
3. Add `offset(...)` when the camera is not centered on the shooter. The **Aim Tuner**
   ([tutorial 21](21-finders.md)) finds the number.

## Next

[Check the hardware (lesson 2b, tutorial 19)](19-testing-procedures.md)
