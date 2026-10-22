# Lesson 9: A shooter, a camera and a turret (for robots that shoot)

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**Goal:** add a flywheel shooter with a feeder, then a camera that aims a turret and picks the shot speed.

**You need:** lessons 1 to 6 (the last one: [Your first autonomous](06-first-autonomous.md)); lessons 7 and 8
are not needed. Skip this lesson if your robot does not shoot. A flywheel
(a heavy wheel that spins fast and throws the game piece) on a motor with its encoder cable, and a feeder (a servo, a CR
servo or a motor). For the second half, a Limelight 3A (or a webcam) and a turret on a motor.
**Safety glasses:** a flywheel throws things.

The lesson robot has no shooter, so this lesson reads two other example robots built the same way:

- [`gobildabot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot): goBILDA's
  BIOBUZZ starter bot. A flywheel fed by a "windmill" on a CR servo.
- [`turretbot`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot): a turret with a
  Limelight on it that follows the goal, and a flywheel whose speed comes from the distance.

**Reference:** [Launcher](../../modules/robot.md#launcher), [Indexer](../../modules/robot.md#indexer),
[Camera](../../modules/robot.md#camera), [Turret](../../modules/robot.md#turret).

---

## 1. A flywheel and its feeder

From [`gobildabot/Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot/Robot.java):

```java
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.crServo("windmill").reversed()));
```

- `Launcher.flywheel("launcher", "gobilda-5203-6000")`: the flywheel motor's name in the configuration,
  and the motor by its [catalog id](../../guides/parts-catalog.md). From the id the library knows the
  encoder counts per turn and the top speed, so it can work in RPM (turns per minute).
- `.feeder(...)`: the part that pushes ONE piece into the wheel. The launcher decides when.
- `Indexer.crServo("windmill")`: a CR servo (a servo that spins all the way round, like a small motor)
  that runs for a moment per piece. `.reversed()`: it turns the other way.
- Other feeders: `Indexer.servo("indexer")` pushes and comes back; `Indexer.motor("feeder")` runs a roller.

**Imports:** each part class needs one at the top of `Robot.java`, all from
`com.crossbotics.ftchelper.robot`: `import com.crossbotics.ftchelper.robot.Launcher;` and
`...robot.Indexer;` here, `...robot.Camera;` and `...robot.Turret;` in sections 5 and 6. Careful with
Alt+Enter (Option+Enter on a Mac): it may offer a second `Launcher` and `Turret` in
`com.crossbotics.ftchelper.launcher` (the building blocks of the under-the-hood tutorials). Pick the
`robot` ones.

**Why a feeder the launcher controls?** A piece only flies right when the wheel is at its speed. If the
driver fed by hand, some pieces would go in too early and fall short.

## 2. Its numbers

From [`gobildabot/Tunables.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot/Tunables.java):

```java
public static class Launcher {
    public static double speedRpm = 2679, stepRpm = 50, readyBandRpm = 107, idleRpm = 0;
    public static double kP = 40, kI = 0, kD = 0, kF = 12.5;
}

public static class Windmill {
    public static double power = 1.0, feedSec = 0.3;
}
```

| Number | Means |
| --- | --- |
| `speedRpm` | the shot speed. Above the motor's top speed, INIT stops with a message. |
| `stepRpm` | how much one D-pad press changes the speed |
| `readyBandRpm` | how close to `speedRpm` counts as "at speed" (it must stay there 0.15 s) |
| `idleRpm` | the speed it drops back to when the driver spins it down (0 = coast to a stop) |
| `kP`, `kI`, `kD`, `kF` | the **gains**: how hard the hub pushes to hold the speed. Used only when `kF` is there. |
| `power`, `feedSec` (Windmill) | the feeder's power, and how long it runs per piece |

A servo feeder (`Indexer.servo`) has `rest`, `push`, `pushSec`, `returnSec` instead. It goes to `rest`
at START, never in INIT.

**Find the gains with the Flywheel Finder** ([how](../../guides/finders.md#flywheel-finder-gains-and-recovery)).
It needs no code. Choose the `launcher` motor; it shows a line to copy into `Tunables.Launcher`:

```text
Tunables.Launcher: kP = 1.17, kI = 0.117, kD = 0, kF = 11.70
```

If the wheel needs longer than 0.25 s to get back to speed after a shot, the line also has
`shotIntervalSec` (the time between two feeds), a little above that time.

You can change `speedRpm` and `stepRpm` with the tuning menu (PRACTICE profile, hold GUIDE 1 s). The
others are read once at INIT: change them in the file.

## 3. The buttons it brings

You write nothing in `Controls.java` for these. They are on the operator's gamepad (gamepad 2), or
gamepad 1 with `oneDriver = true`, as in the goBILDA bot:

| Button | Does |
| --- | --- |
| LB | spin up / spin down (to `idleRpm`) |
| RB | fire one: spins up if needed, feeds as soon as it is at speed |
| RB held | keeps firing, one piece after another; let go and no more are fed |
| D-pad up / down | speed + / - `stepRpm` (not with speed from distance, below) |

Spinning up early with LB saves time: the wheel is ready when the shot is lined up. The goBILDA bot adds
one job of its own, Y to fire all three: `g1.y.press(Actions.fireAll(robot)).says("fire all");`

**Spin up at START.** Add `.spinAtStart()` and the wheel spins up right after START, in autonomous and
TeleOp, so the first shot does not wait. LB still spins it down.

```java
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.crServo("windmill").reversed())
        .spinAtStart());
