# Changelog

What changed in each version. Newest first. How to update: [Keep it up to date](docs/02-keep-up-to-date.md).

## Unreleased

### Added

- **A servo's full range: `.fullRange()`** on `Claw`, `Wrist`, `Positioner`, `Hood.servo`, `Turret.servo` and
  `Indexer.servo` (and `ServoSetup.fullRange()` / `pulseRange(lowUs, highUs)` / `setPulseRange(...)`,
  `PositionServo.fullRange()` / `pulseRange(...)` / `isFullRange()` for the building blocks). The FTC SDK
  sends 600-2400 microseconds for positions 0 to 1 (`PwmControl.PwmRange.defaultRange`); most FTC servos
  take 500-2500, so a goBILDA 2000 servo turns 270 of its 300 degrees. With `.fullRange()` the part sends
  the servo's whole pulse range at INIT (the catalog part's, else 500-2500 us) and 0 to 1 turns all 300.
  Nothing changes without it. A servo that cannot change its pulse (not a REV hub servo) keeps its range
  and INIT says so. Measure positions after turning it on (they point elsewhere with the new range).
  The Servo Finder has the same switch (Y while choosing a servo; its lines then say `.fullRange()`), and
  the Hood Finder uses the hood's. Docs: [Full range](docs/modules/hardware.md#full-range), the cookbook's
  "How do I get my servo's full range?", tutorial 02, lesson 4, troubleshooting.
- **Parts catalog: servo pulse ranges** (`pulseMinUs`, `pulseMaxUs` in `sim/parts/parts.json`, checked
  against the sellers' pages; `Parts.servoPart(id)` with `rangeDeg`, `sdkRangeDeg()`,
  `degreesOver(lowUs, highUs)`). The parts page shows each servo's range on the hub and its full range.
- **Simulator: servo pulse range.** A simulated servo is a `PwmControl` like the hub's: the code's
  `setPwmRange` changes how far 0 to 1 turns it, and each INIT sets it back to 600-2400 us. A robot file's
  `servo.NAME = ..., pwm=500-2500` says the code uses the full range (`:sim:fromRobot` writes it).
- **Tests:** every example robot INITs on its simulated robot with no error (`ExampleRobotsInitTest`), the
  `Tunables.java` that `:sim:newRobot` writes INITs too, and the field check and full range have their own
  library tests.

- **Tunables reference** (`docs/guides/tunables.md`): every number the library reads from
  `Tunables.java`, one table per part (Drive, Odometry, Aim, Buttons, Intake, Lift and Arm, Claw/Wrist/
  Positioner, Turret, interlock rules, Launcher, Indexer, Hood, Camera, PieceSensor, Lights: 133 fields).
  Each row: what it does, units, default (or REQUIRED: INIT stops without it), whether it is in the
  tuning menu (at once, or from the next INIT), which finder measures it, and when to change it. The top
  explains section names, what INIT checks (including the misspelled-field stop below), units, and how
  to copy a finder's lines. Linked from each part in
  `docs/modules/robot.md`, lesson 1 and the README.
- **`tools/check_tunables.py`** (in CI): reads the robot package's code, lists every field it reads
  (`--list`), and fails when the reference misses a field, names one the code no longer reads, or has a
  wrong default, Live or REQUIRED cell; also when an example robot's `Tunables.java` (or the one
  `:sim:newRobot` writes) has a field its part never reads.
- **Docs: several autonomous modes, and choosing one** (docs/guides/autos.md): one OpMode per plan or
  one OpMode with an INIT menu, a decision table, shared steps in a `Routes.java`, names and groups for
  the Driver Station, three worked examples (tested in the simulator), scenario files that choose in the
  menu, and a match-day checklist. Cookbook: three recipes (`ChooseAutoRecipes`), tested by `CookbookTest`.
- **Docs: Git and GitHub for FTC teams** (docs/guides/github.md): the words, a team workflow, and every
  step for the command line and GitHub Desktop (clone with submodules, commit, push, pull, branch, merge,
  a conflict, undo), what never to commit, student privacy, and backups before an event.
- **Docs: keeping the library up to date** (docs/02-keep-up-to-date.md): why, how to hear about a release
  (Watch > Releases), how to read the CHANGELOG, updating with the command line or GitHub Desktop, the
  checks after an update (robot file, scenarios, robot), when not to update, and going back.
- **The start spot can be an INIT menu choice:** a `start(...)` (or `parkWithSeconds(...)`) inside an
  `onlyIf(...)` is used only when that option is chosen. The steps are read again in INIT whenever a menu
  line changes, so the "Place robot" note, the simulator's robot and the odometry's start follow the menu.
