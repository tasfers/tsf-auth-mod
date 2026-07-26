package com.tasfers.tsfauth.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {

    @ModifyVariable(
        method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private static Component modifyReason(Component reason) {
        if (reason != null) {
            String reasonStr = reason.getString();
            if (reasonStr.contains("error code: 521") || 
                reasonStr.contains("error code: 501") || 
                reasonStr.contains("error code: 502") || 
                reasonStr.contains("error code: 503") || 
                reasonStr.contains("error code: 504") ||
                reasonStr.contains("521") ||
                reasonStr.contains("502") ||
                reasonStr.contains("501")) {
                
                return Component.translatable("disconnect.loginFailed.info");
            }
        }
        return reason;
    }
}
