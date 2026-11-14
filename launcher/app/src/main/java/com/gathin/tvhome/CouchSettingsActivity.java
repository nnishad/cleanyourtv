package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Couch-tv's Home settings, natively (couch_menus; the gear's "Home settings"): the sections down the left, the open
 * section's rows on the right as rounded cards that turn white under focus. A row is a toggle or a cycle (OK steps to
 * the next value) or an action. Every choice is kept in the launcher's own preferences, which Config reads over the
 * theme, and bumps the settings revision so Home rebuilds with it when this screen closes. People, languages and
 * updates have no counterpart here (one household, no computer, no network updates).
 */
public final class CouchSettingsActivity extends Activity {

    private static final int REQUEST_LAUNCHER = 7, REQUEST_WATCH_NEXT = 8;
    private static final String WATCH_NEXT_PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final int[] SECTIONS = {R.string.hs_sec_look, R.string.hs_sec_rows, R.string.hs_sec_weather, R.string.hs_sec_apps, R.string.hs_sec_about};
    private static final int[] SECTION_ICONS = {R.drawable.ic_settings_look, R.drawable.ic_settings_rows,
            R.drawable.ic_settings_weather, R.drawable.ic_settings_apps, R.drawable.ic_settings_about};
    private static final String[] SCENE_IDS = {"", "random", "night", "golden", "water", "fog", "dawn", "valley", "bridge",
            "alpine", "ember", "highland", "lake", "snow", "city"};
    private static final int[] SCENE_NAMES = {R.string.hs_scene_time, R.string.hs_scene_random, R.string.hs_scene_night, R.string.hs_scene_golden, R.string.hs_scene_water, R.string.hs_scene_fog, R.string.hs_scene_dawn,
            R.string.hs_scene_valley, R.string.hs_scene_bridge, R.string.hs_scene_alpine, R.string.hs_scene_ember,
            R.string.hs_scene_highland, R.string.hs_scene_lake, R.string.hs_scene_snow, R.string.hs_scene_city};
    private static final String[] CLOCK_IDS = {"system", "12", "24"};
    private static final int[] CLOCK_NAMES = {R.string.hs_clock_tv, R.string.hs_clock_12, R.string.hs_clock_24};
    private static final int[] HIDE_SECONDS = {0, 30, 60, 120, 300};
    private static final int[] HIDE_NAMES = {R.string.hs_idle_off, R.string.hs_30s, R.string.hs_1m, R.string.hs_2m, R.string.hs_5m};
    private static final int[] IDLE_MINUTES = {0, 5, 10, 15, 30};
    private static final int[] IDLE_NAMES = {R.string.hs_idle_off, R.string.hs_5m, R.string.hs_10m, R.string.hs_15m, R.string.hs_30m};
    private static final int[] PART_NAMES = {R.string.hs_part_morning, R.string.hs_part_day, R.string.hs_part_evening,
            R.string.hs_part_night, R.string.hs_part_fog};
    private static final String[] OWN_KEYS = {"scenes", "scene", "scenes_off", "watch_row", "hide_rows_idle_seconds",
            "home_idle_clock_minutes", "show_weather", "hide_tile_names"};

    private SharedPreferences choices;
    private Config config;
    private LinearLayout sectionList, paneRows;
    private TextView paneTitle, crumb, hint;
    private float density;
    private int section;
    /** The Backgrounds list is open (a level under Look and scenes); backgroundsRow is its row in that pane. */
    private boolean inBackgrounds;
    private int backgroundsRow;

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

