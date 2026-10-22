---
id: SHOT-05
title: Battery compensation - 4 quick shots on a tired battery
area: shooter
priority: high
where: real
minutes: 40
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: unreleased
docs: [tutorials/start/09-shooter-and-camera.md#speed-trim-and-a-low-battery, modules/robot.md#speed-trim-and-battery-compensation]
---

## Why

Battery compensation is off by default because it is not yet proven on a real robot. This test is how we find out. It should help the wheel get
back to speed after each shot on a low battery. If it makes no difference, or the wheel overshoots,
we want to know: then we will change the default.

## Safety

- Safety glasses. Nobody in front of the launcher. A clear space where shots land.
- Do not run a battery below 11.0 V on purpose: stop when it reaches about 11.3 V.

## Setup

- SHOT-01 done: `kF` and `nominalVolts` in `Tunables.Launcher`, and `public static boolean batteryCompensation = true;` there to turn it on. (Without `nominalVolts`, INIT says `battery compensation on, nominalVolts not written: assuming 12.8 V`: write yours from the Flywheel Finder.)
- A battery at about 11.5 V under no load (run the robot for a while, or use an older battery). A full battery for step 6.
- A phone to film the Driver Station screen (the speed numbers), or a second person to write them.
- 8 pieces.

## Steps

1. With the 11.5 V battery: INIT, START, spin up (LB). Wait for READY.
2. Fire 4 shots as fast as the launcher allows (hold RB). Film the launcher's measured RPM and target.
3. Write the lowest RPM after each shot, and how long it took to get back to READY.
4. Set `batteryCompensation = false` in `Tunables.Launcher`, rebuild, and repeat steps 1-3 on the same battery.
5. Compare: with compensation on, does the wheel come back to speed faster?
6. Put a full battery in, compensation back on (`true`). Spin up: watch for an overshoot (the speed going more than 3% over the target).

## Pass rule

With compensation on, the time back to READY after each shot is at least 10% shorter than with it
off, and in step 6 the speed never goes more than 3% over the target.

## Record

- battery voltage at the start of each run (on, off)
- for each of the 4 shots, on and off: lowest RPM, time back to READY (seconds)
- the biggest overshoot in step 6 (RPM)
- your `kF`, `nominalVolts`, flywheel motor

## If your robot is different

If your launcher fires slower than the wheel recovers, film single shots instead and say so. If you
see no difference, that is a real result: report it (then leaving it off is fine). If the wheel
overshoots after a battery swap, turn compensation off and tell us.
