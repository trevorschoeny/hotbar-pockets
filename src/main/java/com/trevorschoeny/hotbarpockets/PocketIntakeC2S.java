package com.trevorschoeny.hotbarpockets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server report of which pockets may take a shift-click: bit
 * {@code flatIndex(hotbar, depth)} is set when that pocket is within the client's
 * count and the client's own rules (Inventory Plus's Reach) allow a shift-click
 * into it. Sent on every container screen and every count change; see
 * {@link PocketIntake} for why the server needs it.
 */
public record PocketIntakeC2S(int mask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PocketIntakeC2S> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Pockets.MOD_ID, "pocket_intake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketIntakeC2S> CODEC =
            StreamCodec.of((buf, p) -> buf.writeVarInt(p.mask), buf -> new PocketIntakeC2S(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
