# Odometry

The sensor that knows where the robot is on the field: a goBILDA Pinpoint, a SparkFun OTOS, or dead wheels
(free-turning wheels with encoders that roll on the floor). Learn it step by step: [Drive to a point](../../building-autos/05-auto-builder-odometry-and-pedro.md#drive-to-a-point-driveto),
and [lesson 7](../../tutorials/start/07-odometry.md). The [odometry module](../../modules/odometry.md) has
everything, with sketches of the offsets.

## In Robot.java

```java title="Robot.java"
odometry(Odometry.pinpoint("pinpoint").offsetsMm(-84, -168));   // in define(), after drive(...)
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Odometry.pinpoint(name)` | **yes**, one of the five | a goBILDA Pinpoint | - |
| `Odometry.otos(name)` | **yes**, one of the five | a SparkFun OTOS | - |
| `Odometry.threeWheels(left, right, perp)` | **yes**, one of the five | three dead wheels on encoder ports | - |
| `Odometry.twoWheels(parallel, perp)` | **yes**, one of the five | two dead wheels, and the drive's IMU | - |
| `Odometry.custom(make)` | **yes**, one of the five | your own pose source, made at INIT from the robot's devices (odometry with AprilTags, or another library's localizer). It must use the library's frame: x right, y away, heading 0 = away, counterclockwise + | - |
| `.offsetsMm(xPodLeft, yPodForward)` | Pinpoint | where the pods sit, mm: the forward pod's distance LEFT of the center (right = -), the sideways pod's distance FORWARD of the center (back = -). The Odometry Tuner measures them | - |
| `.robotSizeMm(length, width)` | with an `...FromEdgesMm` | the frame's size, mm (bumpers off). The library takes the middle of it as the robot's center | INIT stops if an `...FromEdgesMm` needs it |
| `.podsFromEdgesMm(forwardPodFromLeft, sidewaysPodFromFront)` | Pinpoint, in place of `offsetsMm` | where the pods sit, from the robot's edges: the forward pod's wheel from the LEFT edge, the sideways pod's wheel from the FRONT edge, mm. The library works out `offsetsMm`. The last of the two you write wins | - |
| `.podsSwingarm()` | Pinpoint, optional | goBILDA swingarm pods instead of 4-bar pods | 4-bar pods |
| `.ticksPerMm(n)` | Pinpoint, optional | other pods: encoder counts per mm of wheel travel | goBILDA's pods |
| `.reversed(...)` | optional | pods that count the wrong way (the Odometry Tuner says which): `"x"` and/or `"y"` for a Pinpoint, the motor port names for dead wheels. A name that is not a pod stops INIT | none reversed |
| `.mountIn(forward, left, headingDeg)` | OTOS | where it sits: inches forward of the center, inches left, and its arrow's direction (counterclockwise degrees) | - |
| `.mountFromEdgesMm(fromFront, fromLeft, headingDeg)` | OTOS, in place of `mountIn` | where it sits, from the robot's edges: its middle from the FRONT edge and from the LEFT edge, mm, and its arrow's direction. Needs `robotSizeMm` | - |
| `.trackWidthIn(in)` | three wheels: **yes** | the distance between the left and right forward pods, inches | INIT stops |
| `.parallelLeftIn(in)` | two wheels | how far LEFT of the center the forward pod is, inches (right = -) | - |
| `.perpForwardIn(in)` | dead wheels | how far FORWARD of the center the sideways pod is, inches (back = -) | - |

**Keep-out boxes:** `fieldMap(FieldMap.margin(13).keepOut(...))` in `Robot.java` lists the places the robot
must not drive through, for `s.routeTo(...)` and `route(...)`
([keep-out boxes and routes](../../modules/odometry.md#keep-out-boxes-and-routes)).

## In Tunables.java

<!-- tunables: Odometry -->
Section `Odometry` (a fixed name). The odometry reads it once, at INIT.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-reset-spot -->
```java title="Tunables.java"
public static class Odometry {                                  // a fixed name: always Odometry
    public static boolean checked = false;                      // true after the Odometry Tuner's Loop test
    // "Set pose here" (TeleOp: hold gamepad 1 START 1 s on this spot), written for BLUE.
    public static double resetX = -56;                          // inches right of center, - = left
    public static double resetY = -61;                          // inches from center, - = our half
    public static double resetHeading = 0;                      // degrees, 0 = facing the field
    // RED: false = the same numbers from the red wall; true (or left out) = mirrored left-right.
    public static boolean mirrorForRed = false;                 // BIOBUZZ: false
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `checked` | like `Drive.calibrated`, for the odometry: `driveTo` and path steps run on a real robot only when this is `true`. Until then INIT warns `Odometry not checked` (the simulator runs them anyway) | true/false | false | no | Odometry Tuner | after the Odometry Tuner's loop check passes |
| `linearScalar` | OTOS only: corrects its distances | times | 1.0 | no | Odometry Tuner | the OTOS says 48 in when the robot drove 50 |
| `angularScalar` | OTOS only: corrects its turns | times | 1.0 | no | test (turn 10 times, compare) | the heading drifts after many turns |
| `resetX`, `resetY` | the "set pose here" spot for `resetPose()`: x, y seen from your wall, written for BLUE. Write both: with only one, there is no reset spot | inches | none: no reset spot | no | measure on the field | you want a button that resets the pose at a known spot |
| `resetHeading` | the heading at the reset spot | degrees | 0 | no | measure on the field | the robot faces another way at that spot |
| `mirrorForRed` | ONE setting for the whole robot (routes, start and reset spots, the aim's goal point): RED uses BLUE's numbers mirrored (true) or the same numbers from the red wall (false). Write it once, in `Aim` or here. The library takes `Aim`'s first, then this one, then your autonomous's `mirrorForRed()`. INIT shows the choice and where it came from | true/false | `Tunables.Aim.mirrorForRed`, else true | no | the game's field | BIOBUZZ's field is turned half a turn: `false` |

### Where mirrorForRed comes from

`mirrorForRed` says whether RED's half is a mirror image of BLUE's. The whole robot uses ONE answer: the
routes, the start and reset spots, and the aim's goal point. The library takes the first one it finds:

| Order | Where | Note |
| --- | --- | --- |
| 1 | `Tunables.Aim.mirrorForRed` | |
| 2 | `Tunables.Odometry.mirrorForRed` | |
| 3 | your autonomous's `mirrorForRed()` method | in TeleOp: the last autonomous's |
| 4 | none written | true |

Write it in one place. On BIOBUZZ's turned field: `false`. INIT shows the answer and where it came from. When
your autonomous's `mirrorForRed()` disagrees with Tunables, INIT warns you to write it in one place.

## What it does

- It gives `robot.pose()` every loop: where the robot is.
- It makes `driveTo` steps in autos work ([Steps for autos](../steps.md)).
- TeleOp starts where the auto ended.
- The library updates the odometry first in each loop, before every part.
- "Set pose here": `resetPose()` ([Robot.java](../robot-java.md#robotdefinition)) puts the robot on the
  reset spot. In TeleOp: hold gamepad 1 START 1 s on that spot.

## Commands

| Question | What it tells you |
| --- | --- |
| `kindName()` | the kind, in words |
| `names()` | the device names |
| `podTicksPerMm()` | a Pinpoint's encoder counts per mm |
| `isReversed(which)` | whether that pod counts the other way |

---

**Look it up:** [`Odometry` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Odometry.html)
(every method). **The other parts:** [Reference](../README.md).
