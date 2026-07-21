package dev.inkbrief.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import dev.inkbrief.data.Card;

public class SyncClient {

    public static class SyncException extends Exception {
        public final int code; // 0=network, 401=auth, other=http

        public SyncException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    /** Full today response including progress counters. */
    public static class TodayResult {
        public final String date;
        public final List<Card> cards;
        public final int total;
        public final int likedToday;
        public final int skippedToday;

        public TodayResult(String date, List<Card> cards, int total, int likedToday, int skippedToday) {
            this.date = date;
            this.cards = cards;
            this.total = total;
            this.likedToday = likedToday;
            this.skippedToday = skippedToday;
        }
    }

    private static final String PREF_API_URL = "api_url";
    private static final String PREF_TOKEN = "token";
    private static final String DEFAULT_API_URL = "http://192.168.10.11:8720";
    private static final String DEFAULT_TOKEN = "dev-token";

    private final Context context;

    public SyncClient(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getBaseUrl() {
        return getPrefs().getString(PREF_API_URL, DEFAULT_API_URL);
    }

    public String getToken() {
        return getPrefs().getString(PREF_TOKEN, DEFAULT_TOKEN);
    }

    public static void saveSettings(Context ctx, String apiUrl, String token) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_API_URL, apiUrl);
        editor.putString(PREF_TOKEN, token);
        editor.apply();
    }

    public List<Card> fetchTodayCards() throws Exception {
        TodayResult result = fetchToday();
        return result == null ? null : result.cards;
    }

    public TodayResult fetchToday() throws Exception {
        // Default API returns pending-only cards for swiping.
        String response = httpGet("/v1/cards/today");
        if (response == null) {
            return null;
        }

        JSONObject json = new JSONObject(response);
        JSONArray cardsArray = json.getJSONArray("cards");
        String date = json.optString("date", "");
        int total = json.optInt("total", cardsArray.length());
        int likedToday = json.optInt("liked_today", 0);
        int skippedToday = json.optInt("skipped_today", 0);

        List<Card> cards = new ArrayList<Card>();
        for (int i = 0; i < cardsArray.length(); i++) {
            JSONObject obj = cardsArray.getJSONObject(i);
            Card card = new Card();
            card.setId(obj.getString("id"));
            card.setPosition(obj.optInt("position", i + 1));
            card.setTag(obj.optString("tag", ""));
            card.setTitle(obj.getString("title"));
            card.setSource(obj.optString("source", ""));
            card.setAiScore(obj.optDouble("ai_score", 0.0));
            card.setSummary(obj.optString("summary", ""));
            card.setUrl(obj.optString("url", ""));
            card.setReason(obj.optString("reason", ""));
            card.setDate(date);
            // Align with backend bandit status (pending/liked/skipped).
            // Never hardcode "new".
            String status = obj.optString("status", "pending");
            if (status == null || status.length() == 0 || "new".equals(status)) {
                status = "pending";
            }
            card.setStatus(status);
            card.setCachedAt(System.currentTimeMillis());
            cards.add(card);
        }
        return new TodayResult(date, cards, total, likedToday, skippedToday);
    }

    public boolean likeCard(String cardId) throws Exception {
        String response = httpPost("/v1/cards/" + cardId + "/like");
        if (response == null) {
            return false;
        }
        JSONObject json = new JSONObject(response);
        return json.optBoolean("success", false);
    }

    public boolean skipCard(String cardId) throws Exception {
        String response = httpPost("/v1/cards/" + cardId + "/skip");
        if (response == null) {
            return false;
        }
        JSONObject json = new JSONObject(response);
        return json.optBoolean("success", false);
    }

    private String httpGet(String path) throws Exception {
        return httpRequest("GET", path, null);
    }

    private String httpPost(String path) throws Exception {
        return httpRequest("POST", path, "");
    }

    private String httpRequest(String method, String path, String body) throws Exception {
        URL url = new URL(getBaseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("X-InkBrief-Token", getToken());
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream os = conn.getOutputStream();
                try {
                    byte[] bytes = body.getBytes("UTF-8");
                    os.write(bytes);
                    os.flush();
                } finally {
                    os.close();
                }
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return readStream(conn.getInputStream());
            }
            if (responseCode == 401) {
                throw new SyncException("unauthorized", 401);
            }
            String errBody = "";
            try {
                InputStream es = conn.getErrorStream();
                if (es != null) {
                    errBody = readStream(es);
                }
            } catch (Exception ignored) {
            }
            throw new SyncException("HTTP " + responseCode + " " + errBody, responseCode);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException(e.getMessage() != null ? e.getMessage() : "network", 0);
        } finally {
            conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
