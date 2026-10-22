---
id: PCS-02
title: Piece count and the next piece picks the shot table
area: pieces
priority: medium
where: both
minutes: 30
needs: [intake, color-sensor|distance-sensor]
kit_bots: []
since: v0.17.0
docs: [tutorials/start/09-shooter-and-camera.md#12-extra-know-which-piece-fires-next, cookbook.md#game-pieces]
---

## Why

`Pieces` counts what comes in and what goes out, and says which piece fires next. With two shot
tables, the next piece can pick the table. The count can be wrong in real life (two pieces touching,
a bounce): we want to know how often.

## Safety

- Keep fingers out of the intake. Safety glasses if you fire.

## Setup

- PCS-01 done (each kind tuned).
- `pieces = add(Pieces.kinds(...).comingIn(...).max(4))` before the launcher line; for the table, `.tableFrom(pieces.nextKind())` on the launcher.
- Simulator: sensor lines in your robot file (`color.NAME = at=intake`, see [game pieces](../../docs/guides/simulator.md#game-pieces)).

## Steps

1. INIT, START. Run the intake. Feed 4 pieces in one at a time. Read the count after each.
2. At 4 the intake should stop taking more (if you used `stopsWhen(robot.pieces::isFull)`).
3. Read `next`: it should be the first piece that went in (or the one at the ready sensor).
4. With two tables: load a NECTAR next. The shot table line should switch to NECTAR.
5. Fire them one by one. The count goes down by 1 each time.
6. Feed 2 pieces that touch each other. Does it count 2?

## Pass rule

In 10 single pieces fed, the count is right at least 9 times, and the table follows the next piece
(the right table for at least 4 of 5 shots).

## Record

- sensors and where they are
- count right out of 10
- what happened with 2 touching pieces
- shots where the wrong table was used

## If your robot is different

A robot with no ready sensor counts only from the intake: say which sensors you have. A robot that
holds fewer pieces: use your `max(...)`.
