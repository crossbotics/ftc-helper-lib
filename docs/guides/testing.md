# Testing procedures

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** the whole team: programmers, builders and drivers. **You need:** a robot that runs, and the
[Hardware Check](../modules/testing.md) OpMode on it.

Most robot problems at events are found in the pits by a 10-minute check. These procedures say
what to check, when, and how. Print the checklists.

```mermaid
flowchart LR
    A["Code change"] --> B["Laptop tests<br/>./gradlew ... test"]
    B --> C["Bench check<br/>robot on a block"]
    C --> D["Floor check<br/>low power"]
    D --> E["Practice runs<br/>read reports"]
    E --> F["Event: pre-match check"]
```

## 1. Laptop tests (every code change)

```bash
./gradlew :TeamCode:testDebugUnitTest
```

This runs your team's tests on the laptop, with Gradle (the tool that builds the code). Fast, no robot.
Write tests for your own logic ([tutorial 13](../tutorials/13-testing.md)).

### Test your Robot.java with pretend devices

The library comes with a **test kit** ([`com.crossbotics.ftchelper.testkit`](../modules/testkit.md)):
pretend motors, servos and switches that your robot in five files can use instead of the real ones.
Your test sets up the robot (that is INIT), presses START, runs a few loops, and checks what the motors
and servos were sent. Put it in `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/RobotTest.java`:

```java
package org.firstinspires.ftc.teamcode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.crossbotics.ftchelper.testkit.PretendDevices;
import org.junit.Test;

public class RobotTest {
    // Tunables.java has no misnamed field or section (INIT would stop or warn).
    @Test public void tunablesNamesAreRight() {
        assertEquals("[]", new Robot().checkTunables().toString());
    }

    // Robot.java and Tunables.java set up without an error (the same checks as INIT).
    @Test public void initWorks() {
        new Robot().setup(new PretendDevices());
    }

    // The lift goes up, and both of its motors get the same power.
    @Test public void liftGoesUpOnBothMotors() {
        PretendDevices devices = new PretendDevices();   // a fresh pretend robot each test
        Robot robot = new Robot();
        robot.setup(devices);                            // INIT
        robot.startForTests();                           // START
        robot.lift.goTo("high");
        robot.all().update();                            // one loop
        assertTrue(devices.motor("lift_left").power > 0);
        assertEquals(devices.motor("lift_left").power, devices.motor("lift_right").power, 1e-9);
        devices.motor("lift_left").position = 2700;      // the pretend encoder: at the top
        robot.all().update();
        assertTrue(robot.lift.isAt("high"));
    }
}
```

What it can and cannot check:

- **Can:** INIT's checks (a misspelled Tunables field, a missing safety number, a device used twice), which
  power or position each command sends, limits, interlock rules, pause (`robot.pauseAll()`) and STOP
  (`robot.all().stop()`), your own `Actions.java` steps that do not wait.
