# Core module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.core`

Learn it hands-on: [12 Organize a whole robot](../tutorials/12-robot-structure.md), [14 Mechanisms](../tutorials/14-mechanisms.md), [15 Telemetry, profiles and tuning](../tutorials/15-telemetry-profiles-tuning.md).

The small pieces that every other part of the library uses. You meet them when you write your own
mechanism class (`Subsystem`), or switch between practice and competition settings (`Profile`). You also
meet them when you describe your drivetrain once for every program (`RobotConfig`). With the
[robot in five files](../reference/robot-java.md), you use them by name only for a special case. To see how
they fit together, start with tutorial 12.

| Class | Use it to |
| --- | --- |
| [`Subsystem`](#subsystem) | give any mechanism the same shape: update it every loop, stop it, show it on the screen |
| [`Subsystems`](#subsystems) | update, stop and show a group of mechanisms together |
| [`Profile`](#profile) | behave differently in PRACTICE and COMPETITION |
| [`SettingsFile`](#settingsfile) | keep small settings on the Control Hub between restarts |
| [`RobotConfig`](#robotconfig) | describe the drivetrain once, for TeleOp and every autonomous |
| [`InitStop`](#initstop) | show a mistake found at INIT as plain lines on the Driver Station |
| [`Time`](#time) | the library's clock (a test can swap in a pretend one) |

---

## Subsystem

An **interface**: a promise that a class has these methods. Every mechanism helper in the library
(`PositionMotor`, `SpinMotor`, `Flywheel`, `Launcher`, `Turret`, cameras...) is a `Subsystem`.

| Method | Required? | What it is for |
| --- | --- | --- |
| `update()` | yes | the loop calls it once every loop. It is the one place that talks to the hardware |
| `stop()` | no (default: nothing) | stop all motion. The OpMode calls it at the end |
| `addTelemetry(RobotTelemetry t)` | no | add this part's lines (standard and debug) |
| `name()` | no (default: class name) | shown in telemetry |

**Why one shape for everything:** ONE line updates the whole robot. Autonomous can keep every mechanism
working during a drive move. And next season's new mechanism plugs in without changes anywhere else.

Your own mechanism:

```java
// "implements Subsystem": Bucket promises to have update() (and may add stop(), addTelemetry())
public class Bucket implements Subsystem {
    private final PositionServo servo;
    private boolean dumping = false;   // the state: what the bucket should be doing

    // Runs once, when the robot code makes the Bucket (in INIT). "bucket" = configuration name.
    public Bucket(HardwareMap hw) {
        servo = PositionServo.create(hw, "bucket").position("CARRY", 0.3).position("DUMP", 0.9);
    }

    public void dump()  { dumping = true; }   // commands only change state: quick, safe from a button
    public void carry() { dumping = false; }

    // @Override: fills in a method Subsystem asks for. "a ? b : c" = if a then b, else c.
    @Override public void update() { servo.goTo(dumping ? "DUMP" : "CARRY"); servo.update(); }
    @Override public void addTelemetry(RobotTelemetry t) { t.line("Bucket", dumping ? "DUMP" : "carry"); }
}
```

The pattern: **commands** change a little state and are quick, so a button can call them safely.
**`update()`** turns that state into hardware actions. A servo command may move the servo at once. So
command a part in INIT only when a move then is safe.

## Subsystems

A group of subsystems that acts like one.

```java
Subsystems robot = new Subsystems().add(lift, claw, intake);   // null parts are skipped
robot.update();               // each, in order
robot.addTelemetry(t);
robot.stop();                 // stops each, even if one throws an error
```

| Method | What it does |
| --- | --- |
| `add(parts...)` | adds parts. It skips null, for optional hardware |
| `update()`, `stop()`, `addTelemetry(t)` | does it for every part, in order. `update()` times each part. When a part takes more than 25 ms (it waits or sleeps), the group shows a warning |
| `getSlowUpdateWarning()` | "" or the last slow part. It stays for 5 s after the last slow update |
| `all()` | the list of parts |

`Subsystems` is itself a `Subsystem`, so a group can contain groups.

## Profile

`PRACTICE` or `COMPETITION`. The Control Hub saves it, so it stays until someone changes it.
**Default: COMPETITION** (the safe one).

| | PRACTICE | COMPETITION |
| --- | --- | --- |
| `RobotTelemetry` debug lines | start ON | start OFF |
| `TuningMenu` | can open | stays closed |
| `EasyAuto` INIT screen | warns "PRACTICE profile" | - |

| Method | What it does |
| --- | --- |
| `Profile.current()` | the active profile |
| `Profile.set(p)` | changes and saves it (or use `InitMenu.profile()`) |
| `Profile.pick(practiceValue, competitionValue)` | one value or the other. It works with numbers, true/false, anything |
| `profile.isPractice()` | whether the profile is PRACTICE |

```java
// Profile.pick(value in PRACTICE, value in COMPETITION)
c.powerLimit = Profile.pick(0.6, 1.0);         // gentle in the shop
boolean showCamera = Profile.pick(true, false); // live view only in practice
```

Full guide: [Profiles](../guides/profiles.md).

## SettingsFile

A tiny file on the Control Hub that keeps settings as name = value pairs:
`/sdcard/FIRST/ftc-helper/settings.properties`. `Profile` and `Tunable` use it. You need it yourself only for
a special case.

| Method | What it does |
| --- | --- |
| `SettingsFile.shared()` | the robot's settings file |
| `SettingsFile.folder()` | the helper's folder: `/sdcard/FIRST/ftc-helper` on the robot, the simulator's data folder on a laptop (the `ftchelper.dataDir` setting) |
| `get(key, fallback)`, `getDouble(key, fallback)`, `has(key)` | read |
| `put(key, value)`, `remove(key)` | change in memory |
| `save()` | write the file. It gives false when it could not, and the robot keeps working. It waits until the file is written, so use it in INIT or tests |
| `saveInBackground()`, `saveInBackground(ok -> ...)` | write the file on a background thread, and return at once. Use this while the robot runs |
| `SettingsFile.useForTests(file)` | tests only: use another file |

A damaged or missing file means "nothing saved". Then the library uses the values in the code.

## RobotConfig

The drivetrain, described ONCE: motor names and directions, autonomous tuning, and how the Control Hub
is mounted. [`EasyTeleOp`](teleop.md) and [`EasyAuto`](auto.md) both read it from their
`robotConfig()`. So TeleOp and every autonomous always match. When you change a motor name here, every
program gets it.

```java
public class RobotSettings extends RobotConfig {
    @Override public DriveConfig drive() {
        DriveConfig c = DriveConfig.mecanum();
        c.frontLeft = "front_left";   // ... the other three names
        c.powerLimit = Profile.pick(0.6, 1.0);
        return c;
    }
    @Override public AutoDriveConfig auto() { ... }     // optional: only autonomous uses it
    @Override public IMU imu(HardwareMap hardwareMap) {
        return ImuSetup.get(hardwareMap, "imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD);
    }
}

// in every TeleOp and autonomous:
@Override protected RobotConfig robotConfig() { return new RobotSettings(); }
// (the tutorials' RobotSettings uses static methods instead: return RobotSettings.config();)
```

| Method | Required? | What you return |
| --- | --- | --- |
| `drive()` | yes | the [`DriveConfig`](drive.md#driveconfig). The library may call it more than once, so make a fresh one each time |
| `auto()` | no | the [`AutoDriveConfig`](drive.md#autodriveconfig). Default: untuned, and autonomous refuses to run until you tune it |
| `imu(hardwareMap)` | yes | the IMU, usually one [`ImuSetup.get`](hardware.md) line. Every Control Hub has one. Null = no IMU: TeleOp is robot-centric only, and autonomous refuses to start |

The mechanisms go in your own `Robot` class, not here. Then your programs can reach them by name
(`robot.lift`). See the [starter kit](../starter-kit.md).

## InitStop

It shows a mistake found at INIT as plain lines on the Driver Station, in place of the FTC app's red line.
A mistake can be a wrong name in Robot.java or a missing number in Tunables.java. For a missing number, you
see:

<samp>INIT STOPPED: the robot did not start.</samp><br>
<samp>Tunables.Lift has no max: write max (...)</samp><br>
<samp>Fix it, then press INIT again. Press STOP now.</samp>

Then it waits for STOP, and nothing moves. The library's OpModes (EasyTeleOp, EasyAuto, the finders, the
Hardware Check, Tune and check) and `robot.init(this)` use it already. InitStop does not catch a mistake
after ▶ (start on the Driver Hub): the FTC app shows it as before. The robot log keeps the full error.

| Method | What it does |
| --- | --- |
| `InitStop.run(this, () -> { ... })` | in your own `LinearOpMode`: runs the code. It shows a mistake before ▶ and waits for STOP |
| `InitStop.showAndWait(this, e)` | show one error now and wait for STOP (false in the simulator) |
| `InitStop.words(e)` | the plain words for an error. It gives a library message as it is, and another error with where it happened, for example "NullPointerException in Robot.java line 42" |

**Why:** the red line starts with Java words, and you can miss the real message after them. In the
simulator, INIT errors still show in its own error panel.

## Time

The library's clock. A JUnit test can swap in a pretend clock and move the time forward by hand. Then a
test of timing is quick, and it gives the same result on a slow computer. Use the testkit's
[`PretendClock`](testkit.md#pretendclock).

`Time.nanos()` is the time now, in nanoseconds. Only the difference between two times means anything. On
the robot it is the real clock, `System.nanoTime()`. Most of the robot's parts read it each time they need
the time. These parts include the five-files modules, the launcher, the mechanisms, the match timer, and
the PID and slew controllers.

| Method | What it does |
| --- | --- |
| `Time.nanos()` | the time now, in nanoseconds. It never waits |
| `Time.useForTests(clock)` | JUnit tests only: use this clock (nanoseconds). Null = the real clock again |

A new part that needs the time reads `Time::nanos`, not `System::nanoTime`, so tests can move it. Two parts
keep the real clock. The slow-loop warning measures how long the loop itself takes. The camera aim uses the
real clock because the camera stamps its pictures with it, so the aim measures a picture's age on it too.

