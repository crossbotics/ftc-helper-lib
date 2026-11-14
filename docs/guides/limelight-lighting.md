# Setting up Limelight lighting pipelines

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** a team with a Limelight 3A that aims with AprilTags. **You need:** the Limelight, its USB-C
cable, a laptop, and a printed AprilTag group (or the field).

Every place has different light: a bright gym, a dim classroom, spotlights at an event. A camera
setting that finds the tags well at school can miss them at an event. So you save three **pipelines**
on the Limelight (a pipeline is one saved set of camera settings, by number). One is for your usual
light, one for bright light and one for dim light. In INIT the robot tries each one, and keeps the one that
sees the tags best ([the lighting test](../modules/vision.md#venue-lighting)).

Reference: [Camera in the robot module](../reference/parts/camera.md), [LimelightTags](../modules/vision.md).
Limelight's own docs: [Limelight 3A Quick-Start](https://docs.limelightvision.io/docs/docs-limelight/getting-started/limelight-3a),
[Tracking AprilTags](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltags).

## 1. Connect the Limelight to a laptop

1. Plug the Limelight into the laptop with its USB-C cable (the laptop powers it).
2. Wait until its status light is green (about 15-20 seconds).
3. In a browser, open **http://limelight.local:5801**. You can also open Limelight's Hardware Manager app, scan,
   and double-click the Limelight. You see the live camera picture and the settings.
4. First time only: in the **Settings** tab, set your team number, then click **Restart Vision Client**.

The Limelight saves changes by itself. It has no "save" button.

## 2. Pipeline 0: your usual light

Pick pipeline **0** in the list at the top of the page. Then:

| Tab | Setting | Set it to | Why |
| --- | --- | --- | --- |
| Input | Pipeline Type | **Fiducial Markers** | "fiducial" = AprilTag |
| Input | Resolution | the highest one | the library needs each tag's 3D position (its distance). More pixels give a steadier one |
| Input | Black Level | **0** | Limelight's starting value |
| Input | Sensor Gain | **15** | Limelight's starting value |
| Input | Exposure | see below | |
| Standard | Family | **AprilTag Classic 36h11** | the game's tags (manual 9.9) |
| Standard | Marker size | **82.55 mm** | BIOBUZZ tags are 3.25 in (manual 9.9). A wrong size gives a wrong distance, and then the shot table picks the wrong speed |
| Standard | ID filter | **empty** (or 30-45) | the library aims at the middle of a CELL's 4 tags and needs at least 2 of them. So do NOT filter to one tag |
| Advanced | Full 3D | **on** | without it the Limelight gives no 3D position, and the distance reads NaN |

**Exposure** is how long each picture collects light. Hold a tag group 4-6 ft from the camera, at the
angle the robot will see it from. In BIOBUZZ the tags are under the CELLs, facing down, so the camera
looks up at them. Start with Exposure low. Raise it until the 4 tags have steady boxes around them
in the picture. Then move the tag a little. When the boxes drop out while it moves, the picture is
blurred. Then lower Exposure a bit and raise Sensor Gain instead. **Lower exposure = less blur but a darker
picture. More gain = a brighter picture, but grainier**, and too much gain makes the distance jump.

## 3. Pipeline 1 (bright) and pipeline 2 (dim)

Set up pipelines **1** and **2** the same way as pipeline 0 (every row of the table). Change only:

- **Pipeline 1, bright light** (sun through windows, event spotlights): **lower Exposure**, so the white
  parts of the tags do not wash out. When bright lights or windows still show as big white spots, raise
  **Black Level** a little (it darkens the picture).
- **Pipeline 2, dim light** (a dark classroom, the back of a hall): **higher Exposure**, and more
  **Sensor Gain** when it is still dark.

Test each one where you can: lights on and off, blinds open and closed. Each should keep steady boxes
on the tags in its own kind of light.

**Write the numbers down** (in the engineering notebook, or a text file in the robot project): each
pipeline's Exposure, Sensor Gain and Black Level. A Limelight software update **erases the
pipelines**. Limelight's docs say to back them up before you update. With the numbers, you can make
them again fast.

## 4. Tell the robot

In `Robot.java`, list the pipelines. Put your usual one first, because the first one wins a tie:

```java
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
```

Put the Limelight back on the robot, in the Control Hub's blue USB 3.0 port. The first time, do this on the
Driver Station: **Configure Robot**, **Scan**, and rename the new "Ethernet Device" to `limelight`
(the name in `Robot.java`).

## 5. At INIT, and at an event

Put a tag group in view at INIT. The spot where the robot starts the match is fine. The Driver Station shows
the result, for example `limelight lighting : pipeline 2 (9 of 10 pictures saw tags)`. When the OpMode starts (▶), the test
stops, and the camera keeps the best pipeline so far.

**At an event:** in the queue (or on the practice field), INIT once with the robot facing a CELL's tags.
Under the `limelight lighting` line, the Driver Station shows two lines. Copy them into
`Tunables.Limelight`:

```java
public static double pipeline = 2;
public static boolean autoLighting = false;
```

Put them in with the laptop, and **install the app on the robot again**: the camera reads these numbers at INIT.
Then every match at that event uses that pipeline, with no test. Back at school, set `autoLighting = true`
again. **Copy tuned values back to `Tunables.java`:** a value changed live (in the tuning menu) is only on
this Control Hub. The file is the record.

## If it does not work

| The Driver Station says | What to do |
| --- | --- |
| `no pictures from pipeline 2 (missing?)` | the Limelight has no such pipeline, or its type is not Fiducial Markers. Set it up (step 3), or take it out of `autoPipelines(...)` |
| the distance is NaN | **Full 3D** is off in that pipeline, or the marker size is missing |
| the distance is wrong by the same share at every distance | the marker size is wrong. Set it to 82.55 mm |
| no pipeline sees the tags | the camera cannot see under the CELL from where the robot is. Aim the Limelight up more, or test at the spot the robot shoots from |
| `limelight` is not in the configuration | Configure Robot, Scan, rename the Ethernet Device (step 4) |

**Live view on the robot:** you see the live picture above only with the Limelight plugged into the laptop. The library
has no live view yet while the Limelight is on the robot. Nobody has tested the Limelight's page through the
Control Hub yet.
