# Odometry and driveTo

Odometry tells the robot where it is on the field. **Field numbers:** x = inches right of the center
line, and y = inches away from your wall (negative = your half). Heading 0 = facing away from you, and
counterclockwise (left) is +. **Every number in your code is seen from your own drivers' wall**, whatever
your alliance: start spots, `driveTo`, the goal point, the reset spot. Only the simulator's map and its
scenario checks (`expect robot x ...`) use one fixed view, from the red wall. See the
[field map](../add-to-robot/09-odometry.md#why-odometry).

**The questions on this page:**

- [How do I add a goBILDA Pinpoint?](#how-do-i-add-a-gobilda-pinpoint)
- [How do I add a SparkFun OTOS?](#how-do-i-add-a-sparkfun-otos)
- [How do I add dead wheels?](#how-do-i-add-dead-wheels)
- [How do I tune the odometry?](#how-do-i-tune-the-odometry)
- [How do I drive to a point on the field?](#how-do-i-drive-to-a-point-on-the-field)
- [How do I get around field elements on the way to a spot?](#how-do-i-get-around-field-elements-on-the-way-to-a-spot)
- [How do I show where the robot is?](#how-do-i-show-where-the-robot-is)
- [How do I use the robot's position in an action?](#how-do-i-use-the-robots-position-in-an-action)
- [How do I set the pose when autonomous did not run?](#how-do-i-set-the-pose-when-autonomous-did-not-run)

## How do I add a goBILDA Pinpoint?

**Where:** `Robot.java`, inside `define()`, after the drive line.

<!-- recipe: odometry-pinpoint -->
```java title="Robot.java"
// After drive(...). The robot's length and width, then where each pod's wheel is, mm: (1)!
// the forward pod from the LEFT edge, the sideways pod from the FRONT edge.
odometry(Odometry.pinpoint("pinpoint").robotSizeMm(430, 400).podsFromEdgesMm(284, 383));
```

1. Measure with a tape across the frame, bumpers off. The library finds the robot's center from the size. Here it works out `offsetsMm(-84, -168)`: the forward pod is 84 mm right of the center, and the sideways pod is 168 mm behind it. The Odometry Tuner then measures the exact offsets, and you write `.offsetsMm(...)` in place of `.podsFromEdgesMm(...)`.

**Why:** the Pinpoint must know where the pods are, so a turn on the spot does not look like a move.
**More:** [Add odometry: one line in Robot.java](../add-to-robot/09-odometry.md#robotjava-one-line), [Pinpoint](../modules/odometry.md#pinpoint).

## How do I add a SparkFun OTOS?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-otos -->
```java title="Robot.java"
// The robot's length and width, then the OTOS's middle from the FRONT and LEFT edges (mm), and its turn.
odometry(Odometry.otos("otos").robotSizeMm(430, 400).mountFromEdgesMm(126, 200, 0));
```

**Why:** the OTOS measures at its own spot. To give the place of the robot's center, it must know where it sits.
**More:** [OTOS](../modules/odometry.md#otos).

## How do I add dead wheels?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: odometry-dead-wheels -->
```java title="Robot.java"
// The configuration names of the motor ports the pods plug into: left, right, sideways pod.
// trackWidthIn: left pod to right pod. perpForwardIn: the sideways pod, 6 in BEHIND the center.
odometry(Odometry.threeWheels("par_left", "par_right", "perp")
        .trackWidthIn(13.2).perpForwardIn(-6));
```

**Why:** each pod's encoder plugs into a motor port, so the names are those ports' configuration names.
**More:** [Three dead wheels](../modules/odometry.md#three-dead-wheels).

## How do I tune the odometry?

**Where:** a new file in TeamCode (one per robot). The whole file:

<!-- recipe: odometry-tuner -->
```java title="OdometryTuner.java"
// The Odometry Tuner: which way each pod counts, the offsets, and a loop check.
@TeleOp(name = "Cookbook Odometry Tuner", group = "Finders")
@Disabled   // delete this line in your copy, or you will not see it
public class OdometryTuner extends OdometryTunerOpMode {
    @Override
    protected RobotDefinition robot() {
        return new Robot();     // your Robot.java: the tuner uses its parts and numbers
    }
}
```

**Why:** the tuner checks which way each pod counts, and measures the offsets. Then set `Tunables.Odometry.checked = true`, or `driveTo` does nothing on the robot.
It is its own small file, because each program in the Driver Station's list is one class. `robot()` makes
it use your `Robot.java`, so it measures the same odometry line that autonomous uses.
**More:** [Odometry Tuner: the Odometry Tuner](../tuning/09-odometry-tuner.md), [Finders](../tuning/09-odometry-tuner.md).

## How do I drive to a point on the field?

**Where:** a file in `autos/`, inside `steps(...)`. It needs a mecanum drive.

<!-- recipe: drive-to -->
```java title="autos/YourAuto.java"
// The start spot (x, y, heading) must be where the robot really stands: the pose starts there.
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", -30, -30, 45, 0.6)
 .parkWithSeconds(4)
 // The last number: "close enough", in inches (default 1).
 .driveTo("Park", -56, -36, 0, 0.7, 2.0);
```

It replaces `.move(...)`: you give a place on the field, not a distance.
**Why:** `driveTo` keeps checking where the robot is. So a bump or a wheel slip does not matter, and the robot ends on the point.
On the robot, it works only after the [Odometry Tuner](#how-do-i-tune-the-odometry) and `checked = true` in `Tunables.java`, section `Odometry`.
**More:** [Drive to points on the field: drive to field points](../building-autos/04-drive-to-points.md#drive-to-a-point-driveto), [driveTo](../building-autos/04-drive-to-points.md#drive-to-a-point-driveto).

## How do I get around field elements on the way to a spot?

**Where:** `Robot.java`, inside `define()`, then a file in `autos/`, or `Controls.java`. It needs odometry and a mecanum drive.

<!-- recipe: field-map -->
```java title="Robot.java"
// After odometry(...). The margin: half the robot's corner to corner size, plus some for drift.
// Each box: two opposite corners, like driveTo's numbers. These are BIOBUZZ's two HIVE frames.
fieldMap(FieldMap.margin(13)
        .keepOut("HIVE frame, near", -19.5, -24.8, 19.5, -15.4)
        .keepOut("HIVE frame, far", -19.5, 15.4, 19.5, 24.8));
```

In an autonomous, `routeTo` is `driveTo` with a way around the boxes (the keep-out areas in the field map). It starts from wherever the park time stopped the robot:

<!-- recipe: route-to-park -->
```java title="autos/YourAuto.java"
s.start("Against our wall, in the left corner tile, facing the field", -56, -61, 0)
 .driveTo("To the goal", -30, -30, 45, 0.6)
 .parkWithSeconds(6)    // the route slows at each corner: give it more time than a straight line
 .routeTo("Park", -56, -36, 0, 0.8);
```

In TeleOp, a button or a time starts it, and any drive stick stops it:

<!-- recipe: route-button -->
```java title="Controls.java"
// route(...) makes the job. It starts when the button or the time says so.
Runnable park = route("Park", -56, -36, 0, 0.8);
g1.dpadLeft.tap(park).says("drive to the park spot (a drive stick stops it)");
atSecondsLeft(10, park);
```

**Why:** a straight line from an unknown place can go through a field element. The robot works out the way when it sets off.
It uses only its pose, so it cannot see another robot. Go slow the first time.
**More:** [Keep-out boxes and routes](../modules/odometry.md#keep-out-boxes-and-routes).

## How do I show where the robot is?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-pose -->
```java title="Controls.java"
show("Where", () -> String.format(Locale.US, "x %.0f, y %.0f in, heading %.0f",
        robot.pose().x, robot.pose().y, robot.pose().headingDeg));
```

**Why:** to check the odometry, push the robot by hand and watch the numbers.
**More:** [robot.pose()](../modules/odometry.md#robotpose). (TeleOp also starts where autonomous ended: [Add odometry](../add-to-robot/09-odometry.md#teleop-starts-where-autonomous-ended).)

## How do I use the robot's position in an action?

**Where:** `Actions.java`, inside the class.

<!-- recipe: pose-in-action -->
```java title="Actions.java"
/** Runs the intake while the robot is on our half of the field (y below 0). */
public static Action intakeOnOurHalf(Robot r) {
    return Action.named("Intake on our half")
            .then(r.intake::in)
            .waitUntil("past the center line", () -> r.pose().y > 0, 10.0)
            .then(r.intake::stop);
}
```

**Why:** the library updates `robot.pose()` every loop, so a wait can test where the robot is.
**More:** [robot.pose()](../modules/odometry.md#robotpose).

## How do I set the pose when autonomous did not run?

**Where:** `Tunables.java`, section `Odometry` (and `Controls.java` for more spots).

When autonomous stopped early or did not run, TeleOp does not know where the robot is. Park the robot on
the reset spot and let go of the drive sticks. Then hold gamepad 1 **START** for 1 second ("set pose
here"). This works in INIT or in the match. Write the spot for BLUE, like a start spot. Write
`mirrorForRed = false` because BIOBUZZ's field is turned half a turn (RED uses the same numbers from its
own wall). On a mirrored field, leave it out (default `true`, RED mirrored). INIT's `Reset spot for RED`
line shows which one the library uses.

<!-- recipe: tunables-reset-spot -->
```java title="Tunables.java"
public static class Odometry {                                  // a fixed name: always Odometry
    public static boolean checked = false;                      // true after the Odometry Tuner's Loop test
    // "Set pose here" (TeleOp: hold gamepad 1 START 1 s on this spot), written for BLUE. (1)!
    public static double resetX = -56;                          // inches right of center, - = left
    public static double resetY = -61;                          // inches from center, - = our half
    public static double resetHeading = 0;                      // degrees, 0 = facing the field
    // RED: false = the same numbers from the red wall; true (or left out) = mirrored left-right. (2)!
    public static boolean mirrorForRed = false;                 // BIOBUZZ: false
}
```

1. Inches and degrees seen from your wall, like a start spot. Here the spot is our left corner tile, facing the field.
2. BIOBUZZ's field is turned half a turn between the alliances, so write `false`. `true` is for a mirrored field.

For more spots, or to set the pose from an action, write this in `Controls.java`, inside `buttons(...)`:

<!-- recipe: reset-pose-button -->
```java title="Controls.java"
// A second spot of your own: hold D-pad down 1 s with the robot in the right corner tile. (1)!
g1.dpadDown.heldFor(1.0, () -> robot.resetPose(56, -61, 0)).says("hold 1 s: pose = right corner tile");
// An action can set it too (the action is in Actions.java, below). (2)!
g1.dpadUp.heldFor(1.0, Actions.poseHereThenIntake(robot)).says("hold 1 s: pose here, intake on");
```

1. x, y and heading are written for BLUE. RED uses them like the reset spot in `Tunables.Odometry`.
2. Here the action sets the `Tunables.Odometry` spot, then runs the intake.

The action, in `Actions.java` inside the class:

<!-- recipe: pose-here-action -->
```java title="Actions.java"
/** Sets the pose to the reset spot in Tunables.Odometry, then runs the intake. */
public static Action poseHereThenIntake(Robot r) {
    return Action.named("Pose here, intake on")
            .then(r::resetPose)            // resetPose() with no numbers: the Tunables.Odometry spot
            .then(r.intake::in);
}
```

**Why:** with a pose, the aim's odometry backup and field-centric "forward" work again. The 1 s hold
means a bump of the button does nothing. Write all the spots from your own drivers' wall, like every
field number in your code.
**More:** [Add odometry: TeleOp starts where autonomous ended](../add-to-robot/09-odometry.md#teleop-starts-where-autonomous-ended), [resetPose()](../reference/robot-java.md#robotdefinition).
