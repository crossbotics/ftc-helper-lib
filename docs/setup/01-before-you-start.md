# Set up 1: before you start

**Set up, page 1 of 9.** No Java needed.

## What you need

- A laptop (Mac or Windows) with internet.
- A **Control Hub** and a **Driver Hub** (or an Android phone with the Driver Station app).
- A USB-C cable for the Control Hub.
- A gamepad.
- A free **GitHub account**, for each student who changes code. GitHub's rules say you must be 13 or older.

**Only using the simulator?** You need only the laptop and a GitHub account. The simulator is a pretend
robot on the laptop.

## How the pieces connect

![The laptop runs Android Studio and installs the app on the Control Hub by USB cable or Wi-Fi. The Control Hub runs the Robot Controller app with your code; motors, servos and sensors plug into it. The Driver Hub runs the Driver Station and talks to the Control Hub by Wi-Fi; the gamepads plug into it. The simulator is a pretend robot on the laptop and needs no hubs.](../images/setup-hubs.svg)

## The words

These words are on every page of these docs.

| Word | Means |
| --- | --- |
| **Control Hub** | The robot's computer (made by REV). Motors, servos and sensors plug into it. |
| **Driver Hub** | The device the drive team holds. It runs the **Driver Station** app. The gamepads plug into it. |
| **Robot Controller app** | The app on the Control Hub. Your code is part of it. |
| **Robot project** | The folder of code that becomes the Robot Controller app. It is called `FtcRobotController`. |
| **TeamCode** | The folder in the robot project where your team's code goes. |
| **Robot configuration** | The list of motors, servos and sensors on the robot, each with a name. Your code finds each part by its name. |
| **OpMode** | One program you pick on the Driver Hub. |
| **TeleOp** | An OpMode that people drive with gamepads. |
| **Autonomous** | An OpMode that runs on its own at the start of a match. |
| **INIT, ▶, STOP** | The Driver Hub's buttons. INIT gets ready (nothing may move). ▶ starts. STOP ends. |
| **Android Studio** | The program on the laptop where you write and build the code. |
| **Git, GitHub** | Git keeps every saved version of the code. GitHub keeps a copy online, so the team can share it. |
| **The library** | ftc-helper-lib: ready-made code for drives, lifts, buttons and autonomous steps. |

## Done when

You have the hardware above, and each student who codes has a GitHub account.

**Next:** [Install Android Studio](02-android-studio.md).
