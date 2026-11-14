# Add lights

A colored light that tells the drivers what the robot knows: ready to fire, full, a mode on, the last 20 seconds.

**You need:**

- A robot that drives, with its five files ([The five files](../five-files/README.md)).
- On the robot, one of these two:
  - a goBILDA RGB Indicator Light on a servo port. In the robot configuration it is a "Servo". Call it
    `light`.
  - a REV Blinkin LED Driver with an LED strip. In the robot configuration it is a "REV Blinkin LED
    Driver". Call it `blinkin`.

## 1. What it is

A **light** shows one color at a time. You write **rules** in `Controls.java`: "show this color when this
is true".

- **The drivers watch the robot, not the Driver Station.** A light tells them "ready" without a look at the
  screen.
- **The first true rule wins.** Put the most important rule first.
- **A flash beats every rule** while it lasts: one short flash for each piece fired, or from a button or an
  action.
- **The alliance shows at INIT, with no code.** Before the drivers pick the alliance, the light blinks
  white once a second. After the pick, it is solid RED or BLUE. At ▶ it flashes the alliance for 1 s, then
  your rules start.

**Keep your rules away from red and blue.** Then nobody reads "intake on" as "BLUE alliance". The examples
use purple, orange, green, yellow and white.

**Game rules:** the referees can switch off a light that flashes faster than about 5 times a second
(R202 J). So the library never changes the light faster than every 0.1 s, and blinks at most 5 times a
second. Do not shine a light onto the field (G302).

## 2. Add it to the five files

### Robot.java: one line

**Where:** `Robot.java`. The field `public Lights lights;` goes at the top of the class. The line goes in
`define()`, after the drive.

<!-- recipe: light-add -->
```java title="Robot.java"
// At the top of Robot.java: import com.crossbotics.ftchelper.robot.Lights;
// A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
lights = add(Lights.gobilda("light"));
// Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
//   lights = add(Lights.blinkin("blinkin"));
```

- Use one of the two lines. The name in quotes is the name in the robot configuration.
- When a name is red, click it and press Alt+Enter (Option+Enter on a Mac) to add the import.

**When a Blinkin color looks wrong on your strip:** give that color another Blinkin pattern. Your rules still say
`LightColor.BLUE`. A goBILDA light ignores this line.

**Where:** `Robot.java`, in `define()`, below the lights line.

<!-- recipe: light-blinkin -->
```java title="Robot.java"
// At the top of Robot.java: import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
// A REV Blinkin LED Driver ("REV Blinkin LED Driver" in the robot configuration):
lights = add(Lights.blinkin("blinkin"));
// Plain blue too dark on your strip? Show another Blinkin pattern for BLUE.
lights.blinkinPattern(LightColor.BLUE, RevBlinkinLedDriver.BlinkinPattern.BLUE_VIOLET);
```

The pattern names are on REV's Blinkin page.

### Two lights

A robot can have two lights (or more). Pick one of these ways:

| You want | Do this |
| --- | --- |
| Both lights show the same thing | One part that drives both: `.alsoLight(...)` |
| Two goBILDA lights show the same thing, with no code | Plug both into one servo port with a servo Y-cable. Robot.java stays one light |
| Each light shows something different | Two parts, each with its own rules |

**Both lights show the same:**

**Where:** `Robot.java`, in `define()`, in place of the lights line.

<!-- recipe: light-two-synced -->
```java title="Robot.java"
// "light" and "light_right" = the two lights' names in the robot configuration (the same kind).
// Both show the same color, blinking and flashes. Write the rules once, for robot.lights.
lights = add(Lights.gobilda("light").alsoLight("light_right"));
```

- Both lights must be the same kind: two goBILDA lights, or two Blinkins.
- Write the rules once, for `robot.lights`. Both lights show the same color, blinking and flashes.

**Each light shows something different:**

**Where:** `Robot.java`. The two fields go at the top of the class. The lines go in `define()`, in place of
the lights line.

<!-- recipe: light-two-separate -->
```java title="Robot.java"
// At the top of the class: public Lights leftLight, rightLight;   Here in define():
leftLight = add(Lights.gobilda("light_left"));
rightLight = add(Lights.gobilda("light_right"));
```

**Where:** `Controls.java`, inside `buttons(...)`. Each light has its own rules and its own `otherwise`.

<!-- recipe: light-two-rules -->
```java title="Controls.java"
// The left light: the launcher. The right light: the intake.
robot.leftLight.show(LightColor.GREEN).when(robot.launcher::isReady);
robot.leftLight.otherwise(LightColor.OFF);
robot.rightLight.show(LightColor.YELLOW).when(robot.intake::isRunning);
robot.rightLight.otherwise(LightColor.OFF);
```

**Why each light needs its own rules:** a rule, a flash and `otherwise` belong to one part. A flash on
`robot.leftLight` does not show on `robot.rightLight`.

### Tunables.java: one switch

**Where:** `Tunables.java`, a new section. You need it only to turn off the alliance at INIT. The section's
name is the device's name in CamelCase: `"light"` reads `Tunables.Light`.

<!-- recipe: tunables-lights -->
```java title="Tunables.java"
public static class Light {                                     // "light" in the configuration
    public static boolean allianceAtInit = true;                // show the alliance in INIT, flash it at ▶ (true)
}
```

`false`: no alliance color at INIT, and your rules show from INIT on.

