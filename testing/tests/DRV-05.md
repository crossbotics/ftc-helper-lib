---
id: DRV-05
title: Move Tuner finds the stopping and turning numbers
area: drive
priority: high
where: both
minutes: 30
needs: []
kit_bots: [gobilda-strafer]
since: unreleased
docs: [tuning/10-move-tuner.md, reference/parts/drive.md#in-tunablesjava]
---

## Why

The Drive Tuner makes a move the right length. The Move Tuner must make it stop in the right place, and
make turns end on their heading, by suggesting `moveMinPower`, `slowDownIn` and the turn numbers.

## Setup

- A mecanum or tank robot in five files with `Tunables.Drive.calibrated = true` (or the simulator).
- 5 ft of clear floor around the robot. A tape measure.
- A charged battery (12.5 V or more).

## Safety

- Nobody near the robot. Hand on STOP. Nothing moves before ▶.

## Steps

1. INIT **Tune and check**. The **Move Tuner** must be on the list after the Drive Tuner (and the
   Odometry Tuner, with odometry). Open it, press ▶.
2. Set the power of your autonomous moves (D-pad left/right).
3. Run **1 Creep**. The screen must show `moveMinPower now ...` and the lines to copy.
4. Run **2 Stops**. It must show one line per `slowDownIn` it tried, and end with `OK` or the best one.
   Measure one 24 in move with the tape: it must end within 0.5 in of the tuner's number.
5. Run **3 Turns** until it says `OK`, at most 6 rounds. Write down each round's change.
6. Run **4 Drift**. It must show the heading change of each 48 in move.
7. Mecanum: run **5 Move and turn** until it says `OK`, at most 5 rounds.
8. While a test moves, press GUIDE. The robot must stop at once, and the screen must say `not measured`.
9. Copy the lines into `Tunables.Drive`. Run an autonomous with an 8 in and a 24 in move and a 90 degree turn,
   3 times.

## Pass rule

Steps 1 to 8 show what they say. In step 9 every move ends within 0.5 in of its distance, and every turn
within 2 degrees, on 3 runs out of 3.

## Record

- The robot, the floor, the battery and where (simulator or real robot), and whether it has odometry.
- Each number the tuner suggested, and how many rounds Turns and Move and turn took.
- The ends in step 9.

## If your robot is different

A tank robot has no Move and turn test, and Creep and Drift test forward only. With odometry, the tuner
measures with it: check one move with the tape anyway.
