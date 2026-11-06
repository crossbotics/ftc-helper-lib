package com.crossbotics.ftchelper.gamepad;

import static org.junit.Assert.assertEquals;

import com.qualcomm.robotcore.hardware.Gamepad;
import org.junit.After;
import org.junit.Test;

/** The screens name the buttons printed on each driver's controller. */
public class ButtonNamesTest {
    @After public void forget() {
        ButtonNames.watch(null, null);
        ButtonNames.force(1, null);
        ButtonNames.force(2, null);
    }

    private static Gamepad pad(Gamepad.Type type) {
        Gamepad g = new Gamepad();
        g.type = type;
        return g;
    }

    @Test public void lettersOnALogitechAndBeforeAControllerIsLinked() {
        assertEquals("A = start, B = stop", ButtonNames.say("{A} = start, {B} = stop"));
        ButtonNames.watch(pad(Gamepad.Type.LOGITECH_F310), pad(Gamepad.Type.UNKNOWN));
        assertEquals("hold START 1 s, LB, RT, GUIDE", ButtonNames.say("hold {START} 1 s, {LB}, {RT}, {GUIDE}"));
        assertEquals("G2 A: fire", ButtonNames.say("G2 A: fire"));
    }

    @Test public void psNamesOnAPsController() {
        ButtonNames.watch(pad(Gamepad.Type.SONY_PS4), null);
        assertEquals("× = start, ○ = stop, □, △", ButtonNames.say("{A} = start, {B} = stop, {X}, {Y}"));
        assertEquals("L1 R1 L2 R2 SHARE OPTIONS PS", ButtonNames.say("{LB} {RB} {LT} {RT} {BACK} {START} {GUIDE}"));
        assertEquals("G1 ×: intake; G1 free: R2, △", ButtonNames.say("G1 A: intake; G1 free: {RT}, {Y}"));
        assertEquals("the kernel's PS4 too", ButtonNames.Style.PLAYSTATION,
                ButtonNames.styleOf(pad(Gamepad.Type.SONY_PS4_SUPPORTED_BY_KERNEL)));
    }

    @Test public void eachGamepadHasItsOwnKind() {
        ButtonNames.watch(pad(Gamepad.Type.LOGITECH_F310), pad(Gamepad.Type.SONY_PS4));
        assertEquals("A then ×", ButtonNames.say("{A} then {2:A}"));
        assertEquals("G1 RT, G2 R2, G2:L1", ButtonNames.say("G1 RT, G2 RT, G2:LB"));
        assertEquals("{2:RT}", ButtonNames.mark(2, "RT"));
        assertEquals("D-pad up", ButtonNames.mark(1, "D-pad up"));
    }

    @Test public void theDriverHubsStartIsNeverAGamepadButton() {
        ButtonNames.watch(pad(Gamepad.Type.SONY_PS4), null);
        assertEquals("Press ▶, then hold OPTIONS", ButtonNames.say("Press {PLAY}, then hold {START}"));
    }

    @Test public void otherTextStaysAsItIs() {
        ButtonNames.watch(pad(Gamepad.Type.SONY_PS4), null);
        assertEquals("A charged battery. Stop A. X pod. {x}. G1 Axis", ButtonNames.say("A charged battery. Stop A. X pod. {x}. G1 Axis"));
        assertEquals(null, ButtonNames.say(null));
    }
}
