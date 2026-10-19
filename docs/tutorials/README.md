# Tutorials

Hands-on lessons that teach the library by building real robot programs, one idea at a time.
Each tutorial has a **complete program** you can copy into TeamCode and run. Those programs are
built and checked every time the library changes, so the code in these lessons always works
with the current version.

The [module pages](../modules/) are the reference: every function, every parameter.
The tutorials are the "how and why": what to write, in what order, and what to check on the robot.

## The learning path

Do them in order the first time. Later, jump to the one you need.

| # | Tutorial | You learn | Robot needed |
| --- | --- | --- | --- |
| 01 | [OpMode basics with the library](01-opmode-basics.md) | the shape of every program here, imports, fields, lambdas, `try`/`finally` | Control Hub + gamepad only |
| 02 | [Motors and servos](02-motors-and-servos.md) | `MotorSetup`, `ServoSetup`, `Hw`, encoders, servo ranges | 1 motor, 1 servo |
| 03 | [Buttons](03-buttons.md) | every kind of binding, toggles with rumble, triggers, choosing from a list | gamepad only |
| 04 | [Drive a mecanum robot](04-drive-mecanum.md) | `MecanumDrive`, stick shaping, slow mode, field-centric, reverse | mecanum chassis |
| 05 | [Drive a standard (tank) robot](05-drive-tank.md) | `TankDrive`, arcade vs tank style, 2 or 4 motors | standard chassis |
| 06 | [A fast, clear loop](06-fast-clear-loop.md) | bulk reads, `LoopTimer`, telemetry layout | any drivetrain |
| 07 | [PID: hold a lift at a height](07-pid-lift.md) | `PidController`, live tuning from the gamepad | a lift with an encoder |
| 08 | [A lift that cannot hurt itself](08-safe-lift.md) | `SafeMotor`: soft limits, homing, slow zone, set heights + hand control | a lift (+ bottom switch) |
| 09 | [Your first autonomous](09-first-auto.md) | `AutoDrive`, shared `RobotSettings`, tuning distance and turns | any drivetrain |
| 10 | [Autonomous with mechanisms](10-auto-with-mechanisms.md) | loop hook, results, choosing the plan in INIT, mirroring red/blue, pose handoff | drivetrain + lift |
| 11 | [Driver assists](11-driver-assists.md) | heading snap with PID, `AimRumble`, smooth starts with `SlewRateLimiter` | mecanum + IMU |
| 12 | [Organize a whole robot](12-robot-structure.md) | `Robot` and subsystem classes, a short TeleOp and auto that share them | drivetrain, lift, claw |
| 13 | [Test your code without a robot](13-testing.md) | JUnit tests for team code, run on a laptop | none |
| 14 | [Mechanism helpers](14-mechanisms.md) | `PositionMotor`, `SpinMotor`, `PositionServo`, `SpinServo`, `Subsystems` | lift, servo, intake |
| 15 | [Telemetry, profiles and tuning](15-telemetry-profiles-tuning.md) | driver vs debug telemetry, practice/competition, live tuning, match timer | a lift |
| 16 | [Easy autonomous](16-easy-autos.md) | `EasyAuto`: steps, INIT choices, mirroring, park time, the report | tuned drivetrain + lift + claw |
| 17 | [Launcher](17-launcher.md) | flywheel, hood, feeder, shot table, `Launcher` | a shooter |
| 18 | [Vision aiming](18-vision-aim.md) | webcam or Limelight 3A through one interface, safe aiming | camera + drivetrain |
| 19 | [Testing procedures](19-testing-procedures.md) | the Hardware Check and a testing order that finds problems early | any robot |
| 20 | [Easy TeleOp](20-easy-teleop.md) | `EasyTeleOp`: driving, timer and menu for free; add buttons and mechanisms; one `RobotConfig` for TeleOp and auto | drivetrain + lift + claw |
| 21 | [Find your numbers](21-finders.md) | the finders: servo ends, lift limits, flywheel gains, wheel size, camera offset, shot table | the parts to measure |
| 22 | [The robot in five files](22-robot-in-five-files.md) | `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java`, autos: modules with their numbers and buttons; make them from your configuration; the simulator robot file from the code | none, then your robot |

**Two paths.** Tutorials 01-13 teach how things work by building them yourself. Tutorials 14-21 use
the ready-made helpers that do the same work for you. New programmers: do 01-06, then jump to 14.
Want the whole robot fast? [Tutorial 22](22-robot-in-five-files.md): the robot in five files, made from your configuration
(or the older [starter kit](../starter-kit.md)).

Time: about 30-60 minutes each, more for 07, 09 and 12 (tuning takes time).

## Before you start

1. The library is in your robot project: [Add it to a project](../01-add-to-project.md).
2. Read [Signs and units](../conventions.md) once. It saves hours of "why does it turn the wrong way".
3. Have the robot configuration on the Driver Station match the names in the tutorial,
   or change the names in the code. Each tutorial lists the names it uses.

## How to use a tutorial program

The programs are in the library at
[`examples/.../examples/tutorials/`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials).

1. Copy the file into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
2. Change its first line to `package org.firstinspires.ftc.teamcode;`
   (tutorial 12's files go in a folder `robot` there, with `package org.firstinspires.ftc.teamcode.robot;`,
   and also need `RobotSettings`).
3. Delete the `@Disabled` line, so it shows on the Driver Station.
4. Build, then run it from the Driver Station.

**Better: type it yourself.** Each tutorial builds the program step by step. Typing it (and making
the mistakes) teaches far more than copying. Use the full file to check your work.

## Safety for every tutorial

- Start with low power (`powerLimit = 0.4`, or a small multiplier). Raise it after it works.
- Wheels off the ground the first time a drivetrain runs.
- Keep a hand near the Driver Station STOP button.
- Lifts and arms: know where the hard stops are before you run anything.
