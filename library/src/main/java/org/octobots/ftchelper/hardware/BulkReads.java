package org.octobots.ftchelper.hardware;

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
 * Docs: docs/modules/hardware.md
 */
public final class BulkReads {
    private BulkReads() { }

    /** Turns on AUTO bulk reads on every hub. Safe default. Call once in INIT. */
    public static List<LynxModule> auto(HardwareMap hardwareMap) {
        return set(hardwareMap, LynxModule.BulkCachingMode.AUTO);
    }

    /**
     * Turns on MANUAL bulk reads on every hub. You MUST call {@link #clear} once per loop,
     * or the readings never change.
     */
    public static List<LynxModule> manual(HardwareMap hardwareMap) {
        return set(hardwareMap, LynxModule.BulkCachingMode.MANUAL);
    }

    /** Clears the cached readings so the next read gets new values. Use with {@link #manual}. */
    public static void clear(List<LynxModule> hubs) {
        for (LynxModule hub : hubs) hub.clearBulkCache();
    }

    private static List<LynxModule> set(HardwareMap hardwareMap, LynxModule.BulkCachingMode mode) {
        List<LynxModule> hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) hub.setBulkCachingMode(mode);
        return hubs;
    }
}
