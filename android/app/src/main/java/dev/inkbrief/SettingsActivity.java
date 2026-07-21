package dev.inkbrief;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.inkbrief.sync.SyncClient;

public class SettingsActivity extends Activity {

    private EditText apiUrlInput;
    private EditText tokenInput;
    private TextView hintText;

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

        TextView title = new TextView(this);
        title.setText("\u8BBE\u7F6E");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, pad);
        root.addView(title);

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

        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("Token\uff08\u4EC5 ASCII\uff0c\u9ED8\u8BA4 dev-token\uff09");
        tokenLabel.setTextSize(16);
        tokenLabel.setTextColor(Color.BLACK);
        tokenLabel.setPadding(0, 0, 0, pad / 2);
        root.addView(tokenLabel);

        // Visible text on e-ink so wrong IME input is obvious (was: password).
        tokenInput = new EditText(this);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tokenInput.setTextSize(15);
        tokenInput.setPadding(0, 0, 0, pad / 2);
        root.addView(tokenInput);

        hintText = new TextView(this);
        hintText.setTextSize(13);
        hintText.setTextColor(Color.parseColor("#555555"));
        hintText.setPadding(0, 0, 0, pad);
        hintText.setText("\u82E5\u51FA\u73B0\u9274\u6743\u5931\u8D25\uff0c\u70B9\u300C\u6062\u590D\u9ED8\u8BA4\u300D");
        root.addView(hintText);

        Button saveBtn = new Button(this);
        saveBtn.setText("\u4FDD\u5B58");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        root.addView(saveBtn);

        Button resetBtn = new Button(this);
        resetBtn.setText("\u6062\u590D\u9ED8\u8BA4 Token");
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tokenInput.setText(SyncClient.DEFAULT_TOKEN);
                Toast.makeText(SettingsActivity.this,
                        "\u5DF2\u586B\u5165 " + SyncClient.DEFAULT_TOKEN,
                        Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(resetBtn);

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
        apiUrlInput.setText(prefs.getString("api_url", SyncClient.DEFAULT_API_URL));
        String token = prefs.getString("token", SyncClient.DEFAULT_TOKEN);
        if (token == null || token.length() == 0 || !SyncClient.isTokenSafe(token)) {
            token = SyncClient.DEFAULT_TOKEN;
        }
        tokenInput.setText(token.trim());
    }

    private void saveSettings() {
        String apiUrl = apiUrlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();

        if (apiUrl.length() == 0) {
            Toast.makeText(this, "\u8BF7\u8F93\u5165API\u5730\u5740", Toast.LENGTH_SHORT).show();
            return;
        }
        if (token.length() == 0) {
            token = SyncClient.DEFAULT_TOKEN;
        }
        if (!SyncClient.isTokenSafe(token)) {
            Toast.makeText(this,
                    "Token \u53EA\u80FD\u7528\u82F1\u6587/\u6570\u5B57/\u7B26\u53F7\uff0c\u5DF2\u6062\u590D\u9ED8\u8BA4",
                    Toast.LENGTH_LONG).show();
            token = SyncClient.DEFAULT_TOKEN;
            tokenInput.setText(token);
        }

        SyncClient.saveSettings(this, apiUrl, token);
        Toast.makeText(this, "\u5DF2\u4FDD\u5B58", Toast.LENGTH_SHORT).show();
        finish();
    }
}
