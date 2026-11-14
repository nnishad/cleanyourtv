#!/usr/bin/env bash
# Put the Cursed look on a TV in one go: the tools' Python environment, the launcher built and made Home, the
# permission Keep watching needs, and the theme pushed. Every step is an existing script of this repository
# (launcher/build-and-install.sh keeps its Home safety checks); this one only runs them in order and stops, before
# the TV is touched, when something the later steps need is missing.
#
# Usage: tools/install-cursed.sh <tv ip> [--weather "Place,latitude,longitude"] [--no-build]
#        TV_HOST=<tv ip> tools/install-cursed.sh
#   --weather   show "23° Clear" beside the date, for that place (one small request to Open-Meteo every 30 minutes
#               while Home is on screen). Without it the launcher makes no network request at all.
#               Example: --weather "Vilnius,54.69,25.28". It edits launcher/app/src/main/res/values/config.xml.
#   --no-build  install the APK already built (launcher/app/build/outputs/apk/release/app-release.apk)
# BUILD=docker or BUILD=native forces the way the launcher is built; by default a computer with the Android SDK builds
# natively and one without builds in Docker (tools/docker-build.sh), so it needs nothing else installed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WEATHER=""; NO_BUILD=0; TV="${TV_HOST:-}"
while [ $# -gt 0 ]; do
  case "$1" in
    --weather) WEATHER="${2:-}"; [ -n "$WEATHER" ] || { echo "ERROR: --weather needs \"Place,latitude,longitude\"." >&2; exit 2; }; shift 2 ;;
    --no-build) NO_BUILD=1; shift ;;
    -h|--help) sed -n 2,16p "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "ERROR: unknown option $1 (see --help)." >&2; exit 2 ;;
    *) TV="$1"; shift ;;
  esac
done
if [ -z "$TV" ]; then
  echo "ERROR: give the TV's IP address, e.g. tools/install-cursed.sh 192.168.1.50 (Settings > Network shows it)." >&2
  exit 2
fi
export TV_HOST="$TV"
step() { printf '\n==> %s\n' "$*"; }

step "1/5  The tools"
command -v python3 >/dev/null || { echo "ERROR: Python 3 is not installed (https://www.python.org/downloads/)." >&2; exit 1; }
if [ ! -x tools/venv/bin/python ]; then
  python3 -m venv tools/venv
  tools/venv/bin/pip install -q -r tools/requirements.txt
fi
PY=tools/venv/bin/python

step "2/5  Reaching the TV at $TV_HOST"
if ! "$PY" -I tools/tv.py --probe; then
  echo >&2
  echo "ERROR: could not talk to the TV. Check that it is on, that USB debugging (and Network or Wireless debugging)" >&2
  echo "is switched on in its Developer options, that you allowed this computer on the TV's screen, and that the" >&2
  echo "address is right. docs/TROUBLESHOOTING.md#connecting has the cases, including macOS's Local Network permission." >&2
  exit 1
fi

step "3/5  Building the launcher"
if [ -n "$WEATHER" ]; then
  python3 - "$WEATHER" <<'PY'
import re, sys
place, lat, lon = [p.strip() for p in sys.argv[1].split(",")]
float(lat); float(lon)   # a typo stops here, before anything is built or sent
p = "launcher/app/src/main/res/values/config.xml"
s = open(p, encoding="utf-8").read()
def put(name, value, kind="string"):
    global s
    tag = r'(<%s name="%s"[^>]*>)[^<]*(</%s>)' % (kind, name, kind)
    if not re.search(tag, s):
        sys.exit("config.xml has no " + name)
    s = re.sub(tag, lambda m: m.group(1) + value + m.group(2), s, count=1)
put("latitude", lat); put("longitude", lon); put("weather_place", place); put("show_weather", "true", "bool")
open(p, "w", encoding="utf-8").write(s)
print("Weather on for", place)
PY
fi
APK=launcher/app/build/outputs/apk/release/app-release.apk
if [ "$NO_BUILD" = 1 ]; then
  [ -f "$APK" ] || { echo "ERROR: --no-build, but $APK does not exist." >&2; exit 1; }
  export SKIP_BUILD=1
else
  native=0
  for d in "${ANDROID_HOME:-}" ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools /usr/local/share/android-commandlinetools; do
    [ -n "$d" ] && [ -d "$d/platforms" ] && native=1
  done
  case "${BUILD:-}" in native) native=1 ;; docker) native=0 ;; esac
  if [ "$native" = 1 ]; then
    echo "Building with the Android SDK on this computer (BUILD=docker builds in Docker instead)."
  elif command -v docker >/dev/null && docker info >/dev/null 2>&1; then
    echo "No Android SDK found: building in Docker (the first time builds its image, about 1.5 GB and a few minutes)."
    tools/docker-build.sh
    export SKIP_BUILD=1
  else
    echo "ERROR: nothing to build with. Install Docker Desktop (https://www.docker.com/products/docker-desktop/), start it," >&2
    echo "and run this again; or install JDK 17 and the Android SDK (platform 34), as README.md, \"Doing it by hand\", says." >&2
    exit 1
  fi
fi

step "4/5  Installing it and making it Home"
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT
# The upload prints one "chunk n/567" per piece on a single line; show it as one word.
launcher/build-and-install.sh 2>&1 | tee "$LOG" | sed -E 's/(chunk [0-9]+\/[0-9]+)+/uploading.../'
STOCK="$(sed -n 's/^Stock launcher: //p' "$LOG" | tail -n 1 | tr -d '\r')"
"$PY" -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS; echo granted' >/dev/null 2>&1 || true   # Keep watching

step "5/5  The Cursed theme"
tools/theme.sh themes/cursed

cat <<EOF

Done. The TV now opens the Cursed home screen. Press the gear for Home settings: backgrounds, rows, the dock grid.

Before you leave it: switch USB and Network/Wireless debugging off in the TV's Developer options. While it is on, anyone
on your Wi-Fi can control the TV. Then check from here:  $PY -I tools/adb-closed.py $TV_HOST

To go back:
  tools/theme.sh --reset                                   (removes the theme, keeps the launcher)
EOF
if [ -n "$STOCK" ] && [ "$STOCK" != none ]; then
  cat <<EOF
  $PY -I tools/tv.py 'pm enable --user 0 $STOCK'   (turns the TV's own launcher back on)
  then Home settings > About > Default home screen, and pick the original. docs/restore.md has the rest.
EOF
fi
