# Tutorial 11: Driver assists

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**In the five files:** one line in `Controls.java` gives the aim buzz and whole-robot aim
([What the drivers see and feel](../controls/03-see-and-feel.md), [Aim: buttons to add](../reference/parts/aim.md#buttons-to-add)).
**Build it by hand** to see how an assist works inside: PID on the IMU, a buzz from an error, and a ramp.

**You will build:** a mecanum TeleOp where a D-pad press turns the robot to face an exact direction, the
gamepad buzzes when it is lined up, and starts are smooth.

**You need:** a mecanum robot with the IMU, and `RobotSettings` from Tutorial 09. Do Tutorials 04 and 07
first. A standard drive works the same way with `TankDrive.arcade`.

**Full program:** [`Tut11HeadingHold.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut11HeadingHold.java)

---

## 1. Snap to a heading

To line up square to a wall by hand takes time. With a snap, the driver presses a D-pad direction, and
the robot turns to face exactly that way while the driver keeps driving. This is PID (Tutorial 07) on
the heading:

```java
PidController headingPid = new PidController(0.015, 0, 0.002)
        .setAngleMode(true)
        .setOutputLimits(-0.6, 0.6)
        .setTolerance(2);
```

Headings wrap around at 180. From 170 to -170 is a 20 degree turn, not 340. `setAngleMode(true)` makes
the controller always take the short way.

- kP 0.015 is the turn power per degree of error. There is no I.
- kD 0.002 slows the turn near the target, so the robot does not swing past.
- `setOutputLimits(-0.6, 0.6)` stops a snap from being violent.
- Within 2 degrees counts as "pointed right".

The D-pad headings are field headings. 0 is the way the robot faced when the heading was last zeroed,
and positive is left (counterclockwise). So D-pad LEFT = 90 = face left. BACK resets the heading to 0:
press it when the robot faces straight away from you.

## 2. Hand control back to the driver

```java
private Double targetHeading = null;   // null = driver turns by hand
...
.bindPress(Pad.G1, Btn.DPAD_UP, () -> snapTo(0, headingPid))
.bindPress(Pad.G1, Btn.DPAD_LEFT, () -> snapTo(90, headingPid))
...
double turn = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
if (turn != 0) targetHeading = null;           // the driver takes over
if (targetHeading != null) {
    turn = -headingPid.calculate(targetHeading, heading);
}
drive.driveFieldCentric(forward, strafe, turn, heading);
```

This is the same pattern as the lift in Tutorial 08: a button sets a target, and a touch on the stick
cancels it. `targetHeading` is a `Double` (capital D), so it can be `null`.

### Why the minus sign?

The IMU and the PID count **counterclockwise** (left = positive). The drive's turn is **clockwise**
(right = positive), so it matches the stick. The minus sign flips one into the other
([Signs and units](../conventions.md)). Without it, the robot turns AWAY from the target, faster and faster.

## 3. Feel it

The driver watches the robot, not the screen. A buzz says "you are pointed right":

```java
AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(3);
...
aimFeel.update(Angles.shortestDiff(targetHeading, heading));
```

Inside 3 degrees, the gamepad buzzes, and it buzzes stronger as the robot gets closer.
`Angles.shortestDiff` gives the error between two headings the short way around. Use it every time you
need that error.

## 4. Smooth starts

A tall robot that goes from 0 to full speed in one loop can tip, and its wheels slip. The "slew rate" is
how fast a value may change. A `SlewRateLimiter` lets a value change only that fast:

```java
SlewRateLimiter forwardRamp = new SlewRateLimiter(2.5, 6.0);   // speed up 2.5/s, slow down 6/s
...
double forward = forwardRamp.calculate(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
```

2.5 per second means 0 to full in 0.4 s. The robot may slow down faster (6 per second), so it still
stops quickly. Use one limiter per value (forward and strafe here). Turning has no limiter, so aiming
stays quick.

## Run it

1. Point the robot away from you, or press BACK to reset "forward". Press ▶ (start on the Driver Hub).
2. Turn the robot by hand, then press D-pad up: the robot turns back to face forward, and the gamepad buzzes.
3. Hold the left stick forward and press D-pad left: the robot drives AND turns to face left.
4. Push the right stick during a snap: the snap stops, and you turn by hand.
5. Slam the left stick forward: the robot speeds up smoothly. Let go: it stops quickly.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Turns away from the target, faster and faster | The minus sign in section 2 is missing. |
| Shakes around the target | `kP` is too big, or `kD` is too big. Lower them. |
| Stops a few degrees short | Raise `kP` a little. When that small error is good enough, use a bigger tolerance. |
| Goes the long way around | `setAngleMode(true)` is missing. |
| No buzz | The gamepad has no rumble motors, or the error never gets inside the threshold. |

## Try this

1. Add the right bumper as "snap to the nearest 45 degrees". (Hint: `Math.round(heading / 45) * 45`.)
2. Make the ramp gentler only while the lift is up, because a tall robot tips more easily.
3. Use `PidController.atTarget()` to show a big "LOCKED" line at the top of telemetry.

---

**Next:** [Tutorial 12: Organize a whole robot](12-robot-structure.md).

**Look it up:** [Control module](../modules/control.md) (`PidController`, `SlewRateLimiter`),
[Gamepad module: AimRumble](../modules/gamepad.md#aimrumble).
