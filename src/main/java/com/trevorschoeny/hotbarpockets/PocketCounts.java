package com.trevorschoeny.hotbarpockets;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Each player's pocket counts (0 to 3 behind each hotbar slot), kept on the server.
 *
 * <p>Saved with the player in a Fabric attachment, kept through death, and handed to
 * SlotKit's active count ({@link Pockets#applyCount}) on every join and respawn, since
 * SlotKit's count lasts only a session. The server then knows which pockets exist:
 * one past the count is inert there, so no shift-click or other mod reaches it.
 *
 * <h3>Migration</h3>
 *
 * Until 2.1.0 the client kept counts per world name or server address, and the server
 * none. A player with no saved counts here takes the ones their client sends on join
 * ({@link PocketLegacyCountsC2S}). Every count is also raised to cover the deepest
 * pocket holding an item, so an item is never left in a pocket that has stopped
 * existing, whatever the client sent.
 */
public final class PocketCounts {

    private PocketCounts() {}

    // No initializer: absent means this player's counts were never set here, which is
    // what migration asks.
    private static final AttachmentType<List<Integer>> COUNTS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(HotbarPockets.MOD_ID, "counts"),
            builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> apply(handler.player));
        // A respawn builds a new player whose SlotKit counts start over. Fabric copies the
        // attachment to it in its own AFTER_RESPAWN listener, so this one runs in a later phase.
        Identifier afterCopy = Identifier.fromNamespaceAndPath(HotbarPockets.MOD_ID, "after_attachment_copy");
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, afterCopy);
        ServerPlayerEvents.AFTER_RESPAWN.register(afterCopy, (oldPlayer, newPlayer, alive) -> apply(newPlayer));
    }

    /** The saved count for {@code hotbar}, 0 when none. */
    public static int saved(ServerPlayer player, int hotbar) {
        List<Integer> counts = player.getAttached(COUNTS);
        return counts == null || hotbar >= counts.size() ? 0 : clamp(counts.get(hotbar));
    }

    /** Sets {@code hotbar}'s count, emptying the pockets it drops into the inventory first. */
    public static void set(ServerPlayer player, int hotbar, int count) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        count = clamp(count);
        PocketServerOps.evict(player, hotbar, count, Pockets.MAX_PER_SLOT);
        int[] next = savedAll(player);
        next[hotbar] = count;
        save(player, next);
        apply(player);
    }

    /** Takes the client's pre-2.1.1 counts, only if this player has none saved yet. */
    public static void adopt(ServerPlayer player, int[] legacy) {
        if (player.hasAttached(COUNTS)) return;
        int[] counts = new int[Pockets.HOTBAR_SLOTS];
        for (int n = 0; n < counts.length && n < legacy.length; n++) counts[n] = clamp(legacy[n]);
        save(player, counts);
        apply(player);
    }

    // Saved counts, each raised to the deepest occupied pocket, into SlotKit.
    private static void apply(ServerPlayer player) {
        var pockets = Pockets.POCKETS.bind(player);
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            int count = saved(player, n);
            for (int d = count; d < Pockets.MAX_PER_SLOT; d++) {
                if (!pockets.getStack(Pockets.flatIndex(n, d)).isEmpty()) count = d + 1;
            }
            Pockets.applyCount(player, n, count);
        }
    }

    private static int[] savedAll(ServerPlayer player) {
        int[] counts = new int[Pockets.HOTBAR_SLOTS];
        for (int n = 0; n < counts.length; n++) counts[n] = saved(player, n);
        return counts;
    }

    private static void save(ServerPlayer player, int[] counts) {
        player.setAttached(COUNTS, java.util.Arrays.stream(counts).boxed().toList());
    }

    private static int clamp(int c) {
        return Math.max(0, Math.min(Pockets.MAX_PER_SLOT, c));
    }
}
