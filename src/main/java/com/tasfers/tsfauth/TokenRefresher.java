package com.tasfers.tsfauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TokenRefresher {
    private static final Logger LOGGER = LoggerFactory.getLogger("tsf-auth-refresher");
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "TsfAuth-TokenRefresher");
        thread.setDaemon(true);
        return thread;
    });

    public static void start() {
        // Run immediately on startup, then every 30 minutes
        SCHEDULER.scheduleAtFixedRate(TokenRefresher::refreshTokens, 5, 30 * 60, TimeUnit.SECONDS);
    }

    private static void refreshTokens() {
        LOGGER.info("Starting background token refresh...");
        boolean updatedAny = false;
        String currentHost = com.tasfers.tsfauth.TsfAuthPreLaunch.getAuthHost();
        String bypassHost = currentHost;
        if (currentHost.contains("localhost")) {
            bypassHost = currentHost.replace("localhost", "127.0.0.1");
        } else if (currentHost.contains("127.0.0.1")) {
            bypassHost = currentHost.replace("127.0.0.1", "localhost");
        }
        String refreshUrl = bypassHost + "/auth/refresh";
        if (!refreshUrl.startsWith("http://") && !refreshUrl.startsWith("https://")) {
            String protocol = (currentHost.contains("localhost") || currentHost.contains("127.0.0.1")) ? "http://" : "https://";
            refreshUrl = protocol + refreshUrl;
        }

        for (AccountManager.Account acc : AccountManager.getAccounts()) {
            if (!acc.isValid || acc.clientToken == null || acc.clientToken.isEmpty()) {
                continue;
            }

            try {
                URL url = new URL(refreshUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JsonObject body = new JsonObject();
                body.addProperty("accessToken", acc.accessToken);
                body.addProperty("clientToken", acc.clientToken);
                body.addProperty("requestUser", true);

                try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                    writer.write(body.toString());
                    writer.flush();
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                        JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                        if (response.has("accessToken")) {
                            acc.accessToken = response.get("accessToken").getAsString();
                            
                            if (net.minecraft.client.Minecraft.getInstance().getUser() != null &&
                                net.minecraft.client.Minecraft.getInstance().getUser().getProfileId().equals(java.util.UUID.fromString(acc.uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")))) {
                                
                                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                                    try {
                                        java.util.UUID refUuid = java.util.UUID.fromString(acc.uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                                        for (java.lang.reflect.Field f : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                                            if (f.getType() == com.mojang.authlib.GameProfile.class) {
                                                f.setAccessible(true);
                                                f.set(net.minecraft.client.Minecraft.getInstance(), new com.mojang.authlib.GameProfile(refUuid, acc.username));
                                            }
                                        }

                                        net.minecraft.client.User currentUser = net.minecraft.client.Minecraft.getInstance().getUser();
                                        
                                        // Update the user object in memory safely
                                        if (currentUser != null) {
                                            java.util.UUID parsedUuid = java.util.UUID.fromString(acc.uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                                            com.tasfers.tsfauth.mixin.UserAccessor userAccessor = (com.tasfers.tsfauth.mixin.UserAccessor) (Object) currentUser;
                                            userAccessor.setYggName(acc.username);
                                            userAccessor.setYggProfileId(parsedUuid);
                                            userAccessor.setYggAccessToken(acc.accessToken);
                                            
                                            com.mojang.authlib.Environment env = com.mojang.authlib.EnvironmentParser.getEnvironmentFromProperties().orElse(com.mojang.authlib.yggdrasil.YggdrasilEnvironment.PROD.getEnvironment());
                                            com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService authService = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(net.minecraft.client.Minecraft.getInstance().getProxy(), env);
                                            com.mojang.authlib.minecraft.UserApiService newApiService = authService.createUserApiService(acc.accessToken);
                                            ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setUserApiService(newApiService);
                                            
                                            net.minecraft.client.multiplayer.ProfileKeyPairManager newPkm = net.minecraft.client.multiplayer.ProfileKeyPairManager.create(newApiService, currentUser, net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
                                            ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setProfileKeyPairManager(newPkm);
                                        }
                                        
                                        // Update the active session on disk
                                        try {
                                            com.google.gson.JsonObject sessionJson = new com.google.gson.JsonObject();
                                            sessionJson.addProperty("username", acc.username);
                                            sessionJson.addProperty("uuid", acc.uuid);
                                            sessionJson.addProperty("accessToken", acc.accessToken);
                                            java.nio.file.Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_session.json"), sessionJson.toString());
                                        } catch (Exception ex) {}
                                        
                                    } catch (Exception e) {
                                        LOGGER.error("Failed to live-update session for active account", e);
                                    }
                                });
                            }
                        }
                        if (response.has("clientToken")) {
                            acc.clientToken = response.get("clientToken").getAsString();
                        }
                        acc.isValid = true;
                        updatedAny = true;
                        LOGGER.info("Successfully refreshed token for " + acc.username);
                    }
                } else if (code == 403 || code == 401) {
                    acc.isValid = false;
                    updatedAny = true;
                    LOGGER.warn("Token for " + acc.username + " became invalid (Code: " + code + ")");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to refresh token for " + acc.username, e);
            }
        }

        if (updatedAny) {
            AccountManager.saveAccounts();
        }
    }
}
