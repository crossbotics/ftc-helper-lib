# Contributing: add or change something in the library

Anyone on the team can improve the library. Because every robot project uses it, each change
follows the same five steps, so nothing breaks and the docs stay true.

## The five steps (for every change)

1. **Code** in `library/src/main/java/io/github/jasoncross/ftchelper/<module>/`.
2. **Javadoc** on every public class and method: what it does, each parameter (with its unit and
   range), what it returns, and a short example. Plain words.
3. **Test** in `library/src/test/java/...` for any logic (math, timing, button edges, limits).
   Hardware calls cannot be unit-tested; put the logic in a plain class (like `SoftLimits` for
   `SafeMotor`) and test that.
4. **Docs:** update the module page in `docs/modules/` (the method table and an example), and
   `docs/troubleshooting.md` if it fixes a common problem.
5. **CHANGELOG.md:** add a line under "Unreleased": Added / Changed / Fixed.

A change is not done until all five are done.

## Style

- Java 8 (no `var`, no `List.of`). Same as the FTC SDK.
- Signs and units from [Signs and units](conventions.md). Put the sign in the name when it can be
  confused: `turnClockwise`, `headingCcwDeg`, `distanceIn`, `timeoutMs`.
- Nothing in the library may block TeleOp: no `sleep()`, no waiting loops, except in clearly
  autonomous-only classes like `AutoDrive` (which always check `opModeIsActive()`, have a timeout,
  and stop motors in `finally`).
- Hardware setup methods leave motors at power 0.
- Nothing game-specific. Field positions, tag IDs and mechanism tuning belong in TeamCode.
- Classes that use time take a clock in a second constructor (`LongSupplier clockNanos`) so tests
  can control time. See `PidController` or `LoopTimer`.

## Build and test

You need JDK 17 or newer and the Android SDK. Android Studio installs both. From the library folder:

```bash
./gradlew :library:testDebugUnitTest :examples:assembleDebug
```

This runs every test and builds the example OpModes. Both must pass before you push. GitHub
also runs this on every push (`.github/workflows/build.yml`).

If Gradle cannot find the Android SDK, make a file `local.properties` in this folder with the line
`sdk.dir=/path/to/Android/sdk` (Android Studio shows the path in **Settings > Languages & Frameworks > Android SDK**).

## Working from inside a robot project

The library is a submodule, so you can edit it from the robot project in Android Studio and test it
on the robot right away. To save the change:

```bash
cd ftc-helper-lib
git checkout -b my-change          # the submodule is usually on a "detached" commit; make a branch
git add -A
git commit -m "Add ... to SafeMotor"
git push -u origin my-change
```

Then open a pull request on GitHub for the library, and ask the programming lead to review it.
After it is merged, update the robot project to the new version ([Keep it up to date](02-keep-up-to-date.md)).

## Releasing a version

The programming lead does this after merging changes:

1. In `CHANGELOG.md`, rename "Unreleased" to the new version and date, e.g. `## v0.2.0 - 2026-11-01`.
2. Commit, then tag and push:

```bash
git tag v0.2.0
git push origin main --tags
```

Version numbers: change the middle number (0.**2**.0) for new features, the last number
(0.2.**1**) for bug fixes, and the first number when something old stops working the same way.

## Ideas for later

- A vision module (AprilTags, from last season's `AprilTagVision`), once the new season's camera is chosen.
- Odometry pods (goBILDA Pinpoint) and pose tracking.
- A subsystem/command structure for bigger robots.
