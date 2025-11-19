package com.inulute.mediumunlocker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextInputEditText urlInput;
    private MaterialButton unlockButton;
    private MaterialButton aboutButton;
    private MaterialButton supportButton;
    private MaterialButton githubButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        handleIntent(getIntent());
    }

    private void initializeViews() {
        urlInput = findViewById(R.id.urlInput);
        unlockButton = findViewById(R.id.unlockButton);
        aboutButton = findViewById(R.id.aboutButton);
        supportButton = findViewById(R.id.supportButton);
        githubButton = findViewById(R.id.githubButton);
    }

    private void setupListeners() {
        unlockButton.setOnClickListener(v -> processUrl());

        aboutButton.setOnClickListener(v -> showAboutDialog());

        // Top bar buttons
        supportButton.setOnClickListener(v -> openUrl("https://support.inulute.com"));

        githubButton.setOnClickListener(v -> openUrl("https://github.com/inulute/medium-unlocker"));

        // Handle keyboard "Go" button
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                processUrl();
                return true;
            }
            return false;
        });

        // Auto-paste from clipboard if it contains a Medium URL
        tryAutoPasteFromClipboard();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open URL: " + url, e);
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        String type = intent.getType();
        Log.d(TAG, "Handling intent - Action: " + action + ", Type: " + type);

        // Handle URL shared from browser or other apps
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String url = data.toString();
                Log.d(TAG, "Received VIEW intent with URL: " + url);
                processAndOpenUrl(url);
            }
        }
        // Handle text/URL shared via share menu
        else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            Log.d(TAG, "Received SEND intent with text: " + sharedText);
            if (sharedText != null) {
                String url = extractUrl(sharedText);
                if (url != null && isMediumUrl(url)) {
                    Log.d(TAG, "Valid Medium URL found in shared text: " + url);
                    processAndOpenUrl(url);
                } else {
                    Log.w(TAG, "No valid Medium URL found in shared text");
                    urlInput.setText(sharedText);
                    Toast.makeText(this, "Please paste a valid Medium URL", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void tryAutoPasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String clipText = item.getText().toString();
                String url = extractUrl(clipText);
                if (url != null && isMediumUrl(url)) {
                    urlInput.setText(url);
                    urlInput.setSelection(url.length());
                }
            }
        }
    }

    private void processUrl() {
        String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
        Log.d(TAG, "Processing URL from input: " + url);

        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract URL if text contains other content
        String extractedUrl = extractUrl(url);
        if (extractedUrl != null) {
            Log.d(TAG, "Extracted URL: " + extractedUrl);
            url = extractedUrl;
        }

        if (!isMediumUrl(url)) {
            Log.w(TAG, "Not a Medium URL: " + url);
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }

        processAndOpenUrl(url);
    }

    private void processAndOpenUrl(String mediumUrl) {
        Log.d(TAG, "Processing Medium URL: " + mediumUrl);
        String freediumUrl = convertToFreedium(mediumUrl);
        Log.d(TAG, "Converted to Freedium URL: " + freediumUrl);

        Toast.makeText(this, "Opening article via Freedium...", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, WebViewActivity.class);
        intent.putExtra("url", freediumUrl);
        intent.putExtra("originalUrl", mediumUrl);
        startActivity(intent);

        // Clear the input for next use
        if (urlInput != null) {
            urlInput.setText("");
        }
    }

    private String convertToFreedium(String mediumUrl) {
        // Use Freedium.cfd - format: https://freedium.cfd/{medium-url}
        return "https://freedium.cfd/" + mediumUrl;
    }

    private boolean isMediumUrl(String url) {
        if (url == null || url.isEmpty()) return false;

        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("medium.com") ||
                lowerUrl.matches(".*://[a-zA-Z0-9-]+\\.medium\\.com.*");
    }

    private String extractUrl(String text) {
        if (text == null) return null;

        // Simple URL extraction - looks for http(s) URLs
        String urlPattern = "(https?://[^\\s]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // If no protocol, check if it starts with medium.com or subdomain
        if (text.toLowerCase().contains("medium.com")) {
            if (!text.startsWith("http")) {
                return "https://" + text;
            }
            return text;
        }

        return null;
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about_title))
                .setMessage(getString(R.string.about_message))
                .setPositiveButton("OK", null)
                .setNeutralButton("Share App", (dialog, which) -> shareApp())
                .show();
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Medium Unlocker");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out Medium Unlocker - Read Medium articles without restrictions!");
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
}
