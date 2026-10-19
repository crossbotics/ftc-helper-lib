# ftc-helper-lib

Shortcuts for programming an FTC robot in Java, made by Indianola Robotics (FTC team 8938).

It takes the jobs every robot needs and makes each one a line or two of clear code: motors and
servos, buttons, mecanum and standard (tank) drive, lifts and arms that cannot hurt themselves,
intakes with jam back-out, a full shooter kit (flywheel, hood, turret, feeder), AprilTag aiming with a
webcam or Limelight 3A, autonomous modes written as a list of steps, live tuning, practice and
competition profiles, and a hardware check. It started from our 2025-26 (DECODE) robot code, and is
built to be extended every season.

```java
PositionMotor lift = PositionMotor.create(hardwareMap, "lift")
        .limits(0, 2600).pid(0.004, 0, 0.0002, 0.05)
        .position("DOWN", 0).position("HIGH", 2400);
SpinMotor intake = SpinMotor.create(hardwareMap, "intake").powers(1.0, -0.7);

ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"))
        .bindToggle(Pad.G2, Btn.A, intake::in, intake::stop);

// An autonomous is a list of steps:
routine.step("Lift up", lift.startStep("HIGH"))
       .move("To the goal", 24, 0, 0.6)
       .turn("Face it", 45, 0.5)            // mirrored for RED automatically
       .step("Score", claw.goToStep("OPEN"))
       .thenPark()
       .move("Park", 20, 90, 0.8);
```

## Start here

| I want to... | Read |
| --- | --- |
| Add the library to our robot project | [Add it to a project](docs/01-add-to-project.md) |
| **A whole robot program, fast** | [Starter kit](docs/starter-kit.md) |
| **Learn it step by step (19 hands-on tutorials)** | [Tutorials](docs/tutorials/README.md) |
| See how it all fits together | [Architecture](docs/guides/architecture.md) |
| Make autonomous modes the easy way | [Easy autonomous modes](docs/guides/easy-autos.md) |
| A whole robot program in five short files (and make them from your configuration) | [Tutorial 22](docs/tutorials/22-robot-in-five-files.md), [robot module](docs/modules/robot.md) |
| Drive + slow mode + intake, ready to copy | [Example: drive and intake](docs/guides/example-drive-intake.md) |
| Keep the loop fast: what blocks and what never does | [Non-blocking code](docs/guides/non-blocking.md) |
| Practice vs competition behavior | [Profiles](docs/guides/profiles.md) |
| Change numbers while the robot runs | [Tuning](docs/guides/tuning.md) |
| Test the robot (checklists) | [Testing procedures](docs/guides/testing.md) |
| Try OpModes without a robot (pretend Control Hub + Driver Hub) | [Simulator](docs/guides/simulator.md) |
| Motors and servos the simulator knows (and how to add your own) | [Parts catalog](docs/guides/parts-catalog.md) |
| Your real robot in the simulator, with an AI agent's help | [Robot-file skill](docs/guides/robot-file-skill.md) |
| Four real BIOBUZZ starter bots to try in the simulator | [Starter bots](docs/guides/starter-bots.md) |
| From your robot's CAD to the simulator (parts, which motor drives what, shot tables) | [CAD tools](tools/cad/README.md), [Launcher program](tools/launcher/README.md) |
| Get ready for a new season | [New season](docs/guides/new-season.md) |
| Add something new to the library | [Extending](docs/guides/extending.md), [Contributing](docs/contributing.md) |
| Get the newest version, or an older one | [Keep it up to date](docs/02-keep-up-to-date.md) |
| Know which way is "positive" | [Signs and units](docs/conventions.md) |
| Fix a problem | [Troubleshooting](docs/troubleshooting.md) |
| Find last season's class | [Coming from last season's code](docs/from-last-season.md) |

## What is inside

Every class and every function is explained in the module pages.

