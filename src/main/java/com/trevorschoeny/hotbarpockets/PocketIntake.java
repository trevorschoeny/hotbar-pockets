package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which pockets the server lets a shift-click land in: only the ones the player's
 * client reported Reach allows ({@link PocketIntakeC2S}), none until it has.
 *
 * <h3>Why the server needs telling</h3>
 *
 * The server judges every shift-click itself, and keeps pockets past the player's
 * count inert ({@link PocketCounts}), so those it never fills. But Inventory Plus's
 * Reach, which decides whether a live pocket takes a shift-click, is client-only and
 * absent on a dedicated server. Left alone, a dedicated server with Inventory Plus
 * routed shift-clicks into pockets whatever Reach said. The client reports Reach's
 * answer per pocket, and this veto holds the server to it. A client can only narrow
 * what its own pockets accept, so trusting the report is safe.
 *
 * <p>Only the plain shift-click is judged here. A move run under another operation
 * (a restock's quick-move) is judged as that operation, by its own rules.
 */
public final class PocketIntake {

    private PocketIntake() {}

    // Player UUID to the last reported mask. Keyed by UUID, so a respawn's new
    // ServerPlayer keeps it; dropped on disconnect.
    private static final Map<UUID, Integer> ACCEPTS = new ConcurrentHashMap<>();

    public static void register() {
        SlotOperations.veto((ref, operation) ->
                operation.equals(BehaviorKeys.SHIFT_CLICK_IN) && denies(ref.player(), ref.slot()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ACCEPTS.remove(handler.player.getUUID()));
    }

    /** Records what {@code player}'s client said its pockets accept. */
    public static void report(Player player, int mask) {
        ACCEPTS.put(player.getUUID(), mask);
    }

    // Server side only: the client judges its own pockets by reveal and its own vetoes.
    private static boolean denies(Player player, net.minecraft.world.inventory.Slot slot) {
        if (player == null || slot == null || player.level().isClientSide()) return false;
        CreatedSlot created = CreatedSlot.of(slot);
        if (created == null || !Pockets.isPocket(created)) return false;
        int mask = ACCEPTS.getOrDefault(player.getUUID(), 0);
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
                if (Pockets.isPocket(created, n, d)) return (mask & (1 << Pockets.flatIndex(n, d))) == 0;
            }
        }
        return true;
    }
}
