package com.crossbotics.ftchelper.sim;

import com.crossbotics.ftchelper.robot.Starting;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * The cookbook's recipes write MEASURE for the numbers only a real robot can give (docs/cookbook.md:
 * "How a recipe's section reads"), so INIT stops until a team writes theirs. The tests "measure" them
 * here: each MEASURE is set to a sample number before a cookbook robot INITs. A MEASURE with no sample
 * fails loudly, so a new recipe gets one.
 */
final class CookbookSamples {
    private static final String PKG = "com.crossbotics.ftchelper.examples.cookbook.";
    private static boolean done;

    private CookbookSamples() { }

    /** Writes the sample numbers in place of every MEASURE in the cookbook's sections (once). */
    static synchronized void fill() {
        if (done) return;
        Map<String, Double> s = new HashMap<>();
        put(s, "Tunables$Lift", "min", 0, "max", 2600, "down", 0, "low", 900, "high", 2400);
        put(s, "Tunables$LiftLeft", "min", 0, "max", 2900, "down", 0, "low", 1000, "high", 2700);
        put(s, "Tunables$Arm", "min", -10, "max", 120, "stowed", 0, "pickup", 10, "score", 95);
        put(s, "Tunables$Claw", "open", 0.210, "closed", 0.770);
        put(s, "Tunables$Wrist", "down", 0.15, "level", 0.45, "up", 0.80);
        put(s, "Tunables$IntakeDeploy", "stowed", 0.0, "deployed", 0.8);
        put(s, "Tunables$Turret", "min", -90, "max", 90);
        put(s, "Tunables$Indexer", "rest", 0.15, "push", 0.55);
        put(s, "AimRecipes$MovingShotNumbers$Aim", "flightSec", 0.8, "shooterForwardIn", -4, "shooterLeftIn", 0,
                "cameraForwardIn", 7, "cameraLeftIn", 0);
        put(s, "AimRecipes$TrimNumbers$Launcher", "kP", 1.17, "kI", 0.117, "kF", 11.70, "nominalVolts", 12.6);
        put(s, "HoodRecipes$Numbers$Hood", "posA", 0.20, "posB", 0.65, "angleA", 0, "angleB", 30);
        put(s, "HoodRecipes$MotorNumbers$Hood", "posA", 0, "posB", 420, "angleA", 0, "angleB", 30);
        put(s, "InterlockRecipes$Numbers$Turret", "min", -190, "max", 190, "homeAngle", -45);
        put(s, "InterlockRecipes$Numbers$TurretLift", "turretFrom", 130, "turretTo", 218, "liftAbove", 270);
        put(s, "InterlockRecipes$SlipRingNumbers$Turret", "encoderOffset", 117);
        put(s, "InterlockRecipes$ServoTurretNumbers$Turret", "posA", 0.10, "angleA", -135, "posB", 0.90, "angleB", 135);
        for (String top : new String[] {"Tunables", "AimRecipes", "HoodRecipes", "InterlockRecipes"}) {
            try {
                walk(Class.forName(PKG + top), s);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        done = true;
    }

    private static void put(Map<String, Double> s, String section, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            s.put(section + "." + pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
    }

    private static void walk(Class<?> c, Map<String, Double> s) {
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != double.class) continue;
            try {
                f.setAccessible(true);
                if (!Starting.isMeasure(f.getDouble(null))) continue;
                String key = c.getName().substring(PKG.length()) + "." + f.getName();
                Double v = s.get(key);
                if (v == null) throw new IllegalStateException("CookbookSamples: no sample number for " + key);
                f.setDouble(null, v);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        for (Class<?> inner : c.getDeclaredClasses()) walk(inner, s);
    }
}
