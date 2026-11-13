# Tutorial 10: Autonomous with mechanisms

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, steps start a part while the robot drives, and the INIT menu picks red or blue: [Put steps together](../building-autos/02-put-steps-together.md).
Build it by hand to see how a part keeps working while a move waits, and how to wait safely.

**You will build:** an autonomous that raises the lift while it drives, with alliance and start delay
chosen on the gamepad in INIT.

**You need:** a tuned drivetrain (Tutorial 09) and the lift from Tutorial 08.

**Full program:** [`Tut10AutoWithLift.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut10AutoWithLift.java)

---

## 1. The lift

The lift is the one from Tutorial 08, with the numbers from Tutorial 07:

```java
// "lift" = the name in the robot configuration. The encoder starts at 0 here.
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();
// Soft limits 0 to 2600 ticks, slow near the ends. The lift also needs a real hard stop.
SafeMotor lift = new SafeMotor(liftMotor).setLimits(0, 2600).setSlowZone(250, 0.2); // (1)!
PidController liftPid = new PidController(0.004, 0, 0.0002, 0.05).setTolerance(20);   // tutorial 07 (2)!
```

1. 2600 is a bit above `LIFT_UP` (2400). So a wrong target or a bug cannot drive the lift into its
   top. Near an end, the power goes down to at most 0.2, so the lift does not slam.
   **Safety:** the lift also needs a physical hard stop ([Tutorial 08](08-safe-lift.md#1-what-can-go-wrong-with-a-lift)).
2. kP, kI, kD, kF and the tolerance in ticks, from Tutorial 07.

Start the robot with the lift DOWN: `resetEncoder()` makes the current position 0.

## 2. The problem: moves wait

While `auto.move(...)` runs, none of YOUR code runs. A lift PID in your loop gets no updates, and the
lift sags or overshoots.

## 3. The loop hook

A **hook** is a job you hand to someone else's code, so it runs at the right moment.
Give `AutoDrive` a job to run once per loop **inside** every move and turn:

```java
auto.setLoopHook(() -> {
    lift.update(); // (1)!
    lift.moveTo(liftTarget, liftPid); // (2)!
});
```

1. First: read the lift's switch and position.
2. Then: PID power toward the target, with the limits applied.

Now the lift keeps working during every move. To raise the lift while the robot drives, change the
target and then drive:

```java
liftTarget = LIFT_UP;                 // start raising...
auto.move(30, 0, 0.6);                // ...while driving
```

`liftTarget` is a field, because the hook (a lambda) reads it. Keep the hook quick: no `sleep`, and no
loops that wait.

## 4. Choose the plan in INIT

Before ▶ (start on the Driver Hub), the drivers make choices. Use a small `ControllerBindings` in an INIT
loop:

```java
ControllerBindings setup = new ControllerBindings()
        .bindPress(Pad.G1, Btn.X, () -> red = false)
        .bindPress(Pad.G1, Btn.B, () -> red = true)
        .bindPress(Pad.G1, Btn.DPAD_UP, () -> delaySec = Math.min(delaySec + 1, 10))
        .bindPress(Pad.G1, Btn.DPAD_DOWN, () -> delaySec = Math.max(delaySec - 1, 0));
while (opModeInInit()) {
    setup.update(gamepad1, gamepad2);
    telemetry.addData("Alliance (X blue / B red)", red ? "RED" : "BLUE");
    telemetry.addData("Start delay (D-pad)", "%.0f s", delaySec);
    telemetry.update();
}
if (isStopRequested()) return;
```

- `opModeInInit()` is true until ▶ (or STOP). Read buttons in INIT, but never move: that is the
  library's safety rule.
- `Math.min` and `Math.max` keep the delay between 0 and 10 seconds. A delay keeps you out of your
  partner's way.

## 5. Mirror the route for red and blue

The red side of the field is often a mirror image of blue. Write the route once, for blue. Then flip
left and right for red:

```java
double side = red ? -1 : 1;
auto.turn(90 * side, 0.5);     // blue: left 90, red: right 90
```

Mirror sideways moves the same way: `auto.move(12, 90 * side, 0.5)`. Forward and back do not change.
Check your game's field first: some fields are rotated, not mirrored. For a rotated field, flip the
forward and back parts too.

## 6. React to a stuck move

Every move returns how it ended:

```java
AutoDrive.Result r = auto.move(30, 0, 0.6);    // ...while driving
if (r == AutoDrive.Result.STALLED) {
    auto.move(4, 180, 0.4);                    // blocked: back off and try once more
    auto.move(6, 0, 0.4);
}
```

`STALLED` means something blocks the robot. To push on wastes time and can burn out the motors. The other results are `DONE`, `TIMED_OUT` and
`STOPPED` (the match ended).

## 7. Wait for a mechanism, safely

Between moves, the hook does not run. To wait for the lift, keep it working yourself, and always add a
time limit:

```java
ElapsedTime wait = new ElapsedTime();
while (opModeIsActive() && !liftPid.atTarget() && wait.seconds() < 2.0) {
    lift.update();
    lift.moveTo(liftTarget, liftPid);
    idle();
}
```

Use three conditions, every time: **the match is still on, the job is not done, the time is not up.**
A loop without a time limit can wait forever and waste the whole autonomous.

**Do not use `sleep(...)`**, also not for a start delay. While the code sleeps, nothing runs: no
telemetry, no mechanism updates. Wait with a loop instead, like the start delay in the program:

```java
ElapsedTime delay = new ElapsedTime();
while (opModeIsActive() && delay.seconds() < delaySec) {
    telemetry.addData("Waiting", "%.1f s", delaySec - delay.seconds());
    telemetry.update();
    idle();
}
```


## 8. Clean up and hand over to TeleOp

```java
} finally {
    lift.stop();
    PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
}
```

`finally` runs also when the match ends in the middle of a move. A **pose** is the robot's x, y and
heading. `PoseStore` keeps the saved pose until TeleOp reads it. Here the
program knows only the heading. With odometry, you save x and y too:
[Add odometry: TeleOp starts where autonomous ended](../add-to-robot/09-odometry.md#teleop-starts-where-autonomous-ended).

## Run it

1. Press INIT. Press B (red), then D-pad up twice (2 s delay). The screen shows your choices.
2. Press ▶. The robot waits 2 s, drives forward while the lift rises, turns RIGHT (red), and drives on.
3. Run it again as blue: the robot turns LEFT.
4. Put a box in the way of the first move: the robot backs off and tries again.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift sags during moves | The program sets no loop hook, or the hook does not call `moveTo`. |
| Lift keeps going after the program ends | Put `lift.stop()` in `finally`. |
| Choices do not change in INIT | Put `setup.update(gamepad1, gamepad2)` inside the `opModeInInit()` loop. |
| Red and blue go the same way | A turn or sideways move does not use `side`. |
| Waits forever | A loop that waits has no time limit. Add `&& timer.seconds() < ...`. |

## Try this

1. Add a choice of two routes ("near" and "far") on the D-pad left/right.
2. Lower the lift on the way back without stopping: set `liftTarget = LIFT_DOWN` before the move.
3. When the first move is `STALLED` twice, skip to "park" instead of trying the rest.

---

**Next:** [Tutorial 11: Driver assists](11-driver-assists.md)

**Look it up:** [drive.md: AutoDrive](../modules/drive.md#autodrive), [safety.md: SafeMotor](../modules/safety.md#safemotor), [util.md: PoseStore](../modules/util.md#posestore)
