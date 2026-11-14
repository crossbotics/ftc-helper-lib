# Contributing: add or change something in the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Anyone on the team can improve the library. Because every robot project uses it, each change
follows the same six steps, so nothing breaks and the docs and tutorials stay true.

## The six steps (for every change)

1. **Code** in `library/src/main/java/com/crossbotics/ftchelper/<module>/`, commented for novice
   programmers: plain everyday words, the WHY, and pointers to the docs ([Commenting style](commenting-style.md)).
   Doc pages follow the [Writing style](writing-style.md).
2. **Javadoc** on every public class and method: what it does, each parameter (with its unit and
   range), what it returns, and a short example. Plain words.
3. **Test** in `library/src/test/java/...` for any logic (math, timing, button edges, limits).
   A unit test cannot call hardware. So put the logic in a plain class (like `SoftLimits` for
   `SafeMotor`) and test that.
4. **Docs:** update the module page in `docs/modules/` (the method table and an example), and
   `docs/troubleshooting.md` if it fixes a common problem.
5. **Tutorials, guides and diagrams:** if the change affects how the library is USED, update every tutorial that uses it
   (both the page in `docs/tutorials/` and its program in `examples/.../tutorials/`). A new feature
   that students will use needs a place in a tutorial: add a section to the closest one, or a new
   tutorial with its own program (and a row in `docs/tutorials/README.md`). Update the guide that
   covers it (`docs/guides/`), and any diagram that shows it (Mermaid blocks in the pages, SVGs in
   `docs/images/`). A new module gets a row in the README table and a page in `docs/modules/`.
   A new docs section (a folder in `docs/`), a moved page or a new rule for teams' code also goes in the
   ftc-helper skill (`skills/ftc-helper/SKILL.md`): teams' AI agents use it as their map of the docs.
6. **CHANGELOG.md:** add a line under "Unreleased": Added / Changed / Fixed. Start each entry with one plain sentence
   (what it does for a team), then the details. The release's "What's new" comes from those sentences.

A change is not done until all six are done.

### How the tutorials stay correct

- Every tutorial has a complete program in `examples/src/main/java/.../examples/tutorials/`.
  The build compiles them, so a library change that breaks a tutorial breaks the build.
- Tutorial 13's tests (`examples/src/test/...`) run with the build too.
- `tools/check_docs.py` fails if a link or heading link is broken, if a public library class is
  missing from the module pages, or if a tutorial program is not mentioned in a tutorial page.
- The [cookbook](cookbook/README.md) is the exception: every java block in it is a copy of a marked region
  (`// recipe: name` ... `// end recipe`) in `examples/.../cookbook/`. `tools/check_docs.py` fails
  if they differ. Change the Java file, then run `python3 tools/check_docs.py --fix-cookbook` to copy
  the regions into the page. The simulator test `CookbookTest` INITs and runs every cookbook OpMode.
- `tools/check_tunables.py` reads every `Tunables.java` field the robot package reads (`t.number`,
  `t.flag`, `t.tunable`, ...) and fails if [the Tunables reference](reference/tunables.md) misses one, names
  one the code no longer reads, or has a wrong default, "In tuning menu" or REQUIRED cell. It also checks that the
  example robots' `Tunables.java` files (and the one `:sim:newRobot` writes) use only fields their parts
  read, and that `TunableFields.java` (the lists INIT uses to stop on a misspelled field) lists exactly
  the fields each part reads. A new number in a module: add its row to the reference and its name to
  `TunableFields.java`.
- `tools/check_docs.py` also checks the skills for teams' AI agents (`skills/`): every path they name must
  exist, and the ftc-helper skill must list every `docs/` folder. The website makes its docs files for AI
  chats (`llms.txt`) from the docs at every build, so they need nothing by hand.
- The build does not compile code snippets IN the tutorial pages. When you change a program, search its
  tutorial page for the same lines and update them.
- Before a release, read the changed tutorials once from top to bottom as a student would.

## Style

- Java 8 (no `var`, no `List.of`). Same as the FTC SDK.
- Signs and units from [Signs and units](conventions.md). Put the sign in the name when it can be
  confused: `turnClockwise`, `headingCcwDeg`, `distanceIn`, `timeoutMs`.
- Nothing in the library may block TeleOp ([Non-blocking code](guides/non-blocking.md)): no `sleep()`, no waiting loops, except in clearly
  autonomous-only classes like `AutoDrive` (which always check `opModeIsActive()`, have a timeout,
  and stop motors in `finally`).
