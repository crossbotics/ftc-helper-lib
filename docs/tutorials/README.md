# Tutorials

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Hands-on lessons that teach the library by building real robot programs, one idea at a time. There are
two tracks: the **main track** (start here) and **under the hood**.
Each tutorial has a **complete program** you can copy into TeamCode and run. Those programs are
built and checked every time the library changes, so the code in these lessons always works
with the current version.

The [module pages](../modules/) are the reference: every function, every parameter.
The tutorials are the "how and why": what to write, in what order, and what to check on the robot.

## Start here: the main track

Never used Android Studio? Start with [Start from zero](../start-from-zero.md) (45 to 60 minutes). Used
Scratch or Blocks before? Read [From Blocks to Java](../blocks-to-java.md) next, before the Quick start:
it shows where each block you know went.

Want a driving robot first, with no explanations? Do the [Quick start](../quick-start.md): it has a road
for a starter bot as it is, a changed starter bot, and your own robot. Then come back here. Not sure which robot code to start from? [Pick your template](../templates.md).

The shortest way to a whole robot program. **Lessons 1 to 6 are for every robot:** do them in order.
**Lessons 7 to 9 are for some robots:** do only the ones your robot needs (odometry, paths, a shooter).
**Lesson 10 is advanced:** automatic tests of your code, when your robot works.

**Which robot you build:** your own. You change **your robot's five files** (the ones the Quick start
put in your TeamCode), a few lines per lesson. Each lesson explains with a worked example, the
**lesson robot** ([`examples/.../lessonbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/lessonbot)):
a mecanum drive, an intake, a lift and a claw, in five short files. Read the lesson robot's lines, then
make the same change in your own TeamCode; the lesson robot's files are also the answer key. (Lesson 1
explains with goBILDA's starter bot, lesson 7 with an odometry robot, and lesson 9 with two shooter robots, the same way.) Every part
brings its own numbers and buttons, so each lesson adds only a few lines. A simulator test runs the
finished lesson robot, so the code here always works.

**The simulator commands:** each lesson shows two sets. The lesson robot's use `-PteamCode=none` (the
library's examples). Your own robot's use `-PteamCode=../TeamCode`:
`./gradlew :sim:fromRobot -PteamCode=../TeamCode`, then `./gradlew :sim:run -PteamCode=../TeamCode`.
Run them from the `ftc-helper-lib` folder ([the Terminal tab](../start-from-zero.md#6-the-terminal-tab)).
Simulator tests (lesson 10) for your own robot go in your robot project's
`simulator/scenarios/` folder, so they are saved with your code.

**"Every loop":** the lessons often say a job runs "every loop". The **loop** is the library's loop: it
runs many times a second, and each time round it reads the gamepads, runs the jobs and updates every
part. You never write it ([From Blocks to Java](../blocks-to-java.md#the-key-idea-you-never-write-the-loop)).

| # | Lesson | You learn | Robot needed |
| --- | --- | --- | --- |
| 1 | [The robot in five files](start/01-robot-in-five-files.md) | what each of the five files is for; make them from your robot configuration; try it in the simulator | none |
| 2 | [Drive](start/02-drive.md) | the drive line: mecanum or tank, motors, wheels, the IMU; the driving you get for free; check the directions | a drivetrain (or the simulator) |
| 2b | [Check the hardware (lesson 2b)](19-testing-procedures.md) | the Hardware Check, and a testing order that finds problems early | your robot |
| 3 | [Buttons](start/03-buttons.md) | `Controls.java`: the buttons the parts bring, your own jobs, one or two drivers | a gamepad |
| 4 | [An intake, a lift and a claw](start/04-lift-and-claw.md) | parts, their numbers in `Tunables.java`, homing, finding and tuning the numbers | the mechanisms |
| 5 | [Actions](start/05-actions.md) | several parts together, from a button or in an autonomous | the mechanisms |
| 6 | [Your first autonomous](start/06-first-autonomous.md) | tune the drive, then steps, park time, the report | a tuned drivetrain |
| | **The lessons your robot needs** | | |
| 7 | [Odometry: where am I on the field?](start/07-odometry.md) | which device to buy, the odometry line, the Odometry Tuner, `driveTo` field points, TeleOp starting where autonomous ended | a mecanum drive, a Pinpoint (or OTOS, or dead wheels) |
| 8 | [Paths with Pedro Pathing](start/08-paths-with-pedro.md) (optional) | what a path follower does and whether you need one, adding Pedro, lines and curves in your field numbers, Pedro's tuner, the first real run | a mecanum drive and odometry (lesson 7 done) |
| 9 | [A shooter, a camera and a turret](start/09-shooter-and-camera.md) (for robots that shoot) | a flywheel and feeder, aiming with a camera, a turret that follows the goal, the odometry as backup aim, two shot tables, whole-robot aim, a buzz on target, knowing which piece fires next, a light for the drivers | lesson 6 done, a shooter, a camera |
| | **Advanced** | | |
| 10 | [Test your robot code](start/10-test-your-robot-code.md) (advanced) | record a test in the simulator, read it line by line, tolerances, goals not details, a few good tests, update a test after a planned change, run them all with one command | lesson 6 done; the simulator |

Then: [the button menu](../guides/buttons.md) (every way a button can work),
[the cookbook](../cookbook.md) (short "how do I...?" answers with code to copy),
[the autonomous builder](../guides/autonomous-builder.md) (everything autonomous can do),
[the finders](../guides/finders.md) (measure your numbers), and the [simulator](../guides/simulator.md).

## Under the hood: how it works

**These tutorials are optional: you can skip them all.** The main track is enough to build and run a
robot. They build the same things by hand, with the library's building blocks. Do them to learn
how a robot program works inside (what a PID is, what a bulk read is, why software limits do not
replace hard stops), or when you need a part that has no five-files module. Each has a complete
program.

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
| 18 | [Vision aiming](18-vision-aim.md) | webcam or Limelight 3A through one interface, safe aiming | camera + drivetrain |
| 19 | [Check the hardware (lesson 2b)](19-testing-procedures.md) | the Hardware Check and a testing order (it is lesson 2b of the main track) | any robot |
| 20 | [Easy TeleOp](20-easy-teleop.md) | `EasyTeleOp`, the layer under `Controls.java` (also used by the [starter kit](../starter-kit.md)) | drivetrain + lift + claw |
| 21 | [Find your numbers](21-finders.md) | the finders: servo ends, lift limits, flywheel gains, wheel size, camera offset, shot table | the parts to measure |

Time: about 30-60 minutes each, more for 07, 09 and 12 (tuning takes time). New to Java? Do 01 first:
it explains imports, fields and lambdas (`() -> ...`), which every lesson uses.

## Before you start

1. The library is in your robot project: [Add it to a project](../01-add-to-project.md).
2. Read [Signs and units](../conventions.md) once. It saves hours of "why does it turn the wrong way".
3. Have the robot configuration on the Driver Station (the list of devices and their names) match
   the names in the tutorial, or change the names in the code. Each tutorial lists the names it uses.

## How to use a tutorial program

The programs are in the library at
[`examples/.../examples/tutorials/`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials).

1. Copy the file into `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
2. Change its first line to `package org.firstinspires.ftc.teamcode;` (the `package` line must
   match the folder the file is in)
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