### Controls.java: the rules

**Where:** `Controls.java`, inside `buttons(...)`. Every file that names a color needs
`import com.crossbotics.ftchelper.robot.LightColor;` at the top. Press Alt+Enter on a red `LightColor` to
add it.

Write the lines in this order: the flashes, then the endgame, then your rules.

**1. A flash for each piece fired, and one from a button:**

<!-- recipe: light-flash -->
```java title="Controls.java"
// A flash beats every rule while it lasts (write it first: it reads that way).
// A short white flash for each piece fired, and one from a button.
robot.lights.show(LightColor.WHITE).forSeconds(0.2)
        .whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
g2.a.press(() -> robot.lights.flash(LightColor.YELLOW)).says("flash the light (signal the partner)");
```

The flash is 0.2 s, so two quick shots are two flashes, not one long one.

**2. Yellow in the last 20 seconds:**

<!-- recipe: light-endgame -->
```java title="Controls.java"
// Yellow, blinking, in the last 20 seconds of TeleOp: time to park (above the other rules: it wins).
robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
```

The time counts from ▶, like the gamepad buzzes. It is about 1 s off from the field clock.

**3. Your rules, one per line:**

<!-- recipe: light-rules -->
```java title="Controls.java"
// One rule per line. The first true rule wins: put the most important first.
// Red and blue are left for the alliance (shown at INIT), so no rule looks like one.
robot.lights.show(LightColor.ORANGE).blinking().when(robot.intake::isPushingOut);  // pushing out
robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);              // at speed: fire
robot.lights.show(LightColor.PURPLE).when(robot.intake::isRunning);             // intake on
robot.lights.otherwise(LightColor.OFF);                                         // none true
```

- Use the parts your robot has. Any true/false works as a rule: a part's method
  (`robot.intake::isRunning`), a mode (`() -> robot.turret.followMode().isOn()`), or your own test
  (`() -> robot.lift.position() > 1000`).
- `otherwise(...)` is the color when no rule is true.

**The alliance colors:** you add nothing, because it is on by default. When your light shows red badly,
pick other colors for it:

<!-- recipe: light-alliance -->
```java title="Controls.java"
// On by default: the alliance at INIT (solid RED or BLUE), flashed for 1 s at ▶.
// Your light shows red badly? Other colors for the alliance at INIT (BLUE, and PURPLE for RED):
robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);
// No alliance color at INIT at all? Delete the line above and write:
//     robot.lights.allianceAtInit(false);
```

A line in `Controls.java` wins over `Tunables.Light.allianceAtInit`.

### Actions.java and autos: a flash when an action is done

You need no line here for the light alone. An action can flash the light as its last step. No action
"claims" the light, so any action may flash it.

**Where:** `Actions.java`, one method per action.

<!-- recipe: light-action -->
```java title="Actions.java"
// In Actions.java (it needs import com.crossbotics.ftchelper.robot.LightColor; at the top).
// The last step: a 1 s white flash says "done" to the drivers. Pick a color your rules do not use.
public static Action shootTwo(Robot r) {
    return Action.named("Shoot two")
            .then(r.launcher::spinUp)
            .waitUntil("at speed", r.launcher::isReady, 3.0)
            .then(() -> r.launcher.fire(2))
            .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
            .then(() -> r.lights.flash(LightColor.WHITE, 1.0));
}
```

A GREEN flash over the GREEN "ready" rule cannot be seen. Pick another color, or a longer time.

## 3. Find its numbers

A light has no numbers to find. The colors come from your rules.

## 4. Check it, then tune it

**Check it:**

1. Press INIT. Before you pick the alliance, the light must blink white once a second.
2. Pick the alliance. The light must be solid RED or BLUE.
3. Press ▶. The light must flash the alliance for 1 s, then show your rules.
4. Make each rule true, one at a time (for example, turn on the intake). The light must show that rule's
   color.
5. Press the flash button (gamepad 2 A). The light must flash yellow.

**Tune it:** nothing to tune. To change a color, change the rule and send the code again.

## Fix a problem

| Problem | Why, and the fix |
| --- | --- |
| INIT stops: no device with that name | The name in `Robot.java` must match the robot configuration exactly |
| The light shows the wrong rule | The first true rule wins. Move the most important rule up |
| A flash does not show | It is the same color as the rule under it. Pick another color |
| Two quick shots give one long flash | Put `.forSeconds(0.2)` before `whenCountGoesUp(...)` |
| Your light shows red or blue badly | Pick other alliance colors: `robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);` |
| A Blinkin color looks wrong | `lights.blinkinPattern(...)` in `Robot.java` |
| The light blinks white in INIT | No alliance is picked yet. TeleOp: press gamepad 1 X or B. Autonomous: pick it in the INIT menu |
| INIT stops: no device named the second light's name | That name is in `.alsoLight(...)` or a second `add(...)` line. Add it to the robot configuration, or remove it |
| One of two lights shows nothing | With a Y-cable: check the cable. With `.alsoLight(...)`: both must be the same kind |
| INIT stops on `blinking(...)` | More than 5 times a second is not allowed (R202 J) |

## Done when

- INIT shows the alliance color on the light.
- Each rule shows its color when it is true.
- No rule uses red or blue.

---

**Next:** [Add odometry](09-odometry.md).

**Look it up:** [Lights](../reference/parts/lights.md): every rule, color and command.
