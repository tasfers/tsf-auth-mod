package com.tasfers.tsfauth.mixin;

import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(User.class)
public interface UserAccessor {
    @Mutable
    @Accessor("name")
    void setYggName(String name);

    @Mutable
    @Accessor("uuid")
    void setYggProfileId(java.util.UUID uuid);

    @Mutable
    @Accessor("accessToken")
    void setYggAccessToken(String accessToken);
}
