# The robot file

The robot file (`simulator/robot.properties` in your robot project) describes your robot to the simulator.
The simulator makes most of it from `Robot.java` ([Your robot file](../simulator/02-your-robot-file.md)).
Every line is `name = value`. Text after `#` (after a space) is a note.

## The commands

Run every command from the `ftc-helper-lib` folder. A `-P` word after the command is an option:
`-Pname=value` gives a setting, and a few options (like `-Plan`) are only on or off.

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
./gradlew :sim:run -Probot=sim/robots/my-robot.properties -Plan
```

`-PteamCode=` points at your robot project's TeamCode. Without it, a command finds `../TeamCode` when the
library sits in a robot project. `-PteamCode=none` runs the library's own examples. With `-Plan`, anyone
on that WiFi can open the page and run OpModes. Use it on a team or home network, not on open school WiFi.

| Command | Does | Options |
| --- | --- | --- |
| `./gradlew :sim:run` (the **Simulator** button) | the simulator page; first makes the robot file from `Robot.java` | `-Probot=FILE` (default: your `simulator/robot.properties`, else the library's example), `-Plan` (phones on the WiFi), `-Pport=8781` (another port, to run two at once; default 8780), `-PteamCode=DIR`, `-PnoFromRobot` (use the robot file as it is), `-Popen` (open the page in the browser, as the button does) |
| `./gradlew :sim:scenario -Pfile=a.sim` (the **Simulator tests** button: no `-Pfile`) | runs test scripts ([Scenario files](scenario-files.md)); with no `-Pfile` in a robot project: every test in `simulator/scenarios`; first makes the robot file from `Robot.java` | `-Pfile=a.sim,b.sim` (a comma list) or a folder (every `.sim` in it), `-Probot=`, `-PteamCode=`, `-PnoFromRobot` |
| `./gradlew :sim:fromRobot` | only makes the robot file from your `Robot.java` (`:sim:run` and `:sim:scenario` do it first) | `-ProbotClass=` (which class), `-Pout=FILE`, `-Pfield=NAME` (default `biobuzz-2026`) |
| `./gradlew :sim:updateLibrary` (the **Update library** button) | switches the library folder to its newest release and prints what changed; commits nothing ([Keep it up to date](../02-keep-up-to-date.md#update-the-update-library-button)) | `-PlibVersion=v0.20.1` (a specific version, or back) |
| `./gradlew :sim:installButtons` | adds the **Simulator** and **Simulator tests** buttons to Android Studio now (a Gradle sync in Android Studio adds them too) | |
| `./gradlew :sim:fromConfig -Pconfig=FILE.xml` | a first robot file from a robot configuration | `-Pout=FILE`, `-Pfield=NAME` |
| `./gradlew :sim:newRobot -Pconfig=FILE.xml` | the five files (Robot.java, ...) from a configuration | `-PteamCode=DIR`, `-Ppackage=` (default `org.firstinspires.ftc.teamcode`) |
| `./gradlew :sim:fit` | fits Characterizer logs ([Real speeds](../simulator/03-make-it-match-your-robot.md#6-real-speeds-optional)) | `-Plog=`, `-Probot=`, `-Pwrite`, `-Pforce` |

**Other libraries in TeamCode** (Road Runner, FTC Dashboard...): put their Gradle lines in
`simulator/sim-dependencies.gradle` in the robot project. Pedro Pathing needs none: it comes with the
library's `pedro` module.

```groovy
repositories { maven { url = 'https://maven.brott.dev/' } }
dependencies { testImplementation 'com.acmerobotics.dashboard:dashboard:0.4.16' }
```

## An example

```properties
field = biobuzz-2026                  # the season's field (sim/fields/biobuzz-2026.json)
field.model = fields/biobuzz-2026.glb # optional: the field's 3D model for the 3D view (path from this file)
robot.lengthIn = 18                   # size, for bumping into field elements
robot.widthIn = 18
robot.heightIn = 18                   # the chassis box with no shapes, and which overhead field parts it bumps (with shapes, the shapes decide the size rules)
robot.tallestIn = 12                  # optional: a part the shapes do not draw reaches this high (size rules count it)
start.x = -56                         # where it starts (inches from the field center; y away from the drivers)
start.y = -61.55                      # BIOBUZZ G304: touching the wall, outside the LOADING ZONE
start.heading = 0                     # degrees, counterclockwise; 0 = facing away from the drivers
drive.type = mecanum
drive.motors = front_left, front_right, back_left, back_right
drive.part = gobilda-5203-312         # from the parts catalog: speed, encoder counts, current
drive.wheelDiameterIn = 3.90          # the REAL wheel (the Drive Tuner should find it)
drive.gearRatio = 1                   # motor turns per wheel turn (default 1)
drive.strafeEfficiency = 0.85         # mecanum: how much of a strafe really happens (default 0.85)
imu = imu
motor.lift = lift, part=gobilda-5203-312, min=0, max=2750, gravity=0.06
motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder, shotDrop=0.15   # feeder: a motor, CR servo or servo
servo.claw = 0.5, part=gobilda-2000-torque
crservo.feeder =                      # the flywheel's feeder (a CR servo here)
camera.front = name=Webcam 1, forward=7, up=12, pitch=20   # see "Cameras"
touch.lift_bottom = lift, below=20
```

## Every setting

| Line | Default | Means |
| --- | --- | --- |
| `field = NAME` | `biobuzz-2026` | the field file in `sim/fields/` |
| `field.model = FILE` | the library's model of the field (`sim/fields/<field>.glb`, BIOBUZZ has one) | the field's 3D model for the 3D view (path from the robot file) |
| `parts = FILE.json` | none | your own parts catalog, added to the library's ([parts catalog](../guides/parts-catalog.md)) |
| `battery = V` | 12.8 | battery volts at the start (the page's slider: 11 to 13.5) |
| `robot.lengthIn`, `robot.widthIn` | 18 | the footprint, for bumping into field parts |
| `robot.heightIn` | 18 | the chassis box with no shapes, and which overhead field parts it bumps |
| `robot.chassisHeightIn` | 4 with shapes, else `heightIn` | the height of the chassis box |
| `robot.tallestIn` | none | a part the shapes do not draw reaches this high (the size rules count it) |
| `drive.type` | `none` | `mecanum`, `tank` (2 or 4 motors) or `none` (a test rig) |
| `drive.motors = a, b, ...` | none | the wheel motors |
| `drive.part` | none | the wheels' motor from the catalog |
| `drive.wheelDiameterIn` | 3.78 | wheel size |
| `drive.trackWidthIn`, `drive.wheelBaseIn` | 15, 13 | left-right and front-back wheel spacing |
| `drive.mirrored` | `left` | which side's motors are mounted mirrored (turn the other way for the same power) |
| `drive.strafeEfficiency` | 0.85 | how much of the asked strafe the robot makes (mecanum) |
| `drive.kS`, `drive.accelSec`, `drive.turnDegPerCount` | 0, 0.15, worked out | from the Characterizer ([Real speeds](../simulator/03-make-it-match-your-robot.md#6-real-speeds-optional)) |
| `imu = NAME` | none | the IMU's name |
| `missing = a, b` | none | devices described in this file but NOT in the configuration: a part not on the robot yet ([Your robot file](../simulator/02-your-robot-file.md#5-measure-numbers-and-missing-parts)) |
| `odometry = KIND NAMES` | none | pretend odometry from the robot's true motion: `pinpoint NAME`, `otos NAME`, `threeWheels L, R, P` or `twoWheels PAR, PERP` ([odometry](../modules/odometry.md#the-simulator)) |
| `odometry.offsetsMm = X, Y` | 0, 0 | Pinpoint: where the pods sit (X pod mm left, Y pod mm forward); sets the raw pod counts the Odometry Tuner's Spin test reads |
| `odometry.mountIn = F, L, H` | | OTOS: where it sits (written from Robot.java); the pretend OTOS reports the center's pose, so it does not change the simulation |
| `odometry.trackWidthIn` | 12 | three dead wheels: left to right pod, inches |
| `odometry.parallelLeftIn`, `odometry.perpForwardIn` | 0, 0 | dead wheels: the forward pod's inches left (two wheels), the sideways pod's inches forward |
| `odometry.ticksPerIn` | 505.3 | dead wheels: pod counts per inch (goBILDA 4-bar pods) |
| `odometry.reversed = names` | none | these pods count backward (as wired; the code's `reversed(...)` turns them back): dead-wheel port names, or `x`/`y` for a Pinpoint's pods |
| `odometry.scaleError` | 0 | noise: 0.01 = it measures 1% too far (wheel slip, a wrong pod size) |
| `odometry.driftDegPerMin` | 0 | noise: the heading creeps this many degrees per minute |
| `motor.NAME = KIND, ...` | | a motor: `free`, `lift` or `flywheel`, then `part=`, `ticksPerRev=` (catalog, else 537.7 or 28 for a flywheel), `rpm=` or `maxTicksPerSec=` (catalog, else 2780), `min=`, `max=` (counts), `gravity=` (0), `kS=` (0), `accelSec=`, `coastSec=`, `feeder=` (a motor, CR servo or servo that feeds a flywheel), `feederPush=` (a servo feeder: where it goes to push; a push counts once it is half way there. Without it, once it is 0.15 from its start. `:sim:fromRobot` writes it from `Tunables` `push`), `shotDrop=` (0.15: how much a shot slows the wheel), `stage=CRSERVO` (a feed wheel that brings the pieces to a servo `feeder`: a push sends a piece only if this wheel ran at least 0.05 s before the arm started to move out; `:sim:fromRobot` writes it for [`feedWheel(...)`](../reference/robot-java.md)), `stop=SERVO`, `stopOpen=` (0.5) (a feed stop: pieces get through only while that servo is within 0.05 of `stopOpen`; `:sim:fromRobot` writes it for [`feedStop(...)`](../reference/parts/launcher.md#firing-shot-modes-feed-stop-piece-path)), `drivenBy=CRSERVO` (no motor on this port: only an encoder, on a shaft that CR servo turns, e.g. a CR servo turret's Through Bore), `with=FIRST` (a second motor on the same slide or arm as the motor `FIRST`: both turn one shaft and both push, so each carries half its `gravity`; `mirrored=true`: its shaft turns the other way, two motors facing each other. `:sim:fromRobot` writes it for [`alsoMotor`](../reference/parts/lift.md#two-motors-on-one-lift-or-arm)) |
| `servo.NAME = START, ...` | start 0.5 | a servo: `part=` (catalog), or `rangeDeg=` and `secPer60=` for one not in the catalog. `pwm=500-2500`: the robot's code uses the servo's full range (`.fullRange()`; `:sim:fromRobot` writes it); the start position and every position in the file are then in that range. Like the hub, the simulated servo gets the normal 600-2400 us at each INIT and the code's `setPwmRange` after, so 0 to 1 turns a goBILDA 2000 servo 270 degrees, or 300 with full range |
| `servo.speed` | 1.0 | positions per second for a servo with no part |
| `crservo.NAME =` | | a continuous servo (the value is not used) |
| `light.NAME = gobilda` or `blinkin` | | a colored light ([`Lights`](../reference/parts/lights.md)): a goBILDA RGB Indicator Light (a servo) or a REV Blinkin; the page shows its color on the robot, on the 3D view and in the Servos tab |
| `touch.NAME = MOTOR, below=N` | below 20 | a switch pressed while that motor is within N counts of its `min` (reads `getState()` false when pressed) |
| `touch.NAME = MOTOR, at=C, width=W` | width 10 | a switch part way along: pressed while that motor is within W counts of C (counts from where it starts), e.g. a turret's home switch |
| `analog.NAME = MOTOR, offsetDeg=D` | offsetDeg 0 | an absolute encoder on that motor's shaft (0 to 3.3 V per turn), reading D degrees at the start: a CR servo turret's angle sensor |
| `color.NAME = at=ready` | | a color sensor with distance (REV Color Sensor V3) that sees the piece at one spot: `at=ready` (next to fire) or `at=intake` ([game pieces](#game-pieces)) |
| `distance.NAME = at=intake, acrossMm=150` | `acrossMm=150` | a distance sensor across a channel: `acrossMm` minus the piece's size when one is there ([game pieces](#game-pieces)) |
| `camera.NAME = ...` | | a webcam or Limelight ([cameras](#cameras)); `on=turret`: it turns with the turret |
| `huskylens.NAME = ...` | | a HuskyLens ([cameras](#cameras)); `taught=` says what it was taught |
| `shape.NAME = TYPE, ...` | | a part to draw and check ([robot shapes](#robot-shapes)). Sizes when `size=` is left out: slide 2x2x12, arm 10x2x2, turret 8x8x2, claw 3x1x3, roller 2x12x2, flywheel 4x2x4 |
| `collect.NAME = shape=a+b, motor=M, holds=N` | `reach=0.75`, `holds=4` | takes pieces in ([game pieces](#game-pieces)); `crservo=` instead of `motor=`, `inward=-`; `limits=NECTAR:0+POLLEN:3`: the most of one kind (`0` = never that kind; a name matches every kind that contains it). Preload pieces that do not fit wait on the tiles behind the robot |
| `shoot.NAME = shape=S, ...` | `angle=40`, `share=0.5` | shoots pieces: `spin=`, `speed=` (a fixed speed, in/s), `table=` (a shot table, path from the robot file), `flywheel=` (which flywheel, when there are two), `hood=SERVO, hoodAt0=A, hoodAt1=B` (the angle follows a hood servo: A with the servo at 0, B at 1) |
| `grab.NAME = shape=S, servo=V, closedBelow=X` | `reach=0.5` | a claw that holds a piece (`closedAbove=` for one that closes toward 1) |
| `start.x`, `start.y`, `start.heading` | | where the robot starts |
| `preload = on/off` | on | the field file's preload in the robot at the start |
| `physics = engine/simple` | engine | the physics engine, or the old "a bump stops the move" rule |
| `tunable.SECTION.FIELD = N` | none | a stand-in for a Tunables number written `MEASURE`: the simulator uses N in its place ([MEASURE in the simulator](../simulator/02-your-robot-file.md#5-measure-numbers-and-missing-parts)) |

A flywheel's rim speed comes from its `flywheel` shape. The diameter is the larger of the shape's
forward and up sizes.

## Robot shapes

The simulator can draw the robot as simple shapes: the chassis box, plus parts from a short menu, each
moved by its motor or servo. The 3D view draws them. The simulator checks the size rules with them:
the 18 in start cube, and 18 x 24 in and 29 in tall (R105), now and **at full reach** (every moving part
tried over its whole range). The Control Hub panel shows the sizes in green or red.

```properties
robot.chassisHeightIn = 4
# shape.NAME = TYPE, on=PARENT, at=forward,left,up, size=forward x left-right x up, then what moves it
shape.lift = slide, at=-7,0,4, size=2x8x8, axis=up, motor=lift, ticksPerIn=344
shape.arm = arm, on=lift, at=0,0,8, size=9x1.5x1.5, axis=left, motor=arm, gear=3.5, start=-90
shape.wrist = arm, on=arm, at=9,0,0, size=2.5x1.5x1.5, servo=wrist, pos0=-90, pos1=90
shape.claw = claw, on=wrist, at=2.5,0,-1.5, size=2x0.5x3, servo=claw, pos0=0, pos1=3
shape.intake = roller, at=8,0,1.5, size=2x14x2, motor=intake
shape.flywheel = flywheel, at=-1,0,4, size=4x2x4, motor=flywheel
```

Any shape can also take `tilt=DEG`: mounted at an angle on its parent, turned about the left-right line,
front up +. A roller can take `reversed=yes`: it turns the other way from its motor, through gears. It can
also take `soft=yes`, for soft tubing or flaps that bend. Game pieces touch a soft roller, but the field
does not stop it, and it still counts for the size rules.

| Type | What it is | Where `at` points | Moves by |
| --- | --- | --- | --- |
| `box` | a part that does not move | the middle of its bottom | nothing |
| `slide` | a lift or extension: a fixed stage and a moving stage along `axis` (up, down, forward, back, left, right); `size=` is ONE stage | the bottom of the fixed stage | motor: `ticksPerIn=` or `spoolIn=` (spool diameter, one-stage only: a cascade's top moves faster than its string); servo: `pos0=`/`pos1=` inches |
| `arm` | turns about a hinge; `axis` is the hinge line (`left`, the default: + raises the front; 0 = level forward, +90 up, -90 down) | the hinge; the box reaches forward from it | motor: `ticksPerDeg=`, or `gear=` (motor turns per arm turn: counts per degree = the motor's counts per turn x gear / 360), and `start=` degrees at the bottom stop; servo: `pos0=`/`pos1=` degrees |
| `turret` | turns about up (+ = left) | the middle of its bottom | like `arm` |
| `claw` | two fingers: `size=` palm depth x finger thickness x finger length; the fingers point up from the palm (in the claw's frame) and open left-right | the middle of the palm | servo: `pos0=`/`pos1=` the gap in inches |
| `roller` | an intake roller (a cylinder along `axis`, default across the robot) | its center | spins with its `motor=` or `crservo=` |
| `flywheel` | a launcher wheel | its center | spins with its `motor=` |

Notes:
- `passive=yes` (arm or slide): gravity or springs move it, not the code (a floating intake, a sprung
  flywheel mount). The simulator draws it at `start=`. The size rules try its whole range (`minDeg=`/`maxDeg=`
  or `travel=`). `drop=SERVO>0.6` (or `<`) with `fallsTo=`: once that servo passes the position, the part
  falls to that end and stays. An example is an intake folded up at the start and pushed over at ▶. A new
  match puts it back. `Robot.java` writes these with `Shape.passive()` and `Shape.dropsWhen(...)`
  ([robot module](../reference/robot-java.md#shapes-and-parts)).
- `on=` puts a part on another (default: the chassis). A part on a slide rides on its moving stage
  (`at=0,0,<stage length>` is the moving stage's top). A part on an arm turns with it. Its `at=` is from the
  hinge, so `at=9,0,0` is the tip of a 9 in arm.
- Directions are the robot's: forward, left, up. `at=` is in inches from the parent's mounting point.
- The range of motion comes from the motor's hard stops (`min=`/`max=` on `motor.NAME`), the servo's
  `pos0`..`pos1`, or `travel=` (slide) / `minDeg=`, `maxDeg=` (arm, turret). The size check at full reach
  uses it. A real robot needs hard stops there too: inspectors check the biggest it can get.
- With `physics = engine` (the default), the shapes' boxes also bump into the field and push game pieces.
- Mistakes stop the simulator with the line's name:
  - an unknown parent, motor or servo.
  - a loop.
  - a moving part with no range of motion. Give the motor `min=` and `max=`, or `travel=` / `minDeg=`
    `maxDeg=`.
  - a word with no `=` after the first one. `at=-7,0,4` is fine: it is a list of numbers.
- A `#` after a space starts a note. `color=#ff0000` is a color, not a note.
- Nothing may go below the tiles. The panel and `expect size floor ok` show it. A part there would hit
  the floor on a real robot, so the shapes or the hard stops are wrong.

