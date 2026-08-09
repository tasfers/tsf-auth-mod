package com.tasfers.tsfauth;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class TsfAuthPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("TsfAuth-prelaunch");
    public static String activeHostname = "";
    private static boolean isFetching = false;
    private static long lastFetchTime = 0;
    private static final long RETRY_INTERVAL_MS = 30000; // 30 seconds

    public static String getAuthHost() {
        return getAuthHost(false);
    }

    public static String getAuthHost(boolean forceSync) {
        if (activeHostname != null && !activeHostname.isEmpty()) {
            return activeHostname;
        }

        // 1. Try local config file override or auto-saved host
        try {
            java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir();
            
            // Check tsf_auth_host.txt (manual override)
            java.nio.file.Path hostFile = configDir.resolve("tsf_auth_host.txt");
            if (java.nio.file.Files.exists(hostFile)) {
                String line = java.nio.file.Files.readString(hostFile).trim();
                if (!line.isEmpty()) {
                    activeHostname = line.replace("https://", "").replace("http://", "");
                    LOGGER.info("Using local override auth host: " + activeHostname);
                    return activeHostname;
                }
            }

            // Check tsf_auth_hostname.txt (saved host from UI login)
            java.nio.file.Path autoSavedHostFile = configDir.resolve("tsf_auth_hostname.txt");
            if (java.nio.file.Files.exists(autoSavedHostFile)) {
                String line = java.nio.file.Files.readString(autoSavedHostFile).trim();
                if (!line.isEmpty()) {
                    activeHostname = line.replace("https://", "").replace("http://", "");
                    LOGGER.info("Using saved auth host: " + activeHostname);
                    return activeHostname;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read local auth host override/saved host", e);
        }

        if (forceSync) {
            try {
                LOGGER.info("Attempting synchronous fetch of active auth host from remote...");
                java.net.URL url = new java.net.URL("https://gist.githubusercontent.com/towux/d9053e87d2a85c7b6c99dd429a46ec96/raw/tsf_auth_hostname");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            activeHostname = line.replace("https://", "").replace("http://", "");
                            LOGGER.info("Fetched active auth host from remote: " + activeHostname);
                            return activeHostname;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch auth host from remote, using fallback: " + e.getMessage());
            }
        } else {
            triggerAsyncFetch();
        }

        // Fallback to BuildConstants compiled host but do NOT cache it in activeHostname permanently
        return BuildConstants.getH();
    }

    private static synchronized void triggerAsyncFetch() {
        if (isFetching) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < RETRY_INTERVAL_MS) {
            return;
        }
        isFetching = true;
        lastFetchTime = now;

        Thread fetchThread = new Thread(() -> {
            try {
                LOGGER.info("Attempting to fetch active auth host from remote...");
                java.net.URL url = new java.net.URL("https://gist.githubusercontent.com/towux/d9053e87d2a85c7b6c99dd429a46ec96/raw/tsf_auth_hostname");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            activeHostname = line.replace("https://", "").replace("http://", "");
                            LOGGER.info("Successfully fetched active auth host from remote: " + activeHostname);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch auth host from remote: " + e.getMessage());
            } finally {
                synchronized (TsfAuthPreLaunch.class) {
                    isFetching = false;
                }
            }
        }, "TsfAuth-HostFetcher");
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    @Override
    public void onPreLaunch() {
        String existingIgnored = System.getProperty("authlibinjector.ignoredPackages", "");
        String newIgnored = existingIgnored.isEmpty() ? "com.tasfers.tsfauth" : existingIgnored + ",com.tasfers.tsfauth";
        System.setProperty("authlibinjector.ignoredPackages", newIgnored);

        String hostname = getAuthHost(true);

        LOGGER.info("Attempting to attach authlib-injector for host: " + hostname);
        boolean success = AuthlibInjectorManager.inject(hostname);
        if (success) {
            LOGGER.info("Successfully attached authlib-injector in PreLaunch!");
        } else {
            LOGGER.error("Failed to attach authlib-injector in PreLaunch.");
        }
    }
}
