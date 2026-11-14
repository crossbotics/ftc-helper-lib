# Tutorial 07: PID: hold a lift at a height

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, the lift part has its PID built in, with its gains in `Tunables.java`: [Add a lift](../add-to-robot/01-lift.md).
Build it by hand to see what a PID controller does, or to hold a part that has no module.

**You will build:** a lift that goes to three heights on buttons and holds there, with gains you change
from the gamepad.

**You need:** a lift (slide, elevator or arm) with a motor named `lift` and its encoder cable. Positive
power must move it UP (Tutorial 02). **Turn the robot on with the lift at the bottom.**

**Full program:** [`Tut07LiftPid.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut07LiftPid.java)

> **Safety:** this program keeps the power low, but it has no software limits. Tutorial 08 adds them.
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

- **Output limits** stop a bad gain from slamming the lift. Up gets more, because it fights gravity.
- **Integral limit:** without it, I builds up while the lift is stuck, then shoots it past the target
  ("windup").
- Make the controller **once**, in INIT. It remembers the last error (for D) and the sum (for I). A new
  controller every loop forgets all of that.

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

A rebuild for each of many small changes is slow. So this program changes the gains from the gamepad:

```java
.bindPress(Pad.G1, Btn.DPAD_RIGHT, () -> selected = (selected + 1) % gains.length)   // next gain
.bindPress(Pad.G1, Btn.DPAD_UP, () -> changeGain(pid, true))                         // bigger
.bindPress(Pad.G1, Btn.DPAD_DOWN, () -> changeGain(pid, false))                      // smaller
...
pid.setGains(gains[0], gains[1], gains[2], gains[3]);
```

- Each press multiplies the gain by 1.25 (up) or 0.8 (down), so small and large gains both change by a
  sensible amount. A gain at 0 first jumps to a small value (0.001 for P, 0.0001 for I and D).
- F goes in steps of 0.01 and never below 0, because a negative F pushes the lift down.
- `%` is the remainder after dividing, so after the last gain the choice wraps round to the first.

**Write the final numbers down.** The program loses them when the OpMode stops.

## 6. The tuning recipe

Tune in the order kP, kD, kF, then kI only if needed. The steps are in
[Add a lift: tune it](../add-to-robot/01-lift.md#4-check-it-then-tune-it). In this program, kP is selected at the start (0.002). A, X and Y switch between targets after each change.

## Run it

1. Put the lift at the bottom. Press ▶ (start on the Driver Hub). Position shows 0.
2. Press X: the lift goes toward MIDDLE. With the starting kP, it may stop short. Now tune it, and watch
   "Error" and "At target".

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift goes the wrong way and hits the end | Press STOP first. Positive power moves the lift down: add `.reversed()` to the motor (Tutorial 02). |
| Shakes, overshoots or sags | See the PID table in section 1, or [Add a lift: fix a problem](../add-to-robot/01-lift.md#fix-a-problem). |
| Drops when the OpMode stops | This is normal: the motor gets no power after STOP. `.brake()` slows the drop. |

## Try this

1. Set a target between the buttons with the right stick: `target += (int) (-gamepad1.right_stick_y * 20)`.
   Keep it between 0 and TOP with `MathUtil.clamp`.
2. Show `pid.getLastError()` as a bar of `#` characters, so you can see it shrink.
3. Tune once with an empty lift and once with a game piece on it. Check whether the same gains work.

---

**Next:** [Tutorial 08: A lift that cannot hurt itself](08-safe-lift.md)

**Look it up:** [control.md: PidController](../modules/control.md#pidcontroller)
