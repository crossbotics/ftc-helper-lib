# Interview: the questions, in order

Ask 3-5 at a time. Each answer becomes a line in the robot file (shown after the arrow). Units: inches,
degrees, encoder counts. "Forward/left/up" is from the robot's centre on the floor.

## Size and start

1. How long (front to back) and wide (side to side) is the drive frame, and how tall is it? ->
   `robot.lengthIn`, `robot.widthIn`, `robot.chassisHeightIn`.
2. How tall is the whole robot at the start of a match (folded), floor to the highest point? ->
   `robot.heightIn`. The size rules use the SHAPES, not this number (it still decides which overhead
   field parts the robot bumps): if the shapes come out lower,
   a part is missing (add a `box` for the tallest fixed part: electronics, a hopper).
   **Robot.java in five files?** These answers go in Robot.java (`chassisMm(length, width, height)`,
   `heightMm(...)`, each part's `looks(...)`), not the robot file: see SKILL.md step 4.
3. How heavy is it (kg; a bathroom scale works: weigh yourself with and without it)? -> `robot.massKg`.
4. Where does it start a match (which tile, which way it faces)? -> `start.x`, `start.y`, `start.heading`.
   BIOBUZZ: inches from the field centre, +y away from the red drivers, heading 0 = facing away from them.

Check: a starting robot must fit an 18 in cube. A frame longer than 18 in is a measuring mistake.

## Drivetrain

5. Mecanum wheels or tank (normal) wheels? -> `drive.type`.
6. Which motor drives the wheels (seller part number, or "312 RPM Yellow Jacket")? -> `drive.part` (an id
   from the parts catalog; "sold out" in its notes does not matter for the simulator), or
   `drive.ticksPerRev` and `drive.maxTicksPerSec` if it is not there.
7. Wheel diameter (measure it: goBILDA 104 mm = 4.09 in, REV 75 mm = 2.95 in, 96 mm = 3.78 in)?
   -> `drive.wheelDiameterIn`. Any gears or belts between motor and wheel? -> `drive.gearRatio`.
8. Distance between the left and right wheel centres (track) and front and back wheel centres
   (wheelbase)? -> `drive.trackWidthIn`, `drive.wheelBaseIn`. (Defaults: `drive.gearRatio = 1`,
   `drive.strafeEfficiency = 0.85`; the Drive Tuner and Characterizer measure the real ones.)
9. Do your drive motor names and directions match RobotSettings? -> `drive.mirrored = left` (the
   default) or `right`: the side whose motors TeamCode sets to REVERSE (the code already makes up for any
   gears). Flip it if the code drives forward with NEGATIVE power (for example `left_stick_y` used
   without a minus sign). The check scenario's forward-stick test says whether it is right.

## Each mechanism motor (lift, arm, turret, intake, launcher)

10. What does motor NAME move? -> its kind: a lift or arm = `lift` (it has hard stops and gravity), a
    launcher wheel = `flywheel` (with `feeder=` the motor that feeds it), anything that just spins = `free`.
11. Which motor is it (part number / RPM)? -> `part=` (catalog), or `rpm=` and `ticksPerRev=`.
12. Lift/arm: its encoder counts at the bottom and top hard stops? The Motor Finder shows them (or
    `finder-results.txt`). -> `min=`, `max=`. How much power holds it still (0.05-0.2 typical, the
    Characterizer measures it; that tool finds the power a part needs at each speed)? -> `gravity=`.
13. **Lift: how tall is the robot with the lift all the way up, floor to the top?** This one number
    decides the 29 in rule. The shape's travel = that height - the lift's folded top. Then
    `ticksPerIn=` = (top count - bottom count) / travel (negative if the counts go down as it rises). Use `spoolIn=` only for a one-stage lift
    pulled straight by its spool: a cascade (multi-stage slide where each stage pulls the next) moves its
    top faster than its string.
14. Lift shape: how tall is one stage, and where does its bottom sit (forward/left/up from the robot's
    centre)? -> `size=` (ONE stage) and `at=`. A part riding on the lift: `on=lift`, `at=0,0,<stage height>`
    is the top of the moving stage.
15. Arm: what angle is it at the bottom stop, and at the top stop? Angles: 0 = level and pointing
    forward, +90 = straight up, -90 = straight down. -> `start=` (the angle at the `min=` count, usually the bottom stop), and either
    `ticksPerDeg=` = (top count - bottom count) / (top angle - bottom angle), or `gear=` = motor turns per
    arm turn (then the catalog's counts per motor turn x gear / 360 counts per degree). If the counts go
    DOWN as the arm goes up, `ticksPerDeg=` is negative. How long is it
    (hinge to tip), and how wide and thick? -> `size=length x width x thickness`.

Check: a lift that goes past 29 in at its top, or an arm that reaches past 24 in: tell the team (they
need hard stops), then make sure the shapes are right before blaming the robot.

## Servos

16. What does servo NAME move, and which servo is it (part number)? -> `part=`. Where does it start
    (0..1)? -> `servo.NAME = <start>`. A servo with no part moves at `servo.speed` (positions per second).
17. A claw: is it open or closed at small positions? -> on its claw shape, `pos0=` (gap at 0) and `pos1=`
    (gap at 1, inches). Its `size=` is palm depth (forward) x finger thickness x finger length: the
    fingers point UP from the palm (in the claw's own frame: on a wrist they turn with it) and open
    left-right. A wrist or arm on a servo: the angle at position 0 and at 1 -> `pos0=`, `pos1=` (same
    angle rule as arms; a wrist is an `arm` with `axis=left`, the default).

## Switches and sensors

18. Switch NAME: what presses it (a lift at its bottom?) -> `touch.NAME = <motor>, below=<counts>`.
19. Colour or distance sensor that watches game pieces: does it see pieces come in (intake) or the piece
    ready to fire? -> `color.NAME = at=intake|ready` (a REV Color Sensor V3), or `distance.NAME = at=...,
    acrossMm=<mm from the sensor to the far wall>` (a REV 2m Distance Sensor across a channel). Other
    sensors (colour or distance used for something else, a second webcam for colours): the simulator does
    not have them yet. Say so, and name the OpModes that use them.

## Cameras

20. Each webcam / Limelight: where is its lens (forward, left, up from the robot's centre, inches),
    turned left or right (yaw: + = left), tilted up or down (pitch: + = up, so "15 down" = -15)? ->
    `camera.KEY = name=..., forward=, left=, up=, yaw=, pitch=` or `limelight.KEY = ...` (KEY is any short
    word; `name=` must be the configuration name). A Logitech C920 sees about 70 degrees across
    (`hfov=70.4`); a Limelight 3A 54.5. On a turret (or arm)? Add `on=turret` (the shape's name) and
    measure with the turret pointing straight forward: then it turns with the turret.

## Game pieces

21. How does the robot pick up pieces (a roller? a claw?), which way of the motor pulls them in, and how
    many can it hold? -> `collect.` with `motor=` (or `crservo=` for a CR servo), `inward=-` if negative
    power pulls in, `holds=` (BIOBUZZ allows at most 4); or `grab.` for a claw. "Negative" is AFTER the
    code's `setDirection`: a CR servo set to REVERSE that the code runs at +1 to pull in turns at -1, so it
    needs `inward=-`. The intake shape must stick out past the chassis box (`robot.lengthIn`), or a piece on
    the tiles is pushed away by the box before it reaches the roller. Side spinners on the same motor
    take pieces in too: `shape=intake+left_spinner+right_spinner`.
22. How does it score (shoot? lift and drop?) -> `shoot.` (`angle=` degrees up from forward, `share=`
    of the wheel's rim speed, about 0.5) or `grab.` with the lift/arm shapes. Where is the middle of the
    launcher wheel (forward/left/up)? -> its `flywheel` shape's `at=` (a roller or flywheel's `at=` is its
    centre). What feeds it? -> `feeder=` on the flywheel motor (a motor, a CR servo, or a servo that pushes: it feeds while it is more than 0.15 from where it starts). With CAD, make a
    shot table (`cad.md` step 6). A catapult or spring launcher: make its motor a `flywheel` with
    `feeder=` itself and give the shot a fixed `speed=` (in/s, from a filmed shot).
    A hood on a servo that tilts the shot? -> `hood=SERVO, hoodAt0=A, hoodAt1=B` on the `shoot.` line: the
    shot angle (degrees up from forward) with that servo at position 0 and at 1 (measure the angle at the
    hood's two stops and draw the straight line through them); add its `servo.` line too.

## Odd answers

- A number that breaks a rule (taller than 18 in at the start) is more often a misread than a real
  robot: ask again, from a different angle ("measure from the floor to the top of the highest part").
- Two answers that disagree (TeamCode says 312 RPM, the team says 435): ask which is on the robot now.
