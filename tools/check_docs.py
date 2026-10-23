#!/usr/bin/env python3
"""Checks that the docs and tutorials match the code. Run from the repo root:

    python3 tools/check_docs.py

It fails (exit 1) when:
  1. a Markdown link points to a file that does not exist, or to a heading (#anchor) that does not exist;
  2. a public library class is not named in any docs/modules/*.md page;
  3. a tutorial program (examples/.../tutorials) is not linked from any tutorial page;
  4. a tutorial page has no link to its full program;
  5. a java code block in docs/cookbook.md is not the same as its marked region in the cookbook's Java
     files (examples/.../cookbook: "// recipe: name" ... "// end recipe"), or a region is not shown;
     a guide that shows a recipe (the same "<!-- recipe: name -->" marker) is checked the same way;
  6. a cookbook recipe that mentions the tuning menu does not say to copy the value into Tunables.java;
  9. an example file has a comment longer than 3 "//" lines in a row or 8 lines of "/** */" (the why goes
     in the docs); a docs code box with annotation markers "(1)!" is not followed by the numbered list 1..n;
  8. a recipe's code box does not say which file it goes in (```java title="Robot.java"), worked out
     from where the recipe sits in the cookbook's Java;
  7. a recipe that shows a Tunables section (a region named "tunables-...") breaks the section style:
     one number per line with a "//" comment (a table: a comment line above it), and a safety number
     (min, max, posA, posB, homeAngle, an interlock zone) written as MEASURE, never a number.

After changing a cookbook Java file, copy its regions into docs/cookbook.md with:

    python3 tools/check_docs.py --fix-cookbook
"""
import pathlib
import re
import sys
import textwrap

ROOT = pathlib.Path(__file__).resolve().parent.parent
LIB = ROOT / "library/src/main/java"
TUT_CODE = ROOT / "examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials"
TUT_TEST = ROOT / "examples/src/test/java/com/crossbotics/ftchelper/examples/tutorials"
COOKBOOK_MD = ROOT / "docs/cookbook.md"
COOKBOOK_CODE = ROOT / "examples/src/main/java/com/crossbotics/ftchelper/examples/cookbook"
RECIPE_START = re.compile(r"^\s*// recipe: ([a-z0-9-]+)\s*$")
RECIPE_END = re.compile(r"^\s*// end recipe\s*$")
RECIPE_MARK = re.compile(r"^<!-- recipe: ([a-z0-9-]+) -->\s*$")
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
problems = []


def markdown_files():
    for p in sorted(ROOT.rglob("*.md")):
        if any(part in ("build", ".gradle", ".git") for part in p.parts):
            continue
        yield p


def slug(heading):
    """GitHub's anchor for a heading."""
    s = heading.strip().lower()
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")


def anchors(md_path):
    found = set()
    in_code = False
    for line in md_path.read_text().splitlines():
        if line.startswith("```"):
            in_code = not in_code
        if not in_code and line.startswith("#"):
            found.add(slug(line.lstrip("#")))
    return found


def check_links():
    for md in markdown_files():
        text = md.read_text()
        text = re.sub(r"```.*?```", "", text, flags=re.S)   # ignore code blocks
        for target in LINK.findall(text):
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            path, _, anchor = target.partition("#")
            dest = (md.parent / path).resolve() if path else md
            if not dest.exists():
                problems.append(f"{md.relative_to(ROOT)}: broken link -> {target}")
                continue
            if anchor and dest.suffix == ".md" and anchor not in anchors(dest):
                problems.append(f"{md.relative_to(ROOT)}: no heading #{anchor} in {dest.relative_to(ROOT)}")


def check_classes_documented():
    module_docs = "".join(p.read_text() for p in (ROOT / "docs/modules").glob("*.md"))
    for java in sorted(LIB.rglob("*.java")):
        src = java.read_text()
        if not re.search(r"^public (final |abstract )?(class|enum|interface) ", src, re.M):
            continue
        name = java.stem
        if f"`{name}" not in module_docs and f"[{name}" not in module_docs:
            problems.append(f"{java.relative_to(ROOT)}: public class {name} is not documented in docs/modules/")


