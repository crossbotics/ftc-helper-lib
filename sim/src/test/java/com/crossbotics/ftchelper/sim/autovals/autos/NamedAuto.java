package com.crossbotics.ftchelper.sim.autovals.autos;

/** An autonomous's own names, for SimAutoValuesTest (only its numbers are read, nothing runs). */
public class NamedAuto {
    static final double START_X = 52, START_HEADING = -142.2;
    private static final double PARK_HEADING = 0;

    private static double turnToField(double fieldHeading) {
        return fieldHeading - START_HEADING;
    }
}
