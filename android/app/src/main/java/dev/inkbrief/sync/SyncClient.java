package dev.inkbrief.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import dev.inkbrief.data.Card;

public class SyncClient {

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
        String response = httpGet("/v1/cards/today");
        if (response == null) {
            return null;
        }

        JSONObject json = new JSONObject(response);
        JSONArray cardsArray = json.getJSONArray("cards");
        String date = json.optString("date", "");

        List<Card> cards = new ArrayList<Card>();
        for (int i = 0; i < cardsArray.length(); i++) {
            JSONObject obj = cardsArray.getJSONObject(i);
            Card card = new Card();
            card.setId(obj.getString("id"));
            card.setPosition(obj.getInt("position"));
            card.setTag(obj.optString("tag", ""));
            card.setTitle(obj.getString("title"));
            card.setSource(obj.optString("source", ""));
            card.setAiScore(obj.optDouble("ai_score", 0.0));
            card.setSummary(obj.optString("summary", ""));
            card.setUrl(obj.optString("url", ""));
            card.setReason(obj.optString("reason", ""));
            card.setDate(date);
            card.setStatus("new");
            card.setCachedAt(System.currentTimeMillis());
            cards.add(card);
        }
        return cards;
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

    private String httpGet(String endpoint) throws Exception {
        String urlStr = getBaseUrl() + endpoint;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-InkBrief-Token", getToken());
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
        return null;
    }

    private String httpPost(String endpoint) throws Exception {
        String urlStr = getBaseUrl() + endpoint;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-InkBrief-Token", getToken());
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            OutputStream os = conn.getOutputStream();
            os.write(new byte[0]);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
        return null;
    }
}
