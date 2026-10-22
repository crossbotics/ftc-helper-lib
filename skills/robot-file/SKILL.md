---
name: robot-file
description: Build or update an FTC team's simulator robot file (simulator/robot.properties, for the ftc-helper-lib simulator) from their real robot - the Driver Station's robot configuration XML, their TeamCode, parts list, tape measurements and photos. Use when a team wants their robot in the simulator, asks to "make our robot file", or when the robot changed (new mechanism, new motor, new camera).
---

# Robot file skill

You help a team describe their real robot for the ftc-helper-lib simulator, so it drives, reaches and
scores like theirs. You talk to students: plain words, short questions, a few at a time, and say why a
number matters ("the size check needs the lift's real top height").

The robot file format is in `ftc-helper-lib/docs/guides/simulator.md` (robot file, robot shapes, game
pieces, cameras, real numbers). Read it first. The parts catalog is `ftc-helper-lib/docs/guides/parts-catalog.md`;
what this skill is for, for people: `ftc-helper-lib/docs/guides/robot-file-skill.md`.

**Where commands run:** every `./gradlew :sim:...` command runs from the `ftc-helper-lib` folder, and
relative paths in `-Pconfig=`, `-Pout=`, `-Pfile=`, `-Probot=` are relative to that folder. In a robot
project (`ftc-helper-lib/` next to `TeamCode/`) the robot file is `../simulator/robot.properties` and
`:sim:run` / `:sim:scenario` use it automatically; anywhere else pass `-Probot=<path>`.

## The rules

- **Never make up a number silently.** Every number in the file gets a note saying where it came from:
  `# measured`, `# from the configuration`, `# seller page`, `# from a photo (estimate)`, `# default`.
  An unknown stays a `TODO` line, and you tell the team what is still open.
