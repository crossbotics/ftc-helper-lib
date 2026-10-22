---
id: SHOT-03
title: Shot Tuner - build a shot table with hits and misses
area: shooter
priority: high
where: real
minutes: 60
needs: [flywheel, limelight|webcam|pinpoint|otos|dead-wheels]
kit_bots: []
since: unreleased
docs: [guides/finders.md#shot-tuner-the-shot-table, tutorials/start/09-shooter-and-camera.md#8-two-game-pieces-two-shot-tables]
---

## Why

The Shot Tuner builds the speed-for-each-distance table the way experienced teams do: 3-5 shots per
speed, each marked HIT or MISS, and the best hit ratio wins. We want to know a team that has never
done it can fill a table that scores, in about an hour.

## Safety

- Safety glasses. Nobody between the robot and the goal. Collect pieces only when the flywheel is off (LB).

## Setup

- SHOT-01 done (gains). A camera that sees the goal, or odometry with `Tunables.Aim.goalX`, `goalY`.
- A Shot Tuner file for your robot ([recipe](../../docs/cookbook.md#how-do-i-build-a-shot-table-speed-for-each-distance)).
- 10 or more game pieces, a tape measure, a charged battery.

## Steps

1. Start the Shot Tuner. Two tables (POLLEN and NECTAR)? Press Y until `> Table`, pick one with D-pad left or right, and load that piece.
2. Park at your nearest shooting distance. Check the distance line says where it came from (camera or odometry). LB starts the flywheel.
3. Fire 3-5 (RB). After each, press A = HIT or B = MISS. Change the speed with the D-pad and fire 3-5 again. Try 2-3 speeds.
4. Press X to save the best. Mark one wrong? Left stick click takes it back first.
5. Move back 1-2 ft and repeat, until `Points:` says 4 for this table.
6. Copy the line it shows into `Tunables.Launcher`. Rebuild. In your TeleOp, fire 5 from a distance between two points.

## Pass rule

The table has 4 or more points, and in step 6 at least 3 of 5 shots score from a distance between
two points.

## Record

- camera or odometry for the distance
- your table line(s), copied from the screen
- hits out of 5 in step 6, and the distance
- total time it took (minutes)
- any "sagged" warning, and the battery voltage

## If your robot is different

Fewer than 10 pieces? Fire 3 per speed. A robot without a camera can use the odometry for the
distance: put the robot in the middle of the field facing away from you before INIT, or set
`startSpotIn()`. A hood adds a HOOD choice under Y (see HOOD-03).
