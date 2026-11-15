package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Couch-tv's Night look, drawn natively: a scene photo, the big glass clock inside it (digits filled from the
 * scene's blurred copy) and the scenery cut-out in front of the digits. One view behind everything, drawn
 * again once a minute; the picture is chosen once per start and never animates, so it costs nothing while
 * Home idles. The bitmaps are let go under memory pressure and decoded again on return (release, ensureLoaded).
 * Off unless a theme sets "scenes": true. Reference sizes below are couch-tv's CSS at 1920 px wide.
 */
final class SceneView extends View {

    private static final class Spec {
        final String id;
        final boolean cutout;
        final float top, size, left;   // left < 0: centred
        final float fillBright, fillSat, fillAlpha, photoBright;
        final int rimStroke, rimFill;
        final boolean sheen;
        final int[] shade;
        final float[] shadeAt;
        /** The photo is "<id>.webp", stored losslessly, and the cut-out is the photo through "<id>-mask.png"'s alpha. */
        final boolean lossless;
        /** The digits', the date line's and the top shade's tones are read from the picture itself (Tone), not set here. */
        boolean adaptive;

        Spec(String id, boolean cutout, float top, float size, float left, float fillBright, float fillSat,
             float fillAlpha, float photoBright, int rimStroke, int rimFill, boolean sheen, int[] shade, float[] shadeAt) {
            this(id, cutout, top, size, left, fillBright, fillSat, fillAlpha, photoBright, rimStroke, rimFill, sheen, shade,
                    shadeAt, false);
        }

        Spec(String id, boolean cutout, float top, float size, float left, float fillBright, float fillSat,
             float fillAlpha, float photoBright, int rimStroke, int rimFill, boolean sheen, int[] shade, float[] shadeAt,
             boolean lossless) {
            this.lossless = lossless;
            this.id = id;
            this.cutout = cutout;
            this.top = top;
            this.size = size;
            this.left = left;
            this.fillBright = fillBright;
            this.fillSat = fillSat;
            this.fillAlpha = fillAlpha;
            this.photoBright = photoBright;
            this.rimStroke = rimStroke;
            this.rimFill = rimFill;
            this.sheen = sheen;
            this.shade = shade;
            this.shadeAt = shadeAt;
        }
    }

    /**
     * What a picture asks of the clock that stands in it, read from the picture: how bright it is behind the digits,
     * behind the date line and behind the top buttons, and how busy it is behind the digits. Any picture can then be
     * a scene: pale sky gets dark glass digits and a dark date line, a dark sky bright glass, a middling one frosted
     * white, a busy one (lit buildings) a firmer frost and edge, and a pale top a shade under the white buttons.
     */
    private static final class Tone {
        final float fillBright;
        final float fillSat;
        final int rimStroke, rimFill, shadow, captionColor, captionShadow;
        final int[] shade;
        final float[] shadeAt;

        private Tone(float behind, float busy, float line, float top) {
            if (behind > 0.62f) {            // pale: the picture's own colour, deeper, so it stays a tint of the sky
                fillBright = Math.max(0.42f, 0.7f - (behind - 0.62f) * 1.1f);
                fillSat = 1.45f;
                rimStroke = 0xB3FFFFFF;
                rimFill = busy > 0.2f ? 0x26000000 : 0;
                shadow = 0x2E000000;
            } else if (behind < 0.3f) {      // dark: the glass lit up, with a veil of white so it has a body
                fillBright = 1.7f;
                fillSat = 1f;
                rimStroke = busy > 0.2f ? 0xD9FFFFFF : 0xB3FFFFFF;
                rimFill = busy > 0.2f ? 0x73FFFFFF : 0x47FFFFFF;
                shadow = 0x33000000;
            } else {                         // between: frosted white, more of it as the picture gets brighter or busier
                fillBright = 1.15f;
                fillSat = 1f;
                float frost = 0.22f + (behind - 0.3f) * 0.8f + (busy > 0.2f ? 0.18f : 0f);
                rimStroke = 0xCCFFFFFF;
                rimFill = (Math.round(Math.min(0.62f, frost) * 255) << 24) | 0xFFFFFF;
                shadow = 0x40000000;
            }
            boolean paleLine = line > 0.66f;
            captionColor = paleLine ? 0xE610161C : 0xEBFFFFFF;
            captionShadow = paleLine ? 0x66FFFFFF : 0xB3000000;
            float dim = Math.min(0.42f, Math.max(0f, (top - 0.42f) * 0.95f));   // the top buttons are white
            shade = dim < 0.04f ? null : new int[]{Math.round(dim * 255) << 24, 0};
            shadeAt = shade == null ? null : new float[]{0f, 0.3f};
        }

