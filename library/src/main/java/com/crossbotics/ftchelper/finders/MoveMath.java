package com.crossbotics.ftchelper.finders;

/**
 * The Move Tuner's math: how far a move slid past its end, how a turn swung, and the number to try
 * next. No hardware here, so every rule has a plain JUnit test (MoveMathTest).
 *
 * <p>Each rule changes ONE number per round, by a small step. WHY: two changes at once hide which
 * one helped. The student runs the test again after each change.
 *
 * Docs: docs/tuning/10-move-tuner.md
 */
final class MoveMath {
    private MoveMath() {
    }

    // ---------------------------------------------------------------------------------------------
    // Creep: the smallest power that moves the robot -> moveMinPower
    // ---------------------------------------------------------------------------------------------

    /** The Creep test raises the power by this much each step. */
    static final double CREEP_STEP = 0.01;
    /** ...every this many seconds (time for the robot to start rolling at the new power). */
    static final double CREEP_STEP_SEC = 0.25;
    /** The robot "moves" when it goes at least this fast, inches per second. */
    static final double CREEP_MOVING_IN_PER_SEC = 2.0;
    /** The Creep test gives up at this power (something holds the robot, or a wheel is not connected). */
    static final double CREEP_MAX_POWER = 0.6;
    /** moveMinPower = the creep power x this. WHY: a lower battery needs more power to move the same robot. */
    static final double CREEP_MARGIN = 1.3;

    /**
     * The moveMinPower to suggest from the Creep test.
     *
     * @param forwardPower the power where the robot started to roll forward (NaN = not found)
     * @param rightPower   the same for strafing right (NaN = not tested: tank, or not found)
     * @return the larger one x {@link #CREEP_MARGIN}, rounded UP to 0.02, inside 0.10..0.60; NaN if neither was found
     */
    static double moveMinPowerFromCreep(double forwardPower, double rightPower) {
        double p = Double.NaN;
        if (!Double.isNaN(forwardPower)) p = forwardPower;
        if (!Double.isNaN(rightPower)) p = Double.isNaN(p) ? rightPower : Math.max(p, rightPower);
        if (Double.isNaN(p)) return Double.NaN;
        // Rounded up to a step of 0.02 (the Tunables step): never below what was measured.
        double up = Math.ceil(p * CREEP_MARGIN / 0.02 - 1e-9) * 0.02;
        return round(Math.max(0.10, Math.min(0.60, up)), 2);
    }

    // ---------------------------------------------------------------------------------------------
    // Stops: how far each move slides past its end -> slowDownIn
    // ---------------------------------------------------------------------------------------------

    /** The slowDownIn values the Stops test tries, smallest first. It stops at the first one that is good. */
    static final double[] SLOW_DOWN_STEPS = {0, 3, 6, 9, 12};
    /** The lengths of the Stops test's moves, inches. Each goes forward, then back. */
    static final double[] STOP_MOVES_IN = {6, 12, 24};
    /** A move that slides past its end by this much or less (inches) is good. */
    static final double SLIDE_OK_IN = 0.5;

    /**
     * How far the robot went along the move's direction, inches. From the robot-relative travel
     * (forward, right) and the move's direction.
     *
     * @param forwardIn    travel forward, inches
     * @param rightIn      travel to the right, inches
     * @param directionDeg the move's direction, counterclockwise like a heading: 0 forward, 90 left, 180 back, -90 right
     * @return the travel along that direction (more than the move's length = it slid past the end)
     */
    static double along(double forwardIn, double rightIn, double directionDeg) {
        double rad = Math.toRadians(directionDeg);
        // The direction as (forward, right): forward = cos, right = -sin (left is +90).
        return forwardIn * Math.cos(rad) - rightIn * Math.sin(rad);
    }

    /**
     * The robot-relative travel (forward, right) from two odometry poses, seen from the robot's
     * heading at the start. Field frame: x right, y away, heading counterclockwise, 0 = facing away.
     *
     * @return {forward inches, right inches}
     */
    static double[] robotTravel(double x0, double y0, double headingDeg0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double h = Math.toRadians(headingDeg0);
        // The same turn of the field error into the robot's frame as driveTo uses.
        return new double[] {-dx * Math.sin(h) + dy * Math.cos(h), dx * Math.cos(h) + dy * Math.sin(h)};
    }

