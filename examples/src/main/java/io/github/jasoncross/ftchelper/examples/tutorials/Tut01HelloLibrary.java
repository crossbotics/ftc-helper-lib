// "package" says which folder this file lives in. When you copy the file into TeamCode, change
// this line to your folder (Android Studio usually offers to fix it).
package io.github.jasoncross.ftchelper.examples.tutorials;

// "import" lines tell Java which ready-made pieces this file uses and where to find them.
// com.qualcomm... = the FTC SDK (from FIRST). io.github.jasoncross... = this helper library.
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Btn;
import io.github.jasoncross.ftchelper.gamepad.ControllerBindings.Pad;
import io.github.jasoncross.ftchelper.telemetry.TelemetryHelper;
import io.github.jasoncross.ftchelper.util.LoopTimer;

/**
 * Tutorial 01: the shape of every OpMode in these tutorials. No motors needed.
 * Press A to count. Hold B to see "held". Watch the loop speed.
 *
 * Tutorial: docs/tutorials/01-opmode-basics.md
 */
// @TeleOp is a label (Java calls it an "annotation") that puts this program in the Driver
// Station's TeleOp list, with this name, in the "Tutorials" group.
@TeleOp(name = "Tutorial 01: Hello Library", group = "Tutorials")
// @Disabled HIDES the program from the Driver Station list. When you copy this file, DELETE
// this line, or you will not find your program on the Driver Station.
@Disabled
// "public class Tut01HelloLibrary" makes a new program. The class name must match the file name.
// "extends LinearOpMode" means it is built on the SDK's LinearOpMode: an FTC program (an "OpMode")
// that runs from top to bottom, like a recipe. It gives us hardwareMap, gamepad1, gamepad2 and
// telemetry (the text on the Driver Station screen) for free.
public class Tut01HelloLibrary extends LinearOpMode {

    // A FIELD: a variable that belongs to the whole class. Button actions can change it.
    // ("private" = only this file can use it. "int" = a whole number. It starts at 0.)
    private int presses = 0;

    // "@Override" says "this replaces a method the SDK already has". The SDK calls runOpMode()
    // when the driver presses INIT, and our program runs from here.
    // ("public void" = anyone may call it, and it gives nothing back.)
    @Override
    public void runOpMode() {
        // ---------- INIT: runs once, when you press INIT ----------
        // This is where you set things up. Robots must not move during INIT.

        // A LOCAL variable: it only exists inside runOpMode. Measures how many loops run per second.
        LoopTimer loopTimer = new LoopTimer();
        // All the buttons in one place. bindPress = "when A on gamepad 1 is pressed, run this".
        // "() -> presses++" is a lambda: a small piece of code we hand over NOW, to be run LATER,
        // each time A is pressed. "presses++" adds 1 to presses.
        // Holding A down does not keep counting: one press = one count. Tutorial 03 shows all the kinds.
        ControllerBindings controls = new ControllerBindings()
                .bindPress(Pad.G1, Btn.A, () -> presses++);

        // Text for the Driver Station. addLine adds a line; nothing shows until update() sends it.
        telemetry.addLine("Ready. Press START.");
        telemetry.update();

        // ---------- wait for START ----------
        // The program stops here until the driver presses START (or STOP).
        waitForStart();
        // If a button was already held down when START was pressed, do not count it as a press.
        controls.ignoreHeldButtons(gamepad1, gamepad2);

        // ---------- RUN: repeats until STOP ----------
        // "try { ... } finally { ... }": however the "try" part ends (STOP pressed, or an error),
        // the "finally" part still runs. In a robot program, that is where motors get stopped.
        try {
            // "while (opModeIsActive())" repeats everything inside, over and over, until STOP is
            // pressed. Each trip round is one "loop", many times a second. Nothing in here should
            // wait or sleep: the robot has to keep reacting to the gamepads.
            while (opModeIsActive()) {
                // Count this loop, for the loop-speed number.
                loopTimer.update();
                // Read the gamepads and run the actions of any buttons just pressed.
                controls.update(gamepad1, gamepad2);

                // What to show on the screen this loop:
                TelemetryHelper.section(telemetry, "Hello");
                telemetry.addData("A presses", presses);
                // isDown asks "is B held right now?" (yes or no), without binding an action to it.
                TelemetryHelper.flag(telemetry, "B held", controls.isDown(Pad.G1, Btn.B));
                // Loops per second (Hz). Higher is better: the robot reacts faster.
                TelemetryHelper.value(telemetry, "Loop speed", loopTimer.getHz(), "Hz");
                // Send it all to the screen.
                telemetry.update();
            }
        } finally {
            // ---------- STOP: always runs, even after an error ----------
            // There are no motors here, so we just say goodbye. In later tutorials this is where
            // every motor is stopped, for safety.
            telemetry.addLine("Stopped.");
            telemetry.update();
        }
    }
}
