# Tutorial 07: PID: hold a lift at a height

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a lift that goes to three heights on buttons and holds there, with gains you can change from the gamepad.
In the main track, the lift part has its PID built in: [An intake, a lift and a claw](start/04-lift-and-claw.md).

**You will learn:** what a PID controller does, how to send a lift to a height with `PidController` and
hold it there, and how to tune it live from the gamepad.

**You need:** a lift (slide, elevator or arm) with a motor named `lift` and its encoder cable.
Positive power must move it UP (Tutorial 02, "Run it", step 2). Do Tutorials 01-03 first.
**Turn the robot on with the lift at the bottom.**

**Full program:** [`Tut07LiftPid.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut07LiftPid.java)
**Reference:** [control.md](../modules/control.md#pidcontroller)

> This tutorial keeps the power low, but it has no real limits. Tutorial 08 adds them.
> Stay near STOP, and find your hard stops first.

---

## 1. The idea

Heights are in encoder **ticks** (the counts from Tutorial 02). For example, you want the lift at 1200
ticks, and it is at 400. The **error** is 1200 - 400 = 800.
A **PID controller** turns the error into a power, every loop. It adds up to four parts. For each part,
you choose a number, called its **gain**. The gain sets how strong that part is:

| Part | Gain | What it does | Like... |
| --- | --- | --- | --- |
| **P** proportional | `kP` | power = kP x error. Far = push hard, close = push gently. | a spring |
| **I** integral | `kI` | adds up the error over time. Fixes a small error that never goes away. | a slow, stubborn push |
| **D** derivative | `kD` | looks at how fast the error shrinks and pushes against it. Stops overshoot. | a shock absorber |
| **F** feedforward | `kF` | a fixed push every loop, no matter what. Holds the lift up against gravity. | a counterweight |

Most lifts work well with **P + D + a little F**. Many lifts never need I.

## 2. Make the controller

Write the heights first, in ticks:

```java
// 0 = where the lift was at INIT. TOP must be below the real top of the lift.
static final int BOTTOM = 0, MIDDLE = 1200, TOP = 2400;   // encoder ticks: measure yours (1)!
```

1. Measure yours: move the lift by hand and read the position on telemetry (Tutorial 02).
   Keep TOP a little below the hard stop at the top.

Then make the controller:

```java
PidController pid = new PidController(gains[0], gains[1], gains[2], gains[3])   // kP, kI, kD, kF
        .setOutputLimits(-0.4, 0.7)   // never more than 40% down or 70% up
        .setIntegralLimit(0.2)        // I can never add more than 0.2 power
        .setTolerance(20);            // within 20 ticks counts as "there"
```

The output limits stop a bad gain from slamming the lift while you tune. Up gets more power because it
fights gravity. Without the integral limit, I can build up while the lift is stuck. Then it makes the
lift shoot past the target. This is "windup".

Make the controller **once**, in INIT. It remembers the last error (for D) and the sum (for I). A new
controller every loop would forget all of that.

## 3. Use it every loop

```java
double power = pid.calculate(target, lift.getCurrentPosition());
lift.setPower(power);
```

Target and measurement go in, and power comes out. Run it every loop, also at the target: then it holds
the lift up against gravity.

## 4. Change the target with buttons

```java
.bindPress(Pad.G1, Btn.A, () -> newTarget(BOTTOM, pid))
.bindPress(Pad.G1, Btn.Y, () -> newTarget(TOP, pid))
...
private void newTarget(int ticks, PidController pid) {
    target = ticks;
    pid.reset();   // forget the old I and D memory
}
```

Call `reset()` each time the target changes. Otherwise the I sum and the last error from the old target
give a kick at the start of the new move.

## 5. Tune it live

Tuning takes about 30 changes. A rebuild and an install for each change is slow. So in this program,
you change the gains from the gamepad while it runs:

```java
.bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)   // next gain
.bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))                         // bigger
.bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false))                      // smaller
...
pid.setGains(gains[0], gains[1], gains[2], gains[3]);
```

Telemetry shows a `>` next to the gain you are changing. Each press multiplies it by 1.25 (up) or 0.8
(down). So small and large gains both change by a sensible amount. 0 x 1.25 is still 0, so a gain at 0
first jumps to a small value (0.001 for P, 0.0001 for I and D). F goes in plain steps of 0.01 and never
goes below 0, because a negative F would push the lift down. `%` in `(selected + 1) % gains.length` is the
remainder after dividing. So after the last gain, the choice wraps round to the first.
`setGains` does not reset the controller.

**Write the final numbers down.** The program loses them when the OpMode stops. Put them into your real
program.

## 6. The tuning recipe

Do these steps in order. After each change, switch between targets with A, X and Y, and watch.

1. **P only.** Start at kP 0.002. kP is already selected at the start, so press D-pad up to raise it.
   Stop when the lift moves quickly to each height. When it shakes or bounces around the target, go back
   down one or two steps.
2. **D.** When the lift overshoots (goes past and comes back), select kD. Raise it from 0.0001 until the
   overshoot is gone. Too much D makes the lift sluggish or buzzy.
3. **F.** When the lift stops a little below the target and sags, select kF. Raise it until the lift
   holds its height with almost no error. F is about the power that holds the lift still in mid-air.
4. **I (only if needed).** When a small error still stays, add a very small kI. The integral limit
   keeps it from building up too much.

A good result: the lift reaches each height in under a second, overshoots by less than ~1%, and holds
still.

## Run it

1. Put the lift at the bottom. Press ▶ (start on the Driver Hub). Position shows 0.
2. Press X: the lift goes toward MIDDLE. With the starting kP, it may be slow or stop short. That is
   normal: now tune it.
3. Follow the recipe. Watch "Error" and "At target" on telemetry.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift goes the wrong way and hits the end | Press STOP first. Positive power moves the lift down: add `.reversed()` to the motor (Tutorial 02). |
| Shakes around the target | kP is too big, or kD is too big. Lower them. |
| Overshoots then comes back | Add kD. |
| Stops short and stays there | Add kF (lift) or a small kI. |
| Drops when the OpMode stops | This is normal: the motor gets no power after STOP. `.brake()` slows the drop. |

## Try this

1. Set a target between the buttons with the right stick: `target += (int) (-gamepad1.right_stick_y * 20)`.
   Keep it between 0 and TOP with `MathUtil.clamp`.
2. Show `pid.getLastError()` as a bar of `#` characters, so you can see it shrink.
3. Tune once with an empty lift and once with a game piece on it. Check whether the same gains work.

## Next

[Tutorial 08: A lift that cannot hurt itself](08-safe-lift.md)
