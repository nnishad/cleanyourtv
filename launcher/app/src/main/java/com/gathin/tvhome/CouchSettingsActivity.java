package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home settings, the launcher's one settings screen (the gear's "Home settings"): a glass sheet on the right, with Home beside it
 * following each change at once. Three levels: the sections, a section's rows, and for a few rows a page of their own
 * (Backgrounds, Wallpaper, Open on start). A row is a switch, a stepper (OK or Right for the next value, Left for the
 * one before), a row that opens a page, an action, or plain information. Every choice is kept in the launcher's own
 * preferences, which Config reads over the theme.
 */
public final class CouchSettingsActivity extends Activity {

    /** The result's extra when Reorder apps was chosen: Home rebuilds and then enters reorder mode. */
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int REQUEST_WATCH_NEXT = 8, REQUEST_LOCATION = 9;
    private static final String WATCH_NEXT_PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final int LOOK = 0, ROWS = 1, LAYOUT = 2, MOTION = 3, WEATHER = 4;
    private static final int[] SECTIONS = {R.string.hs_sec_look, R.string.hs_sec_rows, R.string.hs_sec_layout,
            R.string.hs_sec_motion, R.string.hs_sec_weather, R.string.hs_sec_about};
    private static final int[] SECTION_ICONS = {R.drawable.ic_settings_look, R.drawable.ic_settings_rows,
            R.drawable.ic_settings_apps, R.drawable.ic_settings_motion, R.drawable.ic_settings_weather,
            R.drawable.ic_settings_about};
    private static final int PAGE_NONE = 0, PAGE_BACKGROUNDS = 1, PAGE_WALLPAPER = 2, PAGE_OPEN_ON_START = 3, PAGE_PLACE = 4;
    private static final String[] SCENE_IDS = {"", "random", "d_chandratal", "d_pangong", "d_munnar", "d_bachalpsee", "d_bled", "d_autumn", "d_maldives", "d_rakotz", "d_jaisalmer", "d_lauterbrunnen", "d_worli", "d_nyc", "d_varanasi"};
    private static final int[] SCENE_NAMES = {R.string.hs_scene_time, R.string.hs_scene_random, R.string.hs_scene_d_chandratal, R.string.hs_scene_d_pangong, R.string.hs_scene_d_munnar, R.string.hs_scene_d_bachalpsee, R.string.hs_scene_d_bled, R.string.hs_scene_d_autumn, R.string.hs_scene_d_maldives, R.string.hs_scene_d_rakotz, R.string.hs_scene_d_jaisalmer, R.string.hs_scene_d_lauterbrunnen, R.string.hs_scene_d_worli, R.string.hs_scene_d_nyc, R.string.hs_scene_d_varanasi};
    private static final String[] CLOCK_IDS = {"system", "12", "24"};
    private static final int[] CLOCK_NAMES = {R.string.hs_clock_tv, R.string.hs_clock_12, R.string.hs_clock_24};
    private static final int[] HIDE_SECONDS = {0, 30, 60, 120, 300};
    private static final int[] HIDE_NAMES = {R.string.hs_idle_off, R.string.hs_30s, R.string.hs_1m, R.string.hs_2m, R.string.hs_5m};
    private static final int[] IDLE_MINUTES = {0, 5, 10, 15, 30};
    private static final int[] IDLE_NAMES = {R.string.hs_idle_off, R.string.hs_5m, R.string.hs_10m, R.string.hs_15m, R.string.hs_30m};
    private static final int[] COLUMNS = {4, 5, 6, 7}, ROWS_BELOW = {0, 1, 2, 3}, DOCK_OPACITIES = {100, 70, 50, 30};
    private static final int[] PART_NAMES = {R.string.hs_part_0, R.string.hs_part_1, R.string.hs_part_2, R.string.hs_part_3,
            R.string.hs_part_4, R.string.hs_part_5, R.string.hs_part_6, R.string.hs_part_7};
    /** Everything this screen can set; Reset takes all of it back to what the theme says. */
    private static final String[] OWN_KEYS = {"scenes", "scene", "scenes_off", "watch_row", "hide_rows_idle_seconds",
            "home_idle_clock_minutes", "show_weather", "hide_tile_names", "columns", Config.ROWS_BELOW_DOCK_KEY,
            "animations", "advanced_effects", Config.WALLPAPER_CHOICE_KEY, Config.DOCK_OPACITY_KEY,
            Config.CLOCK_FORMAT_KEY, OpenOnStart.KEY};

