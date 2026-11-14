# Tutorial 20: Easy TeleOp

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

With the five files, `Controls.java` already runs on `EasyTeleOp`: [Controls.java](../five-files/03-controls-java.md).
Build one by hand here to see what `EasyTeleOp` does inside, and to write a TeleOp for a robot without
the five files.

**You will build:** a full TeleOp (driving, lift and claw) in a few short methods.

**You need:** a drivetrain set up in `RobotSettings` (tutorial 09), a motor `lift` and a servo `claw`.

**Full program:** [`Tut20EasyTeleOp.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut20EasyTeleOp.java)

---

## 1. Compare with tutorial 12

Tutorial 12's TeleOp set up the drive and IMU, read and shaped the sticks, chose field- or robot-centric,
updated the lift, showed telemetry, and stopped everything in `finally`. Every robot needs all of them,
and they are the same on every robot. `EasyTeleOp` does all of it. You write only what is special about
YOUR robot. It is the TeleOp partner of `EasyAuto` (tutorial 16).

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

These are the SAME two methods as in tutorial 16's autonomous. `robotConfig()` gives the drive, the
autonomous tuning and the IMU in one object (a [`RobotConfig`](../modules/core.md#robotconfig)). So
TeleOp and autonomous can never use different motor names or directions. The tutorial `RobotSettings`
keeps its settings in `static` methods, so `RobotSettings.config()` wraps them into one object. The
starter kit's `RobotSettings` IS such an object, so there you write `new RobotSettings()`.

`EasyTeleOp` updates the mechanisms you return every loop, shows them on the screen, and stops them at
the end.

## 3. The driver's controls: already done

You get the driving on gamepad 1 without writing anything: left stick drives, right stick X turns, left
trigger slows. Double-tap Y for field-centric. BACK says "the robot now faces away from me". GUIDE opens
the tuning menu (PRACTICE profile). Field-centric "forward" continues from where autonomous ended, so the
driver does not need BACK at the start of a match.

Every free control, the alliance choice and the match timer: [EasyTeleOp: what it does for you](../modules/teleop.md#what-it-does-for-you).
In `libraryButtons(LibraryButtons lib)`, you can move each of them to another button, or free it. For
example: `lib.fieldCentric(Key.g1(Btn.X));` ([the full list](../modules/teleop.md#moving-the-librarys-buttons)).

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
you read gamepad 1's sticks here, check `isTuningMenuOpen()` first. Never `sleep()` or wait here: the
whole robot stops while you wait ([Non-blocking code](../guides/non-blocking.md)).

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

---

**Next:** [Tutorial 21: Find your numbers](21-finders.md)
**Look it up:** [teleop.md](../modules/teleop.md)
