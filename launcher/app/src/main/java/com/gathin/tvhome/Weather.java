package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Bedroom TV patch: the weather for one place, from Open-Meteo (https://open-meteo.com, no account or key).
 * What leaves the TV is the place's latitude and longitude, once every 30 minutes while Home is on screen.
 * The last reading is kept in SharedPreferences, so the line is there at once after a restart, even offline.
 */
final class Weather {
    private static final String PREFS = "weather";
    private static final int MAX_BYTES = 16 * 1024;

    private Weather() {
    }

    static final class Reading {
        final int tempC, feelsC, code;
        final boolean day;
        final long at;

        Reading(int tempC, int feelsC, int code, boolean day, long at) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.at = at;
        }
    }

    /** Drops the reading kept on the TV (the place changed). */
    static void forget(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** The last reading kept on the TV, or null if there is none. */
    static Reading cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("at")) return null;
        return new Reading(p.getInt("temp", 0), p.getInt("feels", 0), p.getInt("code", -1),
                p.getBoolean("day", true), p.getLong("at", 0));
    }

    /** One request. Blocks, so call it off the main thread. Returns null on any failure. */
    static Reading fetch(Context context, String latitude, String longitude) {
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                    + "&current=temperature_2m,apparent_temperature,weather_code,is_day&timezone=auto");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setRequestProperty("User-Agent", "tvhome-weather");
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < MAX_BYTES) out.write(buf, 0, n);
            }
            JSONObject now = new JSONObject(out.toString("UTF-8")).getJSONObject("current");
            Reading r = new Reading((int) Math.round(now.getDouble("temperature_2m")),
                    (int) Math.round(now.getDouble("apparent_temperature")), now.getInt("weather_code"),
                    now.optInt("is_day", 1) == 1, System.currentTimeMillis());
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("temp", r.tempC)
                    .putInt("feels", r.feelsC).putInt("code", r.code).putBoolean("day", r.day).putLong("at", r.at).apply();
            return r;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** WMO weather code as a short phrase in the launcher's language, day or night where it matters. */
    static String describe(Context ctx, int code, boolean day) {
        int res;
        switch (code) {
            case 0: res = day ? R.string.wx_sunny : R.string.wx_clear; break;
            case 1: res = day ? R.string.wx_mostly_sunny : R.string.wx_mostly_clear; break;
            case 2: res = R.string.wx_partly_cloudy; break;
            case 3: res = R.string.wx_overcast; break;
            case 45: case 48: res = R.string.wx_fog; break;
            case 51: case 53: case 55: res = R.string.wx_drizzle; break;
            case 56: case 57: res = R.string.wx_freezing_drizzle; break;
            case 61: res = R.string.wx_light_rain; break;
            case 63: res = R.string.wx_rain; break;
            case 65: res = R.string.wx_heavy_rain; break;
            case 66: case 67: res = R.string.wx_freezing_rain; break;
            case 71: res = R.string.wx_light_snow; break;
            case 73: res = R.string.wx_snow; break;
            case 75: res = R.string.wx_heavy_snow; break;
            case 77: res = R.string.wx_snow_grains; break;
            case 80: res = R.string.wx_light_showers; break;
            case 81: res = R.string.wx_showers; break;
            case 82: res = R.string.wx_heavy_showers; break;
            case 85: case 86: res = R.string.wx_snow_showers; break;
            case 95: res = R.string.wx_thunderstorm; break;
            case 96: case 99: res = R.string.wx_thunderstorm_hail; break;
            default: return "";
        }
        return ctx.getString(res);
    }

    /** "31° Partly cloudy": the short form beside the date on a scene, which draws icon(r) before it. */
    static String brief(Context ctx, Reading r) {
        String what = describe(ctx, r.code, r.day);
        return String.format(Locale.US, "%d°", r.tempC) + (what.isEmpty() ? "" : " " + what);
    }

    /** The small picture for a reading: sun or moon, cloud, fog, rain, snow or storm. */
    static int icon(Reading r) {
        int c = r.code;
        if (c == 0 || c == 1) return r.day ? R.drawable.ic_wx_sun : R.drawable.ic_wx_moon;
        if (c == 2 || c == 3) return R.drawable.ic_wx_cloud;
        if (c == 45 || c == 48) return R.drawable.ic_wx_fog;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return R.drawable.ic_wx_snow;
        if (c >= 95) return R.drawable.ic_wx_storm;
        if (c >= 51 && c <= 82) return R.drawable.ic_wx_rain;
        return 0;
    }

    /** "Vilnius  ·  31°C  ·  Partly cloudy  ·  Feels like 34°C". */
    static String line(Context ctx, String place, Reading r) {
        StringBuilder s = new StringBuilder();
        if (!place.isEmpty()) s.append(place).append("  ·  ");
        s.append(String.format(Locale.US, "%d°C", r.tempC));
        String what = describe(ctx, r.code, r.day);
        if (!what.isEmpty()) s.append("  ·  ").append(what);
        if (Math.abs(r.feelsC - r.tempC) >= 2) s.append("  ·  ").append(ctx.getString(R.string.wx_feels_like, r.feelsC));
        return s.toString();
    }
}
