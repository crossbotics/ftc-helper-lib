# Tutorial 07: PID: hold a lift at a height

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/jasoncross/ftc-helper-lib/issues).

**You will learn:** what a PID controller does, how to use `PidController` to send a lift to a
height and hold it there, and how to tune it live from the gamepad.

**You need:** a lift (slide, elevator or arm) with a motor named `lift` and its encoder cable.
Positive power must move it UP (Tutorial 02, "Run it", step 2). Tutorials 01-03.
**Turn the robot on with the lift at the bottom.**

**Full program:** [`Tut07LiftPid.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut07LiftPid.java)
**Reference:** [control.md](../modules/control.md#pidcontroller)

> This tutorial keeps the power low, but it has no real limits. Tutorial 08 adds them.
> Stay near STOP, and know where your hard stops are.

---

## 1. The idea

You want the lift at 1200 ticks. It is at 400. The **error** is 1200 - 400 = 800.
A PID controller turns the error into a power, every loop:

| Part | Gain | What it does | Like... |
| --- | --- | --- | --- |
| **P** proportional | `kP` | power = kP x error. Far = push hard, close = push gently. | a spring |
| **I** integral | `kI` | adds up the error over time. Fixes a small error that never goes away. | a slow, stubborn push |
| **D** derivative | `kD` | looks at how fast the error shrinks and pushes against it. Stops overshoot. | a shock absorber |
| **F** feedforward | `kF` | a fixed push every loop, no matter what. Holds the lift up against gravity. | a counterweight |

Most lifts work well with **P + D + a little F**. Many never need I.

## 2. Make the controller

```java
PidController pid = new PidController(gains[0], gains[1], gains[2], gains[3])   // kP, kI, kD, kF
        .setOutputLimits(-0.4, 0.7)   // never more than 40% down or 70% up
        .setIntegralLimit(0.2)        // I can never add more than 0.2 power
        .setTolerance(20);            // within 20 ticks counts as "there"
```

Make it **once**, in INIT. It remembers the last error (for D) and the sum (for I), so a new one
every loop would forget everything.

## 3. Use it every loop

```java
double power = pid.calculate(target, lift.getCurrentPosition());
lift.setPower(power);
```

That is all: target, measurement, power out.

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

Call `reset()` each time the target changes. Otherwise the I sum and the last error from the
old target give a kick at the start of the new move.

## 5. Tune it live

Changing a number, rebuilding and reinstalling takes 2 minutes. Tuning takes 30 changes.
So this program lets you change the gains from the gamepad while it runs:

```java
.bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)   // next gain
.bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))                         // bigger
.bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false))                      // smaller
...
pid.setGains(gains[0], gains[1], gains[2], gains[3]);
```

Telemetry shows a `>` next to the gain you are changing. Each press multiplies it by 1.25 (up)
or 0.8 (down); F goes in steps of 0.01.

**Write the final numbers down.** They are lost when the OpMode stops. Put them into your
real program.

## 6. The tuning recipe

Do these in order. Switch between targets with A, X and Y after each change and watch.

1. **P only.** Start at kP 0.002. Raise it (D-pad up) until the lift moves quickly to each
   height. If it shakes or bounces around the target, go back down one or two steps.
2. **D.** If it overshoots (goes past and comes back), select kD and raise it from 0.0001
   until the overshoot is gone. Too much D makes it sluggish or buzzy.
3. **F.** If it stops a little below the target and sags, select kF and raise it until it holds
   its height with almost no error. F is about the power that holds the lift still in mid-air.
4. **I (only if needed).** If a small error still stays, add a very small kI. The integral limit
   keeps it from building up too much.

Good result: reaches each height in under a second, overshoots by less than ~1%, holds still.

## Run it

1. START with the lift at the bottom. Position 0.
2. X: the lift goes toward MIDDLE. With the starting kP it may be slow or stop short. That is
   expected: now tune.
3. Follow the recipe. Watch "Error" and "At target" on telemetry.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift goes the wrong way and hits the end | Positive power moves it down. Add `.reversed()` to the motor (Tutorial 02). STOP first! |
| Shakes around the target | kP too big, or kD too big. Lower them. |
| Overshoots then comes back | Add kD. |
| Stops short and stays there | Add kF (lift) or a small kI. |
| Drops when the OpMode stops | Normal: no power after STOP. `.brake()` slows it. |

## Try this

1. Set a target between the buttons with the right stick: `target += (int) (-gamepad1.right_stick_y * 20)`.
   (Keep it inside 0 and TOP with `MathUtil.clamp`.)
2. Show `pid.getLastError()` as a bar of `#` characters, so you can see it shrink.
3. Tune once with an empty lift and once carrying a game piece. Do the same gains work?

## Next

[Tutorial 08: A lift that cannot hurt itself](08-safe-lift.md)
