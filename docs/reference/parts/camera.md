# Camera

A Limelight, a webcam or a HuskyLens. It finds the alliance's goal by its AprilTags. Learn it step by step:
[Add a camera](../../add-to-robot/07-camera.md).

## In Robot.java

```java title="Robot.java"
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
camera = add(Camera.webcam("Webcam 1"));
camera = add(Camera.huskyLens("huskylens").tag(1, 34).tag(2, 35).goal("BLUE SCORING", "RED SCORING"));
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Camera.limelight(name)` | **yes**, one of the three | a Limelight | - |
| `Camera.webcam(name)` | **yes**, one of the three | a webcam | - |
| `Camera.huskyLens(name)` | **yes**, one of the three | a HuskyLens | - |
| `.goal(blueGroup, redGroup)` | recommended | each alliance's HIVE, like `goal("BLUE SCORING", "RED SCORING")`. Aiming uses it ([The goal](#the-goal-is-our-hives-up-cell)) | `goal()` gives null, and nothing aims |
| `.autoPipelines(n...)` | optional | a Limelight: the pipelines the lighting test tries at INIT ([Venue lighting](#venue-lighting)) | no test: `pipeline` |
| `.autoExposure()` | optional | a webcam: the lighting test tries auto, 3, 6 and 12 ms | no test: `exposureMs` |
| `.autoExposure(ms...)` | optional | a webcam: the exposures the test tries, like `autoExposure(6, 3, 12)` | - |
| `.tag(taughtNumber, tagNumber)` | HuskyLens | the number you taught it, and the tag's real number. A HuskyLens says the taught number | - |
| `.colors()`, `.objects()` | HuskyLens, optional | it looks for taught colors, or one object, instead of tags | tags |
| `.label(taughtNumber, name)` | HuskyLens, optional | a name for `target(name)` | - |
| `.upsideDown()` | webcam, optional | the webcam is mounted upside down: left is left again, and the tags read right side up ([Upside down](#a-webcam-upside-down)) | an upside-down webcam aims the wrong way, and it takes the down CELL for the goal |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | a warning at INIT (below) |

The camera starts in INIT. When the robot configuration has no camera of that name, INIT shows a warning and
goes on. The OpMode runs, and the camera sees nothing.

### The goal is our HIVE's up CELL

`goal("BLUE SCORING", "RED SCORING")` names each alliance's HIVE.

- `goal()` gives that HIVE's CELL whose tags read right side up now: the up CELL, the one you can score in
  ("RED SCORING" or "RED AUDIENCE": a tip swaps them).
- A CELL whose tags read upside down (a down CELL, for example seen from under the HIVE) cannot be the goal.
  Then `goal()` gives null, and the Driver Station says
  `Goal : not seen (RED SCORING is upside down (a down CELL))`.
- Aiming, the turret, the shot table's distance, LOCKED and the launcher's "aimed" checks all use `goal()`.
- `target("RED SCORING")` still gives that group, upside down or not.
- How the camera tells up from down: [vision.md](../../modules/vision.md#taggroup).

### A HuskyLens

- It says the number you taught it. List each one with its tag's real number: `tag(taughtNumber, tagNumber)`.
- Measure `hfovDeg` (its view angle). Write `sizeIn` for a rough distance: the shot table needs it.
- INIT says what is missing.
- It has no lighting test.
- Its limits, and how to teach it: [vision.md](../../modules/vision.md#huskylenscamera).

### Venue lighting

Each venue has different light: a bright gym, a dim classroom, spotlights at an event. A camera setting that
finds the tags at school can miss them at an event. So the camera can test a few settings in INIT and keep the
one that sees the most AprilTags ([how the test works](../../modules/vision.md#venue-lighting)).

The settings it tests depend on the camera:

| Camera | What it tests | Turn it on in Robot.java |
| --- | --- | --- |
| Limelight | its **pipelines**. A pipeline is a set of camera settings that you save on the Limelight by number. Save one for your usual light, one for bright light and one for dim light ([how](../../guides/limelight-lighting.md)) | `.autoPipelines(0, 1, 2)` |
| Webcam | exposures: how long each picture collects light | `.autoExposure()` (auto, 3, 6 and 12 ms) |
| HuskyLens | nothing: it has no lighting test | - |

```java title="Robot.java"
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
camera = add(Camera.webcam("Webcam 1").autoExposure());          // or your own list: autoExposure(6, 3, 12)
```

**At an event:**

1. In the queue, press INIT with the robot facing a tag. The test stops at ▶ and keeps the best setting so
   far, so a tag must be in view.
2. The Driver Station shows the result, for example
   `limelight lighting : pipeline 2 (9 of 10 pictures saw tags)`. While the camera sees no tag, this line is a
   warning.
3. It also shows two lines for `Tunables.java`. Copy them into the camera's section (`Tunables.Limelight`).
   Then every match at this venue uses that setting, with no test:

   ```java title="Tunables.java"
   public static double pipeline = 2;
   public static boolean autoLighting = false;
   ```

   A webcam shows `public static double exposureMs = 6;` instead of `pipeline`.
4. Back home, set `autoLighting = true` again.

**Do the test again** with `relight()` when the robot started with no tag in view, or the light changed
([Buttons to add](#buttons-to-add)).

- In the match it takes up to 5 s (or `lightingAfterStartMs`, when you set it).
- The camera sees no targets until it is done, so the aim stops too.
- The TeleOp screen shows `Webcam 1 lighting: ...` from then on.
- If no setting sees a tag, it keeps the setting it had.
- With no `autoExposure` or `autoPipelines`, or with `autoLighting = false`, it does nothing. It gives
  false, and the TeleOp screen says so for 5 s.

### A webcam upside down

A webcam mounted upside down sees an upside-down picture. Left and right swap, so whole-robot aim turns the
wrong way. Every tag reads upside down, so the down CELL looks like the up CELL: the camera aims at the wrong
one. Add `.upsideDown()`:

```java title="Robot.java"
camera = add(Camera.webcam("Webcam 1").upsideDown().goal("BLUE SCORING", "RED SCORING"));
```

The library then turns each reading back: left is left, and the up CELL is the goal again. The live view on
the Driver Station stays upside down. In the simulator robot file, `:sim:fromRobot` writes `roll=180` on the
camera's line. A robot file you wrote yourself needs it too ([Cameras](../robot-file.md#cameras)).

A Limelight has its own setting on its web page (Settings, Orientation): `upsideDown()` is for a webcam only,
and INIT stops if you add it to a Limelight or a HuskyLens.

### Live view

Use it to check the brightness.

- A webcam shows its picture in the PRACTICE profile. INIT says
  `Webcam1 live view: on (PRACTICE): Driver Station menu > Camera Stream, in INIT`.
- A Limelight's live picture: plug it into a laptop and open http://limelight.local:5801. Nobody has tested
  it through the Control Hub yet.

## In Tunables.java

<!-- tunables: Camera -->
The section's name is the camera's name in CamelCase: `limelight` reads `Limelight`, `Webcam 1` reads
`Webcam1`. The camera reads them once, at INIT. They are not in the tuning menu: change them in
`Tunables.java` and install again. Leave them out to keep the defaults.

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-camera -->
```java title="Tunables.java"
public static class Limelight {                                 // "limelight" in the configuration
    public static double pipeline = 0;                          // a Limelight: the pipeline to use (0)
    public static boolean autoLighting = true;                  // try a few settings at INIT, keep the best (true)
    public static double pitchDeg = DEFAULT;                    // its tilt up, degrees (0 = level)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `liveView` | a webcam: show its picture (the Driver Station's Camera Stream, in INIT) in the PRACTICE profile; never in COMPETITION (it costs speed) | true/false | true | no | none | `false` to save speed in practice too |
| `pipeline` | a Limelight: the pipeline to use | number | 0 | no | the lighting test at INIT | the test found a better one at this venue |
| `autoLighting` | try a few settings at INIT and keep the best (`autoPipelines` / `autoExposure`) | true/false | true | no | none | `false` to skip the test at a venue you know |
| `exposureMs` | a webcam: a fixed exposure when the test is off (-1 = the camera's own) | ms | -1 | no | the lighting test at INIT | the test found a better one |
| `gain` | a webcam: the gain for a fixed exposure (-1 = the camera's highest) | number | -1 | no | test | pictures are too dark or noisy |
| `lightingSettleMs` | the test: wait after each switch | ms | 300 | no | none | pictures are still changing |
| `lightingPictures` | the test: pictures per setting | count | 10 | no | none | results vary |
| `lightingMinPictures` | the test: pictures with tags a setting needs | count | 3 | no | none | a bad setting wins |
| `lightingAfterStartMs` | the test: keep trying this long after ▶ (no targets meanwhile) | ms | 0 | no | none | only for a special case |
| `pitchDeg` | a webcam or Limelight: its tilt up; then bearings and distances are along the floor ([CameraTilt](../../modules/vision.md#cameratilt)) | degrees (+ = up) | 0 (level) | no | measure (a level app on the camera's face) | the camera is tilted; after a change, make the shot table again |
| `hfovDeg` | a HuskyLens: its view angle, left edge to right edge of the picture | degrees | 60 (a guess) | no | measure ([how](../../modules/vision.md#huskylenscamera)) | always: measure yours |
| `sizeIn` | a HuskyLens: the real size of what it looks at, for a rough distance (none: no distance) | inches | none | no | measure | the longest side it shows: a tag's black square, a ball's width |

`pitchDeg` is also the simulator robot file's `pitch`.

### CameraFix

<!-- tunables: CameraFix -->
Section `CameraFix` (a fixed name). **ALPHA: nobody has tested it on a robot. Not for BIOBUZZ:** its tags
move with the HIVE. It works only with `cameraFix(camera)` in Robot.java. It corrects the odometry's x and y
from the camera:
[Camera fix (alpha)](../../modules/odometry.md#camera-fix-alpha).

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `enabled` | the camera fix on or off | true/false | false | no | none | to try it (not in BIOBUZZ) |
| `turnDegBlue` | the turn from the Limelight's field frame to ours, on BLUE | degrees | 0 | no | Camera Fix Check | the check says another turn |
| `turnDegRed` | the same on RED | degrees | 0 | no | Camera Fix Check | the same |
| `maxAgeMs` | the fix skips a picture older than this | ms | 100 | no | none | only for a special case |
| `maxSpeedInPerSec` | faster than this: no fix | inches per second | 20 | no | none | only for a special case |
| `maxTurnDegPerSec` | turning faster than this: no fix (the heading lags in the picture) | degrees per second | 30 | no | none | only for a special case |
| `maxJumpIn` | the fix refuses a bigger difference (a bad picture) | inches | 12 | no | none | only for a special case |
| `blend` | how much of the difference the fix applies, per picture | 0-1 | 0.2 | no | none | the pose jumps (less) |
| `minTags` | the fix skips a picture with fewer tags | count | 2 | no | none | only for a special case |

## Commands

| Command | What it does |
| --- | --- |
| `relight()` | does the lighting test again. It gives false when there is no test to do |

| Question | What it tells you |
| --- | --- |
| `goal()` | the alliance's goal now, or null ([The goal](#the-goal-is-our-hives-up-cell)) |
| `target(name)`, `target(id)` | that group or tag, or null when the camera does not see it |
| `source()` | the [`TargetSource`](../../modules/vision.md) inside |
| `lighting()` | the lighting test itself |
| `liveViewOn()` | whether the webcam's live view is on |
| `isLimelight()`, `isWebcam()`, `isHuskyLens()` | which kind it is |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

## Buttons to add

The camera has no usual buttons. To do the lighting test again, put `relight()` on a button in
`Controls.java`. The driver aims the camera at a tag, then holds the button for 0.5 s. The hold stops a bump
from starting it:

```java title="Controls.java"
g1.y.heldFor(0.5, robot.camera::relight).rumble().says("camera: lighting test again (aim at a tag)");
```

Robot 1 uses this line, with one driver. **With two drivers:** gamepad 1's Y is the library's field-centric
button (a double-tap), so INIT stops on this line. Put it on the operator's BACK instead:
`g2.back.heldFor(0.5, robot.camera::relight)`. The driver aims at a tag, and the operator holds BACK.

[How to read a button line](../controls.md#reading-a-button-line).

---

**Look it up:** [`Camera` in the Javadoc](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Camera.html)
(every method). **The other parts:** [Reference](../README.md).
