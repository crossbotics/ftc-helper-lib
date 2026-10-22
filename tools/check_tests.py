#!/usr/bin/env python3
"""Checks the community test list (testing/tests/*.md) and writes testing/catalogue.json from it.
Run from the repo root:

    python3 tools/check_tests.py           check everything; fails if catalogue.json is out of date
    python3 tools/check_tests.py --fix     check, then write testing/catalogue.json
    python3 tools/check_tests.py --set-since v0.17.0
                                           at a release: every "since: unreleased" becomes v0.17.0

It fails (exit 1) when:
  1. a test file has no frontmatter, a missing or unknown field, or a value not in testing/vocabulary.json;
  2. two tests have the same ID, or a file name is not its ID (AIM-01.md);
  3. a required section is missing (Why, Setup, Steps, Pass rule, Record, If your robot is different;
     Safety too when the test runs on a real robot), the steps are not a numbered list, or the pass
     rule has no number in it;
  4. a link (in "docs:" or in the text) points to a file or a heading that does not exist;
  5. a kit bot is listed that lacks something the test needs;
  6. a "needs" word or a kit bot has no matching option in .github/ISSUE_TEMPLATE/test-report.yml
     (the coverage table and the triage read the form, so the words must be the same), or an area
     has no area-... label in tools/make_labels.sh;
  7. testing/catalogue.json is not what --fix would write (the website reads that file).

Why a file per test with a small header: a student adds a test by copying one file and changing it.
No extra Python packages: the small parsers below read the header and the form.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ROOT / "testing/tests"
VOCAB = ROOT / "testing/vocabulary.json"
CATALOGUE = ROOT / "testing/catalogue.json"
FORM = ROOT / ".github/ISSUE_TEMPLATE/test-report.yml"

ID_RE = re.compile(r"^[A-Z]{2,5}-\d{2}$")
VERSION_RE = re.compile(r"^v\d+\.\d+\.\d+$")
LINK = re.compile(r"(!?)\[([^\]]*)\]\(([^)\s]+)\)")
FIELDS = ["id", "title", "area", "priority", "where", "minutes", "needs", "kit_bots", "since", "docs"]
# Section heading in the file -> key in catalogue.json. The order here is the order on the website.
SECTIONS = [
    ("Why", "why", True),
    ("Safety", "safety", False),        # required for real-robot tests (checked below)
    ("Setup", "setup", True),
    ("Steps", "steps", True),
    ("Pass rule", "pass", True),
    ("Record", "record", True),
    ("If your robot is different", "different", True),
]

problems = []
warnings = []
unreleased = []   # test IDs with "since: unreleased" (one note at the end, not one per test)


def problem(where, text):
    problems.append(f"{where}: {text}")


# ---------------------------------------------------------------------------------------------
# A tiny YAML reader: enough for GitHub issue forms (maps, lists, quoted and plain words, "|"
# blocks, comments). Not a full YAML parser on purpose: CI's plain python3 has no PyYAML.

def _scalar(text):
    text = text.strip()
    if not text:
        return None
    if text[0] in "\"'":
        q = text[0]
        end = text.rfind(q)
        body = text[1:end]
        if q == '"':
            body = body.replace('\\n', '\n').replace('\\"', '"').replace('\\\\', '\\')
        else:
            body = body.replace("''", "'")
        return body
    if text.startswith("[") and text.endswith("]"):
        inner = text[1:-1].strip()
        return [_scalar(p) for p in _split_flow(inner)] if inner else []
    text = re.sub(r"\s+#.*$", "", text)
    if text in ("true", "false"):
        return text == "true"
    if re.fullmatch(r"-?\d+", text):
        return int(text)
    if re.fullmatch(r"-?\d+\.\d+", text):
        return float(text)
    return text


def _split_flow(inner):
    parts, depth, quote, cur = [], 0, None, ""
    for ch in inner:
        if quote:
            cur += ch
            if ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
            cur += ch
        elif ch == "[":
            depth += 1
            cur += ch
        elif ch == "]":
            depth -= 1
            cur += ch
        elif ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def mini_yaml(text):
    """Reads a small YAML document into dicts, lists and strings."""
    lines = []
    for raw in text.splitlines():
        stripped = raw.strip()
        lines.append((len(raw) - len(raw.lstrip(" ")), raw))
    pos = [0]

    def skip_blank():
        while pos[0] < len(lines):
            s = lines[pos[0]][1].strip()
            if s and not s.startswith("#"):
                return
            pos[0] += 1

    def block_scalar(indent_parent):
        out = []
        block_indent = None
        while pos[0] < len(lines):
            ind, raw = lines[pos[0]]
            if raw.strip() and ind <= indent_parent:
                break
            if raw.strip() and block_indent is None:
                block_indent = ind
            out.append(raw[block_indent:] if block_indent and raw.strip() else raw.strip())
            pos[0] += 1
        return "\n".join(out).rstrip("\n") + "\n"

    def value_after(rest, indent):
        rest = rest.strip()
        if rest in ("|", "|-", ">", ">-"):
            return block_scalar(indent)
        if rest == "" or rest.startswith("#"):
            skip_blank()
            if pos[0] < len(lines) and lines[pos[0]][0] > indent:
                return node(lines[pos[0]][0])
            if pos[0] < len(lines) and lines[pos[0]][0] == indent and lines[pos[0]][1].strip().startswith("- "):
                return node(indent)
            return None
        return _scalar(rest)

    def mapping(indent, first=None):
        out = {}
        if first is not None:
            k, v = first
            out[k] = v
        while True:
            skip_blank()
            if pos[0] >= len(lines):
                return out
            ind, raw = lines[pos[0]]
            s = raw.strip()
            if ind != indent or s.startswith("- "):
                return out
            m = re.match(r"^([\w\-]+)\s*:(.*)$", s)
            if not m:
                raise ValueError(f"line {pos[0] + 1}: cannot read: {s}")
            pos[0] += 1
            out[m.group(1)] = value_after(m.group(2), indent)

    def sequence(indent):
        out = []
        while True:
            skip_blank()
            if pos[0] >= len(lines):
                return out
            ind, raw = lines[pos[0]]
            s = raw.strip()
            if ind != indent or not (s.startswith("- ") or s == "-"):
                return out
            item = s[2:] if s.startswith("- ") else ""
            pos[0] += 1
            m = re.match(r"^([\w\-]+)\s*:(.*)$", item)
            if m and not item.startswith(("\"", "'")):
                inner = indent + 2
                first = (m.group(1), value_after(m.group(2), inner))
                out.append(mapping(inner, first))
            else:
                out.append(_scalar(item))

    def node(indent):
        skip_blank()
        if pos[0] >= len(lines):
            return None
        s = lines[pos[0]][1].strip()
        return sequence(indent) if s.startswith("- ") else mapping(indent)

    return node(0)


def form_options():
    """{field id: [option words]} from the test-report form."""
    form = mini_yaml(FORM.read_text())
    out = {}
    for item in form.get("body", []):
        fid = item.get("id")
        attrs = item.get("attributes") or {}
        if not fid:
            continue
        opts = attrs.get("options") or []
        out[fid] = [o.get("label") if isinstance(o, dict) else o for o in opts]
    return out, form


# ---------------------------------------------------------------------------------------------
# Test files.

def slug(heading):
    """GitHub's anchor for a heading (the same as tools/check_docs.py)."""
    s = heading.strip().lower()
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")


