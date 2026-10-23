// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package com.crossbotics.ftchelper.examples.tutorials.robot;

// com.qualcomm... and org.firstinspires... = the FTC SDK. com.crossbotics... = this helper library.
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.crossbotics.ftchelper.hardware.ServoSetup;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Tutorial 12: the claw as a small subsystem class. OpModes just say robot.claw.open().
 * more: https://crossbotics.com/ftc-helper/docs/tutorials/12-robot-structure/#3-a-subsystem-lift
 */
public class Claw {
    private final Servo servo;
    // What we last told it (a servo cannot report where it is). Not moved until open() or close().
    private boolean open = true;

    // Nothing moves here, so it is safe in INIT.
    public Claw(HardwareMap hardwareMap) {
        // "claw" = the name in the robot configuration. Range measured on the robot.
        servo = ServoSetup.get(hardwareMap, "claw").range(0.20, 0.75).done();   // 0 = open, 1 = closed
    }

    public void open() {
        servo.setPosition(0);
        open = true;
    }

    public void close() {
        servo.setPosition(1);
        open = false;
    }

    // Open if closed, close if open: one button.
    public void toggle() {
        if (open) close(); else open();
    }

    // True if we last told it to open.
    public boolean isOpen() {
        return open;
    }

    public void addTelemetry(Telemetry telemetry) {
        telemetry.addData("Claw", open ? "open" : "closed");
    }
}
