# Keep the library up to date

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**Before this:** [Add it to a project](01-add-to-project.md). New to git? Read
[Git and GitHub for FTC teams](guides/github.md) first: it explains commit, push, pull and submodule.

Your robot project remembers ONE exact version of the library (one git commit: one saved snapshot of
the library's files). It does not change by itself. This is good: the robot never changes the night
before an event because someone changed the library. **You choose when to update.** This page says
why, when, and how.

## Why update

An old library version keeps its old bugs. A new version brings:

- **Fixes.** A bug another team found (a lift that could overshoot, an autonomous step that did not stop
  on time) is fixed once in the library. You get the fix only when you update.
- **Safety.** Limits, timeouts and stall checks get better. A safety fix protects your robot, your
  hands and the field.
- **Game and SDK updates.** FIRST changes the game manual (Team Updates) and the FTC SDK during the
  season. The library follows: new field numbers for the simulator, changes for a new SDK.
- **New features**, like new parts, recipes and simulator checks.

A team that never updates fights bugs that are already fixed. A team that updates the day before an
event may meet a change it has not tested. So: **update often, but not right before an event.**

## Find out about a new version

- **Watch the library on GitHub.** On the library's page
  ([github.com/crossbotics/ftc-helper-lib](https://github.com/crossbotics/ftc-helper-lib)), click
  **Watch** (top right) > **Custom** > tick **Releases** > **Apply**. GitHub then emails you when a
  version is released. (You need a GitHub account; one mentor or the programming lead is enough.)
- **Read the [CHANGELOG](../CHANGELOG.md).** It lists every version, newest first. The part
  "Unreleased" is what is coming next.
- **The version list:** the [tags page](https://github.com/crossbotics/ftc-helper-lib/tags) shows every
  version (`v0.16.0`, ...), newest first.
- **Check once a week** in the season (for example after the first practice of the week). It takes one
  minute. Releases come about once a week: the code changes of that week go out together.
- **The docs site shows the newest release.** Fixes to the docs go on the site between releases, so it
  can be a little ahead of the `ftc-helper-lib/docs` folder in your project. A box that says "Coming in
  the next release" is not in any version yet. On an older version? Read the `docs` folder in your own
  project: it matches your version.

## Read the CHANGELOG before you update

Open the CHANGELOG and read every version between yours and the new one. Each version has up to three
lists:

| List | Means | What you do |
| --- | --- | --- |
| **Added** | something new | nothing, unless you want it |
| **Changed** | something works differently now | **read each line**: does your code use it? You may need to change a line, or test that part again |
| **Fixed** | a bug is gone | test the part it names; it may behave a little differently (better) |

Look for these words in particular: **renamed**, **removed**, **now**, **default**, **must**,
**Tunables**. They mean "your code or your numbers may need a change". Write them down: they are your
test list after the update.

**The version number tells you a lot** (like `v0.16.0`):

- the last number changed (`v0.16.0` -> `v0.16.1`): only fixes. Small risk.
- the middle number changed (`v0.16.1` -> `v0.17.0`): new features; read **Changed**.
- the first number changed (`v0.x` -> `v1.0.0`): something old works differently. Plan a whole practice
  for it.

## See which version you have

Command line (the **Terminal** tab of Android Studio, in the robot project folder):

```bash
git submodule status
```

It prints the commit (a long code of 40 letters and digits) and, if there is one, the version tag (a
name like `v0.16.0` given to one release), for example
` 3f2c1a9e5b7d40c18a6f2e9b0d4c7a1f8e3b5c62 ftc-helper-lib (v0.16.0)`. The tag in brackets is the part
to read.

## Update: the command line

All commands run in the **Terminal** tab of Android Studio, in the robot project folder. Commit and
push your own work first (so you can tell your changes apart from the update).

```bash
cd ftc-helper-lib
git fetch --tags
git tag --sort=-v:refname
```

The last line lists the versions, newest first (press **q** if the list does not end by itself). Pick
the newest one, for example `v0.17.0`, and switch to it:

```bash
git checkout v0.17.0
cd ..
git add ftc-helper-lib
git commit -m "ftc-helper-lib v0.17.0"
```

What the lines do: switch the library to `v0.17.0`, go back to the project folder, and commit the
choice. Git may say `You are in 'detached HEAD' state`: that is normal for a submodule on a version tag.

Then do the [checks after an update](#after-an-update-check-everything). Push only when they pass:

```bash
git push
```

**Want the newest code, not a released version?** `git submodule update --remote ftc-helper-lib`
gets the newest `main` of the library, with the "Unreleased" changes. Use it to try a fix you are
waiting for, never before an event.

## Update: GitHub Desktop

GitHub Desktop has no button to change a submodule's version, so you type a few lines in a terminal it
opens for you:

1. Commit and push your own work first (**Commit to main**, **Push origin**).
2. **Repository > Open in Terminal** (Windows: **Repository > Open in Command Prompt**, or the name of
   your shell, like Git Bash). A terminal opens in the robot project folder.
3. Type these lines (use the newest version instead of `v0.17.0`;
   `git tag --sort=-v:refname` lists them, newest first, after the `git fetch --tags` line):

   ```bash
   cd ftc-helper-lib
   git fetch --tags
   git checkout v0.17.0
   cd ..
   ```

4. Back in GitHub Desktop, the **Changes** tab shows `ftc-helper-lib` as changed (a submodule
   change). Type `ftc-helper-lib v0.17.0` in **Summary** and click **Commit to main**.
5. Do the [checks after an update](#after-an-update-check-everything), then **Push origin**.

## After an update: check everything

Do these in order. Each one catches a different kind of problem.

1. **Sync Gradle:** **Sync Now** in the yellow bar, or **File > Sync Project with Gradle Files**.
2. **Build:** **Build > Make Project** (the hammer icon). A red error that names a library method:
   look for it in the CHANGELOG's **Changed** list; it says the new name or the new way.
3. **Run your tests** (your saved simulator tests in `simulator/scenarios/`). They run the same
   checks as last time, so they show what changed. In Android Studio: pick **Simulator tests** in the
   list next to the green Run button, and click Run. Or, from the `ftc-helper-lib` folder
   (`cd ftc-helper-lib` in the Terminal):

   ```bash
   ./gradlew :sim:scenario
   ```

   Or one file: `./gradlew :sim:scenario -Pfile=../simulator/scenarios/main-teleop.sim`. It first remakes
   the simulator robot file from `Robot.java` (the robot file format can change with the library).
   No tests yet? Run each autonomous in the simulator by hand (the **Simulator** button, or
   `./gradlew :sim:run`) and read its report, or record a test ([start lesson 10](tutorials/start/10-test-your-robot-code.md)).
4. **New simulator buttons?** A new library version can bring new files in `ftc-helper-lib/sim/run-buttons`.
   `./gradlew :sim:installButtons` adds the ones you do not have yet (it never replaces yours).
5. **Install the app on the robot** (the green Run button, with the Control Hub connected).
6. **Test on the robot**, on blocks first: every button of TeleOp, then each autonomous at low power
   (PRACTICE profile), then at match power. Test the parts on your list from the CHANGELOG first.
7. **Commit and push.** Now the team has the new version. Tell them, with the version number.

## After a teammate updates it

`git pull` gets their commit, but your `ftc-helper-lib` folder still has the old files until you run:

```bash
git submodule update
```

(GitHub Desktop does this by itself when it pulls.)

If `git status` shows `modified: ftc-helper-lib (new commits)` and you did not mean to change it, this
command also puts it back to the version the project expects.

## When not to update

- **The week before an event.** Use the version you tested. **Why:** a change you have not tested can
  break the robot when there is no time to fix it. Write the version in your event checklist.
- **During an event.** Not even for a fix you read about. Unless the robot cannot play without it,
  wait until you are home.
- **In the middle of a big change of your own.** Finish and commit your work first, so you know which
  change caused a problem.

**The best time:** right after an event, or at the start of a practice week, with time to test.

## Use a specific version

Each release has a tag like `v0.16.0`. To use exactly that one (also to go back to an older one):

```bash
cd ftc-helper-lib
git fetch --tags
git checkout v0.16.0
cd ..
git add ftc-helper-lib
git commit -m "Use ftc-helper-lib v0.16.0"
```

What the lines do: go into the library folder, download the list of versions, switch to `v0.16.0`, go
back to the project folder, and commit the choice. Push it, and the team gets it too.

## Go back to the old version

The new version causes a problem and there is no time to fix it? Go back. You lose nothing: the update
is only a commit.

**Not pushed yet:** switch the library back to the version you had (`git submodule status` before the
update said which):

```bash
cd ftc-helper-lib
git checkout v0.16.0
cd ..
git add ftc-helper-lib
git commit -m "Back to ftc-helper-lib v0.16.0"
```

**Already pushed:** undo the update commit with a revert (a new commit that does the opposite), then
put the library folder where the project now says:

```bash
git log --oneline
git revert --no-edit 3f2c1a9
git submodule update
git push
```

`3f2c1a9` stands for the id of the update commit in the `git log` list (press **q** to leave the
list). In GitHub Desktop: **History** tab, right-click the update commit > **Revert Changes in
Commit**, then **Push origin**.

Then tell the library's authors what broke ([an issue](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose)):
the version, what you did, and what you saw.

## A new FTC SDK version

The FTC SDK (the code from FIRST that every robot app is built on) changes every season (and
sometimes during one). The library is built against one SDK
version (now **12.0.0**, the 2026-27 release, set in `library/build.gradle`), but your robot app always uses the SDK
version in YOUR project's `build.dependencies.gradle`: the library is only compiled against 12.0.0 and
brings no SDK of its own.

SDK 12.0 changed AprilTag detections (single tags and "clusters"). The library's `WebcamTags` is written
for the SDK 12 types, so its vision classes need SDK 12 or newer; your own AprilTag code may need the
fix in the SDK's release notes.

So after you update the SDK:

1. Build. If it builds and works, you are done.
2. If the library does not build with the new SDK, check for a newer library version
   ([update](#update-the-command-line)).
3. Still broken? Report it, or fix it: [Contributing](contributing.md).

## Change the library from inside the robot project

You can edit the library's files right in Android Studio. But those changes are inside the
submodule, so they need their own commit and push to the library repository.
Follow [Contributing](contributing.md). If you only change it in your robot project, the change
is lost the next time someone updates.
