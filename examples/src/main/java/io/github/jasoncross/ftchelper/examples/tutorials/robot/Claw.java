package io.github.jasoncross.ftchelper.examples.tutorials.robot;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import io.github.jasoncross.ftchelper.hardware.ServoSetup;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** Tutorial 12: the claw as a small subsystem class. */
public class Claw {
    private final Servo servo;
    private boolean open = true;

    public Claw(HardwareMap hardwareMap) {
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

    public void toggle() {
        if (open) close(); else open();
    }

    public boolean isOpen() {
        return open;
    }

    public void addTelemetry(Telemetry telemetry) {
        telemetry.addData("Claw", open ? "open" : "closed");
    }
}