Scenario checks: `expect size start ok` (fits the 18 in cube now), `expect size match ok` (18 x 24 x 29
now), `expect size most ok` (every pose at full reach), `expect size floor ok` (nothing below the tiles
now). `fail` instead of `ok` checks the opposite. Also `print size`, and `print cad` (see CAD models below).

How the simulator checks "at full reach": it tries every combination of the moving parts' important
values. A slide or claw is biggest at an end of its range. The simulator tries a hinge (arm, turret) at both ends, at
each multiple of 90 degrees, and every few degrees between. That is every 5 degrees or less with a few
moving parts, and coarser with many, to stay under 50,000 poses. The panel shows the worst pose. It is a
careful check, not a proof: leave a little margin, and measure the real robot at its hard stops.

## Game pieces

Which robot shapes handle the pieces. `:sim:fromRobot` writes these lines from Robot.java.

```properties
# take in a piece within 0.75 in of the intake shape while motor "intake" turns inward (power above 0.2;
# inward=- if negative power takes in; crservo= instead of motor=); it holds 4. Several shapes with +
# (shape=intake+left_spinner+right_spinner): a piece comes in at any of them
collect.intake = shape=intake, motor=intake, holds=4
# each flywheel shot (motor.flywheel = flywheel, feeder=feeder) sends a held piece off this shape.
# The feeder is a motor or CR servo (a piece each time it starts; one more every 0.3 s from 0.45 s on
# while it keeps running), or a servo that pushes
# (an indexer: it feeds while it is more than 0.15 away from where it starts). The shot leaves
# 45 degrees up from the shape's forward (angle= defaults to 40), at half the wheel's rim speed
shoot.launcher = shape=flywheel, angle=45, share=0.5
# the claw holds a piece in its fingers while servo "claw" is below 0.3, and drops it when it opens
# (a claw that closes toward 1: closedAbove=0.5 instead)
grab.claw = shape=claw, servo=claw, closedBelow=0.3
```

