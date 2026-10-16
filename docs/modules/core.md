# Core module

Package: `io.github.jasoncross.ftchelper.core`

Learn it hands-on: [12 Organize a whole robot](../tutorials/12-robot-structure.md), [14 Mechanisms](../tutorials/14-mechanisms.md), [15 Telemetry, profiles and tuning](../tutorials/15-telemetry-profiles-tuning.md).

The small pieces every other part of the library is built on.

| Class | Use it to |
| --- | --- |
| [`Subsystem`](#subsystem) | make any mechanism "updatable, stoppable, showable" |
| [`Subsystems`](#subsystems) | update, stop and show a group of mechanisms together |
| [`Profile`](#profile) | behave differently in PRACTICE and COMPETITION |
| [`SettingsFile`](#settingsfile) | keep small settings on the Control Hub between restarts |
| [`RobotConfig`](#robotconfig) | describe the drivetrain once, for TeleOp and every autonomous |

---

## Subsystem

An **interface**: a promise that a class has these methods. Every mechanism helper in the library
(`PositionMotor`, `SpinMotor`, `Flywheel`, `Launcher`, `Turret`, cameras...) is a `Subsystem`.

| Method | Required? | What it is for |
| --- | --- | --- |
| `update()` | yes | called once every loop; the one place that talks to the hardware |
| `stop()` | no (default: nothing) | stop all motion; called at the end of every OpMode |
| `addTelemetry(RobotTelemetry t)` | no | add this part's lines (standard and debug) |
| `name()` | no (default: class name) | shown in telemetry |

**Why one shape for everything?** Because then ONE line updates the whole robot, autonomous can
keep every mechanism working during a drive move, and next season's new mechanism plugs in
without changing anything else.

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

The pattern: **commands** change a little state and are quick (safe from a button); **`update()`**
turns that state into hardware actions. (A servo command may move the servo at once; never command
anything in INIT unless moving then is safe.)

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
| `add(parts...)` | adds parts (null is skipped, for optional hardware) |
| `update()`, `stop()`, `addTelemetry(t)` | does it for every part, in order. `update()` times each part: one slower than 25 ms (it waits or sleeps) shows a warning |
| `getSlowUpdateWarning()` | "" or the last slow part (shown for 5 s after the last slow update) |
| `all()` | the list of parts |

`Subsystems` is itself a `Subsystem`, so groups can contain groups.

## Profile

`PRACTICE` or `COMPETITION`. Saved on the Control Hub, so it stays until someone changes it.
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
| `Profile.pick(practiceValue, competitionValue)` | one value or the other; works with numbers, true/false, anything |
| `profile.isPractice()` | true for PRACTICE |

```java
// Profile.pick(value in PRACTICE, value in COMPETITION)
c.powerLimit = Profile.pick(0.6, 1.0);         // gentle in the shop
boolean showCamera = Profile.pick(true, false); // live view only in practice
```

Full guide: [Profiles](../guides/profiles.md).

## SettingsFile

A tiny key/value file on the Control Hub: `/sdcard/FIRST/ftc-helper/settings.properties`.
`Profile` and `Tunable` use it; you rarely need it yourself.

| Method | What it does |
| --- | --- |
| `SettingsFile.shared()` | the robot's settings file |
| `get(key, fallback)`, `getDouble(key, fallback)`, `has(key)` | read |
| `put(key, value)`, `remove(key)` | change in memory |
| `save()` | write the file; returns false if it could not (the robot keeps working). It waits for the storage: use it in INIT or tests |
| `saveInBackground()`, `saveInBackground(ok -> ...)` | write the file on a background thread and return at once. Use this while the robot runs. |
| `SettingsFile.useForTests(file)` | tests only: use another file |

A damaged or missing file simply means "nothing saved": the values in the code are used.

## RobotConfig

The drivetrain described ONCE: motor names and directions, autonomous tuning, and how the Control
Hub is mounted. [`EasyTeleOp`](teleop.md) and [`EasyAuto`](auto.md) both read it from their
`robotConfig()`, so TeleOp and every autonomous always match. Change a motor name here and every
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
| `drive()` | yes | the [`DriveConfig`](drive.md#driveconfig). May be called more than once: make a fresh one each time. |
| `auto()` | no | the [`AutoDriveConfig`](drive.md#autodriveconfig). Default: untuned (autonomous refuses to run until you tune it) |
| `imu(hardwareMap)` | yes | the IMU, usually one [`ImuSetup.get`](hardware.md) line. Every Control Hub has one. (Null = no IMU: TeleOp is robot-centric only, and autonomous refuses to start.) |

The mechanisms are not in here: they go in your own `Robot` class, so your programs can reach them
by name (`robot.lift`). See the [starter kit](../starter-kit.md).
