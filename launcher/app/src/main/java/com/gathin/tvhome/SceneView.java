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

    private static final int WHITE_RIM = 0x8CFFFFFF, WHITE_FILL = 0x1AFFFFFF;
    /** A dark band at the top so the white top buttons and the date read on a pale sky. */
    private static final int[] BRIGHT_SKY = {0x59000000, 0x00000000};
    private static final Spec[] SPECS = {
            // Golden hour: a little smaller, low enough for the volcano's tip to rise into the digits
            new Spec("golden", true, 152, 390, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true, null, null),
            // Waterline: the clock sits on the water, clear of the Keep watching row; no cut-out
            new Spec("water", false, 200, 420, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true,
                    new int[]{0x6B000C18, 0x1F000C18, 0x00000000, 0x80001428}, new float[]{0f, 0.40f, 0.52f, 1f}),
            // Fog: a big dark clock on the left, its digits on the rows' left edge, sinking into the misty hills
            new Spec("fog", true, 172, 470, 68, 0.46f, 0.8f, 0.8f, 1f, 0x99FFFFFF, 0, false,
                    new int[]{0x66000000, 0x00000000}, new float[]{0f, 0.24f}),   // the top buttons are white on a pale sky
            // Rainforest dawn: warm light at the top, the clock low enough for the ridges to cross it
            new Spec("dawn", true, 230, 400, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true,
                    new int[]{0x6B321C0C, 0x00321C0C}, new float[]{0f, 0.55f}),
            // The landscapes below: 16:9 crops of the photos at the panel's 1920x1080, stored losslessly. Each clock
            // sits on the depth plane found with a depth map, so the nearer scenery crosses the bottom of the digits.
            // Misty valley: the peaks under the cloud rise into the digits
            scene("valley", 160, 440, 1f, BRIGHT_SKY),
            // Arch bridge: the clock inside the circle of the arch and its reflection, the arch and pillar in front
            scene("bridge", 250, 380, 1.2f, null),
            // Alpine lake: the peaks rise into the digits from below
            scene("alpine", 140, 440, 1f, BRIGHT_SKY),
            // Ember ridges: a sunrise haze; the snowy ridge line crosses the digits
            scene("ember", 190, 400, 1f, null),
            // Highland valley: the clock in the clouds, the ridge's peaks in front
            scene("highland", 170, 420, 1f, BRIGHT_SKY),
            // Mountain lake: the clock in the clouds over the valley, the slopes in front at both ends
            scene("lake", 180, 440, 1f, BRIGHT_SKY),
            // Snow ridge: behind the near snowy ridge and its trees, in front of the far range
            scene("snow", 170, 440, 1f, BRIGHT_SKY),
            // City at dusk: the towers rise into the digits
            scene("city", 230, 380, 1f, null),
    };

    private static Spec scene(String id, float top, float size, float fillBright, int[] shade) {
        return new Spec(id, true, top, size, -1, fillBright, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true, shade,
                shade == null ? null : new float[]{0f, 0.3f}, true);
    }

    /** Scenes for each part of the day; following the time shows one of them, and keeps it until the part changes. */
    private static final String[][] BY_TIME = {
            {"dawn", "ember"},                                                  // 5 to 9
            {"water", "alpine", "lake", "highland", "snow", "bridge"},          // 9 to 16
            {"golden", "city"},                                                 // 16 to 19
            {"city", "ember"},                                                  // 19 to 5: the two darkest
            {"fog", "valley"},                                                  // fog or mist reported
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
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    /** The minute before, while its digits dissolve into the new ones (clockFade 0 to 1); null otherwise. */
    private String prevTime;
    private float clockFade = 1f;
    private android.animation.ValueAnimator clockAnim;
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
            serif = getResources().getFont(R.font.instrument_serif);
        } catch (RuntimeException ignored) {
        }
        try {
            outfit = getResources().getFont(R.font.outfit_medium);
        } catch (RuntimeException ignored) {
        }
        clockFace = serif != null ? serif : Typeface.create("serif", Typeface.NORMAL);
        captionFace = outfit != null ? outfit : Typeface.DEFAULT;
        for (Paint p : new Paint[]{fill, rimFill, rim, sheen}) p.setTypeface(clockFace);
        fill.setTextSize(1000f);
        fill.getTextBounds("0123456789", 0, 10, digits);
        digitEm = digits.height() / 1000f;
        rim.setStyle(Paint.Style.STROKE);
        fadePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        applySpec();
        caption.setTypeface(captionFace);
        caption.setColor(0xEBFFFFFF);
        caption.setTextAlign(Paint.Align.CENTER);
        ensureLoaded();
    }

    /** The paints that depend on the scene. */
    private void applySpec() {
        android.graphics.ColorFilter photo = spec.photoBright != 1f ? filter(spec.photoBright, 1f) : null;
        photoPaint.setColorFilter(photo);
        fgPaint.setColorFilter(photo);
        fill.setColorFilter(filter(spec.fillBright, spec.fillSat));
        fill.setAlpha(Math.round(spec.fillAlpha * 255));
        rimFill.setColor(spec.rimFill);
        rim.setColor(spec.rimStroke);
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
        Weather.Reading r = Weather.cached(ctx);
        if (r != null && (r.code == 45 || r.code == 48) && System.currentTimeMillis() - r.at < 3 * 3600_000L) return 4;
        int h = java.time.LocalTime.now().getHour();
        return h >= 5 && h < 9 ? 0 : h >= 9 && h < 16 ? 1 : h >= 16 && h < 19 ? 2 : 3;
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

    /** The weather line under the date ("Vilnius · 30°C · Sunny"); empty hides it. */
    void setWeather(String line) {
        weather = line == null ? "" : line;
        invalidate();
    }

    private final Runnable minute = this::refresh;

    /** Time and date again, then once more on the next minute change, until pause(). */
    void refresh() {
        String now = DateTimeFormatter.ofPattern(is24 ? "HH:mm" : "h:mm").format(LocalTime.now());
        date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(LocalDate.now());
        // The minute turned while Home sat still: the old digits dissolve into the new. With someone moving about
        // the digits just change, so the dissolve's redraws never land on a focus move.
        boolean dissolve = animated && photo != null && !time.isEmpty() && !now.equals(time) && isShown()
                && android.os.SystemClock.uptimeMillis() - lastInput > 1500L;
        if (clockAnim != null) clockAnim.cancel();
        if (dissolve) {
            prevTime = time;
            time = now;
            clockFade = 0f;
            clockAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            clockAnim.setDuration(420L);
            clockAnim.setInterpolator(Motion.MOVE);
            clockAnim.addUpdateListener(a -> {
                clockFade = (float) a.getAnimatedValue();
                invalidate();
            });
            clockAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    prevTime = null;
                    clockFade = 1f;
                    invalidate();
                }
            });
            clockAnim.start();
        } else {
            prevTime = null;
            clockFade = 1f;
            time = now;
        }
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
        if (prevTime != null && clockFade < 1f) {
            // The scene with the old minute, then the band the clock stands in again with the new one, let in by degrees.
            drawScene(c, w, h, prevTime);
            float s = w / 1920f;
            int layer = c.saveLayerAlpha(0, (spec.top - 20) * s, w, (spec.top + BOX_BASELINE * spec.size + 30) * s,
                    Math.round(clockFade * 255));
            drawScene(c, w, h, time);
            c.restoreToCount(layer);
        } else {
            drawScene(c, w, h, time);
        }
    }

    /** The whole scene, with {@code shown} as its clock. */
    private void drawScene(Canvas c, int w, int h, String shown) {
        float s = w / 1920f;
        dst.set(0, 0, w, h);
        c.drawBitmap(photo, null, dst, photoPaint);
        drawClock(c, w, h, s, shown);
        if (cutout != null) drawCutout(c, s);
        if (spec.shade != null) {   // over the clock and the cut-out alike, so the cut-out has no edge in it
            if (shadeFor != h) {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h, spec.shade, spec.shadeAt, Shader.TileMode.CLAMP));
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
     * The scenery in front of the digits, but only over their lower part: over the digits it fades out from 90% of
     * their height up to 62%, so the top of every digit always shows and any time reads, whichever peak or tower
     * stands behind it. Beside the digits the cut-out is the photo's own pixels, so cutting it away there shows
     * nothing.
     */
    private void drawCutout(Canvas c, float s) {
        int layer = c.saveLayer(0, 0, getWidth(), getHeight(), null);
        c.drawBitmap(cutout, null, dst, fgPaint);
        float from = clockTop + 0.62f * clockH, to = clockTop + 0.9f * clockH;
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
        String line = weather.isEmpty() ? date : date + "   ·   " + weather;
        caption.setTextSize(31 * s);
        caption.setShadowLayer(8 * s, 0, 2 * s, 0xB3000000);
        float base = (spec.top - 12) * s;   // just above the digits, whatever height the scene puts them at
        if (spec.left < 0) {
            caption.setTextAlign(Paint.Align.CENTER);
            c.drawText(line, getWidth() / 2f, base, caption);
        } else {   // a clock set to one side: the line starts where its digits do
            caption.setTextAlign(Paint.Align.LEFT);
            c.drawText(line, spec.left * s, base, caption);
        }
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
        c.drawText(time, x, base, fill);
        if (spec.rimFill != 0) c.drawText(time, x, base, rimFill);
        c.drawText(time, x, base, rim);
        if (spec.sheen) {   // linear-gradient(160deg, .55, 0 at 38%, 0 at 70%, .22) clipped to the digits
            float top = base - digitH, bottom = base;
            double a = Math.toRadians(160);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float bw = tw, bh = digitH;
            float len = Math.abs(bw * dx) + Math.abs(bh * dy);
            float cx = x + bw / 2f, cy = (top + bottom) / 2f;
            sheen.setShader(new LinearGradient(cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f,
                    new int[]{0x8CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF}, new float[]{0f, 0.38f, 0.70f, 1f}, Shader.TileMode.CLAMP));
            c.drawText(time, x, base, sheen);
        }
    }
}