        /** Reads {@code photo} where {@code spec}'s clock, date line and the top buttons stand. One small copy of it. */
        static Tone of(Bitmap photo, Spec spec) {
            final int w = 96, h = 54;
            Bitmap small = Bitmap.createScaledBitmap(photo, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            if (small != photo) small.recycle();
            float kx = w / 1920f, ky = h / 1080f;
            float left = spec.left < 0 ? 560 : spec.left, right = spec.left < 0 ? 1360 : spec.left + 800;
            float[] behind = stats(px, w, h, left * kx, (spec.top + 0.06f * spec.size) * ky, right * kx,
                    (spec.top + BOX_BASELINE * spec.size) * ky);
            float cl = spec.left < 0 ? 700 : spec.left, cr = spec.left < 0 ? 1220 : spec.left + 520;
            float[] line = stats(px, w, h, cl * kx, (spec.top - 46) * ky, cr * kx, (spec.top - 4) * ky);
            float[] top = stats(px, w, h, 1380 * kx, 50 * ky, 1900 * kx, 180 * ky);
            return new Tone(behind[0], behind[1], line[0], top[0]);
        }

        /** Mean luminance (0 to 1) and its spread over a box of the small copy. */
        private static float[] stats(int[] px, int w, int h, float x0, float y0, float x1, float y1) {
            int ax = Math.max(0, Math.round(x0)), ay = Math.max(0, Math.round(y0));
            int bx = Math.min(w, Math.max(ax + 1, Math.round(x1))), by = Math.min(h, Math.max(ay + 1, Math.round(y1)));
            double sum = 0, sq = 0;
            int n = 0;
            for (int y = ay; y < by; y++) {
                for (int x = ax; x < bx; x++) {
                    int c = px[y * w + x];
                    double l = (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
                    sum += l;
                    sq += l * l;
                    n++;
                }
            }
            if (n == 0) return new float[]{0.5f, 0f};
            double mean = sum / n;
            return new float[]{(float) mean, (float) Math.sqrt(Math.max(0, sq / n - mean * mean))};
        }
    }

    private static final int WHITE_RIM = 0x8CFFFFFF, WHITE_FILL = 0x1AFFFFFF;
    /** A dark band at the top so the white top buttons and the date read on a pale sky. */
    private static final int[] BRIGHT_SKY = {0x59000000, 0x00000000};
    private static final Spec[] SPECS = {
            // The day set (d_*): one real place for each three hours, made for the clock: open sky where the digits
            // stand and the place's own skyline crossing their feet. The cut-outs follow each skyline.
            auto("d_chandratal", true, 200, 420),                 // 0-3 Chandratal, Spiti: under the Milky Way
            auto("d_pangong", true, 200, 420),                    // 3-6 Pangong Lake before dawn: the range in front
            auto("d_munnar", true, 200, 420),                     // 6-9 Munnar tea hills at sunrise: the silver oaks
            auto("d_bled", true, 200, 420),                       // 9-12 Lake Bled: the Alps and the island church
            auto("d_maldives", true, 200, 420),                   // 12-15 Maldives lagoon: the digits' feet under water
            auto("d_jaisalmer", true, 200, 420),                  // 15-18 Jaisalmer: the fort and khejri trees in front
            auto("d_marinedrive", true, 167, 420),                // 18-21 Marine Drive at dusk: the towers in front
            auto("d_varanasi", true, 130, 420),                   // 21-24 Varanasi ghats: the temple spires in front
            // More of the day set, from the person's own reference photos, so each part of the day has a second scene
            // and the picture differs from one day to the next.
            auto("d_bachalpsee", true, 112, 420),                 // 6-9 Bachalpsee: the snowy peaks in front
            auto("d_autumn", true, 200, 420),                     // 9-12 an autumn boulevard: the rooftops and trees in front, the hazy skyline behind
            auto("d_rakotz", true, 300, 300),                     // 12-15 Rakotz bridge: the clock inside the arch's circle
            auto("d_lauterbrunnen", true, 165, 420),              // 15-18 Lauterbrunnen: the clouded ridges in front
            auto("d_worli", true, 200, 420),                      // 18-21 Worli at sunset: the towers in front
            auto("d_nyc", false, 110, 420),                       // 18-21 a rainy New York avenue: no cut-out, the towers fill both sides
    };

    /** A scene that takes its clock's tones from its own picture (Tone): only where the clock stands is given. */
    private static Spec auto(String id, boolean cutout, float top, float size) {
        Spec s = new Spec(id, cutout, top, size, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true, null, null, true);
        s.adaptive = true;
        return s;
    }

    /** Scenes for each part of the day; following the time shows one of them, and keeps it until the part changes. */
    private static final String[][] BY_TIME = {
            {"d_chandratal"},                                                   // 0 to 3
            {"d_pangong"},                                                      // 3 to 6
            {"d_munnar", "d_bachalpsee"},                                       // 6 to 9
            {"d_bled", "d_autumn"},                                             // 9 to 12
            {"d_maldives", "d_rakotz"},                                         // 12 to 15
            {"d_jaisalmer", "d_lauterbrunnen"},                                 // 15 to 18
            {"d_marinedrive", "d_worli", "d_nyc"},                              // 18 to 21
            {"d_varanasi"},                                                     // 21 to 24
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Spec spec;
    /** The configured scene: an id, "random" for a different one each start, or empty to follow the time and weather. */
    private final String wanted;
    /** Scenes switched off in Home settings: never picked. */
    private final java.util.Set<String> off;
    /** Told the scene's accent colour once its photo is in (design/REVAMP-PLAN.md 3.1). */
    interface AccentListener {
        void onAccent(int argb);
    }
    private AccentListener accentListener;
    private int accent;
    private final boolean is24;
    private final Typeface clockFace, captionFace;
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint();
    private final Paint scrimPaint = new Paint();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);   // the glass is a half-size blur
    private final Paint rimFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private final Rect digits = new Rect();
    /**
     * The box every scene's clock was placed for (spec.top and spec.size): digits 0.87 size tall with their baseline
     * 0.9175 size below top, as couch-tv's Antonio sat in its CSS line box. Any clock face is scaled into that box, so
     * the scenery that crosses the digits still crosses them where it was planned to.
     */
    private static final float BOX_DIGITS = 0.87f, BOX_BASELINE = 0.9175f;
    /** The clock face's digit height per unit of text size. */
    private final float digitEm;
    /** Where drawClock put the digits, for drawCutout. */
    private float clockLeft, clockRight, clockTop, clockH;
    private float fadeFrom = -1, fadeTo = -1;
    private final Paint fadePaint = new Paint();
    private final Matrix glassMatrix = new Matrix();
    private Bitmap photo, glass, cutout;
    private BitmapShader glassShader;
    private boolean loading, released;
    private int shadeFor = -1, scrimFor = -1;
    private String time = "", date = "", weather = "";
    private android.graphics.drawable.Drawable weatherIcon;
    /** Fades and dissolves are on (animations on, reduced motion off). */
    private final boolean animated;
    /** A picture laid over the scene and faded out, so a new scene dissolves in over the old one (setFader). */
    private android.widget.ImageView fader;
    private Bitmap fadeBitmap;
    /** The first load makes no fade of its own: Home is covering a rebuild itself (setFader). */
    private boolean quietOnce;
    /** Run once the scene's pictures are in (whenReady). */
    private Runnable ready;
    /** When a key was last pressed on Home: the minute's dissolve is skipped while someone is moving about. */
    private static volatile long lastInput;

    /** A key was pressed on Home. */
    static void noteInput() {
        lastInput = android.os.SystemClock.uptimeMillis();
    }

    /** Drawn while the pictures are not in memory; Home's own background is taken away under the scene. */
    private final int background;

    SceneView(Context context, Config cfg) {
        super(context);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Everything here changes once a minute, but the whole window is redrawn on every frame of a focus move: the
        // photo, the cut-out, the gradients and above all the 400 px digits, which are too big for the glyph cache and
        // are drawn as paths each time. As a GPU layer it is drawn once per change and is one textured quad after that.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        background = cfg.backgroundColor;
        animated = cfg.animations && !cfg.reducedMotion;
        off = cfg.scenesOff;
        wanted = cfg.scene == null || off.contains(cfg.scene) ? "" : cfg.scene;
        spec = pick(context, wanted, off);
        time = date = "";
        is24 = ClockFormat.is24(context, cfg.clockFormat);
        Typeface serif = null, outfit = null;
        try {
            serif = getResources().getFont(R.font.outfit_semibold);
        } catch (RuntimeException ignored) {
        }
        try {
            outfit = getResources().getFont(R.font.outfit_medium);
        } catch (RuntimeException ignored) {
        }
        clockFace = serif != null ? serif : Typeface.create("sans-serif-medium", Typeface.NORMAL);
        captionFace = outfit != null ? outfit : Typeface.DEFAULT;
        for (Paint p : new Paint[]{fill, rimFill, rim, sheen}) p.setTypeface(clockFace);
        fill.setTextSize(1000f);
        fill.getTextBounds("0123456789", 0, 10, digits);
        digitEm = digits.height() / 1000f;
        rim.setStyle(Paint.Style.STROKE);
        fadePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        caption.setColor(0xEBFFFFFF);
        applySpec();
        caption.setTypeface(captionFace);
        caption.setTextAlign(Paint.Align.CENTER);
        ensureLoaded();
    }

    /** The picture's own reading for an adaptive scene; null for a hand-set one, and until the picture is in. */
    private Tone tone;
    /** What is drawn with now: the scene's own values, or the picture's reading of them. */
    private int rimFillNow, shadowNow, captionShadowNow = 0xB3000000;
    private int[] shadeNow;
    private float[] shadeAtNow;

    /** The paints that depend on the scene. */
    private void applySpec() {
        android.graphics.ColorFilter photo = spec.photoBright != 1f ? filter(spec.photoBright, 1f) : null;
        photoPaint.setColorFilter(photo);
        fgPaint.setColorFilter(photo);
        Tone t = spec.adaptive ? tone : null;
        fill.setColorFilter(filter(t != null ? t.fillBright : spec.fillBright, t != null ? t.fillSat : spec.fillSat));
        fill.setAlpha(Math.round(spec.fillAlpha * 255));
        rimFillNow = t != null ? t.rimFill : spec.rimFill;
        rimFill.setColor(rimFillNow);
        rim.setColor(t != null ? t.rimStroke : spec.rimStroke);
        shadowNow = t != null ? t.shadow : 0;
        shadeNow = t != null ? t.shade : spec.shade;
        shadeAtNow = t != null ? t.shadeAt : spec.shadeAt;
        int line = t != null ? t.captionColor : 0xEBFFFFFF;
        captionShadowNow = t != null ? t.captionShadow : 0xB3000000;
        caption.setColor(line);
        if (weatherIcon != null) weatherIcon.setTint(line);
        shadeFor = -1;
    }

    void setAccentListener(AccentListener l) {
        accentListener = l;
        if (accent != 0 && l != null) l.onAccent(accent);
    }

    /**
     * Home came back: when the scene follows the time and weather and they now call for another one (evening came,
     * fog came down), that one comes in. A pinned or random scene stays.
     */
    void followTime() {
        if (!wanted.isEmpty() || poolNow(getContext(), off).contains(spec.id)) return;
        Spec now = byTime(getContext(), off);
        if (now != spec) cover(snapshot());   // the old scene stays up until the new one is in, then dissolves
        spec = now;
        applySpec();
        release();
        ensureLoaded();
    }

    /** The part of the day now, as an index into BY_TIME: fog when it is foggy, else by the hour. */
    private static int partNow(Context ctx) {
        return java.time.LocalTime.now().getHour() / 3;   // one part for each three hours
    }

    /** Every scene id, in the order Home settings lists them. */
    static String[] ids() {
        String[] out = new String[SPECS.length];
        for (int i = 0; i < SPECS.length; i++) out[i] = SPECS[i].id;
        return out;
    }

    /** The part of the day a scene belongs to (an index into BY_TIME), or -1. */
    static int partOf(String id) {
        for (int p = 0; p < BY_TIME.length; p++) for (String s : BY_TIME[p]) if (s.equals(id)) return p;
        return -1;
    }

    /** The scenes switched on, in SPECS order; all of them when every one is off. */
    private static java.util.List<String> enabled(java.util.Set<String> off) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Spec s : SPECS) if (!off.contains(s.id)) out.add(s.id);
        if (out.isEmpty()) for (Spec s : SPECS) out.add(s.id);
        return out;
    }

