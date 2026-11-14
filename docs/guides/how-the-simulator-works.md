# How the simulator works

The inside of the simulator: how it moves the robot, how it plays the game, how it judges shots, and
what it does not simulate. To use it, start with [Simulator](../simulator/README.md).

## Not simulated

Phone cameras, and a webcam's colors (`PieceSensor.camera`). The simulator has color and distance sensors
only as [piece sensors](#game-pieces). The Shot and Aim tuners work without a camera when `createCamera()`
returns null. The physics is a model: you must still find the real numbers (gains, speeds, shot tables)
on the robot.

Differences to know:

- The screen here updates as fast as the OpMode sends. A real Driver Station redraws about 4 times a
  second, so a flickering line looks steadier there.
- The loop runs faster than on a robot, and hardware answers at once. So code that counts loops, instead
  of using time, can act differently on the robot.
- The IMU never drifts, and it ignores how the hub is mounted, so the simulator does not check your
  `.imu(...)` line. On a robot, a wrong line can make pitch, roll and the turn speed wrong, and it can start
  the yaw at a wrong angle. So check it on the real robot. With the Hardware Check, turn left, and yaw goes
  UP. In TeleOp with field-centric, stick up goes away from you.
- STOP works like on a robot: a `sleep()` ends at once. An OpMode that ignores STOP for 3 s loses the
  hardware: restart the simulator to run another.
- Each INIT here forgets `MatchMemory.chosen()` and the start spot of the last program. So the simulator
  does not put the robot on an old program's spot. A real robot keeps them until a program sets them. So
  your own OpMode must not read them before it has set them. The library's autonomous and TeleOp do this
  for you.

## The page

**Control Hub** (right):

- the field from above, with the drivers at the bottom. To move the robot, drag it on the field, or type
  **x**, **y** and **heading**. Use the **Face** buttons, and tick **Snap** to go against a wall and line
  up with the tiles. These are the same controls as the auto builder's start spot, in the numbers that
  `s.start` takes.
- the **Push the robot** buttons, folded. They roll the robot like a hand push, also while a program runs.
  So its wheels and odometry feel it: the Drive Tuner's push test uses this.
- **Start over** (Back to start, Reset field).
- every motor: power, count, speed, current, and "STOP" when it is at a hard stop.
- every servo: where it is and where it is going.
- the switches: click a switch to hold it pressed.
- the cameras and the tags they see.
- the battery voltage. Lower it to see a weaker robot.

**Path lines** (on the field map and in the 3D view): an orange dashed line is the path that a Pedro
`follow` step drives (the plan). A green line is where the robot actually went in its last 30 seconds of
driving (the trail). The plan stays, faded, after its step ends, so you can compare the two. **Clear
lines** clears both. Untick **Path lines** to hide them. More: [Pedro module](../modules/pedro.md#the-simulator).

The robot is a gray box from the robot file (`robot.lengthIn` x `widthIn` x `chassisHeightIn`), with a
white arrow at the front. It turns red when it bumps into something. Its shapes ([Robot shapes](../reference/robot-file.md#robot-shapes)) move with their
motors and servos. Rollers and wheels have dark stripes, so you see them turn, and which way. A roller
glows **green** while it takes pieces in, and **orange** while it pushes them out. A flywheel or other
spinner glows **yellow** while it turns. A passive part (moved by gravity or springs) **blinks yellow**
while it moves.

The page builds the field from the field file's shapes (tiles, walls, tape, solid and overhead parts).
Each AprilTag is a square with its ID. With a 3D model of the field, the page shows that model
instead, with the tag squares on top. The library has BIOBUZZ's model, made from FIRST's field CAD, and a
robot file can name its own with `field.model`. "Field CAD model" in the **View** menu switches between
them. Each camera shows as a cone (webcam yellow, Limelight green). "Camera view" in the View menu shows a
camera's picture in the corner (pick the camera when there are two). The page redraws it 15 times a
second, smaller than the screen, so it costs little. The game pieces are striped balls, so you can see
them roll and spin. A tipping HIVE turns smoothly. With a rules file, the simulator turns it over its
`tipSeconds`, and the page plays the same turn. Without one, the simulator tips it at once, and the page
still turns it over half a second. The 3D library (three.js, MIT license) comes with the simulator, so the
page works without the internet. On a slow computer, untick "Show the 3D view" in the View menu.

The toolbar over the 3D view has the ways to look on the left. **View** sets where it looks from, and has
the "Show", "Field CAD model" and "Camera view" boxes. **Layers** draws lines and maps over the field. The
things to do are on the right: **Tests**, **Edit robot**, **Auto builder**.

**Smooth motion.** The simulator moves everything 100 times a second. It sends the page where everything
is 50 times a second (a stream: `GET /stream`), with its own clock. The page draws the moment about 1/20 s
in the past, and glides between the two updates around it. So the robot and the balls move smoothly at
any frame rate, and they do not jump 10 times a second. Across a jump (Back to start, Reset field, the
robot put somewhere by hand), the page does not glide. When the stream cannot open (a very old browser,
too many pages open), the page asks for `/state` 10 times a second instead. It still glides, but further
behind.

### Where the robot starts

**Where the robot starts.** On the page, INIT of an autonomous is a new match. Every game piece goes back
where the match starts them (with the preload), and the robot goes to its start spot. A TeleOp's INIT
keeps the field as it is, so the TeleOp goes on from the autonomous. Scenario files set up their own field.

In INIT, the simulator puts the robot where the program says:

- An autonomous that names its start spot (`s.start(note, x, y, heading)`, or `startSpotIn()` in an
  EasyAuto) puts the robot there, for the alliance chosen in its menu.
- An autonomous with its spot only in words (`startPosition()`, or `s.start(note, heading)`) puts the
  robot on the chosen alliance's side (the robot file's start). The Driver Hub asks you to drag the robot
  to the exact spot. At ▶, the simulator remembers that spot for this autonomous (in
  `sim-start-spots.properties`, next to the simulator's settings). The next INIT puts the robot there.
  For the other alliance, it uses the same spot on that alliance's side. Or give the spot in inches in the
  code (`s.start(note, x, y, heading)`), so the robot goes there by itself.
- A TeleOp with no autonomous before it places the robot when the drivers choose the alliance (X / B). RED gets the robot file's start. BLUE gets that spot's mirror image. Until they choose, the robot
  stands on BLUE's spot, and the page looks from the blue wall. The reason: the TeleOp says
  `Alliance : BLUE until you choose`, and aims and drives for BLUE.
- After an autonomous, TeleOp leaves the robot where autonomous ended.

### The Match panel and the score bar

With a rules file (BIOBUZZ has one: [Game rules and the score](#game-rules-and-the-score)), the Control
Hub's **Match** button shows the game. A score bar sits over the 3D view: click it to open the panel.

- **The score and the clock**: RED and BLUE, the period (AUTO, TELEOP, "AUTO done", FINAL or free play)
  and the time left. **The score counts like a referee** (manual 10.5). HIVE TIPs count when they happen.
  LEAVE and AUTO PARK count at the end of AUTO. Pieces in the CELLs, FLOWERs and GARDENs and TELEOP PARK
  count **only at the end of the match**. The reason: they change all match long (a TIP spills the CELL's
  pieces). Until then, they show **gray, as "+N"**: what they would add if the match ended now. When
  TeleOp ends (or you stop it), the score is **FINAL** and held. When you move the robot after that, it
  changes nothing. A new autonomous, or a TeleOp after a FINAL, starts a new match. BIOBUZZ starts each
  side at "+10": the field's own pieces in the up CELLs and GARDENs.
- **Where the points come from**: click a side. The table has the same groups as FIRST's score calculator: AUTO
  (LEAVE, PARK, TIPs), TELEOP (TIPs, CELL, FLOWERs, the bottom NECTAR bonus, GARDEN, PARK), FOUL (the other
  side's fouls), the total, and the **RANKING POINTS**. These are SWARM (LEAVE + PARK points, 16 to earn
  it) and POLLINATOR 1 and 2 (4 and 7 TIPs). Each has a tick or a cross and the count. The end-of-match
  rows have a * until the end. A line says whether a NECTAR into a FLOWER is allowed now (G410).
- **FLOWERs**: each FLOWER's owner, and its stack from the bottom up. Each piece is a dot in its color, and
  a hollow dot is outside the scoring rings. In 3D, a ring over each FLOWER shows its owner's color (gray:
  none) and how many pieces count.
- **Shots**: the tally of verdicts (clean, rim, other HIVE, frame, miss) and the last few shots. In 3D, the
  page shows the verdict over the piece when the simulator judges it. The piece's trail (Layers: Ball
  trails) takes the verdict's color.
- **Scatter**: off (every shot the same), realistic, rough, or as the robot file says (`/cmd action=scatter`).
- **Hit rate**: 400 pretend shots from the robot as it is now (`GET /hit-rate`), judged like real ones.
  It shows how many go in cleanly, with its plus-or-minus, and the word Good (80 % or more), Risky
  (50-80 %) or Poor. It also says why the others did not go in. Pick the piece, or the next one in the
  robot.
- **Fouls**: each foul charged (G407, G410), newest first. A new one shows over the 3D view for 5 s.

### Layers

The **Layers** button over the 3D view turns each of these on or off. This browser remembers your choice.

| Layer | What it shows |
|---|---|
| Camera sight lines | A line from the chosen camera to each tag in front of it. **Green**: the camera sees it (with about how many pixels across). **Pink "blocked?"**: the camera model sees it, but a field part is in the way of the line (the simulator's camera sees through field parts; a real one does not, so do not count on that tag from there). **Orange**: too slanted (the tag's face is turned more than 60 degrees from the camera). **Red**: too small (fewer pixels across than the tag finder needs: 6.5 per step of decimation, so 20 at the SDK's decimation 3). **Gray, faint**: out of the picture (left, right, above or below). **Purple**: the pretend lighting does not work with the camera's setting now (`goodPipelines`, `goodExposureMs`). Not seen: only the nearest four lines of each kind. The words at the bottom left list every tag. "Not open" means the OpMode has no camera running, so it gets nothing even where the lines are green. |
| Camera view cone | The camera's view, 36 in long. |
| Floor map | Where on the field the chosen camera can see tags: each 4 in square is shaded by how many tags the camera would see with the robot standing there (light: 1, darker: 2 or 3, darkest: 4 or more). The simulator works it out with the same camera rules the OpMode gets (field parts do not block in this map), with the robot facing the way it faces now, or, ticked "turned any way", the best of 24 headings. Turret and other parts stay as they are now. It updates once a second. |
| Shot arc | Where each launcher would send its next piece if it fired now: a dashed yellow line from the launcher, with its peak height (check the venue's ceiling and lights). The simulator works it out from the flywheel's speed now, the shot angle (the hood's angle if it has one) and a shot table's average, with the same air drag and spin lift as a real shot, but it flies through everything (no goal, no walls, no other pieces). The words at the bottom left give the angle, speed and peak ("shoots backward, 33° up" for a launcher that throws over the robot's back); a stopped flywheel says so. |
| Ball trails | The last half second of each loose ball's path, in its color: how it flew and bounced. |
| Path lines | The Pedro plan and the trail (the same as **Path lines** in the Robot tab). |
| Robot size box | A box around everything the robot has out now (green while it fits 18 x 24 in and 29 in tall, red when not; worked out from the shapes' boxes, not the CAD models) and the 18 in start cube, dashed. |

## Motors and the parts catalog

The simulator moves each motor by the same "feedforward" model that a real motor follows. The model: power (at
12.8 V) = kS (to start moving) + speed / maxTicksPerSec + accelSec / maxTicksPerSec x speeding-up (+
gravity for a lift). Without measurements, it uses the parts catalog's top speed and typical values. To make the simulated robot move like YOURS, measure it with the Characterizer ([Real speeds](../simulator/03-make-it-match-your-robot.md#6-real-speeds-optional)).

The settings the Characterizer fills in (`./gradlew :sim:fit`, or the lines it shows on the Driver Hub):

| Setting | Means |
| --- | --- |
| `drive.kS`, `drive.maxTicksPerSec`, `drive.accelSec` | the wheels: power to start moving, speed per unit of power (counts/s), seconds to reach ~63% of a new speed |
| `drive.turnDegPerCount` | degrees the robot turns per wheel count of turning ((left - right) / 2): wheels slip when a robot turns |
| `motor.NAME = ..., kS=, maxTicksPerSec=, accelSec=, gravity=` | a lift or arm (gravity = power that holds it) |
| `motor.NAME = ..., kS=, maxTicksPerSec=, accelSec=, coastSec=` | a flywheel (coastSec: seconds to lose ~63% of its speed with power off) |

`maxTicksPerSec` is the speed gained per unit of power: at full power, the speed is (1 - kS) x this.
When the Characterizer runs in the simulator, it writes its logs in `sim/build/sim-data/characterizer`.
`./gradlew :sim:fit` (no -Plog) reads those. A test (SimCharacterizerTest) gives the simulated robot known
numbers and checks that the fit finds them again.

`sim/parts/parts.json` lists the common FTC motors and servos with their real part numbers. Their numbers
come from the seller's page, with the date someone checked them. The list has goBILDA Yellow Jacket (5203
and 5204, every ratio), REV HD Hex (bare, UltraPlanetary combinations, spur gearboxes) and Core Hex,
AndyMark NeveRest, goBILDA 2000 series servos, REV Smart Robot Servos and Axon servos. A robot file names a
part by its `id` (`part=gobilda-5203-312`). The simulator takes these numbers from it. For a motor: the top
speed, encoder counts per turn and current. For a servo: the speed and range. When the seller does not
publish a number, the catalog leaves it empty, with a note. The catalog uses real ratios (an UltraPlanetary
"5:1" is 5.23:1). Servo speeds use the seller's row closest to 5 V. A Control Hub servo port gives about
5 V, and the headline number is often for 6 V.

Motor kinds:

- `free`: it only spins.
- `lift`: hard stops, gravity.
- `flywheel`: it coasts. While its `feeder` (a motor or a CR servo) runs, pieces go through and slow it
  down. One piece goes each time the feeder starts, then one every 0.3 s from 0.45 s on.

`ticksPerRev=` sets a motor's encoder counts per turn (default 537.7; flywheel 28). On a robot, `setPower`
in RUN_USING_ENCODER aims for the top speed of the motor TYPE chosen in the configuration. Here it aims for
the robot file's `maxTicksPerSec`. The wheel motors are on the left/right sides of the robot, like on a real
one, with one side's motors mounted mirrored. So motor directions in your code matter here too. That side
is the left unless `drive.mirrored = right`. Write the side that your code reverses to drive straight
(goBILDA's starter bot reverses the right one). If your code drives forward with negative power, write the
other side. Check it: the drive stick forward must drive the robot forward.

## Physics

The robot moves with a physics engine (ode4j, pure Java). The wheels say how fast the robot
wants to go. The drive pushes the robot's body toward that speed as hard as the wheels' grip allows. Walls
and the field's solid and overhead parts are solid shapes: each part's footprint, from its lowest to its
highest point. The robot stops at them or slides along them. Its parts (the robot shapes) hit them too: a
tall lift can hit the HIVE's overhead frame. When something blocks the body, the wheels keep turning (they
slip). So the wheel encoders keep counting while the robot pushes, like on a real robot. An encoder-driven
autonomous can "arrive" while the robot is pinned.

The body follows the wheels a little behind: its grip takes about 0.02 s to catch up. So an
encoder-driven autonomous ends within about half an inch of where `physics = simple` would put it.

Limits:

- The engine holds the robot flat, so it never tips or climbs.
- The robot's parts are solid boxes on it, and they do not stop their own motors. An arm that swings into
  the HIVE pushes the robot, and the arm does not stall.
- Field parts are simple prisms: a FLOWER is solid all the way up.

Coordinates: inches from the field center, +x to the right and +y away from the drivers (drawn at the
bottom), z up from the tiles. Headings and tag facing are in degrees, counterclockwise from +x for tags.

### The size check at full reach

The simulator checks the size rules with the robot's shapes: the 18 in start cube, and 18 x 24 in and 29 in tall (R105).
It checks them now and **at full reach**, with every moving part tried over its whole range. The Control Hub panel shows the sizes in green or red.

How the simulator checks "at full reach": it tries every combination of the moving parts' important
values. A slide or claw is biggest at an end of its range. It tries a hinge (arm, turret) at both ends, at
each multiple of 90 degrees, and every few degrees between. That is every 5 degrees or less with a few
moving parts, and coarser with many, to stay under 50,000 poses. The panel shows the worst pose. It is a
careful check, not a proof: leave a little margin, and measure the real robot at its hard stops.

## Game pieces

With the physics engine, the field file's game pieces are on the field. BIOBUZZ: the 4 POLLEN in each
GARDEN. The sizes (POLLEN 2.8 in and NECTAR 3.6 in) come from manual 9.8. The weights (25 g and 41 g) come
from AndyMark's am-5851/am-5852 pages, not the manual. The pieces roll and bounce, and the robot pushes them. The FLOWER tops are goals
(the opening's center and height measured in the FLOWER CAD). The robot file says which robot shapes
handle the pieces:

The match starts with every piece that the manual stages on the field (10.3.1). The simulator places them
from the official field CAD: 4 POLLEN in each GARDEN (loose on the tiles), 4 POLLEN in each FLOWER and 3 NECTAR in each
upward CELL. A FLOWER is solid above its bottom opening, the Retrieval Opening. That opening is 9-99 mm up,
and open toward the field and both sides. So the POLLEN above the opening hold their places (the field
file's `stacks`). The bottom POLLEN sits in the opening as a real piece. It stays still until the robot or
another piece comes near, and a part that reaches in can knock it out. An intake running inward (or a
closed claw) at the opening takes it. Either way, the next one drops into its place about 0.12 s later.
The robot rolls over the FLOWER's thin floor plate (0.35 in), and pieces rest on it.


The colors are the screen colors of the field file, not a real sensor's. Measure the real ones with the
Piece Tuner (it also runs here). A motor or CR servo feeder sends one piece each time it turns on.
If it keeps running, it sends one more every 0.3 s from 0.45 s on. So keep an indexer's `feedSec` under
0.45 s. Otherwise one feed fires two pieces here, and the count is off by one.
A second webcam's colors are not simulated: it says "not found" and sees nothing.

**Rollers turn the pieces they touch.** A roller or spinner (any `roller` or `flywheel` shape) drags a
piece along with its surface. It moves the piece at up to its real surface speed, as far as the grip allows. So spinners can pull
a piece in, or fling one away, even when the robot cannot take it (when it is full).

**The HIVEs tip** (manual 9.6, 10.5.1). Each CELL is an open box from the field CAD: its floor, back, sides and
roof. It is about 20 x 14 in open and 12 in deep. So a shot can go in, roll down to the back and stay.
The 3 staged NECTAR rest in each up CELL. A piece counts when it has come to rest in a HIVE's up CELL
(slow for 0.3 s). When those pieces weigh 187 g or more, the HIVE turns 60 degrees. The number comes from
the team's field tests: no tip at about 175 g, and a tip at about 199-200 g. The 3 NECTAR are 123 g, so the
3rd POLLEN tips it. When the HIVE turns, the other CELL comes up, and the pieces in the CELL that went down
fall out. The 8 AprilTags on its CELLs move with it, and cameras see them where they are now. The HIVE
turns over 2 s (slow, fast, slow). That is the rules file's `tipSeconds`, an estimate until someone films a
real tip: real HIVEs tip slowly. Its CELLs carry their pieces and push what they meet. The HIVE counts as
TIPPED when it gets there (10.5.1). In a match, only LAUNCHED pieces may tip it (10.5.1, G417). A robot may
not catch what falls out until it has touched something else (G409). The simulator does not referee
either rule. The page shows the pieces in each up CELL (they score if they are still there at the end) and
the tips. When a piece goes over a wall, the simulator takes it off and counts it ("left the field").
`print pieces` lists it all. Scenario checks: `expect cell "Red" >= 2`, `expect tips "Red" >= 1`,
`expect left >= 1`.

The 3D view shows what the robot holds, in its top-left corner. It shows a dot in each piece's color, an empty dot
for each free place (its `holds=`), and the count ("In the robot: 3 / 4 · 3 POLLEN").

On the page (Control Hub, Robot tab):

- **Back to start** puts the robot on its start spot, and every piece where the match starts it.
- **Reset field** puts the pieces back and leaves the robot where it is.
- **Preloaded** (ticked by default) gives the robot the preload now and at every reset. Untick it for an
  empty robot.

In the robot file, `preload = off` starts the robot empty. In a scenario, use `set preload on|off` and
`set field start`.

Scenario lines: `set piece POLLEN X Y [Z]`, `set scatter off|realistic|rough|file`, `expect stored >= 1`,
`expect launched >= 1`, `expect scored "FLOWER" >= 1` (pieces that went into goals whose name has the text;
`"all"` = every goal), `expect cell "Red" >= 1`, `expect tips "Red" >= 1`, `expect left >= 1`,
`expect points "red" >= 20` (the alliance's match points now), `expect clean >= 1` (shots that went
cleanly into an up CELL), `print pieces` (also the points and the shot verdicts).

Not in the simulator yet:

- the alliance areas' NECTAR (the human player's, and the one a tip lets them enter, G426).
- the HIVE's arm between its CELLs: a piece there passes through.
- the tip judged at the damper (10.5.1). The simulator calls it TIPPED when the turn ends.
- a held piece's weight on the arm.
- robot parts that stop when they hit a piece.

How far a ball rolls on the tiles is a guess (about 2 m from 1 m/s) until someone times a real one. What
the rules cover and what they do not: [Game rules and the score](#game-rules-and-the-score).

## Game rules and the score

A season's rules sit in a second file next to the field file: `sim/fields/biobuzz-2026.rules.json`.
Someone types it by hand from the game manual, and each part says which section. (A script makes the field
file itself from the CAD.) With the rules file, the simulator plays by the manual and keeps the score.
Without one (an older or home-made field), the simulator counts a piece in a goal and takes it off. HIVEs
tip at once, and there is no score.

What it models (BIOBUZZ, Competition Manual TU01):

| Rule | Manual | In the simulator |
| --- | --- | --- |
| Pieces stay in a FLOWER | 9.7 | A piece that falls into the top lands on the FLOWER's stack (each piece resting on the one below; the lowest in or on the lower ring). A full FLOWER (8 POLLEN) takes no more: the next one stays on top. |
| FLOWER scoring volume | 10.5.2 | A piece at least partly between the middle ring's top (5.26 in, field CAD) and the top ring's top (21.4 in) counts. At the start, 3 of each FLOWER's 4 POLLEN are in it; the bottom one is not. |
| FLOWER owner | 10.5.2 | The alliance with the top-most NECTAR in the volume owns the FLOWER: 2 points for every piece in it, whoever put them there. |
| Bottom NECTAR bonus | 10.5.2 | 5 points to the alliance with the bottom-most NECTAR in the volume. |
| NECTAR in the last minute | G410 | NECTAR into a FLOWER before the last 60 s of TELEOP (or in AUTO) is a MAJOR FOUL: 20 points to the other alliance. It still counts in the FLOWER. |
| No more than 4 at a time | G407 | A MAJOR FOUL (20 points to the other alliance), once per match, when it looks STRATEGIC: 6 or more pieces in the robot at once, or 5 for more than 3 s (MOMENTARY), twice. 5 for a moment is no foul. The simulator takes as many pieces as `holds=` lets it. |
| Only POLLEN from the bottom | 9.7, G418 | A NECTAR is too tall for the Retrieval Opening: once it is the lowest piece it stays in, and nothing above it can be taken out. |
| HIVE TIP | 10.5.1 | 20 points to the HIVE's alliance, AUTO or TELEOP by when the turn ended. |
| Pieces in the up CELL | 10.5.1 | 2 points each (the 3 staged NECTAR count too), for the HIVE's alliance. |
| GARDEN | 10.5.3 | 1 point for each piece at least partly in a GARDEN (center under 6 in: the manual gives no height), for that GARDEN's alliance. |
| LEAVE, PARK | 10.5.4 | At the end of AUTO: LEAVE (3) if the robot no longer touches the wall, PARK (5) if it is at least partly in its LOADING ZONE. TELEOP PARK (5): where it is now. |

The robot is the alliance that its program chose in the INIT menu, or red when the program chose none. The
match clock comes from the Driver Station. A running autonomous is AUTO (30 s). A running TeleOp is TELEOP
(2:00). "Seconds left" counts down from the TeleOp's ▶, even with the TeleOp timer off. With no OpMode
running, it is free play: no fouls, and tips count as TELEOP. The score is live ("if the match ended now").
It starts again at "Back to start", at "Reset field" and when an autonomous starts.

Not modeled:

- the other three robots.
- the human player and the alliance areas' NECTAR (one per tip, all of them in the last 60 s).
- the 8 s transition between AUTO and TELEOP.
- the ranking points (SWARM, POLLINATOR).
- every other foul (G408 opponent NECTAR, G409 catching what a tipped HIVE drops, G417 touching the HIVE,
  and so on), and G407's VERBAL WARNING and YELLOW CARD.

FLOWER points scored before the last minute count, as the manual says ("still scored, but subject to
penalties"). The simulator charges only the NECTAR foul (G410) and the 4-piece foul (G407). The simulator
is not a referee: it counts what it can see.

## Clean shots, scatter and the hit rate

**Each shot gets a verdict.** The simulator judges it the same way as the team's turret shot simulator (a
line-by-line port of its `judge()`):

- **clean** when the shot comes through an up CELL's opening going down at least 10 degrees, and clears the
  rim by its own radius. Before that, it touched neither the frame, the outside of its own CELL nor the other
  HIVE.
- **rim** when it touched the rim going in: it may bounce out.
- **other HIVE** or **frame** when it would have gone in, but hit that first.
- **miss** otherwise.

The simulator keeps the last 20 shots' verdicts and a count of each, for the page and `print pieces`. The
judge's rule gives the verdict. The physics decides whether the piece stays in (and tips the HIVE). For the
judge, the CELLs count 1 in bigger (the frame tube, as in the turret simulator). The HIVE frame is the field
file's blocks. The arm between the CELLs is not there (it is not in the physics either). A test
(`SimCellJudgeTest`) checks the port against the turret simulator's `judge()` on 600 shared shots, 120 of
each verdict. They agree on every one.

**Scatter.** Real shots differ: the ball's wear and size, the wheel speed, the hood's play. On the
`shoot.` line, `scatter=realistic` makes each shot differ a little. One standard deviation is: exit speed
1.5 %, launch angle 0.35 degrees, aim 0.25 degrees left-right, spin 10 %. The first three are the turret
simulator's "Realistic" for a launcher without a feed table. The spin share is a guess.
`scatter=rough` gives about double, and `scatter=off` (the default) gives none. You can set each part on
its own: `scatterSpeed=` (%), `scatterAngle=`, `scatterAim=` (degrees), `scatterSpin=` (%).
Ball wear: `wear=red NECTAR:8+blue NECTAR:8` adds that much speed spread (%) for those pieces. In the
turret's feed model, a new NECTAR leaves about 30 % slower than a medium one at the same settings. Balls a
quarter of that apart give about 8 %. With a shot table, the table's spread replaces the speed, angle and
spin parts, and aim and wear still apply. The page can switch every launcher at once (`/cmd`
`action=scatter`: off, realistic, rough, or `file` for the robot file's). In a scenario, use
`set scatter realistic`.

**Hit rate.** `GET /hit-rate` (for the page) fires 400 pretend shots from where the robot is now. The shots
are not on the field, so nothing moves. They use the launcher as it is now (wheel speed, hood, the robot's
own speed) and the scatter (realistic if scatter is off). The simulator judges each one, for example "57 %
clean (plus or minus 5), Risky". **Good** is 80 % or more, **Risky** 50 to 80 %, **Poor** under 50 % (the
turret simulator's words). It assumes an empty CELL and single shots.

For the page (and your own tools):

- `/state` has a `game` object: the clock, the score by alliance and by kind, each FLOWER's stack and
  owner, each HIVE's tip progress, the last shots' verdicts, the scatter and the fouls.
- `GET /hit-rate?n=400&piece=POLLEN` answers with the hit rate as JSON.
- `POST /cmd` with `action=scatter&mode=realistic` sets the scatter.

## How shots fly, and shot tables

A shot leaves the launcher shape at `angle=` (degrees up from its forward; default 40). Its speed is
`share=` of the wheel's rim speed (default 0.5: a guess). It has `spin=`: 1 = backspin as if the ball
rolled along a still hood, -1 topspin, 0 none. In flight, the air slows it. The drag uses Cd 0.5 (the drag coefficient).
That is a guess: nobody measured it for POLLEN or NECTAR. A piece type can set `dragCd` in the field file. Spin lifts or
drops the shot: the Magnus force, with lift coefficient = radius x spin / speed, at most 0.5.

**A hood.** With `hood=SERVO, hoodAt0=80, hoodAt1=30`, the shot angle follows where that servo is,
instead of `angle=`: 80 at position 0, 30 at 1, and a straight line between. With a shot table, the table's
scatter goes around the hood's angle. `:sim:fromRobot` writes it for a launcher whose `Hood.servo(...)` has
`shotAngles(atA, atB)` (the shot angle at the hood's two stops). A motor hood does not move the shot angle
in the simulator yet.

**Shot tables** replace the guesses with how the real launcher throws: `shoot.NAME = shape=flywheel,
table=launcher-table.json`. For each piece and wheel speed, the table gives the likely exit speed, angle,
spin and exit point, and how much they scatter. Each shot picks its numbers from that spread, so shots
scatter like real ones. A share of feeds that do not become shots drop out. Make a table with the
**launcher program** (`tools/launcher/`). It cuts your robot's CAD along the ball's path. It runs many shots
through it, with the ball as a soft disc squeezed against the spinning wheel and the hood. The CAD cannot
give some numbers. So it tries them over ranges: how soft the balls are, how much the wheel and hood grip,
where the ball comes in. Then it writes the table. It is still a model: film a few real shots (a phone's
slow motion, next to a tape measure) and compare.

## Cameras

With a webcam in the robot file, your real camera code runs: `WebcamTags`, or the SDK's own
`VisionPortal` and `AprilTagProcessor`. The simulator has pretend copies of the SDK's camera classes, made
by the SDK's own builders.

What it does: the camera opens about 1 s after the OpMode makes it, then sends 30 pictures a second.
For each picture, it works out which of the field file's AprilTags are in view. A tag is in view when it
is inside the picture and faces the camera (within 60 degrees). It must also be big enough to find at the
OpMode's decimation. That is about 6.5 pixels across per step of decimation, which matches the FTC docs' ranges.
The simulator then turns the tags into the SDK's own detections, the way the SDK makes them. A tag in one
of the tag library's clusters gives a cluster detection. BIOBUZZ: the SDK 12 library has one cluster per
CELL group of 4 tags, "RED SCORING" and so on. Any other tag gives a single-tag detection, without a pose
when the library does not know the tag. `ftcPose` uses the SDK's own formulas, so range, bearing (positive
= left) and elevation mean the same as on the robot.

On the page, the Control Hub panel lists each camera with the tags in its picture and what it found.
The 3D view draws the camera's view as a cone on the robot (webcam yellow, Limelight green). "Camera view"
shows the picture from the camera, and the tags it sees turn green. A camera on a turret (`on=turret`)
moves with the turret in the 3D view too. The **Layers** menu draws the camera's sight lines to the tags,
with the reason it cannot see each one. It also draws a floor map of where the camera can see them
([Layers](#layers)).

The hardware map gives your code a Limelight3A: the SDK's own class, with its network part replaced. So
`LimelightTags` and code that calls `getLatestResult()` directly work. About 0.5 s after `start()`, it
sends 50 results a second. The simulator makes them as the same JSON a real Limelight sends. The SDK's
own `LLResult` reads them: `isValid()`, `tx`/`ty`/`ta` (the biggest tag), and one `FiducialResult` per tag.
A Limelight does not group tags into clusters. Each result has `tx` (+ = right), `ty` (+ = up), `ta`, the
pixel position and `getTargetPoseCameraSpace()` (meters; x right, y down, z out of the lens). Every
pipeline finds AprilTags, and `pipelineSwitch` changes the index it reports. Not simulated: botpose /
MegaTag (no field map), robot-space poses, Python, color and neural pipelines, snapshots.

Limits:

- The poses are exact: no noise, blur or lag beyond the Limelight's reported latency.
- Field parts do not block the view.
- The camera does not find tags slanted more than 60 degrees.
- The webcam's `robotPose` is always null.
- Only the AprilTag finder gets webcam pictures. Other VisionProcessors get `init()` and nothing more.
- Phone cameras are not simulated.
- The resolution comes from the robot file, not from `setCameraResolution`.
