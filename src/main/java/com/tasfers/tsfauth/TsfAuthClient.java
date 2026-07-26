package com.tasfers.tsfauth;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class TsfAuthClient implements ClientModInitializer {
    public static final String MOD_ID = "tsf-auth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Status can be accessed globally
    public static String currentStatus = "Not authenticated via mod";

    @Override
    public void onInitializeClient() {

        LOGGER.info("tsfauth mod initialized!");

        // Load accounts early and pre-fetch skins
        AccountManager.loadAccounts();
        for (AccountManager.Account acc : AccountManager.getAccounts()) {
            SkinFetcher.getSkin(acc.uuid);
        }

        // Start background token refresher
        TokenRefresher.start();

        // Start background auth server status checker
        AuthServerStatusChecker.start();



        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            java.nio.file.Path sessionFile = configDir.resolve("tsf_auth_session.json");
            if (java.nio.file.Files.exists(sessionFile)) {
                String jsonStr = java.nio.file.Files.readString(sessionFile);
                com.google.gson.JsonObject sessionJson = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                if (sessionJson.has("username") && sessionJson.has("uuid") && sessionJson.has("accessToken")) {
                    String username = sessionJson.get("username").getAsString();
                    String uuid = sessionJson.get("uuid").getAsString();
                    String accessToken = sessionJson.get("accessToken").getAsString();
                    
                    net.minecraft.client.User currentUser = net.minecraft.client.Minecraft.getInstance().getUser();
                    if (currentUser != null) {
                        java.util.UUID parsedUuid = java.util.UUID.fromString(uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                        
                        for (java.lang.reflect.Field f : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                            if (f.getType() == com.mojang.authlib.GameProfile.class) {
                                f.setAccessible(true);
                                f.set(net.minecraft.client.Minecraft.getInstance(), new com.mojang.authlib.GameProfile(parsedUuid, username));
                            }
                        }

                        com.tasfers.tsfauth.mixin.UserAccessor userAccessor = (com.tasfers.tsfauth.mixin.UserAccessor) (Object) currentUser;
                        userAccessor.setYggName(username);
                        userAccessor.setYggProfileId(parsedUuid);
                        userAccessor.setYggAccessToken(accessToken);
                        
                        com.mojang.authlib.Environment env = com.mojang.authlib.EnvironmentParser.getEnvironmentFromProperties().orElse(com.mojang.authlib.yggdrasil.YggdrasilEnvironment.PROD.getEnvironment());
                        com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService authService = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(net.minecraft.client.Minecraft.getInstance().getProxy(), env);
                        com.mojang.authlib.minecraft.UserApiService newApiService = authService.createUserApiService(accessToken);
                        ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setUserApiService(newApiService);
                        
                        net.minecraft.client.multiplayer.ProfileKeyPairManager newPkm = net.minecraft.client.multiplayer.ProfileKeyPairManager.create(newApiService, currentUser, net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
                        ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setProfileKeyPairManager(newPkm);
                        
                        currentStatus = "§a" + username;
                        LOGGER.info("Successfully loaded saved session for: " + username);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load saved session", e);
        }
    }
}
