package com.tasfers.tsfauth.mixin;

import com.tasfers.tsfauth.TsfAuthClient;
import com.tasfers.tsfauth.AuthScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @org.spongepowered.asm.mixin.Unique
    private Button authButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.authButton = Button.builder(Component.literal(""), button -> {
            this.minecraft.setScreen(new com.tasfers.tsfauth.AccountListScreen(this));
        })
        .bounds(this.width / 2 - 202, this.height - 52, 44, 44)
        .build();
        this.addRenderableWidget(this.authButton);
        
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
            
            if (this.authButton != null) {
                int btnX = this.authButton.getX();
                int btnY = this.authButton.getY();
                int btnW = this.authButton.getWidth();
                int btnH = this.authButton.getHeight();
                int color = this.authButton.active ? 0xFFFFFFFF : 0xFFA0A0A0;
                
                int gap = 3;
                int textHeight = this.font.lineHeight * 2 + gap;
                int startY = btnY + (btnH - textHeight) / 2;
                
                context.drawCenteredString(this.font, "tsf", btnX + btnW / 2, startY, color);
                context.drawCenteredString(this.font, "auth", btnX + btnW / 2, startY + this.font.lineHeight + gap, color);
            }
        });
    }
}
