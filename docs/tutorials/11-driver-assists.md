# Tutorial 11: Driver assists

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** a mecanum TeleOp where a D-pad press turns the robot to face an exact direction, the gamepad buzzes when it is lined up, and starts are smooth.

**You will learn:** how to make the robot turn to an exact heading with one button (PID on the
IMU), how to let the driver FEEL when it is pointed right (`AimRumble`), and how to smooth out
sudden starts (`SlewRateLimiter`).

**You need:** a mecanum robot with the IMU, `RobotSettings` from Tutorial 09. Tutorials 04 and 07.
(A standard drive works the same way with `TankDrive.arcade`.)

**Full program:** [`Tut11HeadingHold.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut11HeadingHold.java)
**Reference:** [control.md](../modules/control.md), [gamepad.md: AimRumble](../modules/gamepad.md#aimrumble)

---

## 1. Snap to a heading

Lining up square to a wall or a goal by hand takes time. Instead: press a D-pad direction and
the robot turns to face exactly that way, while the driver keeps driving with the left stick.

This is PID again (Tutorial 07), but the "position" is the heading:

```java
PidController headingPid = new PidController(0.015, 0, 0.002)
        .setAngleMode(true)
        .setOutputLimits(-0.6, 0.6)
        .setTolerance(2);
```

`setAngleMode(true)` is important: headings wrap around at 180. From 170 to -170 is a 20 degree
turn, not 340. In angle mode the controller always takes the short way.

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

The same pattern as the lift in Tutorial 08: a button sets a target, touching the stick cancels it.
`targetHeading` is a `Double` (capital D), not a `double`, so it can be `null`.

### Why the minus sign?

The IMU and the PID think in **counterclockwise** (left = positive). The drive's turn is
**clockwise** (right = positive), so it matches the stick. To go from one to the other, flip the sign.
See [Signs and units](../conventions.md). Forget the minus and the robot turns AWAY from the target, faster and faster.

The D-pad directions are field directions: 90 = facing left (counterclockwise from the start).

## 3. Feel it

The driver's eyes are on the robot, not the screen. A buzz says "you are pointed right":

```java
AimRumble aimFeel = new AimRumble(gamepad1).setThreshold(3);
...
aimFeel.update(Angles.shortestDiff(targetHeading, heading));
```

Inside 3 degrees it buzzes; the closer, the stronger. `Angles.shortestDiff` gives the error the
short way around. (Use it for any "how far off am I" with headings.)

## 4. Smooth starts

A tall robot that goes from 0 to full speed in one loop can tip, and wheels slip. A
`SlewRateLimiter` lets a value change only so fast ("slew rate" means "how fast it may change"):

```java
SlewRateLimiter forwardRamp = new SlewRateLimiter(2.5, 6.0);   // speed up 2.5/s, slow down 6/s
...
double forward = forwardRamp.calculate(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2));
```

2.5 per second means 0 to full in 0.4 s. Slowing down is allowed to be faster (6 per second),
so the robot still stops quickly. Use one limiter per value (forward and strafe here). Turning is
usually left without one, so aiming stays quick.

## Run it

1. START facing away from you (or press BACK to reset "forward").
2. Turn the robot by hand, then press D-pad up: it turns back to face forward and stops.
3. Hold the left stick forward while pressing D-pad left: it drives AND turns to face left.
4. Push the right stick during a snap: the snap stops, you turn by hand.
5. When the snap finishes: a buzz.
6. Slam the left stick forward: the robot speeds up smoothly. Let go: it stops quickly.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Turns away from the target, faster and faster | The minus sign in section 2 is missing. |
| Shakes around the target | `kP` too big or `kD` too big; lower them. |
| Stops a few degrees short | Raise `kP` a little, or a bigger tolerance if it is good enough. |
| Goes the long way around | `setAngleMode(true)` is missing. |
| No buzz | Gamepad has no rumble motors, or the error never gets inside the threshold. |

## Try this

1. Add the right bumper as "snap to the nearest 45 degrees". (Hint: `Math.round(heading / 45) * 45`.)
2. Make the ramp gentler only while the lift is up (a tall robot tips more easily).
3. Use `PidController.atTarget()` to show a big "LOCKED" line at the top of telemetry.

## Next

[Tutorial 12: Organize a whole robot](12-robot-structure.md)
