package com.crossbotics.ftchelper.testkit;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * A pretend Driver Station screen for JUnit tests: it keeps the last screen sent, one line per entry
 * ("Lift : high  900 ticks", "!! warning").
 *
 * <pre>{@code
 * PretendTelemetry screen = new PretendTelemetry();
 * RobotTelemetry t = new RobotTelemetry(screen.telemetry);
 * robot.all().addTelemetry(t);
 * t.update();
 * assertTrue(screen.has("Lift : high"));
 * }</pre>
 *
 * Docs: docs/modules/testkit.md
 */
public class PretendTelemetry {
    /** The lines of the screen being built (before {@code update()}). */
    public final List<String> building = new ArrayList<>();
    /** The last screen sent, one line per entry. */
    public List<String> lastScreen = new ArrayList<>();
    /** How many screens were sent. */
    public int updates = 0;
    /** The SDK telemetry to hand to code that wants one (e.g. {@code new RobotTelemetry(...)}). */
    public final Telemetry telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(),
            new Class<?>[]{Telemetry.class}, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "addLine":
                        if (a != null && a.length == 1) building.add(String.valueOf(a[0]));
                        return null;
                    case "addData":
                        if (a.length == 2) building.add(a[0] + " : " + a[1]);
                        else building.add(a[0] + " : " + String.format(java.util.Locale.US, String.valueOf(a[1]), (Object[]) a[2]));
                        return null;
                    case "update":
                        lastScreen = new ArrayList<>(building);
                        building.clear();
                        updates++;
                        return true;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == a[0];
                    default: return PretendDevices.nothing(m.getReturnType());
                }
            });

    /** True if a line of the last screen contains these words. */
    public boolean has(String words) {
        for (String line : lastScreen) if (line.contains(words)) return true;
        return false;
    }
}
