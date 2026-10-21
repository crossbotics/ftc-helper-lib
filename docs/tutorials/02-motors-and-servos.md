# Tutorial 02: Motors and servos

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will learn:** how to get a motor and a servo from the robot configuration and set them up
in one readable chain, how to read an encoder, how to limit a servo to a safe range, and how to
handle a part that is not on every robot.

**You need:** one motor named `arm` (with its encoder cable plugged in), one servo named `claw`.
Optional: a touch sensor named `arm_touch`. Tutorial 01.

**Full program:** [`Tut02MotorAndServo.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut02MotorAndServo.java)
**Reference:** [hardware.md](../modules/hardware.md)

---

## 1. Get and set up the motor

Without the library, setting up a motor is five separate lines that are easy to get in the
wrong order. With it:

```java
DcMotorEx arm = MotorSetup.get(hardwareMap, "arm")
        .brake()
        .resetEncoder()
        .runWithoutEncoder()
        .done();
```

Read it top to bottom:

- `get(hardwareMap, "arm")`: find the motor named "arm" in the active configuration.
- `.brake()`: at power 0 the motor resists turning, so the arm does not fall.
- `.resetEncoder()`: the encoder count becomes 0 **here**. So start with the arm in a known place.
- `.runWithoutEncoder()`: power goes straight to the motor. (We can still READ the encoder.)
- `.done()`: power 0, and hand back the motor.

**Order matters:** reset first, then the run mode. A reset leaves the motor in a "stopped" mode
until a run mode is set.

### What if the name is wrong?

Change `"arm"` to `"arn"` and run. The Driver Station shows:

```
No DcMotorEx named "arn" in the active robot configuration. Names of this type that do exist:
[arm, front_left, ...]. Check the spelling and the active configuration on the Driver Station.
```

The list of real names usually shows the mistake at once. Change it back.

## 2. Get and set up the servo

```java
Servo claw = ServoSetup.get(hardwareMap, "claw")
        .range(0.20, 0.75)
        .done();
```

A servo goes to a position from 0 to 1. But your claw probably only works between, say,
0.20 (fully open) and 0.75 (fully closed). Past those, it pushes into the frame, stalls, and gets hot.

`.range(0.20, 0.75)` fixes that for good: after it, **0 means 0.20 and 1 means 0.75.** Your
code says `setPosition(0)` for open and `setPosition(1)` for closed, and can never go further.

### How to find the two numbers

1. Temporarily remove `.range(...)`.
2. Bind two buttons to move the servo in small steps (try it: Tutorial 03 shows how), and show
   `claw.getPosition()` on telemetry.
3. Write down the position where the claw is just fully open and just fully closed.
4. Put them in `.range(open, closed)`.

If the claw closes at 0 and opens at 1 and you want it the other way, add `.reversed()`.

## 3. A part that might not be there

```java
TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");
```

`tryGet` returns `null` (nothing) if there is no device with that name, instead of stopping.
Use it for parts that are only on some robots. **Always check for null** before using it:

```java
if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
```

Use `Hw.get(...)` (no "try") for parts the robot cannot work without: it stops with a clear message.

## 4. Buttons and the loop

```java
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
        .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
        .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());
```

X shows another use of `MotorSetup`: `MotorSetup.of(arm)` works on a motor you already have,
here to zero the encoder again.

In the loop:

```java
double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;
arm.setPower(power);

TelemetryHelper.motor(telemetry, "Arm", arm);
TelemetryHelper.servo(telemetry, "Claw", claw);
```

- `-gamepad1.left_stick_y`: the minus is needed, because the SDK says "stick up" is negative.
- `shape(..., 0.05, 1)` ignores tiny stick noise. `* 0.5` limits the arm to half power while you test.
- `TelemetryHelper.motor` shows power, encoder position, and speed on one line.

In `finally`: `arm.setPower(0);`.

## Run it

1. With the arm in its rest position, INIT and START. Position shows 0.
2. Push the stick up gently. The arm moves up and the position goes UP.
   **If the position goes down when the arm goes up**, add `.reversed()` to the motor chain.
   Positive power must move the mechanism in its "positive" direction. Later tutorials depend on it.
3. A and B open and close the claw, inside the range you set.
4. X sets the position back to 0.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Motor does not move, position changes | Wrong motor port, or the encoder cable is on a different port from the power cable. |
| Position stays 0 while the arm moves | Encoder cable not plugged in, or plugged into another port. |
| Servo buzzes and gets warm at one end | The range goes past the mechanical end. Make the range smaller. |
| Servo does not move at all | Wrong port, or the servo is set to "Continuous Rotation" in the configuration (it must be "Servo"). |

## Try this

1. Change the motor to `.coast()` and let go of the stick with the arm raised. What happens?
   Now you know why arms and lifts use `.brake()`.
2. Add a third claw position, "half open" at 0.5, on the Y button.
3. Make the stick control the arm at full power only while the right bumper is held.
   (Hint: `gamepad1.right_bumper ? 1.0 : 0.5`.)

## Next

[Tutorial 03: Buttons](03-buttons.md)
