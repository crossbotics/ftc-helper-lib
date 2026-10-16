package io.github.jasoncross.ftchelper.sim;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the simulator: ./gradlew :sim:run   then open http://localhost:8780
 * Arguments: [robot file] [--lan] [--port N]. Stop it with Ctrl+C.
 */
public final class SimMain {
    private SimMain() { }

    public static void main(String[] args) throws Exception {
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
        SimServer server = new SimServer(sim, port, lan);
        System.out.println();
        System.out.println("FTC simulator running with robot file " + robotFile);
        System.out.println("Open http://localhost:" + server.port() + " in a browser. Ctrl+C stops it.");
        if (lan) System.out.println("Phones on the same WiFi: http://<this computer's address>:" + server.port() + "/?phone=2");
        System.out.println(sim.ds.opModes.size() + " OpModes found.");
        Runtime.getRuntime().addShutdownHook(new Thread(sim::shutdown));
        server.serve();
    }
}
