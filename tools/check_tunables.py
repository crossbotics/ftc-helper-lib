#!/usr/bin/env python3
"""Keeps the Tunables reference (docs/guides/tunables.md) complete and right. Run from the repo root:

    python3 tools/check_tunables.py          # check (exit 1 on a problem)
    python3 tools/check_tunables.py --list   # print every field the library reads, by part

How it works. The library reads a team's Tunables.java in one place: the TunableSection class
(library/.../robot/). Every module asks it for a field by name: t.number("kP", 0.004),
t.flag("calibrated", false), t.tunable("inPower", 1.0, 0.05), t.table("calibration"),
t.tables("speedByDistance"), t.require("min", ...), t.has("goalX"), t.others(KNOWN) (named positions).
This script reads the Java source of the robot package, finds every one of those calls, and builds the
list of fields per part. It then fails (exit 1) when:
  1. a field the code reads is not in the reference, or the reference has a field the code no longer reads;
  2. a field read through tunable(...) (in the tuning menu) is not marked "yes" in the Live column, or
     the other way round;
  3. a field the code requires (t.require: INIT stops without it) does not say REQUIRED in Default;
  4. a default written as a plain number in the code is not the number in the Default column;
  5. a read uses a field name that is not written in the code ("t.number(name, 0)") and is not in the
     DYNAMIC list below (then this script cannot tell which field it is: add it there);
  6. an example robot's Tunables (examples/), or the Tunables.java that SimNewRobot writes for a team,
     has a field in a part's section that the part never reads (INIT would stop on it);
  7. TunableFields.java (the lists INIT uses to stop on a misspelled field) does not list exactly the
     fields the code reads, for every part (the team-picked names like <position> are handled there apart).

The reference marks each part with an HTML comment, <!-- tunables: Key -->, followed by tables whose
first column holds the field names in backticks. Fields that are names the team picks are written as
<position>, <KIND>, <part>From and speedByDistance<Name>.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROBOT = ROOT / "library/src/main/java/com/crossbotics/ftchelper/robot"
REFERENCE = ROOT / "docs/guides/tunables.md"
EXAMPLES = ROOT / "examples/src/main/java/com/crossbotics/ftchelper/examples"
SIM_NEW_ROBOT = ROOT / "sim/src/test/java/com/crossbotics/ftchelper/sim/SimNewRobot.java"

# Which part (reference key) each Java file's reads belong to. RobotDefinition and ButtonPlan name their
# section in the call itself (TunableSection.of(..., "Odometry")), so they are not here.
FILE_KEY = {
    "Drive.java": "Drive",
    "Odometry.java": "Odometry",
    "Aim.java": "Aim",
    "Intake.java": "Intake",
    "MotorPositions.java": "LiftArm", "Lift.java": "LiftArm", "Arm.java": "LiftArm",
    "ServoPositions.java": "ServoPositions", "Claw.java": "ServoPositions", "Wrist.java": "ServoPositions",
    "Positioner.java": "ServoPositions",
    "Turret.java": "Turret",
    "Interlock.java": "Interlock",
    "Launcher.java": "Launcher",
    "Indexer.java": "Indexer",
    "Hood.java": "Hood",
    "Camera.java": "Camera",
    "PieceSensor.java": "PieceSensor",
    "Lights.java": "Lights",
}
# A section named in the call (TunableSection.of(tunablesClass, "Buttons")) -> reference key.
NAMED_KEY = {"Drive": "Drive", "Odometry": "Odometry", "Aim": "Aim", "Buttons": "Buttons"}

# Reads whose field name is not written in the call. "file: call text" -> the fields it stands for
# (empty: the call is inside a helper whose own calls are read instead, e.g. Aim's live(t, "turnKp", ...)).
DYNAMIC = {
    "Aim.java: t.tunable(field": [],                       # inside live(t, "name", ...)
    "Camera.java: t.has(field": [], "Camera.java: t.number(field": [],   # inside optional(t, "name", ...)
    "Turret.java: t.require(f": [],                        # the loop over posA, angleA, posB, angleB (REQUIRED_EXTRA)
    "Interlock.java: t.has(f": [],                         # the zone's fields, read just below
    "Interlock.java: t.number(pre + \"Above\"": ["<part>Above"],
    "Interlock.java: t.number(pre + \"Below\"": ["<part>Below"],
    "Interlock.java: t.number(pre + \"From\"": ["<part>From"],
    "Interlock.java: t.number(pre + \"To\"": ["<part>To"],
    "Interlock.java: t.has(pre + \"Margin\"": ["<part>Margin"],
    "Interlock.java: t.number(pre + \"Margin\"": ["<part>Margin"],
    "PieceSensor.java: t.has(k": ["<KIND>"],
    "PieceSensor.java: t.tunable(k": ["<KIND>"],
    "MotorPositions.java: t.others(KNOWN": ["<position>"],
    "ServoPositions.java: t.others(KNOWN": ["<position>"],
}
# In the tuning menu although not read through tunable(...): PositionMotor/PositionServo make them
# (part.tunable(name()) in MotorPositions and ServoPositions).
LIVE_EXTRA = {
    "LiftArm": {"kP", "kI", "kD", "kF", "<position>"},
    "ServoPositions": {"<position>"},
    "PieceSensor": {"<KIND>"},
}
# Required although not read through require(...): the code checks them itself and stops INIT.
REQUIRED_EXTRA = {
    "Turret": {"posA", "angleA", "posB", "angleB"},
    "Interlock": {"<part>From", "<part>To", "<part>Above", "<part>Below"},
}

READ = re.compile(r"\b(?P<var>\w+)\.(?P<m>number|flag|tunable|table|tables|require|has|others)\(\s*")
CHAINED = re.compile(r"TunableSection\.of\([^;()]*(?:\([^()]*\))?[^;()]*,\s*\"(?P<sec>\w+)\"\)\."
                     r"(?P<m>number|flag|tunable|table|tables|require|has|others)\(\s*")
ASSIGN = re.compile(r"\b(?P<var>\w+)\s*=\s*TunableSection\.of\([^;]*?,\s*\"(?P<sec>\w+)\"\)\s*;")
NUMBER = re.compile(r"^-?\d+(\.\d+)?$")

problems = []


def args_at(text, i):
    """The call's arguments as text, starting just after its "(" at i (commas at depth 0 split them)."""
    out, depth, cur = [], 0, ""
    while i < len(text):
        c = text[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                out.append(cur.strip())
                return out
            depth -= 1
        if c == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += c
        i += 1
    return out


def statement_before(text, i):
    """The text of the statement that contains position i, up to i."""
    start = max(text.rfind(";", 0, i), text.rfind("{", 0, i), text.rfind("}", 0, i))
    return text[start + 1:i]


def motor_defaults():
    """Lift's and Arm's defaults() arrays: index -> {class: value}."""
    out = {}
    for cls in ("Lift", "Arm"):
        src = (ROBOT / (cls + ".java")).read_text()
        m = re.search(r"double\[\]\s+defaults\(\)\s*\{[^}]*?new double\[\]\s*\{([^}]*)\}", src)
        for idx, v in enumerate(x.strip() for x in m.group(1).split(",")):
            out.setdefault(idx, {})[cls] = v
    return out


def extract():
    """{key: {field: {"live": bool, "required": bool, "defaults": set of str, "guarded": bool}}}"""
    fields = {}
    dyn_seen = set()
    d_index = motor_defaults()

    def add(key, name, method, default=None, guarded=False, helper_live=False):
        f = fields.setdefault(key, {}).setdefault(name, {"live": False, "required": False, "defaults": set(), "guarded": False})
        if method == "tunable" or helper_live:
            f["live"] = True
        if method == "require":
            f["required"] = True
        if guarded:
            f["guarded"] = True
        if default is not None and method in ("number", "flag", "tunable", "helper") and not guarded:
            m = re.fullmatch(r"d\[(\d+)\]", default)
            if m:
                for v in d_index[int(m.group(1))].values():
                    f["defaults"].add(v)
            elif NUMBER.match(default) or default in ("true", "false"):
                f["defaults"].add(default)

    for path in sorted(ROBOT.glob("*.java")):
        if path.name == "TunableSection.java":
            continue
        text = path.read_text()
        # Strip comments (a Javadoc example is not a read), keeping offsets roughly the same.
        text = re.sub(r"/\*.*?\*/", lambda m: " " * len(m.group(0)), text, flags=re.S)
        text = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), text)
        vars_ = set(re.findall(r"\bTunableSection\s+(\w+)", text)) | {"tunables", "simSection"}
        assigns = [(m.start(), m.group("var"), m.group("sec")) for m in ASSIGN.finditer(text)]
        helpers = {}
        for m in re.finditer(r"\b(\w+)\(\s*TunableSection\s+\w+\s*,\s*String\s+\w+[^)]*\)\s*\{", text):
            body = text[m.end():text.find("\n    }", m.end())]
            helpers[m.group(1)] = ".tunable(" in body

        def key_for(var, pos):
            for start, v, sec in reversed(assigns):
                if v == var and start < pos:
                    return NAMED_KEY.get(sec)
            return FILE_KEY.get(path.name)

        def handle(key, var, method, pos_args):
            args = args_at(text, pos_args)
            first = args[0] if args else ""
            call = "%s: %s.%s(%s" % (path.name, var, method, first)
            if key is None:
                problems.append("%s reads Tunables (%s): add the file to FILE_KEY in tools/check_tunables.py" % (path.name, call))
                return
            lit = re.fullmatch(r"\"(\w+)\"", first)
            if not lit:
                if call not in DYNAMIC:
                    problems.append("%s: the field name is not written in the call, so this script cannot tell which "
                                    "field it reads. Add it to DYNAMIC in tools/check_tunables.py" % call)
                    return
                dyn_seen.add(call)
                for name in DYNAMIC[call]:
                    add(key, name, method)
                return
            name = lit.group(1)
            if method == "tables":
                add(key, name, "table")
                add(key, name + "<Name>", "table")
                return
            # "Guarded": only read when Tunables has it (t.has("x") ? t.number("x", 0) : 150, or an "if"
            # just above), so the number in the call is not the real default.
            before = statement_before(text, pos_args - len(var) - len(method) - 2)
            guarded = method in ("number", "flag", "tunable") and (
                ('has("%s")' % name) in text[max(0, pos_args - 400):pos_args] or before.rstrip().endswith("?"))
            add(key, name, method, args[1] if len(args) > 1 else None, guarded)

        for m in READ.finditer(text):
            if m.group("var") in vars_:
                handle(key_for(m.group("var"), m.start()), m.group("var"), m.group("m"), m.end())
        for m in CHAINED.finditer(text):
            handle(NAMED_KEY.get(m.group("sec")), "TunableSection.of(\"%s\")" % m.group("sec"), m.group("m"), m.end())
        for h, live in helpers.items():
            for m in re.finditer(r"\b%s\(\s*\w+\s*,\s*\"(\w+)\"\s*,\s*" % h, text):
                args = args_at(text, m.end())
                guarded = ('has("%s")' % m.group(1)) in text[max(0, m.start() - 400):m.start()]
                add(FILE_KEY.get(path.name), m.group(1), "helper", args[0] if args else None, guarded, helper_live=live)

    for call in DYNAMIC:
        if call not in dyn_seen:
            problems.append("DYNAMIC in tools/check_tunables.py names a read the code no longer has: " + call)
    for key, extra in REQUIRED_EXTRA.items():
        for name in extra:
            if name in fields.get(key, {}):
                fields[key][name]["required"] = True
    for key, extra in LIVE_EXTRA.items():
        for name in extra:
            if name in fields.get(key, {}):
                fields[key][name]["live"] = True
    return fields


