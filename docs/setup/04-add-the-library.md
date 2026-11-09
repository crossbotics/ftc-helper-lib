# Set up 4: add the library

**Set up, page 4 of 9.** Do it **once per robot project**, usually a mentor or the
code lead. After it is committed and pushed, everyone who clones the project gets the library.

**Skip this page** if there is already an `ftc-helper-lib` folder next to `TeamCode`, with files in it.

## How it works

The library goes inside your robot project as a **git submodule**: a project kept inside another
project. Your project remembers exactly which version of the library it uses.
**Why a submodule:** it works at events with no internet, you can read the library's code in Android
Studio, and the whole team gets the same version.

## You need

- The robot project open in Android Studio, synced ([page 3](03-github-and-the-project.md)).
- Git in Android Studio's Terminal tab: `git --version` prints `git version 2...` ([the Terminal](09-the-terminal.md)).

## Step 1: add the submodule

Type this line and press Enter.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](09-the-terminal.md#which-folder))

```bash
git submodule add https://github.com/crossbotics/ftc-helper-lib.git ftc-helper-lib
```

A new folder, `ftc-helper-lib`, is now next to `TeamCode`.

Want a fixed version instead of the newest? See [Use a specific version](../02-keep-up-to-date.md#use-a-specific-version)
after this page.

## Step 2: tell Gradle about the library

1. Open `settings.gradle`. It is in the top project folder, **not** in `TeamCode`.
2. At the bottom you see:

   ```groovy
   include ':FtcRobotController'
   include ':TeamCode'
   ```

3. Add these two lines under them:

   ```groovy
   include ':ftc-helper-lib'
   project(':ftc-helper-lib').projectDir = new File(rootDir, 'ftc-helper-lib/library')
   ```

**Why the second line:** the submodule also holds examples, docs and the simulator. The robot app needs
only the `library` folder.

## Step 3: let TeamCode use the library

1. Open `TeamCode/build.gradle`.
2. Find the `dependencies { ... }` block at the bottom.
3. Add one line inside it:

   ```groovy
   dependencies {
       implementation project(':FtcRobotController')
       implementation project(':ftc-helper-lib')      // <- add this line
   }
   ```

## Step 4: sync

Click **Sync Now** in the yellow bar at the top of the editor (or **File > Sync Project with Gradle
Files**). Wait until it finishes with no red errors.

## Step 5: check that it works

1. In the `teamcode` folder, make a new Java file named `HelperCheck`
   (right-click `teamcode` > **New > Java Class**).
2. Replace everything in it with this:

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

3. Build: **Build > Make Project** (or the hammer: [the buttons](06-build-and-install.md#the-buttons)).
   It builds? The library is in. **Why it proves it:** the `import com.crossbotics...` line only works
   when the library is there.
4. Delete `HelperCheck.java`.

## Step 6: commit and push

**Why:** teammates get the library only after you commit and push it.

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](09-the-terminal.md#which-folder))

```bash
git add .gitmodules ftc-helper-lib settings.gradle TeamCode/build.gradle
git commit -m "Add ftc-helper-lib"
git push
```

GitHub Desktop: tick those four, type the message, **Commit to main**, then **Push origin**.

## The simulator buttons (you do nothing)

At each Gradle sync, the library adds three entries to the run list ([the buttons](06-build-and-install.md#the-buttons)):

- **Simulator:** a pretend robot on your laptop, in your browser ([the simulator](../guides/simulator.md)).
- **Simulator tests:** runs your project's simulator tests.
- **Update library:** gets the newest library version ([Keep it up to date](../02-keep-up-to-date.md#update-the-update-library-button)).

Not in the list? **File > Close Project**, then open the project again.
Don't want them? Write `simulatorButtons=false` in your project's `gradle.properties`.

## Done when

The test build passed, `HelperCheck.java` is deleted, and the change is pushed.

**Next:** [Update the hubs](05-update-the-hubs.md).
