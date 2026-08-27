"""One-off parser for community Talking Wall answer key text."""
import json
import re
from pathlib import Path

SRC = Path(r"C:\Users\sunse\.cursor\projects\c-Users-sunse-projects-solisium-autopilot\agent-tools\8ff97174-4d4e-470c-b5e6-12bea3575e04.txt")
OUT = Path(__file__).resolve().parent.parent / "core" / "src" / "jvmMain" / "resources" / "talking-wall-community.json"

CATEGORY_MAP = {
    "Answer Key — Nix": "nix",
    "Answer Key — Cosmology and Ancient History": "cosmology",
    "Answer Key — Regions and Bestiary": "regions",
    "Answer Key — Core Characters": "characters",
    "Answer Key — NPCs and Factions": "npcs",
    "Answer Key — Trivia and Trick Questions": "trivia",
}

line_re = re.compile(r"^- (True|False) — (.+)$")

def main() -> None:
    text = SRC.read_text(encoding="utf-8")
    category = "general"
    statements = []
    seen = set()
    for raw in text.splitlines():
        line = raw.strip()
        for label, key in CATEGORY_MAP.items():
            if line.startswith(label):
                category = key
                break
        m = line_re.match(line)
        if not m:
            continue
        answer = m.group(1).lower() == "true"
        statement = m.group(2).strip()
        norm = re.sub(r"\s+", " ", statement.lower())
        if norm in seen:
            continue
        seen.add(norm)
        row_id = f"community_{len(statements) + 1:04d}"
        statements.append(
            {
                "id": row_id,
                "statement": statement,
                "answerTrue": answer,
                "category": category,
                "notes": None,
            }
        )
    payload = {
        "source": "community",
        "sourceLabel": "Community answer key (Scaryel, Jul 2026)",
        "version": 1,
        "statements": statements,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {len(statements)} statements to {OUT}")

if __name__ == "__main__":
    main()
