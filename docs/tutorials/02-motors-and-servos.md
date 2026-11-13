# Tutorial 02: Motors and servos

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a program that moves an arm with the stick and opens and closes a claw with buttons.
The main track sets up motors and servos for you: [Add a lift](../add-to-robot/01-lift.md).

**You will learn:** how to get a motor and a servo from the robot configuration, how to set them up in
one readable chain, how to read an encoder, how to limit a servo to a safe range, and what to do with a
part that is not on every robot.

**You need:** one motor named `arm` (with its encoder cable plugged in), one servo named `claw`, and
Tutorial 01 done. Optional: a touch sensor named `arm_touch`.

**Full program:** [`Tut02MotorAndServo.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut02MotorAndServo.java)
**Reference:** [hardware.md](../modules/hardware.md)

---

## 1. Get and set up the motor

An **encoder** is a sensor on the motor that counts how far the shaft has turned. That count is the
**position**, in "ticks".

Without the library, you set up a motor with five separate lines, and it is easy to get them in the
wrong order. With the library:

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
- `.runWithoutEncoder()`: power goes straight to the motor. Your code can still READ the encoder.
- `.done()`: power 0, and hand back the motor.

**Order matters:** reset first, then the run mode. A reset leaves the motor in a "stopped" mode until
your code sets a run mode.

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

A servo goes to a position from 0 to 1. But your claw probably works only in a smaller range, for
example from 0.20 (fully open) to 0.75 (fully closed). Past those, the claw pushes into the frame, the
servo stalls, and it gets hot.

`.range(0.20, 0.75)` fixes that for good: after it, **0 means 0.20 and 1 means 0.75.** Your code says
`setPosition(0)` for open and `setPosition(1)` for closed, and it can never go further.

`CLAW_OPEN` and `CLAW_CLOSED` in the program are named numbers (0 and 1). `static final` means they
never change. A name says what a number means. A bare `0.0` does not.

`.done()` hands you the servo. The servo does not move until you first set a position.

### How to find the two numbers

1. Remove `.range(...)` for now.
2. Bind two buttons to move the servo in small steps (Tutorial 03 shows how). Show
   `claw.getPosition()` on telemetry.
3. Write down the position where the claw is just fully open, and where it is just fully closed.
4. Put them in `.range(open, closed)`.

When the claw closes at 0 and opens at 1, and you want it the other way, add `.reversed()`.

### The servo's whole turn

The hub tells a servo where to go with a pulse: 600 microseconds for position 0, 2400 for 1. Most FTC
servos take 500 to 2500. So 0 to 1 turns them a bit less than the box says: a goBILDA 2000 servo turns
270 degrees, not 300. To get the rest of the turn, ask for the full pulse:

```java
Servo wrist = ServoSetup.get(hardwareMap, "wrist").fullRange().done();   // 0 to 1 = 300 degrees
```

After this change, the same position points somewhere else. So do it before you find your numbers.
[More](../modules/hardware.md#full-range).

## 3. A part that might not be there

```java
TouchSensor touch = Hw.tryGet(hardwareMap, TouchSensor.class, "arm_touch");
```

When there is no device with that name, `tryGet` does not stop the program. It returns `null` (Java's
word for "nothing here"). Use it for parts that are only on some robots. **Always check for null**
before you use the part:

```java
if (touch != null) TelemetryHelper.flag(telemetry, "Touch", touch.isPressed());
```

For parts the robot cannot work without, use `Hw.get(...)` (no "try"). It stops with a clear message.

## 4. Buttons and the loop

```java
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.A, () -> claw.setPosition(CLAW_OPEN))
        .bindPress(Pad.G1, Btn.B, () -> claw.setPosition(CLAW_CLOSED))
        .bindPress(Pad.G1, Btn.X, () -> MotorSetup.of(arm).resetEncoder().runWithoutEncoder().done());
```

X shows another use of `MotorSetup`. `MotorSetup.of(arm)` works on a motor you already have. Here it
zeroes the encoder again. A reset stops the motor, so the chain sets the run mode again right after
it. `done()` sets power 0 until the next loop sets the stick power.

In the loop:

```java
double power = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 1) * 0.5;
arm.setPower(power);

TelemetryHelper.motor(telemetry, "Arm", arm);
TelemetryHelper.servo(telemetry, "Claw", claw);
```

- `-gamepad1.left_stick_y`: the SDK (the FTC software your code runs on) says "stick up" is negative.
  The minus makes it positive.
- `shape(..., 0.05, 1)` ignores tiny stick noise. `* 0.5` limits the arm to half power while you test.
- `TelemetryHelper.motor` shows power, encoder position, and speed on one line.
- `TelemetryHelper.servo` shows the position you last asked for. A servo cannot report where it
  really is.

In `finally`, put `arm.setPower(0);`.

## Run it

**Safety:** this arm has no limits in code yet. Its hard stop is the metal or plastic end of its travel,
and only you stop the arm there. Know where that stop is, keep hands clear, and use short, gentle pushes.

1. Put the arm in its rest position. Press INIT and ▶ (start on the Driver Hub). Position shows 0.
2. Push the stick up gently. The arm moves up and the position goes UP.
   **If the position goes down when the arm goes up**, add `.reversed()` to the motor chain.
   Later tutorials need positive power to move the mechanism in its "positive" direction.
3. A and B open and close the claw, inside the range you set.
4. X sets the position back to 0.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Motor does not move, position changes | The motor is on the wrong port, or the encoder cable is on a different port from the power cable. |
| Position stays 0 while the arm moves | The encoder cable is not plugged in, or it is in another port. |
| Servo buzzes and gets warm at one end | The range goes past the mechanical end. Make the range smaller. |
| Servo does not move at all | The servo is on the wrong port, or the configuration says "Continuous Rotation" for it (it must be "Servo"). |

## Try this

1. Change the motor to `.coast()`. Raise the arm and let go of the stick. Watch the arm: this is why
   arms and lifts use `.brake()`.
2. Add a third claw position, "half open" at 0.5, on the Y button.
3. Make the stick control the arm at full power only while the driver holds the right bumper.
   (Hint: `gamepad1.right_bumper ? 1.0 : 0.5`.)

## Next

[Tutorial 03: Buttons](03-buttons.md)
