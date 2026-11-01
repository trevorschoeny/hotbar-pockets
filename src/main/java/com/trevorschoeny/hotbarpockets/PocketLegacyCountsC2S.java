package com.trevorschoeny.hotbarpockets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server, once per join: the nine counts this client saved for this world
 * before the server kept them (2.1.0 and earlier, {@code pockets.json}). The server
 * adopts them only for a player it has no counts for yet ({@link PocketCounts#adopt}),
 * so a player keeps their layout through the update.
 */
public record PocketLegacyCountsC2S(int[] counts) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketLegacyCountsC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_legacy_counts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketLegacyCountsC2S> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarIntArray(p.counts),
                    buf -> new PocketLegacyCountsC2S(buf.readVarIntArray(Pockets.HOTBAR_SLOTS)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
