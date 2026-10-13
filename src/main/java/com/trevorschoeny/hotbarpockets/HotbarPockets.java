package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.TriBool;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entrypoint. The pocket slots run on the server, which holds,
 * saves and rotates their contents, and on the client, which shows them and takes input.
 */
public final class HotbarPockets implements ModInitializer {

    public static final String MOD_ID = "hotbarpockets";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // The storage and the 27 panels, on both sides, before any menu is built.
        Pockets.register();
        PocketOperations.define();

        // Payload types are known on both sides, wherever the payload travels.
        PayloadTypeRegistry.serverboundPlay().register(PocketRotateC2S.TYPE, PocketRotateC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PocketEvictC2S.TYPE, PocketEvictC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PocketQuickMoveC2S.TYPE, PocketQuickMoveC2S.CODEC);

        // The server's half of each request, run on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(PocketRotateC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server == null) return;
            // The operation is named by id; one no mod defined drops the packet.
            BehaviorKey<TriBool> take = PocketOperations.served(payload.take());
            BehaviorKey<TriBool> put = PocketOperations.served(payload.put());
            if (take == null || put == null) return;
            server.execute(() -> PocketServerOps.rotate(
                    sp, payload.hotbar(), payload.count(), payload.forward(), take, put));
        });
        ServerPlayNetworking.registerGlobalReceiver(PocketEvictC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server == null) return;
            server.execute(() -> PocketServerOps.evict(
                    sp, payload.hotbar(), payload.fromDepth(), payload.toDepth()));
        });
        ServerPlayNetworking.registerGlobalReceiver(PocketQuickMoveC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server == null) return;
            BehaviorKey<TriBool> take = PocketOperations.served(payload.take());
            BehaviorKey<TriBool> put = PocketOperations.served(payload.put());
            if (take == null || put == null) return;
            server.execute(() -> PocketServerOps.quickMove(
                    sp, payload.hotbar(), payload.depth(), take, put));
        });
    }
}
