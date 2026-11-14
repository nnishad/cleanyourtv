# Restore point: CleanYourTV with the couch-tv look ported (2026-11-14)

What is on the TCL Smart TV Pro (Android 11) at this point:

- **App:** `com.gathin.tvhome` built from git tag `couch-port-v1` (APK md5 `a6bdf400a8acdbf3fa509cc33d76c311`, 2658291 bytes; the
  APK is not in the repo, the tag rebuilds it).
- **Theme:** `config.json` here (also `themes-local/couch-scenes/`), turning on:
  - `scenes`: the five couch-tv scenes with the glass clock (SceneView), one per start
  - `watch_row`: Keep watching from Android's Watch Next (KeepWatching)
  - `home_idle_clock_minutes`: 15 (the ambient clock that CleanYourTV already had)
  - `hide_rows_idle_seconds`: 0 (off; set e.g. 60 to fade the rows out when idle)
- **Needs:** `pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS` once per install (not granted on install).
- **Older restore point:** `restore/2026-11-14-native-b3` (tag `restore-native-b3`), the B3 wallpaper look before the port.

## Restore

```sh
git checkout couch-port-v1
cd launcher
TV_HOST=<tv ip> STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 ./build-and-install.sh
cd ..
TV_HOST=<tv ip> tools/theme.sh themes-local/couch-scenes
tools/venv/bin/python -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS'
```

Reinstalling switches the Home key helper off: `settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService`
puts it back (as `build-and-install.sh` does with HOME_KEY_SERVICE=1).

Not for upstream: adds INTERNET (weather and Keep watching pictures), READ_TV_LISTINGS and 2 MB of scene pictures
(the APK size gate does not apply to this fork).
