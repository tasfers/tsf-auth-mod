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

    public static String getAuthHost() {
        if (activeHostname != null && !activeHostname.isEmpty()) {
            return activeHostname;
        }

        // 1. Try local config file override
        try {
            java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir();
            java.nio.file.Path hostFile = configDir.resolve("tsf_auth_host.txt");
            if (java.nio.file.Files.exists(hostFile)) {
                String line = java.nio.file.Files.readString(hostFile).trim();
                if (!line.isEmpty()) {
                    activeHostname = line.replace("https://", "").replace("http://", "");
                    LOGGER.info("Using local override auth host: " + activeHostname);
                    return activeHostname;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read local auth host override", e);
        }

        // 2. Try fetching from remote GitHub repository
        try {
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
            LOGGER.error("Failed to fetch auth host from remote, using fallback", e);
        }

        // 3. Fallback to BuildConstants compiled host
        activeHostname = BuildConstants.getH();
        LOGGER.info("Using compiled fallback auth host: " + activeHostname);
        return activeHostname;
    }

    @Override
    public void onPreLaunch() {
        String existingIgnored = System.getProperty("authlibinjector.ignoredPackages", "");
        String newIgnored = existingIgnored.isEmpty() ? "com.tasfers.tsfauth" : existingIgnored + ",com.tasfers.tsfauth";
        System.setProperty("authlibinjector.ignoredPackages", newIgnored);

        String hostname = getAuthHost();

        LOGGER.info("Attempting to attach authlib-injector for host: " + hostname);
        boolean success = AuthlibInjectorManager.inject(hostname);
        if (success) {
            LOGGER.info("Successfully attached authlib-injector in PreLaunch!");
        } else {
            LOGGER.error("Failed to attach authlib-injector in PreLaunch.");
        }
    }
}