    /** A slide must be at least this much smaller (inches) to count as better: the measurements vary a little. */
    static final double CLEARLY_BETTER_IN = 0.1;

    /** True when slide {@code a} is clearly smaller than slide {@code b} (any slide beats NaN or "none yet"). */
    static boolean clearlyBetter(double a, double b) {
        return Double.isNaN(b) || a < b - CLEARLY_BETTER_IN;
    }

    /** True when one slowDownIn value is good: every move stopped within {@link #SLIDE_OK_IN} and none got stuck. */
    static boolean stopsOk(double worstSlideIn, boolean stalled) {
        return !stalled && worstSlideIn <= SLIDE_OK_IN;
    }

    // ---------------------------------------------------------------------------------------------
    // Turns: overshoot, swings, end error -> turnKp, turnKd, turnMinPower
    // ---------------------------------------------------------------------------------------------

    /** The Turns test's turns, degrees (counterclockwise +). Each pair turns back, so it ends where it began. */
    static final double[] TURNS_DEG = {90, -90, 135, -135, 45, -45};
    /** Each turn in the Turns test may take this long, seconds. */
    static final double TURN_TIMEOUT_SEC = 4.0;
    /** A turn that goes past its target by more than this (degrees) overshoots. */
    static final double OVERSHOOT_OK_DEG = 3.0;
    /** A 90-degree turn that takes longer than this (seconds) is slow. */
    static final double SLOW_90_SEC = 2.0;

    /**
     * Watches a turn's heading error loop by loop: the largest swing past the target, and how many
     * times it crossed to the other side (outside the tolerance). Reset it for each turn.
     */
    static final class TurnTrace {
        private final double toleranceDeg;
        private double startSign = 0;
        private double lastSide = 0;
        double overshootDeg = 0;
        int swings = 0;

        /** @param toleranceDeg crossings inside this band (degrees) do not count */
        TurnTrace(double toleranceDeg) {
            this.toleranceDeg = toleranceDeg;
        }

        /**
         * One loop.
         *
         * @param errorDeg target minus heading, degrees (the short way around)
         */
        void add(double errorDeg) {
            if (startSign == 0) {
                if (Math.abs(errorDeg) <= toleranceDeg) return;
                startSign = Math.signum(errorDeg);
                lastSide = startSign;
            }
            // Past the target = the error has the other sign than at the start.
            overshootDeg = Math.max(overshootDeg, -startSign * errorDeg);
            if (Math.abs(errorDeg) > toleranceDeg) {
                double side = Math.signum(errorDeg);
                if (side != lastSide) {
                    swings++;
                    lastSide = side;
                }
            }
        }
    }

    /** How a test ended. Nothing to change is not the same as right. */
    enum Outcome {
        /** It is right. */
        PASSED("PASSED"),
        /** A number changed, or a move did not finish: test it again. */
        RUN_AGAIN("RUN AGAIN"),
        /** It is wrong and no number here fixes it: the screen says what to do. */
        CANT_FIX("CAN'T FIX");

        final String words;

        Outcome(String words) {
            this.words = words;
        }
    }

    /** How a test ended, why, and the one number to change ({@code field} null = none). */
    static final class Suggestion {
        final Outcome outcome;
        final String field;
        final double value;
        final String why;

        /** A number to change: the test must run again with it. */
        Suggestion(String field, double value, String why) {
            this(Outcome.RUN_AGAIN, field, value, why);
        }

        private Suggestion(Outcome outcome, String field, double value, String why) {
            this.outcome = outcome;
            this.field = field;
            this.value = value;
            this.why = why;
        }

        static Suggestion noChange(Outcome outcome, String why) {
            return new Suggestion(outcome, null, Double.NaN, why);
        }