```

## 4. The two safety rules

The launcher keeps two rules for you. Know them, because they explain what the robot does.

1. **Never feed into a slow flywheel.** A piece is fed only when the wheel has stayed within
   `readyBandRpm` of its speed, and every other check (step 7) is true. Between two pieces it waits
   `shotIntervalSec`, so the wheel can get back to speed.
2. **Never wait forever.** If it is not ready within 3 s, it gives up the shots still waiting and the
   Driver Station says why: `Launcher gave up: flywheel`. A robot that fired late, at a bad moment,
   would be worse than one that did not fire.

## 5. A camera that knows the goal

From [`turretbot/Robot.java`](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/Robot.java):

```java
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
```

- `Camera.limelight("limelight")`: a Limelight 3A, by its configuration name. A webcam is
  `Camera.webcam("Webcam 1")`.
- `.goal("BLUE SCORING", "RED SCORING")`: this season's goal for each alliance, by the name of its group
  of AprilTags (the square black-and-white codes on the field). The camera then gives the goal for this
  match's alliance, chosen in autonomous's INIT menu (TeleOp remembers it).
- `.autoPipelines(0, 1, 2)`: venue lighting. Every gym has different light, and a camera setting that
  finds tags well at school can miss them at an event. In INIT the robot tries each pipeline, counts
  the pictures that see a tag, and keeps the best. Put your usual pipeline first: it wins a tie.
  A webcam does the same with `.autoExposure()` (it tries exposures instead of pipelines).
  How it works: [vision.md](../../modules/vision.md#venue-lighting).
- Its numbers: `Tunables.Limelight.pipeline` (the pipeline to use when the test is off) and
  `autoLighting` (true = run the test; false = skip it and use `pipeline`).

**Set up the three pipelines.** On a laptop on the robot's network, open the Limelight's page:
http://limelight.local:5801. Then, for each pipeline:

1. Pick the pipeline number (0, 1 or 2) in the pipeline list at the top.
2. Set its type to AprilTag (Fiducial), and the tag family and size from the game manual.
3. In the **Input** tab, move the **Exposure** slider: 0 = your usual light, 1 = bright light (lower
   exposure, so tags are not washed out white), 2 = dim light (higher exposure, and more **Sensor
   Gain** if it is still dark).
4. Save the pipeline. The Limelight's own docs (docs.limelightvision.io) show every setting.

**At INIT and at START.** Have a tag in view at INIT (where the robot starts the match is fine), or the
test cannot help. The Driver Station shows the result, e.g.
`limelight lighting: pipeline 2 (9 of 10 pictures saw tags)`. At START the test stops at once and keeps
the best pipeline so far (the first one if none saw a tag), so the robot never aims with a pipeline
it is only trying.

**At an event:** in the queue (or on the practice field), INIT once with the robot facing a HIVE tag.
Read the pipeline it picks. The Driver Station then shows two lines, e.g.

```java
public static double pipeline = 2;
public static boolean autoLighting = false;
```

Put them in `Tunables.Limelight` (change the `pipeline` line that is there; add the `autoLighting` line
if it is not), and the robot uses that pipeline at once in every match there, with no test. Back at
school, set `autoLighting = true` again. A webcam shows `exposureMs` instead of `pipeline`: that is a
new line to add to the camera's section (for example `Tunables.Webcam`).
- It starts in INIT, so it is already looking at START. It has no buttons.

## 6. A turret that follows it

```java
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
```

- `Turret.motor("turret", "gobilda-5203-117")`: the motor's name and its catalog id.
- `.gearRatio(5)`: the motor turns 5 times per turret turn (the gears or belt). It must be right, or
  every angle is wrong.
- `.follows(camera)`: from START, every loop, the turret turns toward the goal. The camera is on the
  turret. (One on the robot's body: add `.cameraOnBody()`.)

```java
public static class Turret {
    public static double min = -90, max = 90;
    public static double kP = 0.02, kI = 0, kD = 0.001;
    public static double maxPower = 0.8, tolerance = 1.5, cameraOffset = 0;
}
```

- `min`, `max`: the range in degrees (0 = straight forward, counterclockwise +). It never turns past
  them. A goal outside waits at the limit, and the Driver Station says "goal out of turret range".
- `kP`, `kI`, `kD`: how hard it turns toward the target. Tune `kP` live in the tuning menu: too low and
  it lags, too high and it shakes.
- `tolerance`: how many degrees off still counts as "aimed". `cameraOffset`: if shots land to one side,
  the [Aim Tuner](../../guides/finders.md#aim-tuner-camera-offset) finds it.
- **0 degrees is where the turret points when you press INIT.** Point it straight forward first.
- Its buttons: operator right stick X turns it by hand (following pauses while you move it), BACK
  switches following on and off.

The software limits do not replace hard stops: give the turret a mechanical stop just past each limit.

## 7. Speed from distance, and fire only when aimed

```java
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer"))
        .speedFromDistance(camera)
        .requireReady("turret aimed", () -> turret.isAimed()));
