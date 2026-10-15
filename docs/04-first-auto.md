# Your first autonomous with the library

This walk-through builds an autonomous with `AutoDrive`. The finished file is
[`ExampleAuto.java`](../examples/src/main/java/io/github/jasoncross/ftchelper/examples/ExampleAuto.java).

Before you start: the drive works in TeleOp, and the IMU heading goes UP when you turn the robot
left ([check it](modules/hardware.md#imusetup)).

## 1. Shared drive settings

TeleOp and autonomous must use the same motor names and directions. Put them in one place.
Make `TeamCode/.../RobotSettings.java`:

```java
package org.firstinspires.ftc.teamcode;

import io.github.jasoncross.ftchelper.drive.AutoDriveConfig;
import io.github.jasoncross.ftchelper.drive.DriveConfig;

public final class RobotSettings {
    public static DriveConfig drive() {
        DriveConfig c = DriveConfig.mecanum();   // or DriveConfig.tank() / tankTwoMotor()
        c.powerLimit = 0.8;
        return c;
    }

    public static AutoDriveConfig auto() {
        AutoDriveConfig c = new AutoDriveConfig();
        c.ticksPerRev = 537.7;          // goBILDA 312 RPM
        c.wheelDiameterIn = 3.78;       // 96 mm wheels, then tune
        c.calibrationConfirmed = false; // set true after tuning (step 3)
        return c;
    }
}
```

In TeleOp, use `new MecanumDrive(hardwareMap, RobotSettings.drive())`.

## 2. The autonomous OpMode

```java
@Autonomous(name = "Main Auto")
public class MainAuto extends LinearOpMode {
    @Override
    public void runOpMode() {
        BulkReads.auto(hardwareMap);
        IMU imu = ImuSetup.get(hardwareMap, "imu",
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
        AutoDrive auto = new AutoDrive(this, RobotSettings.drive(), RobotSettings.auto(), imu);

        waitForStart();

        auto.move(24, 0, 0.6);    // 24 inches forward
        auto.turn(90, 0.5);       // turn left 90 degrees
        auto.move(12, 0, 0.5);    // 12 inches forward (the new forward)
    }
}
```

Directions in `move`: 0 forward, 90 right, 180 back, -90 left. Turns: positive = left.
On a standard (tank) drive, `move` only takes 0 or 180: turn first, then move.

## 3. Tune, then confirm

`AutoDrive` refuses to run while `calibrationConfirmed` is false. That is on purpose: an
untuned robot drives the wrong distance. Follow [Tune AutoDrive](modules/drive.md#tune-autodrive),
then set `calibrationConfirmed = true`.

## 4. Do other things while driving

Moves wait until they finish, so the rest of your code does not run during them. To keep a lift
or flywheel working while the robot drives, give `AutoDrive` a loop hook:

```java
auto.setLoopHook(() -> lift.moveTo(liftTarget, liftPid));
```

Your hook runs once per loop inside every move and turn. Keep it quick (no `sleep`).

## 5. When something goes wrong

Each move returns a result: `DONE`, `STALLED` (stuck), `TIMED_OUT`, or `STOPPED`.

```java
AutoDrive.Result r = auto.move(30, 0, 0.7);
telemetry.addData("Move 1", r);
telemetry.update();
```

## 6. Hand the pose to TeleOp

At the end:

```java
PoseStore.save(new FieldPose(0, 0, auto.headingDeg()));
```

In TeleOp INIT, `PoseStore.consume()` gives it back (or `null` if there is none).
See [util.md](modules/util.md#posestore).
