package com.tasfers.tsfauth.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
public class ServerInfoMixin {

    @Inject(method = "enforcesSecureChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void onEnforcesSecureChat(CallbackInfoReturnable<Boolean> cir) {
        // Bypass the client-side chat lock.
        // If a server sets enforce-secure-profile=false, it still expects unsigned messages.
        // But if the client is missing keys, the client disables the chat box entirely.
        // This mixin prevents the client from disabling the chat box.
        cir.setReturnValue(false);
    }
}
