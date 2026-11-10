# Start from a starter bot

**Pick your start, page 1 of 4.** For a starter bot from goBILDA (tank or Strafer), REV, AndyMark or Studica.

The library has the whole code for each of these starter bots. It uses the same configuration names
as the seller's own code, so it runs on the bot as it is. You copy it, change one line, and build.

## 1. Find your bot

The folders are in the library, in
`ftc-helper-lib/examples/src/main/java/com/crossbotics/ftchelper/examples/`.

| Starter bot | Its folder | Its autonomous |
| --- | --- | --- |
| goBILDA FTC StarterBot (tank) | [`gobildabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildabot) | `autos/LaunchAndBackUp.java` |
| goBILDA FTC StarterBot with Mecanum Wheels (Strafer) | [`gobildastraferbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/gobildastraferbot) | `autos/LaunchAndLeave.java` |
| REV DUO FTC Starter Bot | [`revbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/revbot) | `autos/ShootAndLeave.java` |
| AndyMark Robits BIOBUZZ StarterBot | [`andymarkbot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/andymarkbot) | `autos/ThrowAndLeave.java` |
| Studica FTC Starter Bot | [`studicabot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/studicabot) | `autos/ShootAndLeave.java` |

goBILDA's folder also has `autos/AutoCookbook.java`: a menu of example steps. Copy it only if you want it.

## Try it first in the simulator

You can drive the bot's code before you copy it. The simulator is a pretend robot on your laptop.
It runs the library's copy of the code.

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](../setup/09-the-terminal.md#which-folder))

```bash
./gradlew :sim:run -Probot=sim/robots/starter-bot-rev.properties -PteamCode=none
```

Open http://localhost:8780 and pick **REV Bot TeleOp**. `-PteamCode=none` runs the library's copy of the code, not yours. For another bot, change `rev` in the command:
`gobilda`, `gobilda-strafer`, `andymark` or `studica`. The first time, do the
[laptop setup](../setup/09-the-terminal.md#laptop-setup-for-gradlew-commands-once-per-laptop).

## 2. Check the configuration names

The robot configuration must use the seller's names:
[the names for each starter bot](../guides/starter-bots.md#the-configuration-names).
You made the configuration in [Set up 7](../setup/07-robot-configuration.md). Check each name there.
**Why:** the code finds each motor and servo by its name. One wrong letter, and INIT stops.

## 3. Copy the files

Copy the bot's folder into `TeamCode`: [Copy example files into TeamCode](04-copy-into-teamcode.md).
Then come back here.

## 4. Check the IMU line

Open `Robot.java`. Find the line with `.imu(`. It says where the Control Hub's **logo** faces, then
where its **USB ports** face. Look at the hub on your robot. Is it mounted another way? Change the line
([How your Control Hub is mounted](../five-files/01-robot-java.md#how-your-control-hub-is-mounted)).
**Why:** the IMU (the sensor in the hub that knows which way the robot faces) must know how the hub sits.
Turns in autonomous use it.

Change nothing else yet.

## 5. Read your bot's notes

Some numbers in your bot's `Tunables.java` are guesses. Each bot's notes say which ones:
[REV](../guides/starter-bots.md#rev-bot-code), [goBILDA](../guides/starter-bots.md#gobilda-bot-code),
[goBILDA Strafer](../guides/starter-bots.md#gobilda-strafer-bot-code),
[AndyMark](../guides/starter-bots.md#andymark-bot-code), [Studica](../guides/starter-bots.md#studica-bot-code).
You measure them later, on the [Tunables.java page](../five-files/02-tunables-java.md).

Two kit mechanisms are not parts of the library. Their code uses the closest part and says so in
`Robot.java`:

- **AndyMark's catapult** runs as an `Indexer`: one press runs its motor for a set time. Tune that time
  so one press is one throw.
- **Studica's flywheel** has a 2:1 gear. The speed in `Tunables.java` is the motor's speed. The wheels
  turn at half of it. Tune it by watching shots.

## 6. Build

In Android Studio: **Build > Make Project**.

## You changed your starter bot

A part added, taken off or moved? Another motor or servo? Do steps 1 to 6 first, with the seller's
code as it is. Then make your changes **one at a time**: change, build, check.
**Why:** when something breaks, you know which change did it.
For each kind of change, [You changed your starter bot](../guides/starter-bots.md#you-changed-your-starter-bot)
says what to change in each file.

## Done when

- The bot's files are in `TeamCode`: `Robot.java`, `Tunables.java`, `Controls.java`, `Actions.java`,
  and the `autos` folder.
- **Build > Make Project** ends with no red errors.

## Problems

| Problem | Fix |
| --- | --- |
| A line is red: `cannot find symbol` | A `package` or `import` line still names the example folder. See [Copy example files, step 3](04-copy-into-teamcode.md#3-change-the-imports). |
| INIT stops: a name is not found | The configuration name is not the seller's name. INIT lists the real names. Fix the configuration ([Set up 7](../setup/07-robot-configuration.md)). |
| The TeleOp is not on the Driver Station | The `@Disabled` line is still in `Controls.java`. See [Copy example files, step 4](04-copy-into-teamcode.md#4-delete-the-disabled-lines). |

---

**Next:** [The five files](../five-files/README.md). They explain every line you just copied.

**Look it up:** [the starter bots: names, numbers and notes](../guides/starter-bots.md).
