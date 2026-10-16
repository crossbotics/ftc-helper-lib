# Drive module

Package: `io.github.jasoncross.ftchelper.drive`

Learn it hands-on: [04 Mecanum](../tutorials/04-drive-mecanum.md), [05 Tank](../tutorials/05-drive-tank.md), [09 First autonomous](../tutorials/09-first-auto.md), [10 Autonomous with mechanisms](../tutorials/10-auto-with-mechanisms.md).

Drives a **mecanum** chassis (4 mecanum wheels) or a **standard** chassis (also called tank or
differential drive: normal wheels, a left side and a right side, 2 or 4 motors), in TeleOp and in autonomous.
Signs used here: [Signs and units](../conventions.md). Read it first.

| Your robot | TeleOp class | Autonomous class | Config |
| --- | --- | --- | --- |
| Mecanum, 4 motors | `MecanumDrive` | `AutoDrive` | `DriveConfig.mecanum()` |
| Standard, 4 motors | `TankDrive` | `AutoDrive` | `DriveConfig.tank()` |
| Standard, 2 motors | `TankDrive` | `AutoDrive` | `DriveConfig.tankTwoMotor()` |

| Class | Use it to |
| --- | --- |
| [`DriveConfig`](#driveconfig) | choose the drive type, name the motors, set their directions and a power limit |
| [`MecanumDrive`](#mecanumdrive) | drive a mecanum robot in TeleOp (robot-centric or field-centric) |
| [`TankDrive`](#tankdrive) | drive a standard (tank) robot in TeleOp (arcade or tank style) |
| [`ReverseDrive`](#reversedrive) | a mode where the back of the robot acts as the front |
| [`AutoDriveConfig`](#autodriveconfig) | tuning numbers for autonomous moves |
| [`AutoDrive`](#autodrive) | autonomous: drive a distance, turn an angle, turn to a heading |
| [`MecanumMixer`](#mecanummixer) | the mecanum wheel-power math (no hardware) |
| [`DifferentialMixer`](#differentialmixer) | the standard-drive left/right math (no hardware) |
| [`MotionMath`](#motionmath) | the speed-taper math for autonomous moves |
| [`StallDetector`](#stalldetector) | tell when a move is stuck |

Also in this page: [Check motor directions](#check-motor-directions) and [Tune AutoDrive](#tune-autodrive).

---

## DriveConfig

A small settings object. Start from the factory method for your drivetrain, then change what
is different on your robot.

```java
DriveConfig driveConfig = DriveConfig.mecanum();   // or DriveConfig.tank(), DriveConfig.tankTwoMotor()
driveConfig.frontLeft = "fl";                      // only if your names are different
driveConfig.powerLimit = 0.6;
```

| Factory | Type | Motor names |
| --- | --- | --- |
| `DriveConfig.mecanum()` (same as `new DriveConfig()`) | `MECANUM` | `front_left`, `front_right`, `back_left`, `back_right` |
| `DriveConfig.tank()` | `TANK` | `front_left`, `front_right`, `back_left`, `back_right` |
| `DriveConfig.tankTwoMotor()` | `TANK` | `left_drive`, `right_drive` (the FTC sample names); back names are `null` |

| Field | Default | Meaning |
| --- | --- | --- |
| `type` | `MECANUM` | `DriveType.MECANUM` or `DriveType.TANK` |
| `frontLeft`, `frontRight`, `backLeft`, `backRight` | `"front_left"` ... `"back_right"` | configuration names of the motors. For a 2-motor tank, use `frontLeft`/`frontRight` and set both back names to `null`. |
| `frontLeftReversed` ... `backRightReversed` | left = `true`, right = `false` | which motors are reversed |
| `powerLimit` | `1.0` | the most power any wheel ever gets. Use 0.4 on a new robot. |
| `brake` | `true` | wheels hold still at power 0 |

| Method | What it does |
| --- | --- |
| `hasBackMotors()` | true if both back motor names are set |
| `validate()` | checks the settings make sense (the drive classes call it for you). A mecanum drive needs 4 names; back names must be both set or both `null`. |

Use the SAME `DriveConfig` in TeleOp and autonomous. A good way: one class in TeamCode with a
`static DriveConfig make()` method that both use.

---

## MecanumDrive

```java
MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);
waitForStart();
while (opModeIsActive()) {
    drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
}
drive.stop();
```

The constructor gets the four motors, sets direction and brake, uses plain power
(RUN_WITHOUT_ENCODER), and sets power to 0. A wrong motor name gives a clear error.

| Method | What it does |
| --- | --- |
| `drive(forward, strafeRight, turnClockwise)` | robot-centric drive. Each value -1 to 1. |
| `drive(forward, strafeRight, turnClockwise, speedScale)` | same, times a speed multiplier 0 to 1 (for a slow mode) |
| `driveFieldCentric(fieldForward, fieldRight, turnClockwise, headingCcwDeg)` | field-centric drive (see below) |
| `driveFieldCentric(..., headingCcwDeg, speedScale)` | same, with a speed multiplier |
| `stop()` | all wheels to 0 |
| `setPowers(double[4])` | set each wheel yourself (order FL, FR, BL, BR). For testing. The power limit still applies. |
| `setBrake(boolean)` | `true` = hold at 0 power, `false` = coast |
| `getMotors()` | the four `DcMotorEx` motors (FL, FR, BL, BR) |
| `getConfig()` | the `DriveConfig` |

If the numbers add up to more than 1 on a wheel (for example full forward + full strafe), all four
wheels are scaled down together. The robot keeps the direction you asked for.

### Robot-centric or field-centric?

- **Robot-centric:** stick up = robot drives where IT faces. Simple. If the robot faces you,
  stick up drives it toward you.
- **Field-centric:** stick up = robot drives AWAY FROM YOU, whichever way it faces. Easier for
  new drivers. Needs the IMU.

```java
double heading = ImuSetup.headingDeg(imu);
drive.driveFieldCentric(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x, heading);
```

The IMU heading must be 0 when the robot faces away from the driver. Give the driver a button
to fix it during a match: `controls.bindPress(Pad.G1, Btn.BACK, imu::resetYaw)` (point the robot
away from you, then press).

---

## TankDrive

For a standard (tank / differential) drive with 2 or 4 motors. It cannot move sideways.

```java
TankDrive drive = new TankDrive(hardwareMap, DriveConfig.tankTwoMotor());
waitForStart();
while (opModeIsActive()) {
    drive.arcade(-gamepad1.left_stick_y, gamepad1.right_stick_x);
}
drive.stop();
```

The constructor gets the motors, sets direction and brake, uses plain power, and sets power to 0.
It refuses a `MECANUM` config (and `MecanumDrive` refuses a `TANK` one), so you cannot mix them up.

| Method | What it does |
| --- | --- |
| `arcade(forward, turnClockwise)` | **arcade style:** one stick for forward, one for turning. Each -1 to 1. |
| `arcade(forward, turnClockwise, speedScale)` | same, with a speed multiplier 0 to 1 |
| `tank(left, right)` | **tank style:** one stick for each side, e.g. `tank(-gamepad1.left_stick_y, -gamepad1.right_stick_y)` |
| `tank(left, right, speedScale)` | same, with a speed multiplier |
| `stop()` | both sides to 0 |
| `setPowers({left, right})` | set each side yourself. The power limit still applies. |
| `setBrake(boolean)` | `true` = hold at 0 power, `false` = coast |
| `getMotors()` | `{left, right}` for 2 motors, `{FL, FR, BL, BR}` for 4 |
| `getConfig()` | the `DriveConfig` |

**Arcade or tank style?** Arcade is easier for most new drivers. Tank style gives very direct
control of each side. `ExampleTankTeleOp` lets the driver switch with X.

---

## ReverseDrive

When ON, forward and strafe flip, so the back of the robot acts as the front. Turning does not flip.

```java
ReverseDrive reverse = new ReverseDrive();
controls.bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));
// every loop:
drive.drive(reverse.apply(forward), reverse.apply(strafe), turn);
```

| Method | What it does |
| --- | --- |
| `toggle()` | flip the mode. Returns the new state (true = reversed). |
| `set(boolean)` | set the mode |
| `isReversed()` | true when reversed |
| `apply(value)` | use on forward and strafe only. Returns the value, flipped if reversed. |

---

## AutoDriveConfig

Tuning numbers for `AutoDrive`. The defaults are last season's robot. **Tune them on this robot**
(see [Tune AutoDrive](#tune-autodrive)).

| Field | Default | Meaning |
| --- | --- | --- |
| `calibrationConfirmed` | `false` | `AutoDrive` refuses to run until you set this to `true`. A reminder to tune first. |
| `ticksPerRev` | 537.7 | encoder counts per motor turn. goBILDA 312 RPM = 537.7, 435 RPM = 384.5. Check your motor's page. |
| `wheelDiameterIn` | 4.098 | wheel size, inches. Start with the real size (96 mm = 3.78 in), then tune. |
| `gearRatio` | 1.0 | motor turns per wheel turn |
| `strafeCorrection` | 1.15 | mecanum wheels slip sideways. Sideways wheel travel is divided by this to get the real distance, and sideways power is raised by it so diagonals stay straight. |
| `turnKp` | 0.012 | turn power per degree of error |
| `turnKd` | 0.003 | slows the turn near the end to stop overshoot |
| `turnToleranceDeg` | 1.0 | a turn is done inside this error... |
| `turnSettleSec` | 0.15 | ...for this long |
| `turnMinPower` | 0.0 | smallest turn power outside the tolerance. Try 0.05-0.1 if turns stop short. |
| `moveMinPower` | 0.30 | slowest speed at the end of a move |
| `taperStartFraction` | 0.10 | start slowing for the last 10% of a move |
| `moveTurnScale` | 0.60 | `moveWithTurn`: heading correction is multiplied by this |
| `moveMaxTurnPower` | 0.35 | `moveWithTurn`: most turn power while moving |
| `actionTimeoutSec` | 10.0 | any move or turn gives up after this long |
| `stallExitEnabled` | `true` | stop a move early if the robot is stuck |
| `stallProgressIn` | 0.25 | stuck = less than this many inches of progress... |
| `stallTimeSec` | 0.4 | ...within this many seconds |

`ticksPerInch()` gives the encoder counts per inch, worked out from the first four numbers.

---

## AutoDrive

Autonomous moves with the wheel encoders and the IMU, for mecanum and standard (tank) robots.
The `DriveConfig` type tells it which. Each method **waits until the move ends**,
so use them only in a `LinearOpMode` autonomous. No wheel ever gets more power than
`DriveConfig.powerLimit`, whatever `maxPower` you pass.

```java
AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);
waitForStart();
auto.move(24, 0, 0.6);    // 24 in forward
auto.move(12, 90, 0.5);   // 12 in right
auto.turn(90, 0.5);       // turn 90 degrees left
auto.turnTo(0, 0.5);      // face the start direction
```

### The constructor

`new AutoDrive(opMode, driveConfig, autoConfig, imu)`

- `opMode`: your OpMode, usually `this`.
- `driveConfig`: the same `DriveConfig` TeleOp uses.
- `autoConfig`: tuning. Throws an error if `calibrationConfirmed` is false.
- `imu`: from `ImuSetup.get(...)`.

It resets the drive encoders, uses RUN_USING_ENCODER and brake, and sets power to 0.

### Methods

| Method | What it does |
| --- | --- |
| `move(distanceIn, directionDeg, maxPower)` | drive straight, no turning. `directionDeg` is robot-relative: 0 forward, 90 right, 180 back, -90 left. A negative distance goes the other way. |
| `moveWithTurn(distanceIn, fieldDirectionDeg, targetHeadingDeg, maxPower, maxTurnPower)` | drive in a FIELD direction (fixed even while turning) and turn to a heading at the same time. If the move finishes and the heading is not there yet, it finishes the turn inside the same time limit. After a stall it does not turn. |
| `turn(degrees, maxPower)` | turn in place by an amount. Positive = left. |
| `turnTo(headingDeg, maxPower)` | turn in place to face a heading (IMU degrees, 0 = start direction) |
| `headingDeg()` | the IMU heading |
| `resetHeading()` | make the current heading 0 |
| `getWheelTicks()` | encoder counts (FL, FR, BL, BR) |
| `stop()` | stop all wheels |
| `setLoopHook(code)` | run your code once per loop during every move, e.g. `auto.setLoopHook(() -> lift.moveTo(target, pid));` Keep it quick. |
| `setStopCondition(condition)` | end a move early (result `STOPPED`) when this becomes true. `AutoRoutine` uses it for park time. |

There is also a constructor for simulations and tests, with no robot: you give it pretend motors,
a heading, "is the OpMode running" and "idle". The library's own tests drive a pretend robot this way.

### On a standard (tank) drive

A standard drive cannot move sideways, so:

- `move` works only with direction `0` (forward) or `180` (back). Any other direction throws an error
  that says "turn first, then move".
- `moveWithTurn` throws an error. Use `turnTo(...)` and then `move(...)`.
- `turn` and `turnTo` work the same as on mecanum.
- `strafeCorrection` is not used.

```java
auto.move(24, 0, 0.6);     // forward
auto.turnTo(90, 0.5);      // face left
auto.move(12, 0, 0.6);     // forward again (in the new direction)
```

### Results

Every move returns how it ended, an `AutoDrive.Result`:

| Result | Meaning |
| --- | --- |
| `DONE` | reached the target |
| `STALLED` | stopped early: the robot was stuck (pushing a wall or robot) |
| `TIMED_OUT` | took longer than `actionTimeoutSec` |
| `STOPPED` | the OpMode was stopped |

You can ignore it, or react:

```java
if (auto.move(30, 0, 0.7) == AutoDrive.Result.STALLED) {
    auto.move(4, 180, 0.4);   // back off a little
}
```

### How a move works (cruise and taper)

The distance is measured from the four wheel encoders: forward travel plus sideways travel
(divided by `strafeCorrection`), so straight, sideways and diagonal moves all go the real distance.
The robot drives at `maxPower` until the last 10% (`taperStartFraction`) of the distance, then
slows in a straight line down to `moveMinPower`. This makes stops more accurate. The motors
always stop at the end, whatever happens (even if your code throws an error).

---

## Check motor directions

Do this first on every new drivetrain. **Put the robot on a block, wheels off the ground.**

1. Copy `examples/.../MotorDirectionCheck.java` into TeamCode, set its `config()` to your
   `DriveConfig`, and remove `@Disabled`.
2. Run it. Hold X, Y, A, B: each spins one wheel (front-left, front-right, back-left, back-right;
   on a 2-motor tank only X = left and Y = right).
3. Every wheel must roll so the robot would go FORWARD.
4. If one rolls backward, flip its `...Reversed` value in your `DriveConfig`.
5. Then on the floor with `powerLimit = 0.4`: stick up = forward, right stick right = turn clockwise.
   Mecanum only: left stick right = strafe right. If strafing goes diagonal, a motor name is on the wrong wheel.

## Tune AutoDrive

Do these in order. Write each result in your team's log.

1. **IMU:** check the heading sign (see [hardware.md](hardware.md#imusetup)).
2. **Ticks per inch:** set `ticksPerRev` from the motor's product page and `wheelDiameterIn` to the
   real wheel size. Run `auto.move(48, 0, 0.5)`. Measure the real distance. New diameter =
   old diameter x (real distance / 48). Repeat until within half an inch.
3. **Strafe (mecanum only):** run `auto.move(48, 90, 0.5)`. New `strafeCorrection` = old x (48 / real distance).
4. **Turns:** run `auto.turn(90, 0.5)` and `auto.turn(-90, 0.5)`. If it overshoots, raise `turnKd`
   or lower `turnKp`. If it stops short, raise `turnKp` (small steps, like 0.002) or set `turnMinPower`.
5. Set `calibrationConfirmed = true`.

---

## MecanumMixer

The math under `MecanumDrive`. No hardware, so you can test it. You only need it to build your own drive.

| Method | What it does |
| --- | --- |
| `mix(forward, strafeRight, turnClockwise, limit)` | four wheel powers (FL, FR, BL, BR), scaled together so none is over `limit`. All zero if an input is NaN. |
| `fieldToRobot(fieldForward, fieldRight, headingCcwDeg)` | changes a field-relative command into `{forward, strafeRight}` for the robot |
| `fieldDirection(fieldClockwiseDeg, headingCcwDeg)` | the robot-relative `{forward, strafeRight}` unit vector for a field direction |
| `FRONT_LEFT`, `FRONT_RIGHT`, `BACK_LEFT`, `BACK_RIGHT` | the index of each wheel (0-3) |

## DifferentialMixer

The math under `TankDrive`. No hardware.

| Method | What it does |
| --- | --- |
| `arcade(forward, turnClockwise, limit)` | `{left, right}` = `{forward + turn, forward - turn}`, scaled together so neither is over `limit`. Both zero if an input is NaN. |
| `tank(left, right, limit)` | `{left, right}`, each cut to -1..1 and multiplied by `limit` |
| `LEFT`, `RIGHT` | the index of each side (0, 1) |

## MotionMath

| Method | What it does |
| --- | --- |
| `taperSpeed(remainingFraction, minSpeed, maxSpeed, taperStart)` | the speed for this part of a move. `maxSpeed` until `taperStart` is left, then a straight line down to `minSpeed`. Example: (0.05, 0.3, 0.8, 0.1) gives 0.55. |

## StallDetector

Tells when something with a target is stuck. `AutoDrive` uses it; you can use it for a lift too.

```java
StallDetector stall = new StallDetector(0.25, 0.4);   // 0.25 of progress within 0.4 s
// every loop:
if (stall.update(distanceLeft, getRuntime())) { ... stuck ... }
```

| Method | What it does |
| --- | --- |
| `new StallDetector(minProgress, windowSec)` | stuck = less than `minProgress` closer within `windowSec` |
| `update(remaining, timeSec)` | true if stuck. Never true when `remaining` is already below `minProgress`. |
| `reset()` | start over (before a new move) |

It checks progress over the whole time window, not each loop, so it works the same at any loop speed.
