# From photos to robot shapes

Photos are the easy way for a team with no CAD. They tell you WHICH parts the robot has and roughly
WHERE they sit. A tape measure then gives the few numbers that decide a rule or a collision. The
simulator draws the robot as a chassis box plus simple shapes: every shape is in
`docs/reference/robot-java.md` ("Shapes and parts", for Robot.java) and `docs/reference/robot-file.md`
("Robot shapes", for the robot file).

## 1. Get the photos

**How the team gives them to you:** in Claude Code, drag the photo files into the chat, or put them in a
folder and give the path (for example `photos/`). In Codex or a chat app, attach them. Read each photo
before you use it. Photos stay with the team: do not upload or commit them anywhere (add the folder to
`.gitignore` if it is inside the project).

**Ask for these photos.** Tell the team why each one matters:

| Photo | Why |
| --- | --- |
| Side, the whole robot, front to the left | the heights: lift, arm, launcher, intake |
| Top, the whole robot, from straight above (stand on a chair) | the places: forward and left of each part |
| Front, the whole robot | the width, and the parts at the sides |
| Each moving part at both ends: lift down and up, arm folded and out, claw open and closed, turret at both limits, intake folded and down | the range of motion, for the 18 in and 29 in size rules |
| Close-ups of the intake and the launcher | where pieces go in and out, and the shot angle |

**What makes a photo useful:**

- **Something of known size in view, next to the robot and at the same distance from the camera:** a
  24 in field tile, a tape measure laid out flat, a goBILDA U-channel (48 mm across). Without it, a
  photo gives no sizes.
- **Straight on,** with the camera level with the middle of the robot, and the camera far away (step
  back and zoom in). A photo from close up makes the near parts look bigger than they are.
- **The robot on the floor,** bumpers off, in its start pose for the side, top and front photos.

## 2. Read each photo

1. **List the parts you see,** and match each one to a device in the robot configuration (and a part in
   Robot.java). A part you cannot match: ask the team ("the black box at the back: is it the hub, or a
   part with a motor?").
2. **Pick the shape for each part** (the table below). Only the parts that move, reach out or touch
   game pieces need a shape. Bolts, wires and the hub do not.
3. **Estimate each place from the robot's edges,** never from its middle. Use the known-size object to
   turn pixels into mm: measure in the photo from the FRONT edge, the LEFT edge and the floor to the
   middle of the part. Then work out the numbers the code takes (from the chassis middle on the floor):
   - forward = chassis length / 2 - (from the front edge)
   - left = chassis width / 2 - (from the left edge)
   - up = from the floor

   `docs/images/sim-measure-from-edges.svg` shows the same sum for students.
4. **Note every number as an estimate:** `.note("photo estimate")` in Robot.java, or
   `# from a photo (estimate)` in the robot file.

## 3. Measure what decides a rule

A photo estimate is fine for the 3D picture. These need a tape measure, so ask for them by name, a few at
a time, always from an edge or the floor:

- the chassis: front edge to back edge, and side to side (bumpers off), and floor to the top of the frame.
- the tallest point with every part at its start, and with the lift all the way up.
- how far the arm or intake reaches past the front edge when it is out.
- the floor to the middle of the intake roller, and its distance in from the front edge.

Then replace those estimates, and change their notes to `measured`.

## 4. Write the shapes

**Robot.java in five files?** Write every shape in Robot.java, in mm: each moving part takes
`looks(...)` on the line that adds it, and parts that do not move take `fixed(Shape.box(...))`. Then run
`./gradlew :sim:fromRobot`. See SKILL.md step 4. **No Robot.java?** Write `shape.` lines in the robot
file, in inches.

| On the robot | In Robot.java (mm) | In the robot file (inches) | Notes |
| --- | --- | --- | --- |
| a box that does not move (a tower, a hopper, a hood) | `fixed(Shape.box("tower").atMm(...).sizeMm(...))` | `box` | only if it sticks out or is tall |
| a lift or slide | `Lift ... .looks(Shape.slide().atMm(...).sizeMm(...).travelMm(...))` | `slide`, `axis=up` (or forward), `ticksPerIn=` or `spoolIn=` | the size is ONE stage |
| an arm on a pivot | `Arm ... .looks(Shape.arm().atMm(...).sizeMm(length, width, thickness))` | `arm`, `axis=left`, `ticksPerDeg=` or `gear=` + `start=` | the place is the hinge; the box reaches forward from it |
| a servo that folds a part (an intake that deploys) | `Positioner ... .looks(Shape.arm()... .angle("stowed", 90).angle("deployed", -10))` | `arm`, servo `pos0=`/`pos1=` | two angles at two named positions |
| a wrist on an arm or lift | `Wrist ... .looks(Shape.arm().on(arm)...)` | `arm`, `on=<arm>`, `at=<arm length>,0,0` | |
| a turret | `Turret ... .looks(Shape.turret().atMm(...).sizeMm(...))` | `turret` | turns about up; parts ride it with `on(turret)` |
| a claw | `Claw ... .looks(Shape.claw().on(wrist).atMm(...).sizeMm(...))` | `claw`, servo `pos0=`/`pos1=` = the finger gap in inches | |
| intake rollers or wheels | `Intake ... .looks(Shape.roller("catalog-id").atMm(...).lengthMm(...))` | `roller`, motor or `crservo=` | across the robot by default |
| a launcher wheel | `Launcher ... .looks(Shape.flywheel("catalog-id").atMm(...)).shotAngle(deg)` | `flywheel` + a `shoot.` line | the shot angle: degrees up from the front |

What the place points to: the bottom middle of a box, slide or turret; the hinge of an arm; the middle of
a roller or flywheel; the palm of a claw. A part `on` another is placed from that part's mount point.

**The shot angle from a photo:** in the side close-up of the launcher, the angle between the floor and the
way a piece leaves (along the hood or the ramp). Up from the robot's front: 90 is straight up, more than
90 leaves over the back.

## 5. Check it against the photos

1. Run the simulator, open **Edit robot**, and look at the robot from the side and from above.
2. Put the simulator's view next to the matching photo. Ask the team: "Does this look like your robot?
   What is in the wrong place?"
3. Fix what they point at. They can drag a part in Edit robot themselves and copy the Java it prints.
4. Press **Every part out** and compare with the "both ends" photos: each part must reach where the photo
   shows it, and stay inside the size box.

Set the ranges from the real hard stops (the Motor Finder's counts), not from the photos. A pose that
breaks 18 x 24 x 29 in, or goes below the tiles, is a finding to discuss with the team. First check the
shapes against the tape measurements.

## Have CAD?

CAD beats photos for every number. Build the shapes first (the boxes bump into things), then add the CAD
models on top: see `cad.md` and "CAD models" in `docs/simulator/03-make-it-match-your-robot.md`. Photos
are still useful to check that the CAD is the robot as built.

## No photos?

Ask for a hand sketch from the side and from the top with a few dimensions (from the edges), or describe
each mechanism in words with the interview's numbers. Mark sizes that nobody measured
`estimate: not measured`.
