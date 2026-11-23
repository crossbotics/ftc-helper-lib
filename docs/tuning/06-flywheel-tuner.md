# Flywheel Tuner: PID and bang-bang (experimental)

Test many speed-control settings on the flywheel, both PID and bang-bang, and find the best of each.

**In Tune and check:** yes, when Robot.java has a launcher.
**You need:** the robot on a block, safety glasses, nobody in front of the launcher, **no balls in the
robot**, a FULL battery (12.6 V or more) and a used battery (12.0 to 12.4 V), and room to drive 4 ft for the
driving check.

**Experimental:** this tool is new. The simulator cannot show the real overshoot or a real shot. Check the
result on the field before a match.

## Why use it

A ball takes speed from the flywheel. The next ball must wait until the wheel is back at speed, or it goes
short. How fast the wheel gets back depends on the speed control:

- **PID** (`speedControl = "PID"`, the default): the hub pushes harder the further the wheel is from its
  speed. `kP` is the push strength.
- **Bang-bang** (`speedControl = "BANG_BANG"`): full power when the wheel is well below its speed, a steady
  holding power near it, and no power (coast) above it.

The [Flywheel Finder](06-flywheel-finder.md) finds PID numbers that hold a speed. This tool compares many PID
and bang-bang settings by the same tests, and keeps only the safe ones. It finds all six bang-bang numbers,
which no other tool does.

It changes nothing in `Tunables.java`: it works the same whichever `speedControl` your robot uses.

## How it works

It never feeds a ball. A **pretend shot** turns the power off until the wheel has lost one ball's speed, then
turns the setting back on and times how fast the wheel is back at speed. One ball's speed is `ballDropRpm` in
`Tunables.Launcher`: the speed one real ball takes off your wheel (the [Flywheel Finder](06-flywheel-finder.md)
shows it as "Shot drop"). Without `ballDropRpm`, it uses `shotDropRpm`. Shot counting never uses `ballDropRpm`.

```java
public static double ballDropRpm = 230;   // in class Launcher: one real ball takes 230 RPM off our wheel
```

**A pretend shot that is too small to time:** the wheel must lose more than `readyBandRpm` plus 2 speed-reading
steps (the full-power test measures the step). With less, the time back at speed is mostly chance. Then the
tuner does not rank by that time. The result page says `NOT ranked by time back at speed`. Set `ballDropRpm`.

Each setting gets the same test:

1. The wheel rolls to a stop (power off), until it reads under 60 RPM for 1 s.
2. From the stop to the middle shot speed. "Steady speed" means 0.75 s in a row inside `readyBandRpm`.
3. It holds the speed for 2 s.
4. Three pretend shots.
5. A speed change up to the table's highest speed, then down to its lowest. Before a PID setting goes down, it
   checks the braking (see `would brake the motor hard` below). A setting that would brake hard fails, and the
   lower speed is never sent.

The tests on the FULL battery (run 1):