        /** True only when it passed. */
        boolean ok() {
            return outcome == Outcome.PASSED;
        }
    }

    /**
     * The next change for turns, from the Turns test's worst turn.
     *
     * @param worstOvershootDeg largest swing past a target, degrees
     * @param mostSwings        most crossings in one turn
     * @param worstEndErrorDeg  largest heading error after a turn stopped, degrees (absolute)
     * @param timedOut          a turn ran out of time
     * @param slowest90Sec      the slower of the two 90-degree turns, seconds
     * @param kp                turnKp now
     * @param kd                turnKd now
     * @param minPower          turnMinPower now
     * @param toleranceDeg      turnToleranceDeg now
     * @return the one change to try, or an OK (field null)
     */
    static Suggestion suggestTurn(double worstOvershootDeg, int mostSwings, double worstEndErrorDeg, boolean timedOut,
                                  double slowest90Sec, double kp, double kd, double minPower, double toleranceDeg) {
        boolean swinging = mostSwings >= 2;
        // 1. A turn that did not settle: it ran out of time, or it ended off target.
        if (timedOut || worstEndErrorDeg > toleranceDeg) {
            // Back and forth across the target: too much push for the brake.
            if (swinging) return lessSwing(kp, kd, "turns swing back and forth");
            // Close, but it never stays inside the tolerance: the smallest turn power pushes it a little
            // past each time ("hunting"). A slightly wider tolerance lets the turn end.
            if (minPower > 0 && worstEndErrorDeg <= 2 * toleranceDeg && toleranceDeg < MAX_TOLERANCE_DEG - 1e-9) {
                return new Suggestion("turnToleranceDeg", Math.min(MAX_TOLERANCE_DEG, toleranceDeg + 0.5),
                        "turns rock: allow a little wider");
            }
            // Stuck off target (short of it, or past it): near the target the push is too weak to move
            // the robot at all. A smallest turn power fixes that; WHY not more kP first: more kP also
            // makes the turn come in faster and swing more.
            if (minPower < MIN_POWER_STEPS[MIN_POWER_STEPS.length - 1] - 1e-9) {
                double next = MIN_POWER_STEPS[0];
                for (double step : MIN_POWER_STEPS) {
                    if (step > minPower + 1e-9) {
                        next = step;
                        break;
                    }
                }
                return new Suggestion("turnMinPower", next, "turns stop off target: more turnMinPower");
            }
            return new Suggestion("turnKp", round(kp * 1.2, 4), "turns stop off target: more push");
        }
        // 2. It settled, but went too far past the target first.
        if (worstOvershootDeg > OVERSHOOT_OK_DEG || swinging) return lessSwing(kp, kd, "turns swing past the target");
        // 3. Right, but slow.
        if (slowest90Sec > SLOW_90_SEC) {
            return new Suggestion("turnKp", round(kp * 1.2, 4), "turns are slow: more push");
        }
        return Suggestion.noChange(Outcome.PASSED, "turns end on target, with no swing");
    }

    /** The Turns test widens turnToleranceDeg up to this, degrees. */
    static final double MAX_TOLERANCE_DEG = 3.0;

    /** The turnMinPower values the Turns test tries, one per round. */
    static final double[] MIN_POWER_STEPS = {0.05, 0.08, 0.11};

    // Less swing: more brake (kD) while it is small next to kP; after that, less push (kP).
    private static Suggestion lessSwing(double kp, double kd, String what) {
        if (kd < 0.5 * kp) return new Suggestion("turnKd", round(kd * 1.3, 4), what + ": more brake");
        return new Suggestion("turnKp", round(kp * 0.8, 4), what + ": less push");
    }

    // ---------------------------------------------------------------------------------------------
    // Drift: heading change while moving straight (report only)
    // ---------------------------------------------------------------------------------------------

    /** A straight move that turns the robot more than this (degrees) drifts. */
    static final double DRIFT_OK_DEG = 2.0;
    /** The Drift test's moves, inches. */
    static final double DRIFT_MOVE_IN = 48;

