package com.trevorschoeny.hotbarpockets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;


import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's side of pocket <b>counts</b>: how many pockets (0 to 3) each of the 9
 * hotbar slots has. The server owns them ({@link PocketCounts}) and SlotKit syncs them
 * as each pocket's active count; this reads that copy and asks the server to change it.
 *
 * <p>Pushes counts into the server-safe {@link PocketHoverState} for the slots' reveal
 * predicates and the panels.
 *
 * <h3>Migration</h3>
 *
 * Until 2.1.0 the client saved counts per world name or server address in
 * {@code config/hotbarpockets/pockets.json}. That file is now only read: on joining a
 * world, its counts for that world go to the server once ({@link PocketLegacyCountsC2S}),
 * which takes them only if it has none of its own for the player.
 */
public final class PocketState {

    private PocketState() {}

    /** World id to its nine saved counts, from the pre-2.1.1 file. */
    private static final Map<String, int[]> LEGACY = new ConcurrentHashMap<>();

    /** Reads the legacy file and sends this world's counts on every join. */
    public static void register() {
        loadLegacy();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            String world = worldId();
            int[] counts = world == null ? null : LEGACY.get(world);
            if (counts != null && ClientPlayNetworking.canSend(PocketLegacyCountsC2S.TYPE)) {
                sender.sendPacket(new PocketLegacyCountsC2S(counts));
            }
        });
    }

    /** {@code singleplayer:<level name>} or {@code server:<address>}, as the file keys them. */
    private static @Nullable String worldId() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer sp = mc.getSingleplayerServer();
        if (sp != null) return "singleplayer:" + sp.getWorldData().getLevelName();
        ServerData server = mc.getCurrentServer();
        return server != null ? "server:" + server.ip : null;
    }

    // Package-private for CountChecks, which writes a legacy file and reads it again.
    static void loadLegacy() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(HotbarPockets.MOD_ID).resolve("pockets.json");
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld") ? root.getAsJsonObject("perWorld") : new JsonObject();
            for (var e : perWorld.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonObject().getAsJsonArray("counts");
                int[] counts = new int[Pockets.HOTBAR_SLOTS];
                for (int i = 0; arr != null && i < counts.length && i < arr.size(); i++) counts[i] = arr.get(i).getAsInt();
                LEGACY.put(e.getKey(), counts);
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | ClassCastException ex) {
            HotbarPockets.LOGGER.error("[hotbarpockets] failed to read {}, no counts to carry over", path, ex);
        }
    }

    /** The count for a hotbar slot, as the server last sent it; 0 outside a world. */
    public static int count(int hotbar) {
        var player = Minecraft.getInstance().player;
        if (player == null || hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return 0;
        return Pockets.count(player, hotbar);
    }

    /** Asks the server for one more pocket behind a hotbar slot, up to 3. */
    public static void grow(int hotbar) {
        int c = count(hotbar);
        if (c < Pockets.MAX_PER_SLOT) ClientPlayNetworking.send(new PocketCountC2S(hotbar, c + 1));
    }

    /**
     * Asks the server for one fewer. It moves the removed top pocket's item into the
     * inventory, or drops it if there's no room, then drops the count.
     */
    public static void shrink(int hotbar) {
        int c = count(hotbar);
        if (c > 0) ClientPlayNetworking.send(new PocketCountC2S(hotbar, c - 1));
    }

    /** Pushes every hotbar slot's count into the server-safe hover state. */
    public static void pushAll() {
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) PocketHoverState.setCount(n, count(n));
    }
}
