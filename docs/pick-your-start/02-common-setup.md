# Start from a common setup

**Pick your start, page 2 of 4.** For a robot that is like one of the library's example robots.

Each example robot is a robot in five files that builds and drives. Pick the one most like your
robot. You copy it, and then delete what your robot does not have. **Why:** deleting a line is
easier than writing one, and the lines you keep already work together.

## 1. Pick the closest example

The folders are in the library, in
`ftc-helper-lib/examples/src/main/java/com/crossbotics/ftchelper/examples/`.

| Your robot has | Start from | What is in it |
| --- | --- | --- |
| a mecanum drive and an intake | [`quickstart`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart) | the smallest robot with a part. A `// STEP` mark on each line to change |
| a mecanum drive, an intake, a lift and a claw | [`lessonbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot) | the robot of the main-track lessons, and its Drive Tuner |
| a tank drive and a flywheel shooter | [`flywheelbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/flywheelbot) | an intake, a deploy servo, a flywheel, a servo feeder and a webcam that aims the whole robot |
| a mecanum drive with a turret and a camera | [`turretbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot) | a Limelight that the turret follows, a hood, a folding claw, and two finders |
| a mecanum drive with odometry (Pinpoint, OTOS or dead wheels) | [`odometrybot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/odometrybot) | autos that drive to field points, and the Odometry Tuner |
| odometry and Pedro Pathing | [`pedrobot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/pedrobot) | `odometrybot` plus curved paths |

**Odometry** is a sensor set that tracks where the robot is on the field. **Pedro Pathing** is a
separate library that drives curved paths.

More detail on each one: [Pick your template](../templates.md#your-robot-looks-like-this-start-from-that).

**`pedrobot`:** add Pedro to the project first
([lesson 8, section 3](../tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)).

**Some have extra files.** Copy them with the rest:

| Example | Extra files | What they are |
| --- | --- | --- |
| `lessonbot` | `DriveTuner.java` | a finder for the drive |
| `turretbot` | `HoodFinder.java`, `ShotTuner.java` | finders for the hood and the shots |
| `odometrybot` | `OdometryTuner.java` | a finder for the odometry |
| `pedrobot` | `PedroConstants.java` | Pedro's numbers |

`lessonbot` also has `OwnTeleOp.java`: the same TeleOp written as a plain FTC OpMode, to read. Do not
copy it.

A **finder** is a small program that measures a number on the real robot and shows the line to copy
into your code.

## 2. Copy the files

Copy the example's folder into `TeamCode`: [Copy example files into TeamCode](04-copy-into-teamcode.md).
Then come back here.

## 3. Make it match your robot

Open `Robot.java`. Change only these, then build:

1. **The device names.** These are the names in quotes right after the part's kind, for example
   `"front_left"` in `Drive.mecanum("front_left", ...)` and `"claw"` in `Claw.servo("claw")`. Each one
   must be exactly the name in your robot configuration ([Set up 7](../setup/07-robot-configuration.md)).
   Capitals and `_` count. **Why:** the code finds each device by its name.
   Leave the other quoted words as they are: `.part("gobilda-2000-torque")` is a catalog id (which
   motor or servo it is), and words like `"high"` in `goTo("high")` are position names.
2. **The drive motor and wheels:** `.motor(...)` and `.wheel(...)` (or `.wheelMm(...)`) on the drive line
   ([the drive line](../five-files/01-robot-java.md#the-drive-attribute-by-attribute)).
3. **The IMU line** (`.imu(...)`): where the Control Hub's logo and USB ports face. Change it only if
   your hub is mounted another way ([how](../five-files/01-robot-java.md#how-your-control-hub-is-mounted)).
4. **A part your robot does not have:** delete its line in `define()` and its field
   (`public Claw claw;`). Then build. Each red mark now shows a place that still uses the part. Fix
   each one, in any file:
   - A button line in `Controls.java`: delete the whole line, up to its `;`.
   - An action in `Actions.java` that uses the part: delete the whole action, from
     `/** ... */` and `public static Action` to its closing `}`. Then delete each line that used that
     action.
   - A step in an autonomous: delete the step. If it ends with `;`, put the `;` on the step before it.

   **Why:** the build finds every place that uses the part for you. Delete whole lines and whole
   actions: half a line does not build.

   For example, a robot with no claw, from `lessonbot`:
   - `Robot.java`: delete `public Claw claw;` and the `claw = add(...)` line.
   - `Controls.java`: delete the `g2.b.toggle(robot.claw)` line.
   - `Actions.java`: delete both actions. Each one uses the claw.
   - Build again. Now the lines that used those actions are red. In `Controls.java`, delete the
     `g2.rb.press(...)` line, and the `g2.lb.tap(...)` statement: two lines, up to the `;`.
   - `autos/ScoreAndPark.java`: delete the `.run("Hold the piece", ...)` and `.action(...)` steps.

The part's section in `Tunables.java` (for example `public static class Claw`) can stay for now. It does
no harm. Delete it when you tidy up. Keep the other numbers as they are for now. You measure them on the
[Tunables.java page](../five-files/02-tunables-java.md).

A part your robot has but the example does not? Add it later, one at a time, on the
[Robot.java page](../five-files/01-robot-java.md#add-a-part).

## 4. Build

In Android Studio: **Build > Make Project**.

## Done when

- The five files are in `TeamCode`: `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java`,
  and the `autos` folder. Your configuration names are in `Robot.java`.
- **Build > Make Project** ends with no red errors.

## Problems

| Problem | Fix |
| --- | --- |
| A line is red: `cannot find symbol` | A `package` or `import` line still names the example folder ([Copy example files, step 3](04-copy-into-teamcode.md#3-change-the-imports)). Or the line uses a part you deleted: delete the line. |
| INIT stops: a name is not found | A name in `Robot.java` is not the name in the configuration. INIT lists the real names. |
| `pedrobot` does not build: `com.pedropathing` not found | Pedro is not in the project yet ([lesson 8, section 3](../tutorials/start/08-paths-with-pedro.md#3-add-pedro-to-your-project)). |

---

**Next:** [The five files](../five-files/README.md). They explain every line you just copied.

**Look it up:** [Pick your template](../templates.md): more about each example robot.
