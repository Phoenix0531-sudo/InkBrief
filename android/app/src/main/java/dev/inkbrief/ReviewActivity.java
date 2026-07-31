package dev.inkbrief;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.inkbrief.sync.SyncClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Weekly review + tag weights screen for Kindle e-ink.
 *
 * Fetches /v1/weekly/review and /v1/config/tags, renders pure TextViews
 * (no WebView, no Material). Black on white, large tap targets.
 */
public class ReviewActivity extends Activity {

    private SyncClient syncClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        syncClient = new SyncClient(this);
        buildUI();
        loadData();
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        // Title
        TextView title = new TextView(this);
        title.setText("\u2605 \u672C\u5468\u56DE\u987E");
        title.setTextSize(22);
        title.setTypeface(title.getTypeface());
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        root.addView(title);

        // Content container
        TextView content = new TextView(this);
        content.setId(1001);
        content.setTextSize(15);
        content.setTextColor(Color.parseColor("#222222"));
        content.setLineSpacing(4, 1.2f);
        content.setText("\u6B63\u5728\u52A0\u8F7D\u2026");
        root.addView(content);

        // Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 40, 0, 0);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("\u5237 \u65B0");
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { loadData(); }
        });
        btnRow.addView(refreshBtn);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("\u8BBE \u7F6E");
        settingsBtn.setPadding(32, 0, 0, 0);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ReviewActivity.this, SettingsActivity.class));
            }
        });
        btnRow.addView(settingsBtn);

        Button backBtn = new Button(this);
        backBtn.setText("\u8FD4 \u56DE");
        backBtn.setPadding(32, 0, 0, 0);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        btnRow.addView(backBtn);

        root.addView(btnRow);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String weeklyJson = syncClient.getRaw("/v1/weekly/review");
                    String tagsJson = syncClient.getRaw("/v1/config/tags");
                    final String display = formatReview(weeklyJson, tagsJson);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView content = (TextView) findViewById(1001);
                            content.setText(display);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView content = (TextView) findViewById(1001);
                            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                            content.setText("\u52A0\u8F7D\u5931\u8D25\uFF1A" + msg
                                    + "\n\n\u8BF7\u68C0\u67E5 API \u5730\u5740\u4E0E Token");
                            Toast.makeText(ReviewActivity.this,
                                    "\u52A0\u8F7D\u5931\u8D25", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String formatReview(String weeklyJson, String tagsJson) {
        StringBuilder sb = new StringBuilder();

        try {
            JSONObject weekly = new JSONObject(weeklyJson);
            String weekStart = weekly.optString("week_start", "");
            String weekEnd = weekly.optString("week_end", "");
            int totalLiked = weekly.optInt("total_liked", 0);
            int totalSkipped = weekly.optInt("total_skipped", 0);

            sb.append("\u5468\u671F: ").append(weekStart).append(" \u2500 ").append(weekEnd).append("\n");
            sb.append("\u559C\u6B22: ").append(totalLiked)
              .append("   \u8DF3\u8FC7: ").append(totalSkipped).append("\n");
            sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n\n");

            JSONArray tags = weekly.optJSONArray("tags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    JSONObject tag = tags.optJSONObject(i);
                    if (tag == null) continue;
                    String name = tag.optString("name", "");
                    int likes = tag.optInt("likes", 0);
                    int skips = tag.optInt("skips", 0);
                    double weight = tag.optDouble("weight", 0);
                    String trend = tag.optString("trend", "");

                    sb.append("\u25a0 ").append(name).append("\n");
                    sb.append("  \u559C\u6B22 ").append(likes)
                      .append("  \u8DF3\u8FC7 ").append(skips)
                      .append("  \u6743\u91CD ").append(String.format("%.2f", weight))
                      .append("  ").append(trendArrow(trend)).append("\n");

                    JSONArray topItems = tag.optJSONArray("top_items");
                    if (topItems != null && topItems.length() > 0) {
                        sb.append("  \u70ED\u95E8:\n");
                        for (int j = 0; j < Math.min(3, topItems.length()); j++) {
                            JSONObject item = topItems.optJSONObject(j);
                            if (item == null) continue;
                            String title = item.optString("title", "");
                            if (title.length() > 40) title = title.substring(0, 40) + "\u2026";
                            sb.append("    \u00b7 ").append(title).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }

            // Tag weights from config/tags
            JSONObject tagsConfig = new JSONObject(tagsJson);
            JSONObject weights = tagsConfig.optJSONObject("tags");
            boolean coldStart = tagsConfig.optBoolean("cold_start", true);
            if (weights != null) {
                sb.append("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
                sb.append("\u5F53\u524D\u6807\u7B7E\u6743\u91CD");
                sb.append(coldStart ? " (\u51b7\u542F\u52a8)" : " (\u5df2\u5b66\u4e60)").append("\n");
                JSONArray keys = weights.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.optString(i);
                        double val = weights.optDouble(key, 0);
                        sb.append("  ").append(key).append(": ")
                          .append(String.format("%.2f", val)).append("\n");
                    }
                }
            }

            // Suggestions
            JSONArray suggestions = weekly.optJSONArray("suggestions");
            if (suggestions != null && suggestions.length() > 0) {
                sb.append("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
                sb.append("\u5EFA\u8BAE\n");
                for (int i = 0; i < suggestions.length(); i++) {
                    String s = suggestions.optString(i);
                    if (s != null && s.length() > 0) {
                        sb.append("  \u00b7 ").append(s).append("\n");
                    }
                }
            }

        } catch (Exception e) {
            sb.append("\u89E3\u6790\u5931\u8d25: ").append(e.getMessage());
        }

        return sb.toString();
    }

    private String trendArrow(String trend) {
        if (trend == null) return "";
        if ("up".equals(trend)) return "\u2191";
        if ("down".equals(trend)) return "\u2193";
        return "\u2014";
    }
}
