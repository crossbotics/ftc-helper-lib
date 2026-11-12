# Keep the library up to date

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**Before this:** [Add the library](setup/04-add-the-library.md). If you are new to git, read
[Git and GitHub for FTC teams](guides/github.md) first: it explains commit, push, pull and submodule.

Your robot project remembers ONE exact version of the library (one git commit: one saved snapshot of
the library's files). It does not change by itself. This is good: the robot never changes the night
before an event because someone changed the library. **You choose when to update.**

## Why update

An old library version keeps its old bugs. A new version brings:

- **Fixes.** We fix a bug another team found (a lift that could overshoot, an autonomous step that did not
  stop on time) once, in the library. You get the fix only when you update.
- **Safety.** Limits, timeouts and stall checks get better. A safety fix protects your robot, your
  hands and the field.
- **Game and SDK updates.** FIRST changes the game manual (Team Updates) and the FTC SDK during the
  season. The library follows: new field numbers for the simulator, changes for a new SDK.
- **New features**, like new parts, recipes and simulator checks.

A team that never updates fights bugs that are already fixed. A team that updates the day before an
event may meet a change it has not tested. So: **update often, but not right before an event.**

## Find out about a new version

- **The Simulator button tells you.** Once a day it asks GitHub for the newest version. When a newer version is out, the Run
  window and the simulator page say so ([the Update library button](#update-the-update-library-button)).
- **Watch the library on GitHub.** On the library's page
  ([github.com/crossbotics/ftc-helper-lib](https://github.com/crossbotics/ftc-helper-lib)), click
  **Watch** (top right) > **Custom** > tick **Releases** > **Apply**. GitHub then emails you about each new
  version. (You need a GitHub account. One mentor or the programming lead is enough.)
- **Read the [CHANGELOG](../CHANGELOG.md).** It lists every version, newest first. When changes
  are waiting for the next version, they are at the top, under "Unreleased".
- **The version list:** the [tags page](https://github.com/crossbotics/ftc-helper-lib/tags) shows every
  version (`v0.16.0`, ...), newest first.
- **Check once a week** in the season (for example after the first practice of the week). In the season, new versions can come out several times a week.
- **The docs site shows the newest release.** We put docs fixes on the site between releases, so the
  site can be a little ahead of the `ftc-helper-lib/docs` folder in your project. A box that says "Coming in
  the next release" describes something that is not in any version yet. If you use an older version, read
  the `docs` folder in your own project: it matches your version.

## Read the CHANGELOG before you update

Open the CHANGELOG and read every version between yours and the new one. Each version has up to three
lists:

| List | Means | What you do |
| --- | --- | --- |
| **Added** | something new | nothing, unless you want it |
| **Changed** | something works differently now | **read each line**, and check whether your code uses it. You may need to change a line, or test that part again |
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

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
git submodule status
```

It prints the commit (a long code of 40 letters and digits). When the commit has a version tag (a
name like `v0.16.0` given to one release), it prints the tag too. For example:
` 3f2c1a9e5b7d40c18a6f2e9b0d4c7a1f8e3b5c62 ftc-helper-lib (v0.16.0)`. Read the tag in brackets.

## Update: the Update library button

This is the easiest way. It needs git on the command line: `git --version` in the Terminal must print a
version. If it does not, read [git is not recognized](troubleshooting.md#autonomous-choice-and-git-problems).

1. Commit and push your own work first. **Why:** you can then tell your changes apart from the update.
2. In the list next to the green Run button, pick **Update library**, then click Run. If it is not in the list,
   close the project and open it again ([the buttons](setup/04-add-the-library.md#the-simulator-buttons-you-do-nothing)).
3. It gets the version list from GitHub, switches the `ftc-helper-lib` folder to the newest version, and
   shows **What changed** (the CHANGELOG parts since your version). Read it: a version can ask you to
   change a line in your code.
4. Sync (the elephant button), build (the hammer), and fix what the notes say.
5. Do the [checks after an update](#after-an-update-check-everything).
6. Commit the change (`ftc-helper-lib v0.20.2`, for example: the Run window says the exact text). Push
   only when the checks pass.

It commits nothing by itself, and it stops when someone has changed files inside `ftc-helper-lib`. **A specific
version, or back to the old one:** open **Run > Edit Configurations**, pick **Update library**, and add
`-PlibVersion=v0.20.1` after `updateLibrary` in the **Run** box.

**How you know a version is out:** the **Simulator** button checks once a day. When a newer version is
out, the Run window and the simulator page (under the Driver Hub) say so: `ftc-helper-lib v0.20.2 is out
(this project has v0.20.1)`. It updates nothing. (Offline, it says nothing.)

## Update: the command line

Commit and push your own work first, so you can tell your changes apart from the update.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
cd ftc-helper-lib
git fetch --tags
git tag --sort=-v:refname
```

The last line lists the versions, newest first (press **q** if the list does not end by itself). Pick
the newest one, for example `v0.17.0`, and switch to it.

**Where:** the same Terminal tab, now in the `ftc-helper-lib` folder (after the `cd ftc-helper-lib` line).

```bash
git checkout v0.17.0
cd ..
git add ftc-helper-lib
git commit -m "ftc-helper-lib v0.17.0"
```

What the lines do: switch the library to `v0.17.0`, go back to the project folder, and commit the
choice. Git may say `You are in 'detached HEAD' state`: that is normal for a submodule on a version tag.

Then do the [checks after an update](#after-an-update-check-everything). Push only when they pass.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
git push
```

**For the newest code instead of a released version:** in the project folder,
`git submodule update --remote ftc-helper-lib` gets the newest `main` of the library, with the "Unreleased" changes. Use it to try a fix you are
waiting for, never before an event.

## Update: GitHub Desktop

GitHub Desktop has no button to change a submodule's version, so you type a few lines in a terminal it
opens for you. Those lines need git on the command line, which GitHub Desktop does not give you on
Windows: `git --version` must print a version first
([git is not recognized](troubleshooting.md#autonomous-choice-and-git-problems)).

1. Commit and push your own work first (**Commit to main**, **Push origin**).
2. Click **Repository > Open in Terminal** (Windows: **Repository > Open in Command Prompt**, or the name of
   your shell, like Git Bash). A terminal opens in the robot project folder.
3. Type these lines. Use the newest version instead of `v0.17.0`: after the `git fetch --tags` line,
   `git tag --sort=-v:refname` lists them, newest first.

   **Where:** that terminal, in the robot project folder.

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

1. **Sync Gradle:** click **Sync Now** in the yellow bar, or **File > Sync Project with Gradle Files**.
2. **Build:** click **Build > Make Project** (the hammer icon). When a red error names a library method,
   look for it in the CHANGELOG's **Changed** list. That list gives the new name or the new way.
3. **Run your tests** (your saved simulator tests in `simulator/scenarios/`). They run the same
   checks as last time, so they show what changed. In Android Studio: pick **Simulator tests** in the
   list next to the green Run button, and click Run. Or type the command.

   **Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](setup/09-the-terminal.md#which-folder))

   ```bash
   ./gradlew :sim:scenario
   ```

   To run one file: `./gradlew :sim:scenario -Pfile=../simulator/scenarios/main-teleop.sim`. The command
   first remakes the simulator robot file from `Robot.java` (the robot file format can change with the
   library). With no tests yet, run each autonomous in the simulator by hand (the **Simulator** button, or
   `./gradlew :sim:run`) and read its report. Or record a test ([start lesson 10](tutorials/start/10-test-your-robot-code.md)).
4. **New simulator buttons:** a new library version can bring new ones. The Gradle sync after the update
   adds the ones you do not have yet (it never replaces yours).
5. **Install the app on the robot** (the green Run button, with the Control Hub connected).
6. **Test on the robot**, on blocks first: every button of TeleOp, then each autonomous at low power
   (PRACTICE profile), then at match power. Test the parts on your list from the CHANGELOG first.
7. **Commit and push.** Now the team has the new version. Tell them, with the version number.

## After a teammate updates it

`git pull` gets their commit, but your `ftc-helper-lib` folder still has the old files until you run this command.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
git submodule update
```

(GitHub Desktop does this by itself when it pulls.)

If `git status` shows `modified: ftc-helper-lib (new commits)` and you did not mean to change it, this
command also puts it back to the version the project expects.

## When not to update

- **The week before an event.** Use the version you tested. **Why:** a change you have not tested can
  break the robot when there is no time to fix it. Write the version in your event checklist.
- **During an event.** Do not update, even for a fix you read about. Wait until you are home, unless the
  robot cannot play without it.
- **In the middle of a big change of your own.** Finish and commit your work first, so you know which
  change caused a problem.

**The best time:** right after an event, or at the start of a practice week, with time to test.

## Use a specific version

Each release has a tag like `v0.16.0`. To use exactly that one (also to go back to an older one), type these lines.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

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

When the new version causes a problem and there is no time to fix it, go back. You lose nothing: the update
is only a commit.

**Not pushed yet:** switch the library back to the version you had (`git submodule status` before the
update said which).

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
cd ftc-helper-lib
git checkout v0.16.0
cd ..
git add ftc-helper-lib
git commit -m "Back to ftc-helper-lib v0.16.0"
```

**Already pushed:** undo the update commit with a revert (a new commit that does the opposite), then
put the library folder where the project now says.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](setup/09-the-terminal.md#which-folder))

```bash
git log --oneline
git revert --no-edit 3f2c1a9
git submodule update
git push
```

`3f2c1a9` stands for the id of the update commit in the `git log` list (press **q** to leave the
list). In GitHub Desktop: open the **History** tab, right-click the update commit, and click
**Revert Changes in Commit**. Then click **Push origin**.

Then tell the library's authors what broke ([an issue](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose)):
the version, what you did, and what you saw.

## A new FTC SDK version

The FTC SDK (the code from FIRST that every robot app is built on) changes every season (and
sometimes during one). We build the library against one SDK version (now **12.0.0**, the 2026-27
release, set in `library/build.gradle`). Your robot app always uses the SDK version in YOUR project's
`build.dependencies.gradle`. The library uses 12.0.0 only to compile, and brings no SDK of its own.

SDK 12.0 changed AprilTag detections (single tags and "clusters"). We wrote the library's `WebcamTags`
for the SDK 12 types, so its vision classes need SDK 12 or newer. Your own AprilTag code may need the
fix in the SDK's release notes.

So after you update the SDK:

1. Build. If it builds and works, you are done.
2. If the library does not build with the new SDK, check for a newer library version
   ([update](#update-the-command-line)).
3. If it is still broken, report it, or fix it: [Contributing](contributing.md).

## Change the library from inside the robot project

You can edit the library's files right in Android Studio. But those changes are inside the
submodule, so they need their own commit and push to the library repository.
Follow [Contributing](contributing.md). If you change it only in your robot project, the next
update loses the change.
