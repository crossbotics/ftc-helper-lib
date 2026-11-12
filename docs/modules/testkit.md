# Test kit module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.testkit`

Learn it hands-on: [13 Testing](../tutorials/13-testing.md#7-test-your-robot-in-five-files). Guide: [Testing procedures](../guides/testing.md#test-your-robotjava-with-pretend-devices).

The test kit gives you pretend motors, servos and switches for **JUnit tests on a laptop**. Your `Robot.java`
(the robot in five files) runs on them instead of the real hardware. A test sets the robot up (INIT), presses ▶
(start on the Driver Hub), runs a loop, and checks what the robot sent to each motor and servo. A test needs no
robot and no Control Hub, and it takes a second. So you can run the tests after every change.

| Class | Use it to |
| --- | --- |
| [`PretendDevices`](#pretenddevices) | give your robot pretend devices instead of the hardwareMap |
| [`PretendMotor`](#pretendmotor-pretendservo-pretendcrservo) | read the power sent, set the encoder |
| [`PretendServo`](#pretendmotor-pretendservo-pretendcrservo) | read the position sent |
| [`PretendCRServo`](#pretendmotor-pretendservo-pretendcrservo) | read the power sent to a CR servo |
| [`PretendPinpoint`](#pretendpinpoint) | a goBILDA Pinpoint that says the robot is where the test puts it |
| [`PretendTelemetry`](#pretendtelemetry) | read what the Driver Station would show |
| [`PretendClock`](#pretendclock) | move the time forward by hand: a timing test does not wait |

**Why it is in the library:** a team's tests (in `TeamCode/src/test`) can use it with no extra Gradle lines
(only JUnit, [tutorial 13](../tutorials/13-testing.md#3-set-up-teamcode-for-tests-once)). It is a few
small classes. Nothing in it runs on the robot unless a test makes it.

## PretendDevices

```java
PretendDevices devices = new PretendDevices();   // a fresh pretend robot
Robot robot = new Robot();
robot.setup(devices);                            // INIT (Tunables.java as written; INIT's checks run)
robot.startForTests();                           // ▶ (start)
robot.lift.goTo("high");
robot.all().update();                            // one loop
assertTrue(devices.motor("lift").power > 0);     // the lift motor got power up
devices.motor("lift").position = 2400;           // the pretend encoder: at the top
devices.press("lift_bottom", true);              // a pretend switch pressed
```

| Method | What it does |
| --- | --- |
| `new PretendDevices()` | a pretend configuration where every name the robot asks for exists. Each test starts like a robot that you turned on a moment ago. For that, it forgets what the library keeps between programs (tuned numbers in memory, a turret's last angle, a launcher's last trim, autonomous's alliance: `RobotDefinition.freshRobotApp()`). It uses an empty temporary settings file. Its motors read their pretend encoder every time (no 3 ms reuse) |
| `motor(name)`, `servo(name)`, `crServo(name)`, `pinpoint(name)` | the pretend device with that name. It makes the device now when there is none yet |
| `press(name, pressed)` | press or let go of a pretend switch (a touch sensor, or a magnetic switch on a digital port) |
| `analog(name, volts, maxVolts)` | a pretend analog input (an absolute encoder) |
| `notConfigured(names...)` | takes these names out of the configuration. Use it to test an `optional()` part, or `checkConfiguration` |
| `battery(volts)` | the battery the hubs read |
| `get`, `tryGet`, `has` | what the robot asks ([`Devices`](../reference/robot-java.md#robotdefinition)). The kit has no pretend camera or color sensor. Such a device counts as not plugged in: a part that can do without it warns and goes on |

With the robot: `robot.setup(devices)` (INIT), `robot.startForTests()` (what ▶ does), `robot.all().update()`
(one loop), `robot.pauseAll()` / `robot.resume()` (BACK), `robot.all().stop()` (STOP),
`robot.checkTunables()` (the Tunables name checks, with no devices). More: [RobotDefinition](../reference/robot-java.md#robotdefinition).

**What it cannot do:** move anything. A pretend motor's encoder stays where your test puts it. Time does
not pass by itself, so an action's `waitSeconds` never ends in a test. There is no camera picture. For
these, use the [simulator](../guides/simulator.md). A scenario file is a test too.

## PretendMotor, PretendServo, PretendCRServo

Each pretend device has plain fields. Your test reads and sets them:

| Class | Fields |
| --- | --- |
| `PretendMotor` | `power` (last sent), `position` (the encoder: set it), `velocity`, `currentAmps`, `direction`, `zeroPower`, `mode`, `powerWrites`, `maxAbsPower`, `failSetPower` (true: `setPower` throws an error, like an unplugged motor), `motor` (the SDK `DcMotorEx`) |
| `PretendServo` | `position` (last sent; NaN = never), `writes`, `direction`, `pulseUs` (set by `.fullRange()`), `servo` (the SDK `Servo`) |
| `PretendCRServo` | `power`, `writes`, `direction`, `servo` (the SDK `CRServo`) |

## PretendPinpoint

A robot with `odometry(Odometry.pinpoint("pinpoint"))` gets one at INIT. It moves only when your test moves it:

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
| `has(words)` | whether a line of the last screen contains them |
| `updates` | how many screens the robot sent |

## PretendClock

With the real clock, a timing test must wait, and a slow computer can make it fail when the code is fine.
The pretend clock moves only when your test moves it. The robot's parts read the library's clock
([`Time`](core.md#time)). So with a pretend clock, a timing test ("the arm pushes 0.2 s after fire") runs at
once and gives the same result on every computer.

```java
@After public void realClock() {
    PretendClock.stopUsing();          // (1)!
}

@Test public void theArmPushesAfterStaging() {
    PretendClock clock = PretendClock.use();
    PretendDevices devices = new PretendDevices();
    Robot robot = new Robot();
    robot.setup(devices);
    robot.startForTests();
    PretendMotor fly = devices.motor("launcher");
    robot.launcher.spinUp();
    for (int i = 0; i < 100; i++) {    // 0.5 s, 5 ms a loop
        clock.addSec(0.005);           // (2)!
        fly.velocity = fly.velocityCommand;   // (3)!
        robot.all().update();
        if (i == 60) robot.launcher.fire();   // at 0.3 s
    }
    assertEquals(0.7, devices.servo("feeder_arm").position, 1e-9);
}
```

1. The next test gets the real clock again.
2. The clock is 5 ms later at once, and nothing waits. The whole loop takes far less than a second.
3. A pretend motor does not speed up by itself. This line says that the wheel reached the speed the robot asked
   for. Without it, the flywheel is never ready, and nothing fires.

| Member | What it does |
| --- | --- |
| `PretendClock.use()` | makes a pretend clock and has the robot's parts use it (the only way to make one) |
| `PretendClock.stopUsing()` | goes back to the real clock (in `@After`) |
| `addSec(sec)` | moves the time forward (seconds, 0 or more) |
| `sec()` | the time now, seconds (it starts at 1) |

A part that waits for a helper thread (a camera, a distance sensor on the library's helper thread) still
needs real time, because the thread runs on its own. The camera aim also keeps the real clock, because the
camera stamps its pictures with it.

**Always** call `PretendClock.stopUsing()` in `@After`. A pretend clock that stays in use stops time for every
later test. Their flywheels are never ready, and their time limits never end.

