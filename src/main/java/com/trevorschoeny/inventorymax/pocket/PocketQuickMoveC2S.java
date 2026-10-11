package com.trevorschoeny.inventorymax.pocket;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server request to quick-move one pocket's contents to wherever a
 * shift-click would send them. This is the restock path for slots the player
 * isn't holding (a totem refilling the equipment totem slot, a new armor piece
 * equipping after one breaks).
 *
 * <h3>Why the server does it</h3>
 *
 * Like rotation ({@link PocketRotateC2S}), this can't be a client-side
 * shift-click. Restock fires in the world (no screen open), where the client's
 * pocket slots are inert: they reject clicks and report empty. The server's
 * pocket slots are always active, so the server runs the real
 * {@code quickMoveStack} from the pocket slot and lets the existing routing
 * (vanilla for armor, {@code InventoryMenuQuickMoveMixin} for the totem and
 * elytra slots) move the item to the right place, then {@code broadcastChanges}
 * syncs it back.
 *
 * <p>Restocking a held tool does <b>not</b> use this; it rotates the item into
 * the hand through {@link PocketCyclable#bringToHotbar}. This is only for slots
 * the player isn't holding (totem, armor, offhand).
 *
 * <p>{@code take} and {@code put} name the operation the move serves by id (a
 * restock, in practice). The server looks each up in MenuKit, asks {@code take}
 * about the pocket before moving and runs the quick-move under both, so MenuKit
 * judges the destination as that operation rather than a shift-click; an id it
 * does not know drops the packet.
 */
public record PocketQuickMoveC2S(int hotbar, int depth, Identifier take, Identifier put)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketQuickMoveC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_quick_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketQuickMoveC2S> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.hotbar);
                        buf.writeVarInt(p.depth);
                        Identifier.STREAM_CODEC.encode(buf, p.take);
                        Identifier.STREAM_CODEC.encode(buf, p.put);
                    },
                    buf -> new PocketQuickMoveC2S(buf.readVarInt(), buf.readVarInt(),
                            Identifier.STREAM_CODEC.decode(buf), Identifier.STREAM_CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
