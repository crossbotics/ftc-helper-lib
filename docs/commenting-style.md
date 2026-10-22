# Commenting style

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

The people reading this code are high-school students, many with little or no Java: **assume a
novice programmer**. Comments are how they learn what each part does and WHY it is written that way.
Every file follows this guide.

**Write in plain, everyday, non-technical language.** Say "the robot waits here" instead of
"this blocks the thread". Say "a number that can change" instead of "a mutable field". When a
technical word cannot be avoided (encoder, PID, servo), explain it in a few everyday words right
there, or point to the page that explains it.

**Point to other files and docs** when they explain more: "See docs/modules/drive.md",
"Tutorial 07 explains PID step by step", "The signs are in docs/conventions.md",
"This is the same idea as SafeMotor (safety/SafeMotor.java)".

## The rules

1. **Explain WHY, not just WHAT.** `// clamp the power` says what. `// clamp: a motor only accepts -1..1,
   and NaN (a bad sensor reading) would make it jump` says why.
2. **Plain, short, non-technical sentences.** Everyday words. Explain any technical word the first
   time in everyday terms (PID, encoder, tick, lambda, edge...), or link to the doc that explains it.
3. **One comment per idea**, on the line above the code it explains (or at the end of a short line).
4. **Units and signs** wherever a number appears: ticks, inches, degrees, seconds, milliseconds;
   "positive = left (counterclockwise)".
5. **Safety reasons** are always written down: why a motor is stopped, why a limit exists, why we never wait.
6. **Java ideas** that a beginner would not know get a short, everyday-language note the first time
   they appear in a file (for example "`() -> ...` is a small piece of code we hand over to run later"):
   lambda `() -> ...`, method reference `lift::stepUp`, ternary `a ? b : c`, `final`, `static`,
   `interface`, `@Override`, `try/finally`, `synchronized`, `volatile`, generics `<T>`, enum.
7. **Do not comment the obvious.** `i++` and simple getters need nothing. A comment that only repeats
   the code is noise.
8. **Section headers** in long classes: `// ---- Setup ----`, `// ---- Every loop ----`.
9. **Examples and tutorial programs get more comments than the library**: almost every block is
   explained, because students copy them.
10. **Keep comments true.** When code changes, its comments change in the same commit.

## Format

- Javadoc (`/** ... */`) on every public class and method: what it does, parameters (with units and
  ranges), what it returns, and a short example when useful.
- `//` comments inside methods for the steps and the reasons.
- Line length: about 110 characters.
