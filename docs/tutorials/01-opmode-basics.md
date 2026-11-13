# Tutorial 01: OpMode basics with the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

The five files write the OpMode for you: [The five files](../five-files/README.md).
Write one by hand here to see the shape that every robot program has inside.

**You will build:** a small program that counts A presses and shows the loop speed on the Driver Station.

**You need:** a Control Hub (or a Robot Controller phone), a Driver Station, gamepad 1. You need no motors.

**Full program:** [`Tut01HelloLibrary.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut01HelloLibrary.java).
To copy a program into TeamCode: [Copy example files into TeamCode](../pick-your-start/04-copy-into-teamcode.md).

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

- `while (opModeIsActive()) { ... }` is a **loop**: the code inside the braces runs again and again
  while the OpMode runs. Each time, it reads the gamepads, decides, and sets motor powers.
- The robot cannot react while it sleeps. So never put `sleep()` in a TeleOp loop.
- Code in `finally` runs when the loop ends **for any reason**: the driver pressed STOP, or your code
  crashed. So put `drive.stop()` there. Then a bug never leaves a motor running.

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

1. `@TeleOp` puts the program in the Driver Station's TeleOp list, with this name, in the "Tutorials"
   group. `@Autonomous` puts it in the Autonomous list.
2. Every tutorial file has `@Disabled`, so the lists stay short. Delete it in your copy, or your program
   does not show on the Driver Station.
3. The class name must match the file name. `extends LinearOpMode` gives you `hardwareMap`, `gamepad1`,
   `gamepad2` and `telemetry` (the text on the Driver Station screen).
4. `private` = only this file can use it. `int` = a whole number.
   [Why it is a field](#a-field-for-what-a-button-changes).
5. `@Override` says "this replaces a method the SDK already has".

## 2. Imports

`import` lines at the top of the file tell Java where each class lives. `com.qualcomm...` is the FTC SDK.
`com.crossbotics...` is this helper library. To add one, click the red class name and press
**Alt+Enter** (Mac: **Option+Enter**).

`ControllerBindings` has small "inner" names (`Pad`, `Btn`, `Trigger`). Import them too, so you can
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

- `new LoopTimer()` makes a loop-speed meter.
- `new ControllerBindings()` makes the button list. `.bindPress(...)` adds one job:
  "when gamepad 1's A goes down, add one to `presses`".
- `() -> presses++` is a **lambda**: a small piece of code you hand over to run LATER, each time the
  driver presses A. For more than one line, use braces: `() -> { presses++; telemetry.speak("hi"); }`.

### A field for what a button changes

A **field** is a variable made inside the class but outside any method, like `presses`. Java does not
let a lambda change a variable made inside a method. A lambda may change a field. Rule of thumb:
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

1. `isDown` tells you whether B is held right now. It binds no action to B.
2. Loops per second (Hz). Higher is better: the robot reacts faster.

- `ignoreHeldButtons`: a button held at ▶ does nothing until the driver lets go
  ([Tutorial 03](03-buttons.md) explains why).
- `controls.update(...)` reads the gamepads and runs the jobs. Call it once per loop.
- Add the telemetry lines, then call `telemetry.update()` **once**, at the end of the loop.

## Run it

1. INIT: the screen says "Ready". Press ▶.
2. Press A a few times: the count goes up by exactly one per press, also when you hold A.
3. Hold B: "B held : yes".
4. Loop speed: probably 100+ Hz with nothing connected.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Red class name, and Alt+Enter offers no import | The library is missing: [Add the library](../setup/04-add-the-library.md). |
| "Variable used in lambda expression should be final" | The variable a button changes must be a **field**. Move it out of `runOpMode`. |
| The OpMode is not on the Driver Station | Delete the `@Disabled` line, build, and install again. |

## Try this

1. Add a binding so B **resets** the count to 0. (Hint: `bindPress(Pad.G1, Btn.B, () -> presses = 0)`.)
2. Show the loop time in milliseconds with `loopTimer.getAverageMs()`.
3. Put `sleep(100);` inside the loop. Watch the loop speed, and how late the program sees an A press.
   This is why TeleOp loops must never sleep. Take it out again.

---

**Next:** [Tutorial 02: Motors and servos](02-motors-and-servos.md).
**Look it up:** [gamepad.md](../modules/gamepad.md), [telemetry.md](../modules/telemetry.md), [util.md](../modules/util.md).