- `tools/check_docs.py` also checks a cookbook recipe shown in a guide (the same `<!-- recipe: name -->` marker).
- **Interlock rules** (`robot.Interlock`, `robot.Zone`): `keepApart("TurretLift", turret.zone(),
  lift.zoneAbove())` in Robot.java keeps two parts from ever being in their zones at the same time (a
  turret over the back while a lift is up; an arm and an intake; a wrist and a low lift). Any number of
  rules between any parts with a position (Turret, Lift, Arm, Claw, Wrist, Positioner, Hood), or
  `Zone.when(words, condition)` for your own sensor. Zones in `Tunables.<rule>` (`<part>From/To`,
  `<part>Above`, `<part>Below`, optional `<part>Margin`), read once at INIT (not live, on purpose) and
  checked there (missing numbers, empty zones, zones outside or covering a part's range). Enforced every
  loop for every command (buttons, sticks, actions, autos, a following turret, shooting on the move):
  whoever is in its zone first keeps it, the other waits two margins outside its own (target kept); a
  turret takes the other way round if its range allows; both in: each may only move out. Measured
  position for motors, a time estimate for servos, an unhomed turret counts as anywhere. A held turret
  is never `isAimed()`. One Driver Station line per rule while it holds something. Guide:
  docs/guides/interlocks.md; four cookbook recipes.
- **Every kind of turret in the five files:** `Turret.servo(name)` (a position servo, `posA/angleA/posB/angleB`),
  `Turret.crServo(name).encoder(port, countsPerRev)` (a REV Through Bore on a motor port) or
  `.analogEncoder(port)` (an absolute encoder, `encoderOffset`), `continuous()` (a slip ring: no limits,
  the shortest way), `homeSwitch(name)` (a touch sensor or a magnetic switch as a Digital Device: homes at
  START, slowly, with a timeout and a Driver Station line, and sets its angle again each time it passes
  the switch; `homeAngle`, `homeDirection`, `homingPower`, `homingTimeout`), `startAngle` (no switch),
  `isHomed()`, `isHeld()`. TeleOp starts from the angle the last program ended at (`keepAngle`, 5 minutes,
  also a TeleOp started again); with nothing carried, TeleOp's INIT says to turn the turret to
  `startAngle` and INIT again. Ranges +/-90, +/-180, +/-190 tested. The low-level `launcher.Turret` gained
  power/encoder suppliers, a servo mode, continuous mode and a fence. New page: docs/guides/turret-setup.md
  (questions, the lines for each answer, four worked examples, how to find each number).
- **The V2 example robot** (`examples/.../v2bot`): a turret shooter (team 8938's turret v8.1GB: a
  magnetic home switch, +/-190 degrees, a servo hood), odometry, a piece count, and a Viper-Slide lift with
  a wrist and a claw, the lifter's steps as actions (grab, score in a FLOWER, stow) and the TurretLift
  interlock. Simulator tests.
- `Lift`, `Wrist` and `Claw` take `looks(...)`: a lift's `Shape.slide()...travelMm(mm)` (its counts per inch
  from `min`..`max`), a wrist's arm `on(lift)`, `Shape.claw()` `on(wrist)`.
- Simulator robot file: `touch.NAME = MOTOR, at=C, width=W` (a switch part way along, e.g. a turret's
  home switch), `analog.NAME = MOTOR, offsetDeg=D` (an absolute encoder), `motor.NAME = ..., drivenBy=CRSERVO`
  (an encoder turned by a CR servo).

- **Shooting on the move (optional, off by default until tuned on a real robot):** `Tunables.Aim.shootOnMove = true`
  with `flightSec` (+ optional `flightSecPerIn`, `maxLeadIn` 24) aims at a virtual goal (the goal minus
  the shooter's speed x the flight time) and brings an old camera picture forward to now (the robot's
  turn and move since; its age from the camera's timestamp, or `cameraLatencyMs`). The turret, whole-robot
  aim, `isAimed()` and the launcher's distance all use it. Works with a turret (camera on it or on the
  body, or odometry only) and with a fixed shooter; `shooterForwardIn, shooterLeftIn` and
  `cameraForwardIn, cameraLeftIn` say where they are from the robot's centre. `Aim.movingShotMode()`
  (a button can switch it), `isMovingShot()`, `lead()`; the Driver Station says
  `Aim: moving shot: lead 12.3 deg, 18 in`. The math: `vision.MovingShot`. Lesson 9 section 14, a
  cookbook recipe.
- `PoseTracker.velocityX()`, `velocityY()`: which way the robot moves over the field (in/s);
  `useClock(...)` for tests.
