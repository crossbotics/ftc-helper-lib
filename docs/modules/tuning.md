# Tuning module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

Package: `com.crossbotics.ftchelper.tuning`

Learn it hands-on: [15 Telemetry, profiles and tuning](../tutorials/15-telemetry-profiles-tuning.md). Full guide: [Tuning while the robot runs](../guides/tuning.md).

Change numbers while the robot runs, from the gamepad, with no rebuild.

| Class | Use it to |
| --- | --- |
| [`Tunable`](#tunable) | one changeable number |
| [`PidTunables`](#pidtunables) | the four PID gains of a controller, as tunables |
| [`TuningMenu`](#tuningmenu) | the on-screen menu that changes them (PRACTICE only) |

---

## Tunable

```java
// static final: one shared Tunable, made once (the number inside can still change)
static final Tunable LIFT_KP = Tunable.number("Lift/kP", 0.004, 0.0005);   // name, code value, step
...
pid.setGains(LIFT_KP.get(), 0, 0, 0);   // read it every loop
```

| Method | What it does |
| --- | --- |
| `Tunable.number(name, value, step)` | makes one ("Group/Name"), or returns the existing one with that name |
| `.range(min, max)` | the menu cannot go outside |
| `get()` | the value now (read it every loop) |
| `set(v)`, `increase()`, `decrease()`, `reset()` | change it (in memory) |
| `isChanged()` | true if it differs from the code value |
| `name()`, `group()`, `defaultValue()`, `step()`, `display()` | information |
| `Tunable.all()` | every tunable made |
| `Tunable.saveAll()` | saves the changed ones on the hub (waits for the storage) |
| `Tunable.saveAllInBackground(ok -> ...)`, `Tunable.resetAllInBackground(ok -> ...)` | the same without waiting; the tuning menu uses these |
| `Tunable.resetAll()` | all back to code values; saved values deleted |
| `Tunable.countChanged()` | how many differ from the code (EasyAuto warns in INIT) |
| `Tunable.changedAsCode()` | the ones that differ, as lines to copy into the code: `"Lift: kP = 0.0050"` (in `Tunables.java`, class `Lift`) |

**Saved values come back after a restart.** That is what makes tuning fast, and also a risk:
a value tuned in the shop is used at the event even if the code says something else. So:
when a number is good, **copy it into the code** and press "reset all". The code is the record.

## PidTunables

```java
// name prefix, then the code values of kP, kI, kD, kF
static final PidTunables LIFT = PidTunables.of("Lift", 0.004, 0, 0.0002, 0.05);
LIFT.applyTo(liftPid);       // every loop: copies the menu's values into the controller
```

Makes "Lift/kP", "Lift/kI", "Lift/kD", "Lift/kF" with sensible steps (1/8 of the value).
`PositionMotor.tunable("Lift")` does this for you, plus every named position.

## TuningMenu

```java
TuningMenu menu = new TuningMenu(gamepad1);
controls.bindPress(Pad.G1, Btn.GUIDE, menu::toggle);   // center button opens/closes it
// every loop:
menu.update();
if (menu.isOpen()) menu.addTelemetry(t);   // the menu shows only while it is open
```

| While open | Does |
| --- | --- |
| D-pad up / down | choose a number |
| D-pad right / left | change by one step (hold a bumper: x10) |
| A | save all on the hub |
| X | the chosen one back to its code value |
| Y | ALL back to code values, saved values deleted |
| B | the changed values as lines to copy into the code (also in the robot log, tag `ftc-helper`); B again: the list |

It only opens in the PRACTICE profile. **Do not drive with the same gamepad while it is open**:
check `menu.isOpen()` and stop reading the sticks (the starter kit's TeleOp shows how).
