# Tutorials

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

These tutorials build robot programs by hand, with the library's building blocks, one idea at a time
(**under the hood**). Start with the main track below: it is the docs sections, not tutorials.
Each tutorial has a **complete program** you can copy into TeamCode and run. A test builds and checks
those programs every time the library changes.

The [module pages](../modules/) are the reference: every function, every parameter.
The tutorials give the "how and why": what to write, in what order, and what to check on the robot.

## Start here: the main track

The main track is now the docs sections, in this order:

1. [Set up](../setup/README.md): Android Studio, GitHub, the library, the configuration.
2. [Pick your start](../pick-your-start/README.md): a starter bot, a common setup, or a blank slate.
3. [The five files](../five-files/README.md): `Robot.java`, `Tunables.java`, `Controls.java`,
   `Actions.java` and the autos. Done when the robot drives and runs a first auto.
4. [Add to your robot](../add-to-robot/README.md): one page per part.
5. [Controls](../controls/README.md): every way a button can work.
6. [Building autos](../building-autos/README.md): steps, field points, Pedro paths, the auto builder.
7. [Tuning](../tuning/README.md): find and check every number.
8. [Tests](../tests/README.md): simulator tests of your code, and checks of the real robot.

**Used Scratch or Blocks before:** read [From Blocks to Java](../blocks-to-java.md) first.

## Under the hood: how it works

**These tutorials are optional: you can skip them all.** The main track is enough to build and run a
robot. These tutorials build the same things by hand, with the library's building blocks. Do them to
learn how a robot program works inside: what a PID is, what a bulk read is, why software limits do not
replace hard stops. Also do them when you need a part that has no five-files module. Each one has a
complete program.

| # | Tutorial | You learn | Robot needed |
| --- | --- | --- | --- |
| 01 | [OpMode basics with the library](01-opmode-basics.md) | the shape of every program, imports, fields, lambdas, `try`/`finally` (Java basics) | Control Hub + gamepad only |
| 02 | [Motors and servos](02-motors-and-servos.md) | `MotorSetup`, `ServoSetup`, `Hw`, encoders, servo ranges | 1 motor, 1 servo |
| 03 | [Buttons](03-buttons.md) | `ControllerBindings`: every kind of binding, toggles with rumble, triggers | gamepad only |
| 04 | [Drive a mecanum robot](04-drive-mecanum.md) | `MecanumDrive`, stick shaping, slow mode, field-centric, reverse | mecanum chassis |
| 05 | [Drive a standard (tank) robot](05-drive-tank.md) | `TankDrive`, arcade vs tank style, 2 or 4 motors | standard chassis |
| 06 | [A fast, clear loop](06-fast-clear-loop.md) | bulk reads, `LoopTimer`, telemetry layout | any drivetrain |
| 07 | [PID: hold a lift at a height](07-pid-lift.md) | what P, I, D and F do; `PidController`; live tuning | a lift with an encoder |
| 08 | [A lift that cannot hurt itself](08-safe-lift.md) | `SafeMotor`: soft limits, homing, slow zone; why hard stops still matter | a lift (+ bottom switch) |
| 09 | [Autonomous by hand with AutoDrive](09-first-auto.md) | `AutoDrive`, tuning distance and turns | any drivetrain |
| 10 | [Autonomous with mechanisms](10-auto-with-mechanisms.md) | loop hook, results, INIT choices, mirroring, pose handoff, by hand | drivetrain + lift |
| 11 | [Driver assists](11-driver-assists.md) | heading snap with PID, `AimRumble`, `SlewRateLimiter` | mecanum + IMU |
| 12 | [Organize a whole robot](12-robot-structure.md) | `Robot` and subsystem classes by hand (what five files does for you) | drivetrain, lift, claw |
| 13 | [Test your code without a robot](13-testing.md) | JUnit tests for team code, run on a laptop | none |
| 14 | [Mechanism helpers](14-mechanisms.md) | `PositionMotor`, `SpinMotor`, `PositionServo`, `SpinServo`, `Subsystems`: what the modules are made of | lift, servo, intake |
| 15 | [Telemetry, profiles and tuning](15-telemetry-profiles-tuning.md) | driver vs debug telemetry, practice/competition, live tuning, match timer | a lift |
| 16 | [Easy autonomous](16-easy-autos.md) | `EasyAuto`, the layer under the five-files autonomous | tuned drivetrain + lift + claw |
| 17 | [Launcher](17-launcher.md) | flywheel, hood, feeder, shot table, by hand | a shooter |
| 18 | [Vision aiming](18-vision-aim.md) | webcam, Limelight 3A or HuskyLens through one interface, safe aiming | camera + drivetrain |
| 19 | [Check the hardware](19-testing-procedures.md) | the Hardware Check and a testing order  | any robot |
| 20 | [Easy TeleOp](20-easy-teleop.md) | `EasyTeleOp`, the layer under `Controls.java` (also used by the [starter kit](../starter-kit.md)) | drivetrain + lift + claw |
| 21 | [Find your numbers](21-finders.md) | the finders: servo ends, lift limits, flywheel gains, wheel size, camera offset, shot table | the parts to measure |

Tutorials 07, 09 and 12 take longer than the others, because tuning takes time. **New to Java:** do 01
first. It explains imports, fields and lambdas (`() -> ...`), and every lesson uses them.

## Before you start

1. The library is in your robot project: [Add the library](../setup/04-add-the-library.md).
2. Read [Signs and units](../conventions.md) once. It saves you hours of fixing a robot that turns the wrong
   way.
3. The robot configuration on the Driver Station is the list of devices and their names. Make it match
   the names in the tutorial, or change the names in the code. Each tutorial lists the names it uses.

## How to use a tutorial program

The programs are in the library at
[`examples/.../examples/tutorials/`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials).

1. Copy the file into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
2. The `package` line must match the folder the file is in. So change the first line to
   `package org.firstinspires.ftc.teamcode;`.
   Tutorial 12's files go in a folder `robot` there, with `package org.firstinspires.ftc.teamcode.robot;`.
   They also need `RobotSettings`.
3. Delete the `@Disabled` line, so the program shows on the Driver Station.
4. Build, then run it from the Driver Station.

**Better: type it yourself.** Each tutorial builds the program step by step. When you type it, you make
mistakes and fix them, and you learn more than from a copy. Use the full file to check your work.

## Safety for every tutorial

- Start with low power (`powerLimit = 0.4`, or a small multiplier). Raise it after the program works.
- Lift the wheels off the ground the first time a drivetrain runs.
- Keep a hand near the Driver Station STOP button.
- For lifts and arms, find the hard stops before you run anything.
