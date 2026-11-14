# Sample prompt for an AI coding assistant

Use this if you would rather have an assistant (Claude Code, Codex or similar, with shell access, on a computer on the same
Wi-Fi as the TV) do the debloating and the install. Without an assistant, `tools/setup-cursed.sh` does the same job: see
the [guide](README.md#b-one-command-and-switch-off-what-the-tv-maker-runs).

On the TV first: Settings > Device Preferences > About, press OK on **Build** seven times, back, Developer options, turn
on **USB debugging** (and Network or Wireless debugging if offered). Keep the TV on.

Then clone the fork, open that folder in your assistant, fill in the three `<...>` values and paste:

```
I want to debloat my Android TV and put the Cursed home screen on it.
My TV model is: <TV MODEL>
My TV's IP address is: <IP ADDRESS>
My city for the weather line (optional): <City, or "skip">

Work only inside this clone of the Cursed fork of CleanYourTV (git clone -b cursed
https://github.com/nnishad/cleanyourtv), so its safety checks stay in place. Read AGENTS.md and
PROMPT.md first and follow them. Their safety rules are strict: back up before changing anything,
disable apps and never uninstall them, leave what the TV needs to work, and ask me before
disabling anything my family uses (streaming apps, voice assistant, Chromecast, firmware updater).

Rollback points. Do this before anything else changes:
- Make a git checkpoint of the repo: run `git add -A && git commit -m "checkpoint"` (or note
  the commit if nothing is changed) and tell me its hash. Commit again before each step that
  edits a file. If something in the repo goes wrong, `git reset --hard <that commit>` puts it back.
- Save the backup PROMPT.md asks for (package list, disabled list, settings) into backup/. That
  folder, ~/.cleanyourtv/ and shots/ hold device details: never commit them.
- The TV itself is rolled back with `tools/install-cursed.sh <ip> --rollback` (the launcher,
  theme and anything a --debloat list switched off) and `tools/restore.sh <list>` (a debloat list).
  Both work from records written before the first change. If a step fails, stop, say what
  failed, run the matching rollback, and tell me; do not retry in a loop.

Then, in this order, checking each step with screenshots pulled from the TV:
1. Connect to the TV at the IP address I gave you over ADB. Do not scan my network or look
   for other devices. Confirm the model and Android version with me.
2. Install the launcher and theme first (some lists need the new launcher in place before
   they are applied): tools/install-cursed.sh <ip> --yes. Add --no-build only if I tell you to.
   Do not write or install any other launcher, change the launcher's code, or use another theme.
3. Debloat as PROMPT.md describes, using the repo's tools (tools/disable.sh), with the tested list
   in examples/ for my TV if there is one for my exact model and build, else a list built from
   tools/privacy.py. Read the list's header for ordering notes. Ask me the checklist questions
   and show me the list; disable nothing until I say yes.
4. Check Home with a screenshot, then tell me to switch USB and Network debugging off in the
   TV's Developer options, and confirm with tools/adb-closed.py.

The weather needs nothing from you: it uses where the TV says it is, and I can type a city in
Home settings > Weather > Place.
```
