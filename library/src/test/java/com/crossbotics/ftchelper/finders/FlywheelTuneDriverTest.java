package com.crossbotics.ftchelper.finders;

import static org.junit.Assert.assertEquals;

import com.crossbotics.ftchelper.FakeMotor;
import com.crossbotics.ftchelper.finders.FlywheelTuneSession.Output;
import com.crossbotics.ftchelper.launcher.Flywheel;
import com.crossbotics.ftchelper.launcher.FlywheelTestAccess;
import com.qualcomm.robotcore.hardware.DcMotor;
import org.junit.Test;

/** The Flywheel Tuner's driver on a wheel with two motors: a plain power reaches both, and so does "off". */
public class FlywheelTuneDriverTest {

    @Test public void fullPowerAndTheSteadyPowersGoToTheFollowerToo() {
        FakeMotor main = new FakeMotor(), second = new FakeMotor();
        Flywheel f = new Flywheel("launcher", main.motor).countsPerRev(28).maxRpm(6000);
        FlywheelTestAccess.attachFollower(f, second.motor);
        FlywheelTuneDriver d = new FlywheelTuneDriver(f);
        assertEquals(2, d.motorCount());
        d.apply(new Output(Output.Kind.POWER, 1, 0, null, false));
        assertEquals(1, main.power, 0);
        assertEquals("the follower gets full power too", 1, second.power, 0);
        assertEquals(DcMotor.RunMode.RUN_WITHOUT_ENCODER, second.mode);
        assertEquals(DcMotor.ZeroPowerBehavior.FLOAT, second.zeroPower);
        d.apply(new Output(Output.Kind.POWER, 0.42, 0, null, false));
        assertEquals(0.42, second.power, 0);
        d.apply(Output.OFF);
        assertEquals(0, main.power, 0);
        assertEquals("off reaches the follower", 0, second.power, 0);
    }
}
