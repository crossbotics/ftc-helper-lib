# Add a camera

A camera that finds your alliance's goal by its AprilTags, so the robot can aim and pick its shot speed.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: a Limelight 3A or a webcam. In the robot configuration, call it `limelight` (or
  `Webcam 1` for a webcam).
- AprilTags to look at: the field's goal, or printed tags.
- For a Limelight: a laptop and the Limelight's USB-C cable.

## 1. What it is

A **camera** finds the goal and says how far away it is and in which direction. AprilTags are the square
black-and-white codes on the field.

- **It knows each alliance's goal.** `.goal("BLUE SCORING", "RED SCORING")` names each alliance's HIVE.
  The camera gives this match's goal only, never the other alliance's.
- **It aims only at the up CELL.** In BIOBUZZ, teams can score only in the CELL whose tags read right side
  up. The camera never aims at a down CELL.
- **Everything that aims uses it:** a turret that follows the goal, the launcher's speed from the
  distance, whole-robot aim and the aim buzz.
- **It tests the light at INIT.** Each place has different light. The camera tries a few settings and
  keeps the one that sees the most tags.

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class (`public Camera camera;`). The line goes in
`define()`, after the drive and **before** the parts that use it (a turret, a launcher).

<!-- recipe: camera-lighting -->
```java title="Robot.java"
// In INIT the Limelight tries these pipelines (your usual one FIRST) and keeps the best one.
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
// A webcam: Camera.webcam("Webcam 1").autoExposure().goal(...) tries the camera's own
// exposure, then 3, 6 and 12 ms.
```

- `.autoPipelines(0, 1, 2)`: the lighting test at INIT. A **pipeline** is a set of camera settings that
  you save on the Limelight by number. Put your usual one first, because it wins a tie.
