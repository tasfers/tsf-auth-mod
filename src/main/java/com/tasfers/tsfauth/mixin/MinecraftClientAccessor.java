package com.tasfers.tsfauth.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("user")
    @Mutable
    void setUser(User user);

    @Accessor("user")
    User getUser();

    @Accessor("userApiService")
    com.mojang.authlib.minecraft.UserApiService getUserApiService();

    @Accessor("userApiService")
    @Mutable
    void setUserApiService(com.mojang.authlib.minecraft.UserApiService apiService);

    @Accessor("profileKeyPairManager")
    @Mutable
    void setProfileKeyPairManager(net.minecraft.client.multiplayer.ProfileKeyPairManager manager);

    @Accessor("profileKeyPairManager")
    net.minecraft.client.multiplayer.ProfileKeyPairManager getProfileKeyPairManager();
}
