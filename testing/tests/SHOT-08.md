---
id: SHOT-08
title: Tap, then hold the fire button to spray
area: shooter
priority: medium
where: both
minutes: 10
needs: [flywheel]
kit_bots: [gobilda, rev, studica]
since: unreleased
docs: [tutorials/start/09-shooter-and-camera.md#4-the-two-safety-rules, guides/tunables.md#launcher]
---

## Why

With `sprayEnabled = true`, a tap of the fire button and then a held second press sprays: a steady
stream that does not wait for the speed. Every press still fires at once. We want to know that
drivers can do the gesture on purpose, that two quick taps never spray by accident, and that the
spray stops the moment the button is let go.

## Safety

- Safety glasses. Nobody in front of the launcher.
- Robot on a block or held so it cannot move.

## Setup

- `sprayEnabled = true` in the launcher's section of `Tunables.java`.
- The launcher's own RB (or a button given `robot.launcher.fireOn(...)`).
- 6 pieces loaded (or as many as the robot holds).

## Steps

1. START. Wait until the Driver Station shows READY.
2. Tap RB twice, fast. Count the pieces. The launcher line must not say SPRAY.
3. Reload. Tap RB, then press it again at once and hold it 2 s. Watch the launcher line and the pieces.
4. Let go in the middle of the stream. Watch whether more pieces fire.

## Pass rule

Step 2 fires 2 pieces and never shows SPRAY. Step 3 shows `SPRAY (tap, then hold)` and the pieces
stream out. In step 4 at most one more piece leaves after the release.

## Record

- the pieces fired in steps 2 and 3, and in step 4 after the release
- whether the drivers found the gesture easy
- your `Buttons.tapGapSec` and `Buttons.holdSec` if you changed them

## If your robot is different

No feeder (pieces pushed in some other way): report "could not run".
