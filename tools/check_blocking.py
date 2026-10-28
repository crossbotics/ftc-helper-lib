#!/usr/bin/env python3
"""Checks that nothing blocks by accident. Run from the repo root:

    python3 tools/check_blocking.py

Fails (exit 1) when:
  1. the library has sleep(...), Thread.sleep, .join(), a `while` loop or a `for (;;)` outside the
     classes that block ON PURPOSE (listed below, each with the reason);
  2. any example or tutorial program calls sleep(...).

Comments and Javadoc are ignored. See docs/guides/non-blocking.md.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LIB = ROOT / "library/src/main/java/com/crossbotics/ftchelper"
EXAMPLES = ROOT / "examples/src/main/java"

# Library classes allowed to wait, and why.
ALLOWED_LOOPS = {
    "drive/AutoDrive.java": "autonomous moves block on purpose (with time limits and STOP checks)",
    "auto/AutoRoutine.java": "autonomous step runner blocks on purpose (keeps mechanisms updating)",
    "auto/EasyAuto.java": "an OpMode: its INIT and run loops",
    "testing/HardwareCheckOpMode.java": "an OpMode: its run loop",
    "teleop/EasyTeleOp.java": "an OpMode: its INIT and run loops (each pass is quick; it never waits)",
    "finders/FinderOpMode.java": "an OpMode: its INIT and run loops (each pass is quick; it never waits)",
    "finders/TuneAndCheckOpMode.java": "an OpMode: its INIT menu and run loops (each pass is quick; it never waits)",
    "finders/DriveTunerOpMode.java": "a tuning OpMode: its test moves block on purpose, like autonomous",
    "util/BackgroundWorker.java": "the background thread's own loop (not the robot's loop)",
}

SLEEP = re.compile(r"\bsleep\s*\(|Thread\.sleep|\.join\s*\(")
LOOP = re.compile(r"\bwhile\s*\(|for\s*\(\s*;\s*;\s*\)")


def code_only(text):
    # block comments and Javadoc (keep their line breaks so line numbers stay right)
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)                     # line comments
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)          # string contents
    return text


problems = []
for java in sorted(LIB.rglob("*.java")):
    rel = java.relative_to(LIB).as_posix()
    code = code_only(java.read_text())
    for n, line in enumerate(code.splitlines(), 1):
        if SLEEP.search(line):
            problems.append(f"library/{rel}:{n}: sleep/join in the library: {line.strip()}")
        if LOOP.search(line) and rel not in ALLOWED_LOOPS:
            problems.append(f"library/{rel}:{n}: a loop in a class that must not block: {line.strip()}")

for java in sorted(EXAMPLES.rglob("*.java")):
    code = code_only(java.read_text())
    for n, line in enumerate(code.splitlines(), 1):
        if SLEEP.search(line):
            problems.append(f"{java.relative_to(ROOT)}:{n}: sleep() in an example: use a non-blocking wait")

if problems:
    print("Blocking check FAILED:")
    for p in problems:
        print("  - " + p)
    print("If a class really must block, add it to ALLOWED_LOOPS with the reason, and say BLOCKS in its Javadoc.")
    sys.exit(1)
print("Blocking check passed.")