- Hardware setup methods leave motors at power 0.
- Parts stay general: any team, any season. A robot that needs something new gets the general block that is
  missing, not its own mechanism. Season knowledge (the field, the rules, piece and goal names, scoring) may
  be in the library as season definitions, kept apart and updated each season. A team's own start spots,
  routes, positions and tuned numbers belong in TeamCode.
- In the docs, text the robot or a tool SHOWS (a Driver Station message, a warning, a Gradle or git
  error) goes in `<samp>...</samp>`. Text you TYPE (code, names, commands) stays in backticks. The
  site shows the two differently, so a student can tell "what you see" from "what you write".
  Inside `<samp>`, write `&lt;` and `&amp;` for `<` and `&`.
- Classes that use time take a clock in a second constructor (`LongSupplier clockNanos`) so tests
  can control time. See `PidController` or `LoopTimer`. A default clock is `Time::nanos` (core), so a
  test's `PretendClock` moves every part at once (most parts do). Keep `System.nanoTime` only where the time
  is compared with one from the SDK (camera picture times) or measures the loop itself.

## Build and test

You need JDK 17 or newer and the Android SDK. Android Studio installs both. You also need Node.js for the
`check_builder.mjs` line.

**Where:** a terminal, in the `ftc-helper-lib` folder.

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug :examples:testDebugUnitTest
./gradlew :sim:testDebugUnitTest :pedro:testDebugUnitTest
python3 tools/check_docs.py           # links, docs for every class, the Cookbook's code
python3 tools/parts_page.py --check   # the parts page matches sim/parts/parts.json
python3 tools/check_blocking.py       # nothing in the loop waits
python3 tools/check_tunables.py       # every Tunables field the code reads is documented
node tools/check_builder.mjs          # the simulator's auto builder page code
python3 tools/check_tests.py          # the community test list and testing/catalogue.json
```

This runs every test (the library's, the examples', the simulator's and the Pedro module's), builds the
examples and every tutorial program, and checks the docs, the parts page, the auto builder page and the
community test list. All must pass before you push. GitHub runs the same checks on every push
(`.github/workflows/build.yml`), except the `:pedro` tests.

If Gradle cannot find the Android SDK, make a file `local.properties` in this folder with the line
`sdk.dir=/path/to/Android/sdk` (Android Studio shows the path in **Settings > Languages & Frameworks > Android SDK**).

## Working from inside a robot project

The library is a submodule, so you can edit it from the robot project in Android Studio and test it
on the robot right away. To save the change, type these lines.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
cd ftc-helper-lib
git checkout -b my-change          # the submodule is usually on a "detached" commit; make a branch
git add -A
git commit -m "Add ... to SafeMotor"
git push -u origin my-change
```

Then open a pull request on GitHub for the library, and ask the programming lead to review it.
After someone merges it, update the robot project to the new version ([Keep it up to date](02-keep-up-to-date.md)).

## Help us test, and add a test

