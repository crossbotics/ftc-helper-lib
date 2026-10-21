package com.crossbotics.ftchelper.drive;

/**
 * A "drive backwards" mode: when ON, forward and strafe are flipped, so the back of the
 * robot acts as the front. Turning is NOT flipped (the stick still turns the robot the same way).
 *
 * <p>Useful when the driver must look at the robot's back (for example to load a game piece).
 *
 * <pre>{@code
 * ReverseDrive reverse = new ReverseDrive();
 * controls.bindPress(Pad.G1, Btn.L_STICK_BTN, () -> confirm.play(reverse.toggle()));
 * // every loop:
 * drive.drive(reverse.apply(forward), reverse.apply(strafe), turn);
 * }</pre>
 *
 * Docs: docs/modules/drive.md. Tutorial 04 ("Reverse drive") shows it in a TeleOp.
 */
public final class ReverseDrive {
    // The mode, remembered between loops. Starts OFF (normal driving).
    private boolean reversed = false;

    /**
     * Flips the mode.
     *
     * @return the new state (true = reversed), handy for a sound or rumble that tells the driver
     */
    public boolean toggle() {
        reversed = !reversed;
        return reversed;
    }

    /** Sets the mode. */
    public void set(boolean reversed) {
        this.reversed = reversed;
    }

    /** True when reversed. */
    public boolean isReversed() {
        return reversed;
    }

    /**
     * Use on forward and strafe only. Returns the value, flipped if reversed.
     * (Not on turning: a stick pushed right should still turn the robot clockwise.)
     */
    public double apply(double translation) {
        // "a ? b : c" = "if a then b, else c".
        return reversed ? -translation : translation;
    }
}
