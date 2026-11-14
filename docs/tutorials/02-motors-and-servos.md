# Tutorial 02: Motors and servos

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, `Robot.java` gets and sets up each motor and servo for you: [Add a lift](../add-to-robot/01-lift.md),
[Add a claw](../add-to-robot/03-claw.md). Do it by hand here to see what that setup does, or for a part
that has no module.

**You will build:** a program that moves an arm with the stick and opens and closes a claw with buttons.

**You need:** one motor named `arm` (with its encoder cable plugged in), one servo named `claw`.
Optional: a touch sensor named `arm_touch`.

**Full program:** [`Tut02MotorAndServo.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut02MotorAndServo.java)

---

## 1. Get and set up the motor

An **encoder** is a sensor on the motor that counts how far the shaft has turned. That count is the
**position**, in "ticks". Without the library, a motor needs five separate lines in the right order.
With the library, it is one chain:

```java
DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
        .brake()
        .resetEncoder()
        .runWithoutEncoder()
        .done();
```

- `get(hardwareMap, "arm")`: find the motor named "arm" in the active configuration. When the name is
  wrong, the error lists the names that do exist.
- `.brake()`: at power 0 the motor resists turning, so the arm does not fall.
- `.resetEncoder()`: the encoder count becomes 0 **here**. So start with the arm in a known place.
- `.runWithoutEncoder()`: power goes straight to the motor. Your code can still READ the encoder.
- `.done()`: power 0, and hand back the motor.

**Order matters:** reset first, then the run mode. A reset leaves the motor in a "stopped" mode until
your code sets a run mode.

## 2. Get and set up the servo

```java
Servo claw = ServoSetup.get(hardwareMap, "claw")
        .range(0.20, 0.75)
        .done();
```

A servo goes to a position from 0 to 1. But your claw works only in a smaller range, for example from
0.20 (fully open) to 0.75 (fully closed). Past those, the claw pushes into the frame, the servo stalls,
and it gets hot. After `.range(0.20, 0.75)`, **0 means 0.20 and 1 means 0.75.** So `setPosition(0)` opens,
`setPosition(1)` closes, and the servo can never go further.

`.done()` hands you the servo. The servo does not move until you first set a position.
To swap which end is 0, add `.reversed()`.

**Find the two numbers** with the [Servo Finder](../tuning/03-servo-finder.md). To use the servo's whole
turn (`.fullRange()`), set it before you find them: [Full range](../modules/hardware.md#full-range).

## 3. A part that might not be there

```java
TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");
```

When there is no device with that name, `tryGet` returns `null` (Java's word for "nothing here") and the
program goes on. Use it for parts that are only on some robots. **Always check for null** before you use
the part:

```java
if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
```

For parts the robot cannot work without, use `Hw.get(...)`. It stops with a clear message.

## 4. Buttons and the loop

```java
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
        .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
        .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());
```

`CLAW_OPEN` and `CLAW_CLOSED` are named numbers (0 and 1): a name says what a number means.
`MotorSetup.of(arm)` works on a motor you already have. Here X zeroes the encoder again, then sets the
run mode again, because a reset stops the motor.

In the loop:

```java
double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;
arm.setPower(power);

TelemetryHelper.motor(telemetry, "Arm", arm);
TelemetryHelper.servo(telemetry, "Claw", claw);
```

- The minus: the SDK says "stick up" is negative.
- `shape(..., 0.05, 1)` ignores tiny stick noise. `* 0.5` limits the arm to half power while you test.
- `TelemetryHelper.motor` shows power, encoder position, and speed on one line.
- `TelemetryHelper.servo` shows the position you last asked for. A servo cannot report its true
  position.

In `finally`, put `arm.setPower(0);`.

## Run it

**Safety:** this arm has no limits in code yet. Its hard stop is the metal or plastic end of its travel,
and only you stop the arm there. Know where that stop is, keep hands clear, and use short, gentle pushes.

1. Put the arm in its rest position. Press INIT and ▶. Position shows 0.
2. Push the stick up gently. The arm moves up and the position goes UP.
   **If the position goes down when the arm goes up**, add `.reversed()` to the motor chain.
   Later tutorials need positive power to move the mechanism in its "positive" direction.
3. A and B open and close the claw, inside the range you set. X sets the position back to 0.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Position stays 0 while the arm moves | The encoder cable is not plugged in, or it is in another port. |
| Servo buzzes and gets warm at one end | The range goes past the mechanical end. Make the range smaller. |
| Servo does not move at all | The servo is on the wrong port, or the configuration says "Continuous Rotation" for it (it must be "Servo"). |

More wiring problems: [Wiring checks](../tuning/02-wiring-checks.md).

## Try this

1. Change the motor to `.coast()`. Raise the arm and let go of the stick. Watch the arm: this is why
   arms and lifts use `.brake()`.
2. Add a third claw position, "half open" at 0.5, on the Y button.
3. Make the stick control the arm at full power only while the driver holds the right bumper.
   (Hint: `gamepad1.right_bumper ? 1.0 : 0.5`.)

---

**Next:** [Tutorial 03: Buttons](03-buttons.md).
**Look it up:** [hardware.md](../modules/hardware.md).
