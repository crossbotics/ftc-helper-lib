// Which folder this file lives in. Change it to your TeamCode package when you copy the file.
package io.github.jasoncross.ftchelper.examples.starter;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import io.github.jasoncross.ftchelper.core.RobotConfig;
import io.github.jasoncross.ftchelper.finders.CharacterizerOpMode;
import java.util.Arrays;
import java.util.List;

/**
 * STARTER KIT finder: measures how the robot really moves (drive, turning, the lift, the arm and the
 * flywheel), for the simulator's robot file and for feedforward in your code. About 5 ft clear in front
 * and behind for the drive test; lifts start at the bottom. How to use it: docs/modules/finders.md.
 */
@TeleOp(name = "Characterizer", group = "Finders")
// @Disabled hides it from the list. Delete this line in your copy, or you will not see it.
@Disabled
public class Characterizer extends CharacterizerOpMode {
    // The same settings TeleOp and autonomous use (motor names and directions).
    @Override
    protected RobotConfig robotConfig() {
        return new RobotSettings();
    }

    // What to measure besides the drive: each lift/arm with its soft limits (counts from the bottom,
    // a little below the hard stop), and each flywheel. Use your motor names.
    @Override
    protected List<Mechanism> mechanisms() {
        return Arrays.asList(
                Mechanism.lift("lift", 0, 2600),
                Mechanism.lift("arm", 0, 1100),
                Mechanism.flywheel("flywheel"));
    }
}
