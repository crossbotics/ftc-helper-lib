# Tuning module

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

Package: `com.crossbotics.ftchelper.tuning`

Learn it hands-on: [15 Telemetry, profiles and tuning](../tutorials/15-telemetry-profiles-tuning.md). Full guide: [Tuning while the robot runs](../tuning/16-live-tuning.md).

With this module you change numbers from the gamepad while the robot runs, with no rebuild. Use it to tune PID
gains, positions and speeds fast in the shop. A **tunable** is one number the menu can change.
`PositionMotor.tunable(...)` and the [robot in five files](../reference/tunables.md) make their numbers tunable for
you. This page is for your own numbers, and for how the menu works.

| Class | Use it to |
| --- | --- |
| [`Tunable`](#tunable) | make one number changeable from the menu |
| [`PidTunables`](#pidtunables) | make the four PID gains of a controller tunable |
| [`TuningMenu`](#tuningmenu) | show the on-screen menu that changes them (PRACTICE profile only) |

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
| `Tunable.number(name, value, step)` | makes one ("Group/Name"), or gives the one that already has that name (the first code value stays) |
| `Tunable.fromCode(name, value, step)` | the same, but an existing one that nobody tuned takes this code value. `PositionMotor.tunable`, `PositionServo.tunable`, `PidTunables.of` and the robot in five files use it. So two programs that use one name each use their own numbers |
| `.range(min, max)` | the menu cannot go outside this range |
| `get()` | the value now (read it every loop) |
| `set(v)`, `increase()`, `decrease()`, `reset()` | change it in memory |
| `isChanged()` | whether it differs from the code value |
| `name()`, `group()`, `defaultValue()`, `step()`, `display()` | read its name, group, code value, step and display text |
| `Tunable.all()` | every tunable the code made |
| `Tunable.shown()` | the ones the tuning menu shows: all except those of a part that is not on the robot |
| `Tunable.hideGroup(group, hidden)`, `Tunable.hiddenGroups()`, `isHidden()` | leaves a group ("IntakeDeploy") out of the menu, or shows it again. The robot in five files does this at every INIT for a part marked `optional()` whose device is missing ([parts not on the robot yet](../reference/robot-java.md#parts-not-on-the-robot-yet)). The saved values stay |
| `Tunable.saveAll()` | saves the changed ones on the hub. It waits until the storage is done |
| `Tunable.saveAllInBackground(ok -> ...)`, `Tunable.resetAllInBackground(ok -> ...)` | the same, without waiting. The tuning menu uses these |
| `Tunable.resetAll()` | puts all back to the code values, and deletes the saved values |
| `Tunable.countChanged()` | how many differ from the code. EasyAuto warns about them in INIT |
| `Tunable.forgetAllForTests()` | for JUnit tests only: forgets every tunable in memory, so one test's robot does not see another's numbers ([`PretendDevices`](testkit.md) does it). Never use it in robot code |
| `Tunable.changedAsCode()` | the ones that differ, as lines to copy into the code: `"Lift: kP = 0.005"` (in `Tunables.java`, class `Lift`) |

**Saved values come back after a restart.** That makes tuning fast, and it is also a risk:
the robot uses a value tuned in the shop at the event, even when the code says something else. So
when a number is good, **copy it into the code** and press "reset all". The code is the record.

## PidTunables

```java
// name prefix, then the code values of kP, kI, kD, kF
static final PidTunables LIFT = PidTunables.of("Lift", 0.004, 0, 0.0002, 0.05);
LIFT.applyTo(liftPid);       // every loop: copies the menu's values into the controller
```

`PidTunables.of` makes "Lift/kP", "Lift/kI", "Lift/kD" and "Lift/kF". Each step is 1/8 of the value.
`PositionMotor.tunable("Lift")` does this for you, and also makes every named position tunable.

## TuningMenu

```java
TuningMenu menu = new TuningMenu(gamepad1);
controls.bindPress(Pad.G1, Btn.GUIDE, menu::toggle);   // center button opens/closes it
// every loop:
menu.update();
if (menu.isOpen()) menu.addTelemetry(t);   // the menu shows only while it is open
```

| While open | What it does |
| --- | --- |
| D-pad up / down | choose a number |
| D-pad right / left | change by one step (hold a bumper for x10) |
| A | save all on the hub |
| X | put the chosen one back to its code value |
| Y | put ALL back to the code values, and delete the saved values |
| B | show the changed values as lines to copy into the code. They also go in the robot log, tag `ftc-helper`. Press B again for the list |

The menu opens only in the PRACTICE profile. **Do not drive with the same gamepad while it is open.**
Check `menu.isOpen()`, and stop reading the sticks while it is true. The starter kit's TeleOp shows how.
