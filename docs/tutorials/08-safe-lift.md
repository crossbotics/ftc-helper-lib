# Tutorial 08: A lift that cannot hurt itself

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, the lift part has its limits, homing and stick control built in: [Add a lift](../add-to-robot/01-lift.md).
Build it by hand to see how software limits work, and why they do not replace hard stops.

**You will build:** the lift from Tutorial 07, now with limits it cannot drive past, a bottom switch, and
stick control that takes over from the buttons.

**You need:** the lift from Tutorial 07 (motor `lift`, positive = up) and its PID gains. A touch sensor
at the bottom named `lift_bottom` is best.

**Full program:** [`Tut08SafeLift.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut08SafeLift.java)

---

## 1. What can go wrong with a lift

- **Driven past the top:** the string snaps, the slide pulls apart, or the robot breaks the 29 in height
  rule.
- **Driven into the bottom:** the motor stalls and gets hot, or the belt skips.
- **Encoder drift** (the belt skips, or the robot turned on with the lift up): every height is wrong.

`SafeMotor` handles all three in software. **It does not replace hard stops**: a hard stop is a
physical block that the lift hits at each end. Rule R105 B says the robot must stay inside its size
limits without software. The software stops the motor before it hits the block. The block holds when
the software is wrong.

## 2. Wrap the motor

```java
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();
SafeMotor lift = new SafeMotor(liftMotor)
        .setLimits(BOTTOM, MAX)       // 0 to 2600 ticks
        .setSlowZone(250, 0.2)        // within 250 ticks of an end, slow down to 20%
        .setMaxPower(0.9);
```

- **Soft limits** (limits in software, not metal): power that pushes past 0 or 2600 becomes 0. Power
  AWAY from the limit always works, so the lift never gets "stuck at the top".
- **Slow zone:** close to an end, `SafeMotor` lowers the power toward that end, so the lift does not slam.
- **Max power:** the most power the motor ever gets. 90% is gentler on the motor and the string.

Set MAX a little below the real top. Then the soft limit stops the lift before the hard stop.

## 3. The bottom switch and homing

```java
TouchSensor bottom = Hw.tryGet(hardwareMap, TouchSensor.class, "lift_bottom");
if (bottom != null) lift.setLowerSwitch(bottom::isPressed);
```

`bottom::isPressed` is short for the lambda `() -> bottom.isPressed()`.

With a lower switch:

- The switch always stops downward power, whatever the encoder says.
- When the lift presses the switch, `SafeMotor` sets the position to 0 (**homing**). So each time the
  lift reaches the bottom, it fixes the encoder drift.
- **Until the first homing, the lift only goes DOWN, slowly (30%).** `SafeMotor` does not know the
  position yet, so up is not safe. The driver pushes the lift down once at the start. When the lift is
  already on the switch, it homes at once.

Without a switch, position 0 is wherever the lift was when the robot turned on. So always turn the robot
on with the lift at the bottom.

## 4. The pattern: set heights + hand control

```java
private Integer target = null;   // null = by hand
...
.bindPress(Pad.G2, Btn.A, () -> goTo(BOTTOM, pid))
.bindPress(Pad.G2, Btn.Y, () -> goTo(TOP, pid))
.bindPress(Pad.G2, Btn.B, () -> target = null)      // stop holding
```

Then, every loop:

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

- `target` is an `Integer` (not `int`), so it can be `null`: "no set height, by hand".
- **A touch on the stick cancels the set height,** so the driver is always in control. The 0.1 deadband
  stops a bumped stick from canceling it.
- Before homing, the zero is not known, so the program cancels set heights until the lift is down.
- B forgets the target. With the stick at rest, the brake holds the lift.
- `moveTo(target, pid)` is `pid.calculate` + `setPower`, with the limits applied. The PID gains
  are yours from Tutorial 07.
- **Safety:** `lift.update()` comes first every loop, and **every power for this motor goes through the
  SafeMotor**. A direct call to `liftMotor.setPower(...)` skips the limits.

## 5. Telemetry that explains itself

```java
if (lift.isWaitingForHoming()) telemetry.addLine("Push the lift DOWN to the switch to home it.");
TelemetryHelper.value(telemetry, "Asked power", lift.getRequestedPower(), 2);
TelemetryHelper.value(telemetry, "Sent power", lift.getAppliedPower(), 2);
```

When "Asked" is 0.5 and "Sent" is 0, a limit stopped the lift. So when the lift does not move, these
two lines show why.

## Run it

1. With a switch, start with the lift a little UP. Telemetry asks you to home it. Stick up: nothing.
   Stick down: the lift goes down slowly and reaches the switch. The position becomes 0.
2. Stick up: the lift moves, then slows near the top and stops at MAX. Stick down: it moves away at once.
3. Press Y: the lift goes to TOP. Touch the stick: that cancels the button target.
4. Hold the stick down at the bottom: "Asked -0.5, Sent 0".

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Only goes down, slowly | The lift is not homed yet. Push it down to the switch. |
| Will not go down at all | `SafeMotor` thinks the lift is at 0, or the switch is pressed. Check `At bottom` and the switch wiring. |
| Stops early at the top | MAX is too low, or the position drifted. Home again at the bottom. |
| Goes past the top | Press STOP. Positive power is not "up" (reverse the motor), or MAX is wrong. |
| Hums at the bottom when holding BOTTOM | The F push in the PID does that. It is normal. Or use `target = null` at the bottom. |

## Try this

1. Make B "go to bottom, then let go": when the lift reaches BOTTOM (`pid.atTarget()`), set `target = null`.
2. Add an upper limit switch with `setUpperSwitch(...)`.
3. Let the driver home the lift again on demand: add a button that sets `target = null` and drives down
   at -0.3 until `lift.isAtLower()`.

---

**Next:** [Tutorial 09: Autonomous by hand with AutoDrive](09-first-auto.md)

**Look it up:** [safety.md: SafeMotor](../modules/safety.md#safemotor), [control.md: PidController](../modules/control.md#pidcontroller)