A robot with a `collect.` or `shoot.` line starts with the field file's preload inside it. BIOBUZZ: 4
POLLEN, and `holds=4` is the game's rule (no more than 4 at a time). A robot with only a `grab.` line gets
the preload on the tiles, touching its back. A robot with none of the three gets none. With a `shoot.`
line, a shot needs a piece: no piece, no shot. A piece that comes into a goal (a FLOWER top) scores. It
drops onto that FLOWER's stack and stays there ([Game rules and the score](../guides/how-the-simulator-works.md#game-rules-and-the-score)).
"Back to start" puts the pieces back. The Control Hub panel shows what the robot holds, what it shot and
what scored. The 3D view and the field from above draw the pieces.

**Piece sensors** (for [`Pieces` and `PieceSensor`](parts/pieces.md)):

```properties
# a color sensor at the "ready to fire" slot: it sees the next piece to fire (the oldest held) in the
# field file's color, 15 mm away; nothing there: dark gray, 120 mm
color.ready_color = at=ready
# at=intake: it sees each piece the intake takes in, for 0.25 s
color.intake_color = at=intake
# a distance sensor across a 150 mm channel: 150 minus the piece's size (reads 79 mm with a POLLEN, 59 mm with a NECTAR)
distance.intake_distance = at=intake, acrossMm=150
```

