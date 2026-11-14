# Design study: swerve drives

Status: **study only, no code.** Written 2026-10-21 against v0.15.0 (`main`). It helps the mentor decide
whether to build swerve support.

## The short answer

- The library does not support swerve today. `DriveConfig.DriveType` is `MECANUM` or `TANK`.
- Pedro Pathing 3.0.2 has a swerve drivetrain (`Swerve` + `CoaxialPod`). Today a team can use it only
  through Pedro's escape hatch (`Pedro.follower((hw, localizer) -> ...)`). Then it has no TeleOp, no
  `move`/`turn`/`driveTo`, no simulator and no help to line up the wheels.
- None of goBILDA, REV or AndyMark sells an FTC swerve module (checked 2026-10-21). A team builds its
  own pods. goBILDA does sell the usual steering servo (Axon MAX MK2, with a position wire).
- Full support is about **3 weeks** of work. A smaller first step (TeleOp + Pedro + alignment finder +
  simulator) is about **2 weeks**.

## What a swerve drive is (for the docs later)

Each wheel sits in a **pod** that can turn. One motor drives the wheel. A second actuator points the pod.
On FTC it is usually a continuous-rotation servo. An **absolute encoder** (a sensor that
knows the pod angle the moment the robot turns on) tells the code which way the pod points. On FTC
this is an analog input (0–3.3 V for one turn).

Why teams want it: full speed in every direction (mecanum loses about 30 % sideways), and more push,
because the wheels are plain traction wheels. Why it is hard: it has 8 actuators for 4 wheels. The team must build
the pods and line them up. The steering needs a small control loop of its own.

## How Pedro 3.0.2 does it (read from the jars with `javap`)

- `Swerve implements Drivetrain`: `new Swerve(hardwareMap, SwerveConfig, SwervePod...)`. It does the
  math from (forward, strafe, turn) to one (angle, speed) per pod (`computePodPowers`), with voltage
  compensation. `SwerveConfig.zeroPowerBehavior` is `X_LOCK` or `IGNORE_ANGLE_CHANGES`.
- `SwervePod` is an **interface**: `getOffset()`, `getAngle()`, `adjustThetaForEncoder(..)`,
  `move(angle, power, ..)`, `setToFloat()`, `setToBreak()`, `debug()`. So we can plug in our own pod.
- `CoaxialPod` is Pedro's pod: a `DcMotorEx` drive motor, a `CRServo` steering servo and an
  `AnalogInput` encoder. `CoaxialPodConfig` holds `motorName`, `servoName`, `servoEncoderName`,
  `driveDirection`, `servoDirection`, `angleOffsetRad` (the alignment), `podOffset` (where the pod
  sits), `analogMinVoltage` 0 / `analogMaxVoltage` 3.3, `encoderReversed`, and `turnController`.
- `turnController` defaults to `Controller.zero`: **the pods do not steer at all until you set it.**
- `move(..)` turns the pod the short way, and flips the wheel direction when the target is more than
  90° away. It stops steering inside 2° (0.0349 rad).

## The plan, part by part

### 1. The Robot.java line