- **Measurements beat photos.** A photo is fine for the 3D picture. Anything that decides a rule or a
  collision (the robot's size, a lift's top, an arm's reach, the intake height) needs a tape measure.
  For a size that does not decide anything (an arm's thickness), a labelled estimate is fine:
  `# estimate: not measured`. No photos? Ask for a hand sketch with a few dimensions.
- **Heights are to the middle** of a part (a wheel's centre, a roller's centre) unless you say otherwise:
  ask that way ("floor to the middle of the wheel").
- **Names come from the configuration**, exactly (case and spaces). A wrong name makes the OpMode stop
  with "Unable to find a hardware device".
- **Photos stay with the team.** Do not upload or commit them anywhere.
- **Check before you hand it over** (the last section). A robot file that does not load is not done.

## 1. Collect what exists

Ask for these, and use whatever the team has:

1. **The robot configuration file.** On the Control Hub it is `/sdcard/FIRST/<configuration name>.xml`
   (the name the Driver Station shows under "Configure Robot"). Ways to get it: the REV Hardware Client's
   file manager; Android Studio's Device Explorer; `adb pull /sdcard/FIRST/<name>.xml`. No file? Ask them
   to read the device list off the Driver Station's configuration screens.
2. **TeamCode.** It must be the Android Studio `TeamCode` module (the simulator builds
   `TeamCode/src/main/java`; loose .java files go in `src/main/java/org/firstinspires/ftc/teamcode/`).
   Read it yourself: `RobotSettings`/`Robot.java` (motor names, directions, limits), the
   `hardwareMap.get(...)` calls, any `Characterizer`/finder results (`finder-results.txt`).
3. **Parts list**: the motors (seller part numbers, gear ratios), servos, wheels.
4. **Photos**: front, side, top, and each mechanism at both ends of its travel (lift down and up, arm
   folded and out, claw open and closed). See `photos.md`.
5. **CAD of the whole robot**, if they have it (a STEP file, or Onshape / Fusion that can export one):
   it gives exact sizes, places, spin axes and seller part numbers, and draws the real parts. Follow
   `cad.md`: it reads the CAD, you guess which motor drives what from its clues and ask only when
   unsure, and it writes the CAD models. Shapes come first anyway: the boxes are what bump into things.

With CAD, do `cad.md` steps 1-3 now: its numbers and clues answer most of the interview.

No configuration file? Write the robot file by hand: one device line for each `hardwareMap.get(...)` name
in TeamCode (copy the layout of `sim/robots/starter.properties`), and skip step 2. A colored light is
`light.NAME = gobilda` (goBILDA RGB Indicator Light, a servo port) or `light.NAME = blinkin` (REV Blinkin).

## 2. Make the draft

**TeamCode in five files** (a `Robot.java` that `extends RobotDefinition`, docs/modules/robot.md)? Make
the draft from the code instead: it knows each device's module, the drive, the stops and the claw's
positions.

```bash
./gradlew :sim:fromRobot -PteamCode=path/to/TeamCode
```

Its lines end with `# from Robot.java` and are rewritten each run. When you measure a value, write it on
a line WITHOUT that mark (or remove the mark): your line is then kept on every later run. Then go on
with step 3 for the TODO lines (sizes, places, the shot).

Otherwise, from the configuration file:

```bash
./gradlew :sim:fromConfig -Pconfig=path/to/config.xml -Pout=../simulator/robot.properties
```

It writes a draft with every device, matches the motor types it can to the parts catalog, marks the
rest `TODO`, checks the draft loads, and prints the open questions. (If the file exists it writes
`robot.properties.draft` next to it: merge by hand.)

A team that has no code yet: `./gradlew :sim:newRobot -Pconfig=... -PteamCode=...` writes the five files
from the configuration (check its TODOs with the team), then use `:sim:fromRobot`.

## 3. Ask the open questions

Use `interview.md`: it lists the questions in a good order, what each answer becomes in the file, and
how to check an answer that looks odd. Ask 3-5 at a time. Fill in the file as answers come. Show the
team the lines you changed.

## 4. Build the shapes

**TeamCode in five files (a `Robot.java` that `extends RobotDefinition`)?** Put the answers in Robot.java,
not in the robot file: each part's catalog id (`part(...)`, `Drive.wheel(...)`), its shapes
(`looks(Shape...)`), `chassisMm`, `heightMm`, `fixed(...)` (docs/modules/robot.md "Shapes and parts"),
then run `./gradlew :sim:fromRobot`. Write a `shape.` line by hand in the robot file only for a part
Robot.java cannot draw yet (Lift, Arm, Turret, Claw, Wrist, cameras). Why: the generator keeps every
unmarked line, so a hand-written `shape.intake` would win over Robot.java's for good (it prints "kept
your shape.intake" each run); if one exists from before, delete it once Robot.java draws that part.

Otherwise, use `photos.md` to turn the mechanisms into robot shapes (`shape.NAME = box|slide|arm|turret|claw|roller|flywheel`),
then the game-piece lines (`collect.`, `shoot.`, `grab.`, and piece sensors `color.`/`distance.`) and the cameras. Mount points (`at=`) and sizes
(`size=`) in inches from the robot's centre on the floor: forward, left, up.

## 5. Check it

Write this scenario as `simulator/scenarios/robot-check.sim` (use the team's TeleOp name) and run it:
`./gradlew :sim:scenario -Pfile=../simulator/scenarios/robot-check.sim` (add `-Probot=` outside a robot project).

```text
# The robot file: loads, fits the rules, and the team's TeleOp starts with these devices.
expect size start ok        # fits the 18 in start cube now
expect size floor ok        # nothing below the tiles
expect size most ok         # every pose at full reach fits 18 x 24 x 29 in
print size                  # the sizes, and the worst pose if one does not fit
print cad                   # only with CAD models: each one next to its box
init "Main TeleOp"          # TODO: the team's TeleOp (an OpMode that uses every device)
expect error none           # no "Unable to find a hardware device": every name matches
start
hold g1.left_stick_y -1      # the drive stick forward (use the team's drive control)
wait 1
release all
expect robot y > -50        # TODO: the start y + about 10: it drove FORWARD (else flip drive.mirrored)
expect error none
stop
```

1. **It loads.** Errors start with `PROBLEM:` and name the line. Fix each one. An error inside the
   team's OpMode (a missing device, a crash) shows up as a failed `expect error none` with the message.
2. **The size rules.** `print size` shows the worst pose with its values (inches up a slide, degrees for
   an arm) and what it breaks. Compare those heights with the tape-measured ones: if the shapes make
   the robot taller or longer than the team measured, the shapes or numbers are wrong (ask them to
   measure again, e.g. "floor to the top with the lift all the way up"); if they agree, it is a real
   problem: tell the team they need hard stops or a shorter part. Never tune a shape just to pass.
3. **Names match.** `init` of the team's OpModes gives no error (every configured name is in the file).
   An OpMode that uses a device the simulator does not have (a colour sensor) stops there: tell
   the team which OpModes those are.
4. **It looks right.** Show the 3D view (`./gradlew :sim:run`, http://localhost:8780) next to a photo; ask
   the team: "does this look like your robot?" Fix what they point at.
5. Leave the scenario in `simulator/scenarios/`: it now runs with every test.

## 6. Real numbers next

Tell the team the speeds are still the parts catalog's. For real ones: run the **Characterizer** on the
robot (drive, turn, each lift/arm and flywheel), copy its logs, then
`./gradlew :sim:fit -Plog=<folder> -Probot=../simulator/robot.properties -Pwrite`.

## Report

End with: what the file now has, what is still `TODO` (and who can answer it), and the check results.
