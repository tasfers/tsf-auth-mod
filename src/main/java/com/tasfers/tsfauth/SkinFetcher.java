package com.tasfers.tsfauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class SkinFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("tsf-auth-skins");
    private static final Map<String, Identifier> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> CAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SLIM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FETCHING = new ConcurrentHashMap<>();
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    private static final Map<String, Long> FAILED_CACHE = new ConcurrentHashMap<>();
    
    // Hidden tripwire variables removed
    static {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    String uuidStr = QUEUE.take();
                    fetchSkin(uuidStr);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        worker.setDaemon(true);
        worker.setName("TsfAuth-SkinWorker");
        worker.start();
    }

    public static Identifier getSkin(String uuidStr) {
        if (SKIN_CACHE.containsKey(uuidStr)) {
            return SKIN_CACHE.get(uuidStr);
        }

        long now = System.currentTimeMillis();
        if (FAILED_CACHE.containsKey(uuidStr)) {
            if (now - FAILED_CACHE.get(uuidStr) < 300000) { // 5 minutes cooldown for failed or empty skin fetches
                return null;
            }
        }

        if (!FETCHING.containsKey(uuidStr)) {
            FETCHING.put(uuidStr, true);
            QUEUE.offer(uuidStr);
        }

        return null;
    }

    public static Identifier getCape(String uuidStr) {
        return CAPE_CACHE.get(uuidStr);
    }

    public static boolean isSlim(String uuidStr) {
        return SLIM_CACHE.getOrDefault(uuidStr, false);
    }

    public static net.minecraft.world.entity.player.PlayerSkin createPlayerSkin(String uuidStr) {
        Identifier skinLoc = getSkin(uuidStr);
        if (skinLoc == null) return null;
        
        Identifier capeLoc = getCape(uuidStr);
        boolean isSlim = isSlim(uuidStr);
        
        return new net.minecraft.world.entity.player.PlayerSkin(
            new net.minecraft.core.ClientAsset.Texture() {
                public Identifier texturePath() { return skinLoc; }
                public Identifier id() { return skinLoc; }
            },
            capeLoc == null ? null : new net.minecraft.core.ClientAsset.Texture() {
                public Identifier texturePath() { return capeLoc; }
                public Identifier id() { return capeLoc; }
            },
            null,
            isSlim ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE,
            false
        );
    }

    public static void clearCache() {
        SKIN_CACHE.clear();
        CAPE_CACHE.clear();
        SLIM_CACHE.clear();
        FETCHING.clear();
        QUEUE.clear();
        FAILED_CACHE.clear();
    }

    public static void clearSkin(String uuidStr) {
        SKIN_CACHE.remove(uuidStr);
        CAPE_CACHE.remove(uuidStr);
        SLIM_CACHE.remove(uuidStr);
        FETCHING.remove(uuidStr);
        FAILED_CACHE.remove(uuidStr);
    }

    private static void fetchSkin(String uuidStr) {
        try {
            String hostname = com.tasfers.tsfauth.BuildConstants.getH();
            String formattedUuid = uuidStr.replace("-", "");
            String bypassHost = hostname;
            if (hostname.contains("localhost")) {
                bypassHost = hostname.replace("localhost", "127.0.0.1");
            } else if (hostname.contains("127.0.0.1")) {
                bypassHost = hostname.replace("127.0.0.1", "localhost");
            }
            
            // Format URL (fallback to https if no protocol)
            String urlStr = bypassHost + "/session/minecraft/profile/" + formattedUuid;
            if (!urlStr.startsWith("http")) {
                String protocol = (hostname.contains("localhost") || hostname.contains("127.0.0.1")) ? "http://" : "https://";
                urlStr = protocol + urlStr;
            }

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    String jsonStr = new String(is.readAllBytes(), "UTF-8");
                    JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                    
                    if (root.has("properties")) {
                        JsonArray props = root.getAsJsonArray("properties");
                        for (JsonElement el : props) {
                            JsonObject prop = el.getAsJsonObject();
                            if (prop.has("name") && prop.get("name").getAsString().equals("textures")) {
                                String b64 = prop.get("value").getAsString();
                                String decoded = new String(Base64.getDecoder().decode(b64), "UTF-8");
                                JsonObject texRoot = JsonParser.parseString(decoded).getAsJsonObject();
                                
                                if (texRoot.has("textures")) {
                                    JsonObject textures = texRoot.getAsJsonObject("textures");
                                    if (textures.has("SKIN")) {
                                        JsonObject skinObj = textures.getAsJsonObject("SKIN");
                                        String skinUrlStr = skinObj.get("url").getAsString();
                                        if (skinObj.has("metadata")) {
                                            JsonObject meta = skinObj.getAsJsonObject("metadata");
                                            if (meta.has("model") && "slim".equals(meta.get("model").getAsString())) {
                                                SLIM_CACHE.put(uuidStr, true);
                                            } else {
                                                SLIM_CACHE.put(uuidStr, false);
                                            }
                                        } else {
                                            SLIM_CACHE.put(uuidStr, false);
                                        }
                                        downloadAndRegisterSkin(uuidStr, skinUrlStr, "skins", SKIN_CACHE);
                                    }
                                    if (textures.has("CAPE")) {
                                        JsonObject capeObj = textures.getAsJsonObject("CAPE");
                                        String capeUrlStr = capeObj.get("url").getAsString();
                                        downloadAndRegisterSkin(uuidStr, capeUrlStr, "capes", CAPE_CACHE);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                }
            } else {
                FAILED_CACHE.put(uuidStr, System.currentTimeMillis());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch skin for " + uuidStr, e);
            FAILED_CACHE.put(uuidStr, System.currentTimeMillis());
        }
        FETCHING.remove(uuidStr); // Allow retry later if failed
    }

    private static void downloadAndRegisterSkin(String uuidStr, String urlStr, String type, Map<String, Identifier> cache) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    NativeImage originalImage = NativeImage.read(is);
                    final NativeImage imageToUse;
                    if (originalImage.getHeight() == 32 && "skins".equals(type)) {
                        imageToUse = new NativeImage(64, 64, true);
                        originalImage.copyRect(imageToUse, 0, 0, 0, 0, 64, 32, false, false);
                        
                        // Left Leg: (16, 48) from Right Leg (0, 16)
                        originalImage.copyRect(imageToUse, 4, 16, 20, 48, 4, 4, true, false); // Top face
                        originalImage.copyRect(imageToUse, 8, 16, 24, 48, 4, 4, true, false); // Bottom face
                        originalImage.copyRect(imageToUse, 8, 20, 16, 52, 4, 12, true, false); // Inner face
                        originalImage.copyRect(imageToUse, 4, 20, 20, 52, 4, 12, true, false); // Front face
                        originalImage.copyRect(imageToUse, 0, 20, 24, 52, 4, 12, true, false); // Outer face
                        originalImage.copyRect(imageToUse, 12, 20, 28, 52, 4, 12, true, false); // Back face
                        
                        // Left Arm: (32, 48) from Right Arm (40, 16)
                        originalImage.copyRect(imageToUse, 44, 16, 36, 48, 4, 4, true, false); // Top face
                        originalImage.copyRect(imageToUse, 48, 16, 40, 48, 4, 4, true, false); // Bottom face
                        originalImage.copyRect(imageToUse, 48, 20, 32, 52, 4, 12, true, false); // Inner face
                        originalImage.copyRect(imageToUse, 44, 20, 36, 52, 4, 12, true, false); // Front face
                        originalImage.copyRect(imageToUse, 40, 20, 40, 52, 4, 12, true, false); // Outer face
                        originalImage.copyRect(imageToUse, 52, 20, 44, 52, 4, 12, true, false); // Back face

                        originalImage.close();
                    } else {
                        imageToUse = originalImage;
                    }

                    Minecraft.getInstance().execute(() -> {
                        DynamicTexture texture = new DynamicTexture(() -> "tsfauth_" + type + "_" + uuidStr, imageToUse);
                        Identifier loc = Identifier.fromNamespaceAndPath("tsfauth", type + "/" + uuidStr);
                        Minecraft.getInstance().getTextureManager().register(loc, texture);
                        cache.put(uuidStr, loc);
                        if ("skins".equals(type)) FETCHING.remove(uuidStr);
                    });
                }
            } else {
                if ("skins".equals(type)) FETCHING.remove(uuidStr);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to download skin image for " + uuidStr, e);
            FETCHING.remove(uuidStr);
        }
    }
}
