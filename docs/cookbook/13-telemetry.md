# Telemetry

**The questions on this page:**

- [How do I show a value on the Driver Station screen?](#how-do-i-show-a-value-on-the-driver-station-screen)
- [How do I round a number on the Driver Station?](#how-do-i-round-a-number-on-the-driver-station)
- [How do I show lines from my own autonomous step?](#how-do-i-show-lines-from-my-own-autonomous-step)

## How do I show a value on the Driver Station screen?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-value -->
```java title="Controls.java"
// A line of its own in buttons(...). The value is read again every loop.
show("Lift ticks", robot.lift::position);
show("Intake on", robot.intake::isRunning);                 // shows "Intake on : true"
show("Flywheel ready", () -> robot.launcher.isReady());
```

**Why:** you see what the robot "thinks" while you drive. The value must be quick to read, with no waiting.
**More:** [Controls: feedback](../controls/03-see-and-feel.md#feedback-on-a-button). For debug lines (loop time and more), hold gamepad 1 START, then press BACK. The lines turn on or off when you let go. BACK first only pauses the robot. When the alliance switch is on, let go before 1.5 s: a 1.5 s hold switches the alliance and does not change the debug lines.

## How do I round a number on the Driver Station?

**Where:** `Controls.java`, inside `buttons(...)`.

<!-- recipe: show-format -->
```java title="Controls.java"
// Round a number: %.0f = no decimals, %.1f = one decimal. Locale.US keeps the dot (not a comma).
show("Target RPM", () -> String.format(Locale.US, "%.0f", robot.launcher.targetRpm()));
```

**Why:** `%.0f` means "a number with 0 decimals", and `%.2f` gives 2.
**More:** [Controls: feedback](../controls/03-see-and-feel.md#feedback-on-a-button).

## How do I show lines from my own autonomous step?

**Where:** a file in `autos/`, inside `steps(...)`.

<!-- recipe: auto-telemetry -->
```java title="autos/YourAuto.java"
// In your own step: lines on the Driver Station while it runs, and a note in the report.
s.step("Spin up", ctx -> {
    robot.launcher.spinUp();
    double giveUpAt = ctx.secondsLeft() - 3;    // a time limit: at most 3 s
    while (ctx.isActive() && !robot.launcher.isReady() && ctx.secondsLeft() > giveUpAt) {
        ctx.telemetry().line("Flywheel", "spinning up to %.0f RPM", robot.launcher.targetRpm());
        ctx.loop();          // updates every part once: the robot keeps running
    }
    ctx.note(robot.launcher.isReady() ? "at speed" : "not at speed after 3 s");
});
```

**Why:** `ctx.loop()` keeps the robot running inside your loop. The time limit makes sure that the loop ends.
**More:** [Your own steps](../building-autos/08-advanced-autos.md#write-your-own-step), [The report](../building-autos/09-match-day.md#after-the-match-read-the-report).