If you are not changing code, testing helps as much. The test list is in [`testing/`](../testing/README.md): small
tests with steps, a pass rule and what to write down. Teams pick the ones that fit their robot on
https://crossbotics.com/ftc-helper/testing/ and send the result with the "Test report" form. To add a
test: copy a file in `testing/tests/`, change it, and run `python3 tools/check_tests.py --fix`
([how](../testing/README.md#add-a-test)).

## The simulator: notes for maintainers

Teams use the simulator from [Simulator](simulator/README.md). These notes are for people who change it.

### Fields: one file per season

`sim/fields/<season>.json` holds a season's field: solid elements (the robot bumps into them), floor
zones and tape lines, things overhead, and the AprilTags with their position and the way they face. The
robot file picks one (`field = biobuzz-2026`). The page draws the field. When the robot bumps into an
element, the robot turns red and the panel names the element. When the robot file loads, the simulator
refuses a start position that overlaps a wall or an element. A new season is a new file. A script made the
BIOBUZZ file from the official field CAD. The script is not in this library yet (ask the maintainer). The
same kind of script works for the next CAD.

Game pieces in the field file: `pieceTypes` (name, `diameterIn`, `massG`, `color`, `bounce`), `pieces`
(where each starts: type, x, y, z), `goals` (name, x, y, the opening's height `z`, `radiusIn`) and
`preload` (how many of each type a robot starts with).

A season's rules go in `sim/fields/<season>.rules.json`, next to the field file (see
[Game rules and the score](guides/how-the-simulator-works.md#game-rules-and-the-score)). The rules are the points, match times, which pieces
and zones are each alliance's, FLOWER heights, the HIVE's tip time and the clean-shot rules. Someone types
them from the manual, section by section, so a re-made field file never loses them. For a field file given
by path, the simulator looks for the rules file in the same folder, with the same name.

The 3D view uses a field's 3D model: a `.glb` file in the same frame, in inches, z up. It sits next to the
field file, with the same name. `sim/fields/biobuzz-2026.glb` is BIOBUZZ's, made from FIRST's official
field CAD, and every robot file with `field = biobuzz-2026` uses it. A robot file can name another model
with `field.model` (a path from the robot file). Without a model, the 3D view builds the field from the
field file.

### The auto builder's shared files

**Shared with the web builder.** The plan, its checks, the Java, the scenario, the start note, the
setup card and the auto sheet come from the same files as the web builder: `pathcore.js`, `stepscore.js`,
`mapdraw.js` and `setupcard.js` in `sim/src/test/resources/sim/builder/`. **The library is their source**:
change them here, and release them. The web site's `scripts/sync-path-builder.mjs` copies them, with the
field file, from the release. `node tools/check_builder.mjs` does three checks:

- The Java and the scenario for a set of plans must be letter for letter what the shared files wrote when
  the fixtures were made (`sim/src/test/resources/builder/fixtures.json`;
  `--write-fixtures sim/src/test/resources/sim/builder` makes them again after an intended change).
- It checks the simulator-only part (`simcore.js`).
- When it finds a checkout of the site, it fails when the site's copies differ from the release that the
  site names.

The fixtures are a snapshot: they find a change, but not a first answer that was wrong. So one small plan
has its numbers worked out by hand. The site copies these files and the field from that release, never
from your working folder.

### The page's helpers

The page's helpers (gliding between updates, the striped ball, the labels) are in `sim/.../simview.js`.
It is a small file with no other library in it, so another page (like a team's own shot simulator) can
load it too.

### Automatic tests

`sim/src/test/.../FinderSafetyTest.java` runs whole OpModes with scripted button presses, on every
push. For example, it checks that help stops the motors, and that a stick held through help does not
count. It checks that the Drive Tuner's push test finds the real wheel size. It also runs every example
with the sticks and a trigger held, and checks that no motor stays on by itself at STOP. It looks before
the simulator switches the motors off. Its first run found a real bug: every finder crashed at INIT in
v0.7.2.

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
./gradlew :sim:testDebugUnitTest
```

`SimRecorderTest` plays a recording session the way the page sends it, and saves the test. It replays the
test (PASSED), changes the robot (FAILED), updates the test from a measuring run, and replays it (PASSED).

`BuiltAutoTest` runs the [auto builder](building-autos/06-auto-builder-steps.md)'s plans with the example robots (a Simple steps
plan on goBILDA's starter bot, a Pedro path on the Pedro robot). It checks that they end near the plan.
The builder's page code has its own check (Node.js), also on every push:

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first).

```bash
node tools/check_builder.mjs
```

## When to release

- **Once a week:** the code changes made that week go out together in one release, with one set of
  notes. Until then they collect under "Unreleased" in `CHANGELOG.md`, and the docs site shows them as
  "Coming in the next release".
- **A larger change** (a new tool, a new module) can go out on its own when it is ready.
- **An urgent fix** (something that stops teams) goes out at once, as a bug-fix version.

**Why:** each release sends an email to every team that watches the releases. Fewer, fuller releases are
easier to follow than many small ones.

## Docs fixes between releases

A fix to the docs of something already released (a typo, a clearer step, a wrong number) does not need
a release. The site builds its docs from the `docs-live` branch: the last release plus these fixes.

1. Make the fix on `main` as usual, in a commit that changes only `docs/` (and `README.md`).
2. Put the same commit on `docs-live`, check it, and push it.

**Where:** a terminal, in the `ftc-helper-lib` folder.

```bash
git checkout docs-live
git cherry-pick <the commit>
python3 tools/check_docs.py
git push origin docs-live
git checkout main
```

   The check matters: a box fixed on `main` can use code that is not released yet. Then the check fails
   on `docs-live` (`git reset --hard origin/docs-live` drops the pick), and the fix waits for the release.
   If the cherry-pick stops with a conflict, the page changed on `main` since the release. Run
   `git cherry-pick --abort`, then make the fix again by hand on `docs-live` (and keep the `main` one).
3. Deploy the site (crossbotics `./scripts/deploy.sh --go`): the pages update, and no email goes out.

On GitHub, a push to `docs-live` runs only the docs checks (`tools/check_docs.py`, `tools/check_tests.py`),
not the Gradle build: its code already passed as the release. That is the `docs` job in
`.github/workflows/build.yml`. GitHub uses the workflow file of the branch it runs on. So a change to that
file reaches `docs-live` with the next release, or sooner when you cherry-pick that commit (only the workflow file).

Docs for a feature that is not released yet stay on `main` only: teams must not read about something
their version does not have. A release moves `docs-live` to the new tag (step 4 of [Releasing a version](#releasing-a-version)).

## Releasing a version

The programming lead does this after merging changes. `vX.Y.Z` is the new version, for example `v0.19.0`.
`tools/release.py` does the steps and stops at the first problem, so a step cannot be forgotten.

1. Get the changelog and the test list ready. The script renames "Unreleased" in `CHANGELOG.md` to the new version and
   today's date, and sets `since:` on the tests that said `since: unreleased`.

**Where:** a terminal, in the `ftc-helper-lib` folder.

```bash
python3 tools/release.py prepare vX.Y.Z
```

   If it stops after it changed files, run `git restore CHANGELOG.md testing/`, fix the problem and run it again.

2. Read the changes, run the checks in `AGENTS.md`, and commit.
3. Write the notes of the **GitHub release** in a file, `notes.md` in the repo's top folder (git ignores it). They have three parts, in this order
   (the script stops if one is missing):
   - **What's new:** 3 to 6 lines in plain words, one per new feature, for a student who will not read
     the rest. Say what it does for the team, not how it is built.
   - **Plain words in these first two parts.** Write for an FTC student who does not code: short
     sentences, no class or method names, no file paths. FTC and robot words are fine (AprilTags, INIT,
     TeleOp, autonomous, servo, odometry, Driver Station): every FTC team knows them. Bold the feature's
     name at the start of each line. Read it aloud once: if it needs a programmer to understand, rewrite it.
   - **Before you update:** anything that works differently now (a button, a default, a renamed page or
     setting), and what to do about it. If nothing changes, say "Nothing old stops working."
     Then a link to [Keep it up to date](https://crossbotics.com/ftc-helper/docs/02-keep-up-to-date/).
   - **Full change list:** that version's CHANGELOG part, pasted as it is.

4. Publish. It runs the checks that need no Gradle (the Gradle tests are step 2), tags, pushes `main` and the tag together, makes the GitHub release, moves
   `docs-live` to the release, and checks that `docs-live` holds the same files as the tag. `--dry-run` gets the
   latest from GitHub and runs only the checks. It pushes nothing.

**Where:** a terminal, in the `ftc-helper-lib` folder.

```bash
python3 tools/release.py publish vX.Y.Z notes.md
```

   GitHub checks the tag too: the build fails if the tag has no changelog heading or a test still says
   `since: unreleased`. The script moves `docs-live` with a merge commit that holds exactly the tag's files. It does not force-push.
   If publish stops part way, fix the problem and run the same command: it goes on with the steps not done yet.

**Why:** a tag alone sends no message. Teams that click **Watch > Custom > Releases** on GitHub get an
email for each release, and that is how they learn to update ([Keep it up to date](02-keep-up-to-date.md#find-out-about-a-new-version)).

Version numbers: change the middle number (0.**19**.0) for new features, the last number
(0.19.**1**) for bug fixes, and the first number when something old stops working the same way.

## Writing a tutorial

Use the same shape as the others, so students always know where to look:

1. **Title, "You will learn", "You need"** (hardware names, earlier tutorials), links to the full
   program and the module reference.
2. **Numbered sections** that build the program step by step. Each one: why, the code, what each
   line does. Explain any new Java idea the first time it appears.
3. **Run it:** what the student should see, as a checklist.
4. **If something goes wrong:** a table of problem -> fix.
5. **Try this:** 2-4 small exercises with hints.
6. **Next:** the next tutorial.

Write for a high-school student: short sentences, plain words, the "why" before the "how".
Safety first: low power, wheels off the ground, hard stops.

## Ideas for later

- A subsystem/command structure for bigger robots.
