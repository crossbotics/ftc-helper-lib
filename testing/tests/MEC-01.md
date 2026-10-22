---
id: MEC-01
title: A lift or arm on two motors - same power, same moment
area: mechanisms
priority: high
where: real
minutes: 30
needs: [lift]
kit_bots: []
since: v0.17.0
docs: [modules/robot.md#two-motors-on-one-lift-or-arm]
---

## Why

Many lifts have a motor on each side. If the two motors get different powers, or one turns the wrong
way, they fight: they get hot and can break a gearbox or bend the slide. `alsoMotor` and
`alsoMotorReversed` send every power to both motors in the same loop. We tested it in the simulator
and with the test kit; real two-motor lifts are needed.

## Safety

- **Chain or belt OFF for steps 1 and 2.** Two motors that fight can break a gearbox.
- Low power first (`maxPower` 0.3 in the lift's Tunables section). Fingers out of the slide.
- A hand on STOP. Hard stops at both ends.

## Setup

- The lift's line in `Robot.java` with its second motor: `lift = add(Lift.slide("lift_left").alsoMotorReversed("lift_right"));` (or `alsoMotor` when both turn the same way). An arm: `Arm.motor(...).alsoMotorReversed(...)`.
- The encoder cable in the FIRST motor (`lift_left`). The Tunables section is the first motor's name in CamelCase (`Tunables.LiftLeft`).

## Steps

1. Chain or belt off. INIT: read `LiftLeft: two motors, "lift_left" (its encoder is read) and "lift_right" (...)`.
2. START. Push the lift's stick up a little. Both motor shafts must turn the way that lifts. One wrong? Use the other method (`alsoMotor` or `alsoMotorReversed`), rebuild, try again.
3. Chain or belt on. Run the lift to each named position (low, high...) 3 times.
4. Pause with gamepad 1 BACK while it moves up: both motors stop at once.
5. After 2 minutes of use, touch both motors: they should be about as warm as each other.

## Pass rule

Both motors turn the lifting way (2 of 2) in step 2, the lift reaches each position within its
`tolerance` in all runs of step 3, and neither motor is clearly hotter than the other in step 5.

## Record

- lift or arm, motors (model and gearbox), which method you used (`alsoMotor` or `alsoMotorReversed`)
- how the motors face each other (mirror images, or the same way)
- any position it missed, and by how much (counts or inches)
- the motor temperatures by hand (cool, warm, hot)

## If your robot is different

An arm driven from both ends works the same way: test its positions in degrees. If your two motors
drive separate slides that are only joined at the top, report how far apart the two sides got
(inches), since only the first motor's encoder is read.
