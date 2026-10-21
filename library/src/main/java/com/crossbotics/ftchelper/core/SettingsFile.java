package com.crossbotics.ftchelper.core;

import com.crossbotics.ftchelper.util.BackgroundWorker;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * A small settings file on the Control Hub, so choices and tuned numbers survive a restart.
 * Used by {@link Profile} and {@code Tunable}. You rarely need it directly.
 *
 * <p>The file is {@code /sdcard/FIRST/ftc-helper/settings.properties} on the Control Hub.
 * You can see it with the REV Hardware Client or Android Studio's Device Explorer.
 *
 * <p>Inside, each line is {@code key=value}, for example {@code profile=PRACTICE}. Every value is
 * stored as text; {@link #getDouble} turns it back into a number.
 *
 * <p>Safety: nothing here ever stops the robot. A missing, damaged or unwritable file only means
 * the values written in the code are used.
 *
 * Docs: docs/modules/core.md
 */
public final class SettingsFile {
    // Where the robot's settings file lives on the Control Hub.
    private static final File DEFAULT_FOLDER = new File("/sdcard/FIRST/ftc-helper");
    // The one shared settings file for the whole app ("static": one copy, not one per object).
    private static SettingsFile shared;

    // This object's file on disk.
    private final File file;
    // The values in memory. Properties is Java's ready-made "key=value" list that can read and
    // write this kind of file.
    private final Properties values = new Properties();
    // True once we have read the file, so we read it only once.
    private boolean loaded = false;
    // Held while writing the file, so two saves never write at the same moment.
    private final Object writeLock = new Object();
    // The background thread for saveInBackground(), shared by every settings file.
    private static BackgroundWorker saver;

    // Every public method below is "synchronized": only one piece of code at a time may use the
    // values, so a background thread and the loop cannot mix up a read and a write.

    /**
     * A settings file at a path you choose (used by tests).
     *
     * @param file the file to read and write
     */
    public SettingsFile(File file) {
        this.file = file;
    }

    /** The robot's settings file. */
    public static synchronized SettingsFile shared() {
        // Made the first time someone asks, then the same one is handed out every time.
        if (shared == null) shared = new SettingsFile(new File(DEFAULT_FOLDER, "settings.properties"));
        return shared;
    }

    /** For tests: use another file as the shared one. Pass null to go back to the robot's file. */
    public static synchronized void useForTests(SettingsFile file) {
        shared = file;
    }

    /**
     * A saved text value, or {@code fallback} if none is saved.
     *
     * @param key      the name the value is saved under
     * @param fallback what to return when nothing is saved
     */
    public synchronized String get(String key, String fallback) {
        load();
        return values.getProperty(key, fallback);
    }

    /**
     * A saved number, or {@code fallback} if none is saved or it is not a number.
     *
     * @param key      the name the value is saved under
     * @param fallback what to return when nothing usable is saved
     */
    public synchronized double getDouble(String key, double fallback) {
        String text = get(key, null);
        if (text == null) return fallback;
        // "try/catch": if the text is not a number (someone edited the file by hand), use the
        // fallback instead of crashing.
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

    /**
     * Sets a value in memory. Call {@link #save()} to write it to the file.
     *
     * @param key   the name to save it under
     * @param value the text to save; null removes the key
     */
    public synchronized void put(String key, String value) {
        // Read the file first, so saving later does not wipe out values we never loaded.
        load();
        if (value == null) values.remove(key);
        else values.setProperty(key, value);
    }

    /** Removes a value. Call {@link #save()} after. */
    public synchronized void remove(String key) {
        put(key, null);
    }

    /**
     * Writes the file. This WAITS for the storage (a few milliseconds, sometimes more): in a
     * running OpMode use {@link #saveInBackground} instead.
     *
     * @return true if it worked. False (for example no storage) never stops the robot.
     */
    public boolean save() {
        // Copy the values while holding the lock (quick), then write the copy WITHOUT the lock, so
        // the loop can still read and change values while the storage is slow.
        Properties copy;
        synchronized (this) {
            load();
            copy = (Properties) values.clone();
        }
        // Only one write to this file at a time.
        synchronized (writeLock) {
            return write(copy);
        }
    }

    /**
     * Writes the file on a background thread and returns at once, so the robot loop never waits
     * for the storage. Saves asked for while one is waiting are merged: only the newest values are written.
     *
     * @param whenDone gets true if it worked, false if not (runs on the background thread); may be null
     */
    public void saveInBackground(Consumer<Boolean> whenDone) {
        saver().submitLatest("save " + file.getPath(), () -> {
            boolean ok = save();
            if (whenDone != null) whenDone.accept(ok);
        });
    }

    /** {@link #saveInBackground(Consumer)} without a result. */
    public void saveInBackground() {
        saveInBackground(null);
    }

    // One background thread for every settings save in the app (made the first time it is needed).
    private static synchronized BackgroundWorker saver() {
        if (saver == null) saver = new BackgroundWorker("settings");
        return saver;
    }

    // Writes these values to the file. Called with writeLock held.
    private boolean write(Properties copy) {
        try {
            // Make the folder if it is not there yet. If that fails, give up quietly.
            File folder = file.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) return false;
            // Safe against a power cut: write a new file next to it, make sure it is really on the
            // storage (sync), then swap it in with one rename. A cut before the rename leaves the old
            // file; after it, the new one. Never a half-written file.
            // ("try (...)" closes the file again automatically, even if writing fails part way.)
            File temp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                copy.store(out, "ftc-helper-lib settings");
                out.flush();
                out.getFD().sync();
            }
            if (!temp.renameTo(file)) {
                // Some storage will not rename over a file: remove the old one, then rename.
                // If that fails too, the new file stays as ".tmp", and the next load reads it.
                if (!file.delete() || !temp.renameTo(file)) return false;
            }
            return true;
        } catch (IOException | SecurityException e) {
            // Could not write (no storage, no permission): report false, do not crash the robot.
            return false;
        }
    }

    // Reads the file into memory, the first time only.
    private void load() {
        if (loaded) return;
        loaded = true;
        // No file yet is normal (first run): start with no saved values. Only a ".tmp" left by a save
        // that could not rename: read that.
        File temp = new File(file.getPath() + ".tmp");
        File from = file.exists() ? file : temp.exists() ? temp : null;
        if (from == null) return;
        try (InputStream in = new FileInputStream(from)) {
            values.load(in);
        } catch (IOException | SecurityException e) {
            // A damaged or unreadable file means "no saved settings"; the code's values are used.
        }
    }
}
