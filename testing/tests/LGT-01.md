---
id: LGT-01
title: Lights - colour rules and a flash per shot
area: lights
priority: low
where: both
minutes: 20
needs: [lights]
kit_bots: []
since: unreleased
docs: [tutorials/start/09-shooter-and-camera.md#13-a-light-for-the-drivers, modules/robot.md#lights]
---

## Why

A light lets the drivers see the robot's state without looking at the Driver Station. The rules
must pick the first true one, and the light must never change faster than 5 times a second (game
rule R202 J).

## Safety

- Do not point bright lights at people's eyes or onto the field.

## Setup

- `lights = add(Lights.gobilda("light"));` or `Lights.blinkin("blinkin")` in `Robot.java`.
- Rules in `Controls.java`, for example the five lines in lesson 9 section 13.
- Simulator: the light is a dot on the robot.

## Steps

1. INIT: the light shows a colour already (the rules work from INIT).
2. START. Make each rule true in turn (spin up, aim on, ...) and check the colour.
3. Make two rules true at once: the one written first wins.
4. Fire 3 pieces (`fire(3)` or hold RB): 3 white flashes.
5. Wait for the last 20 s of TeleOp (or use a short match timer): yellow blinking.
6. Film the blinking with a phone (slow motion): count changes per second.

## Pass rule

Every colour matches its rule (all of them), 3 shots give 3 flashes, and the light changes at most
10 times a second (5 on and 5 off).

## Record

- light type (goBILDA RGB, REV Blinkin)
- your rules (copy the lines)
- any colour that was wrong (which rule, what it showed)
- changes per second from the video

## If your robot is different

Use your own rules; any true/false works. If a goBILDA colour looks wrong on your light, say which
LightColor and what you saw.
