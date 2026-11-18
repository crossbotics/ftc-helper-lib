---
id: SHOT-14
title: A spinning feeder stops when the flywheel sees the shot
area: shooter
priority: medium
where: both
minutes: 15
needs: [flywheel]
kit_bots: [gobilda, gobilda-strafer, rev]
since: v0.43.0
docs: [reference/parts/indexer.md, reference/parts/launcher.md#in-tunablesjava, tuning/06-flywheel-finder.md, reference/parts/indexer.md#in-tunablesjava, tuning/17-fix-by-symptom.md]
---

## Why

With `stopOnShot`, the feed wheels stop as soon as the piece hits the flywheel. If the robot misses the shot,
the wheels run too long and can push a second piece. If it sees a shot too early, the piece stops short.

## Setup

- A flywheel launcher with a CR servo or motor feeder (`Indexer.crServo(...)` or `Indexer.motor(...)`), or its
  simulator robot file. Pieces to fire. The goBILDA and REV kit bots have a CR servo feeder.
- In the feeder's Tunables section: `stopOnShot = true`, and `feedSec = 1` (long, so an early stop is easy to see).
- Firing with SPRAY for step 5: `sprayEnabled = true` in the launcher's Tunables section.
- On the simulator: skip steps 1 and 3. The Flywheel Finder has no piece to feed there, and step 3 needs an
  empty robot.

## Safety

- Eye protection. Nobody in front of the launcher. Keep fingers out of the wheels.

## Steps

1. Run the Flywheel Finder (Tune and check). Set the target to your shot speed. Feed 10 pieces, one at a
   time, and wait for "Ready: YES" before each. Write down "Shots seen" and the smallest "Shot drop". It must
   show 10. If it shows fewer, stop here and record the numbers.
2. Run your TeleOp. Fire one piece. The feed wheels must stop as soon as the piece leaves, well before 1 second.
3. Fire with no piece. The wheels must run the full 1 second (`feedSec`), then stop.
4. Load 3 pieces close together. Fire 3 times, one at a time. Each shot must take one piece only.
5. Load 3 pieces. Spray them (tap, then hold the fire button). The wheels must not stop between pieces. They
   stop at most 1 second (`feedSec`) after the last feed starts.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The feeder (CR servo or motor), the flywheel speed, and the "Shots seen" count and smallest "Shot drop" of step 1.
- `shotDropRpm`, if you changed it.

## If your robot is different

A light piece may not slow the flywheel enough to count as a shot. Then set `shotDropRpm` in the launcher's
Tunables section lower (try 100), and run steps 2 to 4 again. If that does not work,
leave `stopOnShot` off and tune `feedSec` by hand.