    private SharedPreferences choices;
    private Config config;
    private LinearLayout paneRows;
    private TextView paneTitle, crumb, hint;
    private float density;
    private boolean reduced;
    /** Where the sheet is: section < 0 is the list of sections; page is a row's own page inside a section. */
    private int section = -1, page = PAGE_NONE;
    /** The row a page was opened from, to put focus back on it. */
    private int pageRow;
    /** The row whose switch was just pressed: its thumb slides to the new side when the pane is redrawn. */
    private int toggledRow = -1;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        reduced = MainActivity.reducedMotionNow(this);

        // A sheet on the right; Home stays visible on the left and follows each change at once (MainActivity.refreshLive).
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.sheet_bg);
        sheet.setPadding(dp(40), dp(40), dp(48), dp(28));
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.44f);
        android.widget.FrameLayout.LayoutParams sheetLp = new android.widget.FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
        sheetLp.gravity = Gravity.END;
        root.addView(sheet, sheetLp);

        // The sheet's title is set in the clock's serif: the one display face, on Home and here.
        paneTitle = text(getString(R.string.hs_title), 34, Color.WHITE, true);
        try {
            paneTitle.setTypeface(getResources().getFont(R.font.instrument_serif));
        } catch (RuntimeException ignored) {
        }
        paneTitle.setPadding(dp(6), 0, 0, dp(2));
        sheet.addView(paneTitle);
        crumb = text("", 14, getColor(R.color.text_tertiary), false);
        crumb.setPadding(dp(6), 0, 0, dp(18));
        sheet.addView(crumb);
        ScrollView pane = new ScrollView(this);
        pane.setVerticalScrollBarEnabled(false);
        pane.setClipToPadding(false);
        paneRows = new LinearLayout(this);
        paneRows.setOrientation(LinearLayout.VERTICAL);
        pane.addView(paneRows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        hint = text("", 12, getColor(R.color.text_tertiary), false);
        hint.setPadding(dp(6), dp(10), 0, 0);
        sheet.addView(hint);
        setContentView(root);
        if (!reduced) {   // the sheet slides in from the right edge
            sheet.setTranslationX(width * 0.15f);
            sheet.setAlpha(0f);
            sheet.animate().translationX(0f).alpha(1f).setDuration(Motion.SLOW).setInterpolator(Motion.ENTER).start();
        }
        showSections(0, 0);
    }

    // ---- the three levels ----------------------------------------------------------------------------------

    /** The first level: one row per section. {@code from} is the side the rows come in from: 0 none, -1 the left (back). */
    private void showSections(int focus, int from) {
        section = -1;
        page = PAGE_NONE;
        heading(getString(R.string.hs_title), getString(R.string.hs_subtitle), R.string.hs_hint_sections);
        paneRows.removeAllViews();
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            View row = getLayoutInflater().inflate(R.layout.item_settings_section, paneRows, false);
            ((ImageView) row.findViewById(R.id.sectionIcon)).setImageResource(SECTION_ICONS[i]);
            ((TextView) row.findViewById(R.id.sectionTitle)).setText(SECTIONS[i]);
            row.setOnClickListener(v -> {
                showSection(index, 1);
                focusRow(0);
            });
            Focus.attach(row, false, reduced);
            paneRows.addView(row);
        }
        focusRow(focus);
        comeIn(from);
    }

    private void showSection(int index, int from) {
        section = index;
        page = PAGE_NONE;
        heading(getString(SECTIONS[index]), getString(R.string.hs_title), R.string.hs_hint_rows);
        paneRows.removeAllViews();
        switch (index) {
            case LOOK:
                lookPane();
                break;
            case ROWS:
                rowsPane();
                break;
            case LAYOUT:
                layoutPane();
                break;
            case MOTION:
                motionPane();
                break;
            case WEATHER:
                weatherPane();
                break;
            default:
                aboutPane();
        }
        comeIn(from);
    }

    private void showPage(int which, int from) {
        page = which;
        paneRows.removeAllViews();
        int title = which == PAGE_BACKGROUNDS ? R.string.hs_backgrounds : which == PAGE_WALLPAPER ? R.string.wallpaper_label
                : which == PAGE_PLACE ? R.string.hs_place_label : R.string.open_on_start_label;
        heading(getString(title), getString(SECTIONS[section]), which == PAGE_BACKGROUNDS ? R.string.hs_hint_backgrounds
                : which == PAGE_PLACE ? R.string.hs_place_page_hint : R.string.hs_hint_page);
        if (which == PAGE_BACKGROUNDS) backgroundsPage();
        else if (which == PAGE_WALLPAPER) wallpaperPage();
        else if (which == PAGE_PLACE) placePage();
        else openOnStartPage();
        comeIn(from);
    }

    private void heading(String title, String under, int hintRes) {
        paneTitle.setText(title);
        crumb.setText(under);
        hint.setText(hintRes);
    }

    /** The rows of a new level slide in from the side they come from and fade up; a redraw in place does neither. */
    private void comeIn(int from) {
        if (from == 0 || reduced) return;
        paneRows.animate().cancel();
        paneRows.setTranslationX(from * dp(28));
        paneRows.setAlpha(0f);
        paneRows.animate().translationX(0f).alpha(1f).setDuration(Motion.STANDARD).setInterpolator(Motion.ENTER).start();
        paneTitle.setAlpha(0f);
        paneTitle.animate().alpha(1f).setDuration(Motion.STANDARD).start();
    }

    /** Focus on the row asked for, or the nearest one after it (then before it) that can take focus. */
    private void focusRow(int index) {
        int n = paneRows.getChildCount();
        if (n == 0) return;
        int at = Math.max(0, Math.min(index, n - 1));
        for (int i = at; i < n; i++) {
            if (paneRows.getChildAt(i).isFocusable()) {
                paneRows.getChildAt(i).requestFocus();
                return;
            }
        }
        for (int i = at - 1; i >= 0; i--) {
            if (paneRows.getChildAt(i).isFocusable()) {
                paneRows.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    /** Back steps out one level: a page to its section, a section to the sections, the sections to Home. */
    @Override
    public void onBackPressed() {
        if (page != PAGE_NONE) {
            showSection(section, -1);
            focusRow(pageRow);
            return;
        }
        if (section >= 0) {
            showSections(section, -1);
            return;
        }
        super.onBackPressed();
    }

    /** Redraws the level in place after a change and puts focus back on the row that was changed. */
    private void rebuild(int focusIndex) {
        if (section < 0) showSections(focusIndex, 0);
        else if (page != PAGE_NONE) showPage(page, 0);
        else showSection(section, 0);
        focusRow(focusIndex);
        toggledRow = -1;
    }

    /** A choice was saved: Home rebuilds with it now, and the pane is redrawn with focus where it was. */
    private void applied(int row) {
        changed();
        config = Config.load(this);
        rebuild(row);
    }

    // ---- the sections --------------------------------------------------------------------------------------

    private void lookPane() {
        toggle(R.string.hs_scenes, getString(R.string.hs_scenes_note), config.scenes,
                v -> choices.edit().putBoolean("scenes", v).apply());
        if (config.scenes) {
            // The Scene row offers following the time, a different one each start, and the scenes that are switched on.
            final List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            int at = 0;
            for (int i = 0; i < SCENE_IDS.length; i++) {
                if (i >= 2 && (config.scenesOff.contains(SCENE_IDS[i]) || !hasScene(SCENE_IDS[i]))) continue;
                if (SCENE_IDS[i].equalsIgnoreCase(config.scene)) at = ids.size();
                ids.add(SCENE_IDS[i]);
                names.add(getString(SCENE_NAMES[i]));
            }
            step(R.string.hs_scene, null, names.toArray(new String[0]), at,
                    v -> choices.edit().putString("scene", ids.get(v)).apply());
            int on = SceneView.ids().length - countOff();
            open(R.string.hs_backgrounds, getString(R.string.hs_backgrounds_note),
                    getString(R.string.hs_backgrounds_count, on, SceneView.ids().length), PAGE_BACKGROUNDS);
        } else {
            String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
            open(R.string.wallpaper_label, getString(R.string.hs_wallpaper_note),
                    "none".equals(current) ? getString(R.string.wallpaper_none)
                            : current != null ? current : getString(R.string.wallpaper_theme_default), PAGE_WALLPAPER);
        }
        toggle(R.string.hs_app_names, getString(R.string.hs_app_names_note), !config.hideTileNames,
                v -> choices.edit().putBoolean("hide_tile_names", !v).apply());
        int clock = 0;
        for (int i = 0; i < CLOCK_IDS.length; i++) if (CLOCK_IDS[i].equals(config.clockFormat)) clock = i;
        step(R.string.hs_clock, null, strings(CLOCK_NAMES), clock,
                v -> choices.edit().putString(Config.CLOCK_FORMAT_KEY, CLOCK_IDS[v]).apply());
        step(R.string.dock_opacity_label, getString(R.string.hs_dock_opacity_note), percents(DOCK_OPACITIES),
                indexOf(DOCK_OPACITIES, config.dockOpacity),
                v -> choices.edit().putInt(Config.DOCK_OPACITY_KEY, DOCK_OPACITIES[v]).apply());
    }

    private void rowsPane() {
        toggle(R.string.keep_watching, getString(R.string.hs_watch_note), config.watchRow,
                v -> choices.edit().putBoolean("watch_row", v).apply());
        if (config.watchRow && checkSelfPermission(WATCH_NEXT_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            action(getString(R.string.hs_allow_watch), getString(R.string.hs_allow_watch_note), () ->
                    requestPermissions(new String[]{WATCH_NEXT_PERMISSION}, REQUEST_WATCH_NEXT));
        }
        step(R.string.hs_hide_rows, getString(R.string.hs_hide_rows_note), strings(HIDE_NAMES),
                indexOf(HIDE_SECONDS, config.hideRowsIdleSeconds),
                v -> choices.edit().putInt("hide_rows_idle_seconds", HIDE_SECONDS[v]).apply());
        step(R.string.hs_ambient, getString(R.string.hs_ambient_note), strings(IDLE_NAMES),
                indexOf(IDLE_MINUTES, config.homeIdleClockMinutes),
                v -> choices.edit().putInt("home_idle_clock_minutes", IDLE_MINUTES[v]).apply());
    }

    private void layoutPane() {
        action(getString(R.string.hs_reorder), getString(R.string.hs_reorder_note), () -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        step(R.string.columns_label, getString(R.string.hs_columns_note), numbers(COLUMNS), indexOf(COLUMNS, config.columns),
                v -> choices.edit().putInt("columns", COLUMNS[v]).apply());
        step(R.string.rows_below_dock_label, getString(R.string.hs_rows_note), numbers(ROWS_BELOW),
                indexOf(ROWS_BELOW, config.rowsBelowDock),
                v -> choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, ROWS_BELOW[v]).apply());
        if (config.openOnStartChoice) {
            open(R.string.open_on_start_label, getString(R.string.hs_open_on_start_note), openOnStartLabel(), PAGE_OPEN_ON_START);
        }
    }

    private void motionPane() {
        toggle(R.string.hs_animations, getString(R.string.hs_animations_note), config.animations, v -> {
            SharedPreferences.Editor e = choices.edit().putBoolean("animations", v);
            if (!v) e.putBoolean("advanced_effects", false);   // the effects are animations
            e.apply();
        });
        if (config.animations) {
            toggle(R.string.advanced_effects, getString(R.string.hs_advanced_note), config.advancedEffects,
                    v -> choices.edit().putBoolean("advanced_effects", v).apply());
        } else {
            action(getString(R.string.advanced_effects), getString(R.string.hs_advanced_needs), null);
        }
    }

    private void weatherPane() {
        WeatherPlace place = WeatherPlace.current(this);
        boolean tv = place == null && config.weatherAuto;   // no city: the TV's own location may give the place
        String value = place != null ? place.name : getString(R.string.hs_place_none), note = getString(R.string.hs_place_set_note);
        if (place != null) {
            toggle(R.string.hs_show_weather, getString(R.string.hs_weather_note),
                    choices.getBoolean("show_weather", WeatherPlace.onByDefault(this)),
                    v -> choices.edit().putBoolean("show_weather", v).apply());
        } else if (tv && !TvLocation.allowed(this)) {
            action(getString(R.string.hs_allow_location), getString(R.string.hs_allow_location_note), () ->
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION));
        } else if (tv) {
            toggle(R.string.hs_show_weather, getString(R.string.hs_weather_tv_note), choices.getBoolean("show_weather", true),
                    v -> choices.edit().putBoolean("show_weather", v).apply());
            if (TvLocation.cached(this) != null) value = getString(R.string.hs_place_tv);
            else note = getString(R.string.hs_location_none);
        } else {
            action(getString(R.string.hs_show_weather), getString(R.string.hs_weather_none), null);
        }
        open(R.string.hs_place_label, note, value, PAGE_PLACE);
    }

    private void aboutPane() {
        action(getString(R.string.hs_about, versionName()), getString(R.string.hs_about_note), null);
        action(getString(R.string.hs_default_home), getString(R.string.hs_default_home_note), () -> {
            try {
                startActivity(new Intent(this, HomeSettingsActivity.class));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        });
        final int at = paneRows.getChildCount();
        action(getString(R.string.hs_reset), getString(R.string.hs_reset_note), () -> {
            SharedPreferences.Editor e = choices.edit();
            for (String key : OWN_KEYS) e.remove(key);
            e.apply();
            applied(at);
        });
    }

    // ---- the pages -----------------------------------------------------------------------------------------

    private int countOff() {
        int n = 0;
        for (String id : SceneView.ids()) if (config.scenesOff.contains(id)) n++;
        return n;
    }

    private static boolean hasScene(String id) {
        for (String s : SceneView.ids()) if (s.equals(id)) return true;
        return false;
    }

    /** One row per scene, with its picture, on or off; the ones switched off are never shown. One always stays on. */
    private void backgroundsPage() {
        for (String id : SceneView.ids()) {
            final int at = paneRows.getChildCount();
            final boolean isOn = !config.scenesOff.contains(id);
            int part = SceneView.partOf(id);
            View row = row(getString(sceneName(id)), part >= 0 ? getString(PART_NAMES[part]) : null, null, () -> {
                java.util.Set<String> off = new java.util.LinkedHashSet<>(config.scenesOff);
                if (isOn && SceneView.ids().length - countOff() <= 1) {
                    hint.setText(R.string.hs_backgrounds_keep_one);
                    return;
                }
                if (isOn) off.add(id);
                else off.remove(id);
                choices.edit().putString("scenes_off", String.join(",", off)).apply();
                toggledRow = at;
                applied(at);
            });
            switchOn(row, isOn, at);
            ((LinearLayout) row).addView(thumbnail(id, isOn), 0);
            paneRows.addView(row);
        }
    }

    /** A small picture of the scene at the start of its row, faded when the scene is off. */
    private View thumbnail(String id, boolean isOn) {
        ImageView image = new ImageView(this);
        try (java.io.InputStream in = getAssets().open("scenes/thumbs/" + id + ".jpg")) {
            image.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (java.io.IOException ignored) {
        }
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(getResources().getDimension(R.dimen.radius_s));
        image.setBackground(shape);
        image.setClipToOutline(true);
        image.setAlpha(isOn ? 1f : 0.4f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(54));
        lp.setMarginEnd(dp(16));
        image.setLayoutParams(lp);
        return image;
    }

    private static int sceneName(String id) {
        for (int i = 0; i < SCENE_IDS.length; i++) if (SCENE_IDS[i].equals(id)) return SCENE_NAMES[i];
        return R.string.hs_scene;
    }

    /**
     * Type a city, press Search on the keyboard, pick the place from what is found. The keyboard is the TV's own: OK on
     * the field opens it. The field stays while results come and go below it.
     */
    private void placePage() {
        final EditText field = new EditText(this);
        field.setHint(R.string.hs_place_type);
        field.setHintTextColor(getColor(R.color.text_tertiary));
        field.setTextColor(Color.WHITE);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setBackgroundResource(R.drawable.settings_card_bg);
        field.setPadding(dp(18), dp(14), dp(18), dp(14));
        try {
            field.setTypeface(getResources().getFont(R.font.outfit_medium));
        } catch (RuntimeException ignored) {
        }
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldLp.bottomMargin = dp(8);
        paneRows.addView(field, fieldLp);

        final TextView status = text("", 14, getColor(R.color.text_tertiary), false);
        status.setPadding(dp(6), dp(2), 0, dp(10));
        status.setVisibility(View.GONE);
        paneRows.addView(status);
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        paneRows.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (WeatherPlace.chosen(this) != null) {
            action(getString(R.string.hs_place_forget), null, () -> {
                WeatherPlace.forget(this);
                placeChosen();
            });
        }

        field.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(field, 0);
        });
        field.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getAction() == KeyEvent.ACTION_DOWN
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER);
            if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            searchPlaces(field.getText().toString().trim(), status, results);
            return true;
        });
    }

    private void searchPlaces(String query, TextView status, LinearLayout results) {
        results.removeAllViews();
        if (query.isEmpty()) return;
        status.setText(R.string.hs_place_searching);
        status.setVisibility(View.VISIBLE);
        final String language = java.util.Locale.getDefault().getLanguage();
        new Thread(() -> {
            final List<WeatherPlace> found = WeatherPlace.search(query, language);
            runOnUiThread(() -> {
                if (isDestroyed() || page != PAGE_PLACE) return;
                results.removeAllViews();
                if (found == null || found.isEmpty()) {
                    status.setText(found == null ? R.string.hs_place_offline : R.string.hs_place_not_found);
                    return;
                }
                status.setVisibility(View.GONE);
                for (WeatherPlace place : found) {
                    results.addView(row(place.name, place.where().isEmpty() ? null : place.where(), null, () -> {
                        WeatherPlace.save(this, place);
                        choices.edit().putBoolean("show_weather", true).apply();   // choosing a place turns the weather on
                        placeChosen();
                    }));
                }
                if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            });
        }, "place-search").start();
    }

    /** A place was chosen or forgotten: Home rebuilds with it, and the Weather section is back in view. */
    private void placeChosen() {
        changed();
        config = Config.load(this);
        page = PAGE_NONE;
        showSection(section, -1);
        focusRow(pageRow);
    }

    /** The theme's own choice, each picture the theme brought, or none; the one in use carries a tick. */
    private void wallpaperPage() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        pick(getString(R.string.wallpaper_theme_default), current == null,
                () -> choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply());
        for (File image : images) {
            final String name = image.getName();
            pick(name, name.equals(current), () -> choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, name).apply());
        }
        pick(getString(R.string.wallpaper_none), "none".equals(current),
                () -> choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply());
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void openOnStartPage() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String current = choices.getString(OpenOnStart.KEY, "");
        pick(getString(R.string.off), current.isEmpty(), () -> choices.edit().remove(OpenOnStart.KEY).apply());
        for (String[] app : apps) {
            pick(app[0], app[1].equals(current), () -> {
                OpenOnStart.skipThisBoot(this);
                choices.edit().putString(OpenOnStart.KEY, app[1]).apply();
            });
        }
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ---- row kinds -----------------------------------------------------------------------------------------

    private interface BoolSetter {
        void set(boolean value);
    }

    /** On or off: a switch at the end of the row, which OK flips. */
    private void toggle(int label, String note, boolean current, BoolSetter save) {
        final int at = paneRows.getChildCount();
        View row = row(getString(label), note, null, () -> {
            save.set(!current);
            toggledRow = at;
            applied(at);
        });
        switchOn(row, current, at);
        paneRows.addView(row);
    }

    /** Shows the row's switch; on the row that was just pressed the thumb slides over from the side it was on. */
    private void switchOn(View row, boolean checked, int at) {
        SwitchView view = row.findViewById(R.id.rowSwitch);
        view.setVisibility(View.VISIBLE);
        if (at == toggledRow && !reduced) {
            view.startFrom(!checked);
            view.setChecked(checked, true);
        } else {
            view.setChecked(checked, false);
        }
    }

    /**
     * One of a few values, shown between two small arrows: OK or Right steps to the next (round to the first again),
     * Left to the one before. The row nudges the way it was stepped.
     */
    private void step(int label, String note, String[] names, int selected, java.util.function.IntConsumer save) {
        final int at = paneRows.getChildCount();
        final int n = names.length, now = Math.max(0, Math.min(selected, n - 1));
        View row = row(getString(label), note, arrows("‹  ", names[now], "  ›"), () -> {
            save.accept((now + 1) % n);
            applied(at);
        }, (view, code, event) -> {
            boolean next = code == KeyEvent.KEYCODE_DPAD_RIGHT, before = code == KeyEvent.KEYCODE_DPAD_LEFT;
            if (!next && !before) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                save.accept((now + (next ? 1 : n - 1)) % n);
                applied(at);
                // The pane was redrawn: the nudge goes to the row now standing in this one's place.
                if (at < paneRows.getChildCount()) Focus.nudge(paneRows.getChildAt(at), code, reduced);
            }
            return true;
        });
        row.setTag(R.id.rowValue, R.string.hs_hint_step);
        paneRows.addView(row);
    }

    /** A row with a page of its own behind it: its value, then an arrow. */
    private void open(int label, String note, String value, int which) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(getString(label), note, arrows("", value, "  ›"), () -> {
            pageRow = at;
            showPage(which, 1);
            focusRow(0);
        }));
    }

    /** One choice on a page: OK takes it, and the one in use carries a tick. */
    private void pick(String label, boolean current, Runnable save) {
        final int at = paneRows.getChildCount();
        SpannableString tick = current ? new SpannableString("✓") : null;
        if (tick != null) tick.setSpan(new ForegroundColorSpan(getColor(R.color.accent)), 0, 1, 0);
        paneRows.addView(row(label, null, tick, () -> {
            save.run();
            applied(at);
        }));
    }

    private void action(String label, String note, Runnable run) {
        paneRows.addView(row(label, note, null, run));
    }

    /** The value with its arrows a shade dimmer, so the value reads first. */
    private CharSequence arrows(String before, String value, String after) {
        SpannableString s = new SpannableString(before + value + after);
        int dim = getColor(R.color.text_tertiary);
        if (!before.isEmpty()) s.setSpan(new ForegroundColorSpan(dim), 0, before.length(), 0);
        if (!after.isEmpty()) s.setSpan(new ForegroundColorSpan(dim), s.length() - after.length(), s.length(), 0);
        return s;
    }

    private View row(String label, String note, CharSequence value, Runnable run) {
        return row(label, note, value, run, null);
    }

    private View row(String label, String note, CharSequence value, Runnable run, View.OnKeyListener keys) {
        View row = getLayoutInflater().inflate(R.layout.item_settings_row, paneRows, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(label);
        TextView noteView = row.findViewById(R.id.rowNote);
        if (note != null) {
            noteView.setText(note);
            noteView.setVisibility(View.VISIBLE);
        }
        TextView valueView = row.findViewById(R.id.rowValue);
        if (value == null) valueView.setVisibility(View.GONE);
        else valueView.setText(value);
        if (run != null) {
            row.setOnClickListener(v -> run.run());
            Focus.attach(row, false, reduced, keys);   // the press dip under OK
            row.setOnFocusChangeListener(focusHint(row));
        } else {
            row.setFocusable(false);   // information, not a button
            row.setAlpha(0.72f);
        }
        return row;
    }

    /**
     * The line under the rows says how the focused row is worked; a stepper has its own. Focus.attach has a focus
     * listener of its own, which this one runs first.
     */
    private View.OnFocusChangeListener focusHint(View row) {
        final View.OnFocusChangeListener inner = row.getOnFocusChangeListener();
        return (v, has) -> {
            if (inner != null) inner.onFocusChange(v, has);
            if (!has || page == PAGE_BACKGROUNDS) return;
            Object stepper = v.getTag(R.id.rowValue);
            hint.setText(stepper instanceof Integer ? (Integer) stepper
                    : page != PAGE_NONE ? R.string.hs_hint_page : R.string.hs_hint_rows);
        };
    }

    private TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        try {
            t.setTypeface(getResources().getFont(medium ? R.font.outfit_medium : R.font.outfit_regular));
        } catch (RuntimeException ignored) {
        }
        return t;
    }

    private String[] strings(int[] res) {
        String[] out = new String[res.length];
        for (int i = 0; i < res.length; i++) out[i] = getString(res[i]);
        return out;
    }

    private static String[] numbers(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = String.valueOf(values[i]);
        return out;
    }

    private static String[] percents(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i] + "%";
        return out;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    /** A change was made: Home, visible beside the sheet, rebuilds with it now (MainActivity.refreshLive). */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit();
        MainActivity.refreshLive();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_WATCH_NEXT) {
            changed();   // Home asks Watch Next again
            rebuild(0);
        } else if (request == REQUEST_LOCATION) {
            changed();   // Home asks the TV where it is
            config = Config.load(this);
            rebuild(0);
        }
    }
}
