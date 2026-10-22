---
id: SHOT-04
title: Speed trim on the D-pad, and last match's trim
area: shooter
priority: high
where: both
minutes: 20
needs: [flywheel, limelight|webcam|pinpoint|otos|dead-wheels]
kit_bots: []
since: unreleased
docs: [tutorials/start/09-shooter-and-camera.md#speed-trim-and-a-low-battery, cookbook.md#every-shot-is-a-bit-short-today-how-do-i-trim-the-speed-a-low-battery]
---

## Why

Some days every shot is a bit short. The operator trims all speeds by 1% per D-pad press. The trim must
stop at 15%, never ask for more than the motor can do, start at 0 every INIT, and only OFFER last
time's trim (never put it back by itself).

## Safety

- Safety glasses if you fire pieces. You can do this test without firing (watch the numbers).

## Setup

- A launcher with speed from the distance (`.speedFromDistance(camera)` or `.speedFromOdometry()`) and a shot table.
- Two drivers: the trim is on gamepad 2 (one driver: gamepad 1).

## Steps

1. INIT, START. Spin up (LB). Read the launcher's first line: `Speed trim: 0%`.
2. Press D-pad up 3 times: `Speed trim: +3%`. The target speed goes up about 3%.
3. Hold D-pad up: one more step every 0.25 s. Keep holding: it stops at +15% (or says `(motor limit)` first).
4. Double-tap D-pad down: back to 0%.
5. Trim to +3%. STOP. INIT again: the trim is 0% and the Driver Station says `Trim last time: +3% (double-tap D-pad up after START to use it)`.
6. START, double-tap D-pad up: the trim is +3%.

## Pass rule

Each press changes the target by 1% of the table's speed (within 10 RPM), the trim never passes 15%
or the motor's top speed, and after INIT the trim is 0 until you double-tap D-pad up (1 of 1).

## Record

- the target speed at 0% and at +3% (RPM)
- where it stopped in step 3 (+15% or motor limit, and the RPM)
- the INIT line in step 5
- one driver or two

## If your robot is different

With a fixed speed (no table) the D-pad changes `speedRpm` by `stepRpm` instead of trimming: report
that it did so, if that is your robot. If you put the trim on other buttons in your own code, say how.