- A webcam: `Camera.webcam("Webcam 1").autoExposure()`. It tries exposures in place of pipelines.
- A HuskyLens works too, with fewer features: no lighting test, and a rough distance
  ([Camera: A HuskyLens](../reference/parts/camera.md#a-huskylens)).
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

**Use it in the other parts.** Add `.follows(camera)` to the turret ([Add a turret](06-turret.md)). Add
`.speedFromDistance(camera)` to the launcher, so its speed comes from the shot table:

**Where:** `Robot.java`, at the end of the launcher's line, before the `);`.

```java title="Robot.java"
.speedFromDistance(camera)
```

**Fire only when aimed** (no turret): add this check to the launcher's line too. It feeds a piece only when
the camera sees the goal now and the robot points at it. So the robot never fires blind. It needs
`import com.crossbotics.ftchelper.robot.Aim;`.

```java title="Robot.java"
.requireReady("aimed", () -> aim().stage() == Aim.Stage.LOCKED)
```

With a turret, use the turret's check in place of this one ([Add a turret](06-turret.md#actionsjava-and-autos-use-it-in-steps)).

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The section's name is the camera's name in CamelCase:
`"limelight"` reads `Tunables.Limelight`, and `"Webcam 1"` reads `Tunables.Webcam1`.

<!-- recipe: tunables-camera -->
```java title="Tunables.java"
public static class Limelight {                                 // "limelight" in the configuration
    public static double pipeline = 0;                          // a Limelight: the pipeline to use (0)
    public static boolean autoLighting = true;                  // try a few settings at INIT, keep the best (true)
    public static double pitchDeg = DEFAULT;                    // its tilt up, degrees (0 = level)
}
```

- `pipeline`: the pipeline to use when the lighting test is off.
- `autoLighting`: `true` runs the lighting test at each INIT. At an event, you can set it to `false`
  ([Camera lighting: At an event](../tuning/11-camera-lighting.md#at-an-event)).
- `pitchDeg`: the camera's tilt up, in degrees. Then the camera gives the direction and the distance along
  the floor.
- The camera reads these numbers once, at INIT. They are not in the tuning menu. Change them in
  `Tunables.java` and install again.

**Aiming numbers** are in a section with a fixed name, `Tunables.Aim`: how whole-robot aim turns, and the
goal's place on the field for the odometry backup. Every line is optional ([Aim](../reference/parts/aim.md)).

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. The camera itself needs no button. These lines use what
it sees.

The aim buzz: gamepad 2 buzzes while the aim is close.

<!-- recipe: aim-rumble -->
```java title="Controls.java"
// Gamepad 2 buzzes while the aim is close: the closer, the stronger (Tunables.Aim.rumbleDeg).
// It works for a turret that follows the goal and for whole-robot aim.
g2.rumbleWhenAimed();
```

**No turret?** The whole robot turns to face the goal while the driver holds RB. Do not use it with a
turret that follows the goal: INIT stops.

<!-- recipe: aim-whole-robot -->
```java title="Controls.java"
// Hold RB: the robot turns to face the goal by itself; the driver keeps forward and sideways.
g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off).says("hold: aim the robot at the goal");
// Or a toggle. Careful: while it is on, the robot turns by itself, even after you let go.
// g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();
```

**The driver still wins:** while aim mode is on, a push of the turn stick turns the robot as the stick says.
Let go, and the aim takes over again.

**Aim only when you shoot:** add `public static boolean aimOnFire = true;` to `Tunables.Aim`. Then, with aim
mode off, RB with the goal in view turns the robot to the goal, fires, and gives the turn stick back. It
waits at most `aimOnFireSec` (0.5 s), so a press always fires. Tune whole-robot aim first: it uses the same
numbers ([Quick turn on fire](../reference/parts/aim.md#quick-turn-on-fire)).

To run the lighting test again in a match, put `relight()` on a button
([Camera: Buttons to add](../reference/parts/camera.md#buttons-to-add)).

### Actions.java and autos: use it in steps

**Where:** an autonomous in `autos/`, at the shooting spot. Three ready-made steps aim, get ready and fire,
with nothing in `Actions.java`:

```java title="autos/YourAuto.java"
.aimAtGoal("Aim", 4, 45)                      // turns the robot; goal not seen: looks 45 degrees each way
.readyToLaunch("Ready", 3)                    // spins up, waits until at speed and aimed
.action(robot.launcher.fireAllAction())       // fires every piece it holds, only when aimed
```

- `aimAtGoal` turns the whole robot to the goal (at most 4 s). When the camera does not see the goal, the
  robot turns slowly up to 45 degrees each way to find it. With a turret that follows the goal, it waits for
  the turret instead.
- `readyToLaunch` waits until the wheel is at speed and every launcher check is true (at most 3 s).
- With the "aimed" check, a robot that never found the goal fires nothing.
- **Start where the camera sees the goal.** In BIOBUZZ, at the start, our HIVE's up CELL is on the HIVE's
  right side, seen from our wall ([Aim in an auto](../reference/parts/aim.md#aim-in-an-auto)).

## 3. Find its numbers

1. **Camera lighting** ([how](../tuning/11-camera-lighting.md)): for a Limelight, save 3 pipelines on a
   laptop (usual, bright and dim light).
2. **`pitchDeg`:** put a phone level app on the camera's face. Read the tilt up, in degrees.
3. **Aim Tuner** ([how](../tuning/12-aim-tuner.md)): it finds `cameraOffset`, when the camera is beside
   the launcher. Do not measure it: the tuner finds it from test shots.
4. **Shot Tuner** ([how](../tuning/13-shot-tuner.md)): the shot table, a speed for each distance. Do it
   again when you change `pitchDeg`.

## 4. Check it, then tune it

**Check it:**

1. Turn the robot so that the camera sees a tag. Press INIT.
2. Read the lighting line, for example `limelight lighting : pipeline 2 (9 of 10 pictures saw tags)`. When
   the camera sees no tag, this line is a warning.
3. Look at the camera's picture ([See what the camera sees](../tuning/12-aim-tuner.md#see-what-the-camera-sees)).
   The tags must be sharp, and not too dark or too bright.
4. Press ▶. Point the robot at your goal. The Goal line must say **seen**, then
   `Goal : LOCKED (camera, 52 in, 0.8 deg off)` when the robot (or the turret) points at it.

**Tune it:** the aim numbers in `Tunables.Aim` (`turnKp`, `turnKd`) only change whole-robot aim. Change
them with [Live tuning](../tuning/16-live-tuning.md). Then put the tuned numbers in `Tunables.java`.

## More for your camera

### When the camera loses the goal: the odometry

An arm, another robot or a bad angle can hide the tags. A robot with odometry
([Add odometry](09-odometry.md)) knows where it is, so it can work out where the goal is too. Tell it where
the goal is:

**Where:** `Tunables.java`, in the `Tunables.Aim` section.

```java title="Tunables.java"
public static class Aim {
    public static double goalX = 13;                            // the goal: inches right of the center line
    public static double goalY = -13;                           // the goal: inches from the center, - = our half
    public static boolean mirrorForRed = false;                 // BIOBUZZ: false
}
```

- `goalX`, `goalY` are field numbers, seen from your own drivers' wall, as in `driveTo`
  ([Field numbers](../building-autos/01-what-an-auto-can-do.md#field-numbers)). BIOBUZZ: the up CELL at the
  start is 13 in right of the center line and 13 in toward your wall. Put the point where the camera
  measures to (the tags), so the camera's distance and the odometry's agree.
- `mirrorForRed`: BIOBUZZ is `false`, because the field looks the same from each wall. On a mirror-image
  field, write `true`. INIT shows the choice on its `Aim :` line. Check it before the match.
- You need nothing more. The turret and the shot table use the camera while it sees the goal, and the
  odometry while it does not: `Turret : 12 deg  AIMED  following: odometry`. A robot with no camera uses
  `.followsOdometry()` on the turret and `.speedFromOdometry()` on the launcher.

**The camera comes first,** because the odometry drifts a little in a match. The odometry needs a good
start, so the backup stays off until the pose is set: an autonomous ran first, or the drivers parked on the
reset spot and held gamepad 1 START for 1 s ([Add odometry: TeleOp starts where autonomous ended](09-odometry.md#teleop-starts-where-autonomous-ended)).
It also stays off while it says the goal is farther than the field allows. Every setting:
[Aim: The goal point](../reference/parts/aim.md#the-goal-point).

### Shooting while moving

A piece shot from a moving robot keeps the robot's speed. At 30 in/s sideways, a piece in the air for
0.8 s lands 24 in to the side. So the robot aims that far the other way, at a "virtual goal". It needs
odometry, for the robot's speed.

**Where:** `Tunables.java`, in the `Tunables.Aim` section.

```java title="Tunables.java"
public static boolean shootOnMove = false;   // true only after testing it on the real robot
public static double flightSec = 0.8;        // seconds in the air: measure it
public static double shooterForwardIn = -4;  // the turret's pivot: inches ahead of the odometry's center
public static double shooterLeftIn = 0;      // the turret's pivot: inches left of the odometry's center
```

- **Measure `flightSec`** with a phone's slow-motion video (240 pictures a second). Film a shot from the
  side, count the pictures from the launcher to the goal, and divide by 240 (192 pictures = 0.8 s).
- **The shooter's place:** do not measure from the robot's middle. Measure from the edges, in inches:
  `shooterForwardIn` = (half the robot's length) - (the pivot's distance from the FRONT edge).
  `shooterLeftIn` = (half the robot's width) - (the pivot's distance from the LEFT edge). Example: a robot
  17 in long with the pivot 12.5 in from the front: 8.5 - 12.5 = -4 (4 in behind the middle).
- **Test it:** first shoot standing still: the shots must land as before. Then drive slowly sideways past
  the goal and shoot. Misses toward the way the robot drives: `flightSec` is too short. Misses the other
  way: it is too long.
- A button can switch it off in a match: `g2.a.toggle(robot.aim().movingShotMode()).says("shoot on the move on/off");`.

Every setting, a camera on the body, longer flights: [Shooting on the move](../reference/parts/aim.md#shooting-on-the-move).

### Try it in the simulator

The turret robot has a camera on a turret, and a shot table for each piece. Make its simulator file from
its `Robot.java`, then run it.

**Where:** the Terminal, in the `ftc-helper-lib` folder.

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.turretbot.Robot -Pout=build/turretbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/turretbot.properties
```

Open http://localhost:8780. Choose "TurretBot TeleOp", pick the alliance in INIT (gamepad 1 X = BLUE,
B = RED), and press ▶. Drive until the goal is in view. The turret follows it, and the speed changes with
the distance.

- **Where to stand:** the tags are under the up CELL, so the camera must be below and in front of them.
  RED: about x 57, y -13, facing the HIVE (heading 90). BLUE: about x -57, y 13 (heading -90). Drag the
  robot there on the map.
- Spin up (LB) and fire (RB). The POLLEN table was tuned in the simulator, so POLLEN shots go in. The
  NECTAR table is example numbers, so expect misses.
- **Whole-robot aim:** do the same with `flywheelbot` (`-ProbotClass=com.crossbotics.ftchelper.examples.flywheelbot.Robot -Pout=build/flywheelbot.properties`).
  Choose "FlywheelBot TeleOp", drive until the goal is in view, and press RB on gamepad 1.
- Your own robot: [Simulator](../simulator/README.md).

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| `limelight: not found: no Limelight called "limelight"` | The name in the robot configuration is different (case counts), or the Limelight is not in it. The OpMode still runs, without the camera |
| `limelight lighting: NO TAGS SEEN` (after ▶) | No pipeline saw a tag at INIT. Have a tag in view at INIT. Check each pipeline on the Limelight's page |
| `limelight lighting: no pictures from pipeline 2 (missing?)` | That pipeline is not set up on the Limelight. Make it, or take it out of `autoPipelines(...)` |
| `Goal : not seen (RED SCORING is upside down (a down CELL))` | The camera sees only a down CELL. Drive to see the up CELL |
| The speed never changes with distance | No goal in view. Check `pipeline` and the `goal(...)` names |
| INIT stops: `robot.aimMode() ... separate modes` | The turret follows the goal. Remove the `aimMode()` line, or `.follows(...)` on the turret |
| Whole-robot aim stops a few degrees off | Raise `turnKp`, or set `minTurnPower` in `Tunables.Aim` |
| It is aimed, but the shots go wide by the same angle | The camera is beside the launcher. Run the Aim Tuner |
| `limelight lighting: trying 1 of 3: pipeline 0 (no tags yet: face a tag)` (INIT) | No pipeline saw a tag yet. Turn the robot toward a tag. The test goes on until ▶ |
| `Aim: no goal` | Whole-robot aim is on, but the camera does not see the goal, and there is no odometry backup |
| `following: odometry` points the wrong way | Check `goalX`, `goalY` (seen from your own wall), `mirrorForRed`, and that the pose started right |
| `Aim odometry: the pose was never set` | TeleOp ran with no autonomous before it. Run the autonomous first, or park on the reset spot and hold gamepad 1 START 1 s |
| `Aim odometry: ... more than the field allows` | The pose is wrong. The backup stays off until it is right |
| `Aim: camera says 60 in, odometry 48 in` | The pose drifted, or `goalX`, `goalY` are not where the camera measures to |

## Done when

- At INIT, facing a tag, the lighting line shows a setting and no warning.
- Pointed at your goal, the Driver Station shows `Goal : LOCKED`.
- A Limelight has its 3 pipelines saved.

---

**Next:** [Add lights](08-lights.md).

**Look it up:** [Camera](../reference/parts/camera.md) and [Aim](../reference/parts/aim.md): every
setting, number and command.
