# Add a turret

A part that turns the launcher to aim, inside its limits, by hand or following the goal.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot: a turret turned by a motor with an encoder, and hard stops at both ends. In the robot
  configuration, call the motor `turret`.
- To follow the goal: a camera ([Add a camera](07-camera.md)).

## 1. What it is

A **turret** turns to an angle and holds it. With a camera, it follows the goal by itself.

- **Angles are in degrees.** 0 is straight forward. Counterclockwise (to the left, seen from above) is +.
- **It never turns past its range** (`min` to `max`). When the goal is outside, it waits at the limit, and
  the Driver Station says `goal out of turret range`.
- **It needs a known 0.** Without a switch, 0 is where the turret points at INIT. Put it straight forward
  by hand before every INIT. A home switch finds 0 by itself at ▶.

**Hard stops and software limits:** `min` and `max` are software limits. They are your wires' limits too.
Software can be wrong, so the turret also needs physical blocks at both ends.

Teams build turrets in many ways. This page adds a motor turret. For a servo, a CR servo, a slip ring or a
home switch, see [Your turret is different](#your-turret-is-different) below.

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field goes at the top of the class (`public Turret turret;`). The line goes in
`define()`, after the camera line.

<!-- recipe: turret-follows-camera -->
```java title="Robot.java"
// The camera knows the goal of each alliance (BLUE first, then RED).
camera = add(Camera.limelight("limelight").goal("BLUE SCORING", "RED SCORING"));
// "turret" = the motor's name in the robot configuration. 5 motor turns per turret turn.
// follows(camera): it follows the goal from ▶, but never past Tunables.Turret min and max.
turret = add(Turret.motor("turret", "gobilda-5203-117").gearRatio(5).follows(camera));
```

- `.gearRatio(5)`: how many turns the motor makes for one turn of the turret. Count the teeth: turret gear
  / motor gear. A 20-tooth gear on a 100-tooth ring is 5.
- `.follows(camera)`: the turret follows the goal from ▶. **With no camera yet:** leave out the camera line and
  `.follows(camera)`. Add them on [Add a camera](07-camera.md).
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

### Tunables.java: its numbers

**Where:** `Tunables.java`, a new section. The section's name is the device's name in CamelCase:
`"turret"` reads `Tunables.Turret`.

<!-- recipe: tunables-turret -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double startAngle = DEFAULT;                  // where you set it by hand before INIT (0)
    public static double kP = DEFAULT;                          // push per degree off (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = DEFAULT;                    // the most power (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double cameraOffset = DEFAULT;                // aims a little to one side: Aim Tuner (0)
}
```

- `MEASURE` means "find this on the robot". INIT stops until you write a number. **Why:** a guessed range
  can pull the wires off.
- `DEFAULT` means "use the library's starting value". You tune `kP` and `kD` later (step 4).
- `cameraOffset` comes from the Aim Tuner, after the camera.

### Controls.java: its buttons

**Where:** `Controls.java`, inside `buttons(...)`. A part has no buttons until you add them.

<!-- recipe: buttons-turret -->
```java title="Controls.java"
g2.rightStickX.use(v -> robot.turret.manual(-v)).says("by hand (right = clockwise)");
g2.rightStickButton.toggle(robot.turret.followMode()).rumble().says("follow the goal on/off");   // only with follows(...)
```

The operator's right stick turns the turret by hand. A click of the same stick switches following on and
off: 2 buzzes = on, 1 buzz = off.

**With one gamepad** (`oneDriver = true`): the stick click is also the library's field-centric button. Move
field-centric first ([Turret: Buttons to add](../reference/parts/turret.md#buttons-to-add)).

### Actions.java and autos: use it in steps

You need no line here for a turret that follows the goal. The launcher can wait until the turret is
aimed. Add this to the launcher's line in `Robot.java`:

**Where:** `Robot.java`, at the end of the launcher's line, before the `);`.

```java title="Robot.java"
.requireReady("turret aimed", () -> turret.isAimed())
```

For a turret that follows the goal, "aimed" means **at the goal now**. The robot sees the goal (camera or
odometry), and the turret is in range and on it. When the robot loses the goal, the turret is not aimed. So
the launcher never fires blind.

To point it at an angle in an action, use `r.turret.aimAt(45)`. It stops following.

### Your turret is different

**A home switch** (a touch sensor or a magnetic switch). At ▶ the turret turns slowly until it finds the
switch. Then its angle is right, even when someone turned it by hand.

<!-- recipe: turret-home-switch -->
```java title="Robot.java"
// "turret", "turret_home" = the motor's and the home switch's names in the robot configuration.
// At ▶ the turret turns slowly until it finds the switch.
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
        .homeSwitch("turret_home")
        .follows(camera));
```

<!-- recipe: tunables-turret-switch -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    public static double min = MEASURE;                         // furthest right, degrees (Motor Finder)
    public static double max = MEASURE;                         // furthest left, degrees: 0 = forward, + = left
    public static double homeAngle = MEASURE;                   // where the switch closes (turret-setup.md)
    public static double homingPower = DEFAULT;                 // how slowly it looks for the switch (0.2)
    public static double homingTimeout = DEFAULT;               // the most seconds it looks (5)
}
```

**A servo turret.** A servo goes to a position by itself. It uses two servo positions and the turret's
angle at each. It has no PID numbers and no homing.

