---
id: CTL-01
title: Pause at BACK, and START-then-BACK pairs
area: controls
priority: high
where: both
minutes: 15
needs: [any]
kit_bots: [gobilda, rev, andymark, studica]
since: unreleased
docs: [tutorials/start/03-buttons.md#7-pause-with-back-and-the-other-library-buttons, guides/buttons.md#moving-the-librarys-buttons]
---

## Why

BACK is the panic button: it must pause the robot at the press, even if the driver holds it. START and
BACK together (START first) are the debug lines. If BACK goes down first, it must stay a plain pause
and never start the robot again by itself.

## Safety

- Real robot: low power, clear floor. You will stop the robot while it moves.

## Setup

- Any robot in five files with its TeleOp. Simulator: any robot (a kit bot from SIM-01 is fine). In the simulator, BACK is the Backspace key and START is Enter.

## Steps

1. INIT, START. Drive forward and run a mechanism (an intake).
2. Press BACK: everything stops at once, the Driver Station says PAUSED. Keep holding BACK for 3 s: the robot stays stopped.
3. Let go, then tap BACK: everything goes on (the intake runs again).
4. On gamepad 1, hold the small START button, then press BACK. Let go of both: the debug lines show. The robot does not pause.
5. Press BACK first, then START: it is a plain pause. Let go of both: the robot stays paused. Tap BACK to go on.
6. Hold BACK for 1 s with field-centric on: it pauses and resets "forward". Robot-centric: a note says "forward" is only reset in field-centric.

## Pass rule

The robot stops within 0.2 s of the BACK press in steps 2 and 5, never starts again while BACK is held
or after BACK-then-START (0 times), and step 4 shows the debug lines without a pause.

## Record

- robot, one driver or two
- how fast it stopped (about)
- what step 4 and step 5 did
- anything that surprised the driver

## If your robot is different

If you moved pause or debug to other buttons (`libraryButtons`), use yours and say which. A Logitech
or PS controller names the buttons differently (Share, Options): say which controller you used.