    // ---------------------------------------------------------------------------------------------
    // Move and turn: moveWithTurn -> moveTurnScale, moveMaxTurnPower
    // ---------------------------------------------------------------------------------------------

    /** The Move and turn test's moves, inches (sideways while turning 90 degrees). */
    static final double MOVE_TURN_IN = 24;
    /** The heading may still be this far off (degrees) when the distance is done. More = the turn lags. */
    static final double TURN_LAG_OK_DEG = 10.0;
    /** The heading may swing past the target this far (degrees) during the move. */
    static final double MOVE_TURN_OVERSHOOT_OK_DEG = 5.0;

    /**
     * The next change for moveWithTurn.
     *
     * @param worstLagDeg       the largest heading error when a move's distance was done, degrees
     * @param worstOvershootDeg the largest swing past the target heading during a move, degrees
     * @param scale             moveTurnScale now
     * @param maxTurn           moveMaxTurnPower now
     * @return the one change to try, a pass, or "can't fix"
     */
    static Suggestion suggestMoveTurn(double worstLagDeg, double worstOvershootDeg, double scale, double maxTurn) {
        if (worstOvershootDeg > MOVE_TURN_OVERSHOOT_OK_DEG) {
            return new Suggestion("moveTurnScale", round(Math.max(0.1, scale * 0.8), 2),
                    "it swings past while moving: turn less");
        }
        if (worstLagDeg > TURN_LAG_OK_DEG) {
            if (scale < 1.0) {
                return new Suggestion("moveTurnScale", round(Math.min(1.0, scale * 1.25), 2),
                        "the turn ends late: turn more");
            }
            if (maxTurn < 0.8) {
                return new Suggestion("moveMaxTurnPower", round(Math.min(0.8, maxTurn + 0.1), 2),
                        "the turn ends late: allow more turn power");
            }
            return Suggestion.noChange(Outcome.CANT_FIX, "the turn lags at the most turn power");
        }
        return Suggestion.noChange(Outcome.PASSED, "the heading arrives with the move");
    }

    /**
     * {@link #suggestMoveTurn(double, double, double, double)}, when a move may not have finished.
     *
     * @param didNotFinish a move got stuck or took too long: its numbers prove nothing
     * @return "run again" with no change if a move did not finish, else the same as the other method
     */
    static Suggestion suggestMoveTurn(double worstLagDeg, double worstOvershootDeg, double scale, double maxTurn,
                                      boolean didNotFinish) {
        if (didNotFinish) return Suggestion.noChange(Outcome.RUN_AGAIN, "a move did not finish");
        return suggestMoveTurn(worstLagDeg, worstOvershootDeg, scale, maxTurn);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Waits (without blocking: call it every loop) until some readings stop changing: each stays
     * within its tolerance for {@code windowSec}. Gives up after {@code maxSec}. Used after each
     * move, so the robot has really stopped before it is measured.
     */
    static final class StillWatch {
        private final double windowSec, maxSec;
        private double startSec = Double.NaN, windowStartSec;
        private double[] windowValues;

        StillWatch(double windowSec, double maxSec) {
            this.windowSec = windowSec;
            this.maxSec = maxSec;
        }

        /**
         * One loop.
         *
         * @param nowSec     the clock, seconds
         * @param values     the readings now (wheel inches, heading degrees...)
         * @param tolerances how much each reading may change and still count as still
         * @return true once still (or out of time)
         */
        boolean update(double nowSec, double[] values, double[] tolerances) {
            if (Double.isNaN(startSec)) {
                startSec = nowSec;
                windowStartSec = nowSec;
                windowValues = values.clone();
                return false;
            }
            if (nowSec - startSec >= maxSec) return true;
            for (int i = 0; i < values.length; i++) {
                if (Math.abs(values[i] - windowValues[i]) > tolerances[i]) {
                    // It moved: start the window again from here.
                    windowStartSec = nowSec;
                    windowValues = values.clone();
                    return false;
                }
            }
            return nowSec - windowStartSec >= windowSec;
        }
    }

    /** Rounds to this many decimals. */
    static double round(double value, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(value * f) / f;
    }
}
