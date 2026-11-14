# Tutorial 04: Drive a mecanum robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

In the five files, one `drive(...)` line in `Robot.java` gives you all of this: [Robot.java](../five-files/01-robot-java.md).
Build the drive by hand here with `MecanumDrive` to see what that line does inside.

**You will build:** a TeleOp that drives a mecanum robot, with slow mode, field-centric driving and reverse drive.

**You need:** a mecanum chassis with motors `front_left`, `front_right`, `back_left`, `back_right`, the
hub's IMU (`imu`), and a block to lift the wheels off the ground.

**Full program:** [`Tut04MecanumDrive.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut04MecanumDrive.java)

---

## 1. How mecanum wheels move

Each mecanum wheel has rollers at 45 degrees. The robot spins the four wheels in different
combinations to drive forward, sideways ("strafe"), turn, or all three at once. The library does the
math (in `MecanumMixer`). You only say what you want ([Signs and units](../conventions.md)):

```java
drive.drive(forward, strafeRight, turnClockwise);   // each -1 to 1
```

## 2. Set up the drive

```java
DriveConfig driveConfig = DriveConfig.mecanum();
driveConfig.powerLimit = 0.6;
MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);
```

`DriveConfig` holds the motor names, which motors are reversed, and the power limit. The defaults
(`front_left` ... `back_right`, left side reversed) fit most goBILDA and REV chassis. For other names:
`driveConfig.frontLeft = "fl";`.

`powerLimit = 0.6` means no wheel ever gets more than 60%. Keep it low until everything works.

## 3. Check the motor directions FIRST

With one motor reversed wrongly, the robot spins or drifts, and nothing else makes sense. With the robot
on a block, run [`MotorDirectionCheck`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/MotorDirectionCheck.java)
and hold X, Y, A, B to spin one wheel each. Every wheel must roll the way that moves the robot
**forward**. Flip a wrong one in your config, for example `driveConfig.backRightReversed = true;`.
More: [Motor Direction Check](../tuning/02-wiring-checks.md#motor-direction-check-each-wheel-rolls-forward).

## 4. The basic drive loop

```java
double forward = -gamepad1.left_stick_y;
double strafe  =  gamepad1.left_stick_x;
double turn    =  gamepad1.right_stick_x;
drive.drive(forward, strafe, turn);
```

The minus on `left_stick_y`: the SDK says "up" is negative.

## 5. Make the sticks feel good

```java
double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
double strafe  = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
double turn    = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
```

`StickShaping.shape(value, deadband, exponent)` changes the raw stick number before it goes to the motors:

- **Deadband 0.05:** a stick at rest is often not at exactly 0. Without a deadband, the robot creeps.
- **Exponent 2:** half stick gives a quarter of the power. Small moves become precise, and a full push is
  still full power. Try 1 (straight) and 3 (very soft), and pick one.

## 6. Slow mode on a trigger

```java
double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
drive.drive(forward, strafe, turn, speed);
```

Trigger let go = full speed. Pulled all the way = 30%. Use it to line up to score.

## 7. Field-centric driving

With **robot-centric** driving, "stick up" means "where the robot faces". When the robot faces the
driver, everything feels backwards. With **field-centric** driving, "stick up" always means "away from
the driver". For that, the robot must know its **heading** (which way it faces, in degrees). The **IMU**,
a turn sensor inside the Control Hub, measures it.

```java
IMU imu = ImuSetup.get(hardwareMap, "imu",
        RevHubOrientationOnRobot.LogoFacingDirection.UP,
        RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
...
drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
```

**The two directions must match how YOUR hub is mounted.** Wrong values give a wrong heading, and
field-centric driving goes the wrong way. How to read them and check them:
[How your Control Hub is mounted](../five-files/01-robot-java.md#how-your-control-hub-is-mounted).

The heading is 0 where the robot pointed when the IMU started. So give the driver a button that says
"forward is where the robot points now". The driver points the robot away first:

```java
.bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
```

`imu::resetYaw` is a short way to write `() -> imu.resetYaw()`. Yaw is the IMU's word for heading.
In the program, Y toggles between the two styles, with a rumble to confirm.

## 8. Reverse drive

To load from the back, the driver may want the back to act as the front. `ReverseDrive` does that,
for forward and strafe only:

```java
ReverseDrive reverse = new ReverseDrive();
.bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()))
...
drive.drive(reverse.apply(forward), reverse.apply(strafe), turn, speed);
```

`reverse.toggle()` flips the mode and returns the new state, so `confirm.play(...)` buzzes twice for ON
and once for OFF. In field-centric mode the robot's front does not matter, so the program uses reverse
only in robot-centric mode. In `finally`, put `drive.stop();`.

## Run it

1. With the wheels off the ground, push the stick up: all wheels roll forward. Then drive on the floor.
2. Stick up = forward, stick right = slides right, right stick right = turns clockwise.
3. Left trigger slows the robot down.
4. Y (two buzzes): field-centric. Turn the robot 90 degrees and push stick up: it still goes away from you.
5. Point the robot away and press BACK: it resets "forward".
6. Click the left stick (two buzzes): the back is now the front.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Spins when it should go straight | A motor direction is wrong. Section 3. |
| Slides diagonally when strafing | Two motor names are on the wrong wheels (for example front_left and back_left swapped). |
| Field-centric goes the wrong way, or circles | The IMU directions do not match the hub, or the driver did not reset "forward" with the robot facing away. |
| Robot creeps with sticks let go | Raise the deadband to 0.08. |
| "MecanumDrive needs a MECANUM DriveConfig" | You used `DriveConfig.tank()`. Use `DriveConfig.mecanum()` or `TankDrive`. |

## Try this

1. Change the exponent to 3 for turning only. Check whether aiming is easier.
2. Put field-centric ON by default.
3. Make a "precision mode" toggle that sets a fixed `speed = 0.35`, instead of the trigger.

---

**Next:** [Tutorial 05: Drive a standard (tank) robot](05-drive-tank.md), or
[Tutorial 06: A fast, clear loop](06-fast-clear-loop.md) when you have no tank drive.
**Look it up:** [drive.md](../modules/drive.md), [hardware.md: ImuSetup](../modules/hardware.md#imusetup), [gamepad.md](../modules/gamepad.md).
