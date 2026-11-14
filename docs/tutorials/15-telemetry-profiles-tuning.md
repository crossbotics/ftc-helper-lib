# Tutorial 15: Telemetry, profiles and tuning

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**In the five files:** the screen, the profile, the tuning menu and the match buzz are ready with no code
([What the drivers see and feel](../controls/03-see-and-feel.md), [Live tuning](../tuning/16-live-tuning.md)).
**Build it by hand** to see how each one works inside, or to add them to a program without the five files.

**You will build:** a lift program with a hideable debug screen, a practice/competition switch, a tuning
menu and a match timer.

**You need:** a motor `lift` (tutorial 08). Do Tutorial 14 first.

**Full program:** [`Tut15TelemetryTuning.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials/Tut15TelemetryTuning.java)

---

## 1. Two kinds of telemetry reader

In a match, the **driver** glances at the screen and needs modes, targets, "READY". The **programmer** at
the bench needs powers, errors, raw sensor values. When the screen shows both, the driver's lines get
lost. `RobotTelemetry` keeps them apart:

```java
RobotTelemetry t = new RobotTelemetry(telemetry);
t.line("Time", timer.format());            // standard: always
t.debug("Loop", "%.0f Hz", loopHz);        // debug: only when debug is on
t.warn("Final 20 seconds!");               // always, at the TOP
t.update();                                // once per loop
```

Debug starts ON in the PRACTICE profile and OFF in COMPETITION. Here, BACK toggles it
(`.bindPress(Pad.G1, Btn.BACK, t::toggleDebug)`). Every mechanism helper's `addTelemetry` already splits
its lines the same way.

## 2. Profiles

A **profile** is a setting the whole robot reads: PRACTICE (at the bench) or COMPETITION (at an event).

```java
InitMenu menu = new InitMenu(gamepad1).profile();     // a "Profile" line in INIT
...
lift.maxPower(Math.min(LIFT_MAX_POWER.get(), Profile.pick(0.5, 1.0)));
```

`Profile.pick(practice, competition)` gives one value or the other. `Math.min` takes the smaller of the
two, so the lift's top power is never more than the profile allows, whatever the tuned value is. Use
the profile for comfort (speeds, extra screens), never to turn safety off
([Profiles](../guides/profiles.md)).

## 3. Live tuning

A `Tunable` is a number you can change from the gamepad while the program runs, without rebuilding.

```java
static final Tunable LIFT_MAX_POWER = Tunable.number("Lift/maxPower", 0.8, 0.05).range(0.1, 1.0); // (1)!
...
PositionMotor lift = ... .tunable("Lift");     // its PID gains and positions too
TuningMenu tuning = new TuningMenu(gamepad1);   // will not open in COMPETITION (2)!
ControllerBindings menuKey = new ControllerBindings().bindPress(Pad.G1, Btn.GUIDE, tuning::toggle);
ControllerBindings driver = new ControllerBindings().bindPress(Pad.G1, Btn.BACK, t::toggleDebug);
ControllerBindings operator = new ControllerBindings()
        .bindPress(Pad.G2, Btn.A, () -> lift.goTo("DOWN"))
        .bindPress(Pad.G2, Btn.Y, () -> lift.goTo("HIGH"));
...
menuKey.update(gamepad1, gamepad2);
operator.update(gamepad1, gamepad2);
tuning.update();
if (!tuning.isOpen()) driver.update(gamepad1, gamepad2);   // gamepad 1 belongs to the menu while it is open (3)!
```

1. "Lift/maxPower" is its name in the menu (group "Lift"). 0.8 is the code value, and 0.05 is one D-pad
   step. `range(0.1, 1.0)` stops anyone from tuning the lift to full reverse by mistake.
2. The menu stays closed in COMPETITION, so a driver cannot change numbers by accident at an event.
3. When the menu closes, the real program calls `driver.ignoreHeldButtons(...)`, so a button still held
   from the menu does not fire.

**Why three button lists:** the menu uses gamepad 1's D-pad, bumpers, A, B, X and Y. If the driver's
gamepad-1 buttons kept working, Y for "reset all" would also run whatever Y does for the robot. The lift
buttons are on gamepad 2, so a second person can test each change while you tune.

Read the value **every loop** (`LIFT_MAX_POWER.get()`), so that a change acts at once. The menu's buttons,
and how to copy a number back into the code: [Live tuning](../tuning/16-live-tuning.md).

## 4. The match timer

```java
MatchTimer timer = MatchTimer.teleOp().rumbleAt(20, gamepad1);
...
timer.start();          // right after ▶ (start)
timer.update();         // every loop: runs the cues
t.line("Time", timer.format());
```

Drivers often cannot look up, so a buzz at a set time helps them. In 2026-27 the field signals 1:00
(FLOWER ownership unlocks) and 0:20 (final 20 seconds).

## 5. The INIT warning

```java
if (Tunable.countChanged() > 0) t.warn(Tunable.countChanged() + " tuned value(s) differ from the code");
```

Saved tuning values come back after a restart. This warning stops the team from forgetting them at an
event. `EasyAuto` shows it for you.

## Run it

1. In INIT, set Profile to PRACTICE with the D-pad. Press ▶ (start on the Driver Hub).
2. The screen shows the debug lines (Loop, Profile, lift power). BACK hides them.
3. Press GUIDE to open the tuning menu. Change `Lift/maxPower` to 0.3: gamepad 2 A/Y now move the lift
   slowly. Gamepad 1 A saves.
4. Stop and run again: the INIT screen warns "1 tuned value(s) differ from the code".
5. Open the menu and press Y: the values go back to the code values. The next time, the warning is gone.
6. Set Profile to COMPETITION: GUIDE does nothing, and debug starts off.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| Menu does not open | The profile is COMPETITION. That block is on purpose. |
| A change has no effect | The code reads the value once in INIT. Read it every loop. |
| Driving while tuning | Check `tuning.isOpen()`, and ignore gamepad1's sticks while the menu is open. |

## Try this

1. Make the D-pad slow-mode speed a `Tunable`, and find the speed drivers like best.
2. Use `setMinIntervalMs(100)`. Compare the loop speed (debug "Loop") with it and without it.
3. Add a 1:00 cue that shows a warning line for 3 seconds.

---

**Next:** [Tutorial 16: Easy autonomous](16-easy-autos.md).

**Look it up:** [Telemetry module](../modules/telemetry.md), [Core module: Profile](../modules/core.md#profile),
[Tuning module](../modules/tuning.md), [Match module: MatchTimer](../modules/match.md#matchtimer).
