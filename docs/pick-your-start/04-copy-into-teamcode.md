# Copy example files into TeamCode

**Pick your start, page 4 of 4.** Pages 1 to 3 send you here. Do these steps, then go back.

The example files are in the library. Your robot's code must be in `TeamCode`. You copy the files,
then change a few lines so that they belong to `TeamCode`.

On this page, `<example>` is the name of the folder you copy, for example `gobildabot`. Put the real
name there, without the `< >`.

## 1. Copy the files

1. In Android Studio's **Project** panel, pick **Project** in the menu at the top of the panel (not
   **Android**). **Why:** the **Android** view does not show the library's examples.
2. Open `ftc-helper-lib/examples/src/main/java/com/crossbotics/ftchelper/examples/<example>/`.
3. Select every `.java` file in it. Copy them (**Ctrl+C**, Mac: **Cmd+C**).
4. Open `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`. Right-click `teamcode` and pick
   **Paste**. Android Studio may show a **Copy** box: press **OK**.
5. Right-click `teamcode`, pick **New > Package**. Type `autos` after the `teamcode.` that is already there.
6. Copy every file from the example's `autos` folder into your new `autos` folder the same way.

## 2. Change the package lines

The first line of each file is its `package` line. It must name the folder that the file is in now:

| File | Its first line |
| --- | --- |
| next to `Robot.java` | `package org.firstinspires.ftc.teamcode;` |
| in `autos` | `package org.firstinspires.ftc.teamcode.autos;` |

Before:

```java
package com.crossbotics.ftchelper.examples.gobildabot;
```

After:

```java
package org.firstinspires.ftc.teamcode;
```

Android Studio may have changed some of them for you when you pasted. Check every file.
**Why:** a file whose `package` line does not match its folder does not build.

## 3. Change the imports

The files in `autos` import `Robot`, and some import `Tunables` and `Actions`. Those imports still name
the example folder. Change each one to `org.firstinspires.ftc.teamcode`.

Before:

```java
import com.crossbotics.ftchelper.examples.gobildabot.Robot;
```

After:

```java
import org.firstinspires.ftc.teamcode.Robot;
```

Do the same in every file that has an `import com.crossbotics.ftchelper.examples` line.
Android Studio marks each line it cannot find in red.

## 4. Delete the @Disabled lines

Some example OpModes have this line above the class:

```java
@Disabled
```

and this import:

```java
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
```

Delete both, in every file that has them: `Controls.java`, each autonomous, and each finder.
**Why:** `@Disabled` hides an OpMode on the Driver Station. The examples use it, so they do not fill
the list. Your copies must show.

## 5. Change the OpMode names

In `Controls.java`, find `@TeleOp(name = "...")`. In each autonomous, find
`@Autonomous(name = "...", preselectTeleOp = "...")`. Change the names to your own.
`group = "Examples"`: change it to your own group, or delete it. Leave `group = "Finders"` on the
finder files: the docs look for the finders in that group.

**`preselectTeleOp`** must be the TeleOp's name, exactly. **Why:** after the autonomous, the Driver
Station picks that TeleOp for you.

## Done when

- No `package` or `import` line names `com.crossbotics.ftchelper.examples`.
- No `@Disabled` line is left.
- **Build > Make Project** ends with no red errors.

**Next:** go back to your start page: [a starter bot](01-starter-bot.md#4-check-the-imu-line),
[a common setup](02-common-setup.md#3-make-it-match-your-robot), or
[a blank slate](03-blank-slate.md#way-b-copy-the-smallest-files).