        // A sheet on the right; Home stays visible on the left and follows each change at once (MainActivity.refreshLive).
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.sheet_bg);
        sheet.setPadding(dp(40), dp(44), dp(48), dp(28));
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.44f);
        android.widget.FrameLayout.LayoutParams sheetLp = new android.widget.FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
        sheetLp.gravity = Gravity.END;
        root.addView(sheet, sheetLp);

        paneTitle = text(getString(R.string.hs_title), 28, Color.WHITE, true);
        paneTitle.setPadding(dp(6), 0, 0, dp(4));
        sheet.addView(paneTitle);
        crumb = text("", 14, getColor(R.color.text_tertiary), false);
        crumb.setPadding(dp(6), 0, 0, dp(18));
        sheet.addView(crumb);
        ScrollView pane = new ScrollView(this);
        pane.setVerticalScrollBarEnabled(false);
        pane.setClipToPadding(false);
        paneRows = new LinearLayout(this);
        paneRows.setOrientation(LinearLayout.VERTICAL);
        sectionList = paneRows;
        pane.addView(paneRows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        hint = text("", 12, getColor(R.color.text_tertiary), false);
        hint.setPadding(dp(6), dp(10), 0, 0);
        sheet.addView(hint);
        setContentView(root);
        if (!MainActivity.reducedMotionNow(this)) {   // the sheet slides in from the right edge
            sheet.setTranslationX(width * 0.15f);
            sheet.setAlpha(0f);
            sheet.animate().translationX(0f).alpha(1f).setDuration(Motion.SLOW).setInterpolator(Motion.ENTER).start();
        }
        showSections(0);
    }

    /** The first level: one row per section, with what it holds. */
    private void showSections(int focus) {
        section = -1;
        paneTitle.setText(R.string.hs_title);
        crumb.setText(R.string.hs_subtitle);
        hint.setText(R.string.hs_hint_sections);
        paneRows.removeAllViews();
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            View row = getLayoutInflater().inflate(R.layout.item_settings_section, paneRows, false);
            ((ImageView) row.findViewById(R.id.sectionIcon)).setImageResource(SECTION_ICONS[i]);
            ((TextView) row.findViewById(R.id.sectionTitle)).setText(SECTIONS[i]);
            row.setOnClickListener(v -> {
                showSection(index);
                View first = firstFocusable();
                if (first != null) first.requestFocus();
            });
            Focus.attach(row, false, false);
            paneRows.addView(row);
        }
        paneRows.getChildAt(Math.max(0, Math.min(focus, paneRows.getChildCount() - 1))).requestFocus();
    }

    private View firstFocusable() {
        for (int i = 0; i < paneRows.getChildCount(); i++) if (paneRows.getChildAt(i).isFocusable()) return paneRows.getChildAt(i);
        return null;
    }

    /** Back inside a section returns to the sections; Back on the sections closes the sheet. */
    @Override
    public void onBackPressed() {
        if (inBackgrounds) {
            showSection(0);
            View row = paneRows.getChildAt(backgroundsRow);
            if (row != null) row.requestFocus();
            return;
        }
        if (section >= 0) {
            showSections(section);
            return;
        }
        super.onBackPressed();
    }

    // ---- the panes ------------------------------------------------------------------------------------

    private void showSection(int index) {
        section = index;
        inBackgrounds = false;
        paneTitle.setText(SECTIONS[index]);
        crumb.setText(R.string.hs_title);
        hint.setText(R.string.hs_hint_rows);
        paneRows.removeAllViews();
        switch (index) {
            case 0:
                lookPane();
                break;
            case 1:
                rowsPane();
                break;
            case 2:
                weatherPane();
                break;
            case 3:
                appsPane();
                break;
            default:
                aboutPane();
        }
    }

    /** Redraws the pane after a change and puts focus back on the row that was changed. */
    private void rebuild(int focusIndex) {
        if (section < 0) {
            showSections(0);
            return;
        }
        if (inBackgrounds) showBackgrounds();
        else showSection(section);
        View row = paneRows.getChildAt(Math.max(0, Math.min(focusIndex, paneRows.getChildCount() - 1)));
        if (row != null) row.requestFocus();
    }

    private void lookPane() {
        toggle(R.string.hs_scenes, "scenes", config.scenes, getString(R.string.hs_scenes_note));
        if (config.scenes) {
            // The Scene row offers following the time, a different one each start, and the scenes that are switched on.
            java.util.List<String> ids = new java.util.ArrayList<>();
            java.util.List<Integer> names = new java.util.ArrayList<>();
            int at = 0;
            for (int i = 0; i < SCENE_IDS.length; i++) {
                if (i >= 2 && config.scenesOff.contains(SCENE_IDS[i])) continue;
                if (SCENE_IDS[i].equalsIgnoreCase(config.scene)) at = ids.size();
                ids.add(SCENE_IDS[i]);
                names.add(SCENE_NAMES[i]);
            }
            int[] nameRes = new int[names.size()];
            for (int i = 0; i < nameRes.length; i++) nameRes[i] = names.get(i);
            cycle(R.string.hs_scene, null, nameRes, at, v -> choices.edit().putString("scene", ids.get(v)).apply());
            backgroundsRow = paneRows.getChildCount();
            int on = SceneView.ids().length - countOff();
            paneRows.addView(row(getString(R.string.hs_backgrounds), getString(R.string.hs_backgrounds_note),
                    getString(R.string.hs_backgrounds_count, on, SceneView.ids().length), () -> {
                        showBackgrounds();
                        View first = firstFocusable();
                        if (first != null) first.requestFocus();
                    }));
        }
        toggleInverted(R.string.hs_app_names, "hide_tile_names", config.hideTileNames, getString(R.string.hs_app_names_note));
        int clock = 0;
        for (int i = 0; i < CLOCK_IDS.length; i++) if (CLOCK_IDS[i].equals(config.clockFormat)) clock = i;
        cycle(R.string.hs_clock, null, CLOCK_NAMES, clock, v -> choices.edit().putString(Config.CLOCK_FORMAT_KEY, CLOCK_IDS[v]).apply());
    }

    private int countOff() {
        int n = 0;
        for (String id : SceneView.ids()) if (config.scenesOff.contains(id)) n++;
        return n;
    }

    /** One row per scene, on or off; the ones switched off are never shown. One always stays on. */
    private void showBackgrounds() {
        inBackgrounds = true;
        paneTitle.setText(R.string.hs_backgrounds);
        crumb.setText(SECTIONS[0]);
        hint.setText(R.string.hs_hint_backgrounds);
        paneRows.removeAllViews();
        for (String id : SceneView.ids()) {
            final int at = paneRows.getChildCount();
            boolean isOn = !config.scenesOff.contains(id);
            int part = SceneView.partOf(id);
            View row = row(getString(sceneName(id)), part >= 0 ? getString(PART_NAMES[part]) : null,
                    getString(isOn ? R.string.on : R.string.off), () -> {
                        java.util.Set<String> off = new java.util.LinkedHashSet<>(config.scenesOff);
                        if (isOn && SceneView.ids().length - countOff() <= 1) {
                            hint.setText(R.string.hs_backgrounds_keep_one);
                            return;
                        }
                        if (isOn) off.add(id);
                        else off.remove(id);
                        choices.edit().putString("scenes_off", String.join(",", off)).apply();
                        changed();
                        config = Config.load(this);
                        rebuild(at);
                    });
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

    private void rowsPane() {
        toggle(R.string.keep_watching, "watch_row", config.watchRow, getString(R.string.hs_watch_note));
        if (config.watchRow && checkSelfPermission(WATCH_NEXT_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            action(getString(R.string.hs_allow_watch), getString(R.string.hs_allow_watch_note), () ->
                    requestPermissions(new String[]{WATCH_NEXT_PERMISSION}, REQUEST_WATCH_NEXT));
        }
        cycle(R.string.hs_hide_rows, getString(R.string.hs_hide_rows_note),
                HIDE_NAMES, indexOf(HIDE_SECONDS, config.hideRowsIdleSeconds),
                v -> choices.edit().putInt("hide_rows_idle_seconds", HIDE_SECONDS[v]).apply());
        cycle(R.string.hs_ambient, getString(R.string.hs_ambient_note),
                IDLE_NAMES, indexOf(IDLE_MINUTES, config.homeIdleClockMinutes),
                v -> choices.edit().putInt("home_idle_clock_minutes", IDLE_MINUTES[v]).apply());
    }

    private void weatherPane() {
        boolean configured = !getString(R.string.weather_place).trim().isEmpty()
                && !getString(R.string.latitude).trim().isEmpty();
        boolean on = choices.getBoolean("show_weather", getResources().getBoolean(R.bool.show_weather)) && configured;
        toggle(R.string.hs_show_weather, "show_weather", on, getString(configured ? R.string.hs_weather_note : R.string.hs_weather_none));
        action(getString(R.string.hs_place, configured ? getString(R.string.weather_place) : getString(R.string.hs_place_none)),
                getString(R.string.hs_place_note), null);
    }

    private void appsPane() {
        action(getString(R.string.hs_reorder), getString(R.string.hs_reorder_note), () -> {
            setResult(RESULT_OK, new Intent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true));
            finish();
        });
        action(getString(R.string.hs_grid), getString(R.string.hs_grid_note), () ->
                startActivityForResult(new Intent(this, LauncherSettingsActivity.class), REQUEST_LAUNCHER));
    }

    private void aboutPane() {
        action(getString(R.string.hs_about, versionName()), getString(R.string.hs_about_note), null);
        action(getString(R.string.hs_reset), getString(R.string.hs_reset_note), () -> {
            SharedPreferences.Editor e = choices.edit();
            for (String key : OWN_KEYS) e.remove(key);
            e.remove(Config.CLOCK_FORMAT_KEY);
            e.apply();
            changed();
            config = Config.load(this);
            rebuild(1);
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ---- row kinds ---------------------------------------------------------------------------------------

    private void toggle(int label, String key, boolean current, String note) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(getString(label), note, getString(current ? R.string.on : R.string.off), () -> {
            choices.edit().putBoolean(key, !current).apply();
            changed();
            config = Config.load(this);
            rebuild(at);
        }));
    }

    /** A toggle whose stored switch means the opposite of what the row says: "App names" On is hide_tile_names false. */
    private void toggleInverted(int label, String key, boolean hidden, String note) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(getString(label), note, getString(hidden ? R.string.off : R.string.on), () -> {
            choices.edit().putBoolean(key, !hidden).apply();
            changed();
            config = Config.load(this);
            rebuild(at);
        }));
    }

    private void cycle(int label, String note, int[] names, int selected, java.util.function.IntConsumer apply) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(getString(label), note, getString(names[Math.max(0, Math.min(selected, names.length - 1))]), () -> {
            apply.accept((selected + 1) % names.length);
            changed();
            config = Config.load(this);
            rebuild(at);
        }));
    }

    private void action(String label, String note, Runnable run) {
        paneRows.addView(row(label, note, null, run));
    }

    private View row(String label, String note, String value, Runnable run) {
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
        if (run != null) row.setOnClickListener(v -> run.run());
        else row.setFocusable(false);   // information, not a button
        return row;
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
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_LAUNCHER) return;
        if (result == RESULT_OK) {
            setResult(RESULT_OK, data);
            if (data != null && data.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_WATCH_NEXT) {
            changed();   // Home asks Watch Next again
            rebuild(0);
        }
    }
}
