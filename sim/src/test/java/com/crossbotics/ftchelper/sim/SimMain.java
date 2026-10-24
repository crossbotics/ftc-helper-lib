package com.crossbotics.ftchelper.sim;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the simulator: ./gradlew :sim:run   then open http://localhost:8780
 * Arguments: [robot file] [--lan] [--port N]. Stop it with Ctrl+C.
 */
public final class SimMain {
    private SimMain() { }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (NumberFormatException e) {
            // a number that is not one, somewhere: the message alone would not say where, so the trace too
            System.err.println();
            System.err.println("PROBLEM: a number was expected (" + e.getMessage() + "). Check the robot file's numbers and the scenario's lines; where:");
            e.printStackTrace();
            System.exit(2);
        } catch (IllegalArgumentException e) {
            // A mistake in a robot file, field file or command: its message names it; no Java trace.
            System.err.println();
            System.err.println("PROBLEM: " + e.getMessage());
            System.exit(2);
        } catch (java.nio.file.NoSuchFileException e) {
            System.err.println();
            System.err.println("PROBLEM: no such file: " + e.getMessage() + " (paths are from the ftc-helper-lib folder)");
            System.exit(2);
        }
    }

    private static void run(String[] args) throws Exception {
        Path robotFile = Paths.get("sim/robots/starter.properties");
        boolean lan = false, open = false;
        int port = 8780;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--lan")) lan = true;
            else if (args[i].equals("--open")) open = true;
            else if (args[i].equals("--port") && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else robotFile = Paths.get(args[i]);
        }
        robotFile = SimFromRobot.auto(robotFile);
        SimEngine sim = new SimEngine(SimRobot.load(robotFile));
        sim.rememberSpots = true;   // a words-only autonomous starts where the team placed it last time
        sim.start();
        SimServer server = new SimServer(sim, port, lan, robotFile);
        System.out.println();
        System.out.println("FTC simulator running with robot file " + robotFile);
        System.out.println("Open http://localhost:" + server.port() + " in a browser. Ctrl+C stops it.");
        if (lan) System.out.println("Phones on the same WiFi: http://<this computer's address>:" + server.port() + "/?phone=2");
        System.out.println(sim.ds.opModes.size() + " OpModes found.");
        if (open) openBrowser("http://localhost:" + server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(sim::shutdown));
        server.serve();
    }

    /**
     * Opens the page in the computer's own browser (--open: the Simulator button in Android Studio). The
     * server is already listening, so the page loads. If no browser opens, it says so: the address above
     * still works (in Android Studio's Run window it is a link).
     */
    static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[] {"open", url}
                : new String[] {"xdg-open", url};   // Linux and Chromebook
        try {
            java.io.File nowhere = new java.io.File(os.contains("win") ? "NUL" : "/dev/null");   // the browser's own messages
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(nowhere)).start();
            System.out.println("Opening the page in your browser.");
        } catch (java.io.IOException | RuntimeException e) {
            System.out.println("Could not open a browser (" + e.getMessage() + "): open " + url + " yourself.");
        }
    }
}
