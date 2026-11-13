---
id: LGT-01
title: Lights - color rules and a flash per shot
area: lights
priority: low
where: both
minutes: 20
needs: [lights]
kit_bots: []
since: v0.17.0
docs: [add-to-robot/08-lights.md#controlsjava-the-rules, reference/parts/lights.md]
---

## Why

A light lets the drivers see the robot's state without looking at the Driver Station. The rules
must pick the first true one, and the light must never change faster than 5 times a second (game
rule R202 J).

## Safety

- Do not point bright lights at people's eyes or onto the field.

## Setup

- `lights = add(Lights.gobilda("light"));` or `Lights.blinkin("blinkin")` in `Robot.java`.
- Rules in `Controls.java`, for example the rules in [Add lights: the rules](../../docs/add-to-robot/08-lights.md#controlsjava-the-rules).
- Simulator: the light is a dot on the robot.

## Steps

1. INIT: the light shows the alliance, not your rules (solid red or blue once it is chosen; [LGT-02](LGT-02.md) tests that part). START: a 1 s flash of the alliance, then your rules.
2. Make each rule true in turn (spin up, aim on, ...) and check the color.
3. Make two rules true at once: the one written first wins.
4. Fire 3 pieces (`fire(3)` or hold RB): 3 white flashes.
5. Wait for the last 20 s of TeleOp (or use a short match timer): yellow blinking.
6. Film the blinking with a phone (slow motion): count changes per second.

## Pass rule

Every color matches its rule (all of them), 3 shots give 3 flashes, and the light changes at most
10 times a second (5 on and 5 off).

## Record

- light type (goBILDA RGB, REV Blinkin)
- your rules (copy the lines)
- any color that was wrong (which rule, what it showed)
- changes per second from the video

## If your robot is different

Use your own rules; any true/false works. If a goBILDA color looks wrong on your light, say which
LightColor and what you saw.
