# Reference: every class and method

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** anyone who wants to look up one class or method. **You need:** nothing; it helps to have
done the first [lessons](tutorials/README.md#start-here-the-main-track).

The reference lists **every public class, method and setting** of the library, with what it does,
its inputs and what it gives back. It is made from the explanations in the code (Javadoc), so it is
always up to date with the version you have. It has a **search box**: type a name (`parkWithSeconds`,
`PositionMotor`, `slow`) and jump straight to it.

The module pages ([robot](modules/robot.md), [drive](modules/drive.md), ...) explain how the parts
fit together, with examples; the reference is for looking one thing up. For "how do I...?" questions,
with code to copy, see the [cookbook](cookbook.md).

## Open it

**Online:** [crossbotics.com/ftc-helper/reference](https://crossbotics.com/ftc-helper/reference/) (the
newest release).

Or make it on your computer (for the version you have), from the ftc-helper-lib folder:

```bash
./gradlew :library:reference
```

Then open `library/build/reference/index.html` in a browser. It takes about a minute, and it needs
the same setup as building the library ([Contributing](contributing.md)). Your robot project never
makes it by itself, so it adds no time to your robot's builds.

## Read a page

Each class page starts with what the class is for, often with a short example. Below come its methods:
the name and inputs (`move(String label, double inches, double directionDeg, double maxPower)`), a
sentence on what it does, then each input (`@param`) and what it gives back (`@return`). The word
before each input is its type: `String` is text, `double` is a number with a decimal point. "Static"
methods are called on the class (`Action.named("Score")`); the others on an object you made
(`robot.lift.goTo("high")`).