def anchors(md_path):
    found, in_code = set(), False
    for line in md_path.read_text().splitlines():
        if line.startswith("```"):
            in_code = not in_code
        if not in_code and line.startswith("#"):
            found.add(slug(line.lstrip("#")))
    return found


def check_target(where, path, anchor):
    """path is relative to the repo root."""
    dest = ROOT / path
    if not dest.exists():
        problem(where, f"link to a missing file: {path}")
        return
    if anchor and dest.suffix == ".md" and anchor not in anchors(dest):
        problem(where, f"no heading #{anchor} in {path}")


def read_frontmatter(text, where):
    m = re.match(r"^---\n(.*?)\n---\n(.*)$", text, re.S)
    if not m:
        problem(where, "no frontmatter (the --- header at the top)")
        return None, text
    head = {}
    for n, line in enumerate(m.group(1).splitlines(), 2):
        if not line.strip() or line.strip().startswith("#"):
            continue
        km = re.match(r"^([a-z_]+):\s*(.*)$", line)
        if not km:
            problem(where, f"header line {n} is not 'name: value': {line}")
            continue
        head[km.group(1)] = _scalar(km.group(2)) if km.group(2).strip() else ""
    return head, m.group(2)


def blocks(lines):
    """Markdown lines -> [{"p": text} | {"ul": [items]} | {"ol": [items]}]. Inline Markdown is kept."""
    out, cur = [], None
    for line in lines + [""]:
        s = line.rstrip()
        bullet = re.match(r"^\s{0,3}[-*]\s+(.*)$", s)
        num = re.match(r"^\s{0,3}(\d+)\.\s+(.*)$", s)
        if not s.strip():
            if cur and cur[0] == "p":
                out.append({"p": " ".join(cur[1])})
                cur = None
            continue
        if bullet or num:
            kind = "ul" if bullet else "ol"
            text = bullet.group(1) if bullet else num.group(2)
            if cur and cur[0] == "p":
                out.append({"p": " ".join(cur[1])})
                cur = None
            if cur and cur[0] != kind:
                out.append({cur[0]: cur[1]})
                cur = None
            if not cur:
                cur = (kind, [])
                if num:
                    cur[1].append(("__start", int(num.group(1))))
            cur[1].append(text)
            continue
        if cur and cur[0] in ("ul", "ol") and line.startswith((" ", "\t")):
            cur[1][-1] += " " + s.strip()     # an item that goes on to the next line
            continue
        if cur and cur[0] in ("ul", "ol"):
            out.append({cur[0]: cur[1]})
            cur = None
        if not cur:
            cur = ("p", [])
        cur[1].append(s.strip())
    if cur:
        out.append({cur[0]: cur[1]} if cur[0] != "p" else {"p": " ".join(cur[1])})
    # Numbered lists: drop the start marker, but remember it was not 1.
    for b in out:
        if "ol" in b:
            items = b["ol"]
            start = items[0][1] if items and isinstance(items[0], tuple) else 1
            b["ol"] = [i for i in items if not isinstance(i, tuple)]
            if start != 1:
                b["start"] = start
    return out


