package com.crossbotics.ftchelper.examples.v2bot;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.LogoFacingDirection;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot.UsbFacingDirection;
import com.crossbotics.ftchelper.robot.Camera;
import com.crossbotics.ftchelper.robot.Claw;
import com.crossbotics.ftchelper.robot.Drive;
import com.crossbotics.ftchelper.robot.Hood;
import com.crossbotics.ftchelper.robot.Indexer;
import com.crossbotics.ftchelper.robot.Intake;
import com.crossbotics.ftchelper.robot.Interlock;
import com.crossbotics.ftchelper.robot.Launcher;
import com.crossbotics.ftchelper.robot.Lift;
import com.crossbotics.ftchelper.robot.Lights;
import com.crossbotics.ftchelper.robot.Odometry;
import com.crossbotics.ftchelper.robot.PieceSensor;
import com.crossbotics.ftchelper.robot.Pieces;
import com.crossbotics.ftchelper.robot.RobotDefinition;
import com.crossbotics.ftchelper.robot.Shape;
import com.crossbotics.ftchelper.robot.Turret;
import com.crossbotics.ftchelper.robot.Wrist;

/**
 * The V2 robot, file 1 of 5: a turret shooter AND a lift with a wrist and a claw, with an interlock
 * rule so the turret and the claw never hit each other. It is modelled on team 8938's 2026-27 designs
 * (turret v8.1GB and the "pincer" lifter); every number we have not measured on a built robot says
 * "example".
 *
 * <ul>
 *   <li>A mecanum drive with a goBILDA Pinpoint (odometry): the turret keeps aiming from the robot's
 *       position when the camera loses the goal.</li>
 *   <li>An intake, a color sensor that counts the pieces, and an indexer servo that feeds the launcher.</li>
 *   <li>A turret on a goBILDA Gear-Driven Turret bearing, turned by a 312 RPM motor (2.75 motor turns
 *       per turret turn). It turns -190 to +190 degrees (the "safe way round" picks the side that does
 *       not wind up the wires) and finds its angle on a magnetic home switch at START.</li>
 *   <li>A flywheel launcher with a hood on a servo, speed and hood angle from the distance to the goal.</li>
 *   <li>A lift at the back: a goBILDA Viper-Slide on a 435 RPM motor. On its top stage: a wrist (a servo
 *       that swings the claw out the back, 0 to 145 degrees between hard stops) and a two-finger claw.</li>
 *   <li><b>The interlock:</b> while the wrist is folded or at pick-up (0 to 47 degrees), the claw sits
 *       right behind the robot, inside the turret's swing: the turret stays out of the back. While the
 *       turret points back, the wrist stays out of 0 to 47 degrees. One line below; the numbers are in
 *       Tunables.TurretClaw. docs/guides/interlocks.md explains it.</li>
 * </ul>
 *
 * <p>Docs: the turret's options are on docs/guides/turret-setup.md; the lifter's steps are in Actions.java.
 */
public class Robot extends RobotDefinition {
    public Camera camera;
    public Pieces pieces;
    public Intake intake;
    public Turret turret;
    public Launcher launcher;
    public Lift lift;
    public Wrist wrist;
    public Claw claw;
    public Lights lights;
    public Interlock turretClaw;

    @Override
    protected void define() {
        drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
                .motor("gobilda-5203-312").wheelMm(104)
                .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
        // The Pinpoint and its two pods. Example offsets: measure your own (the Odometry Tuner does it).
        odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));

        // A Limelight 3A on the turret. It knows this season's goal tags for each alliance.
        camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));

        // The piece count: a color sensor where pieces come in. ABOVE the intake line, so the count is
        // up to date before the intake asks "full?". BIOBUZZ allows 4 at a time (G407).
        pieces = add(Pieces.kinds("POLLEN", "RED_NECTAR", "BLUE_NECTAR")
                .comingIn(PieceSensor.color("intake_color"))
                .max(4)
                .startsWith(4, "POLLEN"));
        intake = add(Intake.motor("intake").stopsWhen(() -> pieces.isFull()));

        // The turret. homeSwitch: a REV Magnetic Limit Switch (REV-31-1462), configured as a "Digital
        // Device"; a magnet on the turret closes it at Tunables.Turret.homeAngle. At START the turret
        // turns slowly until it finds it (and sets its angle again every time it passes it).
        // follows(camera): it follows the goal from START (gamepad 2's BACK switches following).
        turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
                .homeSwitch("turret_home")
                .follows(camera));

        // The flywheel (a 6000 RPM motor; the shot tables are in motor RPM) and the indexer that feeds it.
        // The hood: a goBILDA Torque servo turns it through a 20T pinion and a 108T gear.
        // It only fires when the turret is aimed and there is a piece to fire.
        launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
                .feeder(Indexer.servo("indexer"))
                .hood(Hood.servo("hood").part("gobilda-2000-torque").shotAngles(80, 55))
                .speedFromDistance(camera)
                .startTable("pollen")
                .requireReady("turret aimed", () -> turret.isAimed())
                .requireReady("a piece to fire", () -> !pieces.isEmpty()));

        // The lift: a goBILDA 4-stage Viper-Slide (3210-0004-0004) on a 435 RPM motor (5203-2402-0014).
        // Heights in encoder counts (Tunables.Lift). looks(...): how it is drawn in the simulator: the
        // slide in the back left corner, and the wrist and claw riding on its top stage.
        lift = add(Lift.slide("lift").part("gobilda-5203-435")
                .looks(Shape.slide().atMm(-188, 188, 12).sizeMm(27, 27, 288).travelMm(421)
                        .note("example: from the lifter design, measure your own")));
        // The wrist: a goBILDA Speed servo (2000-0025-0003) on an 8 mm axle across the slide. Angles are
        // measured from straight down; 0 = folded, 145 = its hard stop (a stop collar and pin).
        wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed")
                .looks(Shape.arm().on(lift).atMm(0, -188, 142).sizeMm(115, 30, 20)
                        .angle("folded", -90).angle("flower", -212.8)));
        // The claw: two fingers on one goBILDA Torque servo (2000-0025-0002), geared together.
        claw = add(Claw.servo("claw").part("gobilda-2000-torque")
                .looks(Shape.claw().on(wrist).atMm(115, 0, 0).sizeMm(60, 120, 50)));

        // THE INTERLOCK: the turret and the claw (on the wrist) are never both in their zones. Whoever is
        // in its zone first keeps it; the other one waits outside its own. The zones are in
        // Tunables.TurretClaw. The lift needs no rule: with the wrist above 47 degrees no lifter part comes
        // near the turret at any lift height (team 8938's pincer model, 2026-10-22).
        // turretClaw.isHolding() is true while it holds a part back (Controls.java shows it on the light).
        turretClaw = keepApart("TurretClaw", turret.zone(), wrist.zone());

        // A goBILDA RGB Indicator Light (a "Servo" in the configuration). Its colors: Controls.java.
        lights = add(Lights.gobilda("light"));
    }
}
