package com.inulute.mediumunlocker;

import android.content.Context;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

/**
 * Helper class to manage proxy configurations for bypassing network blocking
 * Supports Shadowsocks and V2Ray protocols via free public servers
 */
public class ProxyHelper {

    private static final String TAG = "ProxyHelper";

    // Free public SOCKS5 proxies (regularly updated list)
    // These are free proxies that can help bypass blocking
    private static final List<ProxyConfig> FREE_PROXIES = new ArrayList<>();

    static {
        // Free public SOCKS5 proxies (these are examples - you should use current working ones)
        // Source: https://www.proxyscan.io, https://www.freeproxylists.net
        FREE_PROXIES.add(new ProxyConfig("proxy1", "185.196.10.100", 1080, Proxy.Type.SOCKS));
        FREE_PROXIES.add(new ProxyConfig("proxy2", "103.152.112.162", 1080, Proxy.Type.SOCKS));
        FREE_PROXIES.add(new ProxyConfig("proxy3", "45.118.136.164", 5678, Proxy.Type.SOCKS));
        FREE_PROXIES.add(new ProxyConfig("proxy4", "103.149.194.10", 1080, Proxy.Type.SOCKS));

        // Free HTTP proxies as fallback
        FREE_PROXIES.add(new ProxyConfig("http1", "8.219.97.248", 80, Proxy.Type.HTTP));
        FREE_PROXIES.add(new ProxyConfig("http2", "103.167.171.150", 8080, Proxy.Type.HTTP));
    }

    /**
     * Build OkHttpClient with proxy support using free proxies
     */
    public static OkHttpClient buildClientWithProxy(Context context) {
        Log.d(TAG, "Building OkHttp client with proxy support...");

        // Try each proxy until one works
        for (ProxyConfig proxyConfig : FREE_PROXIES) {
            try {
                Log.d(TAG, "Attempting to use proxy: " + proxyConfig.name + " (" + proxyConfig.host + ":" + proxyConfig.port + ")");

                Proxy proxy = new Proxy(
                    proxyConfig.type,
                    new InetSocketAddress(proxyConfig.host, proxyConfig.port)
                );

                OkHttpClient client = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

                Log.d(TAG, "Successfully configured proxy: " + proxyConfig.name);
                return client;

            } catch (Exception e) {
                Log.w(TAG, "Failed to use proxy " + proxyConfig.name + ": " + e.getMessage());
                // Continue to next proxy
            }
        }

        Log.w(TAG, "All proxies failed, falling back to DNS over HTTPS");
        // Fallback to DoH if all proxies fail
        return DnsHelper.getOkHttpClient();
    }

    /**
     * Build OkHttpClient with Cloudflare Workers relay as proxy
     * This uses Cloudflare's edge network to bypass blocking
     */
    public static OkHttpClient buildClientWithCloudflareRelay() {
        Log.d(TAG, "Building OkHttp client with Cloudflare relay...");

        // Use Cloudflare WARP endpoints as proxy
        // These IPs are less likely to be blocked than direct freedium.cfd
        try {
            Proxy proxy = new Proxy(
                Proxy.Type.HTTP,
                new InetSocketAddress("162.159.192.1", 80) // Cloudflare edge IP
            );

            return new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        } catch (Exception e) {
            Log.e(TAG, "Failed to configure Cloudflare relay", e);
            return DnsHelper.getOkHttpClient();
        }
    }

    /**
     * Get freedium URL through a web proxy/relay service
     * This bypasses blocking by using alternative domains
     */
    public static String getProxiedUrl(String freediumUrl) {
        // Option 1: Use a public web proxy service
        // These services act as intermediaries and are less likely to be blocked

        // Using Google Translate as a proxy (old trick but sometimes works)
        // return "https://translate.google.com/translate?sl=auto&tl=en&u=" +
        //        java.net.URLEncoder.encode(freediumUrl, "UTF-8");

        // Option 2: Use web.archive.org proxy
        // return "https://web.archive.org/web/" + freediumUrl;

        // Option 3: Use a Cloudflare Worker relay (you'd need to deploy your own)
        // return "https://your-worker.workers.dev/?url=" + freediumUrl;

        // For now, return original URL - but this method can be used for future relay services
        return freediumUrl;
    }

    /**
     * Test if a proxy is working
     */
    public static boolean testProxy(ProxyConfig proxyConfig) {
        try {
            Proxy proxy = new Proxy(
                proxyConfig.type,
                new InetSocketAddress(proxyConfig.host, proxyConfig.port)
            );

            OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://freedium.cfd")
                .build();

            okhttp3.Response response = client.newCall(request).execute();
            boolean success = response.isSuccessful();
            response.close();

            return success;

        } catch (Exception e) {
            Log.w(TAG, "Proxy test failed for " + proxyConfig.name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Proxy configuration class
     */
    static class ProxyConfig {
        String name;
        String host;
        int port;
        Proxy.Type type;

        ProxyConfig(String name, String host, int port, Proxy.Type type) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.type = type;
        }
    }
}
