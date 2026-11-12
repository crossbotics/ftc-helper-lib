---
id: SIM-13
title: The simulator gives the 4-piece foul (G407)
area: simulator
priority: low
where: sim
minutes: 10
needs: []
kit_bots: [gobilda, gobilda-strafer]
since: v0.41.0
docs: [guides/simulator.md#game-rules-and-the-score, reference/parts/intake.md#in-robotjava]
---

## Why

BIOBUZZ rule G407: a robot may not hold more than 4 pieces at a time. A robot that holds 6, or holds 5
for a long time more than once, gets a MAJOR FOUL. The simulator must give it, so a team sees the cost of
an intake that takes too many.

## Setup

- SIM-01 works on this laptop.
- A copy of your robot file. On its `collect.` line, set `holds=6`.

## Steps

1. Start the simulator with the copy. Run a TeleOp and press ▶.
2. Drive over pieces until the robot holds 5. Push one out within 3 s. The **Fouls** list must stay empty.
3. Take a fifth piece again and keep it for more than 3 s. Push one out. Do it a second time. The
   **Fouls** list must show one G407 foul, and the other alliance must get 20 points.
4. Press **Back to start**. Run the TeleOp again and take 6 pieces. The G407 foul must show as the sixth
   piece comes in.
5. Take more pieces. No second G407 foul may show in the same match.

## Pass rule

Each step does what it says, on 2 runs out of 2.

## Record

- Your computer and browser.
- The robot file you used.

## If your robot is different

Any robot file with a `collect.` line works. A robot that cannot reach 5 pieces cannot do this test.
