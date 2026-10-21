# Tutorial 01: OpMode basics with the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

**You will build:** a small program that counts A presses and shows the loop speed on the Driver Station.
It is the base of every other tutorial. The main track skips these details: [The robot in five files](start/01-robot-in-five-files.md).

**You will learn:** the shape every program in these tutorials uses, how to import library
classes, why some variables are "fields", what `() -> ...` means, and why we use `try`/`finally`.

**You need:** a Control Hub (or a Robot Controller phone), a Driver Station, gamepad 1.
No motors.

**Full program:** [`Tut01HelloLibrary.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut01HelloLibrary.java)

---

## 1. The four parts of an OpMode

An **OpMode** is a program you pick on the Driver Station. A `LinearOpMode` is the kind that runs
from top to bottom, like a recipe:

```java
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials")
public class Tut01HelloLibrary extends LinearOpMode {
    @Override
    public void runOpMode() {
        // 1. INIT: runs once, when the driver presses INIT. Set up hardware here.

        waitForStart();     // 2. waits here until the driver presses START

        try {
            while (opModeIsActive()) {
                // 3. RUN: repeats about 50-200 times per second, until STOP
            }
        } finally {
            // 4. STOP: always runs at the end. Stop the motors here.
        }
    }
}
```

- `@TeleOp(name = ...)` puts it in the TeleOp list. `@Autonomous` puts it in the Autonomous list.
- `group` sorts the list.
- `while (opModeIsActive()) { ... }` is a **loop**: the code inside the braces runs again and again
  while the OpMode is running.
- The loop must be quick. It reads the gamepads, decides, sets motor powers, and goes around
  again. Never put `sleep()` in a TeleOp loop: the robot cannot react while it sleeps.

### Why `try`/`finally`?

Code in `finally` runs when the loop ends **for any reason**: STOP was pressed, or your code
crashed with an error. Putting `drive.stop()` there means a bug never leaves a motor running.

## 2. Imports: telling Java where a class lives

Library classes live in packages like `com.crossbotics.ftchelper.util`. At the top of the
file, `import` lines tell Java where to find them:

```java
import com.crossbotics.ftchelper.util.LoopTimer;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
```

You do not need to type these. Type the class name (it turns red), click it, press
**Alt+Enter** (Mac: **Option+Enter**) and choose **Import class**.

`ControllerBindings` has small "inner" names (`Pad`, `Btn`, `Trigger`). Import them too, so
you can write `Pad.G1` instead of `ControllerBindings.Pad.G1`:

```java
import com.crossbotics.ftchelper.gamepad.ControllerBindings;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Btn;
import com.crossbotics.ftchelper.gamepad.ControllerBindings.Pad;
```

## 3. Set up in INIT

```java
LoopTimer loopTimer = new LoopTimer();
ControllerBindings controls = new ControllerBindings()
        .bindPress(Pad.G1, Btn.A, () -> presses++);
```

- `new` makes a new object from a class. `new LoopTimer()` makes a loop-speed meter.
- `new ControllerBindings()` makes the button list. `.bindPress(...)` adds one job:
  "when gamepad 1's A goes down, add one to `presses`".
- The dots on new lines are a **chain**: each `.bind...` returns the same object, so you can keep
  adding. This is how most of the library is used.

### What is `() -> presses++`?

It is a **lambda**: a small piece of code you hand over to run LATER. `ControllerBindings` keeps
it and runs it each time A is pressed. Read it as "a job with no inputs that does `presses++`".

For more than one line, use braces: `() -> { presses++; telemetry.speak("hi"); }`.

### Why is `presses` a field?

```java
public class Tut01HelloLibrary extends LinearOpMode {
    private int presses = 0;      // a FIELD: belongs to the class
```

Java does not let a lambda change a normal local variable (one made inside `runOpMode`).
A **field** is a variable made inside the class but outside any method (a **method** is a named
block of code, like `runOpMode`). A lambda may change a field. Rule of thumb:
*anything a button changes is a field.*

## 4. The loop

```java
waitForStart();
controls.ignoreHeldButtons(gamepad1, gamepad2);

while (opModeIsActive()) {
    loopTimer.update();
    controls.update(gamepad1, gamepad2);

    TelemetryHelper.section(telemetry, "Hello");
    telemetry.addData("A presses", presses);
    TelemetryHelper.flag(telemetry, "B held", controls.isDown(Pad.G1, Btn.B));
    TelemetryHelper.value(telemetry, "Loop speed", loopTimer.getHz(), "Hz");
    telemetry.update();
}
```

- `ignoreHeldButtons` right after START: if someone holds A while pressing START, it does not
  count until they let go. (Tutorial 03 explains.)
- `controls.update(...)` once per loop reads the gamepads and runs the jobs.
- Telemetry: add lines, then **one** `telemetry.update()` at the end of the loop.

## Run it

1. INIT: the screen says "Ready".
2. START. Press A a few times: the count goes up by exactly one per press, even if you hold it.
3. Hold B: "B held : yes".
4. Loop speed: probably 100+ Hz with nothing connected. (Hz means "times per second".)

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Red `LoopTimer` in Android Studio | Import it (Alt+Enter). If the import is not offered, the library is not added: [Add it to a project](../01-add-to-project.md). |
| "Variable used in lambda expression should be final" | The variable a button changes must be a **field**. Move it out of `runOpMode`. |
| The OpMode is not on the Driver Station | Delete the `@Disabled` line, build, and install again. |
| Count goes up many times per press | You used `bindHold` instead of `bindPress`. |

## Try this

1. Add a binding so B **resets** the count to 0. (Hint: `bindPress(Pad.G1, Btn.B, () -> presses = 0)`.)
2. Show the loop time in milliseconds with `loopTimer.getAverageMs()`.
3. Put `sleep(100);` inside the loop. What happens to the loop speed and to how fast A presses
   are noticed? Now you know why TeleOp loops must never sleep. (Take it out again.)

## Next

[Tutorial 02: Motors and servos](02-motors-and-servos.md)
