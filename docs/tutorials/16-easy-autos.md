# Tutorial 16: Easy autonomous

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

With the five files, your autos already run on `EasyAuto`: [Building autos](../building-autos/README.md).
Build one by hand here to see what `EasyAuto` does inside, and to write an auto for a robot without the
five files.

**You will build:** an autonomous written as a list of named steps: grab, drive, lift, drop, park.

**You need:** a tuned drivetrain (tutorial 09: `RobotSettings` with `calibrationConfirmed = true`),
a motor `lift` and a servo `claw`.

**Full program:** [`Tut16EasyAuto.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut16EasyAuto.java)

---

## 1. Compare with tutorial 10

Tutorial 10's autonomous had an INIT menu, mirroring by hand (`side`), a loop hook, waits with time
limits, `finally` cleanup and a pose hand-off. Every autonomous needs them, and they are the same every
time. `EasyAuto` does all of it. You write only what is special about THIS autonomous.

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
the settings from tutorial 09 as one [`RobotConfig`](../modules/core.md#robotconfig). Tutorial 20's
TeleOp uses the same one.

`EasyAuto` updates the mechanisms you return every loop, during every step. So a lift keeps holding while
the robot drives. `EasyAuto` also stops them all at the end, even after an error. Start the robot with the
lift DOWN: that position counts as 0.

A servo cannot say where it is. So the claw's `travelTime(0.3)` tells a step how long a full move takes,
and the step knows when the claw should have finished.

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

The distances only work from one exact spot, so the INIT screen shows the placement. It also shows the
menu (alliance, delay, Route) and warnings. `parkReserveSec()` keeps the last 4 seconds for parking.

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

Read it out loud: it IS the plan. The labels show on the Driver Station during the run and in the
report.

- **`startStep` vs `goToStep`:** `startStep` starts the lift and goes on at once, so the lift rises while
  the robot drives. `goToStep` waits until the claw should be closed.
- **Directions:** `move(name, inches, direction, power)`: 0 = forward, 90 = left, -90 = right. Every
  angle counts counterclockwise, so positive turn degrees = left.
- **`waitUntil` has a time limit** (2 s). `lift::isAtTarget` is short for `() -> lift.isAtTarget()`. When
  the lift never gets there, the routine goes on, and the report says "timed out".
- **Mirroring:** the routine is written for BLUE. On RED, `EasyAuto` flips turns and left/right moves.
- **Park:** everything before `thenPark()` stops when 4 s are left. Park always runs.

How each of these works, with examples: [Put steps together](../building-autos/02-put-steps-together.md).
Every step: [Steps](../reference/steps.md).

## Run it

1. In INIT, read the placement line. Choose BLUE, Route = Score. Press ▶ (start on the Driver Hub).
2. The screen shows each step and the time left. At the end, check that every step in the report says
   "done".
3. Run as RED: the robot turns the other way and parks to the left. Then try "Park only".

The first run on the real robot: [Put steps together](../building-autos/02-put-steps-together.md#the-first-run-on-the-robot).

## If something goes wrong

| Problem | Fix |
| --- | --- |
| "AUTONOMOUS IS OFF: the drive is not tuned yet" in INIT | Tune with tutorial 09, then set `calibrationConfirmed` true in `RobotSettings.auto()`. |
| Lift sags while driving | The lift is not in `createSubsystems()`. |
| A step says STALLED | Something was in the way, or the power is too low to move the robot. |
| RED does something strange | A step uses a number that must not be mirrored. Use `run(...)` or `alliance()`. |

## Try this

1. Add a choice "Pieces: 1/2" and a second scoring trip for 2.
2. Use `rememberHeading` before scoring and `returnToHeading` after.
3. Override `maxStartDelaySec()` to return 25, and set a 25 s start delay. The robot still parks.
4. Make a second autonomous class with a different route. Share the robot setup through a base class
   (like the [starter kit](../starter-kit.md)'s `StarterAuto`).

---

**Next:** [Tutorial 17: Launcher](17-launcher.md)
**Look it up:** [auto.md](../modules/auto.md)
