# Instructions for AI agents (Claude Code, Codex)

This is ftc-helper-lib, a Java helper library for FTC robots, used by Indianola Robotics (team 8938)
as a git submodule in its FtcRobotController projects. Its readers are high-school students with
little or no Java: the goal is to make setting up and coding a robot as easy as possible, and to
keep it useful for MANY seasons. Explanations must be plain and short, with the "why".

Read first: `README.md`, `docs/guides/architecture.md` (how it fits together),
`docs/conventions.md` (signs and units), `docs/contributing.md` (the rules),
`docs/guides/extending.md` (where new things plug in).

## Layout

- `library/` the library (packages: hardware, gamepad, drive, mechanism, launcher, vision, teleop, auto, robot, finders,
  match, control, telemetry, tuning, safety, core, testing, testkit, util). Tests in `library/src/test`
  (pretend motors/servos/telemetry in `FakeMotor.java`, `Fakes.java`). `testkit` is the PUBLIC test kit for
  teams' JUnit tests (`PretendDevices` & co., in the library so TeamCode needs no extra Gradle lines; the
  library's own tests keep their older fakes; `examples/src/test/.../v2bot/V2botTest.java` shows it).
- `examples/` compiled example OpModes: `ExampleXxx`, the starter kit (`starter/`), one program per
  tutorial (`tutorials/`, tutorial 13's tests in `examples/src/test`), and robots in five files (the four kit bots
  `gobildabot/`, `revbot/`, `andymarkbot/`, `studicabot/`, and `lessonbot/`, `flywheelbot/`, `turretbot/`,
  `v2bot/` (turret + lift with wrist and claw + an interlock rule), `odometrybot/`, `pedrobot/`, `quickstart/`; docs/templates.md lists them; main-track lesson 1 explains them; `SimFromRobotTest` runs them), and the cookbook's recipes (`cookbook/`, one file per topic,
  shown in `docs/cookbook.md`; `CookbookTest` in `sim/` INITs and runs every cookbook OpMode).
