# From the robot's CAD to the robot file

Use this when the team has CAD of the whole robot (a STEP file, or Onshape / Fusion that can export one). The CAD
gives exact sizes, places, spin axes and seller part numbers. It does not say which configuration name drives
which part: you guess that from clues and ask the team when you are not sure. The result is the same robot file
as the interview makes, plus CAD models for the 3D view and the size rules ("CAD models of the parts" in
`docs/guides/simulator.md`).

**CAD from a seller belongs to the seller.** Keep the STEP and the STL files with the team (their own repository
or computer), never in a public repository. Commit only `cad-work/cad/*.stl` and the JSON files you write; add
`cad-work/meshes.npz` and `cad-work/parts.json` to `.gitignore` (big, and `read` makes them again).

## 1. Read the CAD

The tool needs Python with build123d (`tools/cad/README.md`; about 500 MB). From the `ftc-helper-lib` folder:

```bash
python tools/cad/robot_cad.py read path/to/robot.step ../simulator/cad-work
```

It reads the file once (a 400 MB STEP takes about 2 minutes) and writes `cad-work/parts.json`: every part with its
seller part number, its match in the parts catalog (motors and servos), its size and place, and, for round parts,
the spin axis and radius. Look at the counts it prints: few catalog matches can mean the CAD names its parts
without part numbers (then ask the team which motors are where).

## 2. Find the robot frame

Write `cad-work/frame.json` with the three settings the robot file will use:

```json
{"cad.units": "mm", "cad.axes": "y,-x,z", "cad.origin": "0,0,0"}
```

`cad.axes` is three CAD axes in this order: the robot's forward, left, up.
- **forward:** the side the intake (or the front the team drives toward) is on. Say which side you picked and why.
- **left:** whatever makes forward x left = up (the tool refuses a mirrored choice: then flip one sign).
- **up:** the CAD axis the robot stands along (Onshape and SolidWorks: z; Fusion: usually y).
- **origin:** start with `0,0,0`. `clues` finds the drive wheels and prints a suggested `cad.origin` (the middle of
  the wheels, at the bottom of the lowest): copy it into `frame.json` and run `clues` again.

Then check `clues`' first lines: the whole robot about as big as the team says, its lowest point about 0 in, the
drive wheels found. If not, fix `frame.json` first.

```bash
python tools/cad/robot_cad.py clues ../simulator/cad-work ../simulator/cad-work/frame.json
```

## 3. Guess what drives what, and ask only when unsure

`clues` lists each motor and servo it matched in the parts catalog, where it is, its shaft direction, and the round
parts that line up with it (on the same axis, or linked by gears, belts or chains on shared axles). Put that next to
the configuration file and TeamCode, and make a table:

`clues` prints lines like this (goBILDA's starter bot):

```text
  motor gobilda-5203-117         5203-2402-0051:1    at 6.8 2.1 5.5 in (forward, left, up), shaft left
        turns: 3613-4008-0048:4 (1.9 in across, axle left; the other way (through gears) as the motor)
```

| CAD part | Clues | Guess (configuration name) | Sure? |
| --- | --- | --- | --- |
| 2 x 312 RPM motors, low, one per side | drive layout; names left_drive / right_drive | drive motors | yes |
| 312 RPM motor on the 96 mm wheel's axis | same axis; code: `setVelocity(1250)` | launcher | yes |
| 2 speed servos at the front corners | place; names left/right_intake_servo | corner wheels | which is which: **ask** |

Clues, strongest first: the code (what a motor does: `setVelocity` with a high target is a launcher, a trigger-run
motor an intake, run-to-position with limits a lift or arm); the shaft lining up with a part or a gear train to it;
the configuration names ("intake", "lift"); the motor's speed (6000 RPM near a big wheel: a launcher; a slow motor
near a slide: a lift); left and right from the names.

**When the CAD and the code disagree, ask.** Kits change parts: goBILDA's starter bot uses a 312 RPM motor with
its gearbox taken off (1:1, about 6000 RPM) as the launcher, so the CAD's part number says 312 RPM, but the code's
launcher speed only makes sense at 6000. A motor missing from the parts catalog: use the catalog entry with the
same motor and ratio, with a note (`# am-5550 NeveRest Hex 19.2: same motor and ratio as the Orbital 20`).

Show the team the table. Ask only about the rows that are not sure, and about anything the CAD and the code
disagree on. Note where each answer came from in the robot file: `# from the CAD`, `# guessed from the CAD and the
code`, `# confirmed by the team`.

## 4. Group the parts and write the models

Each shape gets the parts that move with it; everything else is the chassis. Write `cad-work/groups.json`:

```json
{
  "chassis": "rest",
  "intake": {"near": [7.36, -0.24, 3.66], "within": 4.5, "axle": "left", "onAxis": true},
  "launcher": {"near": [-1.57, -0.18, 6.61], "within": 2.5, "axle": "left", "onAxis": true, "except": [412]},
  "lift-stage": {"parts": [101, 102, 103]}
}
```

- `"parts"`: the part index (`i` in parts.json; not the seller's part number). `"near"` + `"within"`: every part whose middle is within that
  many inches of the point (robot frame). `"onAxis": true` keeps only parts centred on the axle line through the
  point (`"axle"`; within `"axisTolIn"`, default 0.15 in): use it for anything that spins. `"names"`: a regex on the part's name. `"except"`: drop parts.
- **A spinning group holds only the parts that spin** (wheels, hubs, shaft): the size rules count a spinning model
  as the whole circle it sweeps, so a motor or bracket in it makes the robot look bigger than it is. Keep motors in
  the chassis group unless they really turn with the part.
- A lift's moving stage and what rides on it is one group; its fixed stage another (`baseModel=`).

```bash
python tools/cad/robot_cad.py groups ../simulator/cad-work ../simulator/cad-work/frame.json ../simulator/cad-work/groups.json
```

It writes `cad-work/cad/<group>.stl` (simplified to at most 150,000 triangles each: a few MB), prints each group's
size and place in the robot frame, and a starting shape line for each group. A simplified model is blurred by up to
the amount it prints; the size rules are good to about that much. Use those numbers for the shapes: `at=` (see "What `at=` points to" in `photos.md`) and `size=` close to the group's size, axis from
the clues.

## 5. Write the robot file and check it

Write the robot file as in `SKILL.md`, with the CAD lines added (copy `frame.json`'s three settings, then
`robot.model = cad-work/cad/chassis.stl` and `model=` on each shape). Then run the check scenario from `SKILL.md`
with `print cad` and `print size`:

- `print cad`: every model sits on its box (no "FAR"), the right size (no "times smaller").
- `print size`: the size at the start and at full reach should match the CAD's own size (the `clues` first line)
  within about 0.2 in (the size rules add a small safety margin). More: a group holds a part it should not (a bracket in a roller group).
- Run the team's TeleOp with `init`: no "Unable to find a hardware device", and pushing the drive stick forward
  moves the robot forward (if it goes backward, set `drive.mirrored` to the other side).
- Show the 3D view and ask the team whether it looks like their robot.

## 6. The launcher (if the robot has a flywheel)

The shot's angle and speed are guesses until you make a shot table: follow `tools/launcher/README.md` with the
launcher group's parts as the wheel and the feeder's place as the entry. Look at its picture, and ask the team the
one thing the CAD cannot show: which way the wheel turns (and where the ball comes in, if the picture's paths look
wrong). Put the table next to the robot file and add `table=` to the `shoot.` line.
