# Telemetry module

Package: `io.github.jasoncross.ftchelper.telemetry`

Short helpers for Driver Station telemetry that looks the same in every OpMode, so drivers and
programmers find numbers fast.

## TelemetryHelper

```java
TelemetryHelper.section(telemetry, "Lift");
TelemetryHelper.motor(telemetry, "Lift", liftMotor);
TelemetryHelper.value(telemetry, "Target", liftTarget, "ticks");
TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
telemetry.update();
```

The Driver Station shows:

```
--- Lift ---
Lift : pwr 0.45 | pos 1203 | vel 812/s
Target : 1500.00 ticks
At bottom : no
```

| Method | Adds |
| --- | --- |
| `section(telemetry, title)` | a heading line: `--- title ---` |
| `value(telemetry, label, number, unit)` | `label : number unit`, 2 decimals |
| `value(telemetry, label, number, decimals)` | `label : number`, with the decimals you choose |
| `flag(telemetry, label, boolean)` | `label : yes` or `label : no` |
| `motor(telemetry, label, motor)` | power, encoder position and speed (counts per second) on one line |
| `servo(telemetry, label, servo)` | the servo's last commanded position. (A normal servo cannot report where it really is.) |
| `num(number, decimals)` | the number as text, e.g. `num(0.4567, 2)` = `"0.46"`. NaN shows as `--`. |

These only ADD lines. You still call `telemetry.update()` once at the end of the loop.

## Tips

- One `telemetry.update()` per loop. More than one makes the screen flicker and slows the loop.
- Show what the driver needs during a match at the top; put debug numbers lower.
- Show the loop speed while you test: `TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz")`
  (see `LoopTimer` in [util](util.md)).
