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
   In examples and tutorial programs (rule 9) that note goes in the docs next to the code (a code
   annotation or the text), not in the file.
7. **Do not comment the obvious.** `i++` and simple getters need nothing. A comment that only repeats
   the code is noise.
8. **Section headers** in long classes: `// ---- Setup ----`, `// ---- Every loop ----`.
9. **Examples and tutorial programs keep only the essential comments** (decided 2026-10-23): what a line
   is, units, "= the name in the robot configuration", "measure this", a safety warning, and a
   `more: https://crossbotics.com/...` link where it helps. At most 3 `//` lines in a row and 8 lines of
   `/** */` (`tools/check_docs.py` checks it). **Why:** teams copy these files, and long explanations get
   in the way of their own code. The WHY (rules 1 and 6) goes in the docs, next to the code that shows it:
   as a [code annotation](#code-annotations-in-the-docs) or in the text.
10. **Keep comments true.** When code changes, its comments change in the same commit.
11. **Say it once** (docs code boxes). Each part has its own job:
    - the **code comment** says WHAT the line is, in one line;
    - the **(+) note** ([code annotation](#code-annotations-in-the-docs)) says WHY, for that one line
      only: at most about 3 sentences, and no numbers the text or a table already gives;
    - the **text under the box** says what the step does and what to check.

    Never write the same sentence twice (in the comment and the note, or the note and the text). A note
    every recipe would repeat (what `.part(...)` does) is explained once, where it first appears.

## Code annotations in the docs

A docs code box can explain a line without a long comment in the code. Add a marker `(1)!` at the end of
the line's `//` comment (or `// (1)!` alone after the code), and put a numbered list right under the box,
one item per marker, in order:

````text
```java title="Robot.java"
lift = add(Lift.slide("lift").part("gobilda-5203-435"));   // the lift (1)!
```

1. "lift" is the motor's name in the robot configuration. Its numbers: `Tunables.Lift`.
````

On crossbotics.com each marker becomes a small (+) button that opens its item; the list itself is hidden
and the Copy button copies the code without the markers. On GitHub the marker and the list show as text.
The markers are only in the docs: `tools/check_docs.py` takes them out before it compares a box with its
Java file, and `--fix-cookbook` keeps them on the lines that did not change.

## Format

- Javadoc (`/** ... */`) on every public class and method: what it does, parameters (with units and
  ranges), what it returns, and a short example when useful.
- `//` comments inside methods for the steps and the reasons.
- Line length: about 110 characters.
