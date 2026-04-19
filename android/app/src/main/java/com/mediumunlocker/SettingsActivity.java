package com.inulute.mediumunlocker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MediumUnlockerPrefs";
    static final String PREF_TEXT_ZOOM = "text_zoom";
    static final String PREF_REMEMBER_POSITION = "remember_position";
    static final String PREF_MAX_HISTORY = "max_history";
    static final String PREF_HOME_FEED = "home_feed";

    private static final String[] FEED_LABELS = {"Recent Articles", "Bookmarks", "Both"};
    private static final String[] FEED_VALUES = {"history", "bookmarks", "both"};

    private SeekBar textZoomSeekBar;
    private TextView textZoomValue;
    private SwitchMaterial rememberPositionSwitch;
    private TextInputEditText maxHistoryInput;
    private TextView homeFeedValue;
    private String selectedFeed = "history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save_settings) {
                saveSettings();
                return true;
            }
            return false;
        });

        textZoomSeekBar = findViewById(R.id.textZoomSeekBar);
        textZoomValue = findViewById(R.id.textZoomValue);
        rememberPositionSwitch = findViewById(R.id.rememberPositionSwitch);
        maxHistoryInput = findViewById(R.id.maxHistoryInput);
        homeFeedValue = findViewById(R.id.homeFeedValue);

        loadSettings();
        setupZoomSeekBar();

        findViewById(R.id.textZoomReset).setOnClickListener(v -> {
            textZoomSeekBar.setProgress(50);
            textZoomValue.setText("100%");
        });

        View homeFeedRow = findViewById(R.id.homeFeedRow);
        if (homeFeedRow != null) {
            homeFeedRow.setOnClickListener(v -> showFeedPicker());
        }
    }

    private void showFeedPicker() {
        int current = feedIndex(selectedFeed);
        new AlertDialog.Builder(this)
                .setTitle("Show on Home Screen")
                .setSingleChoiceItems(FEED_LABELS, current, (dialog, which) -> {
                    selectedFeed = FEED_VALUES[which];
                    homeFeedValue.setText(FEED_LABELS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int feedIndex(String value) {
        for (int i = 0; i < FEED_VALUES.length; i++) {
            if (FEED_VALUES[i].equals(value)) return i;
        }
        return 0;
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        int zoom = prefs.getInt(PREF_TEXT_ZOOM, 100);
        textZoomSeekBar.setProgress(zoom - 50);
        textZoomValue.setText(zoom + "%");

        rememberPositionSwitch.setChecked(prefs.getBoolean(PREF_REMEMBER_POSITION, true));

        int maxHistory = prefs.getInt(PREF_MAX_HISTORY, 100);
        maxHistoryInput.setText(String.valueOf(maxHistory));

        selectedFeed = prefs.getString(PREF_HOME_FEED, "history");
        homeFeedValue.setText(FEED_LABELS[feedIndex(selectedFeed)]);
    }

    private void setupZoomSeekBar() {
        textZoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textZoomValue.setText((progress + 50) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void saveSettings() {
        int maxHistory = 100;
        String maxHistoryText = maxHistoryInput.getText() != null
                ? maxHistoryInput.getText().toString().trim() : "";
        if (!maxHistoryText.isEmpty()) {
            try {
                maxHistory = Integer.parseInt(maxHistoryText);
                if (maxHistory < 10) maxHistory = 10;
                if (maxHistory > 1000) maxHistory = 1000;
            } catch (NumberFormatException ignored) { }
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_TEXT_ZOOM, textZoomSeekBar.getProgress() + 50)
                .putBoolean(PREF_REMEMBER_POSITION, rememberPositionSwitch.isChecked())
                .putInt(PREF_MAX_HISTORY, maxHistory)
                .putString(PREF_HOME_FEED, selectedFeed)
                .apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
