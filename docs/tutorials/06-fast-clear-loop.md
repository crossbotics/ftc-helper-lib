# Tutorial 06: A fast, clear loop

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/jasoncross/ftc-helper-lib/issues).

**You will learn:** how to measure your loop speed, how bulk reads make it faster, and how to lay
out telemetry so drivers find what they need.

**You need:** a mecanum drivetrain (change to `TankDrive` for a standard one). Tutorials 01-04.

**Full program:** [`Tut06FastLoop.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut06FastLoop.java)
**Reference:** [hardware.md: BulkReads](../modules/hardware.md#bulkreads), [util.md: LoopTimer](../modules/util.md#looptimer), [telemetry.md](../modules/telemetry.md)

---

## 1. Why loop speed matters

Everything your robot does happens once per loop: read the sticks, read the sensors, decide, set
the powers. At 25 loops per second, the robot reacts 40 ms late, PID control gets jumpy, and
driving feels mushy. At 100+ it feels instant.

## 2. Measure it

```java
LoopTimer loopTimer = new LoopTimer();
...
while (opModeIsActive()) {
    loopTimer.update();      // first thing in the loop
    ...
    TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");
}
```

Always measure before and after a change. Guessing is usually wrong.

## 3. Bulk reads

Every time your code reads an encoder or a sensor on a hub, the Control Hub sends a message and
waits for the answer: about 2 ms each. Four drive encoders plus a lift encoder is about 10 ms per
loop, just waiting.

A **bulk read** asks the hub for ALL its readings in one message. Two ways:

**AUTO (easy, start here):**

```java
BulkReads.auto(hardwareMap);   // one line in INIT
```

**MANUAL (fastest):** you clear the saved readings yourself, once, at the top of every loop:

```java
List<LynxModule> hubs = BulkReads.manual(hardwareMap);
...
while (opModeIsActive()) {
    BulkReads.clear(hubs);     // FIRST line of the loop
    ...
}
```

**If you forget `clear` in MANUAL mode, the readings never change.** The robot will act
very strangely (a PID that never sees the lift move). That is why AUTO is the safe default.

The program lets you switch bulk reads on and off with A while it runs, so you can see the
difference in Hz. (It sets the hub mode directly for this demo; normal programs just call
`BulkReads.auto` or `manual` once.)

## 4. Telemetry the driver can use

During a match, the driver glances at the screen for half a second. Put what they need at the
top, and hide the rest:

```java
// What the driver needs: at the top.
TelemetryHelper.section(telemetry, "Driver");
TelemetryHelper.flag(telemetry, "Bulk reads (A)", bulk);
TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");

// Debug numbers: at the bottom, and only when asked for.
if (showDebug) {
    TelemetryHelper.section(telemetry, "Debug (B)");
    for (int i = 0; i < wheels.length; i++) TelemetryHelper.motor(telemetry, names[i], wheels[i]);
}
telemetry.update();
```

- `section` puts a `--- Driver ---` heading line, so groups are easy to find.
- `motor` puts power, position and speed on ONE line.
- Telemetry also costs loop time. Many lines = slower loop. Show debug only when needed (B).
- Call `telemetry.update()` **once** per loop.

## Run it

1. START. Note the Hz with bulk reads ON.
2. Press A (bulk reads OFF). The Hz drops, because each wheel read is now a separate message.
3. Press B to show the debug section: the Hz drops a little more. Telemetry is not free either.

Write down your numbers. A typical result: 60 Hz without bulk reads, 150+ Hz with.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Readings frozen | MANUAL mode without `BulkReads.clear(hubs)` at the top of the loop. |
| Hz is very low (< 30) even with bulk reads | Look for `sleep()` in the loop, or a camera/vision process running. Turn off debug telemetry. |

## Try this

1. Change the program to `BulkReads.auto(hardwareMap)` and remove the `clear` line. Compare Hz with MANUAL.
2. Add 20 telemetry lines in a `for` loop. How much does the Hz drop?
3. Show the loop time in ms (`getAverageMs()`) instead of Hz. Which is easier to understand?

## Next

[Tutorial 07: PID: hold a lift at a height](07-pid-lift.md)
