---
id: CTL-05
title: A PS controller sees its own button names on the screens
area: controls
priority: medium
where: both
minutes: 10
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: v0.32.0
docs: [modules/gamepad.md#buttonnames]
---

## Why

A student holding a PlayStation-style controller has no button called "A". The library's screens must
name the buttons printed on the controller in use, and say ▶ (not START) for the Driver Hub's start button.

## Setup

- A robot in five files (any kit bot), or its simulator robot file.
- A PS4, PS5 or Etpark controller as gamepad 1 (linked with OPTIONS + ×). In the simulator: plug a Sony
  controller into the laptop and pick it as gamepad 1 on the page (an Etpark shows letters there), or put
  `set gamepad 1 ps` at the top of a scenario.

## Safety

- On a real robot: wheels on a block before you press ▶. Nothing moves in INIT.

## Steps

1. INIT **Tune and check**. The menu's last lines must say "× = open it. Then ▶." and "SHARE = back to this menu".
2. Open the Servo Finder with ×. Its screen must say "Press ▶" and "PS (center button), or hold OPTIONS 1 s = help".
3. Press ▶ on the Driver Hub, then the PS button: the help page must name × ○ □ △ and L1, with no A, B, X or Y.
4. STOP. INIT your TeleOp and press the PS button: the button map must say "G1 ×", "G1 R2" and the like.
5. Do steps 1 and 2 again with a Logitech or Xbox controller: the screens must say A, B, BACK, GUIDE and START.

## Pass rule

Each step shows what it says, on 2 runs out of 2. No line shows a mark in braces, such as `{A}`.

## Record

- The controller (make and model) and where (simulator or real robot).
- Any screen line that still shows a letter name on a PS controller, or a `{...}` mark.

## If your robot is different

Lines your own code writes with plain `telemetry.addLine(...)` keep the names you wrote. Send them
through `RobotTelemetry` with marks such as `{A}` to get the controller's names.
