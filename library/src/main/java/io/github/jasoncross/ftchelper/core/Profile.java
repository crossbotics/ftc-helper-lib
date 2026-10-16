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
 * Docs: docs/guides/profiles.md
 */
public enum Profile {
    PRACTICE,
    COMPETITION;

    private static final String KEY = "profile";
    private static Profile current = null;

    /** The active profile (read from the saved setting the first time). */
    public static synchronized Profile current() {
        if (current == null) {
            String saved = SettingsFile.shared().get(KEY, COMPETITION.name());
            current = PRACTICE.name().equals(saved) ? PRACTICE : COMPETITION;
        }
        return current;
    }

    /** Changes the profile and saves it on the Control Hub. */
    public static synchronized void set(Profile profile) {
        current = profile;
        SettingsFile settings = SettingsFile.shared();
        settings.put(KEY, profile.name());
        settings.save();
    }

    /** For tests: forget the loaded value so it is read again. */
    public static synchronized void resetForTests() {
        current = null;
    }

    /**
     * One value for practice, another for competition.
     *
     * @return {@code practice} in the PRACTICE profile, else {@code competition}
     */
    public static <T> T pick(T practice, T competition) {
        return current() == PRACTICE ? practice : competition;
    }

    /** Same as {@link #pick(Object, Object)} for numbers (no boxing). */
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
