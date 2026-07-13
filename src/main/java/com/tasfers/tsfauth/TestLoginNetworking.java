package com.tasfers.tsfauth;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.resources.Identifier;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
public class TestLoginNetworking {
    public static void test() {
        ClientLoginNetworking.registerGlobalReceiver(Identifier.fromNamespaceAndPath("tsfauth", "session_sync"), (client, handler, buf, listenerAdder) -> {
            return CompletableFuture.completedFuture(PacketByteBufs.create());
        });
    }
}
