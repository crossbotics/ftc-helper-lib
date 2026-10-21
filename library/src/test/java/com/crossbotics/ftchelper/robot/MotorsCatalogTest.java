package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Motors.java is copied from the simulator's parts catalog: they must not drift apart. */
public class MotorsCatalogTest {

    @Test public void everyMotorMatchesThePartsCatalog() throws Exception {
        // Unit tests run in the library folder; the catalog is in the simulator's folder next to it.
        File catalog = new File("../sim/parts/parts.json");
        assertTrue("no " + catalog.getAbsolutePath(), catalog.isFile());
        String json = new String(Files.readAllBytes(catalog.toPath()), StandardCharsets.UTF_8);
        String motors = json.substring(json.indexOf("\"motors\""), json.indexOf("\"servos\""));
        // Each motor object: its id, then (later in the same object) freeRpm and countsPerRev.
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"(.*?)\\}", Pattern.DOTALL).matcher(motors);
        int seen = 0;
        while (m.find()) {
            String id = m.group(1);
            double rpm = number(m.group(2), "freeRpm");
            double counts = number(m.group(2), "countsPerRev");
            Motors.Kind k = Motors.get(id);
            assertEquals(id + " counts", counts, k.countsPerRev, 1e-9);
            assertEquals(id + " rpm", rpm, k.freeRpm, 1e-9);
            seen++;
        }
        assertEquals("every catalog motor is in Motors.java", seen, Motors.all().size());
    }

    private static double number(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([-0-9.eE]+)").matcher(text);
        assertTrue(key + " missing", m.find());
        return Double.parseDouble(m.group(1));
    }
}