## Cameras

```properties
# name = the name in the robot configuration. Where it sits: inches forward/left/up from the robot's
# center on the floor; yaw (+ = turned left) and pitch (+ = tilted up) in degrees. hfov = how wide it
# sees, in degrees (Logitech C920 at 640x480: about 70; C270: 42.5). Defaults: 0, 0, 10, 0, 0, 70.4, 640, 480.
camera.front = name=Webcam 1, forward=7, left=0, up=12, yaw=0, pitch=20, hfov=70.4, width=640, height=480
```

For a robot in five files, `:sim:fromRobot` makes this line from Robot.java. Its `pitch` is the camera's
`pitchDeg` in Tunables when you wrote one, else 30 (a guess). Change `forward`, `up` and `hfov` to your
robot's.

**Limelight 3A**: the same settings with `limelight.` (hfov default 54.5, from its spec sheet).

```properties
limelight.ll = name=limelight, forward=6, left=-3, up=14, yaw=0, pitch=25
```

**HuskyLens**: the same settings with `huskylens.`. It gives boxes in its 320 x 240 screen. The hfov default
60 is a guess: use the number you measured. `taught=` lists what you taught it, in order: tag numbers, or
piece names from the field file for colors and objects. `-` skips a number.

```properties
huskylens.h = name=huskylens, forward=6, up=12, pitch=30, hfov=60, taught=34|35|36|37
huskylens.p = name=piececam, forward=8, up=8, pitch=-15, taught=POLLEN|NECTAR
```