def repo_links(text, test_file, where):
    """Relative links -> 'repo:' links from the repo root (the website turns them into site links)."""
    def fix(m):
        bang, label, target = m.groups()
        if re.match(r"^(https?:|mailto:|#)", target):
            return m.group(0)
        path, _, anchor = target.partition("#")
        resolved = (test_file.parent / path).resolve()
        try:
            rel = resolved.relative_to(ROOT).as_posix()
        except ValueError:
            problem(where, f"link outside the repo: {target}")
            return m.group(0)
        check_target(where, rel, anchor)
        return f"{bang}[{label}](repo:{rel}{'#' + anchor if anchor else ''})"
    return LINK.sub(fix, text)


def parse_needs(raw, vocab, where):
    """needs: [a, b|c] -> [["a"], ["b", "c"]] (every item must be there; "|" = either one)."""
    if raw in ("any", ["any"]):
        return []
    if not isinstance(raw, list):
        problem(where, "needs must be a list, like [flywheel, limelight|webcam], or [any]")
        return []
    out = []
    for item in raw:
        alts = [a.strip() for a in str(item).split("|")]
        for a in alts:
            if a not in vocab["needs"]:
                problem(where, f"needs: unknown word '{a}' (allowed: any, {', '.join(vocab['needs'])})")
        out.append(alts)
    return out


