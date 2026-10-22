# Test kit module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.testkit`

Learn it hands-on: [13 Testing](../tutorials/13-testing.md#7-test-your-robot-in-five-files). Guide: [Testing procedures](../guides/testing.md#test-your-robotjava-with-pretend-devices).

Pretend motors, servos and switches for **JUnit tests on a laptop**: your `Robot.java` (the robot in
five files) runs on them instead of the real hardware. A test sets the robot up (INIT), presses START,
runs a loop, and checks what each motor and servo was sent. No robot, no Control Hub: a test takes a
second, so you can run them after every change.

| Class | Use it to |
| --- | --- |
| [`PretendDevices`](#pretenddevices) | give your robot pretend devices instead of the hardwareMap |
| [`PretendMotor`](#pretendmotor-pretendservo-pretendcrservo) | read the power sent, set the encoder |
| [`PretendServo`](#pretendmotor-pretendservo-pretendcrservo) | read the position sent |
| [`PretendCRServo`](#pretendmotor-pretendservo-pretendcrservo) | read the power sent to a CR servo |
| [`PretendPinpoint`](#pretendpinpoint) | a goBILDA Pinpoint that says the robot is where the test puts it |
| [`PretendTelemetry`](#pretendtelemetry) | read what the Driver Station would show |

**Why in the library:** a team's tests (in `TeamCode/src/test`) can use it with no extra Gradle lines
(only JUnit, [tutorial 13](../tutorials/13-testing.md#3-set-up-teamcode-for-tests-once)). It is a few
small classes; nothing in it runs on the robot unless a test makes it.

## PretendDevices

```java
PretendDevices devices = new PretendDevices();   // a fresh pretend robot
Robot robot = new Robot();
robot.setup(devices);                            // INIT (Tunables.java as written; INIT's checks run)
robot.startForTests();                           // START
robot.lift.goTo("high");
robot.all().update();                            // one loop
assertTrue(devices.motor("lift").power > 0);     // the lift motor got power up
devices.motor("lift").position = 2400;           // the pretend encoder: at the top
devices.press("lift_bottom", true);              // a pretend switch pressed
```

| Method | What it does |
| --- | --- |
| `new PretendDevices()` | a pretend configuration where every name asked for exists. It also forgets what the library keeps between programs (tuned numbers in memory, a turret's last angle, a launcher's last trim, autonomous's alliance: `RobotDefinition.freshRobotApp()`), uses an empty temporary settings file, and makes motors read their pretend encoder every time (no 3 ms reuse): each test starts like a robot just turned on |
| `motor(name)`, `servo(name)`, `crServo(name)`, `pinpoint(name)` | the pretend device with that name (made now if needed) |
| `press(name, pressed)` | press or let go of a pretend switch (a touch sensor, or a magnetic switch on a digital port) |
| `analog(name, volts, maxVolts)` | a pretend analog input (an absolute encoder) |
| `notConfigured(names...)` | these names are NOT in the configuration (test an `optional()` part, or `checkConfiguration`) |
| `battery(volts)` | the battery the hubs read |
| `get`, `tryGet`, `has` | what the robot asks ([`Devices`](robot.md#robotdefinition)). A kind the kit has no pretend one for (a camera, a color sensor) counts as not plugged in: parts that can do without it warn and go on |

With the robot: `robot.setup(devices)` (INIT), `robot.startForTests()` (START), `robot.all().update()`
(one loop), `robot.pauseAll()` / `robot.resume()` (BACK), `robot.all().stop()` (STOP),
`robot.checkTunables()` (the Tunables name checks, no devices needed). See [RobotDefinition](robot.md#robotdefinition).

**What it cannot do:** move anything. A pretend motor's encoder stays where your test puts it, time does
not pass by itself (an action's `waitSeconds` never ends in a test), and there is no camera picture. For
that, use the [simulator](../guides/simulator.md) (a scenario file is a test too).

## PretendMotor, PretendServo, PretendCRServo

Plain fields your test reads and sets:

| Class | Fields |
| --- | --- |
| `PretendMotor` | `power` (last sent), `position` (the encoder: set it), `velocity`, `currentAmps`, `direction`, `zeroPower`, `mode`, `powerWrites`, `maxAbsPower`, `failSetPower` (true: `setPower` throws, like an unplugged motor), `motor` (the SDK `DcMotorEx`) |
| `PretendServo` | `position` (last sent; NaN = never), `writes`, `direction`, `pulseUs` (set by `.fullRange()`), `servo` (the SDK `Servo`) |
| `PretendCRServo` | `power`, `writes`, `direction`, `servo` (the SDK `CRServo`) |

## PretendPinpoint

A robot with `odometry(Odometry.pinpoint("pinpoint"))` gets one at INIT. It never moves by itself:

```java
devices.pinpoint("pinpoint").moveTo(10, 0, 90);   // the device's own frame: x forward, y left (inches), degrees
robot.all().update();                             // robot.pose() now has it (from the start spot)
```

| Member | What it is |
| --- | --- |
| `x`, `y`, `headingDeg` | where the device says it is (its own frame, inches and degrees) |
| `moveTo(x, y, headingDeg)` | sets all three |

## PretendTelemetry

```java
PretendTelemetry screen = new PretendTelemetry();
RobotTelemetry t = new RobotTelemetry(screen.telemetry);
robot.all().addTelemetry(t);
t.update();
assertTrue(screen.has("Lift : high"));
```

| Member | What it is |
| --- | --- |
| `telemetry` | the SDK `Telemetry` to hand over |
| `lastScreen` | the last screen sent, one line per entry (`"Lift : high  900 ticks"`, `"!! warning"`) |
| `has(words)` | true if a line of the last screen contains them |
| `updates` | how many screens were sent |