# ----------------------------- the reference page -----------------------------

def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def read_reference():
    """{key: {field: {"default": str, "live": str, "line": int}}}"""
    out = {}
    if not REFERENCE.exists():
        problems.append("%s is missing" % REFERENCE.relative_to(ROOT))
        return out
    key, header = None, None
    for n, line in enumerate(REFERENCE.read_text().splitlines(), 1):
        m = re.match(r"\s*<!-- tunables: (\w+) -->", line)
        if m:
            key, header = m.group(1), None
            if key in out:
                problems.append("tunables.md:%d: <!-- tunables: %s --> is written twice" % (n, key))
            out.setdefault(key, {})
            continue
        if line.startswith("## ") or line.startswith("# "):
            key, header = None, None
            continue
        if key is None or not line.startswith("|"):
            if not line.startswith("|"):
                header = None
            continue
        c = cells(line)
        if header is None:
            header = c
            continue
        if set(line.replace("|", "").strip()) <= set("-: "):
            continue
        if "Field" not in header:
            continue
        names = re.findall(r"`([^`]+)`", c[header.index("Field")])
        for name in names:
            row = {"line": n,
                   "default": c[header.index("Default")] if "Default" in header else "",
                   "live": c[header.index("Live")] if "Live" in header else ""}
            if name in out[key]:
                problems.append("tunables.md:%d: `%s` is in the %s tables twice" % (n, name, key))
            out[key][name] = row
    return out


