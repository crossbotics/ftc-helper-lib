---
id: MEC-03
title: A part on two servos - same position, same moment
area: mechanisms
priority: medium
where: both
minutes: 15
needs: []
kit_bots: [gobilda-strafer]
since: v0.30.0
docs: [modules/robot.md#claw, guides/tunables.md#claw-wrist-and-positioner]
---

## Why

A claw with a servo for each finger, or an arm with a servo on each side, must move both servos together.
If one moves and the other does not, they fight: a servo burns out, or the part twists.

## Setup

- A Claw (or a Wrist, Positioner or servo Indexer) with a second servo:
  `Claw.servo("claw_left").alsoServoReversed("claw_right")`, or its simulator robot file.
- `Tunables.ClawLeft`: `open` and `closed` found with the Servo Finder on `claw_left`, `alsoTrim = 0`.

## Safety

- Test with nothing in the claw first. Keep fingers out of it.

## Steps

1. INIT. Neither servo must move.
2. Press ▶ (start on the Driver Hub), then close the claw (operator B). Both fingers must close at the same moment and meet in the
   middle.
3. Open it. Both must open together.
4. Open and close it 5 times fast. Neither finger may ever move alone: both must end at the same place each
   time. (A pause, BACK, does not stop a servo that is already going: it finishes its move. Feed arms go back to
   `rest` on a pause.)
5. If the fingers do not quite meet, set `alsoTrim = 0.02` (or -0.02) in the tuning menu, then open and close
   again (a new trim is used at the next move). The second finger must move a little more (or less).

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The part (claw, wrist, arm, feed arms), the servos, and the `alsoTrim` you needed.

## If your robot is different

Two servos that face the same way: use `alsoServo` instead. One finger opens when the other closes: you have
the wrong one of the two.
