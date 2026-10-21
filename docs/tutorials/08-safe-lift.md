# Tutorial 08: A lift that cannot hurt itself

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will learn:** how `SafeMotor` keeps a mechanism inside its range with soft limits, a limit
switch, homing and a slow zone; and the standard pattern for "set heights with buttons, plus the
stick to take over by hand".

**You need:** the lift from Tutorial 07 (motor `lift`, positive = up), its PID gains, and
ideally a touch sensor at the bottom named `lift_bottom`. Tutorials 01-03 and 07.

**Full program:** [`Tut08SafeLift.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut08SafeLift.java)
**Reference:** [safety.md](../modules/safety.md)

---

## 1. What can go wrong with a lift

- Driven past the top: string snaps, slide pulls apart, robot breaks the 29 in height rule.
- Driven into the bottom: motor stalls, gets hot, belt skips.
- Encoder "drifts" (belt skips, robot turned on with the lift up): now every height is wrong.

`SafeMotor` handles all three in software. **It does not replace hard stops.** Rule R105 B says
the robot must stay inside its size limits without software. Software stops the motor before it
hits the block; the block holds if the software is wrong.

## 2. Wrap the motor

```java
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();
SafeMotor lift = new SafeMotor(liftMotor)
        .setLimits(BOTTOM, MAX)       // 0 to 2600 ticks
        .setSlowZone(250, 0.2)        // within 250 ticks of an end, slow down to 20%
        .setMaxPower(0.9);
```

- **Soft limits:** power that pushes past 0 or 2600 becomes 0. Power AWAY from the limit always
  works, so the lift can never get "stuck at the top".
- **Slow zone:** close to an end, power toward it is reduced, so it does not slam.
- **Max power:** the most it ever gets.

Set MAX a little below the real top, so the soft limit stops it before the hard stop.

## 3. The bottom switch and homing

```java
TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
if (bottom != null) lift.setLowerSwitch(bottom::isPressed);
```

With a lower switch:

- The switch always stops downward power, whatever the encoder says.
- Pressing it sets the position to 0 (**homing**). Encoder drift is fixed every time the lift
  reaches the bottom.
- **Until it is homed the first time, the lift only goes DOWN, slowly (30%).** The position is
  not known yet, so up is not safe. The driver pushes it down once at the start; if it is already
  on the switch, it homes at once.

Without a switch, position 0 is wherever the lift was when the robot turned on: always turn on
with it at the bottom.

## 4. The pattern: set heights + hand control

```java
private Integer target = null;   // null = by hand
...
.bindPress(Pad.G2, Btn.A, () -> goTo(BOTTOM, pid))
.bindPress(Pad.G2, Btn.Y, () -> goTo(TOP, pid))
.bindPress(Pad.G2, Btn.B, () -> target = null)      // stop holding
```

And every loop:

```java
lift.update();                                      // FIRST: switches and homing
controls.update(gamepad1, gamepad2);

double stick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
if (stick != 0 || lift.isWaitingForHoming()) target = null;   // the stick takes over
if (target == null) {
    lift.setPower(stick);
} else {
    lift.moveTo(target, pid);
}
```

- `target` is an `Integer` (not `int`) so it can be `null`, meaning "no set height, by hand".
- **Touching the stick cancels the set height.** The driver is always in control.
- `moveTo(target, pid)` is `pid.calculate` + `setPower`, with the limits applied and the target
  kept inside them.
- `lift.update()` must come first every loop, and **every power for this motor goes through the
  SafeMotor**. Calling `liftMotor.setPower(...)` directly skips the limits.

## 5. Telemetry that explains itself

```java
if (lift.isWaitingForHoming()) telemetry.addLine("Push the lift DOWN to the switch to home it.");
TelemetryHelper.value(telemetry, "Asked power", lift.getRequestedPower(), 2);
TelemetryHelper.value(telemetry, "Sent power", lift.getAppliedPower(), 2);
```

When "Asked" is 0.5 and "Sent" is 0, a limit stopped it. This answers "why won't the lift move?"
in two seconds.

## Run it

1. Start with the lift a little UP (with a switch). Telemetry asks you to home it. Stick up:
   nothing. Stick down: it goes down slowly, reaches the switch, position becomes 0.
2. Stick up: it moves, then slows near the top and stops at MAX. Stick down: it moves away at once.
3. Y: goes to TOP. Touch the stick: the button target is cancelled, you are in control.
4. Hold the stick down at the bottom: "Asked -0.5, Sent 0".

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Only goes down, slowly | Not homed yet. Push it down to the switch. |
| Will not go down at all | It thinks it is at 0 or the switch is pressed. Check `At bottom` and the switch wiring. |
| Stops early at the top | MAX too low, or the position drifted. Re-home at the bottom. |
| Goes past the top | Positive power is not "up" (reverse the motor), or MAX is wrong. STOP. |
| Hums at the bottom when holding BOTTOM | The F push in the PID. Normal; or use `target = null` at the bottom. |

## Try this

1. Make B "go to bottom, then let go": after reaching BOTTOM (`pid.atTarget()`), set `target = null`.
2. Add an upper limit switch with `setUpperSwitch(...)`.
3. Let the driver re-home on demand: a button that sets `target = null` and drives down at -0.3
   until `lift.isAtLower()`.

## Next

[Tutorial 09: Your first autonomous](09-first-auto.md)
