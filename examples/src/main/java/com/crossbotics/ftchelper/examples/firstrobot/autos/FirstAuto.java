package com.crossbotics.ftchelper.examples.firstrobot.autos;

import com.crossbotics.ftchelper.examples.firstrobot.Robot;
// recipe: first-auto
import com.crossbotics.ftchelper.robot.Auto;
import com.crossbotics.ftchelper.robot.Steps;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

/** The five files, file 5 of 5: an autonomous. The smallest one: it drives out of the start spot. */
@Autonomous(name = "First Robot Auto", preselectTeleOp = "First Robot TeleOp")
public class FirstAuto extends Auto<Robot> {

    @Override
    protected void steps(Steps s, Robot robot) {
        s.start("Against our wall, facing the field", 0)
         .forward("Leave the start", 12, 0.4);
    }
}
// end recipe
