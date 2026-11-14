package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keep watching, natively (watch_row): Android's Watch Next list, the shows Netflix, Hotstar and others say you
 * are part way through, as a row of picture cards above the dock, each with its title inside. Read only, nothing is stored but the small pictures
 * (cacheDir/kw, a week at most, plus 16 decoded ones in memory). The query and the pictures run off the main
 * thread; the row is rebuilt only when the list changed. Needs READ_TV_LISTINGS, granted with pm grant.
 */
final class KeepWatching {

    interface Listener {
        /** A card took focus: the dock's glass highlight has to go. {@code context} says what it is, for the context line. */
        void onCardFocused(String context);
    }

    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    private static final String READ_TV = "android.permission.READ_TV_LISTINGS";
    private static final int MAX_CARDS = 10, PER_APP = 3, THUMB_WIDTH = 384;
    private static final long CACHE_DAYS_MS = 7L * 24 * 3600 * 1000;
    private static final String[] COLUMNS = {"_id", "package_name", "title", "episode_title", "season_display_number",
            "episode_display_number", "poster_art_uri", "thumbnail_uri", "last_playback_position_millis",
            "duration_millis", "intent_uri"};

    private static final class Item {
        String id, pkg, app, title, sub, art, intentUri;
        int progress;   // percent
        long leftMs;    // how much is left to watch, 0 when unknown
    }

    private final Activity activity;
    private final View block;
    private final RecyclerView list;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(16);
    private final Set<String> inflight = new HashSet<>();
    private final File cacheDir;
    private final float density;
    private List<Item> items = new ArrayList<>();
    private int cardWidth;   // 0: the layout's own width; the height follows at 16:9
    private int accent;      // the scene's accent for the progress bars; 0 keeps the default
    private int bandPx;      // the dock band's width the row lines up with
    private String signature = "";
    private int lastWant = -1;
    private boolean swept;

    KeepWatching(Activity activity, View block, RecyclerView list, Listener listener) {
        this.activity = activity;
        this.block = block;
        this.list = list;
        this.listener = listener;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.cacheDir = new File(activity.getCacheDir(), "kw");
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        list.setItemAnimator(null);
        list.setHasFixedSize(true);
        list.setAdapter(new Adapter());
    }

    /** Looks at the list again (Home came back); a no-op when it has not changed. */
    void refresh() {
        io.execute(() -> {
            List<Item> got = query();
            ui.post(() -> apply(got));
        });
    }

    /** Gives the decoded pictures back (memory pressure while Home is not on screen). */
    void release() {
        thumbs.evictAll();
    }

    /** The scene's accent colour, for the progress bars. */
    void setAccent(int argb) {
        if (argb == accent) return;
        accent = argb;
        list.getAdapter().notifyDataSetChanged();
    }

    /** The "Keep watching" heading, which the context line sits level with. */
    View heading() {
        return ((ViewGroup) block).getChildAt(0);
    }

