# Tutorial 10: Autonomous with mechanisms

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/jasoncross/ftc-helper-lib/issues).

**You will learn:** how to keep a lift working WHILE the robot drives, how to choose alliance
and a start delay in INIT with the gamepad, how to mirror a route for red and blue, how to react
when a move gets stuck, how to wait for a mechanism safely, and how to hand information to TeleOp.

**You need:** a tuned drivetrain (Tutorial 09) and the lift from Tutorial 08.

**Full program:** [`Tut10AutoWithLift.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut10AutoWithLift.java)
**Reference:** [drive.md: AutoDrive](../modules/drive.md#autodrive), [safety.md](../modules/safety.md), [util.md: PoseStore](../modules/util.md#posestore)

---

## 1. The problem: moves wait

`auto.move(30, 0, 0.6)` does not return until the move is done. While it runs, none of YOUR
code runs: a lift PID in your loop would stop being updated, and the lift would sag or overshoot.

## 2. The loop hook

Give `AutoDrive` a job to run once per loop **inside** every move and turn:

```java
auto.setLoopHook(() -> {
    lift.update();
    lift.moveTo(liftTarget, liftPid);
});
```

Now the lift keeps working during every move. To raise the lift while driving, just change the
target and then drive:

```java
liftTarget = LIFT_UP;                 // start raising...
auto.move(30, 0, 0.6);                // ...while driving
```

`liftTarget` is a field, because the hook (a lambda) reads it.

Keep the hook quick: no `sleep`, no waiting loops.

## 3. Choose the plan in INIT

Before START, the drivers set up the robot and choose things. Use a small `ControllerBindings`
in an INIT loop:

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

`opModeInInit()` is true until START (or STOP). The robot must not move in INIT; reading buttons
and showing choices is fine. Blue = X (the blue button on many pads), red = B.

A start delay lets you stay out of your partner's way.

## 4. Mirror the route for red and blue

The red side of the field is usually a mirror image of blue. Write the route once, for blue, and
flip left/right for red:

```java
double side = red ? -1 : 1;
auto.turn(90 * side, 0.5);     // blue: left 90, red: right 90
```

Mirror sideways moves the same way: `auto.move(12, 90 * side, 0.5)`. Forward and back do not change.

Check your game's field first: some fields are rotated, not mirrored. Then flip the forward/back
parts too.

## 5. React to a stuck move

Every move returns how it ended:

```java
AutoDrive.Result r = auto.move(30, 0, 0.6);
if (r == AutoDrive.Result.STALLED) {
    auto.move(4, 180, 0.4);    // blocked: back off and try once more
    auto.move(6, 0, 0.4);
}
```

`STALLED` means the robot stopped making progress (another robot, a wall, a game piece).
Other results: `DONE`, `TIMED_OUT`, `STOPPED` (the match ended).

## 6. Wait for a mechanism, safely

Between moves, the hook is not running. If you need to wait for the lift, keep it working
yourself, and always add a time limit:

```java
ElapsedTime wait = new ElapsedTime();
while (opModeIsActive() && !liftPid.atTarget() && wait.seconds() < 2.0) {
    lift.update();
    lift.moveTo(liftTarget, liftPid);
}
```

Three conditions, every time: **the match is still on, the job is not done, the time is not up.**
A loop without a time limit can wait forever and waste the whole autonomous.

**Do not use `sleep(...)`**, even for a start delay: while the code sleeps, nothing runs (no
telemetry, no mechanism updates). Wait with a loop instead, like the start delay in the program:

```java
ElapsedTime delay = new ElapsedTime();
while (opModeIsActive() && delay.seconds() < delaySec) {
    telemetry.addData("Waiting", "%.1f s", delaySec - delay.seconds());
    telemetry.update();
    idle();
}
```

## 7. Clean up and hand over to TeleOp

```java
} finally {
    lift.stop();
    PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
}
```

`finally` runs even if the match ends in the middle of a move. `PoseStore` keeps the saved pose
until TeleOp reads it with `PoseStore.consume()` (Tutorial 12 shows this). Here we only know the
heading; with odometry pods you would save x and y too.

## Run it

1. INIT: press B (red), D-pad up twice (2 s delay). The screen shows your choices.
2. START: waits 2 s, drives forward while the lift rises, turns RIGHT (red), drives on.
3. Run it again as blue: it turns LEFT.
4. Put a box in the way of the first move: it backs off and tries again.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Lift sags during moves | The loop hook is not set, or it does not call `moveTo`. |
| Lift keeps going after the program ends | `lift.stop()` must be in `finally`. |
| Choices do not change in INIT | `setup.update(gamepad1, gamepad2)` must be inside the `opModeInInit()` loop. |
| Red and blue go the same way | `side` is not used in a turn or sideways move. |
| Waits forever | A waiting loop without a time limit. Add `&& timer.seconds() < ...`. |

## Try this

1. Add a choice of two routes ("near" and "far") on the D-pad left/right.
2. Lower the lift on the way back without stopping: set `liftTarget = LIFT_DOWN` before the move.
3. If the first move is `STALLED` twice, skip to "park" instead of trying the rest.

## Next

[Tutorial 11: Driver assists](11-driver-assists.md)
