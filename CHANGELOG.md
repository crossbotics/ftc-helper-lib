# Changelog

What changed in each version. Newest first. How to update: [Keep it up to date](docs/02-keep-up-to-date.md).

## Unreleased

### Added

- **Start things along a path:** `PathAt` in a `follow` (or `driveTo`) step starts something once, at
  a place along the path, while the robot keeps driving: `PathAt.inchesLeft(12, ...)`,
  `PathAt.fraction(0.5, ...)`, `PathAt.inchesDone(...)`, `PathAt.part(2, ...)` (where part 2 of a
  `then` path starts) and `PathAt.near(x, y, withinIn, ...)`. Each takes a name and a command, or an
  action. If the path ends early, the report says which did not start (`STALLED, not fired: Spin up`).
  Mistakes (a fraction of 2, a part the path does not have) stop INIT. The Pedro example starts its
  intake halfway; lesson 8, the pedro module page and a cookbook recipe show it.
- `PathFollower`: `totalIn()`, `partNow()` and `parts(path)`, with defaults, so an add-on written
  before them still works. `PathJob`: `totalIn()`, `partNow()`. `AutoRoutine.followPath` and
  `driveTo` take a list of `PathEvent`s.
- **Game pieces: which piece is where, how many, which fires next.** `Pieces` (robot in five files):
  `Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR").comingIn(sensor).readyToFire(sensor).max(4)
  .startsWith(4, "POLLEN")`. Each new piece the intake sensor sees counts one in (with its kind), each
  indexer feed one out; `count()`, `next()`, `isFull()`, `isEmpty()`, `nextIs(kind)`, `held()`, and hooks
  `nextKind()` (a `Supplier<String>`, e.g. for a shot table per kind), `whenFull()`, `whenEmpty()`,
  `whenNextIs(kind)` (conditions for LEDs, buzzes, waits). `PieceSensor.color(...)` (REV Color Sensor V3:
  colour + distance), `PieceSensor.distance(...)` (REV 2m Distance Sensor: tells sizes apart, not
  colours), `PieceSensor.camera(...)` (a second webcam with the SDK's PredominantColorProcessor); also
  usable alone. Their numbers (each kind's hue or mm, `nearMm`, tolerances, `steadyMs`) in Tunables.
- **Piece Tuner** (`PieceTunerOpMode`): each piece sensor's live readings; measures each kind and EMPTY
  and writes the Tunables lines to copy.
- `Intake.stopsWhen(condition)`: never runs in while it is true (e.g. full), pushing out still works.
  `Indexer.onFeed(runnable)`: your code runs at each feed.
- Simulator: pretend piece sensors, `color.NAME = at=ready|intake` and `distance.NAME = at=..., acrossMm=150`;
  `:sim:fromRobot` writes them, `:sim:newRobot` turns a REV Color Sensor V3 or 2m Distance Sensor in the
  configuration into a `PieceSensor` line. Cookbook: a "Game pieces" section.
- **Aiming helpers** (so the drivers can focus on driving and firing), in the robot in five files;
  [robot.md: Aim](docs/modules/robot.md#aim), [lesson 9](docs/tutorials/start/09-shooter-and-camera.md)
  sections 8-11, and four cookbook recipes ("Aiming and shooting"):
  - **The odometry as a backup for the camera:** with an `odometry(...)` line and the goal's place in
    the new `Tunables.Aim` (`goalX`, `goalY`, in `driveTo`'s numbers; `mirrorForRed` like autonomous),
    a turret that `follows(camera)` and a launcher's `speedFromDistance(camera)` use the odometry's
    direction and distance while the camera does not see the goal. The Driver Station says which
    (`following: camera` / `odometry`). No camera: `Turret.followsOdometry()`,
    `Launcher.speedFromOdometry()`. `robot.aim()` gives the goal (`goal()`, `source()`,
    `fromOdometry()`); `TargetObservation.ODOMETRY` marks a goal worked out from the odometry.
  - **Whole-robot aim** (no turret): `g1.rb.toggle(robot.aimMode())` in `Controls.java`. While on, the
    robot turns to face the goal and the driver keeps forward and sideways; the turn stick still wins.
    Numbers in `Tunables.Aim` (`turnKp`, `turnKd`, `maxTurnPower`, `minTurnPower`, `toleranceDeg`,
    `cameraOffset`). `DriverControls.turnAssist(...)` is the hook under it.
  - **The aim buzz:** `g2.rumbleWhenAimed();` buzzes while the aim (a following turret, or whole-robot
    aim) is within `Tunables.Aim.rumbleDeg`, the closer the stronger (last season's on-target buzz).
  - **One shot table per game piece:** every `speedByDistance...` table in `Tunables.Launcher` is a
    table named by the rest of its name (`speedByDistancePollen` = "pollen"). Switch with
    `robot.launcher.useTable("nectar")` (a button, an action, an autonomous), `nextTable()` (the
    launcher's new suggested button, operator D-pad right, with two or more tables), or let code choose:
    `tableFrom(() -> name)` (ready for a game-piece sensor). `startTable(name)` picks the first. The
    Driver Station shows the table and where the distance came from. A wrong name warns, never stops.
  - **Shot Tuner:** tunes one table at a time (Y to `TABLE`, then the D-pad) and prints each table's
    line for `Tunables.java`; takes the distance from the odometry when the camera does not see the
    goal (the robot starts at `startSpotIn()`); says the next step on screen and to copy the lines into
    `Tunables.java`.
- Examples: `turretbot` has a POLLEN and a NECTAR table and buzzes gamepad 2 when on target;
  `flywheelbot` has a webcam and whole-robot aim on G1 RB.

### Changed

- `PedroFollower.remainingIn()` is now the distance left along the WHOLE path (all its parts), measured
  from the robot's closest point on the path. It was Pedro's distance for the part it steers along now,
  which Pedro may switch well before the join. The Driver Station's `Path: 18 in to go` uses it too.

## v0.16.0 - 2026-10-21

Nothing old stops working. Some lesson pages have new names and numbers (listed under Changed): old
links to them on the website change. A camera missing from the configuration no longer stops INIT.

### Added

- Simulator: the field map and the 3D view draw the running `follow` step's path (orange, dashed) and
  where the robot really went in its last 30 s of driving (green), with a **Clear lines** button and a
  **Path lines** box. `PedroFollower.plannedPoints()` gives the path as points (read-only).
- The Pedro example's `PedroConstants` are now numbers measured on the simulated robot (the measuring
  steps of Pedro's Foresight tuner, run in the simulator), not Pedro's Quickstart sample numbers.
- **Kit bot templates:** the REV DUO FTC Starter Bot, the AndyMark Robits BIOBUZZ StarterBot and the
  Studica FTC Starter Bot in five files (`examples/.../revbot`, `andymarkbot`, `studicabot`), with each
  seller's configuration names, next to `gobildabot`. A simulator test runs each one's TeleOp and
  autonomous on its robot file. AndyMark's catapult (no catapult part: an `Indexer` on its motor) and
  Studica's Maverick motors and 2:1 flywheel gear (goBILDA motors stand in) are said plainly in the files.
- `docs/templates.md`, **Pick your template**: which code to start from for your robot, and how to copy
  it into TeamCode. Linked from the README, the Quick start, Start from zero and the tutorials page.
- **Venue lighting** (camera settings that suit the light where you are): in INIT the camera tries a
  few settings, counts the pictures that see AprilTags with each, and keeps the best one. A tie goes
  to the first setting; if no tag is seen, it keeps trying through INIT. At START it stops and keeps
  the best setting so far (the first one if none saw a tag), so nothing aims with a trial picture
  (have a tag in view at INIT). Settling starts when the camera has really switched; a setting the
  camera refuses, or a pipeline that sends no pictures ("no pictures from pipeline 2 (missing?)"), gets
  no score and is skipped. It never waits (switches go to a background helper).
  - Limelight: `LimelightTags.autoPipelines(0, 1, 2)` (one pipeline per kind of light; a picture
    counts only if that pipeline made it). Ported from the team's 2025-26 `LimelightPipelineAutoSelector`.
  - Webcam: `WebcamTags.autoExposure()` (the camera's own exposure, then 3, 6 and 12 ms) or
    `autoExposure(ms...)`, with `.gain(g)` (default: the camera's highest), using the SDK's
    `ExposureControl` and `GainControl`; "auto" gets the camera's own gain back.
  - `LightingSweep`: the helper behind both (`lighting()` gives it: `status()`, `scores()`,
    `problems()`, `restart()` for a retry in the match, `newSession()` when the camera restarts).
  - Five-files robot: `Camera.limelight(...).autoPipelines(0, 1, 2)` / `Camera.webcam(...).autoExposure()`.
    The result is on the Driver Station at INIT, with the two `Tunables` lines (Java, ready to paste)
    that skip the test next time. Optional Tunables: `autoLighting`, `lightingSettleMs`,
    `lightingPictures`, `lightingMinPictures`, `lightingAfterStartMs` (default 0: stop at START), and
    for a webcam `exposureMs` and `gain`.
  - A camera missing from the robot configuration no longer stops the OpMode: the `Camera` module
    warns at INIT and sees nothing. A camera still not working 3 s into INIT is a warning too.
  - Modules can do quick work in the INIT loop (`Module.initLoop`; the camera keeps reading pictures).
  - Simulator: `goodPipelines=1|2` (Limelight) and `goodExposureMs=4-10` (webcam) make a pretend
    camera find tags only with those settings.
- Example robot 2 (turretbot) uses `autoPipelines(0, 1, 2)`; Lesson 9 (the shooter lesson) teaches it.
- **Cookbook** ([docs/cookbook.md](docs/cookbook.md)): about 70 short "how do I...?" answers in ten
  topics (driving, buttons and gestures, mechanisms, actions, autonomous, odometry and driveTo, Pedro
  paths, telemetry, tuning and finders, simulator). Each has a few lines of code to copy, one "why"
  sentence and links to the lesson and module page. The code lives in compiled Java
  (`examples/.../cookbook/`, one file per topic, `@Disabled` OpModes); `tools/check_docs.py` fails if a
  page block and its `// recipe:` region differ (`--fix-cookbook` copies them over), and the simulator
  test `CookbookTest` INITs and runs every cookbook OpMode. The page starts with a "Coming from Blocks"
  table and the five files shown whole (from a small cookbook robot), and its scenario example runs as
  `sim/scenarios/cookbook-intake.sim`. Checked with two persona reviews (a student who never coded, a
  Blocks student). Linked from the README, the lessons index, the reference, the quick start and the
  module pages.

### Changed

- Cookbook (review fixes): what `.part(...)` is and when it is needed; the tank drive's `reversed`;
  pick a free button (the parts' standard buttons, `free()`); `-PteamCode` in the scenario command;
  CR servo, operator, CamelCase and GUIDE (tap: button map, hold: tuning menu) explained; Blocks table
  rows for a sensor `if`, a driver choice, `repeat`, servo positions and `waitForStart`; a new
  "repeat steps" recipe (a `for` loop in an action, as in andymarkbot); why the Odometry Tuner is its
  own file. From Blocks to Java: `repeat 3 times` now says the same.
- The simulated Pinpoint and OTOS give a fresh pose at every read (moved on by the speed since the last
  physics step), like the real ones, which update much faster than the physics. Before, a fast loop (Pedro's
  tuners) saw the robot stand still between the physics steps (100 a second).
- **The main track's order:** 1 five files, 2 drive, 2b check the hardware, 3 buttons, 4 intake/lift/claw,
  5 actions, 6 first autonomous, then the lessons your robot needs: 7 odometry, 8 Pedro paths (optional),
  9 a shooter, a camera and a turret (for robots that shoot).
- **Renamed lesson pages** (their web addresses change): `docs/tutorials/22-robot-in-five-files.md` is now
  `docs/tutorials/start/01-robot-in-five-files.md`; `start/08-odometry.md` is now `start/07-odometry.md`;
  `start/09-paths-with-pedro.md` is now `start/08-paths-with-pedro.md`; `start/07-shooter-and-camera.md`
  is now `start/09-shooter-and-camera.md`.
- Under-the-hood tutorial 09 is now called "Autonomous by hand with AutoDrive" (same file), so it is no
  longer a second "Your first autonomous".
- Docs: the Quick start points kit bots to Pick your template first; the hardware check page is called
  "Check the hardware (lesson 2b)" everywhere; the under-the-hood tutorials are marked optional; mentions
  of tutorials 01 and 09 say "under-the-hood", so they are not read as lessons 1 and 9.
- Kit bot templates: REV's poker must be folded by hand before INIT (the code does not move it at INIT);
  the guessed servo positions (REV poker, Studica flap) say to find them with the Servo Finder before the
  first TeleOp; REV and Studica `Tunables` have a commented `kP, kI, kD, kF` line for the Flywheel Finder.
- `WebcamTags.exposure(ms, gain)`: a gain of -1 now means the camera's highest gain.
- `Steps.follow(...)` runs through the new `AutoRoutine.followPath(label, PathJob, seconds)` (the
  same behavior; `PathJob` is the small plug a path-following module fills in).
- Docs: FTC Dashboard, FTControl Panels and Pedro's tuner are for practice only: rule R704.D bans them
  at events, with the steps to take them out. The Pedro lesson and module page list odometry next to
  the mecanum drive as a requirement, and link the new Pedro Path Builder web page.
- Docs: `docs/design/swerve.md` (a study: swerve support is deferred) and `docs/design/possible-work.md`
  (ideas put off on purpose).

## v0.15.0 - 2026-10-21

Nothing old changes: odometry and the pedro module are new. Only a robot with an `odometry(...)` line
uses odometry, and only a project that adds the `pedro` folder gets Pedro Pathing.

### Added

- **Odometry** (package `com.crossbotics.ftchelper.odometry`): where the robot is on the field, from a
  goBILDA Pinpoint (`PinpointOdometry`), a SparkFun OTOS (`OtosOdometry`), or three or two dead wheels
  (`DeadWheelOdometry`). Every kind is a `PoseTracker`: one pose in the drivers' frame (x right, y away,
  heading 0 = facing away, counterclockwise +, the same as the autonomous start spot), `setPose`,
  `isReady` and plain status words ("X pod not found: check its cable"). It updates first every loop,
  before every part; a Pinpoint costs one I2C read per loop.
- `Robot.java`: one line, `odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168))` (or `otos(...)`
  `.mountIn(...)`, `threeWheels(...)`, `twoWheels(...)`), and a `Tunables.Odometry` section (`checked`,
  and an OTOS's `linearScalar` / `angularScalar`).
- `robot.pose()`: the robot's place every loop (also a `Pose` line on the Driver Station).
- `driveTo(label, x, y, heading, power[, tolerance])` in autonomous (`Steps`, `AutoRoutine`,
  `AutoDrive`): drives to a field point and turns at once (mecanum), correcting all the way; it slows
  in proportion in the last 8 inches so it stops on the point. Written for BLUE, mirrored like the start
  spot. Done within the tolerance (1 in) and 2 degrees; while the heading is off it turns with at least
  0.08 power (or `AutoDriveConfig.turnMinPower`, if set). Stuck: the usual stall rule far from the point,
  and less than 0.1 in of progress in 2 s in the last 8 inches. Skipped on a real robot, with a report
  line, until `Tunables.Odometry.checked = true`. A short odometry glitch is waited out (the robot holds
  still up to 0.2 s); still not ready, the step ends with `NO_ODOMETRY` (Pinpoint fault, OTOS not
  answering: re-checked once a second). After a `driveTo` that did not finish, the autonomous goes on
  with its next steps. On a tank drive, `driveTo` stops at INIT with a message (not in the match).
- The pose starts at the autonomous start spot; the end pose goes to TeleOp, which starts the
  odometry there, and field-centric "forward" comes from the odometry's heading. A pose set while the
  Pinpoint is still calibrating (TeleOp's INIT) is kept and written as soon as it is ready.
- Autonomous INIT shows the odometry's status: `Odometry: Pinpoint ready`, or a warning such as
  `Odometry (Pinpoint): X pod not found: check its cable`, so a missing pod is seen before START.
- `.reversed(...)` in the odometry line: names add up over several calls, a Pinpoint takes `"X"` or
  `"x"`, and a name that is not a pod stops INIT (`a Pinpoint's pods are "x" and "y"`).
- `Odometry.custom(devices -> yourPoseTracker)`: your own pose source (it must use the library's frame);
  `driveTo`, `robot.pose()` and TeleOp use it like the built-in kinds. No simulator device for it.
- The **Odometry Tuner** (`OdometryTunerOpMode`): Forward, Left, Spin and Loop tests: which way each
  pod counts, the offsets (or track width), an OTOS's distance scale, and a loop check for `checked`.
  Only the numbers to change are shown (`.offsetsMm(...)`, `.trackWidthIn(..).perpForwardIn(..)`...), so
  `.reversed(...)` and the pod type stay as written; also saved in `finder-results.txt`. The Spin test
  measures the turn from the Pinpoint's own heading (dead wheels and the OTOS: the IMU, so they need
  `.imu(...)` in `drive(...)`, or the tuner stops at INIT). A Forward push more than 10% off says
  "was it really 48 in?" (a test mistake).
- Simulator: a pretend Pinpoint, OTOS and dead wheels from the robot's true motion, with noise
  (`odometry.scaleError`, `odometry.driftDegPerMin`); `:sim:fromRobot` writes the `odometry` lines.
  `:sim:newRobot` and `:sim:fromConfig`: a goBILDA Pinpoint or SparkFun OTOS in the robot configuration
  becomes the odometry (the `Robot.java` line with a `Tunables.Odometry` section, or the robot-file line).
- Docs: [Lesson 8: Odometry](docs/tutorials/start/07-odometry.md) (which device to buy, wiring, the
  tuner, `driveTo`, TeleOp hand-off), a labelled
  [BIOBUZZ field map](docs/images/biobuzz-field-map.svg) in the lesson's frame (made by
  `tools/field_map.py` from the simulator's field file) and the [odometry module](docs/modules/odometry.md); `driveTo` in
  the [autonomous builder](docs/guides/autonomous-builder.md#drive-to-a-field-point-driveto), the
  Odometry Tuner in the [finders guide](docs/guides/finders.md#odometry-tuner-directions-offsets-and-a-loop-check).
- Example: `odometrybot` (lesson 8's robot: a Pinpoint, `ToTheGoal` autonomous with `driveTo`, its
  TeleOp and Odometry Tuner), with a simulator test.

### Added: Pedro Pathing (the pedro module)

- A new **`pedro` folder** (`com.crossbotics.ftchelper.pedro`), added to a robot project with two
  Gradle lines (docs/modules/pedro.md). It brings Pedro Pathing 3.0.2; teams that do not add it never
  download Pedro.
- `Robot.java`: `follower(Pedro.follower(PedroConstants.foresight))`. Pedro uses the `drive(...)` names
  and directions and the `odometry(...)` pose (one odometry, tuned once). It drives only while a path
  runs; the drive motors are plain power during a path and put back after it.
- `follow(label, path[, seconds])` in autonomous (`Steps`): waits until Pedro is at the end; the report
  says `timed out`, `STALLED` or `NO_ODOMETRY`; skipped until `Tunables.Odometry.checked`; a path of the
  wrong kind stops INIT.
- `PedroPath`: lines and curves in our field numbers (the same as `driveTo`), with `turnTo`,
  `keepHeading`, `facePath`, `facePathBackwards`; mirrored for RED like every step. Pedro's own paths
  work too (Pedro's frame, not mirrored).
- `PedroPath.then(...)` (several parts with no stop between them), `.maxSpeed(fraction)` per part,
  `.facePoint(x, y)`, and `.holdEnd([seconds])`: the robot holds the end point while the next steps
  run; the next driving step (`driveTo`, `move`, `turn`), the next path, the end of the OpMode or the
  time limit (5 s by default) ends the hold (`AutoRoutine.beforeDriving(...)`, `PathFollower.isHolding()`).
  A `PedroPath` never changes (every method returns a new one); `then` stops INIT when a part does not
  start where the last one ended.
- `PedroTuning`: Pedro's Foresight tuner (from Pedro's Quickstart) on Robot.java's drive and odometry.
- `PathFollower` (core): the small plug a path-following module implements; `RobotDefinition.follower(...)`,
  `makeOdometry(...)`; `Drive.isReversed(...)` is public.
- Example `examples/.../pedrobot`; the simulator runs Pedro too.
## v0.14.0 - 2026-10-21

One change can stop INIT on purpose: a lift or arm without `min`/`max`, or a setting name with a typo, in
`Tunables.java`. The message says what to fix.

### Changed

- Holding BACK (or tapping it in an EasyTeleOp without a pause job) resets field-centric "forward" only
  while field-centric driving is on. In robot-centric driving it does nothing and says why for 3 s.
- A lift or arm must write `min` and `max` in `Tunables.java`, and a name that looks like a setting
  (`Max`, `kp`, `maxPowr`) stops INIT. Before, `Max` quietly became a position and `max` stayed the
  library's default (a lift could drive past its real top).

### Added

- Docs: [Start from zero](docs/start-from-zero.md) (students who never coded: the robot pieces, Android
  Studio, a first build, reading Java, the Terminal) and [From Blocks to Java](docs/blocks-to-java.md)
  (you never write the loop; a block-to-Java table). For experienced programmers:
  [What runs every loop](docs/guides/every-loop.md) and [Use only what you need](docs/guides/use-only-what-you-need.md).
  Lessons: your own robot's simulator commands, the never-wait rule, and a scenario test of your own.
- `show(label, value)` in `Controls.java`: your own value on the Driver Station every loop.
- A gesture job that takes too long (a `sleep()`) shows a Driver Station warning that names the fix.

### Fixed

- From the reviews (Codex, Fable, and three personas: a student who never coded, a Blocks student, a
  veteran): a lift's `update()` reads its encoder and switches fresh every loop (a reading is used again
  for at most 3 ms); past a soft limit the power is checked and sent every loop; the turret homes on a
  fresh count; a required `min`/`max` must be a real number; capitals and `_` (`MAX`, `k_P`) always stop
  INIT, near names (`maxPwr`) only when the real setting is missing (so `slowZone2` next to `slowZone` is
  a position); `SpinMotor` re-sends its power too. The slow-job warning says "button job". A scenario
  compare with a word explains how to check words (`expect screen has "Label : true"`).
- Loop time: `SafeMotor` (lifts, arms) and the turret read their encoder once per loop instead of up to
  seven times. `SafeMotor` sends its limited power again every 25 loops, so a power written straight to
  the motor cannot stick.
- The "update took N ms" warning now goes away 5 s after the last slow update (it stayed until the end).
- Lifts and arms read each limit switch once per loop too.
- Loop time in the TeleOp debug lines (START + BACK): `Loop: 12.3 ms (81 Hz)`.
- Quick start template: the package-line note is after the package line (a beginner who replaced the
  first line got two package lines and "class, interface, or enum expected").

## v0.13.0 - 2026-10-21

Nothing old stops working, with one change: with `oneDriver = true`, your own `g2` lines move to gamepad 1.

### Added

- Docs from a "new student" walk-through: a laptop setup box (Java, `local.properties`, Windows), every lesson
  snippet with its imports, which robot you build (your own; the lesson robot is the worked example),
  Java ideas (lambda, `::`, chaining, generics) explained where first used.
- [The button menu](docs/guides/buttons.md): every way a button can work in `Controls.java`, as a menu
  (which button, how it starts, what it does, feedback).
- Gestures, several on one button: `tap`, `hold` (with other gestures: from 0.3 s), `doubleTap`,
  `tapThenHold` (last season's "spray"), `taps(n, ...)`, `heldFor(sec, ...)`. A job runs at once unless
  a longer gesture starts the same way. Times in `Tunables.Buttons` (`tapGapSec` 0.35, `holdSec` 0.3),
  live in the tuning menu. The button map lists each gesture.
- Pairs: `g2.back.with(g2.y, job)` (hold the shift key, press the other button).
- Feedback: `.rumble()` / `.rumble(n)` on any job or gesture (a toggle: 2 buzzes = on, 1 = off);
  `g2.rumbleWhen(test)` buzzes each time the robot's test becomes true. `Launcher.isSpinning()`.
- Simulator: a buzz shakes the gamepad picture; a real controller or a phone buzzes too.
- Lesson robot: the claw buzzes, and LB has two gestures (tap: grab and lift; held 1 s: lift down).

### Changed

- `toggle(onEnable, onDisable)` in `Controls.java` keeps its on/off state itself (same behavior).
- With `oneDriver = true`, your own `g2` lines in `Controls.java` now go on gamepad 1 too (like the parts'
  buttons), so one driver can reach them. Before, they stayed on gamepad 2.

### Fixed

- `Controls.java`: a hold's stop job (`hold(job, stopJob)`) now runs even while the robot is paused,
  so a part a hold started (an intake) no longer starts again when the pause ends.

## v0.12.1 - 2026-10-21

Nothing old stops working.

### Added

- [Quick start](docs/quick-start.md): a driving robot in 30 minutes. The five files come from your robot
  configuration (`:sim:newRobot`) or from a new copy-in template (`examples/.../quickstart`) whose lines
  to change are marked STEP 1 to 8. A simulator test drives the template and runs its autonomous.

### Changed

- `:sim:newRobot`'s `Controls.java` comment describes BACK (tap: pause; hold: reset forward) and GUIDE
  (tap: button map; hold: tuning menu) as they work now.
- Docs: the tuning menu opens after START (INIT, START, then hold GUIDE 1 s); lessons 4 and 6 said INIT only.
- The quick start template says what else to delete when a robot has no intake.
- Docs: every page reviewed for a student new to Java (each Java idea and technical word explained where it
  is first used, steps numbered, a "why" for each choice), then checked line by line against the code.
  About 20 wrong statements fixed on the way (e.g. the simulator's keyboard works only after you set the
  gamepad to "Keyboard"; the Servo Finder needs its start point before X and B).

### Fixed

- Robot in five files: a `Tunables.java` section that no part reads (like `Tunables.Auto`) no longer
  overwrites a value your code sets while it runs; the menu writes into the field only when its value changes.
- Two robots in one project with the same tunable name (like `Auto/leaveInches`): each robot now gets
  its own code value, unless the value was tuned. New: `Tunable.fromCode(...)` (the robot in five files
  uses it; `Tunable.number(...)` is unchanged).
- Parts catalog page: the "Top speed (counts/s)" column showed 28 instead of 2800 for the REV UltraPlanetary motors (and 308 instead of 3080 for the NeveRest Orbital 3.7). The simulator was not affected.
- Shot Tuner: the line to copy now compiles: `public static double[][] speedByDistance = {{48, 2550}};   // in Tunables.Launcher`.

## v0.12.0 - 2026-10-21

Nothing old stops working.

### Added

- Finders: every finder also shows (and saves in `finder-results.txt`) the lines for the robot in five
  files, so nobody translates by hand: `Tunables.Claw: P1 = 0.210, P2 = 0.770` (Servo Finder),
  `Tunables.Lift: min = 0, max = 2580, P1 = 1200` (Motor Finder; `Robot.java: add .reversed()` when
  needed), `Tunables.Launcher: kP = ..., kF = ...` (Flywheel Finder), `Robot.java: .wheelMm(100.0)` and
  `Tunables.Drive: strafeCorrection = 1.120` (Drive Tuner), `Tunables.Launcher.speedByDistance = {{48, 2550}, ...};`
  (Shot Tuner, without a hood), `Tunables.Turret: cameraOffset = -1.5` (Aim Tuner, with a turret). The
  section is the device's name in CamelCase. New: `FinderResults.tunablesLine(...)`, `ShotPoints.tunablesLine(...)`.
- `DriveTunerOpMode.robot()` and `ShotTunerOpMode.robot()`: the robot in five files in one line,
  `@Override protected RobotDefinition robot() { return new Robot(); }`. The Shot Tuner then uses the
  robot's first launcher, camera and drive (its turret stays still, its "fire when aimed" checks are off).
- Robot in five files: the `Tunables.java` sections that no part reads (like `Tunables.Auto`) are in
  the tuning menu too ("Auto/parkInches"). The menu's value is written into the field, so the team's
  code reads it as usual; a saved value is used from the next INIT (an autonomous needs no rebuild).
  INIT lists the values to copy back into `Tunables.java`, as for every tuned value.
  Only non-`final` numbers; true/false settings and tables stay as written.
- Examples: `lessonbot/DriveTuner.java` (lesson 6) and `turretbot/ShotTuner.java` (lesson 7), both `@Disabled`.
- `launcher.Launcher.clearReadyChecks()`: removes every `requireReady` check.

### Changed

- `DriveTunerOpMode.robotConfig()` and `ShotTunerOpMode.createLauncher()` are no longer abstract (they
  come from `robot()` by default). Code that overrides them works as before.
- Lessons 4, 6 and 7, the finders guide and reference: copy the `Tunables.` lines; the hand
  translation is gone.

## v0.11.2 - 2026-10-21

Nothing old stops working.

### Fixed

- `Lift.slide(...).part(id)`: the simulator now uses that motor (before, it always wrote a 312 RPM motor).

### Added

- Tutorials: a **main track** of 7 lessons on the robot in five files (lesson 1 is tutorial 22; new
  lessons in `docs/tutorials/start/`: drive, buttons, intake/lift/claw, actions, first autonomous,
  shooter and camera), built on a new example robot (`examples/.../lessonbot`, run by a simulator test).
  Tutorials 01-21 are now the "under the hood" track. The README, "Add it to a project", the starter kit
  and the new-season guide point to the main track first.

### Changed

- The two-driver slow trigger's label says what it does: "slow (squeeze: the further, the slower)".
- The untuned-drive message points to the main track's lesson 6.
- Docs: the reference is online at crossbotics.com/ftc-helper/reference ([Reference](docs/reference.md)).
- `:library:reference` writes an empty web-font file, so the pages no longer ask for a missing one.

## v0.11.1 - 2026-10-21

Nothing old stops working.

### Added

- Docs: [The autonomous builder](docs/guides/autonomous-builder.md), the complete guide to `Auto` and
  `Steps`, built on a tested example (`examples/.../gobildabot/autos/AutoCookbook.java`, run by a
  simulator test with each menu choice).
- `./gradlew :library:reference`: a searchable reference (web pages) of every public class and method
  ([Reference](docs/reference.md)).

### Changed

- Docs: "Keep time to park" explains `parkWithSeconds` (robot module, tutorial 22, easy autos).
- Docs checked against the code (three outside checks): about 40 wrong or out-of-date statements fixed
  (module pages, guides, tutorials, simulator), and missing items added: a table of every simulator
  command and option, every robot-file setting with its default, and the five-files button methods
  (`tapOrDoubleTap`, `slow`, stick `free`/`says`) and part commands.

## v0.11.0 - 2026-10-20

**The Java package has a new name: `com.crossbotics.ftchelper`** (it was `io.github.jasoncross.ftchelper`).
The library now lives at github.com/crossbotics/ftc-helper-lib, and the package name follows the
crossbotics.com domain, so it stays right even if the code moves again. Nothing else changed: same
classes, same methods, same behavior.

### How to update your robot code

1. Update the submodule to v0.11.0 (see [Keep it up to date](docs/02-keep-up-to-date.md)).
2. In Android Studio: **Edit > Find > Replace in Files** (Ctrl+Shift+R, Cmd+Shift+R on a Mac). Find
   `io.github.jasoncross.ftchelper`, replace with `com.crossbotics.ftchelper`, scope **TeamCode**,
   **Replace All**.
3. Build. Red imports left over mean a file still has the old name: run step 2 again.

Robot files for the simulator (`.properties`) and scenario files do not name the package, so they do not
change.

### Changed

- Package `io.github.jasoncross.ftchelper` → `com.crossbotics.ftchelper` in the library, examples,
  simulator, tools, docs and tutorials. The Android namespaces and the simulator's Gradle tasks follow.

## v0.10.2 - 2026-10-20

Nothing old stops working.

### Added

- Tuning menu: B lists the changed values as lines to copy into `Tunables.java` (also in the robot log);
  `Tunable.changedAsCode()`.

### Fixed

- Saving settings on the hub cannot leave a half-written file after a power cut: it writes a new file,
  syncs it, then swaps it in.

## v0.10.1 - 2026-10-20

Nothing old stops working. The library moved to github.com/crossbotics/ftc-helper-lib (old links
forward there).

### Added

- Simulator 3D view: rollers and wheels have dark stripes, so you can see them turn and which way. A
  roller glows green while it takes pieces in and orange while it pushes them out; a flywheel or other
  spinner glows yellow while it turns; a passive part (gravity, springs) blinks while it moves.
- Simulator: the Control Hub's Robot, Motors, Servos & switches and Cameras panels can each be shown or
  hidden; the shown ones stack, and the browser remembers the choice.
- `Pad.Button.slow()`, for one driver: on a trigger (`g1.lt.slow()`) the robot drives slower the further it
  is pulled, down to `slowSpeed`; on a button, slow while held. `DriverControls.update(gamepad, speed)`.
- Simulator: an intake takes pieces in at every roller and spinner (`collect.intake = shape=intake+left_spinner+...`),
  so side spinners that reach into a FLOWER's opening can take its bottom POLLEN.
- Simulator 3D view: double-click the robot (no program running, or in INIT) to move it by dragging and
  turn it with a ring. The robot's spot shows under the map as an `s.start(...)` start spot.
- Simulator 3D view: the in-the-robot badge says when the robot is full (it takes no more pieces).
- Simulator physics: a FLOWER's bottom POLLEN is a real piece the robot can knock out (the next one drops
  in); rollers and spinners drag the pieces they touch at their surface speed; the robot rolls over a
  FLOWER's thin floor plate.
- `Shape.soft()` / `soft=yes`: a soft roller (tubing) that bends past the field; pieces still touch it.
- Parts catalog: goBILDA Boot-Wheel 72 mm (`gobilda-boot-72-rex`, 3615-4008-0072).
- README: a simulator section with a picture.
- README: how the library is made (experience plus AI: Claude, Codex, cross-model checking). Every docs page
  and the simulator page say it is a work in progress and link to GitHub issues for feedback.
- Simulator page: the Driver Hub looks like the Driver Station app (Auto/TeleOp lists, one INIT/play/stop
  button, battery, gamepad lights) and the on-screen gamepads look like real controllers.
- The GUIDE button map leaves out the free buttons (INIT still lists them).
- `./gradlew :sim:run -Pport=8781`: another port, to run two simulators at once.
- **GUIDE shows the button map** in a Controls.java TeleOp (a tap; tap again to close); holding GUIDE
  1 s opens the tuning menu (`EasyTeleOp.buttonHelp()`).
- **BACK pauses the robot** in a Controls.java TeleOp: every part stops at once on brake, the wheels hold,
  actions wait; BACK again and each goes on as before (`robot.pauseAll()`, `resume()`, `togglePause()`).
  Holding BACK 1 s resets "forward" (`EasyTeleOp.backTap()`). STOP brakes every motor too.
- **RB fires one; hold it to keep firing** (the launcher's standard button: `fireWhileHeld()`,
  `ceaseFire()`). `tapOrDoubleTap(tap, doubleTap)` for any button. `Shape.reversed()`: a roller that
  turns the other way from its motor. `Shape.tiltDeg(deg)` (robot file `tilt=`): a part mounted at an
  angle on its parent. `Intake.isPushingOut()`. `Launcher.spinAtStart()`: at speed from START.

- **TeleOp chooses the alliance in INIT** when autonomous did not pass one (gamepad 1: X = BLUE, B = RED;
  also to change it), and shows autonomous's report. `MatchMemory.hasAlliance()`, `rememberReport()`,
  `lastReport()`.
- Simulator: when an autonomous ends, the Driver Station chooses its `preselectTeleOp`, like the real one.
- **An autonomous names its start spot** with its note: `s.start(note, x, y, heading)` (EasyAuto:
  `startSpotIn()`), seen from your wall (written for BLUE; RED as the routes). The simulator puts the robot there in
  INIT for the chosen alliance. `MatchMemory.placement()`, `setPlacement()`, `fieldPose()`.
- The BIOBUZZ examples say the field is turned half a turn between the alliances (`mirrorForRed()` false).
- Simulator: a TeleOp with no autonomous before it puts the robot at its alliance's default spot once the
  drivers choose the alliance; drag the robot on the field map (and turn it with the mouse wheel) while
  no program runs or in INIT.
- Simulator: the Drivers and From-above views are from the alliance in use (autonomous's INIT menu, or
  TeleOp's choice): `MatchMemory.chosen()` / `setChosen()`.

### Changed

- **Autonomous ends when its steps are done**, even with time left, so the Driver Station moves to the
  TeleOp at once (the report is shown at the end, in the robot log and in TeleOp's INIT).
- An autonomous with an untuned drive (`calibrated` / `calibrationConfirmed` false) shows "AUTONOMOUS IS
  OFF: the drive is not tuned yet" on the Driver Station and does nothing, instead of stopping with an
  error. In the simulator it runs anyway, with a warning, so autos can be tried before tuning.
- The 3D view turns a fast wheel slowly (at most 1.2 turns a second), so its direction shows; the real
  speed is in the telemetry.

### Fixed (outside review)

- While paused (BACK), buttons and sticks do nothing: before, a button could start a part (a CR-servo
  feeder) with nothing updating it to stop.
- STOP stops every part even if one fails to pause (an unplugged motor); the first error is reported after.
- Shots still waiting when the robot pauses are fired after it goes on (before, they were dropped).
- Simulator: a field file stack with no pieces no longer stops the physics.
- An autonomous whose profile is changed in INIT to one with an untuned drive shows "AUTONOMOUS IS OFF"
  instead of stopping with an error.
- `EasyAuto.alliancesOnOneWall()` (new, default false): on a field where both alliances' drivers stand at
  one wall (DECODE), the simulator puts BLUE's start spot at that wall, not the far one.
- `Pad.Button.slow()` with two drivers stops at INIT with a message (before, it did nothing).
- `MatchMemory.clear()` also forgets the start spot and the chosen alliance.
- TeleOp INIT: START+B (pairing a gamepad) no longer picks RED.
- Simulator: a FLOWER's bottom piece counts as knocked out when it leaves where it rests (not the field
  file's spot, which may be in a wall).

## v0.10.0 - 2026-10-20

Nothing old stops working: a Robot.java without parts or shapes writes the same simulator lines as before.

### Added

- **Shapes and parts in Robot.java** ([docs/modules/robot.md](docs/modules/robot.md#shapes-and-parts)):
  `part(id)` gives a part's motor or servo by its catalog id, `looks(Shape...)` how it looks and where it
  sits (`Shape.roller`, `flywheel`, `arm`, `slide`, `box`), and `./gradlew :sim:fromRobot` writes them into
  the simulator robot file with no TODO shape guesses. Intake, Positioner, Launcher and Indexer take shapes. Also
  `chassisMm`, `heightMm` and `fixed(...)` on the robot, `Drive.wheel(id)` and `wheelsAtMm`,
  `Intake.holds(n)`, `Launcher.shotAngle(deg)`.
- **Wheels and rollers in the parts catalog** (`Parts`, `sim/parts/parts.json`): goBILDA Gecko 48 and
  72 mm, Hogback 96 mm, Offset surgical tubing rollers 60 and 90 mm. A shape takes its size from here.
- **Passive parts in the simulator** (`passive=yes`): moved by gravity or springs, not the code; the
  size rules try their whole range. `drop=SERVO>0.6`: a part a servo pushes over falls to its stop and
  stays (an intake folded up at the start).
- **Scenarios:** `expect shape NAME OP VALUE` (an arm's angle, a slide's inches).
- The flywheelbot example names its parts and draws its robot, with a folding intake.
- `Shape.flywheel()` for a launcher wheel not in the catalog (with `diameterMm` and `lengthMm`).
- `robot.tallestIn` in the robot file (written by `heightMm`): a part the shapes do not draw still counts
  in the size rules.
- Checked when the robot file is made: a shape setting that does not fit its shape, riding `on` a part
  with no shape, an intake with no roller, one `Shape` in two parts, two parts with one name in the
  robot file, a `shotAngle` outside 0 to 180. Each stops with the part's name.
- The robot-file skill puts sizes and shapes in Robot.java for a team that writes its robot in five files.

## v0.9.0 - 2026-10-19

Nothing old stops working: the starter kit and every class from v0.8 build and behave the same. The new
way to write a robot (the robot in five files) is added next to it; tutorial 22 shows how to start.

### Added

- **The robot in five files** (package `robot`, [docs/modules/robot.md](docs/modules/robot.md),
  [tutorial 22](docs/tutorials/start/01-robot-in-five-files.md)): `Robot.java` says what is on the robot
  (`RobotDefinition`: `drive(...)` and one `add(...)` per mechanism), `Tunables.java` holds every number
  (one section per part), `Controls.java` is the TeleOp and the button map (`ControlMap`), `Actions.java`
  holds moves that use several parts (`Action`), and each autonomous is a list of `Steps` (`Auto`).
- **Ready-made modules:** `Drive` (mecanum or tank), `Intake` (motor and/or CR servos, on at the start),
  `Lift`, `Arm`, `Claw`, `Wrist`, `Positioner`, `Turret` (follows the camera's goal inside its limits),
  `Launcher` (spin up, fire, speed from the camera's distance), `Indexer` (servo, CR servo or motor) and
  `Camera` (Limelight or webcam, the alliance's goal). Each brings its Tunables section with safe
  defaults and its standard buttons.
- **Tunables:** the numbers tuned most are in the tuning menu (which ones, and when they apply:
  docs/modules/robot.md); INIT lists the values tuned on the
  robot that differ from Tunables.java, as lines to copy back. Missing numbers fall back to defaults
  (INIT notes them); a tuned position stays inside its safe range.
- **The standard button map**, with a one-driver switch (`oneDriver` in Controls.java), checked at INIT
  (two jobs on one button, a job on a driving button) and shown on the Driver Station in INIT.
- **Actions** never block, claim the parts they use (the driver moving a part stops them), stop with a
  reason when a wait times out, and run the same in TeleOp and autonomous. **Modes** for always-on rules
  (a turret following, a launcher's speed from distance).
- `./gradlew :sim:newRobot`: the five files from the robot configuration. `./gradlew :sim:fromRobot`:
  the simulator robot file from Robot.java (generated lines marked, the team's lines kept, a device
  described differently stops it).
- Examples: goBILDA's starter bot rewritten in five files (`examples/.../gobildabot`), and two more robots
  (`flywheelbot`, `turretbot`); all three run in the simulator's tests.
- `AutoContext.note(text)`: a custom step can put a note on its report line.
- `EasyTeleOp`: `onStart()`, `addInitTelemetry(...)` and `fieldCentricButton()`. `EasyAuto`:
  `addInitTelemetry(...)`. `DriverControls.update(gamepad, slow)`. `ControllerBindings`:
  `bindTriggerRelease`, `bindTriggerToggle`.
- Simulator: a flywheel's feeder can be a servo (an indexer that pushes); a claw that closes toward 1
  (`grab.... closedAbove=`).

## v0.8.1 - 2026-10-17

Nothing changes for a robot project that pulls this version. Everything here is in the simulator.

### Added

- **The HIVEs tip.** Each CELL is an open box (floor, back, sides and roof, from the field CAD: about
  20 x 14 in open, 12 in deep), so a shot goes in, rolls to the back and stays. When the pieces resting
  in a HIVE's up CELL weigh 187 g or more (the team's field tests; the 3 staged NECTAR are 123 g, so the
  3rd POLLEN tips it) the HIVE turns 60 degrees: the other CELL comes up, the pieces fall out and the 8
  AprilTags on its CELLs move with it, so cameras see them where they are now. The field file's new
  `hives` list gives each HIVE's pivot, which CELL starts up, the CELL shape, the tipping weight and its
  tag IDs. In a match only LAUNCHED pieces may tip it (manual 10.5.1, G417); the simulator does not
  referee that.
- The page shows the pieces in each up CELL and the tips (Control Hub, Robot tab); with a field model
  its HIVE parts turn, without one the CELLs are drawn as plates.
- A piece that goes over a wall is taken off and counted ("left the field").
- Scenario checks `expect cell "Red" >= 1`, `expect tips "Red" >= 1`, `expect left >= 1`; `print pieces`
  lists them. Example: `sim/scenarios/hive-tips.sim`.
- **What the robot holds, on the 3D view:** a badge in the corner with a dot in each held piece's
  colour, an empty dot for each free place (`holds=`) and the count.

### Changed

- The HIVEs are no longer solid blocks: a shot at a CELL used to bounce off, now it goes in. The 3 staged
  NECTAR in each up CELL are loose pieces resting in the CELL (they fall out when it tips).
- A piece is taken off the field as soon as it is past a wall (was 10 ft from the centre).

## v0.8.0 - 2026-10-17

Nothing changes for a robot project that pulls this version: the library builds the same way and no
class was removed. Everything under "Simulator" runs only on a laptop, from the `ftc-helper-lib` folder.

### Added

- **Simulator** (`sim/`): a pretend Control Hub and Driver Hub on your laptop. `./gradlew :sim:run`,
  then open http://localhost:8780. Your real OpMode code runs with pretend motors, servos, switches,
  IMU and battery, and a robot on the season's field. The page fits one laptop screen: Driver Hub,
  the field in 3D with the gamepads under it, and the Control Hub in tabs. Gamepads: on-screen,
  keyboard, a real USB or Bluetooth controller, or a phone on the same WiFi (`-Plan`).
  Guide: [docs/guides/simulator.md](docs/guides/simulator.md).
- Simulator physics (ode4j): the robot is a solid body pushed by its wheels as hard as their grip
  allows; it stops at and slides along walls and field parts, its wheels slip while it pushes, and its
  parts hit things too. `physics = simple` keeps a plain "a bump stops the move" rule. `robot.massKg`,
  `drive.grip`.
- **The robot file** (`sim/robots/*.properties`): every device under its configuration name, the
  motors and servos from the parts catalog, the robot's size, start pose, field and which side's motors
  are mirrored (`drive.mirrored`). In a robot project (`ftc-helper-lib/` next to `TeamCode/`) the
  simulator builds **your TeamCode**, lists only your OpModes and uses `simulator/robot.properties`;
  other libraries go in `simulator/sim-dependencies.gradle`. Iterative OpModes run too.
- **Parts catalog** (`sim/parts/parts.json`, page: [docs/guides/parts-catalog.md](docs/guides/parts-catalog.md)):
  common FTC motors and servos with the numbers from the sellers' pages and the date checked. A part
  not in it: `rpm=`/`ticksPerRev=` on a motor, `rangeDeg=`/`secPer60=` on a servo, or a team parts file.
- **Season fields** (`sim/fields/biobuzz-2026.json`, measured from the official field CAD): solid and
  overhead elements, 16 AprilTags, walls, tape and zones; the game pieces (POLLEN, NECTAR) where the
  match starts them (4 POLLEN in each GARDEN and each FLOWER, 3 NECTAR in each up CELL), the FLOWER tops
  as goals, and the 4-POLLEN preload. An intake at a FLOWER's bottom opening takes its POLLEN one by one.
- **Back to start, Reset field and Preloaded** on the page (and `preload = off`, `set preload`,
  `set field start`): start a practice run with or without the preload, or put the pieces back.
- **The field in 3D** on the page (three.js, bundled so it works offline): field, robot, tag squares,
  camera cones and pictures, Drivers / Audience / From above / Follow robot views; a team's own field
  model with `field.model`.
- **Robot shapes** (`shape.NAME = box|slide|arm|turret|claw|roller|flywheel`, moved by their motor or
  servo), drawn in 3D and used for the **size rules**: the 18 in start cube and 18 x 24 x 29 in now and
  at full reach (every pose tried), and nothing below the tiles. Scenario checks `expect size ...`,
  `print size`.
- **CAD models of the parts**: `robot.model`, `model=`, `baseModel=`, `cad.units`, `cad.axes`,
  `cad.origin`, `cadPose=` draw a team's STL or OBJ exports in place of the boxes; the size rules use
  them too; big models are simplified for the picture. `print cad` checks each model sits on its box.
- **Game pieces in the robot file**: `collect.` (an intake), `shoot.` (a flywheel shot: `angle=`,
  `share=`, `spin=`, `speed=`, or a `table=`), `grab.` (a claw). A flywheel's feeder may be a motor or
  a CR servo. Shots fly with air drag and spin. **Shot tables** replace the guesses with how your
  launcher really throws, with scatter.
- **Pretend cameras**: webcams (`camera.KEY = name=..., forward=, up=, pitch=`) that work with
  `WebcamTags` and the SDK's own `VisionPortal`/`AprilTagProcessor` (BIOBUZZ cluster detections, the
  SDK's ftcPose formulas); a pretend Limelight 3A (`limelight.KEY`) that `LimelightTags` and
  `getLatestResult()` code read.
- **Scenario files** (`.sim`, `./gradlew :sim:scenario -Pfile=...`): drive the simulator like a person
  and check what happens (screen text, motors, servos, position, bumps, pieces, cameras, size rules).
  Every scenario also runs as an automatic test; `sim/scenarios/` has examples.
- **Whole-OpMode tests** (`FinderSafetyTest` and others) run on every push (they found the v0.7.3 bug).
- **Robot-file skill** (`skills/robot-file/`): instructions an AI coding agent follows to build your
  robot file from your robot configuration, TeamCode, measurements, photos or CAD, then check it.
  `./gradlew :sim:fromConfig -Pconfig=...` drafts the file from the Control Hub's configuration XML.
  Guide: [docs/guides/robot-file-skill.md](docs/guides/robot-file-skill.md).
- **Four BIOBUZZ starter bots** (REV, goBILDA, AndyMark, Studica) as simulator robots and scenarios,
  with every number's source noted: [docs/guides/starter-bots.md](docs/guides/starter-bots.md).
- **CAD tools** (`tools/cad/robot_cad.py`, Python with build123d): read a whole-robot STEP file into a
  parts list with seller part numbers, find which motor drives what, and write one STL per moving
  group. **Launcher program** (`tools/launcher/launcher_lab.py`): works out how a flywheel launcher
  throws from the CAD and writes a shot table.
- **Characterizer** (`CharacterizerOpMode`, `CharacterizeMath`, starter kit `Characterizer.java`):
  measures how the drive, turning, lifts/arms and flywheels really move and fits their feedforward
  numbers (kS, kV, kA, kG, coasting); shows the lines to copy and saves each test's log.
  `./gradlew :sim:fit -Plog=... -Pwrite` puts the real numbers into your robot file.
- **`Feedforward`** (control): power = kS x sign(speed) + kV x speed + kA x speeding-up + kG, with
  battery scaling.
- **`TagGroup`** and `LimelightTags.groups(...)`: a Limelight also reports each row of tags as ONE
  target named like the webcam's cluster ("RED SCORING", ...), so `camera.target("RED SCORING")` works
  with either camera.
- CI runs the simulator tests and checks the parts page is current.

### Changed

- Tutorial 18 and the examples aim at a BIOBUZZ CELL group (they used last season's tag 20) and show
  "Targets seen".
- Docs: `distanceIn` is flat in the camera's own view; the SDK's BIOBUZZ cluster aiming point is about
  9 in beyond the tag row, not the CELL opening; a webcam reports a clustered tag only as its cluster.
- Finders: holding START opens help while running too (it only worked in INIT).
- Finder results and Characterizer logs go to the folder in the Java setting `ftchelper.dataDir` when
  it is set (the simulator uses it); on the robot the folder is unchanged.

### Fixed

- Characterizer safety: it stops when something pushes but does not move (each drive wheel on its own,
  a lift going up or down), when a lift move runs out of time, when a wheel turns the wrong way, at the
  lift's soft limits whatever the power, and on GUIDE or START; a drive roll ends at `maxDriveCounts()`
  from where it started; every motor is stopped even if one fails.
- Fit tool: keeps Windows line endings, notes and motor names with spaces; skips bad lines; leaves out
  poor fits (`-Pforce` uses them); never adds a motor; keeps a `.bak`.

## v0.7.3 - 2026-10-16

### Fixed

- **Every finder crashed at INIT in v0.7.2** (the help-key code called itself without end). Found by
  the new simulator on its first run. A test now covers it.

## v0.7.2 - 2026-10-16

### Fixed (Codex "Astra" and Fable reviews of the help page)

- After START and after help closes, a finder ignores gamepad 1's sticks, triggers and held buttons
  until they are let go once (`FinderOpMode.inputsReady()`), so a finger already down cannot restart
  a motor, a speed test, aiming or driving.
- Drive Tuner: GUIDE (or START) stops a moving test at once and opens help; the test is not measured.
  The help page uses the real test distance.
- Help also opens by holding START 1 s, for controllers whose GUIDE button never reaches the app.
- `helpLines()` has a default, so a team's own finder still compiles.
- Flywheel Finder: after help, the motor is back in step 1's set-up (a new top-speed test is right).
- `PositionServo` slow moves never jump the rest of the way after a pause in updates.
- Help wording: what really stops (motors; servos hold, the camera runs), LOW/HIGH end names,
  step sizes, homing after help; every finder's Javadoc names the help key.

## v0.7.1 - 2026-10-16

### Added

- Finders: **GUIDE = help page** (every button and the steps), in INIT or while running. While it is
  open, the finder's buttons pause and everything that moves stops. New `FinderOpMode.helpLines()`
  and `pause()`.

## v0.7.0 - 2026-10-16

### Added

- **Finders** (new package `finders`): ready-made OpModes that measure the numbers your code needs
  and show the code lines to copy (also saved in `/sdcard/FIRST/ftc-helper/finder-results.txt`):
  `ServoFinderOpMode` (servo ends and positions), `MotorFinderOpMode` (limits, positions, top speed),
  `FlywheelFinderOpMode` (top speed, speed-control gains, recovery time), `DriveTunerOpMode` (wheel
  size by push and drive tests, strafe correction), `ShotTunerOpMode` (builds the shot table: speed,
  hood, distance from the camera, single or continuous shots), `AimTunerOpMode` (camera offset).
  Shared parts: `FinderOpMode`, `FinderResults`, `FinderMath`, `Chooser`, `ShotPoints`.
- `Turret.cameraOffset(deg)`; `Launcher.getHood()`, `Launcher.hasTable()`; `Flywheel.getMaxRpm()`;
  `PositionServo.getTargetDegrees()`.
- Starter kit: `ServoFinder`, `MotorFinder`, `FlywheelFinder`, `DriveTuner`. Examples:
  `ExampleShotTuner`, `ExampleAimTuner`. Tutorial 21 and the [finders guide](docs/guides/finders.md).

### Changed

- `AimController.offset` docs: it is where the target sits in the camera's view when the launcher is
  aimed (left = +). The code is unchanged.

### Safety and correctness (Codex "Astra" and Fable reviews, before release)

- Servo Finder: nothing moves until you choose a start point and press A; the servo stays between
  the marked ends (LB to go past); a position outside the ends is noted.
- Flywheel Finder: the A press that chooses the motor does not start the wheel; X reverses the
  wheel; a backward encoder is reported instead of guessed as `.reversed()`.
- Shot Tuner: the flywheel is off at START (LB on); hold-RT shooting stops after B, a jam or a
  "gave up"; speed and hood stay inside the hardware's limits, and a hood never moved starts at a
  real angle.
- Drive Tuner: a stuck or timed-out move is not measured; the push test checks every wheel agrees
  and lets the wheels roll freely; new numbers are used at once; the moves run outside button actions.
- Aim Tuner: X homes a turret; LB stops the flywheel; the aim restarts fresh after A.
- Results: one device's lines no longer delete another's; the save result shows on the screen.

## v0.6.0 - 2026-10-15

### Added

- **`EasyTeleOp`** (new package `teleop`): a whole TeleOp where you add only your mechanisms, buttons
  and per-loop code. Driving, field-centric switch, "forward" from autonomous, match timer buzzes,
  tuning menu, debug telemetry, warnings and stopping everything come with it.
- **`RobotConfig`** (core): the drivetrain, autonomous tuning and IMU described once.
  `EasyTeleOp` and `EasyAuto` both read it from `robotConfig()`.
- **`DriverControls`** (drive): standard stick driving for mecanum and tank, slow trigger,
  field-centric switch. Usable on its own.
- Tutorial 20 (Easy TeleOp) and its program; module page `docs/modules/teleop.md`.

### Changed

- `EasyAuto`: `driveConfig()`, `autoDriveConfig()` and `createImu()` are no longer required; return
  `new RobotSettings()` from `robotConfig()` instead. Old autonomous code that overrides them still works.
- Starter kit: `RobotSettings` extends `RobotConfig`; `MainTeleOp` is built on `EasyTeleOp`
  (about 40 lines of code instead of 109, same buttons); `StarterAuto` uses `robotConfig()`.
  A tank drivetrain now needs only `DriveConfig.tank()` in `RobotSettings`.
- Tutorial `RobotSettings.config()`; tutorial 16 uses it and preselects tutorial 20's TeleOp.
- Debug telemetry: START + BACK together, in either order. BACK alone resets "forward" when it is
  let go, with one buzz, so the chord can never reset it by accident.
- Settings (tuning menu, profile) are saved on a background thread: the robot loop never waits.

### Fixed (Codex "Astra" and Fable reviews)

- `DriverHeading` no longer loses autonomous's heading on INIT, STOP, INIT again; it is cleared at
  START (`forgetSaved()`).
- `EasyAuto`: `startHeadingDeg()` is mirrored for RED like the routes. A missing IMU gives a clear
  message instead of a crash. An error while stopping no longer hides the program's own error
  (same in `EasyTeleOp`).
- Opening the tuning menu runs the "release" actions of held driver buttons
  (`ControllerBindings.releaseAll()`), so nothing started by a held button keeps running.
- `MecanumDrive` / `TankDrive`: one failing motor no longer keeps the others from getting their power (or stop).
- `DriverControls.drive(...)` and `tank(...)` for custom driving; `EasyTeleOp.configureDriving(...)`.
- The IMU is read for the debug heading line only when debug is on.

## v0.5.0 - 2026-10-15

### Added

- `DriverHeading` (drive): field-centric "forward" continues from where autonomous ended, so drivers
  do not need to reset it at the start of TeleOp. `EasyAuto.startHeadingDeg()` for robots that do not
  start facing straight away from their driver. `PoseStore.ageSec()`.

### Changed

- Starter TeleOp and tutorial 12's TeleOp start in robot-centric mode; Y turns field-centric on.
  Both use `DriverHeading`.

## v0.4.2 - 2026-10-15

### Changed

- Plain-language comments through the whole library, every example and tutorial program, and the
  code blocks in the docs, written for new programmers. Style: [docs/commenting-style.md](docs/commenting-style.md).
- Slow-code alarms (`getSlowActionWarning`, `getSlowUpdateWarning`) now go away 5 s after the last slow event.
- Starter TeleOp: hold START and press BACK for debug telemetry. BACK alone still resets "forward";
  with START held it no longer also resets it.

### Fixed

- `AutoRoutine`: a step cut short by the park time says "cut short (time for park)", not "done" or "timed out".
- `EasyAuto`: STOP in INIT no longer overwrites the alliance and heading saved for TeleOp. A profile
  change in INIT rebuilds the mechanisms too, not only the drive. `createSubsystems()` may return null,
  and if it throws, the wheels are still stopped.
- `MatchTimer`: a cue at exactly the full length runs; works with any clock value.
- `Flywheel`: the follower gets the `pidf` gains whether `pidf` is called before or after `follower`.
- `Launcher.shoot` with no shot speed says so at once ("no shot speed ...").
- `RobotTelemetry.setMinIntervalMs`: warnings from skipped loops are kept for the next send.
- `BackgroundWorker`: an error without a message is reported by its name, not "null"; a missing SDK
  class no longer stops the worker.
- `WebcamTags` / `LimelightTags`: `close()` then `start()` again works like new (exposure is sent again;
  the first Limelight frame is not taken for an old one).
- `HardwareCheckOpMode`: the first nudge of a servo with no known position starts from 0.5.
- Examples: tutorial 07's F gain and tutorial 17's manual speed cannot go below 0; tutorial 12's
  autonomous does nothing if STOP is pressed in INIT.

## v0.4.1 - 2026-10-15

### Fixed (non-blocking audit)

- `LimelightTags`: the pipeline switch and `setRobotHeading` are network requests that wait for an
  answer; they now run on a background thread, so they never slow the loop (heading: only the newest is sent).
- `WebcamTags`: manual exposure settings (USB requests) are applied in the background.
- `HardwareCheckOpMode`: IMUs are started in INIT, not in the loop.
- Tutorial 10: the start delay no longer uses `sleep()`; waiting loops call `idle()`.

### Added

- `BackgroundWorker` (util): slow jobs off the robot's loop.
- Slow-code alarms: `ControllerBindings.getSlowActionWarning()` (actions over 20 ms) and
  `Subsystems` warns when a part's `update()` takes over 25 ms.
- Every intentionally blocking method says BLOCKS in its Javadoc.
- Guide: [Non-blocking code](docs/guides/non-blocking.md). `tools/check_blocking.py`, run on every push.

## v0.4.0 - 2026-10-15

### Added

- **Mechanism helpers** (`mechanism`): `PositionMotor` (named positions, PID, limits, homing switch,
  slow zone, manual with hold, units, tunable), `SpinMotor` (in/out/stop, jam back-out),
  `PositionServo` (named positions, degrees, slow moves, arrival estimate), `SpinServo` (CR servo, timed runs, angle sensor).
- **Launcher kit** (`launcher`): `Flywheel` (RPM, hub speed control, ready with settle time, shot detection),
  `ShotTable`, the `Feeder` interface with `MotorFeeder` and `ServoFeeder`, `Turret` (degrees, safe way
  round inside limits, field-relative aim, homing), and `Launcher` (feeds only when every check is ready, gives up with a reason).
- **Vision** (`vision`): `TargetSource` interface, `WebcamTags` (SDK 12 single tags and clusters),
  `LimelightTags` (Limelight 3A), `TargetObservation` (bearing + = left), `AimController` (no aim on stale/missing targets).
- **Easy autonomous** (`auto`): `AutoRoutine` (named steps, mirroring for RED, park reserve, report),
  `EasyAuto` base class (INIT menu, placement text, timer, delay, cleanup, TeleOp hand-off), `AutoStep`, `AutoContext`.
  Mechanisms provide ready-made steps (`goToStep`, `startStep`, `shootStep`, `aimStep`...).
- **Match** (`match`): `Alliance`, `MatchTimer` (cues and gamepad buzzes), `InitMenu`, `MatchMemory`.
- **Core** (`core`): `Subsystem` interface and `Subsystems` group, `Profile` (PRACTICE/COMPETITION, saved), `SettingsFile`.
- **Tuning** (`tuning`): `Tunable`, `PidTunables`, `TuningMenu` (gamepad menu, PRACTICE only, saved on the hub).
- **Telemetry**: `RobotTelemetry` (standard and debug levels, warnings on top, rate limit).
- **Safety**: `JamDetector`.
- **Testing**: `HardwareCheckOpMode` (lists and tests every configured device).
- `AutoDrive.setStopCondition(...)`; the simulation constructor is now public.
- Examples: `ExampleDriveIntake` (mecanum + slow toggle + intake on at start, stop/start/reverse), the
  6-file **starter kit**, tutorial programs 14-19.
- Docs: module pages for every new package, guides (architecture, profiles, tuning, easy autos, testing
  procedures, new season, extending, the drive+intake example), the starter kit page, tutorials 14-19,
  diagrams (Mermaid and SVG).

### Changed

- Built and tested against FTC SDK 12.0 with the `Vision` artifact (compileOnly) for the vision classes.

### Checked

- Full outside reviews by Codex ("Astra") and Fable before release. Fixed from their findings:
  turret homing (already-on-switch, timeout with fault, slow manual before homing), out-of-range turret
  targets never count as aimed, `Turret.aimAtTarget` (fresh pictures only, each once), servo arrival
  estimate when the same target is re-sent, `Launcher.stop()` stays OFF (new IDLE state), cleanup that
  stops every part even if one throws (Launcher, EasyAuto, AutoRoutine), Limelight picture time
  includes its own latency, one distance meaning for both cameras (along the floor), old camera
  targets dropped after 500 ms, flywheel follower without an encoder is switched off, flywheel stop
  coasts, a new `shoot()` clears a feeder jam, `AimController.turnPower` uses the drive's sign
  (`ccwPower` added), `AutoRoutine` no longer mixes step-building and running waits, `EasyAuto`
  applies a profile changed in INIT and has `mirrorForRed()`, `PositionMotor.units()` order check,
  typo'd position names warn instead of crashing, missing switch names warn, hardware check stick
  deadband, tuning-menu buttons no longer also drive the robot in the examples.

## v0.3.0 - 2026-10-15

### Added

- **13 hands-on tutorials** in `docs/tutorials/`, from a first OpMode to a full robot structure and
  testing without a robot. Each has a complete program in `examples/.../tutorials/` that the build
  compiles, plus "Run it", "If something goes wrong" and "Try this" sections.
- Tutorial 12's example robot (`Robot`, `Lift`, `LiftHeights`, `Claw`, `MainTeleOp`, `MainAuto`) and
  tutorial 13's tests (`LiftHeightsTest`), run by the build.
- `tools/check_docs.py`: checks links, heading links, that every public class is documented, and that
  every tutorial program is used. Runs on GitHub with every push.
- Module pages link to the tutorials that teach them.

### Changed

- The old "first TeleOp" and "first autonomous" pages are replaced by tutorials 04, 05 and 09.
- Contributing: every change now also updates the tutorials (step 5 of 6).

## v0.2.0 - 2026-10-15

### Added

- **Standard (tank / differential) drive**, 2 or 4 motors: `TankDrive` (arcade and tank style),
  `DifferentialMixer`, `DriveConfig.type`, and the factories `DriveConfig.mecanum()`, `tank()`, `tankTwoMotor()`.
- `AutoDrive` works with standard drive too: `move` forward/back, `turn`, `turnTo`.
  Sideways moves and `moveWithTurn` give a clear error on a standard drive.
- `DriveConfig.validate()` and `hasBackMotors()`. `MecanumDrive` and `TankDrive` refuse the wrong config type.
- `ExampleTankTeleOp`. `MotorDirectionCheck` now works for every drive type.

### Changed

- Built and tested against FTC SDK **12.0** (the 2026-27 BIOBUZZ release). Still works with 11.2.1.
- README rules check now cites the 2026-27 manual (R304 reuse, R105 B hard stops).
- Package renamed to `io.github.jasoncross.ftchelper` (was `org.octobots.ftchelper` before the first push).

## v0.1.0 - 2026-10-15

First version. Built and tested against FTC SDK 11.2.1.

### Added

- **hardware:** `Hw` (device lookup with a list of real names on error), `MotorSetup`, `ServoSetup`, `ImuSetup`, `BulkReads`.
- **gamepad:** `ControllerBindings` (press, release, hold, toggle, trigger press/hold/axis, held-at-START guard),
  `StickShaping` (deadband, curve, slow-mode trigger), `ToggleRumble`, `AimRumble`.
- **drive:** `DriveConfig`, `MecanumDrive` (robot-centric and field-centric), `ReverseDrive`, `MecanumMixer`,
  `AutoDrive` with `AutoDriveConfig` (encoder moves, move-while-turning, IMU turns), `MotionMath`, `StallDetector`.
- **control:** `PidController` (with feedforward, integral limit, angle mode, tolerance), `SlewRateLimiter`.
- **telemetry:** `TelemetryHelper`.
- **safety:** `SafeMotor` (soft limits, limit switches, homing, slow zone, PID moves), `SoftLimits`.
- **util:** `MathUtil`, `Angles`, `LoopTimer`, `FieldPose`, `PoseStore`.
- Example OpModes: `ExampleTeleOp`, `ExampleAuto`, `MotorDirectionCheck`.
- Docs for every class and method, set-up and update guides, troubleshooting.

### Checked

- Outside review by Codex ("Astra") before release. Its 10 findings were fixed: AutoDrive power limit,
  no turning after a stall, one time limit for move-and-turn, true diagonal distance, safe homing for
  `SafeMotor`, slew reversal, PID derivative at the -180/180 seam, IMU start check, held-button guard,
  rumble docs. Robot simulation tests were added for `AutoDrive` and `SafeMotor`.

### Changed from last season's robot code

See [Coming from last season's code](docs/from-last-season.md).
