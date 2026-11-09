# Set up 8: every practice: pull, change, commit, push

**Set up, page 8 of 9.** Everyone who changes code does this, every practice.

**Why:** git keeps every saved version of the code. You can undo a mistake, share code between
laptops, and get back the exact code that ran at the last event.

## The loop

### 1. Pull first: get the team's newest code

**GitHub Desktop:** click **Fetch origin** (top bar). If it changes to **Pull origin**, click it.

**Command line:**

**Where:** Android Studio's **Terminal** tab, in the **project folder** (a new Terminal tab starts there). ([Which folder](09-the-terminal.md#which-folder))

```bash
git pull
git submodule update
```

**Why `git submodule update`:** it puts the library at the version the project expects.

### 2. Change, build, test

One change at a time. Build (hammer), install (Run), test on the robot.

### 3. Commit: save a snapshot each time a small change works

**GitHub Desktop:**

1. The **Changes** tab lists the changed files. Untick a file you do not want.
2. Type a message in **Summary** (bottom left).
3. Click **Commit to main**.

**Command line** (the same place):

```bash
git status
git add TeamCode
git commit -m "Auto: park 2 in further left"
```

Read what `git status` lists, so you only commit what you meant to.

**A good message** says what changed and why: `Lift: high step 2400 -> 2350 (it hit the stop)`.
Not `stuff` or `fix`.

### 4. Push before you leave

**GitHub Desktop:** click **Push origin**.

**Command line** (the same place): `git push`

The push is refused (`rejected ... fetch first`)? A teammate pushed first. Pull (step 1), then push again.

## Team rules

1. **`main` always builds and works.** Never push code that does not build.
2. **Pull before you start.**
3. **Commit often,** one small working change at a time.
4. **Push before you leave.** Until then, your commits are only on your laptop.
5. **Two people, one file:** talk first ("I am changing `Tunables.java`").

## Done when

You pulled at the start, and your work is pushed at the end.

**More git** (branches, conflicts, undo, before an event): [Git and GitHub for FTC teams](../guides/github.md).

**Set up is done.** Next you start using the library: [Quick start: pick your road](../quick-start.md#pick-your-road).
