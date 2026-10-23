# Tutorial 04: Drive a mecanum robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a TeleOp that drives a mecanum robot, with slow mode, field-centric driving and reverse drive.
The main track gives you all of this in one line: [Drive](start/02-drive.md).

**You will learn:** how to set up a mecanum drivetrain, check its motor directions, make the
sticks feel good, add a slow mode, switch to field-centric driving, and drive "backwards".

**You need:** a mecanum chassis with motors `front_left`, `front_right`, `back_left`,
`back_right`, and the hub's IMU (`imu`). Tutorials 01-03. **A block to lift the wheels off the ground.**

**Full program:** [`Tut04MecanumDrive.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut04MecanumDrive.java)
**Reference:** [drive.md](../modules/drive.md), [Signs and units](../conventions.md)

---

## 1. How mecanum wheels move

Each mecanum wheel has rollers at 45 degrees. By spinning the four wheels in different
combinations, the robot can drive forward, sideways ("strafe"), turn, or all three at once.
The library does the math (in `MecanumMixer`). You only say what you want:

```java
drive.drive(forward, strafeRight, turnClockwise);   // each -1 to 1
```

## 2. Set up the drive

```java
DriveConfig driveConfig = DriveConfig.mecanum();
driveConfig.powerLimit = 0.6;
MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);
```

`DriveConfig` holds the motor names, which motors are reversed, and the power limit. The
defaults (`front_left` ... `back_right`, left side reversed) fit most goBILDA and REV chassis.
If your names differ, change them: `driveConfig.frontLeft = "fl";`.

`powerLimit = 0.6` means no wheel ever gets more than 60%. Keep it low until everything works.
`new MecanumDrive(...)` finds the four motors. It stops with a clear message if a name is wrong.

## 3. Check the motor directions FIRST

If one motor is reversed wrongly, the robot spins or drifts in strange ways, and nothing else
will make sense. Check before driving on the floor:

1. Wheels off the ground (robot on a block).
2. Run [`MotorDirectionCheck`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/MotorDirectionCheck.java)
   (copy it like a tutorial program). Hold X, Y, A, B to spin one wheel each.
3. Every wheel must roll the way that would move the robot **forward**.
4. Wrong one? Flip it in your config, e.g. `driveConfig.backRightReversed = true;`.

## 4. The basic drive loop

```java
double forward = -gamepad1.left_stick_y;
double strafe  =  gamepad1.left_stick_x;
double turn    =  gamepad1.right_stick_x;
drive.drive(forward, strafe, turn);
```

Remember the minus on `left_stick_y` (the SDK says "up" is negative).

On the floor, check: stick up = forward, stick right = slides right, right stick right = turns
clockwise. If it slides diagonally, two motor names are swapped between wheels.

## 5. Make the sticks feel good

```java
double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
double strafe  = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
double turn    = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
```

`StickShaping.shape(value, deadband, exponent)` changes the raw stick number before it goes to the motors:

- **Deadband 0.05:** sticks rarely rest at exactly 0. Without this, the robot creeps.
- **Exponent 2:** half stick gives a quarter of the power. Small moves become precise; a full
  push is still full power. Drivers usually love it. Try 1 (straight) and 3 (very soft) and pick.

## 6. Slow mode on a trigger

```java
double speed = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
drive.drive(forward, strafe, turn, speed);
```

Trigger let go = full speed. Pulled all the way = 30%. In between = in between. Great for lining
up to score.

## 7. Field-centric driving

With **robot-centric** driving (what we have so far), "stick up" means "where the robot faces".
When the robot faces the driver, everything feels backwards.

With **field-centric** driving, "stick up" always means "away from the driver", whichever way
the robot faces. It needs the **IMU** to know which way the robot faces. The IMU is a sensor inside the Control Hub
that measures turning. The direction the robot faces is its **heading**, in degrees.

```java
IMU imu = ImuSetup.get(hardwareMap, "imu",
        RevHubOrientationOnRobot.LogoFacingDirection.UP,
        RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
...
drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
```

**The two directions must match how YOUR hub is mounted:** which way the REV logo points and
which way the USB ports point. Wrong values = wrong heading = field-centric goes crazy.
Check: show `ImuSetup.headingDeg(imu)` on telemetry, turn the robot LEFT by hand: the number must go UP.

The heading is 0 where the robot pointed when the IMU started. Give the driver a button to say
"forward is where the robot points now" (point it away from you first):

```java
.bindPress(Pad.G1, Btn.BACK, imu::resetYaw)
```

`imu::resetYaw` (two colons) is a short way to write the lambda `() -> imu.resetYaw()`.
Yaw is the IMU's word for heading.

The program toggles between the two styles with Y, with rumble confirmation (Tutorial 03).

## 8. Reverse drive

Sometimes the driver must look at the back of the robot (to load something). `ReverseDrive`
makes the back act as the front, for forward and strafe only:

```java
ReverseDrive reverse = new ReverseDrive();
.bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()))
...
drive.drive(reverse.apply(forward), reverse.apply(strafe), turn, speed);
```

`reverse.toggle()` flips the mode and returns the new state, so `confirm.play(...)` buzzes twice
for ON and once for OFF. (Reverse only makes sense in robot-centric mode, so the program uses it there.)

## 9. Always stop in `finally`

```java
} finally {
    drive.stop();
}
```

On the screen, `fieldCentric ? "field-centric" : "robot-centric"` means: if `fieldCentric` is
true, use the first text, otherwise the second.

## Run it

1. Wheels off the ground: stick up, all wheels roll forward. Then on the floor, at 0.6 power.
2. Forward, strafe, turn, and all three together all move as expected.
3. Small stick moves are slow and smooth.
4. Left trigger slows the robot down.
5. Y (two buzzes): field-centric. Turn the robot 90 degrees and push stick up: it still goes away from you.
6. BACK while facing away resets "forward".
7. Click the left stick (two buzzes): the back is now the front.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Spins when it should go straight | A motor direction is wrong. Section 3. |
| Slides diagonally when strafing | Two motor names are on the wrong wheels (e.g. front_left and back_left swapped). |
| Field-centric goes the wrong way, or circles | IMU directions do not match the hub, or "forward" was not reset while facing away. |
| Robot creeps with sticks let go | Raise the deadband to 0.08. |
| "MecanumDrive needs a MECANUM DriveConfig" | You used `DriveConfig.tank()`. Use `DriveConfig.mecanum()` or `TankDrive`. |

## Try this

1. Change the exponent to 3 for turning only. Is aiming easier?
2. Put field-centric ON by default.
3. Make a "precision mode" toggle that sets a fixed `speed = 0.35`, instead of the trigger.
4. Show which mode is active in big letters at the top of telemetry for the driver.

## Next

[Tutorial 06: A fast, clear loop](06-fast-clear-loop.md)
(or [05](05-drive-tank.md) if you also have a standard drivetrain)
