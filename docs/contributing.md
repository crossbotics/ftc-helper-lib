# Contributing: add or change something in the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Anyone on the team can improve the library. Because every robot project uses it, each change
follows the same six steps, so nothing breaks and the docs and tutorials stay true.

## The six steps (for every change)

1. **Code** in `library/src/main/java/com/crossbotics/ftchelper/<module>/`, commented for novice
   programmers: plain everyday words, the WHY, and pointers to the docs ([Commenting style](commenting-style.md)).
2. **Javadoc** on every public class and method: what it does, each parameter (with its unit and
   range), what it returns, and a short example. Plain words.
3. **Test** in `library/src/test/java/...` for any logic (math, timing, button edges, limits).
   Hardware calls cannot be unit-tested; put the logic in a plain class (like `SoftLimits` for
   `SafeMotor`) and test that.
4. **Docs:** update the module page in `docs/modules/` (the method table and an example), and
   `docs/troubleshooting.md` if it fixes a common problem.
5. **Tutorials, guides and diagrams:** if the change affects how the library is USED, update every tutorial that uses it
   (both the page in `docs/tutorials/` and its program in `examples/.../tutorials/`). A new feature
   that students will use needs a place in a tutorial: add a section to the closest one, or a new
   tutorial with its own program (and a row in `docs/tutorials/README.md`). Update the guide that
   covers it (`docs/guides/`), and any diagram that shows it (Mermaid blocks in the pages, SVGs in
   `docs/images/`). A new module gets a row in the README table and a page in `docs/modules/`.
6. **CHANGELOG.md:** add a line under "Unreleased": Added / Changed / Fixed. Start each entry with one plain sentence
   (what it does for a team), then the details; the release's "What's new" is built from those sentences.

A change is not done until all six are done.

### How the tutorials stay correct

- Every tutorial has a complete program in `examples/src/main/java/.../examples/tutorials/`.
  The build compiles them, so a library change that breaks a tutorial breaks the build.
- Tutorial 13's tests (`examples/src/test/...`) run with the build too.
- `tools/check_docs.py` fails if a link or heading link is broken, if a public library class is
  missing from the module pages, or if a tutorial program is not mentioned in a tutorial page.
- The [cookbook](cookbook.md) is the exception: every java block in it is copied from a marked region
  (`// recipe: name` ... `// end recipe`) in `examples/.../cookbook/`, and `tools/check_docs.py` fails
  if they differ. Change the Java file, then run `python3 tools/check_docs.py --fix-cookbook` to copy
  the regions into the page. The simulator test `CookbookTest` INITs and runs every cookbook OpMode.
- `tools/check_tunables.py` reads every `Tunables.java` field the robot package reads (`t.number`,
  `t.flag`, `t.tunable`, ...) and fails if [the Tunables reference](guides/tunables.md) misses one, names
  one the code no longer reads, or has a wrong default, Live or REQUIRED cell. It also checks that the
  example robots' `Tunables.java` files (and the one `:sim:newRobot` writes) use only fields their parts
  read, and that `TunableFields.java` (the lists INIT uses to stop on a misspelled field) lists exactly
  the fields each part reads. A new number in a module: add its row to the reference and its name to
  `TunableFields.java`.
- Code snippets IN the tutorial pages are not compiled. When you change a program, search its
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
- Nothing game-specific. Field positions, tag IDs and mechanism tuning belong in TeamCode.
- Classes that use time take a clock in a second constructor (`LongSupplier clockNanos`) so tests
  can control time. See `PidController` or `LoopTimer`.

## Build and test

You need JDK 17 or newer and the Android SDK. Android Studio installs both. From the library folder:

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug :examples:testDebugUnitTest :sim:testDebugUnitTest
python3 tools/check_docs.py
python3 tools/check_blocking.py
python3 tools/parts_page.py --check
python3 tools/check_tunables.py
python3 tools/check_tests.py
```

This runs every test (the library's, the examples' and the simulator's), builds the examples and every
tutorial program, and checks the docs and the parts page.
All must pass before you push. GitHub
also runs this on every push (`.github/workflows/build.yml`).

If Gradle cannot find the Android SDK, make a file `local.properties` in this folder with the line
`sdk.dir=/path/to/Android/sdk` (Android Studio shows the path in **Settings > Languages & Frameworks > Android SDK**).

## Working from inside a robot project

The library is a submodule, so you can edit it from the robot project in Android Studio and test it
on the robot right away. To save the change:

```bash
cd ftc-helper-lib
git checkout -b my-change          # the submodule is usually on a "detached" commit; make a branch
git add -A
git commit -m "Add ... to SafeMotor"
git push -u origin my-change
```

Then open a pull request on GitHub for the library, and ask the programming lead to review it.
After it is merged, update the robot project to the new version ([Keep it up to date](02-keep-up-to-date.md)).

## Help us test, and add a test

Not changing code? Testing helps as much. The test list is in [`testing/`](../testing/README.md): small
tests with steps, a pass rule and what to write down. Teams pick the ones that fit their robot on
https://crossbotics.com/ftc-helper/testing/ and send the result with the "Test report" form. To add a
test: copy a file in `testing/tests/`, change it, and run `python3 tools/check_tests.py --fix`
([how](../testing/README.md#add-a-test)).

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
2. Put the same commit on `docs-live`, check it, and push it:

```bash
git checkout docs-live
git cherry-pick <the commit>
python3 tools/check_docs.py
git push origin docs-live
git checkout main
```

   The check matters: a box fixed on `main` can use code that is not released yet. Then the check fails
   on `docs-live` (`git reset --hard origin/docs-live` drops the pick), and the fix waits for the release.
   If the cherry-pick stops with a conflict, the page changed on `main` since the release:
   `git cherry-pick --abort`, then make the fix again by hand on `docs-live` (and keep the `main` one).
3. Deploy the site (crossbotics `./scripts/deploy.sh --go`): the pages update, and no email goes out.

Docs for a feature that is not released yet stay on `main` only: teams must not read about something
their version does not have. A release moves `docs-live` to the new tag (step 4 of [Releasing a version](#releasing-a-version)).

## Releasing a version

The programming lead does this after merging changes. `vX.Y.Z` is the new version, e.g. `v0.19.0`.

1. In `CHANGELOG.md`, rename "Unreleased" to the new version and date, e.g. `## vX.Y.Z - 2026-11-01`.
   Then `python3 tools/check_tests.py --set-since vX.Y.Z` (the tests that said `since: unreleased`).
2. Commit, then tag and push (`-a` makes an annotated tag, like every release tag in this repo):

```bash
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin main --tags
```

3. Make a **GitHub release** from the tag. Its notes have three parts, in this order:
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

   On GitHub: **Releases** > **Draft a new release**, choose the tag, paste the notes, **Publish
   release**. Or:

```bash
gh release create vX.Y.Z --verify-tag --title "vX.Y.Z" --notes-file notes.md
```

4. Move `docs-live` to the release, so the site's docs start from it:

```bash
git push --force origin 'vX.Y.Z^{commit}:refs/heads/docs-live'
```

   `^{commit}` pushes the commit the tag points to. A branch cannot point at an annotated tag, so
   `git push origin vX.Y.Z:refs/heads/docs-live` is rejected.

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