    /** The switched-on scenes for this part of the day; every switched-on scene when none of them is for now. */
    private static java.util.List<String> poolNow(Context ctx, java.util.Set<String> off) {
        java.util.List<String> on = enabled(off), out = new java.util.ArrayList<>();
        for (String id : BY_TIME[partNow(ctx)]) if (on.contains(id)) out.add(id);
        // Every scene for this part of the day switched off: the scene for the hour, not any scene at all.
        if (out.isEmpty()) for (String id : BY_TIME[java.time.LocalTime.now().getHour() / 3]) if (on.contains(id)) out.add(id);
        return out.isEmpty() ? on : out;
    }

    /** One of the scenes for this part of the day, not the one shown last time when there is a choice. */
    private static Spec byTime(Context ctx, java.util.Set<String> off) {
        java.util.List<String> ids = poolNow(ctx, off);
        SharedPreferences prefs = ctx.getSharedPreferences("scene_state", Context.MODE_PRIVATE);
        String id = another(ids, prefs.getString("last_timed", ""));
        prefs.edit().putString("last_timed", id).apply();
        return spec(id);
    }

    /** A random one of ids, other than last when there is a choice. */
    private static String another(java.util.List<String> ids, String last) {
        int next = new Random().nextInt(ids.size());
        if (ids.size() > 1 && ids.get(next).equals(last)) next = (next + 1) % ids.size();
        return ids.get(next);
    }

