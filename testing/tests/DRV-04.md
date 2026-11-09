---
id: DRV-04
title: Drive Tuner at your autonomous's power, and slowDownIn for short moves
area: drive
priority: high
where: both
minutes: 20
needs: []
kit_bots: [gobilda-strafer]
since: v0.33.0
docs: [guides/finders.md#drive-tuner-wheel-size-and-strafe-correction, guides/tunables.md#drive]
---

## Why

A faster robot slides further when it stops, and a short move has little road to slow down on. The Drive
Tuner must measure at the power the autonomous uses, and `slowDownIn` must make short moves end in the
right place.

## Setup

- A mecanum or tank robot in five files with `Tunables.Drive.calibrated = true` (or the simulator).
- The Drive Tuner's floor: tape marked 0 and 48 in, 5 ft clear ahead and to the right.
- A charged battery (12.5 V or more).

## Safety

- Nobody in the robot's path. Hand on STOP. Nothing moves before ▶.

## Steps

1. INIT **Tune and check**, open the **Drive Tuner**, press ▶. The test list shows `Power : 0.5`.
2. Press D-pad right once. It must show `Power : 0.6`. D-pad left/right must stop at 0.3 and 1.0.
3. Run the **Drive** test (it is faster than at 0.5) and, on a mecanum robot, the **Strafe** test. Measure,
   set it, press A until each measures 48 within 0.5 in. `Tunables.Drive: calibrated = true` must show only
   after that.
4. Change the power again: `calibrated = true` must go away until the Drive (and Strafe) test measures right
   again.
5. STOP. In the results file, the Drive Tuner part must say `measured at power 0.6`.
6. Run an autonomous with an 8 in move at 0.6, 3 times. Measure where it ends each time.
7. Set `Tunables.Drive.slowDownIn = 6`, INIT, run it 3 more times. It must end closer to the 8 in (it slows
   down over its last 6 in now), or as close as before.

## Pass rule

Steps 1 to 5 show what they say. In step 7 the 8 in move ends within 0.5 in of 8 on 3 runs out of 3, and
never further from 8 than in step 6.

## Record

- The robot, the floor (tiles or other), the battery and where (simulator or real robot).
- The three ends in step 6 and in step 7.

## If your robot is different

With odometry pods, `driveTo` steps measure the real position, so slide matters less: test the plain
`.move(...)` steps. A robot whose autonomous uses several powers: tune at the power of the moves that must be
exact.
