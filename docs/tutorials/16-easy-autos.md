# Tutorial 16: Easy autonomous

**You will learn:** how `EasyAuto` turns an autonomous into a list of named steps, how to add INIT
choices, how red/blue mirroring works, how to keep time for parking, and how to read the report.

**You need:** a tuned drivetrain (tutorial 09: `RobotSettings` with `calibrationConfirmed = true`),
a motor `lift`, a servo `claw`. Tutorials 09, 10, 14.

**Full program:** [`Tut16EasyAuto.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut16EasyAuto.java)
**Reference:** [auto.md](../modules/auto.md). Guide: [Easy autonomous modes](../guides/easy-autos.md).

---

## 1. Compare with tutorial 10

Tutorial 10's autonomous had: an INIT loop with a menu, mirroring by hand (`side`), a loop hook,
waiting loops with time limits, `finally` cleanup, a pose hand-off. All needed, all the same in
every autonomous. `EasyAuto` does all of it; you write only what is special about THIS autonomous.

## 2. Tell it about the robot

```java
public class Tut16EasyAuto extends EasyAuto {
    private PositionMotor lift;
    private PositionServo claw;

    @Override protected DriveConfig driveConfig() { return RobotSettings.drive(); }
    @Override protected AutoDriveConfig autoDriveConfig() { return RobotSettings.auto(); }
    @Override protected IMU createImu() { return RobotSettings.imu(hardwareMap); }

    @Override protected Subsystems createSubsystems() {
        lift = PositionMotor.create(hardwareMap, "lift")...;
        claw = PositionServo.create(hardwareMap, "claw")...;
        return new Subsystems().add(lift, claw);
    }
```

`@Override` means "I am filling in a method `EasyAuto` asks for". The mechanisms you return are
updated every loop during every step, so a lift keeps holding while the robot drives.

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
Route, profile), and warnings.

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
- **`waitUntil` has a time limit** (2 s). If the lift never gets there, the routine goes on and the
  report says "timed out".
- **Mirroring:** written for BLUE. On RED, `turn(45)` becomes -45 and `move(20, 90)` goes left.
- **Park:** everything before `thenPark()` stops when 4 s are left; Park always runs.

## Run it

1. INIT: read the placement line. Choose BLUE, Route = Score. START.
2. The screen shows "Step 3/10 To the goal" and the time left.
3. At the end: the report. Every step "done"? 
4. Run as RED: it turns the other way and parks to the left.
5. Choose "Park only": just grab, off the wall, park.
6. Set a 25 s start delay: the main steps are skipped ("skipped (time for park)") but it still parks.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "calibrationConfirmed is false" in INIT | tune with tutorial 09, then set it true in `RobotSettings.auto()` |
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
