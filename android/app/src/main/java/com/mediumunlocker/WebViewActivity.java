package com.inulute.mediumunlocker;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class WebViewActivity extends AppCompatActivity {

    private static final String TAG = "WebViewActivity";
    private static final String PREFS_NAME = "MediumUnlockerPrefs";
    private static final String PREF_WEBVIEW_POPUP_SHOWN_VERSION = "webview_popup_shown_version";

    private HistoryManager historyManager;
    private MenuItem bookmarkMenuItem;
    private MenuItem forwardMenuItem;

    private WebView webView;
    private LinearProgressIndicator progressBar;
    private MaterialToolbar toolbar;
    private ScrollView errorLayout;
    private TextView errorMessage;
    private MaterialCardView blockingInfoCard;
    private TextView proxyStatusText;
    private MaterialButton retryButton;
    private MaterialButton tryProxyButton;
    private MaterialButton tryAlternativeButton;
    private android.widget.LinearLayout loadingOverlay;
    private TextView loadingText;

    private String currentUrl;
    private String originalUrl;
    private boolean positionRestored = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        historyManager = HistoryManager.getInstance(this);
        initializeViews();
        setupToolbar();
        setupWebView();
        setupButtons();
        loadUrl();
        showUpdateDialogIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveReadingPosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveReadingPosition();
    }

    private void saveReadingPosition() {
        if (originalUrl == null || originalUrl.isEmpty() || webView == null) return;
        if (!rememberPosition()) return;
        final String urlKey = originalUrl;
        webView.evaluateJavascript("window.scrollY", value -> {
            try {
                int y = (int) Double.parseDouble(value.trim());
                if (y > 0) historyManager.savePosition(urlKey, y);
            } catch (Exception ignored) { }
        });
    }

    private boolean rememberPosition() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(SettingsActivity.PREF_REMEMBER_POSITION, true);
    }

    private void showUpdateDialogIfNeeded() {
        Intent intent = getIntent();
        String updateVersion = intent != null ? intent.getStringExtra("update_version") : null;
        String updateUrl = intent != null ? intent.getStringExtra("update_url") : null;
        if (updateVersion == null || updateVersion.isEmpty() || updateUrl == null || updateUrl.isEmpty()) return;
        String shownVersion = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_WEBVIEW_POPUP_SHOWN_VERSION, "");
        if (updateVersion.equals(shownVersion)) return;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(PREF_WEBVIEW_POPUP_SHOWN_VERSION, updateVersion).apply();
        showUpdateDialog(updateVersion, updateUrl);
    }

    private void showUpdateDialog(String version, String url) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_update);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCancelable(true);
        ((TextView) dialog.findViewById(R.id.updateVersionText)).setText("v" + version + " is now available");
        dialog.findViewById(R.id.updateSkipButton).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.updateCancelButton).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.updateNowButton).setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void initializeViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        toolbar = findViewById(R.id.toolbar);
        errorLayout = findViewById(R.id.errorLayout);
        errorMessage = findViewById(R.id.errorMessage);
        blockingInfoCard = findViewById(R.id.blockingInfoCard);
        proxyStatusText = findViewById(R.id.proxyStatusText);
        retryButton = findViewById(R.id.retryButton);
        tryProxyButton = findViewById(R.id.tryProxyButton);
        tryAlternativeButton = findViewById(R.id.tryAlternativeButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.webview_menu);

        bookmarkMenuItem = toolbar.getMenu().findItem(R.id.action_bookmark);
        forwardMenuItem = toolbar.getMenu().findItem(R.id.action_forward);
        if (forwardMenuItem != null) forwardMenuItem.setVisible(false);

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_bookmark) { toggleBookmark(); return true; }
            else if (id == R.id.action_forward) { webView.goForward(); return true; }
            else if (id == R.id.action_open_browser) { openInBrowser(); return true; }
            else if (id == R.id.action_share) { shareArticle(); return true; }
            else if (id == R.id.action_refresh) { webView.reload(); return true; }
            return false;
        });
    }

    private void updateNavButtons() {
        if (forwardMenuItem != null) {
            forwardMenuItem.setVisible(webView != null && webView.canGoForward());
        }
    }

    private void toggleBookmark() {
        if (originalUrl == null || originalUrl.isEmpty()) return;
        String title = webView.getTitle() != null ? webView.getTitle() : "";
        String freediumUrl = webView.getUrl() != null ? webView.getUrl() : currentUrl;
        if (historyManager.isBookmarked(originalUrl)) {
            historyManager.removeBookmark(originalUrl);
            updateBookmarkIcon(false);
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
        } else {
            historyManager.addBookmark(title, originalUrl, freediumUrl != null ? freediumUrl : "");
            updateBookmarkIcon(true);
            Toast.makeText(this, "Bookmarked!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateBookmarkIcon(boolean bookmarked) {
        if (bookmarkMenuItem != null) {
            bookmarkMenuItem.setIcon(bookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Use text zoom from settings
        int textZoom = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(SettingsActivity.PREF_TEXT_ZOOM, 100);
        settings.setTextZoom(textZoom);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                positionRestored = false;
                showLoading();
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                updateNavButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideLoading();
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);

                String title = view.getTitle();
                if (title != null && !title.isEmpty() && !title.startsWith("http")) {
                    toolbar.setTitle(title);
                }

                // Save to history and update bookmark icon
                if (originalUrl != null && !originalUrl.isEmpty()) {
                    String pageTitle = (title != null && !title.isEmpty() && !title.startsWith("http")) ? title : "";
                    historyManager.saveToHistory(pageTitle, originalUrl, url != null ? url : "");
                    updateBookmarkIcon(historyManager.isBookmarked(originalUrl));
                }

                // Restore reading position (only on first load of this article)
                if (!positionRestored && rememberPosition() && originalUrl != null && !originalUrl.isEmpty()) {
                    positionRestored = true;
                    int savedY = historyManager.getPosition(originalUrl);
                    if (savedY > 0) {
                        webView.postDelayed(() ->
                            webView.evaluateJavascript("window.scrollTo({top:" + savedY + ",behavior:'smooth'})", null), 1200);
                    }
                }

                updateNavButtons();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    hideLoading();
                    view.setVisibility(View.GONE);
                    String url = request.getUrl().toString();
                    if (url.contains("freedium-mirror.cfd")) {
                        Log.d(TAG, "Mirror domain failed, switching to primary...");
                        if (loadingOverlay != null) {
                            loadingOverlay.setVisibility(View.VISIBLE);
                            if (loadingText != null) loadingText.setText("Switching to primary server...");
                        }
                        view.setVisibility(View.VISIBLE);
                        view.loadUrl(url.replace("freedium-mirror.cfd", "freedium.cfd"));
                        return;
                    }
                    showError();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String url = error.getUrl();
                if (url != null && (url.contains("freedium-mirror.cfd") || url.contains("freedium.cfd"))) handler.proceed();
                else handler.cancel();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("freedium.cfd") || url.contains("freedium-mirror.cfd") || url.contains("medium.com")) {
                    return false;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { Log.e(TAG, "Failed to open URL: " + url, e); }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) hideLoading();
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.isEmpty() && !title.startsWith("http")) {
                    toolbar.setTitle(title);
                    if (originalUrl != null && !originalUrl.isEmpty()) {
                        String currentFreediumUrl = view.getUrl() != null ? view.getUrl() : "";
                        historyManager.saveToHistory(title, originalUrl, currentFreediumUrl);
                    }
                }
            }
        });
    }

    private void setupButtons() {
        retryButton.setOnClickListener(v -> {
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            if (currentUrl != null) webView.loadUrl(currentUrl);
        });

        tryProxyButton.setVisibility(View.GONE);

        tryAlternativeButton.setText("Try Mirror Server");
        tryAlternativeButton.setVisibility(View.VISIBLE);
        tryAlternativeButton.setOnClickListener(v -> {
            if (webView.getUrl() != null) {
                String current = webView.getUrl();
                String newUrl;
                if (current.contains("freedium-mirror.cfd")) {
                    newUrl = current.replace("freedium-mirror.cfd", "freedium.cfd");
                    if (loadingText != null) loadingText.setText("Switching to primary server...");
                } else {
                    newUrl = current.replace("freedium.cfd", "freedium-mirror.cfd");
                    if (loadingText != null) loadingText.setText("Switching to mirror server...");
                }
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(newUrl);
            } else if (currentUrl != null) {
                webView.loadUrl(currentUrl.replace("freedium.cfd", "freedium-mirror.cfd"));
            }
        });

        blockingInfoCard.setVisibility(View.GONE);
    }

    private void loadUrl() {
        Intent intent = getIntent();
        currentUrl = intent.getStringExtra("url");
        originalUrl = intent.getStringExtra("originalUrl");
        if (currentUrl != null && !currentUrl.isEmpty()) webView.loadUrl(currentUrl);
        else showError();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showError() {
        errorLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        errorMessage.setText("Unable to load article. Please check your connection and try again.");
    }

    private void openInBrowser() {
        String url = webView.getUrl() != null ? webView.getUrl() : currentUrl;
        if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void shareArticle() {
        String url = originalUrl != null ? originalUrl : webView.getUrl();
        String title = webView.getTitle();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, "Share article"));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
