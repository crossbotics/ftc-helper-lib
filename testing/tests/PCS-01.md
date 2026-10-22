---
id: PCS-01
title: Piece Tuner - can the sensor tell the pieces apart?
area: pieces
priority: medium
where: real
minutes: 30
needs: [color-sensor|distance-sensor]
kit_bots: []
since: unreleased
docs: [guides/finders.md#piece-tuner-what-each-sensor-sees, modules/robot.md#pieces-and-piecesensor]
---

## Why

A colour sensor (or a distance sensor) tells the robot which piece it holds: POLLEN or NECTAR, red or
blue. How well it works depends on where the sensor is mounted and the light. We want readings from
many mounts.

## Safety

- Nothing moves in this test. Keep fingers out of the intake if it is powered.

## Setup

- A `Pieces` or `PieceSensor` line in `Robot.java` ([lesson 9 section 12](../../docs/tutorials/start/09-shooter-and-camera.md#12-extra-know-which-piece-fires-next)).
- The Piece Tuner file for your robot ([recipe](../../docs/cookbook.md#how-do-i-tune-the-piece-sensors)).
- One of each game piece.

## Steps

1. Start the Piece Tuner. D-pad up/down chooses the sensor.
2. Put a POLLEN where it really sits (or passes). D-pad left/right to POLLEN, press A, hold still.
3. Do the same for each kind (RED_NECTAR, BLUE_NECTAR), then EMPTY with nothing there.
4. Read any warning (two kinds too close, EMPTY looks like a piece).
5. Copy the lines into `Tunables.java`, rebuild, run it again.
6. Hold up each piece 10 times, in slightly different spots: count how often "It sees" names it right.

## Pass rule

Each kind is named right at least 9 of 10 times, and EMPTY reads NONE at least 9 of 10 times.

## Record

- sensor (REV Color Sensor V3, REV 2m Distance, a webcam) and how far it is from the piece (mm)
- the lines it printed
- right out of 10 for each kind and for EMPTY
- the warnings it gave

## If your robot is different

A distance sensor tells sizes apart, not colours: test POLLEN against NECTAR only. Different light
(a gym, a window) changes colour readings: if you can, test in two places and say where.
