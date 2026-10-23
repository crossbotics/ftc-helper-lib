# Tutorial 16: Easy autonomous

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** an autonomous written as a list of named steps: grab, drive, lift, drop, park.
The main track's autonomous is built on `EasyAuto` and is shorter still: [Your first autonomous](start/06-first-autonomous.md).

**You will learn:** how `EasyAuto` turns an autonomous into a list of named steps, how to add INIT
choices, how red/blue mirroring works, how to keep time for parking, and how to read the report.

**You need:** a tuned drivetrain (tutorial 09: `RobotSettings` with `calibrationConfirmed = true`),
a motor `lift`, a servo `claw`. Tutorials 09, 10, 14.

**Full program:** [`Tut16EasyAuto.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut16EasyAuto.java)
**Reference:** [auto.md](../modules/auto.md). Guide: [Easy autonomous modes](../guides/easy-autos.md).

---

## 1. Compare with tutorial 10

Tutorial 10's autonomous had: an INIT loop with a menu, mirroring by hand (`side`), a loop hook,
waiting loops with time limits, `finally` cleanup, a pose hand-off. All needed, all the same in
every autonomous. `EasyAuto` does all of it; you write only what is special about THIS autonomous.

## 2. Tell it about the robot

`extends EasyAuto` means your class starts with everything `EasyAuto` already does (it is itself a
`LinearOpMode`). There is no `runOpMode()` and no loop: you only fill in the parts it asks for.

```java
// preselectTeleOp = the TeleOp's exact name: the Driver Station gets it ready after autonomous.
@Autonomous(name = "Tutorial 16: Easy Auto", group = "Tutorials", preselectTeleOp = "Tutorial 20: Easy TeleOp") // (1)!
```

1. When autonomous ends, the Driver Station selects this TeleOp, so the drivers only press INIT.
   The name must match the TeleOp's name exactly, and that TeleOp must not be `@Disabled` either.

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

`@Override` means "I am filling in a method `EasyAuto` asks for". `RobotSettings.config()` hands
over the drive, tuning and IMU settings from tutorial 09 as one object (a
[`RobotConfig`](../modules/core.md#robotconfig)); tutorial 20's TeleOp uses the same one. The mechanisms you return are
updated every loop during every step, so a lift keeps holding while the robot drives, and they all
stop at the end, even after an error. Start the robot with the lift DOWN: that position counts as 0.
The claw's `travelTime(0.3)` says the servo needs about 0.3 s for a full move. A servo cannot say
where it is, so this is how a step knows when the claw should have finished moving.

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

The INIT screen shows the placement (so the drive team sets up right), the menu (alliance, delay,
Route, profile), and warnings. The distances in the steps only work from that exact spot.
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
     .move("Park", 20, 90, 0.7);
}
```

Read it out loud: it IS the plan. Things to notice:

- **Labels** show on the Driver Station during the run and in the report. Make them say what the robot does.
- **`startStep` vs `goToStep`:** `lift.startStep("HIGH")` starts the lift and goes on at once; it
  rises while the robot drives. `claw.goToStep("CLOSED")` waits until the claw should be closed.
- **Moves and turns:** `move(name, inches, direction, power)`, with direction 0 = forward, 90 = right,
  180 = back, -90 = left. Turns: positive degrees = left. `turnTo(..., 0, ...)` faces the start direction.
- **`waitUntil` has a time limit** (2 s). `lift::isAtTarget` is short for `() -> lift.isAtTarget()`:
  a question the routine asks over and over. If the lift never gets there, the routine goes on and the
  report says "timed out".
- **Mirroring:** written for BLUE. On RED, `turn(45)` becomes -45 and `move(20, 90)` goes left. On a field
  turned half a turn between the alliances (BIOBUZZ), override `mirrorForRed()` to return false: the same
  route works for both.
- **Park:** everything before `thenPark()` stops when 4 s are left; Park always runs.

## Run it

1. INIT: read the placement line. Choose BLUE, Route = Score. START.
2. The screen shows "Step 4/11 To the goal" and the time left.
3. At the end: the report. Is every step "done"?
4. Run as RED: it turns the other way and parks to the left.
5. Choose "Park only": just grab, off the wall, park.
6. Override `maxStartDelaySec()` (write your own version of it, with `@Override`) to return 25 (the menu stops at 10 s), then set a 25 s start delay: the
   main steps are skipped ("skipped (time for park)") but it still parks.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "AUTONOMOUS IS OFF: the drive is not tuned yet" in INIT | tune with tutorial 09, then set `calibrationConfirmed` true in `RobotSettings.auto()` |
| Lift sags while driving | the lift is not in `createSubsystems()` |
| A step says STALLED | something was in the way, or the power is too low to move |
| "timed out" on Lift is up | the lift is slow: tune it, or give `waitUntil` more time |
| RED does something strange | a step uses a number that should not be mirrored: use `run(...)` or `alliance()` |

## Try this

1. Add a choice "Pieces: 1/2" and a second scoring trip for 2.
2. Use `rememberHeading` before scoring and `returnToHeading` after.
3. Make a second autonomous class with a different route, sharing the robot setup through a base
   class (like the [starter kit](../starter-kit.md)'s `StarterAuto`).

## Next

[Tutorial 17: Launcher](17-launcher.md)
