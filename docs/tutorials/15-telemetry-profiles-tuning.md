# Tutorial 15: Telemetry, profiles and tuning

**You will learn:** telemetry with a driver level and a debug level, the practice/competition
profile, changing numbers live with the tuning menu, and the match timer.

**You need:** a motor `lift` (tutorial 08). Tutorial 14.

**Full program:** [`Tut15TelemetryTuning.java`](../../examples/src/main/java/io/github/jasoncross/ftchelper/examples/tutorials/Tut15TelemetryTuning.java)
**Reference:** [telemetry.md](../modules/telemetry.md), [core.md: Profile](../modules/core.md#profile), [tuning.md](../modules/tuning.md), [match.md: MatchTimer](../modules/match.md#matchtimer)

---

## 1. Two kinds of telemetry reader

The **driver** glances at the screen for half a second during a match: they need modes, targets,
"READY". The **programmer** at the bench needs powers, errors, raw sensor values. Showing both all
the time buries what the driver needs. `RobotTelemetry` keeps them apart:

```java
RobotTelemetry t = new RobotTelemetry(telemetry);
t.line("Time", timer.format());            // standard: always
t.debug("Loop", "%.0f Hz", loopHz);        // debug: only when debug is on
t.warn("Final 20 seconds!");               // always, at the TOP
t.update();                                // once per loop
```

Debug starts ON in the PRACTICE profile, OFF in COMPETITION, and BACK toggles it here
(`.bindPress(Pad.G1, Btn.BACK, t::toggleDebug)`). Every mechanism helper's `addTelemetry` already
splits its lines the same way.

## 2. Profiles

```java
InitMenu menu = new InitMenu(gamepad1).profile();     // a "Profile" line in INIT
...
lift.maxPower(Math.min(LIFT_MAX_POWER.get(), Profile.pick(0.5, 1.0)));
```

`Profile.pick(practice, competition)` gives one value or the other. The choice is saved on the
Control Hub. Use it for comfort (speeds, extra screens), never to turn safety off.
Guide: [Profiles](../guides/profiles.md).

## 3. Live tuning

```java
static final Tunable LIFT_MAX_POWER = Tunable.number("Lift/maxPower", 0.8, 0.05).range(0.1, 1.0);
...
PositionMotor lift = ... .tunable("Lift");     // its PID gains and positions too
TuningMenu tuning = new TuningMenu(gamepad1);
ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
ControllerBindings driver = new ControllerBindings().bindPress(Pad.G1, Btn.BACK, t::toggleDebug);
ControllerBindings operator = new ControllerBindings()
        .bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
        .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));
...
menuKey.update(gamepad1, gamepad2);
operator.update(gamepad1, gamepad2);
tuning.update();
if (!tuning.isOpen()) driver.update(gamepad1, gamepad2);   // gamepad 1 belongs to the menu while it is open
```

**Why three button lists?** The menu uses gamepad 1's D-pad, A, X and Y. If the driver's gamepad-1
buttons kept working, pressing Y to "reset all" would also run whatever Y does for the robot. The
lift buttons are on gamepad 2, so a second person can test each change while you tune.

Press GUIDE (the center button) in PRACTICE: a menu lists every tunable. D-pad up/down chooses,
right/left changes, A saves on the hub. Read the value **every loop** (`LIFT_MAX_POWER.get()`), so
a change acts at once. When a number is right, copy it into the code and press Y (reset all).
Guide: [Tuning while the robot runs](../guides/tuning.md).

## 4. The match timer

```java
MatchTimer timer = MatchTimer.teleOp().rumbleAt(20, gamepad1);
...
timer.start();          // right after START
timer.update();         // every loop: runs the cues
t.line("Time", timer.format());
```

2026-27: the field signals 1:00 (FLOWER ownership unlocks) and 0:20 (final 20 seconds). A buzz at
those times helps drivers who cannot look up.

## 5. The INIT warning

```java
if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
```

Saved tuning values come back after a restart. This warning makes sure nobody forgets them at an
event. `EasyAuto` shows it for you.

## Run it

1. INIT: set Profile to PRACTICE with the D-pad. START.
2. Debug lines (Loop, Profile, lift power) are visible. BACK hides them.
3. GUIDE: the tuning menu. Change `Lift/maxPower` to 0.3: gamepad 2 A/Y now move the lift slowly.
   Gamepad 1 A saves.
4. Stop and run again: the INIT screen warns "1 tuned value(s) differ from the code".
5. Open the menu, Y: back to the code values; the warning is gone next time.
6. Set Profile to COMPETITION: GUIDE does nothing, debug starts off.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Menu does not open | Profile is COMPETITION (on purpose) |
| A change has no effect | the code reads the value once in INIT; read it every loop |
| Driving while tuning | check `tuning.isOpen()` and ignore gamepad1's sticks while open |

## Try this

1. Make the D-pad slow-mode speed a `Tunable` and find the speed drivers like best.
2. Use `setMinIntervalMs(100)` and compare the loop speed (debug "Loop") with and without.
3. Add a 1:00 cue that shows a warning line for 3 seconds.

## Next

[Tutorial 16: Easy autonomous](16-easy-autos.md)
