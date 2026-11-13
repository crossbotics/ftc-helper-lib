# Lights

A colored light on the robot. The drivers watch the robot, not the Driver Station, so the light shows them
what they need: green when the robot is ready to fire, orange blinking when it is jammed, a white flash for
each piece fired, yellow in the last 20 seconds.

## In Robot.java

```java title="Robot.java"
lights = add(Lights.gobilda("light"));      // Robot.java: a goBILDA RGB Indicator Light ("Servo" in the configuration)
lights = add(Lights.blinkin("blinkin"));    // or a REV Blinkin LED Driver and its LED strip ("REV Blinkin LED Driver")
```

| Attribute | Needed? | What it sets | If you leave it out |
| --- | --- | --- | --- |
| `Lights.gobilda(name)` | **yes**, one of the two | a goBILDA RGB Indicator Light. In the configuration it is a "Servo" | - |
| `Lights.blinkin(name)` | **yes**, one of the two | a REV Blinkin LED Driver and its LED strip. In the configuration: "REV Blinkin LED Driver" | - |
| `.alsoLight(names...)` | optional | more lights of the same kind that always show the same as this one (the same color, blinking and flashes). Lights that show different things: two `add(...)` lines instead | one light |
| `.optional()` | optional | the part may not be on the robot yet ([Parts not on the robot yet](../robot-java.md#parts-not-on-the-robot-yet)) | INIT stops when its device is missing |
| `.tunables("Name")` | optional | read another `Tunables` section | the device's name in CamelCase |

You choose which color shows when in `Controls.java` ([Commands](#commands)). In the simulator, the light
is a dot on the robot and on the 3D view.

## In Tunables.java

<!-- tunables: Lights -->
The section's name is the device's name in CamelCase (`light` reads `Light`, `blinkin` reads `Blinkin`).

**Copy into `Tunables.java`.** Change only what you need. `DEFAULT` keeps the starting value
([DEFAULT and MEASURE](../tunables.md#default-and-measure)).

<!-- recipe: tunables-lights -->
```java title="Tunables.java"
public static class Light {                                     // "light" in the configuration
    public static boolean allianceAtInit = true;                // show the alliance in INIT, flash it at ▶ (true)
}
```

| Field | What it does | Units | Default | In tuning menu | Found by | When to change |
| --- | --- | --- | --- | --- | --- | --- |
| `allianceAtInit` | shows the alliance (red or blue) during INIT and flashes it at ▶. A `robot.lights.allianceAtInit(...)` line in Controls.java wins over this one | true/false | true | no | none | `false`: your rules show in INIT too |

## Commands

You write which color shows when in `Controls.java`, inside `buttons(...)`: one line per rule. Every file that names a
color (`Controls.java`, and `Actions.java` if an action flashes the light) needs
`import com.crossbotics.ftchelper.robot.LightColor;` at the top.

```java
robot.lights.show(LightColor.WHITE).forSeconds(0.2)                    // a flash first (it beats every rule):
        .whenCountGoesUp(() -> robot.launcher.part().getShotsFired()); // one short flash per piece fired
robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);     // then the most important rule
robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);     // ready to fire
robot.lights.show(LightColor.PURPLE).when(() -> robot.turret.followMode().isOn());   // a turret robot
robot.lights.show(LightColor.PURPLE).when(() -> robot.aimMode().isOn()); // or: whole-robot aim on (no turret)
robot.lights.otherwise(LightColor.OFF);                                 // when no rule is true
```

| Command | What it does |
| --- | --- |
| `show(color).when(test)` | shows that color while `test` is true |
| `show(color).blinking().when(test)` | blinks 2 times a second. `blinking(1)`: once a second (any number above 0, at most 5) |
| `show(color).whenStarts(test)` | a 0.5 s flash each time `test` turns from false to true. Put `.forSeconds(1)` first for a longer flash |
| `show(color).whenCountGoesUp(count)` | a 0.5 s flash each time the number goes up. `() -> robot.launcher.part().getShotsFired()` flashes once per piece fired (`whenStarts(robot.launcher::isShooting)` would flash once for all of a `fire(3)`). Shots closer together than the flash run into one long flash: `.forSeconds(0.2)` first keeps them apart |
| `show(color).inLastSeconds(20)` | shows it in the last 20 s of TeleOp's 2:00, counted from ▶ |
| `otherwise(color)` | when no rule is true (default `OFF`) |
| `flash(color)`, `flash(color, seconds)` | flashes now, over every rule: from a button or an action step |
| `clearRules()` | removes every rule |
| `allianceAtInit(false)` | turns off the alliance at INIT ([below](#the-alliance-at-init)) |
| `allianceColors(blue, red)` | the colors for the alliance at INIT, for a light that shows red or blue badly |
| `blinkinPattern(color, pattern)` | a Blinkin only: your own pattern for a color, e.g. `blinkinPattern(LightColor.BLUE, BlinkinPattern.BLUE_VIOLET)` |

| Question | What it tells you |
| --- | --- |
| `color()` | the color the rules pick |
| `showing()` | what is lit this instant (OFF in a blink's dark half) |
| `isFlashing()` | whether a flash shows now |
| `showsAllianceAtInit()` | whether the alliance at INIT is on |
| `isPresent()` | whether the part is on the robot (an `optional()` part may not be) |

### How the rules work

- **The first true rule wins**, in the order written. A flash beats every rule while it lasts, wherever it
  is written (write it first, so the file reads in that order). A flash in the color of the rule under it cannot be
  seen: give it another color.
- **Any true/false works:** `robot.intake::isRunning`, a mode (`() -> robot.turret.followMode().isOn()`),
  or your own (`() -> robot.lift.position() > 1000`). A rule that fails counts as false, and the Driver
  Station warns.
- The light works from INIT (the drivers see it light up) and goes dark at STOP. An action never "claims" it.
- **At most one change every 0.1 s** (`Lights.MIN_CHANGE_SEC`). Even a rule that turns on and off every loop, or
  flash after flash, switches the light on and off at most 5 times a second (R202 J below). A change that
  comes sooner waits a loop or two. A flash shorter than 0.1 s may not show.

### Colors

`LightColor`: `RED`, `ORANGE`, `YELLOW`, `GREEN`, `BLUE`, `PURPLE`, `WHITE`, `OFF`. Each light turns them
into its own signal:

| LightColor | goBILDA position | Blinkin pattern |
| --- | --- | --- |
| RED | 0.280 | RED |
| ORANGE | 0.333 | ORANGE |
| YELLOW | 0.388 | YELLOW |
| GREEN | 0.500 | GREEN |
| BLUE | 0.611 | BLUE |
| PURPLE | 0.700 | VIOLET |
| WHITE | 1.0 | WHITE |
| OFF | 0.0 | BLACK |

- The goBILDA numbers are from the seller's chart (3118-0808-0002, Product Insight #4, checked 2026-10-21):
  off below 1100 microseconds, red to violet up to 1900, white above. That is with the hub's normal servo
  signal (0 = 600, 1 = 2400 microseconds).
- RED and PURPLE sit a little inside that window. The chart's 0.277 and 0.722 are right on its edges.
- The Blinkin (REV-11-1105) uses the SDK's `RevBlinkinLedDriver`. With no signal it shows its own rainbow
  "color waves".

### Game rules (2026-27 manual)

- The referees can switch off lights that flash faster than about 5 times a second (R202 J), or very bright
  lights (R202 F). So `blinking(...)` takes at most 5 times a second (a bigger number is an error),
  and the light never changes faster than every 0.1 s.
- A light in your alliance's color is allowed: the manual has no rule against it (R202 F and J, R401, R402
  checked). The ROBOT SIGNS must still show it (R401, R402).
- Do not shine lights onto the field (G302 example).

## The alliance at INIT

A wrong alliance pick in INIT makes the robot aim at the other alliance's goal all match. The drive team sees
the light from the field, not the Driver Station's small text. So the light shows the alliance. It is on by
default, on every robot with a light.

- **INIT:** solid `RED` or `BLUE` once the alliance is picked (TeleOp: gamepad 1 X or B, or the one
  autonomous left; autonomous: its INIT menu). Before the alliance is picked: **white blinking once a
  second** ("choose the alliance"). Your rules wait until ▶.
- **▶:** a 1 s flash of the alliance (TeleOp after autonomous: the one it got), then your rules.
- **Alliance switched in TeleOp** (the opt-in `lib.switchAlliance(...)`): a 1 s flash of the new one.
- Keep your rules' colors away from red and blue (the examples use purple and orange). Then nobody reads
  "intake on" as "BLUE alliance".

To turn it off, in `Tunables.java` (the light's section, named after its device, for example
`Tunables.Light`):

```java
public static class Light {
    public static boolean allianceAtInit = false;   // the rules show in INIT too, and no alliance flashes
}
```

Or in `Controls.java`: `robot.lights.allianceAtInit(false);`.

For a light that shows red or blue badly, pick two other colors:
`robot.lights.allianceColors(LightColor.PURPLE, LightColor.ORANGE);` (blue's color first). For a Blinkin, you
can set a pattern of your own:
`robot.lights.blinkinPattern(LightColor.BLUE, BlinkinPattern.BLUE_VIOLET);`
(`import com.qualcomm.hardware.rev.RevBlinkinLedDriver.BlinkinPattern;`).

## Buttons to add

Lights need no button. A button can flash the light: `g2.a.press(() -> robot.lights.flash(LightColor.GREEN))`.

---

**Look it up:** [`Lights`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/Lights.html)
and [`LightColor`](https://crossbotics.com/ftc-helper/reference/com/crossbotics/ftchelper/robot/LightColor.html)
in the Javadoc (every method). **The other parts:** [Reference](../README.md).
