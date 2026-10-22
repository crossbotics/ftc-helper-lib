---
id: PATH-02
title: PathAt - start things along a path while it drives
area: paths
priority: high
where: both
minutes: 40
needs: [mecanum, pinpoint|otos|dead-wheels, pedro]
kit_bots: []
since: unreleased
docs: [tutorials/start/08-paths-with-pedro.md#5-write-your-first-path, modules/pedro.md#do-things-along-the-path]
---

## Why

`PathAt` starts a part (an intake, a spin-up) at a place along a Pedro path while the robot keeps
driving. It is new. We need to know each kind fires once, at the right place, on robots that are not
ours, and that the report says when one did not fire.

## Safety

- Real robot: the robot drives a path by itself. Clear floor, PRACTICE profile, hand on STOP.
- Use a part that is safe to switch on while driving (an intake, a light). Not a flywheel with pieces in it.

## Setup

- Lesson 8 done: Pedro added, `PedroConstants` tuned, `Tunables.Odometry.checked = true`.
- A path with two parts (`PedroPath.line(...).then(PedroPath.curve(...))`), about 4-6 ft long.
- Tape marks on the floor where you expect each thing to start (real robot).

## Steps

1. Add three things to your `follow` step: `PathAt.fraction(0.5, "Intake on", robot.intake::in)`, `PathAt.part(2, "Intake off", robot.intake::stop)` and `PathAt.inchesLeft(12, ...)` with any part of yours.
2. Run it in the simulator first: does each start where you expected? (Watch the part, and the Driver Station.)
3. Run it on the robot (PRACTICE). Watch where the robot is when each one starts. Mark it on the floor.
4. Measure how far each mark is from where you expected it.
5. Put a box in the path so the robot stops early (it ends `STALLED` or timed out). Read the report: it should list the things that did not start (`not fired: ...`).
6. Try a mistake: `PathAt.fraction(2, ...)`. INIT must stop with a message.

## Pass rule

Each `PathAt` starts once (3 of 3) within 3 in of where you expected on the robot; step 5's report
names every thing that did not fire; step 6 stops at INIT with a message that names the mistake.

## Record

- your path (copy the `PedroPath` line)
- for each `PathAt`: expected place, real place, distance off (inches)
- the report line from step 5
- the INIT message from step 6

## If your robot is different

Use any parts you have; a light or a servo is fine. `PathAt.near(x, y, 6, ...)` and
`PathAt.inchesDone(...)` are welcome as extra tries. `driveTo` steps take `PathAt` too: if you have
no Pedro, try `PathAt.inchesLeft(...)` on a `driveTo` and say so.
