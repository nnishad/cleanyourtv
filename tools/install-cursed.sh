#!/usr/bin/env bash
# Put the Cursed look on a TV in one go: the tools' Python environment, the launcher built and made Home, the
# permission Keep watching needs, and the theme pushed. Every step is an existing script of this repository
# (launcher/build-and-install.sh keeps its Home safety checks); this one only runs them in order and stops, before
# the TV is touched, when something the later steps need is missing.
#
# Usage: tools/install-cursed.sh [<tv ip>] [--no-build] [--yes]
#        tools/install-cursed.sh --find         only list the TVs on this Wi-Fi
#   With no address it looks for TVs on this Wi-Fi (devices with network debugging switched on), lists them, and asks
#   which one is yours. Before it changes anything it shows what the TV is and asks once more; --yes skips that
#   question (a script or an assistant passes it after the person has said yes).
#   --subnet    the network to search when it is not the usual /24 around this computer, e.g. 192.168.1.0/24
#   --no-build  install the APK already built (launcher/app/build/outputs/apk/release/app-release.apk)
# BUILD=docker or BUILD=native forces the way the launcher is built; by default a computer with the Android SDK builds
# natively and one without builds in Docker (tools/docker-build.sh), so it needs nothing else installed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NO_BUILD=0; TV="${TV_HOST:-}"; YES=0; FIND=0; SUBNET=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y) YES=1; shift ;;
    --find) FIND=1; shift ;;
    --subnet) SUBNET="${2:-}"; [ -n "$SUBNET" ] || { echo "ERROR: --subnet needs a network like 192.168.1.0/24." >&2; exit 2; }; shift 2 ;;
    --no-build) NO_BUILD=1; shift ;;
    -h|--help) sed -n 2,22p "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "ERROR: unknown option $1 (see --help)." >&2; exit 2 ;;
    *) TV="$1"; shift ;;
  esac
done
command -v python3 >/dev/null || { echo "ERROR: Python 3 is not installed (https://www.python.org/downloads/)." >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }
INTERACTIVE=0; [ -t 0 ] && INTERACTIVE=1

# Devices on this network that answer on the ports a TV opens. One line each: "ready|ip|name" when network ADB (5555,
# which this installer needs) is open, "off|ip|name" for a TV that answers but has debugging switched off. Only TCP
# connections to three ports are tried, nothing is sent to what answers.
discover() {
  python3 - "$SUBNET" <<'PY'
import errno, ipaddress, socket, sys
from concurrent.futures import ThreadPoolExecutor
ADB, TV_PORTS = 5555, (6466, 8009)    # 6466 Android TV remote service, 8009 Chromecast
arg = sys.argv[1]
mine = None
if arg:
    net = ipaddress.ip_network(arg, strict=False)
else:
    u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        u.connect(("10.255.255.255", 1))   # sends nothing; it only picks this computer's interface
        mine = u.getsockname()[0]
    finally:
        u.close()
    net = ipaddress.ip_network(mine + "/24", strict=False)
hosts = [h for h in (list(net.hosts()) or [net.network_address]) if str(h) != mine]
unreachable = [0]
def tcp(host, port):
    try:
        with socket.create_connection((str(host), port), timeout=0.7):
            return True
    except OSError as e:
        if e.errno in (errno.EHOSTUNREACH, errno.ENETUNREACH, errno.EACCES, errno.EPERM):
            unreachable[0] += 1
        return False
def look(host):
    return host, tcp(host, ADB), any(tcp(host, p) for p in TV_PORTS)
def name(host):
    try:
        return socket.gethostbyaddr(str(host))[0]
    except OSError:
        return ""
with ThreadPoolExecutor(max_workers=96) as pool:
    results = list(pool.map(look, hosts))
found = 0
for host, adb, tv in results:
    if adb or tv:
        found += 1
        print("%s|%s|%s" % ("ready" if adb else "off", host, name(host)))
if not found and unreachable[0] > len(hosts):
    print("blocked||", end="")
    sys.exit(3)
PY
}

