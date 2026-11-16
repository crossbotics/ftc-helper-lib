# Mechanisms

Each part is one line in `Robot.java`'s `define()`. It also needs a field at the top of the class (for
example `public Lift lift;`) and a section with its numbers in `Tunables.java`. The section's name is
the configuration name in CamelCase: `intake_deploy` reads `IntakeDeploy`. For its buttons, copy its lines into `Controls.java` (see "buttons to add" in the [reference](../reference/robot-java.md)).

**The questions on this page:**

- [How do I add an intake on a motor?](#how-do-i-add-an-intake-on-a-motor)
- [How do I add an intake on CR servos?](#how-do-i-add-an-intake-on-cr-servos)
- [How do I make the intake run from the start?](#how-do-i-make-the-intake-run-from-the-start)
- [How do I add a lift with set heights?](#how-do-i-add-a-lift-with-set-heights)
- [My lift goes down when it should go up. How do I fix it?](#my-lift-goes-down-when-it-should-go-up-how-do-i-fix-it)
- [My motor isn't in the catalog](#my-motor-isnt-in-the-catalog)
- [How do I run one lift on two motors?](#how-do-i-run-one-lift-on-two-motors)
- [How do I add an arm that moves in degrees?](#how-do-i-add-an-arm-that-moves-in-degrees)
- [How do I add a claw?](#how-do-i-add-a-claw)
- [How do I run a claw on two servos?](#how-do-i-run-a-claw-on-two-servos)
- [How do I add a wrist?](#how-do-i-add-a-wrist)
- [How do I add any servo with named positions (a gate, a fold-out)?](#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)
- [How do I get my servo's full range?](#how-do-i-get-my-servos-full-range)
- [How do I send a part to a position from a button?](#how-do-i-send-a-part-to-a-position-from-a-button)
- [My robot doesn't have this part yet. How do I keep its code?](#my-robot-doesnt-have-this-part-yet-how-do-i-keep-its-code)
- [How do I add a launcher (flywheel and feeder)?](#how-do-i-add-a-launcher-flywheel-and-feeder)
- [How do I add a turret that follows the goal?](#how-do-i-add-a-turret-that-follows-the-goal)
- [How do I fire only when aimed, at the right speed?](#how-do-i-fire-only-when-aimed-at-the-right-speed)

## How do I add an intake on a motor?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-motor -->
```java title="Robot.java"
// At the top of the class: public Intake intake;   Here in define():
// "intake" = the motor's name in the robot configuration. Its numbers: Tunables.Intake.
// .part(...): which motor it is (optional, recommended).
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
```

<!-- recipe: tunables-intake -->
```java title="Tunables.java"
public static class Intake {                                    // "intake" in the configuration
    public static double inPower = DEFAULT;                     // power taking pieces in, 0 to 1 (1.0)
    public static double outPower = DEFAULT;                    // power pushing pieces out (0.7)
}
```

**Why:** the intake reads its numbers from `Tunables.Intake`. Its buttons are lines in `Controls.java`: [Intake: buttons to add](../reference/parts/intake.md#buttons-to-add).
**More:** [Add a lift](../add-to-robot/01-lift.md#robotjava-one-line), [Intake](../reference/parts/intake.md).

## How do I add an intake on CR servos?

A CR (continuous rotation) servo spins all the way round, like a small motor, instead of going to a position.

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-cr-servos -->
```java title="Robot.java"
// Two continuous-rotation (CR) servos. The one that spins the wrong way is reversed. (1)!
// "intake", "intake_right" = the servos' names in the robot configuration. Numbers: Tunables.Intake (the 1st name).
// .part(...): which servo it is (optional, recommended).
intake = add(Intake.crServos("intake", "intake_right").part("gobilda-2000-speed")
        .reversed("intake_right"));
```

1. Also add the field at the top of the class: `public Intake intake;`. The two servos face each other, so one must turn the other way. `.reversed(...)` names that servo.

**Why:** two servos that face each other must turn opposite ways to pull a piece in. The numbers are in `Tunables.Intake` too (`inPower`, `outPower`).
**More:** [Intake](../reference/parts/intake.md).

## How do I make the intake run from the start?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: intake-on-at-start -->
```java title="Robot.java"
// Runs in from ▶ until stopped. Its button (operator RT) then switches it on/off.
// .part(...): which motor it is (optional, recommended).
add(Intake.motor("intake").part("gobilda-5203-1150").onAtStart());
```

**Why:** many robots collect pieces all match. With this line, the driver does not have to switch the intake on.
**More:** [Intake](../reference/parts/intake.md).

## How do I add a lift with set heights?

**Where:** `Robot.java` (the part) and `Tunables.java` (its numbers).

<!-- recipe: lift -->
```java title="Robot.java"
// At the top of the class: public Lift lift;   Here in define():
// "lift", "lift_bottom" = the motor's and the bottom switch's names in the robot configuration. (1)!
// .part(...): which motor it is (optional, recommended).
lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom"));
```

1. Each time the lift presses the bottom switch, the switch sets the lift's 0. With no switch, delete `.bottomSwitch(...)`.

<!-- recipe: tunables-lift -->
```java title="Tunables.java"
public static class Lift {                                      // "lift" in the configuration
    public static double min = MEASURE;                         // lowest safe point, counts (Motor Finder)
    public static double max = MEASURE;                         // highest safe point, counts (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (from the motor)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (from the motor)
    public static double kF = DEFAULT;                          // hold up against gravity (0.05)
    public static double down = MEASURE;                        // a named height, counts: any name you like
    public static double low = MEASURE;                         // a named height (Motor Finder)
    public static double high = MEASURE;                        // a named height (Motor Finder)
}
```

**Why:** the lift never goes past `min` and `max`, so it cannot break itself. INIT stops if they are missing.
`MEASURE` stops INIT until you write the real number. Find yours with the [Motor Finder](15-tuning.md#how-do-i-find-a-lifts-limits-and-heights).
`"lift"` must be the motor's name in your configuration. To send the lift to a height, see [from a button](#how-do-i-send-a-part-to-a-position-from-a-button).
**More:** [Add a lift](../add-to-robot/01-lift.md#tunablesjava-its-numbers), [Lift](../reference/parts/lift.md),
[why the bottom switch matters](../add-to-robot/01-lift.md#1-what-it-is).

## My lift goes down when it should go up. How do I fix it?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: lift-reversed -->
```java title="Robot.java"
// The lift goes DOWN on positive power (the Motor Finder says "add .reversed()"): flip it.
// .part(...): which motor it is (optional, recommended).
add(Lift.slide("lift").part("gobilda-5203-435").reversed());
```

**Why:** the lift's limits and heights count up as it goes up, so positive power must move it up.
**More:** [Motor Finder](../tuning/05-motor-finder.md), [Troubleshooting](../troubleshooting.md#robot-behavior).

## My motor isn't in the catalog

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: own-parts -->
```java title="Robot.java"
// A motor that is not in the catalog: its encoder counts per output turn, then its free speed (RPM). (1)!
launcher = add(Launcher.flywheel("launcher", Motors.custom("my-flywheel-motor", 28, 5800)));
// A servo: how far it turns (degrees), then its speed (seconds per 60 degrees, at 4.8 V). (2)!
claw = add(Claw.servo("claw").part(Parts.customServo("my-claw-servo", 270, 0.16)));
// Motors.unknown(...): "I know neither number" (a single unknown number: Double.NaN). (3)!
lift = add(Lift.slide("lift").part(Motors.unknown("old-lift-motor")));
```

1. Find the two numbers on the seller's page. goBILDA: "Encoder Resolution" (the number "at the Output Shaft") and "No-Load Speed". REV: "Encoder Counts per Revolution" (at the output) and "Free Speed". The name (`"my-flywheel-motor"`) then works like a catalog id, here and in `.part(...)`.
2. When the seller lists 4.8 V and 6 V, use the 4.8 V speed, because the hub gives a servo about 5 V.
3. `Double.NaN` means "I don't know this one". The part then uses general defaults, and INIT says so. A launcher or an arm needs at least the counts per turn: `Motors.custom("...", 28, Double.NaN)`.

**Why:** the library works out a part's defaults (a launcher's gains and speed, a lift's kP, a servo's
move time) from its motor's or servo's numbers. When you describe yours with the seller's numbers, it
works like a catalog part. INIT says `defaults from my-flywheel-motor (custom)`, and `:sim:fromRobot`
writes its numbers into the simulator's robot file. For a number you do not know, write `Double.NaN`
("I don't know this one"). Or write the whole part as `Motors.unknown` / `Parts.unknownServo`. The part
then uses general defaults, and INIT says so. Do not use a catalog motor that is "close": its numbers
would be wrong, and nothing would warn you.
**More:** [Parts not in the catalog](../reference/robot-java.md#parts-not-in-the-catalog), [Parts catalog](../guides/parts-catalog.md).

## How do I run one lift on two motors?

**Where:** `Robot.java`, inside `define()`, and `Tunables.java`.

<!-- recipe: lift-two-motors -->
```java title="Robot.java"
// "lift_left", "lift_right" = the motors' names in the robot configuration. Numbers: Tunables.LiftLeft. (1)!
// Plug the encoder cable into the FIRST motor: only its encoder is read. (2)!
// The motors face each other, so the second turns the other way: alsoMotorReversed. (3)!
lift = add(Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"));
```

1. The numbers are in `Tunables.LiftLeft` (the FIRST motor's name in CamelCase), not in `Tunables.Lift`.
2. Both motors always get the same power, also when the lift stops or pauses. So one encoder is enough.
3. When both motors turn the same way for "up", use `.alsoMotor("lift_right")` instead.

<!-- recipe: tunables-lift-two-motors -->
```java title="Tunables.java"
// A lift on two motors, Lift.slide("lift_left").part("gobilda-5203-435").alsoMotorReversed("lift_right"):
// its section is the FIRST motor's name in CamelCase.
public static class LiftLeft {                                  // "lift_left" in the configuration
    public static double min = MEASURE;                         // lowest safe point, counts (first motor)
    public static double max = MEASURE;                         // highest safe point, counts (first motor)
    public static double kP = DEFAULT;                          // push toward the target (from the motor)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (from the motor)
    public static double kF = 0.06;                             // a heavier slide needs more hold (0.05)
    public static double down = MEASURE;                        // a named height (Motor Finder)
    public static double low = MEASURE;                         // a named height (Motor Finder)
    public static double high = MEASURE;                        // a named height (Motor Finder)
}
```

**Why:** two motors on one slide must never fight. The library sends both the very same power in the same
loop. It does this also when the lift stops or pauses (BACK). It does it also when an interlock rule (a rule that stops two parts from hitting each other) holds the lift. Your own code that copies the power every loop would
miss pause and STOP. **Check the directions before the chain goes on.** With the chain or belt off, each
motor must turn the way that lifts. If one is wrong, swap `alsoMotorReversed` and `alsoMotor`. Two motors
that push against each other get hot and can break a gearbox. An arm works the same way:
`Arm.motor("arm_left", "gobilda-5203-117").alsoMotorReversed("arm_right")`. The simulator ties both
motors to one slide (`:sim:fromRobot` writes the second motor's line).
**More:** [Two motors on one lift or arm](../reference/parts/lift.md#two-motors-on-one-lift-or-arm), [Tunables names](../reference/parts/lift.md#in-tunablesjava).

## How do I add an arm that moves in degrees?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: arm -->
```java title="Robot.java"
// "arm" = the motor's name in the robot configuration. Its numbers: Tunables.Arm.
// "gobilda-5203-117" = the motor's catalog id (needed: it works in degrees). (1)!
// gearRatio: motor turns per arm turn. 0 degrees = where it is at INIT.
arm = add(Arm.motor("arm", "gobilda-5203-117").gearRatio(2));
```

1. The arm needs the motor's counts per turn to work in degrees. So, as for a turret or a launcher, the arm's motor id is required.

<!-- recipe: tunables-arm -->
```java title="Tunables.java"
public static class Arm {                                       // "arm" in the configuration
    public static double min = MEASURE;                         // lowest safe angle, degrees, 0 = at INIT
    public static double max = MEASURE;                         // highest safe angle, degrees (Motor Finder)
    public static double kP = DEFAULT;                          // push toward the target (0.02)
    public static double kI = DEFAULT;                          // fix a small steady miss (0)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double kF = 0.1;                              // hold up against gravity (0)
    public static double stowed = MEASURE;                      // a named angle, degrees: any name you like
    public static double pickup = MEASURE;                      // a named angle (Motor Finder)
    public static double score = MEASURE;                       // a named angle (Motor Finder)
}
```

**Why:** degrees are easier to think about than encoder counts. Put the arm at its 0 before you press INIT.
**More:** [Arm](../reference/parts/arm.md).

## How do I add a claw?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: claw -->
```java title="Robot.java"
// At the top of the class: public Claw claw;   Here in define():
// "claw" = the servo's name in the robot configuration. Its numbers: Tunables.Claw.
// .part(...): which servo it is (optional, recommended).
claw = add(Claw.servo("claw").part("gobilda-2000-torque"));
```

<!-- recipe: tunables-claw -->
```java title="Tunables.java"
public static class Claw {                                      // "claw" in the configuration
    // The Servo Finder shows "Tunables.Claw: P1 = 0.210, P2 = 0.770": write P1 as open, P2 as closed.
    public static double open = MEASURE;                        // servo position, open (Servo Finder)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** a claw needs two numbers, open and closed, and the Servo Finder gives them. Its button is a line in `Controls.java`: [Claw: buttons to add](../reference/parts/claw.md#buttons-to-add).
**More:** [Add a lift](../add-to-robot/01-lift.md#robotjava-one-line), [Claw](../reference/parts/claw.md).

## How do I run a claw on two servos?

**Where:** `Robot.java`, inside `define()`, and `Tunables.java`.

<!-- recipe: claw-two-servos -->
```java title="Robot.java"
// "claw_left", "claw_right" = the servos' names in the robot configuration. Numbers: Tunables.ClawLeft.
// Find open and closed in Tune and check > Servo Finder on the FIRST servo: the second moves with it.
// The servos face each other, so the second turns the other way: alsoServoReversed.
claw = add(Claw.servo("claw_left").part("gobilda-2000-torque").alsoServoReversed("claw_right"));
```

<!-- recipe: tunables-claw-two-servos -->
```java title="Tunables.java"
// A claw on two servos, Claw.servo("claw_left").alsoServoReversed("claw_right"): its section is the
// FIRST servo's name in CamelCase.
public static class ClawLeft {                                  // "claw_left" in the configuration
    public static double open = MEASURE;                        // servo position, open (Servo Finder on claw_left)
    public static double closed = MEASURE;                      // servo position, closed (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
    public static double alsoTrim = DEFAULT;                    // the second servo: raw position added so the two meet (0; used at the next move)
}
```

**Why:** two servos on one part must never fight. The library sends both the same position at the same
moment, also when the robot pauses (BACK) or stops. Your own code that copies the position would miss
some of these moments. **Reversed or not:** servos that face each other (mirror images, the usual way)
turn opposite ways, so use `alsoServoReversed`. When both face the same way, use `.alsoServo("claw_right")`.
**When the fingers do not quite meet:** the horns may sit a spline tooth apart. Set `alsoTrim` (a small raw
position, like 0.02) for the second servo. The same line works on a Wrist, a Positioner and a servo feeder:
`Indexer.servo("feeder_left").alsoServoReversed("feeder_right")` (two arms that pinch a piece into a launcher).
On a CR servo feeder, it adds a second wheel that spins the other way: `Indexer.crServo("feeder_left").alsoServoReversed("feeder_right")`.
**More:** [Two servos on one part](../reference/parts/claw.md), [Tunables names](../reference/parts/claw.md#in-tunablesjava).

## How do I add a wrist?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: wrist -->
```java title="Robot.java"
// At the top of the class: public Wrist wrist;   Here in define():
// "wrist" = the servo's name in the robot configuration. Its numbers: Tunables.Wrist.
// .part(...): which servo it is (optional, recommended).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed"));
```

<!-- recipe: tunables-wrist -->
```java title="Tunables.java"
public static class Wrist {                                     // "wrist" in the configuration
    public static double down = MEASURE;                        // servo position: any name you like
    public static double level = MEASURE;                       // servo position (Servo Finder)
    public static double up = MEASURE;                          // servo position (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** a wrist steps through its named positions. Its buttons are lines in `Controls.java`: [Wrist: buttons to add](../reference/parts/wrist.md#buttons-to-add).
**More:** [Wrist](../reference/parts/wrist.md).

## How do I add any servo with named positions (a gate, a fold-out)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: positioner -->
```java title="Robot.java"
// Any servo with named positions: a gate, a kicker, a fold-out intake.
// "intake_deploy" = the servo's name in the robot configuration. Numbers: Tunables.IntakeDeploy.
// .part(...): which servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque"));
// Right after ▶ (autonomous and TeleOp): fold the intake out.
atStart(() -> Action.named("Deploy").then(() -> intakeDeploy.goTo("deployed")));
```

<!-- recipe: tunables-positioner -->
```java title="Tunables.java"
public static class IntakeDeploy {                              // "intake_deploy" in the configuration
    public static double stowed = MEASURE;                      // servo position, folded (Servo Finder)
    public static double deployed = MEASURE;                    // servo position, folded out (Servo Finder)
    public static double travelSec = DEFAULT;                   // time for a full 0-to-1 move (0.3, or the servo's)
}
```

**Why:** a `Positioner` has no usual buttons, so you choose when it moves. Here it moves at ▶.
**More:** [Positioner](../reference/parts/positioner.md), [an action at ▶](08-actions.md#how-do-i-run-an-action-when-the-opmode-starts).

## How do I get my servo's full range?

**Where:** `Robot.java`, inside `define()`: add `.fullRange()` to the servo part.

<!-- recipe: servo-full-range -->
```java title="Robot.java"
// fullRange(): positions 0 to 1 = the servo's whole turn (300 degrees on a goBILDA 2000). (1)!
// Turn it on FIRST, then find the positions (Servo Finder: press Y while choosing the servo).
wrist = add(Wrist.servo("wrist").part("gobilda-2000-speed").fullRange());
```

1. For positions 0 to 1, the hub normally sends 600-2400 us (microseconds). With that signal, a goBILDA 2000 servo turns only 270 of its 300 degrees. `fullRange()` sends the whole 500-2500 us.

**Why:** the FTC SDK's normal servo signal (600 to 2400 microseconds) is narrower than what most servos
take (500 to 2500). So the servos turn less than the seller says: 270 degrees for a goBILDA 2000, 243 for
a REV Smart Robot Servo. Add it only when you need the extra turn. It works the same on a Claw, a
Positioner, `Hood.servo`, `Turret.servo` and `Indexer.servo`. After the change, the positions you measured
before point somewhere else, so measure them again. Without `.fullRange()`, nothing changes.
**More:** [Full range](../modules/hardware.md#full-range) (a table per servo), [Claw, Wrist, Positioner](../reference/parts/claw.md).

## How do I send a part to a position from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: mechanism-commands -->
```java title="Controls.java"
// The names are the ones in Tunables.java (capitals do not matter).
g2.y.press(() -> robot.arm.goTo("score")).says("arm to score");
g2.a.press(() -> robot.arm.goTo("pickup")).says("arm to pick up");
g2.x.press(() -> robot.lift.goTo("high")).says("lift to high");
g2.b.press(robot.claw::toggle).says("claw open/close");
g2.back.press(() -> robot.intakeDeploy.goTo("stowed")).says("fold the intake in");
```

**Why:** `goTo("name")` uses the position by its name, so the number is only in `Tunables.java`.
These lines use Y, A, X and B. The lift's and the claw's usual lines use the same buttons, so write one
or the other. See [pick a free button](03-buttons.md).
**More:** [Lift](../reference/parts/lift.md), [Claw](../reference/parts/claw.md).

## My robot doesn't have this part yet. How do I keep its code?

You change three places: the part's line in `Robot.java`, the action in `Actions.java`, and its button in
`Controls.java`.

**`Robot.java`**, inside `define()`: add `.optional()` to the part's `add` line. Here the part is a deploy
servo that folds the intake out. The `atStart` line runs the action below at ▶.

<!-- recipe: optional-part -->
```java title="Robot.java"
// optional(): while "intake_deploy" is not in the robot configuration, the part is off (INIT goes on). (1)!
// .part(...): which motor or servo it is (optional, recommended).
intakeDeploy = add(Positioner.servo("intake_deploy").part("gobilda-2000-torque").optional());
intake = add(Intake.motor("intake").part("gobilda-5203-1150"));
// At ▶: fold the intake out, then run it (the action is in Actions.java, below). (2)!
atStart(() -> Actions.deployIntake(this));
```

1. The deploy servo is not on the robot yet. INIT shows a note and goes on (see **Why**, below).
2. With no deploy servo, the action skips the servo and the wait for it, and only starts the intake.

**`Actions.java`**, inside `public class Actions { ... }`: the action. In your file the brackets say
`(Robot r)`.

<!-- recipe: optional-part-action -->
```java title="Actions.java"
/** Folds the intake out with the deploy servo, then runs the intake. */
public static Action deployIntake(NotOnTheRobotYet r) {   // in your Actions.java: (Robot r)
    return Action.named("Deploy intake")
            .then(() -> r.intakeDeploy.goTo("deployed"))
            .waitUntil("the deploy servo", r.intakeDeploy::isDone, 2.0)
            .then(r.intake::in, () -> r.intakeDeploy.goTo("stowed"));
}
```

**`Controls.java`**, inside `buttons(...)`: the button, with `.needs(...)` at the end.

<!-- recipe: optional-part-button -->
```java title="Controls.java"
// needs(...): while the deploy servo is not on the robot, X does nothing and the
// button map at INIT says "off: IntakeDeploy not on the robot".
g2.x.press(Actions.deployIntake(robot)).says("deploy intake").needs(robot.intakeDeploy);
```

**Why:** teams build robots in steps. Without `optional()`, INIT stops when the part's device is not in
the configuration (`No Servo named "intake_deploy" ...`). With it, INIT shows a note and goes on:
`"intake_deploy" not in the configuration: IntakeDeploy is off (optional part)`. In the COMPETITION
profile, the note is a warning at the top of the screen (`!! COMPETITION: ... IntakeDeploy is OFF ...`).
So nobody plays a match without a part by mistake. Everything that uses the part skips it:
- its commands
- its buttons (the button map says `G2 X: deploy intake - off: IntakeDeploy not on the robot`)
- the action steps (an autonomous keeps going, and its report says `skipped IntakeDeploy (not on the robot)`)

When you wire the servo and add it to the configuration, it works, with no code to change. You need no
`if (HAS_DEPLOY_SERVO)` switches. To try it in the simulator without the part, write `missing = intake_deploy`
in the robot file.
**More:** [Parts not on the robot yet](../reference/robot-java.md#parts-not-on-the-robot-yet).

## How do I add a launcher (flywheel and feeder)?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher -->
```java title="Robot.java"
// A flywheel (in RPM) and the indexer that feeds it one piece at a time.
// "launcher", "indexer" = names in the robot configuration. Numbers: Tunables.Launcher, .Indexer.
// The motor's catalog id is in the brackets (needed). The indexer's .part(...) is optional, recommended. (1)!
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed")));
```

1. The launcher needs its motor's id for its speed control. The indexer's `.part(...)` is optional ([what it does](01-the-basics.md#words-you-will-meet)).

<!-- recipe: tunables-launcher -->
```java title="Tunables.java"
public static class Launcher {                                  // "launcher" in the configuration
    public static double speedRpm = DEFAULT;                    // shot speed without the table (60% of top)
    public static double stepRpm = DEFAULT;                     // speed change per D-pad press (100)
    public static double readyBandRpm = DEFAULT;                // "at speed" = this close to the target (60)
    public static double idleRpm = 1500;                        // spins between shots: quicker first shot (0)
    public static double shotIntervalSec = DEFAULT;             // least time between two pieces (0.25)
    // The hub's speed control: the defaults for the motor until the Flywheel Finder (INIT shows them).
    // Then write the Finder's numbers in their place.
    public static double kP = DEFAULT;                          // push per speed error (kF / 10)
    public static double kI = DEFAULT;                          // fix a steady error (kP / 10)
    public static double kD = DEFAULT;                          // brake changes (0)
    public static double kF = DEFAULT;                          // steady power for a speed (from the motor)
    // {inches to the goal, RPM}, blended between rows. Example rows: write the Shot Tuner's instead.
    public static double[][] speedByDistance = {{24, 2200}, {48, 2550}, {72, 2900}, {96, 3250}};
}
```

<!-- recipe: tunables-indexer -->
```java title="Tunables.java"
public static class Indexer {                                   // "indexer" in the configuration
    public static double rest = MEASURE;                        // servo position, out of the way (Servo Finder)
    public static double push = MEASURE;                        // servo position, piece pushed (Servo Finder)
    public static double pushSec = DEFAULT;                     // time to push (0.25, or the servo's speed)
    public static double returnSec = DEFAULT;                   // time to come back (the same as pushSec)
}
```

**Why:** the launcher feeds a piece only when the wheel is at speed, so every shot flies the same way.
**More:** [Add a launcher](../add-to-robot/05-launcher.md#robotjava-one-line), [Launcher](../reference/parts/launcher.md).

## How do I feed with two wheels, one on each side?

**Where:** `Robot.java`, inside `define()`. Add the intake before the launcher.

<!-- recipe: launcher-two-feed-wheels -->
```java title="Robot.java"
// Two CR servo wheels squeeze each piece into the flywheel. They face each other, so the
// second spins the other way: alsoServoReversed. Numbers: Tunables.FeederLeft (power, feedSec).
// feedWheelOutWith: the intake pushing out turns both wheels backward too.
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.crServo("feeder_left").alsoServoReversed("feeder_right").part("gobilda-2000-speed"))
        .feedWheelOutWith(intake));
```

**Why:** two wheels that squeeze the piece push it in straight. One power (`Tunables.FeederLeft`) runs both,
so they can never fight. When the intake pushes pieces out, the wheels turn backward, so a piece in the feeder
comes out too. A shot always wins.
**Test the direction first:** set `feedSec` to 2, fire with no piece, and watch both wheels pull in. Both pull
out: add `.reversed()` to the feeder. One pulls out: use `.alsoServo(...)` in place of `.alsoServoReversed(...)`.
**More:** [Indexer](../reference/parts/indexer.md), [Launcher](../reference/parts/launcher.md).

## How do I add a turret that follows the goal?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: turret-follows-camera -->
```java title="Robot.java"
// The camera knows the goal of each alliance (BLUE first, then RED).
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
// "turret" = the motor's name in the robot configuration. 5 motor turns per turret turn.
// follows(camera): it follows the goal from ▶, but never past Tunables.Turret min and max.
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
```

<!-- recipe: tunables-turret -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double startAngle = DEFAULT;                  // where you set it by hand before INIT (0)
    public static double kP = DEFAULT;                          // push per degree off (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = DEFAULT;                    // the most power (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
}
```

**Why:** the turret aims for the driver, and its limits keep the wires from twisting.
**More:** [Add a turret: the turret](../add-to-robot/06-turret.md), [Turret](../reference/parts/turret.md), [Camera](../reference/parts/camera.md).

## How do I fire only when aimed, at the right speed?

**Where:** `Robot.java`, inside `define()` (and `Tunables.java` for the numbers).

<!-- recipe: launcher-aimed -->
```java title="Robot.java"
// The speed comes from the distance to the goal (Tunables.Launcher.speedByDistance),
// and a piece is only fed when the turret points at the goal.
// .part(...): which servo it is (optional, recommended).
launcher = add(Launcher.flywheel("launcher", "gobilda-5203-6000")
        .feeder(Indexer.servo("indexer").part("gobilda-2000-speed"))
        .speedFromDistance(camera)
        .requireReady("turret aimed", () -> turret.isAimed()));
```

**Why:** a shot fired before the turret is on target, or at the wrong speed, is a lost piece. For a
turret that follows the goal, `isAimed()` means on the goal NOW. When the camera loses the goal, it is false.
**More:** [Add a camera](../add-to-robot/07-camera.md#robotjava-one-line).
