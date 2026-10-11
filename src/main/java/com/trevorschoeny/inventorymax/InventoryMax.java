package com.trevorschoeny.inventorymax;

import com.trevlar.menukit.api.window.TriBool;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.containerlocks.ContainerLocks;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;
import com.trevorschoeny.inventorymax.mending.InventoryMendingProvider;
import com.trevorschoeny.inventorymax.operations.InventoryMaxOperations;
import com.trevorschoeny.inventorymax.pocket.PocketEvictC2S;
import com.trevorschoeny.inventorymax.pocket.PocketQuickMoveC2S;
import com.trevorschoeny.inventorymax.pocket.PocketRotateC2S;
import com.trevorschoeny.inventorymax.pocket.PocketServerOps;
import com.trevorschoeny.inventorymax.pocket.Pockets;
import com.trevlar.menukit.containers.api.slot.MendingCandidates;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entrypoint for Inventory Max, the server-side companion
 * to Inventory Plus. Universal: the pocket slots run on the server (to hold and
 * persist their contents) and on the client (to render and take input).
 *
 * <p>IM only adds to IP; it never changes what IP does on its own.
 */
public class InventoryMax implements ModInitializer {

    public static final String MOD_ID = "inventorymax";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Load the config on both sides (idempotent, the client entrypoint
        // calls it too). Otherwise the toggles the server reads (inventory
        // mending, equipment behaviors, container-lock enforcement) silently
        // fall back to defaults on a dedicated server. Server-safe: IMConfig's
        // only IP type is PocketHudMode, a plain enum.
        IMConfig.load();

        // Register the shared container-lock channel. Universal, and it must
        // happen before any container menu opens so the slot state is known
        // wherever a lock is read or written.
        ContainerLocks.register();

        // Player-attached pocket storage (27 slots). Must be registered on both
        // sides before any InventoryMenu is constructed, so the slot mixin can
        // bind it.
        Pockets.register();

        // Player-attached equipment storage (2 slots, elytra and totem). Same
        // constraint: it must precede any InventoryMenu construction so the
        // equipment slot mixin can bind it.
        EquipmentSlots.register();

        // Declare the slots' server behavior by address; the creation mixins
        // build the slots without any. Pockets opt into MENDING; the elytra and
        // totem slots get accept-filter GATING, Curse of Binding and MENDING.
        Pockets.declareSlotBehavior();
        EquipmentSlots.declareSlotBehavior();
        // Slot operations and slot groups. Pocket Cycler is defined as an
        // operation so MenuKit lists it and a lock can refuse it. The
        // groups are declared here rather than left to MenuKit: Containers, which
        // declares a group only when the first inventory menu is built, so they
        // are listed from the title screen. The 27 pockets are one "Pockets" set.
        InventoryMaxOperations.define();
        Pockets.declareGroups();
        EquipmentSlots.declareGroups();

        // C2S payload types are registered on both sides, since the codec must
        // be known wherever the payload travels.
        PayloadTypeRegistry.serverboundPlay().register(PocketRotateC2S.TYPE, PocketRotateC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PocketEvictC2S.TYPE, PocketEvictC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PocketQuickMoveC2S.TYPE, PocketQuickMoveC2S.CODEC);

        // Server-side receivers. They run the authoritative pocket change on the
        // server thread, off the network thread via server.execute.
        ServerPlayNetworking.registerGlobalReceiver(PocketRotateC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server != null) {
                // The operation is named by id; one no mod defined drops the
                // packet rather than being read as some other operation.
                BehaviorKey<TriBool> take = InventoryMaxOperations.served(payload.take());
                BehaviorKey<TriBool> put = InventoryMaxOperations.served(payload.put());
                if (take == null || put == null) return;
                server.execute(() -> PocketServerOps.rotate(
                        sp, payload.hotbar(), payload.count(), payload.forward(), take, put));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PocketEvictC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server != null) {
                server.execute(() -> PocketServerOps.evict(
                        sp, payload.hotbar(), payload.fromDepth(), payload.toDepth()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PocketQuickMoveC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            MinecraftServer server = sp.level().getServer();
            if (server != null) {
                BehaviorKey<TriBool> take = InventoryMaxOperations.served(payload.take());
                BehaviorKey<TriBool> put = InventoryMaxOperations.served(payload.put());
                if (take == null || put == null) return;
                server.execute(() -> PocketServerOps.quickMove(
                        sp, payload.hotbar(), payload.depth(), take, put));
            }
        });

        // Mend Anywhere: feed the vanilla inventory into MKC's XP-repair pool
        // through the mending primitive's consumer hook (gated by the IMConfig
        // toggle, default on). Equipment slots and pockets opt in separately at
        // the slot level.
        MendingCandidates.register(InventoryMendingProvider.INSTANCE);

        LOGGER.info("[inventorymax] Common init, pocket + equipment storage + networking + mending registered.");
    }
}
