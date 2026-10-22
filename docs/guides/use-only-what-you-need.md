# Use only what you need

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**For:** teams with their own code who do not want a framework. **You need:** your own
`LinearOpMode` and the library [added to the project](../01-add-to-project.md).

You do not have to use `EasyTeleOp`, `ControlMap` or the robot in five files. Every building block
below works on its own, next to plain SDK code, in your own loop. For each one: the import, a short
example, what it depends on, and how to get around it.

**Mixing with the five files:** there is no per-part switch yet to take one part out of
`robot.update()`. A part added in `Robot.java` (`add(...)`) is updated every loop, and it sends its own
power to its motor. So if you want to drive a motor yourself, **do not** add it as a part in
`Robot.java`. Use the building block for it (`PositionMotor` or `SafeMotor`, below) in your own OpMode
instead. **Why:** two pieces of code sending power to one motor fight each other: the part keeps sending
its own power (a Lift or Arm sends it again at least every 25 loops), so yours does not last.

"Depends on" was checked in the source. **None of these blocks reads or writes a file, and none
keeps static state, unless the table says so.**

| Block | Depends on | Files or statics |
| --- | --- | --- |
| [`ControllerBindings`](#controllerbindings) | the SDK `Gamepad` | none |
| [`SafeMotor`](#safemotor) | `PidController`, `SoftLimits`, `MathUtil` | one static: the 3 ms reading reuse, changed only by the test hook `SafeMotor.freshReadingsForTests(boolean)` (tests only) |
| [`PositionMotor`](#positionmotor) | `SafeMotor`, `PidController`, `MotorSetup`, `Hw` | none, **unless** you call `.tunable(...)` (see there) |
| [`PidController`](#pidcontroller) | `MathUtil`, `Angles` | none |
| [`MotorSetup`, `Hw`](#motorsetup-and-hw) | the SDK `HardwareMap` | none |
| [`BulkReads`](#bulkreads) | the SDK `LynxModule` | none (it sets the hubs' mode) |
| [`LoopTimer`](#looptimer) | nothing | none |
| [`MatchTimer`](#matchtimer) | the SDK `Gamepad` (for buzzes) | none |
| [The finders and the Hardware Check](#the-finders-and-the-hardware-check) | their own OpModes | the finders write a results file when they end |

## ControllerBindings

```java
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;

ControllerBindings buttons = new ControllerBindings()
        .bindPress(Pad.G2, Btn.A, () -> claw.setPosition(0.2))   // once per press
        .bindHold(Pad.G2, Btn.DPAD_UP, () -> winch.setPower(0.5)) // every loop while held
        .bindRelease(Pad.G2, Btn.DPAD_UP, () -> winch.setPower(0));
waitForStart();
buttons.ignoreHeldButtons(gamepad1, gamepad2);   // a button held through START does not fire
while (opModeIsActive()) {
    buttons.update(gamepad1, gamepad2);
    // ... your own code
}
```

Each job is timed: one over 20 ms sets `getSlowActionWarning()` (show it if you like), for example
`G2:A button job took 40 ms: the whole robot stopped that long. ...`.
Escape hatch: `isDown(pad, btn)` reads a button yourself; you can always read `gamepad1` directly
next to it. Full list: [gamepad](../modules/gamepad.md#controllerbindings).

## SafeMotor

```java
import com.crossbotics.ftchelper.safety.SafeMotor;

SafeMotor lift = new SafeMotor(liftMotor)          // your own DcMotorEx, set up your way
        .setLimits(0, 2900)                        // encoder ticks
        .setLowerSwitch(bottom::isPressed)
        .setSlowZone(200, 0.25);
while (opModeIsActive()) {
    lift.update();                                 // first: reads the encoder and switches
    lift.setPower(-gamepad2.left_stick_y);         // limits applied
}
lift.stop();
```

`update()` reads the encoder and the switches fresh each loop (later reads within 3 ms reuse them, so a
reading is at most 3 ms old) and sends a power only when it changes, plus every 25 loops (every loop
while it is past a soft limit). Escape hatch: `getMotor()` gives the `DcMotorEx`. Power written straight
to it skips the limits, and lasts at most 25 loops while `update()` runs every loop: `SafeMotor` then
sends its own limited power again. For a different power on purpose, stop calling `update()` for
that motor. Details: [safety](../modules/safety.md#safemotor).

## PositionMotor

```java
import com.crossbotics.ftchelper.mechanism.PositionMotor;

PositionMotor lift = new PositionMotor("lift", liftMotor)   // your own DcMotorEx (brake, encoder reset)
        .limits(0, 2600)
        .pid(0.004, 0, 0.0002, 0.05)
        .position("LOW", 900)
        .position("HIGH", 2400);
buttons.bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));   // once per press, not every loop
// in the loop:
lift.manual(-gamepad2.left_stick_y);   // non-zero takes over; 0 holds where let go
lift.update();
```

Call `goTo` once per press: each call starts the target fresh (it resets the PID).

`PositionMotor.create(hardwareMap, name)` gets the motor for you (brake, encoder reset, plain power);
the constructor above takes the motor you set up yourself.

Depends on: without `.tunable(...)`, nothing outside the table. **With `.tunable(prefix)`** the gains
and positions become `Tunable`s: a static registry shared by the whole app, read from
`/sdcard/FIRST/ftc-helper/settings.properties` the first time (see
[what runs every loop](every-loop.md#static-state-that-lives-across-opmode-runs)). Leave it out to keep
it plain.

Escape hatches: `getSafeMotor()` (the limits layer), `getPid()` (the `PidController`; change gains
with `setGains`). Details: [mechanism](../modules/mechanism.md#positionmotor).

## PidController

```java
import com.crossbotics.ftchelper.control.PidController;

PidController pid = new PidController(0.004, 0, 0.0002);   // kP, kI, kD (kF is a 4th argument)
pid.setTolerance(20);
// in the loop:
double power = pid.calculate(target, motor.getCurrentPosition());
motor.setPower(power);
if (pid.atTarget()) { /* there */ }
```

Pure math: no hardware. `reset()` when the target jumps. `setAngleMode(true)` for headings.
Details: [control](../modules/control.md#pidcontroller).

## MotorSetup and Hw

```java
import com.crossbotics.ftchelper.hardware.Hw;
import com.crossbotics.ftchelper.hardware.MotorSetup;

DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
        .reversed().brake().resetEncoder().runWithoutEncoder().done();
TouchSensor bottom = Hw.get(hardwareMap, TouchSensor.class, "arm_bottom");   // wrong name: lists the real ones
DistanceSensor extra = Hw.tryGet(hardwareMap, DistanceSensor.class, "test"); // null if missing
```

`MotorSetup.of(motor)` sets up a motor you already have. `done()` gives the plain `DcMotorEx`.
Details: [hardware](../modules/hardware.md#motorsetup).

## BulkReads

```java
import com.crossbotics.ftchelper.hardware.BulkReads;

List<LynxModule> hubs = BulkReads.manual(hardwareMap);   // or BulkReads.auto(hardwareMap)
waitForStart();
while (opModeIsActive()) {
    BulkReads.clear(hubs);   // first line, every loop
    // ... reads
}
```

It only sets the SDK's bulk-caching mode on every hub. Details: [hardware](../modules/hardware.md#bulkreads).

## LoopTimer

```java
import com.crossbotics.ftchelper.util.LoopTimer;

LoopTimer loop = new LoopTimer();
while (opModeIsActive()) {
    loop.update();
    telemetry.addData("Loop", "%.1f ms (%.0f Hz)", loop.getAverageMs(), loop.getHz());
    // ...
    telemetry.update();
}
```

Please [send us](https://github.com/crossbotics/ftc-helper-lib/issues) your numbers: there is no
measured Control Hub loop time for the library yet.

## MatchTimer

```java
import com.crossbotics.ftchelper.match.MatchTimer;

MatchTimer timer = MatchTimer.teleOp()                 // 2:00
        .rumbleAt(20, gamepad1, gamepad2)              // both gamepads buzz at 0:20
        .cueAt(30, () -> endgame = true);              // your own job at 0:30
waitForStart();
timer.start();
while (opModeIsActive()) {
    timer.update();                                    // runs the buzzes and cues
    telemetry.addData("Time", timer.format());
}
```

Details: [match](../modules/match.md#matchtimer).

## The finders and the Hardware Check

Each is a ready-made OpMode. You add a one-line file; it needs nothing else from your code.

```java
import com.crossbotics.ftchelper.finders.MotorFinderOpMode;

@TeleOp(name = "Motor Finder", group = "Finders")
public class MotorFinder extends MotorFinderOpMode { }
```

The same for `ServoFinderOpMode`, `FlywheelFinderOpMode` and the
[Hardware Check](../modules/testing.md#hardwarecheckopmode) (`HardwareCheckOpMode`). They show the
numbers as plain code too (`PositionMotor.create(...).limits(...)`), not only `Tunables.` lines.
When a finder ends, it writes its lines to `/sdcard/FIRST/ftc-helper/finder-results.txt`. The
Drive Tuner and Shot Tuner need more: see [finders](../modules/finders.md).

## Mixing with the bigger pieces

You can also start big and step down:

- **EasyTeleOp, your own driving:** override `drive(Gamepad gamepad)` (it is called every loop
  while the tuning menu is closed), e.g. `driving().tank(-gamepad.left_stick_y, -gamepad.right_stick_y, 1);`.
- **The robot in five files, your own code for one part:** a module's `part()` gives the plain part
  inside (`lift.part()` is its `PositionMotor`; `part().getSafeMotor()` and `part().getPid()` go one
  layer deeper).
- **Your own code every loop with the five files:** `robot.addOwn(part)` (a class that `implements
  Subsystem`: updated every loop after the library's parts, and its `stop()` at STOP and at pause) or
  `robot.everyLoop(() -> ...)` (a small piece of code; no `stop()`). Call them in `define()` or in
  `Controls.java`'s `buttons(...)` ([Your own code every loop](../modules/robot.md#your-own-code-every-loop)).
  A ControlMap's own `everyLoop()` is the library's (it runs the buttons), so it cannot be overridden there.
- **`EasyTeleOp.everyLoop()`:** with a plain [EasyTeleOp](../modules/teleop.md) (not the five files):
  your own code every loop, before the parts update.
- **A second motor on a lift or arm:** no code of your own: `Lift.slide("lift_left").alsoMotorReversed("lift_right")`
  ([two motors](../modules/robot.md#two-motors-on-one-lift-or-arm)).

## When not to use it

- **Path following** (splines, following a curved path): the core library has none. Add the optional
  [pedro module](../modules/pedro.md): it plugs [Pedro Pathing](https://pedropathing.com/) into your
  `odometry(...)` and `drive(...)` lines and adds a `follow(...)` step. For
  [Road Runner](https://rr.brott.dev/), use it directly with the library's odometry
  ([`PoseTracker`](../modules/odometry.md)); a Road Runner module is planned.
- **You already have a working command framework** (your own, FTCLib, NextFTC): use the small blocks
  above (`SafeMotor`, `PidController`, `BulkReads`) inside it, not `EasyTeleOp`.
- **You need a value the library does not show:** read the SDK device yourself; every block hands
  over its raw device.

## How to pin a version

Use one tagged version for a whole event, and update between events. How:
[Keep it up to date: use a specific version](../02-keep-up-to-date.md#use-a-specific-version).