- `library/.../robot/` the five-files layer: `RobotDefinition` + modules built on the plain parts. A new
  module: extend `Module` (or `MotorPositions`/`ServoPositions`), read numbers through `TunableSection`,
  route every command through `touch()` (actions and the driver rely on it), add `suggestButtons` and
  `simLines`, a row in docs/modules/robot.md, and its fields in docs/guides/tunables.md and `TunableFields.java`. It must work when `optional()` and missing (built on
  `MissingPart`'s stand-in devices, never updated): its "done" questions true, its "busy" ones false
  (`OptionalPartsTest` calls every public method of every module kind that way). `./gradlew :sim:newRobot` (SimNewRobot) guesses modules
  from device names: teach it the new one.
- `sim/` the simulator: a pretend Control Hub and Driver Hub on a laptop (`./gradlew :sim:run`, then
  http://localhost:8780). Its code is in `sim/src/test` (the SDK only runs on a laptop there); the
  page is `sim/src/test/resources/sim/index.html` (its drawing helpers in `simview.js` next to it; what it
  reads: `SimJson` for the state, `SimView` for the 50-a-second stream and the Layers data); robot files are in `sim/robots/`.
  The page's auto builder is `sim/.../resources/sim/builder/` (ES modules: `builder.js` the panel, `simcore.js`
  the simulator-only logic, and `pathcore.js`, `stepscore.js`, `mapdraw.js`, `setupcard.js` shared with the web
  site's path builder: the LIBRARY is their source, the site copies them); it runs plans through `BuiltAuto`
  (names resolved by `BuiltCode`, runs watched by `BuiltRun`). `node tools/check_builder.mjs` checks it. Its scripted
  tests (`FinderSafetyTest`) run whole OpModes: add one when an OpMode's behavior matters (safety,
  help, buttons). Not part of robot builds. Guide: docs/guides/simulator.md. Parts page:
  docs/guides/parts-catalog.md is made from sim/parts/parts.json by tools/parts_page.py (never edit it by hand).
- `skills/robot-file/` the robot-file skill (for teams' agents): builds a team's simulator robot file. When
  the robot file format or `:sim:fromConfig` changes, update SKILL.md, interview.md and photos.md too.
  - To check a change to an OpMode yourself, write a scenario file and run
    `./gradlew :sim:scenario -Pfile=...` (report: each check ok/FAIL with the value seen).
  - `sim/parts/parts.json`: motors and servos with seller-page numbers and the date checked; add a
    part the same way (never from memory). `sim/fields/<season>.json`: one per season, made from the
    official field CAD by a script, not typed by hand. `sim/fields/<season>.rules.json` next to it: the game's rules
    (points, match times, FLOWER heights, tip time, clean-shot rules), typed from the manual with section
    numbers (SimRules; scoring in SimScore, the clean-shot judge in SimCellJudge, a port of the turret
    simulator's judge() checked by SimCellJudgeTest).
- `docs/` `modules/` (reference: every public class and method), `tutorials/` (hands-on lessons),
  `guides/` (how-to and checklists), `images/` (SVG diagrams), `starter-kit.md`, setup/update pages.
- `testing/` the community test list: one file per test in `testing/tests/`, words in `vocabulary.json`,
  `catalogue.json` made by `python3 tools/check_tests.py --fix` (crossbotics.com reads it). A new
  user-facing feature gets a test there. Issue forms in `.github/ISSUE_TEMPLATE/` (their option words
  must match vocabulary.json), the triage helper's guide in `.github/triage.md`. How: `testing/README.md`.
- `tools/check_docs.py` checks links, heading links, class coverage, tutorial programs, and that every
  java block in `docs/cookbook.md` matches its `// recipe: name` region in `examples/.../cookbook/`
  (after changing one: `python3 tools/check_docs.py --fix-cookbook`). A guide may show a recipe too, with the
  same `<!-- recipe: name -->` marker (docs/guides/autos.md does): it is checked and fixed the same way.
- `tools/check_tunables.py` finds every Tunables field the robot package reads and checks it against
  `docs/guides/tunables.md` (the reference: one table per part) and the example robots' Tunables files.
  A module that reads a new field (or reads one through a name that is not a string literal: add it to
  the script's DYNAMIC list) needs a row there, and its name in `robot/TunableFields.java` (INIT stops on
  any field a part does not list there; the script checks the lists match the code).

## Rules: documentation is part of every change

Every change follows the six steps in `docs/contributing.md`. A change is NOT done until all are done:

1. Code, **commented for novice programmers** per `docs/commenting-style.md` (plain everyday words, the WHY,
   pointers to the docs that explain more; examples and tutorial programs get the most comments).
2. Javadoc on every public class/method (what, parameters with units and ranges, returns, a short example).
3. Tests (pretend hardware and an injected clock; simulations for anything that moves).
4. The module page in `docs/modules/` (method tables + an example). New package = new page + README table row.
5. Tutorials, guides, diagrams: every tutorial page AND its compiled program that use the changed API;
   the guide that covers it; any Mermaid diagram or `docs/images/*.svg` that shows it. A new
   user-facing feature gets taught in a tutorial (a section in the closest one, or a new tutorial
   with its own program and a row in `docs/tutorials/README.md`). Add code examples with clear
   "what and why" text.
6. `CHANGELOG.md` (Added / Changed / Fixed under "Unreleased").

Also keep current when relevant: `docs/troubleshooting.md` (new common problems),
`docs/from-last-season.md`, `docs/guides/new-season.md` (rules and SDK facts per season),
`docs/guides/testing.md` (checklists), `docs/starter-kit.md` and its files, and THIS file
(when the layout or rules change).

Code snippets inside Markdown are not compiled (except the cookbook's, checked against
`examples/.../cookbook/`): when you change a program, update the matching snippets in its page. When
an API changes, update the cookbook recipes that use it too; a new user-facing feature gets a recipe. Check SVG diagrams by rendering them (e.g. `qlmanage -t -s 700 -o <dir> <file>.svg`).

## Rules: code

- Java 8 language level. FTC SDK dependencies stay `compileOnly` in the module build files.
- One sign convention everywhere (`docs/conventions.md`): drive forward/strafe right/turn clockwise +;
  headings, turret angles, bearings counterclockwise +. Do not add a second one.
- **Non-blocking (see `docs/guides/non-blocking.md`):** nothing that runs in the loop may wait:
  no `sleep`, no waiting loops, no synchronous network/USB requests in commands, `update()` or
  button actions. Slow hardware requests go on a `BackgroundWorker` (use `submitLatest` for
  repeated values; never move motors from it). Only autonomous moves/steps block, ON PURPOSE: they
  say BLOCKS in their Javadoc, check `opModeIsActive()`/`ctx.isActive()`, keep mechanisms updating,
  have a time limit, and stop motors in `finally`. Examples never call `sleep()`.
  `tools/check_blocking.py` enforces this; a new intentionally blocking class must be added to its
  allow-list with the reason.
- Hardware setup leaves motors at power 0. Nothing moves before START.
- Safety stays on in every profile (limits, timeouts, jam checks, stale-target rejection).
- Extend through the interfaces (`Subsystem`, `AutoStep`, `Feeder`, `TargetSource`); keep new parts
  testable (devices and a clock in the constructor).
- No game-specific code (field positions, tag ids, scoring heights): that belongs in TeamCode.

## Before finishing

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug :examples:testDebugUnitTest
python3 tools/check_docs.py
python3 tools/check_blocking.py
python3 tools/check_tunables.py
node tools/check_builder.mjs       # the simulator's auto builder page code
python3 tools/check_tests.py
```

All must pass. Releases: follow `docs/contributing.md#releasing-a-version`. The mentor asks for an
outside review (Codex "Astra", and Fable) of large changes before release.
