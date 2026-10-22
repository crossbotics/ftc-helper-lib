# Coming from last season's code

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

The library started from the 2025-26 (DECODE) robot code, branch `codex/biobuzz-base` of the
team's FtcRobotController repository. This page is for programmers who know that code. New to it?
Start with the [Quick start](quick-start.md) instead.

This table shows where each reusable part went and what changed.
Game-specific code (launcher, feed, intake, vision, routes) is NOT in the library.

| Last season | Now | What changed |
| --- | --- | --- |
| `input/ControllerBindings` | `gamepad.ControllerBindings` | Same `bindPress`/`bindHold`/`bindToggle`/`bindTriggerPress`/`bindTriggerAxis`. New: `bindRelease`, `bindTriggerHold`, `setTriggerThreshold`, `ignoreHeldButtons` (replaces the per-button "seeded" press), `isDown`, `GUIDE` and `TOUCHPAD` buttons. Fixed: trigger presses no longer borrow the bumper's slot. `registerExtraButtonReader` is now `registerExtraButton`. |
| `input/DriveInputScaling.slowScale` | `gamepad.StickShaping.slowScale(trigger, slowest)` | The slowest speed is a parameter instead of a static setting. New: `curve`, `shape`. |
| `input/ReverseDriveControl` | `drive.ReverseDrive` | No longer binds a button itself; you bind it (see [drive.md](modules/drive.md#reversedrive)). |
| `utils/RumbleNotifier` | `gamepad.AimRumble` | Same feel and defaults. `setMinMax` is now `setFeel`; `setActive` is `setEnabled`. |
| `utils/ToggleRumble` | `gamepad.ToggleRumble` | Pattern is set with `setPattern` instead of a static config class. `isConfirming` is now `isPlaying`. |
| `drive/MecanumMixer` | `drive.MecanumMixer` | Same `mix`. `fieldVector` is now `fieldDirection`. New: `fieldToRobot` for field-centric TeleOp. |
| `drive/MecanumDrive` | `drive.MecanumDrive` | Takes a `DriveConfig` object instead of the static `RobotConfig`. New: `driveFieldCentric`, `setPowers`, `setBrake`. |
| `drive/Drivebase` (Auto helpers) | `drive.AutoDrive` | Settings come from `AutoDriveConfig` (same default numbers as `DriveTuning`). `moveWithTwist` is now `moveWithTurn`. New: `turnTo`, a `Result` for each move. Changes below. |
| `config/DriveTuning` | `drive.AutoDriveConfig` | Fields renamed in plain words, for example `TURN_KP` is `turnKp`, `AUTO_MOVE_MIN_SPEED` is `moveMinPower`. |
| `odometry/FieldPose`, `odometry/PoseStore` | `util.FieldPose`, `util.PoseStore` | `setLastKnownPose`/`consumeLastKnownPose` are now `save`/`consume`; `peek`, `has` and `clear` remain. |
| `auto/BaseAuto` + `AutoSequence` | `auto.AutoRoutine`, `auto.EasyAuto` | Same idea (a chain of named steps, endgame/park reserve, remembered heading). Game steps (fire, intake rows) are gone: mechanisms give their own steps. Steps are mirrored for RED by the routine. |
| `subsystems/Launcher`, `control/FiringController`, `config/AutoRpmConfig` | `launcher.Flywheel`, `launcher.Launcher`, `launcher.ShotTable` | General versions: RPM with settle time and shot detection; readiness gating with named checks and a give-up reason; distance table with hood angles. Last season's live speed scale is the speed trim (`robot.launcher.trimBy`, the D-pad), and the battery is compensated automatically (`Flywheel.batteryCompensation`). |
| `subsystems/Feed` | `launcher.MotorFeeder`, `launcher.ServoFeeder` (`Feeder` interface) | One piece per call; the launcher decides when. |
| `subsystems/Intake` jam logic | `mechanism.SpinMotor`, `safety.JamDetector` | General jam detection and back-out; the DECODE packing states are not included. |
| `vision/*` | `vision.WebcamTags`, `vision.LimelightTags`, `vision.AimController` | Rewritten for SDK 12 (single tags and clusters). Bearing is + = left for both cameras. No field localization (BIOBUZZ tags move). |
| `Alliance`, `TeleOp_Blue`/`TeleOp_Red` | `match.Alliance`, `match.MatchMemory` | One TeleOp for both alliances: it reads the alliance autonomous chose. |
| TeleOp auto-stop timer | `match.MatchTimer` | Countdown with cues and gamepad buzzes. |
| `config/*` tunables + FTC Dashboard | `tuning.Tunable`, `tuning.TuningMenu`, `core.Profile` | Tune from the gamepad, saved on the hub; Dashboard still optional. |
| (new) | `drive.TankDrive` (standard drive), `safety.SafeMotor`, `control.PidController`, `control.SlewRateLimiter`, `telemetry.TelemetryHelper`, `hardware.*` | New helpers. |

## Behavior changes in AutoDrive (on purpose)

- **Power limit:** `DriveConfig.powerLimit` now applies to every autonomous move and turn.
- **Diagonal distance:** distance is measured from the real forward and sideways travel. Before, a 45 degree
  move stopped short (about 17 of 24 inches), and the strafe correction had no effect on pure strafes.
  The sideways power is also raised by `strafeCorrection`, so diagonals keep their direction.
  **Re-tune `strafeCorrection`** with the steps in drive.md.
- **moveWithTurn** only finishes its turn after a successful move, and inside the same time limit.
  Before, a stuck robot could start turning, and the time limit could nearly double.
- **Negative distance** now drives the opposite way. Before, `move(-10, 0, ...)` drove FORWARD 10 inches.
- **Stall check** looks at progress over a time window (0.25 in within 0.4 s by default). Before, it
  checked each loop, so a fast loop could wrongly decide a slow-moving robot was stuck.
- `AUTO_STALL_VELOCITY_EPSILON` is gone; `stallProgressIn` and `stallTimeSec` do the whole job.
- `turnMinPower` (new, default 0 = old behavior) can stop turns from stalling just short.
- Every move returns `DONE`, `STALLED`, `TIMED_OUT` or `STOPPED`.
- Unused leftovers (`setRunToPosition`, `restoreBaseRunMode`, the TeleOp `drive()` inside Drivebase) were removed.
  For TeleOp, use `MecanumDrive`.
