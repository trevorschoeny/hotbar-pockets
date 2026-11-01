package com.trevorschoeny.hotbarpockets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server request to set how many pockets {@code hotbar} has, sent by the
 * + and - buttons. The server owns the count ({@link PocketCounts#set}): it empties
 * any pockets the new count drops into the inventory, then tells the client back
 * through SlotKit's active count.
 */
public record PocketCountC2S(int hotbar, int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketCountC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_count"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketCountC2S> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.hotbar);
                        buf.writeVarInt(p.count);
                    },
                    buf -> new PocketCountC2S(buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