def check_tutorials():
    tut_docs = {p: p.read_text() for p in (ROOT / "docs/tutorials").glob("[0-9][0-9]-*.md")}
    all_text = "".join(tut_docs.values())
    for java in sorted(list(TUT_CODE.rglob("*.java")) + list(TUT_TEST.rglob("*.java"))):
        name = java.name
        if name not in all_text and java.stem not in all_text:
            problems.append(f"{java.relative_to(ROOT)}: tutorial program is not mentioned in any tutorial page")
    for page, text in tut_docs.items():
        if "examples/src/" not in text:
            problems.append(f"{page.relative_to(ROOT)}: tutorial page has no link to its program")


FINDERS = {"MotorFinder", "ServoFinder", "HoodFinder", "DriveTuner", "OdometryTuner", "ShotTuner", "PieceTuner"}


def recipe_file(java, start, code=""):
    """The TeamCode file a recipe goes in, worked out from where it sits in the cookbook's Java (shown as
    the code box's title, e.g. "Robot.java")."""
    rel = java.relative_to(COOKBOOK_CODE)
    if rel.parts[0] == "autos":
        return "autos/" + rel.name
    if rel.stem in ("Robot", "Controls", "Actions", "Tunables") or rel.stem in FINDERS:
        return rel.name
    # A recipe that is a whole method: what it returns says where it goes.
    if re.search(r"static Action \w+\(", code):
        return "Actions.java"
    if re.search(r"static Steps \w+\(", code):
        return "autos/YourAuto.java"
    # A topic file: the innermost method or class around the recipe says which file it belongs in.
    stack = []
    for n, line in enumerate(java.read_text().splitlines(), 1):
        if n >= start:
            break
        code = re.sub(r'"(\\.|[^"\\])*"', '""', line.split("//")[0])
        for ch in code:
            if ch == "{":
                stack.append(code.strip())
            elif ch == "}" and stack:
                stack.pop()
    for opener in reversed(stack):
        if re.search(r"\bdefine\(\)", opener):
            return "Robot.java"
        if re.search(r"\bbuttons\(", opener) or "extends ControlMap" in opener:
            return "Controls.java"
        if re.search(r"\bsteps\(", opener) or re.search(r"extends Auto\b", opener):
            return "autos/YourAuto.java"
        if re.search(r"static Action \w+\(", opener) or re.search(r"class Actions\b", opener):
            return "Actions.java"
        if re.search(r"class \w*Numbers\b", opener) or re.search(r"class Tunables\b", opener):
            return "Tunables.java"
    return None


def fence(rel, start, code):
    """The opening line of a recipe's code box: java, with the file it goes in as the box's title."""
    name = recipe_file(ROOT / rel, start, code)
    return f'```java title="{name}"' if name else "```java"


def cookbook_regions():
    """The marked regions of the cookbook's Java files: name -> (file, line, code without the common indent)."""
    regions = {}
    for java in sorted(COOKBOOK_CODE.rglob("*.java")):   # with autos/
        rel = java.relative_to(ROOT)
        name, start, body = None, 0, []
        for n, line in enumerate(java.read_text().splitlines(), 1):
            m = RECIPE_START.match(line)
            if m:
                if name:
                    problems.append(f"{rel}:{n}: recipe {m.group(1)} starts inside recipe {name} (no nesting)")
                name, start, body = m.group(1), n, []
            elif RECIPE_END.match(line):
                if not name:
                    problems.append(f"{rel}:{n}: '// end recipe' with no '// recipe: name' before it")
                elif name in regions:
                    problems.append(f"{rel}:{start}: recipe {name} is also in {regions[name][0]}:{regions[name][1]}")
                else:
                    regions[name] = (rel, start, textwrap.dedent("\n".join(body)).strip("\n"))
                name = None
            elif name:
                body.append(line)
        if name:
            problems.append(f"{rel}:{start}: recipe {name} has no '// end recipe'")
    return regions


