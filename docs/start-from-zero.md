# Start from zero: the tools, the robot project and your first edit

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

This page is for you if you have never built an FTC robot program in Java. It needs no Java. At the end
you have Android Studio, the robot project, a first program on the robot, and one safe edit. Then the
[Quick start](quick-start.md) is the next page (used Blocks before? [From Blocks to Java](blocks-to-java.md)
first).

**You need:** a laptop (Mac or Windows) with internet, a Control Hub, a Driver Station (see below), and
a USB cable for the Control Hub. **Time:** 45 to 60 minutes, most of it waiting for downloads.

**Done already?** If Android Studio opens your team's robot project and it builds, go to the
[Quick start](quick-start.md).

## 1. The pieces and their names

These words are on every page of these docs.

- **Control Hub:** the robot's computer (made by REV). The motors, servos and sensors plug into it.
  It runs the **Robot Controller app**, and your code is part of that app.
- **Driver Station:** the app the drive team uses to pick a program and start it. The gamepads plug
  into the device that runs it. A **Driver Hub** is the REV device that runs the Driver Station app
  (an Android phone can run it too). These docs say "Driver Station".
- **Robot configuration:** the list of motors, servos and sensors on the robot, each with a name, for
  example `front_left` or `intake`. You make it on the Driver Station, and it is saved on the Control
  Hub. **Why it matters:** your code finds each part by its name, so the names in the code must be
  exactly the same (capitals count).
- **OpMode:** one program you pick on the Driver Station. A robot project has many OpModes: one to
  drive, one or more autonomous ones, some test ones.
- **TeleOp** and **Autonomous:** the two kinds of OpMode. A TeleOp is driven by people with gamepads.
  An Autonomous runs on its own, with no gamepads, at the start of the match.
- **INIT**, **START** and **STOP:** the buttons on the Driver Station. INIT gets the OpMode ready
  (nothing may move yet). START begins it. STOP ends it.

## 2. Install Android Studio and get the robot project

**Android Studio** is the program you write and build the robot code in.

