# Add the library to a robot project

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

This page adds ftc-helper-lib to an FTC robot project in Android Studio. It takes about 10 minutes.
You do it **once per robot project**. After that, everyone who clones the project (copies it from
GitHub to their computer) gets the library too.

**Before this:** nothing. **After this:** the [Quick start](quick-start.md).

## How it works (the short version)

- Your robot project is a git repository (a folder whose history git keeps).
- We put ftc-helper-lib inside it as a **git submodule**: a repository inside a repository.
  Your project remembers exactly which version of the library it uses.
- Then we tell Gradle (the tool that builds the robot app) that `TeamCode` uses the library.
  `TeamCode` is the folder where your team's robot code lives.

Why a submodule? It works at events with no internet, you can read the library code right
in Android Studio, and everyone on the team gets the same version.

## What you need

- The robot project (the `FtcRobotController` folder) open in Android Studio, and it builds.
  For 2026-27 that is FTC SDK 12.0, which needs Android Studio Narwhal 3 Feature Drop or newer.
- Git. Android Studio's **Terminal** tab (bottom of the window) has it on most computers.
  Type `git --version` to check.

## Step 1: Add the submodule

Open the **Terminal** tab in Android Studio. It starts in the project folder (the one that has
`TeamCode` and `FtcRobotController` in it). Type this line and press Enter:

```bash
git submodule add https://github.com/crossbotics/ftc-helper-lib.git ftc-helper-lib
```

It downloads the library from GitHub. You now have a new folder, `ftc-helper-lib`, next to `TeamCode`.

> Want a fixed version instead of the newest? See
> [Use a specific version](02-keep-up-to-date.md#use-a-specific-version) after this page.

## Step 2: Tell Gradle about the library

Open `settings.gradle` (in the project folder, NOT inside TeamCode). At the bottom you see:

```groovy
include ':FtcRobotController'
include ':TeamCode'
```

Add these two lines under them:

```groovy
include ':ftc-helper-lib'
project(':ftc-helper-lib').projectDir = new File(rootDir, 'ftc-helper-lib/library')
```

The first line adds the library to the build. The second line says "its code is in the `library`
folder inside the submodule". **Why:** the submodule also holds examples, docs and the simulator,
and the robot app needs only the library part.

## Step 3: Let TeamCode use the library

Open `TeamCode/build.gradle`. Find the `dependencies { ... }` block at the bottom. Add one line inside it.
**Why:** step 2 told Gradle the library exists; this line says TeamCode may use it.

```groovy
dependencies {
    implementation project(':FtcRobotController')
    implementation project(':ftc-helper-lib')      // <- add this line
}
```

## Step 4: Sync

Click **Sync Now** in the yellow bar at the top of the editor
(or **File > Sync Project with Gradle Files**). Wait until it finishes with no red errors.
**Why:** Android Studio reads the Gradle files again and finds the library.

## Step 5: Check that it works

Make a new Java file in `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/` named
`HelperCheck.java`, and paste this into it. The `import com.crossbotics...` line uses a class from
the library, so the file only builds if the library is in:

```java
package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.crossbotics.ftchelper.util.Angles;

@TeleOp(name = "Helper Check")
public class HelperCheck extends LinearOpMode {
    @Override
    public void runOpMode() {
        telemetry.addData("Library works", Angles.shortestDiff(-170, 170) == 20);
        telemetry.update();
        waitForStart();
    }
}
```

Build (**Build > Make Project**, or the hammer icon). If it builds, the library is in.
You can run "Helper Check" on the robot: it shows `Library works : true`. Then delete the file.

## Step 6: Commit

The submodule is part of your project now, so commit it like any change. (A **commit** saves a
snapshot of your files in git's history.) **Why:** teammates get the library only after you commit
and push it.

```bash
git add .gitmodules ftc-helper-lib settings.gradle TeamCode/build.gradle
git commit -m "Add ftc-helper-lib"
```

## Step 7: The simulator buttons (once)

Two run buttons start the [simulator](guides/simulator.md) from Android Studio, with no Terminal:

- **Simulator**: the simulator with your TeamCode. When it is ready, the page (http://localhost:8780)
  opens in your browser. (No browser opened? The address in the Run window is a blue link: click it.)
- **Simulator tests**: every test in your project's `simulator/scenarios` folder ([lesson 10](tutorials/start/10-test-your-robot-code.md)).

**You do nothing to get them:** the library adds them at each Gradle sync (Android Studio syncs when it
opens the project; the elephant button at the top right, or **File > Sync Project with Gradle Files**,
syncs again). The first time on a laptop, the Build window says `Simulator button added to Android Studio`.
**Not in the list yet?** Close the project (**File > Close Project**) and open it again: Android Studio
reads its buttons when it opens a project. They are two small files in your project's
`.idea/runConfigurations` folder. That folder is not in git, so each laptop adds them at its first sync.
Don't want them? Write `simulatorButtons=false` in your project's `gradle.properties`.

**Use them:** in the list next to the green Run button (it shows `TeamCode`), pick **Simulator**, then
click Run (the green triangle). The red square **Stop** button stops the simulator. The Run window then
says `Build cancelled` in red: that is normal, nothing broke. To go back to the
robot app, pick `TeamCode` in the list again.

**Why the buttons need no setup:** they use Android Studio's own Java and Android SDK. You do not set
`JAVA_HOME`, and `ftc-helper-lib` needs no `local.properties`. (Typing `./gradlew` in the Terminal still
needs the laptop setup.)

## For everyone else on the team

When a teammate clones the robot project for the first time, they must get the submodule too:

```bash
git clone --recurse-submodules <robot project URL>
```

If they already cloned it without that, the `ftc-helper-lib` folder is empty. Fix it with:

```bash
git submodule update --init
```

GitHub Desktop (**File > Clone Repository…**) gets submodules by itself. Android Studio's **Git > Clone** does not always get submodules. If the build says
`Project directory '.../ftc-helper-lib/library' does not exist`, run the command above.

## Next

- [Quick start](quick-start.md): five files, the lines to change, and a driving robot in 30 minutes
- [Tutorials](tutorials/README.md): start with the main track, lesson 1: [the robot in five files](tutorials/start/01-robot-in-five-files.md)
  (`./gradlew :sim:newRobot` writes the five files from your robot configuration, and adds the simulator buttons)
- [Keep it up to date](02-keep-up-to-date.md)
- New to git? [Git and GitHub for FTC teams](guides/github.md): clone, commit, push, pull, with the
  command line or GitHub Desktop
