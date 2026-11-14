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
| Type scale (Outfit) | Caption 12 sp, Body 14 sp, Label 16 sp, Title 20 sp, Headline 28 sp; clock stays Antonio. Nothing under 12 sp (24 px) |
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
  start" stays as an option.
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
