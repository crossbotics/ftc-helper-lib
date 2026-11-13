# Tutorial 05: Drive a standard (tank) robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a TeleOp that drives a standard (tank) robot, with a button to switch between two driving styles.
The main track sets up a tank drive in one line: [Robot.java](../five-files/01-robot-java.md).

**You will learn:** how to set up a standard drivetrain (also called tank, differential, or
"skid steer"), the two ways to drive it (arcade and tank style), and how the 2- and 4-motor versions are
different.

**You need:** a standard chassis with two motors `left_drive` and `right_drive`
(or four: `front_left`, `front_right`, `back_left`, `back_right`). Do Tutorials 01-03 first.

**Full program:** [`Tut05TankDrive.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut05TankDrive.java)
**Reference:** [drive.md: TankDrive](../modules/drive.md#tankdrive)

---

## 1. How a standard drive moves

A standard drive has normal wheels on a left side and a right side. Both sides forward = drive forward.
Left forward, right back = spin right. It cannot move sideways.

## 2. Set up the drive

```java
DriveConfig driveConfig = DriveConfig.tankTwoMotor();   // DriveConfig.tank() for 4 motors
driveConfig.powerLimit = 0.6;
TankDrive drive = new TankDrive(hardwareMap, driveConfig);
```

| Your robot | Config | Motor names |
| --- | --- | --- |
| 2 motors | `DriveConfig.tankTwoMotor()` | `left_drive`, `right_drive` (same as the FTC samples) |
| 4 motors | `DriveConfig.tank()` | `front_left`, `front_right`, `back_left`, `back_right` |

`powerLimit = 0.6` keeps every wheel at 60% or less while you test. Raise it when the robot drives
correctly. When a motor name is wrong, `new TankDrive(...)` stops with a clear message.

With 4 motors, both motors on a side always get the same power.

The motors on the left face the other way, so the library reverses the left side by default. Check it
with [`MotorDirectionCheck`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/MotorDirectionCheck.java):
set its `config()` to `DriveConfig.tankTwoMotor()` and lift the wheels off the ground. X = left,
Y = right. Both must roll forward.

## 3. Arcade style

One stick moves forward and back, and the other turns. For most new drivers, this style is the easiest.

```java
drive.arcade(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
        StickShaping.shape(gamepad1.right_stick_x, 0.05, 2), speed);
```

The library adds and subtracts for you: left = forward + turn, right = forward - turn. When a side
goes past full power, the library scales both sides down together. Then the robot keeps the curve you
asked for.

## 4. Tank style

Each stick drives one side, like a tank or a bulldozer. It is very direct, and some drivers like it
better.

```java
drive.tank(StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2),
        StickShaping.shape(-gamepad1.right_stick_y, 0.05, 2), speed);
```

BOTH sticks' Y need the minus.

## 5. Let the driver choose

```java
.bindToggle(Pad.G1, Btn.X,
        () -> { tankStyle = true; confirm.play(true); },
        () -> { tankStyle = false; confirm.play(false); })
```

Let each driver try both styles for a practice session. Keep the one they drive best with.

## 6. Slow mode and stopping

These work the same as on mecanum: `StickShaping.slowScale(gamepad1.left_trigger, 0.3)` as the last
argument, and `drive.stop()` in `finally`.

## 7. Autonomous on a standard drive

`AutoDrive` (Tutorial 09) works on a standard drive too, but only forward/back and turns:
`move(24, 0, ...)`, `move(24, 180, ...)`, `turn(...)`, `turnTo(...)`. A sideways move gives an error that
says "turn first, then move".

## Run it

1. With the wheels off the ground, push the stick up: both sides roll forward.
2. On the floor, arcade style drives and turns as expected. Right stick right = turn right.
3. X (two buzzes): tank style. Both sticks up = forward. Left up, right down = spin right.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Spins when both sides should go forward | One side's direction is wrong: flip `frontLeftReversed` or `frontRightReversed`. (With 2 motors, the library stores `left_drive` and `right_drive` as the "front" motors.) |
| Turns the wrong way in arcade | Left and right motor names are swapped. |
| "TankDrive needs a TANK DriveConfig" | Use `DriveConfig.tank()` or `tankTwoMotor()`. |
| 4-motor robot: one wheel fights the others | That one motor's direction is wrong. Use MotorDirectionCheck. |

## Try this

1. Add `ReverseDrive` to arcade mode (Tutorial 04, section 8).
2. Make turning gentler than driving: multiply the turn by 0.7.
3. With 4 motors: change the config to `DriveConfig.tank()` and check all four with MotorDirectionCheck.

## Next

[Tutorial 06: A fast, clear loop](06-fast-clear-loop.md)
