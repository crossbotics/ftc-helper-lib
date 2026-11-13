# Camera lighting

Set up the camera so that it finds the AprilTags in every kind of light.

**In Tune and check:** no. The camera tests the light by itself, at each INIT. This page sets it up.
**You need:** a camera in Robot.java, AprilTags on the field (or printed ones), and for a Limelight, a
laptop and the Limelight's USB-C cable.

## Why the light matters

Each place has different light: a bright gym, a dim classroom, spotlights at an event. A camera setting that
finds the tags at school can miss them at an event. Then the robot cannot aim, and the Shot Tuner gets no
distance.

So the camera tests a few settings at INIT, and keeps the one that sees the most tags. Do this setup
before the Aim Tuner and the Shot Tuner: both need a camera that sees the tags well.

| Camera | What it tests | Turn it on in Robot.java |
| --- | --- | --- |
| Limelight | its **pipelines**: sets of camera settings that you save on the Limelight by number | `.autoPipelines(0, 1, 2)` |
| Webcam | exposures: how long each picture collects light | `.autoExposure()` (auto, 3, 6 and 12 ms) |
| HuskyLens | nothing: it has no lighting test | - |

## Set it up

### A Limelight

1. Save 3 pipelines on the Limelight: one for your usual light, one for bright light, and one for dim
   light. The [Limelight lighting guide](../guides/limelight-lighting.md) has the steps, on a laptop.
2. Add `.autoPipelines(0, 1, 2)` to the camera's line.

**Where:** `Robot.java`, inside `define()`, the camera's line.

```java title="Robot.java"
camera = add(Camera.limelight("limelight").autoPipelines(0, 1, 2).goal("BLUE SCORING", "RED SCORING"));
```

### A webcam

Add `.autoExposure()` to the camera's line.

**Where:** `Robot.java`, inside `define()`, the camera's line.

```java title="Robot.java"
camera = add(Camera.webcam("Webcam 1").autoExposure());   // or your own list: autoExposure(6, 3, 12)
```

## Check it

1. Turn the robot so that the camera sees a tag. Press INIT.
2. Read the lighting line on the Driver Station, for example
   `limelight lighting : pipeline 2 (9 of 10 pictures saw tags)`. When the camera sees no tag, this line
   is a warning.
3. Look at the camera's picture: [See what the camera sees](12-aim-tuner.md#see-what-the-camera-sees).

## At an event

1. In the queue, press INIT with the robot facing a tag. The test stops at ▶ and keeps the best setting so
   far, so a tag must be in view.
2. The Driver Station shows the result, and two lines for `Tunables.java`. Copy them into the camera's
   section (`Tunables.Limelight`). Then every match at this venue uses that setting, with no test.

   **Where:** `Tunables.java`, inside the camera's section.

   ```java title="Tunables.java"
   public static double pipeline = 2;
   public static boolean autoLighting = false;
   ```

   A webcam shows `public static double exposureMs = 6;` in place of `pipeline`.
3. Back home, set `autoLighting = true` again.

## Fix a mistake

- **The robot started with no tag in view:** the test kept the setting it had. Turn to a tag and INIT
  again. In a match, a button can run the test again: `relight()`
  ([Buttons to add](../reference/parts/camera.md#buttons-to-add)).
- **Back home, the camera misses tags:** check that `autoLighting` is `true` again (step 3 above).

## Done when

- At INIT, facing a tag, the lighting line shows a setting and no warning.
- A Limelight has its 3 pipelines saved.

---

**Next:** [Aim Tuner: camera offset](12-aim-tuner.md).

**Look it up:** [Camera: Venue lighting](../reference/parts/camera.md#venue-lighting),
[how the test works](../modules/vision.md#venue-lighting).