def numbers_in(text):
    return {float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", text)}


def check_reference(code, ref):
    for key in sorted(set(code) | set(ref)):
        if key not in ref:
            problems.append("tunables.md has no <!-- tunables: %s --> section (the code reads %s)"
                            % (key, ", ".join(sorted(code[key]))))
            continue
        if key not in code:
            problems.append("tunables.md: section %s is not read by any code" % key)
            continue
        for name in sorted(set(code[key]) - set(ref[key])):
            problems.append("tunables.md (%s): `%s` is read by the code but not in the reference" % (key, name))
        for name in sorted(set(ref[key]) - set(code[key])):
            problems.append("tunables.md:%d (%s): `%s` is not read by the code (an old name?)"
                            % (ref[key][name]["line"], key, name))
        for name in sorted(set(code[key]) & set(ref[key])):
            f, r = code[key][name], ref[key][name]
            where = "tunables.md:%d (%s) `%s`" % (r["line"], key, name)
            live = r["live"].lower()
            if f["live"] and not live.startswith("yes"):
                problems.append(where + ": it is in the tuning menu: its Live cell must start with \"yes\"")
            if not f["live"] and not live.startswith("no"):
                problems.append(where + ": it is NOT in the tuning menu: its Live cell must start with \"no\"")
            if f["required"] and not r["default"].startswith("REQUIRED"):
                problems.append(where + ": INIT stops without it: its Default cell must start with REQUIRED")
            if not f["required"]:
                want = f["defaults"]
                have = numbers_in(r["default"])
                for d in want:
                    if d in ("true", "false"):
                        if d not in r["default"]:
                            problems.append(where + ": the code's default is %s, the reference says \"%s\"" % (d, r["default"]))
                    elif float(d) not in have:
                        problems.append(where + ": the code's default is %s, the reference says \"%s\"" % (d, r["default"]))


# ----------------------------- the example robots -----------------------------

def class_bodies(text):
    """{class name: body text} for every class in a Java file (nested ones too)."""
    out = {}
    for m in re.finditer(r"\bclass\s+(\w+)[^{]*\{", text):
        depth, i = 1, m.end()
        while i < len(text) and depth:
            depth += {"{": 1, "}": -1}.get(text[i], 0)
            i += 1
        out[m.group(1)] = text[m.end():i - 1]
    return out


def sections_of(body):
    """The sections directly inside a Tunables class body: {name: {field names}}."""
    out = {}
    for name, sec in class_bodies(body).items():
        if not re.search(r"\bclass\s+%s\b" % name, body):
            continue
        names = set()
        for decl in re.findall(r"public\s+static\s+(?:final\s+)?(?:double\[\]\[\]|double|int|float|long|boolean)\s+([^;]+);", sec):
            depth, cur, parts = 0, "", []
            for c in decl:
                depth += {"{": 1, "}": -1}.get(c, 0)
                if c == "," and depth == 0:
                    parts.append(cur)
                    cur = ""
                else:
                    cur += c
            parts.append(cur)
            for p in parts:
                m = re.match(r"\s*(\w+)", p)
                if m:
                    names.add(m.group(1))
        out[name] = names
    return out


def camel(device):
    out, upper = "", True
    for c in device:
        if not c.isalnum():
            upper = True
            continue
        out += c.upper() if upper else c
        upper = False
    return out or "Part"


MODULE_KEY = {"Lift": "LiftArm", "Arm": "LiftArm", "Claw": "ServoPositions", "Wrist": "ServoPositions",
              "Positioner": "ServoPositions", "Turret": "Turret", "Launcher": "Launcher", "Indexer": "Indexer",
              "Hood": "Hood", "Camera": "Camera", "Intake": "Intake", "Lights": "Lights", "PieceSensor": "PieceSensor"}
FACTORY = re.compile(r"\b(%s)\.(\w+)\(\"([^\"]+)\"" % "|".join(MODULE_KEY))


def robot_sections(define):
    """From a robot's define() text: {section name: (key, extra allowed names)}."""
    out = {"Drive": ("Drive", set()), "Odometry": ("Odometry", set()), "Aim": ("Aim", set()), "Buttons": ("Buttons", set())}
    kinds = set(re.findall(r"\"([A-Z][A-Z0-9_]*)\"", " ".join(re.findall(r"\.kinds\(([^)]*)\)", define))))
    var_section = {}
    for m in FACTORY.finditer(define):
        cls, device = m.group(1), m.group(3)
        if cls == "Drive":
            continue
        rest = define[m.end():]
        nxt = FACTORY.search(rest)
        chain = rest[:nxt.start()] if nxt else rest
        chain = chain.split(";")[0]
        t = re.search(r"\.tunables\(\"(\w+)\"\)", chain)
        section = t.group(1) if t else camel(device)
        out[section] = (MODULE_KEY[cls], kinds if cls == "PieceSensor" else set())
        v = re.search(r"\b(\w+)\s*=\s*(?:add\()?\s*$", define[:m.start()].split(";")[-1])
        if v:
            var_section[v.group(1)] = section
    for m in re.finditer(r"keepApart\(\s*\"(\w+)\"\s*,([^;]*)\)\s*;", define):
        allowed = set()
        for var, kind in re.findall(r"\b(\w+)\.(zone|zoneAbove|zoneBelow)\(\)", m.group(2)):
            sec = var_section.get(var)
            pre = (sec[0].lower() + sec[1:]) if sec else None
            words = {"zone": ["From", "To"], "zoneAbove": ["Above"], "zoneBelow": ["Below"]}[kind] + ["Margin"]
            allowed |= {(pre or "*") + w for w in words}
        out[m.group(1)] = ("Interlock", allowed)
    return out


def allowed_field(name, key, extra, code):
    known = code.get(key, {})
    if name in known:
        return True
    if key in ("LiftArm", "ServoPositions"):
        return True   # a named position (the library stops INIT on one that looks like a setting)
    if key == "Launcher" and name.startswith("speedByDistance"):
        return True
    if key == "PieceSensor" and name in extra:
        return True
    if key == "Interlock":
        return name in extra or any(e.startswith("*") and name.endswith(e[1:]) for e in extra)
    return False


def check_examples(code):
    checked = 0
    for java in sorted(EXAMPLES.rglob("*.java")):
        text = java.read_text()
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        text = re.sub(r"//[^\n]*", "", text)
        if "void define()" not in text:
            continue
        bodies = class_bodies(text)
        for cname, body in bodies.items():
            d = re.search(r"void define\(\)\s*\{", body)
            if not d:
                continue
            define = body[d.end():]
            t = re.search(r"\btunables\((\w+)\.class\)", define)
            if t:
                holder = bodies.get(t.group(1))
                if holder is None:
                    other = java.parent / (t.group(1) + ".java")
                    holder = class_bodies(other.read_text()).get(t.group(1)) if other.exists() else None
            else:
                tj = java.parent / "Tunables.java"
                holder = class_bodies(tj.read_text()).get("Tunables") if tj.exists() else None
            if holder is None:
                continue
            checked += 1
            wanted = robot_sections(define)
            for sec, names in sections_of(holder).items():
                if sec not in wanted:
                    continue   # the team's own section (Auto...): every number is in the tuning menu
                key, extra = wanted[sec]
                for name in sorted(names):
                    if not allowed_field(name, key, extra, code):
                        problems.append("%s (%s): Tunables.%s.%s is not read by the %s part (a typo or an old name?)"
                                        % (java.relative_to(ROOT), cname, sec, name, key))
    if checked == 0:
        problems.append("no example robot was checked: has examples/ moved?")


def check_new_robot(code):
    """The Tunables.java that :sim:newRobot writes for a team."""
    if not SIM_NEW_ROBOT.exists():
        return
    text = SIM_NEW_ROBOT.read_text()
    for m in re.finditer(r"new Part\(\"(\w+)\"[^;]*;", text, flags=re.S):
        key = MODULE_KEY.get(m.group(1))
        if key is None:
            continue
        for name in re.findall(r"\.num\(\"(\w+)\"", m.group(0)):
            if not allowed_field(name, key, set(), code):
                problems.append("SimNewRobot.java writes Tunables field %s for a %s, which never reads it" % (name, m.group(1)))


FIELDS_JAVA = ROBOT / "TunableFields.java"


def check_field_lists(code):
    """TunableFields.java: one part("Key", "field", ...) per part, the same fields the code reads."""
    text = FIELDS_JAVA.read_text()
    lists = {}
    for m in re.finditer(r"\bpart\(\s*\"(\w+)\"((?:\s*,\s*\"\w+\")*)\s*\)\s*;", text):
        lists[m.group(1)] = set(re.findall(r"\"(\w+)\"", m.group(2)))
    for key in sorted(set(code) | set(lists)):
        if key == "Pieces":
            continue                       # reads nothing (its sensors have their own sections)
        want = {n for n in code.get(key, {}) if "<" not in n}
        have = lists.get(key)
        if have is None:
            problems.append("TunableFields.java has no part(\"%s\", ...) list (INIT checks every part's names)" % key)
            continue
        for n in sorted(want - have):
            problems.append("TunableFields.java part(\"%s\"): add \"%s\" (the code reads it; without it INIT stops on it)" % (key, n))
        for n in sorted(have - want):
            problems.append("TunableFields.java part(\"%s\"): \"%s\" is not read by the code (remove it)" % (key, n))


def main():
    code = extract()
    if "--list" in sys.argv:
        total = 0
        for key in sorted(code):
            print("%s (%d)" % (key, len(code[key])))
            for name, f in sorted(code[key].items()):
                flags = [w for w, on in (("live", f["live"]), ("REQUIRED", f["required"])) if on]
                print("    %-22s default %-12s %s" % (name, "/".join(sorted(f["defaults"])) or "-", " ".join(flags)))
            total += len(code[key])
        print("%d parts, %d fields" % (len(code), total))
        return 0
    check_reference(code, read_reference())
    check_field_lists(code)
    check_examples(code)
    check_new_robot(code)
    if problems:
        print("Tunables reference check: %d problem(s)" % len(problems))
        for p in problems:
            print("  - " + p)
        return 1
    print("Tunables reference check: OK (%d parts, %d fields; example robots' Tunables match)"
          % (len(code), sum(len(v) for v in code.values())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
