# Launcher revamp: design system and implementation plan

Branch `couch-port`, starting from commit `41e12148`. Target: TCL Smart TV Pro (Android TV 11,
1920x1080 at 60 Hz, density 320, 2 GB RAM, Mali-G31). Built and reviewed on the `TCL_TV_1080`
emulator first; nothing goes to the TV without an explicit yes.

## 1. Where the launcher is today (audit)

Measured in `launcher/app/src/main` and on the emulator.

| Area | Today | Problem |
|---|---|---|
| Focus | 5 different treatments: glass slab + zoom (tiles), 2 dp white ring (Keep watching), ringed pill (menus), solid white fill with dark text (Home settings), brighter ring + 4% zoom (top buttons) | Moving between rows feels like moving between apps |
| Corner radii | 6 values in drawables (14, 16, 18, 21, 24, 26 dp) plus 4 more in code | Nothing nests cleanly |
| Type | 16 sizes from 8 sp to 120 sp; 8, 10 and 11 sp are 16 to 22 px on the panel | Too small to read from a sofa; no scale |
| Motion | About 20 different durations (60 to 1200 ms), 4 interpolators | No shared rhythm |
| Colour | 54 hard-coded values across layouts and drawables | No roles; contrast depends on which scene came up |
| Settings | Two screens with two designs: Home settings (cards over dimmed Home) and Launcher settings (flat `#101419` page) | One job, two looks |
| Menus | Inputs, gear, tile menu and sleep-style lists are each sized and anchored by hand | Inconsistent width, padding and position |
| Text | 18 English strings hard-coded in `CouchSettingsActivity`; upstream ships about 30 languages | The port broke localisation |
| Speed | App work per frame under 1 ms; GPU draw about 23 ms per animated frame; the TV composites on the same GPU | 60 fps is out of reach; design has to be cheap per frame |

## 2. What premium TV interfaces do (research)

- **Focus is depth, not an outline.** tvOS lifts the focused item with parallax; after inactivity it
  dims everything else and grows the focused item. (Apple HIG, Focus and selection.)
- **One focus vocabulary.** Compose for TV defines four indications (scale, border, glow, colour)
  and applies the same ones to every surface; its card default is 1.1x scale with a 3 dp border.
  (Android Developers blog; `CardDefaults` reference.)
- **Safe area.** Android TV asks for about 5% margins (48 dp sides, 27 dp top and bottom) for
  content, with backgrounds full-bleed. (Android TV layout guide.)
- **Low density, large type, high contrast, predictable D-pad paths.** (Amazon Fire TV guidelines;
  Smashing Magazine, Designing for TV.)

Not from the sources: the exact type floor and timing values below are my own choices for this
panel, to be tuned on the TV.

## 3. Design direction: "one sheet of glass"

Home is a photograph with one layer of glass floating over it. Everything you can focus is glass;
focus is one bright piece of glass that travels with you.

### 3.1 Tokens (one source of truth)

New `res/values/tokens.xml` (dimens, colours, styles) and `Tokens.java` / `Motion.java` for code.
Every layout, drawable and animation reads from these; no literals elsewhere.

| Token | Values |
|---|---|
| Type scale (Outfit) | Caption 12 sp, Body 14 sp, Label 16 sp, Title 20 sp, Headline 28 sp; the clock is Instrument Serif (Antonio until `21172321`, see section 7). Nothing under 12 sp (24 px) |
| Radii | Small 12 dp (thumbnails, chips), Medium 20 dp (tiles, cards, rows), Large 28 dp (dock, sheets, popovers), Full (round buttons) |
| Spacing | 4 dp grid: 4, 8, 12, 16, 24, 32, 48 |
| Screen margins | Bands 24 dp from the sides (as now); all text and controls at least 48 dp from the sides and 27 dp from top and bottom |
| Glass surfaces | Rest (white 10%), Raised (white 16%), Focus (white 24% + 2 dp white rim) |
| Text | Primary 100%, Secondary 72%, Tertiary 52% white; one soft shadow style for text over photos |
| Accent | One colour per scene, sampled once from the photo (warm for Golden, teal for Waterline...). Used for progress bars, switches and the focus glow |
| Motion | Fast 120 ms (press, colour), Standard 200 ms (focus, menus), Slow 320 ms (screens, scene). Two curves: decelerate for entering, standard for moving. One spring for the focus glass |

