# Cursed

<img src="screenshot.jpg" width="640" alt="Home with the Cursed theme: a glass serif clock behind alpine peaks, a glass dock below">

A photograph fills Home and the clock is part of it: large glass digits stand behind the nearer scenery, so peaks,
ridges and city towers cross their lower edge, and every time still reads.

- **Fifteen scenes** that follow the time of day: morning, daytime, evening, night (four night scenes: stars, northern
  lights, a lake under the Milky Way, an old town's lights), and fog when the weather reports it. Or a different one
  each start, or one you pin. While Home sits idle with its rows put away, the scene turns to another every few minutes.
- **Backgrounds you choose:** Home settings, Look and scenes, Backgrounds lists every scene with its picture;
  switch off the ones you do not want.
- **Keep watching:** a row of picture cards above the dock, with what your apps say you are part way through, the
  title on the card and the progress along its bottom. Hold OK on a card to take a show off the row.
- **One Home settings panel** beside Home, which changes as you choose: backgrounds, rows, the grid and dock,
  motion, weather. Its sections slide, its switches slide, and Home dissolves rather than blinks when you change one.
- **A tight glass dock**, icons without names, one focus look on every control.
- **30 languages** for everything this fork adds (machine translated, not reviewed by native speakers).

## Install

About ten minutes the first time, one command. You need:

| | |
|---|---|
| **A TV** | Android TV or Google TV, Android 8 or newer. Not Samsung, LG, Roku or Vizio (they are not Android TV). |
| **A computer on the same Wi-Fi** | macOS or Linux. On Windows use [WSL](https://learn.microsoft.com/windows/wsl/install): the installer is a shell script. |
| **Python 3** and **Docker or the Android SDK** | To build the launcher. With neither installed, [Docker Desktop](https://www.docker.com/products/docker-desktop/) is the easy one: the installer builds inside it. |
| **Debugging switched on, on the TV** | Next section. |

### 1. Switch debugging on

On the TV: **Settings > Device Preferences > About**, press **Build** seven times (it says you are a developer), go
back, open **Developer options** and turn on **USB debugging**, and **Network debugging** or **Wireless debugging** if
there is such a switch. Keep the TV on.

A Chromecast with Google TV or a Google TV Streamer has Wireless debugging with a pairing code; the installer needs the
classic port, so follow [Connecting](../../docs/TROUBLESHOOTING.md#connecting) once.

### 2. Run it

```bash
git clone -b cursed https://github.com/nnishad/cleanyourtv
cd cleanyourtv
tools/install-cursed.sh
```

It looks for TVs on your Wi-Fi (a few seconds), lists the ones with debugging switched on, and asks which is yours. When the
TV asks **Allow USB debugging?**, choose Always allow. Then it shows the TV's model and Android version and asks
**Go ahead?** once more: say `y`, and it:

1. sets up the tools (a Python environment inside the folder, nothing system-wide);
2. connects to the TV;
3. builds the launcher (with your Android SDK, or in Docker if you have none; the first Docker build makes a 1.5 GB image and takes a few minutes, a few more on an M-series Mac);
4. installs it and makes it the home screen, **turning off the TV's own launcher** (it stays installed, and nothing else
   is removed). It checks that the new launcher starts before the old one is touched, and puts the old one back if it
   does not;
5. gives it permission to read what you are part way through, and pushes this theme.

Press Home on the remote. That is it.

### 3. Close the door

Switch USB and Network/Wireless debugging **off** again in Developer options: while it is on, anyone on your Wi-Fi can
control the TV. Check from the computer:

```bash
tools/venv/bin/python -I tools/adb-closed.py <your TV's address>
```

## Options

**Address, search and questions.** If you know the TV's address (Settings > Network), skip the search:
`tools/install-cursed.sh 192.168.1.50`. `tools/install-cursed.sh --find` only lists the TVs it can see. The search
covers this computer's own network (the /24 around its address); for a bigger or different one, add
`--subnet 192.168.1.0/24`. `--yes` skips the final question, for a script or an assistant that has already asked you.
Run without a keyboard (no terminal to answer on) and without `--yes`, it refuses to change anything.

**Weather** beside the date ("☽ 23° Clear"). Off until you choose a place, and the launcher makes no network request at
all while it is off. On the TV: **Home settings > Weather > Place**, press OK, type your nearest city with the TV's
keyboard, press Search, and pick the right one from the list (places with the same name are told apart by region and
country). The weather switches on. What you type goes to Open-Meteo (free, no account) once to find the place, and then the place's
coordinates go to it for the forecast every 30 minutes while Home is on screen; nothing else is sent. "Forget the place" on the same screen takes it away; the
switch above it turns the weather off and keeps the place.

**Keep watching** is hidden until an app reports something you were part way through; Netflix, YouTube and others do.
The installer has already given the launcher permission to read that list (and only read it).

**Building on a computer that has the Android SDK but you would rather use Docker**, or the other way round:
`BUILD=docker tools/install-cursed.sh` or `BUILD=native ...`. `--no-build` installs the APK you built last time.

## Using it

- **The gear** opens Home settings (backgrounds, rows, the grid and dock, motion, weather) and TV settings.
- **Inputs** is the button at the top right, with the TV's HDMI inputs, Bluetooth and Network.
- **Hold OK on an app** to move it, add it to the dock or see its info; **on a Keep watching card** to take the show off.
- **Up from the dock** goes to Keep watching. After a minute of nothing the rows fade away and the picture takes over;
  any button brings them back.

## Update

```bash
git pull
tools/install-cursed.sh
```

Your apps and their order are kept. If the update changed the theme file, the choices you made in Home settings start again from the theme.

## Undo

```bash
tools/theme.sh --reset                                                     # removes the theme, keeps the launcher
tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 <the TV's own launcher>'
```

The installer prints the exact second line at the end (usually `com.google.android.tvlauncher` or
`com.google.android.apps.tv.launcherx`). Then open **Home settings > About > Default home screen** and pick the
original. [docs/restore.md](../../docs/restore.md) has the rest.

## If something goes wrong

| What you see | What to do |
|---|---|
| *No TV with network debugging switched on was found* | Switch USB debugging (and Network or Wireless debugging) on in the TV's Developer options, keep the TV on, and run it again. A TV that answers with debugging off is named in the output. On another network, add `--subnet`; or give the address. |
| *Nothing could be reached at all* | On a Mac, allow your terminal under **System Settings > Privacy & Security > Local Network** and reopen it. |
| *could not talk to the TV* | Is it on, is debugging on, did you press Allow on the TV, is the address right? On a Mac, allow your terminal under **System Settings > Privacy & Security > Local Network**, then reopen it. More in [Connecting](../../docs/TROUBLESHOOTING.md#connecting). |
| No *Developer options* after pressing Build seven times, or USB debugging is greyed out | Some operator boxes lock it; nothing can reach the TV then. See [Troubleshooting](../../docs/TROUBLESHOOTING.md#connecting). |
| *nothing to build with* | Install and start Docker Desktop, then run it again. |
| *Unsupported class file major version* | Your JDK is too new for Gradle. Use Docker: `BUILD=docker tools/install-cursed.sh`. |
| *INSTALL_FAILED_OLDER_SDK* | The TV is older than Android 8. The launcher cannot run on it; nothing was changed. |
| The screen is black or blank after a restart | Press Home. If it stays, use the `pm enable` line under **Undo**. |
| Keep watching stays empty | Nothing has reported progress yet, or the permission was refused: Home settings > Home rows > Allow access to Watch Next. |

## How this differs from upstream CleanYourTV

The scenes, the clock and the settings panel are launcher code, so the theme needs this branch's launcher. The launcher
upstream ignores these keys.

| | Upstream launcher | This branch |
|---|---|---|
| APK size | about 0.6 MB | about 28 MB (the photos are stored losslessly) |
| Permissions | none for network | `INTERNET`, used only by the weather line you switch on (Open-Meteo); `READ_TV_LISTINGS` for Keep watching |
| Based on | current `main` | upstream as of launcher 3.3 |

This installs the launcher and the look only. To also switch off the TV maker's ads and preloaded apps, follow the
main [README](../../README.md); the two work together.

## Tested on

An Android TV 12 emulator at 1920x1080, where the screenshot and the installer were tested. An earlier build of this
branch ran on an Android TV 11 set with 2 GB of RAM. This build has not been run on a real TV yet, and not measured on
any: the cross-fades are full-screen blends, which a slow graphics chip may not do smoothly. On a 1 GB set the photos
may be too heavy. The installer, its search for TVs and its questions were run against that emulator on a Mac (the search found a device on this computer's loopback, not a real TV: the real one was off), and so was choosing a city for the weather. They were not tried on Linux, Windows (WSL), a Docker build or a real TV.

## Credits

Photos and fonts: [launcher/SCENES-CREDITS.md](../../launcher/SCENES-CREDITS.md). Two of the night photos are
CC BY-SA 4.0 (share-alike): keep their credit if you redistribute. Built on
[CleanYourTV](https://github.com/Gathin23/cleanyourtv); the scenes look comes from [couch-tv](https://github.com/Seagey/couch-tv).
