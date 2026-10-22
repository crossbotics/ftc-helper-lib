---
id: MEC-02
title: A servo's full range - 300 degrees instead of 270
area: mechanisms
priority: medium
where: real
minutes: 25
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.17.0
docs: [modules/hardware.md#full-range, guides/finders.md#servo-finder-ends-and-positions]
---

## Why

The hub sends a servo a pulse from 600 to 2400 microseconds for positions 0 to 1. Most FTC servos take
500 to 2500, so part of their turn is never used: a goBILDA 2000 servo turns 270 of its 300 degrees.
`.fullRange()` sends the whole range. The numbers come from the sellers' pages; we want them checked on
real servos.

## Safety

- Take the servo off its part (or free its linkage) for steps 1 to 4: at full range it turns further and can push on a stop.
- Listen for a humming servo: a servo pushing on something. Back off at once.

## Setup

- A servo on a REV hub (Control Hub, Expansion Hub or Servo Hub) with a horn and a mark on it.
- The Servo Finder OpMode, and a phone angle app or a printed protractor.
- The seller's range for your servo (goBILDA 2000: 300 degrees over 500-2500 us; REV Smart Robot Servo: 270; Axon MK2: up to 360).

## Steps

1. Start the Servo Finder. Choose the servo (do NOT press Y). Go to 0, measure the angle. Go to 1, measure. Write the difference.
2. Stop. Start it again and press **Y** while choosing the servo (full range). Go to 0 and to 1 again. Measure.
3. Compare both with the [table](../../docs/modules/hardware.md#full-range): normal range about 90% of the seller's, full range about all of it.
4. Put the servo back on its part. Add `.fullRange()` to the part's line in `Robot.java` (for example `Claw.servo("claw").fullRange()`). Measure its positions again with the Servo Finder (Y on) and copy the new numbers into `Tunables.java`.
5. INIT your TeleOp: no note that the servo cannot change its pulse. Run the part to each position.

## Pass rule

The normal range and the full range are each within 5 degrees of the table's numbers for your servo
(2 of 2), and with `.fullRange()` the part reaches every position it was measured at (all of them).

## Record

- servo model and the hub it is on
- the angle from 0 to 1: normal range, full range
- the positions before and after (they change with the range)
- any INIT note about the pulse range

## If your robot is different

A servo on something other than a REV hub cannot change its range: INIT says so and nothing changes;
report that. An Axon or REV servo programmed to a smaller range turns less: say how it was programmed.
A servo turret or hood has `.fullRange()` too ([TUR-03](TUR-03.md), [HOOD-01](HOOD-01.md)).
