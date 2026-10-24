package com.crossbotics.ftchelper.robot;

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
        // a 2 in roller across the front of the guessed 18 in chassis, low: it takes in pieces the robot drives onto
        assertTrue(all, all.contains("shape.intake = roller, at=8,0,2, size=2x14x2, motor=intake   # TODO measure: place and size"));
        assertTrue(all, all.contains("holds=4   # TODO how many it holds"));
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

    // A lift draws its slide (counts per inch from travelMm and its range) with a wrist and a claw on it.
    @Test public void aLiftCarriesAWristAndAClaw() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                tunables(LifterNumbers.class);
                drive(Drive.mecanum("fl", "fr", "bl", "br"));
                Lift lift = add(Lift.slide("lift").looks(Shape.slide().atMm(-188, 188, 12).sizeMm(27, 27, 288).travelMm(254)));
                Wrist wrist = add(Wrist.servo("wrist").looks(Shape.arm().on(lift).atMm(0, -188, 142).sizeMm(115, 30, 20)
                        .angle("folded", -90).angle("flower", -210)));
                add(Claw.servo("claw").looks(Shape.claw().on(wrist).atMm(115, 0, 0).sizeMm(60, 120, 50)));
            }
        };
        String all = String.join("\n", r.simLines());
        assertTrue(all, all.contains("shape.lift = slide, at="));
        assertTrue(all, all.contains("motor=lift, ticksPerIn=100.0"));          // 1000 counts over 10 in
        assertTrue(all, all.contains("shape.wrist = arm, on=lift"));
        assertTrue(all, all.contains("shape.claw = claw, on=wrist"));
        assertTrue(all, all.contains("grab.claw = shape=claw"));
        try {
            new RobotDefinition() {
                @Override protected void define() {
                    tunables(LifterNumbers.class);
                    drive(Drive.mecanum("fl", "fr", "bl", "br"));
                    add(Lift.slide("lift").looks(Shape.slide().atMm(0, 0, 0)));
                }
            }.simLines();
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs .travelMm"));
        }
    }

    public static class LifterNumbers {
        public static class Lift { public static double min = 0, max = 1000; }
        public static class Wrist { public static double folded = 0.1, flower = 0.6; }
        public static class Claw { public static double open = 0.5, closed = 0.3; }
    }

    @Test public void typosAndUnsupportedPartsStopWithAClearMessage() {
        expectArg(() -> Shape.roller("offset-tubing-80"), "offset-tubing-90");
        expectArg(() -> Positioner.servo("s").part("gobilda-2000-fast"), "gobilda-2000-speed");
        expectArg(() -> Intake.motor("i").part("gobilda-5203-118"), "gobilda-5203-117");
        expectArg(() -> Shape.arm().axis("sideways"), "axis");
        try {
            Camera.webcam("Webcam 1").looks(Shape.box("x"));
            fail("expected an error");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cannot take shapes"));
        }
    }

    // ----------------------------- the guesses start from what the code knows -----------------------------

    public static class GuessNumbers {
        public static class Lift { public static double min = 0, max = 1100; }
        public static class Arm { public static double min = -20, max = 120; }
    }

    @Test public void guessesSitInsideTheChassisFromChassisMm() {
        String all = lines(new RobotDefinition() {
            @Override protected void define() {
                tunables(GuessNumbers.class);
                drive(Drive.tank("l", "r"));
                chassisMm(400, 300, 80);                       // 15.75 x 11.81 x 3.15 in
                add(Intake.motor("intake").part("gobilda-5203-1150"));
                add(Lift.slide("lift").part("gobilda-5203-435"));
            }
        });
        // The roller's front at the chassis' front (7.87 in), 2 in up; 2 in narrower than the chassis.
        assertTrue(all, all.contains("shape.intake = roller, at=6.87,0,2, size=2x9.81x2, motor=intake   # TODO measure: place and size"));
        // The slide at the back (-7.87 in), on the chassis; as tall as the 18 in cube allows; it travels
        // up to 29 in (29 - 0.25 - 3.15 - 14 = 11.6 in), so 1100 counts over 11.6 in.
        assertTrue(all, all.contains("shape.lift = slide, at=-6.87,0,3.15, size=2x8x14, axis=up, motor=lift, ticksPerIn=94.8   # TODO measure: place, size and travel"));
    }

    @Test public void aClawRidesTheLiftAndTheFlywheelTheTurret() {
        String all = lines(new RobotDefinition() {
            @Override protected void define() {
                tunables(GuessNumbers.class);
                drive(Drive.tank("l", "r"));
                heightMm(400);                                    // 15.75 in: the stage is shorter
                add(Lift.slide("lift"));
                add(Claw.servo("claw"));
                add(Turret.motor("turret", "gobilda-5203-117"));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000"));
            }
        });
        // 15.75 - 4 - 2 (the claw on top) - 0.25 = 9.5 in; travel 29 - 0.25 - 4 - 9.5 - 2 = 13.25 in.
        assertTrue(all, all.contains("shape.lift = slide, at=-8,0,4, size=2x8x9.5, axis=up, motor=lift, ticksPerIn=83.0"));
        assertTrue(all, all.contains("shape.claw = claw, on=lift, at=2,0,9.5, size=2x0.5x2, servo=claw, pos0=0, pos1=3   # TODO measure: place and size (on top of the lift)"));
        assertTrue(all, all.contains("shape.turret = turret, at=0,0,4, size=6x6x2, motor=turret,"));
        // The flywheel: a 96 mm wheel from the catalog, on top of the turret.
        assertTrue(all, all.contains("shape.launcher = flywheel, on=turret, at=0,0,3.89, size=3.78x0.94x3.78, motor=launcher   "
                + "# size from the catalog (gobilda-hogback-96, a common flywheel wheel), TODO measure: place and size (on the turret)"));
    }

    @Test public void aGuessedArmFitsOverItsRange() {
        String all = lines(new RobotDefinition() {
            @Override protected void define() {
                tunables(GuessNumbers.class);
                drive(Drive.tank("l", "r"));
                add(Arm.motor("arm", "gobilda-5203-117"));
                add(Claw.servo("claw"));
            }
        });
        // -20 to 120 degrees, a claw on its end: at -20 the tiles (the hinge 5 in up) keep it to 5 in.
        assertTrue(all, all.contains("shape.arm = arm, at=0,0,5, size=5x1.5x1.5, axis=left, motor=arm, ticksPerDeg=3.959, start=-20   # TODO measure"));
        assertTrue(all, all.contains("shape.claw = claw, on=arm, at=5,0,0,"));
    }

    // ----------------------------- looks(...) on an arm, a turret and a hood -----------------------------

    public static class LooksNumbers {
        public static class Arm { public static double min = 0, max = 150; }
        public static class Turret { public static double min = -90, max = 90; }
        public static class Hood { public static double posA = 0.2, posB = 0.6, angleA = 10, angleB = 50; }
        public static class Launcher { public static double speedRpm = 2600; }
    }

    @Test public void anArmATurretAndAHoodDrawTheirLooks() {
        String all = lines(new RobotDefinition() {
            @Override protected void define() {
                tunables(LooksNumbers.class);
                drive(Drive.tank("l", "r"));
                Turret turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5)
                        .looks(Shape.turret().atMm(-50, 0, 120).sizeMm(160, 160, 40)));
                add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2)
                        .looks(Shape.arm().atMm(100, 0, 150).sizeMm(250, 30, 30)));
                add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                        .hood(com.crossbotics.ftchelper.robot.Hood.servo("hood").part("gobilda-2000-torque")
                                .looks(Shape.arm().on(turret).atMm(0, 0, 100).sizeMm(80, 60, 5)))
                        .looks(Shape.flywheel("gobilda-hogback-96").on(turret).atMm(0, 0, 60)));
            }
        });
        // The turret shape turns with its motor (as the guess did): 1425.1 x 5 / 360 counts a degree, from min.
        assertTrue(all, all.contains("shape.turret = turret, at=-1.97,0,4.72, size=6.3x6.3x1.57, motor=turret, ticksPerDeg=19.793, start=-90"));
        // The arm swings with its motor from min (0 degrees).
        assertTrue(all, all.contains("shape.arm = arm, at=3.94,0,5.91, size=9.84x1.18x1.18, motor=arm, ticksPerDeg=7.917, start=0"));
        // The hood's arm: its degrees from the hood's stops (0.2 -> 10, 0.6 -> 50: 100 a unit, -10 at 0).
        assertTrue(all, all.contains("shape.hood = arm, on=turret, at=0,0,3.94, size=3.15x2.36x0.2, servo=hood, pos0=-10, pos1=90"));
        assertTrue(all, all.contains("shape.launcher = flywheel, on=turret"));
        for (String l : all.split("\n")) if (l.startsWith("shape.")) assertTrue(l, !l.contains("TODO"));
    }

    public static class ServoTurretNumbers {
        public static class Turret { public static double posA = 0.2, angleA = -60, posB = 0.8, angleB = 60; }
    }

    @Test public void aServoTurretDrawsItsLooksAndAWrongKindStops() {
        String all = lines(new RobotDefinition() {
            @Override protected void define() {
                tunables(ServoTurretNumbers.class);
                drive(Drive.tank("l", "r"));
                add(Turret.servo("turret").part("gobilda-2000-torque").looks(Shape.turret().atMm(0, 0, 100).sizeMm(150, 150, 30)));
            }
        });
        // -60 at 0.2, 60 at 0.8: 200 degrees a unit, -100 at 0 and 100 at 1.
        assertTrue(all, all.contains("shape.turret = turret, at=0,0,3.94, size=5.91x5.91x1.18, servo=turret, pos0=-100, pos1=100"));
        stops("it turns a turret shape (Shape.turret()), not a arm", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Turret.motor("turret", "gobilda-5203-117").looks(Shape.arm().atMm(0, 0, 100).sizeMm(150, 150, 30)));
            }
        });
        stops("is an arm: it swings an arm shape", new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Arm.motor("arm", "gobilda-5203-117").looks(Shape.turret().atMm(0, 0, 100).sizeMm(150, 150, 30)));
            }
        });
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
        stops(".travelMm(...) is for a slide only", new RobotDefinition() {
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

    @Test public void aLiftsPartGoesToTheSimulator() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Lift.slide("lift").part("gobilda-5203-435"));
            }
        };
        assertTrue(lines(r), lines(r).contains("motor.lift = lift, part=gobilda-5203-435,"));
    }

    @Test public void aSoftRollerSaysSo() {
        RobotDefinition r = new RobotDefinition() {
            @Override protected void define() {
                drive(Drive.tank("l", "r"));
                add(Intake.motor("intake").looks(Shape.roller("offset-tubing-90").atMm(200, 0, 40).lengthMm(300).soft(),
                        Shape.roller("gobilda-boot-72-rex").name("spin").atMm(200, 160, 20).axis("up").lengthMm(25)));
            }
        };
        assertTrue(lines(r), lines(r).contains(", soft=yes"));
        assertTrue(lines(r), lines(r).contains("size=2.83x2.83x0.98, axis=up"));
    }
}