# Code annotations (docs only): a marker "(1)!" at the end of a "//" comment in a docs code box, or a
# "// (1)!" added after the code (the same marker as Material for MkDocs). The site turns each marker into a (+) button that opens the matching item
# of the numbered list right under the box; GitHub shows the marker and the list as they are. The Java
# files have no markers: the comparisons below take them out first.
ANNOT_MARK = re.compile(r"^(.*//.*?)\s*\((\d{1,2})\)!\s*$")


def strip_marker(line):
    """The line without its annotation marker, and the marker's number (None when it has none)."""
    m = ANNOT_MARK.match(line)
    if not m:
        return line, None
    rest = m.group(1).rstrip()
    if rest.endswith("//"):
        rest = rest[:-2].rstrip()
    return rest, int(m.group(2))


def strip_markers(text):
    return "\n".join(strip_marker(l)[0] for l in text.split("\n"))


def remark(old_body, new_code):
    """new_code with the markers of old_body put back on the lines that did not change."""
    marked = {}
    for l in old_body.split("\n"):
        clean, n = strip_marker(l)
        if n is not None:
            marked.setdefault(clean, l)
    return "\n".join(marked.get(l, l) for l in new_code.split("\n"))


def check_cookbook():
    if not COOKBOOK_MD.exists():
        problems.append("docs/cookbook.md is missing")
        return
    regions = cookbook_regions()
    lines = COOKBOOK_MD.read_text().splitlines()
    shown, pending, i = set(), None, 0
    while i < len(lines):
        line = lines[i]
        m = RECIPE_MARK.match(line)
        if m:
            pending = (m.group(1), i + 1)
        elif line.startswith("```"):
            info = line[3:].strip()
            lang = info.split()[0] if info else ""
            end = i + 1
            while end < len(lines) and not lines[end].startswith("```"):
                end += 1
            body = "\n".join(lines[i + 1:end]).strip("\n")
            if lang == "java":
                if not pending:
                    problems.append(f"docs/cookbook.md:{i + 1}: java block with no '<!-- recipe: name -->' line before it")
                elif pending[0] not in regions:
                    problems.append(f"docs/cookbook.md:{pending[1]}: no recipe {pending[0]} in {COOKBOOK_CODE.relative_to(ROOT)}")
                else:
                    rel, start, code = regions[pending[0]]
                    shown.add(pending[0])
                    if line != fence(rel, start, code):
                        problems.append(f"docs/cookbook.md:{i + 1}: recipe {pending[0]}: the box should open with "
                                        f"{fence(rel, start, code)} (python3 tools/check_docs.py --fix-cookbook)")
                    if code != strip_markers(body):
                        page, java = strip_markers(body).splitlines(), code.splitlines()
                        k = next((k for k in range(max(len(page), len(java)))
                                  if k >= len(page) or k >= len(java) or page[k] != java[k]), 0)
                        problems.append(f"docs/cookbook.md:{i + 2 + k}: recipe {pending[0]} differs from {rel}:{start + 1 + k}"
                                        f"\n      page: {page[k] if k < len(page) else '(nothing)'}"
                                        f"\n      java: {java[k] if k < len(java) else '(nothing)'}")
            elif pending:
                problems.append(f"docs/cookbook.md:{pending[1]}: recipe {pending[0]} is not followed by a java block")
            pending, i = None, end
        elif pending and line.strip():
            problems.append(f"docs/cookbook.md:{pending[1]}: recipe {pending[0]}: put the marker right before its java block")
            pending = None
        i += 1
    for name in sorted(set(regions) - shown):
        rel, start, _ = regions[name]
        problems.append(f"{rel}:{start}: recipe {name} is not shown in docs/cookbook.md")
    # The code is the record: a recipe that tunes live must say to copy the value back.
    for section in re.split(r"\n(?=### )", COOKBOOK_MD.read_text()):
        if "tuning menu" in section and "Tunables.java" not in section:
            problems.append(f"docs/cookbook.md: '{section.splitlines()[0]}' mentions the tuning menu "
                            "but not copying the value into Tunables.java")


