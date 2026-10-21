#!/usr/bin/env python3
"""Checks that the docs and tutorials match the code. Run from the repo root:

    python3 tools/check_docs.py

It fails (exit 1) when:
  1. a Markdown link points to a file that does not exist, or to a heading (#anchor) that does not exist;
  2. a public library class is not named in any docs/modules/*.md page;
  3. a tutorial program (examples/.../tutorials) is not linked from any tutorial page;
  4. a tutorial page has no link to its full program.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LIB = ROOT / "library/src/main/java"
TUT_CODE = ROOT / "examples/src/main/java/com/crossbotics/ftchelper/examples/tutorials"
TUT_TEST = ROOT / "examples/src/test/java/com/crossbotics/ftchelper/examples/tutorials"
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


check_links()
check_classes_documented()
check_tutorials()

if problems:
    print("Docs check FAILED:")
    for p in problems:
        print("  - " + p)
    sys.exit(1)
print("Docs check passed.")
