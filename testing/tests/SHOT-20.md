---
id: SHOT-20
title: ejectFeedSec pushes out one piece, not two
area: shooter
priority: low
where: real
minutes: 10
needs: [flywheel]
kit_bots: []
since: v0.46.0
docs: [reference/parts/launcher.md]
---

## Why

An eject runs a spinning feeder (CR servo or motor) for its `feedSec`. That can be long enough to push the next
piece out too. With `ejectFeedSec` set, the feeder runs only that long.

## Safety

- Eye protection. Nobody in front of the launcher.

## Setup

- A launcher with a CR servo or motor feeder and a button for `robot.launcher.ejectAction()`.
- `ejectFeedSec = 0.6` in the Launcher part of Tunables.java to start.

## Steps

1. Load 3 pieces. Press the eject button once. Count the pieces that come out.
2. Change `ejectFeedSec` by 0.1 at a time until one piece comes out each time (5 times in a row).
3. Check that the piece lands just outside the robot (`ejectRpm`).

## Pass rule

5 presses in a row: exactly 1 piece out each time, landing within 12 in of the robot.

## Record

- the feeder, `feedSec`, the `ejectFeedSec` that works, `ejectRpm`

## If your robot is different

A push servo: `ejectFeedSec` does nothing (it pushes one piece already); skip the test. A spinning feeder behind a
feed stop or a feed wheel: it applies, from when the feeder really starts.
