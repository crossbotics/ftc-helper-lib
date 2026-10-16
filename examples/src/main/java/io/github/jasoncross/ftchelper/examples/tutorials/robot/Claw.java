// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.tutorials.robot;

// "import" lines list the ready-made pieces this file uses: the FTC SDK's (com.qualcomm... and
// org.firstinspires...) and this library's (io.github.jasoncross...).
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.hardware.ServoSetup;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Tutorial 12: the claw as a small subsystem class. */
// A "subsystem" = one part of the robot with everything about it in one file. OpModes just say
// robot.claw.open(); they do not need to know servo numbers. See docs/tutorials/12-robot-structure.md.
// Not an OpMode, so it never shows on the Driver Station.
public class Claw {
    // The servo. "private" = only this file touches it, so nobody can send it a wrong position.
    // "final" = set once, in the constructor, and never swapped.
    private final Servo servo;
    // What we last told the claw to do. A servo cannot report where it is, so we remember it.
    // It starts as true (open), but the servo itself is not moved until open() or close() is called.
    private boolean open = true;

    // The "constructor": runs when someone writes "new Claw(hardwareMap)". It finds the servo.
    // Nothing moves here, so it is safe in INIT.
    public Claw(HardwareMap hardwareMap) {
        // "claw" = the servo's name in the configuration. range(0.20, 0.75) uses only the part of
        // the servo's travel measured on the robot (fully open to fully closed), so the code can
        // never push the claw into the frame. After it, position 0 = open and 1 = closed.
        servo = ServoSetup.get(hardwareMap, "claw").range(0.20, 0.75).done();   // 0 = open, 1 = closed
    }

    // Open the claw (position 0 inside the range above).
    public void open() {
        servo.setPosition(0);
        open = true;
    }

    // Close the claw (position 1 inside the range above).
    public void close() {
        servo.setPosition(1);
        open = false;
    }

    // Open if closed, close if open. Handy for one button.
    public void toggle() {
        if (open) close(); else open();
    }

    // True if we last told it to open.
    public boolean isOpen() {
        return open;
    }

    // Adds the claw's line to the Driver Station screen.
    // "a ? b : c" = if a is true use b, otherwise c.
    public void addTelemetry(Telemetry telemetry) {
        telemetry.addData("Claw", open ? "open" : "closed");
    }
}
