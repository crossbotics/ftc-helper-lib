#!/usr/bin/env python3
"""Checks that nothing blocks by accident. Run from the repo root:

    python3 tools/check_blocking.py

Fails (exit 1) when:
  1. the library has sleep(...), Thread.sleep or .join();
  2. the library has a `while` loop or a `for (;;)` outside the methods that block ON PURPOSE (listed
     below, each with the reason);
  3. the library waits another way (Object.wait, .await, .acquire, .take, LockSupport.park, or a class
     made for waiting, such as Future, CountDownLatch or Semaphore) outside the methods listed for it;
  4. any example or tutorial program sleeps or waits one of these ways.

Comments and Javadoc are ignored. See docs/guides/non-blocking.md.

What it cannot see, since it only reads the words of the code:
  - a wait for a lock (`synchronized`) that another thread holds during slow work. Never hold a lock the
    robot's loop also needs while you write a file or talk to a device: use a separate lock.
  - a slow request inside a call, such as an SDK method that waits for a device.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LIB = ROOT / "library/src/main/java/com/crossbotics/ftchelper"
EXAMPLES = ROOT / "examples/src/main/java"

# The methods allowed to loop in each file, and why. A file here is no free pass: a new loop in another
# method of the file fails until it is added.
ALLOWED_LOOPS = {
    "drive/AutoDrive.java": ({"move", "moveWithTurn", "driveTo", "followRoute", "turnUntil", "settle"},
                             "autonomous moves block on purpose (with time limits and STOP checks)"),
    "auto/AutoRoutine.java": ({"turnTowardWithSweep", "followPath", "waitUntil", "waitFor"},
                              "autonomous step runner blocks on purpose (keeps mechanisms updating)"),
    "auto/EasyAuto.java": ({"runBody", "runAfterSetup"}, "an OpMode: its INIT and run loops"),
    "testing/HardwareCheckOpMode.java": ({"runBody"}, "an OpMode: its INIT and run loops"),
    "teleop/EasyTeleOp.java": ({"runBody"}, "an OpMode: its INIT and run loops (each pass is quick; it never waits)"),
    "finders/FinderOpMode.java": ({"runBody"}, "an OpMode: its INIT and run loops (each pass is quick; it never waits)"),
    "finders/TuneAndCheckOpMode.java": ({"runBody"}, "an OpMode: its INIT menu and run loops (each pass is quick; it never waits)"),
    "finders/DriveTunerOpMode.java": ({"chooseNext", "choosePrevious"}, "a short search for the next test in a fixed list"),
    "util/BackgroundWorker.java": ({"run"}, "the background thread's own loop (not the robot's loop)"),
    "core/InitStop.java": ({"showAndWait"}, "an INIT error: shows it and waits for STOP (nothing moves)"),
}

# The methods allowed to wait another way.
ALLOWED_WAITS = {
    "util/BackgroundWorker.java": ({"run"}, "the background thread waits for its next job (not the robot's loop)"),
}

SLEEP = re.compile(r"\bsleep\s*\(|Thread\.sleep|\.join\s*\(")
LOOP = re.compile(r"\bwhile\s*\(|for\s*\(\s*;\s*;\s*\)")
WAIT = re.compile(r"(?<![\w.])wait\s*\(|\.wait\s*\(|\.await\w*\s*\(|\.acquire\w*\s*\(|\.take\s*\(|\.invokeAll\s*\(|\.invokeAny\s*\("
                  r"|LockSupport\s*\.\s*park|\b(?:Future|CompletableFuture|FutureTask|CountDownLatch|CyclicBarrier|Semaphore"
                  r"|Phaser|Exchanger|BlockingQueue|ArrayBlockingQueue|LinkedBlockingQueue|SynchronousQueue|ReentrantLock|Condition)\b")
# "name(...) {" with nothing that makes it a statement: a method (or a class made in place).
METHOD = re.compile(r"(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w\s,.]+)?$")
NOT_METHODS = {"if", "for", "while", "switch", "catch", "synchronized", "try", "else", "do"}


def code_only(text):
    # block comments and Javadoc (keep their line breaks so line numbers stay right)
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)                     # line comments
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)          # string contents
    return text


def imports_gone(code):
    """The import lines blank: an import alone does not wait."""
    return re.sub(r"^\s*import\s[^\n]*", "", code, flags=re.M)


def method_of_each_line(code):
    """{line number: the method around the line's start} (the innermost one; a lambda counts as its method)."""
    stack, start, line, out = [], 0, 1, {1: None}
    for i, c in enumerate(code):
        if c == "\n":
            line += 1
            named = [n for n in stack if n]
            out[line] = named[-1] if named else None
        elif c in ";{}":
            if c == "{":
                head = code[start:i].strip()
                m = METHOD.search(head)
                stack.append(m.group(1) if m and m.group(1) not in NOT_METHODS and "->" not in head else None)
            elif c == "}" and stack:
                stack.pop()
            start = i + 1
    return out


def problems_in(rel, code, loops=True):
    """The problems in one library file's code (without comments)."""
    found = []
    methods = method_of_each_line(code)
    loop_ok = ALLOWED_LOOPS.get(rel, (set(), ""))[0]
    wait_ok = ALLOWED_WAITS.get(rel, (set(), ""))[0]
    for n, line in enumerate(code.splitlines(), 1):
        where = methods.get(n)
        if SLEEP.search(line):
            found.append(f"library/{rel}:{n}: sleep/join in the library: {line.strip()}")
        if loops and LOOP.search(line) and where not in loop_ok:
            found.append(f"library/{rel}:{n}: a loop in {where}(), not a method that may block: {line.strip()}")
        if WAIT.search(line) and where not in wait_ok:
            found.append(f"library/{rel}:{n}: a wait in {where}(), not a method that may block: {line.strip()}")
    return found


def self_test():
    """The checks must be able to fail: each of these lines is a wait they must find."""
    for bad in ["latch.await();", "lock.wait();", "wait(10);", "sem.acquire();", "queue.take();", "LockSupport.park();",
                "Future<Integer> f = pool.submit(job);", "Thread.sleep(5);", "worker.join();", "while (busy) { }", "for (;;) { }"]:
        code = "class A {\n  void update() {\n    " + bad + "\n  }\n}\n"
        if not problems_in("x/A.java", code):
            return f"it misses: {bad}"
    good = "class A {\n  void update() {\n    int v = value.get();\n    await = 1;\n    m.put(1, 2);\n  }\n}\n"
    if problems_in("x/A.java", good):
        return "it objects to ordinary code"
    other = "class A {\n  void run() {\n    while (a) { }\n  }\n  void update() {\n    while (a) { }\n  }\n}\n"
    if len(problems_in("util/BackgroundWorker.java", other)) != 1:
        return "the allow-list must name methods, not whole files"
    return None


problems = []
wrong = self_test()
if wrong:
    problems.append("check_blocking.py itself: " + wrong)
for java in sorted(LIB.rglob("*.java")):
    rel = java.relative_to(LIB).as_posix()
    problems += problems_in(rel, imports_gone(code_only(java.read_text())))
for rel, (names, _) in list(ALLOWED_LOOPS.items()) + list(ALLOWED_WAITS.items()):
    code = code_only((LIB / rel).read_text())
    for name in names:
        if not re.search(r"\b%s\s*\(" % name, code):
            problems.append(f"tools/check_blocking.py: {rel} has no method {name}(): take it off the list")

for java in sorted(EXAMPLES.rglob("*.java")):
    code = imports_gone(code_only(java.read_text()))
    for n, line in enumerate(code.splitlines(), 1):
        if SLEEP.search(line) or WAIT.search(line):
            problems.append(f"{java.relative_to(ROOT)}:{n}: a wait in an example: use a non-blocking wait: {line.strip()}")

if problems:
    print("Blocking check FAILED:")
    for p in problems:
        print("  - " + p)
    print("If a method really must block, add it to ALLOWED_LOOPS or ALLOWED_WAITS with the reason, and say BLOCKS in its Javadoc.")
    sys.exit(1)
print("Blocking check passed.")
