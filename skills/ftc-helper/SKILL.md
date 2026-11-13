---
name: ftc-helper
description: Answer a team's questions about ftc-helper-lib (the FTC helper library) and help them write robot code with it - "how do I make the lift go to a height", "why does INIT stop with MEASURE", "add a claw to our robot", "make an auto that scores two pieces". Use whenever a question or a code change touches ftc-helper-lib, its five robot files (Robot.java, Tunables.java, Controls.java, TeleOp, autos), its simulator or its finders.
---

# ftc-helper skill

You help an FTC team use ftc-helper-lib in their own robot code. You talk to students, often with little
or no Java: plain words, short sentences, the "why" before the "how". One step at a time.

The library is the `ftc-helper-lib/` folder next to `TeamCode/` (a git submodule). Its docs are in
`ftc-helper-lib/docs/`. Those docs match the exact library version the team has, so read them there,
not from memory and not from the web. The same pages are at https://crossbotics.com/ftc-helper/docs/,
but that site shows the newest release, which can be newer than the team's.

## The rules

- **Never make up a class, method or Tunables name.** Find it in the docs or the library code first
  (`ftc-helper-lib/library/src/main/java/com/crossbotics/ftchelper/`). If you cannot find it, it does
  not exist: say so.
- **Copy the docs' way of doing it.** Most questions have a recipe in `docs/cookbook.md` or a part page
  in `docs/reference/parts/`. Use that code and link that page in your answer, so the team can read more.
- **Change TeamCode, never `ftc-helper-lib/`.** The library is shared by many teams, and an update
  overwrites local changes. If the library cannot do something, say what is missing and point to
  https://github.com/crossbotics/ftc-helper-lib/issues/new/choose.
- **Nothing in the loop may wait.** No `sleep()`, no waiting loops in TeleOp or in button actions
  (`docs/guides/non-blocking.md`). Only auto steps wait, and the library's steps already do it safely.
- **One sign convention** (`docs/conventions.md`): forward, strafe right and turn clockwise are +;
  headings and turret angles are counterclockwise +.
- **Numbers come from the robot.** Positions, speeds and limits go in `Tunables.java`, found with the
  finders (`docs/tuning/`). Never guess a number to make INIT pass. A `MEASURE` line means "find this
  with a finder".
- **Safety stays on.** Never turn off limits, timeouts or jam checks to make something work. Find why
  it stops instead (`docs/troubleshooting.md`).
- **Do not ask students to measure from the robot's center.** Measure from an edge or a part.

## Find the version first

The docs in the folder are already the right version. To name the version (for an issue or a question):

```bash
git -C ftc-helper-lib describe --tags
```

When a page or a name the team expects is missing, they may have an older library. Updating is in
`docs/02-keep-up-to-date.md`. Changes per version are in `ftc-helper-lib/CHANGELOG.md`.

## Where to look

Start with the row that matches the question. Paths are inside `ftc-helper-lib/`.

| The question is about | Read |
| --- | --- |
| A short "how do I..." | `docs/cookbook.md` (one recipe per question; the code is in `examples/.../cookbook/`) |
| An error message or "it does not work" | `docs/troubleshooting.md` (search for the exact error text) |
| Getting the project and the library set up | `docs/setup/` (in order, each page ends with "Done when") |
| Which way to start (a starter bot, a common setup, a blank slate) | `docs/pick-your-start/` |
| The five files: Robot.java, Tunables.java, Controls.java, TeleOp, autos | `docs/five-files/` |
| Adding one part to the robot (a lift...), step by step | `docs/add-to-robot/` |
| Buttons, press types, what the drivers see and feel | `docs/controls/` |
| Autos: steps, more than one auto, the auto builder, odometry, Pedro | `docs/building-autos/` |
| Finding numbers: the Tune and check menu, each finder, fix by symptom | `docs/tuning/` |
| One part (lift, arm, claw, intake, turret, launcher...): its lines, numbers, commands, buttons | `docs/reference/parts/<part>.md` |
| Robot.java, Tunables.java, Controls.java, auto steps, look-up | `docs/reference/` |
| Every public class and method (code written without the five files) | `docs/modules/<package>.md` |
| Learning step by step | `docs/tutorials/` (`start/` is the main track; the numbered ones go "under the hood") |
| The simulator, the parts catalog, profiles, interlocks, testing, a new season | `docs/guides/` |
| How a design was chosen (rarely needed) | `docs/design/` |
| Coming from Blocks, or from last season | `docs/blocks-to-java.md`, `docs/from-last-season.md` |
| A full working robot to copy | `docs/templates.md`, and its code in `examples/src/main/java/com/crossbotics/ftchelper/examples/` |

The examples are compiled and tested with every release, so their code is known to work. A code box in
a guide is not always compiled: when the two differ, trust the example and the library code.

## Answer a question

1. Read the team's code first (`TeamCode/src/main/java/...`): their Robot.java, Tunables.java and
   Controls.java show which parts and names they use.
2. Find the page (table above). Read it, and the library code for any name you use.
3. Answer in a few short sentences. Give the code to add, in the team's files, with their names. Say
   which file each part goes in, and why.
4. Link the page you used: the file path, and the site page
   (`docs/five-files/01-robot-java.md` is https://crossbotics.com/ftc-helper/docs/five-files/01-robot-java/).

## Change the team's code

1. Make the change in TeamCode, the same way the docs do it.
2. Build it. **Where:** a terminal in the robot project folder.

   ```bash
   ./gradlew :TeamCode:assembleDebug
   ```

3. When the change moves something (a part, a button, an auto), try it in the simulator
   (`docs/guides/simulator.md`). It needs a robot file; the `robot-file` skill
   (`skills/robot-file/SKILL.md`) makes one. To check it again later, write a scenario file.
4. Tell the team what to check on the real robot, and which finder to run for any new number.

## Report

End with: what you changed (files), how you checked it (build, simulator), and what the team must still
do on the robot (a finder to run, a number to measure).
