package com.inulute.mediumunlocker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        initializeViews();
        setupToolbar();
        setupWebView();
        setupButtons();
        loadUrl();
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
        tryProxyButton = findViewById(R.id.tryProxyButton);
        tryAlternativeButton = findViewById(R.id.tryAlternativeButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.inflateMenu(R.menu.webview_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_open_browser) {
                openInBrowser();
                return true;
            } else if (id == R.id.action_share) {
                shareArticle();
                return true;
            } else if (id == R.id.action_refresh) {
                webView.reload();
                return true;
            }
            return false;
        });
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Enable JavaScript and DOM storage
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Normal caching - use cache but validate with server for fresh content
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Rendering optimizations
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        // Allow mixed content for freedium.cfd
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Hardware acceleration for smooth rendering
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Desktop user agent for better content
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );

        // Fast WebViewClient - no interception overhead
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoading();
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideLoading();
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                String title = view.getTitle();
                if (title != null && !title.isEmpty() && !title.startsWith("http")) {
                    toolbar.setTitle(title);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    hideLoading();
                    
                    // Immediately hide WebView to prevent default error page
                    view.setVisibility(View.GONE);
                    
                    // Auto-retry with mirror if using primary domain
                    String url = request.getUrl().toString();
                    if (url.contains("freedium.cfd") && !url.contains("freedium-mirror.cfd")) {
                        Log.d(TAG, "Primary domain failed, switching to mirror...");
                        
                        // Show loading overlay instead of Toast
                        if (loadingOverlay != null) {
                            loadingOverlay.setVisibility(View.VISIBLE);
                            if (loadingText != null) {
                                loadingText.setText("Switching to mirror server...");
                            }
                        }
                        
                        String mirrorUrl = url.replace("freedium.cfd", "freedium-mirror.cfd");
                        view.loadUrl(mirrorUrl);
                        return;
                    }
                    
                    showError();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String url = error.getUrl();
                // Accept SSL for freedium.cfd
                if (url != null && url.contains("freedium.cfd")) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Keep freedium and medium navigation in WebView
                if (url.contains("freedium.cfd") || url.contains("freedium-mirror.cfd") || url.contains("medium.com")) {
                    return false;
                }

                // Open external links in browser
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL: " + url, e);
                }
                return true;
            }
        });

        // Progress updates
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    hideLoading();
                }
            }
        });
    }

    private void setupButtons() {
        retryButton.setOnClickListener(v -> {
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            if (currentUrl != null) {
                webView.loadUrl(currentUrl);
            }
        });

        // Hide proxy buttons - not needed for most users
        tryProxyButton.setVisibility(View.GONE);
        
        // Configure alternative button for mirror
        tryAlternativeButton.setText("Try Mirror Server");
        tryAlternativeButton.setVisibility(View.VISIBLE);
        tryAlternativeButton.setOnClickListener(v -> {
            if (webView.getUrl() != null) {
                String current = webView.getUrl();
                String newUrl;
                if (current.contains("freedium-mirror.cfd")) {
                    // If already on mirror, switch back to primary (toggle behavior)
                    newUrl = current.replace("freedium-mirror.cfd", "freedium.cfd");
                    if (loadingText != null) loadingText.setText("Switching to primary server...");
                } else {
                    // Switch to mirror
                    newUrl = current.replace("freedium.cfd", "freedium-mirror.cfd");
                    if (loadingText != null) loadingText.setText("Switching to mirror server...");
                }
                
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(newUrl);
            } else if (currentUrl != null) {
                // Fallback if WebView URL is null
                String newUrl = currentUrl.replace("freedium.cfd", "freedium-mirror.cfd");
                webView.loadUrl(newUrl);
            }
        });
        
        blockingInfoCard.setVisibility(View.GONE);
    }

    private void loadUrl() {
        Intent intent = getIntent();
        currentUrl = intent.getStringExtra("url");
        originalUrl = intent.getStringExtra("originalUrl");

        if (currentUrl != null && !currentUrl.isEmpty()) {
            webView.loadUrl(currentUrl);
        } else {
            showError();
        }
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
        if (url != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
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
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
