# Turrets and interlocks

Every kind of turret has its own lines: motor, servo or CR servo, with a switch or not, with any range.
See [Setting up a turret](../guides/turret-setup.md). An interlock is a rule that stops two parts from
hitting each other. Code: [`InterlockRecipes.java`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook/InterlockRecipes.java).

**The questions on this page:**

- [How do I home my turret with a switch?](#how-do-i-home-my-turret-with-a-switch)
- [How do I set up a turret on a servo?](#how-do-i-set-up-a-turret-on-a-servo)
- [How do I set up a slip-ring turret (round and round)?](#how-do-i-set-up-a-slip-ring-turret-round-and-round)
- [How do I stop two parts from hitting each other?](#how-do-i-stop-two-parts-from-hitting-each-other)
- [How do I pick up and score with a lift, a wrist and a claw?](#how-do-i-pick-up-and-score-with-a-lift-a-wrist-and-a-claw)

## How do I home my turret with a switch?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-home-switch -->
```java title="Robot.java"
// "turret", "turret_home" = the motor's and the home switch's names in the robot configuration. (1)!
// At ▶ the turret turns slowly until it finds the switch. (2)!
turret = add(Turret.motor("turret", "gobilda-5203-312").gearRatio(2.75)
        .homeSwitch("turret_home")
        .follows(camera));
```

1. The home switch is a touch sensor ("REV Touch Sensor" in the configuration) or a magnetic switch ("Digital Device", for example the REV Magnetic Limit Switch).
2. After that, each time the turret passes the switch, the switch sets its angle right again.

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

**Why:** a motor's encoder counts from where the turret was at INIT. With a switch, the turret finds its
real angle at ▶ by itself. It never does this during INIT, because nothing moves before ▶. Every time
the turret passes the switch, the switch fixes the error from a slipping belt. Until the turret is homed
(has found its real angle), it does not follow the goal and is never "aimed". You must write
`homeAngle`, or INIT stops. `min`, `max` default to -90 and 90. With no switch, put the turret at
`startAngle` (default 0, straight forward) by hand before INIT.
**More:** [Setting up a turret: how it knows its angle](../guides/turret-setup.md#2-how-does-it-know-its-angle).

## How do I set up a turret on a servo?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-servo -->
```java title="Robot.java"
// "turret" = the servo's name in the robot configuration. Numbers: Tunables.Turret. (1)!
// fullRange(): the servo's whole turn. Turn it on BEFORE you measure posA and posB. (2)!
turret = add(Turret.servo("turret").part("gobilda-2000-speed").fullRange().follows(camera));
```

1. A standard servo turns the turret to an angle by itself. It needs no encoder, no PID and no homing.
2. With it, a goBILDA 2000 servo turns 300 degrees, not 270. Find posA and posB with the Servo Finder: press Y while you choose the servo.

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

**Why:** a servo knows how to go to a position, so the turret needs no encoder, no PID and no homing.
It is the cheapest turret. But a servo cannot say where it is. So at ▶, the library sends it to
`startAngle` (or the middle of its range), and "aimed" waits `travelSec` for it. You must write `posA`,
`angleA`, `posB`, `angleB`, or INIT stops. Find the positions with the [Servo Finder](15-tuning.md#how-do-i-find-a-servos-open-and-closed-numbers),
and measure the angle at each with a phone angle app. Its range is between `angleA` and `angleB`. Put
hard stops a little past them.
**More:** [Setting up a turret](../guides/turret-setup.md#1-what-turns-it), [Full range](04-mechanisms.md#how-do-i-get-my-servos-full-range).

## How do I set up a slip-ring turret (round and round)?

**Where:** `Robot.java`, inside `define()` (the turret line), and a section `Turret` in `Tunables.java`.

<!-- recipe: turret-slip-ring -->
```java title="Robot.java"
// A CR servo turns it, an absolute encoder says where it points, continuous(): no limits. (1)!
// "turret", "turret_pos" = the servo's and the encoder's names in the robot configuration.
// .part(...): which servo it is (optional, recommended).
turret = add(Turret.crServo("turret").part("axon-max-mk2").analogEncoder("turret_pos").continuous()
        .follows(camera));
```

1. The encoder is on an analog port and knows the angle at once, so the turret needs no homing. A slip ring (a turning joint that carries the wires) lets the turret turn with no limits. With no limits, the turret always takes the shortest way.

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

**Why:** with a slip ring, the wires never wind up. So the turret can always take the shortest way to
the goal, and is never "out of range". A motor turret can be `continuous()` too.
**More:** [Setting up a turret: how far can it turn?](../guides/turret-setup.md#3-how-far-can-it-turn).

## How do I stop two parts from hitting each other?

**Where:** `Robot.java`, inside `define()`, one line below the parts' `add` lines. Also a new section in
`Tunables.java` for the zones, with the same name as the rule.

<!-- recipe: interlock-rule -->
```java title="Robot.java"
// AFTER the parts' add lines: the turret stays out of its zone while the lift is in its
// zone, and the other way round. Whoever is in its zone first keeps it.
keepApart("TurretLift", turret.zone(), lift.zoneAbove());
```

<!-- recipe: tunables-interlock -->
```java title="Tunables.java"
// The rule's own section, named like the rule in Robot.java. Each zone in its part's units.
public static class TurretLift {                                // keepApart("TurretLift", ...) in Robot.java
    public static double turretFrom = MEASURE;                  // degrees: where the turret's zone starts
    public static double turretTo = MEASURE;                    // degrees: where it ends (robot's back)
    public static double liftAbove = MEASURE;                   // counts: the slide is up from here
    public static double liftStopSec = 0.2;                     // a heavy slide takes longer to stop (0.15)
}
```

**Why:** each part is safe inside its own limits, but a turret over the back and a lift at the back can
still meet. The library keeps the rule every loop, whatever moves the parts: buttons, sticks, actions, a
following turret or autonomous steps. The part that is in its zone first keeps it. The other part waits
at the edge of its own zone, and the Driver Station says so (`TurretLift : Lift held at 216 counts: Turret is between
130 and 218 deg`). The rule slows a fast part down as it comes near, so it brakes in time, even with
slow loops. To do this, the rule measures the part's speed every loop. The library reads the zones at
INIT only, so change them in `Tunables.java`.
**More:** [Two parts that must not collide](../guides/interlocks.md), [Interlock](../reference/parts/interlock.md).

## How do I pick up and score with a lift, a wrist and a claw?

**Where:** `Actions.java`, inside the class; then a button in `Controls.java`, inside `buttons(...)`:
`g2.y.press(Actions.scoreInFlower(robot)).says("score in a FLOWER");`

<!-- recipe: lifter-sequence -->
```java title="Actions.java"
/** Lifts a held ball over a FLOWER, lets it go and comes back folded (Actions.java). */
public static Action scoreInFlower(Robot r) {
    return Action.named("Score in FLOWER")
            .then(() -> r.wrist.goTo("carry"))                  // 1. swing the claw out first
            .waitUntil("wrist out", r.wrist::isDone, 1.0)
            .then(() -> r.lift.goTo("flower"))                  // 2. lift (the interlock may make it wait)
            .waitUntil("lift up", r.lift::isAtTarget, 4.0)
            .then(() -> r.wrist.goTo("flower"))                 // 3. tip it over the FLOWER
            .waitUntil("over the FLOWER", r.wrist::isDone, 1.0)
            .then(r.claw::open)                                 // 4. let go
            .waitUntil("let go", r.claw::isDone, 1.0)
            .then(() -> r.wrist.goTo("carry"))                  // 5. back the same way
            .waitUntil("wrist back", r.wrist::isDone, 1.0)
            .then(() -> r.lift.goTo("stowed"))
            .waitUntil("lift down", r.lift::isAtTarget, 3.0)
            .then(() -> r.wrist.goTo("folded"), r.claw::close);
}
```

**Why:** the order keeps the parts out of each other's way. The claw swings out before the lift rises,
and folds only after the lift is down. Each wait has a time limit, so a stuck part ends the action
instead of stopping the robot. With an interlock rule, the lift waits by itself while the turret is in the way.
**More:** the whole robot: [`v2bot`](../../examples/src/main/java/com/crossbotics/ftchelper/examples/v2bot/Actions.java).
