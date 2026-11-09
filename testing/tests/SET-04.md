---
id: SET-04
title: Encoder Check finds a swapped or missing encoder cable
area: setup
priority: high
where: both
minutes: 20
needs: [any]
kit_bots: [gobilda, gobilda-strafer, rev, andymark, studica]
since: v0.39.0
docs: [guides/finders.md#encoder-check-each-encoder-cable-in-its-own-port, modules/finders.md#encodercheckopmode]
---

## Why

A lift, an arm, a flywheel and the drive's distances all trust the encoder count. An encoder cable in
another motor's port, or not plugged in, makes them read the wrong count. The Encoder Check must find
both faults and name the right motor.

## Safety

- The robot on a block, wheels off the ground. Lifts and arms at their bottom stop.
- Hands and cables clear: each motor turns a little.
- Unplug and plug encoder cables only with the robot OFF.

## Setup

- A robot in five files with at least two motors that have encoder cables (two drive motors are enough).
- In the simulator: the scenario lines `set encoder NAME none` and `set encoder NAME OTHER` make the faults.

## Steps

1. INIT **Tune and check**. **Encoder Check** must be in the menu, after the Motor Direction Check.
2. Open it, press ▶ (start on the Driver Hub). Nothing may move until you hold A.
3. Hold A. One motor at a time must turn a little. Let go of A in the middle: everything must stop at once.
4. Hold A until the end. Every motor with a good cable must say `OK: counts up` (or `down`). Each lift or arm
   must move away from its stop, never into it.
5. Robot OFF. Swap the encoder cables of two motors (for example front-left and front-right). Robot ON, then
   steps 1 to 4 again. Each of the two must say `counts on "..." instead: encoder cables swapped?` and name
   the other one.
6. Robot OFF. Put the cables back, then unplug one encoder cable. Robot ON, steps 1 to 4 again. That motor
   must say `NO COUNTS: encoder cable missing?`.
7. Robot OFF. Plug every cable back in the right port.

## Pass rule

Steps 4, 5 and 6 name the right motor and the right fault, on 2 runs out of 2.

## Record

- The robot and where (simulator or real robot).
- The two motors you swapped, and the motor you unplugged.
- Each result line the check showed for them.
- Any motor that said OK when it should not (or the other way round).

## If your robot is different

It checks that a motor's push counts on its own port. A two-motor lift or arm is pushed with both motors:
only the first motor's encoder is read. A motor whose encoder nothing reads (an intake) may say
`no counts (fine...)`: that is a pass.
