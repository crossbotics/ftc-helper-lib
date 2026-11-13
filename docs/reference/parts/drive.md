# Drive

The wheels that move the robot: a mecanum drive or a tank drive. Every robot has one. Learn it step by
step: [Robot.java, the drive](../../five-files/01-robot-java.md#the-drive-attribute-by-attribute).

## In Robot.java

```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96).imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
drive(Drive.tank("left_drive", "right_drive").motor("gobilda-5203-312").wheelMm(96).reversed("right_drive"));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Drive.mecanum(fl, fr, bl, br)` | **yes**, one of the three | a mecanum drive and its four motor names | - |
| `Drive.tank(left, right)` | **yes**, one of the three | a tank drive on two motors | - |
| `Drive.tank(fl, fr, bl, br)` | **yes**, one of the three | a tank drive on four motors | - |
| `.motor(id)` | recommended | the drive motor's catalog id ([parts catalog](../../guides/parts-catalog.md)). The other parts use `part(id)` | `gobilda-5203-312` |
| `.wheelMm(mm)` | recommended | the wheel diameter, mm | 96 |
| `.wheel(id)` | optional | the wheel by its catalog id ([Shapes](../robot-java.md#shapes-and-parts)), like `"gobilda-hogback-96"` or `"gobilda-gripforce-mecanum-104"`. The library takes its diameter from the catalog | `wheelMm` |
| `.gearRatio(r)` | optional | motor turns per wheel turn | 1 |
| `.wheelsAtMm(track, wheelBase)` | optional | for the simulator: the distance between wheel centers side to side, and front to back, mm | a guess |
| `.reversed(names...)` | optional | exactly these motors run reversed | the left side is reversed |
| `.imu(name, logo, usb)` | optional | the IMU, and how the hub is mounted | "imu", UP, FORWARD |
| `.noImu()` | optional | the robot has no IMU | the IMU above |

The drive cannot be `optional()`: a robot always drives.

## In Tunables.java

<!-- tunables: Drive -->
Section `Drive` (a fixed name). The drive reads it once, at INIT.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-drive -->
```java title="Tunables.java"
public static class Drive {                                     // a fixed name: always Drive
    public static double maxPower = 0.6;                        // top wheel power: slow for first drives (1.0)
    public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (same as maxPower)
    public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
    public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
    public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
    public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
    public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
    public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `maxPower` | the most power the wheels get (COMPETITION profile) | 0 to 1 | 1.0 | yes, next INIT | test | the robot is too fast for the drivers |
| `practiceMaxPower` | the most power in the PRACTICE profile | 0 to 1 | the same as `maxPower` | yes, next INIT | test | a brand-new driver, for a day; drivers should practice at match speed |
| `slowSpeed` | the most power while the driver pulls the slow trigger all the way (a small squeeze slows it a little) | 0 to 1 | 0.3 | yes, next INIT | test | fine moves are too slow or too fast |
| `strafeCorrection` | mecanum wheels slip sideways. This makes a sideways move in autonomous as long as asked | times | 1.15 | yes, next INIT | Drive Tuner | sideways moves are short or long |
| `strafeCorrectionLeft` | the same for moves to the LEFT, when the robot slips differently that way (when you leave it out, `strafeCorrection` counts both ways) | times | `strafeCorrection` | yes, next INIT | Drive Tuner (Strafe left) | moves to the left are short or long, moves to the right are right |
| `turnKp` | autonomous turn: how hard it turns per degree off | power per degree | 0.012 | yes, next INIT | Move Tuner (Turns) | turns stop short (more) or wobble (less) |
| `turnKd` | autonomous turn: brakes the turn as it gets close | power per degree/s | 0.003 | yes, next INIT | Move Tuner (Turns) | turns overshoot (more) |
| `turnMinPower` | autonomous turn: the least turn power while the heading is still off, so a turn does not stop a little short. 0 = off (`driveTo` and routes then use 0.08) | 0 to 0.3 | 0 | yes, next INIT | Move Tuner (Turns) | turns stop a few degrees short |
| `turnToleranceDeg` | autonomous turn: done when the heading is this close (for 0.15 s) | degrees | 1.0 | yes, next INIT | test | turns take long to end (more); the heading must be closer (less) |
| `moveMinPower` | the least power that still moves the robot (autonomous) | 0 to 1 | 0.30 | yes, next INIT | Move Tuner (Creep) | the robot stops just before the end of a move |
| `slowDownIn` | autonomous: a short move can slide past its end. With this, every move slows down over at least its last this-many inches (and also over its last 10%). Then a short move reaches the end about as slowly as a long one, and slides about the same. `driveTo` and routes already slow down over their last 8 in | inches, 0 to 48 | 0 (only the last 10%) | yes, next INIT | Move Tuner (Stops) | short moves slide past the end; moves at different speeds end in different places |
| `moveTurnScale` | `moveWithTurn`: the heading correction while moving is multiplied by this | 0 to 1 | 0.60 | yes, next INIT | Move Tuner (Move and turn) | the turn is not done when the move ends (more); the heading swings past its target (less) |
| `moveMaxTurnPower` | `moveWithTurn` and routes: the most turn power while moving | 0.05 to 1 | 0.35 | yes, next INIT | Move Tuner (Move and turn) | the turn lags even at `moveTurnScale` 1 |
| `calibrated` | autonomous drives the robot only when this is `true`. Until then INIT says `AUTONOMOUS IS OFF` (the simulator runs it anyway, and says so) | true/false | false | no | Drive Tuner, then set it | after the Drive Tuner, once moves are right |

**`calibrated`:** first tune with the Drive Tuner ([Tune the drive first](../../five-files/05-autos.md#tune-the-drive-first)).
The Drive Tuner and the Shot Tuner need the whole robot:
`@Override protected RobotDefinition robot() { return new Robot(); }`. Every finder shows `Tunables.`
lines for you to copy ([finders](../../tuning/README.md)).

## Commands

| Command | What it does |
| --- | --- |
| `robot.reverseDriveMode()` | a mode where the back of the robot acts as the front: it flips forward and strafe. It does not flip the turn. Field-centric driving ignores it. It is off at ▶ |

| Question | What it tells you |
| --- | --- |
| `isMecanum()` | whether it is a mecanum drive |
| `motorNames()` | the motor names, in order |
| `isReversed(name)` | whether that motor runs reversed |

In an auto, the drive's steps are `drive`, `strafe`, `turn` and the others ([Steps for autos](../steps.md)).

## Buttons

The drive's buttons are the library's own, on gamepad 1: the sticks, slow, and field-centric. You do not add
them. To move them, see [Controls.java](../controls.md).

**Reverse drive** is one line in `Controls.java`:

```java title="Controls.java"
g1.x.toggle(robot.reverseDriveMode()).rumble();
```

While it is on, the Driver Station says `Drive : REVERSED: the back is the front`.

---

**Look it up:** [`Drive` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Drive.html)
(every method). **The other parts:** [Reference](../README.md).
