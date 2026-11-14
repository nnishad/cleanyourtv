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
 * The weather for one place, from Open-Meteo (https://open-meteo.com, no account or key).
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

    /** WMO weather code as a short phrase, day or night where it matters. */
    static String describe(int code, boolean day) {
        switch (code) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm, hail";
            default: return "";
        }
    }

    /** "Vilnius  ·  31°C  ·  Partly cloudy  ·  Feels like 34°C". */
    static String line(String place, Reading r) {
        StringBuilder s = new StringBuilder();
        if (!place.isEmpty()) s.append(place).append("  ·  ");
        s.append(String.format(Locale.US, "%d°C", r.tempC));
        String what = describe(r.code, r.day);
        if (!what.isEmpty()) s.append("  ·  ").append(what);
        if (Math.abs(r.feelsC - r.tempC) >= 2) s.append(String.format(Locale.US, "  ·  Feels like %d°C", r.feelsC));
        return s.toString();
    }
}
