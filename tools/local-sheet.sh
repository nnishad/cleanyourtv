#!/usr/bin/env bash
# Personal (couch-port branch): screenshot every launcher screen on the emulator into one review sheet.
# Usage: tools/local-sheet.sh <label>   ->  shots/sheet-<label>.png
set -u
cd "$(dirname "$0")/.."
L=${1:-now}; A=~/Library/Android/sdk/platform-tools/adb; S=emulator-5554; D=shots/sheet-$L; mkdir -p "$D"
k(){ $A -s $S shell input keyevent "$1"; sleep "${2:-1.3}"; }
shot(){ TV_HOST=127.0.0.1 TV_PORT=5555 tools/venv/bin/python -I tools/tv.py --screenshot "$D/$1.png" >/dev/null 2>&1; }
k KEYCODE_HOME 3; shot 1-home
k KEYCODE_DPAD_RIGHT; shot 2-dock-focus
k KEYCODE_DPAD_UP; shot 3-cards
k KEYCODE_DPAD_UP; shot 4-top-buttons
k KEYCODE_DPAD_LEFT; k KEYCODE_DPAD_LEFT; k KEYCODE_DPAD_LEFT; k KEYCODE_DPAD_CENTER 2; shot 5-inputs
k KEYCODE_BACK; k KEYCODE_DPAD_RIGHT; k KEYCODE_DPAD_RIGHT; k KEYCODE_DPAD_RIGHT; k KEYCODE_DPAD_CENTER 2; shot 6-gear
k KEYCODE_DPAD_CENTER 3; shot 7-home-settings
k KEYCODE_DPAD_RIGHT; shot 8-settings-row
k KEYCODE_BACK 2; k KEYCODE_BACK 2; k KEYCODE_HOME 3
$A -s $S shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; shot 9-tile-menu; k KEYCODE_BACK
/private/tmp/claude-501/-Users-nikhilnishad/1212309f-e10e-4460-b0ef-4da8a76a54e2/scratchpad/fontenv/bin/python -I - "$D" "shots/sheet-$L.png" <<'PY'
import sys, os
from PIL import Image, ImageDraw
d, out = sys.argv[1], sys.argv[2]
fs = sorted(f for f in os.listdir(d) if f.endswith('.png'))
W, H, cols = 640, 360, 3
sheet = Image.new('RGB', (cols * W, ((len(fs) + cols - 1) // cols) * H), 'black')
for k, f in enumerate(fs):
    im = Image.open(os.path.join(d, f)).convert('RGB').resize((W, H))
    x, y = (k % cols) * W, (k // cols) * H
    sheet.paste(im, (x, y)); dr = ImageDraw.Draw(sheet); dr.rectangle((x, y, x + 150, y + 16), fill='black'); dr.text((x + 4, y + 2), f[:-4], fill='white')
sheet.save(out)
print(out)
PY
