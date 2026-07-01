package com.tasfers.tsfauth.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SessionSyncPayload(String version, String modsJson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SessionSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("tsfauth", "session_sync"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SessionSyncPayload> CODEC = CustomPacketPayload.codec(
        SessionSyncPayload::write,
        SessionSyncPayload::new
    );

    private SessionSyncPayload(net.minecraft.network.FriendlyByteBuf buf) {
        this(buf.readUtf(256), buf.readUtf(1048576)); // 1MB for data
    }

    private void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUtf(this.version);
        buf.writeUtf(this.modsJson);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
