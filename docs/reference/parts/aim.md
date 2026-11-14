# Aim

The part that says where the goal is, for everything that aims: `robot.aim()`. **It uses the camera first,
and the odometry as a backup.**
A following turret, a launcher's speed from distance, whole-robot aim and the aim buzz all use it. Learn it
step by step: [Add a camera](../../add-to-robot/07-camera.md).

## In Robot.java

Aim has no line of its own. It comes with:

| Attribute | Needed? | What it gives |
| --- | --- | --- |
| a [camera](camera.md) with `.goal(blue, red)` | one of the two | the goal from the camera |
| an [`odometry(...)`](odometry.md) line, and `Tunables.Aim.goalX, goalY` | one of the two | the goal from where the robot is (the backup) |

**The camera first.** While the camera sees the goal (a picture newer than 150 ms, `cameraMaxAgeMs`), Aim
uses the camera. When the camera does not see it, and the robot has odometry and a goal point, Aim works out
the goal's direction and distance from where the robot is.

## In Tunables.java

<!-- tunables: Aim -->
Section `Aim` (a fixed name). Every line is optional. Leave out what you do not use.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-aim -->
```java title="Tunables.java"
/** Aiming (every line is optional; leave out what you do not use). */
public static class Aim {                                       // a fixed name: always Aim
    // The goal on the field, in inches, seen from your drivers' wall (the same numbers as driveTo).
    public static double goalX = 13;                            // inches right of center (BIOBUZZ)
    public static double goalY = -13;                           // inches from center, - = our half (BIOBUZZ)
    public static boolean mirrorForRed = false;                 // BIOBUZZ: turned, not mirrored (true)
    public static double turnKp = DEFAULT;                      // whole-robot aim: push per degree off (0.02)
    public static double turnKd = DEFAULT;                      // brake near the goal (0.002)
    public static double maxTurnPower = DEFAULT;                // the most it turns by itself (0.5)
    public static double toleranceDeg = DEFAULT;                // "aimed" within this, degrees (2)
    public static double rumbleDeg = DEFAULT;                   // rumbleWhenAimed(): buzz within this (4)
    public static boolean aimOnFire = false;                    // no turret: a fire press turns to the goal first
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `goalX`, `goalY` | the goal on the field, seen from YOUR drivers' wall like every field number in your code (x right of the center line, y from the center toward the far wall), written for BLUE: the odometry backup aims at it when the camera does not see the goal. Write both: with only one, there is no odometry backup. BIOBUZZ: 13, -13 | inches | none: camera only | no | the field map | once per season (both, or neither) |
| `mirrorForRed` | ONE setting for the whole robot: RED's numbers (the goal point, the routes, the start and reset spots) are BLUE's mirrored (true) or the same numbers from the red wall (false). The library takes the first one written: this one, `Tunables.Odometry.mirrorForRed`, your autonomous's `mirrorForRed()` | true/false | true | no | the game's field | BIOBUZZ: `false` |
| `cameraMaxAgeMs` | a camera picture older than this does not count as "sees the goal" | ms | 150 | no | test | the camera is slow and the goal flickers to "odometry" |
| `maxGoalDistance` | the odometry backup is off while it says the goal is farther than this (a wrong pose) | inches | 170 | no | the field size | another field size |
| `cameraOdometryAgree` | warns when the camera and the odometry differ by more than this | inches | 6 | no | test | too many warnings with a good pose |
| `goalSteadySec` | `waitForGoal`: how long the goal must stay in view and still before the step goes on (a tipped HIVE can swing) | s | 1 | no | test | shots after the wait miss while the HIVE still moves (more); the wait is too long (less; 0 = go on when first seen) |
| `goalSteadyDeg` | `waitForGoal`: how far left or right the goal may move meanwhile | degrees | 2 | no | test | a still goal never counts as still (the camera's numbers jump: more) |
| `goalSteadyIn` | `waitForGoal`: how much nearer or farther the goal may move meanwhile | inches | 2 | no | test | the same |
| `turnKp` | whole-robot aim: turn power per degree off | power per degree | 0.02 | yes, at once | test | aim is slow (more) or swings (less) |
| `turnKd` | whole-robot aim: brakes the turn as it gets close | power per degree/s | 0.002 | yes, at once | test | aim overshoots (more) |
| `maxTurnPower` | whole-robot aim: the most turn power | 0 to 1 | 0.5 | yes, at once | test | aim is too fast or too slow |
| `minTurnPower` | whole-robot aim: the least power that still turns the robot | 0 to 1 | 0 | yes, at once | test | aim stops a few degrees short |
| `toleranceDeg` | whole-robot aim counts as aimed within this | degrees | 2 | yes, at once | test | shots miss although "AIMED" (less) |
| `aimOnFire` | quick turn on fire (no turret, aim mode off): a fire press with the goal in view turns the robot to the goal, then fires | true/false | false | no | test | the robot fires before it points at the goal (turn it on) |
| `aimOnFireSec` | quick turn on fire: the most a press waits for the turn before it fires anyway | seconds | 0.5 | no | test | shots wait too long (less), or fire before the turn is done (more) |
| `lockMarginDeg` | once LOCKED (or AIMED), it stays so until the error passes `toleranceDeg` + this, so it does not flicker at the edge | degrees | 1 | no | test | LOCKED flickers (more) |
| `sweepStepDeg` | `aimAtGoal(label, sec, sweepDeg)`: the robot turns this much between two looks while it sweeps for the goal | degrees | 15 | no | test | the robot turns past the goal between looks (less) |
| `sweepPower` | the sweep's turn power | 0-1 | 0.3 | no | test | the sweep is too slow, or the picture blurs (less) |
| `cameraOffset` | whole-robot aim (no turret): a camera that is not on the robot's center line. A turret has its own, `Tunables.Turret.cameraOffset`. The Aim Tuner prints the line for the one your robot uses | degrees | 0 | yes, at once | test | the robot aims a little to one side |
| `rumbleDeg` | the aim buzz starts this close to the goal | degrees | 4 | yes, at once | test | the drivers want it earlier or later |
| `shootOnMove` | shooting while driving: on at ▶ | true/false | false | no | test | only after you tune it on the real robot |
| `flightSec` | shooting on the move: the piece's time in the air | seconds | 0 | yes, at once | test (film a shot) | moving shots land behind (more) or ahead |
| `flightSecPerIn` | shooting on the move: more flight time per inch of distance | seconds per inch | 0 | yes, at once | test | far moving shots miss but near ones hit |
| `maxLeadIn` | shooting on the move: the most it aims ahead | inches | 24 | yes, at once | test | fast moves need more lead |
| `cameraLatencyMs` | shooting on the move: the camera's delay, instead of its own timestamp | ms | the camera's own timestamp | yes, at once | test | the camera's timestamp is wrong |
| `shooterForwardIn`, `shooterLeftIn` | where the shooter (turret pivot or launcher exit) is from the robot's center | inches | 0 | no | measure from the edges (below) | the shooter is not at the center |
| `cameraForwardIn`, `cameraLeftIn` | where a camera on the robot's body is from the center | inches | 0 | no | measure from the edges (below) | a body camera that is not at the center |

More lines, with example numbers:

```java title="Tunables.java"
public static class Aim {                                   // Tunables.java (every line optional; all: see below)
    public static double goalX = 13, goalY = -13;           // the goal on the field, inches (odometry backup)
    public static boolean mirrorForRed = false;             // default true; BIOBUZZ's turned field: false
    public static double turnKp = 0.02, turnKd = 0.002, maxTurnPower = 0.5, minTurnPower = 0, toleranceDeg = 2;
    public static double cameraOffset = 0, rumbleDeg = 4;
    public static double maxGoalDistance = 170, cameraOdometryAgree = 6;   // inches: odometry checks
    public static boolean shootOnMove = false;              // shooting while driving (below): off until tuned
    public static double flightSec = 0.8, flightSecPerIn = 0, maxLeadIn = 24;   // flightSec default 0: no lead until written
    public static double shooterForwardIn = 0, shooterLeftIn = 0, cameraForwardIn = 0, cameraLeftIn = 0;
}
```

### The goal point

- It uses `driveTo`'s numbers: seen from your drivers' wall, x inches right of the field's center line, y
  from the center toward the far wall, written for BLUE.
- `mirrorForRed` (default true, like autonomous): RED mirrors x. Write `false` for a field turned half a
  turn: then RED uses the same numbers from the red wall.
- One shot table fits both distances only when the camera and the odometry measure to the same place. So put
  the point where the camera measures to (the goal's tags).
- INIT shows the alliance and the choice:
  `Aim: RED, goal x 13, y -13 in (the same numbers from each wall: Tunables.Aim.mirrorForRed = false)`.
- `mirrorForRed` is one setting for the whole robot (autonomous routes, the aim, TeleOp's reset spot).
  The library takes `Tunables.Aim.mirrorForRed`, else `Tunables.Odometry.mirrorForRed`, else your autonomous's
  `mirrorForRed()` (a TeleOp uses the last autonomous's), else true. INIT warns when two of them differ
  ([the three mirrorForRed settings](odometry.md)).

### A wrong pose must not aim the robot

The odometry is only as good as its pose: the auto's start spot, carried into TeleOp.

- The backup is OFF until something sets the pose: the auto ran in this match, or your code called
  `robot.odometry().setPose(...)`. Until then, Aim uses the camera only, and the Driver Station warns:
  `Aim odometry: the pose was never set ...`.
- Holding BACK to reset "forward" does not count. It changes only what "forward" means for the driver. It
  does not change the pose.
- The backup is also off while the odometry says the goal is farther than `maxGoalDistance` (170 in: more than the field allows).
- While the camera and the odometry both see the goal, and their distances differ by more than
  `cameraOdometryAgree` (6 in), the Driver Station warns: `Aim: camera says 60 in, odometry 48 in: ...`.

## Whole-robot aim

For a robot with no turret. Turn it on with one line in `Controls.java` ([Buttons to add](#buttons-to-add)).

- While it is on, the robot turns to face the goal. The driver still controls forward and sideways.
- Moving the turn stick still turns it by hand.
- It is off at ▶.
- Its numbers: `turnKp`, `turnKd`, `maxTurnPower`, `minTurnPower` (the least power that turns the robot),
  `toleranceDeg` and `cameraOffset`.
- This `cameraOffset` is `Tunables.Aim`'s, for the whole robot. A turret has its own in `Tunables.Turret`.
  The Aim Tuner prints the line for the one your robot uses.
- The Driver Station shows `Aim : camera, 1.5 deg off  AIMED`.

**Turret-following and whole-robot aim are separate modes. Use one.** A [turret](turret.md) that follows the
goal (`follows(camera)`, `followsOdometry()`) aims by itself. `robot.aimMode()` is for a robot with no turret,
or one that does not follow. Both at once would chase the same error with two motors, and swing back and
forth. So `robot.aimMode()` stops at INIT with a message on a robot whose turret follows the goal.

### The aim lock rules

These rules took about 60 commits last season. Now they are the defaults.

- **Our goal only:** the camera gives this match's alliance goal (`goal("BLUE SCORING", "RED SCORING")`),
  never the other alliance's. It gives only the CELL that reads right side up (the up CELL), never a down
  CELL's underside ([Camera](camera.md)).
- **The button turns aim on only with the goal in view.** A press with no goal leaves it off (with
  `.rumble()`: one buzz instead of two). The Driver Station says `Aim mode not on: no goal in view` for 3 s.
  The library does not check code that calls `robot.aimMode().on()`.
- **A short hold:** a picture stays good for `cameraMaxAgeMs` (150 ms) when the tag drops out for a moment.
- **No flicker:** once LOCKED (or AIMED), it stays so until the error passes `toleranceDeg` + `lockMarginDeg`
  (2 + 1 degrees). So the Goal line, the buzz and a launcher's "aimed" check do not flicker at the edge.

## Seen, in range, LOCKED

On a robot that can aim (a camera with `goal(...)`, or the odometry backup), TeleOp always shows one line,
so the drivers know when to shoot: `Goal : LOCKED (camera, 52 in, 0.8 deg off)`.

Each stage includes the ones before it:

| Stage | It means |
| --- | --- |
| **not seen** | the goal is not known |
| **seen** | the goal is known. It also says "out of the shot table's range" when its distance is outside the launcher's `speedByDistance` rows |
| **in range** | the distance is inside the table, or the launcher has no table |
| **LOCKED** | the robot's front, or a following turret, is within `toleranceDeg`. Aim mode does not have to be on |

Your code can use it too:

- For lights: `robot.aim().stage() == Aim.Stage.LOCKED`.
- To fire only when the robot points at the goal (no turret; aim mode on or off; never blind):
  `.requireReady("aimed", () -> aim().stage() == Aim.Stage.LOCKED)` on the launcher line, as the
  `flywheelbot` example does.

## Aim in an auto

`s.aimAtGoal("Aim", 3)`:

- It turns the whole robot to the goal: up to 4 turns, a fresh reading after each, until it is
  within `toleranceDeg`. It uses `maxTurnPower`.
- With a turret that follows the goal, it waits until the turret is aimed.
- It never turns blind. With no goal, it waits up to the time limit, and the report says `not seen`.
- The auto builder has it as "+ Aim at the goal".

**With a sweep:** `s.aimAtGoal("Aim", 4, 45)`.

- When the goal is not in view, the robot turns slowly (`sweepPower`, 0.3) in steps of `sweepStepDeg` (15),
  up to 45 degrees to the left, then to the right. It looks after each step.
- Once it sees the goal, it aims.
- When it never sees the goal, it turns back to where it started, and the report says `not seen (swept)`.
- It never turns blind past the sweep.

## Quick turn on fire

It turns the robot, so it is **off unless you set it**: `Tunables.Aim.aimOnFire = true`.

```java title="Tunables.java"
public static class Aim {                    // Tunables.java
    public static boolean aimOnFire = true;  // RB with the goal in view: turn to it, then fire
}
```

For a robot with no turret, with aim mode off:

- A press of the fire button (the launcher's RB, or `robot.launcher.fire()`) with the goal in view turns the
  robot to the goal. The piece waits until the robot is within `toleranceDeg`, at most `aimOnFireSec`
  (0.5 s: a press always fires).
- When the shots are done (and the driver lets go of the button), the driver controls the turn again. The turn stick still wins.
- With no goal in view at the press, it fires as before, with no turn.
- When the goal is lost during the turn (a turn blurs the picture), it waits, up to `aimOnFireSec`, then fires.
- It uses whole-robot aim's numbers (`turnKp` and the others).
- It does nothing with aim mode on (it aims already), with a turret that follows the goal, or while the
  launcher sprays (the last seconds, or tap-then-hold). SPRAY does not wait, so a turn would throw pieces to
  the side.
- An auto's `fire(n)` and fire actions do not turn. Use `aimAtGoal`.
- While it turns, `errorDeg()`, `isAimed()` and the aim buzz follow it. The launcher's "not ready" list
  says `turning to the goal`.

## Shooting on the move

Turn it on with `shootOnMove = true`. **It is off by default: tune it on the real robot first**
([Add a camera: Shooting while moving](../../add-to-robot/07-camera.md#shooting-while-moving)).

- A piece keeps the robot's speed. So the goal every part gets (`goal()`, the turret, `isAimed()`, the
  launcher's distance) becomes the **virtual goal**: the goal - the shooter's speed x `flightSec`
  (+ `flightSecPerIn` x the distance), at most `maxLeadIn` (24 in) away.
- Aim first brings a camera's picture forward to now: it adds the robot's turn and move since the camera took
  it. Aim takes the picture's age from the camera's own timestamp, or from `cameraLatencyMs` when you write
  it.
- It needs odometry (the robot's speed).
- It works with a turret that follows the goal (camera on the turret, `cameraOnBody()`, or
  `followsOdometry()`), and with whole-robot aim (a fixed shooter).
- `shooterForwardIn, shooterLeftIn`: the turret's pivot (or a fixed launcher's exit) from the robot's center,
  inches. Measure from the edges, not the middle: forward = length / 2 - (from the front edge), left =
  width / 2 - (from the left edge). The distance is from there, and a turning robot swings it sideways.
- `cameraForwardIn, cameraLeftIn`: a camera on the body. Aim treats a camera on the turret as if it sits at the pivot.
- INIT shows `Moving shot: ON: flight 0.80 s ...`. TeleOp shows
  `Aim: moving shot: lead 12.3 deg, 18 in` while the robot moves, and a debug line with every number.
- When it is off, nothing changes.
- The math: [MovingShot](../../modules/vision.md#movingshot).

## Commands

| Command | What it does |
| --- | --- |
| `mode()` (= `robot.aimMode()`) | whole-robot aim. It stops with a message on a robot whose turret follows the goal |
| `movingShotMode()` | shooting on the move on/off. It is on from ▶ with `shootOnMove = true` |

| Question | What it tells you |
| --- | --- |
| `goal()`, `source()` | the goal now (null if neither), and CAMERA, ODOMETRY or NONE |
| `fromOdometry()` | the goal from the odometry alone (a [TargetObservation](../../modules/vision.md) with id `ODOMETRY`), or null (no pose set, or farther than `maxGoalDistance`) |
| `odometryProblem()` | why the odometry backup is off now, "" if it works |
| `disagreement()` | the camera/odometry distance warning, "" if they agree |
| `hasGoalPoint()` | whether `Tunables.Aim` has `goalX` and `goalY` |
| `canAim()` | whether the robot can find the goal at all (a camera with a goal, or the odometry backup) |
| `stage()` | NOT_SEEN, SEEN, IN_RANGE or LOCKED now (`Stage.words()`: "not seen" ... "LOCKED") |
| `aimsOnFire()`, `isTurningToFire()` | whether quick turn on fire is set (`aimOnFire`); whether a fire press is turning the robot now |
| `errorDeg()` | degrees off now (NaN = unknown). While shooting on the move: off the virtual goal |
| `isAimed()` | whether it is aimed: the whole robot within `toleranceDeg`, or the following turret's `isAimed()` (on the goal now) |
| `rumbleDeg()` | the buzz's `rumbleDeg` |
| `buzzErrorDeg()` | the buzz's error. Also the camera's bearing on a robot with no turret and the aim off |
| `isMovingShot()` | whether shooting on the move is on and the robot moves (over 2 in/s or 5 deg/s) |
| `lead()` | the last lead (a [MovingShot](../../modules/vision.md#movingshot) `Result`: `leadDeg`, `leadIn`, `distanceIn`), null if none |
| `Aim.sourceOf(observation)` | CAMERA, ODOMETRY or NONE for that observation |

## Buttons to add

Aim has no buttons until you add its lines to `Controls.java`
([how to read a button line](../controls.md#reading-a-button-line)).

```java title="Controls.java"
g1.rb.toggle(robot.aimMode()).says("aim the robot at the goal on/off").rumble();   // whole-robot aim
g2.rumbleWhenAimed();                                                               // the aim buzz
g2.a.toggle(robot.aim().movingShotMode());                                          // shooting on the move on/off
```

**The aim buzz** (`rumbleWhenAimed()`, an [AimRumble](../../modules/gamepad.md)):

- It buzzes that gamepad while the aim is within `rumbleDeg`. The closer, the stronger and longer.
- It follows a turret that is following, or whole-robot aim.
- A robot with no turret buzzes with aim mode off too. The driver turns it by hand, and it buzzes when the
  camera sees the goal straight ahead (`buzzErrorDeg()`).
- It is only the buzz: a launcher's "aimed" check stays off while nothing aims.
- It does not buzz with no goal in view, or while paused.

---

**Look it up:** [`Aim` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Aim.html)
(every method). **The other parts:** [Reference](../README.md).
