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
    private static final String[] SECTIONS = {"Look and scenes", "Home rows", "Weather", "Apps", "About"};
    private static final int[] SECTION_ICONS = {R.drawable.ic_settings_look, R.drawable.ic_settings_rows,
            R.drawable.ic_settings_weather, R.drawable.ic_settings_apps, R.drawable.ic_settings_about};
    private static final String[] SCENE_IDS = {"", "random", "night", "golden", "water", "fog", "dawn"};
    private static final String[] SCENE_NAMES = {"Follows the time", "Different each start", "Night", "Golden hour", "Waterline", "Fog", "Dawn"};
    private static final String[] CLOCK_IDS = {"system", "12", "24"};
    private static final String[] CLOCK_NAMES = {"As the TV is set", "12-hour", "24-hour"};
    private static final int[] HIDE_SECONDS = {0, 30, 60, 120, 300};
    private static final String[] HIDE_NAMES = {"Off", "30 seconds", "1 minute", "2 minutes", "5 minutes"};
    private static final int[] IDLE_MINUTES = {0, 5, 10, 15, 30};
    private static final String[] IDLE_NAMES = {"Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes"};
    private static final String[] OWN_KEYS = {"scenes", "scene", "watch_row", "hide_rows_idle_seconds",
            "home_idle_clock_minutes", "show_weather", "hide_tile_names"};

    private SharedPreferences choices;
    private Config config;
    private LinearLayout sectionList, paneRows;
    private TextView paneTitle;
    private float density;
    private int section;

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xF50A1410);   // Home only just shows behind it
        root.setPadding(dp(64), dp(40), dp(64), dp(26));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Home settings", 30, Color.WHITE, true);
        title.setPadding(dp(6), dp(2), 0, dp(26));
        left.addView(title);
        sectionList = new LinearLayout(this);
        sectionList.setOrientation(LinearLayout.VERTICAL);
        left.addView(sectionList);
        root.addView(left, new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(44), 0, 0, 0);
        paneTitle = text("", 28, Color.WHITE, true);
        paneTitle.setPadding(dp(4), dp(2), 0, dp(20));
        right.addView(paneTitle);
        ScrollView pane = new ScrollView(this);
        pane.setVerticalScrollBarEnabled(false);
        pane.setClipToPadding(false);
        paneRows = new LinearLayout(this);
        paneRows.setOrientation(LinearLayout.VERTICAL);
        pane.addView(paneRows, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = text("Up and down to choose, OK to change, Back to close", 12, 0x80FFFFFF, false);
        hint.setPadding(dp(4), dp(10), 0, 0);
        right.addView(hint);
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setContentView(root);

        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            View row = getLayoutInflater().inflate(R.layout.item_settings_section, sectionList, false);
            ((ImageView) row.findViewById(R.id.sectionIcon)).setImageResource(SECTION_ICONS[i]);
            ((TextView) row.findViewById(R.id.sectionTitle)).setText(SECTIONS[i]);
            row.setOnFocusChangeListener((v, has) -> {
                if (has) showSection(index);
            });
            row.setOnClickListener(v -> {
                View first = paneRows.getChildCount() > 0 ? paneRows.getChildAt(0) : null;
                if (first != null) first.requestFocus();
            });
            sectionList.addView(row);
        }
        showSection(0);
        sectionList.getChildAt(0).requestFocus();
    }

    // ---- the panes ------------------------------------------------------------------------------------

    private void showSection(int index) {
        section = index;
        for (int i = 0; i < sectionList.getChildCount(); i++) sectionList.getChildAt(i).setSelected(i == index);
        paneTitle.setText(SECTIONS[index]);
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
        showSection(section);
        View row = paneRows.getChildAt(Math.max(0, Math.min(focusIndex, paneRows.getChildCount() - 1)));
        if (row != null) row.requestFocus();
    }

    private void lookPane() {
        toggle("Scenes", "scenes", config.scenes, "The big glass clock inside a landscape");
        if (config.scenes) {
            int at = 0;
            for (int i = 0; i < SCENE_IDS.length; i++) if (SCENE_IDS[i].equalsIgnoreCase(config.scene)) at = i;
            cycle("Scene", null, SCENE_NAMES, at, v -> choices.edit().putString("scene", SCENE_IDS[v]).apply());
        }
        toggleInverted("App names", "hide_tile_names", config.hideTileNames, "Under the focused tile and inside icon tiles");
        int clock = 0;
        for (int i = 0; i < CLOCK_IDS.length; i++) if (CLOCK_IDS[i].equals(config.clockFormat)) clock = i;
        cycle("Clock", null, CLOCK_NAMES, clock, v -> choices.edit().putString(Config.CLOCK_FORMAT_KEY, CLOCK_IDS[v]).apply());
    }

    private void rowsPane() {
        toggle("Keep watching", "watch_row", config.watchRow, "Shows you were part way through, from your apps");
        if (config.watchRow && checkSelfPermission(WATCH_NEXT_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            action("Allow access to Watch Next", "Keep watching stays empty until this is allowed", () ->
                    requestPermissions(new String[]{WATCH_NEXT_PERMISSION}, REQUEST_WATCH_NEXT));
        }
        cycle("Hide rows when idle", "Leaves the scene, the clock and the weather; any button brings the rows back",
                HIDE_NAMES, indexOf(HIDE_SECONDS, config.hideRowsIdleSeconds),
                v -> choices.edit().putInt("hide_rows_idle_seconds", HIDE_SECONDS[v]).apply());
        cycle("Ambient clock when idle", "A dim drifting clock, kind to the screen",
                IDLE_NAMES, indexOf(IDLE_MINUTES, config.homeIdleClockMinutes),
                v -> choices.edit().putInt("home_idle_clock_minutes", IDLE_MINUTES[v]).apply());
    }

    private void weatherPane() {
        boolean configured = !getString(R.string.weather_place).trim().isEmpty()
                && !getString(R.string.latitude).trim().isEmpty();
        boolean on = choices.getBoolean("show_weather", getResources().getBoolean(R.bool.show_weather)) && configured;
        toggle("Show weather", "show_weather", on, configured ? "One small request every 30 minutes while Home is on" : "No place is set");
        action("Place: " + (configured ? getString(R.string.weather_place) : "none"),
                "Set with weather_place, latitude and longitude in config.xml", null);
    }

    private void appsPane() {
        action("Reorder apps", "Move tiles with the remote", () -> {
            setResult(RESULT_OK, new Intent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true));
            finish();
        });
        action("Grid, dock and wallpaper", "Columns, rows, dock opacity, effects and more", () ->
                startActivityForResult(new Intent(this, LauncherSettingsActivity.class), REQUEST_LAUNCHER));
    }

    private void aboutPane() {
        action("CleanYourTV launcher " + versionName(), "With the couch-tv look: scenes, Keep watching and the idle options", null);
        action("Reset these choices", "Back to what the theme says", () -> {
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

    private void toggle(String label, String key, boolean current, String note) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(label, note, current ? "On" : "Off", () -> {
            choices.edit().putBoolean(key, !current).apply();
            changed();
            config = Config.load(this);
            rebuild(at);
        }));
    }

    /** A toggle whose stored switch means the opposite of what the row says: "App names" On is hide_tile_names false. */
    private void toggleInverted(String label, String key, boolean hidden, String note) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(label, note, hidden ? "Off" : "On", () -> {
            choices.edit().putBoolean(key, !hidden).apply();
            changed();
            config = Config.load(this);
            rebuild(at);
        }));
    }

    private void cycle(String label, String note, String[] names, int selected, java.util.function.IntConsumer apply) {
        final int at = paneRows.getChildCount();
        paneRows.addView(row(label, note, names[Math.max(0, Math.min(selected, names.length - 1))], () -> {
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

    /** A change was made: Home rebuilds with it once this screen closes (see MainActivity#onResume). */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
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
