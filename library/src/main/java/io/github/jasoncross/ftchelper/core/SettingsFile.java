package io.github.jasoncross.ftchelper.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * A small settings file on the Control Hub, so choices and tuned numbers survive a restart.
 * Used by {@link Profile} and {@code Tunable}. You rarely need it directly.
 *
 * <p>The file is {@code /sdcard/FIRST/ftc-helper/settings.properties} on the Control Hub.
 * You can see it with the REV Hardware Client or Android Studio's Device Explorer.
 *
 * Docs: docs/modules/core.md
 */
public final class SettingsFile {
    private static final File DEFAULT_FOLDER = new File("/sdcard/FIRST/ftc-helper");
    private static SettingsFile shared;

    private final File file;
    private final Properties values = new Properties();
    private boolean loaded = false;

    /** A settings file at a path you choose (used by tests). */
    public SettingsFile(File file) {
        this.file = file;
    }

    /** The robot's settings file. */
    public static synchronized SettingsFile shared() {
        if (shared == null) shared = new SettingsFile(new File(DEFAULT_FOLDER, "settings.properties"));
        return shared;
    }

    /** For tests: use another file as the shared one. Pass null to go back to the robot's file. */
    public static synchronized void useForTests(SettingsFile file) {
        shared = file;
    }

    /** A saved text value, or {@code fallback} if none is saved. */
    public synchronized String get(String key, String fallback) {
        load();
        return values.getProperty(key, fallback);
    }

    /** A saved number, or {@code fallback} if none is saved or it is not a number. */
    public synchronized double getDouble(String key, double fallback) {
        String text = get(key, null);
        if (text == null) return fallback;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** True if a value is saved under this key. */
    public synchronized boolean has(String key) {
        load();
        return values.containsKey(key);
    }

    /** Sets a value in memory. Call {@link #save()} to write it to the file. */
    public synchronized void put(String key, String value) {
        load();
        if (value == null) values.remove(key);
        else values.setProperty(key, value);
    }

    /** Removes a value. Call {@link #save()} after. */
    public synchronized void remove(String key) {
        put(key, null);
    }

    /**
     * Writes the file.
     *
     * @return true if it worked. False (for example no storage) never stops the robot.
     */
    public synchronized boolean save() {
        load();
        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) return false;
            try (OutputStream out = new FileOutputStream(file)) {
                values.store(out, "ftc-helper-lib settings");
            }
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (!file.exists()) return;
        try (InputStream in = new FileInputStream(file)) {
            values.load(in);
        } catch (IOException | SecurityException e) {
            // A damaged or unreadable file means "no saved settings"; the code's values are used.
        }
    }
}
