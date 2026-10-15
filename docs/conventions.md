# Signs and units

Robot code goes wrong most often when two parts disagree about which way is "positive".
This library uses ONE set of rules everywhere. Learn these and you can read any of it.

## Driving

| Value | Positive means | Where you see it |
| --- | --- | --- |
| `forward` | robot moves forward | `MecanumDrive.drive`, `TankDrive.arcade`, the mixers |
| `strafeRight` | robot moves to its right (mecanum only) | `MecanumDrive.drive`, `MecanumMixer.mix` |
| `turnClockwise` | robot turns right (clockwise, seen from above) | same |
| IMU heading | robot turned **left** (counterclockwise) | `ImuSetup.headingDeg`, `AutoDrive.turn`, `AutoDrive.turnTo`, `FieldPose.headingDeg` |
| `move(..., directionDeg, ...)` | clockwise from forward: 0 forward, 90 right, 180 back, -90 left | `AutoDrive.move`, `AutoDrive.moveWithTurn` |

Why is turning clockwise-positive but heading counterclockwise-positive?

- The right stick's X is positive when pushed right, and pushing right should turn the robot right.
  So `drive(forward, strafe, gamepad1.right_stick_x)` just works.
- The FTC SDK's IMU says heading grows when the robot turns left. That is the math rule
  (counterclockwise = positive) and we do not fight the SDK.

The names tell you which one you have: `turnClockwise`, `headingCcwDeg` ("CCW" = counterclockwise).

## Gamepad sticks

The SDK gives **negative** `left_stick_y` when you push the stick UP (away from you).
So "forward" is `-gamepad1.left_stick_y`. Every example in these docs has the minus sign.

## Units

| Thing | Unit |
| --- | --- |
| Distances (drive) | inches |
| Angles | degrees |
| Motor power | -1 to 1 |
| Servo position | 0 to 1 |
| Mechanism positions (lifts, arms) | encoder ticks (counts) |
| Time | seconds, unless the name ends in `Ms` (milliseconds) or `Nanos` |

## Wheel order

Any array of four wheel values is in this order:

`[front-left, front-right, back-left, back-right]`

Standard (tank) drive left/right arrays are `[left, right]`.

`MecanumMixer.FRONT_LEFT` (0), `FRONT_RIGHT` (1), `BACK_LEFT` (2), `BACK_RIGHT` (3) name the positions.