def read_test(path, vocab):
    where = path.relative_to(ROOT).as_posix()
    text = path.read_text()
    head, body = read_frontmatter(text, where)
    if head is None:
        return None
    for f in FIELDS:
        if f not in head or head[f] in ("", None):
            problem(where, f"missing header field '{f}'")
    for f in head:
        if f not in FIELDS:
            problem(where, f"unknown header field '{f}' (allowed: {', '.join(FIELDS)})")
    tid = head.get("id", "")
    if not ID_RE.match(str(tid)):
        problem(where, f"id '{tid}' must look like AIM-01 (2-5 capital letters, a dash, 2 digits)")
    elif path.stem != tid:
        problem(where, f"the file must be called {tid}.md")
    if head.get("area") not in vocab["areas"]:
        problem(where, f"area '{head.get('area')}' is not one of: {', '.join(vocab['areas'])}")
    if head.get("priority") not in vocab["priorities"]:
        problem(where, f"priority must be one of: {', '.join(vocab['priorities'])}")
    if head.get("where") not in vocab["where"]:
        problem(where, f"where must be one of: {', '.join(vocab['where'])}")
    minutes = head.get("minutes")
    if not isinstance(minutes, int) or not 1 <= minutes <= 240:
        problem(where, "minutes must be a whole number from 1 to 240")
    since = head.get("since")
    if since != "unreleased" and not VERSION_RE.match(str(since)):
        problem(where, "since must be a release like v0.16.0, or 'unreleased'")
    if since == "unreleased":
        unreleased.append(str(tid))
    needs = parse_needs(head.get("needs"), vocab, where)
    kit = head.get("kit_bots")
    if kit in ("none", None, ""):
        kit = []
    if not isinstance(kit, list):
        problem(where, "kit_bots must be a list, like [gobilda, rev], or []")
        kit = []
    for k in kit:
        if k not in vocab["kit_bots"]:
            problem(where, f"kit_bots: unknown '{k}' (allowed: {', '.join(vocab['kit_bots'])})")
            continue
        has = set(vocab["kit_bots"][k]["has"])
        for alts in needs:
            if not has.intersection(alts):
                problem(where, f"kit_bots: {k} has no {' or '.join(alts)}, which the test needs")
    for k, bot in vocab["kit_bots"].items():
        if k not in kit and all(set(bot["has"]).intersection(a) for a in needs):
            warnings.append(f"{where}: the {k} kit bot has everything the test needs but is not in kit_bots")
    docs = head.get("docs")
    if isinstance(docs, str):
        docs = [docs]
    if not isinstance(docs, list) or not docs:
        problem(where, "docs must list at least one docs page, like [guides/finders.md#hood-finder]")
        docs = []
    doc_links = []
    for d in docs:
        p, _, a = str(d).partition("#")
        check_target(where, "docs/" + p, a)
        doc_links.append({"path": "docs/" + p, "anchor": a})

    # Sections: "## Heading" lines.
    sections, current, intro = {}, None, []
    for line in body.splitlines():
        h = re.match(r"^##\s+(.+?)\s*$", line)
        if h:
            current = h.group(1)
            if current in sections:
                problem(where, f"section '{current}' twice")
            sections[current] = []
        elif line.startswith("# "):
            problem(where, "no '# ' title in the body: the title is in the header")
        elif current is None:
            if line.strip():
                intro.append(line)
        else:
            sections[current].append(line)
    if intro:
        problem(where, "text before the first '## ' section (put it under '## Why')")
    known = [s[0] for s in SECTIONS]
    for name in sections:
        if name not in known:
            problem(where, f"unknown section '## {name}' (allowed: {', '.join(known)})")
    out_sections = {}
    for name, key, required in SECTIONS:
        lines = sections.get(name)
        needed = required or (name == "Safety" and head.get("where") in ("real", "both"))
        if lines is None or not "".join(lines).strip():
            if needed:
                problem(where, f"missing section '## {name}'")
            continue
        fixed = [repo_links(l, path, where) for l in lines]
        out_sections[key] = blocks(fixed)
    steps = out_sections.get("steps", [])
    if steps and (len(steps) != 1 or "ol" not in steps[0] or steps[0].get("start", 1) != 1):
        problem(where, "## Steps must be one numbered list (1. 2. 3.) and nothing else")
    pass_text = json.dumps(out_sections.get("pass", []))
    if out_sections.get("pass") and not re.search(r"\d", pass_text):
        problem(where, "## Pass rule needs a number (how far, how fast, how many out of how many)")
    record = out_sections.get("record", [])
    if record and not all("ul" in b for b in record):
        problem(where, "## Record must be a bullet list (- one thing to write down per line)")
    title = str(head.get("title", ""))
    if len(title) > 80:
        problem(where, "title longer than 80 characters")
    return {
        "id": tid,
        "title": title,
        "area": head.get("area"),
        "priority": head.get("priority"),
        "where": head.get("where"),
        "minutes": minutes,
        "needs": needs,
        "kit_bots": kit,
        "since": since,
        "docs": doc_links,
        "file": where,
        "sections": out_sections,
    }


