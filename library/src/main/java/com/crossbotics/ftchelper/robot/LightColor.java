package com.crossbotics.ftchelper.robot;

import com.qualcomm.hardware.rev.RevBlinkinLedDriver.BlinkinPattern;

/**
 * The colors a {@link Lights} module can show. Each light turns the name into its own signal: a
 * servo position for the goBILDA RGB Indicator Light, a pattern for the REV Blinkin.
 *
 * <pre>{@code
 * robot.lights.show(LightColor.GREEN).when(robot.intake::isRunning);
 * }</pre>
 *
 * <p>Why not just "Color"? Android already has a class called {@code Color} (color sensors use it),
 * and two classes with one name in one file do not compile.
 *
 * Docs: docs/modules/robot.md (section "Lights")
 */
public enum LightColor {
    // goBILDA RGB Indicator Light 3118-0808-0002: its seller page (Product Insight #4, checked
    // 2026-10-21) gives a servo position for each color, with the hub's normal servo signal
    // (600 to 2400 microseconds). Below 1100 us the light is off, above 1900 us it is white.
    // Two numbers are moved a little inside that window on purpose (see each one).

    /** Red. goBILDA 0.280 (the table says 0.277 = 1099 us, a hair under the 1100 us "off" line), Blinkin RED. */
    RED(0.280, BlinkinPattern.RED, "#ff2020"),
    /** Orange. goBILDA 0.333, Blinkin ORANGE. */
    ORANGE(0.333, BlinkinPattern.ORANGE, "#ff8c00"),
    /** Yellow. goBILDA 0.388, Blinkin YELLOW. */
    YELLOW(0.388, BlinkinPattern.YELLOW, "#ffe600"),
    /** Green. goBILDA 0.500, Blinkin GREEN. */
    GREEN(0.500, BlinkinPattern.GREEN, "#20c040"),
    /** Blue. goBILDA 0.611, Blinkin BLUE. */
    BLUE(0.611, BlinkinPattern.BLUE, "#2050ff"),
    /** Purple. goBILDA 0.700 (between indigo 0.666 and violet 0.722: violet is 1 us from turning white), Blinkin VIOLET. */
    PURPLE(0.700, BlinkinPattern.VIOLET, "#9030ff"),
    /** White. goBILDA 1.0, Blinkin WHITE. */
    WHITE(1.0, BlinkinPattern.WHITE, "#ffffff"),
    /** Dark. goBILDA 0.0, Blinkin BLACK (all LEDs off). */
    OFF(0.0, BlinkinPattern.BLACK, "#000000");

    private final double gobilda;
    private final BlinkinPattern blinkin;
    private final String hex;

    LightColor(double gobilda, BlinkinPattern blinkin, String hex) {
        this.gobilda = gobilda;
        this.blinkin = blinkin;
        this.hex = hex;
    }

    /**
     * The servo position (0 to 1) that shows this color on a goBILDA RGB Indicator Light
     * (3118-0808-0002) with the hub's normal servo signal.
     */
    public double gobildaPosition() {
        return gobilda;
    }

    /** The REV Blinkin LED Driver (REV-11-1105) pattern that shows this color: a solid color. */
    public BlinkinPattern blinkinPattern() {
        return blinkin;
    }

    /** About how it looks, as a web color ("#20c040"): for the simulator page. */
    public String webColor() {
        return hex;
    }
}
