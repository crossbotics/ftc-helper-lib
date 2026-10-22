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
        boolean lan = false;
        int port = 8780;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--lan")) lan = true;
            else if (args[i].equals("--port") && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else robotFile = Paths.get(args[i]);
        }
        SimEngine sim = new SimEngine(SimRobot.load(robotFile));
        sim.start();
        SimServer server = new SimServer(sim, port, lan, robotFile);
        System.out.println();
        System.out.println("FTC simulator running with robot file " + robotFile);
        System.out.println("Open http://localhost:" + server.port() + " in a browser. Ctrl+C stops it.");
        if (lan) System.out.println("Phones on the same WiFi: http://<this computer's address>:" + server.port() + "/?phone=2");
        System.out.println(sim.ds.opModes.size() + " OpModes found.");
        Runtime.getRuntime().addShutdownHook(new Thread(sim::shutdown));
        server.serve();
    }
}
