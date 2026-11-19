---
id: SHOT-15
title: The Feed Time Finder gives a feedSec that feeds every piece
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel]
kit_bots: [gobilda, gobilda-strafer, rev]
since: v0.44.0
docs: [tuning/07-feed-time-finder.md, reference/parts/indexer.md#in-tunablesjava, modules/finders.md]
---

## Why

A CR servo or motor feeder runs for `feedSec` per piece. Too short, and a piece far back does not reach the
flywheel. The Feed Time Finder times test shots and gives `feedSec` from the longest. If its times are wrong,
the robot misses pieces in a match.

## Setup

- A flywheel launcher with a CR servo or motor feeder (`Indexer.crServo(...)` or `Indexer.motor(...)`), with no
  feed stop, no `.pushedBy(...)` and no feed wheel, or its simulator robot file. The goBILDA and REV kit bots have a CR servo feeder.
- 5 pieces or more.
- On the simulator: the pieces are already loaded. Skip the loading in steps 3 and 4, and skip step 5 (it needs an empty robot).

## Safety

- Eye protection. Nobody in front of the launcher. Keep fingers out of the wheels.

## Steps

1. Run Tune and check. The menu must show "Feed Time Finder". Open it with A, then press ▶ (start).
2. Press LB. The wheel must spin up, and the screen must say "ready: RB = test shot".
3. Load one piece. Press RB. The piece must fire, and the feeder must stop at the shot. The screen must show
   "Test shots" with one time.
4. Do 4 more test shots. Load each piece in a different place, and one far back. The screen must show 5 times,
   the shortest, average and longest, and a `feedSec` line.
5. Fire with no piece loaded. After 3 seconds, the screen must say "No shot seen in 3 s", and the count must
   not go up.
6. Put the `feedSec` line in Tunables.java. Run your TeleOp. Fire 5 pieces, from the same places as in step 4.
   Each press must fire one piece.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The feeder (CR servo or motor), its `power`, and the 5 test shot times of step 4.
- The `feedSec` the finder gave, and whether `stopOnShot` was on.

## If your robot is different

A light piece may not slow the flywheel enough to count as a shot: the finder then says "No shot seen" for every
test shot. Set `shotDropRpm` in the launcher's Tunables section lower (try 100), and run the steps again.
