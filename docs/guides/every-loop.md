# What runs every loop

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** programmers who want to know exactly what the library does on the hub before they trust
it. **You need:** to know a `LinearOpMode` loop and what a bulk read is.

We read everything here from the source (`library/src/main/java`). Each part names its file, so you
can check it.

**No measured loop time yet.** We have not measured how long one EasyTeleOp or ControlMap loop takes
on a real Control Hub. Measure yours with [`LoopTimer`](#show-the-loop-time) and
[tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose) the number, with the parts on your robot.

## TeleOp: EasyTeleOp and ControlMap

`EasyTeleOp.runOpMode()` is `final`, so you cannot change this order. `ControlMap` (`Controls.java`) is an
EasyTeleOp that fills in the hooks.

### INIT (once)

1. `BulkReads.auto(hardwareMap)`: AUTO bulk reads on every hub ([below](#bulk-reads)).
2. The IMU (`createImu()`), the driver's heading, the drive motors (`DriverControls`), `configureDriving`.
3. The alliance from `MatchMemory`, the 2:00 `MatchTimer` with its buzzes, the tuning menu.
4. `createSubsystems()`. In a ControlMap this is `robot().setup(hardwareMap)`: it builds every module
   from `Tunables.java`. The INIT checks (a missing Lift `max`, a misspelled field...) stop here.
5. `bindButtons(...)`, then `buttonHelp()`.

Then the **INIT loop** runs until ▶ (start on the Driver Hub): gamepad 1 X/B choose the alliance, the warnings (PRACTICE
profile, tuned values), your `addInitTelemetry`, `t.update()`. **The INIT loop updates no part and gives
no motor power.**

### ▶ start (once)

First `timer.start()`. Then EasyTeleOp forgets the saved heading (the heading belongs to this run now). It ignores
buttons held through ▶ until the driver lets go. Then it calls `onStart()` (ControlMap: each module's start, like an intake
"on at the start", and the `atStart` actions).

### Each loop, in this order

| # | What | Notes |
| --- | --- | --- |
| 1 | `menuKey.update(gamepad1, gamepad2)` | GUIDE: the button map / tuning menu |
| 2 | `operator.update(...)` | gamepad 2 buttons (each job timed, [slow warning](#slow-code-warnings)) |
| 3 | `timer.update()` | match timer: buzzes and cues |
| 4 | `tuning.update()` | the tuning menu (reads gamepad 1 only while open) |
| 5 | menu open: `driving.hold()`. Menu closed: `driver.update(...)`, then `drive(gamepad1)` | driver buttons and driving pause while the menu uses gamepad 1 |
| 6 | `everyLoop()` | ControlMap: `plan.loop(...)` (gestures, double-taps, "buzz when" watches, each timed), then every stick job (`use(...)`) |
| 7 | `robot.update()` | every part, see [below](#robotupdate) |
| 8 | telemetry | the tuning menu, or the button map, or: slow-job warnings, time, drive, every part's lines, your `addTelemetry` (ControlMap: pause note, `show(...)` values); then `t.update()` |

`t.update()` sends every loop (`RobotTelemetry.setMinIntervalMs` is 0 unless you change it). The library
builds the lines every loop either way, so a part's telemetry reads still happen.

The IMU: in field-centric driving, `DriverControls` reads the heading once per loop. A bulk read does not
include the IMU. The debug line "Heading" reads it again, only while debug lines are on.

### robot.update()

`Subsystems.update()` updates each part in the order you added it, and times each one. In a
ControlMap (`RobotDefinition.setup`), the order is:

1. Your own `Tunables` sections (sections no part reads) get the tuning menu's values. It writes a field
   only when its value changed, and it writes no file.
2. Nothing else runs while the robot is paused (BACK).
3. **The odometry first of all** (with an `odometry(...)` line). It reads the device once and works
   out this loop's pose. Then every action, part and `driveTo` reads the same, fresh pose
   ([odometry](../modules/odometry.md#what-runs-every-loop)).
4. `ActionRunner` next: each running Action does its step for this loop. So an Action's command
   reaches a part before that part updates, in the same loop.
5. Cameras, then every other module, in the order `Robot.java` adds them.

### What a part does on the hub per loop

| Part | Reads | Writes |
| --- | --- | --- |
| Lift, Arm (`PositionMotor` / `SafeMotor`) | `SafeMotor.update()` reads the encoder and each switch **fresh**, every loop. Later reads in the same loop (the PID, telemetry) within 3 ms reuse those readings, so a reading is at most 3 ms old. | the power only when it changes, and again every 25 loops. **Past a soft limit** (below min or above max): it checks and sends the power **every loop** |
| Turret (`launcher.Turret`) | the encoder at most once per 3 ms | the power only when it changes |
| Drive (`MecanumDrive`, `TankDrive`) | none, except the IMU in field-centric | every wheel power, every loop |
| Intake in the five files (`robot.Intake`) | none | each motor's and CR servo's power only when it changes (no re-send) |
| Intake building block (`mechanism.SpinMotor`) | while running in with jam detection: the speed (`getVelocity()`, part of the bulk read) every loop. When the jam check uses current, also `getCurrent()` every loop: a **separate** hub command, outside the bulk read | the power only when it changes, and again every 25 writes (one write per `update()`) |
| Flywheel, launcher (`launcher.Flywheel`, used by `robot.Launcher`) | the speed (`getVelocity()`) every loop, and a follower motor's speed too | the speed (`setVelocity`) only when the target changes |
| Servos (`mechanism.PositionServo`, used by the claw, wrist and other servo parts) | none (a servo cannot report where it is) | the position only when it changes; during a slow move, a small step each loop |
| Odometry, Pinpoint (`odometry.PinpointOdometry`) | one I2C read per loop: the Pinpoint's position, speed and status together (the Pinpoint works out the pose itself, about 1500 times a second) | none (only when something sets the pose: at the start of autonomous, TeleOp's INIT, BACK held) |
| Odometry, OTOS (`odometry.OtosOdometry`) | two I2C reads per loop: position, then speed | none (only when something sets the pose) |
| Odometry, dead wheels (`odometry.DeadWheelOdometry`) | each pod's encoder (part of the bulk read). With two wheels, also the IMU's heading (a separate read) | none |
| Cameras (`vision.WebcamTags`, `vision.LimelightTags`) | the newest tags, from the camera's own process (webcam) or the Limelight's last result. No hub command | none |

Why the 3 ms: in a loop, `update()`, the PID and the telemetry all ask for the position. Each ask
used to be a trip to the hub. Now `update()` always reads fresh at the start of the loop. Later
asks within 3 ms reuse that reading, so they share one. **A reading is at most 3 ms old.** Code that
never calls `update()` gets a reading at most 3 ms old every time it asks. When more than 3 ms pass
between `update()` and the telemetry (slow parts in between), the telemetry reads again. Tests that
move a pretend motor with no time passing turn the reuse off with
`SafeMotor.freshReadingsForTests(true)`. Robot code never needs it.

Why the 25 loops: a power written straight to `getMotor()` skips the limits. `SafeMotor` sends its
own limited power again every 25 loops, even when it did not change. So a raw write lasts at most 25
loops (about half a second at 50 loops a second). Past a soft limit, `SafeMotor` sends every loop, so a raw
write can never keep pushing past the limit. A part that rests exactly at a limit, like a lift on its bottom
switch, is not "past", so it does not send every loop. `SpinMotor` sends again every 25 writes in the same way.

### Bulk reads

`BulkReads.auto(hardwareMap)` (EasyTeleOp, EasyAuto and the finders call it) puts every hub in the
SDK's AUTO bulk-caching mode. A bulk read gets many values from a hub in one message. In AUTO mode the first read gets all of a hub's bulk data (encoders,
digital inputs, analog inputs...) in one message. **A second read of the same value starts a
new bulk read.** That is why the parts read each encoder and each switch once per loop (above).

**For exactly one bulk read per hub per loop:** use `BulkReads.manual(hardwareMap)`, and call
`BulkReads.clear(hubs)` at the top of your own loop ([hardware](../modules/hardware.md#bulkreads)).
EasyTeleOp does not do this for you.

### Slow-code warnings

| Timed | Limit | Warning (top of the Driver Station) |
| --- | --- | --- |
| each part's `update()` (`Subsystems.SLOW_UPDATE_MS`) | 25 ms | `<part> update took 40 ms: update() must not wait or sleep` |
| each button job (`ControllerBindings.SLOW_ACTION_MS`) | 20 ms | `G2:A button job took 40 ms: the whole robot stopped that long. ...` (the gamepad and button first) |
| each gesture or "buzz when" job (ControlMap) | 20 ms | `a button job took 40 ms: the whole robot stopped that long. ...` |

Each warning goes away 5 s after the last slow update or job. Fixes: [troubleshooting](../troubleshooting.md#robot-behavior).

### Show the loop time

Every EasyTeleOp and `Controls.java` TeleOp shows it in the debug lines (hold the gamepad's START button, then press BACK):
`Loop: 12.3 ms (81 Hz)`. Lower is better, and under 20 ms is good. To show it all the time, or in your own
OpMode, use [`LoopTimer`](../modules/util.md#looptimer).

In an EasyTeleOp:

```java
private final LoopTimer loop = new LoopTimer();

@Override protected void everyLoop() { loop.update(); }

@Override protected void addTelemetry(RobotTelemetry t) {
    t.line("Loop", "%.1f ms (%.0f Hz)", loop.getAverageMs(), loop.getHz());
}
```

In a ControlMap, `everyLoop` and `addTelemetry` belong to the ControlMap itself. Use `show(...)` there:
the ControlMap reads its value once per loop, so the timer can update in it. It does not read the value while the tuning menu or the
button map is open.

```java
private final LoopTimer loop = new LoopTimer();

@Override protected void buttons(Robot robot, Pad g1, Pad g2) {
    show("Loop Hz", () -> { loop.update(); return Math.round(loop.getHz()); });
}
```

## Autonomous: EasyAuto and Auto

`EasyAuto.runOpMode()` is `final` too.

- **INIT:** `BulkReads.auto`, then the IMU (with no IMU, it stops with a message). When the drive is "not tuned", it shows
  "AUTONOMOUS IS OFF" and never moves. Then the mechanisms (`createSubsystems()`; Auto:
  `robot.setup(...)`), the INIT menu, and the INIT loop (menu, warnings, lines). It updates no part.
- **▶:** when the profile changed in INIT, EasyAuto makes the drive and the mechanisms again. Then the
  30 s timer starts, and `AutoRoutine.runAll()` runs each step. Steps block on purpose.
- **Each loop while a step drives or waits:** `AutoDrive` reads what the move needs (the four
  wheel encoders for a move, the IMU for a turn), sets the wheel powers, then calls the routine's
  `tick()`, then `idle()`.
  A wait (`waitFor`, `waitUntil`) calls `tick()` and `idle()`.
- **`driveTo`** (with odometry) reads the pose that the last `tick()` worked out. So the library reads the odometry
  once per loop, not twice.
- **`tick()`:** `robot.update()` (the same as TeleOp's, odometry first), `timer.update()`, the step's line, the
  time left, every part's telemetry, `t.update()`.
- **End:** when the steps are done, EasyAuto stops everything and the report goes to `MatchMemory`.
  After any end (done, STOP or an error), it stops everything again. Only when the drivers pressed ▶,
  the alliance and the end heading go to `MatchMemory` for TeleOp. With odometry, the whole
  end pose goes, and TeleOp's INIT starts the odometry there.

## When the library touches storage

Storage means files on the Control Hub (`/sdcard/FIRST/ftc-helper/`). This table lists every write.

| When | What | Waits? |
| --- | --- | --- |
| the first `Tunable` is made, or the first `Profile.current()` | reads `settings.properties` once per app run (`SettingsFile.load`) | yes, once, in INIT |
| tuning menu **A** (save) and **Y** (reset all) | `Tunable.saveAllInBackground` / `resetAllInBackground` write `settings.properties` | no: a background thread |
| the profile changes (`Profile.set`, the INIT menu's profile line) | writes `settings.properties` | no: a background thread |
| a finder ends (`FinderOpMode`) | `finder-results.txt` | yes, after the motors stop, outside the loop |
| each Characterizer test ends | a CSV in `characterizer/` | no: a background thread |
| you call `Tunable.saveAll()`, `resetAll()` or `SettingsFile.save()` | `settings.properties` | **yes** (do not call them in a loop) |

Nothing else writes a file. **`MatchMemory` and `PoseStore` live in memory** (see below). A settings write
goes to a `.tmp` file first, and then the library renames it. So a power cut never
leaves a half-written `settings.properties`. A failed write never stops the robot.

## Static state that lives across OpMode runs

All OpModes run in one robot app. A `static` field keeps its value from one OpMode run to the
next, until the app restarts. The library keeps these static fields:

| Static | Why | What it means for you |
| --- | --- | --- |
| the `Tunable` registry (`Tunable.ALL`) | the tuning menu finds tunables made anywhere | a value changed in the menu stays in the next OpMode run, even unsaved, until the app restarts. `Tunable.number(name, ...)` returns the existing one, and ignores the second default (`fromCode` takes the new code value when nobody tuned the old one) |
| `Profile.current` | read from the file once | when you change the file by hand, restart the app |
| `SettingsFile.shared` | one settings file for the app | loaded once |
| `MatchMemory` (alliance, report, placement) | autonomous hands them to TeleOp | ignored after 5 minutes (`MAX_AGE_SEC`). An autonomous writes the remembered alliance only after ▶. Every INIT loop sets the "chosen" alliance and the placement (the simulator reads them) |
| `PoseStore` | autonomous hands the end heading to TeleOp | EasyTeleOp forgets it at ▶, so a later TeleOp run does not use it |
| `ButtonPlan.buzzer` | a test hook: tests count buzzes instead of buzzing | package-private; robot code never changes it |
| `SafeMotor.READ_KEEP_NANOS`, `Turret.READ_KEEP_NANOS` | the 3 ms reuse time | package-private. Tests change `SafeMotor`'s with the public hook `SafeMotor.freshReadingsForTests(boolean)` (true = read every time); robot code never needs it |
| background threads (`SettingsFile`, finders) | one thread for file writes | made on first use |

## See also

- [Your own code at INIT, ▶, every loop and STOP](your-own-code.md): where your code goes in each
  step, and how to write the whole OpMode yourself with the library's robot.
- [Non-blocking code](non-blocking.md): what blocks and what never does.
- [Use only what you need](use-only-what-you-need.md): the building blocks in your own loop.
- [Architecture](architecture.md): the big picture.
