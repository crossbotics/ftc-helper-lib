---
id: ILK-01
title: Interlock rule - two parts are never in their zones at once
area: interlocks
priority: high
where: both
minutes: 40
needs: [lift|turret]
kit_bots: []
since: unreleased
docs: [guides/interlocks.md, guides/interlocks.md#who-waits, guides/interlocks.md#checked-at-init]
---

## Why

An interlock rule (`keepApart(...)` in `Robot.java`) keeps two parts that share space from crashing,
whoever moves them: a button, a stick, an action, a turret that follows the goal. If it lets both in
once, two parts can break. We test it on our turret and wrist; your two parts are different.

## Safety

- Simulator first. On the real robot, start with slow powers (`maxPower` 0.3 for both parts).
- Measure the zones by moving the parts by hand, with the power off. Never let the test be the first time the two parts come near each other.
- A hand on STOP. Hard stops still on the robot: the rule does not replace them.

## Setup

- Two parts with a position (a turret, lift, arm, wrist, claw, positioner or hood) that can hit each other.
- One line in `Robot.java` after the parts, for example `keepApart("TurretLift", turret.zone(), lift.zoneAbove());`, and its section in `Tunables.java` with your measured zones (`turretFrom`, `turretTo`, `liftAbove`), as in the [interlocks guide](../../docs/guides/interlocks.md#the-idea-with-a-turret-and-a-lift).
- Simulator (no robot of your own yet): the V2 example robot. In the `ftc-helper-lib` folder: `./gradlew :sim:fromRobot -PteamCode=none -ProbotClass=com.crossbotics.ftchelper.examples.v2bot.Robot -Pout=build/v2bot.properties`, then `./gradlew :sim:run -PteamCode=none -Probot=build/v2bot.properties`, open http://localhost:8780 and choose "V2 Bot TeleOp". Its rule is `TurretClaw`: turret 147 to 217 degrees, wrist 0 to 47 degrees.

## Steps

1. INIT. Read the `Interlock ...` line: it names both parts and both zones, in each part's units (degrees, counts, servo position).
2. START. Put part A in its zone (V2: the wrist folded, as it starts).
3. Move part B toward its zone with its stick or button (V2: gamepad 2 right stick X turns the turret toward the back). Part B stops before its zone, and the Driver Station shows one line, for example `TurretClaw : Turret held at ... deg: Wrist is between ...`.
4. Keep asking part B to go in for 5 s. It stays out.
5. Move part A out of its zone (V2: gamepad 2 Y, score in a FLOWER: the wrist swings out). Part B now goes in: by itself if a button sent it to a target (the target is kept), or while you still push its stick.
6. Now B is in its zone. Ask A to go back in (V2: gamepad 2 X, stow). A waits outside its zone until B leaves.
7. Run an action or an autonomous step that moves both parts at once. Read which one waits.
8. Put a wrong number in the rule's Tunables section (say `liftAbove` larger than the lift's `max`). INIT must stop and say what is wrong. Put it back.

## Pass rule

The two parts are never both in their zones (0 times in steps 3 to 7), the waiting part goes on by
itself once the other leaves (2 of 2, steps 5 and 6), and step 8 stops INIT with a message that names
the rule.

## Record

- your two parts (kinds, motor or servo) and the zones you wrote, in their units
- how far outside its zone each waiting part stopped (degrees, counts or position)
- the Driver Station line while it held a part (copy it)
- the INIT message from step 8
- simulator or real robot

## If your robot is different

Any two parts with a position work: an arm and an intake that folds out, a wrist and a low lift, a hood
and a turret. A part with no position (an intake roller) needs `Zone.when(...)` with your own sensor:
say if you used it. If your parts can only hit at some lift heights, a rule with a narrower zone may
be enough: say what you chose and why.