**A camera on a turret** (or an arm or slide): add `on=` and the shape's name. The camera then turns
(and moves) with that part. Measure forward/left/up/yaw with the turret pointing straight forward (its
angle 0). `:sim:fromRobot` writes `on=turret` by itself when Robot.java has a turret that
`follows(camera)` (without `cameraOnBody()`). It also puts the flywheel on the turret, so the shots go
where the turret points. Without `on=`, the camera stays fixed to the chassis while the turret turns. The
turret then turns to its limit, or stops off the goal while it says AIMED.

```properties
shape.turret = turret, at=0,0,8, size=6x6x2, motor=turret, ticksPerDeg=7.467
limelight.ll = name=limelight, forward=6, left=0, up=12, yaw=0, pitch=30, on=turret
```

**Pretend venue lighting** lets you try the library's [lighting test](../modules/vision.md#venue-lighting).
`goodPipelines=1|2` on a `limelight.` line finds tags only on those pipelines. `goodExposureMs=4-10` on a
`camera.` line finds tags only with a manual exposure in that range. Put 0 in the range for the camera's
own automatic exposure. Without them, every setting finds tags.

```properties
limelight.ll = name=limelight, forward=6, up=14, pitch=25, goodPipelines=2
camera.front = name=Webcam 1, forward=7, up=12, pitch=20, goodExposureMs=5-8
```

