package com.tasfers.tsfauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AuthScreen extends Screen {
    private final Screen parent;
    private EditBox usernameField;
    private EditBox passwordField;
    private String statusMessage = "";
    private String prefillUsername = "";

    public AuthScreen(Screen parent) {
        super(Component.literal("tsf auth"));
        this.parent = parent;
    }

    public AuthScreen(Screen parent, String username) {
        super(Component.literal("tsf auth"));
        this.parent = parent;
        this.prefillUsername = username;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.usernameField = new EditBox(this.font, centerX - 100, centerY - 44, 200, 20, Component.literal("Username"));
        this.usernameField.setMaxLength(256);
        if (this.prefillUsername != null && !this.prefillUsername.isEmpty()) {
            this.usernameField.setValue(this.prefillUsername);
        }
        this.usernameField.setHint(Component.literal("Username").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x888888)));
        this.addRenderableWidget(this.usernameField);

        this.passwordField = new EditBox(this.font, centerX - 100, centerY - 4, 200, 20, Component.literal("Password"));
        this.passwordField.setMaxLength(256);
        this.passwordField.setHint(Component.literal("Password").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x888888)));
        this.passwordField.addFormatter((string, integer) -> {
            return net.minecraft.util.FormattedCharSequence.forward("*".repeat(string.length()), net.minecraft.network.chat.Style.EMPTY);
        });
        this.addRenderableWidget(this.passwordField);

        this.addRenderableWidget(Button.builder(Component.literal("Login"), button -> performLogin())
                .bounds(centerX - 100, centerY + 36, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(this.parent))
                .bounds(centerX + 5, centerY + 36, 95, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            this.performLogin();
            return true;
        }
        return super.keyPressed(event);
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void performLogin() {
        String hostname = com.tasfers.tsfauth.BuildConstants.getH();
        String username = this.usernameField.getValue();
        String password = this.passwordField.getValue();

        if (hostname.isEmpty() || username.isEmpty() || password.isEmpty()) {
            this.statusMessage = "§cFill all fields";
            return;
        }

        this.statusMessage = "§eAuthenticating...";

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                JsonObject agent = new JsonObject();
                agent.addProperty("name", "Minecraft");
                agent.addProperty("version", 1);
                payload.add("agent", agent);
                payload.addProperty("username", username);
                payload.addProperty("password", password);

                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
                String protocol = (hostname.contains("localhost") || hostname.contains("127.0.0.1")) ? "http://" : "https://";
                String bypassHost = hostname;
                if (hostname.contains("localhost")) {
                    bypassHost = hostname.replace("localhost", "127.0.0.1");
                } else if (hostname.contains("127.0.0.1")) {
                    bypassHost = hostname.replace("127.0.0.1", "localhost");
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(protocol + bypassHost + "/auth/authenticate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String accessToken = json.get("accessToken").getAsString();
                    JsonObject profile = json.getAsJsonObject("selectedProfile");
                    String id = profile.get("id").getAsString();
                    String name = profile.get("name").getAsString();

                    // Format UUID
                    String uuid = id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                    );

                    TsfAuthClient.currentStatus = "§a" + name;
                    
                    // Save hostname for preLaunch injection
                    java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
                    java.nio.file.Files.writeString(configDir.resolve("tsf_auth_hostname.txt"), hostname);
                    
                    String clientToken = json.has("clientToken") ? json.get("clientToken").getAsString() : "";
                    
                    com.tasfers.tsfauth.AccountManager.addOrUpdateAccount(name, uuid, accessToken, clientToken);
                    
                    // Seamless Secure Chat: Dynamically overwrite the existing user in memory
                    try {
                        for (java.lang.reflect.Field f : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                            if (f.getType() == com.mojang.authlib.GameProfile.class) {
                                f.setAccessible(true);
                                f.set(net.minecraft.client.Minecraft.getInstance(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString(uuid), name));
                            }
                        }

                        net.minecraft.client.User currentUser = net.minecraft.client.Minecraft.getInstance().getUser();
                        if (currentUser != null) {
                            com.tasfers.tsfauth.mixin.UserAccessor userAccessor = (com.tasfers.tsfauth.mixin.UserAccessor) (Object) currentUser;
                            userAccessor.setYggName(name);
                            userAccessor.setYggProfileId(java.util.UUID.fromString(uuid));
                            userAccessor.setYggAccessToken(accessToken);
                            
                            com.mojang.authlib.Environment env = com.mojang.authlib.EnvironmentParser.getEnvironmentFromProperties().orElse(com.mojang.authlib.yggdrasil.YggdrasilEnvironment.PROD.getEnvironment());
                            com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService authService = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(net.minecraft.client.Minecraft.getInstance().getProxy(), env);
                            com.mojang.authlib.minecraft.UserApiService newApiService = authService.createUserApiService(accessToken);
                            ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setUserApiService(newApiService);
                            
                            net.minecraft.client.multiplayer.ProfileKeyPairManager newPkm = net.minecraft.client.multiplayer.ProfileKeyPairManager.create(newApiService, currentUser, net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
                            ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setProfileKeyPairManager(newPkm);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (hostname.equals(com.tasfers.tsfauth.TsfAuthPreLaunch.activeHostname)) {
                        this.statusMessage = "§aSuccess!";
                        Minecraft.getInstance().execute(() -> {
                            this.minecraft.setScreen(this.parent);
                        });
                    } else {
                        this.statusMessage = "§aSuccess! §cRESTART GAME FOR MULTIPLAYER";
                    }
                } else {
                    String body = response.body();
                    String errorMessage = "Unknown error";
                    try {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        if (json.has("errorMessage")) {
                            errorMessage = json.get("errorMessage").getAsString();
                        } else if (json.has("message")) {
                            errorMessage = json.get("message").getAsString();
                        } else if (json.has("error")) {
                            errorMessage = json.get("error").getAsString();
                        }
                    } catch (Exception parseEx) {
                        errorMessage = body != null ? body.trim() : "Unknown error";
                        if (errorMessage.length() > 100) {
                            errorMessage = errorMessage.substring(0, 97) + "...";
                        }
                    }
                    this.statusMessage = "§cLogin failed: " + errorMessage;
                }
            } catch (Exception e) {
                this.statusMessage = "§cLogin error: " + e.getMessage();
                e.printStackTrace();
            }
        });
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredString(this.font, "Username", centerX, centerY - 56, 0xFFFFFFFF);
        context.drawCenteredString(this.font, "Password", centerX, centerY - 16, 0xFFFFFFFF);

        if (!statusMessage.isEmpty()) {
            context.drawCenteredString(this.font, statusMessage, centerX, centerY + 21, 0xFFFFFFFF);
        }
    }
}