| Module | Package | What it does | Docs |
| --- | --- | --- | --- |
| Hardware | `io.github.jasoncross.ftchelper.hardware` | Set up motors, servos and the IMU in one line. Clear errors for wrong names. Faster loops with bulk reads. | [hardware.md](docs/modules/hardware.md) |
| Gamepad | `io.github.jasoncross.ftchelper.gamepad` | Press, hold, release, toggle and trigger actions. Stick shaping. Slow-mode trigger. Rumble feedback. | [gamepad.md](docs/modules/gamepad.md) |
| Drive | `io.github.jasoncross.ftchelper.drive` | Mecanum (robot- and field-centric) and standard/tank drive, standard driver controls, "forward" carried over from autonomous, reverse mode, autonomous moves and turns for both. | [drive.md](docs/modules/drive.md) |
| Mechanism | `io.github.jasoncross.ftchelper.mechanism` | Lifts/arms to named positions, intakes with jam back-out, servos with positions or degrees, continuous servos. | [mechanism.md](docs/modules/mechanism.md) |
| Launcher | `io.github.jasoncross.ftchelper.launcher` | Flywheel in RPM, shot table, feeders, turret with safe limits, and a Launcher that only feeds when ready. | [launcher.md](docs/modules/launcher.md) |
| Vision | `io.github.jasoncross.ftchelper.vision` | AprilTags with a webcam (SDK 12 clusters too) or a Limelight 3A, one interface, safe aiming. | [vision.md](docs/modules/vision.md) |
| Robot | `io.github.jasoncross.ftchelper.robot` | The robot in five files: Robot.java (what is on it), Tunables.java (its numbers), Controls.java (the buttons and the TeleOp), Actions.java (moves that use several parts) and autos. Ready-made modules: intake, lift, arm, claw, wrist, turret, launcher, indexer, camera. | [robot.md](docs/modules/robot.md) |
| TeleOp | `io.github.jasoncross.ftchelper.teleop` | EasyTeleOp: a whole TeleOp where you add only your mechanisms and buttons; driving, timer, tuning menu and safety come with it. | [teleop.md](docs/modules/teleop.md) |
| Auto | `io.github.jasoncross.ftchelper.auto` | Autonomous as a list of named steps; EasyAuto does menu, timer, mirroring, park time, report. | [auto.md](docs/modules/auto.md) |
| Match | `io.github.jasoncross.ftchelper.match` | Alliance and mirroring, match timer with buzz cues, INIT menu, auto-to-TeleOp memory. | [match.md](docs/modules/match.md) |
| Control | `io.github.jasoncross.ftchelper.control` | PID controller and slew-rate limiter (ramp). | [control.md](docs/modules/control.md) |
| Telemetry | `io.github.jasoncross.ftchelper.telemetry` | Driver and debug levels, warnings on top, rate limit; one-line helpers. | [telemetry.md](docs/modules/telemetry.md) |
| Tuning | `io.github.jasoncross.ftchelper.tuning` | Change numbers live from the gamepad; saved on the hub. | [tuning.md](docs/modules/tuning.md) |
| Safety | `io.github.jasoncross.ftchelper.safety` | Soft limits, limit switches, homing, slow zones, jam detection. | [safety.md](docs/modules/safety.md) |
| Core | `io.github.jasoncross.ftchelper.core` | The Subsystem pattern, practice/competition profiles, settings file, RobotConfig (the robot described once). | [core.md](docs/modules/core.md) |
| Testing | `io.github.jasoncross.ftchelper.testing` | A ready-made Hardware Check OpMode. | [testing.md](docs/modules/testing.md) |
| Finders | `io.github.jasoncross.ftchelper.finders` | Ready-made OpModes that measure servo ends, lift limits, flywheel gains, wheel size, camera offset and the shot table, and show the code to copy. | [finders.md](docs/modules/finders.md) |
| Util | `io.github.jasoncross.ftchelper.util` | Math and angle helpers, loop timer, Auto-to-TeleOp pose handoff. | [util.md](docs/modules/util.md) |

Example OpModes you can copy (including the [starter kit](docs/starter-kit.md)) are in [`examples/`](examples/src/main/java/io/github/jasoncross/ftchelper/examples), and every tutorial's complete program is in [`examples/.../tutorials/`](examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials).

## Works with

- FTC SDK **12.0** (the 2026-27 BIOBUZZ release; built and tested). Also works with 11.2.1. See [A new FTC SDK version](docs/02-keep-up-to-date.md#a-new-ftc-sdk-version).
- Android Studio Narwhal 3 Feature Drop or newer (needed by SDK 12.0).
- Android Studio. (OnBot Java and Blocks cannot use a library like this.)
- Java 8 language level, like the SDK.

## Build and test the library by itself

You only need this if you change the library. From this folder:

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug :examples:testDebugUnitTest
python3 tools/check_docs.py
python3 tools/check_blocking.py
```

You need JDK 17 or newer and the Android SDK (Android Studio installs both). Details: [Contributing](docs/contributing.md).

## Rules check (2026-27 BIOBUZZ manual, TU01)

- **R304:** custom software can be reused year to year, and robot software made before Kickoff
  is allowed. So using this library is fine.
- **R105 B:** the robot must stay inside its size limits **without software**. The soft limits in
  `SafeMotor` help, but every moving part also needs a physical hard stop. Inspectors check the
  maximum mechanical extension.

Read the newest manual and Team Updates each season, and update this section.
