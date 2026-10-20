package io.github.jasoncross.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Parts.java is copied from the simulator's parts catalog: they must not drift apart. */
public class PartsCatalogTest {

    private static String catalog() throws Exception {
        // Unit tests run in the library folder; the catalog is in the simulator's folder next to it.
        File f = new File("../sim/parts/parts.json");
        assertTrue("no " + f.getAbsolutePath(), f.isFile());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void everyWheelMatchesThePartsCatalog() throws Exception {
        String json = catalog();
        String wheels = json.substring(json.indexOf("\"wheels\""));
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"(.*?)\\}", Pattern.DOTALL).matcher(wheels);
        int seen = 0;
        while (m.find()) {
            Parts.Wheel w = Parts.wheel(m.group(1));
            assertEquals(w.id + " diameter", number(m.group(2), "diameterMm"), w.diameterMm, 1e-9);
            Matcher width = Pattern.compile("\"widthMm\"\\s*:\\s*(null|[-0-9.eE]+)").matcher(m.group(2));
            assertTrue(w.id + " widthMm missing", width.find());
            if (width.group(1).equals("null")) assertTrue(w.id + " has no width in the catalog", !w.hasWidth());
            else assertEquals(w.id + " width", Double.parseDouble(width.group(1)), w.widthMm, 1e-9);
            seen++;
        }
        assertEquals("every catalog wheel is in Parts.java", seen, Parts.wheels().size());
    }

    @Test public void everyServoIdMatchesThePartsCatalog() throws Exception {
        String json = catalog();
        String servos = json.substring(json.indexOf("\"servos\""), json.indexOf("\"wheels\""));
        // Only the servo objects' own ids (the byVoltage rows have none).
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(servos);
        List<String> ids = new ArrayList<>();
        while (m.find()) ids.add(m.group(1));
        assertEquals(ids, new ArrayList<>(Parts.servos()));
    }

    @Test public void aTypoListsTheKnownParts() {
        try {
            Parts.wheel("offset-tubing-80");
            throw new AssertionError("expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("offset-tubing-90"));
        }
    }

    private static double number(String text, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([-0-9.eE]+)").matcher(text);
        assertTrue(key + " missing", m.find());
        return Double.parseDouble(m.group(1));
    }
}
