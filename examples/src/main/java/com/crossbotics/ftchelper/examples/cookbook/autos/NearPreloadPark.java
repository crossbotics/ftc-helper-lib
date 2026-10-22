// recipe: autos-near-preload-park
package com.crossbotics.ftchelper.examples.cookbook.autos;
// In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode.autos;
// and the first import below to:  import org.firstinspires.ftc.teamcode.Robot;
// (Routes is in the same autos folder: no import needed for it.)

import com.crossbotics.ftchelper.examples.cookbook.Robot;
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;

/** Score the preload, then park. preselectTeleOp: YOUR TeleOp's name. */
@Autonomous(name = "Near: Preload + Park", group = "Autos", preselectTeleOp = "Cookbook TeleOp")
@Disabled   // delete this line in your copy
public class NearPreloadPark extends Auto<Robot> {
    @Override
    protected boolean mirrorForRed() {
        return false;   // BIOBUZZ: the field is turned half a turn, one route for both alliances
    }

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("NEAR: against our wall, 2nd tile left of centre, facing the field", -36, -61, 0);
        Routes.scorePreload(s, robot);
        Routes.park(s);
    }
}
// end recipe
