package com.crossbotics.ftchelper.finders;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
    // What Robot.java told the menu: there is one, each device's Tunables class, the parts in degrees.
    private final boolean fiveFiles;
    private final Map<String, String> sections;
    private final Set<String> inDegrees;

    Hosting(LinearOpMode host, boolean fiveFiles, Map<String, String> sections, Set<String> inDegrees) {
        this.host = host;
        this.fiveFiles = fiveFiles;
        this.sections = Collections.unmodifiableMap(sections);
        this.inDegrees = Collections.unmodifiableSet(inDegrees);
        this.backWas = host.gamepad1 != null && host.gamepad1.back;
    }

    /** True when the menu found a Robot.java: the results are then lines for Tunables.java. */
    boolean fiveFiles() {
        return fiveFiles;
    }

    /** This device's class in Tunables.java, or null when Robot.java does not say. */
    String section(String device) {
        return sections.get(device);
    }

    /** True when this device's part counts in degrees (an arm, a turret), not in motor counts. */
    boolean inDegrees(String device) {
        return inDegrees.contains(device);
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
