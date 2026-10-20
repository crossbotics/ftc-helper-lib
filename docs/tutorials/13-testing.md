# Tutorial 13: Test your code without a robot

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/jasoncross/ftc-helper-lib/issues).

**You will learn:** how to write small automatic tests (JUnit) for your team's code, run them on a
laptop in seconds, and which parts of robot code can be tested this way.

**You need:** Tutorial 12 (the `LiftHeights` class). No robot.

**Full program:** [`LiftHeightsTest.java`](../../examples/src/test/java/io/github/jasoncross/ftchelper/examples/tutorials/robot/LiftHeightsTest.java)

---

## 1. Why test without a robot?

The robot is shared, the battery runs out, and the build team needs it too. Many bugs are not
about hardware at all: a wrong list index, a sign error, a height out of range. A test finds those
in one second, on any laptop, every time you change the code.

The library itself has over 100 such tests. They found real bugs before any robot moved.

## 2. What you can test

| Testable on a laptop | Needs the robot |
| --- | --- |
| math, lists, choices (`LiftHeights.up()`) | does the motor turn? |
| "with these numbers, the PID gives this power" | is the PID tuned well? |
| button logic (`ControllerBindings` with a pretend `Gamepad`) | does the driver like it? |
| limits (`SoftLimits.limit(...)`) | is the hard stop in the right place? |

**Tip:** keep logic in small classes with no hardware (like `LiftHeights`), and keep hardware in
the subsystem classes. Then the logic is easy to test.

## 3. Set up TeamCode for tests (once)

Open `TeamCode/build.gradle` and add to `dependencies { ... }`:

```groovy
testImplementation 'junit:junit:4.13.2'
```

and inside `android { ... }`:

```groovy
testOptions {
    unitTests.returnDefaultValues = true
}
```

Make the folder `TeamCode/src/test/java/org/firstinspires/ftc/teamcode/` (next to `main`). Sync Gradle.

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
- `assertEquals(expected, actual)` fails the test if they differ.
- Name tests as sentences about what should be true. When one fails, the name tells you what broke.

A test that protects a safety rule:

```java
@Test public void heightsAreInOrderAndInsideTheLimit() {
    LiftHeights[] all = LiftHeights.values();
    for (int i = 1; i < all.length; i++) assertTrue(all[i].ticks > all[i - 1].ticks);
    assertTrue(LiftHeights.HIGH.ticks <= Lift.MAX_TICKS);
}
```

If someone later sets HIGH above the lift's limit, this test fails before the code reaches the robot.

## 5. Testing with library pieces and your numbers

```java
@Test public void ourLiftPidPushesUpWhenBelowTarget() {
    long[] now = {0};
    PidController pid = new PidController(0.004, 0, 0.0002, 0.05, () -> now[0]);
    assertEquals(1.0, pid.calculate(LiftHeights.HIGH.ticks, 0), 1e-9);
}
```

Library classes that use time (`PidController`, `SlewRateLimiter`, `LoopTimer`, the rumble classes)
have a second constructor that takes a **clock**. In a test you control time: `now[0] += 20_000_000L`
moves it 20 ms forward. No waiting, and the result is the same every run.

The third number in `assertEquals(a, b, 1e-9)` is the allowed difference for decimal numbers.

### A real lesson from writing this test

The first version jumped one controller from position 0 to 2400 in 20 ms and expected only the
F push. It got -1.0 instead: the D part saw a huge, sudden "movement" and pushed back hard. The
test was wrong, not the PID: no real lift moves that fast. Tests teach you how your code really
behaves.

## 6. Run the tests

- **In Android Studio:** open the test file, click the green arrow next to `class` (all tests)
  or next to one `@Test`. Green = pass, red = fail with the reason.
- **In the Terminal tab:**

```bash
./gradlew :TeamCode:testDebugUnitTest
```

Run them before every commit, and always before an event.

## Run it

1. Set up section 3, copy `LiftHeights` and the test (fix the package names).
2. Run: 6 tests pass.
3. Break something on purpose: make `up()` return `this`. Run again: a test fails, and its name says what broke.
   Fix it.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| `Cannot resolve symbol 'Test'` | `testImplementation 'junit:junit:4.13.2'` is missing, or Gradle was not synced. |
| "Method ... not mocked" | `unitTests.returnDefaultValues = true` is missing. |
| Test cannot find your class | The test's `package` line must match the class's package. |

## Try this

1. Test `LiftHeights.closestTo` with a position exactly halfway between two heights. Which one
   should win? Decide, write the test, and change the code if needed.
2. Test a `ControllerBindings` setup: make `new Gamepad()`, set `gamepad.a = true`, call
   `update`, check your counter. (The library's `GamepadTest` shows how.)
3. Write a test for a mirror function `double side(boolean red)` from Tutorial 10.

## Next

[Tutorial 14: Mechanism helpers](14-mechanisms.md): the ready-made versions of everything you built
in tutorials 07-12.
