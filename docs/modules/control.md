# Control module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.control`

Learn it hands-on: [07 PID lift](../tutorials/07-pid-lift.md), [11 Driver assists](../tutorials/11-driver-assists.md).

The math that turns "where I want to be" into a motor power. Use it when you build a mechanism by
hand: a lift that holds a height, a turn to a heading, a smooth start. The ready-made mechanisms
([`PositionMotor`](mechanism.md#positionmotor), the [robot in five files](robot.md)) already use it,
so you only set their numbers. To learn what PID means, start with tutorial 07.

| Class | Use it to |
| --- | --- |
| [`PidController`](#pidcontroller) | move a mechanism to a target and hold it there |
| [`SlewRateLimiter`](#slewratelimiter) | stop a value from changing too fast (a ramp) |
| [`Feedforward`](#feedforward) | work out the power a motor needs for a speed, from measured numbers |

---

## PidController

### What a PID controller does

You have a **target** (lift at 1500 ticks) and a **measurement** (lift is at 900 now). Ticks are
counts from the motor's **encoder**, a sensor that counts how far the motor has turned.
The difference is the **error** (600). Each loop, the controller turns the error into a power.
It adds up to four parts. Each part has a number you tune, called a **gain** (kP, kI, kD, kF):

- **P (proportional):** power = kP x error. Far away = push hard. Close = push gently.
- **I (integral):** adds up error over time. Fixes a small error that P alone never closes
  (for example gravity pulling a lift down). Start with 0.
- **D (derivative):** looks at how fast the error shrinks and pushes back, so you do not overshoot.
- **F (feedforward):** a fixed extra push every loop, for example 0.1 to hold an arm up against gravity.

### Example: a lift

```java
PidController liftPid = new PidController(0.005, 0, 0.0002)   // kP, kI, kD
        .setOutputLimits(-0.5, 1.0)     // go down at half power at most
        .setTolerance(15);              // within 15 ticks counts as "there"

// every loop: (where we want to be, where the encoder says we are) -> a power
double power = liftPid.calculate(liftTarget, lift.getCurrentPosition());
lift.setPower(power);   // send it every loop: that is what holds the lift up
```

Make the controller ONCE (in INIT), not in the loop: it remembers past errors.
With a [`SafeMotor`](safety.md), use `safeLift.moveTo(target, liftPid)` instead: it does the same and keeps the limits.

### Methods

| Method | What it does |
| --- | --- |
| `new PidController(kP, kI, kD)` | make one, no feedforward |
| `new PidController(kP, kI, kD, kF)` | make one, with a fixed feedforward push |
| `calculate(target, measured)` | **call every loop.** Returns the power, inside the output limits. |
| `setGains(kP, kI, kD, kF)` | change the gains (for live tuning) |
| `setOutputLimits(min, max)` | limit the output. Default -1 to 1. |
| `setIntegralLimit(max)` | the most power the I part may add (stops "windup": see below) |
| `setTolerance(value)` | how close counts as "at target" |
| `setAngleMode(true)` | for headings in degrees: the error takes the short way around (170 to -170 = +20) |
| `atTarget()` | true if the last error was within the tolerance |
| `getLastError()` | the last error (target minus measured) |
| `reset()` | clear the memory. **Call it when you set a new target.** |
| `getKP()`, `getKI()`, `getKD()`, `getKF()` | read the gains |

The `set...` methods return the controller, so you can chain them.

### How to tune (do it in this order)

1. Set kI = 0, kD = 0. Start kP small: `1 / (a big error)`. For a lift with 2000 ticks of travel,
   try 0.002.
2. Raise kP until the mechanism gets close to the target quickly. If it shakes back and forth
   ("oscillates") around the target, kP is too big: go back a step.
3. If it overshoots, add a little kD (start at kP / 20) and raise it slowly.
4. If it stops a bit short and stays there (often on a lift), add a small kF (lift) or
   a small kI with `setIntegralLimit(0.2)`.
5. Write the final numbers in your design log.

**Integral windup:** if the mechanism is stuck (blocked by a game piece), the I part keeps
growing. When it is free, the big build-up throws it past the target. `setIntegralLimit` caps it.

### Turning with PID

```java
PidController headingPid = new PidController(0.012, 0, 0.003).setAngleMode(true);
// IMU headings are counterclockwise-positive, so this power is too (positive = turn left)
double turnCcw = headingPid.calculate(targetHeading, ImuSetup.headingDeg(imu));
drive.drive(0, 0, -turnCcw);   // the drive's turn is clockwise-positive, so flip the sign
```

---

## SlewRateLimiter

Stops a value from changing too fast. Use it so a tall robot does not tip, or a heavy
mechanism does not jerk, when the driver slams a stick.

```java
SlewRateLimiter forwardRamp = new SlewRateLimiter(2.0, 4.0);   // speed up 2/s, slow down 4/s
// every loop (minus: the SDK gives stick UP as negative, and up should mean forward):
double forward = forwardRamp.calculate(-gamepad1.left_stick_y);
```

With rate 2.0, going from 0 to full power takes 0.5 seconds.

| Method | What it does |
| --- | --- |
| `new SlewRateLimiter(rate)` | same limit both ways, per second |
| `new SlewRateLimiter(rise, fall)` | `rise` = away from zero (speeding up), `fall` = toward zero (slowing down) |
| `calculate(input)` | **call every loop.** Moves toward the input, no faster than the limit. The first call returns the current value (0 unless you used `reset`). |
| `reset(value)` | jump straight to a value (e.g. 0 when you stop) |
| `getValue()` | the last output |

Tip: let it slow down faster than it speeds up (`fall` > `rise`), so the robot still stops quickly.

## Feedforward

**Feedforward** means: work out the power a motor needs for a speed BEFORE there is an error, from
how the motor behaves. A PID only reacts once an error has built up. The two work well together.
The [Characterizer](finders.md#characterizeropmode) measures the numbers on your robot.

```
power = kS x sign(speed) + kV x speed + kA x speeding-up + kG
```

- **kS:** the power just to start moving (to beat friction).
- **kV:** the extra power for each count per second of speed.
- **kA:** the extra power for speeding up (per count per second, per second).
- **kG:** the power that holds a lift up against gravity.

Speeds are in encoder counts per second, like `DcMotorEx.getVelocity()`. Powers are for a 12.8 V battery.

```java
Feedforward ff = new Feedforward(0.06, 0.00037, 0.00004);        // from the Characterizer
motor.setPower(ff.power(1500, 0, battery.getVoltage()));          // 1500 counts/s, scaled for the battery
// a lift: feedforward for the move plus a PID for what is left over
lift.setPower(liftFf.power(targetSpeed, 0) + pid.calculate(target, lift.getCurrentPosition()));
```

| Method | What it does |
| --- | --- |
| `new Feedforward(kS, kV, kA)` / `(kS, kV, kA, kG)` | the numbers (kG for a lift) |
| `power(speed, accel)` | the power at 12.8 V |
| `power(speed, accel, batteryVolts)` | the same, scaled for the battery now (not limited to -1..1) |
| `topSpeed()` | the speed at full power |

