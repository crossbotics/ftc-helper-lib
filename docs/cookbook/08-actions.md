# Actions

An action is a list of steps that use several parts, with waits between them. It never makes the robot
wait, because it does a little every loop. Actions go in `Actions.java`, one method each. The same
action works on a button and in an autonomous.

**The questions on this page:**

- [How do I do several things one after the other?](#how-do-i-do-several-things-one-after-the-other)
- [How do I repeat steps several times?](#how-do-i-repeat-steps-several-times)
- [How do I move two parts at the same time?](#how-do-i-move-two-parts-at-the-same-time)
- [How do I wait half a second (like sleep in Blocks)?](#how-do-i-wait-half-a-second-like-sleep-in-blocks)
- [How do I wait until something is true?](#how-do-i-wait-until-something-is-true)
- [How do I start an action from a button?](#how-do-i-start-an-action-from-a-button)
- [How do I stop every running action?](#how-do-i-stop-every-running-action)
- [How do I run an action when the OpMode starts?](#how-do-i-run-an-action-when-the-opmode-starts)
- [How do I use the same action in an autonomous?](#how-do-i-use-the-same-action-in-an-autonomous)

## How do I do several things one after the other?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-steps -->
```java title="Actions.java"
/** Lift up, open the claw, lift down: one step after the other. */
public static Action scoreHigh(Robot r) {
    return Action.named("Score high")
            .then(() -> r.lift.goTo("high"))
            .waitUntil("lift at the top", r.lift::isAtTarget, 2.0)   // at most 2 s
            .then(r.claw::open)
            .waitSeconds(0.3)                                        // let the piece drop
            .then(() -> r.lift.goTo("down"));
}
```

**Why:** each step starts when the one before it is done, and the driver can still drive the whole time.
**More:** [Actions.java: scoreHigh](../five-files/04-actions-java.md#your-first-action), [Action](../reference/steps.md#action).

## How do I repeat steps several times?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-repeat -->
```java title="Actions.java"
/** Pulses the intake 3 times: the for loop adds the same steps 3 times to the action. */
public static Action pulseThree(Robot r) {
    Action a = Action.named("Pulse three");
    for (int i = 0; i < 3; i++) {   // runs once, when the action is made: it never waits
        a.then(r.intake::in)
         .waitSeconds(0.4)
         .then(r.intake::stop)
         .waitSeconds(0.3);
    }
    return a;
}
```

**Why:** the `for` loop runs once, while Java makes the action, and writes the steps 3 times. Then the action does them one after the other, and the robot does not wait.
**More:** [Action](../reference/steps.md#action).

## How do I move two parts at the same time?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-together -->
```java title="Actions.java"
/** Two parts at once: then(a, b) starts both in the same loop. */
public static Action grabAndLift(Robot r) {
    return Action.named("Grab and lift")
            .then(r.claw::close, r.intake::stop)                     // both at once
            .waitUntil("claw closed", r.claw::isDone, 1.0)           // isDone: it should be there
            .then(() -> r.lift.goTo("low"));
}
```

**Why:** `then(a, b)` starts both parts in the same loop, so neither waits for the other.
**More:** [Actions.java: grabAndLift](../five-files/04-actions-java.md#the-steps).

## How do I wait half a second (like sleep in Blocks)?

Search words: sleep, wait, pause, delay.

**In an action** (`Actions.java`):

<!-- recipe: wait-in-action -->
```java title="Actions.java"
/** Opens the claw, waits half a second, then lowers the lift. */
public static Action dropAndLower(Robot r) {
    return Action.named("Drop and lower")
            .then(r.claw::open)
            .waitSeconds(0.5)                     // half a second; the robot keeps running
            .then(() -> r.lift.goTo("down"));
}
```

**In an autonomous** (inside `steps(...)`), give the step a name first, for the report:

<!-- recipe: wait-in-auto -->
```java title="autos/YourAuto.java"
s.run("Open the claw", robot.claw::open)
 .waitSeconds("Let the piece drop", 0.5)    // a name for the report, then the seconds
 .run("Lift down", () -> robot.lift.goTo("down"));
```

**Why:** `sleep()` stops the whole robot: no driving, and no other part moves. `waitSeconds` waits while everything keeps running.
**More:** [Actions.java: the rules](../five-files/04-actions-java.md#the-rules-every-action-follows), [Non-blocking code](../guides/non-blocking.md).

## How do I wait until something is true?

**Where:** `Actions.java`, inside the class.

<!-- recipe: action-wait-until -->
```java title="Actions.java"
/** Lowers the lift, and opens the claw only once the lift is down. */
public static Action lowerAndRelease(Robot r) {
    return Action.named("Lower and release")
            .then(() -> r.lift.goTo("down"))
            // Waits until the test is true. If 2 s pass first, the action STOPS here and says why.
            .waitUntil("lift down", () -> r.lift.isAt("down"), 2.0)
            .then(r.claw::open);
}
```

**Why:** every wait has a time limit, so a stuck part cannot freeze the action forever.
**More:** [Actions.java: the rules](../five-files/04-actions-java.md#the-rules-every-action-follows).

## How do I start an action from a button?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-button -->
```java title="Controls.java"
// Press Y: the action starts. Press Y again while it runs: it is canceled, safely.
g2.y.press(Actions.scoreHigh(robot)).says("score high");
g2.x.press(Actions.grabAndLift(robot)).says("grab and lift");
```

**Why:** the same button cancels it. A stick or button that moves the same part also stops it, so the driver always wins.
**More:** [Actions.java: start it from a button](../five-files/04-actions-java.md#start-it-from-a-button).

## How do I stop every running action?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: action-cancel-all -->
```java title="Controls.java"
// Stop every running action at once (each stops what it started, safely).
g2.back.press(robot::cancelAll).says("stop all actions");
```

**Why:** a stopped action leaves each part safe: a lift holds its height, and an intake stops.
**More:** [RobotDefinition: for your code](../reference/robot-java.md#robotdefinition).

## How do I run an action when the OpMode starts?

**Where:** `Robot.java`, inside `define()`.

<!-- recipe: action-at-start -->
```java title="Robot.java"
// In Robot.java's define(): this action runs once, right after ▶ (autonomous and TeleOp).
atStart(() -> Actions.deployIntake(this));
```

The action it runs, in `Actions.java` inside the class (`intakeDeploy` is a
[servo with named positions](04-mechanisms.md#how-do-i-add-any-servo-with-named-positions-a-gate-a-fold-out)):

<!-- recipe: action-deploy-intake -->
```java title="Actions.java"
/** Folds the intake out (the atStart line in Robot.java runs it). */
public static Action deployIntake(Robot r) {
    return Action.named("Deploy intake").then(() -> r.intakeDeploy.goTo("deployed"));
}
```

**Why:** the library never moves anything in INIT, so a fold-out waits for ▶. This is the library's
safety choice, because people are still next to the robot. The game rules would allow it (R103.B), but
the robot must be still once INIT is done (G304.H).
**More:** [Actions.java: an action at ▶](../five-files/04-actions-java.md#an-action-when-the-opmode-starts).

## How do I use the same action in an autonomous?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: action-in-auto -->
```java title="autos/YourAuto.java"
s.start("Against our wall, facing the field", 0)
 .forward("To the goal", 24, 0.5)
 .action(Actions.scoreHigh(robot), 5)     // waits until it is done (at most 5 s)
 .back("Back up", 12, 0.5);
```

**Why:** you write the move once, and use it in TeleOp and autonomous. The report says if it timed out.
**More:** [Actions.java: in an autonomous](../five-files/04-actions-java.md#use-it-in-an-autonomous).
