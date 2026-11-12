# Safety module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.safety`

Learn it hands-on: [08 A lift that cannot hurt itself](../tutorials/08-safe-lift.md).

Keeps lifts, slides, arms and turrets inside their safe range. Then code can never drive them into the
frame, snap a string, or stall a motor at the end of travel. (A stalled motor pushes but cannot turn, and
it gets hot.) [`PositionMotor`](mechanism.md#positionmotor) and the
[robot in five files](../reference/parts/lift.md) use it for you. Use it directly when you control a motor
by hand. Start with tutorial 08.

| Class | Use it to |
| --- | --- |
| [`SafeMotor`](#safemotor) | run a motor with soft limits, limit switches, homing and a slow zone (each explained [below](#what-it-does-each-loop)) |
| [`SoftLimits`](#softlimits) | use the limit rule by itself (no hardware) |
| [`JamDetector`](#jamdetector) | tell when a motor is jammed (too slow or too much current, for a set time) |

For an easier start, use [`PositionMotor`](mechanism.md#positionmotor). It wraps `SafeMotor` with PID,
named positions and steps.

**Software limits are not enough on their own.** Rule R105 B (2026-27 manual) says the robot must stay
inside its size limits *without the use of software*. Inspectors check the maximum mechanical extension.
So every moving part needs a **hard stop** (a physical block). Use `SafeMotor` AND a hard stop. The
software stops the motor before it hits the block, and the block holds when the software is wrong.

---

## SafeMotor

```java
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();   // hold at 0 power, start at 0
TouchSensor bottom = hardwareMap.get(TouchSensor.class, "lift_bottom");

SafeMotor lift = new SafeMotor(liftMotor)
        .setLimits(0, 2900)               // encoder ticks
        .setLowerSwitch(bottom::isPressed)   // "how to read the switch", asked every loop
        .setSlowZone(200, 0.25);          // last 200 ticks: power down to 0.25 at the end

waitForStart();
while (opModeIsActive()) {
    lift.update();                                  // FIRST: read switches, re-zero at bottom
    lift.setPower(-gamepad2.left_stick_y);          // limits are applied for you
}
lift.stop();                                        // no power once the OpMode ends
```

### What it does each loop

1. **Soft limits** (limits in software, from the encoder count): power that would push past `min` or `max`
   becomes 0. Power that moves away from the limit always works, so the part can never get "stuck at the
   top".
2. **Limit switches:** a pressed switch stops power toward it, whatever the encoder says.
3. **Homing:** the moment the lower switch closes, the position becomes 0. This fixes encoder drift (a
   slipping belt, or a robot turned on with the lift half up). Until the first homing, the part only goes
   down, slowly ([Homing](#homing-with-a-lower-switch)).
4. **Slow zone:** close to a limit, `SafeMotor` reduces the power toward it, so the mechanism does not slam.

**Hub traffic:** `update()` reads the encoder and each switch **fresh**, every loop. Later reads in the
same loop (`getPosition()`, the PID, the limits and the telemetry) within 3 ms reuse those readings. So a
reading is at most 3 ms old, and one loop usually makes one read of each. Code that never calls `update()`
gets a reading at most 3 ms old every time it asks. `SafeMotor` sends a power only when the power changes,
and again every 25 loops. **Past a soft limit** (below `min` or above `max`), it checks and sends the power
every loop (see [Rules for use](#rules-for-use)).

**For tests only:** a test can move a pretend motor with no time passing. Without help, it then sees the
old reading. `SafeMotor.freshReadingsForTests(true)` reads the encoder and the switches every time a test
asks (no 3 ms reuse). `false` puts the normal reuse back. Robot code never needs it.

**Set the motor direction first:** positive power must move toward `max` (up or out). Check it with a low
power before you add limits.

### Methods

Setup (each one gives back the `SafeMotor`, so you can chain them):

| Method | What it does |
| --- | --- |
| `new SafeMotor(motor)` | wrap a motor |
| `setLimits(min, max)` | the safe range in ticks, measured from home (0) |
| `setSlowZone(distance, powerAtLimit)` | within `distance` ticks of a limit, power toward it goes down to `powerAtLimit` at the limit |
| `setMaxPower(max)` | the most power ever sent, 0 to 1 |
| `setLowerSwitch(reader)` | a lower limit switch. Homing uses it too |
| `setUpperSwitch(reader)` | an upper limit switch |
| `setRequireHoming(boolean)` | with a lower switch: `true` (default) = only slow downward power until homed |
| `setHomingPower(power)` | the most downward power before homing, default 0.3 |
| `alsoPowers(otherMotor)` | a second motor on the same mechanism (a lift with a motor on each side). It gets exactly the same power as this one, every time (limits and `stop()` too). `SafeMotor` reads only this motor's encoder. First set the second motor's direction, so + power moves the mechanism the same way on both. Check it with the chain off. `getPartners()` lists them. In the five files: [`alsoMotor`](../reference/parts/lift.md#two-motors-on-one-lift-or-arm) |

Every loop:

| Method | What it does |
| --- | --- |
| `update()` | **call it first in every loop.** It reads the encoder and the switches, and homes when the lower switch closes. It sends your last power again with the limits applied, so the limits act with no new `setPower` call. Every 25 loops it sends the limited power even when it did not change. While the position is past a soft limit, it sends it every loop |
| `setPower(power)` | set power, -1 to 1, with the limits applied. It gives the power it sent |
| `moveTo(target, pid)` | move toward a target with a [`PidController`](control.md#pidcontroller). It keeps the target inside the limits, and gives the power sent |
| `stop()` | power 0 |

Read:

| Method | What it gives |
| --- | --- |
| `getPosition()` | position in ticks, from home |
| `isAtLower()` / `isAtUpper()` | whether it is at or past a limit, or its switch is pressed |
| `isHomed()` | whether the lower switch or `zeroHere()` has set the zero |
| `isWaitingForHoming()` | whether it has a lower switch, needs homing, and is not homed yet |
| `getRequestedPower()` | the last power you asked for |
| `getAppliedPower()` | the last power it sent to the motor. When it is 0 while you asked for power, a limit stopped it |
| `getMotor()` | the `DcMotorEx` inside. Power you send to it skips the limits: see [Rules for use](#rules-for-use) |
| `zeroHere()` | make the current position 0 (home by hand) |

### Reading switches

`setLowerSwitch` takes "a way to read the switch". For example:

```java
lift.setLowerSwitch(touchSensor::isPressed);              // REV Touch Sensor
lift.setLowerSwitch(() -> !magnetSwitch.getState());      // REV Magnetic Limit Switch (DigitalChannel):
                                                          // it reads false when the magnet is near
```

### Homing (with a lower switch)

When the robot turns on, the encoder does not know where the mechanism is. So with a lower switch,
`SafeMotor` does not trust the position until it is **homed** (the switch was pressed once):

- Until then, only DOWNWARD power works, at most 0.3 (`setHomingPower`). `SafeMotor` does not use the soft
  limits yet.
- Drive it down until the switch is pressed. The position becomes 0, and everything works normally.
- When it is already on the switch at the first `update()`, it homes at once.

In TeleOp, the driver pushes the lift down once at the start. In autonomous, drive it down with
`setPower(-0.3)` each loop until `isHomed()` is true, with a timeout.

`setRequireHoming(false)` turns this off and trusts the start position as 0. We do not recommend it.

### No lower switch

Position 0 is wherever the motor was when the robot was turned on.
So **turn the robot on with the mechanism all the way down**.

### Rules for use

- Send ALL power for this motor through the `SafeMotor` (`setPower` or `moveTo`). A call to
  `getMotor().setPower(...)` skips the limits, but that power cannot stick. While `update()` runs every
  loop, `SafeMotor` sends its own limited power again within 25 loops (about half a second at 50 loops a
  second). It does this even when that power did not change, and every loop while it is past a soft limit.
- Call `update()` once per loop, before `setPower` or `moveTo`.

---

## SoftLimits

The limit rule by itself. `SafeMotor` uses it. Use it when you control a motor some other way.

```java
// (power you want, position now, min, max, slow zone, power at the limit, bottom switch, top switch)
double safe = SoftLimits.limit(power, position, 0, 2900, 200, 0.25, atBottom, atTop);
```

| Parameter | Meaning |
| --- | --- |
| `power` | the power you want, -1 to 1 (positive = toward max) |
| `position` | where it is now |
| `min`, `max` | the safe range |
| `slowZone` | the distance from a limit where the rule reduces power toward it (0 = none) |
| `slowZonePower` | the power allowed right at the limit, 0 to 1 |
| `atLowerSwitch`, `atUpperSwitch` | switch states (`false` when there is no switch) |

It gives the safe power.

---

## JamDetector

```java
JamDetector jam = new JamDetector(50, 0.3);       // slower than 50 ticks/s for 0.3 s = jammed
jam.setMaxCurrent(6);                             // optional: or above 6 A for 0.3 s
// every loop: (power sent, speed in ticks/s, current in amps, time now in seconds)
if (jam.update(power, motor.getVelocity(), amps, getRuntime())) { ...back it out... }
```

| Method | What it does |
| --- | --- |
| `new JamDetector(minSpeed, seconds)` | too slow for this long while powered = jammed |
| `setMinPower(p)` | below this power, the motor is not "trying" (default 0.15) |
| `setMaxCurrent(amps)` | it also counts a jam when the current is above this |
| `update(power, speed, nowSec)` / `update(power, speed, amps, nowSec)` | whether the motor is jammed now |
| `reset()` | start over after you clear a jam |

`SpinMotor` and `MotorFeeder` use it for you.

