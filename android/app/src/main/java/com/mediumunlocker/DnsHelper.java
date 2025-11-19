package com.inulute.mediumunlocker;

import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * DNS over HTTPS helper to bypass network provider blocking
 */
public class DnsHelper {
    private static final String TAG = "DnsHelper";
    private static List<DnsOverHttps> dnsServers;
    private static OkHttpClient okHttpClient;

    /**
     * Get OkHttpClient configured with DNS over HTTPS
     * Uses multiple DNS providers as fallback
     */
    public static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = buildClientWithDoh();
        }
        return okHttpClient;
    }

    private static OkHttpClient buildClientWithDoh() {
        // Create a bootstrap client (uses system DNS)
        OkHttpClient bootstrapClient = new OkHttpClient.Builder().build();

        // Initialize multiple DoH servers for fallback
        dnsServers = new ArrayList<>();

        // Cloudflare DoH
        try {
            DnsOverHttps cloudflare = new DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(HttpUrl.get("https://1.1.1.1/dns-query"))
                    .build();
            dnsServers.add(cloudflare);
            Log.d(TAG, "Added Cloudflare DoH (1.1.1.1)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Cloudflare DoH", e);
        }

        // Google DoH
        try {
            DnsOverHttps google = new DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(HttpUrl.get("https://8.8.8.8/dns-query"))
                    .build();
            dnsServers.add(google);
            Log.d(TAG, "Added Google DoH (8.8.8.8)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Google DoH", e);
        }

        // AdGuard DoH (alternative)
        try {
            DnsOverHttps adguard = new DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(HttpUrl.get("https://dns.adguard.com/dns-query"))
                    .build();
            dnsServers.add(adguard);
            Log.d(TAG, "Added AdGuard DoH");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AdGuard DoH", e);
        }

        // Use first available DoH server
        DnsOverHttps primaryDns = !dnsServers.isEmpty() ? dnsServers.get(0) : null;

        if (primaryDns != null) {
            // Create the main client with DoH
            return new OkHttpClient.Builder()
                    .dns(primaryDns)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
        } else {
            Log.e(TAG, "No DoH servers available, using system DNS");
            return bootstrapClient;
        }
    }

    /**
     * Resolve hostname using DNS over HTTPS with fallback
     * Tries multiple DoH servers if one fails
     */
    public static List<InetAddress> lookup(String hostname) throws UnknownHostException {
        UnknownHostException lastException = null;

        // Try each DoH server
        for (DnsOverHttps dnsServer : dnsServers) {
            try {
                List<InetAddress> addresses = dnsServer.lookup(hostname);
                Log.d(TAG, "DNS over HTTPS lookup successful for " + hostname + ": " + addresses);
                return addresses;
            } catch (UnknownHostException e) {
                Log.w(TAG, "DoH server failed for " + hostname + ", trying next...", e);
                lastException = e;
            }
        }

        // All DoH servers failed
        if (lastException != null) {
            Log.e(TAG, "All DoH servers failed for " + hostname);
            throw lastException;
        }

        throw new UnknownHostException("No DoH servers configured");
    }
}