if [ -z "$TV" ] || [ "$FIND" = 1 ]; then
  echo "Looking for Android TVs on this Wi-Fi (a few seconds)..."
  rc=0; FOUND="$(discover)" || rc=$?
  if [ "$rc" = 3 ]; then
    echo "Nothing could be reached at all. On a Mac, allow your terminal under System Settings > Privacy & Security >" >&2
    echo "Local Network, reopen it and run this again; or give the TV's address yourself." >&2
    exit 1
  fi
  READY="$(printf '%s\n' "$FOUND" | sed -n 's/^ready|//p')"
  OFF="$(printf '%s\n' "$FOUND" | sed -n 's/^off|//p')"
  NREADY=$(printf '%s' "$READY" | grep -c . || true)
  show() { printf '%s\n' "$1" | awk -F'|' '{ n = NR ")"; printf "  %-3s %-16s %s\n", n, $1, $2 }'; }
  if [ "$NREADY" -gt 0 ]; then
    echo "Devices with network debugging switched on:"; show "$READY"
  fi
  if [ -n "$OFF" ]; then
    echo "A TV answered here with debugging switched OFF (switch USB and Network debugging on in its Developer options):"
    printf '%s\n' "$OFF" | awk -F'|' '{ printf "      %-16s %s\n", $1, $2 }'
  fi
  if [ "$NREADY" = 0 ]; then
    echo "No TV with network debugging switched on was found. Switch USB debugging (and Network or Wireless debugging)" >&2
    echo "on in the TV's Developer options and keep it on, then run this again; or give its address: tools/install-cursed.sh <ip>." >&2
    exit 1
  fi
  [ "$FIND" = 1 ] && exit 0
  if [ "$INTERACTIVE" != 1 ]; then
    echo "Which one is the TV? Ask the person, then run: tools/install-cursed.sh <ip> --yes" >&2
    exit 2
  fi
  DEFAULT=""; [ "$NREADY" = 1 ] && DEFAULT=1
  while [ -z "$TV" ]; do
    read -r -p "Which one is your TV? Number or address${DEFAULT:+ [$DEFAULT]}, q to quit: " PICK || exit 1
    PICK="${PICK:-$DEFAULT}"
    case "$PICK" in
      q|Q) exit 0 ;;
      ''|*[!0-9.]*) echo "A number from the list, or an address." >&2 ;;
      *.*) TV="$PICK" ;;
      *) TV="$(printf '%s\n' "$READY" | sed -n "${PICK}p" | cut -d'|' -f1)"; [ -n "$TV" ] || echo "No such number." >&2 ;;
    esac
  done
fi
if [ -z "$TV" ]; then
  echo "ERROR: no TV address (try tools/install-cursed.sh --find)." >&2
  exit 2
fi
if [ "$YES" != 1 ] && [ "$INTERACTIVE" != 1 ]; then
  echo "ERROR: this changes the TV's home screen, so it asks first and there is no one to ask here. After the person has" >&2
  echo "said yes, run it again with --yes." >&2
  exit 2
fi
export TV_HOST="$TV"

step "1/5  The tools"
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

if [ "$YES" != 1 ]; then
  echo
  echo "That is the TV at $TV_HOST. The next steps make Cursed its home screen: they build and install the launcher and turn"
  echo "off the TV's own launcher (nothing is uninstalled, and it is put back if the new one does not start)."
  read -r -p "Go ahead? [y/N] " GO || exit 1
  case "$GO" in y|Y|yes|YES) ;; *) echo "Nothing was changed."; exit 0 ;; esac
fi

step "3/5  Building the launcher"
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
# Keep watching reads the Watch Next list; the weather may ask the TV where it is (build-and-install already granted the
# precise location, which is also what reads the Wi-Fi name; this adds the coarse one Android 12 wants beside it).
"$PY" -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS; pm grant com.gathin.tvhome android.permission.ACCESS_COARSE_LOCATION; echo granted' >/dev/null 2>&1 || true
LOCATION_MODE="$("$PY" -I tools/tv.py 'settings get secure location_mode' 2>/dev/null | tail -n 1 | tr -d '\r' || true)"

step "5/5  The Cursed theme"
tools/theme.sh themes/cursed

cat <<EOF

Done. The TV now opens the Cursed home screen. Press the gear for Home settings: backgrounds, rows, the dock grid, and
Weather > Place, where you can type a city. Until you do, the weather beside the date uses where the TV says it is.
$( [ "${LOCATION_MODE:-}" = 0 ] && printf '\nThe TV'"'"'s Location setting is off, so it cannot say where it is: switch it on (Settings > Device Preferences >\nLocation), or type a city in Home settings > Weather > Place.\n' )

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