- **Cannot:** how the real robot moves (gravity, a heavy lift, a slipping belt), anything that waits
  for time to pass (an action's `waitSeconds`), cameras. Use the [simulator](simulator.md) for those,
  and the checks below on the real robot.

Set TeamCode up for tests once ([tutorial 13, step 3](../tutorials/13-testing.md#3-set-up-teamcode-for-tests-once):
the JUnit line and `returnDefaultValues`). The test kit comes with the library, and the FTC SDK with the
FtcRobotController project: nothing else to add. A complete example:
[`V2botTest.java`](../../examples/src/test/java/com/crossbotics/ftchelper/examples/v2bot/V2botTest.java).

## 2. Hardware check (after any wiring or configuration change)

Run the **Hardware Check** OpMode ([starter kit](../starter-kit.md), or
[module page](../modules/testing.md)). Robot on a block.

| Device | Check | If wrong |
| --- | --- | --- |
| every motor | moves with positive power the way you expect; encoder counts UP | `.reversed()` on its part in `Robot.java`; check the encoder cable port |
| drive motors | each wheel rolls FORWARD with positive power | `Drive.mecanum(...).reversed("front_left", ...)` in `Robot.java` |
| a lift or arm on two motors | with the chain or belt OFF, both turn the way that lifts | `alsoMotor` / `alsoMotorReversed` ([two motors](../modules/robot.md#two-motors-on-one-lift-or-arm)) |
| servos | find and write down each position you need (Servo Finder) | the named positions in `Tunables.java` |
| CR servos | spin both ways | configuration must say "Continuous Rotation Servo" |
| touch / magnetic switches | change when pressed / magnet near | wiring; magnetic: false = near |
| IMU | yaw goes UP when the robot turns LEFT | the logo/USB directions in the drive's `.imu("imu", LogoFacingDirection..., UsbFacingDirection...)` in `Robot.java` |
| distance / color | sensible numbers | wiring, I2C port, configuration type |
| camera | listed; Limelight "connected"; HuskyLens `Answers: yes` and a `Sees` line per box | USB / Ethernet / I2C cable, configuration name; a HuskyLens's Protocol Type |
| battery | 12.5 V or more when charged | charge or replace; under 12.0 V never play a match |
| Robot.java's devices (with `robot()`) | `Robot.java: every device is in the configuration`, or only the parts you know are not built yet (`optional, so the part is off`) | add the device to the configuration, or fix its name ([parts not on the robot yet](../modules/robot.md#parts-not-on-the-robot-yet)) |

## 3. Mechanism checks (after building or changing a mechanism)

| Mechanism | Procedure | Pass |
| --- | --- | --- |
| Lift / arm | 1. Hard stops in place. 2. Low `maxPower` in `Tunables`. 3. Home it (push down to the switch). 4. Drive by hand to each end: it slows and stops before the hard stop. 5. Each named position. 6. Pause (G1 BACK) while it moves: it stops (both motors, with two). | stops before hard stops; reaches each position in ~1 s; holds |
| Intake | in, out, stop; block it by hand (with jam detection) | backs out and goes on; jam count goes up |
| Servo parts | each named position; toggle; slow move | no buzzing at the ends (if it buzzes, the range is too wide) |
| Flywheel | spin to 3 speeds; compare telemetry RPM with a tachometer or slow-motion video | readings match within ~3%; READY within ~1 s |
| Launcher | shoot 1, then 3; block the turret check | each piece only when READY; `whyNotReady` names what is missing |
| Turret | home; aim at 0, 90, -90, 180 and back; manual to each limit | short way round; never past a limit; cables slack |
| Vision | point at a tag; read bearing (positive = left), distance with a tape measure | bearing sign right; distance within ~5% |

## 4. Floor check (before practice runs)

1. The PRACTICE profile (`Tunables.Drive.practiceMaxPower`, e.g. 0.4).
2. Drive: forward, strafe, turn, field-centric.
3. Every button on the driver's sheet does what it says.
4. The match timer buzzes (test with a short `new MatchTimer(30)`).

## 5. Autonomous check

1. Run at low speed; read the step report.
2. Run as RED once.
3. Run with a start delay once.
4. Full speed: under ~28 s, parks.
5. Put a box in the way: the move ends STALLED and the robot stops pushing.

## 6. Event: before EVERY match (2 minutes)

- [ ] Battery 12.5 V+ (hardware check shows it), a spare on the cart
- [ ] Robot starts inside 18 in; everything folded; hard stops tight
- [ ] Lift/turret at their start positions (home switches in reach)
- [ ] Profile: COMPETITION, no "tuned values differ" warning
- [ ] No `!! COMPETITION: ... is OFF (optional part)` warning at INIT for a part that IS on the robot (a renamed or unplugged device)
- [ ] Right autonomous selected, right alliance and delay in the menu
- [ ] Robot placed exactly as the INIT screen's "Place robot" says
- [ ] After autonomous, TeleOp's INIT screen says "Forward: from autonomous"
- [ ] Gamepads: 1 = driver (Start+A), 2 = operator (Start+B)

## Without a robot: the simulator

Many problems show up before you touch the robot: a crash at INIT, a wrong button, a screen that does
not fit. Run the OpMode in the [simulator](simulator.md) first.

## 7. After a problem

Write it down: what you did, what you expected, what happened, and the telemetry (debug ON). Then
reproduce it on the bench before changing code. If it is a library bug, add a failing test first
([Contributing](../contributing.md)).
