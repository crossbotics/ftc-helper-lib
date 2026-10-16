package io.github.jasoncross.ftchelper.sim;

import io.github.jasoncross.ftchelper.finders.CharacterizeMath;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fit tool: reads the Characterizer's logs (characterizer/*.csv, from the robot or from the
 * simulator), fits each motor's numbers, prints the robot-file settings, and with --write puts them
 * into the robot file (other lines and notes stay as they are).
 *
 * ./gradlew :sim:fit [-Plog=file-or-folder] [-Probot=robot.properties] [-Pwrite]
 *
 * Logs of the same test (same kind and motor) are fitted together, so running a test 2-3 times gives
 * steadier numbers.
 */
public final class SimFit {
    private SimFit() { }

    /** The settings to write: robot-file key -> (setting -> value). Drive settings use key "" (whole lines). */
    static Map<String, Map<String, String>> fit(List<Path> logs, List<String> report) throws IOException {
        // group the logs: "drive", "turn", "lift:lift", "flywheel:flywheel"
        Map<String, List<double[]>> groups = new LinkedHashMap<>();
        for (Path p : logs) {
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            String kind = null, motor = null;
            boolean complete = true;
            List<double[]> rows = new ArrayList<>();
            double offset = 0;
            for (String line : lines) {
                if (line.startsWith("# ") && line.contains("stopped early")) complete = false;
                if (line.startsWith("# kind=")) {
                    for (String part : line.substring(2).trim().split("\\s+")) {
                        if (part.startsWith("kind=")) kind = part.substring(5);
                        if (part.startsWith("motor=")) motor = part.substring(6);
                    }
                    continue;
                }
                if (line.startsWith("#") || line.startsWith("t,") || line.trim().isEmpty()) continue;
                String[] f = line.split(",");
                double[] r = new double[f.length];
                for (int i = 0; i < f.length; i++) r[i] = Double.parseDouble(f[i]);
                rows.add(r);
            }
            if (kind == null || !complete) {
                report.add("skipped " + p.getFileName() + (kind == null ? " (not a Characterizer log)" : " (stopped early)"));
                continue;
            }
            String key = kind + (motor != null ? ":" + motor : "");
            List<double[]> all = groups.computeIfAbsent(key, k -> new ArrayList<>());
            // one log after another on one clock, with a gap so the moments do not mix
            if (!all.isEmpty()) offset = all.get(all.size() - 1)[0] + 5;
            for (double[] r : rows) {
                double[] c = r.clone();
                c[0] += offset;
                all.add(c);
            }
            report.add("read " + p.getFileName() + ": " + key + ", " + rows.size() + " moments");
        }
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<double[]>> g : groups.entrySet()) {
            List<double[]> rows = g.getValue();
            int n = rows.size();
            double[] t = new double[n], volts = new double[n], power = new double[n], vel = new double[n], turnVel = new double[n], heading = new double[n];
            for (int i = 0; i < n; i++) {
                double[] r = rows.get(i);
                t[i] = r[0]; volts[i] = r[1]; power[i] = r[2]; vel[i] = r[4]; turnVel[i] = r[5]; heading[i] = r[6];
            }
            String kind = g.getKey().contains(":") ? g.getKey().substring(0, g.getKey().indexOf(':')) : g.getKey();
            String motor = g.getKey().contains(":") ? g.getKey().substring(g.getKey().indexOf(':') + 1) : null;
            CharacterizeMath.Fit f = CharacterizeMath.fit(t, volts, power, vel, kind.equals("lift"));
            if (!f.ok()) {
                report.add(g.getKey() + ": could not fit (r2 " + num(f.r2, 2) + ", " + f.samples + " moments)");
                continue;
            }
            report.add(String.format(Locale.US, "%s: kS %.4f, kV %.6g, kA %.6g%s, r2 %.4f, %d moments", g.getKey(), f.kS, f.kV, f.kA,
                    kind.equals("lift") ? String.format(Locale.US, ", kG %.4f", f.kG) : "", f.r2, f.samples));
            Map<String, String> s = new LinkedHashMap<>();
            switch (kind) {
                case "drive":
                    s.put("drive.kS", num(f.kS, 4));
                    s.put("drive.maxTicksPerSec", num(f.ticksPerSecPerPower(), 0));
                    s.put("drive.accelSec", num(f.accelSec(), 4));
                    out.put("", merge(out.get(""), s));
                    break;
                case "turn": {
                    double d = CharacterizeMath.turnDegPerCount(turnVel, CharacterizeMath.yawRates(t, heading));
                    if (!Double.isNaN(d)) s.put("drive.turnDegPerCount", num(d, 6));
                    out.put("", merge(out.get(""), s));
                    break;
                }
                case "lift":
                    s.put("kS", num(f.kS, 4));
                    s.put("maxTicksPerSec", num(f.ticksPerSecPerPower(), 0));
                    s.put("accelSec", num(f.accelSec(), 4));
                    s.put("gravity", num(f.kG, 4));
                    out.put("motor." + motor, s);
                    break;
                default: {
                    s.put("kS", num(f.kS, 4));
                    s.put("maxTicksPerSec", num(f.ticksPerSecPerPower(), 0));
                    s.put("accelSec", num(f.accelSec(), 4));
                    double coast = CharacterizeMath.coastSec(t, power, vel);
                    if (!Double.isNaN(coast)) s.put("coastSec", num(coast, 3));
                    out.put("motor." + motor, s);
                }
            }
        }
        return out;
    }

    private static Map<String, String> merge(Map<String, String> a, Map<String, String> b) {
        Map<String, String> m = new LinkedHashMap<>();
        if (a != null) m.putAll(a);
        m.putAll(b);
        return m;
    }

    private static String num(double v, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    /**
     * Puts the settings into the robot file's text: "drive.x = v" lines are replaced (or added at the end),
     * and "motor.NAME = ..." lines get each key=value replaced or added. Notes after # stay.
     */
    static String apply(String text, Map<String, Map<String, String>> settings) {
        List<String> lines = new ArrayList<>(java.util.Arrays.asList(text.split("\n", -1)));
        Map<String, String> whole = settings.getOrDefault("", new LinkedHashMap<>());
        for (Map.Entry<String, String> e : whole.entrySet()) {
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).matches("\\s*" + java.util.regex.Pattern.quote(e.getKey()) + "\\s*[=:].*")) {
                    lines.set(i, e.getKey() + " = " + e.getValue() + "   # measured (Characterizer)");
                    found = true;
                }
            }
            if (!found) {
                int at = lines.size();
                while (at > 0 && lines.get(at - 1).isEmpty()) at--;
                lines.add(at, e.getKey() + " = " + e.getValue() + "   # measured (Characterizer)");
            }
        }
        for (Map.Entry<String, Map<String, String>> m : settings.entrySet()) {
            if (m.getKey().isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.matches("\\s*" + java.util.regex.Pattern.quote(m.getKey()) + "\\s*[=:].*")) continue;
                found = true;
                // split off a note (a # after a space)
                int hash = -1;
                for (int k = 1; k < line.length() && hash < 0; k++) if (line.charAt(k) == '#' && Character.isWhitespace(line.charAt(k - 1))) hash = k;
                String body = hash >= 0 ? line.substring(0, hash).trim() : line.trim(), note = hash >= 0 ? "   " + line.substring(hash) : "";
                for (Map.Entry<String, String> kv : m.getValue().entrySet()) {
                    java.util.regex.Matcher x = java.util.regex.Pattern.compile("(,\\s*)" + kv.getKey() + "\\s*=\\s*[^,]*").matcher(body);
                    if (x.find()) body = body.substring(0, x.start()) + x.group(1) + kv.getKey() + "=" + kv.getValue() + body.substring(x.end());
                    else body = body + ", " + kv.getKey() + "=" + kv.getValue();
                }
                lines.set(i, body + note);
            }
            if (!found) {
                lines.add(m.getKey() + " = free, " + String.join(", ", m.getValue().entrySet().stream()
                        .map(kv -> kv.getKey() + "=" + kv.getValue()).toArray(String[]::new)) + "   # measured (Characterizer): check the kind");
            }
        }
        return String.join("\n", lines);
    }

    public static void main(String[] args) throws IOException {
        String robot = null, log = null;
        boolean write = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--robot")) robot = args[++i];
            else if (args[i].equals("--log")) log = args[++i];
            else if (args[i].equals("--write")) write = true;
        }
        if (log == null) throw new IllegalArgumentException("--log file-or-folder");
        File f = new File(log);
        List<Path> logs = new ArrayList<>();
        if (f.isDirectory()) {
            File[] list = f.listFiles((d, n) -> n.endsWith(".csv"));
            if (list != null) for (File x : list) logs.add(x.toPath());
            java.util.Collections.sort(logs);
        } else if (f.isFile()) {
            logs.add(f.toPath());
        }
        if (logs.isEmpty()) {
            System.out.println("No Characterizer logs in " + f.getAbsolutePath() + ". Copy them from the robot's /sdcard/FIRST/ftc-helper/characterizer.");
            System.exit(1);
        }
        List<String> report = new ArrayList<>();
        Map<String, Map<String, String>> settings = fit(logs, report);
        for (String r : report) System.out.println(r);
        System.out.println();
        System.out.println("Robot file settings:");
        for (Map.Entry<String, Map<String, String>> e : settings.entrySet()) {
            if (e.getKey().isEmpty()) for (Map.Entry<String, String> kv : e.getValue().entrySet()) System.out.println("  " + kv.getKey() + " = " + kv.getValue());
            else System.out.println("  " + e.getKey() + ": " + e.getValue().toString().replaceAll("[{}]", ""));
        }
        if (write && robot != null) {
            Path p = Paths.get(robot);
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Files.write(p, apply(text, settings).getBytes(StandardCharsets.UTF_8));
            System.out.println("\nWrote them into " + p.toAbsolutePath() + " (check it, then commit).");
        } else if (!write) {
            System.out.println("\nAdd -Pwrite to put them into the robot file" + (robot != null ? " (" + robot + ")" : "") + ".");
        }
    }
}
