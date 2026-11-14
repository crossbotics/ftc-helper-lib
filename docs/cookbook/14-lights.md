# Lights

A colored light on the robot tells the drivers what the robot "knows" (full, ready, a mode on), so they
do not have to look at the Driver Station. The color names need `import com.crossbotics.ftchelper.robot.LightColor;`
at the top of each file that uses them: `Controls.java`, and `Actions.java` if an action flashes the
light. To add it, press Alt+Enter on a red `LightColor`. The full details: [Lights](../reference/parts/lights.md).

**The questions on this page:**

- [How do I add a colored light for the drivers?](#how-do-i-add-a-colored-light-for-the-drivers)
- [How do I change a color on a REV Blinkin?](#how-do-i-change-a-color-on-a-rev-blinkin)
- [How do I show a color when something is true?](#how-do-i-show-a-color-when-something-is-true)
- [How do I show the alliance on the light before the match?](#how-do-i-show-the-alliance-on-the-light-before-the-match)
- [How do I flash the light when something happens?](#how-do-i-flash-the-light-when-something-happens)
- [How do I flash the light when an action is done?](#how-do-i-flash-the-light-when-an-action-is-done)
- [How do I blink the light in the last 20 seconds?](#how-do-i-blink-the-light-in-the-last-20-seconds)

## How do I add a colored light for the drivers?

**Where:** `Robot.java`, inside `define()` (and a field `public Lights lights;`).

<!-- recipe: light-add -->
```java title="Robot.java"
// At the top of Robot.java: import com.crossbotics.ftchelper.robot.Lights; (1)!
// A goBILDA RGB Indicator Light on a servo port ("Servo" in the robot configuration):
lights = add(Lights.gobilda("light"));
// Or a REV Blinkin LED Driver with an LED strip ("REV Blinkin LED Driver" in the configuration):
//   lights = add(Lights.blinkin("blinkin"));
```

1. `Controls.java` needs `import com.crossbotics.ftchelper.robot.LightColor;` for the colors. To add it, press Alt+Enter on a red `LightColor`.

**Why:** the light is one line, like any part. It has no numbers and no buttons of its own: the colors come from your rules.
**More:** [Lights](../reference/parts/lights.md) (the parts and their color tables).

## How do I change a color on a REV Blinkin?

**Where:** `Robot.java`, inside `define()`, below the lights line.

<!-- recipe: light-blinkin -->
```java title="Robot.java"
// At the top of Robot.java: import com.qualcomm.hardware.rev.RevBlinkinLedDriver;
// A REV Blinkin LED Driver ("REV Blinkin LED Driver" in the robot configuration):
lights = add(Lights.blinkin("blinkin"));
// Plain blue too dark on your strip? Show another Blinkin pattern for BLUE. (1)!
lights.blinkinPattern(LightColor.BLUE, RevBlinkinLedDriver.BlinkinPattern.BLUE_VIOLET);
```

1. The pattern names are on REV's Blinkin page. Other lights ignore this line.

**Why:** each `LightColor` is one of the Blinkin's patterns (`BLUE` is its plain blue). LED strips differ.
When one color looks wrong on yours, give that color another pattern. Your rules still say
`LightColor.BLUE`. A goBILDA light ignores the line.

## How do I show a color when something is true?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: light-rules -->
```java title="Controls.java"
// One rule per line. The first true rule wins: put the most important first.
// Red and blue are left for the alliance (shown at INIT), so no rule looks like one.
robot.lights.show(LightColor.ORANGE).blinking().when(robot.intake::isPushingOut);  // pushing out
robot.lights.show(LightColor.GREEN).when(robot.launcher::isReady);              // at speed: fire
robot.lights.show(LightColor.PURPLE).when(robot.intake::isRunning);             // intake on
robot.lights.otherwise(LightColor.OFF);                                         // none true
```

**Why:** any true/false works as a rule:
- a part's method (`robot.intake::isRunning`)
- a mode: `() -> robot.turret.followMode().isOn()` (the turret follows the goal), or on a robot without a turret, `() -> robot.aimMode().isOn()` (whole-robot aim is on)
- your own test (`() -> robot.lift.position() > 1000`, if your robot has a lift)

Blinking is at most 5 times a second, and the light never changes faster than every 0.1 s (FTC rule R202 J).
**More:** [Lights](../reference/parts/lights.md), [lambdas](../five-files/04-actions-java.md).

## How do I show the alliance on the light before the match?

**Where:** you add nothing, because it is on by default. To change its colors or turn it off, write in
`Controls.java`, inside `buttons(...)`.

<!-- recipe: light-alliance -->
```java title="Controls.java"
// On by default: the alliance at INIT (solid RED or BLUE), flashed for 1 s at ▶. (1)!
// Your light shows red badly? Other colors for the alliance at INIT (BLUE, and PURPLE for RED):
robot.lights.allianceColors(LightColor.BLUE, LightColor.PURPLE);
// No alliance color at INIT at all? Delete the line above and write: (2)!
//     robot.lights.allianceAtInit(false);
```

1. Until the drivers choose an alliance, the light blinks white. You do not write anything for this.
2. Your rules then show from INIT on. Or, in `Tunables.java`, write this in the light's section: `public static class Light { public static boolean allianceAtInit = false; }`. The line in `Controls.java` wins.

**Why:** with a wrong alliance pick in INIT, the robot aims at the other alliance's goal all match. The
light is solid RED or BLUE before ▶ (white, blinking once a second, until the drivers pick). So the drive
team sees a wrong pick from the field. The game manual has no rule against a light in your alliance
color, but the ROBOT SIGNS are still required (R401, R402). Keep your own rules' colors away from red and blue.
**More:** [Lights](../reference/parts/lights.md#the-alliance-at-init).

## How do I flash the light when something happens?

**Where:** `Controls.java`, inside `buttons(...)`, above your other light rules.

<!-- recipe: light-flash -->
```java title="Controls.java"
// A flash beats every rule while it lasts (write it first: it reads that way). (1)!
// A short white flash for each piece fired, and one from a button.
robot.lights.show(LightColor.WHITE).forSeconds(0.2)
        .whenCountGoesUp(() -> robot.launcher.part().getShotsFired());
g2.a.press(() -> robot.lights.flash(LightColor.YELLOW)).says("flash the light (signal the partner)");
```

1. A flash wins, wherever you write it. The launcher counts the pieces it feeds. The flash is 0.2 s, so two quick shots are two flashes, not one long one.

**Why:** `whenCountGoesUp` flashes each time the number goes up, so each flash is a real shot.
`whenStarts(test)` flashes when a true/false turns from false to true (`whenStarts(robot.intake::isRunning)`:
the intake has started). A flash lasts 0.5 s. `.forSeconds(...)` before `when...` changes it. Here it is
shorter, so two shots a quarter second apart are two flashes, not one long one. `flash(color)` from a
button or an action flashes once.

## How do I flash the light when an action is done?

**Where:** `Actions.java`, inside the class.

<!-- recipe: light-action -->
```java title="Actions.java"
// In Actions.java (it needs import com.crossbotics.ftchelper.robot.LightColor; at the top).
// The last step: a 1 s white flash says "done" to the drivers. Pick a color your rules do not use. (1)!
public static Action shootTwo(Robot r) {
    return Action.named("Shoot two")
            .then(r.launcher::spinUp)
            .waitUntil("at speed", r.launcher::isReady, 3.0)
            .then(() -> r.launcher.fire(2))
            .waitUntil("both gone", () -> !r.launcher.isShooting(), 3.0)
            .then(() -> r.lights.flash(LightColor.WHITE, 1.0));
}
```

1. A GREEN flash over the GREEN "ready" rule cannot be seen. Pick another color, or a longer time.

**Why:** no action "claims" the light, so any action (or button) may flash it. A flash beats every rule.
But you cannot see a flash in the same color as the rule under it, so pick another color, or a longer time.

## How do I blink the light in the last 20 seconds?

**Where:** `Controls.java`, inside `buttons(...)`, before the other rules.

<!-- recipe: light-endgame -->
```java title="Controls.java"
// Yellow, blinking, in the last 20 seconds of TeleOp: time to park (above the other rules: it wins).
robot.lights.show(LightColor.YELLOW).blinking().inLastSeconds(20);
```

**Why:** the time counts from ▶ (TeleOp's 2:00), like the gamepad buzzes. It is about a second off from
the field clock, which is good enough for a reminder.
