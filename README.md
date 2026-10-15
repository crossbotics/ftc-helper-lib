# ftc-helper-lib

Shortcuts for programming an FTC robot in Java, made by Indianola Robotics (FTC team 8938).

It takes the jobs every robot needs (setting up motors, reading buttons, driving a mecanum
chassis, PID control, telemetry, keeping a lift inside its safe range) and makes each one a
line or two of clear code. It started from our 2025-26 (DECODE) robot code, cleaned up and tested.

```java
DcMotorEx lift = MotorSetup.get(hardwareMap, "lift").brake().resetEncoder().runWithoutEncoder().done();
MecanumDrive drive = new MecanumDrive(hardwareMap, new DriveConfig());

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
| Write a TeleOp with it, step by step | [Your first TeleOp](docs/03-first-teleop.md) |
| Write an autonomous with it | [Your first autonomous](docs/04-first-auto.md) |
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
| Drive | `io.github.jasoncross.ftchelper.drive` | Mecanum drive (robot-centric and field-centric), reverse mode, autonomous moves and turns with encoders and the IMU. | [drive.md](docs/modules/drive.md) |
| Control | `io.github.jasoncross.ftchelper.control` | PID controller and slew-rate limiter (ramp). | [control.md](docs/modules/control.md) |
| Telemetry | `io.github.jasoncross.ftchelper.telemetry` | Neat, same-every-time Driver Station lines. | [telemetry.md](docs/modules/telemetry.md) |
| Safety | `io.github.jasoncross.ftchelper.safety` | Soft limits, limit switches, homing and slow zones for lifts, slides and arms. | [safety.md](docs/modules/safety.md) |
| Util | `io.github.jasoncross.ftchelper.util` | Math and angle helpers, loop timer, Auto-to-TeleOp pose handoff. | [util.md](docs/modules/util.md) |

Example OpModes you can copy are in [`examples/`](examples/src/main/java/io/github/jasoncross/ftchelper/examples).

## Works with

- FTC SDK **11.2.1** (tested). Newer SDK versions should work; see [Keep it up to date](docs/02-keep-up-to-date.md#a-new-ftc-sdk-version).
- Android Studio. (OnBot Java and Blocks cannot use a library like this.)
- Java 8 language level, like the SDK.

## Build and test the library by itself

You only need this if you change the library. From this folder:

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug
```

You need JDK 17 or newer and the Android SDK (Android Studio installs both). Details: [Contributing](docs/contributing.md).

## Rules check

The FTC Competition Manual has rules about software written before the season. We keep this
library in a public repository so any team can see and use it. Read the current manual
before each season and check that this is still enough.
