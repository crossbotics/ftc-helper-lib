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

To run the lighting test again in a match, put `relight()` on a button
([Camera: Buttons to add](../reference/parts/camera.md#buttons-to-add)).

### Actions.java and autos: use it in steps

**Where:** an autonomous in `autos/`, before a fire step.

```java
.aimAtGoal("Aim", 3)
```

It turns the whole robot to the goal, or waits until a following turret is aimed. It never turns blind:
with no goal in view, it waits up to 3 s ([Aim in an auto](../reference/parts/aim.md#aim-in-an-auto)).

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

## Done when

- At INIT, facing a tag, the lighting line shows a setting and no warning.
- Pointed at your goal, the Driver Station shows `Goal : LOCKED`.
- A Limelight has its 3 pipelines saved.

---

**Next:** [Add lights](08-lights.md).

**Look it up:** [Camera](../reference/parts/camera.md) and [Aim](../reference/parts/aim.md): every
setting, number and command.
