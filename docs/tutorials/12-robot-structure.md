# Tutorial 12: Organize a whole robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** a whole robot program (drive, lift, claw) split into small classes, with a TeleOp and an autonomous that share them.
The main track gives you this structure ready-made: [The robot in five files](start/01-robot-in-five-files.md).

**You will learn:** how to split a real robot program into a `Robot` class and one class per
mechanism ("subsystem"), so TeleOp and autonomous share the same code and stay short.

**You need:** Tutorials 08-10. A drivetrain, a lift (`lift`, optional `lift_bottom`) and a claw servo (`claw`).

**Full programs:** the [`robot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/robot) folder:
`Robot`, `Lift`, `LiftHeights`, `Claw`, `MainTeleOp`, `MainAuto` (and `RobotSettings` from Tutorial 09).

---

## 1. Why organize?

By now one OpMode has: drive setup, IMU setup, lift motor, limits, PID, set heights, claw servo,
button bindings, telemetry. Then autonomous needs most of it AGAIN. Copies drift apart: someone
fixes the lift limit in TeleOp and forgets autonomous.

The fix: **each mechanism gets its own class that knows everything about it.** OpModes only say
WHAT to do ("lift to HIGH", "open the claw"), never HOW.

```
robot/
  LiftHeights.java   the set heights (no hardware: testable, Tutorial 13)
  Lift.java          lift motor, limits, PID, modes
  Claw.java          claw servo and its positions
  Robot.java         makes all the mechanisms, updates them, stops them
  MainTeleOp.java    buttons -> robot actions (short!)
  MainAuto.java      a route -> robot actions (short!)
RobotSettings.java   drive config, auto tuning, IMU (Tutorial 09)
```

In your TeamCode, put these in a folder `robot` (package `org.firstinspires.ftc.teamcode.robot`).

## 2. The set heights: `LiftHeights`

```java
public enum LiftHeights {
    BOTTOM(0), LOW(800), MIDDLE(1600), HIGH(2400);

    public final int ticks;
    public LiftHeights up()   { return this == HIGH ? HIGH : values()[ordinal() + 1]; }
    public LiftHeights down() { return this == BOTTOM ? BOTTOM : values()[ordinal() - 1]; }
    public static LiftHeights closestTo(int position) { ... }
}
```

An **enum** is a fixed list of named values. Names like `LiftHeights.HIGH` are much clearer than
`2400` all over the code, and the numbers live in ONE place. `up()`/`down()` step through the
list (the D-pad list from Tutorial 03, done properly). `ordinal()` is a value's place in the list
(BOTTOM = 0, LOW = 1, ...), and `values()` is the whole list. No hardware here, so Tutorial 13 can test it.

## 3. A subsystem: `Lift`

Everything from Tutorial 08, inside one class:

```java
public class Lift {
    private final SafeMotor motor;
    private final PidController pid = ...;
    private LiftHeights target = null;   // null = by hand
    private double handPower = 0;

    public Lift(HardwareMap hardwareMap) { ...MotorSetup, SafeMotor, optional switch... }

    public void goTo(LiftHeights height) { target = height; pid.reset(); }
    public void stepUp()   { ... }
    public void stepDown() { ... }
    public void manual(double power) { handPower = power; if (power != 0) target = null; }

    public void update() {               // once every loop
        motor.update();
        if (motor.isWaitingForHoming()) target = null;
        if (target == null) motor.setPower(handPower);
        else motor.moveTo(target.ticks, pid);
    }

    public boolean isAtTarget() { ... }
    public void stop() { ... }
    public void addTelemetry(Telemetry telemetry) { ... }
}
```

The pattern for every subsystem:

- **Constructor** (the method with the class's name, run by `new Lift(...)`): get and set up the hardware.
- **Commands** that only change state (`goTo`, `manual`): quick, safe to call from a button.
- **`update()`**: the one place that talks to the motors, once per loop.
- **Questions** (`isAtTarget`) and **`addTelemetry`**.
- **`stop()`**.

`Claw` is the same idea, smaller: `open()`, `close()`, `toggle()`, `isOpen()`.

## 4. The whole robot: `Robot`

`public final` fields can be read from other classes (`robot.lift`) but never swapped for another object.

```java
public class Robot {
    public final IMU imu;
    public final Lift lift;
    public final Claw claw;

    public Robot(HardwareMap hardwareMap) {
        BulkReads.auto(hardwareMap);
        imu = RobotSettings.imu(hardwareMap);
        lift = new Lift(hardwareMap);
        claw = new Claw(hardwareMap);
    }

    public void update() { lift.update(); }
    public void stop() { lift.stop(); }
    public void addTelemetry(Telemetry telemetry) { lift.addTelemetry(telemetry); claw.addTelemetry(telemetry); }
}
```

The drivetrain is NOT in `Robot`: TeleOp drives with `MecanumDrive`, autonomous with `AutoDrive`.
Both read `RobotSettings.drive()`, so they still match.

## 5. TeleOp becomes short

```java
Robot robot = new Robot(hardwareMap);
MecanumDrive drive = new MecanumDrive(hardwareMap, RobotSettings.drive());

ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.BACK, robot.imu::resetYaw)
        .bindPress(Pad.G2, Btn.DPAD_UP, robot.lift::stepUp)
        .bindPress(Pad.G2, Btn.DPAD_DOWN, robot.lift::stepDown)
        .bindPress(Pad.G2, Btn.A, () -> robot.lift.goTo(LiftHeights.BOTTOM))
        .bindPress(Pad.G2, Btn.Y, () -> robot.lift.goTo(LiftHeights.HIGH))
        .bindPress(Pad.G2, Btn.RB, robot.claw::toggle);
