# Set up 5: update the hubs

**Set up, page 5 of 9.** Do it **once a season**, and again before an event if REV releases an update.

**Only using the simulator on this laptop?** Skip this page. The simulator needs no hubs. Go to [Every practice](08-every-practice.md).

**Why:** the Driver Station app and the Robot Controller app must have the same version: **12.0** for
the 2026-27 season. If not, the Driver Hub shows a version warning, or the two do not connect. Robot
inspectors check this at events.

## The Driver Hub (no laptop needed)

1. Charge the Driver Hub.
2. Connect it to a Wi-Fi network with internet.
3. Open the **Software Manager** app on it.
4. Tap **Update All**. Wait until it is done.

## The Control Hub

1. On the laptop, install the **REV Hardware Client 2**
   ([rhc2.revrobotics.com](https://rhc2.revrobotics.com/download/download.html)).
   It runs on Windows 10 or 11, macOS 15 or newer, and Linux.
2. Connect the Control Hub to the laptop with a USB cable. Turn the robot on.
3. In the REV Hardware Client, pick the hub.
4. Install the newest **operating system** and **firmware**.

REV's own steps: [Updating the Driver Hub](https://docs.revrobotics.com/duo-control/managing-the-control-system/updating-the-driver-hub).

The **Robot Controller app** is not updated here. The next page installs it from Android Studio,
because your code is part of it.

## Done when

The Software Manager says the Driver Hub is up to date, and the REV Hardware Client shows no updates
for the Control Hub.

**Next:** [Build and install](06-build-and-install.md).
