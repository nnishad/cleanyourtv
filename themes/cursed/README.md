# Cursed

<img src="screenshot.jpg" width="640" alt="Home with the Cursed theme: a glass serif clock behind alpine peaks, a glass dock below">

A photograph fills Home and the clock is part of it: large glass digits stand behind the nearer scenery, so peaks,
ridges and city towers cross their lower edge, and every time still reads.

- **Eleven scenes** that follow the time of day: morning, daytime, evening, night, and fog when the weather
  reports it. Or a different one each start, or one you pin.
- **Backgrounds you choose:** Home settings, Look and scenes, Backgrounds lists every scene with its picture;
  switch off the ones you do not want.
- **Keep watching:** a row above the dock with what your apps say you are part way through.
- **Home settings** in a panel beside Home, which changes as you choose.
- **A tight glass dock**, icons without names, one focus look on every control.

## This is a fork, not only a theme

The scenes, the clock and the settings panel are launcher code. `config.json` here only switches them on, so the
theme needs the launcher from this branch. The launcher upstream ignores these keys.

| | Upstream launcher | This branch |
|---|---|---|
| APK size | about 0.6 MB | about 19 MB (the photos are stored losslessly) |
| Permissions | none for network | `INTERNET`, used only by the optional weather line; `READ_TV_LISTINGS` for Keep watching |
| Based on | current `main` | upstream as of launcher 3.3 |

## Install

You need what the main [README](../../README.md) asks for: ADB debugging on the TV, Python 3, JDK 17 and the
Android SDK (platform 34). A TV that is new to CleanYourTV should go through the README's quick start first;
it covers the backup and the Home safety checks.

```bash
git clone -b cursed https://github.com/nnishad/cleanyourtv
cd cleanyourtv
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt

TV_HOST=192.168.1.50 launcher/build-and-install.sh      # builds the launcher and makes it Home
TV_HOST=192.168.1.50 tools/theme.sh themes/cursed       # switches the theme on
```

Replace `192.168.1.50` with your TV's address. To go back: `tools/theme.sh --reset` removes the theme, and
`tools/restore.sh` undoes the rest.

### Keep watching

The row needs permission to read Android's Watch Next list. Either open Home settings, Home rows, and choose
Allow Watch Next, or grant it over ADB:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS'
```

The row stays hidden while no app reports anything in progress.

### Weather line (optional)

Off as shipped, and the launcher makes no network request without it. To show "Place · 24°C · Clear" beside the
date, set these in `launcher/app/src/main/res/values/config.xml` before building:

```xml
<string name="latitude" translatable="false">54.69</string>
<string name="longitude" translatable="false">25.28</string>
<bool name="show_weather">true</bool>
<string name="weather_place" translatable="false">Vilnius</string>
```

The readings come from Open-Meteo, one small request every 30 minutes while Home is on screen.

## Tested on

An Android TV 12 emulator at 1920x1080, where the screenshot was taken. An earlier build of this branch ran on an
Android TV 11 set with 2 GB of RAM. It has not been measured on other TVs; on a 1 GB set the photos may be too heavy.

## Credits

Photos and fonts: [launcher/SCENES-CREDITS.md](../../launcher/SCENES-CREDITS.md). Built on [CleanYourTV](https://github.com/Gathin23/cleanyourtv);
the scenes look comes from [couch-tv](https://github.com/Seagey/couch-tv).
