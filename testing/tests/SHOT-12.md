---
id: SHOT-12
title: Feed Stop Finder: the open time and the test shots
area: shooter
priority: medium
where: real
minutes: 20
needs: [flywheel]
kit_bots: []
since: v0.41.0
docs: [tuning/07-feed-stop-finder.md, reference/parts/launcher.md]
---

## Why

The Feed Stop Finder now finds the feed stop's two times. It works out `feedStopMoveSec` from the servo's
catalog speed, and it times test shots for `feedStopOpenSec`. We want to know that the times it gives are
right on a real launcher, and that its warnings make sense to students.

## Safety

- Eye protection for everyone near the robot. Nobody in front of the launcher.
- Fingers away from the feed stop and the wheel while the wheel is on.

## Setup

- A launcher with a feed stop servo, `.feedStop("feed_stop", "<the servo's catalog id>")` in Robot.java.
- 5 pieces.
- A phone that films slow motion (to check the times).

## Steps

1. **Tune and check > Feed Stop Finder.** Find and save hold (X) and release (B).
2. Read `Opens in : about ... s`. Film the stop opening in slow motion, and count the real open time.
3. Press LB: the wheel spins up. Check that the D-pad, A, X and B do nothing while it is on.
4. Do 5 test shots with RB, a piece behind the stop each time. Read `Test shots : ...`.
5. Film one test shot in slow motion: count the time from the stop opening to the piece touching the wheel.
6. Fire one test shot with no piece. The finder must say `No shot seen in 3 s`.
7. If you can, put two pieces tight against each other behind the stop, and fire. Read whether the finder
   says `2 pieces in one opening`.
8. Copy the times line into `Tunables.java`, and fire 5 pieces in TeleOp in the PRACTICE profile. Read
   `Last feed ended`.

## Pass rule

The `Opens in` time is at least the filmed open time (1 of 1), each test shot time is within 0.05 s of the
filmed time (1 of 1), step 6 warns (1 of 1), and every TeleOp shot in step 8 says `shot seen` (5 of 5).

## Record

- the servo's catalog id, hold and release
- `Opens in` and the filmed open time
- the 5 test shot times and the filmed time
- the warnings you saw (steps 6 and 7), and whether students understood them
- the times line, and the `Last feed ended` lines in step 8

## If your robot is different

A servo that is not in the catalog: `Opens in` says it is not known. Do the rest, and measure the open time
with the video only. A heavy wheel that slows by less than 150 RPM: every test shot may warn `No shot seen`
even when the piece fired. Record that, with the wheel's speed drop from the `Shot` debug line.
