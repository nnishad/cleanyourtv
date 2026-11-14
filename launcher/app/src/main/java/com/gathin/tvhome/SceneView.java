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

        Spec(String id, boolean cutout, float top, float size, float left, float fillBright, float fillSat,
             float fillAlpha, float photoBright, int rimStroke, int rimFill, boolean sheen, int[] shade, float[] shadeAt) {
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
    private static final Spec[] SPECS = {
            // Night: the clock behind the peak, pink glass
            new Spec("night", true, 150, 400, -1, 1.15f, 1.2f, 0.9f, 0.86f, 0x80FFD7E1, 0x0FFFC8D7, true, null, null),
            // Golden hour: a little smaller, low enough for the volcano's tip to rise into the digits
            new Spec("golden", true, 152, 390, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true, null, null),
            // Waterline: the clock sits on the water; no cut-out
            new Spec("water", false, 268, 420, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true,
                    new int[]{0x6B000C18, 0x1F000C18, 0x00000000, 0x80001428}, new float[]{0f, 0.40f, 0.52f, 1f}),
            // Fog: a big dark clock on the left, sinking into the misty hills
            new Spec("fog", true, 172, 470, 96, 0.46f, 0.8f, 0.8f, 1f, 0x99FFFFFF, 0, false,
                    new int[]{0x66000000, 0x00000000}, new float[]{0f, 0.24f}),   // the top buttons are white on a pale sky
            // Rainforest dawn: warm light at the top, the clock low enough for the ridges to cross it
            new Spec("dawn", true, 230, 400, -1, 1f, 1f, 1f, 1f, WHITE_RIM, WHITE_FILL, true,
                    new int[]{0x6B321C0C, 0x00321C0C}, new float[]{0f, 0.55f}),
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Spec spec;
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
    private final Matrix glassMatrix = new Matrix();
    private Bitmap photo, glass, cutout;
    private BitmapShader glassShader;
    private boolean loading, released;
    private int shadeFor = -1, scrimFor = -1;
    private String time = "", date = "", weather = "";

    SceneView(Context context, Config cfg) {
        super(context);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        spec = pick(context, cfg.scene);
        time = date = "";
        is24 = ClockFormat.is24(context, cfg.clockFormat);
        Typeface antonio = null, outfit = null;
        try {
            antonio = getResources().getFont(R.font.antonio_light);
        } catch (RuntimeException ignored) {
        }
        try {
            outfit = getResources().getFont(R.font.outfit_medium);
        } catch (RuntimeException ignored) {
        }
        clockFace = antonio != null ? antonio : Typeface.create("sans-serif-light", Typeface.NORMAL);
        captionFace = outfit != null ? outfit : Typeface.DEFAULT;
        if (spec.photoBright != 1f) {
            photoPaint.setColorFilter(filter(spec.photoBright, 1f));
            fgPaint.setColorFilter(filter(spec.photoBright, 1f));
        }
        for (Paint p : new Paint[]{fill, rimFill, rim, sheen}) {
            p.setTypeface(clockFace);
            p.setLetterSpacing(-0.02f);
        }
        fill.setColorFilter(filter(spec.fillBright, spec.fillSat));
        fill.setAlpha(Math.round(spec.fillAlpha * 255));
        rimFill.setColor(spec.rimFill);
        rim.setStyle(Paint.Style.STROKE);
        rim.setColor(spec.rimStroke);
        caption.setTypeface(captionFace);
        caption.setColor(0xEBFFFFFF);
        caption.setTextAlign(Paint.Align.CENTER);
        ensureLoaded();
    }

    /** A new picture each start, never the one shown last time, unless config.json pins one ("scene": "fog"). */
    private static Spec pick(Context ctx, String wanted) {
        for (Spec s : SPECS) if (s.id.equalsIgnoreCase(wanted)) return s;
        SharedPreferences prefs = ctx.getSharedPreferences("scene_state", Context.MODE_PRIVATE);
        int last = prefs.getInt("last", -1);
        Random random = new Random();
        boolean again = last >= 0 && last < SPECS.length;
        int next = random.nextInt(again ? SPECS.length - 1 : SPECS.length);
        if (again && next >= last) next++;
        prefs.edit().putInt("last", next).apply();
        return SPECS[next];
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
        time = DateTimeFormatter.ofPattern(is24 ? "HH:mm" : "h:mm").format(LocalTime.now());
        date = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(LocalDate.now());
        invalidate();
        ui.removeCallbacks(minute);
        ui.postDelayed(minute, 60_000L - System.currentTimeMillis() % 60_000L + 50L);
    }

    /** Home left the screen: no more ticks until refresh() is called again. */
    void pause() {
        ui.removeCallbacks(minute);
    }

    /** Decodes the pictures off the main thread when they are not in memory. */
    void ensureLoaded() {
        released = false;
        if (loading || photo != null) return;
        loading = true;
        final Context ctx = getContext().getApplicationContext();
        io.execute(() -> {
            Bitmap p = decode(ctx, "scenes/" + spec.id + ".jpg", Bitmap.Config.RGB_565, 1);
            Bitmap g = decode(ctx, "scenes/" + spec.id + "-glass.jpg", Bitmap.Config.RGB_565, 2);   // a blur: half size is plenty
            Bitmap f = spec.cutout ? decode(ctx, "scenes/" + spec.id + "-fg.webp", Bitmap.Config.ARGB_8888, 1) : null;
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
            });
        });
    }

    /** Memory pressure while Home is not on screen: give the pictures back (about 14 MB). */
    void release() {
        released = true;
        Bitmap[] all = {photo, glass, cutout};
        photo = glass = cutout = null;
        glassShader = null;
        for (Bitmap b : all) if (b != null) b.recycle();
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
        if (photo == null || w == 0) return;
        float s = w / 1920f;
        dst.set(0, 0, w, h);
        c.drawBitmap(photo, null, dst, photoPaint);
        if (spec.shade != null) {
            if (shadeFor != h) {
                shadePaint.setShader(new LinearGradient(0, 0, 0, h, spec.shade, spec.shadeAt, Shader.TileMode.CLAMP));
                shadeFor = h;
            }
            c.drawRect(0, 0, w, h, shadePaint);
        }
        drawCaption(c, s);
        drawClock(c, w, h, s);
        if (cutout != null) c.drawBitmap(cutout, null, dst, fgPaint);
        if (scrimFor != h) {   // a little dark at the bottom so the dock stays readable
            scrimPaint.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x00000000, 0x00000000, 0x80000000},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            scrimFor = h;
        }
        c.drawRect(0, 0, w, h, scrimPaint);
    }

    private void drawCaption(Canvas c, float s) {
        String line = weather.isEmpty() ? date : date + "   ·   " + weather;
        caption.setTextSize(31 * s);
        caption.setShadowLayer(8 * s, 0, 2 * s, 0xB3000000);
        c.drawText(line, getWidth() / 2f, 138 * s, caption);
    }

    private void drawClock(Canvas c, int w, int h, float s) {
        float size = spec.size * s;
        for (Paint p : new Paint[]{fill, rimFill, rim, sheen}) p.setTextSize(size);
        rim.setStrokeWidth(Math.max(1f, 1.44f * s));
        if (glass != null && glassShader == null) {
            glassShader = new BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            glassMatrix.setScale(w / (float) glass.getWidth(), h / (float) glass.getHeight());
            glassShader.setLocalMatrix(glassMatrix);
            fill.setShader(glassShader);
        }
        float tw = fill.measureText(time);
        float x = spec.left < 0 ? (w - tw) / 2f : spec.left * s;
        float lineHeight = 0.82f * size;
        Paint.FontMetrics fm = fill.getFontMetrics();
        float base = spec.top * s + lineHeight / 2f - (fm.ascent + fm.descent) / 2f;   // a CSS line box centres its text
        c.drawText(time, x, base, fill);
        if (spec.rimFill != 0) c.drawText(time, x, base, rimFill);
        c.drawText(time, x, base, rim);
        if (spec.sheen) {   // linear-gradient(160deg, .55, 0 at 38%, 0 at 70%, .22) clipped to the digits
            float top = spec.top * s, bottom = top + lineHeight;
            double a = Math.toRadians(160);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float bw = tw, bh = lineHeight;
            float len = Math.abs(bw * dx) + Math.abs(bh * dy);
            float cx = x + bw / 2f, cy = (top + bottom) / 2f;
            sheen.setShader(new LinearGradient(cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f,
                    new int[]{0x8CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF}, new float[]{0f, 0.38f, 0.70f, 1f}, Shader.TileMode.CLAMP));
            c.drawText(time, x, base, sheen);
        }
    }
}
