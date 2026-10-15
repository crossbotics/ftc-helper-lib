# Util module

Package: `io.github.jasoncross.ftchelper.util`

Learn it hands-on: [06 A fast, clear loop](../tutorials/06-fast-clear-loop.md), [10 Autonomous with mechanisms](../tutorials/10-auto-with-mechanisms.md), [13 Testing](../tutorials/13-testing.md).

Small helpers used everywhere.

| Class | Use it to |
| --- | --- |
| [`MathUtil`](#mathutil) | clamp, deadband, lerp, compare, normalize |
| [`Angles`](#angles) | do math with headings (the -180/180 seam) |
| [`LoopTimer`](#looptimer) | see how fast your loop runs |
| [`FieldPose`](#fieldpose) | hold an x, y, heading |
| [`PoseStore`](#posestore) | pass the robot's pose from Autonomous to TeleOp |

---

## MathUtil

| Method | What it does | Example |
| --- | --- | --- |
| `clamp(value, min, max)` | keeps a number inside a range. NaN gives 0 (a safe value for a motor). Also works with `int`. | `clamp(1.3, -1, 1)` = 1.0 |
| `deadband(value, band)` | values smaller than `band` become 0 | `deadband(0.03, 0.05)` = 0 |
| `deadbandScaled(value, band)` | same, but starts again from 0 at the band edge, so there is no jump | `deadbandScaled(0.55, 0.1)` = 0.5 |
| `lerp(a, b, t)` | the number part of the way from a to b | `lerp(0, 10, 0.25)` = 2.5 |
| `isNear(a, b, tolerance)` | true if a and b are within tolerance | `isNear(100, 103, 5)` = true |
| `normalize(values, limit)` | scales an array down together so none is over `limit` | `[2, 1, -1, 0]`, 1 gives `[1, 0.5, -0.5, 0]` |

## Angles

Headings go around a circle. Plain subtraction is wrong near the -180/180 seam:
from 170 to -170 is a 20 degree turn, but -170 - 170 = -340.

| Method | What it does | Example |
| --- | --- | --- |
| `wrapDegrees(deg)` | writes any angle between -180 and 180 | `wrapDegrees(190)` = -170 |
| `shortestDiff(target, current)` | the shortest turn from current to target. Positive = turn left (counterclockwise). | `shortestDiff(-170, 170)` = 20 |

## LoopTimer

```java
LoopTimer loopTimer = new LoopTimer();
while (opModeIsActive()) {
    loopTimer.update();
    telemetry.addData("Loop", "%.0f Hz", loopTimer.getHz());
    ...
}
```

| Method | What it does |
| --- | --- |
| `update()` | call once at the top of each loop. Returns seconds since the last call. |
| `getHz()` | loops per second (smoothed). 50-200 is usual; below 30 feels laggy. |
| `getAverageMs()` | average loop time in milliseconds |
| `getLastSeconds()` | how long the last loop took |
| `reset()` | start over |

If the loop is slow: turn on [bulk reads](hardware.md#bulkreads), send fewer telemetry lines, and
make sure nothing in the loop calls `sleep()`.

## FieldPose

An x, y (inches) and heading (degrees, counterclockwise positive). The library does not decide
where (0, 0) is on the field: your team decides that each season and writes it down.

| Member | Meaning |
| --- | --- |
| `new FieldPose(x, y, headingDeg)` | make one (`new FieldPose()` = 0, 0, 0) |
| `x`, `y`, `headingDeg` | the values; you can read and change them |
| `copy()` | a separate copy |
| `toString()` | text like `(12.0 in, -3.5 in, 90.0 deg)` |

## PoseStore

Passes the robot's last pose from Autonomous to TeleOp. Both run in the same app, so a saved
value stays between them (until the app restarts or the robot loses power).

```java
// End of Autonomous:
PoseStore.save(new FieldPose(x, y, auto.headingDeg()));

// TeleOp INIT:
FieldPose start = PoseStore.consume();
if (start != null) { ... use it ... }
```

| Method | What it does |
| --- | --- |
| `save(pose)` | saves a copy. `null` is ignored. |
| `consume()` | gives the saved pose and forgets it, so it is used once. `null` if none. |
| `peek()` | gives a copy and keeps it saved |
| `has()` | true if one is saved |
| `clear()` | forgets it |
