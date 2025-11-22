package com.inulute.mediumunlocker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Primary: Shields.io (no rate limit)
    private static final String SHIELDS_API_URL = "https://img.shields.io/github/v/release/inulute/medium-unlocker.json";
    // Backup: GitHub API (has rate limit)
    private static final String GITHUB_API_URL = "https://api.github.com/repos/inulute/medium-unlocker/releases/latest";
    private static final String GITHUB_RELEASES_URL = "https://github.com/inulute/medium-unlocker/releases/latest";

    private static final String PREFS_NAME = "MediumUnlockerPrefs";
    private static final String PREF_SKIP_VERSION = "skip_version";
    private static final String PREF_POPUP_SHOWN_VERSION = "popup_shown_version";
    private static final String PREF_CACHED_VERSION = "cached_latest_version";
    private static final String PREF_LAST_CHECK = "last_update_check";
    private static final long UPDATE_CHECK_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours

    private TextInputEditText urlInput;
    private MaterialButton unlockButton;
    private MaterialButton aboutButton;
    private MaterialButton supportButton;
    private MaterialButton githubButton;
    private MaterialButton updateButton;

    private ExecutorService executor;

    // Pending update info
    private String pendingUpdateVersion = null;
    private String pendingUpdateUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor();

        initializeViews();
        setupListeners();
        handleIntent(getIntent());

        // Check for updates in background
        checkForUpdates();
    }

    private void initializeViews() {
        urlInput = findViewById(R.id.urlInput);
        unlockButton = findViewById(R.id.unlockButton);
        aboutButton = findViewById(R.id.aboutButton);
        supportButton = findViewById(R.id.supportButton);
        githubButton = findViewById(R.id.githubButton);
        updateButton = findViewById(R.id.updateButton);
    }

    private void setupListeners() {
        unlockButton.setOnClickListener(v -> processUrl());

        aboutButton.setOnClickListener(v -> showAboutDialog());

        // Top bar buttons
        updateButton.setOnClickListener(v -> showUpdateDialog());

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

        String urlPattern = "(https?://[^\\s]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        if (text.toLowerCase().contains("medium.com")) {
            if (!text.startsWith("http")) {
                return "https://" + text;
            }
            return text;
        }

        return null;
    }

    // ==================== Update Checker ====================

    private void checkForUpdates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
        long now = System.currentTimeMillis();
        String currentVersion = getAppVersion();

        // Check if we should use cached version or fetch new
        if (now - lastCheck < UPDATE_CHECK_INTERVAL) {
            // Use cached version
            String cachedVersion = prefs.getString(PREF_CACHED_VERSION, "");
            Log.d(TAG, "Using cached version: " + cachedVersion);

            if (!cachedVersion.isEmpty() && isNewerVersion(currentVersion, cachedVersion)) {
                String skipVersion = prefs.getString(PREF_SKIP_VERSION, "");
                if (!cachedVersion.equals(skipVersion)) {
                    pendingUpdateVersion = cachedVersion;
                    pendingUpdateUrl = GITHUB_RELEASES_URL;
                    updateButton.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Update button visible (from cache)");
                }
            }
            return;
        }

        // Fetch from network
        Log.d(TAG, "Starting update check from network...");

        executor.execute(() -> {
            try {
                Log.d(TAG, "Current app version: " + currentVersion);

                // Try shields.io first (no rate limit)
                String latestVersion = fetchVersionFromShields();

                // Fallback to GitHub API if shields.io fails
                if (latestVersion == null) {
                    Log.d(TAG, "Shields.io failed, trying GitHub API...");
                    latestVersion = fetchVersionFromGitHub();
                }

                if (latestVersion != null) {
                    Log.d(TAG, "Latest version from remote: " + latestVersion);

                    // Cache the response
                    prefs.edit()
                        .putString(PREF_CACHED_VERSION, latestVersion)
                        .putLong(PREF_LAST_CHECK, now)
                        .apply();

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        String skipVersion = prefs.getString(PREF_SKIP_VERSION, "");
                        String popupShownVersion = prefs.getString(PREF_POPUP_SHOWN_VERSION, "");
                        Log.d(TAG, "New version available! Current: " + currentVersion + ", Latest: " + latestVersion);

                        if (!latestVersion.equals(skipVersion)) {
                            pendingUpdateVersion = latestVersion;
                            pendingUpdateUrl = GITHUB_RELEASES_URL;

                            final boolean shouldShowPopup = !latestVersion.equals(popupShownVersion);
                            final String versionToSave = latestVersion;

                            runOnUiThread(() -> {
                                updateButton.setVisibility(View.VISIBLE);
                                Log.d(TAG, "Update button now visible");

                                // Show popup only once per version
                                if (shouldShowPopup) {
                                    showUpdateDialog();
                                    // Mark popup as shown for this version
                                    prefs.edit()
                                        .putString(PREF_POPUP_SHOWN_VERSION, versionToSave)
                                        .apply();
                                }
                            });
                        }
                    } else {
                        Log.d(TAG, "App is up to date");
                    }
                } else {
                    Log.e(TAG, "Failed to fetch version from all sources");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
            }
        });
    }

    private String fetchVersionFromShields() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(SHIELDS_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Shields.io response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "Shields.io response: " + response.toString());

                JSONObject json = new JSONObject(response.toString());
                String version = json.getString("value");
                // Clean version string (remove 'v' prefix if present)
                version = version.replace("v", "").trim();
                Log.d(TAG, "Parsed version from shields.io: " + version);
                return version;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch from shields.io", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private String fetchVersionFromGitHub() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(GITHUB_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "GitHub API response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String tagName = json.getString("tag_name");
                // Clean version string
                tagName = tagName.replace("v", "").trim();
                Log.d(TAG, "Parsed version from GitHub: " + tagName);
                return tagName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch from GitHub API", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0";
        }
    }

    private boolean isNewerVersion(String current, String latest) {
        try {
            // Handle null or empty
            if (current == null || latest == null || current.isEmpty() || latest.isEmpty()) {
                return false;
            }

            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");

            int maxLength = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < maxLength; i++) {
                int currentNum = 0;
                int latestNum = 0;

                if (i < currentParts.length) {
                    String part = currentParts[i].replaceAll("[^0-9]", "");
                    if (!part.isEmpty()) currentNum = Integer.parseInt(part);
                }
                if (i < latestParts.length) {
                    String part = latestParts[i].replaceAll("[^0-9]", "");
                    if (!part.isEmpty()) latestNum = Integer.parseInt(part);
                }

                if (latestNum > currentNum) return true;
                if (latestNum < currentNum) return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error comparing versions: " + current + " vs " + latest, e);
        }
        return false;
    }

    private void showUpdateDialog() {
        if (pendingUpdateVersion == null || pendingUpdateUrl == null) {
            Toast.makeText(this, "No update available", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_update);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.setCancelable(true);

        // Set version text
        TextView versionText = dialog.findViewById(R.id.updateVersionText);
        versionText.setText("v" + pendingUpdateVersion + " is now available");

        // Skip button (left)
        MaterialButton skipButton = dialog.findViewById(R.id.updateSkipButton);
        skipButton.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_SKIP_VERSION, pendingUpdateVersion)
                    .apply();
            updateButton.setVisibility(View.GONE);
            pendingUpdateVersion = null;
            pendingUpdateUrl = null;
            dialog.dismiss();
        });

        // Cancel button
        MaterialButton cancelButton = dialog.findViewById(R.id.updateCancelButton);
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Update button
        MaterialButton updateNowButton = dialog.findViewById(R.id.updateNowButton);
        updateNowButton.setOnClickListener(v -> {
            openUrl(pendingUpdateUrl);
            dialog.dismiss();
        });

        dialog.show();
    }

    // ==================== About Dialog ====================

    private void showAboutDialog() {
        String version = getAppVersion();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.setCancelable(true);

        // Set version text
        TextView versionText = dialog.findViewById(R.id.aboutVersion);
        versionText.setText("Version " + version);

        // GitHub button
        MaterialButton githubButton = dialog.findViewById(R.id.aboutGithubButton);
        githubButton.setOnClickListener(v -> {
            openUrl("https://github.com/inulute/medium-unlocker");
            dialog.dismiss();
        });

        // Share button
        MaterialButton shareButton = dialog.findViewById(R.id.aboutShareButton);
        shareButton.setOnClickListener(v -> {
            shareApp();
            dialog.dismiss();
        });

        // Close button
        MaterialButton closeButton = dialog.findViewById(R.id.aboutCloseButton);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Medium Unlocker");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out Medium Unlocker - Read Medium articles without restrictions!\n\nhttps://github.com/inulute/medium-unlocker");
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
