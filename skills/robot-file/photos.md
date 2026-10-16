# From photos to robot shapes

The simulator draws a robot as a chassis box plus simple shapes (see "Robot shapes" in
`docs/guides/simulator.md`). Photos tell you WHICH shapes and roughly WHERE; a tape measure gives the
numbers that matter for the rules and for touching the field.

## Ask for these photos

- Front, side and top, the whole robot, with something of known size in view (a 24 in field tile, a
  ruler, a goBILDA channel: 48 mm across).
- Each mechanism at both ends of its travel: lift down and up, arm folded and out, claw open and closed,
  turret at both limits.
- Close-ups of the intake and launcher (where pieces go in and out).

## Read a photo

- Use the known-size object to turn pixels into inches. Say "estimate" in the note.
- Find the robot's centre on the floor (the middle of the drive frame). Every `at=` is measured from
  there: forward, left, up (inches). A part with `on=` is measured from its parent's mount point instead.
- What `at=` points to: the bottom centre of a `box`, `slide` or `turret`; the hinge of an `arm`; the
  centre of a `roller` or `flywheel`; the palm of a `claw`.
- Only the parts that move, reach out, or touch pieces need shapes. Bolts and wires do not.

## Which shape

| On the robot | Shape | Driven by | Notes |
| --- | --- | --- | --- |
| a box that does not move (electronics, a hopper, a bumper) | `box` | nothing | only if it sticks out or is tall |
| linear slide / lift / extension | `slide` with `axis=up` (or forward) | its motor: `ticksPerIn=` or `spoolIn=` | `size=` is ONE stage; the moving stage rides `travel` inches |
| arm on a pivot | `arm`, `axis=left` (swings up and down) | motor `ticksPerDeg=`/`gear=` + `start=`, or servo `pos0=`/`pos1=` | the box reaches FORWARD from the hinge; `at=` is the hinge |
| wrist on an arm | `arm` with `on=<the arm>` and `at=<arm length>,0,0` | servo | |
| turret | `turret` | motor or servo | turns about up |
| claw / gripper | `claw` | servo, `pos0=`/`pos1=` = the finger gap in inches | put it `on=` the wrist or arm |
| intake roller / wheels | `roller` | motor or `crservo=` | runs across the robot (`axis=left`) |
| launcher wheel | `flywheel` | its motor | add `shoot.` for game pieces |

Mount parts on parts with `on=`: a claw on a wrist on an arm on a lift. A part on a slide rides on the
moving stage; a part on an arm turns with it.

## No photos?

Ask for a hand sketch from the side and from the top with a few dimensions, or describe each mechanism
in words with the interview's numbers. Mark sizes that nobody measured `# estimate: not measured`.

## Then

- Set the ranges from the real hard stops (the Motor Finder's counts), not from the photos.
- Check "at full reach" in the Control Hub panel, or `print size` in a scenario: it tries every pose and
  shows the worst one with its values and what it breaks. A pose that breaks 18 x 24 x 29 in or goes
  below the tiles is a finding to discuss with the team (first check the shapes against measurements).
- Show the 3D view next to the photos and ask the team what looks wrong.
