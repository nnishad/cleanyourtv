package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The place the weather line is for. It is chosen in Home settings > Weather: the person types a city, Open-Meteo's
 * geocoding finds the places of that name (the forecast comes from the same service, so no other party is involved),
 * and the one picked is kept on the TV. The place chosen there wins over weather_place, latitude and longitude in
 * config.xml, which still work for a build that fixes its place. Kept in a file of its own, so a new theme, or Reset
 * in Home settings, does not forget it; "Forget the place" does.
 */
final class WeatherPlace {

    private static final String PREFS = "weather_place";
    private static final int MAX_BYTES = 32 * 1024;

    final String name, region, country, lat, lon;

    WeatherPlace(String name, String region, String country, String lat, String lon) {
        this.name = name;
        this.region = region;
        this.country = country;
        this.lat = lat;
        this.lon = lon;
    }

    /** "Uttar Pradesh, India": what tells two places of one name apart. */
    String where() {
        StringBuilder s = new StringBuilder();
        if (!region.isEmpty() && !region.equals(name)) s.append(region);
        if (!country.isEmpty()) s.append(s.length() > 0 ? ", " : "").append(country);
        return s.toString();
    }

    /** The place picked in Home settings, or null. */
    static WeatherPlace chosen(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.contains("lat") || !p.contains("lon")) return null;
        return new WeatherPlace(p.getString("name", ""), p.getString("region", ""), p.getString("country", ""),
                p.getString("lat", ""), p.getString("lon", ""));
    }

    /** The place picked in Home settings, else the one config.xml names, else null: no place, no weather. */
    static WeatherPlace current(Context context) {
        WeatherPlace picked = chosen(context);
        if (picked != null) return picked;
        String name = context.getString(R.string.weather_place).trim(), lat = context.getString(R.string.latitude).trim(),
                lon = context.getString(R.string.longitude).trim();
        return name.isEmpty() || lat.isEmpty() || lon.isEmpty() ? null : new WeatherPlace(name, "", "", lat, lon);
    }

    /** Whether the weather is on until someone says otherwise: when a place was picked, or config.xml turns it on. */
    static boolean onByDefault(Context context) {
        return chosen(context) != null || context.getResources().getBoolean(R.bool.show_weather);
    }

    static void save(Context context, WeatherPlace place) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("name", place.name)
                .putString("region", place.region).putString("country", place.country).putString("lat", place.lat)
                .putString("lon", place.lon).apply();
        Weather.forget(context);   // the old place's reading must not be shown for the new one
    }

    static void forget(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        Weather.forget(context);
    }

    /**
     * The places called {@code query}, best known first, in {@code language} where Open-Meteo has the name in it.
     * Blocks: call it off the main thread. An empty list means no such place; null means the search could not be made.
     */
    static List<WeatherPlace> search(String query, String language) {
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language="
                    + URLEncoder.encode(language, "UTF-8") + "&name=" + URLEncoder.encode(query, "UTF-8"));
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
            List<WeatherPlace> found = new ArrayList<>();
            JSONArray results = new JSONObject(out.toString("UTF-8")).optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                double lat = r.getDouble("latitude"), lon = r.getDouble("longitude");
                found.add(new WeatherPlace(r.optString("name", "").trim(), r.optString("admin1", "").trim(),
                        r.optString("country", "").trim(), String.format(Locale.US, "%.4f", lat),
                        String.format(Locale.US, "%.4f", lon)));
            }
            return found;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
