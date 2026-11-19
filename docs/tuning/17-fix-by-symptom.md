# Fix it by what you see

Something on the robot is not right. Find what you see, and change one number.

**You need:** the finders done for the part ([the tuning order](README.md)).

Find what you see in the first column. Change the setting in the middle column, in the direction it says.
Change one number at a time, and test after each change. The [tuning menu](16-live-tuning.md) changes most
of them while the robot runs. Then copy the good value back into `Tunables.java`.

When the last column names a finder, run that finder again: a guess is worse than a measurement.

| What you see or want | Setting | Which way |
| --- | --- | --- |
| **Driving** | - | - |
| The robot is too fast for the drivers | [`Drive.maxPower`](../reference/parts/drive.md#in-tunablesjava) | less |
| A brand-new driver needs a slower robot for a day | [`Drive.practiceMaxPower`](../reference/parts/drive.md#in-tunablesjava) | less, then back to `DEFAULT` |
| Fine moves with the slow trigger are too slow or too jumpy | [`Drive.slowSpeed`](../reference/parts/drive.md#in-tunablesjava) | more / less |
| Double taps are missed, or single taps wait too long | [`Buttons.tapGapSec`](../reference/controls.md#buttons-in-tunablesjava) | more / less |
| **Autonomous moves** | - | - |
| Autonomous does not run ("AUTONOMOUS IS OFF") | [`Drive.calibrated`](../reference/parts/drive.md#in-tunablesjava) | `true`, after the Drive Tuner |
| Sideways moves are short or long (mecanum) | [`Drive.strafeCorrection`](../reference/parts/drive.md#in-tunablesjava) | Drive Tuner |
| Turns stop a few degrees off target | [`Drive.turnMinPower`](../reference/parts/drive.md#in-tunablesjava), then [`Drive.turnKp`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Turns), or more |
| Turns wobble or overshoot | [`Drive.turnKp`](../reference/parts/drive.md#in-tunablesjava) / [`Drive.turnKd`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Turns), or less / more |
| Turns rock near the end and take long to finish | [`Drive.turnToleranceDeg`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Turns), or more |
| The robot stops just before the end of a move | [`Drive.moveMinPower`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Creep), or more |
| Moves slide past the end | [`Drive.slowDownIn`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Stops), or more (try 6) |
| `moveWithTurn` ends its move before the turn is done | [`Drive.moveTurnScale`](../reference/parts/drive.md#in-tunablesjava), then [`Drive.moveMaxTurnPower`](../reference/parts/drive.md#in-tunablesjava) | Move Tuner (Move and turn), or more |
| Path steps do not run on the real robot | [`Odometry.checked`](../reference/parts/odometry.md#in-tunablesjava) | `true`, after the Odometry Tuner |
| The odometry's distances are off (OTOS) | [`Odometry.linearScalar`](../reference/parts/odometry.md#in-tunablesjava) | Odometry Tuner |
| **Intake** | - | - |
| Pieces are thrown or jam on the way in | [`Intake.inPower`](../reference/parts/intake.md#in-tunablesjava) | less |
| Pieces do not come out | [`Intake.outPower`](../reference/parts/intake.md#in-tunablesjava) | more |
| **Lift, arm, servos** | - | - |
| A lift or arm is slow to arrive, or shakes | [`kP`](../reference/parts/lift.md#in-tunablesjava) | more / less |
| A lift sags | [`kF`](../reference/parts/lift.md#in-tunablesjava) | more |
| A lift or arm slams into its ends | [`slowZone`](../reference/parts/lift.md#in-tunablesjava) | more |
| A claw does not open or close far enough | [positions](../reference/parts/claw.md#in-tunablesjava) | Servo Finder |
| Actions go on before a servo arrives | [`travelSec`](../reference/parts/claw.md#in-tunablesjava) | more |
| **Launcher** | - | - |
| Shots land short or long | [`speedRpm`](../reference/parts/launcher.md#in-tunablesjava) (or the shot table) | Shot Tuner, or more / less |
| The flywheel is slow to reach speed, or its speed swings | [`kP`, `kF`](../reference/parts/launcher.md#in-tunablesjava) | Flywheel Finder |
| It waits too long before it fires | [`readyBandRpm`](../reference/parts/launcher.md#in-tunablesjava) | more |
| Shots land at different distances | [`readyBandRpm`](../reference/parts/launcher.md#in-tunablesjava) | less |
| The second of two quick shots lands short | [`shotIntervalSec`](../reference/parts/launcher.md#in-tunablesjava) | more |
| The first shot takes too long to spin up | [`idleRpm`](../reference/parts/launcher.md#in-tunablesjava) | more (uses more battery) |
| One D-pad press changes the speed too much or too little | [`stepRpm`](../reference/parts/launcher.md#in-tunablesjava) | less / more |
| **Feeding** | - | - |
| It feeds two pieces at once | [`feedSec`](../reference/parts/indexer.md#in-tunablesjava) | less |
| It feeds two at once, but less `feedSec` feeds none from farther back | [`stopOnShot`](../reference/parts/indexer.md#in-tunablesjava) | true |
| It feeds no piece | [`feedSec`](../reference/parts/indexer.md#in-tunablesjava) | more |
| It feeds the near pieces but not the ones far back | [`feedSec`](07-feed-time-finder.md) | the Feed Time Finder's number |
| The Flywheel Finder's "Shots seen" is less than the pieces fed | [`shotDropRpm`](../reference/parts/launcher.md#in-tunablesjava) | less |
| With `stopOnShot`, the wheels still run the full `feedSec` after a shot | [`shotDropRpm`](../reference/parts/launcher.md#in-tunablesjava) | less |
| With `stopOnShot`, the wheels stop before the piece gets to the flywheel | [`shotDropRpm`](../reference/parts/launcher.md#in-tunablesjava) | more |
| A pusher comes back before the piece is gone | [`pushSec`](../reference/parts/indexer.md#in-tunablesjava) | more |
| **Aiming** | - | - |
| Aim is slow, or swings past the goal | [`Aim.turnKp`](../reference/parts/aim.md#in-tunablesjava) | more / less |
| Shots miss although it says "AIMED" | [`Aim.toleranceDeg`](../reference/parts/aim.md#in-tunablesjava) | less |
| The robot or turret aims a little to one side | [`cameraOffset`](../reference/parts/aim.md#in-tunablesjava) | Aim Tuner |
| A turret swings too hard | [`Turret.maxPower`](../reference/parts/turret.md#in-tunablesjava) | less |
| The wires allow a turret to turn more or less | [`Turret.min`, `max`](../reference/parts/turret.md#in-tunablesjava) | Motor Finder |
| **Camera and sensors** | - | - |
| The camera pictures are too dark or noisy | [`Camera.gain`](../reference/parts/camera.md#in-tunablesjava) | more / less |
| A HuskyLens's turn angles are too big or too small | [`Camera.hfovDeg`](../reference/parts/camera.md#in-tunablesjava) | measure it |
| A color sensor sees pieces that are not there | [`nearMm`](../reference/parts/pieces.md#in-tunablesjava) | less |
| A color sensor mixes up kinds of piece | [`hueTolerance`](../reference/parts/pieces.md#in-tunablesjava) | less |

When nothing here fits, read [Troubleshooting](../troubleshooting.md): it covers error messages, and
problems with wiring and code.

## Done when

- The robot does what you want, and each changed number is in `Tunables.java`.

---

**Next:** [Tests](../tests/README.md): check your code and your robot after every change.

**Look it up:** [Tunables.java](../reference/tunables.md), [Troubleshooting](../troubleshooting.md).
