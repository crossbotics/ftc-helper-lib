# Changelog

What changed in each version. Newest first. How to update: [Keep it up to date](docs/02-keep-up-to-date.md).

## Unreleased

### Added (simulator branch)

- **Simulator** (`sim/`, standalone build only): a pretend Control Hub and Driver Hub on a laptop.
  `./gradlew :sim:run`, then http://localhost:8780. Real OpMode code with pretend motors, servos,
  switches, IMU, a robot on a field, simple physics; on-screen, keyboard, real-controller and phone
  gamepads. Scripted whole-OpMode tests (`FinderSafetyTest`) run on every push.
  Guide: [docs/guides/simulator.md](docs/guides/simulator.md).
- Simulator: a parts catalog of common FTC motors and servos (`sim/parts/parts.json`, numbers from the
  sellers' pages) that robot files reference; a robot project's own TeamCode and robot file; iterative
  OpModes; scenario files (`./gradlew :sim:scenario`) that also run as tests; season field files
  (`sim/fields/`) with solid elements, zones, tape and AprilTags, starting with BIOBUZZ.
- Simulator: the field in 3D on the page (three.js, served by the simulator so it works offline):
  the field from the field file or a team's 3D model (`field.model` in the robot file), the robot box,
  AprilTag squares, and Drivers / Audience / From above / Follow robot views.
- Simulator: pretend webcams (`camera.KEY = name=Webcam 1, forward=..., pitch=...` in the robot file).
  `WebcamTags` and the SDK's own `VisionPortal`/`AprilTagProcessor` builders work; the field file's
  AprilTags become the SDK's detections (BIOBUZZ: clusters per CELL group) with the SDK's ftcPose
  formulas. The page lists what each camera sees, draws its view cone and shows its picture in 3D.
  Scenario check: `expect camera "Webcam 1" sees 31|none|"RED SCORING"`.
- Simulator: a pretend Limelight 3A (`limelight.KEY = name=limelight, ...`): the SDK's Limelight3A with
  its network part replaced; results are built as the Limelight's JSON and read by the SDK's own
  LLResult (one FiducialResult per tag, tx/ty/ta, target pose in camera space). `LimelightTags` works.
- Simulator: a parts catalog page (`docs/guides/parts-catalog.md`, made from `parts.json` by
  `tools/parts_page.py`; the build checks it is current). Parts not in the catalog: `rpm=`/`ticksPerRev=`
  on a motor, `rangeDeg=`/`secPer60=` on a servo, or a team parts file (`parts = parts.json`).

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
