# Tutorial 13: Test your code without a robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** six small tests that run on a laptop. Most of them test the `LiftHeights` class from
Tutorial 12.

**You will learn:** how to write small automatic tests (JUnit) for your team's code, how to run them on a
laptop in seconds, and which parts of robot code you can test this way.

**You need:** Tutorial 12 done (the `LiftHeights` class). You need no robot.

**Full program:** [`LiftHeightsTest.java`](../../examples/src/test/java/com/crossbotics/ftchelper/examples/tutorials/robot/LiftHeightsTest.java)

---

## 1. Why test without a robot?

The whole team shares the robot, the battery runs out, and the build team needs the robot too. Many
bugs have nothing to do with hardware: a wrong list index, a sign error, a height out of range. A test
finds those in one second, on any laptop, every time you change the code.

The library itself has over 250 such tests. They found real bugs before any robot moved.

## 2. What you can test

| Testable on a laptop | Needs the robot |
| --- | --- |
| math, lists, choices (`LiftHeights.up()`) | whether the motor turns |
| "with these numbers, the PID gives this power" | whether the PID is tuned well |
| button logic (`ControllerBindings` with a pretend `Gamepad`) | whether the driver likes it |
| limits (`SoftLimits.limit(...)`) | whether the hard stop is in the right place |

**Tip:** keep logic in small classes with no hardware (like `LiftHeights`), and keep hardware in the
subsystem classes. Then the logic is easy to test.

## 3. Set up TeamCode for tests (once)

Gradle is the tool that builds your code. `build.gradle` is its settings file.

1. Open `TeamCode/build.gradle` and add to `dependencies { ... }`:

```groovy
testImplementation 'junit:junit:4.13.2'
```

2. In tests, Android classes crash unless you tell them to give default values. So add this inside
   `android { ... }`:

```groovy
testOptions {
    unitTests.returnDefaultValues = true
}
```

3. Make the folder `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/` (next to `main`).
   The tests live in `test`, so they never go onto the robot.
4. Sync Gradle (the elephant button in Android Studio).

## 4. A first test

```java
public class LiftHeightsTest {

    @Test public void upAndDownStepThroughTheHeights() {
        assertEquals(LiftHeights.LOW, LiftHeights.BOTTOM.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.LOW.up());
        assertEquals(LiftHeights.MIDDLE, LiftHeights.HIGH.down());
    }

    @Test public void endsDoNotWrapAround() {
        assertEquals(LiftHeights.HIGH, LiftHeights.HIGH.up());
        assertEquals(LiftHeights.BOTTOM, LiftHeights.BOTTOM.down());
    }
}
```

- Each method marked `@Test` is one test.
- `assertEquals(expected, actual)` fails the test when the two are different. `assertTrue(x)` fails it
  when `x` is false.
- Name tests as sentences about what should be true. When one fails, the name tells you what broke.

A test that protects a safety rule:

```java
@Test public void heightsAreInOrderAndInsideTheLimit() {
    LiftHeights[] all = LiftHeights.values();
    for (int i = 1; i < all.length; i++) assertTrue(all[i].ticks > all[i - 1].ticks);
    assertTrue(LiftHeights.HIGH.ticks <= Lift.MAX_TICKS);
}
```

When someone later sets HIGH above the lift's limit, this test fails before the code reaches the robot.

## 5. Testing with library pieces and your numbers

```java
@Test public void ourLiftPidPushesUpWhenBelowTarget() {
    long[] now = {0};
    PidController pid = new PidController(0.004, 0, 0.0002, 0.05, () -> now[0]);
    assertEquals(1.0, pid.calculate(LiftHeights.HIGH.ticks, 0), 1e-9);
}
```

Some library classes use time: `PidController`, `SlewRateLimiter`, `LoopTimer`, the rumble classes.
They have a second constructor that takes a **clock**. In a test, you control time:
`now[0] += 20_000_000L` moves it 20 ms forward. The test does not wait, and the result is the same every
run.

The third number in `assertEquals(a, b, 1e-9)` is the allowed difference for decimal numbers.

