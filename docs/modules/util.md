# Util module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.util`

Learn it hands-on: [06 A fast, clear loop](../tutorials/06-fast-clear-loop.md), [10 Autonomous with mechanisms](../tutorials/10-auto-with-mechanisms.md), [13 Testing](../tutorials/13-testing.md).

Small helpers for number math, angle math, a loop timer, and passing the robot's position from autonomous
to TeleOp. The rest of the library uses them, and you can use them in any code you write. They need no setup.

| Class | Use it to |
| --- | --- |
| [`MathUtil`](#mathutil) | keep a number in a range, ignore tiny values, blend two numbers, compare, scale down together |
| [`Angles`](#angles) | do math with headings (the -180/180 seam) |
| [`LoopTimer`](#looptimer) | see how fast your loop runs |
| [`FieldPose`](#fieldpose) | hold a pose: x, y and heading |
| [`PoseStore`](#posestore) | pass the robot's pose from Autonomous to TeleOp |
| [`BackgroundWorker`](#backgroundworker) | run slow jobs (network, camera settings) without stopping the loop |
| [`Spelling`](#spelling) | "did you mean ...?" for a name typed by hand |

---

## MathUtil

| Method | What it does | Example |
| --- | --- | --- |
| `clamp(value, min, max)` | keeps a number inside a range. NaN ("not a number", for example 0 / 0) gives 0, a safe value for a motor. It also works with `int`. | `clamp(1.3, -1, 1)` = 1.0 |
| `deadband(value, band)` | turns values smaller than `band` into 0 | `deadband(0.03, 0.05)` = 0 |
| `deadbandScaled(value, band)` | the same, but it starts again from 0 at the band edge, so there is no jump | `deadbandScaled(0.55, 0.1)` = 0.5 |
| `lerp(a, b, t)` | the number `t` of the way from a to b (0 = a, 1 = b, 0.25 = a quarter of the way) | `lerp(0, 10, 0.25)` = 2.5 |
| `isNear(a, b, tolerance)` | true when a and b are within tolerance of each other | `isNear(100, 103, 5)` = true |
| `normalize(values, limit)` | scales an array down together so none is over `limit` | `[2, 1, -1, 0]`, 1 gives `[1, 0.5, -0.5, 0]` |

## Angles

Headings go around a circle, so plain subtraction gives a wrong answer near the -180/180 seam. From 170
to -170 is a 20 degree turn, but -170 - 170 = -340.

| Method | What it does | Example |
| --- | --- | --- |
| `wrapDegrees(deg)` | writes any angle as an angle between -180 and 180 | `wrapDegrees(190)` = -170 |
| `shortestDiff(target, current)` | the shortest turn from current to target. Positive = turn left (counterclockwise). | `shortestDiff(-170, 170)` = 20 |

## LoopTimer

```java
LoopTimer loopTimer = new LoopTimer();
while (opModeIsActive()) {
    loopTimer.update();                                        // top of every loop
    telemetry.addData("Loop", "%.0f Hz", loopTimer.getHz());   // loops per second, no decimals
    ...
}
```

| Method | What it does |
| --- | --- |
| `update()` | call it once at the top of each loop. It gives the seconds since the last call. |
| `getHz()` | loops per second, smoothed. 50-200 is usual. Below 30, the robot feels laggy. |
| `getAverageMs()` | the average loop time in milliseconds |
| `getLastSeconds()` | how long the last loop took |
| `reset()` | starts over |

A slow loop makes the robot lag. To speed it up, turn on [bulk reads](hardware.md#bulkreads), send fewer
telemetry lines, and check that nothing in the loop calls `sleep()`.

## FieldPose

An x and y in inches, and a heading in degrees (counterclockwise is positive). The library does not
decide where (0, 0) is on the field. Your team decides that each season and writes it down.

| Member | Meaning |
| --- | --- |
| `new FieldPose(x, y, headingDeg)` | makes one (`new FieldPose()` = 0, 0, 0) |
| `x`, `y`, `headingDeg` | the values. You can read and change them |
| `copy()` | a separate copy |
| `toString()` | text like `(12.0 in, -3.5 in, 90.0 deg)` |

## PoseStore

Passes the robot's last pose from Autonomous to TeleOp. Both run in the same app, so a saved value stays
between them. It is lost when the app restarts or the robot loses power.

```java
// End of Autonomous:
PoseStore.save(new FieldPose(x, y, auto.headingDeg()));   // inches, inches, degrees (left = +)

// TeleOp INIT:
FieldPose start = PoseStore.consume();   // null if autonomous did not save one
if (start != null) { ... use it ... }    // always check for null first
// For the heading alone, use DriverHeading (docs/modules/drive.md): it does this for you.
```

| Method | What it does |
| --- | --- |
| `save(pose)` | saves a copy. It ignores `null`. |
| `consume()` | gives the saved pose and forgets it, so your code uses it once. `null` when there is none. |
| `peek()` | gives a copy and keeps it saved |
| `has()` | true when a pose is saved |
| `clear()` | forgets it |
| `ageSec()` | seconds since the save (very large when there is none). Over a few minutes means it is from an earlier match. |

## BackgroundWorker

Runs slow jobs on its own **thread** (a second line of work that runs at the same time as your loop). So
the robot's loop never waits. The library uses it for the Limelight's network requests and the webcam
settings. Guide: [Non-blocking code](../guides/non-blocking.md).

```java
// "() -> ..." is a lambda: the slow job, handed over to run on the worker's own thread
BackgroundWorker worker = new BackgroundWorker("my-sensor");
worker.submitLatest("setting", () -> sensor.slowSetting(value));   // returns at once
worker.shutdown();                                                // in stop()
```

| Method | What it does |
| --- | --- |
| `new BackgroundWorker(name)` | starts the thread (a daemon: it never keeps the app from closing) |
| `submit(job)` | puts a job in the queue |
| `submitLatest(key, job)` | puts a job in the queue in place of a waiting job with the same key (only the newest runs) |
| `queued()`, `getLastError()`, `isRunning()` | read the worker's state. A job that throws an error never crashes the robot |
| `shutdown()` | stops the worker. It drops the waiting jobs |

Never move motors or servos from a background job. Only the loop does that.

## Spelling

"Did you mean ...?" for names typed by hand. The library's messages use it for a position name with a
typo (`no position called "lvel" (did you mean level?)`), a claw's `closd`, or a misnamed Tunables section.

```java
String like = Spelling.closest("closd", Arrays.asList("open", "closed"));   // "closed" (null if none is close)
int d = Spelling.distance("closd", "closed");                                  // 1 letter off
```

| Method | What it does |
| --- | --- |
| `closest(name, known)` | the known name that `name` is most likely a typo of, or null. Capitals and `_` never matter. A name of 5 letters or more may be 2 letters off. A shorter one (3 or 4 letters) may be 1 letter off |
| `distance(a, b)` | how many letters must be added, removed or changed to turn `a` into `b` |

