#!/usr/bin/env bash
# Shoot scenes on the Android TV emulator, with the rows up and then with them put away.
# Usage: tools/scene_emu.sh d_bled d_maldives ...      (emulator-5554 running with the launcher installed as Home)
#        OUT=/some/folder tools/scene_emu.sh ...       (default: shots/scenes)
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${OUT:-shots/scenes}"; mkdir -p "$OUT"
E() { adb -s "${SERIAL:-emulator-5554}" "$@"; }
DIR=/sdcard/Android/data/com.gathin.tvhome/files
for sc in "$@"; do
  python3 - "$sc" > "$OUT/config.json" <<'PY'
import json, sys
c = json.load(open("themes-local/couch-scenes/config.json"))
c.update(scene=sys.argv[1], hide_rows_idle_seconds=6, scenes_off="")
print(json.dumps(c))
PY
  E push "$OUT/config.json" /data/local/tmp/cyt.json >/dev/null
  E shell "content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/cyt.json || cp /data/local/tmp/cyt.json $DIR/config.json; date +%s%N > /data/local/tmp/rev; content write --uri content://com.gathin.tvhome.theme/theme_revision < /data/local/tmp/rev; am force-stop com.gathin.tvhome; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity" >/dev/null
  sleep 3; E exec-out screencap -p > "$OUT/${sc}_rows.png"
  sleep 8; E exec-out screencap -p > "$OUT/${sc}_idle.png"
  echo "$sc -> $OUT/${sc}_rows.png, ${sc}_idle.png"
done
rm -f "$OUT/config.json"
