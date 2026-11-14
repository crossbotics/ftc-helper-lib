# Tutorial 06: A fast, clear loop

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, `Robot.java` turns on bulk reads for you, and the screen shows each part's values: [What the drivers see and feel](../controls/03-see-and-feel.md).
Build it by hand to see what a bulk read is, and what makes a loop fast or slow.

**You will build:** a drive program that shows its loop speed. A turns bulk reads on and off. B shows and
hides the debug telemetry.

**You need:** a mecanum drivetrain (change to `TankDrive` for a standard one).

**Full program:** [`Tut06FastLoop.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut06FastLoop.java)

---

## 1. Why loop speed matters

Your robot reads, decides and sets the powers once per loop. At 25 loops per second, the robot reacts 40 ms (milliseconds, thousandths of a second) late. A PID
(Tutorial 07) gets jumpy, and driving feels mushy. At 100+ loops per second, the robot feels instant.

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

Measure before and after every change. A guess is often wrong.

## 3. Bulk reads

A **hub** is the Control Hub or Expansion Hub the motors and sensors plug into. Each time your code reads
an encoder or a sensor, the Control Hub sends a message to the hub and waits for the answer: about 2 ms.
Four drive encoders plus a lift encoder cost about 10 ms per loop, all of it waiting.

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

`List<LynxModule>` is a list of hubs: Java calls a hub a `LynxModule`. The A button sets the hub mode
directly, for the demo only, then calls `loopTimer.reset()` so the old loop speed does not mix in.

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

1. The debug section also reads the encoders. So the bulk-read difference shows most with the debug
   section on.
2. `wheels` comes from `drive.getMotors()`, and `names` is `{"FL", "FR", "BL", "BR"}` in the same order.

`section` puts a `--- Driver ---` heading line. `motor` puts power, position and speed on ONE line.
Telemetry also costs loop time, so show debug only when you need it. Call `telemetry.update()` **once**
per loop.

## Run it

1. Press ▶ (start on the Driver Hub). Write down the Hz with bulk reads ON.
2. Press A (bulk reads OFF). The Hz drops, because each wheel read is now a separate message.
3. Press B to show the debug section. The Hz drops a little more.

A typical result: 60 Hz without bulk reads, 150+ Hz with them.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Readings frozen | The program uses MANUAL mode without `BulkReads.clear(hubs)` at the top of the loop. |
| Hz is very low (< 30) even with bulk reads | Look for `sleep()` in the loop, or a camera process that runs. Turn off debug telemetry. |

## Try this

1. Change the program to `BulkReads.auto(hardwareMap)` and remove the `clear` line. Compare Hz with MANUAL.
2. Add 20 telemetry lines in a `for` loop. Watch how much the Hz drops.

---

**Next:** [Tutorial 07: PID: hold a lift at a height](07-pid-lift.md)

**Look it up:** [hardware.md: BulkReads](../modules/hardware.md#bulkreads), [util.md: LoopTimer](../modules/util.md#looptimer), [telemetry.md: TelemetryHelper](../modules/telemetry.md#telemetryhelper)
