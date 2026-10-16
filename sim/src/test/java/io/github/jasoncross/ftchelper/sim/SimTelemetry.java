package io.github.jasoncross.ftchelper.sim;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.firstinspires.ftc.robotcore.external.Telemetry;

/** A pretend Driver Station screen: keeps the lines of the last telemetry.update(). */
final class SimTelemetry {
    private final List<String> building = new ArrayList<>();
    private List<String> screen = new ArrayList<>();
    private int updates = 0;
    final Telemetry telemetry;

    SimTelemetry() {
        telemetry = (Telemetry) Proxy.newProxyInstance(Telemetry.class.getClassLoader(), new Class<?>[] {Telemetry.class},
                (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "addLine":
                            add(a != null && a.length == 1 ? String.valueOf(a[0]) : "");
                            return chain(Telemetry.Line.class);
                        case "addData":
                            if (a.length == 2) add(a[0] + " : " + a[1]);
                            else add(a[0] + " : " + format(String.valueOf(a[1]), (Object[]) a[2]));
                            return chain(Telemetry.Item.class);
                        case "update":
                            synchronized (this) {
                                screen = new ArrayList<>(building);
                                building.clear();
                                updates++;
                            }
                            return true;
                        case "clear":
                        case "clearAll":
                            synchronized (this) { building.clear(); }
                            return null;
                        case "log": return chain(Telemetry.Log.class);
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        default: return SimDefaults.of(m.getReturnType());
                    }
                });
    }

    private synchronized void add(String line) {
        building.add(line);
    }

    private static String format(String fmt, Object[] args) {
        try {
            return String.format(Locale.US, fmt, args);
        } catch (RuntimeException e) {
            return fmt;
        }
    }

    // Telemetry calls can be chained (addData(...).setRetained(...)): give back an object that
    // accepts any call and returns itself.
    private static Object chain(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, m, a) -> {
            if (m.getReturnType().isInstance(proxy)) return proxy;
            if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (m.getName().equals("equals")) return proxy == a[0];
            return SimDefaults.of(m.getReturnType());
        });
    }

    /** The lines on the screen now. */
    synchronized List<String> screen() {
        return new ArrayList<>(screen);
    }

    /**
     * Sends the screen only if lines were added since the last send (like the FTC app after each
     * init_loop()/loop() of an iterative OpMode: the Driver Station keeps the last screen otherwise).
     */
    void updateIfDirty() {
        boolean dirty;
        synchronized (this) { dirty = !building.isEmpty(); }
        if (dirty) telemetry.update();
    }

    synchronized int updates() {
        return updates;
    }

    synchronized void clearScreen() {
        screen = new ArrayList<>();
        building.clear();
    }
}
