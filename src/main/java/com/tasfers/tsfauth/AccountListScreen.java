package com.tasfers.tsfauth;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
// import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class AccountListScreen extends Screen {
    private final Screen parent;
    private int scrollOffset = 0;
    private String selectedUuid = null;
    private long lastClickTime = 0;
    private Button loginButton;
    private Button deleteButton;
    private Button addButton;
    private Button refreshButton;
    private Button refreshAllButton;
    private Button backButton;
    private float modelXRot = -5.0f;
    private float modelYRot = 30.0f;
    private boolean isDraggingModel = false;
    private Button overlayBackButton;
    private float reconnectCountdown = 10.0f;
    private boolean isReconnecting = false;
    private long lastTickTime = 0;

    public AccountListScreen(Screen parent) {
        super(Component.literal("tsf auth"));
        this.parent = parent;
        AccountManager.loadAccounts();
        AuthServerStatusChecker.triggerCheck();
    }

    @Override
    protected void init() {
        this.loginButton = Button.builder(Component.literal("Login"), button -> {
            performLogin(this.selectedUuid);
        }).bounds(this.width / 2 - 165, this.height - 28, 50, 20).build();
        this.addRenderableWidget(this.loginButton);

        this.addButton = Button.builder(Component.literal("Add"), button -> {
            this.minecraft.setScreen(new AuthScreen(this));
        }).bounds(this.width / 2 - 110, this.height - 28, 40, 20).build();
        this.addRenderableWidget(this.addButton);

        this.deleteButton = Button.builder(Component.literal("Delete"), button -> {
            if (this.selectedUuid != null) {
                AccountManager.removeAccount(this.selectedUuid);
                this.selectedUuid = null;
            }
        }).bounds(this.width / 2 - 65, this.height - 28, 50, 20).build();
        this.addRenderableWidget(this.deleteButton);

        this.refreshButton = Button.builder(Component.literal("Refresh"), button -> {
            if (this.selectedUuid != null) {
                SkinFetcher.clearSkin(this.selectedUuid);
                new Thread(() -> {
                    AccountManager.validateSession(this.selectedUuid);
                }).start();
            }
        }).bounds(this.width / 2 - 10, this.height - 28, 55, 20).build();
        this.addRenderableWidget(this.refreshButton);

        this.refreshAllButton = Button.builder(Component.literal("Refresh All"), button -> {
            new Thread(() -> {
                for (AccountManager.Account acc : AccountManager.getAccounts()) {
                    SkinFetcher.clearSkin(acc.uuid);
                    AccountManager.validateSession(acc.uuid);
                }
            }).start();
        }).bounds(this.width / 2 + 50, this.height - 28, 70, 20).build();
        this.addRenderableWidget(this.refreshAllButton);

        this.backButton = Button.builder(Component.literal("Back"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 + 125, this.height - 28, 40, 20).build();
        this.addRenderableWidget(this.backButton);

        this.overlayBackButton = Button.builder(Component.literal("Назад"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 50, this.height / 2 + 15, 100, 20).build();
        this.addRenderableWidget(this.overlayBackButton);

        // We will render accounts manually and handle clicks in mouseClicked
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean offline = !com.tasfers.tsfauth.AuthServerStatusChecker.isOnline;

        // Toggle widget visibility
        if (this.loginButton != null) this.loginButton.visible = !offline;
        if (this.addButton != null) this.addButton.visible = !offline;
        if (this.deleteButton != null) this.deleteButton.visible = !offline;
        if (this.refreshButton != null) this.refreshButton.visible = !offline;
        if (this.refreshAllButton != null) this.refreshAllButton.visible = !offline;
        if (this.backButton != null) this.backButton.visible = !offline;
        if (this.overlayBackButton != null) this.overlayBackButton.visible = offline;

        if (offline) {
            if (this.overlayBackButton != null) this.overlayBackButton.visible = false;
            super.render(context, mouseX, mouseY, delta);
            
            long now = System.currentTimeMillis();
            if (this.lastTickTime == 0) {
                this.lastTickTime = now;
            }
            float elapsedSeconds = (now - this.lastTickTime) / 1000.0f;
            this.lastTickTime = now;

            if (!this.isReconnecting) {
                this.reconnectCountdown -= elapsedSeconds;
                if (this.reconnectCountdown <= 0.0f) {
                    this.reconnectCountdown = 0.0f;
                    this.isReconnecting = true;
                    com.tasfers.tsfauth.AuthServerStatusChecker.triggerCheck().thenRun(() -> {
                        this.minecraft.execute(() -> {
                            this.isReconnecting = false;
                            this.reconnectCountdown = 10.0f;
                        });
                    });
                }
            }

            // Draw dark background overlay
            context.fill(0, 0, this.width, this.height, 0xD0000000);

            // Draw error message
            context.drawCenteredString(this.font, "§cСервер авторизации недоступен.", this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);

            // Draw status
            String statusText;
            if (this.isReconnecting) {
                statusText = "§eПереподключение...";
            } else {
                statusText = String.format("§7Переподключение через %.1f...", this.reconnectCountdown);
            }
            context.drawCenteredString(this.font, statusText, this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);

            // Render the back button on top of the overlay
            if (this.overlayBackButton != null) {
                this.overlayBackButton.visible = true;
                this.overlayBackButton.render(context, mouseX, mouseY, delta);
            }
            return;
        } else {
            this.lastTickTime = 0;
            this.reconnectCountdown = 10.0f;
            this.isReconnecting = false;
        }

        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Render 3D player model on the left side
        AccountManager.Account previewAcc = null;
        if (this.selectedUuid != null) {
            previewAcc = AccountManager.getAccountByUuid(this.selectedUuid);
        } else if (AccountManager.getActiveUuid() != null && !AccountManager.getActiveUuid().isEmpty()) {
            previewAcc = AccountManager.getAccountByUuid(AccountManager.getActiveUuid());
        }
        
        if (previewAcc != null) {
            renderPlayerModel(context, this.width / 2 - 160, this.height / 2 + 50, 60, mouseX, mouseY, previewAcc);
        }

        List<AccountManager.Account> accounts = AccountManager.getAccounts();
        int yStart = 40;
        int itemHeight = 36;

        for (int i = 0; i < accounts.size(); i++) {
            AccountManager.Account acc = accounts.get(i);
            int y = yStart + i * itemHeight - scrollOffset;

            if (y < 30 || y > this.height - 40) continue; // Basic clipping

            // Calculate vertical offset
            int yOffset = yStart + i * itemHeight - scrollOffset;

            if (yOffset < 30 || yOffset > this.height - 40) continue; // Basic clipping

            boolean isHovered = mouseX >= this.width / 2 - 80 && mouseX <= this.width / 2 + 80 && mouseY >= yOffset && mouseY < yOffset + itemHeight;
            boolean isActive = acc.uuid.equals(AccountManager.getActiveUuid());
            boolean isSelected = acc.uuid.equals(selectedUuid);

            int boxLeft = this.width / 2 - 80;
            int boxRight = this.width / 2 + 80;

            // Draw Background
            int bgColor = isSelected ? 0x88FFFFFF : (isHovered ? 0x44FFFFFF : 0x44000000);
            context.fill(boxLeft, yOffset, boxRight, yOffset + itemHeight - 2, bgColor);

            // Draw Player Head or Empty Square
            net.minecraft.resources.Identifier loc = SkinFetcher.getSkin(acc.uuid);
            if (loc != null && acc.isValid) {
                context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, loc, boxLeft + 6, yOffset + 5, 8.0f, 8.0f, 24, 24, 8, 8, 64, 64, -1);
                context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, loc, boxLeft + 6, yOffset + 5, 40.0f, 8.0f, 24, 24, 8, 8, 64, 64, -1);
            } else {
                context.fill(boxLeft + 6, yOffset + 5, boxLeft + 6 + 24, yOffset + 5 + 24, 0xFF444444);
            }

            // Draw Username
            int nameColor = isActive ? 0xFF55FF55 : 0xFFFFFFFF;
            context.drawString(this.font, acc.username, boxLeft + 38, yOffset + 5, nameColor, true);

            // Draw Status
            String status;
            int statusColor;
            if (acc.isRefreshing) {
                int dots = (int)((net.minecraft.util.Util.getMillis() / 500L) % 4);
                status = "§eRefreshing" + "...".substring(0, dots);
                statusColor = 0xFFFFFF55;
            } else {
                status = "Status: " + (acc.isValid ? "§aValid" : "§cInvalid");
                statusColor = 0xFFDDDDDD;
            }
            context.drawString(this.font, status, boxLeft + 38, yOffset + 17, statusColor, true);

            // Draw Up/Down arrows if selected
            if (isSelected) {
                boolean hoverUp = mouseX >= boxRight - 20 && mouseX <= boxRight && mouseY >= yOffset + 2 && mouseY <= yOffset + 17;
                boolean hoverDown = mouseX >= boxRight - 20 && mouseX <= boxRight && mouseY >= yOffset + 19 && mouseY <= yOffset + 34;
                
                context.pose().pushMatrix();
                context.pose().translate((float)(boxRight - 10), (float)(yOffset + 6));
                context.pose().scale(2.0f, 1.0f);
                context.drawString(this.font, "▲", -this.font.width("▲") / 2, 0, hoverUp ? 0xFFFFFF00 : 0xFFFFFFFF, true);
                context.pose().popMatrix();

                context.pose().pushMatrix();
                context.pose().translate((float)(boxRight - 10), (float)(yOffset + 20));
                context.pose().scale(2.0f, 1.0f);
                context.drawString(this.font, "▼", -this.font.width("▼") / 2, 0, hoverDown ? 0xFFFFFF00 : 0xFFFFFFFF, true);
                context.pose().popMatrix();
            }
        }

        if (this.loginButton != null) {
            this.loginButton.active = (this.selectedUuid != null);
            if (this.selectedUuid != null) {
                AccountManager.Account selectedAcc = AccountManager.getAccountByUuid(this.selectedUuid);
                if (selectedAcc != null && !selectedAcc.isValid) {
                    this.loginButton.setMessage(Component.literal("Relogin"));
                } else if (selectedAcc != null && selectedAcc.uuid.equals(AccountManager.getActiveUuid())) {
                    this.loginButton.setMessage(Component.literal("Active"));
                    this.loginButton.active = false;
                } else {
                    this.loginButton.setMessage(Component.literal("Login"));
                }
            } else {
                this.loginButton.setMessage(Component.literal("Login"));
            }
        }
        
        if (this.deleteButton != null) {
            this.deleteButton.active = (this.selectedUuid != null);
        }
    }
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }
        if (!com.tasfers.tsfauth.AuthServerStatusChecker.isOnline) {
            return false;
        }
        
        int button = event.button();
        double mouseX = event.x();
        double mouseY = event.y();
        
        if (button == 0) { // Left click
            int modelX = this.width / 2 - 160;
            int modelY = this.height / 2 + 50;
            if (mouseX >= modelX - 100 && mouseX <= modelX + 100 && mouseY >= modelY - 200 && mouseY <= modelY + 100) {
                this.isDraggingModel = true;
                return true;
            }

            List<AccountManager.Account> accounts = AccountManager.getAccounts();
            int yStart = 40;
            int itemHeight = 36;
            for (int i = 0; i < accounts.size(); i++) {
                int y = yStart + i * itemHeight - scrollOffset;
                if (y < 30 || y > this.height - 40) continue;

                if (mouseX >= this.width / 2 - 150 && mouseX <= this.width / 2 + 150 && mouseY >= y && mouseY < y + itemHeight) {
                    AccountManager.Account acc = accounts.get(i);
                    
                    if (acc.uuid.equals(this.selectedUuid)) {
                        if (mouseX >= this.width / 2 + 125 && mouseX <= this.width / 2 + 145) {
                            if (mouseY >= y + 2 && mouseY <= y + 17) {
                                AccountManager.moveAccountUp(acc.uuid);
                                return true;
                            } else if (mouseY >= y + 19 && mouseY <= y + 34) {
                                AccountManager.moveAccountDown(acc.uuid);
                                return true;
                            }
                        }
                    }

                    long now = net.minecraft.util.Util.getMillis();
                    if (acc.uuid.equals(this.selectedUuid) && now - this.lastClickTime < 250) {
                        // Double click
                        performLogin(acc.uuid);
                    } else {
                        this.selectedUuid = acc.uuid;
                        this.lastClickTime = now;
                    }
                    return true;
                }
            }
        }
        
        return false;
    }

    private void performLogin(String uuid) {
        if (uuid == null) return;
        AccountManager.Account acc = AccountManager.getAccountByUuid(uuid);
        if (acc == null) return;
        
        if (!acc.isValid) {
            // Needs relogin
            this.minecraft.setScreen(new AuthScreen(this, acc.username));
            return;
        }

        // Trigger dynamic injection to switch account in memory!
        try {
            for (java.lang.reflect.Field f : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                if (f.getType() == com.mojang.authlib.GameProfile.class) {
                    f.setAccessible(true);
                    f.set(net.minecraft.client.Minecraft.getInstance(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString(acc.uuid), acc.username));
                }
            }

            net.minecraft.client.User currentUser = net.minecraft.client.Minecraft.getInstance().getUser();
            if (currentUser != null) {
                com.tasfers.tsfauth.mixin.UserAccessor userAccessor = (com.tasfers.tsfauth.mixin.UserAccessor) (Object) currentUser;
                userAccessor.setYggName(acc.username);
                userAccessor.setYggProfileId(java.util.UUID.fromString(acc.uuid));
                userAccessor.setYggAccessToken(acc.accessToken);
                
                com.mojang.authlib.Environment env = com.mojang.authlib.EnvironmentParser.getEnvironmentFromProperties().orElse(com.mojang.authlib.yggdrasil.YggdrasilEnvironment.PROD.getEnvironment());
                com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService authService = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(net.minecraft.client.Minecraft.getInstance().getProxy(), env);
                com.mojang.authlib.minecraft.UserApiService newApiService = authService.createUserApiService(acc.accessToken);
                ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setUserApiService(newApiService);
                
                net.minecraft.client.multiplayer.ProfileKeyPairManager newPkm = net.minecraft.client.multiplayer.ProfileKeyPairManager.create(newApiService, currentUser, net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
                ((com.tasfers.tsfauth.mixin.MinecraftClientAccessor) net.minecraft.client.Minecraft.getInstance()).setProfileKeyPairManager(newPkm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        AccountManager.setActiveUuid(acc.uuid);

        // Also save to active session file
        try {
            com.google.gson.JsonObject sessionJson = new com.google.gson.JsonObject();
            sessionJson.addProperty("username", acc.username);
            sessionJson.addProperty("uuid", acc.uuid);
            sessionJson.addProperty("accessToken", acc.accessToken);
            java.nio.file.Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tsf_auth_session.json"), sessionJson.toString());
        } catch (Exception e) {}
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!com.tasfers.tsfauth.AuthServerStatusChecker.isOnline) {
            return false;
        }
        this.scrollOffset -= verticalAmount * 20;
        if (this.scrollOffset < 0) this.scrollOffset = 0;
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) {
            this.isDraggingModel = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.isDraggingModel && event.button() == 0) {
            this.modelXRot -= (float)dragY * 2.5f;
            this.modelYRot += (float)dragX * 2.5f;
            
            if (this.modelXRot < -50.0f) this.modelXRot = -50.0f;
            if (this.modelXRot > 50.0f) this.modelXRot = 50.0f;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void renderPlayerModel(GuiGraphics context, int x, int y, int scale, float mouseX, float mouseY, AccountManager.Account acc) {
        if (!acc.isValid) return;
        
        net.minecraft.world.entity.player.PlayerSkin skin = null;
        net.minecraft.resources.Identifier skinLoc = SkinFetcher.getSkin(acc.uuid);
        net.minecraft.resources.Identifier capeLoc = SkinFetcher.getCape(acc.uuid);

        if (skinLoc != null) {
            net.minecraft.resources.Identifier finalSkinLoc = skinLoc;
            net.minecraft.core.ClientAsset.Texture bodyTex = new net.minecraft.core.ClientAsset.Texture() {
                public net.minecraft.resources.Identifier texturePath() { return finalSkinLoc; }
                public net.minecraft.resources.Identifier id() { return finalSkinLoc; }
            };
            net.minecraft.core.ClientAsset.Texture capeTex = capeLoc == null ? null : new net.minecraft.core.ClientAsset.Texture() {
                public net.minecraft.resources.Identifier texturePath() { return capeLoc; }
                public net.minecraft.resources.Identifier id() { return capeLoc; }
            };
            boolean isSlim = SkinFetcher.isSlim(acc.uuid);
            skin = new net.minecraft.world.entity.player.PlayerSkin(bodyTex, capeTex, null, isSlim ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE, false);
        } else {
            return;
        }

        net.minecraft.client.renderer.entity.state.AvatarRenderState state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        state.skin = skin;
        state.showCape = capeLoc != null; // Fixed WaveyCapes NPE crash, now only showing if cape exists
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showHat = true;
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.scale = 1.0f;

        state.bodyRot = 180.0F;
        state.yRot = 0.0F;
        state.xRot = 0.0F;

        org.joml.Quaternionf quaternionf = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf quaternionf1 = new org.joml.Quaternionf()
            .rotateX(this.modelXRot * ((float)Math.PI / 180F))
            .rotateY(this.modelYRot * ((float)Math.PI / 180F));
        quaternionf.mul(quaternionf1);

        org.joml.Vector3f center = new org.joml.Vector3f(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
        
        // To pivot around the center of the entity (C), we want C to end up at the origin (0) of the rectangle.
        // submitEntityRenderState does: v' = Q * v + offset
        // We want Q * C + offset = 0
        // Therefore, offset = - (Q * C)
        org.joml.Vector3f offset = new org.joml.Vector3f(center);
        offset.rotate(quaternionf); // use quaternionf which includes the rotateZ(PI) flip!
        offset.mul(-1.0f);
        
        context.submitEntityRenderState(state, (float)scale, offset, quaternionf, null, x - 100, y - 200, x + 100, y + 100);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