1. Today's settings: what the robot uses now. The others must be at least as fast to steady speed.
2. Full power from a stop: how fast the wheel can speed up at shot speed.
3. Three steady powers: the power each speed needs (bang-bang's `bangKs` and `bangKv`).
4. PID: today's `kP`, then 25, 50, 100 and 150. `kI` and `kD` stay. `kF` moves in 1% steps (at most 6%) until
   the wheel holds within 25 RPM.
5. Bang-bang: from a wide full-power zone to a narrow one, then the best one with a bigger switching gap.
   `bangKv` moves in 1% steps too (at most 6% from the steady powers' number) until it holds within 25 RPM.
6. Today's settings again: did the wheel change while it worked (a warm motor)?

A family (PID or bang-bang) stops getting stronger at its first unsafe result. On the LOW battery (run 2), it
tests today's settings and the best of each family again, then asks for a driving check of each.

**The middle speed:** with a shot table, the speed for the distance halfway between its first and last rows.
The speed changes go to the first and last rows' speeds. With no table: `speedRpm`, and 10% either side.

## Get to the Flywheel Tuner

1. On the Driver Hub, open the TeleOp list and pick **Tune and check** (group "Finders"). Press **INIT**.
2. The menu shows. Press **D-pad down** until the arrow (`>`) is on **Flywheel Tuner**, then press **A**.
3. The Flywheel Tuner's INIT screen shows. Nothing moves yet. Read the `!!` lines. Take every ball out of the
   robot. Press **▶** (start) on the Driver Hub.

## Run 1: the FULL battery

1. Put in a FULL battery: 12.6 V or more with the motor off. The screen shows `Battery : 12.8 V: OK`.
2. Press **A**. It counts down 3 s, then the flywheel starts. It runs every test by itself.
3. Stay with the robot. The screen shows the test number, the setting, what it does now and the speed.
   A setting that fails shows one line: `Why: went more than 100 RPM past the target.`
4. **To stop:** press ANY button. The power goes off at once. The wheel rolls to a stop. Finished tests are
   saved. Press **A** again to go on from the test it was on. The battery may then be up to 0.3 V lower than
   the run's range (it ran down a little), but it must be the same kind of battery.
5. At the end, the screen says `run 1 of 2 done`. Press STOP (■) and turn the robot off.

## Run 2: the LOW battery

1. Put in a used battery: 12.0 to 12.4 V with the motor off. Turn the robot on.
2. Open the Flywheel Tuner again. It reads the saved tests. Press **▶** (start).
3. Press **A** to start run 2. It tests today's settings and the best settings again.
4. **The driving check:** the screen asks first. The sticks then drive at half power, and the flywheel holds
   the middle speed. Clear the floor 5 ft around the robot. Take the robot off its block.
   - **A** starts it (3 s countdown). Wait until the screen says `DRIVE NOW`.
   - Drive forward and back about 4 ft, and turn a little, for 10 seconds.
   - It passes when the speed stays within `readyBandRpm` + 50 RPM.
   - **B** skips it. Then that control has no winner, and the result says why.
   - No drive in Robot.java: the check cannot run. **A** or **B** skips it, and the screen says so.
5. At the end, the screen says DONE or NOT DONE.

## Why a setting fails

| The screen says | What it measured |
| --- | --- |
| `slower to steady speed than today's settings` | from a stop, it reached steady speed more than 0.1 s after today's settings |
| `went more than 100 RPM past the target` | two readings in a row more than 100 RPM past the target (less half a reading step, because each reading is rounded to a step) |
| `held a speed more than 25 RPM off the target` | the average over 2 s, from the encoder's count |
| `the speed jumped more than 100 RPM while holding` | two readings in a row more than 100 RPM off while it held the speed |
| `said "at speed", then lost it right away` | at speed, then out of the ready band within 0.75 s |
| `never got to steady speed in time` | not steady 8 s after the start |
| `not back at speed after a pretend shot in time` | not back at speed 3 s after a pretend shot |
| `did not reach a new speed in time` | not steady 8 s after a speed change |
| `would brake the motor hard` | PID only: `kP` x (speed - target) is more than `kF` x target + a fifth of full power (in encoder counts a second). Bang-bang never brakes |
| `the motor took too much current` | over 9 A for 1 s. Bang-bang then tries again with `bangMaxPower` 0.9, then 0.8 |
| `pulled the battery too low` | under 10.5 V for 0.5 s |
| `lost the speed while driving` | the driving check: further than `readyBandRpm` + 50 RPM |

**Under 10 V** (2 readings in a row): the run stops. The finished tests are kept; the test that was running does
not count. Charge or change the battery, then press **A** to go on. One low reading alone (a dip at a
full-power start) does not stop it.

**Over 9 A for 1 s in the full-power test or the steady powers:** the run stops. Something rubs or jams the
wheel. Turn it by hand: it must spin freely. Fix it, then press **A** to go on.

**The speed reads 0 or backward with the power on:** it stops. Run the
[Encoder Check](02-wiring-checks.md), fix the cable, and start again.

**`Found: full power is too weak`:** full power at shot speed cannot win back one ball's speed
(`ballDropRpm`, else `shotDropRpm`) before the next ball (`shotIntervalSec`). No setting fixes that. Feed slower, or make the
launcher stronger. The tests go on, to find the best there is.

## The result

The winner of each control has the shortest time back at speed after a pretend shot. A tie goes to less
wobble, then to less overshoot, then to less time at full power. When the pretend shot is too small to time
(see "How it works"), the time back at speed does not count: the steadiest wins. A winner passed every test on both
batteries and its driving check.

- **Page 1:** DONE or NOT DONE, each winner's time back at speed, and the time from a stop to steady speed
  (Continuous fire gives up after 3 s).
- **Page 2 (D-pad right):** the PID lines: `kP`, `kI`, `kD`, `kF`.
- **Page 3:** the bang-bang lines: `bangKs`, `bangKv`, `bangFullBelowRpm`, `bangCoastAboveRpm`,
  `bangHysteresisRpm`, `bangMaxPower`.
- **Page 4:** what it cannot measure.
- **X** two times: a new session (after you changed the robot, for example).

Keep `speedControl` as it is. The Group Shot Tuner tests both winners with real balls and chooses one.

## What it cannot measure

- **Motor heat.** Feel the motor after each run. Too hot to touch: let it cool before the next run.
- **How hard the hub's PID really brakes.** The braking test is a calculation from `kP` and `kF`.
- **Real balls.** A pretend shot only slows the wheel. It does not show how a ball leaves.
- **The speed reading's delay.** It assumes 50 ms. The loop with the camera on can be slower than here.

## Fix a mistake

- **Stopped by mistake:** press **A**. It goes on from the test it was on.
- **Wrong battery:** the screen says `NOT full` or `too full`, and nothing starts. Change the battery.
- **You changed `Tunables.java` between runs:** the saved tests no longer fit. The screen says
  `settings changed`. Press **A** to start a new session. The old file is kept as `flywheel-tuner-old.txt`.

**Start over after a finished session:** on the result pages, press **X** two times. The old session is kept
as `flywheel-tuner-old.txt`, and run 1 starts again. Before the end, a stopped run always goes on where it
stopped; to start over then, delete `flywheel-tuner.txt` from the results folder
([get the results file](01-tune-and-check.md#get-the-results-file-on-the-laptop)).

## Put the numbers in your code

The robot uses `Tunables.java`, not the tuner's screen. Take a photo of pages 2 and 3, and type each line
into the class it names. The same lines are in the results file, after STOP.

| The tuner shows | Put it in |
| --- | --- |
| `Open Tunables.java, find class Launcher.` and the PID lines | that class: these replace `kP`, `kI`, `kD`, `kF` |
| the bang-bang lines | the same class: add them, or replace them |

## Done when

the screen says DONE, and you copied both pages of lines.

---

**Next:** [Feed Stop Finder](07-feed-stop-finder.md) (a launcher with a feed stop), or
[Feed Time Finder](07-feed-time-finder.md) (a CR servo or motor feeder with no feed stop).

**Look it up:** [Launcher: In Tunables.java](../reference/parts/launcher.md#in-tunablesjava),
[finders module](../modules/finders.md#flywheeltuneropmode).
