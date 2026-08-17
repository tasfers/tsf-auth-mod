package com.tasfers.tsfauth;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AuthlibInjectorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("tsf-auth-injector");
    private static boolean injected = false;

    public static boolean inject(String hostname) {
        if (injected) return true;

        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path injectorFile = configDir.resolve("authlib-injector.jar");

            if (!Files.exists(injectorFile) || Files.size(injectorFile) < 10000) {
                LOGGER.info("Extracting embedded authlib-injector.jar from mod resources...");
                try (InputStream in = AuthlibInjectorManager.class.getResourceAsStream("/assets/tsfauth/authlib-injector.jar")) {
                    if (in == null) {
                        LOGGER.error("Embedded authlib-injector.jar not found in mod resources!");
                        return false;
                    }
                    Files.copy(in, injectorFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    LOGGER.error("Failed to extract embedded authlib-injector.jar", e);
                    return false;
                }
            }

            String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
            String pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'));

            // Start the local auth proxy server
            com.tasfers.tsfauth.LocalAuthProxyServer.start();
            String apiUrl = "http://127.0.0.1:" + com.tasfers.tsfauth.LocalAuthProxyServer.getActivePort() + "/authlib-injector";

            LOGGER.info("Using ByteBuddyAgent to dynamically attach authlib-injector to PID " + pid + " for " + apiUrl);
            
            // Prevent authlib-injector from hijacking other account switchers (like IAS)
            String existingIgnored = System.getProperty("authlibinjector.ignoredPackages", "");
            String newIgnored = existingIgnored.isEmpty() 
                    ? "com.tasfers.tsfauth,ru.vidtu.ias" 
                    : existingIgnored + ",com.tasfers.tsfauth,ru.vidtu.ias";
            System.setProperty("authlibinjector.ignoredPackages", newIgnored);

            // On Linux/Flatpak, self-attaching can cause ByteBuddy to hang waiting for socket response
            // even though the agent loads successfully. We run it in a daemon thread.
            Thread attachThread = new Thread(() -> {
                try {
                    ByteBuddyAgent.attach(injectorFile.toFile(), pid, apiUrl);
                    LOGGER.info("Successfully attached authlib-injector using ByteBuddyAgent!");
                } catch (Exception e) {
                    LOGGER.error("Background attach threw an exception", e);
                }
            });
            attachThread.setDaemon(true);
            attachThread.start();
            
            // Unconditionally pause the main thread for 2.5 seconds.
            // On Windows, attach finishes instantly but authlib-injector still needs time to retransform classes.
            // If we don't wait, KnotClassLoader starts loading classes concurrently and causes ClassFormatError.
            // On Linux Flatpak, attach hangs forever, so we just wait 2.5 seconds to give it time to load.
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {}
            
            injected = true;
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to attach authlib-injector dynamically using ByteBuddy", e);
            return false;
        }
    }
}
