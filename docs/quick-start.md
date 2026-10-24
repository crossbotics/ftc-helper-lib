# Quick start: pick your road, drive, then a first autonomous

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

This page takes your team from nothing to a robot that drives and runs its mechanisms. Then it takes
you to the simulator and a first autonomous. First pick your road (below). Each road says which
sections of this page to do, in order. **Why roads:** a starter bot's code is already written, so its
team skips the steps that write code. A team with its own robot does them.

**You need:** a laptop, a Control Hub and Driver Station, gamepad 1, the robot, and a block to put the
robot on (wheels off the ground). **Time:** about 30 minutes to drive. The numbers and the first
autonomous take one or two more sessions.

## Pick your road

| Your robot | Your road |
| --- | --- |
| A **starter bot** from goBILDA, REV (DUO), AndyMark (Robits) or Studica, built as the seller's guide says | [Road 1: an unchanged starter bot](#road-1-an-unchanged-starter-bot) |
| One of those starter bots **with your own changes**: a part added, taken off or moved, another motor or servo | [Road 2: a changed starter bot](#road-2-a-changed-starter-bot) |
| **Your own design**, or a kit robot that is not one of those four | [Road 3: your own robot](#road-3-your-own-robot) |

Not sure? Only new buttons, or new numbers? That is still road 1: buttons are in `Controls.java` and
numbers in `Tunables.java`. One part added, taken off or changed makes it road 2.

**Before every road:** never used Android Studio, or no robot project yet? Do
[Start from zero](start-from-zero.md) first (45 to 60 minutes). It installs Android Studio, gets the
robot project, and shows how to read and change one line of Java. Skip it if Android Studio already
builds and installs your robot project. Used Scratch or Blocks before? Read
[From Blocks to Java](blocks-to-java.md) too. Wrote Java with this library last season? See
[Coming from last season's code](from-last-season.md).

### Road 1: an unchanged starter bot

The library has the code for each of the four starter bots, with the seller's own configuration names.
You copy it, check it and measure a few numbers. You write almost no code.

1. [Add the library](#1-add-the-library).
2. [Make the robot configuration](#2-make-the-robot-configuration) with the seller's names:
   [the names for each starter bot](guides/starter-bots.md#the-configuration-names).
3. [Get the five files](#3-get-the-five-files): [way C](#c-copy-your-starter-bots-template), your
   starter bot's template.
4. [Change the STEP lines](#4-change-the-step-lines): only how your Control Hub is mounted (STEP 3).
   Then read the notes for your starter bot ([REV](guides/starter-bots.md#rev-bot-code),
   [goBILDA](guides/starter-bots.md#gobilda-bot-code), [AndyMark](guides/starter-bots.md#andymark-bot-code),
   [Studica](guides/starter-bots.md#studica-bot-code)): they say which numbers are guesses.
5. [Build and check the hardware](#5-build-and-check-the-hardware).
6. [Measure the numbers](#6-measure-the-numbers): the guessed ones first.
7. [Drive, then each mechanism](#7-drive-then-each-mechanism).
8. [The simulator](#8-the-simulator), with your starter bot's robot file.
9. [A first autonomous](#9-a-first-autonomous): your template's `autos/` file.

### Road 2: a changed starter bot

Start from the starter bot's code, so you keep everything that did not change. Then make your changes
one at a time. A worked example: team 8938's robot 1, goBILDA's starter bot with a fold-out intake and a
new launcher mount ([its pages](https://crossbotics.com/teams/8938-octobots/)).

1. [Add the library](#1-add-the-library).
2. [Get the five files](#3-get-the-five-files): [way C](#c-copy-your-starter-bots-template), the
   template of the starter bot you started from.
3. Make your changes: [You changed your starter bot](guides/starter-bots.md#you-changed-your-starter-bot).
   For each change it says what to change in the configuration, `Robot.java`, `Tunables.java`,
   `Controls.java` and the simulator's robot file.
4. [Make the robot configuration](#2-make-the-robot-configuration): the seller's names, plus a name for
   each part you added.
5. [Change the STEP lines](#4-change-the-step-lines): STEP 3 (the Control Hub), and STEP 4 if you
   changed a drive motor.
6. [Build and check the hardware](#5-build-and-check-the-hardware).
7. [Measure the numbers](#6-measure-the-numbers): every new part, and the starter bot's guessed numbers.
8. [Drive, then each mechanism](#7-drive-then-each-mechanism).
9. [The simulator](#8-the-simulator), then [a first autonomous](#9-a-first-autonomous).

### Road 3: your own robot

You make the five files for your robot, from its configuration or from a short template.

1. [Add the library](#1-add-the-library).
2. [Make the robot configuration](#2-make-the-robot-configuration). (Is the robot not wired yet? Do
   this after step 4, with the names you wrote in `Robot.java`.)
3. [Get the five files](#3-get-the-five-files): [way A](#a-from-your-robot-configuration-best-if-it-is-already-set-up)
   from the configuration (best), or [way B](#b-copy-the-template-no-configuration-yet-or-you-want-to-see-every-line),
   the template. Is your robot like one of the example robots (a turret, odometry, a flywheel)?
   [Pick your template](templates.md) has one to start from.
4. [Change the STEP lines](#4-change-the-step-lines): all of them.
5. [Build and check the hardware](#5-build-and-check-the-hardware).
6. [Measure the numbers](#6-measure-the-numbers): every `MEASURE` in `Tunables.java`.
7. [Drive, then each mechanism](#7-drive-then-each-mechanism).
8. [The simulator](#8-the-simulator), then [a first autonomous](#9-a-first-autonomous).
9. Then the [main track lessons](tutorials/README.md#start-here-the-main-track): lessons 1 to 6
   explain each file and add more mechanisms and actions.

Words used on this page:

- **Configuration:** the list of motors, servos and sensors you set up on the Driver Station, each
  with a name. Your code finds each part by that name.
- **INIT** and **START:** the two buttons on the Driver Station. INIT gets the program ready (nothing
  may move yet); START begins the match part.
- **TeleOp:** the program the drivers drive with. **Autonomous:** the program that runs on its own.
- **Field-centric:** stick up drives the robot away from the driver, whichever way the robot faces.
  (Robot-centric, the start: stick up drives the way the robot faces.)
- **Profile:** a set of limits for the whole robot. **PRACTICE** is gentle (for the shop),
  **COMPETITION** is full power (for matches).
- **Action:** a list of steps that one button starts, for example "push out, wait 0.5 s, stop"
  (`spitOut` in `Actions.java`). Lesson 5 shows how to write them.
- **Finder:** a small program that measures a number on the real robot (a servo's positions, a
  flywheel's gains, the wheel size) and shows the line to copy into your code.
- **`MEASURE`:** a word in `Tunables.java` in place of a number only your robot can give. INIT stops on
  it and names the finder that measures it.

## 1. Add the library

Follow [Add it to a project](01-add-to-project.md) (about 10 minutes). After it, your robot project has
an `ftc-helper-lib` folder next to `TeamCode`, and a test build passes.

- **Is there already an `ftc-helper-lib` folder next to `TeamCode`, with files in it?** A mentor added
  it. Skip this step.
- **Is the folder there but empty?** Your clone did not get it. In the Terminal
  ([how](start-from-zero.md#6-the-terminal-tab)), in the robot project's folder, run
  `git submodule update --init`. **Why:** the library is a **submodule** (a project kept inside
  another project), and a clone does not always fetch it
  ([more](01-add-to-project.md#for-everyone-else-on-the-team)).

## 2. Make the robot configuration

The **robot configuration** tells the Control Hub which device is in which port, and its name. Your
code finds each device by that name, so the names must be the same as in `Robot.java` (capitals and
`_` count).

1. Connect the Driver Station to the Control Hub. Open the **⋮** menu, then **Configure Robot**, then
   **New**.
2. Choose the Control Hub (and the Expansion Hub, if the robot has one).
3. For each port with a device: choose its kind (a motor's type, **Servo**, **Continuous Rotation
   Servo**, a sensor) and type its name. A motor type that is not in the list? Use the closest one:
   the library takes the motor's numbers from `.part(...)` in `Robot.java`.
4. Leave the **imu** line as it is: it is the Control Hub's own IMU.
5. **Save**, give the configuration a name (for example `MyRobot`), and **activate** it.

Where the names come from:

- **Road 1 and 2:** the seller's names, in [the table for your starter bot](guides/starter-bots.md#the-configuration-names).
  Road 2 adds a name for each new part.
- **Road 3:** your own names. Short, small letters, with `_` between words: `left_front`, `lift`,
  `claw`. **Why:** way A below copies them into `Robot.java`, so they become your code's names too.

**Why this comes first:** a wrong name stops the program at INIT, and the Driver Station then lists the
names that do exist. More: [hardware names](modules/hardware.md).

## 3. Get the five files

Pick **one** way:

- **Road 1 and 2** (a starter bot): [way C](#c-copy-your-starter-bots-template).
- **Road 3** with the configuration done: [way A](#a-from-your-robot-configuration-best-if-it-is-already-set-up).
- **Road 3** with no configuration yet, or to see every line: [way B](#b-copy-the-template-no-configuration-yet-or-you-want-to-see-every-line).

### A. From your robot configuration (best if it is already set up)

The library writes the five files for you, with each part already in them, from the configuration you
made on the Driver Station. **Why:** the names are copied for you, so they cannot have typos.

1. Copy the configuration file off the Control Hub: it is `/sdcard/FIRST/<name>.xml`. (**`<name>`**
   is a placeholder: put your configuration's name there, without the `< >`. For a configuration
   called `MyRobot` it is `MyRobot.xml`. **XML** is a kind of text file that stores a list in
   labelled `<tags>`; you do not edit it.) Use the REV Hardware Client, or Android Studio's **Device Explorer** (View > Tool
   Windows > Device Explorer) with the hub connected. Save it in your robot project, e.g.
   `MyRobot.xml` next to `TeamCode`.
2. From the `ftc-helper-lib` folder (it runs on the laptop: do the [laptop setup](#laptop-setup-once)
   first), run this **Gradle task** (one job that Gradle, the build tool, can run; `:sim:` says it is
   in the library's `sim` part):

   ```bash
   ./gradlew :sim:newRobot -Pconfig=../MyRobot.xml -PteamCode=../TeamCode
   ```

   It writes `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java` and `autos/LeaveStart.java`
   into TeamCode. It also adds the **Simulator** and **Simulator tests** run buttons to Android Studio
   (the first time, close the project and open it again to see them). It never overwrites your work: if any of the five files is already there, it
   writes nothing and stops with `... is already there: nothing written (move it away first)`. Every guess it is not sure of is a
   `TODO` line in `Robot.java` (a comment that marks something still to do): **check each TODO** like
   the STEP lines below. In `Tunables.java` every setting is there: `DEFAULT` (the library's
   default, worked out from the part where it can) or `MEASURE` (a number to find with a finder: INIT
   stops on it and names the finder). Find each `MEASURE` number (the finders run in the simulator too)
   and write it in its place ([the two words](guides/tunables.md#default-and-measure)).

### B. Copy the template (no configuration yet, or you want to see every line)

The template is in the library, in this folder (inside your `ftc-helper-lib` folder):
[`examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart/`](../examples/src/main/java/com/crossbotics/ftchelper/examples/quickstart).

1. Copy `Robot.java`, `Tunables.java`, `Controls.java` and `Actions.java` into
   `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
2. Make a folder `autos` there (right-click `teamcode`, **New > Package**, `autos`), and copy
   `autos/FirstAuto.java` into it.
3. In `Robot.java`, `Tunables.java`, `Controls.java` and `Actions.java`, change the **first line**.
   The second line of each file is a comment that says what to change it to.

   Before:

   ```java
   package com.crossbotics.ftchelper.examples.quickstart;
   // In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;
   ```

   After:

   ```java
   package org.firstinspires.ftc.teamcode;
   // In your TeamCode, change the line above to exactly:  package org.firstinspires.ftc.teamcode;
   ```

   (You may delete the comment line once it is done.) **Why:** the `package` line must match the
   folder the file is in, or it does not build.
4. In `autos/FirstAuto.java`, change the first line and the two imports of `Robot` and `Tunables`.

   Before:

   ```java
   package com.crossbotics.ftchelper.examples.quickstart.autos;
   ...
   import com.crossbotics.ftchelper.examples.quickstart.Robot;
   import com.crossbotics.ftchelper.examples.quickstart.Tunables;
   ```

   After:

   ```java
   package org.firstinspires.ftc.teamcode.autos;
   ...
   import org.firstinspires.ftc.teamcode.Robot;
   import org.firstinspires.ftc.teamcode.Tunables;
   ```

   **Why:** this file is in the `autos` folder, and it must find `Robot` and `Tunables` in the folder
   above it.
5. In `Controls.java` and `autos/FirstAuto.java`, delete these two lines:

   ```java
   @Disabled
   ```

   ```java
   import com.qualcomm.robotcore.eventloop.opmode.Disabled;
   ```

   **Why:** `@Disabled` hides the examples on a real robot, so the Driver Station would not list
   them. The `import` line is then not used: an import nothing uses is only clutter.

A simulator test runs this template on every library change, so it always builds and drives.

### C. Copy your starter bot's template

Each starter bot is a robot in five files in the library, with the seller's configuration names:

| Starter bot | Its folder (inside `ftc-helper-lib/examples/src/main/java/com/crossbotics/ftchelper/examples/`) | Its autonomous |
| --- | --- | --- |
| goBILDA FTC StarterBot | [`gobildabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot) | `autos/LaunchAndBackUp.java` |
| REV DUO FTC Starter Bot | [`revbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot) | `autos/ShootAndLeave.java` |
| AndyMark Robits BIOBUZZ StarterBot | [`andymarkbot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) | `autos/ThrowAndLeave.java` |
| Studica FTC Starter Bot | [`studicabot`](../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot) | `autos/ShootAndLeave.java` |

Copy your starter bot's files into TeamCode with the steps in
[Copy a template into TeamCode](templates.md#copy-a-template-into-teamcode) (the `package` lines, the
imports in `autos/`, and the `@Disabled` lines). Do its steps 1 to 5 now. Its step 6 (build and check)
is section 5 below. (goBILDA's `autos/AutoCookbook.java` is a menu of example steps: copy it only if you
want it.)

## 4. Change the STEP lines

Open each file and change only the lines marked `// STEP`, in order. **Road 1:** your starter bot's
files have no STEP marks. Change only the `.imu(...)` line (STEP 3 below) to how your Control Hub is
mounted, then go to section 5. **Road 2:** the same, plus STEP 4 and STEP 5 for what you changed.

Used Scratch or Blocks before? [From Blocks to Java](blocks-to-java.md) shows where each block you know
went (the loop, "when pressed", "wait") in these files.

**Java you will see in the files** (more in [lesson 1](tutorials/start/01-robot-in-five-files.md#java-you-will-meet)):

- `() -> r.intake.out()` is a **lambda**: a small piece of code you hand over now, to run later (when
  a button is pressed, or an action gets to that step).
- `r.intake::out` is a **method reference**: the short way to write the same lambda, "the intake's
  `out` method".
- `Drive.mecanum(...).motor(...).wheelMm(96)` is **method chaining**: each method hands the same thing
  back, so the next one adds to it with another dot. Read it left to right as one sentence.


1. **`Robot.java`:** `Drive.mecanum(...)` or `Drive.tank(...)`, and the motor names **exactly** as in the configuration, in the order front left, front right, back left, back right (a two-motor tank: left, right).
   **Why:** The library works out each wheel's power from its place; a typo stops at INIT with the list of real names.
2. **`Robot.java`:** `.motor("gobilda-5203-312")` and `.wheelMm(96)`: your drive motor ([its catalog id](guides/parts-catalog.md)) and wheel size.
   **Why:** Autonomous turns inches into encoder counts with them (an encoder counts how far the motor shaft turned).
3. **`Robot.java`:** `.imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD)`: which way the Control Hub's logo and USB ports face.
   **Why:** The IMU (a sensor in the hub) measures the heading, the way the robot faces; field-centric driving and autonomous turns use it.
4. **`Robot.java`:** only if a wheel turns the wrong way (you find out in section 6 below): add `.reversed(...)` listing **every** reversed drive motor, on its own line **before** `.imu(...)` (the STEP 4 comment marks the place).
   **Why:** The list replaces the default (the left side reversed); `.imu(...)` ends the drive line with `));`, so a `.reversed(...)` after it does not build.
5. **`Robot.java`:** your mechanisms: keep, delete or add `add(...)` lines (and a field for each). On each line, `.part("gobilda-5203-1150")`: your motor's or servo's id (on its label; optional but recommended). No intake? Also delete the `g2.dpadLeft` line in `Controls.java` and `spitOut` in `Actions.java`.
   **Why:** Each part brings its numbers and buttons; with `.part(...)` its starting values are that part's real numbers, and the simulator moves it like the real one.
6. **`Tunables.java`:** leave `maxPower` and `practiceMaxPower` at 0.4 for now.
   **Why:** A slow first drive is a safe first drive.
7. **`Controls.java`:** the TeleOp's name (renamed it? Change `preselectTeleOp` in `autos/FirstAuto.java` to the same name); `group = "Examples"` to your own group name (or delete it); `oneDriver = true` if one person drives and runs the mechanisms.
   **Why:** The name is what you pick on the Driver Station, and `preselectTeleOp` picks the TeleOp for you after the autonomous; one driver puts every button on gamepad 1.
8. **`autos/FirstAuto.java`:** the autonomous's name and `group`, where the drive team puts the robot, and how far it drives.
   **Why:** It only drives after the Drive Tuner ([lesson 6](tutorials/start/06-first-autonomous.md)).

**STEP 5, more mechanisms:** add a line for each part in `define()`, a field for each at the top of
the class, an import for each part class, and its numbers in `Tunables.java` (a section named after the
part: `"lift"` reads `Tunables.Lift`). For example a lift and a claw
([lesson 4](tutorials/start/04-lift-and-claw.md) explains them):

```java
public Lift lift;     // the fields, next to "public Intake intake;"
public Claw claw;

lift = add(Lift.slide("lift").part("gobilda-5203-435").bottomSwitch("lift_bottom")); // in define() (1)!
claw = add(Claw.servo("claw").part("gobilda-2000-torque"));
```

1. `.part(...)` says which motor or servo it is, by the id on its label (these ids are examples). The
   starting values and the simulator then use that part's real numbers.

In `Controls.java` and `autos/FirstAuto.java`, also delete the `@Disabled` line and its
`import com.qualcomm.robotcore.eventloop.opmode.Disabled;` line (section 3, way B), if you have not yet.

## 5. Build and check the hardware

1. Build (the green hammer in Android Studio), then Run to install it on the Control Hub.
   **Why:** the robot runs the app that is on the hub, not the code on your laptop.
2. Add the small **Hardware Check** OpMode from [Check the hardware (lesson 2b)](tutorials/19-testing-procedures.md#1-the-3-line-opmode)
   (it is not one of the five files), build and install again.
3. **Robot on a block, wheels off the ground.** Run the Hardware Check. Every device must be found.
   Each motor must turn, and its encoder must count up at positive power
   ([how to use it](tutorials/19-testing-procedures.md#2-use-it)).
   **Why:** it tests each part alone, before your code moves them together. A part that is not found,
   or that turns the wrong way, is a wire, a port or a name to fix now.

## 6. Measure the numbers

Some numbers only your robot can give: where a servo stops, how far a lift may go, a flywheel's gains.
Guessing them can push a part into the frame. **So the finders come before the first drive.**

- **Road 3:** every `MEASURE` in `Tunables.java`. Your TeleOp and autos stop at INIT on each one and
  name the finder to use. The finders still run: they do not read `Tunables.java`.
- **Road 1 and 2:** the starter bot's files have no `MEASURE`, but some numbers are the seller's or our
  guesses. Your starter bot's notes list them ([REV](guides/starter-bots.md#rev-bot-code),
  [goBILDA](guides/starter-bots.md#gobilda-bot-code), [AndyMark](guides/starter-bots.md#andymark-bot-code),
  [Studica](guides/starter-bots.md#studica-bot-code)). Measure every servo position on that list
  before the first TeleOp (REV's poker, Studica's flap). Road 2: also every part you added.

How:

1. Add the finders your robot needs, each in its own small file next to `Robot.java`: the
   **Servo Finder** and the **Motor Finder** (the whole files are in
   [lesson 4, section 5](tutorials/start/04-lift-and-claw.md#5-find-the-real-numbers-with-the-finders)),
   and for a flywheel the **Flywheel Finder** (the same 3 lines, with `FlywheelFinderOpMode`).
   Build and install.
2. Robot on a block. Run them in this order ([why this order](guides/finders.md#the-order)):
   - **Servo Finder** for each servo: its safe ends and named positions (`open`, `closed`, `up`...).
   - **Motor Finder** for each lift, arm or turret: `min`, `max` and the named positions.
   - **Flywheel Finder** for each flywheel: `kP` to `kF`. Eye protection on, nobody in front of it.
3. Each finder shows lines such as `Tunables.Lift: min = 0, max = 2580`. Write each number in that
   section of `Tunables.java`, in place of `MEASURE` or the guess
   ([how to read a finder's lines](guides/tunables.md#how-to-read-a-finders-lines)).
4. Build and install again. INIT no longer stops: every `MEASURE` has its number.

## 7. Drive, then each mechanism

1. **Start slow.** In `Tunables.java`, section `Drive`, set `maxPower` and `practiceMaxPower` to
   `0.4`. (The `quickstart` template has 0.4 already. The starter bots have `DEFAULT`, which is full
   power.) Build and install. **Why:** a slow first drive is a safe first drive.
2. **Pick the PRACTICE profile** (the gentle limits): INIT any autonomous, choose **Profile** with the
   D-pad (the last line), choose PRACTICE, then STOP. Do not START it. The hub remembers the profile.
   ([Profiles](guides/profiles.md).)
3. **Robot on a block, wheels off the ground.** INIT your TeleOp, START. **Why:** a wrong motor
   direction can make the robot spin or drive off the table.
4. Left stick up: every wheel must roll the way that moves the robot forward. A wheel rolls backward?
   Fix STEP 4 ([how to check each wheel](tutorials/start/02-drive.md#7-check-the-motor-directions)).
5. On the floor: left stick drives (and strafes on mecanum), right stick turns, LT slows down.
6. Tap BACK: everything stops (PAUSED); tap again to go on. Tap GUIDE to see every button.
7. **Each mechanism, one at a time**, on the floor: tap GUIDE for its buttons, then try each one.
   A flywheel: eye protection, nobody in front of it. Something moves the wrong way? Add or take away
   `.reversed()` on its line in `Robot.java`.

It drives? Raise `maxPower` and `practiceMaxPower` in `Tunables.java` (1.0 and 0.6 are common, or write
`DEFAULT` again). `practiceMaxPower` is used in the PRACTICE profile (the gentle mode for the shop),
`maxPower` in COMPETITION. A hub that was never set uses COMPETITION.

**Changed a number live?** The tuning menu (hold GUIDE 1 s after START, in the PRACTICE profile) changes a
number on the hub only. **Copy each new value back into `Tunables.java`**: INIT lists the values that
differ. If you do not, the next build loses it. ([The tuning menu](guides/tunables.md#the-live-column-the-tuning-menu).)

## 8. The simulator

The simulator runs your code on a laptop with a 3D field. You can use it any time after section 3, also
with no robot nearby: try buttons, actions and autonomous routes there first.

**In Android Studio:** pick **Simulator** in the list next to the green Run button, and click Run.
The page opens in your browser when the simulator is ready. (No browser opened? The address in the Run
window is a blue link: click it.)
(No **Simulator** in the list? Close the project and open it again: [Step 7 of Add the library](01-add-to-project.md#step-7-the-simulator-buttons-once).)
The red **Stop** button stops it.

**Or in the Terminal** (do the laptop setup below once), in the `ftc-helper-lib` folder (`cd ftc-helper-lib`):

```bash
./gradlew :sim:run
```

Then open http://localhost:8780, pick your TeleOp, INIT, START, set gamepad 1's list to "Keyboard", and drive with the keyboard
(W A S D, arrows). The simulator builds the robot from your code, so it cannot find a wrong motor direction: only
the real robot can. ([Simulator guide](guides/simulator.md).)

The simulator builds your TeamCode first, so it is also a quick check that your code builds, on a
laptop, with no Control Hub. (`run` is a Gradle **task**: one job Gradle can run. `:sim:` says it is in
the library's `sim` part.)

**What `simulator/robot.properties` is:** each time the simulator starts, it writes this file in your
robot project (next to `TeamCode`) from `Robot.java`, and prints `Robot file made from Robot.java:
simulator/robot.properties`. It is the simulator's description of your robot: its size, its motors and
servos, and how they look. The names and the drive come from `Robot.java`; the sizes are guesses, marked `TODO`.

- **Commit it** with your code. **Why:** everyone on the team then gets the same simulator robot.
- **When you measure the robot,** edit the `TODO` lines with the real numbers. The numbers you write stay.
- **Why it is safe to make it again at each start:** it refreshes only its own lines (marked
  `# from Robot.java`) and keeps the lines you wrote. (`./gradlew :sim:fromRobot` does the same step alone;
  `-PnoFromRobot` starts the simulator with the file as it is.)
- **Changed your code?** Stop the simulator and start it again. **Why:** it builds your code when it
  starts. To stop it in the Terminal: click in the Terminal and press **Ctrl+C**.
- **Road 1 and 2: use your starter bot's measured robot file.** Before the first start, copy
  `ftc-helper-lib/sim/robots/starter-bot-gobilda.properties` (or `-rev`, `-andymark`, `-studica`) to
  `simulator/robot.properties` in your robot project. **Why:** it has the starter bot's real size and
  shapes; a file made from `Robot.java` guesses an 18 x 18 in robot. A copied file has no "made from"
  line at the top, so the simulator does not change it when it starts. **Road 2:** after each change,
  run `./gradlew :sim:fromRobot`: it adds the lines for your new parts to the file
  ([a robot file from Robot.java](guides/simulator.md#a-robot-file-from-robotjava)). It stops and says
  why if a line in the file and your code disagree: then fix that line by hand.
- **`MEASURE` numbers:** the simulator uses a stand-in for each one, so it runs before you measure. The
  real robot still stops at INIT ([MEASURE in the simulator](guides/simulator.md#measure-in-the-simulator)).
- **Simulator tests**, the second button, runs your saved tests. That comes later:
  [lesson 10](tutorials/start/10-test-your-robot-code.md).

### Laptop setup (once)

Only for the Terminal commands (the Simulator button needs none of this). The library is its own
Gradle build, separate from your robot project's. So it needs these, even when the robot project already builds:

- **Java.** The commands need `JAVA_HOME` (where Java is; Java 17 or newer). Android Studio has its
  own Java, usually in its `jbr` folder. Set it in the terminal (it lasts until you close that
  terminal):

  ```bash
  # Mac
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  # Windows (PowerShell)
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```

  **That folder may not exist on your computer** (Android Studio installed somewhere else, or a
  different name). To find a Java folder:

  - In Android Studio: **Settings > Build, Execution, Deployment > Build Tools > Gradle**. The
    **Gradle JDK** line names the Java that Android Studio uses, with its folder. Use that folder.
  - On a Mac, in the Terminal: `/usr/libexec/java_home -V` lists the Javas installed for the whole
    Mac, with their folders. (Android Studio's own Java may not be in that list.)
  - **Neither one finds a Java?** Install a **JDK** 17 or 21 (a JDK is Java with the tools that build
    code), for example Eclipse Temurin from [adoptium.net](https://adoptium.net), with the normal
    settings. Then use its folder (on a Mac, `/usr/libexec/java_home -V` now lists it). You do
    **not** need the Java from java.com.

- **The Android SDK.** If the first command stops with `SDK location not found`, make a plain text file
  `ftc-helper-lib/local.properties` ([how to make a file](start-from-zero.md#make-a-new-file-or-folder):
  right-click the `ftc-helper-lib` folder, **New > File**) with one line (put your user name in place of `<you>`; on a Mac,
  `whoami` in the Terminal prints it, on Windows `$env:USERNAME` in PowerShell):

  ```text
  sdk.dir=/Users/<you>/Library/Android/sdk
  ```

  On Windows: `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. **Why:** your robot project's own
  `local.properties` does not count; the library is its own build. (Android Studio shows the SDK path in
  **Settings > Languages & Frameworks > Android SDK**.)
- **Windows:** type `gradlew.bat` (or `.\gradlew` in PowerShell) instead of `./gradlew`.
- **Normal warnings you can ignore:** `source value 8 is obsolete` (and `target value 8`),
  `Java compiler version 21 has deprecated support for compiling with source/target version 8` (it
  goes on with `Try one of the following options: 1. [Recommended] Use Java toolchain...`: do
  nothing), `SDK processing. This version only understands SDK XML versions up to 3`,
  `Deprecated Gradle features were used`, `[Incubating] Problems report`. The first run downloads a lot
  and takes a few minutes. How to read the rest of the output: [Start from zero: the Terminal
  tab](start-from-zero.md#6-the-terminal-tab).

## 9. A first autonomous

An autonomous drives by counting wheel turns. So the library has a safety rule: **no autonomous runs on
the robot until you say the drive is tuned.** Until then, INIT shows
`AUTONOMOUS IS OFF: the drive is not tuned yet`.

1. **Try it in the simulator first.** Start the Simulator, pick your autonomous, INIT, START. It runs
   there before the drive is tuned, with the line `Drive not tuned: runs in the simulator only`.
   Your autonomous is in `autos/`: `LeaveStart.java` (way A), `FirstAuto.java` (way B), or your starter
   bot's file (way C, [where it starts](guides/starter-bots.md)).
2. **The Drive Tuner.** Add `DriveTuner.java` next to `Robot.java` (the whole file is in
   [lesson 6, section 1](tutorials/start/06-first-autonomous.md#1-tune-the-drive-first)). Put 48 in of
   tape on the floor. Run its Push test, then its Drive test until the robot drives 48 in (and its
   Strafe test on a mecanum drive).
3. **Copy its lines:** `.wheelMm(...)` into the drive line of `Robot.java`, `strafeCorrection` into
   `Tunables.Drive`. Then set `Tunables.Drive.calibrated = true`. Build and install.
4. **Where the robot starts:** INIT shows where to put it (`Place robot : ...`). Change the start and
   the distances in `Tunables.Auto`, not in the steps
   ([route numbers](tutorials/start/06-first-autonomous.md#3-route-numbers-live-in-tunablesauto)).
5. **The first real run:** the PRACTICE profile, space around the robot, and one person with a hand on
   STOP. Read the report on the Driver Station at the end. The autonomous then picks your TeleOp for
   you (`preselectTeleOp`).

Then [lesson 6](tutorials/start/06-first-autonomous.md) explains each line of the autonomous, and how
to add steps.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| The build cannot find `com.crossbotics.ftchelper` | step 1 is not finished: [Add it to a project](01-add-to-project.md) |
| A `package` error | the `package` line of a copied file still says `com.crossbotics.ftchelper.examples...` |
| `class, interface, or enum expected` | the file has two `package` lines, or its first line is broken (a typo, a missing `;`): it must be exactly one `package ...;` line at the top |
| `cannot find symbol` ... `Robot` or `Tunables` in `FirstAuto.java` | its two imports still say `com.crossbotics.ftchelper.examples.quickstart`: section 3, way B, step 4 |
| The TeleOp is not on the Driver Station | the `@Disabled` line is still there, or the app was not reinstalled |
| INIT stops with a list of names | a name in `Robot.java` does not match the configuration (capitals count) |
| It spins when it should drive straight | a wheel direction is wrong: STEP 4 |
| INIT stops on `MEASURE` and names a finder | that number is not measured yet: [section 6](#6-measure-the-numbers) |
| The autonomous says `AUTONOMOUS IS OFF: the drive is not tuned yet` | the Drive Tuner, then `calibrated = true`: [section 9](#9-a-first-autonomous) |
| A number you changed in the tuning menu is gone after a build | it was not copied back into `Tunables.java` ([section 7](#7-drive-then-each-mechanism)) |
| Nothing moves | it is paused (tap BACK) |
| After the autonomous, the Driver Station does not pick your TeleOp | `preselectTeleOp` in `autos/FirstAuto.java` must be the TeleOp's exact name (STEP 7) |
| `./gradlew` says `JAVA_HOME is not set`, or `SDK location not found` | the [laptop setup](#laptop-setup-once) |
| `newRobot` says `is already there: nothing written` | one of the five files is already in TeamCode: move it away (or delete it), then run the command again |

## Next

[Lesson 1: the robot in five files](tutorials/start/01-robot-in-five-files.md) explains what each of the five
files is for. Then lessons 2 to 6 of the [main track](tutorials/README.md#start-here-the-main-track)
add the drive in detail, buttons, mechanisms, actions and the autonomous. After that, do only the
lessons your robot needs (7 odometry, 8 paths, 9 a shooter). A quick question
("how do I add a claw?")? The [cookbook](cookbook.md) has short answers with code to copy.

Road 1 and 2: your starter bot's page says what its code does differently, and what the simulator
cannot show yet: [The four BIOBUZZ starter bots](guides/starter-bots.md).
