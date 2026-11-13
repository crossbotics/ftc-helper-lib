# Tutorial 20: Easy TeleOp

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a full TeleOp (driving, lift and claw) in a few short methods.
The main track's `Controls.java` is built on `EasyTeleOp` and is shorter still: [Controls.java](../five-files/03-controls-java.md).

**You will learn:** how `EasyTeleOp` turns a TeleOp into a few short methods, which controls you get for
free, how to add the operator's buttons and sticks, and how TeleOp and autonomous share one robot
description.

**You need:** a drivetrain set up in `RobotSettings` (tutorial 09), a motor `lift` and a servo `claw`.
Do Tutorials 03, 14 and 16 first.

**Full program:** [`Tut20EasyTeleOp.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut20EasyTeleOp.java)
**Reference:** [teleop.md](../modules/teleop.md).

---

## 1. Compare with tutorial 12

Tutorial 12's TeleOp set up the drive and IMU, read the sticks, shaped them, chose field- or
robot-centric, updated the lift, showed telemetry, and stopped everything in `finally`. The starter
kit's old TeleOp added a match timer, a tuning menu and warnings: over 100 lines. Every robot needs all
of them, and they are the same on every robot. `EasyTeleOp` does all of it. You write only what is
special about YOUR robot. It is the TeleOp partner of `EasyAuto` (tutorial 16).

## 2. Tell it about the robot

```java
public class Tut20EasyTeleOp extends EasyTeleOp {
    private PositionMotor lift;
    private PositionServo claw;

    @Override protected RobotConfig robotConfig() { return RobotSettings.config(); }

    @Override protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")...;
        claw = PositionServo.create(hardwareMap, "claw")...;
        return new Subsystems().add(lift, claw);
    }
```

These are the SAME two methods as in tutorial 16's autonomous. The tutorial `RobotSettings` (from
tutorial 09) keeps its settings in `static` methods, so `RobotSettings.config()` wraps them into one
object. The starter kit's `RobotSettings` IS such an object, so there you write `new RobotSettings()`.
`robotConfig()` gives the drive, the autonomous tuning and the IMU in one object (a
[`RobotConfig`](../modules/core.md#robotconfig)). So TeleOp and autonomous can never use different motor
names or directions. `EasyTeleOp` updates the mechanisms you return every loop, shows them on the
screen, and stops them at the end.

## 3. The driver's controls: already done

You get these on gamepad 1 without writing anything:

| Control | Does |
| --- | --- |
| left stick | drive (mecanum: any direction; tank: forward/back) |
| right stick X | turn |
| left trigger | slow: squeeze for fine moves |
| Y | double-tap: field-centric on/off (starts off) |
| BACK (alone, on release) | "the robot now faces away from me" (one buzz) |
| GUIDE | tuning menu (PRACTICE profile) |
| BACK + START together | debug telemetry on/off when let go (does not reset "forward"). The alliance switch in TeleOp is off. `lib.switchAlliance(Key.g1(Btn.BACK), Key.g1(Btn.START))` turns it on: then both held 1.5 s switch the alliance |
| X / B in INIT | choose BLUE / RED when no autonomous ran before (it remembers autonomous's choice) |

In `libraryButtons(LibraryButtons lib)`, you can move each of these to another button, or free it. For
example: `lib.fieldCentric(Key.g1(Btn.X));` ([the full list](../modules/teleop.md#moving-the-librarys-buttons)).

Field-centric "forward" continues from where autonomous ended (also after INIT, STOP, INIT again). So
the driver does not need BACK at the start of a match ([DriverHeading](../modules/drive.md#driverheading)).

## 4. The operator's buttons

The **operator** is the second driver, on gamepad 2. The operator runs the mechanisms while the driver
drives.

```java
@Override protected void bindButtons(ControllerBindings driver, ControllerBindings operator) {
    operator.bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
            .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"))
            .bindPress(Pad.G2, Btn.RB, () -> claw.toggle("OPEN", "CLOSED"));
}
```

These are the same bindings as in tutorial 03, on the `operator` object `EasyTeleOp` hands you. Each
action only sets a target and returns. The lift moves in its own `update()`. Put extra buttons for the
driver on `driver`. They pause while the tuning menu is open, because the menu uses gamepad 1.

## 5. Every loop: sticks and rules

```java
@Override protected void everyLoop() {
    lift.manual(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
}
```

A stick is not a button, so it goes here. `everyLoop()` runs after the buttons and the driving, before
the mechanisms update. It keeps running while the tuning menu is open. The menu uses gamepad 1, so when
you read gamepad 1's sticks here, check `isTuningMenuOpen()` first. Keep it quick: never `sleep()` or
wait here ([Non-blocking code](../guides/non-blocking.md)).

## 6. Extra screen lines (optional)

```java
@Override protected void addTelemetry(RobotTelemetry t) {
    t.line("Alliance", alliance());
}
```

The lift and claw already show their own lines. The time and drive mode are at the top.

## Run it

1. Run tutorial 16's autonomous first, then this TeleOp. INIT shows "Forward: from autonomous".
2. Press ▶ (start on the Driver Hub). Drive robot-centric. Double-tap Y (two buzzes): field-centric. Push the
   stick up: the robot drives away from you, whichever way it faces.
3. On gamepad 2, A / Y move the lift, the right bumper opens and closes the claw, and the left stick
   moves the lift by hand.
4. Wait until 1:00 is left: both gamepads buzz.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "EasyTeleOp needs your robot settings" | Add `robotConfig()`. |
| Field-centric "forward" is off | The robot did not start autonomous facing away from you. Set `startHeadingDeg()` in the autonomous, or press BACK (in a Controls.java TeleOp: hold BACK 1 s). |
| Y does nothing | The drivetrain is tank, or there is no IMU. Field-centric needs mecanum wheels and the IMU. |
| The robot does not drive | The tuning menu is open. GUIDE closes it. |
| A warning "button job took ... ms" | A button action waits. Remove the wait ([Non-blocking code](../guides/non-blocking.md)). |

## Try this

1. Start in field-centric: `@Override protected boolean startFieldCentric() { return true; }`.
2. Make slow mode slower: `@Override protected double slowSpeed() { return 0.2; }`.
3. Add a driver button: `driver.bindPress(Pad.G1, Btn.A, ...)` in `bindButtons`.

## Next

[Tutorial 21: Find your numbers](21-finders.md). The [starter kit](../starter-kit.md) puts tutorials
16 and 20 together into a whole robot program.
