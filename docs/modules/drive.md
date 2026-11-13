# Drive module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.drive`

Learn it hands-on: [04 Mecanum](../tutorials/04-drive-mecanum.md), [05 Tank](../tutorials/05-drive-tank.md), [09 Autonomous by hand](../tutorials/09-first-auto.md), [10 Autonomous with mechanisms](../tutorials/10-auto-with-mechanisms.md).

Drives a **mecanum** chassis or a **standard** chassis, in TeleOp and in autonomous. A mecanum chassis
has 4 mecanum wheels, so it can also slide sideways. A standard chassis (also called tank or differential
drive) has normal wheels, a left side and a right side, and 2 or 4 motors. With the
[robot in five files](../reference/parts/drive.md), you describe the drive in one line, and these classes
work underneath. Use this page to drive by hand in your own OpMode, or to look up a setting. First read
[Signs and units](../conventions.md): it says which way is plus and which is minus.

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
| [`DriverControls`](#drivercontrols) | standard TeleOp driving from one gamepad (mecanum or tank, slow trigger, field-centric switch) |
| [`DriverHeading`](#driverheading) | know which way the robot faces compared with the driver. It continues from autonomous |
| [`ReverseDrive`](#reversedrive) | switch to a mode where the back of the robot acts as the front |
| [`AutoDriveConfig`](#autodriveconfig) | hold the tuning numbers for autonomous moves |
| [`AutoDrive`](#autodrive) | autonomous: drive a distance, turn an angle, turn to a heading |
| [`MecanumMixer`](#mecanummixer) | do the mecanum wheel-power math (no hardware) |
| [`DifferentialMixer`](#differentialmixer) | do the standard-drive left/right math (no hardware) |
| [`MotionMath`](#motionmath) | do the math that slows a move down near its end (the "taper") |
| [`StallDetector`](#stalldetector) | tell when a move is stuck |
| [`FieldMap`](#fieldmap) | hold your keep-out boxes, and find the route around them |
| [`RouteDriver`](#routedriver) | drive a route in TeleOp, one loop at a time |

The steps to [check motor directions](#check-motor-directions) and [tune AutoDrive](#tune-autodrive) are at
the end of the page.

---

## DriveConfig

A small settings object. Start from the factory method for your drivetrain (a method that makes one with
the usual settings). Then change what is different on your robot.

```java
DriveConfig driveConfig = DriveConfig.mecanum();   // or DriveConfig.tank(), DriveConfig.tankTwoMotor()
driveConfig.frontLeft = "fl";                      // only if your names are different
driveConfig.powerLimit = 0.6;                      // no wheel ever gets more than 0.6 power
```

| Factory | Type | Motor names |
| --- | --- | --- |
| `DriveConfig.mecanum()` (same as `new DriveConfig()`) | `MECANUM` | `front_left`, `front_right`, `back_left`, `back_right` |
| `DriveConfig.tank()` | `TANK` | `front_left`, `front_right`, `back_left`, `back_right` |
| `DriveConfig.tankTwoMotor()` | `TANK` | `left_drive`, `right_drive` (the FTC sample names). The back names are `null` |

| Field | Default | Meaning |
| --- | --- | --- |
| `type` | `MECANUM` | `DriveType.MECANUM` or `DriveType.TANK` |
| `frontLeft`, `frontRight`, `backLeft`, `backRight` | `"front_left"` ... `"back_right"` | the motors' names in the configuration. For a 2-motor tank, use `frontLeft`/`frontRight` and set both back names to `null`. |
| `frontLeftReversed` ... `backRightReversed` | left = `true`, right = `false` | which motors run reversed |
| `powerLimit` | `1.0` | the most power any wheel ever gets. Use 0.4 on a new robot. |
| `brake` | `true` | the wheels hold still at power 0 (false = coast: they roll freely) |

| Method | What it does |
| --- | --- |
| `hasBackMotors()` | true when both back motor names are set |
| `validate()` | checks that the settings make sense (the drive classes call it for you). A mecanum drive needs 4 names. The back names must be both set or both `null`. |

Use the SAME `DriveConfig` in TeleOp and autonomous. One way: write one class in TeamCode with a
`static DriveConfig make()` method, and call it from both.

---

## MecanumDrive

```java
MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);
waitForStart();
while (opModeIsActive()) {
    // forward, strafe right, turn clockwise. Minus: the SDK gives stick UP as negative.
    drive.drive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
}
drive.stop();   // wheels off when the OpMode ends
```

The constructor gets the four motors and sets their direction and brake. It uses plain power
(RUN_WITHOUT_ENCODER) and sets the power to 0. A wrong motor name gives a clear error.

| Method | What it does |
| --- | --- |
| `drive(forward, strafeRight, turnClockwise)` | robot-centric drive. Each value is -1 to 1. |
| `drive(forward, strafeRight, turnClockwise, speedScale)` | the same, times a speed multiplier from 0 to 1 (for a slow mode) |
| `driveFieldCentric(fieldForward, fieldRight, turnClockwise, headingCcwDeg)` | field-centric drive (see below) |
| `driveFieldCentric(..., headingCcwDeg, speedScale)` | the same, with a speed multiplier |
| `stop()` | sets all wheels to 0 |
| `setPowers(double[4])` | sets each wheel yourself (order FL, FR, BL, BR), for testing. The power limit still applies. |
| `setBrake(boolean)` | `true` = hold at 0 power, `false` = coast |
| `getMotors()` | the four `DcMotorEx` motors (FL, FR, BL, BR) |
| `getConfig()` | the `DriveConfig` |

The numbers can add up to more than 1 on a wheel, for example full forward and full strafe. Then the
drive scales all four wheels down together, and the robot keeps the direction you asked for.

### Robot-centric or field-centric?

- **Robot-centric:** stick up drives the robot the way IT faces. It is simple. When the robot faces you,
  stick up drives it toward you.
- **Field-centric:** stick up drives the robot AWAY FROM YOU, whichever way it faces. New drivers find it
  easier. It needs the IMU (the turn sensor inside the Control Hub, see [ImuSetup](hardware.md#imusetup)).

```java
double heading = ImuSetup.headingDeg(imu);   // read every loop: which way the robot faces now
drive.driveFieldCentric(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x, heading);
```

The heading must be 0 when the robot faces away from the driver. In a match, use
[`DriverHeading`](#driverheading) instead of the plain IMU heading. It continues from where autonomous
ended, so "forward" is right from the first second of TeleOp.

## DriverControls

Standard TeleOp driving from one gamepad, for mecanum AND tank robots, in one line per loop.
[`EasyTeleOp`](teleop.md) uses it for you.

```java
DriverHeading heading = new DriverHeading(imu);              // or null: no field-centric
DriverControls driving = DriverControls.create(hardwareMap, new RobotSettings().drive(), heading);
// every loop:
driving.update(gamepad1);
// at the end (in finally):
driving.stop();
```

| Control | What it does |
| --- | --- |
| left stick | drives (mecanum: any direction, tank: forward and back) |
| right stick X | turns |
| left trigger | slows the robot, down to `slowSpeed` at a full squeeze |

| Method | What it does |
| --- | --- |
| `DriverControls.create(hardwareMap, config, heading)` | makes a `MecanumDrive` or a `TankDrive`, from `config.type` |
| `update(gamepad)` | drives from the sticks. Call it every loop. |
| `update(gamepad, slow)` | the same, but your own code turns slow mode on, in place of the left trigger |
| `update(gamepad, speed)` | the same, at a speed your own code works out (0 to 1, for example from a slow trigger that is not LT) |
| `drive(forward, strafeRight, turnClockwise, speed)` | drives with your own numbers (robot-centric), for custom controls |
| `tank(left, right, speed)` | two-stick tank style: one value per side. It works on tank and mecanum robots. |
| `turnAssist(turn -> ...)` | a helper that may change the turn every loop. It gets the driver's shaped turn (clockwise +) and gives back the turn to use. Forward and sideways stay the driver's. Null = none. The five-files TeleOp uses it for [whole-robot aim](../reference/parts/aim.md). |
| `hold()` | keeps the wheels still this loop (for example while a menu uses the gamepad) |
| `stop()` | sets all wheels to 0 |
| `slowSpeed(s)` | the speed at a full slow trigger, default 0.3 |
| `sticks(deadband, exponent)` | the stick feel, default 0.05 and 2 ([StickShaping](gamepad.md)) |
| `reverseWhen(() -> on)` | reverses the drive while the test gives true. Forward and strafe flip (the back is the front), and the turn does not. Field-centric ignores it. The five-files TeleOp uses it for `robot.reverseDriveMode()` |
| `swapSticks(true)`, `sticksSwapped()` | the right stick drives (and strafes), and the left stick X turns. Default false |
| `setFieldCentric(on)`, `toggleFieldCentric()`, `isFieldCentric()` | the field-centric switch. It starts off. It stays off on a tank robot or without a heading. |
| `canFieldCentric()` | true with mecanum wheels and a heading |
| `isSlow()`, `status()` | for telemetry. `status()` gives text like "field  SLOW" |

## DriverHeading

Which way the robot faces compared with the driver: 0 = straight away from the driver, left = +.

**Why:** In INIT, TeleOp sets up the IMU again. That makes "0" the way the robot faces at that moment,
which is wherever autonomous left it. Drivers may not touch the robot between autonomous and TeleOp.
Without help, field-centric "forward" stays wrong until the driver turns the robot and presses a reset
button.

```java
// TeleOp INIT, after the IMU is set up:
DriverHeading heading = new DriverHeading(imu);          // reads autonomous's saved heading
controls.bindPress(Pad.G1, Btn.BACK, heading::reset);    // manual reset: face away first
waitForStart();
heading.forgetSaved();                                   // this run uses it now

// every loop:
drive.driveFieldCentric(forward, strafe, turn, heading.degrees(), speed);
```

| Method | What it does |
| --- | --- |
| `new DriverHeading(imu)` | call it in TeleOp INIT, after the IMU is set up. It reads the heading autonomous saved, when that is less than 5 minutes old. Otherwise 0 = the way the robot faces now. |
| `forgetSaved()` | call it right after ▶ (start on the Driver Hub). It clears the saved heading, so a later TeleOp run does not use it. Do not call it in INIT: then INIT, STOP, INIT again still works. EasyTeleOp does this for you. |
| `degrees()` | the heading compared with the driver, -180 to 180. Call it every loop. |
| `reset()` | means "the robot now faces away from me": the heading now = 0. For a button. |
| `isFromAuto()` | true while it still counts from autonomous (no reset since) |

Where the saved heading comes from:

- **EasyAuto** saves it for you at the end. When the robot does not start facing straight away from its
  driver, override `startHeadingDeg()` with the start angle (left = +). Write it for BLUE. EasyAuto
  mirrors it for RED, like the turns (unless `mirrorForRed()` returns false).
- **Your own autonomous:** at the end, call `PoseStore.save(new FieldPose(0, 0, heading))`. Measure the
  heading from "away from the driver" (use `auto.headingDeg()` when the robot started facing away).

---

## TankDrive

For a standard (tank / differential) drive with 2 or 4 motors. It cannot move sideways.

```java
TankDrive drive = new TankDrive(hardwareMap, DriveConfig.tankTwoMotor());
waitForStart();
while (opModeIsActive()) {
    // left stick = forward/back (minus: stick UP is negative), right stick = turn
    drive.arcade(-gamepad1.left_stick_y, gamepad1.right_stick_x);
}
drive.stop();   // wheels off when the OpMode ends
```

The constructor gets the motors and sets their direction and brake. It uses plain power and sets the
power to 0. It refuses a `MECANUM` config (and `MecanumDrive` refuses a `TANK` one), so you cannot mix
them up.

| Method | What it does |
| --- | --- |
| `arcade(forward, turnClockwise)` | **arcade style:** one stick for forward, one for turning. Each is -1 to 1. |
| `arcade(forward, turnClockwise, speedScale)` | the same, with a speed multiplier from 0 to 1 |
| `tank(left, right)` | **tank style:** one stick for each side, for example `tank(-gamepad1.left_stick_y, -gamepad1.right_stick_y)` |
| `tank(left, right, speedScale)` | the same, with a speed multiplier |
| `stop()` | sets both sides to 0 |
| `setPowers({left, right})` | sets each side yourself. The power limit still applies. |
| `setBrake(boolean)` | `true` = hold at 0 power, `false` = coast |
| `getMotors()` | `{left, right}` for 2 motors, `{FL, FR, BL, BR}` for 4 |
| `getConfig()` | the `DriveConfig` |

**Arcade or tank style:** most new drivers find arcade easier. Tank style gives direct control of each
side. `ExampleTankTeleOp` lets the driver switch with X.

---

## ReverseDrive

When the mode is ON, forward and strafe flip, so the back of the robot acts as the front. The turn does
not flip.

```java
ReverseDrive reverse = new ReverseDrive();
// toggle() flips the mode and returns it; confirm (a ToggleRumble) buzzes 2x = ON, 1x = OFF
controls.bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));
// every loop:
drive.drive(reverse.apply(forward), reverse.apply(strafe), turn);
```

| Method | What it does |
| --- | --- |
| `toggle()` | flips the mode. It gives the new state (true = reversed). |
| `set(boolean)` | sets the mode |
| `isReversed()` | true when reversed |
| `apply(value)` | use it on forward and strafe only. It gives the value, flipped when reversed. |

---

## AutoDriveConfig

Tuning numbers for `AutoDrive`. The defaults come from last season's robot. **Tune them on this robot**
(see [Tune AutoDrive](#tune-autodrive)).

| Field | Default | Meaning |
| --- | --- | --- |
| `calibrationConfirmed` | `false` | `AutoDrive` refuses to run until you set this to `true`. It reminds you to tune first. |
| `ticksPerRev` | 537.7 | encoder counts per motor turn. goBILDA 312 RPM = 537.7, 435 RPM = 384.5. Check your motor's page. |
| `wheelDiameterIn` | 4.098 | the wheel size, in inches. The default is last season's wheel (about 104 mm). Start with your wheel's real size (for example, 96 mm = 3.78 in), then tune. |
| `gearRatio` | 1.0 | motor turns per wheel turn |
| `strafeCorrection` | 1.15 | mecanum wheels slip sideways. `AutoDrive` divides the sideways wheel travel by this to get the real distance. It also multiplies the sideways power by it, so diagonals stay straight. |
| `turnKp` | 0.012 | turn power per degree of error |
| `turnKd` | 0.003 | slows the turn near the end to stop overshoot |
| `turnToleranceDeg` | 1.0 | a turn is done when the error stays inside this... |
| `turnSettleSec` | 0.15 | ...for this long |
| `turnMinPower` | 0.0 | the smallest turn power outside the tolerance. Try 0.05-0.1 when turns stop short. |
| `moveMinPower` | 0.30 | the slowest speed at the end of a move |
| `taperStartFraction` | 0.10 | the move starts slowing for its last 10% |
| `slowDownIn` | 0 | ...and for at least this many inches at the end (0 = only the 10%). `taperFraction(distanceIn)` gives the larger of the two |
| `moveTurnScale` | 0.60 | `moveWithTurn` multiplies the heading correction by this |
| `moveMaxTurnPower` | 0.35 | `moveWithTurn` and routes: the most turn power while moving |
| `driveToNearIn` | 8 | `driveTo` and routes: in the last this-many inches, the speed follows the distance left... |
| `driveToKp` | 0.04 | ...this much power per inch left... |
| `driveToMinPower` | 0.12 | ...but at least this power, so it does not stall short |
| `driveToMinTurn` | 0.08 | `driveTo` and routes: the least turn power while the heading is off, when `turnMinPower` is 0 |
| `actionTimeoutSec` | 10.0 | any move or turn gives up after this long |
| `stallExitEnabled` | `true` | stops a move early when the robot is stuck |
| `stallProgressIn` | 0.25 | stuck = less than this many inches of progress... |
| `stallTimeSec` | 0.4 | ...within this many seconds |

`ticksPerInch()` gives the encoder counts per inch. It works them out from the first four numbers.

---

## AutoDrive

Autonomous moves for mecanum and standard (tank) robots. It measures distance with the wheel
**encoders** (sensors that count how far each motor turned), and it turns with the IMU. The `DriveConfig`
type tells it which drive you have. Each method **waits until the move ends**, so use them only in a
`LinearOpMode` autonomous. No wheel ever gets more power than `DriveConfig.powerLimit`, whatever
`maxPower` you pass.

```java
AutoDrive auto = new AutoDrive(this, driveConfig, autoConfig, imu);   // this = your OpMode
waitForStart();
// Each line waits until that move is done, then the next one starts. 0.6 = top power.
auto.move(24, 0, 0.6);    // 24 in forward
auto.move(12, -90, 0.5);  // 12 in right (90 = left)
auto.turn(90, 0.5);       // turn 90 degrees left
auto.turnTo(0, 0.5);      // face the start direction
```

### The constructor

`new AutoDrive(opMode, driveConfig, autoConfig, imu)`

- `opMode`: your `LinearOpMode`, usually `this`.
- `driveConfig`: the same `DriveConfig` TeleOp uses.
- `autoConfig`: the tuning. The constructor throws an error when `calibrationConfirmed` is false.
- `imu`: from `ImuSetup.get(...)`.

`new AutoDrive(opMode, driveConfig, autoConfig, imu, isActive)` is the same, with your own "keep going"
check (`isActive`, true while the moves may run). Use it for an OpMode that runs inside another one, such
as a finder inside "Tune and check". There, the finder's own `opModeIsActive()` is always false, so every
move would end at once with `STOPPED`.

The constructor resets the drive encoders, uses RUN_USING_ENCODER and brake, and sets the power to 0.

### Methods

| Method | What it does |
| --- | --- |
| `move(distanceIn, directionDeg, maxPower)` | drives straight, with no turning. `directionDeg` is robot-relative and counterclockwise, like a heading: 0 forward, 90 left, 180 back, -90 right. A negative distance goes the other way. |
| `moveWithTurn(distanceIn, fieldDirectionDeg, targetHeadingDeg, maxPower, maxTurnPower)` | drives in a FIELD direction and turns to a heading at the same time. The field direction stays fixed while the robot turns (counterclockwise, 90 = left). When the move ends before the heading is reached, it finishes the turn inside the same time limit. After a stall, it does not turn. |
| `turn(degrees, maxPower)` | turns in place by an amount. Positive = left. |
| `turnTo(headingDeg, maxPower)` | turns in place to face a heading (IMU degrees, 0 = start direction) |
| `turn(degrees, maxPower, timeoutSec)`, `turnTo(headingDeg, maxPower, timeoutSec)` | the same with a shorter time limit, for a step that has its own (never more than `actionTimeoutSec`) |
| `headingDeg()` | the IMU heading |
| `resetHeading()` | makes the current heading 0 |
| `getWheelTicks()` | the encoder counts (FL, FR, BL, BR) |
| `stop()` | stops all wheels |
| `setLoopHook(code)` | runs your code once per loop during every move, for example `auto.setLoopHook(() -> lift.moveTo(target, pid));` Keep it fast. |
| `setStopCondition(condition)` | ends a move early (result `STOPPED`) when this becomes true. `AutoRoutine` uses it for park time. |

A third constructor is for simulations and tests, with no robot. You give it pretend motors, a heading,
"is the OpMode running" and "idle". The library's own tests drive a pretend robot this way.

### On a standard (tank) drive

A standard drive cannot move sideways. So:

- `move` works only with direction `0` (forward) or `180` (back). Any other direction throws an error
  that says "turn first, then move".
- `moveWithTurn` throws an error. Use `turnTo(...)` and then `move(...)`.
- `turn` and `turnTo` work the same as on mecanum.
- `AutoDrive` does not use `strafeCorrection`.

```java
auto.move(24, 0, 0.6);     // forward
auto.turnTo(90, 0.5);      // face left
auto.move(12, 0, 0.6);     // forward again (in the new direction)
```

### Results

Every move returns how it ended, an `AutoDrive.Result`:

| Result | Meaning |
| --- | --- |
| `DONE` | the robot reached the target |
| `STALLED` | the move stopped early: the robot was stuck (pushing a wall or a robot) |
| `TIMED_OUT` | the move took longer than `actionTimeoutSec` |
| `STOPPED` | the OpMode stopped |

You can ignore it, or react to it:

```java
// STALLED = stuck: pushing on would waste time and strain the motors
if (auto.move(30, 0, 0.7) == AutoDrive.Result.STALLED) {
    auto.move(4, 180, 0.4);   // back off a little
}
```

### How a move works (cruise and taper)

`AutoDrive` measures the distance with the four wheel encoders: forward travel plus sideways travel
(divided by `strafeCorrection`). So straight, sideways and diagonal moves all go the real distance.

A robot that stops from full speed slides past its target. So the robot drives at `maxPower` until the
last 10% (`taperStartFraction`) of the distance. Then it slows in a straight line down to `moveMinPower`.
On a short move, 10% is too little road to slow down on, and the robot reaches the end fast and slides past
it. With `slowDownIn` (for example 6), the robot also slows over at least the last 6 in.

At the distance, the wheels brake. The move waits until the robot stops rolling (at most 0.3 s, with the
mechanisms still updating). So the next move starts from a robot that stands still. The motors always stop
at the end, whatever happens (even when your code throws an error).

---

## Check motor directions

A motor that runs the wrong way makes the robot drive in circles. Do this check first on every new
drivetrain. **Put the robot on a block, with the wheels off the ground.**

**With the robot in five files:** you copy nothing. Use **Tune and check > Motor Direction Check**. It reads
the directions from Robot.java ([steps](../tuning/02-wiring-checks.md#motor-direction-check-each-wheel-rolls-forward)).
**With your own `DriveConfig`** (no Robot.java):

1. Copy `examples/.../MotorDirectionCheck.java` into TeamCode. Set its `config()` to your `DriveConfig`,
   and remove `@Disabled`.
2. Run it. Hold X, Y, A or B: each spins one wheel (front-left, front-right, back-left, back-right).
   On a 2-motor tank, only X (left) and Y (right) work.
3. Every wheel must roll so the robot would go FORWARD.
4. When one rolls backward, flip its `...Reversed` value in your `DriveConfig`. With the robot in five
   files, add the motor's name to `.reversed(...)` in `Robot.java` (or take it out).
   A held button spins its wheel at 30%: slow enough to be safe, and fast enough to see the direction.
   Run the check again after anyone changes a motor or a wire.
5. Then put the robot on the floor, with `powerLimit = 0.4`. Stick up must drive forward, and right stick
   right must turn clockwise. On mecanum only, left stick right must strafe right. When strafing goes
   diagonal, a motor name is on the wrong wheel.

## Tune AutoDrive

Do these steps in order. Write each result in your team's log.

1. **IMU:** check the heading sign (see [hardware.md](hardware.md#imusetup)).
2. **Ticks per inch:** set `ticksPerRev` from the motor's product page, and set `wheelDiameterIn` to the
   real wheel size. Run `auto.move(48, 0, 0.5)`. Measure the real distance. New diameter =
   old diameter x (real distance / 48). Repeat until the move is within half an inch.
3. **Strafe (mecanum only):** run `auto.move(48, -90, 0.5)` (to the right). New `strafeCorrection` = old x (48 / real distance).
4. **Turns:** run `auto.turn(90, 0.5)` and `auto.turn(-90, 0.5)`. When it overshoots, raise `turnKd`
   or lower `turnKp`. When it stops short, raise `turnKp` (in small steps, like 0.002) or set `turnMinPower`.
5. Set `calibrationConfirmed = true`.

---

## MecanumMixer

The math under `MecanumDrive`. It uses no hardware, so you can test it on a laptop. You need it only to build your own drive.

| Method | What it does |
| --- | --- |
| `mix(forward, strafeRight, turnClockwise, limit)` | four wheel powers (FL, FR, BL, BR), scaled together so none is over `limit`. All are zero when an input is NaN. |
| `fieldToRobot(fieldForward, fieldRight, headingCcwDeg)` | changes a field-relative command into `{forward, strafeRight}` for the robot |
| `fieldDirection(fieldCcwDeg, headingCcwDeg)` | a field direction as `{forward, strafeRight}` for the robot, with a length of 1 |
| `FRONT_LEFT`, `FRONT_RIGHT`, `BACK_LEFT`, `BACK_RIGHT` | the index of each wheel (0-3) |

## DifferentialMixer

The math under `TankDrive`. It uses no hardware.

| Method | What it does |
| --- | --- |
| `arcade(forward, turnClockwise, limit)` | `{left, right}` = `{forward + turn, forward - turn}`, scaled together so neither is over `limit`. Both are zero when an input is NaN. |
| `tank(left, right, limit)` | `{left, right}`, each cut to -1..1 and multiplied by `limit` |
| `LEFT`, `RIGHT` | the index of each side (0, 1) |

## MotionMath

| Method | What it does |
| --- | --- |
| `taperSpeed(remainingFraction, minSpeed, maxSpeed, taperStart)` | the speed for this part of a move: `maxSpeed` until `taperStart` is left, then a straight line down to `minSpeed`. For example, (0.05, 0.3, 0.8, 0.1) gives 0.55. |

## StallDetector

Tells when something with a target is stuck. `AutoDrive` uses it, and you can use it for a lift too.

```java
StallDetector stall = new StallDetector(0.25, 0.4);   // stuck: not 0.25 in closer within 0.4 s (same unit as distanceLeft)
// every loop: how far is still left, and the time now (getRuntime() = seconds since INIT)
if (stall.update(distanceLeft, getRuntime())) { ... stuck ... }
```

| Method | What it does |
| --- | --- |
| `new StallDetector(minProgress, windowSec)` | stuck = less than `minProgress` closer within `windowSec` |
| `update(remaining, timeSec)` | true when stuck. Never true when `remaining` is already below `minProgress`. |
| `reset()` | starts over (before a new move) |

It checks the progress over the whole time window, so it works the same at any loop speed.

## FieldMap

The places on the field the robot must not drive through, and the way around them. On a five-file robot,
it is one line in `Robot.java`: `fieldMap(...)`. [Keep-out boxes and routes](odometry.md#keep-out-boxes-and-routes)
says what the numbers mean and what a route cannot do. It uses no hardware, so you can use it alone.

```java
FieldMap map = FieldMap.margin(13).keepOut("Post", -5, -5, 5, 5);
List<double[]> points = map.route(pose.x, pose.y, 0, 40);   // each point is {x, y}; empty = no route
```

| Method | What it does |
| --- | --- |
| `FieldMap.margin(inches)` | a new map: how far the robot's center stays from every box |
| `keepOut(name, x1, y1, x2, y2)` | adds a box: two opposite corners, in inches |
| `safePoint(x, y)` | adds a place a route may pass through. INIT stops when the point is within a margin (or less than 0.5 in outside it), or closer to a wall than the margin |
| `fieldSize(inches)` | the side of the square field. Default 144 |
| `route(fromX, fromY, toX, toY)` | the points to drive to, in order, ending at the spot. Empty when there is no route: then the robot must not set off |
| `lineIsClear(x1, y1, x2, y2)` | true when the robot's center may drive straight there: the line misses every margin. For a box whose margin holds the end, the line only has to miss the box itself |
| `whyBadSpot(x, y)` | why a route cannot end there (off the field, on a box, or boxed in), or `""`. A spot inside a margin is fine: the last line goes straight in |
| `marginIn()`, `boxCount()` | what the map holds |

## RouteDriver

Drives a route without waiting. `route(...)` in `Controls.java` uses it for you. In autonomous,
`AutoDrive.followRoute(pose, ready, points, clear, headingDeg, power, toleranceIn)` runs it in a blocking
loop (`Steps.routeTo` uses that). Each loop, call `update()` and send what it gives to the wheels.
[`AutoDrive.driveTo`](#autodrive) does the same work in autonomous, and blocks.

```java
RouteDriver route = new RouteDriver(autoConfig, 1.0, robot::pose, odometry::isReady, System::nanoTime)
        .checkLines(map::lineIsClear);   // leave a point early only if the line on is clear
route.start(map.route(pose.x, pose.y, 0, 40), 0, 0.7, 1.0);
// every loop:
if (route.isRunning()) {
    double[] p = route.update();          // {forward, strafe right, turn clockwise}
    driving.drive(p[0], p[1], p[2], 1);
}
```

| Method | What it does |
| --- | --- |
| `new RouteDriver(cfg, powerLimit, pose, ready, clockNanos)` | while `ready` is false, it holds still, and after 0.2 s it ends |
| `checkLines(check)` | the map's line check (`map::lineIsClear`). A point on the way counts as reached early (within 2 in, or slid past it) only when the line from the robot to the next point is clear. Otherwise the robot drives to the point (within 0.5 in). Give it one for every route from `FieldMap.route` |
| `start(points, headingDeg, maxPower, toleranceIn)` | starts a route. False when it has no points |
| `update()` | one loop. It never waits. It gives all 0 once the route has ended |
| `isRunning()` | true while it drives |
| `result()` | `DONE`, `STALLED`, `TIMED_OUT`, `NO_ODOMETRY` or `CANCELLED`. Null while it runs |
| `cancel()` | ends it now. Then your code sends the wheels what comes next |
| `PASS_POINT_IN` | a point on the way counts as reached within this many inches (2) |
