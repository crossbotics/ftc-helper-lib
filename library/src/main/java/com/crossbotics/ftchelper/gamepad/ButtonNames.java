package com.crossbotics.ftchelper.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names printed on the driver's own controller, for the Driver Station screen: A, B, X, Y on a
 * Logitech or Xbox controller, and ×, ○, □, △ on a PlayStation-style one (PS4, PS5, Etpark).
 *
 * <p>WHY: a student holding a PS controller has no button called "A". And "START" on the screen can
 * mean the Driver Hub's ▶ (start the OpMode) or the START button on the gamepad. So the library's
 * screens write buttons as short marks, and this class turns them into the right name just before the
 * screen is sent:
 *
 * <ul>
 *   <li>{@code {A} {B} {X} {Y} {LB} {RB} {LT} {RT} {BACK} {START} {GUIDE}}: gamepad 1's button
 *       ({@code {START}} is OPTIONS on a PS controller, {@code {GUIDE}} the PS button).</li>
 *   <li>{@code {2:A}} and the like: gamepad 2's button (its own controller may be the other kind).</li>
 *   <li>{@code {PLAY}}: the Driver Hub's ▶ start button (never a gamepad button).</li>
 *   <li>Generated lines such as "G2 RT: intake in" need no marks: "G1 " or "G2 " before a button name
 *       is changed too.</li>
 * </ul>
 *
 * <p>Which kind: the FTC SDK tells it ({@code gamepad.type}) once the controller is linked (START + A,
 * or OPTIONS + ×). Before that, and for every other kind, the letters are used. The library's OpModes
 * call {@link #watch} at INIT; {@code RobotTelemetry} calls {@link #say} on every line it sends.
 *
 * <pre>{@code
 * ButtonNames.watch(gamepad1, gamepad2);
 * telemetry.addLine(ButtonNames.say("{A} = start, {B} = stop"));   // "× = start, ○ = stop" on a PS controller
 * }</pre>
 *
 * Docs: docs/modules/gamepad.md
 */
public final class ButtonNames {
    private ButtonNames() {}

    /** The kind of names a controller has printed on it. */
    public enum Style {
        /** A, B, X, Y, LB, RB, LT, RT, BACK, START, GUIDE: Logitech F310, Xbox, and any unknown controller. */
        LETTERS,
        /** ×, ○, □, △, L1, R1, L2, R2, SHARE, OPTIONS, PS: PS4, PS5 and PS-style controllers. */
        PLAYSTATION
    }

    /** The Driver Hub's start button (the ▶ that starts the OpMode after INIT). */
    public static final String PLAY = "▶";

    // The gamepads the screens are about. "volatile": the OpMode's thread sets them, others read them.
    private static volatile Gamepad pad1, pad2;
    // For tests and the simulator: a style that wins over the gamepads' own (null = ask the gamepads).
    private static volatile Style forced1, forced2;

    private static final String[] BUTTONS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "BACK", "START", "GUIDE"};
    private static final String[] PS = {"×", "○", "□", "△", "L1", "R1", "L2", "R2", "SHARE", "OPTIONS", "PS"};
    // {A}, {2:RT}, {PLAY}...
    private static final Pattern MARK = Pattern.compile("\\{(?:([12]):)?(A|B|X|Y|LB|RB|LT|RT|BACK|START|GUIDE|PLAY)\\}");
    // "G1 A", "G2 RT", "G2:LB" in generated lines (a whole word after it, so "G1 Axis" is not a button).
    private static final Pattern PAD_BUTTON = Pattern.compile("\\bG([12])([ :])(A|B|X|Y|LB|RB|LT|RT|BACK|START|GUIDE)\\b");

    /**
     * Uses these gamepads' kinds for the screen names from now on. The library's OpModes call it at
     * INIT; call it in your own OpMode to get the same names.
     *
     * @param gamepad1 the driver's gamepad (may be null)
     * @param gamepad2 the operator's gamepad (may be null)
     */
    public static void watch(Gamepad gamepad1, Gamepad gamepad2) {
        pad1 = gamepad1;
        pad2 = gamepad2;
    }

    /**
     * For tests and the simulator: names as if gamepad {@code pad} were this kind (null = ask the gamepad).
     *
     * @param pad   1 or 2
     * @param style the kind, or null
     */
    public static void force(int pad, Style style) {
        if (pad == 2) forced2 = style;
        else forced1 = style;
    }

    /**
     * The kind of gamepad 1 or 2.
     *
     * @param pad 1 or 2
     * @return PLAYSTATION for a PS-style controller, else LETTERS (also before it is linked)
     */
    public static Style style(int pad) {
        Style forced = pad == 2 ? forced2 : forced1;
        if (forced != null) return forced;
        Gamepad g = pad == 2 ? pad2 : pad1;
        return g == null ? Style.LETTERS : styleOf(g);
    }

    /**
     * The kind of names on this gamepad.
     *
     * @param g a gamepad
     * @return PLAYSTATION when the SDK says it is a Sony (or Sony-style) controller, else LETTERS
     */
    public static Style styleOf(Gamepad g) {
        Gamepad.Type t = g.type;
        if (t == null) return Style.LETTERS;
        // SONY_PS4, SONY_PS4_SUPPORTED_BY_KERNEL, and later SDKs' PS5 names.
        return t.name().startsWith("SONY") ? Style.PLAYSTATION : Style.LETTERS;
    }

    /**
     * One button's name on gamepad 1 or 2: "A" or "×", "START" or "OPTIONS".
     *
     * @param pad    1 or 2
     * @param button A, B, X, Y, LB, RB, LT, RT, BACK, START or GUIDE (as the SDK names it)
     * @return the name on that controller (the button itself when it is not one of these)
     */
    public static String name(int pad, String button) {
        return nameIn(style(pad), button);
    }

    /**
     * One button's name on a controller of this kind.
     *
     * @param style  the controller's kind
     * @param button A, B, X, Y, LB, RB, LT, RT, BACK, START or GUIDE
     * @return its printed name (the button itself when it is not one of these)
     */
    public static String nameIn(Style style, String button) {
        if (style != Style.PLAYSTATION) return button;
        for (int i = 0; i < BUTTONS.length; i++) if (BUTTONS[i].equals(button)) return PS[i];
        return button;
    }

    /**
     * The mark for one button, for a line that {@link #say} turns into its name later: {@code mark(2, "RT")}
     * is {@code "{2:RT}"}. A name that is not one of the buttons with a mark (a D-pad, a stick) comes back as it is.
     *
     * @param pad    1 or 2
     * @param button A, B, X, Y, LB, RB, LT, RT, BACK, START or GUIDE
     * @return the mark, or the name itself
     */
    public static String mark(int pad, String button) {
        for (String b : BUTTONS) if (b.equals(button)) return "{" + (pad == 2 ? "2:" : "") + button + "}";
        return button;
    }

    /**
     * A screen line with its marks turned into this robot's button names ({@code {A}}, {@code {2:RT}},
     * {@code {PLAY}}, and "G1 A" / "G2 RT"). Text with no marks comes back as it was.
     *
     * @param text a line for the screen (null stays null)
     * @return the line with the names on the controllers in use
     */
    public static String say(String text) {
        if (text == null || (text.indexOf('{') < 0 && text.indexOf('G') < 0)) return text;
        String out = text;
        if (out.indexOf('{') >= 0) {
            Matcher m = MARK.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String button = m.group(2);
                String said = button.equals("PLAY") ? PLAY : name("2".equals(m.group(1)) ? 2 : 1, button);
                m.appendReplacement(sb, Matcher.quoteReplacement(said));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        if (style(1) == Style.LETTERS && style(2) == Style.LETTERS) return out;   // the names are the letters already
        Matcher m = PAD_BUTTON.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int pad = m.group(1).equals("2") ? 2 : 1;
            m.appendReplacement(sb, Matcher.quoteReplacement("G" + pad + m.group(2) + name(pad, m.group(3))));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
