package dev.inkbrief.data;

import org.json.JSONObject;
import org.json.JSONException;

public class Card {

    private String id;
    private int position;
    private String tag;
    private String title;
    private String source;
    private double aiScore;
    private String summary;
    private String url;
    private String reason;
    private String status;
    private String date;
    private long cachedAt;

    public Card() {
        this.status = "pending";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public double getAiScore() { return aiScore; }
    public void setAiScore(double aiScore) { this.aiScore = aiScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }

    public boolean isPending() {
        return status == null || status.length() == 0
                || "pending".equals(status) || "new".equals(status);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id != null ? id : "");
            json.put("position", position);
            json.put("tag", tag != null ? tag : "");
            json.put("title", title != null ? title : "");
            json.put("source", source != null ? source : "");
            json.put("ai_score", aiScore);
            json.put("summary", summary != null ? summary : "");
            json.put("url", url != null ? url : "");
            json.put("reason", reason != null ? reason : "");
            json.put("status", status != null ? status : "pending");
            json.put("date", date != null ? date : "");
            json.put("cached_at", cachedAt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Card fromJson(JSONObject obj) {
        Card card = new Card();
        card.setId(obj.optString("id", ""));
        card.setPosition(obj.optInt("position", 0));
        card.setTag(obj.optString("tag", ""));
        card.setTitle(obj.optString("title", ""));
        card.setSource(obj.optString("source", ""));
        card.setAiScore(obj.optDouble("ai_score", 0.0));
        card.setSummary(obj.optString("summary", ""));
        card.setUrl(obj.optString("url", ""));
        card.setReason(obj.optString("reason", ""));
        String st = obj.optString("status", "pending");
        if (st == null || st.length() == 0 || "new".equals(st)) {
            st = "pending";
        }
        card.setStatus(st);
        card.setDate(obj.optString("date", ""));
        card.setCachedAt(obj.optLong("cached_at", 0));
        return card;
    }
}
