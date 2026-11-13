# Tutorial 01: OpMode basics with the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**You will build:** a small program that counts A presses and shows the loop speed on the Driver Station.
Every other tutorial builds on it. The main track skips these details: [The five files](../five-files/README.md).

**You will learn:** the shape of every program in these tutorials, how to import library classes, why
some variables are "fields", what `() -> ...` means, and why the programs use `try`/`finally`.

**You need:** a Control Hub (or a Robot Controller phone), a Driver Station, gamepad 1.
You need no motors.

**Full program:** [`Tut01HelloLibrary.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut01HelloLibrary.java)

---

## 1. The four parts of an OpMode

An **OpMode** is a program you pick on the Driver Station. A `LinearOpMode` runs from top to bottom,
like a recipe:

```java
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials")
public class Tut01HelloLibrary extends LinearOpMode {
    @Override
    public void runOpMode() {
        // 1. INIT: runs once, when the driver presses INIT. Set up hardware here.

        waitForStart();     // 2. waits here until the driver presses ▶ (start)

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
  while the OpMode runs.
- The robot cannot react while it sleeps. So never put `sleep()` in a TeleOp loop. The loop must be
  quick: it reads the gamepads, decides, sets motor powers, and goes around again.

### The top of the file

```java
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials") // (1)!
@Disabled // hides this program on the Driver Station: delete this line in your copy (2)!
public class Tut01HelloLibrary extends LinearOpMode { // (3)!

    // A field: button actions can change it.
    private int presses = 0; // (4)!

    // The SDK calls runOpMode() when the driver presses INIT.
    @Override // (5)!
    public void runOpMode() {
```

1. `@TeleOp` is a label. Java calls it an "annotation". It puts the program in the Driver Station's
   TeleOp list, with this name, in the "Tutorials" group.
2. Every tutorial file has `@Disabled`, so the lists stay short. Delete it in your copy. Without that,
   your program does not show on the Driver Station.
3. The class name must match the file name. `extends LinearOpMode` builds the program on the SDK's
   `LinearOpMode`. The SDK then gives you `hardwareMap`, `gamepad1`, `gamepad2` and `telemetry` (the
   text on the Driver Station screen).
4. `private` = only this file can use it. `int` = a whole number. It starts at 0.
   [Why it is a field](#why-is-presses-a-field).
5. `@Override` says "this replaces a method the SDK already has". `public void` = any code may call
   it, and it gives nothing back.

### Why `try`/`finally`?

Code in `finally` runs when the loop ends **for any reason**: the driver pressed STOP, or your code
crashed with an error. So put `drive.stop()` there: then a bug never leaves a motor running.

## 2. Imports: telling Java where a class lives

Library classes live in packages (named folders of classes) like `com.crossbotics.ftchelper.util`.
At the top of the file, `import` lines tell Java where to find them. `com.qualcomm...` is the FTC SDK
(from FIRST). `com.crossbotics...` is this helper library. The `package` line above them says which
folder the file is in. When you copy a file into TeamCode, change that line to your folder. Android
Studio usually offers to fix it.

```java
import com.crossbotics.ftchelper.util.LoopTimer;
import com.crossbotics.ftchelper.telemetry.TelemetryHelper;
```

You do not need to type these lines. Type the class name (it turns red), click it, press
**Alt+Enter** (Mac: **Option+Enter**) and choose **Import class**.

`ControllerBindings` has small "inner" names (`Pad`, `Btn`, `Trigger`). Import them too. Then you can
write `Pad.G1` instead of `ControllerBindings.Pad.G1`:

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
  adding. You use most of the library this way.

### What is `() -> presses++`?

It is a **lambda**: a small piece of code you hand over to run LATER. `ControllerBindings` keeps it
and runs it each time the driver presses A. Read it as "a job with no inputs that does `presses++`".

For more than one line, use braces: `() -> { presses++; telemetry.speak("hi"); }`.

### Why is `presses` a field?

```java
public class Tut01HelloLibrary extends LinearOpMode {
    private int presses = 0;      // a FIELD: belongs to the class
```

Java does not let a lambda change a normal local variable (one made inside `runOpMode`).
A **method** is a named block of code, like `runOpMode`. A **field** is a variable made inside the class
but outside any method. A lambda may change a field. Rule of thumb:
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
    TelemetryHelper.flag(telemetry, "B held", controls.isDown(Pad.G1, Btn.B)); // (1)!
    TelemetryHelper.value(telemetry, "Loop speed", loopTimer.getHz(), "Hz"); // (2)!
    telemetry.update();
}
```

1. `isDown` tells you whether B is held right now (yes or no). It binds no action to B.
2. Loops per second (Hz). Higher is better: the robot reacts faster.

- Call `ignoreHeldButtons` right after ▶ (start on the Driver Hub). When someone holds A while they press
  ▶, A then does not count until they let go. Tutorial 03 explains it.
- `controls.update(...)` reads the gamepads and runs the jobs. Call it once per loop.
- For telemetry, add lines, then call `telemetry.update()` **once**, at the end of the loop.

## Run it

1. INIT: the screen says "Ready".
2. Press ▶. Press A a few times: the count goes up by exactly one per press, also when you hold A.
3. Hold B: "B held : yes".
4. Loop speed: probably 100+ Hz with nothing connected. Hz means "times per second".

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Red `LoopTimer` in Android Studio | Import it (Alt+Enter). When Android Studio does not offer the import, the library is missing: [Add the library](../setup/04-add-the-library.md). |
| "Variable used in lambda expression should be final" | The variable a button changes must be a **field**. Move it out of `runOpMode`. |
| The OpMode is not on the Driver Station | Delete the `@Disabled` line, build, and install again. |
| Count goes up many times per press | You used `bindHold` instead of `bindPress`. |

## Try this

1. Add a binding so B **resets** the count to 0. (Hint: `bindPress(Pad.G1, Btn.B, () -> presses = 0)`.)
2. Show the loop time in milliseconds with `loopTimer.getAverageMs()`.
3. Put `sleep(100);` inside the loop. Watch the loop speed, and how fast the program sees an A press.
   This is why TeleOp loops must never sleep. Take it out again.

## Next

[Tutorial 02: Motors and servos](02-motors-and-servos.md)