- **A launcher hood** (`robot.Hood`): `Launcher.flywheel(...).hood(Hood.servo("hood"))` (or
  `Hood.motor("hood", motorId)`, a motor with an encoder). The shot tables get a third number,
  `{inches, rpm, hood}`, blended between rows like the speed, per table (two-number rows still work: the
  hood is then set by hand). The launcher waits for the hood before it feeds (a servo hood: a time
  estimate from `travelSec`; a motor hood: its encoder within `tolerance`). `Tunables.Hood`: `posA`, `posB`
  (the two hard stops, needed: every angle is kept between them), optional `angleA`, `angleB` (without
  them the hood works in servo positions or ticks, and the table holds positions), or a `calibration`
  table of {position, angle} rows for a linkage; `homeAngle` (at START), `stepAngle`, `byHandPerSec`,
  `trimMax`; a motor: `kP`, `maxPower`, `tolerance`. Suggested operator stick: left stick Y, a hood trim
  while the table sets the angle (double-tap D-pad down clears it with the speed trim), else the angle by
  hand. `setAngle`, `home`, `step`, `byHand`, `trimBy`, `setTrim`, `resetTrim`, `angle`, `currentAngle`,
  `isArrived`, `followsTable`, `shotAngles(atA, atB)` (simulator). INIT stops on a row outside the hood's
  range or a table that mixes two- and three-number rows; a hood column with no hood is an INIT note.
- **Hood Finder** (`finders.HoodFinderOpMode`): the hood's two stops (servo nudge, or a motor moved by
  hand), the angles you measured (hold LB + D-pad), points for a calibration table; prints the
  `Tunables.Hood` lines. Examples: `turretbot/HoodFinder.java`, cookbook `HoodFinder.java`.
- `mechanism.AngleDevice`: anything that goes to an angle and says when it is there; `PositionServo`
  is one, and any one can be the plain launcher's hood. `launcher.Launcher.hoodOffset(a)`,
  `getHoodOffset()`, `hoodDeadband(a)`, `getTable()`; `ShotTable.hasHood()`.
- Shot Tuner: with a five-files hood, Y has HOOD (steps of 1/5 and 1/50 of its range), it starts from the
  table's hood at the distance, and the `Tunables.java` line has the hood column. `ShotPoints(hasHood, hoodDecimals)`.
- Simulator: `shoot.NAME = ..., hood=SERVO, hoodAt0=A, hoodAt1=B` (the shot angle follows the hood
  servo); `:sim:fromRobot` writes it from `Hood.shotAngles(...)`. `:sim:newRobot` puts a servo called
  "hood" on the launcher.