    private static Spec spec(String id) {
        for (Spec s : SPECS) if (s.id.equals(id)) return s;
        return SPECS[0];
    }

    /**
     * The scene to show: the one config.json or Home settings pins ("scene": "fog"), one that follows the time and
     * weather (empty, the default), or "random": a new picture each start, never the one shown last time.
     */
    private static Spec pick(Context ctx, String wanted, java.util.Set<String> off) {
        for (Spec s : SPECS) if (s.id.equalsIgnoreCase(wanted)) return s;
        if (!"random".equalsIgnoreCase(wanted)) return byTime(ctx, off);
        SharedPreferences prefs = ctx.getSharedPreferences("scene_state", Context.MODE_PRIVATE);
        String id = another(enabled(off), prefs.getString("last_random", ""));
        prefs.edit().putString("last_random", id).apply();
        return spec(id);
    }

    private static ColorMatrixColorFilter filter(float brightness, float saturation) {
        ColorMatrix cm = new ColorMatrix();
        cm.setScale(brightness, brightness, brightness, 1f);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(saturation);
        cm.postConcat(sat);
        return new ColorMatrixColorFilter(cm);
    }

    /** The weather beside the date ("30° Sunny") and its small picture (0: none); an empty line hides both. */
    void setWeather(String line, int iconRes) {
        weather = line == null ? "" : line;
        weatherIcon = null;
        if (iconRes != 0 && !weather.isEmpty()) {
            try {
                weatherIcon = getContext().getDrawable(iconRes);
                if (weatherIcon != null) weatherIcon.setTint(caption.getColor());
            } catch (RuntimeException ignored) {
            }
        }
        invalidate();
    }

