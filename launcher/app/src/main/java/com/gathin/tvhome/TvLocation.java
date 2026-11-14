package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Where the TV is, for the weather when nobody chose a city (theme key weather_auto). A TV has no GPS to speak of;
 * Android estimates a position from the Wi-Fi networks around it when the TV's Location setting is on, and hands it to
 * an app that holds the location permission (build-and-install grants it, as it is also what reads the Wi-Fi name).
 * The position is worked out on the TV and is not sent anywhere by this class; the weather then asks Open-Meteo for the
 * forecast at those coordinates, rounded to two decimals (about a kilometre). Kept for a week, so Home does not ask the
 * TV's location service every time it opens.
 */
final class TvLocation {

    static final class Fix {
        final String lat, lon;
        final long at;

        Fix(String lat, String lon, long at) {
            this.lat = lat;
            this.lon = lon;
            this.at = at;
        }
    }

    private static final String PREFS = "tv_location";
    private static final long MAX_AGE_MS = 7L * 24 * 3600 * 1000;
    private static final long WAIT_S = 25;

    private TvLocation() {
    }

    /** Whether the launcher may ask for the TV's location. */
    static boolean allowed(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** The position kept on the TV, however old, or null. */
    static Fix cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat")) return null;
        return new Fix(p.getString("lat", ""), p.getString("lon", ""), p.getLong("at", 0));
    }

    /**
     * The kept position when it is under a week old, else the TV's last known one, else one fresh position, else the
     * old one: null when there has never been any. Blocks (up to 25 seconds, only when the TV has none to hand), so call
     * it off the main thread.
     */
    static Fix resolve(Context context) {
        Fix kept = cached(context);
        if (kept != null && System.currentTimeMillis() - kept.at < MAX_AGE_MS) return kept;
        if (!allowed(context)) return kept;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return kept;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) best = fresh(context, lm);
        } catch (SecurityException | IllegalArgumentException e) {
            return kept;
        }
        if (best == null) return kept;
        Fix fix = new Fix(String.format(Locale.US, "%.2f", best.getLatitude()),
                String.format(Locale.US, "%.2f", best.getLongitude()), System.currentTimeMillis());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lat", fix.lat)
                .putString("lon", fix.lon).putLong("at", fix.at).apply();
        return fix;
    }

    /** One new position from the network (Wi-Fi) provider, or the first other one that is on; null when none answers. */
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("MissingPermission")   // resolve() has checked allowed() before it gets here
    private static Location fresh(Context context, LocationManager lm) {
        String provider = null;
        for (String p : new String[]{LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER}) {
            if (lm.getProviders(true).contains(p)) {
                provider = p;
                break;
            }
        }
        if (provider == null) return null;
        final CountDownLatch done = new CountDownLatch(1);
        final Location[] got = new Location[1];
        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                got[0] = location;
                done.countDown();
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, context.getMainExecutor(), location -> {
                    got[0] = location;
                    done.countDown();
                });
            } else {
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            }
            done.await(WAIT_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lm.removeUpdates(listener);
        }
        return got[0];
    }
}
