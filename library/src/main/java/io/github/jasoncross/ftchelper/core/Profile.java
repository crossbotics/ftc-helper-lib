package io.github.jasoncross.ftchelper.core;

/**
 * Practice or competition. Lets the same code behave safely at an event and helpfully in the shop.
 *
 * <table>
 *   <tr><th></th><th>PRACTICE</th><th>COMPETITION</th></tr>
 *   <tr><td>debug telemetry</td><td>on</td><td>off</td></tr>
 *   <tr><td>tuning menu</td><td>allowed</td><td>blocked</td></tr>
 *   <tr><td>your own values</td><td colspan="2">{@code Profile.pick(practiceValue, competitionValue)}</td></tr>
 * </table>
 *
 * <p>The choice is saved on the Control Hub, so it stays until someone changes it
 * (with {@code InitMenu.profile()} or {@link #set}). The default is COMPETITION, the safe one.
 *
 * <pre>{@code
 * double drivePower = Profile.pick(0.5, 1.0);     // gentle in the shop, full power at events
 * if (Profile.current().isPractice()) { ... }
 * }</pre>
 *
 * <p>Java note: an "enum" is a fixed list of named choices. A Profile can only ever be PRACTICE
 * or COMPETITION, so a typo cannot create a third, unknown profile.
 *
 * Docs: docs/guides/profiles.md
 */
public enum Profile {
    /** In the shop: debug telemetry on, tuning menu allowed. */
    PRACTICE,
    /** At an event: debug telemetry off, tuning menu blocked. The safe default. */
    COMPETITION;

    // The name the choice is saved under in the settings file.
    private static final String KEY = "profile";
    // The profile in use, or null until it is first read from the settings file.
    // "static": there is one shared copy for the whole robot app, not one per object.
    private static Profile current = null;

    // "synchronized": only one piece of code at a time may run this, so two threads cannot
    // both read the file at once and disagree.
    /** The active profile (read from the saved setting the first time). */
    public static synchronized Profile current() {
        if (current == null) {
            // Nothing saved yet? Use COMPETITION.
            String saved = SettingsFile.shared().get(KEY, COMPETITION.name());
            // Only the exact word "PRACTICE" gives PRACTICE. Anything else (even a damaged file)
            // gives COMPETITION, the safe choice.
            current = PRACTICE.name().equals(saved) ? PRACTICE : COMPETITION;
        }
        return current;
    }

    /**
     * Changes the profile and saves it on the Control Hub.
     *
     * @param profile the new profile
     */
    public static synchronized void set(Profile profile) {
        current = profile;
        SettingsFile settings = SettingsFile.shared();
        settings.put(KEY, profile.name());
        // Written on a background thread, so the INIT loop never waits for the storage.
        // If saving fails (no storage), the new profile still works until the app restarts.
        settings.saveInBackground();
    }

    /** For tests: forget the loaded value so it is read again. */
    public static synchronized void resetForTests() {
        current = null;
    }

    /**
     * One value for practice, another for competition.
     *
     * <p>{@code <T>} is a "type placeholder" (a generic): it works for any kind of value
     * (text, objects...), and you get back the same kind you gave.
     *
     * @param practice    the value to use in PRACTICE
     * @param competition the value to use in COMPETITION
     * @return {@code practice} in the PRACTICE profile, else {@code competition}
     */
    public static <T> T pick(T practice, T competition) {
        // "a ? b : c" is a short if/else.
        return current() == PRACTICE ? practice : competition;
    }

    /**
     * Same as {@link #pick(Object, Object)} for numbers (no boxing).
     * ("Boxing" is Java wrapping a plain number in an object; this version skips that.)
     */
    public static double pick(double practice, double competition) {
        return current() == PRACTICE ? practice : competition;
    }

    /** Same as {@link #pick(Object, Object)} for true/false. */
    public static boolean pick(boolean practice, boolean competition) {
        return current() == PRACTICE ? practice : competition;
    }

    /** True for PRACTICE. */
    public boolean isPractice() {
        return this == PRACTICE;
    }
}
