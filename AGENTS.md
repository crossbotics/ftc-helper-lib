# Instructions for AI agents (Claude Code, Codex)

This is ftc-helper-lib, a Java helper library for FTC robots, used by Indianola Robotics (team 8938)
as a git submodule in its FtcRobotController projects. Students read this code and its docs, so
explanations must be plain and short enough for a high-school student.

Read first: `README.md`, `docs/conventions.md` (signs and units), `docs/contributing.md` (the rules).

## Rules

- Every change follows the five steps in `docs/contributing.md`: code, Javadoc, test, module doc page,
  CHANGELOG. Docs must match the code exactly (method names, defaults, behavior).
- Java 8 language level. FTC SDK dependencies stay `compileOnly` in `library/build.gradle`.
- One sign convention everywhere (see `docs/conventions.md`). Do not add a second one.
- Nothing may block TeleOp. Autonomous-only blocking code checks `opModeIsActive()`, has a timeout,
  and stops motors in `finally`.
- No game-specific code (field positions, tag IDs, mechanism tuning).
- Before finishing: `./gradlew :library:testDebugUnitTest :examples:assembleDebug` must pass.
