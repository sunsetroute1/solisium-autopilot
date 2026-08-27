"""Parse Google Docs / community Talking Wall lists into talking-wall-community.json."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = Path(__file__).resolve().parent / "talking-wall-google-doc.txt"
SCARYEL = Path(
    r"C:\Users\sunse\.cursor\projects\c-Users-sunse-projects-solisium-autopilot"
    r"\agent-tools\8ff97174-4d4e-470c-b5e6-12bea3575e04.txt"
)
OLD_JSON = ROOT / "core" / "src" / "commonMain" / "resources" / "talking-wall-community.json"
OUT = OLD_JSON

CATEGORY_MAP = {
    "Answer Key — Nix": "nix",
    "Answer Key — Cosmology and Ancient History": "cosmology",
    "Answer Key — Regions and Bestiary": "regions",
    "Answer Key — Core Characters": "characters",
    "Answer Key — NPCs and Factions": "npcs",
    "Answer Key — Trivia and Trick Questions": "trivia",
}

def fold(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()

def load_category_index() -> dict[str, str]:
    categories: dict[str, str] = {}
    if OLD_JSON.exists():
        for row in json.loads(OLD_JSON.read_text(encoding="utf-8")).get("statements", []):
            categories[fold(row["statement"])] = row.get("category") or "general"
    if SCARYEL.exists():
        category = "general"
        line_re = re.compile(r"^- (True|False) — (.+)$")
        for raw in SCARYEL.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            for label, key in CATEGORY_MAP.items():
                if line.startswith(label):
                    category = key
                    break
            m = line_re.match(line)
            if m:
                categories[fold(m.group(2))] = category
    return categories

def parse_google_doc(text: str) -> list[dict]:
    header_re = re.compile(r"^(TRUE|FALSE)\s+(.+)$", re.IGNORECASE)
    statements: list[dict] = []
    current_answer: bool | None = None
    current_parts: list[str] = []

    def flush() -> None:
        nonlocal current_answer, current_parts
        if current_answer is None or not current_parts:
            return
        statement = re.sub(r"\s+", " ", " ".join(current_parts)).strip()
        if len(statement) < 8:
            current_answer = None
            current_parts = []
            return
        statements.append(
            {
                "statement": statement,
                "answerTrue": current_answer,
            }
        )
        current_answer = None
        current_parts = []

    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("---") or line.upper().startswith("FULL LIST"):
            continue
        if line.lower().startswith("published using"):
            continue
        if line.lower().startswith("ctrl+"):
            continue
        if "every 3 hours" in line.lower():
            continue
        if "blue true" in line.lower():
            continue
        if line.startswith("pRAES") or line.startswith("Report abuse"):
            continue
        m = header_re.match(line)
        if m:
            flush()
            current_answer = m.group(1).upper() == "TRUE"
            current_parts = [m.group(2).strip()]
        elif current_answer is not None:
            current_parts.append(line)
    flush()
    return statements

def main() -> None:
    if not DOC.exists():
        raise SystemExit(f"missing source file: {DOC}")
    categories = load_category_index()
    parsed = parse_google_doc(DOC.read_text(encoding="utf-8"))
    seen: set[str] = set()
    statements = []
    for entry in parsed:
        key = fold(entry["statement"])
        if key in seen:
            continue
        seen.add(key)
        row_id = f"community_{len(statements) + 1:04d}"
        statements.append(
            {
                "id": row_id,
                "statement": entry["statement"],
                "answerTrue": entry["answerTrue"],
                "category": categories.get(key, "community"),
                "notes": None,
            }
        )
    payload = {
        "source": "community",
        "sourceLabel": "Community answer key (Google Doc + Scaryel categories, Aug 2026)",
        "version": 2,
        "statements": statements,
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    true_count = sum(1 for s in statements if s["answerTrue"])
    false_count = len(statements) - true_count
    print(f"wrote {len(statements)} statements ({true_count} true, {false_count} false) to {OUT}")

if __name__ == "__main__":
    main()
