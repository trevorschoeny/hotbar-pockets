package com.trevorschoeny.inventorymax.pocket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server request to move the items in a range of a hotbar slot's
 * pockets, depths {@code [fromDepth, toDepth)}, into the player's inventory
 * (or drop them if there's no room).
 *
 * <p>Sent when the player shrinks a pocket panel with the minus button. The
 * count itself is per-world client state; this payload only tells the server
 * which pocket depths to empty, which it does with vanilla's add-or-drop logic
 * ({@link PocketServerOps#evict}).
 */
public record PocketEvictC2S(int hotbar, int fromDepth, int toDepth)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketEvictC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_evict"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketEvictC2S> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.hotbar);
                        buf.writeVarInt(p.fromDepth);
                        buf.writeVarInt(p.toDepth);
                    },
                    buf -> new PocketEvictC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