<!-- recipe: turret-servo -->
```java title="Robot.java"
// "turret" = the servo's name in the robot configuration. Numbers: Tunables.Turret.
// fullRange(): the servo's whole turn. Turn it on BEFORE you measure posA and posB.
turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange().follows(camera));
```

<!-- recipe: tunables-turret-servo -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    // Two servo positions and the turret's angle at each: 0 = straight forward, counterclockwise
    // (left) +. Its range is between the two angles.
    public static double posA = MEASURE;                        // servo position at one end (Servo Finder)
    public static double angleA = MEASURE;                      // the turret's angle there (an angle app)
    public static double posB = MEASURE;                        // servo position, other end (Servo Finder)
    public static double angleB = MEASURE;                      // the turret's angle there (an angle app)
    public static double travelSec = DEFAULT;                   // posA to posB, seconds (0.6, or the servo's)
    public static double startAngle = DEFAULT;                  // sent here at ▶ (0)
}
```

**A slip ring** (the turret turns round and round). A CR servo turns it, and an absolute encoder says
where it points.

<!-- recipe: turret-slip-ring -->
```java title="Robot.java"
// A CR servo turns it, an absolute encoder says where it points, continuous(): no limits.
// "turret", "turret_pos" = the servo's and the encoder's names in the robot configuration.
// .part(...): which servo it is (optional, recommended).
turret = add(Turret.crServo("turret").part("axon-max-mk2").analogEncoder("turret_pos").continuous()
        .follows(camera));
```

<!-- recipe: tunables-turret-slip-ring -->
```java title="Tunables.java"
public static class Turret {                                    // "turret" in the configuration
    // No min and max: it turns round and round.
    public static double kP = 0.015;                            // push per degree off, CR servo (0.02)
    public static double kD = DEFAULT;                          // brake near the target (0.001)
    public static double maxPower = 1.0;                        // a CR servo's full speed (0.8)
    public static double tolerance = DEFAULT;                   // "aimed" within this, degrees (1.0)
    public static double encoderOffset = MEASURE;               // encoder reading, pointing forward
}
```

[Setting up a turret](../guides/turret-setup.md) has every way to build a turret, with the lines for each.

**It can hit another part** (a lift, an arm): write an interlock rule. While the lift is up, the turret
stays out of its way ([Interlock](../reference/parts/interlock.md)).

## 3. Find its numbers

INIT stops on `MEASURE`.

1. **`min` and `max`:** turn the turret by hand to where the wires are tight, both ways. Take a few
   degrees less. Measure the angle from straight forward with a phone angle app.
2. **`homeAngle`** (a home switch): remove the `.homeSwitch(...)` line. Put the turret straight forward,
   press INIT, then ▶. Turn it with the stick until the switch clicks. Read the angle on the Driver
   Station (`Turret : 43 deg`). That is `homeAngle`. Then add the line again.
3. **`posA`, `posB`, `angleA`, `angleB`** (a servo turret): the [Servo Finder](../tuning/03-servo-finder.md)
   gives the two positions. Measure the turret's angle at each with a phone angle app.
4. **`encoderOffset`** (an absolute encoder): put the turret straight forward, press INIT, and read the
   encoder's degrees.
5. **`cameraOffset`:** the [Aim Tuner](../tuning/12-aim-tuner.md), after the camera.

## 4. Check it, then tune it

**Check it:**

1. Put the turret straight forward by hand. Press INIT. Check that no warning names the turret. Press ▶.
2. Push gamepad 2's right stick to the right. The turret must turn **clockwise**. If it turns the other
   way, add `.reversed()`.
3. Turn it to each end with the stick. It must stop before the hard stops.
4. With a camera: click the right stick to turn following on. Point the robot at the goal. The Driver
   Station must say `following: camera`, then `AIMED`.

**Tune it** with [Live tuning](../tuning/16-live-tuning.md), while the robot runs. Change one number, then
let the turret follow the goal:

1. **kP:** raise it until the turret aims fast. If it shakes, go back a step.
2. **kD:** if it goes past the goal and comes back, raise it a little at a time.
3. **kI:** only if it stops a degree short, a very small number.

Then put the tuned numbers in `Tunables.Turret`, in place of `DEFAULT`.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: `Tunables.Turret.min = MEASURE` | Not measured yet. Do step 3 |
| The turret turns away from the goal | Add `.reversed()` to the turret in `Robot.java` |
| Every angle is off by the same amount | It did not point at `startAngle` at INIT. Use a mark, or add a home switch |
| The angles are wrong | `.gearRatio(...)` is wrong (count the teeth again), or it did not point forward at INIT |
| `goal out of turret range` | The goal is past `min` or `max`. Turn the robot |
| `goal out of turret range (behind the robot: holding)` | The odometry says the goal is far behind. The turret holds where it points. Turn the robot toward the goal |
| `homing failed: switch not found` | Homing turns the wrong way: set `homeDirection` (+1 or -1) |
| INIT: `Turret : no angle from a program before: ...` | Turn it to `startAngle` by hand, then INIT again |
| It is aimed, but the shots go wide by the same angle | The camera is beside the launcher. Run the Aim Tuner (`cameraOffset`) |

## Done when

- INIT shows no warning for the turret.
- The stick turns the turret, and it stops before both hard stops.
- `min`, `max` and every other `MEASURE` in `Tunables.Turret` are numbers.
- With a camera: the turret follows the goal and shows `AIMED`.

---

**Next:** [Add a camera](07-camera.md).

**Look it up:** [Turret](../reference/parts/turret.md): every setting, number and command.
