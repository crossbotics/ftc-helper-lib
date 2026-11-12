# Search every class and method

The Javadoc lists **every public class, method and setting** of the library, with what it does, its inputs
and what it gives back. The library makes it from the explanations in the code (Javadoc comments), so it
matches the version you have. It has a **search box**: type a name (`parkWithSeconds`, `PositionMotor`,
`slow`) to jump to it.

The [reference pages](README.md) explain each part, with examples. The Javadoc is for looking one method
up. For "how do I...?" questions, with code to copy, see the [cookbook](../cookbook.md).

## Open it

**Online:** [crossbotics.com/ftc-helper/reference](https://crossbotics.com/ftc-helper/reference/) (the
newest release).

Or make it on your computer, for the version you have.

**Where:** Android Studio's **Terminal** tab, in the **`ftc-helper-lib` folder** (type `cd ftc-helper-lib` first). ([Which folder](../setup/09-the-terminal.md#which-folder))

```bash
./gradlew :library:reference
```

Then open `library/build/reference/index.html` in a browser. Making it needs the same setup as building
the library ([Contributing](../contributing.md)). Your robot project never
makes it by itself, so it adds no time to your robot's builds.

## Read a page

Each class page starts with what the class is for, often with a short example. Below it, each method shows
its name and inputs (`move(double inches, double directionDeg, double maxPower)`), a sentence on what it
does, then each input (`@param`) and what it gives back (`@return`). The word before each input is its type:
`String` is text, `double` is a number with a decimal point. You call a "static" method on the class
(`Action.named("Score")`), and the others on an object you made (`robot.lift.goTo("high")`).
