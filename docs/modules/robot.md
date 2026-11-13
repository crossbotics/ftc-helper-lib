# Robot module

The classes of the five-files layer. How to use each part is in the [Reference](../reference/README.md): one page per part, and pages for
[Robot.java](../reference/robot-java.md), [Tunables.java](../reference/tunables.md),
[Controls.java](../reference/controls.md) and [steps for autos](../reference/steps.md).

Package: `com.crossbotics.ftchelper.robot`

| Class | Use it to |
| --- | --- |
| [`RobotDefinition`](../reference/robot-java.md#robotdefinition) | describe the robot in `Robot.java`: `drive(...)` and one `add(...)` per mechanism |
| [`Drive`](../reference/parts/drive.md) | the drivetrain: mecanum or tank, motor names, motor, wheel, IMU |
| [`Odometry`](../reference/parts/odometry.md) | where the robot is on the field: a goBILDA Pinpoint, a SparkFun OTOS, or dead wheels |
| [`Intake`](../reference/parts/intake.md) | a roller on a motor and/or CR servos: in, out, on at the start |
| [`Lift`](../reference/parts/lift.md) | a slide on a motor with named heights and a bottom switch |
| [`Arm`](../reference/parts/arm.md) | an arm on a motor with named angles in degrees |
| [`Claw`](../reference/parts/claw.md) | a claw servo: open, closed |
| [`Wrist`](../reference/parts/wrist.md) | a wrist servo with named positions |
| [`Positioner`](../reference/parts/positioner.md) | any servo with named positions (deploy, fold, gate) |
| [`Turret`](../reference/parts/turret.md) | a turret inside its limits, by hand or following the camera's goal |
| [`Launcher`](../reference/parts/launcher.md) | a flywheel and its [`Indexer`](../reference/parts/indexer.md): spin up, fire, speed from distance |
| [`Indexer`](../reference/parts/indexer.md) | feeds one piece at a time (servo, CR servo or motor) |
| [`Hood`](../reference/parts/hood.md) | a launcher's hood (servo or motor): its angle from the shot table, by hand, a trim |
| [`Camera`](../reference/parts/camera.md) | a Limelight or webcam; the alliance's goal |
| [`Pieces`](../reference/parts/pieces.md), [`PieceSensor`](../reference/parts/pieces.md) | which game piece is at a spot (color sensor, distance sensor, second webcam); how many the robot holds and which fires next |
| [`Lights`](../reference/parts/lights.md), [`LightColor`](../reference/parts/lights.md) | a colored light for the drivers (goBILDA RGB Indicator Light or REV Blinkin): which color when |
| [`Action`](../reference/steps.md#action) | steps across several parts that never block (never make the loop wait) |
| [`Mode`](../reference/steps.md#modes) | a rule a module follows every loop while the mode is on |
| [`ControlMap`](../reference/controls.md#controlmap), [`Pad`](../reference/controls.md#controlmap) | `Controls.java`: the TeleOp and the button map |
| [`Auto`](../reference/steps.md#auto), [`Steps`](../reference/steps.md#auto) | one autonomous as a list of steps |
| [`Feature`](../reference/robot-java.md#what-your-robot-can-do) | what the library does by itself for your robot, ready or not (`robot.features()`) |
| [`EncoderMotor`](../reference/robot-java.md#robotdefinition) | one motor for the [Encoder Check](../tuning/02-wiring-checks.md#encoder-check-each-encoder-cable-in-its-own-port) (`robot.encoderMotors()`) |
| [`Toggleable`](../reference/controls.md#controlmap) | anything one button switches on and off |
| [`Motors`](../reference/robot-java.md#motors) | look up the encoder counts and speed of common motors, by catalog id (a part's name in the [parts catalog](../guides/parts-catalog.md), e.g. `"gobilda-5203-312"`) |
| [`Shape`](../reference/robot-java.md#shapes-and-parts), [`Parts`](../reference/robot-java.md#shapes-and-parts) | how each part looks and where it sits, for the simulator; wheels, rollers and servos by catalog id |
| [`Starting`](../reference/tunables.md#default-and-measure) | `DEFAULT` and `MEASURE`, the two special values in `Tunables.java` |
| [`Zone`](../reference/parts/interlock.md) | a part's zone for an interlock rule: `lift.zoneAbove()`, `turret.zone()` |
| [`Module`](../reference/robot-java.md#module), [`MotorPositions`](../reference/parts/lift.md), [`ServoPositions`](../reference/parts/claw.md), [`Devices`](../reference/robot-java.md#module) | what the modules share (you rarely use these by name) |


## Where each section went

| Old section | New page |
| --- | --- |
| Drive | [Drive](../reference/parts/drive.md) |
| Odometry | [Odometry](../reference/parts/odometry.md) |
| PathFollower | [PathFollower and PathAt](../reference/parts/path-follower.md) |
| PathAt | [PathFollower and PathAt: PathAt](../reference/parts/path-follower.md#pathat) |
| Camera | [Camera](../reference/parts/camera.md) |
| Aim | [Aim](../reference/parts/aim.md) |
| Intake | [Intake](../reference/parts/intake.md) |
| Intake: buttons to add | [Intake: Buttons to add](../reference/parts/intake.md#buttons-to-add) |
| Lift | [Lift](../reference/parts/lift.md) |
| Two motors on one lift or arm | [Lift: Two motors on one lift or arm](../reference/parts/lift.md#two-motors-on-one-lift-or-arm) |
| Lift: buttons to add | [Lift: Buttons to add](../reference/parts/lift.md#buttons-to-add) |
| Arm | [Arm](../reference/parts/arm.md) |
| Arm: buttons to add | [Arm: Buttons to add](../reference/parts/arm.md#buttons-to-add) |
| Claw | [Claw](../reference/parts/claw.md) |
| Claw: buttons to add | [Claw: Buttons to add](../reference/parts/claw.md#buttons-to-add) |
| Wrist | [Wrist](../reference/parts/wrist.md) |
| Wrist: buttons to add | [Wrist: Buttons to add](../reference/parts/wrist.md#buttons-to-add) |
| Positioner | [Positioner](../reference/parts/positioner.md) |
| Positioner: buttons to add | [Positioner: Buttons to add](../reference/parts/positioner.md#buttons-to-add) |
| Turret | [Turret](../reference/parts/turret.md) |
| Turret: buttons to add | [Turret: Buttons to add](../reference/parts/turret.md#buttons-to-add) |
| Interlock | [Interlock](../reference/parts/interlock.md) |
| Launcher | [Launcher](../reference/parts/launcher.md) |
| Firing: shot modes, feed stop, piece path | [Launcher: Firing: shot modes, feed stop, piece path](../reference/parts/launcher.md#firing-shot-modes-feed-stop-piece-path) |
| Speed trim and battery compensation | [Launcher: Speed trim and battery compensation](../reference/parts/launcher.md#speed-trim-and-battery-compensation) |
| Launcher: buttons to add | [Launcher: Buttons to add](../reference/parts/launcher.md#buttons-to-add) |
| Indexer | [Indexer](../reference/parts/indexer.md) |
| Indexer: buttons to add | [Indexer: Buttons to add](../reference/parts/indexer.md#buttons-to-add) |
| Hood | [Hood](../reference/parts/hood.md) |
| Hood: buttons to add | [Hood: Buttons to add](../reference/parts/hood.md#buttons-to-add) |
| Pieces and PieceSensor | [Pieces and PieceSensor](../reference/parts/pieces.md) |
| Pieces: buttons to add | [Pieces and PieceSensor: Buttons to add](../reference/parts/pieces.md#buttons-to-add) |
| Lights | [Lights](../reference/parts/lights.md) |
| The alliance at INIT | [Lights: The alliance at INIT](../reference/parts/lights.md#the-alliance-at-init) |
| RobotDefinition | [Robot.java: RobotDefinition](../reference/robot-java.md#robotdefinition) |
| Tunables.java | [Tunables.java](../reference/tunables.md) |
| DEFAULT and MEASURE | [Tunables.java: DEFAULT and MEASURE](../reference/tunables.md#default-and-measure) |
| Your own code every loop | [Robot.java: Your own code every loop](../reference/robot-java.md#your-own-code-every-loop) |
| What your robot can do | [Robot.java: What your robot can do](../reference/robot-java.md#what-your-robot-can-do) |
| Parts not in the catalog | [Robot.java: Parts not in the catalog](../reference/robot-java.md#parts-not-in-the-catalog) |
| Parts not on the robot yet | [Robot.java: Parts not on the robot yet](../reference/robot-java.md#parts-not-on-the-robot-yet) |
| Shapes and parts | [Robot.java: Shapes and parts](../reference/robot-java.md#shapes-and-parts) |
| Motors | [Robot.java: Motors](../reference/robot-java.md#motors) |
| Module | [Robot.java: Module](../reference/robot-java.md#module) |
| Making the files | [Robot.java: Making the files](../reference/robot-java.md#making-the-files) |
| Action | [Steps for autos: Action](../reference/steps.md#action) |
| Modes | [Steps for autos: Modes](../reference/steps.md#modes) |
| ControlMap | [Controls.java: ControlMap](../reference/controls.md#controlmap) |
| Reading a button line | [Controls.java: Reading a button line](../reference/controls.md#reading-a-button-line) |
| Drive to a spot by itself | [Controls.java: Drive to a spot by itself](../reference/controls.md#drive-to-a-spot-by-itself) |
| Auto | [Steps for autos: Auto](../reference/steps.md#auto) |
| Keep time to park | [Steps for autos: Keep time to park](../reference/steps.md#keep-time-to-park) |