```java
drive(Drive.swerve(
        Drive.pod("fl_drive", "fl_steer", "fl_angle").atMm(-140, 140),    // x right, y forward
        Drive.pod("fr_drive", "fr_steer", "fr_angle").atMm(140, 140),
        Drive.pod("bl_drive", "bl_steer", "bl_angle").atMm(-140, -140),
        Drive.pod("br_drive", "br_steer", "br_angle").atMm(140, -140))
    .motor("gobilda-5203-312").wheelMm(72)
    .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

- One `Drive.pod(drive motor, steering servo, angle input)` per pod, with two to four pods.
  `atMm(x, y)` is the pod center from the robot center (the same signs as `docs/conventions.md`).
- New numbers in `Tunables.Drive`: one alignment offset per pod (`flOffsetDeg` …, which the
  finder writes), the steering `steerKp` / `steerKd`, and `steerToleranceDeg`.
- `DriveType` gets `SWERVE`. `isMecanum()` stays as it is. Add `canStrafe()` (mecanum or swerve), and
  move the callers that mean "can strafe" to it (`DriveTunerOpMode`, `Pedro`, `DriverControls`).
- `reversed(...)` works for drive motors and steering servos by name, like today.

### 2. TeleOp driving

New plain classes in `drive/`, the same shape as mecanum:

- `SwerveMixer`: (forward, right, turn clockwise) → one (angle, speed) per pod. It scales the speeds so none
  goes past the power limit (like `MecanumMixer`). It is pure math, so tests are simple.
- `SwervePodControl`: one pod. A PD loop from angle error to servo power, the 90° flip, and **cosine
  scaling**: drive power × cos(angle error). With it, a pod that still points the wrong way does not push
  the robot sideways. At zero stick the pod keeps its last angle (no snap back to 0°).
- `SwerveDrive`: `drive(f, s, t, speed)`, `driveFieldCentric(...)`, `stop()`. The steering loop needs a
  call every loop. TeleOp already calls `drive` every loop, so it fits the non-blocking rule.
- `DriverControls.create` gets a third `Wheels` case. `EasyTeleOp` needs no change: strafe and
  field-centric work because `canStrafe()` is true.
- Loop time matters, because each loop has 4 analog reads. Check that bulk reads cover them (the hub's bulk data
  includes the analog inputs), and add a loop-time check to the finder.

### 3. Autonomous `move` / `turn` / `driveTo`

`AutoDrive` is built on four motors and `MecanumMixer` (wheel ticks → distance). The plan:

- Pull a small internal interface out of `AutoDrive`. It has two halves: "drive with (forward, right, turn)", and "the distance
  the robot went (forward, right inches), from the wheels". Mecanum and tank keep their exact math
  behind it. Swerve averages each pod's tick change × (cos angle, sin angle).
- `driveTo` with odometry needs only the first half, so it is the easy part. `move`/`turn` without
  odometry need the second half.
- `strafeCorrection` is 1 for swerve. The Drive Tuner skips its strafe test and shows the pods instead.
- This touches tested mecanum code. Every existing `AutoDrive` test must pass unchanged.

### 4. Pedro

- `Pedro.follower(foresight)` accepts a swerve `drive(...)` line and builds Pedro's `Swerve`.
- **Use our own pod inside Pedro:** the pedro module wraps `SwervePodControl` as Pedro's `SwervePod`
  interface. Then TeleOp, `AutoDrive` and Pedro steer with the same code and the same tuned numbers.
  There is no second steering loop to tune, and no `Controller.zero` trap.
- `LimitedSwerve extends Swerve`: the power limit, like `LimitedMecanum` (`computePodPowers` is public).
- Pod positions go through `PedroFrame` (Pedro's axes and inches).
- Risk: teams use Pedro's swerve less than any other part of Pedro. Test on a real robot before a release says
  "supported".

### 5. The simulator robot

- Robot file: `drive.type = swerve`, `drive.pods = fl_drive fl_steer fl_angle -5.5 5.5, ...` (inches).
  It also has a hidden `drive.podMountDeg = 37, -112, ...` (the true mount angle of each encoder). Then the
  alignment finder has something real to find.
- Steering model: a CR servo turns the pod at up to its no-load speed (Axon MAX MK2: 83 rpm at 6 V,
  goBILDA page), with a little lag. A pretend `AnalogInput` gives (angle + mount) / 360 × 3.3 V. The
  simulator has `CRServo` today but **no `AnalogInput`**, so add it.
- Chassis motion: fit (forward, right, turn) to the four pod velocity vectors (least squares). Then call the
  existing `world.step(dt, forward, right, cw)`. Nothing changes in the physics engine.
- `Drive.simLines`, `SimNewRobot` (guess swerve from names like `*_steer` + `*_angle`), `:sim:fromConfig`
  and the robot-file skill (`skills/robot-file/`) learn the new lines.

### 6. Pod-alignment finder (`SwerveFinderOpMode`)

Steps, each with a plain on-screen prompt:

1. **Steering off.** All servos float. The student lines up every wheel straight forward with a
   straight edge (two goBILDA channels across the pods work). The student presses A, and each pod's voltage becomes its
   offset.
2. **Encoder range and direction.** Each pod turns slowly once, alone. The finder records the lowest
   and highest voltage. It checks that positive servo power makes the angle go up. If not, it says "add
   fl_steer to reversed(...)".
3. **Drive motor direction.** Each wheel rolls forward slowly. The student says yes or no.
4. **Check.** All pods point to 0°, 45°, 90°, then back. The student checks by eye.
5. **Steering tune.** The finder gives each pod a 90° step, and reports the time to settle and any overshoot. Then it nudges
   `steerKp`/`steerKd`.
6. It prints the lines for `Tunables.java`, and says to copy them there. Without the copy, live-tuned values stay only on that
   hub.

A scenario test (`FinderSafetyTest`) also runs it in the simulator with the hidden mount angles.

### 7. Real parts (checked on the seller pages 2026-10-21)

| Seller | What they sell for swerve | Usable on FTC? |
|---|---|---|
| goBILDA | Site search "swerve": 0 results. No module or kit. | Build your own pod |
| goBILDA | Axon MAX Servo MK2, `2004-0025-0002`, $99.99, in stock. 4th wire = analog position. 28 kg·cm / 71 rpm at 4.8 V, 34 kg·cm / 83 rpm at 6 V. H25T spline. | Yes: the steering servo |
| goBILDA | Axon MINI Servo MK2, `2004-0025-0001`, $99.99, in stock. Same 4th wire, smaller. | Yes, for light robots |
| goBILDA | Axon Servo Programmer MK2, `3102-0002-0001`, $59.99. Loads the continuous-rotation firmware ("CR Mode" `.sfm` file). | Needed once per servo |
| REV | 3in MAXSwerve `REV-21-3005` ($275), 4in EasySwerve `REV-21-3006` ($223), drivetrain kits $2,000+. NEO motors, SPARK controllers. | No: FRC only |
| REV | Through Bore Encoder V2 `REV-11-3174`. Its absolute output is a pulse (duty cycle); REV says the FTC hubs cannot read it. Quadrature works on a motor encoder port. | Only as a relative encoder: needs lining up at every power-on |
| AndyMark | SDS MK4 / MK4i / MK4n / MK4c ($283.90–$345.10), MK5i/MK5n ($395, sold out). Their own Swerve & Steer is discontinued. | No: FRC only |

Not checked: the Axon feedback wire's voltage range (the goBILDA pages do not say, and the finder measures
it), and community FTC pod designs (they are not sold products).

Game rules (BIOBUZZ manual TU01): R502 allows "4th Wire Position Feedback". It caps a servo at 8 W at
6 V (the Axon MAX MK2 is listed at 7.5 W). R504 C allows continuous-rotation mode "as specified by the
manufacturer". R503: at most 8 motors and 8 servos. **A 4-pod swerve uses 4 of the 8 motors, 4 of the
8 servos and 4 analog ports** (all four on one hub). The Axon servos need 6 V, and the hubs give 5 V. So the robot needs a servo power module
or the REV Servo Hub.

## Effort estimate (agent work plus mentor review; robot testing on top)

| Part | Days |
|---|---|
| `SwerveMixer`, `SwervePodControl`, `SwerveDrive`, `Drive.swerve` line, `DriveType`, `DriverControls`, tests | 3–4 |
| `AutoDrive` refactor (move/turn/driveTo), Drive Tuner changes, tests | 2–3 |
| Pedro: pod wrapper, `LimitedSwerve`, frame, tests | 1–2 |
| Simulator: `AnalogInput`, pod model, robot file, `SimNewRobot`, robot-file skill, scenarios | 3 |
| Alignment finder + steering tune + scenario test | 2 |
| Docs: `modules/drive.md`, `robot.md`, `pedro.md`, a tutorial section, troubleshooting, CHANGELOG | 2–3 |
| **Total** | **13–17 (about 3 weeks)** |

On a real robot, after the team builds the pods, it needs one or two practice nights to line up and tune them.

## Options for the mentor

1. **Not now (0.5 day).** Keep the Pedro escape hatch. Add one tested example of Pedro's own `Swerve`
   in `docs/modules/pedro.md`, with the `turnController` warning. This fits when no team plans swerve this
   season.
2. **Swerve "first step" (about 2 weeks).** The `Drive.swerve` line, TeleOp, Pedro (with our pod
   inside), `driveTo` with odometry, the alignment finder and the simulator. Leave out `move`/`turn`
   without odometry, and refuse them with a clear message. This fits when a team builds pods this season: Pedro
   or `driveTo` covers autonomous.
3. **Full support (about 3 weeks).** Everything above, plus the `AutoDrive` refactor, so `move`/`turn`
   work without odometry, the same as mecanum.

**Recommendation: option 1 now, option 2 when a team has a working prototype pod.** No seller
has an FTC swerve pod. So every team must design, cut and build four pods first. Until then, nobody can test the library work
on hardware. Swerve also takes half the motor and servo budget, which is tight
for a robot with a turret, lifter and intake. Option 2 gets a team driving and running Pedro paths.
Option 3's extra week mostly serves teams without odometry, and a swerve team will have odometry.

## Sources

- Pedro Pathing 3.0.2 `revhub` and `core` jars, read with `javap`.
- goBILDA: [search "swerve"](https://www.gobilda.com/search.php?search_query=swerve),
  [Axon MAX MK2](https://www.gobilda.com/axon-max-servo-mk2/),
  [Axon MINI MK2](https://www.gobilda.com/axon-mini-servo-mk2/),
  [Axon Servo Programmer MK2](https://www.gobilda.com/axon-servo-programmer-mk2/) (all 2026-10-21).
- REV: [search "swerve"](https://www.revrobotics.com/search.php?search_query=swerve),
  [Through Bore Encoder V2 docs](https://docs.revrobotics.com/rev-crossover-products/sensors/tbe) (2026-10-21).
- AndyMark: [search "swerve"](https://www.andymark.com/search?q=swerve) (2026-10-21, first 24 of 58 results).
- BIOBUZZ Competition Manual TU01: R502, R503, R504.
