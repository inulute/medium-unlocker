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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

    private String currentUrl;
    private String originalUrl;
    private boolean isDnsBlocked = false;
    private boolean useProxy = false;
    private int proxyAttempts = 0;

    // OkHttp client with DNS over HTTPS or proxy to bypass network blocking
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // Initialize DNS over HTTPS client
        okHttpClient = DnsHelper.getOkHttpClient();

        initializeViews();
        setupToolbar();
        setupWebView();
        setupProxyButtons();
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
        tryAlternativeButton = findViewById(R.id.tryAlternativeButton);
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

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Enable caching for better performance
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Improve rendering
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Better text rendering
        settings.setTextZoom(100);
        settings.setMinimumFontSize(8);

        // Allow mixed content (for freedium.cfd to load Medium resources)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Security settings
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Enable safe browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // Set user agent to avoid mobile redirects
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );

        // WebViewClient for handling page loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Page started loading: " + url);
                showLoading();
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
                hideLoading();
                String title = view.getTitle();
                if (title != null && !title.isEmpty()) {
                    toolbar.setTitle(title);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    String errorMessage = "Error loading page";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        errorMessage = error.getDescription().toString();
                    }
                    Log.e(TAG, "Error loading URL: " + request.getUrl() + " - " + errorMessage);
                    hideLoading();
                    showError();
                    Toast.makeText(WebViewActivity.this,
                        "Failed to load: " + errorMessage,
                        Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // For freedium.cfd, proceed despite SSL errors
                // This is necessary because freedium.cfd may use certificates not in Android's trust store
                String url = error.getUrl();
                Log.w(TAG, "SSL Error for URL: " + url + " - " + error.toString());

                if (url != null && url.contains("freedium.cfd")) {
                    Log.d(TAG, "Accepting SSL certificate for freedium.cfd");
                    handler.proceed(); // Bypass SSL error for freedium.cfd
                } else {
                    Log.e(TAG, "SSL Error for non-freedium domain, cancelling");
                    handler.cancel(); // Cancel for other domains
                    super.onReceivedSslError(view, handler, error);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Intercept freedium.cfd requests and use DNS over HTTPS
                if (url.contains("freedium.cfd")) {
                    Log.d(TAG, "Intercepting request for freedium.cfd: " + url);
                    return fetchViaDoH(url);
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);

                // Keep navigation within freedium and medium domains
                if (url.contains("freedium.cfd") || url.contains("medium.com")) {
                    return false; // Let WebView handle it
                }

                // Open external links in browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open URL in browser: " + url, e);
                    Toast.makeText(WebViewActivity.this,
                        "Failed to open link",
                        Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // WebChromeClient for progress updates
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                }
            }
        });

        // Retry button
        retryButton.setOnClickListener(v -> {
            Log.d(TAG, "Retrying to load URL: " + currentUrl);
            isDnsBlocked = false; // Reset blocking flag
            useProxy = false; // Reset proxy flag
            proxyAttempts = 0;
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            // Reinitialize with DoH
            okHttpClient = DnsHelper.getOkHttpClient();
            if (currentUrl != null) {
                webView.loadUrl(currentUrl);
            } else {
                webView.reload();
            }
        });
    }

    private void setupProxyButtons() {
        // Try Proxy Servers - Use free SOCKS5/HTTP proxies
        tryProxyButton.setOnClickListener(v -> {
            Log.d(TAG, "Attempting to use proxy servers...");
            useProxy = true;
            proxyAttempts++;

            // Switch to proxy client
            okHttpClient = ProxyHelper.buildClientWithProxy(this);

            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);

            Toast.makeText(this, "Trying proxy servers...", Toast.LENGTH_SHORT).show();

            if (currentUrl != null) {
                webView.loadUrl(currentUrl);
            }
        });

        // Try Alternative Method - Use Cloudflare relay
        tryAlternativeButton.setOnClickListener(v -> {
            Log.d(TAG, "Attempting alternative method (Cloudflare relay)...");
            useProxy = true;

            // Switch to Cloudflare relay client
            okHttpClient = ProxyHelper.buildClientWithCloudflareRelay();

            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);

            Toast.makeText(this, "Trying alternative method...", Toast.LENGTH_SHORT).show();

            if (currentUrl != null) {
                webView.loadUrl(currentUrl);
            }
        });
    }

    private void loadUrl() {
        Intent intent = getIntent();
        currentUrl = intent.getStringExtra("url");
        originalUrl = intent.getStringExtra("originalUrl");

        Log.d(TAG, "Loading URL: " + currentUrl);
        Log.d(TAG, "Original URL: " + originalUrl);

        if (currentUrl != null && !currentUrl.isEmpty()) {
            Toast.makeText(this, "Loading via freedium.cfd...", Toast.LENGTH_SHORT).show();

            // Clear cache for fresh load
            webView.clearCache(false);

            webView.loadUrl(currentUrl);
        } else {
            Log.e(TAG, "No URL provided");
            Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show();
            showError();
        }
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showError() {
        errorLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        // Show blocking info and proxy options if DNS is blocked
        if (isDnsBlocked) {
            blockingInfoCard.setVisibility(View.VISIBLE);
            tryProxyButton.setVisibility(View.VISIBLE);
            tryAlternativeButton.setVisibility(View.VISIBLE);
            errorMessage.setText("Unable to connect to freedium.cfd due to network restrictions.");

            // Update proxy status text
            if (useProxy) {
                proxyStatusText.setText("⚠️ Proxy method failed. Try alternative or check your connection.");
            } else {
                proxyStatusText.setText("⚙️ Network blocking detected. Try proxy bypass methods below.");
            }
        } else {
            blockingInfoCard.setVisibility(View.GONE);
            tryProxyButton.setVisibility(View.GONE);
            tryAlternativeButton.setVisibility(View.GONE);
            errorMessage.setText("Oops! Something went wrong loading the article. Please try again.");
        }
    }

    private void openInBrowser() {
        String url = webView.getUrl() != null ? webView.getUrl() : currentUrl;
        if (url != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
    }

    private void shareArticle() {
        String url = originalUrl != null ? originalUrl : webView.getUrl();
        String title = webView.getTitle();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, "Share article via"));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Fetch URL using DNS over HTTPS to bypass network provider blocking
     */
    private WebResourceResponse fetchViaDoH(String url) {
        try {
            Log.d(TAG, "Fetching via DoH: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build();

            Response response = okHttpClient.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String contentType = response.header("Content-Type", "text/html");
                String encoding = response.header("Content-Encoding", "utf-8");

                Log.d(TAG, "Successfully fetched via DoH: " + url + " (Content-Type: " + contentType + ")");

                return new WebResourceResponse(
                        contentType.split(";")[0], // MIME type
                        encoding,
                        response.body().byteStream()
                );
            } else {
                Log.e(TAG, "Failed to fetch via DoH: " + url + " (HTTP " + response.code() + ": " + response.message() + ")");
            }
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "DNS RESOLUTION FAILED for freedium.cfd - Network provider is blocking DNS!", e);
            isDnsBlocked = true; // Mark that DNS blocking was detected
            runOnUiThread(() -> {
                if (!useProxy) {
                    Toast.makeText(WebViewActivity.this,
                        "Network blocking detected. Trying proxy bypass...",
                        Toast.LENGTH_LONG).show();
                    // Automatically try proxy on first blocking detection
                    autoTryProxy();
                } else {
                    Toast.makeText(WebViewActivity.this,
                        "Proxy failed. Try alternative method.",
                        Toast.LENGTH_LONG).show();
                }
            });
        } catch (javax.net.ssl.SSLException e) {
            Log.e(TAG, "SSL/TLS Error - Possible SNI blocking or certificate issue", e);
            isDnsBlocked = true; // SNI blocking detected
            runOnUiThread(() -> {
                if (!useProxy && proxyAttempts == 0) {
                    autoTryProxy();
                }
            });
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Connection timeout - freedium.cfd may be blocked at IP level", e);
            isDnsBlocked = true; // IP-level blocking detected
            runOnUiThread(() -> {
                if (!useProxy && proxyAttempts == 0) {
                    autoTryProxy();
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Network error fetching: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            // Generic network errors might not be blocking, so don't set isDnsBlocked
        }

        return null; // Fall back to WebView's default behavior
    }

    /**
     * Automatically try proxy bypass when blocking is detected
     */
    private void autoTryProxy() {
        Log.d(TAG, "Auto-attempting proxy bypass...");
        useProxy = true;
        proxyAttempts++;

        // Switch to proxy client automatically
        okHttpClient = ProxyHelper.buildClientWithProxy(this);

        // Reload the page with proxy
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (currentUrl != null) {
                Log.d(TAG, "Reloading with proxy: " + currentUrl);
                webView.loadUrl(currentUrl);
            }
        }, 1000); // Small delay to show toast message
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