- The turret robot example has a hood (team 8938's turret: a Torque servo through 5.4:1, 0-25 deg),
  three-number shot tables, a Hood Finder, and lesson 9 a section on the hood; cookbook: "How do I add a
  hood to my launcher?" and "How do I find my hood's stops and angles?".

- Docs: a box in lesson 9, robot.md and launcher.md says battery compensation is on by default, how to
  turn it off (`batteryCompensation = false`), and a 4-shot test to check it on your robot.

- **Every library button can move or be freed.** `Controls.java` (and `EasyTeleOp`) get
  `libraryButtons(LibraryButtons lib, Pad g1, Pad g2)`: `lib.pause(...)`, `resetForward`, `debug(key, with)`,
  `slow` (a trigger or a button), `slowToggle`, `fieldCentric`, `buttonMap`, `tuningMenu`, `setPose`,
  `switchAlliance(key, with)`, `allianceInInit(blue, red)`, `swapSticks(true)`; `null` frees a button.
  The defaults are unchanged. Clashes stop INIT with the button's name. New classes
  `teleop.LibraryButtons` and `teleop.Key` (a `Pad.Button` is a `Key`); `Pad.guide` (gamepad 2's GUIDE
  is free for teams); `DriverControls.swapSticks(boolean)`; `EasyTeleOp.driveSpeed()`, `showNote(text)`,
  `switchAlliance()`, `allianceChanged(now)`, `setPoseJob()`, `libraryKeys()`, `libraryJobs(...)`,
  `keyWords(job)`, `standardLibraryButtons()`. With the library's slow freed, `slow()` works with two drivers.
- **Set pose here:** on a robot with odometry, park on the reset spot, let go of the drive sticks and
  hold gamepad 1 START 1 s, in INIT (before the match) or during it: the pose becomes
  `Tunables.Odometry.resetX, resetY, resetHeading` (written for BLUE; RED mirrored like the start spot,
  unless `Tunables.Odometry.mirrorForRed = false`). Two buzzes and `Pose set: ...`; while driving or with
  whole-robot aim on: `Pose NOT set: stop first`. START + A/B (pairing a gamepad) never sets it. INIT
  shows the button, the spot and the mirroring used (`Reset spot for RED: ...`). From code or an
  action: `robot.resetPose()`, `robot.resetPose(x, y, heading)`. `PoseTracker.unsetPose()`.
- **Switch the alliance during TeleOp (optional, off by default):** `lib.switchAlliance(g1.back, g1.start)`
  in `libraryButtons` turns it on; then holding START, then BACK, 1.5 s switches. Both gamepads
  buzz twice, the screen says `Alliance now RED (was BLUE)`, and the camera's goal, the aim's goal point,
  `robot.alliance()` and `MatchMemory` (`changeAlliance(a)`) follow. The odometry pose follows too: on a
  mirrored field it is mirrored in place (x and heading flip); on a turned field (`mirrorForRed = false`,
  BIOBUZZ) it counts as not set, so the odometry aim is off until "set pose here" (the note says so).
  INIT's `Switch alliance` line says whether it is on.
- **INIT says when a library job has no button:** `No pause button` (warning), `No slow button`
  (warning, `Controls.java`), `No field-centric button` (a line).
- **Launcher speed trim that works with shot tables.** `robot.launcher.trimPercent(+2)` makes every shot
  speed (the table's or `speedRpm`) 2% faster; `setTrim(percent)`, `resetTrim()`, `speedTrim()`. Kept to
  +/- `Tunables.Launcher.trimMaxPercent` (15), the same for every table, 0 at every INIT, shown as
  `Speed trim: +3%  READY` (the launcher's first line). With speed from the distance, the operator's
  D-pad up/down trims by `trimStepPercent` (1%), held: one more step every 0.25 s (after 0.5 s);
  double-tap D-pad down = no trim (with a fixed speed: back to `speedRpm`). Never past the motor's top
  speed (`(motor limit)`, `atMotorLimit()`). Last time's trim is remembered until the robot app restarts
  and offered at INIT (`Trim last time: +3% (double-tap D-pad up after START to use it)`,
  `lastTrimOffered()`, `useLastTrim()`), never applied by itself. The Shot Tuner never uses it.
  `launcher.Launcher.speedScale(s)` / `getSpeedScale()` / `getShotRpm()` underneath.
- **Flywheel battery compensation.** `Flywheel.batteryCompensation(volts, nominalVolts)` scales the hub's
  F gain by nominalVolts / the battery (read every 0.25 s, the highest reading of the last second
  (`BATTERY_FILTER_SEC`) so a dip under load does not raise F, sent only on a 2% change, kept to x0.8..x1.3),
  so a low battery gets back to speed after a shot as fast as a full one; warns under 11.5 V. On in the
  robot in five files when `kF` is set (`Tunables.Launcher.nominalVolts`, default 12.8;
  `batteryCompensation = false` turns it off). The Flywheel Finder now records the battery voltage
  (`nominalVolts = 12.6` in its line) and runs step 2 with the compensation. `Hw.batteryVolts(hardwareMap)`,
  `Hw.batteryReader(hardwareMap)` (never waits: the hubs are asked on the library's helper thread; the
  launcher and the Flywheel Finder use it), `Devices.batteryVolts()`, `Devices.batteryReader()`,
  `Flywheel.getBatteryVolts()`, `getFScale()`, `hasBatteryCompensation()`, `noBatteryCompensation()`.
- **Shot Tuner: tune the way teams do.** At each distance shoot 3-5 per speed and mark each shot
  A = HIT or B = MISS; the screen shows `Hits at 2550: 3/4` and every speed tried there, and X saves the
  speed with the best hit ratio. The distance is averaged over half a second of fresh readings; each
  shot keeps the flywheel speed measured as it was fed (a "sagged" shot is flagged, and the saved
  `ShotTable` lines carry `// 4/5 in, measured 2531 RPM`); the speed starts from the table you already
  have at that distance; `Points: POLLEN 3, NECTAR 0  (4-6 per table)` says when you are done. Each
  shot keeps the speed, hood angle and distance it was fired with; with a hood each speed and hood
  angle is its own try (the winner is saved with its hood). Left stick click undoes the last HIT / MISS,
  right stick click drops the last unmarked shot (a dip with no piece); unmarked shots from a spot the
  robot left are dropped. `ShotPoints.Tally` (`mark(rpm, hoodDeg, ...)`, `unmark`, `best()`, `tries()`),
  `ShotPoints.Trials`, `ShotPoints.DistanceAverage`, `ShotPoints.save(..., measuredRpm, hits, shots)`,
  `saved()`, `size()`, `ShotTunerOpMode.sagLimitRpm(rpm)`.
- Aim safety checks: `Aim.odometryProblem()`, `Aim.disagreement()`, `Tunables.Aim.maxGoalDistance` (170)
  and `cameraOdometryAgree` (6); INIT shows the alliance and the `mirrorForRed` choice.
  `PoseTracker.isPoseSet()` and `setHeading(deg)`. `Tunables.Turret.outOfRangeHold` (10).
  `launcher.Turret.getTolerance()`, `degreesOutOfRange(deg)`. `Launcher.table(name)`, `speedRpm()`.
- `Launcher.tableFor(table, kinds...)`: game-piece kinds that share a shot table, so
  `.tableFrom(pieces.nextKind()).tableFor("nectar", "RED_NECTAR", "BLUE_NECTAR")` lets the next piece pick
  its table; "NONE" and "UNKNOWN" keep the table in use.

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
- **Lights:** a colored light the drivers can see, in the five files. `lights = add(Lights.gobilda("light"))`
  (goBILDA RGB Indicator Light 3118-0808-0002, on a servo port) or `Lights.blinkin("blinkin")` (REV Blinkin
  LED Driver REV-11-1105 and its LED strip). Rules in `Controls.java`, the first true one wins:
  `robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady)`, `.blinking()`, `.whenStarts(...)`
  (a 0.5 s flash each time something starts), `.inLastSeconds(20)`, `otherwise(color)`, and `flash(color)`
  from a button or an action. Eight colors (`LightColor`), mapped per light from the sellers' tables.
  Blinking stops at 5 times a second (FTC R202 J). Never waits. The simulator shows the light's color
  (`light.NAME = gobilda` / `blinkin` in the robot file; `:sim:fromRobot`, `:sim:fromConfig` and
  `:sim:newRobot` write it). Cookbook recipes, a Lights section in docs/modules/robot.md, and the turret
  example robot uses one.
- `Lights`: `show(color).whenCountGoesUp(count)` flashes each time a number goes up:
  `() -> robot.launcher.part().getShotsFired()` gives one flash per piece really fired (`fire(3)`: three).
  The cookbook, lesson 9 and the turret example use it instead of `whenStarts(robot.launcher::isShooting)`.

- **The alliance on the light at INIT** (on by default on every robot with `Lights`): before START the
  light is solid RED or BLUE for the alliance picked, and blinks white once a second until one is picked;
  a 1 s flash of the alliance at START (TeleOp after autonomous: the one it got) and when the alliance is
  switched in TeleOp. Then the rules. Off: `allianceAtInit = false` in the light's Tunables section, or
  `lights.allianceAtInit(false)`; other colors: `allianceColors(blue, red)`; a Blinkin pattern of your own
  for a color: `blinkinPattern(color, pattern)`. The game manual has no rule against an alliance-colored
  light (R202 F/J, R401, R402 checked): the old "not with a light" advice is gone. Cookbook recipe,
  robot.md "The alliance at INIT", lesson 9 section 13.
- Simulator: a camera can ride a turret, arm or slide: `on=turret` on its `camera.`/`limelight.` line
  (measured with the turret at 0). `:sim:fromRobot` writes it when a turret `follows(camera)` (camera on
  the turret), and puts the default flywheel shape on the turret too, so the shots go where it points.
- Simulator: TeleOp stops at the end of its 2:00, like the real Driver Station with its TeleOp timer on (a
  "2:00 TeleOp timer" box turns it off; the clock counts down; scenarios: `set teletimer off`).
- TeleOp: holding "set pose here" on a robot without odometry shows a short note ("needs odometry")
  instead of doing nothing.
- The Launcher line says what it waits for: `Launcher : WAITING_READY  left 1  waits for: turret aimed`
  (it was only on the debug lines).

### Changed

- **INIT now stops on a Tunables field that no part reads, in every part's section** (before: only a
  Lift, Arm, Claw, Wrist or Positioner name that looked like a setting). A typo like `inPowr` in
  `Tunables.Intake`, `goalx` in `Tunables.Aim` or `nominalVolt` in `Tunables.Launcher` used to be quietly
  ignored (the part used its default). Now: `Tunables.Intake.inPowr is not a setting of Intake (did you
  mean inPower?): the robot would quietly ignore it. Fix the name (capitals count) or delete the line.
  Known: inPower, outPower`. It checks Drive, Odometry, Aim, Buttons, every part and every interlock
  rule; named positions, piece kinds, `speedByDistance...` tables and a rule's `<part>From`... still pass.
  **If your robot stops at INIT after updating:** fix the field it names, or delete it (a number for your
  own code belongs in your own section, like `Tunables.Auto`, which is never checked). The lists live in
  `robot/TunableFields.java`; `tools/check_tunables.py` checks them against the code.
- **Servo degrees in the parts catalog and the simulator** now match what the hub really sends: positions
  0 to 1 turn a catalog servo over the SDK's 600-2400 us, so a goBILDA 2000 servo turns 270 degrees in
  the simulator (was 300), a REV Smart Robot Servo 243 (was 270), an Axon MK2 324 (was 360), unless the
  code uses `.fullRange()`. Simulated servo speeds in positions per second change to match, and so does
  an arm shape's default angle at position 1. On the real robot nothing changes. Measured numbers
  (positions, `posA`/`posB`, `angleA`/`angleB`, calibration tables, a shape's two `angle(...)`s) are
  not affected. A team parts file without `pulseMinUs`/`pulseMaxUs` keeps its `rangeDeg` as before.
- **v2bot example: its interlock is now turret and wrist** (`keepApart("TurretClaw", turret.zone(),
  wrist.zone())`, `Tunables.TurretClaw`: turret 147 to 217 degrees with a 12 degree margin, wrist 0 to 47 degrees as servo
  positions 0 to 0.274). Team 8938's pincer model (2026-10-22) found the claw within 2 mm of the
  v8.1GB turret only while the wrist is between 3 and 44.5 degrees, at any lift height, so the lift needs
  no zone. The wrist's positions now use 270 degrees over 0 to 1 (the hub's normal range; the wrist needs
  only 145). Its actions: Grab turns the turret forward first (the wrist goes to pick-up inside its zone)
  and follows again after; Score no longer turns the turret. The interlocks guide's turret and lift
  example stays as a general example.

- An autonomous's `steps(...)` is read in INIT again each time the drivers change a menu line (it was
  read once in INIT and once after START). No step runs in INIT, as before. Before, a `start(...)`
  inside an `onlyIf` was used whatever the choice (the last one written won).
- docs/contributing.md: each release also gets a GitHub release (so "Watch > Releases" sends an email).
- Dead-wheel odometry now gives `speedInPerSec()` and `turnDegPerSec()` (worked out from how far the
  robot moved between loops, smoothed); they were always 0.
- `launcher.Launcher.setDistance` moves the hood only when the table has hood angles (a table made with
  `add(distance, rpm)` used to send the hood to 0), and only for a change over `hoodDeadband` (0.25), so
  a jittery camera distance no longer keeps the hood "moving". `hood(...)` takes any `AngleDevice` and
  `getHood()` returns one (was `PositionServo`).
- `ShotPoints.Tally.summary()` and the Shot Tuner's marks show the hood as
  "2550 @ hood 10" (any scale, not only degrees); tries are told apart to 0.001 (was 0.1).
- `Launcher.resetTrim()` (double-tap D-pad down) also clears the hood trim.
- **Pause acts at the press of BACK** (it was at the release, because BACK also has "hold: reset
  forward"): a panicking driver holding BACK gets the stop at once. Held 1 s it also resets "forward"
  (field-centric) and stays paused; a tap goes on (going on is always at the release, so holding BACK
  never starts the robot). `EasyTeleOp.pauseState()` (Controls.java gives it) turns this on.
- **START + BACK: press START first.** A pair with the pause button in it (the debug lines; the alliance
  switch when it is on) now forms only when the other button (START) went down first, or in the same
  loop. BACK pressed first is always a plain pause (held 1 s it still resets "forward"); START joining
  later does nothing that press (no debug lines, no switch, no "set pose"). Before, BACK first paused
  the robot and START joining took the pause back, so the robot stopped for the gap (often 0.1-0.3 s)
  and drove on. The button map says `START: then BACK: debug lines` and `BACK: after START: ...`;
  INIT says `hold START, then BACK, 1.5 s to switch`. Other pairs work in either order, as before.
- A pair's quick job (BACK + START: debug lines) always runs when the buttons are let go.
- The examples' `Tunables.Odometry` say `mirrorForRed = false` (BIOBUZZ's turned field); the whole-robot
  aim recipe holds to aim (`g1.rb.hold(robot.aimMode()::on, robot.aimMode()::off)`), with the toggle second.

- With the alliance switch on START + BACK, the debug lines toggle when the pair is let go (not at the
  press). GUIDE is on the button map (`G1 GUIDE: button map; hold 1 s: tuning menu [library]`), and the
  BACK and START lines list every library job on them.
- `Aim odometry: the pose was never set` now says how to set it with the button.
- The launcher's D-pad down (fixed speed) is now a tap (speed down) and a 1 s hold (back to `speedRpm`), so
  the tap runs when the button is let go.
- `PedroFollower.remainingIn()` is now the distance left along the WHOLE path (all its parts), measured
  from the robot's closest point on the path. It was Pedro's distance for the part it steers along now,
  which Pedro may switch well before the join. The Driver Station's `Path: 18 in to go` uses it too.
- `Lights` never changes color twice within 0.1 s (`Lights.MIN_CHANGE_SEC`), across rules, flashes and
  blinking, so a rule that flips every loop or flash after flash stays at 5 on-offs a second at most
  (FTC R202 J). `blinking(n)` takes any number above 0, at most 5 (its message said 0.5 to 5).
- Docs: `PathAt` examples use parts the example robots have (`"Spin up", robot.launcher::spinUp`), not
  `Actions.spinUp(robot)`, which no example defines; `fraction` is of the WHOLE path; lesson 8 shows a
  time limit with `PathAt`. Lights docs: the `LightColor` import, a whole-robot aim rule, and R402 as advice
  (the alliance colour goes on the ROBOT SIGNS).
- Game pieces: a feed counts its piece out once it has gone (the ready sensor sees it leave, or the
  feed ends without a jam), not when it starts; `next()` names the piece after the one being fed.
  `Indexer.isFeeding()`. While the intake pushes out, pieces leaving past the intake sensor count out
  and nothing counts in. Two touching pieces of the same kind count as two when the distance dips
  between them (`gapMm`, default 8). Pieces suggests a button: hold G2's left stick button 1 s = count
  0. `Intake.inAnyway()`, `isRunningOut()`, `heldBackSec()`; with `stopsWhen`, holding the in-button
  (RT) 1 s runs in anyway, and the Driver Station warns after 2 s held back. `PieceSensor.isWorking()`,
  `arrivals()`, `readings()`. Docs: the drivers, not the count, are responsible for G407.
- Shot Tuner buttons: A = HIT, B = MISS, X = save (the best speed here), hold BACK 1 s = delete the point
  here (was A = save, X = delete, B = stop shooting: let go of RT, or LB, to stop). `startRpm()` now
  defaults to automatic (the table's speed at the distance, else `speedRpm`, else 3000).
- `robot.aimMode()` on a robot whose turret follows the goal stops at INIT: turret-following and
  whole-robot aim are separate modes (both chased the same error and swung back and forth).
- TeleOp's "hold BACK: forward = away from you now" changes only the odometry's heading
  (`setHeading`), so it does not count as setting the pose.

- The example and cookbook lights keep away from red and blue (purple = following the goal / intake on,
  orange = following off / pushing out), so no rule looks like the alliance. The turret robot's
  "following" was BLUE, which looked like the BLUE alliance on a RED robot.
- A Tunables number that is not written: the INIT note now says `not written: library defaults used
  (fine)` instead of `missing`. The turret, cookbook, goBILDA and flywheel examples' Tunables now have the
  optional `slowZone`, `slowZonePower`, `outOfRangeHold` (Turret) and `shotIntervalSec` (Launcher) lines.
- `:sim:fromRobot` writes a camera tilted 30 degrees up (was 20): BIOBUZZ's tags are up to 50 in high on
  a raised CELL, and BLUE's goal starts there.
- "next shot table (...)" in the help and the Shot Tuner's table list are in capitals, like the Driver
  Station's `Shot table: POLLEN`.
- Simulator: the pose line says it is in the field frame (seen from the red wall), and the start-spot line
  says it is the alliance's own frame (as `s.start` takes it).

### Fixed

- Docs: `docs/modules/robot.md` said only a Lift or Arm has numbers INIT requires; also a Hood's
  `posA`/`posB`, a servo Turret's `posA`/`angleA`/`posB`/`angleB`, a Turret's `homeAngle` with a home
  switch, and an interlock rule's zones. Its tuning-menu table missed Aim, Buttons, Hood, PieceSensor,
  Lights and most Turret fields: replaced by the reference's Live column. The Camera section said its
  numbers could be changed in the tuning menu (they cannot); Camera and PieceSensor said an untouched
  `Tunables.java` value wins at the next INIT (a value saved on the hub wins: copy it back because the
  file is the record). `Tunables.Aim.cameraMaxAgeMs` (150 ms) was not documented.
- Simulator: a camera on a turret stayed fixed to the chassis, so a turret that follows the goal turned
  to its limit, or stopped 55 degrees off while saying AIMED, and the shots missed.
- Simulator: moving the robot by hand (the alliance's start spot at INIT, a drag on the map, `set pose`)
  changed the IMU's reading: TeleOp zeroes it at INIT, so a robot put on its spot read minus its old
  heading. Like the odometry, the IMU does not see a hand move now.
- `Launcher gave up: ...` and the turret's `target out of range` warnings stayed on the Driver Station for
  good: the launcher's clears after 5 s (a jam stays while jammed), the turret's 1 s after nothing asks
  for a target past its limits (following off, or the stick took over).
- Debug lines showed `NaN` before anything was sent (`Turret target : NaN deg`, `ClawFold pos : NaN`, the
  hood's position): they show `-`.
- Simulator: loops ran 100 000 times a second (`Loop : 0.0 ms`); each telemetry update now takes at least
  2 ms, about a real hub's loop.
- Simulator: a servo pusher (indexer) held out longer than 0.3 s sent a second piece; it sends one per push.
- Simulator page: a fast double-click on an on-screen gamepad button (under 0.12 s) was one long press;
  it is two presses now (a double-tap).
- The camera lighting test's INIT tip "add a line that is not there" now reads "set these (add any that
  are not there)": the lines are often already in Tunables.
- Simulator: the pretend Pinpoint and OTOS gave their speed turned with the robot; like the real ones
  they now give it over the field.
- After an alliance switch in TeleOp, the odometry pose stayed in the old alliance's numbers, so the
  odometry aim pointed at a mirrored spot and field-centric "forward" was wrong (see Added).
- With two drivers, `lib.slow(null)` then `g2.rt.slow()` read gamepad 1's RT: it reads gamepad 2's now.

- `PedroFollower`: on a path that comes back along the same line (out 24 in, then back), the progress
  followed the outbound leg back to 0, so `PathAt` things on the return leg fired late or never. Where
  two places on the path are equally close it now picks the one ahead of the last progress, and never
  falls back more than 3 in.
- `AutoRoutine.followPath`: the path's length was first read on the second loop, so a path done in its
  first loop had length 0 at the end and `PathAt.inchesDone(1)` never fired. It is read after `start()`
  and again before the end check.
- `PieceSensor.distance`: the REV 2m Distance Sensor (up to 0.2 s per reading) is read on a helper
  thread, so the loop never waits; a reading older than 0.25 s is thrown away.
- A colour or distance sensor missing from the robot configuration no longer stops INIT: it warns and
  sees nothing (like a missing camera).
- A piece camera that stops streaming, or sends no new picture, no longer keeps its last answer: after
  0.5 s with no new reading any piece sensor sees nothing and warns; Pieces does not count with it.
- A jammed or stopped feed no longer counts a piece out (the count said 3 with 4 inside: a 5th piece,
  rule G407).
- A piece backed out through the intake and taken in again was counted twice, and one pushed out
  never counted out (the intake could stay held back by `stopsWhen` for the whole match).
- The ready sensor was not trusted again when the next piece arrived sooner than `steadyMs` after the
  last one left; now a new steady reading is enough.
- A feed that ended (and counted its piece out) while the ready sensor's answer still showed that piece
  put it back in the count: after a counted feed the sensor is trusted again only when it reads empty or
  a new steady reading begins (Codex review). A jammed feed trusts it at once.
- The launcher's battery compensation asked the hubs for the voltage in the loop (a few ms every
  0.25 s): it now uses `Hw.batteryReader` (Codex review).
- Cookbook: the Shot Tuner and Piece Tuner recipes are whole files with their imports (the Piece Tuner
  is its own file, `cookbook/PieceTuner.java`, no `static`); "stop the intake when full" says to put
  the Pieces line above the intake line; the light recipe names the `Lights` / `LightColor` imports.
- Lesson 9 and robot.md: a worked example to fold a trim into the table, one battery example
  (12.6 V tuned, 11.5 V: F x 1.10), the 12.8 V default, the Shot Tuner on gamepad 1 (D-pad left/right
  picks the table, hold BACK 1 s deletes a point), "hit ratio", "sagged" and "blended" explained.
- Piece Tuner: measures only new readings (20 readings, about half a second, in both finders pages).
- `Aim.isAimed()` and `Turret.isAimed()` for a following turret: true only while the goal is seen NOW
  (a fresh picture or the odometry), the turret is homed and in range and the goal error is within the
  tolerance. Before, a turret that had reached its last target stayed "aimed" after the goal was lost,
  so a launcher gated on it could fire while the chassis turned away (Codex review, P1).
- A turret following the odometry with the goal behind the robot no longer swings from one limit to
  the other as the heading wobbles: more than `outOfRangeHold` (10) degrees past a limit it holds where
  it points and says "goal out of turret range (behind the robot: holding)"; it follows again within 5.
- A wrong or unset pose no longer aims the robot: the odometry backup is off until the pose was set
  (autonomous, or `setPose`), with a Driver Station warning; also off while it puts the goal farther
  than the field allows (170 in); and a warning when the camera's and the odometry's distances differ
  by more than 6 in.
- Lesson 9: one `Tunables.Aim` block (sections 9-11) and the `mirrorForRed` default explained (true;
  BIOBUZZ needs false); links to the Shot Tuner file; one spelling for the `> Table` menu.

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
