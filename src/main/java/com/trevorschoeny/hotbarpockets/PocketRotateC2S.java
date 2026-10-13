package com.trevorschoeny.hotbarpockets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server request to rotate a hotbar slot's pocket cycle.
 *
 * <h3>Why the server does it</h3>
 *
 * Pockets cycle in the world (no screen open) as well as in the inventory. In
 * the world, the client's pocket slots are inert and hidden: they reject clicks
 * and report empty, so rotating them with client-side clicks would desync. The
 * server's pocket slots are always active (SlotKit's reveal is side-aware), so
 * the server rotates the real contents and {@code broadcastChanges} syncs them
 * back. The client only sends the request; {@link PocketServerOps#rotate} does
 * the work.
 *
 * <p>{@code count} is the client's current per-world pocket count for that
 * hotbar slot (the server doesn't track it). Trusting it is harmless, since it
 * only limits which pocket slots take part in the rotation.
 *
 * <p>{@code take} and {@code put} name the operation the rotation serves by id:
 * the pocket's own cycle, or a restock or tool switch reaching a pocket (a
 * restock takes and puts under different operations). The server looks each up
 * in SlotKit and asks {@code SlotOperations.allows} for it on every ring slot
 * before rotating; an id it does not know drops the packet.
 */
public record PocketRotateC2S(int hotbar, int count, boolean forward, Identifier take, Identifier put)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketRotateC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_rotate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketRotateC2S> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.hotbar);
                        buf.writeVarInt(p.count);
                        buf.writeBoolean(p.forward);
                        Identifier.STREAM_CODEC.encode(buf, p.take);
                        Identifier.STREAM_CODEC.encode(buf, p.put);
                    },
                    buf -> new PocketRotateC2S(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                            Identifier.STREAM_CODEC.decode(buf), Identifier.STREAM_CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