## CAD models

```properties
cad.units = mm                 # the unit in the export window: mm, cm, m or in (required)
cad.axes = y,-x,z              # the CAD axes that point the robot's forward, left and up (required)
cad.origin = 0,0,0             # the robot's center on the floor, in CAD units (default 0,0,0)
robot.model = cad/chassis.stl  # drawn in place of the chassis box
shape.lift = slide, ..., model=cad/lift-stage.stl, baseModel=cad/lift-base.stl
shape.arm = arm, ..., model=cad/arm.stl
```

- `cad.axes`: look at the CAD's axis triad. Onshape and SolidWorks have up = z (if the robot's front
  points along +y: `y,-x,z`). Fusion usually has up = y (front along +x: `x,-z,y`). A minus sign means
  the other way. The simulator refuses a choice that would draw the robot mirrored.
- `model=` rides the moving part: a slide's moving stage, an arm or turret, a claw's palm (its fingers
  stay boxes, so the gap shows), a roller (it spins), a box. `baseModel=` is a slide's fixed stage.
- `cadPose=` on a shape: the value (inches or degrees) that the part is at in the CAD, when that is not
  the start.
- A roller's or flywheel's model must hold only the parts that spin (wheels, hubs, shaft). The size
  rules count a spinning model as the whole circle it sweeps. So a servo bracket in it would make the
  robot look bigger than it is.
