# Control module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.control`

Learn it hands-on: [07 PID lift](../tutorials/07-pid-lift.md), [11 Driver assists](../tutorials/11-driver-assists.md).

These classes do the math that turns "where I want to be" into a motor power. Use them when you build a
mechanism by hand: a lift that holds a height, a turn to a heading, a smooth start. The ready-made mechanisms
([`PositionMotor`](mechanism.md#positionmotor), the [robot in five files](../reference/robot-java.md)) already use them,
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
The power is the sum of up to four parts. Each part has a number you tune, called a **gain** (kP, kI, kD, kF):

- **P (proportional):** power = kP x error. Far away = push hard. Close = push gently.
- **I (integral):** adds up the error over time. It fixes a small error that P alone never closes
  (for example gravity pulling a lift down). Start with 0.
- **D (derivative):** looks at how fast the error shrinks and pushes back. This keeps the mechanism from
  going past the target (overshooting).
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

The controller remembers past errors. So make it once, in INIT, and not in the loop.
With a [`SafeMotor`](safety.md), use `safeLift.moveTo(target, liftPid)` instead. It does the same, and it also keeps the limits.

### Methods

| Method | What it does |
| --- | --- |
| `new PidController(kP, kI, kD)` | make one, no feedforward |
| `new PidController(kP, kI, kD, kF)` | make one, with a fixed feedforward push |
| `calculate(target, measured)` | **call every loop.** It gives the power, inside the output limits. |
| `setGains(kP, kI, kD, kF)` | change the gains (for live tuning) |
| `setOutputLimits(min, max)` | limit the output. Default -1 to 1. |
| `setIntegralLimit(max)` | the most power the I part may add. This stops "windup" (see below) |
| `setTolerance(value)` | how close counts as "at target" |
| `setAngleMode(true)` | for headings in degrees: the error takes the short way around (170 to -170 = +20) |
| `atTarget()` | whether the last error was within the tolerance |
| `getLastError()` | the last error (target minus measured) |
| `reset()` | clears the memory. **Call it when you set a new target.** |
| `getKP()`, `getKI()`, `getKD()`, `getKF()` | read the gains |

Each `set...` method gives back the controller. So you can chain them: write one after the other, as in the example.

### How to tune (do it in this order)

1. Set kI = 0 and kD = 0. Start with a small kP: `1 / (a big error)`. For a lift with 2000 ticks of travel,
   try 0.002.
2. Raise kP until the mechanism gets close to the target fast. When it shakes back and forth
   around the target ("oscillates"), kP is too big: go back a step.
3. When it goes past the target (overshoots), add a little kD. Start at kP / 20 and raise it slowly.
4. When it stops a bit short and stays there (often on a lift), add a small kF (lift). Or add
   a small kI with `setIntegralLimit(0.2)`.
5. Write the final numbers in your design log.

**Integral windup:** when something blocks the mechanism (a game piece, for example), the I part keeps
growing. When the mechanism comes free, this big build-up throws it past the target. `setIntegralLimit` caps it.

### Turning with PID

```java
PidController headingPid = new PidController(0.012, 0, 0.003).setAngleMode(true);
// IMU headings are counterclockwise-positive, so this power is too (positive = turn left)
double turnCcw = headingPid.calculate(targetHeading, ImuSetup.headingDeg(imu));
drive.drive(0, 0, -turnCcw);   // the drive's turn is clockwise-positive, so flip the sign
```

---

## SlewRateLimiter

A slew rate limiter stops a value from changing too fast. When the driver slams a stick, it keeps a tall robot
from tipping and a heavy mechanism from jerking.

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
| `calculate(input)` | **call every loop.** It moves toward the input, no faster than the limit. The first call gives the current value (0 unless you used `reset`). |
| `reset(value)` | jumps straight to a value (for example 0 when you stop) |
| `getValue()` | the last output |

Tip: let it slow down faster than it speeds up (`fall` > `rise`), so the robot still stops fast.

## Feedforward

A PID reacts only after an error builds up. **Feedforward** works out the power a motor needs for a speed
before there is an error. It uses numbers that describe how the motor behaves. The two work well together.
The [Characterizer](finders.md#characterizeropmode) measures these numbers on your robot.

```
power = kS x sign(speed) + kV x speed + kA x speeding-up + kG
```

- **kS:** the power to start moving (to beat friction).
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

## FlywheelBangBang

The arithmetic of bang-bang speed control for a flywheel, with no motor in it. Full power well below the
speed wins back a shot's speed as fast as the motor can. It never gives negative power, so it never fights
the wheel. [Flywheel](launcher.md#speed-control) runs it for you.

```java
FlywheelBangBang law = new FlywheelBangBang().feedforward(0.4, 0.00205).thresholds(200, 75, 50);
double power = law.calculate(targetRpm, measuredRpm, batteryVolts, nowSec, false);
```

| Method | What it does |
| --- | --- |
| `.feedforward(ks, kv)` | holding volts = ks + kv x RPM. `Feedforward` uses other units: do not copy its numbers |
| `.thresholds(full, coast, hysteresis)` | full power starts `full` below, coasting `coast` above. Each ends `hysteresis` later |
| `.maxPower(p)` | the most power, 0.3 to 1 |
| `calculate(target, rpm, volts, now, late)` | the power. Too fast always gives 0. `late` = no full power this time. A bad number or a target of 0 or less gives 0 |
| `reset()`, `targetChanged()` | forgets everything; judges a new target afresh |
| `getZone()` | OFF, FULL, NEAR or COAST |
| `getKs()`, `getKv()`, `getFullBelowRpm()`, `getCoastAboveRpm()`, `getHysteresisRpm()`, `getMaxPower()` | the numbers in use |

Below half the target, the power rises by `SPIN_UP_RISE_PER_SEC` (4 a second) at most. That eases a start
from rest. The motor must be on FLOAT.
