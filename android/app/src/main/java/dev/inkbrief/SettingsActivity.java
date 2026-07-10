package dev.inkbrief;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText apiUrlInput;
    private EditText tokenInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildUI();
        loadSettings();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // API URL
        TextView apiUrlLabel = new TextView(this);
        apiUrlLabel.setText("API \u670D\u52A1\u5668\u5730\u5740");
        apiUrlLabel.setTextSize(16);
        apiUrlLabel.setTextColor(Color.BLACK);
        apiUrlLabel.setPadding(0, 0, 0, pad / 2);
        root.addView(apiUrlLabel);

        apiUrlInput = new EditText(this);
        apiUrlInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        apiUrlInput.setTextSize(15);
        apiUrlInput.setPadding(0, 0, 0, pad);
        root.addView(apiUrlInput);

        // Token
        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("Token");
        tokenLabel.setTextSize(16);
        tokenLabel.setTextColor(Color.BLACK);
        tokenLabel.setPadding(0, 0, 0, pad / 2);
        root.addView(tokenLabel);

        tokenInput = new EditText(this);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setTextSize(15);
        tokenInput.setPadding(0, 0, 0, pad);
        root.addView(tokenInput);

        // Spacer
        TextView spacer = new TextView(this);
        spacer.setHeight(pad);
        root.addView(spacer);

        // Save button
        Button saveBtn = new Button(this);
        saveBtn.setText("\u4FDD\u5B58");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        root.addView(saveBtn);

        // Back button
        Button backBtn = new Button(this);
        backBtn.setText("\u8FD4\u56DE");
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(backBtn);

        setContentView(root);
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        apiUrlInput.setText(prefs.getString("api_url", "http://192.168.10.11:8720"));
        tokenInput.setText(prefs.getString("token", "dev-token"));
    }

    private void saveSettings() {
        String apiUrl = apiUrlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();

        if (apiUrl.length() == 0) {
            Toast.makeText(this, "\u8BF7\u8F93\u5165API\u5730\u5740", Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove trailing slash
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("api_url", apiUrl);
        editor.putString("token", token);
        editor.apply();

        Toast.makeText(this, "\u5DF2\u4FDD\u5B58", Toast.LENGTH_SHORT).show();
        finish();
    }
}
