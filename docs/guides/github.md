# Git and GitHub for FTC teams

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** every student who changes the robot code, and the mentor. **You need:** a GitHub account
(free; GitHub's rules say you must be 13 or older), and the robot project on GitHub. **Time:** 30
minutes to read, then a few minutes at every practice.

**Why this matters.** Git keeps every saved version of your robot code. With it you can:

- **undo a mistake**, even one from last week;
- **share code** between laptops, so the whole team has the same robot program;
- **try an idea** without breaking the code that works;
- **get back to the exact code** that ran at the last event.

Without it, the code lives on one laptop. When that laptop breaks (or is left at home on the day of an
event), the code is gone.

Each step on this page is shown two ways. Use the one your team likes:

- **The command line:** the **Terminal** tab at the bottom of Android Studio. Type a line, press Enter.
- **GitHub Desktop:** a free app with buttons ([desktop.github.com](https://desktop.github.com)).
  Menu names below are the Mac names; on Windows they are the same words with small letters
  (**Merge into Current Branch…** is **Merge into current branch…**).

Contents: [words](#words-in-one-line-each) · [team workflow](#the-team-workflow) ·
[set up once](#set-up-once) · [clone](#clone-the-robot-project-copy-it-to-a-laptop) ·
[every practice](#every-practice-pull-commit-push) · [branches](#try-an-idea-on-a-branch) ·
[merge](#merge-a-branch-into-main) · [conflicts](#a-simple-conflict) · [undo](#undo-a-bad-change) ·
[never commit](#what-never-to-commit) · [private or public](#private-or-public-and-student-privacy) ·
[before an event](#before-an-event-backups-and-a-tag) · [problems](#problems)

## Words, in one line each

| Word | Means |
| --- | --- |
| **repository** ("repo") | a project folder whose history git keeps. Your robot project is one. |
| **GitHub** | a website that keeps a copy of your repository online, so the team can share it. |
| **clone** | copy a repository from GitHub to your laptop (once per laptop). |
| **commit** | save a snapshot of your changes in the history, with a short message. |
| **push** | send your commits from your laptop to GitHub. |
| **pull** | get your teammates' commits from GitHub onto your laptop. |
| **branch** | a separate line of commits, for trying something without changing `main`. |
| **main** | the main branch. On our team it always builds and works. |
| **merge** | bring the commits of one branch into another (for example, a tested idea into `main`). |
| **conflict** | two people changed the same lines; git asks you which version to keep. |
| **submodule** | a repository inside a repository. ftc-helper-lib is one, in the `ftc-helper-lib` folder. |
| **tag** | a name for one commit, like `v0.16.0` or `event-2026-11-14`. It never moves. |
| **release** | a tag on GitHub with notes on what changed. The library's versions are tags. |

## The team workflow

Five rules. Put them on the wall of the pit.

1. **`main` always works.** Anyone can clone `main`, build it, and drive the robot. Never push code to
   `main` that does not build.
2. **Pull before you start.** Every practice, first get the newest code. Then you do not work on an old
   copy.
3. **Commit often, with a good message.** One commit per small change that works. The message says
   what changed and why, in a few words: `Lift: high step 2400 -> 2350 (it hit the stop)`.
   Bad: `stuff`, `fix`, `asdf`.
4. **Push before you leave.** Your commits are only on your laptop until you push. The next person
   cannot get them otherwise.
5. **Try big ideas on a branch.** A new autonomous, a new part, a big change: make a branch. Merge it
   into `main` only after it is tested on the robot.

**One laptop at a time per file.** Two people who change the same file at the same time get a
conflict. Talk first: "I am changing `Tunables.java`".

## Set up once

**Command line.** Check that git is there, and tell it your name (it goes in every commit):

```bash
git --version
git config --global user.name "YOUR-NAME"
git config --global user.email "12345678+YOUR-NAME@users.noreply.github.com"
git config --global pull.rebase false
```

`git --version` says `not recognized` (Windows)? GitHub Desktop's own git does not work in the Terminal.
See [git is not recognized](../troubleshooting.md#autonomous-choice-and-git-problems).

**Change the two lines before you run them:** `YOUR-NAME` is your first name or GitHub username, and
the email is YOUR private GitHub address (below), not the example's. Keep the quotes.

The last line says what `git pull` does when you and a teammate both made commits: join them with a
merge (the simplest way). Without it, a new git stops with `You have divergent branches`.

Use your first name or your GitHub username, not your full name, and the private email GitHub gives you
(below). **Why:** the history of a public repository is public, names and emails included.

**GitHub Desktop.** Install it, then **GitHub Desktop > Settings > Accounts** (Windows: **File >
Options > Accounts**) and sign in. Desktop then knows your name and email.

**Keep your email private.** On github.com: your picture (top right) > **Settings** > **Emails** >
tick **Keep my email addresses private**. GitHub shows your private address there
(`12345678+name@users.noreply.github.com`). Use that one in `git config`.

## Clone the robot project (copy it to a laptop)

Do this once per laptop. The robot project has the library inside it as a **submodule**, so the clone
must get the submodule too.

**Command line** (in a folder where you keep projects; the URL is on the repository's GitHub page,
green **Code** button):

```bash
git clone --recurse-submodules https://github.com/OUR-TEAM/OUR-ROBOT.git
```

`--recurse-submodules` also gets the `ftc-helper-lib` folder. Forgot it? Go into the project folder
and run:

```bash
git submodule update --init
```

**GitHub Desktop:** **File > Clone Repository…**, choose the **GitHub.com** tab (or **URL** and paste
it), choose the local path, click **Clone**. Desktop gets submodules by itself.

Then open the project folder in Android Studio (**File > Open**). **Android Studio's own Git > Clone
does not always get submodules**: if the build says
`Project directory '.../ftc-helper-lib/library' does not exist`, run `git submodule update --init`.

## Every practice: pull, commit, push

**1. Pull first** (get the team's newest code).

Command line, in the project folder:

```bash
git pull
git submodule update
```

`git submodule update` puts the library at the version the project expects (only needed when someone
changed the library version; it does no harm otherwise).

GitHub Desktop: click **Fetch origin** (top bar). If it changes to **Pull origin**, click it.
Desktop updates the submodule by itself.

**2. Work.** Change the code, build, test on the robot.

**3. Commit** (save a snapshot) each time a small change works.

Command line:

```bash
git status
git add TeamCode
git commit -m "Auto: park 2 in further left"
```

`git status` lists what changed: read it, so you only commit what you meant to. `git add TeamCode`
picks the changes in the `TeamCode` folder for the commit (`git add .` picks every change; check
`git status` first). `git commit -m "..."` saves them with the message.

GitHub Desktop: the **Changes** tab lists the changed files. Untick a file you do not want. Type a
message in **Summary** (bottom left), click **Commit to main**.

**4. Push** (send your commits to GitHub) before you leave, or when a teammate needs them.

Command line:

```bash
git push
```

GitHub Desktop: click **Push origin** (top bar).

If the push is refused (`rejected ... fetch first`), a teammate pushed first. Pull (step 1), then push
again.

## Try an idea on a branch

A branch lets you change code without touching `main`. If the idea does not work, `main` is still fine.

Command line (`auto-far-start` is the branch's name: short, small letters, dashes):

```bash
git switch -c auto-far-start
git push -u origin auto-far-start
```

`git switch -c` makes the branch and moves you onto it. `git push -u origin ...` puts it on GitHub
(only the first time; after that `git push` is enough). Commit on it as usual.

Switch back to `main` (commit your changes first):

```bash
git switch main
```

GitHub Desktop: **Current Branch** (top bar) > **New Branch**, type the name, click **Create
Branch**, then **Publish branch**. To switch, click **Current Branch** and choose a branch.

**Note:** the robot runs the code of the branch you built and installed. Check which branch you are on
(`git status` says `On branch ...`; Desktop shows it under **Current Branch**) before you install the
app for a match.

## Merge a branch into main

When the idea works on the robot, bring it into `main`.

Command line:

```bash
git switch main
git pull
git merge --no-edit auto-far-start
git push
```

Read it as: go to `main`, get the newest `main`, add the branch's commits to it (`--no-edit`: with
the usual message, no text editor), send it to GitHub.
Build and test `main` once more after a merge.

GitHub Desktop: switch to **main**, click **Fetch origin** / **Pull origin**. Then **Branch > Merge
into Current Branch…**, choose `auto-far-start`, and click **Create a merge commit**. Then **Push
origin**.

(Teams that like to review code first can also open a pull request on GitHub instead: the same merge,
but a teammate reads the changes first.)

## A simple conflict

Two people changed the same line. Git cannot choose, so it asks you. Example: on `main`,
`toGoalInches = 30`; on your branch, `toGoalInches = 28`. The merge stops:

```text
CONFLICT (content): Merge conflict in TeamCode/.../Tunables.java
Automatic merge failed; fix conflicts and then commit the result.
```

Open the file. Git wrote both versions into it:

```text
<<<<<<< HEAD
        public static double toGoalInches = 30;
=======
        public static double toGoalInches = 28;
>>>>>>> auto-far-start
```

The part above `=======` is the version you are on (`main`); the part below is the branch's. Keep the
right line, and delete the other line and the three marker lines (`<<<<<<<`, `=======`, `>>>>>>>`):

```java
        public static double toGoalInches = 28;
```

Then build (a marker line left in the file does not build), and finish the merge:

Command line:

```bash
git add TeamCode
git commit -m "Merge auto-far-start: toGoalInches 28"
git push
```

Not sure? Stop the merge and go back to how it was before: `git merge --abort`.

GitHub Desktop: the merge window lists the conflicted files. Click **Open in** (your editor) next to a
file, fix it as above, save. When every file says it is resolved, click **Continue Merge**, then **Push
origin**. **Abort Merge** goes back.

## Undo a bad change

**Changes not committed yet** (you want the file back as it was at the last commit):

```bash
git restore TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Tunables.java
```

GitHub Desktop: **Changes** tab, right-click the file > **Discard Changes…**. Desktop puts the
discarded changes in the computer's trash.

**A commit that is already pushed.** Do not delete history; add a new commit that undoes it. Find the
commit's id (the first letters are enough):

```bash
git log --oneline
git revert --no-edit 3f2c1a9
git push
```

`git log --oneline` lists the commits, newest first (press **q** to leave the list). `git revert`
makes a new commit that does the opposite of `3f2c1a9` (use your commit's id). `--no-edit` keeps git
from opening a text editor for the message.

GitHub Desktop: **History** tab, right-click the commit > **Revert Changes in Commit**, then **Push
origin**.

**Why not delete the commit?** Your teammates already have it. Deleting pushed history breaks their
copies. A revert is safe for everyone.

## What never to commit

| Never commit | Why |
| --- | --- |
| `local.properties` | it holds the path to the Android tools on YOUR laptop; on another laptop it breaks the build |
| `build/` folders, `.gradle/` | made by every build; large, and different on every laptop |
| passwords, Wi-Fi keys, API keys, tokens | anyone who can read the repository can use them, forever (they stay in the history) |
| Driver Station or Control Hub passwords | the same |
| photos or videos of students | privacy: ask first, and keep them out of a public repository |
| very large files (CAD exports, videos) | they make every clone slow; keep them in a shared drive |

The FtcRobotController project's `.gitignore` file already keeps most of these out: git does not list
the files it names. Check `git status` (or the Desktop **Changes** list) before every commit. If you
see one of these, do not commit it; ask the mentor to add it to `.gitignore`.

**A password was pushed by mistake:** change that password at once. Removing it from the history is
not enough: someone may already have a copy.

## Private or public, and student privacy

- **Private repository:** only the people you add can see it. Good while you work on the season's
  secrets. GitHub gives free private repositories.
- **Public repository:** anyone can read it. Good for sharing with other teams, which FIRST
  encourages. Check the season's game manual for any rule about sharing code.
- **Either way, protect students:** usernames instead of full names, the private noreply email, no
  school names with student names, no photos without permission. The history keeps everything: a name
  in a commit message stays there.
- **Use an organization** (a team account on GitHub) for the robot repository, not one student's
  account. Students graduate; the team stays. Add each student as a member, and at least two mentors as
  owners.

## Before an event: backups and a tag

1. **Commit and push everything** you will run. Check that `git status` says
   `nothing to commit, working tree clean` and `Your branch is up to date with 'origin/main'`.
2. **Tag the code** you will run, so you can always get back to it:

   ```bash
   git tag event-2026-11-14
   git push origin event-2026-11-14
   ```

   GitHub Desktop: **History** tab, right-click the newest commit > **Create Tag…**, type the name,
   create it. The next **Push origin** sends it.
3. **A second laptop** with the same code, cloned and built, in case the first one fails. Pull on it
   the night before.
4. **No library update the week before the event** ([why](../02-keep-up-to-date.md#when-not-to-update)).
5. **At the event, with no internet:** commit as usual (commits work offline). Push when you have
   internet again.

Get the event's code back later (to look at it, or to build it again):

```bash
git switch --detach event-2026-11-14
```

(`git switch main` goes back to the newest code.)

## Problems

| What you see | Why, and what to do |
| --- | --- |
| `Project directory '.../ftc-helper-lib/library' does not exist` | the submodule was not cloned: `git submodule update --init` |
| `git status` says `modified: ftc-helper-lib (new commits)` and you did not mean to change it | your library folder is at another version: `git submodule update` |
| `rejected ... (fetch first)` on push | a teammate pushed first: pull, then push |
| `Your local changes would be overwritten by merge` on pull | commit (or discard) your changes first, then pull |
| `CONFLICT` | see [a simple conflict](#a-simple-conflict) |
| `Author identity unknown` | do [set up once](#set-up-once) |
| `You have divergent branches` on pull | do the `pull.rebase false` line in [set up once](#set-up-once), then pull again |
| A strange text screen opens (vim) with a message to edit | git wants a commit message. Type `:wq` and press Enter to save and leave (`:q!` and Enter leaves without saving and stops) |
| You committed on `main` but meant a branch | not pushed yet: `git switch -c my-branch` takes the commits with you; then ask the mentor to put `main` back |
| Everything is a mess | stop, do not force anything, and ask the mentor. Nothing pushed is lost: GitHub has it. |

## Next

- Keep the library up to date: [Keep it up to date](../02-keep-up-to-date.md).
- Add the library to a new robot project: [Add it to a project](../01-add-to-project.md).
- Several autos and choosing one at the field: [Several autonomous modes](autos.md).