MARKED_BLOCK = re.compile(r"<!-- recipe: ([a-z0-9-]+) -->\n(```java[^\n]*)\n(.*?)\n?```", re.S)


def marked_pages():
    """Other pages (guides) that show a cookbook recipe: the same '<!-- recipe: name -->' marker."""
    return [p for p in sorted((ROOT / "docs").rglob("*.md")) if p != COOKBOOK_MD and "<!-- recipe: " in p.read_text()]


def check_marked_pages():
    """A recipe shown on another page must be the same as its Java region too (it is not compiled there)."""
    regions = cookbook_regions()
    for page in marked_pages():
        rel = page.relative_to(ROOT)
        text = page.read_text()
        for m in MARKED_BLOCK.finditer(text):
            line = text[:m.start()].count("\n") + 1
            if m.group(1) not in regions:
                problems.append(f"{rel}:{line}: no recipe {m.group(1)} in {COOKBOOK_CODE.relative_to(ROOT)}")
            elif (regions[m.group(1)][2] != strip_markers(m.group(3).strip("\n"))
                  or m.group(2) != fence(*regions[m.group(1)])):
                problems.append(f"{rel}:{line}: recipe {m.group(1)} differs from {regions[m.group(1)][0]} "
                                "(python3 tools/check_docs.py --fix-cookbook)")
        if text.count("<!-- recipe: ") != len(MARKED_BLOCK.findall(text)):
            problems.append(f"{rel}: a '<!-- recipe: name -->' marker is not right before a java block")


def fix_cookbook():
    """Rewrites each marked java block of docs/cookbook.md (and of the guides that show one) from its Java region."""
    regions = cookbook_regions()

    def fill(m):
        name = m.group(1)
        if name not in regions:
            return m.group(0)
        # keep the annotation markers of the lines that did not change
        return f"<!-- recipe: {name} -->\n{fence(*regions[name])}\n{remark(m.group(2), regions[name][2])}\n```"

    for page in [COOKBOOK_MD] + marked_pages():
        page.write_text(re.sub(r"<!-- recipe: ([a-z0-9-]+) -->\n```java[^\n]*\n(.*?)\n?```", fill, page.read_text(),
                               flags=re.S))


# Students read the example files (examples/), not the generated reference: their comments use plain
# "-" and "1." lists, not HTML. The library's own code keeps HTML (it makes the reference's lists).
HTML_IN_COMMENT = re.compile(r"^\s*\*.*</?(ul|ol|li|p|b)>", re.I)


def check_example_comments():
    for f in sorted((ROOT / "examples" / "src" / "main" / "java").rglob("*.java")):
        lines = f.read_text().splitlines()
        run, start, doc, doc_start = 0, 0, False, 0
        for n, line in enumerate(lines, 1):
            if HTML_IN_COMMENT.match(line):
                problems.append(f"{f.relative_to(ROOT)}:{n}: HTML in a comment ({line.strip()[:40]}...): "
                                "use plain '-' or '1.' lists and blank comment lines (students read these files)")
            t = line.strip()
            # Comments in code teams copy stay short: the "why" goes in the docs (an annotation, or a link).
            if t.startswith("//") and not RECIPE_START.match(line) and not RECIPE_END.match(line):
                if run == 0:
                    start = n
                run += 1
            else:
                if run > MAX_COMMENT_LINES:
                    problems.append(f"{f.relative_to(ROOT)}:{start}: {run} comment lines in a row (at most "
                                    f"{MAX_COMMENT_LINES}): keep what a team needs in the code, move the why to the docs")
                run = 0
            if t.startswith("/**"):
                doc, doc_start = True, n
            if doc and t.endswith("*/"):
                if n - doc_start + 1 > MAX_JAVADOC_LINES:
                    problems.append(f"{f.relative_to(ROOT)}:{doc_start}: a {n - doc_start + 1}-line comment block "
                                    f"(at most {MAX_JAVADOC_LINES}): keep it short, link the docs for the rest")
                doc = False