    /** The cards on screen now, for the inactivity dim. */
    List<View> cards() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) out.add(list.getChildAt(i));
        return out;
    }

    boolean shown() {
        return block.getVisibility() == View.VISIBLE;
    }

    /**
     * Left on the row's first card, or Right on its last: there is nowhere further to go. The key is swallowed, so
     * focus stays in the row (left to itself, Right on the last card found the top buttons) and Focus nudges the card.
     */
    private boolean atEnd(View card, int keyCode) {
        int pos = list.getChildAdapterPosition(card);
        boolean rtl = list.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int back = rtl ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : android.view.KeyEvent.KEYCODE_DPAD_LEFT;
        int on = rtl ? android.view.KeyEvent.KEYCODE_DPAD_LEFT : android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        return (pos == 0 && keyCode == back) || (pos == items.size() - 1 && keyCode == on);
    }

    /** Down from the top buttons: the first card takes focus; false when there is no row to land on. */
    boolean focusFirst() {
        if (!shown()) return false;
        View first = null;
        for (int i = 0; i < list.getChildCount() && first == null; i++) {   // the first whole card, not one cut by the edge
            View child = list.getChildAt(i);
            if (child.getLeft() >= list.getPaddingLeft() - 1) first = child;
        }
        if (first == null) first = list.getChildAt(0);
        return first != null && first.requestFocus();
    }

    /** Puts the row just above the dock: want is the grid's top padding, where the dock row starts. */
    void place(int want) {
        lastWant = want;
        int height = block.getHeight() > 0 ? block.getHeight() : (int) (112 * density);
        int top = Math.max(0, want - height - (int) (10 * density));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) block.getLayoutParams();
        if (lp.topMargin != top) {
            lp.topMargin = top;
            block.setLayoutParams(lp);
        }
    }

    /** Lines the row up with the dock band: it starts at the band's left edge, and a card cut at its right fades out (FadeRow). */
    void alignTo(int left, int right) {
        int wrap = ((View) block.getParent()).getWidth();
        if (right - left <= 0 || wrap <= 0) return;
        // The list reaches zoomRoom past the band on both sides, so a focused card's zoom is not cut; its first card
        // still starts on the band's left edge, and the peeking card fades out there.
        int zoomRoom = (int) (16 * density);
        int padStart = Math.max(0, left - zoomRoom), padEnd = Math.max(0, wrap - right - zoomRoom);
        block.setPaddingRelative(padStart, block.getPaddingTop(), padEnd, block.getPaddingBottom());
        list.setPaddingRelative(left - padStart, list.getPaddingTop(), wrap - right - padEnd, list.getPaddingBottom());
        View heading = ((ViewGroup) block).getChildAt(0);
        heading.setPaddingRelative(left - padStart - (int) (6 * density), heading.getPaddingTop(), 0, heading.getPaddingBottom());
        // New padding with an old scroll offset left the cards short of the band's edge: back to the start, unless
        // someone is in the row.
        if (!list.hasFocus()) ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        bandPx = right - left;
        sizeCards();
    }

    /**
     * Cards are 4.4 across the band, whatever their number: four whole ones, and with more than four the fifth shows
     * its first part at the band's right edge, so it is clear the row scrolls. The row clips at the band, never at
     * the screen.
     */
    private void sizeCards() {
        if (bandPx <= 0) return;
        int gap = (int) (12 * density);
        int width = Math.round((bandPx - 4 * gap) / 4.4f);
        if (width > 0 && width != cardWidth) {
            cardWidth = width;
            list.getAdapter().notifyDataSetChanged();
        }
    }

    /** The grid scrolled away from the dock (Down into more apps): the row rides up and fades out with it. */
    void scrolled(int shiftPx) {
        block.setTranslationY(-shiftPx);
        block.setAlpha(Math.max(0f, 1f - shiftPx / (float) Math.max(1, block.getHeight())));
    }

    private void apply(List<Item> got) {
        StringBuilder sig = new StringBuilder();
        for (Item it : got) sig.append(it.id).append('|').append(it.title).append('|').append(it.sub).append('|').append(it.progress).append(';');
        String now = sig.toString();
        boolean was = shown();
        if (!now.equals(signature)) {
            signature = now;
            items = got;
            sizeCards();
            list.getAdapter().notifyDataSetChanged();
            for (Item it : got) loadArt(it.art);
        }
        boolean show = !items.isEmpty();
        if (show != was) {
            block.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && lastWant >= 0) place(lastWant);
        }
    }

    // ---- the Watch Next list -----------------------------------------------------------------------

    private List<Item> query() {
        List<Item> out = new ArrayList<>();
        Context ctx = activity;
        if (ctx.checkSelfPermission(READ_TV) != PackageManager.PERMISSION_GRANTED) return out;
        PackageManager pm = ctx.getPackageManager();
        Map<String, Integer> perApp = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        try (Cursor c = ctx.getContentResolver().query(WATCH_NEXT, COLUMNS, null, null,
                "last_engagement_time_utc_millis DESC")) {
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX_CARDS) {
                String pkg = c.getString(1);
                if (pkg == null || pkg.equals(ctx.getPackageName())) continue;
                int n = perApp.containsKey(pkg) ? perApp.get(pkg) + 1 : 1;
                perApp.put(pkg, n);
                if (n > PER_APP) continue;
                String label = labels.get(pkg);
                if (label == null) {
                    try {
                        label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                    } catch (Exception e) {
                        label = pkg;
                    }
                    labels.put(pkg, label);
                }
                Item it = new Item();
                it.id = c.getString(0);
                it.pkg = pkg;
                it.app = label;
                String title = c.getString(2);
                it.title = title == null || title.trim().isEmpty() ? label : title.trim();
                String season = c.getString(4), episode = c.getString(5), episodeTitle = c.getString(3);
                StringBuilder sub = new StringBuilder();
                if (season != null && !season.isEmpty()) sub.append('S').append(season);
                if (episode != null && !episode.isEmpty()) sub.append(sub.length() > 0 ? " E" : "E").append(episode);
                if (sub.length() == 0 && episodeTitle != null) sub.append(episodeTitle);
                it.sub = sub.toString();
                String thumb = c.getString(7), poster = c.getString(6);
                it.art = thumb != null && !thumb.isEmpty() ? thumb : poster != null && !poster.isEmpty() ? poster : null;
                long pos = c.getLong(8), dur = c.getLong(9);
                it.progress = dur > 0 && pos > 0 ? (int) Math.min(100, pos * 100 / dur) : 0;
                it.leftMs = dur > 0 && pos > 0 && pos < dur ? dur - pos : 0;
                it.intentUri = c.getString(10);
                out.add(it);
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();   // no provider, or the permission was taken back: an empty row, never a crash
        }
        return out;
    }

    private final java.util.Map<String, android.graphics.drawable.Drawable> icons = new HashMap<>(), banners = new HashMap<>();

    /** The app's TV banner, shown on a card whose show has no picture; cached for the session, null when there is none. */
    private android.graphics.drawable.Drawable appBanner(String pkg) {
        if (banners.containsKey(pkg)) return banners.get(pkg);
        android.graphics.drawable.Drawable d = null;
        try {
            d = activity.getPackageManager().getApplicationBanner(pkg);
        } catch (Exception ignored) {
        }
        banners.put(pkg, d);
        return d;
    }

    /** The app's own icon for the badge on its cards; cached for the session, null when there is none. */
    private android.graphics.drawable.Drawable appIcon(String pkg) {
        if (icons.containsKey(pkg)) return icons.get(pkg);
        android.graphics.drawable.Drawable d = null;
        try {
            d = activity.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        icons.put(pkg, d);
        return d;
    }

    // ---- the pictures -----------------------------------------------------------------------------------

    private void loadArt(String art) {
        if (art == null || thumbs.get(art) != null || !inflight.add(art)) return;
        io.execute(() -> {
            Bitmap b = fetch(art);
            ui.post(() -> {
                inflight.remove(art);
                if (b == null) return;
                thumbs.put(art, b);
                list.getAdapter().notifyDataSetChanged();
            });
        });
    }

    private Bitmap fetch(String art) {
        try {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) return null;
            sweep();
            File f = new File(cacheDir, sha1(art));
            byte[] data = f.isFile() ? readAll(new FileInputStream(f), 0) : download(art);
            if (data == null) return null;
            if (!f.isFile()) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** Only an address Watch Next itself gave us is ever fetched. */
    private byte[] download(String art) throws Exception {
        Uri u = Uri.parse(art);
        String scheme = u.getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            HttpURLConnection c = (HttpURLConnection) new URL(art).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            try (InputStream in = c.getInputStream()) {
                return readAll(in, 1_500_000);
            } finally {
                c.disconnect();
            }
        }
        if ("content".equals(scheme) || "android.resource".equals(scheme)) {
            try (InputStream in = activity.getContentResolver().openInputStream(u)) {
                return in == null ? null : readAll(in, 1_500_000);
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in, int limit) throws Exception {
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (limit > 0 && out.size() > limit) return null;
            }
            return out.toByteArray();
        }
    }

    /** Once per start: pictures older than a week go. */
    private void sweep() {
        if (swept) return;
        swept = true;
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - CACHE_DAYS_MS;
        for (File f : files) if (f.lastModified() < cutoff) f.delete();
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    /** "S1 E4  ·  23 min left": where you are and how long is left; empty when the app says neither. */
    private String where(Item it) {
        StringBuilder s = new StringBuilder(it.sub);
        if (it.leftMs >= 60_000L) {
            if (s.length() > 0) s.append("  \u00B7  ");
            s.append(activity.getString(R.string.kw_min_left, (int) (it.leftMs / 60_000L)));
        }
        return s.toString();
    }

    /** "Planet Earth III  ·  S1 E4  ·  23 min left", for TalkBack. */
    private String describe(Item it) {
        String where = where(it);
        return where.isEmpty() ? it.title : it.title + "  \u00B7  " + where;
    }

    // ---- opening a card -----------------------------------------------------------------------------

    private void open(Item it) {
        try {
            if (it.intentUri != null) {
                Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                // only ever open something in the app that owns the item
                if (i.getComponent() != null && !i.getComponent().getPackageName().equals(it.pkg)) i.setComponent(null);
                i.setPackage(it.pkg);
                i.setSelector(null);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
                return;
            }
        } catch (Exception ignored) {
            // falls through to the app itself
        }
        try {
            PackageManager pm = activity.getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(it.pkg);
            if (launch != null) activity.startActivity(launch);
        } catch (Exception ignored) {
        }
    }

    // ---- the cards ------------------------------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_watch_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Item it = items.get(position);
            h.icon.setImageDrawable(appIcon(it.pkg));
            h.title.setText(it.title);
            h.sub.setText(where(it));
            h.sub.setAlpha(h.itemView.isFocused() ? 1f : 0f);
            h.progress.setVisibility(it.progress > 0 ? View.VISIBLE : View.INVISIBLE);
            h.progress.setProgress(it.progress);
            if (accent != 0) h.progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            Bitmap b = it.art == null ? null : thumbs.get(it.art);
            if (b != null) h.thumb.setImageBitmap(b);
            else h.thumb.setImageDrawable(appBanner(it.pkg));   // no picture (yet): the app's banner, when it has one
            h.size(cardWidth);
            h.itemView.setContentDescription(it.app + ", " + describe(it));   // read out by TalkBack
            h.itemView.setOnClickListener(v -> open(it));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, sub;
            final ImageView icon;
            final ProgressBar progress;
            private int sizedFor;

            /** The card's size (16:9), its rounded corners, and the focus rim drawn over the picture. */
            void size(int width) {
                if (width <= 0 || width == sizedFor) return;
                sizedFor = width;
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                lp.width = width;
                lp.height = Math.round(width * 9 / 16f);
                itemView.setLayoutParams(lp);
            }

            Holder(View v) {
                super(v);
                thumb = v.findViewById(R.id.watchThumb);
                icon = v.findViewById(R.id.watchAppIcon);
                title = v.findViewById(R.id.watchTitle);
                sub = v.findViewById(R.id.watchSub);
                progress = v.findViewById(R.id.watchProgress);
                final float radius = activity.getResources().getDimension(R.dimen.radius_s);
                v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                v.setClipToOutline(true);
                android.graphics.drawable.GradientDrawable rim = new android.graphics.drawable.GradientDrawable();
                rim.setCornerRadius(radius);
                rim.setStroke(Math.round(2 * density), activity.getColor(R.color.glass_focus_rim));
                android.graphics.drawable.StateListDrawable focus = new android.graphics.drawable.StateListDrawable();
                focus.addState(new int[]{android.R.attr.state_focused}, rim);
                focus.addState(new int[0], new android.graphics.drawable.ColorDrawable(0));
                v.setForeground(focus);
                v.setOnFocusChangeListener((view, has) -> {
                    // Where you are and how long is left show on the focused card only; Home's context line is for apps.
                    sub.animate().alpha(has ? 1f : 0f).setDuration(Motion.FAST).start();
                    // A title too long for the card runs across it while the card is focused, and ends in dots otherwise.
                    title.setEllipsize(has ? android.text.TextUtils.TruncateAt.MARQUEE : android.text.TextUtils.TruncateAt.END);
                    title.setMarqueeRepeatLimit(-1);
                    title.setSelected(has);
                    if (has) listener.onCardFocused("");
                });
                Focus.attach(v, true, false, (view, code, event) -> atEnd(view, code));   // zoom, press dip, the end nudge; the rim is the foreground above
            }
        }
    }
}
