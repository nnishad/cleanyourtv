# Restore point: CleanYourTV with the B3 look (2026-11-14)

What was on the TCL Smart TV Pro (Android 11) when this was saved, working and fast:

- **App:** `com.gathin.tvhome`, built from git tag `restore-native-b3` (commit `f76afaac`).
  Installed APK md5 `d4f780e7ea964c3ef65ad55a802f7689` (591,009 bytes). The APK itself is not in the
  repo (repo rule); the tag rebuilds it.
- **Theme:** `theme/` here (config.json, 6.3 MB of wallpapers, credits) = the B3 cinematic look with
  cities and nature wallpapers, glass dock, weather line (Vilnius).
- **TV state:** Home = `com.gathin.tvhome/.MainActivity`; `HomeKeyService` enabled; the couch-tv app
  (`app.couchtv.launcher`) still installed but its Home watch (FireWatch) switched off; stock
  Google TV home left alone.
- **Benchmark on this TV:** cold start 855 ms (p50), launcher memory 52 MB (`work/bench-port-step1`).

## Restore

```sh
git checkout restore-native-b3                      # or: git checkout -b back f76afaac
cd launcher
TV_HOST=<tv ip> STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 ./build-and-install.sh
cd ..
TV_HOST=<tv ip> tools/theme.sh restore/2026-11-14-native-b3/theme
```

Needs the TV's Network debugging on and `tools/venv` (see AGENTS.md). If couch-tv's Home watch is
on, switch it off first: `settings put secure enabled_accessibility_services ""` over adb.

Weather uses the INTERNET permission added in the first commit of the branch; this build is for this
TV only and is not meant for upstream.
