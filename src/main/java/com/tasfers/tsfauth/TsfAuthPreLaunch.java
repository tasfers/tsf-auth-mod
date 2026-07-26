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
        try {
            java.net.URL url = new java.net.URL("https://raw.githubusercontent.com/tasfers/files/refs/heads/main/tsf_auth_host");
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
        activeHostname = "mc-auth.tsf.sh";
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
