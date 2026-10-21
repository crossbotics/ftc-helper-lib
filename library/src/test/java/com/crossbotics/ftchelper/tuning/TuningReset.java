package com.crossbotics.ftchelper.tuning;

/** For tests in other packages: forget every Tunable (they are shared by the whole app). */
public final class TuningReset {
    private TuningReset() { }

    public static void clear() {
        Tunable.clearForTests();
    }
}
