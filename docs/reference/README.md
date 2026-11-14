# Reference

**Look it up.** These pages list every option, number and command. They do not teach. To learn a thing step
by step, start at [the five files](../five-files/README.md).

## The robot file by file

| Page | What is on it |
| --- | --- |
| [Robot.java](robot-java.md) | what every part shares: `drive(...)`, `add(...)`, optional parts, shapes for the simulator |
| [Tunables.java](tunables.md) | how the numbers work: DEFAULT and MEASURE, units, what INIT checks, the tuning menu |
| [Controls.java](controls.md) | button lines, press types, gamepad names, one or two drivers |
| [Steps for autos](steps.md) | every autonomous step, actions, keep time to park |

## One page per part

Each part's page has the same four parts: what you write in `Robot.java`, its numbers in `Tunables.java`,
its commands, and the buttons to add.

| Part | What it is |
| --- | --- |
| [Drive](parts/drive.md) | the wheels: mecanum or tank |
| [Odometry](parts/odometry.md) | where the robot is on the field: Pinpoint, OTOS or dead wheels |
| [PathFollower](parts/path-follower.md) | Pedro paths, and actions along a path |
| [Intake](parts/intake.md) | a roller on a motor and/or CR servos: in, out, on at the start |
| [Lift](parts/lift.md) | a slide on a motor with named heights and a bottom switch |
| [Arm](parts/arm.md) | an arm on a motor with named angles in degrees |
| [Claw](parts/claw.md) | a servo that opens and closes |
| [Wrist](parts/wrist.md) | a servo with named positions that tilts a claw |
| [Positioner](parts/positioner.md) | any servo with named positions: deploy, fold, gate |
| [Turret](parts/turret.md) | turns to aim, inside its limits |
| [Interlock](parts/interlock.md) | a rule that keeps two parts apart |
| [Launcher](parts/launcher.md) | a flywheel: spin up, fire, speed from distance |
| [Indexer](parts/indexer.md) | feeds one piece at a time |
| [Hood](parts/hood.md) | tilts the shot: its angle from the shot table |
| [Camera](parts/camera.md) | a Limelight, webcam or HuskyLens: the alliance's goal |
| [Aim](parts/aim.md) | where the goal is, for everything that aims |
| [Pieces and PieceSensor](parts/pieces.md) | which piece is where, and how many the robot holds |
| [Lights](parts/lights.md) | a colored light for the drivers |

## More

- [Parts catalog](../guides/parts-catalog.md): motors and servos, with their numbers.
- [Troubleshooting](../troubleshooting.md): what you see, and the fix.
- [Search every class and method](javadoc.md): the Javadoc.