MAX_COMMENT_LINES = 3
MAX_JAVADOC_LINES = 8


def check_annotations():
    """A docs code box with annotation markers (1), (2)... is followed by a numbered list with one item each."""
    for page in sorted((ROOT / "docs").rglob("*.md")):
        rel = page.relative_to(ROOT)
        lines = page.read_text().splitlines()
        i = 0
        while i < len(lines):
            if lines[i].startswith("```") and lines[i][3:].strip():
                end = i + 1
                while end < len(lines) and not lines[end].startswith("```"):
                    end += 1
                marks = [n for n in (strip_marker(l)[1] for l in lines[i + 1:end]) if n is not None]
                if marks:
                    if marks != list(range(1, len(marks) + 1)):
                        problems.append(f"{rel}:{i + 1}: annotation markers must be (1)!, (2)!... in order, once each: {marks}")
                    j = end + 1
                    while j < len(lines) and not lines[j].strip():
                        j += 1
                    items = []
                    while j < len(lines) and (re.match(r"^\d+\. ", lines[j]) or lines[j].startswith("   ")
                                              or not lines[j].strip()):
                        m = re.match(r"^(\d+)\. ", lines[j])
                        if m:
                            items.append(int(m.group(1)))
                        if not lines[j].strip() and (j + 1 >= len(lines) or not lines[j + 1].startswith("   ")
                                                     and not re.match(r"^\d+\. ", lines[j + 1])):
                            break
                        j += 1
                    if items != list(range(1, len(marks) + 1)):
                        problems.append(f"{rel}:{end + 1}: the box has {len(marks)} annotation marker(s): put a numbered "
                                        f"list 1..{len(marks)} right under it (found {items or 'none'})")
                i = end
            i += 1


if "--fix-cookbook" in sys.argv:
    fix_cookbook()
    problems.clear()
FIELD = re.compile(r"^\s*public static (double|float|int|long|boolean)(\[\]\[\])? (.+?);(\s*//.*)?$")
SAFETY = re.compile(r"^(min|max|posA|posB|homeAngle|\w+(From|To|Above|Below))$")


def check_recipe_sections():
    """Recipes that show a Tunables section teach the style: one commented number per line, MEASURE for safety."""
    for name, (rel, start, code) in sorted(cookbook_regions().items()):
        if not name.startswith("tunables-"):
            continue
        lines = code.splitlines()
        for k, line in enumerate(lines):
            m = FIELD.match(line)
            if not m:
                continue
            where = f"{rel}:{start + 1 + k}: recipe {name}"
            if m.group(2):
                if k == 0 or not lines[k - 1].strip().startswith("//"):
                    problems.append(f"{where}: a table needs a comment line above it")
                continue
            if not m.group(4):
                problems.append(f"{where}: give the line a short // comment (what the number is)")
            fields = [f.strip() for f in m.group(3).split(",")]
            for f in fields:
                field, _, value = (x.strip() for x in f.partition("="))
                if SAFETY.match(field) and value != "MEASURE":
                    problems.append(f"{where}: {field} is a safety number: write MEASURE, not {value}")


check_links()
check_classes_documented()
check_tutorials()
check_cookbook()
check_marked_pages()
check_example_comments()
check_annotations()
check_recipe_sections()

if problems:
    print("Docs check FAILED:")
    for p in problems:
        print("  - " + p)
    sys.exit(1)
print("Docs check passed.")