1. Download Android Studio from [developer.android.com/studio](https://developer.android.com/studio)
   and install it with the normal settings.
   **Which version:** FTC SDK 12.0 (the 2026-27 robot software) needs **Android Studio Narwhal 3
   Feature Drop or newer**. The newest version from that page is fine.
2. Open Android Studio once. Let it finish its first setup (it downloads the **Android SDK**, the
   tools that make Android apps). Accept the normal choices.
3. Get the **robot project**: the folder of code that becomes the Robot Controller app. It is called
   `FtcRobotController`. Pick **one** way:
   - **A. Your team already has one on GitHub (the usual way).** Ask your mentor or code lead for its
     address. In Android Studio: **File > New > Project from Version Control**. Paste the address.
     Pick a folder on your laptop. Click **Clone**. ("Clone" means copy the project, with its
     history, from GitHub to your laptop.)
     **Why:** everyone on the team then works on the same code.
     **The library may already be in it:** if a mentor added it, there is an `ftc-helper-lib` folder
     next to `TeamCode`. If that folder is **empty**, Android Studio did not get it: in the Terminal
     ([section 6](#6-the-terminal-tab)), type `git submodule update --init` and press Enter. **Why:**
     the library is a **submodule** (a project kept inside another project), and a clone does not
     always fetch it ([Add it to a project](01-add-to-project.md#for-everyone-else-on-the-team)).
   - **B. No team project yet.** Download FTC's project from
     [github.com/FIRST-Tech-Challenge/FtcRobotController](https://github.com/FIRST-Tech-Challenge/FtcRobotController).
     Use the **SDK 12.0** version: open **Releases** on that page, pick **v12.0**, and download the
     source code `.zip`. Unzip it. In Android Studio: **File > Open**, and pick the unzipped folder.
     **Why 12.0:** it is the version this library is built and tested with.
     **Then add the library** to it, after section 3: [Add it to a project](01-add-to-project.md)
     (about 10 minutes). The [Quick start](quick-start.md#1-add-the-library) sends you there too.
4. Open the project folder: the top folder, the one that has `TeamCode` and `FtcRobotController`
   inside it. **Why:** Android Studio needs the whole project, not only the `TeamCode` folder.
5. **Wait for the first Gradle sync.** **Gradle** is the tool that builds the app. The first time, it
   downloads a lot. A progress bar is at the bottom right. It can take 5 to 20 minutes. It is done
   when the bar is gone and the **Build** tab (bottom of the window) says the sync finished.
   **Why wait:** until the sync is done, Android Studio shows many false red errors.
6. **Find TeamCode.** On the left is the **Project** panel. At its top is a list: pick **Project**
   (not "Android"). Then open these folders, one in another:

   ```text
   TeamCode/src/main/java/org/firstinspires/ftc/teamcode/
   ```

   This is where **your team's code** goes. Every file you add in these docs goes in this folder (or
   in a folder inside it). (In the "Android" view the same folder shows as
   `TeamCode > java > org.firstinspires.ftc.teamcode`.)

## 3. First build and install

**Once a season, first: update the hub software.** The Driver Hub (its Driver Station app and its
Android system) and the Control Hub (its operating system and firmware) must be up to date.
**Why:** the Driver Station app and the Robot Controller app must have the same version (the first
two numbers: **12.0** for the 2026-27 season). If not, the Driver Station shows a version warning,
or the two do not connect. Robot inspectors check this at events too.

- **Driver Hub, no laptop needed:** connect the Driver Hub to a Wi-Fi network with internet. Open
  the **Software Manager** app on it and tap **Update All**. Keep it charged; it takes some minutes.
- **Control Hub (and the Driver Hub too, if you like):** the **REV Hardware Client 2** on the laptop
  (Windows 10 or 11, macOS 15 or newer, or Linux; download it from
  [rhc2.revrobotics.com](https://rhc2.revrobotics.com/download/download.html)). Connect the hub with a
  USB cable, pick it, and install the newest **operating system** and **firmware**.
  REV's steps: [Updating the Driver Hub](https://docs.revrobotics.com/duo-control/managing-the-control-system/updating-the-driver-hub).
- **The Robot Controller app** you do not update this way: step 4 below installs it from Android
  Studio, built from the robot project (SDK 12.0). **Why:** your code is part of that app.

1. **Build:** click the green **hammer** at the top of the window (**Build > Make Project**).
   Wait for the **Build** tab to say it finished, with no errors.
   **Why:** this checks that the project builds on your laptop before you change anything.
2. **Connect to the Control Hub.** Turn on the robot. Then pick **one**:
   - **USB (the easiest):** a USB cable from the laptop to the Control Hub's USB-C port.
   - **Wi-Fi:** join the laptop to the Control Hub's Wi-Fi network (the one the Driver Station uses;
     ask your mentor for its name and password). Then, in the Terminal ([section 6](#6-the-terminal-tab)), type
     `adb connect 192.168.43.1:5555`. If it says `adb` is not found, use USB.
3. **Pick the hub.** At the top of the window is a list of devices. After a few seconds it shows the
   Control Hub (its name starts with `REV Robotics`). Pick it.
4. **Install:** click the green **Run** triangle next to it. The run list next to it must say
   `TeamCode`. It builds again, then installs the Robot Controller app on the Control Hub.
   **Why:** the robot runs the app that is on the hub, not the code on your laptop. After each change
   you click Run again.
5. **Check on the Driver Station.** It connects to the robot and shows the OpMode lists (the
   TeleOp list and the Autonomous list).
   - Your team's project already has OpModes? Ask your code lead which one moves nothing (a test
     OpMode, for example), then press INIT, START, then STOP. Not sure? Skip this: in section 5 you
     install **Concept: NullOp**, a program that moves nothing.
   - FTC's new project shows **empty** lists: its sample OpModes are hidden. You run your first one in
     [section 5](#5-copy-a-file-into-teamcode-and-change-one-line) below.

## 4. Reading Java without writing it

You do not need to write Java yet. You need to read it well enough to change one line safely. Here is
a small file, the FTC sample `ConceptNullOp.java`, shortened:

```java
package org.firstinspires.ftc.robotcontroller.external.samples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

@TeleOp(name = "Concept: NullOp", group = "Concept")
@Disabled
public class ConceptNullOp extends OpMode {

  @Override
  public void init() {
    telemetry.addData("Status", "Initialized");   // a line on the Driver Station
  }
  ...
}
```

What each part is:

- **A file** holds one **class** (one piece of a program). The file name and the class name are the
  same: `ConceptNullOp.java` holds `class ConceptNullOp`.
- **The `package` line** (the first line) says which folder the file is in, with dots in place of
  slashes. It **must match the folder**, or the file does not build.
- **`import` lines** name code from other places that this file uses (here, from the FTC SDK).
- **`@TeleOp(...)`** and **`@Disabled`** are notes for the Driver Station. `@TeleOp(name = "...")`
  is the name in the TeleOp list. `@Disabled` hides the OpMode from the list.
- **`{` and `}`** (curly brackets) hold a group of lines together. Every `{` has its own `}` further
  down. The class has one pair; each part inside it has its own pair.
- **`;`** (semicolon) ends a statement (one command), like a full stop ends a sentence.
- **`//`** starts a **comment**: words for people, from `//` to the end of the line. Java ignores them.
- **`"text in quotes"`** is text, used as it is (a name on the Driver Station, a motor name).
  Words **without** quotes are **names** in the code (`telemetry`, `init`). Do not add or remove
  quotes.
- **Capitals count.** `Intake`, `intake` and `INTAKE` are three different names to Java.

**Red text is an error.** A name underlined in red, or a red line in the **Build** tab, is an error.
Read only the **first** error: the later ones often come from the first. It looks like this:

```text
Robot.java:12: error: ';' expected
```

That says: the file (`Robot.java`), the line number (12), and the problem (a `;` is missing). Click it
to go to the line. Fix it, then build again. **Why the first:** one missing `;` or `}` can make
twenty more errors below it, and they all go away with the first fix.

### Java words

These words are in the code and in these docs. One line each; you meet them again in the lessons.

- **variable:** a name that holds a value, like a box with a label. `maxPower` holds `0.4`.
- **`double`:** a kind of variable that holds a number with a decimal point (`0.4`, `96`).
  **`int`** holds a whole number (`3`).
- **`boolean`:** a kind of variable that holds only `true` or `false` (yes or no).
- **`true` / `false`:** the two answers to a yes/no question, for example "is the button pressed?".
- **method:** a named list of commands, like a custom block. `init()` and `buttons(...)` are methods.
  The `( )` after the name hold its inputs.
- **`void`:** written before a method's name when the method hands nothing back.
- **`return`:** hands a result back from a method, and ends the method.
- **object:** one thing the program made from a class, with its own values: your robot, its intake.
  `robot.intake.in()` asks the intake object to run its `in` method.
- **`public`:** other files may use this name. (`private`: only this file may.)
- **`static`:** belongs to the class itself, not to one object, so there is only one. In
  `Tunables.java`, `public static double maxPower` is one number the whole program shares.

## 5. Copy a file into TeamCode and change one line

Now run your own first OpMode: a copy of the FTC sample above. It needs no motors.

1. In the **Project** panel, find the sample:
   `FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptNullOp.java`.
2. Click it, and copy it (**Edit > Copy**). Click the `teamcode` folder (section 2, step 6), and paste it
   (**Edit > Paste**). If Android Studio asks, click **OK** (keep the name).
   **Why copy:** never change FTC's own files. Your team's code lives only in TeamCode.
3. Open the copy (in `teamcode`). Change **exactly one line**, the first one:

   Before:

   ```java
   package org.firstinspires.ftc.robotcontroller.external.samples;
   ```

   After:

   ```java
   package org.firstinspires.ftc.teamcode;
   ```

   **Why:** the file is now in the `teamcode` folder, and the `package` line must match its folder.
   (Android Studio may change it for you when you paste. Check it anyway.)
4. Delete the whole line `@Disabled`. **Why:** with it, the Driver Station hides the OpMode.
5. Build (hammer), then Run (triangle) to install it.
6. On the Driver Station, pick **Concept: NullOp** in the TeleOp list. INIT: it shows
   `Status: Initialized`. START: it shows the run time. STOP.

Common mistakes:

- **Two `package` lines,** or a first line with a typo: the build says
  `class, interface, or enum expected`. The file must have exactly one `package` line, at the top.
- **A deleted `;` or `}`:** the build says `';' expected` or `reached end of file while parsing`.
  Press **Edit > Undo** until the file is as it was.

### Make a new file or folder

Later pages say "make a file" or "make a folder". This is how, in the **Project** panel (the
**Project** view from section 2, step 6). Each kind is made a different way:

1. **A new Java file** (a class, for example `HardwareCheck.java`): right-click the `teamcode` folder,
   **New > Java Class**. Type the name **without** `.java` (`HardwareCheck`), pick **Class**, and
   press Enter. Android Studio writes the `package` line for you. Then replace everything in the new
   file with the whole file the page shows (select all with **Edit > Select All**, then paste), and
   check that the first line is still `package org.firstinspires.ftc.teamcode;`.
   **Why:** the file name must be the class name, and the `package` line must match the folder.
2. **A Java folder** (a folder of code, for example `autos`): right-click the `teamcode` folder,
   **New > Package**, and type `autos` (after the `teamcode.` that is already there). A file in it
   starts with `package org.firstinspires.ftc.teamcode.autos;`. **Why "Package":** in Java, a folder
   of code is called a package.
3. **A plain text file** (not Java: for example `local.properties` or a scenario `my-test.sim`):
   right-click the folder it goes in, **New > File**, and type the **full** name with its ending
   (`local.properties`, `my-test.sim`). Do not add `.txt`. If Android Studio asks what kind of file
   it is ("Register New File Type Association"), pick **Text**.
   **Why the full name:** the ending tells the tools what the file is; `my-test.sim.txt` is not found.
4. **A plain folder** (not Java: for example `simulator/scenarios` next to `TeamCode`): right-click
   the top project folder (the one with `TeamCode` in it), **New > Directory**, and type
   `simulator/scenarios`. The `/` makes a folder inside a folder.

The Quick start puts the autonomous programs in a Java folder `autos` inside `teamcode` (kind 2).

## 6. The Terminal tab

Some steps in these docs are commands you type. You type them in Android Studio's **Terminal**.

1. Open it: the **Terminal** tab at the bottom of the window (or **View > Tool Windows > Terminal**).
2. **Where it starts:** in the project folder, the one with `TeamCode` and `FtcRobotController` in it.
   The text before the cursor (the **prompt**) shows the folder's name.
3. **`cd`** ("change directory") goes into a folder: `cd ftc-helper-lib`. `cd ..` goes back up one
   folder. The library's commands (the simulator, the file maker) run **inside** `ftc-helper-lib`.
   **No `ftc-helper-lib` folder yet?** It is there only after the library was added to the project
   ([Add it to a project](01-add-to-project.md), the Quick start's step 1). With FTC's new project
   (way B), it is not there until you do that step.
4. **Run one command:** type it (or paste it), press **Enter**, and wait for the prompt to come back.
   Run one line at a time. A `git` command says `not recognized` (Windows)? Git is not on the command
   line yet, even with GitHub Desktop: [git is not recognized](troubleshooting.md#autonomous-choice-and-git-problems).
5. **Gradle commands print a lot** (`./gradlew ...`). The first one downloads for a few minutes. Look
   at the **last** lines: `BUILD SUCCESSFUL` is good; `BUILD FAILED` comes with the first problem just
   above `* What went wrong:`. Warnings are not errors. These are normal and safe to ignore:
   `source value 8 is obsolete`, `Java compiler version 21 has deprecated support for compiling with
   source/target version 8`, `SDK processing. This version only understands SDK XML versions up to 3`,
   `Deprecated Gradle features were used`, `[Incubating] Problems report`.
6. **Stop a running command** with **Ctrl+C** (on a Mac too). Some commands keep running on purpose:
   the simulator (`./gradlew :sim:run`) is a small server that runs until you stop it. (Started with
   the **Simulator** button in Android Studio? Its red Stop button stops it.)
7. **Windows:** type `.\gradlew` (PowerShell) or `gradlew.bat` instead of `./gradlew`.

The library's commands also need a one-time **laptop setup** (where Java and the Android SDK are):
[Quick start: Laptop setup](quick-start.md#laptop-setup-once).

## 7. Git in two sentences

A **commit** saves a snapshot of your changes on your laptop, with a short note about what changed
(**Git > Commit** in Android Studio), and a **push** sends your commits to the team's GitHub so
everyone gets them (**Git > Push**). Commit after each thing that works; ask your code lead before you
push to the team's **main branch** (the team's shared copy of the code, the one everyone builds from).
More, step by step for the command line and GitHub Desktop: [Git and GitHub for FTC teams](guides/github.md).

**The library itself:** a mentor or code lead usually adds it to the robot project once, for the
whole team ([Add it to a project](01-add-to-project.md), about 10 minutes). After that, everyone who
clones the project gets it.

## Next

**No `ftc-helper-lib` folder next to `TeamCode` yet?** Add the library first:
[Add it to a project](01-add-to-project.md). Folder there but empty? Type `git submodule update --init`
in the Terminal. Then:

1. Used Scratch or Blocks before? Read [From Blocks to Java](blocks-to-java.md) next, **before** the
   Quick start: it shows where each block you know went.
2. [Quick start](quick-start.md): [pick your road](quick-start.md#pick-your-road) (an unchanged
   starter bot, a changed starter bot, or your own robot), get the five robot files, and drive.
3. [Lesson 1: the robot in five files](tutorials/start/01-robot-in-five-files.md), then lessons 2 to 6 of the
   [main track](tutorials/README.md#start-here-the-main-track), then the lessons your robot needs (7 to 9).
4. Not sure which robot code to start from? [Pick your template](templates.md): a kit bot, an example
   robot, or code made from your robot configuration.