```

- `.speedFromDistance(camera)`: the speed comes from the camera's distance to the goal, from this table:

  ```java
  public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
  ```

  `double[][]` is a table of numbers: a list of rows, each row in `{ }`. Each row is `{inches, rpm}`.
  Between rows the speed is blended. With no goal in view, it keeps the last
  speed (a sudden change would spoil the next shot); before the goal is first seen, it uses `speedRpm`.
  Fill the rows from real shots with the [Shot Tuner](../../guides/finders.md#shot-tuner-the-shot-table).
  It shows the whole line to copy: `public static double[][] speedByDistance = {{24, 2200}, {48, 2550}};   // in Tunables.Launcher`.
  Its file needs one method that names your robot (the turret robot's
  [ShotTuner.java](../../../examples/src/main/java/com/crossbotics/ftchelper/examples/turretbot/ShotTuner.java)):
  `@Override protected RobotDefinition robot() { return new Robot(); }`. It uses your launcher, camera
  and drive. The turret stays still and the "turret aimed" check is off: point the robot with the sticks.
- `.requireReady("turret aimed", () -> turret.isAimed())`: one more check before each piece is fed.
  `() -> turret.isAimed()` is a lambda (lesson 5): the launcher asks it again before every piece. The
  words are what the Driver Station shows if it gives up: `Launcher gave up: turret aimed`.

## 8. Extra: know which piece fires next

Two kinds of piece that fly differently (BIOBUZZ: a 2.8 in POLLEN and a 3.6 in NECTAR) need two
speeds. A colour sensor where the pieces come in (and one at the "ready to fire" slot) lets the robot
count what it holds and know the next one:

```java
pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
        .comingIn(PieceSensor.color("intake_color"))
        .readyToFire(PieceSensor.color("ready_color"))
        .max(4));
```

