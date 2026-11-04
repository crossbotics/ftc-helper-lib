---
id: CTL-04
title: TeleOp INIT menu: choose the alliance and the profile with the D-pad
area: controls
priority: medium
where: both
minutes: 10
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [tutorials/start/03-buttons.md#6-the-button-map-init-and-guide]
---

## Why

A practice round often starts straight in TeleOp, with no autonomous before it to set the alliance. The
drivers must be able to choose the alliance and the PRACTICE / COMPETITION profile in TeleOp's INIT.

## Setup

- A robot in five files (any kit bot), or its simulator robot file.
- No autonomous run since the robot app started (so no alliance is remembered).

## Safety

- On a real robot: wheels on a block, or space to drive, before START. Nothing moves in INIT.

## Steps

1. Pick the TeleOp and press INIT. The screen must show "Choose (D-pad), then START" with
   "> Alliance: BLUE" and "Profile: ..." under it.
2. Press gamepad 1 D-pad right. The alliance must change to RED ("Alliance : RED (chosen here)").
3. Press D-pad down, then D-pad left. The profile must change (COMPETITION to PRACTICE, or back).
   In PRACTICE, the screen must say "PRACTICE profile".
4. Press START and drive. In PRACTICE the top speed must be lower (`Tunables.Drive.practiceMaxPower`).
5. STOP, INIT again: the profile you chose must still be there.
6. Set the profile back to what it was.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- Any step that did not change what it says.

## If your robot is different

A TeleOp of your own that overrides `initMenu()` to return false has no menu: X and B still choose the
alliance.
