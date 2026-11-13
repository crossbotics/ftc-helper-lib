# Tutorial 06: A fast, clear loop

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a drive program that shows its loop speed, with buttons to turn bulk reads and debug telemetry on and off.
The main track does bulk reads for you. This tutorial shows how they work inside.

**You will learn:** how to measure your loop speed, how bulk reads make it faster, and how to lay out
telemetry so drivers find what they need.

**You need:** a mecanum drivetrain (change to `TankDrive` for a standard one). Do Tutorials 01-04 first.

**Full program:** [`Tut06FastLoop.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut06FastLoop.java)
**Reference:** [hardware.md: BulkReads](../modules/hardware.md#bulkreads), [util.md: LoopTimer](../modules/util.md#looptimer), [telemetry.md](../modules/telemetry.md)

---

## 1. Why loop speed matters

Your robot does everything once per loop: read the sticks, read the sensors, decide, set the powers.
At 25 loops per second, the robot reacts 40 ms (milliseconds, thousandths of a second) late. PID control
(the lift holding its height, Tutorial 07) gets jumpy, and driving feels mushy. At 100+ loops per
second, the robot feels instant.

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

Always measure before and after a change. A guess is usually wrong.

## 3. Bulk reads

A **hub** is the Control Hub or Expansion Hub the motors and sensors plug into.
Every time your code reads an encoder or a sensor on a hub, the Control Hub sends a message and waits
for the answer: about 2 ms each. Four drive encoders plus a lift encoder cost about 10 ms per loop, all
of it waiting.

A **bulk read** asks the hub for ALL its readings in one message. There are two ways:

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

**If you forget `clear` in MANUAL mode, the readings never change.** Then the robot acts very
strangely: for example, a PID never sees the lift move. That is why AUTO is the safe default.

In the program, A switches bulk reads on and off while it runs, so you can see the difference in Hz.
For this demo, the program sets the hub mode directly. Normal programs call `BulkReads.auto` or `manual`
once. `List<LynxModule>` is a list of hubs: Java calls a Control Hub or Expansion Hub a `LynxModule`.
After the switch, `loopTimer.reset()` starts measuring again. Then the new loop speed does not mix with
the old one.

## 4. Telemetry the driver can use

During a match, the driver looks at the screen for half a second. Put what the driver needs at the
top, and hide the rest:

```java
// What the driver needs: at the top.
TelemetryHelper.section(telemetry, "Driver");
TelemetryHelper.flag(telemetry, "Bulk reads (A)", bulk);
TelemetryHelper.value(telemetry, "Loop", loopTimer.getHz(), "Hz");

// Debug numbers: at the bottom, and only when asked for.
if (showDebug) { // (1)!
    TelemetryHelper.section(telemetry, "Debug (B)");
    for (int i = 0; i < wheels.length; i++) TelemetryHelper.motor(telemetry, names[i], wheels[i]); // (2)!
}
telemetry.update();
```

1. The program also reads the encoders in the debug section. So the bulk-read difference shows most
   with the debug section on.
2. `wheels` comes from `drive.getMotors()`, in the order front-left, front-right, back-left,
   back-right. `names` is `{"FL", "FR", "BL", "BR"}`, in the same order. The `for` loop goes
   through them one by one (i = 0, 1, 2, 3).

- `section` puts a `--- Driver ---` heading line, so groups are easy to find.
- `motor` puts power, position and speed on ONE line.
- Telemetry also costs loop time. Many lines = slower loop. Show debug only when you need it (B).
- Call `telemetry.update()` **once** per loop.

## Run it

1. Press ▶ (start on the Driver Hub). Write down the Hz with bulk reads ON.
2. Press A (bulk reads OFF). The Hz drops, because each wheel read is now a separate message.
3. Press B to show the debug section: the Hz drops a little more. Telemetry also costs time.

Write down your numbers. A typical result: 60 Hz without bulk reads, 150+ Hz with them.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Readings frozen | The program uses MANUAL mode without `BulkReads.clear(hubs)` at the top of the loop. |
| Hz is very low (< 30) even with bulk reads | Look for `sleep()` in the loop, or a camera/vision process that runs. Turn off debug telemetry. |

## Try this

1. Change the program to `BulkReads.auto(hardwareMap)` and remove the `clear` line. Compare Hz with MANUAL.
2. Add 20 telemetry lines in a `for` loop. Watch how much the Hz drops.
3. Show the loop time in ms (`getAverageMs()`) instead of Hz. Decide which one is easier to understand.

## Next

[Tutorial 07: PID: hold a lift at a height](07-pid-lift.md)
