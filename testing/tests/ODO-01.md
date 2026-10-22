---
id: ODO-01
title: Odometry Tuner - directions, offsets and the loop check
area: odometry
priority: high
where: real
minutes: 45
needs: [pinpoint|otos|dead-wheels]
kit_bots: []
since: v0.16.0
docs: [guides/finders.md#odometry-tuner-directions-offsets-and-a-loop-check, tutorials/start/07-odometry.md#5-the-odometry-tuner]
---

## Why

Everything that uses the robot's place on the field (driveTo, Pedro paths, odometry aim, shooting on
the move) depends on this tuner. It is written for three kinds of odometry. We have tested it on a
Pinpoint; OTOS and dead wheels need your help.

## Safety

- The sticks drive at 30% in this tuner. Clear floor, about 6 x 6 ft.
- Keep feet away from the wheels while you push or drive.

## Setup

- A robot with an `odometry(...)` line in `Robot.java` ([lesson 7](../../docs/tutorials/start/07-odometry.md#4-one-line-in-robotjava)).
- The small Odometry Tuner file next to `Robot.java`.
- A 48 in strip of tape on the floor with a mark at its start, and a tape measure.

## Steps

1. Start "Odometry Tuner". D-pad to "Forward 48 in", A. Drive or push the robot 48 in straight along the tape, B. Write what it says.
2. If it said "counted BACKWARD", add the fix it shows, rebuild, and run step 1 again.
3. "Left 24 in": A, move the robot 24 in to its left, B. Fix and repeat if it says "counted RIGHT".
4. "Spin 3+ turns": A, turn in place at least 3 times with the right stick, B. Copy the offsets it shows into your odometry line. Rebuild.
5. "Loop and back": start on the mark, drive anywhere for about 20 s, come back onto the mark facing the same way, B.
6. If it shows `Tunables.Odometry: checked = true`, set it in `Tunables.java`.

## Pass rule

The Forward test is within 2% (48 in counts as 47.0 to 49.0 in), and the Loop test passes: back
within 1 in and 2 degrees of the mark.

## Record

- odometry kind (Pinpoint, OTOS, 2 or 3 dead wheels) and the pod type
- Forward test: the % off it showed, before and after fixes
- the offsets (or track width) it gave
- Loop test: the distance and angle error it showed, and how many tries it took

## If your robot is different

A tank robot cannot strafe: push it sideways by hand for the Left test. An OTOS has no offsets to
measure (measure `mountIn` with a tape). Dead wheels with the IMU need `.imu(...)` in the drive line.
Say which of these you did. Pods in odd places (far off centre, angled) are very useful to report.
