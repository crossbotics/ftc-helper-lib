package com.crossbotics.ftchelper.hardware;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.HardwareMap;
import java.util.List;

/**
 * Makes sensor and encoder reads much faster with "bulk reads".
 *
 * <p>Without this, each encoder read is a separate message to the hub (about 2 ms each).
 * With bulk reads, the first read in a loop gets everything from the hub in one message.
 * This often doubles the loop speed.
 *
 * <p>Words used here: a "hub" is the Control Hub or Expansion Hub, the box the motors and
 * sensors plug into (the SDK calls it a {@code LynxModule}). An "encoder" counts how far a motor
 * has turned. The "cache" is the hub's saved copy of the last bulk read: reads use the saved
 * copy until it is cleared, so they are fast but only as new as the last bulk read.
 *
 * <pre>{@code
 * // Easiest: one line in INIT. A new bulk read happens when you read any value a second time.
 * BulkReads.auto(hardwareMap);
 *
 * // Fastest: you clear the cache yourself at the top of each loop.
 * List<LynxModule> hubs = BulkReads.manual(hardwareMap);
 * while (opModeIsActive()) {
 *     BulkReads.clear(hubs);
 *     ...
 * }
 * }</pre>
 *
 * Tutorial 06 (docs/tutorials/06-fast-clear-loop.md) shows this in a full program.
 *
 * Docs: docs/modules/hardware.md
 */
public final class BulkReads {
    // "final" on a class means no other class can be built on top of it (extend it).
    // This class is only a box of helper methods, so there is nothing to extend.
    // A "private" constructor means nobody can make a BulkReads object with "new".
    // You never need one: every method is "static", which means you call it on the class name
    // itself, like BulkReads.auto(hardwareMap).
    private BulkReads() { }

    /**
     * Turns on AUTO bulk reads on every hub. Safe default. Call once in INIT.
     *
     * <p>In AUTO mode the hub does a fresh bulk read on its own when you read the same value a
     * second time, so you can never get stuck with old readings.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @return the list of hubs (you only need it if you later switch to {@link #manual})
     */
    public static List<LynxModule> auto(HardwareMap hardwareMap) {
        return set(hardwareMap, LynxModule.BulkCachingMode.AUTO);
    }

    /**
     * Turns on MANUAL bulk reads on every hub. You MUST call {@link #clear} once per loop,
     * or the readings never change.
     *
     * <p>Why use it? It is the fastest: exactly one bulk read per hub per loop.
     * The danger: if you forget {@link #clear}, the robot keeps seeing the same old encoder
     * and sensor numbers, so a lift would never "see" itself move.
     *
     * @param hardwareMap the OpMode's {@code hardwareMap}
     * @return the list of hubs: keep it and pass it to {@link #clear} every loop
     */
    public static List<LynxModule> manual(HardwareMap hardwareMap) {
        return set(hardwareMap, LynxModule.BulkCachingMode.MANUAL);
    }

    /**
     * Clears the cached readings so the next read gets new values. Use with {@link #manual}.
     * Call it once, at the top of every loop, before anything reads a sensor or encoder.
     *
     * @param hubs the list that {@link #manual} gave you
     */
    public static void clear(List<LynxModule> hubs) {
        // "for (X x : list)" means "do this once for each item in the list".
        for (LynxModule hub : hubs) hub.clearBulkCache();
    }

    // Shared by auto() and manual(): find every hub on the robot and give each the same mode.
    // Most robots have two hubs (Control Hub + Expansion Hub); both must be set, or reads from
    // the second hub stay slow.
    private static List<LynxModule> set(HardwareMap hardwareMap, LynxModule.BulkCachingMode mode) {
        List<LynxModule> hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) hub.setBulkCachingMode(mode);
        return hubs;
    }
}
