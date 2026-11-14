#!/usr/bin/env python3
"""Which tested debloat list in examples/ belongs to this TV, judged from what the TV says about itself.

    find_list.py <model> <device> <name> <fingerprint>        print the best matches, one per line:
                                                              score<TAB>folder<TAB>what the list is for
    find_list.py --examples <dir> ...                         look in another folder than ./examples

A list is for one firmware, and a list for a different one can switch off something this TV needs, so only a
strong match counts and nothing is applied by this tool: it names candidates for a person to look at. The score:
the whole build fingerprint found in the list or its README, 200; the fingerprint's first three parts (brand, product,
device), 100; the device codename as a word, 40; the model name, 30. Under 70 is not a match and prints nothing.
Exit status 0 always when it could read the examples (no output means no match), 2 on a bad command line.
"""
import json
import re
import sys
from pathlib import Path


def main(argv):
    args = argv[1:]
    root = Path(__file__).resolve().parent.parent / "examples"
    if args[:1] == ["--examples"]:
        if len(args) < 2:
            sys.stderr.write(__doc__)
            return 2
        root, args = Path(args[1]), args[2:]
    if len(args) != 4:
        sys.stderr.write(__doc__)
        return 2
    model, device, name, fingerprint = (a.strip().lower() for a in args)
    parts = fingerprint.split(":")[0].split("/")[:3] if fingerprint else []
    short = "/".join(parts) if len(parts) == 3 and all(parts) else ""

    def word(text, needle):
        return len(needle) >= 4 and re.search(r"(?<![a-z0-9])" + re.escape(needle) + r"(?![a-z0-9])", text) is not None

    found = []
    for folder in sorted(p for p in root.iterdir() if p.is_dir()):
        try:
            text = "\n".join((folder / f).read_text(encoding="utf-8", errors="replace").lower()
                             for f in ("debloat.txt", "README.md", "tv.json") if (folder / f).is_file())
        except OSError:
            continue
        if not (folder / "debloat.txt").is_file():
            continue
        score = 0
        if fingerprint and fingerprint in text:
            score += 200
        elif short and short in text:
            score += 100
        if word(text, device):
            score += 40
        if word(text, model) or (name and word(text, name)):
            score += 30
        if score >= 70:
            title = folder.name
            try:
                title = json.loads((folder / "tv.json").read_text(encoding="utf-8")).get("tv", title)
            except (OSError, ValueError):
                pass
            found.append((score, folder.name, title))
    for score, folder, title in sorted(found, key=lambda r: (-r[0], r[1]))[:3]:
        print("%d\t%s\t%s" % (score, folder, title))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
