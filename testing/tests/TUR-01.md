---
id: TUR-01
title: Turret home switch (touch or magnetic) - homes at START
area: turret
priority: high
where: both
minutes: 40
needs: [turret, home-switch]
kit_bots: []
since: unreleased
docs: [guides/turret-setup.md#2-how-does-it-know-its-angle, guides/turret-setup.md#what-the-init-screen-shows]
---

## Why

A turret's motor encoder counts from where it was at INIT. A home switch tells the turret where it
really is: it turns slowly to the switch at START, and sets its angle again every time it passes it, so
a slipping belt is fixed during the match. Two kinds of switch work (a touch sensor and a magnetic
switch); we have tested the magnetic one.

## Safety

- The turret moves by itself at START (slowly, at `homingPower`). Fingers and wires away from it.
- Hard stops at the wire limits. Start with `homingPower = 0.2` and `homingTimeout = 5`.

## Setup

- The switch on the robot and in the configuration: a touch sensor (e.g. REV Touch Sensor) as **REV Touch Sensor**, or a magnetic switch (e.g. REV Magnetic Limit Switch, a magnet on the turret) as a **Digital Device**.
- `.homeSwitch("turret_home")` on the turret's line in `Robot.java`, and `homeAngle` in `Tunables.Turret` ([how to find it](../../docs/guides/turret-setup.md#2-how-does-it-know-its-angle)).
- Simulator: the V2 example robot (a magnetic switch at 0 degrees; commands in [ILK-01](ILK-01.md)).

## Steps

1. Turn the turret by hand about 30 degrees away from the switch. INIT. Read the lines `Turret : home switch at ... deg: found at START` and `Turret homing : at START it turns ... slowly to find the switch`. Nothing moves in INIT.
2. START. The turret turns slowly toward the switch (`homing: looking for the switch`) and stops homing when it closes. Following and "aimed" wait until then.
3. Point the turret with the stick to a mark you know (straight forward). Read the angle on the Driver Station. Measure the real angle.
4. Turn the turret past the switch and back 5 times. The angle on the Driver Station stays right (the switch sets it again at each pass).
5. Unplug the switch (power off first), power on, INIT, START: after `homingTimeout` seconds the turret stops and the Driver Station says `homing failed: switch not found`. Plug it back in.
6. Rename the switch in the configuration (or leave it out): INIT says `NO home switch` and the start angle is used.

## Pass rule

After homing, the angle on the Driver Station is within 2 degrees of the real angle (step 3) and still
within 2 degrees after 5 passes (step 4), and step 5 stops within `homingTimeout` + 1 s (1 of 1).

## Record

- switch kind (touch or magnetic) and how it is configured
- `homeAngle`, `homingPower`, `homingTimeout` you used
- the angle error after homing and after 5 passes (degrees)
- which way it turned to home, and whether you needed `homeDirection`
- what step 5 and step 6 showed

## If your robot is different

If homing turns the long way round, set `homeDirection` (+1 = counterclockwise, -1 = clockwise) and
say so. A turret that starts ON the switch has nothing to look for: say what the screen showed. A CR
servo turret with an encoder on a motor port homes the same way: say which you have.
