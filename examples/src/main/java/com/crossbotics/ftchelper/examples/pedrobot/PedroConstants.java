package com.crossbotics.ftchelper.examples.pedrobot;

import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.controllers.Controller;
import com.pedropathing.math.Matrix;
import com.pedropathing.math.Vector2D;

/**
 * Pedro's Foresight numbers. THESE ARE PEDRO'S QUICKSTART SAMPLE NUMBERS, NOT YOUR ROBOT'S: run Pedro's
 * Foresight tuner (docs/modules/pedro.md) and paste its "Java" output here. Pedro reads them at INIT, so
 * they are not in the tuning menu: change them here and send the code again.
 */
public class PedroConstants {
    public static final ForesightConfig foresight = new ForesightConfig(c -> {
        c.forwardTranslational.set(Controller.piecewise(Controller.proportional(0.1)).put(2.5, Controller.proportional(0.3)));
        c.strafeTranslational.set(Controller.piecewise(Controller.proportional(0.1)).put(2.5, Controller.proportional(0.3)));
        c.coast.set(Controller.proportionalFeedforward(0.010978350889324107));
        c.brake.set(Controller.proportionalFeedforward(0.008731598255925491));
        c.headingFeedback.set(Controller.proportional(5.258721785960744));
        c.headingBrakeCoefficients.set(Vector2D.cartesian(0.05642143125655298, 0.0063829525363003695));
        c.linearBrakeCoefficients.set(Matrix.diag(0.10605894992901523, 0.08719146175596092));
        c.quadraticBrakeCoefficients.set(Matrix.diag(0.0014663966976606565, 0.0013837064502458813));
        c.maxAchievableForwardVelocity.set(72.72923108818539);
        c.maxAchievableStrafeVelocity.set(52.34323936525474);
        c.naturalForwardDeceleration.set(85.01144677379789);
        c.naturalStrafeDeceleration.set(104.49787535782846);
    });
}
