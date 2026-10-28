package com.crossbotics.ftchelper.robot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A mode that may only turn on when something is true (whole-robot aim: only with a goal in view). */
public class ModeGuardTest {
    @Test public void aRefusedPressLeavesItOffAndSaysWhy() {
        boolean[] goal = {false};
        Mode m = new Mode("Aim the robot", false);
        m.onlyWhen(() -> goal[0], "no goal in view");
        m.toggle();
        assertFalse("no goal: stays off (one buzz)", m.isOn());
        assertTrue(m.wasRefused());
        assertTrue(m.whyRefused().contains("no goal"));
        goal[0] = true;
        m.toggle();
        assertTrue("goal in view: on (two buzzes)", m.isOn());
        assertFalse(m.wasRefused());
        goal[0] = false;
        assertTrue("once on it stays on", m.isOn());
        m.toggle();
        assertFalse(m.isOn());
    }
}