def check_form(vocab):
    try:
        opts, form = form_options()
    except Exception as e:  # noqa: BLE001 - any read error is a check failure
        problem(FORM.relative_to(ROOT).as_posix(), f"cannot read the form: {e}")
        return
    where = FORM.relative_to(ROOT).as_posix()
    for f in ("test_id", "version", "where", "robot", "result", "measurements", "changed"):
        if f not in opts:
            problem(where, f"no field with id '{f}' (the website fills it in, the triage reads it)")
    if "test-report" not in (form.get("labels") or []):
        problem(where, "labels must include test-report (the coverage table finds reports by it)")
    for key, need in vocab["needs"].items():
        field = need["field"]
        for o in need["options"]:
            if o not in opts.get(field, []):
                problem(where, f"needs '{key}': no option '{o}' in field '{field}' (testing/vocabulary.json)")
    for key, bot in vocab["kit_bots"].items():
        if bot["option"] not in opts.get("robot", []):
            problem(where, f"kit bot '{key}': no option '{bot['option']}' in field 'robot'")
        if not (ROOT / bot["robot_file"]).exists():
            problem("testing/vocabulary.json", f"kit bot '{key}': no file {bot['robot_file']}")
    labels = set(re.findall(r'^label "([a-z0-9-]+)"', (ROOT / "tools/make_labels.sh").read_text(), re.M))
    for area in vocab["areas"]:
        if f"area-{area}" not in labels:
            problem("tools/make_labels.sh", f"no label area-{area} (testing/vocabulary.json has the area '{area}')")
    for name in ("test-report", "docs-feedback", "bug", "needs-triage"):
        if name not in labels:
            problem("tools/make_labels.sh", f"no label {name} (the issue forms add it)")
    for r in vocab["results"]:
        if r not in opts.get("result", []):
            problem(where, f"no result option '{r}'")
    for r in vocab["where_options"].values():
        if r not in opts.get("where", []):
            problem(where, f"no where option '{r}'")


def build():
    vocab = json.loads(VOCAB.read_text())
    check_form(vocab)
    tests, seen = [], {}
    files = sorted(TESTS.glob("*.md"))
    if not files:
        problem("testing/tests", "no tests")
    for path in files:
        t = read_test(path, vocab)
        if not t:
            continue
        if t["id"] in seen:
            problem(t["file"], f"the same ID as {seen[t['id']]}")
        seen[t["id"]] = t["file"]
        tests.append(t)
    order = list(vocab["priorities"])
    tests.sort(key=lambda t: (order.index(t["priority"]) if t["priority"] in order else 9, t["id"]))
    public_vocab = {k: v for k, v in vocab.items() if not k.startswith("_")}
    # The form's questions, so the website can read a report's answers ("### <question>" in its body).
    try:
        _, form = form_options()
        fields = {i["id"]: (i.get("attributes") or {}).get("label") for i in form.get("body", []) if i.get("id")}
    except Exception:  # noqa: BLE001 - check_form already reported it
        fields = {}
    return {"_about": "Made by tools/check_tests.py --fix from testing/tests/*.md. Do not edit by hand.",
            "vocabulary": public_vocab, "form": {"template": FORM.name, "fields": fields}, "tests": tests}


def set_since(version):
    if not VERSION_RE.match(version):
        sys.exit(f"--set-since needs a version like v0.17.0, not {version}")
    n = 0
    for path in sorted(TESTS.glob("*.md")):
        text = path.read_text()
        new = re.sub(r"^since:\s*unreleased\s*$", f"since: {version}", text, count=1, flags=re.M)
        if new != text:
            path.write_text(new)
            n += 1
    print(f"{n} tests now say since: {version}")


def main():
    args = sys.argv[1:]
    if args[:1] == ["--set-since"]:
        if len(args) < 2:
            sys.exit("usage: --set-since v0.17.0")
        set_since(args[1])
        args = ["--fix"]
    catalogue = build()
    text = json.dumps(catalogue, indent=1, ensure_ascii=False) + "\n"
    if not problems:
        if "--fix" in args:
            CATALOGUE.write_text(text)
            print(f"wrote {CATALOGUE.relative_to(ROOT)}")
        elif not CATALOGUE.exists() or CATALOGUE.read_text() != text:
            problem("testing/catalogue.json", "out of date: run python3 tools/check_tests.py --fix")
    for w in warnings:
        print("  note: " + w)
    if unreleased:
        print(f"  note: {len(unreleased)} tests say 'since: unreleased' ({', '.join(unreleased)}); "
              "at the release run --set-since vX.Y.Z")
    if problems:
        print(f"{len(problems)} problem(s) in the test list:")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print(f"Test list OK: {len(catalogue['tests'])} tests.")


if __name__ == "__main__":
    main()