    /**
     * Home sits idle with its rows put away: another of the scenes for now dissolves in, so the picture is never still
     * for long (kinder to the panel too). A pinned scene stays.
     */
    void next() {
        boolean random = "random".equalsIgnoreCase(wanted);
        if ((!wanted.isEmpty() && !random) || photo == null || loading) return;
        java.util.List<String> pool = random ? enabled(off) : poolNow(getContext(), off);
        if (!random && !pool.contains(spec.id)) {   // the three hours turned while Home sat idle
            followTime();
            return;
        }
        if (pool.size() < 2) return;
        String id = another(pool, spec.id);
        cover(snapshot());
        spec = spec(id);
        applySpec();
        release();
        ensureLoaded();
    }

    private final Runnable minute = this::refresh;

    /** Time and date again, then once more on the next minute change, until pause(). */
    void refresh() {
        String now = DateTimeFormatter.ofPattern(is24 ? "HH:mm" : "h:mm").format(LocalTime.now());
        // The minute turned while Home sat still: the old digits dissolve into the new. With someone moving about
        // the digits just change, so the dissolve's redraws never land on a focus move.
        boolean dissolve = animated && photo != null && !time.isEmpty() && !now.equals(time) && isShown()
                && android.os.SystemClock.uptimeMillis() - lastInput > 1500L;
        Bitmap old = dissolve ? clockBand() : null;   // taken while the old minute is still the one drawn
        time = now;
        date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(LocalDate.now());
        if (old != null) dissolveFrom(old);
        else if (wanted.isEmpty() && photo != null && isShown() && LocalTime.now().getMinute() == 0) followTime();   // a new part of the day
        invalidate();
        ui.removeCallbacks(minute);
        ui.postDelayed(minute, 60_000L - System.currentTimeMillis() % 60_000L + 50L);
    }

    /** Home left the screen: no more ticks until refresh() is called again. */
    void pause() {
        ui.removeCallbacks(minute);
    }

    /**
     * The view the dissolves are done with: a picture laid over the scene and faded out by the compositor, which
     * costs no redraw of the scene. Home adds it just above the scene; without it scenes simply change. With
     * {@code quietFirst} the first load fades nothing: Home is rebuilding and covers the whole of itself meanwhile.
     */
    void setFader(android.widget.ImageView view, boolean quietFirst) {
        fader = view;
        quietOnce = quietFirst;
        if (fader != null) fader.setVisibility(GONE);
    }

    /** The band the clock and its caption stand in, in view pixels: {top, bottom}. */
    private int[] band() {
        float s = getWidth() / 1920f;
        return new int[]{Math.max(0, Math.round((spec.top - 60) * s)),
                Math.min(getHeight(), Math.round((spec.top + BOX_BASELINE * spec.size + 30) * s))};
    }

