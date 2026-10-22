package com.crossbotics.ftchelper.examples.pedrobot;

import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;

/**
 * Pedro's Foresight numbers. THESE ARE THE SIMULATED PEDRO BOT'S NUMBERS, NOT YOUR ROBOT'S: run Pedro's
 * Foresight tuner on your robot (docs/modules/pedro.md) and paste its "Java" output here. Pedro reads
 * them at INIT, so they are not in the tuning menu: change them here and send the code again.
 *
 * Where these come from: the measuring steps of Pedro's Foresight tuner (Pedro's Quickstart,
 * 2026-10-15), run in the simulator on this robot's simulator file, with the tuner's default
 * distances (48 in, 30 in/s, 36 in). The tuner read the physics engine's own speed: speeds worked out
 * from the simulated odometry were too uneven for it (docs/modules/pedro.md, "The simulator").
 * So paths in the simulator brake the way the simulated robot really brakes. 2026-10-21.
 */
public class PedroConstants {
    public static final ForesightConfig foresight = new ForesightConfig(c -> {
        c.forwardTranslational.set(Controller.piecewise(Controller.proportional(0.0826)).put(2.5, Controller.proportional(0.224)));
        c.strafeTranslational.set(Controller.piecewise(Controller.proportional(0.0981)).put(2.5, Controller.proportional(0.265)));
        c.coast.set(Controller.proportionalFeedforward(0.0150));
        c.brake.set(Controller.proportionalFeedforward(0.0127));
        c.headingFeedback.set(Controller.proportional(10.1));
        c.headingBrakeCoefficients.set(Vector2D.cartesian(0.149, 0.00026));
        c.linearBrakeCoefficients.set(Matrix.diag(0.142, 0.147));
        c.quadraticBrakeCoefficients.set(Matrix.diag(0.00020, 0.000053));
        c.maxAchievableForwardVelocity.set(66.7);
        c.maxAchievableStrafeVelocity.set(56.8);
        c.naturalForwardDeceleration.set(21.2);
        c.naturalStrafeDeceleration.set(22.1);
    });
}
