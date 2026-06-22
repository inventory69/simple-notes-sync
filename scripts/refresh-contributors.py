#!/usr/bin/env python3
"""Refresh contributors.json from GitHub API. Preserves manual entries/roles/notes."""

import json
import urllib.request
from pathlib import Path

REPO = "inventory69/simple-notes-sync"
CONTRIBUTORS_FILE = Path(__file__).parent.parent / "android" / "contributors.json"


def _is_bot(login: str) -> bool:
    return "[bot]" in login or "weblate" in login.lower()


def fetch_code_logins() -> list[str]:
    url = f"https://api.github.com/repos/{REPO}/contributors?per_page=100"
    req = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "refresh-contributors-script",
    })
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return [c["login"] for c in data if not _is_bot(c["login"])]


def merge(current: dict, code_logins: list[str]) -> dict:
    """Merge code contributors from GitHub into current JSON, preserving all manual fields."""
    existing = {c["login"]: c for c in current.get("contributors", [])}
    for login in code_logins:
        if login not in existing:
            existing[login] = {"login": login, "role": "code"}
    return {"contributors": list(existing.values())}


def main() -> None:
    current = json.loads(CONTRIBUTORS_FILE.read_text()) if CONTRIBUTORS_FILE.exists() else {}
    code_logins = fetch_code_logins()
    result = merge(current, code_logins)
    CONTRIBUTORS_FILE.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    print(f"Updated: {len(result['contributors'])} contributors")


if __name__ == "__main__":
    # ponytail: assert-based self-check before live run
    test_data = {"contributors": [
        {"login": "translator1", "role": "translation"},
        {"login": "coder1", "role": "code", "note": "Maintainer"},
    ]}
    merged = merge(test_data, ["coder1", "newcomer"])
    by_login = {c["login"]: c for c in merged["contributors"]}
    assert by_login["translator1"]["role"] == "translation", "manual translation role must survive"
    assert by_login["coder1"]["note"] == "Maintainer", "manual note must survive"
    assert by_login["newcomer"]["role"] == "code", "new contributor gets code role"
    assert len(merged["contributors"]) == 3, "no duplicates"
    print("Self-check passed — running update…")
    main()