    /** The clock's band as it is drawn now, as a picture; null when there is no fader to show it in, or one is busy. */
    private Bitmap clockBand() {
        int w = getWidth();
        if (w == 0 || fader == null || fader.getVisibility() == VISIBLE) return null;
        int[] band = band();
        if (band[1] <= band[0]) return null;
        try {
            Bitmap b = Bitmap.createBitmap(w, band[1] - band[0], Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.translate(0, -band[0]);
            draw(c);
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * The minute turned: the band's old picture lies over the scene, which draws the new minute once underneath,
     * and the compositor fades the picture away. The scene is drawn once, not on every frame of the dissolve, and
     * the digits that did not change stay as they are, the same pixels above and below.
     */
    private void dissolveFrom(final Bitmap old) {
        final int top = band()[0];
        fader.animate().cancel();
        if (fadeBitmap != null) fadeBitmap.recycle();
        fadeBitmap = old;
        fader.setImageDrawable(new android.graphics.drawable.Drawable() {
            @Override
            public void draw(Canvas canvas) {
                if (!old.isRecycled()) canvas.drawBitmap(old, 0, top, null);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter filter) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        });
        fader.setAlpha(1f);
        fader.setVisibility(VISIBLE);
        fader.animate().alpha(0f).setStartDelay(90L).setDuration(700L).setInterpolator(Motion.MOVE).withEndAction(() -> {
            fader.setVisibility(GONE);
            fader.setImageDrawable(null);
            if (fadeBitmap != null) fadeBitmap.recycle();
            fadeBitmap = null;
        }).start();
    }

    /** Runs {@code r} once the scene's pictures are in; at once when they already are. */
    void whenReady(Runnable r) {
        if (photo != null) r.run();
        else ready = r;
    }

    /** What the scene shows now, as a picture; null before it has drawn. */
    private Bitmap snapshot() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || photo == null) return null;
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            draw(new Canvas(b));
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Lays {@code frame} over the scene (a null frame: Home's flat colour) until uncover(). */
    private void cover(Bitmap frame) {
        if (fader == null || !animated) {
            if (frame != null) frame.recycle();
            return;
        }
        fader.animate().cancel();
        if (fadeBitmap != null && fadeBitmap != frame) fadeBitmap.recycle();
        fadeBitmap = frame;
        if (frame != null) fader.setImageBitmap(frame);
        else fader.setImageDrawable(new android.graphics.drawable.ColorDrawable(background));
        fader.setAlpha(1f);
        fader.setVisibility(VISIBLE);
    }

    /** The scene's pictures are in: whatever covers it dissolves away. With nothing over it, it fades up from the flat colour. */
    private void uncover() {
        if (quietOnce) {
            quietOnce = false;
            return;
        }
        if (fader == null || !animated) return;
        if (fader.getVisibility() != VISIBLE) cover(null);
        fader.animate().alpha(0f).setStartDelay(60L).setDuration(520L).setInterpolator(Motion.MOVE).withEndAction(() -> {
            fader.setVisibility(GONE);
            fader.setImageDrawable(null);
            if (fadeBitmap != null) fadeBitmap.recycle();
            fadeBitmap = null;
        }).start();
    }

    /** Decodes the pictures off the main thread when they are not in memory. */
    void ensureLoaded() {
        released = false;
        if (loading || photo != null) return;
        loading = true;
        final Context ctx = getContext().getApplicationContext();
        io.execute(() -> {
            // Full colour for the photo: 16-bit colour bands in smooth skies (an orange haze, a dusk gradient).
            Bitmap p = decode(ctx, "scenes/" + spec.id + (spec.lossless ? ".webp" : ".jpg"), Bitmap.Config.ARGB_8888, 1);
            Bitmap g = decode(ctx, "scenes/" + spec.id + "-glass.jpg", Bitmap.Config.RGB_565, 2);   // a blur: half size is plenty
            Bitmap f = !spec.cutout ? null : spec.lossless ? throughMask(ctx, p, "scenes/" + spec.id + "-mask.png")
                    : decode(ctx, "scenes/" + spec.id + "-fg.webp", Bitmap.Config.ARGB_8888, 1);
            final Spec read = spec;
            final Tone t = read.adaptive && p != null ? Tone.of(p, read) : null;
            ui.post(() -> {
                loading = false;
                if (released) {
                    for (Bitmap b : new Bitmap[]{p, g, f}) if (b != null) b.recycle();
                    return;
                }
                photo = p;
                glass = g;
                cutout = f;
                glassShader = null;
                if (read == spec) {
                    tone = t;
                    applySpec();
                }
                invalidate();
                uncover();
                if (ready != null) {
                    Runnable r = ready;
                    ready = null;
                    r.run();
                }
                if (p != null) {
                    accent = accentOf(p);
                    if (accentListener != null) accentListener.onAccent(accent);
                }
            });
        });
    }

    /** Memory pressure while Home is not on screen: give the pictures back (about 18 MB). */
    void release() {
        released = true;
        Bitmap[] all = {photo, glass, cutout};
        photo = glass = cutout = null;
        glassShader = null;
        for (Bitmap b : all) if (b != null) b.recycle();
    }

    /**
     * The scene's accent: the strongest hue family among the more colourful pixels in its upper part (a coarse 24x9
     * sample in twelve 30-degree bins, weighted by saturation and brightness), as a light, clear tone that reads on
     * dark glass. A mean would blend a teal sky and an orange peak into green. One pass over 216 pixels per picture.
     */
    private static int accentOf(Bitmap b) {
        Bitmap small = Bitmap.createScaledBitmap(b, 24, 14, true);
        float[] hsv = new float[3];
        double[] bins = new double[12], hueSum = new double[12];
        for (int i = 0; i < 24; i++) {
            for (int j = 0; j < 9; j++) {   // the upper part: sky and subject, not the foreground at the bottom
                android.graphics.Color.colorToHSV(small.getPixel(i, j), hsv);
                double w = hsv[1] * hsv[1] * hsv[2];   // favour clearly coloured pixels over grey ones
                int k = Math.min(11, (int) (hsv[0] / 30f));
                bins[k] += w;
                hueSum[k] += w * hsv[0];
            }
        }
        if (small != b) small.recycle();
        int best = 0;
        for (int k = 1; k < 12; k++) if (bins[k] > bins[best]) best = k;
        if (bins[best] < 1e-3) return 0xFF8FD3FF;
        float hue = (float) (hueSum[best] / bins[best]);
        return android.graphics.Color.HSVToColor(new float[]{hue, 0.45f, 1f});
    }

    /** The cut-out: the photo's own pixels wherever the mask is opaque, so it stays as exact as the photo. */
    private static Bitmap throughMask(Context ctx, Bitmap photo, String maskPath) {
        if (photo == null) return null;
        Bitmap mask = decode(ctx, maskPath, Bitmap.Config.ARGB_8888, 1);
        if (mask == null) return null;
        Bitmap out = Bitmap.createBitmap(photo.getWidth(), photo.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(mask, null, new Rect(0, 0, out.getWidth(), out.getHeight()), null);
        Paint in = new Paint();
        in.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(photo, 0, 0, in);
        mask.recycle();
        return out;
    }

    private static Bitmap decode(Context ctx, String path, Bitmap.Config config, int sample) {
        try (InputStream in = ctx.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = config;
            o.inSampleSize = sample;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (photo == null) {
            c.drawColor(background);
            return;
        }
        drawScene(c, w, h, time);
    }

    /** The whole scene, with {@code shown} as its clock. */
    private void drawScene(Canvas c, int w, int h, String shown) {
        float s = w / 1920f;
        dst.set(0, 0, w, h);
        c.drawBitmap(photo, null, dst, photoPaint);
        drawClock(c, w, h, s, shown);
        if (cutout != null) drawCutout(c, s);
        if (shadeNow != null) {   // over the clock and the cut-out alike, so the cut-out has no edge in it
            if (shadeFor != h) {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h, shadeNow, shadeAtNow, Shader.TileMode.CLAMP));
                shadeFor = h;
            }
            c.drawRect(0, 0, w, h, shadePaint);
        }
        drawCaption(c, s);
        if (scrimFor != h) {   // a little dark at the bottom so the dock stays readable
            scrimPaint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x00000000, 0x00000000, 0x80000000},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            scrimFor = h;
        }
        c.drawRect(0, 0, w, h, scrimPaint);
    }

    /**
     * The scenery in front of the digits, but only over their lower part: over the digits it fades out from 65% of
     * their height up to 40%, so the top of every digit always shows and any time reads, whichever peak or tower
     * stands behind it. Beside the digits the cut-out is the photo's own pixels, so cutting it away there shows
     * nothing.
     */
    private void drawCutout(Canvas c, float s) {
        int layer = c.saveLayer(0, 0, getWidth(), getHeight(), null);
        c.drawBitmap(cutout, null, dst, fgPaint);
        float from = clockTop + 0.4f * clockH, to = clockTop + 0.65f * clockH;
        if (fadeFrom != from || fadeTo != to) {
            fadePaint.setShader(new LinearGradient(0, from, 0, to, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            fadeFrom = from;
            fadeTo = to;
        }
        float pad = 8 * s;
        c.drawRect(clockLeft - pad, 0, clockRight + pad, to, fadePaint);
        c.restoreToCount(layer);
    }

    private void drawCaption(Canvas c, float s) {
        // "Sat 14 Nov   ·   [sun] 23° Clear": the date, then the weather's small picture and its short line.
        String first = weather.isEmpty() ? date : date + "   \u00B7   ";
        caption.setTextSize(31 * s);
        caption.setShadowLayer(8 * s, 0, 2 * s, captionShadowNow);
        caption.setTextAlign(Paint.Align.LEFT);
        float base = (spec.top - 12) * s;   // just above the digits, whatever height the scene puts them at
        float icon = weatherIcon != null ? 30 * s : 0f, gap = weatherIcon != null ? 9 * s : 0f;
        float firstW = caption.measureText(first);
        float total = firstW + (weather.isEmpty() ? 0f : icon + gap + caption.measureText(weather));
        // Centred over a centred clock; with a clock set to one side the line starts where its digits do.
        float x = spec.left < 0 ? (getWidth() - total) / 2f : spec.left * s;
        c.drawText(first, x, base, caption);
        if (weather.isEmpty()) return;
        x += firstW;
        if (weatherIcon != null) {
            int top = Math.round(base - icon * 0.84f);
            weatherIcon.setBounds(Math.round(x), top, Math.round(x + icon), Math.round(top + icon));
            weatherIcon.draw(c);
            x += icon + gap;
        }
        c.drawText(weather, x, base, caption);
    }

    private void drawClock(Canvas c, int w, int h, float s, String time) {
        float digitH = BOX_DIGITS * spec.size * s;
        float size = digitH / digitEm;
        for (Paint p : new Paint[]{fill, rimFill, rim, sheen}) p.setTextSize(size);
        rim.setStrokeWidth(Math.max(1f, 1.44f * s));
        if (glass != null && glassShader == null) {
            glassShader = new BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            glassMatrix.setScale(w / (float) glass.getWidth(), h / (float) glass.getHeight());
            glassShader.setLocalMatrix(glassMatrix);
            fill.setShader(glassShader);
        }
        float tw = fill.measureText(time);
        float x;
        if (spec.left < 0) {
            x = (w - tw) / 2f;
        } else {   // "left" is where the digits visibly start, not the glyph's side bearing
            fill.getTextBounds(time, 0, time.length(), digits);
            x = spec.left * s - digits.left;
        }
        float base = (spec.top + BOX_BASELINE * spec.size) * s;
        clockLeft = x;
        clockRight = x + tw;
        clockTop = base - digitH;
        clockH = digitH;
        if (shadowNow != 0) fill.setShadowLayer(7 * s, 0, 3 * s, shadowNow);   // lifts frosted digits off a busy picture
        else fill.clearShadowLayer();
        Path digitsPath = outline(time, x, base);
        c.drawPath(digitsPath, fill);
        if (rimFillNow != 0) c.drawPath(digitsPath, rimFill);
        c.drawPath(digitsPath, rim);
        if (spec.sheen) {   // linear-gradient(160deg, .55, 0 at 38%, 0 at 70%, .22) clipped to the digits
            float top = base - digitH, bottom = base;
            double a = Math.toRadians(160);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float bw = tw, bh = digitH;
            float len = Math.abs(bw * dx) + Math.abs(bh * dy);
            float cx = x + bw / 2f, cy = (top + bottom) / 2f;
            sheen.setShader(new LinearGradient(cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f,
                    new int[]{0x8CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF}, new float[]{0f, 0.38f, 0.70f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(digitsPath, sheen);
        }
    }

    private final Path outlineRaw = new Path(), outlineMerged = new Path();
    private String outlineKey;

    /**
     * The digits as one outline. A variable font builds a glyph from overlapping parts; stroked as text, the rim traced
     * every overlap as a seam inside the digit. Merged once per minute (and size), the rim follows only the outer edge.
     */
    private Path outline(String text, float x, float base) {
        String key = text + '|' + x + '|' + base + '|' + fill.getTextSize();
        if (key.equals(outlineKey)) return outlineMerged;
        fill.getTextPath(text, 0, text.length(), x, base, outlineRaw);
        outlineMerged.reset();
        if (!outlineMerged.op(outlineRaw, new Path(), Path.Op.UNION)) outlineMerged.set(outlineRaw);
        outlineKey = key;
        return outlineMerged;
    }
}
