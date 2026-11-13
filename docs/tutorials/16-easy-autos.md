# Tutorial 16: Easy autonomous

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** an autonomous written as a list of named steps: grab, drive, lift, drop, park.
The main track's autonomous is built on `EasyAuto` and is shorter still: [The autos](../five-files/05-autos.md).

**You will learn:** how `EasyAuto` turns an autonomous into a list of named steps, how to add INIT
choices, how red/blue mirroring works, how to keep time for parking, and how to read the report.

**You need:** a tuned drivetrain (tutorial 09: `RobotSettings` with `calibrationConfirmed = true`),
a motor `lift` and a servo `claw`. Do Tutorials 09, 10 and 14 first.

**Full program:** [`Tut16EasyAuto.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut16EasyAuto.java)
**Reference:** [auto.md](../modules/auto.md). Guide: [Easy autonomous modes](../guides/easy-autos.md).

---

## 1. Compare with tutorial 10

Tutorial 10's autonomous had: an INIT loop with a menu, mirroring by hand (`side`), a loop hook, loops
that wait with time limits, `finally` cleanup, a pose hand-off. Every autonomous needs all of them, and
they are the same every time. `EasyAuto` does all of it. You write only what is special about THIS
autonomous.

## 2. Tell it about the robot

`extends EasyAuto` means your class starts with everything `EasyAuto` already does. `EasyAuto` is
itself a `LinearOpMode`. There is no `runOpMode()` and no loop: you only fill in the parts it asks for.

```java
// preselectTeleOp = the TeleOp's exact name: the Driver Station gets it ready after autonomous.
@Autonomous(name = "Tutorial 16: Easy Auto", group = "Tutorials", preselectTeleOp = "Tutorial 20: Easy TeleOp") // (1)!
```

1. When autonomous ends, the Driver Station selects this TeleOp, so the drivers only press INIT.
   The name must match the TeleOp's name exactly. That TeleOp must also not be `@Disabled`.

```java
public class Tut16EasyAuto extends EasyAuto {
    private PositionMotor lift;
    private PositionServo claw;

    // drive, autonomous tuning and IMU, all from RobotSettings: the same as TeleOp (tutorial 20)
    @Override protected RobotConfig robotConfig() { return RobotSettings.config(); }

    @Override protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")...;
        claw = PositionServo.create(hardwareMap, "claw")...;
        return new Subsystems().add(lift, claw);
    }
```

`@Override` means "I am filling in a method `EasyAuto` asks for". `RobotSettings.config()` hands over
the drive, tuning and IMU settings from tutorial 09 as one object (a
[`RobotConfig`](../modules/core.md#robotconfig)). Tutorial 20's TeleOp uses the same one. `EasyAuto`
updates the mechanisms you return every loop, during every step. So a lift keeps holding while the robot
drives. `EasyAuto` also stops them all at the end, even after an error. Start the robot with the lift
DOWN: that position counts as 0.
The claw's `travelTime(0.3)` says the servo needs about 0.3 s for a full move. A servo cannot say where
it is. So with this number, a step knows when the claw should have finished moving.

## 3. INIT: placement and choices

```java
@Override protected String startPosition() {
    return "Back wall, touching the left edge of the 2nd tile, facing out";
}
@Override protected void addChoices(InitMenu menu) {
    menu.choice("Route", "Score", "Park only");
}
@Override protected double parkReserveSec() { return 4; }
```

The INIT screen shows the placement, so the drive team sets up right. It also shows the menu (alliance,
delay, Route, profile) and warnings. The distances in the steps only work from that exact spot.
`parkReserveSec()` keeps the last 4 seconds of the 30-second autonomous for parking.

## 4. The steps

```java
@Override protected void buildRoutine(AutoRoutine r) {
    r.step("Grab", claw.goToStep("CLOSED"))
     .move("Off the wall", 4, 0, 0.4);

    if (choice("Route").equals("Score")) {
        r.step("Lift starts up", lift.startStep("HIGH"))
         .move("To the goal", 24, 0, 0.6)
         .turn("Face the goal", 45, 0.5)
         .waitUntil("Lift is up", lift::isAtTarget, 2.0)
         .step("Drop", claw.goToStep("OPEN"))
         .waitFor("Let it fall", 0.3)
         .step("Lift down", lift.startStep("DOWN"))
         .turnTo("Square up", 0, 0.5);
    }

    r.thenPark()
     .right("Park", 20, 0.7);
}
```

Read it out loud: it IS the plan. Look at these points:

- **Labels** show on the Driver Station during the run and in the report. Make them say what the robot
  does.
- **`startStep` vs `goToStep`:** `lift.startStep("HIGH")` starts the lift, and the routine goes on at
  once. The lift rises while the robot drives. `claw.goToStep("CLOSED")` waits until the claw should be
  closed.
- **Moves and turns:** `move(name, inches, direction, power)`, with direction 0 = forward, 90 = left,
  180 = back, -90 = right. For turns, positive degrees = left too. Every angle counts counterclockwise.
  `turnTo(..., 0, ...)` faces the start direction.
  A mecanum robot can also drive AND turn at once: `moveWithTurn(name, inches, direction, heading, power)`.
  The robot turns on the way, so its direction counts from the start direction (like `turnTo`).
- **`waitUntil` has a time limit** (2 s). `lift::isAtTarget` is short for `() -> lift.isAtTarget()`:
  a question the routine asks over and over. When the lift never gets there, the routine goes on, and
  the report says "timed out".
- **Mirroring:** the routine is written for BLUE. On RED, `turn(45)` becomes -45 and `move(20, 90)`
  (left on BLUE) goes right. Some fields are turned half a turn between the alliances (BIOBUZZ). For those, override
  `mirrorForRed()` to return false: then the same route works for both.
- **Park:** everything before `thenPark()` stops when 4 s are left. Park always runs.

## Run it

1. In INIT, read the placement line. Choose BLUE, Route = Score. Press ▶ (start on the Driver Hub).
2. The screen shows "Step 4/11 To the goal" and the time left.
3. At the end, read the report. Check that every step says "done".
4. Run as RED: the robot turns the other way and parks to the left.
5. Choose "Park only": the robot only grabs, moves off the wall and parks.
6. The menu stops at 10 s. Override `maxStartDelaySec()` (write your own version of it, with
   `@Override`) to return 25. Then set a 25 s start delay. The routine skips the main steps
   ("skipped (time for park)"), but the robot still parks.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "AUTONOMOUS IS OFF: the drive is not tuned yet" in INIT | Tune with tutorial 09, then set `calibrationConfirmed` true in `RobotSettings.auto()`. |
| Lift sags while driving | The lift is not in `createSubsystems()`. |
| A step says STALLED | Something was in the way, or the power is too low to move the robot. |
| "timed out" on Lift is up | The lift is slow. Tune it, or give `waitUntil` more time. |
| RED does something strange | A step uses a number that must not be mirrored. Use `run(...)` or `alliance()`. |

## Try this

1. Add a choice "Pieces: 1/2" and a second scoring trip for 2.
2. Use `rememberHeading` before scoring and `returnToHeading` after.
3. Make a second autonomous class with a different route. Share the robot setup through a base class
   (like the [starter kit](../starter-kit.md)'s `StarterAuto`).

## Next

[Tutorial 17: Launcher](17-launcher.md)
