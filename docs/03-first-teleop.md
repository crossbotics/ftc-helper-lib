# Your first TeleOp with the library

This walk-through builds a TeleOp step by step: a mecanum drive with a slow mode, a toggle,
and a lift with safe limits. The finished file is
[`ExampleTeleOp.java`](../examples/src/main/java/io/github/jasoncross/ftchelper/examples/ExampleTeleOp.java).

Before you start: [add the library to your project](01-add-to-project.md), and
[check the motor directions](modules/drive.md#check-motor-directions).

## 1. The empty OpMode

Make `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/MainTeleOp.java`:

```java
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Main TeleOp")
public class MainTeleOp extends LinearOpMode {
    @Override
    public void runOpMode() {
        // INIT: set up hardware here

        waitForStart();

        while (opModeIsActive()) {
            // RUN: this repeats about 50-200 times per second
        }
    }
}
```

Android Studio adds `import` lines for you: when a name is red, click it and press **Alt+Enter**
(Mac: **Option+Enter**) and pick the `io.github.jasoncross.ftchelper...` class.

## 2. Drive

In INIT:

```java
BulkReads.auto(hardwareMap);                       // faster loop
DriveConfig driveConfig = new DriveConfig();
driveConfig.powerLimit = 0.5;                      // careful while testing
MecanumDrive drive = new MecanumDrive(hardwareMap, driveConfig);
```

In the loop:

```java
double forward = StickShaping.shape(-gamepad1.left_stick_y, 0.05, 2);
double strafe  = StickShaping.shape(gamepad1.left_stick_x, 0.05, 2);
double turn    = StickShaping.shape(gamepad1.right_stick_x, 0.05, 2);
double speed   = StickShaping.slowScale(gamepad1.left_trigger, 0.3);
drive.drive(forward, strafe, turn, speed);
```

- The minus on `left_stick_y` is needed: the SDK says "up" is negative.
- `shape(..., 0.05, 2)` ignores tiny stick noise and gives finer control at low speed.
- Pull the left trigger to slow down to 30%.

Build, run, drive. If something goes the wrong way: [Check motor directions](modules/drive.md#check-motor-directions).

## 3. Buttons

Add a field to the class (outside `runOpMode`), so the button code can change it:

```java
private boolean fieldCentric = false;
```

In INIT, after the drive:

```java
IMU imu = ImuSetup.get(hardwareMap, "imu",
        RevHubOrientationOnRobot.LogoFacingDirection.UP,        // match YOUR hub
        RevHubOrientationOnRobot.UsbFacingDirection.FORWARD);
ToggleRumble confirm = new ToggleRumble(gamepad1);

ControllerBindings controls = new ControllerBindings()
        .bindToggle(Pad.G1, Btn.Y,
                () -> { fieldCentric = true;  confirm.play(true);  },
                () -> { fieldCentric = false; confirm.play(false); })
        .bindPress(Pad.G1, Btn.BACK, imu::resetYaw);
```

After `waitForStart();`:

```java
controls.ignoreHeldButtons(gamepad1, gamepad2);
```

At the top of the loop, and change the drive line:

```java
controls.update(gamepad1, gamepad2);
...
if (fieldCentric) {
    drive.driveFieldCentric(forward, strafe, turn, ImuSetup.headingDeg(imu), speed);
} else {
    drive.drive(forward, strafe, turn, speed);
}
```

Now Y switches field-centric on (two buzzes) and off (one buzz). BACK resets "away from me":
point the robot away from you first.

## 4. A lift that cannot break itself

In INIT:

```java
DcMotorEx liftMotor = MotorSetup.get(hardwareMap, "lift")
        .brake().resetEncoder().runWithoutEncoder().done();
SafeMotor lift = new SafeMotor(liftMotor)
        .setLimits(0, 2900)          // measure YOUR top position first
        .setSlowZone(200, 0.25);
```

Turn the robot on with the lift all the way down: that is position 0.
To find the top number, show `lift.getPosition()` on telemetry and raise the lift by hand-power slowly.

In the loop (after `controls.update`):

```java
lift.update();
lift.setPower(StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1));
```

The operator's left stick now moves the lift, and it stops by itself at 0 and 2900.

### Buttons for set heights

Add a field `private Integer liftTarget = null;` (null means "by hand"), make a PID controller in INIT,
and bind buttons:

```java
PidController liftPid = new PidController(0.004, 0, 0.0002).setTolerance(20);
controls.bindPress(Pad.G2, Btn.A, () -> { liftTarget = 0;    liftPid.reset(); })
        .bindPress(Pad.G2, Btn.Y, () -> { liftTarget = 2600; liftPid.reset(); });
```

In the loop, replace the lift line:

```java
double liftStick = StickShaping.shape(-gamepad2.left_stick_y, 0.1, 1);
if (liftStick != 0) liftTarget = null;            // the stick takes over
if (liftTarget == null) lift.setPower(liftStick);
else lift.moveTo(liftTarget, liftPid);
```

Tune the PID numbers: [control.md](modules/control.md#how-to-tune-do-it-in-this-order).

## 5. Telemetry and clean stop

At the end of the loop:

```java
TelemetryHelper.section(telemetry, "Lift");
TelemetryHelper.value(telemetry, "Position", lift.getPosition(), "ticks");
TelemetryHelper.flag(telemetry, "At bottom", lift.isAtLower());
telemetry.update();
```

Wrap the loop in `try { ... } finally { drive.stop(); lift.stop(); }` so the motors always stop,
even if your code has an error. The example file shows the full shape.

## Next

- [Your first autonomous](04-first-auto.md)
- Every function: the [module pages](../README.md#what-is-inside)
