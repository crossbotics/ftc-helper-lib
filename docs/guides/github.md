# Git and GitHub for FTC teams

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** every student who changes the robot code, and the mentor. **You need:** a GitHub account
(free; GitHub's rules say you must be 13 or older), and the robot project on GitHub.

**Why this matters.** Git keeps every saved version of your robot code. With it you can:

- **undo a mistake**, even one from last week;
- **share code** between laptops, so the whole team has the same robot program;
- **try an idea** without breaking the code that works;
- **get back to the exact code** that ran at the last event.

Without it, the code lives on one laptop. When that laptop breaks, or somebody leaves it at home on the day of an
event, the code is gone.

Each step has two ways. Use the one your team likes:

- **The command line:** the **Terminal** tab at the bottom of Android Studio. Type a line, press Enter.
- **GitHub Desktop:** a free app with buttons ([desktop.github.com](https://desktop.github.com)).
  The menu names below are the Mac names. On Windows they are the same words with small letters
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
| **conflict** | two people changed the same lines, and git asks you which version to keep. |
| **submodule** | a repository inside a repository. ftc-helper-lib is one, in the `ftc-helper-lib` folder. |
| **tag** | a name for one commit, like `v0.16.0` or `event-2026-11-14`. It never moves. |
| **release** | a tag on GitHub with notes on what changed. The library's versions are tags. |

## The team workflow

The five team rules: [Set up 8, team rules](../setup/08-every-practice.md#team-rules).

## Set up once

Step by step, for GitHub Desktop and the command line: [Set up 3: GitHub and the robot project](../setup/03-github-and-the-project.md#1-tell-git-who-you-are).

## Clone the robot project (copy it to a laptop)

Step by step: [Set up 3, get the robot project](../setup/03-github-and-the-project.md#2-get-the-robot-project).
When the `ftc-helper-lib` folder is empty after a clone, run `git submodule update --init`.

## Every practice: pull, commit, push

Step by step: [Set up 8: every practice](../setup/08-every-practice.md).

## Try an idea on a branch

A branch lets you change code without touching `main`. When the idea does not work, `main` is still fine.

Command line (`auto-far-start` is the branch's name: short, small letters, dashes):

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git switch -c auto-far-start
git push -u origin auto-far-start
```

`git switch -c` makes the branch and moves you onto it. `git push -u origin ...` puts it on GitHub.
You need it only the first time. After that, `git push` is enough. Commit on the branch as usual.

Switch back to `main` (commit your changes first):

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git switch main
```

GitHub Desktop: **Current Branch** (top bar) > **New Branch**. Type the name, click **Create
Branch**, then **Publish branch**. To switch, click **Current Branch** and choose a branch.

**Note:** the robot runs the code of the branch that you built and installed. Before you install the
app for a match, check which branch you are on. `git status` says `On branch ...`, and Desktop shows it under **Current Branch**.

## Merge a branch into main

When the idea works on the robot, bring it into `main`.

Command line:

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git switch main
git pull
git merge --no-edit auto-far-start
git push
```

These lines go to `main`, get the newest `main`, add the branch's commits to it, and send it to GitHub.
`--no-edit` keeps the usual message and opens no text editor.
Build and test `main` once more after a merge.

GitHub Desktop: switch to **main**, click **Fetch origin** / **Pull origin**. Then **Branch > Merge
into Current Branch…**, choose `auto-far-start`, and click **Create a merge commit**. Then **Push
origin**.

Teams that like to review code first can open a pull request on GitHub instead. It does the same merge,
but a teammate reads the changes first.

## A simple conflict

Two people changed the same line. Git cannot choose, so it asks you. For example, on `main`
an autonomous drives `30` inches to the goal, and on your branch it drives `28`. The merge stops:

```text
CONFLICT (content): Merge conflict in TeamCode/.../autos/NearCycle.java
Automatic merge failed; fix conflicts and then commit the result.
```

Open the file. Git wrote both versions into it:

```text
<<<<<<< HEAD
         .forward("To the goal", 30, 0.6)
=======
         .forward("To the goal", 28, 0.6)
>>>>>>> auto-far-start
```

The part above `=======` is the version you are on (`main`). The part below is the branch's. Keep the
right line. Delete the other line and the three marker lines (`<<<<<<<`, `=======`, `>>>>>>>`):

```java
         .forward("To the goal", 28, 0.6)
```

Then build. A marker line left in the file stops the build. Then finish the merge:

Command line:

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git add TeamCode
git commit -m "Merge auto-far-start: 28 in to the goal"
git push
```

**When you are not sure:** stop the merge and go back to how it was before with `git merge --abort`.

GitHub Desktop: the merge window lists the conflicted files. Click **Open in** (your editor) next to a
file, fix it as above, and save. When every file says it is resolved, click **Continue Merge**, then **Push
origin**. **Abort Merge** goes back.

## Undo a bad change

**Changes not committed yet** (you want the file back as it was at the last commit):

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git restore TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Tunables.java
```

GitHub Desktop: **Changes** tab, right-click the file > **Discard Changes…**. Desktop puts the
discarded changes in the computer's trash.

**A commit that is already pushed.** Do not delete history. Add a new commit that undoes it. Find the
commit's id (the first letters are enough):

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git log --oneline
git revert --no-edit 3f2c1a9
git push
```

`git log --oneline` lists the commits, newest first. Press **q** to leave the list. `git revert`
makes a new commit that does the opposite of `3f2c1a9` (use your commit's id). `--no-edit` keeps git
from opening a text editor for the message.

GitHub Desktop: **History** tab, right-click the commit > **Revert Changes in Commit**, then **Push
origin**.

**Why a revert and not a delete:** your teammates already have the commit. Deleting pushed history breaks their
copies. A revert is safe for everyone.

## What never to commit

| Never commit | Why |
| --- | --- |
| `local.properties` | it holds the path to the Android tools on YOUR laptop. On another laptop it breaks the build |
| `build/` folders, `.gradle/` | every build makes them. They are large, and different on every laptop |
| passwords, Wi-Fi keys, API keys, tokens | anyone who can read the repository can use them, forever (they stay in the history) |
| Driver Station or Control Hub passwords | the same |
| photos or videos of students | privacy: ask first, and keep them out of a public repository |
| very large files (CAD exports, videos) | they make every clone slow. Keep them in a shared drive |

The FtcRobotController project's `.gitignore` file already keeps most of these out: git does not list
the files it names. Check `git status` (or the Desktop **Changes** list) before every commit. When you
see one of these, do not commit it. Ask the mentor to add it to `.gitignore`.

**When someone pushes a password by mistake:** change that password at once. Removing it from the history is
not enough, because someone may already have a copy.

## Private or public, and student privacy

- **Private repository:** only the people you add can see it. Use it while you work on the season's
  secrets. GitHub gives free private repositories.
- **Public repository:** anyone can read it. Use it to share with other teams, which FIRST
  encourages. Check the season's game manual for any rule about sharing code.
- **Either way, protect students:** use usernames instead of full names, and the private noreply email. Put no
  school names with student names, and no photos without permission. The history keeps everything: a name
  in a commit message stays there.
- **Use an organization** (a team account on GitHub) for the robot repository, not one student's
  account. Students graduate, and the team stays. Add each student as a member, and at least two mentors as
  owners.

## Before an event: backups and a tag

1. **Commit and push everything** you will run. Check that `git status` says
   `nothing to commit, working tree clean` and `Your branch is up to date with 'origin/main'`.
2. **Tag the code** you will run, so you can always get back to it:

   ```bash
   git tag event-2026-11-14
   git push origin event-2026-11-14
   ```

   GitHub Desktop: **History** tab, right-click the newest commit > **Create Tag…**. Type the name and
   create it. The next **Push origin** sends it.
3. **Have a second laptop** with the same code, cloned and built, in case the first one fails. Pull on it
   the night before.
4. **No library update the week before the event** ([why](../02-keep-up-to-date.md#when-not-to-update)).
5. **At the event, with no internet:** commit as usual (commits work offline). Push when you have
   internet again.

To get the event's code back later (to look at it, or to build it again), run:

**Where:** Android Studio's **Terminal** tab, in your **robot project's folder** (where `TeamCode` is).

```bash
git switch --detach event-2026-11-14
```

`git switch main` goes back to the newest code.

## Problems

| What you see | Why, and what to do |
| --- | --- |
| `Project directory '.../ftc-helper-lib/library' does not exist` | the clone did not get the submodule: `git submodule update --init` |
| `git status` says `modified: ftc-helper-lib (new commits)` and you did not mean to change it | your library folder is at another version: `git submodule update` |
| `rejected ... (fetch first)` on push | a teammate pushed first. Pull, then push |
| `Your local changes would be overwritten by merge` on pull | commit (or discard) your changes first, then pull |
| `CONFLICT` | see [a simple conflict](#a-simple-conflict) |
| `Author identity unknown` | do [set up once](#set-up-once) |
| `You have divergent branches` on pull | do the `pull.rebase false` line in [set up once](#set-up-once), then pull again |
| A strange text screen opens (vim) with a message to edit | git wants a commit message. Type `:wq` and press Enter to save and leave (`:q!` and Enter leaves without saving and stops) |
| You committed on `main` but meant a branch | when you have not pushed yet, `git switch -c my-branch` takes the commits with you. Then ask the mentor to put `main` back |
| Everything is a mess | stop, do not force anything, and ask the mentor. Nothing pushed is lost: GitHub has it. |

## Next

- Keep the library up to date: [Keep it up to date](../02-keep-up-to-date.md).
- Add the library to a new robot project: [Add the library](../setup/04-add-the-library.md).
- Several autos and choosing one at the field: [Having multiple autos](../building-autos/03-multiple-autos.md).
