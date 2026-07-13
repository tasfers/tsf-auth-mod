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

        // Register custom payloads
        ClientLoginNetworking.registerGlobalReceiver(Identifier.fromNamespaceAndPath("tsfauth", "session_sync"), (client, handler, buf, listenerAdder) -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
                    for (net.fabricmc.loader.api.ModContainer mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
                        if (!mod.getMetadata().getType().equals("builtin") && mod.getMetadata().getId() != null && !mod.getMetadata().getId().equals("fabricloader")) {
                            if (mod.getContainingMod().isEmpty()) {
                                com.google.gson.JsonObject m = new com.google.gson.JsonObject();
                                m.addProperty("id", mod.getMetadata().getId());
                                m.addProperty("name", mod.getMetadata().getName());
                                m.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                                m.addProperty("description", mod.getMetadata().getDescription());
                                
                                java.util.Collection<net.fabricmc.loader.api.metadata.Person> authors = mod.getMetadata().getAuthors();
                                if (!authors.isEmpty()) {
                                    StringBuilder authorStr = new StringBuilder();
                                    for (net.fabricmc.loader.api.metadata.Person p : authors) {
                                        if (authorStr.length() > 0) authorStr.append(", ");
                                        authorStr.append(p.getName());
                                    }
                                    m.addProperty("authors", authorStr.toString());
                                }
                                
                                java.util.Optional<String> homepage = mod.getMetadata().getContact().get("homepage");
                                if (homepage.isPresent()) {
                                    m.addProperty("homepage", homepage.get());
                                }
                                
                                mods.add(m);
                            }
                        }
                    }

                    com.google.gson.JsonObject payloadJson = new com.google.gson.JsonObject();
                    payloadJson.add("mods", mods);

                    String fingerprintJson = payloadJson.toString();
                    String modVersion = BuildConstants.getV();
                    
                    String modHash = "unknown";
                    try {
                        java.nio.file.Path modPath = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("tsf-auth").get().getOrigin().getPaths().get(0);
                        java.security.MessageDigest digest = java.security.MessageDigest.getInstance(BuildConstants.getS());
                        byte[] hashBytes = digest.digest(java.nio.file.Files.readAllBytes(modPath));
                        StringBuilder sb = new StringBuilder();
                        for (byte b : hashBytes) {
                            sb.append(String.format("%02x", b));
                        }
                        modHash = sb.toString();
                    } catch (Exception ex) {
                        LOGGER.error("Failed to calculate mod hash", ex);
                    }
                    
                    net.minecraft.network.FriendlyByteBuf responseBuf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    responseBuf.writeUtf(modVersion);
                    responseBuf.writeUtf(fingerprintJson);
                    responseBuf.writeUtf(modHash);
                    
                    return responseBuf;
                } catch (Exception e) {
                    LOGGER.error("Failed to process and send mod list", e);
                    return net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                }
            });
        });

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