```

Read the button list out loud: it IS the driver's control sheet. Print it for the drive team.

`robot.lift::stepUp` is short for `() -> robot.lift.stepUp()`.

The loop: `controls.update`, `robot.lift.manual(stick)`, `robot.update()`, drive, telemetry.

## 6. Autonomous uses the same classes

```java
robot = new Robot(hardwareMap);
AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), robot.imu);
auto.setLoopHook(robot::update);      // the lift keeps working during every move
...
robot.claw.close();
robot.lift.goTo(LiftHeights.HIGH);
auto.move(30, 0, 0.6);
waitForLift(2.0);
robot.claw.open();
pause(0.3);
```

`waitForLift` and `pause` keep calling `robot.update()` while they wait (Tutorial 10, section 6),
so the lift keeps holding its height. **Do not use `sleep()` while a mechanism must hold
position:** `sleep` stops all your code, including the lift's PID.

## 7. Hand-over from autonomous

`MainAuto` saves its heading with `PoseStore.save(...)` in `finally`. `MainTeleOp` makes a
`DriverHeading` in INIT, which takes that heading. TeleOp sets up the IMU again (that makes "0" the
way the robot faces now), so `DriverHeading` adds the saved heading back. Field-centric "forward"
(Y turns it on) is then right from the start of TeleOp, without a reset. The robot must start
autonomous facing straight away from the driver. See
[DriverHeading](../modules/drive.md#driverheading).

## Run it

1. Copy the `robot` folder and `RobotSettings` into TeamCode (fix the package lines).
2. Run Main Auto: claw closes, lift rises while driving, claw opens, lift lowers on the way back.
3. Run Main TeleOp: telemetry shows "Heading: from autonomous". D-pad steps the lift through LOW, MIDDLE, HIGH.
   Right bumper toggles the claw. The stick takes over the lift.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift does nothing in TeleOp | `robot.update()` is not called in the loop. |
| Lift sags during auto moves | `auto.setLoopHook(robot::update)` is missing. |
| Lift drops while the claw opens | `sleep()` was used instead of `pause()`. |
| Two OpModes behave differently | Something is set up in an OpMode instead of in `Robot`/`RobotSettings`. Move it. |

## Try this

1. Add an `Intake` subsystem (a motor that runs in, out, or stops) with `in()`, `out()`, `stop()`,
   and bind it on gamepad 2.
2. Give `Lift` a `scoreHeightFor(...)` that picks a height from the game situation.
3. Make `Robot.addTelemetry` show only the lines the driver needs, and a `debug` flag for the rest.

## Next

[Tutorial 13: Test your code without a robot](13-testing.md)
