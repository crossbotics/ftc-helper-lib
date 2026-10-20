package io.github.jasoncross.ftchelper.robot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;

/** Robot.java's parts and shapes become the simulator robot file's lines. */
public class ShapesTest {
    /** The numbers these test robots read. */
    public static class Numbers {
        public static class IntakeDeploy { public static double stowed = 0.2, deployed = 0.7; }
    }

    // A folding intake: a servo arm, rollers riding on it, a flywheel and a fixed ramp.
    static class FoldBot extends RobotDefinition {
        Positioner deploy;
        Intake intake;

        @Override protected void define() {
            tunables(Numbers.class);
            drive(Drive.tank("l", "r").wheel("gobilda-hogback-96").wheelsAtMm(402, 288));
            chassisMm(384, 426, 97);
            heightMm(305);
            deploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-speed")
                    .looks(Shape.arm().atMm(127, 0, 101.6).sizeMm(127, 254, 25.4).angle("stowed", 90).angle("deployed", -10)));
            intake = add(Intake.motor("intake").part("gobilda-5203-117").holds(2)
                    .looks(Shape.roller("offset-tubing-90", "offset-tubing-60").on(deploy).atMm(127, 0, 0).lengthMm(254),
                           Shape.arm().name("intake_arm").atMm(127, 0, 101.6).sizeMm(127, 254, 25.4)
                                   .range(-10, 95).startAt(95).dropsWhen(deploy, "deployed", -10),
                           Shape.roller("offset-tubing-90").name("left_spinner").on(deploy).atMm(101.6, 127, 0).axis("up").lengthMm(25.4)));
            add(Launcher.flywheel("launcher", "gobilda-5203-6000").feeder(Indexer.crServo("windmill").part("gobilda-2000-torque"))
                    .looks(Shape.slide().name("launcher_slide").atMm(-40, 0, 168).sizeMm(20, 20, 20).passive().travelMm(25),
                           Shape.flywheel("gobilda-hogback-96").on("launcher_slide").atMm(0, 0, 0))
                    .shotAngle(147));
            fixed(Shape.box("ramp").atMm(-150, 0, 200).sizeMm(100, 150, 10).note("est."));
        }
    }

    private static String lines(RobotDefinition r) {
        return String.join("\n", r.simLines());
    }

    @Test public void partsAndShapesBecomeRobotFileLines() {
        String all = lines(new FoldBot());
        assertTrue(all, all.contains("robot.lengthIn = 15.12"));
        assertTrue(all, all.contains("robot.chassisHeightIn = 3.82"));
        assertTrue(all, all.contains("robot.heightIn = 12.01"));
        assertTrue(all, all.contains("robot.tallestIn = 12.01"));
        assertTrue(all, all.contains("drive.wheelDiameterIn = 3.780   # gobilda-hogback-96"));
        assertTrue(all, all.contains("drive.trackWidthIn = 15.83"));
        assertTrue(all, all.contains("drive.wheelBaseIn = 11.34"));
        // The servo: its part, no TODO; the arm's angles at servo 0 and 1 from the two named angles
        // (0.2 -> 90 and 0.7 -> -10: 200 degrees per unit, so 130 at 0 and -70 at 1).
        assertTrue(all, all.contains("servo.intake_deploy = 0.20, part=gobilda-2000-speed\n"));
        assertTrue(all, all.contains("shape.intake_deploy = arm, at=5,0,4, size=5x10x1, servo=intake_deploy, pos0=130, pos1=-70"));
        // The rollers ride on the arm; the biggest roller sets the diameter; the motor spins them.
        assertTrue(all, all.contains("motor.intake = free, part=gobilda-5203-117\n"));
        assertTrue(all, all.contains("shape.intake = roller, on=intake_deploy, at=5,0,0, size=3.54x10x3.54, motor=intake"
                + "   # offset-tubing-90 + offset-tubing-60"));
        // A passive arm, pushed over by the deploy servo at three quarters of the way (0.2 -> 0.7: 0.575).
        assertTrue(all, all.contains("shape.intake_arm = arm, at=5,0,4, size=5x10x1, start=95, minDeg=-10, maxDeg=95, passive=yes, "
                + "drop=intake_deploy>0.575, fallsTo=-10"));
        assertTrue(all, all.contains("shape.launcher_slide = slide, at=-1.57,0,6.61, size=0.79x0.79x0.79, travel=0.98, passive=yes"));
        assertTrue(all, all.contains("shape.left_spinner = roller, on=intake_deploy, at=4,5,0, size=3.54x3.54x1, axis=up, motor=intake"));
        assertTrue(all, all.contains("collect.intake = shape=intake+left_spinner, motor=intake, holds=2\n"));
        // The flywheel's width comes from the catalog; the shot angle from the code.
        assertTrue(all, all.contains("shape.launcher = flywheel, on=launcher_slide, at=0,0,0, size=3.78x0.94x3.78, motor=launcher"));
        assertTrue(all, all.contains("shoot.launcher = shape=launcher, angle=147, share=0.5"));
        assertTrue(all, all.contains("crservo.windmill =   # gobilda-2000-torque"));
        assertTrue(all, all.contains("shape.ramp = box, at=-5.91,0,7.87, size=3.94x5.91x0.39   # est."));
    }

    @Test public void withoutShapesTheGuessesStayMarkedTodo() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake"));
            }
        };
        String all = lines(r);
        assertTrue(all, all.contains("shape.intake = roller, at=8,0,2, size=2x14x2, motor=intake   # TODO"));
        assertTrue(all, all.contains("holds=3   # TODO"));
        assertTrue(all, !all.contains("robot.lengthIn"));
    }

    @Test public void aRollerWithNoKnownWidthNeedsItsLength() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.roller("offset-tubing-90")));
            }
        };
        try {
            r.simLines();
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("lengthMm"));
        }
    }

    @Test public void anArmNeedsTwoNamedAnglesThatExist() {
        RobotDefinition one = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(Positioner.servo("intake_deploy").looks(Shape.arm().sizeMm(100, 100, 10).angle("stowed", 90)));
            }
        };
        RobotDefinition wrong = new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                add(Positioner.servo("intake_deploy").looks(Shape.arm().sizeMm(100, 100, 10).angle("stowed", 90).angle("down", 0)));
            }
        };
        expect(one, "two positions");
        expect(wrong, "no position \"down\"");
    }

    @Test public void typosAndUnsupportedPartsStopWithAClearMessage() {
        expectArg(() -> Shape.roller("offset-tubing-80"), "offset-tubing-90");
        expectArg(() -> Positioner.servo("s").part("gobilda-2000-fast"), "gobilda-2000-speed");
        expectArg(() -> Intake.motor("i").part("gobilda-5203-118"), "gobilda-5203-117");
        expectArg(() -> Shape.arm().axis("sideways"), "axis");
        try {
            Lift.slide("lift").looks(Shape.box("x"));
            fail("expected an error");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cannot take shapes"));
        }
    }

    @Test public void numbersAreShort() {
        assertEquals("3.5", Shape.n(3.5));
        assertEquals("0", Shape.n(-0.001));
        assertEquals("-70", Shape.n(-70.0));
        assertEquals("15.12", Shape.n(384 / 25.4));
    }

    private static void expect(RobotDefinition r, String words) {
        try {
            List<String> l = r.simLines();
            fail("expected an error, got " + l);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }

    private static void expectArg(Runnable r, String words) {
        try {
            r.run();
            fail("expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }

    public static class TinyMove {
        public static class IntakeDeploy { public static double stowed = 0.50, deployed = 0.51; }
    }

    @Test public void aSmallServoMoveKeepsItsThreshold() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(TinyMove.class);
                drive(Drive.tank("l", "r"));
                Positioner d = add(Positioner.servo("intake_deploy"));
                fixed(Shape.arm().name("flap").atMm(0, 0, 50).sizeMm(50, 50, 10).passive()
                        .range(0, 90).startAt(90).dropsWhen(d, "deployed", 0));
            }
        };
        String all = lines(r);
        assertTrue(all, all.contains("drop=intake_deploy>0.5075"));   // the servo stops at 0.51: past it
    }

    @Test public void aCrServoIntakeCollectsToo() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.crServos("roller").holds(2).looks(Shape.roller("gobilda-gecko-72").atMm(200, 0, 40).lengthMm(300)));
            }
        };
        String all = lines(r);
        assertTrue(all, all.contains("collect.roller = shape=roller, crservo=roller, holds=2"));
    }

    @Test public void twoPartsWithOneNameStop() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.roller("gobilda-gecko-72").atMm(200, 0, 40).lengthMm(300)));
                fixed(Shape.box("intake").atMm(0, 0, 0).sizeMm(600, 600, 600));
            }
        };
        try {
            r.simLines();
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("\"shape.intake\""));
        }
    }

    // Each robot below has one mistake; simLines() must stop and name it.
    private static void stops(String words, RobotDefinition r) {
        try {
            r.simLines();
            fail("expected an error with: " + words);
        } catch (IllegalStateException | IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(words));
        }
    }

    @Test public void settingsThatDoNotFitTheShapeStop() {
        stops(".angle(...) is for an arm moved by a servo part", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                fixed(Shape.arm().name("flap").atMm(0, 0, 50).sizeMm(50, 50, 10).passive().range(0, 90).angle("up", 90));
            }
        });
        stops(".travelMm(...) is for a passive slide only", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                fixed(Shape.box("ramp").atMm(0, 0, 0).sizeMm(10, 10, 10).travelMm(20));
            }
        });
        stops(".lengthMm/.diameterMm are for a roller or flywheel", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                fixed(Shape.box("ramp").atMm(0, 0, 0).sizeMm(10, 10, 10).lengthMm(20));
            }
        });
    }

    @Test public void ridingOnAPartWithNoShapeStops() {
        stops("rides on IntakeDeploy, but IntakeDeploy has no looks(...)", new RobotDefinition() {
            @Override protected void define() {
                tunables(Numbers.class);
                drive(Drive.tank("l", "r"));
                Positioner d = add(Positioner.servo("intake_deploy"));
                add(Intake.motor("intake").looks(Shape.roller("gobilda-gecko-72").on(d).atMm(0, 0, 0).lengthMm(200)));
            }
        });
    }

    @Test public void anIntakeNeedsARoller() {
        stops("needs a roller", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.arm().atMm(0, 0, 50).sizeMm(50, 50, 10).passive().range(0, 90)));
            }
        });
    }

    @Test public void oneShapeForTwoPartsStops() {
        try {
            Shape s = Shape.roller("gobilda-gecko-72").atMm(0, 0, 0).lengthMm(200);
            Intake.motor("a").looks(s);
            Intake.motor("b").looks(s);
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("make a new Shape for each part"));
        }
    }

    @Test public void aShotAngleOutsideZeroTo180Stops() {
        try {
            Launcher.flywheel("launcher", "gobilda-5203-6000").shotAngle(1470);
            fail("expected an error");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("0 to 180"));
        }
    }

    @Test public void aFlywheelNotInTheCatalogTakesItsSize() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                        .looks(Shape.flywheel().atMm(0, 0, 150).diameterMm(101.6).lengthMm(25.4)).shotAngle(60));
            }
        };
        assertTrue(lines(r), lines(r).contains("shape.launcher = flywheel, at=0,0,5.91, size=4x1x4, motor=launcher"));
    }

    @Test public void aReversedRollerSaysSo() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.roller("gobilda-gecko-72").atMm(200, 0, 40).lengthMm(300),
                        Shape.roller("gobilda-gecko-72").name("left_spinner").atMm(200, 150, 40).axis("up").lengthMm(24).reversed()));
            }
        };
        assertTrue(lines(r), lines(r).contains("axis=up, motor=intake, reversed=yes"));
    }

    @Test public void aTiltedPartSaysSo() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.roller("gobilda-gecko-72").atMm(200, 0, 40).lengthMm(300).tiltDeg(40)));
            }
        };
        assertTrue(lines(r), lines(r).contains(", tilt=40"));
    }
}