### 3.2 One focus system

- A single focus glass (today's `GlassView`, which already springs between tiles) serves every row
  on Home: top buttons, Keep watching, dock, app grid. It glides and resizes from one target to the
  next instead of each row drawing its own ring.
- The focused item: 4% zoom, rim, full brightness. Unfocused neighbours stay as they are; after 20 s
  without a key they dim to 70% (the tvOS inactivity cue).
- Popovers and the settings sheet use the same glass, rim and zoom. Solid white rows go.
- Press: OK down shrinks the item to 97%; release springs back and acts.
- Row ends: a 6 dp nudge and return when there is nowhere further to go.
- Navigation sounds: the same system up/down/left/right clicks on every component.

### 3.3 Home

- **Top bar:** Inputs, network, settings as three equal glass buttons; same height, radius and focus
  as everything else.
- **Hero:** clock in the scene; date and weather line above it. A new **context line** replaces the
  weather text while something is focused: the app's name, or for a Keep watching card "S1 E30 ·
  23 min left". This brings names back without labels under tiles.
- **Keep watching v2:** app icon badge instead of the tiny caps label; title 16 sp; accent progress
  bar; fourth card peeks with the edge shade (kept).
- **Dock and app grid:** as now, on the tokens. Tile launch grows from the tile's own bounds
  (`ActivityOptions` scale-up, drawn by the system).
- **Scenes that know the time:** Dawn in the morning, Waterline by day, Golden hour in the evening,
  Night at night, Fog when the weather reading says fog, mist or heavy cloud. "Different each
  start" stays as an option. (Now 13 scenes in five parts of the day, each one switchable: section 7.)
- **Arrival:** returning Home, rows rise 12 dp and fade in, 40 ms apart, once. Cold start skips it.

### 3.4 Menus and settings

- **Popover** (one component): Inputs, the gear menu and the tile menu. Same width rules, 28 dp
  radius, rows 56 dp, anchored to the control that opened it.
- **Settings sheet** (one component): a panel on the right 44% of the screen; Home stays visible on
  the left and updates live as options change. Home settings and Launcher settings merge into it:
  Look, Home rows, Apps and layout, Weather, About. Row kinds: switch, choice, action, info.
- **Reorder mode, app library, card prompt:** restyled with the same rows, radii and focus.

### 3.5 Idle and ambient

- 20 s: unfocused items dim. Chosen time: rows fade, leaving scene, clock and weather (exists).
  Later: the ambient clock, restyled with the same type and accent. The screensaver matches.

### 3.6 Accessibility and language

- Text never under 12 sp; a bottom and top shade tuned per scene so text meets contrast on every
  photo; focus visible without relying on colour.
- Reduced motion (already a switch) turns springs, arrival and nudges into plain fades.
- Content descriptions on every control; the context line is announced to TalkBack.
- All new strings in `strings.xml`; English only at first, structure ready for the other languages.
- First run: three one-line hints (hold OK to move an app, Up for Keep watching, gear for settings).

### 3.7 Performance rules (this GPU)

- Animate only position, scale and opacity of small views; never layout, never a full-screen layer.
- No blur, no new full-screen draws; the scene stays one cached layer.
- At most the focus glass and one item animating at a time; nothing over 320 ms.
- Gates, measured on the TV with `jank.sh` and `tools/bench.py` after each phase: dock median frame
  no worse than 27 ms, cold start under 900 ms, memory under 70 MB.

## 4. Implementation phases

Each phase ends with an emulator screenshot sheet for review; the TV install is a separate yes.

| Phase | Work | Main files | Size |
|---|---|---|---|
| 0. Foundations | Tokens, `Motion`, shared styles; replace literals; screenshot script that captures every screen into one sheet (before/after) | `res/values/tokens.xml`, `Motion.java`, all layouts and drawables | M |
| 1. Focus system | One focus glass across Home; press, nudge, sounds; inactivity dim | `GlassView`, `MainActivity`, `AppAdapter`, `KeepWatching` | L |
| 2. Home | Top bar, context line, Keep watching v2, scene accent, time and weather scenes, arrival, launch animation | `SceneView`, `KeepWatching`, `MainActivity`, `activity_main.xml` | L |
| 3. Menus and settings | Popover and sheet components; merge the two settings screens; live preview; restyle reorder, library, prompts | new `Popover`, `SettingsSheet`; `CouchSettingsActivity`, `LauncherSettingsActivity` | L |
| 4. Idle and ambient | Dim, ambient clock and screensaver on the tokens | `ClockScene`, `ClockActivity`, `ClockDream` | S |
| 5. Accessibility and language | Strings to resources, descriptions, contrast shades, first-run hints | `strings.xml`, all screens | M |
| 6. TV tuning | Install, measure against the gates, tune timings and sizes on the real panel | — | S |

## 5. Risks and open choices

- **Live preview in settings** needs Home to update while the sheet is open; if that proves
  fragile, the sheet still works and Home refreshes on close, as today.
- **One focus glass across rows** changes D-pad code that upstream tuned carefully; this is where
  regressions are most likely, so Phase 1 gets the most emulator testing.
- **Frame rate:** the gates hold the line; they do not promise 60 fps on this TV.
- **Upstream:** the fork diverges further; pulling CleanYourTV updates gets harder.
- **Choices that are yours:** right-side settings sheet (or keep full-screen); glass focus in
  settings (or keep white rows); time-aware scenes as the default (or keep random).

## 6. Status (2026-11-14, emulator only)

| Phase | State | Commit |
|---|---|---|
| 0 Foundations | Done: `tokens.xml`, `Motion` tokens, shared `glass_*` drawables, `tools/local-sheet.sh` | `ccdb5c8f` |
| 1 Focus | Done, with one change of plan: every control shares the same glass, zoom, press dip and end nudge (`Focus`), and tiles keep their moving glass slab. A single slab gliding across *all* rows was not built: it would rewrite upstream's tuned D-pad code for little visible gain. Inactivity dim after 20 s | `ccdb5c8f` |
| 2 Home | Done: context line, Keep watching v2 (icon badge, accent progress, zoom room), scene accent, time and fog scenes, arrival fade, launch from tile | `5e232ea1` |
| 3 Menus and settings | Done: right-side sheet with live preview, launcher settings on the tokens. Reorder mode, app library and card prompt use the shared drawables but were not redesigned | `0fc07921` |
| 4 Idle and ambient | Done: clock and screensaver on Antonio and the tokens (not checked on screen: the emulator will not open it from adb) | `0fa6608b` |
| 5 Accessibility and language | Done: strings in `strings.xml` (English only), card descriptions, first-run tips | `b4241270` |
| 6 TV tuning | Not started: needs the TV, and the user's yes |  |

Not yet measured: frame times on the TV. The press dip, nudge, arrival and dim add short animations of
small views only; `jank.sh` and `tools/bench.py` must confirm the phase 6 gates before this ships.

## 7. After the revamp: scenes, clock and backgrounds (2026-11-14, emulator only)

Everything below was built and checked with screenshots on the `TCL_TV_1080` emulator. The TV is still on tag
`couch-port-v7` (before the revamp); nothing here has been installed on it.

### 7.1 What changed

| Change | Detail | Commit |
|---|---|---|
| Date line follows the clock | Drawn just above the digits (`spec.top - 12`), left-aligned when the clock is (Fog), instead of fixed at the screen's centre | `8bdfa20d` |
| Waterline, Fog | Waterline's clock raised (top 268 to 200) to clear the Keep watching row; Fog's digits start on the rows' left edge (68 px), measured from the visible digit, not the glyph's side bearing | `5225b810` |
| Eight new scenes | See 7.2. Lossless photos, depth-placed clocks, full-colour decode for every scene photo (16-bit colour banded smooth skies) | `85881a7b` |
| Clock typeface | Instrument Serif replaces Antonio on Home and the ambient clock. The face is scaled into the digit box each scene was placed for (digits 0.87 x size tall, baseline 0.9175 x size below top), so placements carry over to any face | `21172321` |
| Readable at every time | Over the digits the cut-out fades out from 90% of their height up to 62%: scenery covers only their lower part. Before, some digit was 40 to 78% hidden at some time on 10 of the 11 scenes with a cut-out. The top shade now draws over clock and cut-out alike | `c87526e8` |
| Backgrounds on or off | Home settings > Look and scenes > Backgrounds: one row per scene with its picture, name, part of day and On/Off. Stored as `scenes_off` (ids joined by commas; also a theme key). At least one stays on | `d4701080`, `ab676939` |

### 7.2 The scenes

`SceneView.SPECS` holds all 12. Night (couch-tv's original) was removed on 2026-11-14 at the user's request; from 19 to 5
Home now shows City at dusk or Ember ridges, the two darkest scenes. `top` and `size` are in px at 1920 wide.

| Id | Name in settings | Part of day | top / size | Cut-out | Photo |
|---|---|---|---|---|---|
| `golden` | Golden hour | Evening, 16 to 19 | 152 / 390 | yes | couch-tv original |
| `water` | Waterline | Daytime, 9 to 16 | 200 / 420 | no | couch-tv original |
| `fog` | Fog | Fog and mist | 172 / 470, left 68 | yes | couch-tv original |
| `dawn` | Dawn | Morning, 5 to 9 | 230 / 400 | yes | couch-tv original |
| `valley` | Misty valley | Fog and mist | 160 / 440 | yes | Magnific (Freepik) free image, "beautiful nature landscape with river mountains" |
| `bridge` | Arch bridge | Daytime | 250 / 380 | mask present, clock sits clear of it inside the arch | Pexels, mdx014, 814499 |
| `alpine` | Alpine lake | Daytime | 140 / 440 | yes | Pexels, willianjusten, 24604748 |
| `ember` | Ember ridges | Morning | 190 / 400 | yes | Pexels, hailsajal, 35132323 |
| `highland` | Highland valley | Daytime | 170 / 420 | yes | Pexels, maxmishin, 10026140 |
| `lake` | Mountain lake | Daytime | 180 / 440 | yes | Pexels, jplenio, 1110656 |
| `snow` | Snow ridge | Daytime | 170 / 440 | yes | Pexels, winter-visual, 30114691 |
| `city` | City at dusk | Evening | 230 / 380 | yes | Pexels, dawn-lio, 4138542 |

Licences: Pexels photos are free to use without attribution. The Magnific free licence asks for attribution when
an image is published; fine on one's own TV, to be checked before this goes into a shared theme.

**Picking a scene.** "Follows the time" (the default) picks one of the switched-on scenes for the current part of
the day (`BY_TIME`), not the one shown last, and keeps it until the part changes; when none of that part's scenes
is on, any switched-on scene. "Different each start" picks among the switched-on scenes. A pinned scene that is
switched off falls back to following the time.

### 7.3 Files per scene (`launcher/app/src/main/assets/scenes/`)

| Kind | Original five | New eight |
|---|---|---|
| Photo, 1920x1080 | `<id>.jpg` | `<id>.webp`, lossless (1.5 to 3 MB each) |
| Blurred copy that fills the digits | `<id>-glass.jpg` | `<id>-glass.jpg` (blur 16 px at 1920, brightness 1.3, saturation 1.2: the recipe fitted from the originals) |
| Scenery in front of the digits | `<id>-fg.webp` (a cut-out picture) | `<id>-mask.png` (alpha only); the cut-out is the photo's own pixels through it, made at load (`throughMask`) |
| Picture for the Backgrounds list | `thumbs/<id>.jpg`, 192x108 | same |

### 7.4 How a new scene was made

1. Scale the photo to 1920 wide (Lanczos), keeping its height for now.
2. Depth map with Depth Anything V2 Small (1 = near).
3. Search the 16:9 crop offset, clock size and height so that scenery nearer than a depth threshold covers about
   a third of the digits, at their bottom, leaves their top clear, and the visible digits end above the Keep
   watching heading (about y 585).
4. Refine the mask along the depth edge with GrabCut, drop specks, fill holes, feather 0.8 px.
5. Encode: photo `cwebp -lossless -z 9`, glass JPEG q90, mask as an alpha-only PNG, thumbnail JPEG.
6. Add a `scene(id, top, size, fillBright, shade)` line to `SPECS` (`BRIGHT_SKY` shade for pale skies), its id to
   `BY_TIME`, and its name to `CouchSettingsActivity.SCENE_IDS/SCENE_NAMES` and `strings.xml`.
7. Check every 12-hour time for hidden digits, then screenshot on the emulator.

The scripts for steps 1 to 4 and 7 were run from a temporary folder and are not in the repo.

### 7.5 Costs and what is not verified

- **APK size:** 2.7 MB before the new scenes, 20.9 MB now (lossless photos). Upstream's 576 KB gate was already
  left behind by the couch-tv port; this fork cannot go back upstream as it is.
- **Memory:** the photo now decodes at 32-bit colour, about 4 MB more than before while Home is showing; the
  new cut-outs are built at load from photo and mask.
- **Not measured on the TV:** load time of a lossless photo, frame times with the cut-out fade (one extra
  off-screen layer inside the scene's own GPU layer, redrawn once a minute), memory. Phase 6 covers these.
- **Not checked on screen:** the ambient clock in Instrument Serif (the emulator will not open it from adb).
- **Still open from section 6:** phase 6 on the TV, redesign of reorder mode, app library and card prompt,
  languages other than English.

### 7.6 Looked at, not built

- **Phone remote.** The TV answers on the Android TV remote service ports (6466, 6467), so the Google TV app
  works as a Wi-Fi remote today. A remote of our own would be a web page plus a small always-on server speaking
  that service (no ADB, no launcher change). A remote inside the launcher is not possible on Android 11 without
  ADB: apps cannot press D-pad keys before Android 13. Put off by choice.
- **Header on the left.** Kept centred: the date line is the clock's caption, and now moves with it.
- **Before the TV work ends:** ADB over the network (port 5555) is still on; close it as `AGENTS.md` describes.

## 8. Keep watching, Inputs and the polish pass (2026-11-14, emulator only)

| Change | Detail | Commit |
|---|---|---|
| Night scene removed | At the user's request. From 19 to 5 Home shows City at dusk or Ember ridges | `274236c6` |
| Keep watching cards | Picture cards with the title inside, 4.4 across the dock band (four whole and a part of a fifth). Where you are and how long is left show on the focused card only. A tile-sized version in the dock's columns was tried first (`480c47dd`) and replaced | `2cae6e51` |
| Cut cards fade | A card cut by the row's edge fades into the scene (`FadeRow`); the black edge shade is gone | `acf8fad1` |
| Inputs button | Always shown with `couch_menus`: its list always ends with Bluetooth and Network | `489f4449` |
| Polish pass | The twelve items below | this commit |

The polish pass, from an audit of every screen:

1. **Context line when scrolled.** Past the dock it rode up with the Keep watching heading and sat on the gear button; it
   now stays just above the topmost row of tiles.
2. **Windows over Home are near opaque.** `glass_panel` (Inputs, gear and tile menus, hints) went from 40 to 72% dark
   to 96 to 98%: the tile menu opens over the dock tiles and its rows ran into their logos. The sheet is 97%.
3. **Hints in our own glass.** Every toast became `hint()`, a glass pill at the top left on the top buttons' line; the
   system toast sat on the dock tiles during a reorder.
4. **One settings screen.** Apps per row, rows below dock, dock opacity, animations, advanced effects, wallpaper, open
   on start and default home moved into the sheet (sections: Look and scenes, Home rows, Apps and layout, Motion,
   Weather, About). `LauncherSettingsActivity` stays for themes without `couch_menus`; the sheet no longer opens it.
5. **Switches and steppers.** On/off rows show `SwitchView`, whose thumb slides; rows with a few values show the value
   between two small arrows and step with OK or Right, back with Left. Rows that open a page end in an arrow.
6. **Context line only where it says something.** Not for Inputs (it says so itself) or the gear; still the network's
   name beside the Wi-Fi icon, and app names.
7. **Scenes dissolve.** A scene that changes with the time of day dissolves out of the old one, and the first scene
   fades up from the flat colour (`SceneView.setFader`: a picture over the scene faded by the compositor, no redraws).
8. **A rebuild is not a blink.** A setting changed beside Home rebuilds Home; the old Home's last frame (PixelCopy) lies
   over the new one until its rows and scene are in, then dissolves (`coverRebuild`).
9. **The minute dissolves.** The old digits dissolve into the new over 420 ms, only when no key was pressed in the last
   1.5 s, so its redraws never land on a focus move.
10. **The sheet's levels slide.** Rows come in from the side they come from; a change in place does not move.
11. **Serif titles.** The sheet's titles use Instrument Serif, the clock's face; everything else stays Outfit.
12. **Long titles run.** The focused Keep watching card's title scrolls when it does not fit.

Checked on the emulator with screen recordings (scene step, minute change, cold start) and screenshots of every
screen. Not measured on the TV: items 7 to 9 add animation and a full-screen copy, and need the phase 6 numbers.

## 9. Night scenes and a calmer Home (2026-11-14, emulator only)

| Change | Detail |
|---|---|
| Four night scenes | `stars` Starlit peaks, `aurora` Northern lights (no cut-out), `tarn` Lakeside stars, `oldtown` Old town lights. They are the 19 to 5 set; Ember ridges is morning only again and City at dusk evening only. Photos from Wikimedia Commons featured pictures, credited in `launcher/SCENES-CREDITS.md` (CC BY 4.0, CC0 and two CC BY-SA 4.0). Old town's digits are frosted (40% white, firmer rim): plain glass vanished over the lit walls |
| Calm app tiles | `calm_tiles` (theme key, and Look and scenes > Calm app tiles): tiles that are not focused are 20% dimmer and 30% less saturated; the focused one comes up to full colour over 200 ms. Off in the local theme since the same day: the user preferred the dock at full colour. The switch stays in settings |
| Rows put away when idle | The local theme sets `hide_rows_idle_seconds` to 60. With the rows away the scene turns to another of the scenes for now every 4 minutes (`SceneView.next`), dissolving; any key brings the rows back and is not acted on |
| Shorter date line | "Sat 14 Nov · [icon] 23° Clear": short day and month, no place name, and a small picture for the weather (`ic_wx_*`: sun, moon, cloud, fog, rain, snow, storm) |
| Remove from Keep watching | Hold OK on a card. Only the app that owns a Watch Next entry can delete it, so the launcher keeps its own list (`keep_watching_hidden`: row id and when it was last watched); the show comes back when it is watched again |

16 scenes now; the APK is 29.6 MB. Checked on the emulator with screenshots (each night scene, hide after idle,
scene turning with the timings shortened for the test, wake on a key, removing a card). Someone else was
pressing keys on the emulator during two of the idle runs, which were repeated.