Each piece the intake sensor sees counts one in, each feed one out. `robot.pieces.next()` says
`"POLLEN"`, `"UNKNOWN"` or `"NONE"`; `robot.pieces.isFull()` is true at 4 (rule G407). Each kind's
colour goes in Tunables, measured with the Piece Tuner. The whole story, other sensors and the hooks
(a shot table per kind, LEDs): [Pieces and PieceSensor](../../modules/robot.md#pieces-and-piecesensor),
and the [cookbook](../../cookbook.md#game-pieces).

## Try it in the simulator

The goBILDA bot has a measured simulator robot. From the `ftc-helper-lib` folder:

```bash
./gradlew :sim:run -PteamCode=none -Probot=sim/robots/starter-bot-gobilda.properties
```

Open http://localhost:8780, choose "goBILDA Bot TeleOp", INIT, START. Tap LB and watch the speed rise,
then tap RB; hold RB to keep firing. Tap RB before the wheel is at speed: the piece waits.

The turret robot: write its robot file from `Robot.java`, then run it:

```bash
./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.turretbot.Robot -Pout=build/turretbot.properties
./gradlew :sim:run -PteamCode=none -Probot=build/turretbot.properties
```

Choose "TurretBot TeleOp". Drive until the goal is in view and watch the turret follow it and the
speed change with the distance. Press BACK on gamepad 2 to stop following. ([Simulator guide](../../guides/simulator.md).)

**Your own robot** with its shooter (and camera), from the `ftc-helper-lib` folder:

```bash
./gradlew :sim:fromRobot -PteamCode=../TeamCode
./gradlew :sim:run -PteamCode=../TeamCode
```

Pick your TeleOp, INIT (read the map: the launcher's standard buttons are there), START, and fire.
For pieces to be picked up and shot, the simulator needs to know where your intake and flywheel are:
give each a `looks(...)` shape in `Robot.java` ([Lesson 1: Draw your robot from Robot.java](01-robot-in-five-files.md#draw-your-robot-from-robotjava)).

## Check it on the robot

1. Safety glasses, and nobody in front of the launcher.
2. Flywheel Finder first: gains in `Tunables.Launcher`. Then spin up with LB and read the speed on the
   Driver Station: does it reach `speedRpm` and stay there?
3. Feed by hand at low speed, then fire with RB. Watch the feeder push ONE piece per shot; tune
   `feedSec` (or `push`, `pushSec`) until it does.
4. Turret: point it forward, INIT, and turn it by hand to each end. It must stop before the hard stops.
5. Only then turn following on, and fill `speedByDistance` at 4 to 6 distances on a charged battery.

## If something goes wrong

| What you see | Why, and what to do |
| --- | --- |
| `Launcher gave up: flywheel` | the wheel never got within `readyBandRpm`: check the encoder cable, find the gains, or lower `speedRpm`. |
| `Launcher gave up: turret aimed` | the goal is not in view, or out of range, or `tolerance` is too tight. |
| INIT stops: `speedRpm` is above the motor's top speed | the number is too high for that motor, or the catalog id is wrong. |
| Two pieces go in at once | the feeder runs too long: lower `feedSec` (or `pushSec`). |
| The turret turns away from the goal | add `.reversed()` to the turret. |
| The turret's angles are wrong | `.gearRatio(...)`, or it did not point forward at INIT. |
| "goal out of turret range" | the goal is past `min` or `max`: turn the robot. |
| The speed never changes with distance | no goal in view; check `Tunables.Limelight.pipeline` and the `goal(...)` names. |
| `limelight lighting: trying 1 of 3: pipeline 0 (no tags yet: face a tag)` (INIT) | the test has tried every pipeline and none saw a tag. Turn the robot toward a tag; the test keeps going until START. |
| `limelight lighting: NO TAGS SEEN` (after START) | no pipeline saw a tag in INIT, so the first one is kept. Next time have a tag in view at INIT; check each pipeline on the Limelight's web page. |
| `limelight lighting: no pictures from pipeline 2 (missing?)` | that pipeline is not set up on the Limelight: make it (see above), or take it out of `autoPipelines(...)`. |
| `limelight: not found: no Limelight called "limelight"` | the name in the robot configuration is different (case counts), or the Limelight is not in it. The OpMode still runs, without the camera. |

## Where next

- This is the last lesson. The main track is lessons 1 to 6, plus the lessons your robot needs. Not
  done yet: [Lesson 7: Odometry: where am I on the field?](07-odometry.md) (the robot knows its place
  on the field, and autonomous drives to field points), and
  [Lesson 8: Paths with Pedro Pathing](08-paths-with-pedro.md) (smooth curved paths in autonomous).
- [The autonomous builder](../../guides/autonomous-builder.md): everything an autonomous can do (fire
  during a move with `alongside`, menu choices, branches).
- [The finders](../../guides/finders.md): measure every number instead of guessing.
- **Under the hood:** [17 Launcher](../17-launcher.md) (a flywheel, hood and feeder built by hand),
  [18 Vision aiming](../18-vision-aim.md) (how the camera's distance and bearing are found), and the
  [other under-the-hood tutorials](../README.md#under-the-hood-how-it-works).
