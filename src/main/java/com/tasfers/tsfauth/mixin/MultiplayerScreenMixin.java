package com.tasfers.tsfauth.mixin;

import com.tasfers.tsfauth.TsfAuthClient;
import com.tasfers.tsfauth.AuthScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private Button authButton;

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void onReposition(CallbackInfo ci) {
        int btnX = this.width / 2 - 154 - 24;
        int btnY = this.height - 52;
        int btnW = 20;
        int btnH = 44; // Span both rows
        
        // Try to find the vanilla "Join Server" button dynamically to align next to it
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                if (widget == this.authButton) continue;
                if (widget.getWidth() == 152 && widget.getY() == this.height - 52) {
                    btnX = widget.getX() - 24;
                    btnY = widget.getY();
                    break;
                }
            }
        }

        if (this.authButton != null) {
            this.authButton.setX(btnX);
            this.authButton.setY(btnY);
            if (!this.children().contains(this.authButton)) {
                this.addRenderableWidget(this.authButton);
            }
        } else {
            this.authButton = Button.builder(Component.literal("👤"), button -> {
                this.minecraft.setScreen(new com.tasfers.tsfauth.AccountListScreen(this));
            })
            .bounds(btnX, btnY, btnW, btnH)
            .build();
            this.addRenderableWidget(this.authButton);
        }
        
        // Hide original title widget to stop it from jumping on resize
        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                if (widget.getMessage().getString().equals(this.title.getString())) {
                    widget.visible = false;
                }
            }
        }

        this.addRenderableOnly((context, mouseX, mouseY, delta) -> {
            context.drawCenteredString(this.font, "tsf account: " + TsfAuthClient.currentStatus, this.width / 2, 4, 0xFFFFFFFF);
            context.drawCenteredString(this.font, this.title, this.width / 2, 19, 0xFFFFFFFF);

            if (this.authButton != null && this.authButton.visible) {
                int dotColor = com.tasfers.tsfauth.AuthServerStatusChecker.isOnline ? 0xFF55FF55 : 0xFFFF5555;
                int bx = this.authButton.getX() + 8;
                int by = this.authButton.getY() + 4;
                context.fill(bx - 1, by - 1, bx + 5, by + 5, 0xFF000000);
                context.fill(bx, by, bx + 4, by + 4, dotColor);
            }
        });
    }
}
