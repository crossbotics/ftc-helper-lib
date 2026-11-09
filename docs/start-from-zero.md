# Read Java and make your first edit

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** anyone new to Java. **Before this:** [Set up](setup/README.md), pages 1 to 7. **Time:** 20 minutes.
At the end you can read a Java file, change one line safely, and make new files in TeamCode.

## Reading Java without writing it

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

## Copy a file into TeamCode and change one line

Now run your own first OpMode: a copy of the FTC sample above. It needs no motors.

1. In the **Project** panel, find the sample:
   `FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptNullOp.java`.
2. Click it, and copy it (**Edit > Copy**). Click the `teamcode` folder ([Set up 3](setup/03-github-and-the-project.md#3-open-the-project-in-android-studio)), and paste it
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
   `Status: Initialized`. ▶: it shows the run time. STOP.

Common mistakes:

- **Two `package` lines,** or a first line with a typo: the build says
  `class, interface, or enum expected`. The file must have exactly one `package` line, at the top.
- **A deleted `;` or `}`:** the build says `';' expected` or `reached end of file while parsing`.
  Press **Edit > Undo** until the file is as it was.

### Make a new file or folder

Later pages say "make a file" or "make a folder". This is how, in the **Project** panel (the
**Project** view, [Set up 3](setup/03-github-and-the-project.md#3-open-the-project-in-android-studio)). Each kind is made a different way:

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

## Next

1. Used Scratch or Blocks before? Read [From Blocks to Java](blocks-to-java.md): it shows where each block you know went.
2. [Quick start](quick-start.md): [pick your road](quick-start.md#pick-your-road), get the five robot files, and drive.
