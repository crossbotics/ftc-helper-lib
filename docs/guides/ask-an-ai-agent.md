# Ask an AI agent about the library

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose).

**For:** students and mentors who use an AI coding agent (Claude Code, Codex, GitHub Copilot, Cursor or
similar), or an AI chat (Claude, ChatGPT).

An AI agent can answer "how do I..." questions about the library and help you write your robot code. It
gives better answers when it reads these docs instead of guessing from memory. Without them, it can make up
methods that do not exist. The library gives it two ways to read the docs:

| You use | Use this | Why |
| --- | --- | --- |
| A coding agent in your robot project | The **ftc-helper skill** | It reads the docs of YOUR library version, and your TeamCode. It can change and build your code. |
| An AI chat in a web browser | The **docs in one file** | No setup. The chat cannot see your code, so paste the part you ask about. |

## The ftc-helper skill (coding agents)

A skill is a set of written instructions that the agent follows. This one is in
`ftc-helper-lib/skills/ftc-helper/SKILL.md`. It tells the agent where each answer is in the docs. It also gives the
library's rules: change TeamCode only, nothing in the loop waits, numbers come from the finders.

### Set it up

Do this once per robot project. After you commit and push it, everyone on the team gets it.

1. In the top project folder (next to `TeamCode`), open `AGENTS.md`. Make the file if it is not there.
   Codex, Copilot, Cursor and most other agents read this file.
2. Add these lines:

   ```markdown
   ## ftc-helper-lib

   For questions about ftc-helper-lib, and for any change to the robot code that uses it, read and
   follow ftc-helper-lib/skills/ftc-helper/SKILL.md. To make or update the simulator's robot file,
   follow ftc-helper-lib/skills/robot-file/SKILL.md.
   ```

3. **With Claude Code:** in the same folder, open `CLAUDE.md` (make it if it is not there) and add this
   line. Claude Code reads `CLAUDE.md`, and this line tells it to read `AGENTS.md` too.

   ```markdown
   @AGENTS.md
   ```

4. Commit and push the two files ([Git and GitHub](github.md)).

The lines point to the skill inside the library folder. **Why:** when you
[update the library](../02-keep-up-to-date.md), the skill and the docs update with it, and they always match
the library version your project uses. Do not copy the skill into your project: a copy gets old.

### Ask it

Open the agent in your robot project and ask in your own words. For example:

- "How do I make the lift go to a height with a button?"
- "INIT stops with MEASURE. What do I do?"
- "Add a claw on the servo called claw to our robot."
- "Make an auto that drives forward, scores, and parks."

The agent reads your Robot.java, Tunables.java and Controls.java, finds the docs page, and answers with
code for your files. It links the page it used, so you can read more. When it changes your code, it builds
it, and it tells you what to check on the real robot.

## The docs in one file (AI chats)

The website has the docs as plain text files that an AI chat can read:

| File | What is in it |
| --- | --- |
| [crossbotics.com/llms.txt](https://crossbotics.com/llms.txt) | A list of every docs page, with one line about each |
| [crossbotics.com/llms-small.txt](https://crossbotics.com/llms-small.txt) | The main pages in full: pick your start, the five files, controls, building autos, tuning, the cookbook and troubleshooting |
| [crossbotics.com/llms-full.txt](https://crossbotics.com/llms-full.txt) | Every page in full. It is large: some chats cannot read all of it |

1. Download `llms-small.txt` and attach it to the chat. Some chats can also read the link directly.
2. Ask your question. Paste the part of your code that it is about.

These files match the newest release on the website. When your project uses an older library version, an
answer can name something your version does not have. The skill does not have this problem.

## When the answer is wrong

An AI agent can be wrong. Build the code, and try it in the [simulator](../simulator/README.md) before you try it on
the robot. When the agent gives an answer that does not match these docs, or the docs do not answer your
question, [tell us](https://github.com/crossbotics/ftc-helper-lib/issues/new/choose). We fix the docs, and
the next answer is better for every team.
