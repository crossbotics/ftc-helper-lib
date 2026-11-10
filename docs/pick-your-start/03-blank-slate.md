# Start from a blank slate

**Pick your start, page 3 of 4.** For your own robot design.

You start with the **smallest robot that works: it drives.** Then you add your parts one at a time,
on the five files pages. **Why:** when only one part is new, a problem can only be in that part.

There are two ways to get the first files. Pick one:

| Your robot | Use |
| --- | --- |
| It is wired, and its configuration is on the Driver Hub ([Set up 7](../setup/07-robot-configuration.md)) | [Way A: from your configuration](#way-a-from-your-configuration) (best) |
| It is not wired yet, or you only use the simulator, or you want to type each part in yourself | [Way B: copy the smallest files](#way-b-copy-the-smallest-files) |

## Way A: from your configuration

The library reads your configuration and writes the five files for you, with a line for each part.
**Why:** it copies the names for you, so they cannot have typos.

1. **Copy the configuration file off the Control Hub.** It is `/sdcard/FIRST/<name>.xml`, where
   `<name>` is your configuration's name (for `MyRobot`, it is `MyRobot.xml`). Use Android Studio's
   **Device Explorer** (**View > Tool Windows > Device Explorer**) with the hub connected, or the REV
   Hardware Client. Save it in the project folder, next to `TeamCode`.
2. **Run the file maker.** The first time, do the
   [laptop setup](../setup/09-the-terminal.md#laptop-setup-for-gradlew-commands-once-per-laptop).

   **Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](../setup/09-the-terminal.md#which-folder))

   ```bash
   ./gradlew :sim:newRobot -Pconfig=../MyRobot.xml -PteamCode=../TeamCode
   ```

   Change `MyRobot.xml` to your file's name. It writes `Robot.java`, `Tunables.java`, `Controls.java`,
   `Actions.java` and `autos/LeaveStart.java` into `TeamCode`.
   It never writes over your work: if one of the files is already there, it writes nothing and stops.
3. **Check each `TODO` line in `Robot.java`.** A `TODO` is a guess the file maker was not sure of, for
   example which part a motor drives.
4. **Build:** **Build > Make Project**.

In `Tunables.java`, each number is `DEFAULT` (the library's starting value) or `MEASURE` (a number
only your robot can give). Leave them for now. The [Tunables.java page](../five-files/02-tunables-java.md)
says how to find each one.

## Way B: copy the smallest files

The smallest files are in the library, in the
[`firstrobot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/firstrobot) folder:
`ftc-helper-lib/examples/src/main/java/com/crossbotics/ftchelper/examples/firstrobot/`.
They are a mecanum drive and nothing else. They are the same files the five files pages show.

1. **Copy the folder** into `TeamCode`: [Copy example files into TeamCode](04-copy-into-teamcode.md).
   Then come back here.
2. **Change the drive line** in `Robot.java`: your four motor names, your motor and wheels, and the
   IMU ([the drive line](../five-files/01-robot-java.md#the-drive-attribute-by-attribute),
   [how your Control Hub is mounted](../five-files/01-robot-java.md#how-your-control-hub-is-mounted)).
   A tank drive? See [A tank drive instead](../five-files/01-robot-java.md#a-tank-drive-instead).
3. **Build:** **Build > Make Project**.

Your other parts come later, one at a time, on the [Robot.java page](../five-files/01-robot-java.md#add-a-part).

## Done when

- The five files are in `TeamCode`: `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java`,
  and the `autos` folder.
- **Build > Make Project** ends with no red errors.

## Problems

| Problem | Fix |
| --- | --- |
| `... is already there: nothing written (move it away first)` | One of the five files is already in `TeamCode`. Move it to another folder, then run the command again. |
| `./gradlew: No such file or directory` | You are not in the `ftc-helper-lib` folder. Type `cd ftc-helper-lib`. |
| A line is red: `cannot find symbol` | A `package` or `import` line still names the example folder ([Copy example files, step 3](04-copy-into-teamcode.md#3-change-the-imports)). |

---

**Next:** [The five files](../five-files/README.md). They explain every line, and you make the robot drive.
