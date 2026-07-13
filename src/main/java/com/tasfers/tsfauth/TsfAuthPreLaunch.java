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

    @Override
    public void onPreLaunch() {
        String existingIgnored = System.getProperty("authlibinjector.ignoredPackages", "");
        String newIgnored = existingIgnored.isEmpty() ? "com.tasfers.tsfauth" : existingIgnored + ",com.tasfers.tsfauth";
        System.setProperty("authlibinjector.ignoredPackages", newIgnored);

        String hostname = com.tasfers.tsfauth.BuildConstants.getH();

        LOGGER.info("Attempting to attach authlib-injector for host: " + hostname);
        boolean success = AuthlibInjectorManager.inject(hostname);
        if (success) {
            activeHostname = hostname;
            LOGGER.info("Successfully attached authlib-injector in PreLaunch!");
        } else {
            LOGGER.error("Failed to attach authlib-injector in PreLaunch.");
        }
    }
}
