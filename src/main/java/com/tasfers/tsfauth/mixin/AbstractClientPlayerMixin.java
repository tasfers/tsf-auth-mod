package com.tasfers.tsfauth.mixin;

import com.tasfers.tsfauth.AccountManager;
import com.tasfers.tsfauth.SkinFetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jetbrains.annotations.Nullable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Shadow
    @Nullable
    protected abstract PlayerInfo getPlayerInfo();

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;

        if (Minecraft.getInstance().hasSingleplayerServer() || this.getPlayerInfo() == null) {
            String uuidStr = player.getGameProfile().id().toString();
            PlayerSkin customSkin = SkinFetcher.createPlayerSkin(uuidStr);

            if (customSkin == null) {
                String username = player.getGameProfile().name();
                AccountManager.Account acc = AccountManager.getAccountByUsername(username);
                if (acc != null && acc.uuid != null) {
                    customSkin = SkinFetcher.createPlayerSkin(acc.uuid);
                    if (customSkin == null) {
                        SkinFetcher.getSkin(acc.uuid);
                    }
                } else {
                    String activeUuid = AccountManager.getActiveUuid();
                    if (activeUuid != null && !activeUuid.isEmpty()) {
                        customSkin = SkinFetcher.createPlayerSkin(activeUuid);
                        if (customSkin == null) {
                            SkinFetcher.getSkin(activeUuid);
                        }
                    }
                }
            }

            if (customSkin != null) {
                cir.setReturnValue(customSkin);
            }
        }
    }
}
