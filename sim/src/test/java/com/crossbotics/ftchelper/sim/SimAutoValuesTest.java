package com.crossbotics.ftchelper.sim;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** Load an existing auto: the numbers behind the names in its steps, from the compiled code. */
public class SimAutoValuesTest {
    @Test public void namesConstantsAndFormulasAreWorkedOut() {
        String json = SimAutoValues.json(com.crossbotics.ftchelper.sim.autovals.autos.NamedAuto.class, Arrays.asList(
                "START_X", "Tunables.Auto.offWallInches", "Tunables.Auto.parkSeconds", "turnToField(PARK_HEADING)",
                "Tunables.Auto.offWallInches * 2 + 1", "-START_X", "Tunables.Auto.nothing", "System.exit(0)", "Math.max(1, 2)"));
        assertTrue(json, json.contains("\"START_X\":52.0"));
        assertTrue(json, json.contains("\"Tunables.Auto.offWallInches\":4.0"));
        assertTrue(json, json.contains("\"Tunables.Auto.parkSeconds\":6.0"));
        assertTrue(json, json.contains("\"turnToField(PARK_HEADING)\":142.2"));
        assertTrue(json, json.contains("\"Tunables.Auto.offWallInches * 2 + 1\":9.0"));
        assertTrue(json, json.contains("\"-START_X\":-52.0"));
        // not a number in the code, or not the auto's own method: nothing is called, null
        assertTrue(json, json.contains("\"Tunables.Auto.nothing\":null"));
        assertTrue(json, json.contains("\"System.exit(0)\":null"));
        assertTrue(json, json.contains("\"Math.max(1, 2)\":null"));
    }
}