### A real lesson from writing this test

The first version jumped one controller from position 0 to 2400 in 20 ms, and expected only the F push.
It got -1.0 instead: the D part saw a huge, sudden "movement" and pushed back hard. The mistake was in
the test: no real lift moves that fast. The PID was right. Tests teach you how your code really
behaves.

## 6. Run the tests

- **In Android Studio:** open the test file. Click the green arrow next to `class` (all tests) or next
  to one `@Test`. Green = pass, red = fail with the reason.
- **In the Terminal tab:**

**Where:** the Terminal tab, in your robot project's folder (the one with `TeamCode` in it).

```bash
./gradlew :TeamCode:testDebugUnitTest
```

A commit is a version of the code that you save in git. Run the tests before every commit, and always
before an event.

## 7. Test your robot in five files

With the robot in five files (`Robot.java`, `Tunables.java`, ...), the library's **test kit** gives your
robot pretend devices: motors whose encoder you set, and servos that remember where your code sent them.
The test does INIT, ▶ (start on the Driver Hub) and a loop. Then it checks what each motor got:

```java
import com.crossbotics.ftchelper.testkit.PretendDevices;

@Test public void liftGoesUpWhenSentHigh() {
    PretendDevices devices = new PretendDevices();   // a fresh pretend robot
    Robot robot = new Robot();
    robot.setup(devices);                            // INIT: Tunables.java is checked like on the robot
    robot.startForTests();                           // ▶ (start)
    robot.lift.goTo("high");
    robot.all().update();                            // one loop
    assertTrue(devices.motor("lift").power > 0);     // "lift" = the motor's configuration name
}

@Test public void tunablesNamesAreRight() {
    // The same name checks as INIT: a misspelled field, a section named after the wrong thing.
    assertEquals("[]", new Robot().checkTunables().toString());
}
```

**Why:** a typo in `Tunables.java` stops INIT on the robot. With a test, it fails on the laptop first.
The test also checks a change to `Robot.java` (a second lift motor, a new interlock rule) in a second.
The test cannot see how the real robot moves (gravity). For that, use the
[simulator](../guides/simulator.md).

**Time passing:** for a test of timing ("the arm pushes 0.2 s after fire"), use a pretend clock. The test
moves the time by hand. So it does not wait, and a slow laptop cannot make it fail:

```java
PretendClock clock = PretendClock.use();   // and PretendClock.stopUsing() in @After
robot.intake.in();
clock.addSec(0.25);                         // 0.25 s later, at once
robot.all().update();
```

A pretend motor does not speed up by itself. For a flywheel, set `fly.velocity = fly.velocityCommand` in
each loop. Otherwise the flywheel is never ready ([PretendClock](../modules/testkit.md#pretendclock) has a
whole example). For a whole robot test, see `examples/src/test/.../v2bot/V2botTest.java`.
More: [test kit](../modules/testkit.md).

## Run it

1. Do the setup in section 3. Copy `LiftHeights` and the test (fix the package names).
2. Run the tests: 6 tests pass.
3. Break something on purpose: make `up()` return `this`. Run again: a test fails, and its name says what
   broke. Fix it.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| `Cannot resolve symbol 'Test'` | `testImplementation 'junit:junit:4.13.2'` is missing, or you did not sync Gradle. |
| "Method ... not mocked" | `unitTests.returnDefaultValues = true` is missing. |
| Test cannot find your class | The test's `package` line must match the class's package. |

## Try this

1. Test `LiftHeights.closestTo` with a position exactly halfway between two heights. Decide which one
   should win, write the test, and change the code when it does not match.
2. Test a `ControllerBindings` setup: make `new Gamepad()`, set `gamepad.a = true`, call `update`, and
   check your counter. The library's `GamepadTest` shows how.
3. Write a test for a mirror function `double side(boolean red)` from Tutorial 10.

## Next

[Tutorial 14: Mechanism helpers](14-mechanisms.md): the ready-made versions of everything you built
in tutorials 07-12.
