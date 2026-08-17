package com.tasfers.tsfauth;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class TsfAuthPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("TsfAuth-prelaunch");
    public static volatile String activeHostname = "";
    private static volatile String lastEtag = "";
    private static volatile long lastFetchTime = 0;
    private static volatile boolean isFetching = false;
    private static final long COOLDOWN_MS = 15_000;
    private static final long AUTO_REFRESH_INTERVAL_MS = 60_000;
    private static final String REMOTE_HOST_URL = "https://gist.githubusercontent.com/towux/d9053e87d2a85c7b6c99dd429a46ec96/raw/tsf_auth_hostname";

    private static final java.util.concurrent.ScheduledExecutorService REFRESH_SCHEDULER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TsfAuth-HostRefresher");
        t.setDaemon(true);
        return t;
    });

    static {
        REFRESH_SCHEDULER.scheduleWithFixedDelay(() -> triggerAsyncFetch(false), 60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    public static String getAuthHost() {
        return getAuthHost(false);
    }

    public static String getAuthHost(boolean forceSync) {
        try {
            java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir();
            java.nio.file.Path hostFile = configDir.resolve("tsf_auth_host.txt");
            if (Files.exists(hostFile)) {
                String line = Files.readString(hostFile).trim();
                if (!line.isEmpty()) {
                    String override = line.replace("https://", "").replace("http://", "");
                    activeHostname = override;
                    return override;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read local auth host override", e);
        }

        if (forceSync || activeHostname.isEmpty()) {
            fetchRemoteHost(true);
        } else if (System.currentTimeMillis() - lastFetchTime > AUTO_REFRESH_INTERVAL_MS) {
            triggerAsyncFetch(false);
        }

        return activeHostname.isEmpty() ? BuildConstants.getH() : activeHostname;
    }

    public static synchronized void triggerAsyncFetch(boolean force) {
        if (isFetching) return;
        long now = System.currentTimeMillis();
        if (!force && (now - lastFetchTime < COOLDOWN_MS)) {
            return;
        }
        isFetching = true;

        Thread fetchThread = new Thread(() -> {
            try {
                fetchRemoteHost(false);
            } finally {
                synchronized (TsfAuthPreLaunch.class) {
                    isFetching = false;
                }
            }
        }, "TsfAuth-HostFetcher");
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    public static void forceRefresh() {
        triggerAsyncFetch(true);
    }

    public static String fetchRemoteHost(boolean synchronous) {
        try {
            java.net.URL url = new java.net.URI(REMOTE_HOST_URL).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "TsfAuth/2.3.0 (Minecraft Client)");
            if (lastEtag != null && !lastEtag.isEmpty()) {
                conn.setRequestProperty("If-None-Match", lastEtag);
            }

            int responseCode = conn.getResponseCode();
            lastFetchTime = System.currentTimeMillis();

            if (responseCode == 304) {
                return activeHostname;
            }

            if (responseCode == 200) {
                String etag = conn.getHeaderField("ETag");
                if (etag != null) {
                    lastEtag = etag;
                }

                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (line != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            String newHost = line.replace("https://", "").replace("http://", "");
                            if (!newHost.equals(activeHostname)) {
                                LOGGER.info("Active auth host updated: " + (activeHostname.isEmpty() ? "<none>" : activeHostname) + " -> " + newHost);
                                activeHostname = newHost;
                            }
                            return activeHostname;
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (synchronous) {
                LOGGER.error("Failed to fetch auth host from remote synchronously: " + e.getMessage());
            } else {
                LOGGER.debug("Async auth host fetch error: " + e.getMessage());
            }
        }
        return activeHostname;
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
