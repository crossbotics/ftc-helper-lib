# ftc-helper-lib

Shortcuts for programming an FTC robot in Java, made by Indianola Robotics (FTC team 8938).

It takes the jobs every robot needs (setting up motors, reading buttons, driving a mecanum
chassis or a standard (tank) chassis, PID control, telemetry, keeping a lift inside its safe range) and makes each one a
line or two of clear code. It started from our 2025-26 (DECODE) robot code, cleaned up and tested.

```java
DcMotorEx lift = MotorSetup.get(hardwareMap, "lift").brake().resetEncoder().runWithoutEncoder().done();
MecanumDrive drive = new MecanumDrive(hardwareMap, DriveConfig.mecanum());   // or TankDrive + DriveConfig.tank()

ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G2, Btn.A, () -> claw.setPosition(OPEN))
        .bindToggle(Pad.G1, Btn.Y, () -> slow = true, () -> slow = false);

while (opModeIsActive()) {
    controls.update(gamepad1, gamepad2);
    drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
}
```

## Start here

| I want to... | Read |
| --- | --- |
| Add the library to our robot project | [Add it to a project](docs/01-add-to-project.md) |
| Get the newest version, or go back to an older one | [Keep it up to date](docs/02-keep-up-to-date.md) |
| **Learn it step by step (13 hands-on tutorials)** | [Tutorials](docs/tutorials/README.md) |
| Write a TeleOp | [Tutorial 04: mecanum](docs/tutorials/04-drive-mecanum.md) or [05: tank](docs/tutorials/05-drive-tank.md) |
| Write an autonomous | [Tutorial 09: first autonomous](docs/tutorials/09-first-auto.md) |
| Know which way is "positive" | [Signs and units](docs/conventions.md) |
| Fix a problem | [Troubleshooting](docs/troubleshooting.md) |
| Find last season's class | [Coming from last season's code](docs/from-last-season.md) |
| Add or change something in the library | [Contributing](docs/contributing.md) |

## What is inside

Every class and every function is explained in the module pages.

| Module | Package | What it does | Docs |
| --- | --- | --- | --- |
| Hardware | `io.github.jasoncross.ftchelper.hardware` | Set up motors, servos and the IMU in one line. Clear errors for wrong names. Faster loops with bulk reads. | [hardware.md](docs/modules/hardware.md) |
| Gamepad | `io.github.jasoncross.ftchelper.gamepad` | Press, hold, toggle and trigger actions. Stick deadband and curves. Slow-mode trigger. Rumble feedback. | [gamepad.md](docs/modules/gamepad.md) |
| Drive | `io.github.jasoncross.ftchelper.drive` | Mecanum drive (robot-centric and field-centric) and standard / tank drive (arcade and tank style, 2 or 4 motors), reverse mode, autonomous moves and turns with encoders and the IMU for both. | [drive.md](docs/modules/drive.md) |
| Control | `io.github.jasoncross.ftchelper.control` | PID controller and slew-rate limiter (ramp). | [control.md](docs/modules/control.md) |
| Telemetry | `io.github.jasoncross.ftchelper.telemetry` | Neat, same-every-time Driver Station lines. | [telemetry.md](docs/modules/telemetry.md) |
| Safety | `io.github.jasoncross.ftchelper.safety` | Soft limits, limit switches, homing and slow zones for lifts, slides and arms. | [safety.md](docs/modules/safety.md) |
| Util | `io.github.jasoncross.ftchelper.util` | Math and angle helpers, loop timer, Auto-to-TeleOp pose handoff. | [util.md](docs/modules/util.md) |

Example OpModes you can copy are in [`examples/`](examples/src/main/java/io/github/jasoncross/ftchelper/examples), and every tutorial's complete program is in [`examples/.../tutorials/`](examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials).

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
```

You need JDK 17 or newer and the Android SDK (Android Studio installs both). Details: [Contributing](docs/contributing.md).

## Rules check (2026-27 BIOBUZZ manual, TU01)

- **R304:** custom software can be reused year to year, and robot software made before Kickoff
  is allowed. So using this library is fine.
- **R105 B:** the robot must stay inside its size limits **without software**. The soft limits in
  `SafeMotor` help, but every moving part also needs a physical hard stop. Inspectors check the
  maximum mechanical extension.

Read the newest manual and Team Updates each season, and update this section.
