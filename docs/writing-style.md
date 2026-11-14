# Writing style for the docs

Every page in `docs/` follows these rules. Our readers are high-school students, many with little Java and
some with English as a second language. The rules come from ASD-STE100 Simplified Technical English and
from [stop-slop](https://github.com/hardikpandya/stop-slop). Code comments follow
[Commenting style](commenting-style.md).

Finished examples: [Intake](reference/parts/intake.md) and [Camera](reference/parts/camera.md).

## Words

- **US English:** color, center, gray, meter, canceled, catalog, license.
- **Plain words.** A technical word gets a short explanation the first time a page uses it: "a pipeline (a set
  of camera settings you save on the Limelight by number)".
- **No filler words:** just, really, simply, actually, basically, easily, quickly, of course.
- **No vague extremes.** Write "always", "never" or "every" only when it is an exact fact. Not "rarely": write
  when (for example, "only for a special case").
- **No time estimates** ("10 minutes", "about an hour").
- **Measure from edges, parts, photos or the catalog,** never from the robot's center.

## Sentences

- **Short sentences.** One idea per sentence, about 20 words or fewer.
- **Active voice.** Name who does it: "the camera reads them at INIT", not "they are read at INIT". The reader
  is "you".
- **Instructions are commands:** "Press INIT." "Copy the two lines."
- **Why before how.** Say what a thing is for before you use its name, and what problem a step fixes before
  the step.
- **No questions as labels.** "**With one driver:**", not "**One driver?**".
- **No announcements.** Cut "this page has three tables", "here is how", "let's look at". Start with the fact.
- **No "not X, it is Y".** Say what it is.
- **No em dashes (—).** Use a colon, a comma or a new sentence. In a table, an empty cell is "-".

## Pages

- A **"Where:"** line above every command: which terminal, which folder.
- Walkthrough pages (setup, pick your start, the five files, building autos) end with **Done when**, **Next**
  and **Look it up**.
- Reference pages start with one line on what the part is, then the tables. No intro text.
- A table's question column is "What it tells you", and its cells start with "whether" for a true/false.

## What never changes in a style pass

- Code blocks, inline code, and `<!-- recipe -->` and `<!-- tunables -->` markers (tools check them).
- Heading text: other pages link to it.
- Link targets, numbers and facts. A fact that looks wrong goes to the mentor; do not fix it in a style pass.
