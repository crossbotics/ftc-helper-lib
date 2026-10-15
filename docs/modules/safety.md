# Safety module

Package: `io.github.jasoncross.ftchelper.safety`

Keeps lifts, slides, arms and turrets inside their safe range, so code can never drive them
into the frame, snap a string, or stall a motor at the end of travel.

| Class | Use it to |
| --- | --- |
| [`SafeMotor`](#safemotor) | a motor with soft limits, limit switches, homing and a slow zone |
| [`SoftLimits`](#softlimits) | the limit rule by itself (no hardware) |

**Software limits are not enough on their own.** FTC rules say a robot must stay inside its size
limits at full extension, and inspectors check it. Every moving part needs a **hard stop** (a
physical block). Use `SafeMotor` AND a hard stop: the software stops the motor before it hits
the block; the block holds if the software is wrong.

---

## SafeMotor

```java
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();
TouchSensor bottom = hardwareMap.get(TouchSensor.class, "lift_bottom");

SafeMotor lift = new SafeMotor(liftMotor)
        .setLimits(0, 2900)               // encoder ticks
        .setLowerSwitch(bottom::isPressed)
        .setSlowZone(200, 0.25);

waitForStart();
while (opModeIsActive()) {
    lift.update();                                  // FIRST: read switches, re-zero at bottom
    lift.setPower(-gamepad2.left_stick_y);          // limits are applied for you
}
lift.stop();
```

### What it does each loop

1. **Soft limits:** power that would push past `min` or `max` becomes 0.
   Power that moves away from the limit always works, so you can never get "stuck at the top".
2. **Limit switches:** a pressed switch stops power toward it, whatever the encoder says.
3. **Homing:** when the lower switch is pressed, the position becomes 0. This fixes encoder drift
   (a slipping belt, or a robot turned on with the lift half up). Until the first homing it only goes
   down, slowly ([Homing](#homing-with-a-lower-switch)).
4. **Slow zone:** close to a limit, power toward it is reduced, so the mechanism does not slam.

**Set the motor direction first:** positive power must move toward `max` (up / out).
Check it with a low power before you add limits.

### Methods

Setup (each returns the `SafeMotor`, so you can chain them):

| Method | What it does |
| --- | --- |
| `new SafeMotor(motor)` | wrap a motor |
| `setLimits(min, max)` | the safe range in ticks, measured from home (0) |
| `setSlowZone(distance, powerAtLimit)` | within `distance` ticks of a limit, power toward it goes down to `powerAtLimit` at the limit |
| `setMaxPower(max)` | the most power ever sent, 0 to 1 |
| `setLowerSwitch(reader)` | a lower limit switch. Also used for homing. |
| `setUpperSwitch(reader)` | an upper limit switch |
| `setRequireHoming(boolean)` | with a lower switch: `true` (default) = only slow downward power until homed |
| `setHomingPower(power)` | the most downward power before homing, default 0.3 |

Every loop:

| Method | What it does |
| --- | --- |
| `update()` | **call first in every loop.** Reads the switches and homes at the lower switch. |
| `setPower(power)` | set power, -1 to 1, with the limits applied. Returns the power really sent. |
| `moveTo(target, pid)` | move toward a target with a [`PidController`](control.md#pidcontroller). The target is kept inside the limits. Returns the power sent. |
| `stop()` | power 0 |

Read:

| Method | What it gives |
| --- | --- |
| `getPosition()` | position in ticks, from home |
| `isAtLower()` / `isAtUpper()` | at or past a limit, or its switch is pressed |
| `isHomed()` | true after the lower switch or `zeroHere()` set the zero |
| `isWaitingForHoming()` | true while it has a lower switch, needs homing, and is not homed yet |
| `getRequestedPower()` | the last power you asked for |
| `getAppliedPower()` | the last power really sent. 0 while you asked for power = a limit stopped it. |
| `getMotor()` | the `DcMotorEx` inside |
| `zeroHere()` | make the current position 0 (home by hand) |

### Reading switches

`setLowerSwitch` takes "a way to read the switch". Examples:

```java
lift.setLowerSwitch(touchSensor::isPressed);              // REV Touch Sensor
lift.setLowerSwitch(() -> !magnetSwitch.getState());      // REV Magnetic Limit Switch (DigitalChannel):
                                                          // it reads false when the magnet is near
```

### Homing (with a lower switch)

When the robot turns on, the encoder does not know where the mechanism is. So with a lower switch,
`SafeMotor` does not trust the position until it is **homed** (the switch was pressed once):

- Until then, only DOWNWARD power works, at most 0.3 (`setHomingPower`), and the soft limits are not used.
- Drive it down until the switch is pressed. The position becomes 0, and everything works normally.
- If it is already on the switch at the first `update()`, it is homed at once.

In TeleOp, the driver just pushes the lift down once at the start. In autonomous, drive it down
with `setPower(-0.3)` each loop until `isHomed()` is true (with a timeout).

`setRequireHoming(false)` turns this off and trusts the start position as 0 (not recommended).

### No lower switch

Position 0 is wherever the motor was when the robot was turned on.
So **turn the robot on with the mechanism all the way down**.

### Rules for use

- Send ALL power for this motor through the `SafeMotor` (`setPower` or `moveTo`). Calling
  `getMotor().setPower(...)` skips the limits.
- Call `update()` once per loop, before `setPower` or `moveTo`.

---

## SoftLimits

The limit rule by itself. `SafeMotor` uses it. Use it if you control a motor some other way.

```java
double safe = SoftLimits.limit(power, position, 0, 2900, 200, 0.25, atBottom, atTop);
```

| Parameter | Meaning |
| --- | --- |
| `power` | the power you want, -1 to 1 (positive = toward max) |
| `position` | where it is now |
| `min`, `max` | the safe range |
| `slowZone` | distance from a limit where power toward it is reduced (0 = none) |
| `slowZonePower` | power allowed right at the limit, 0 to 1 |
| `atLowerSwitch`, `atUpperSwitch` | switch states (`false` if there is no switch) |

Returns the safe power.
