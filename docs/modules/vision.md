# Vision module

Package: `io.github.jasoncross.ftchelper.vision`

Learn it hands-on: [18 Vision aiming](../tutorials/18-vision-aim.md).

Find AprilTags with a USB webcam or a Limelight 3A, and aim at them. Your code talks to ONE
interface, so switching cameras is a one-line change.

| Class | Use it to |
| --- | --- |
| [`TargetSource`](#targetsource) | the interface every camera has |
| [`WebcamTags`](#webcamtags) | AprilTags with a USB webcam (the SDK's processor; SDK 12 clusters too) |
| [`LimelightTags`](#limelighttags) | AprilTags with a Limelight 3A |
| [`TargetObservation`](#targetobservation) | one target: id, name, bearing, distance, picture time |
| [`AimController`](#aimcontroller) | bearing -> turn power, safely |

```mermaid
flowchart LR
    W["WebcamTags"] --> S(["TargetSource"])
    L["LimelightTags"] --> S
    N["next year's camera"] -.-> S
    S --> O["TargetObservation<br/>bearing (+ = left), distance, time"]
    O --> A["AimController -> drive turn"]
    O --> T["turret.aimAtTarget(target, onTurret)"]
    O --> D["launcher.setDistance(distance)"]
```

**2026-27 note:** BIOBUZZ tags are on the HIVE cells and the cells MOVE, so they are good for
aiming but not for finding the robot's position on the field. SDK 12 also adds tag
**clusters** (groups of tags with one aiming point); `WebcamTags` reports a cluster as a target
with id `TargetObservation.CLUSTER` and its name.

---

## TargetSource

| Method | What it does |
| --- | --- |
| `start()` | start the camera (in INIT; a webcam needs 1-2 s) |
| `update()` | every loop: get the newest picture's targets |
| `targets()` | everything in the newest picture (dropped once older than 500 ms: a camera that stops sending never leaves old targets) |
| `target(id)` / `target(name)` | one target, or null |
| `isWorking()`, `status()` | is it streaming? e.g. "STREAMING 28 fps", "not connected" |
| `close()` / `stop()` | free the camera |
| `addTelemetry(t)` | warning if not working; debug: status and every target |

## WebcamTags

```java
WebcamTags camera = WebcamTags.create(hardwareMap, "Webcam 1")
        .resolution(640, 480)
        .decimation(2)
        .exposure(6, 85);        // optional: short exposure = sharp tags while driving
camera.start();
```

| Method | What it does |
| --- | --- |
| `.resolution(w, h)` | 640x480 to start; higher sees farther but is slower |
| `.decimation(d)` | 1 = slow/far, 2 = default, 3 = fast/near |
| `.liveView(on)` | picture on the Robot Controller screen (costs speed) |
| `.library(lib)` | another tag library (default: this season's, from the SDK) |
| `.exposure(ms, gain)` | manual exposure; `status()` says if the camera refused |
| `.keepTargetsMs(ms)` | how long targets stay in `targets()` without a new picture (default 500) |
| `WebcamTags.convert(detection)` | turns an SDK detection into a TargetObservation |

Better distances: calibrate the camera (the FTC docs explain "camera calibration") or use a
camera the SDK already knows (Logitech C270/C920 at common resolutions).

## LimelightTags

```java
LimelightTags camera = LimelightTags.create(hardwareMap, "limelight")
        .pipeline(0)
        .pollRate(100);
camera.start();
```

Set up an AprilTag (fiducial) pipeline in the Limelight's web page first (http://limelight.local:5801
on the robot network). Set the correct tag size there for distances.

| Method | What it does |
| --- | --- |
| `.pipeline(n)` | which pipeline (also switches at once if already started) |
| `.pollRate(hz)` | results per second |
| `setRobotHeading(deg)` | for the Limelight's MegaTag2 position feature (not needed to aim) |
| `.keepTargetsMs(ms)` | how long targets stay without a new picture (default 500) |
| `getLimelight()` | the SDK object, for color pipelines and more |
| `LimelightTags.distanceInches(pose)` | distance from a 3D pose |

The library flips the Limelight's `tx` sign so that bearing is **positive to the LEFT**, like
everywhere else. The picture time includes the Limelight's own capture and processing time, so
"how old is this picture" is honest. The same picture is not reported twice.

## TargetObservation

| Field / method | Meaning |
| --- | --- |
| `id`, `name` | tag id (`CLUSTER` = -1 for clusters) and name |
| `bearingDeg` | how far to turn to face it; **+ = it is to the left** |
| `distanceIn` | inches along the FLOOR from the camera (height difference ignored), or NaN if unknown. Same meaning for both cameras, so a shot table works with either |
| `timeNanos` | when the picture was taken |
| `ageMs(now)`, `isFresh(now, maxAgeMs)` | how old; never aim with an old picture |
| `hasBearing()`, `hasDistance()` | was it worked out? |

## AimController

```java
AimController aim = new AimController(0.02, 0.003)
        .deadband(1.5)      // close enough
        .maxPower(0.5)
        .maxAgeMs(150)
        .offset(0);         // aim to the side of the target (camera not centered on the shooter)
drive.drive(forward, strafe, aim.turnPower(camera.target(20)));   // ready for any drive's turn
```

`turnPower` is **clockwise positive**, the same as every drive's `turn` argument, so it plugs straight
in. `ccwPower` gives the same power counterclockwise positive, for your own heading math.

Gives **0** when there is no target, the picture is old, or there is no bearing: the robot never
turns toward something it cannot see now. The D part only changes on a NEW picture, so a repeated
picture never fakes motion. `isAimed()`, `reset()` (call when the driver lets go of the aim button).

For a turret, use `turret.aimAtTarget(target, cameraOnTurret)`: it uses only fresh pictures, each
picture once (an old bearing used again would push the turret further and further), and holds still
when there is no target. See [launcher.md: Turret](launcher.md#turret).
