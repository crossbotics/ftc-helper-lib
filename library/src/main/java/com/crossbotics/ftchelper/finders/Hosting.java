package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

/**
 * Runs a finder (or the Hardware Check) inside another OpMode: "Tune and check"
 * ({@link TuneAndCheckOpMode}). The finder gets the host's hardware, screen and gamepads, and the host's
 * INIT, START and STOP. In INIT, BACK on gamepad 1 leaves the finder and goes back to the host's menu.
 * You do not use this class yourself.
 */
public final class Hosting {
    private final LinearOpMode host;
    // BACK held in the LAST check: only a new press counts (the press that opened the menu does not).
    private boolean backWas;
    private boolean left = false;

    Hosting(LinearOpMode host) {
        this.host = host;
        this.backWas = host.gamepad1 != null && host.gamepad1.back;
    }

    /** True after BACK in INIT: the finder ended so the host can show its menu again. */
    boolean leftForMenu() {
        return left;
    }

    /**
     * Gives the finder the host's hardware, screen and gamepads. Done again at every check, because the
     * SDK may give an OpMode new gamepad objects.
     */
    public void lend(OpMode guest) {
        guest.hardwareMap = host.hardwareMap;
        guest.telemetry = host.telemetry;
        guest.gamepad1 = host.gamepad1;
        guest.gamepad2 = host.gamepad2;
    }

    /** The host's opModeInInit(); false (and the finder ends) after a new BACK press. */
    public boolean inInit(OpMode guest) {
        lend(guest);
        boolean back = host.gamepad1 != null && host.gamepad1.back;
        boolean pressed = back && !backWas;
        backWas = back;
        if (pressed && host.opModeInInit()) left = true;
        return !left && host.opModeInInit();
    }

    /** The host's opModeIsActive(). */
    public boolean running(OpMode guest) {
        lend(guest);
        return !left && host.opModeIsActive();
    }

    /** The host's isStopRequested(), or true after BACK (so the finder ends without running). */
    public boolean stopRequested() {
        return left || host.isStopRequested();
    }
}