- When the `robot.model` CAD already draws a fixed `box` shape (a tower, a hood), you can delete the box:
  the CAD now decides the size there. Keep the box only if it should bump into things on its own.

**5. Check it.** `print cad` in a scenario prints the settings in words. Then it prints each model next to
the box it covers: its size, and how far its middle is from the box's. "FAR from its box" means that
`cad.origin`, `cad.axes` or the shape's `at=` is wrong (for `robot.model`, almost always `cad.origin`).
"25 times smaller" means that `cad.units` is wrong. Then make each `size=` close to its CAD size, because
the boxes are what bump into things. Look at the 3D view too.

- The simulator can read up to 12,000,000 triangles in all. It simplifies a model with more than 300,000
  for the 3D view: small details like bolt threads blur, and `print cad` says so. The size rules use each
  model's outside (its convex hull), plus a small margin (about 1/200 of the part's size). So they never
  call a robot smaller than its CAD. A roller's model counts as everything it reaches while it spins.
- CAD from a seller (a kit's STEP or Onshape files) belongs to the seller. Keep it in your team's
  repository, not in a public one, unless the seller's terms allow it.

## Physics

```properties
robot.massKg = 15        # the robot's mass (default 15)
drive.grip = 1.0         # how hard the wheels can push, as a share of the robot's weight (about 1 on tiles)
physics = engine         # the default; "simple" = the old rule: a bump stops the whole move, no sliding
```
