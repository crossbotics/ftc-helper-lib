# Driving

**The questions on this page:**

- [How do I set up a mecanum drive?](#how-do-i-set-up-a-mecanum-drive)
- [How do I set up a tank (standard) drive?](#how-do-i-set-up-a-tank-standard-drive)
- [A wheel turns the wrong way. How do I fix it?](#a-wheel-turns-the-wrong-way-how-do-i-fix-it)
- [How do I tell the robot how the Control Hub is mounted?](#how-do-i-tell-the-robot-how-the-control-hub-is-mounted)
- [How do I make the robot slower (or faster)?](#how-do-i-make-the-robot-slower-or-faster)
- [How do I let one driver do everything?](#how-do-i-let-one-driver-do-everything)
- [How do I put slow mode on a trigger?](#how-do-i-put-slow-mode-on-a-trigger)
- [Driving that is already built in](#driving-that-is-already-built-in)

## How do I set up a mecanum drive?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: drive-mecanum -->
```java title="Robot.java"
// The four motor names, in this order: front left, front right, back left, back right.
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)       // the motor (catalog id) and wheel size
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the names must match the configuration, and the wheel size makes autonomous distances right.
**More:** [Robot.java](../five-files/01-robot-java.md), [Drive](../reference/parts/drive.md).

## How do I set up a tank (standard) drive?

**Where:** `Robot.java`, inside `define()` (in place of the mecanum line).

<!-- recipe: drive-tank -->
```java title="Robot.java"
drive(Drive.tank("left_drive", "right_drive")
        .motor("gobilda-5203-312").wheelMm(96)
        .reversed("right_drive")                     // only the motors that run backward
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the motors on the two sides face opposite ways, so one side runs backward. List that side's
motors in `.reversed(...)`. With two motors per side, name all four: `Drive.tank("front_left", "front_right", "back_left", "back_right")`.
**More:** [Robot.java](../five-files/01-robot-java.md#your-motor-names), [Drive](../reference/parts/drive.md).

## A wheel turns the wrong way. How do I fix it?

**Where:** `Robot.java`, in the drive line, before `.imu(...)`.

<!-- recipe: drive-reversed -->
```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // List EVERY reversed drive motor. Without this line the left side is reversed.
        .reversed("front_right", "back_right")
        .imu("imu", LogoFacingDirection.UP, UsbFacingDirection.FORWARD));
```

**Why:** the motors on the two sides face opposite ways. So one side must run reversed, or the robot does not go straight.
**More:** [Wiring checks: check the motor directions](../tuning/02-wiring-checks.md#motor-direction-check-each-wheel-rolls-forward), [Troubleshooting](../troubleshooting.md#robot-behavior).

## How do I tell the robot how the Control Hub is mounted?

**Where:** `Robot.java`, the `.imu(...)` part of the drive line.

<!-- recipe: drive-imu -->
```java title="Robot.java"
drive(Drive.mecanum("front_left", "front_right", "back_left", "back_right")
        .motor("gobilda-5203-312").wheelMm(96)
        // Which way the hub's LOGO faces, then which way its USB PORTS face, on the robot.
        .imu("imu", LogoFacingDirection.LEFT, UsbFacingDirection.UP));
```

**Why:** the IMU (the hub's turn sensor) must know which way is up. If it does not, turns and field-centric driving go wrong.
**More:** [Robot.java: the IMU](../five-files/01-robot-java.md#how-your-control-hub-is-mounted).

## How do I make the robot slower (or faster)?

**Where:** `Tunables.java`, section `Drive`.

<!-- recipe: tunables-drive -->
```java title="Tunables.java"
public static class Drive {                                     // a fixed name: always Drive
    public static double maxPower = 0.6;                        // top wheel power: slow for first drives (1.0)
    public static double practiceMaxPower = DEFAULT;            // top wheel power in PRACTICE (same as maxPower)
    public static double slowSpeed = DEFAULT;                   // top power with the slow trigger held (0.3)
    public static double strafeCorrection = DEFAULT;            // sideways moves: Drive Tuner (1.15)
    public static double turnKp = DEFAULT;                      // auto turns: push per degree off (0.012)
    public static double turnKd = DEFAULT;                      // auto turns: brake near the end (0.003)
    public static double moveMinPower = DEFAULT;                // auto: least power that moves it (0.30)
    public static boolean calibrated = false;                   // true after the Drive Tuner: auto runs then
}
```

**Why:** `maxPower` is the top power in both profiles, so the drivers practice at match speed. Lower it
for the first drives, then set it back. To set a lower top power for PRACTICE only, use
`practiceMaxPower`, for example on a new driver's first day.
**More:** [Tunables.java: the numbers](../five-files/02-tunables-java.md#the-drives-numbers), [Profiles](../guides/profiles.md).

## How do I let one driver do everything?

**Where:** `Controls.java`, at the top of the class.

<!-- recipe: one-driver -->
```java title="Controls.java"
// true: every button is on gamepad 1 (the operator's buttons move there).
public static boolean oneDriver = true;
```

**Why:** with one driver, the operator's buttons move to gamepad 1, and the stick clicks switch slow mode and field-centric.
**More:** [How buttons work: one driver or two](../controls/01-how-buttons-work.md#one-gamepad-and-a-second-one-when-you-want-it).

## How do I put slow mode on a trigger?

**Where:** `Controls.java`, inside `buttons(...)`. This is for one driver. With two drivers, LT on gamepad 1 is already slow mode.

<!-- recipe: slow-trigger -->
```java title="Controls.java"
// One driver: slow on LT. The further it is pulled, the slower (down to slowSpeed).
g1.lt.slow().says("slow");
```

**Why:** a trigger gives fine control: a small squeeze makes the robot a little slower.
**More:** [ControlMap: one driver](../reference/controls.md#controlmap).

## Driving that is already built in

You write no code for these. With `Controls.java` (two drivers):

- **Drive:** the gamepad 1 left stick drives (and strafes), and right stick X turns. LT gives slow mode: the further you pull it, the slower the robot.
- **Field-centric** (mecanum): double-tap gamepad 1 **Y** to switch it on and off. Then "forward" is away from the drivers, whichever way the robot faces.
- **Pause:** tap gamepad 1 **BACK**, and everything stops on brake. Tap again to go on.
- **Reset "forward":** turn the robot to face away from you, then hold gamepad 1 **BACK** for 1 second.

**More:** [How buttons work: the driving you get for free](../controls/01-how-buttons-work.md#the-librarys-own-buttons), [the standard map](../reference/controls.md#controlmap).
