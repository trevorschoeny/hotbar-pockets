package com.trevorschoeny.hotbarpockets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Client-side, per-world pocket <b>counts</b>: how many pockets (0 to 3) each of the 9
 * hotbar slots shows. The 27 slots always exist on the server; this decides how many of
 * each hotbar slot's 3 are shown and usable. Players keep a different layout per world,
 * and the server never tracks it (the rotate and evict payloads carry the count).
 * A world starts with none. Saved to {@code config/hotbarpockets/pockets.json}.
 *
 * <p>Pushes counts into the server-safe {@link PocketHoverState} for the slots' reveal
 * predicates.
 */
// ponytail: a small file of our own until ConfigKit 1.0; this moves to ConfigKit's
// WorldStore, cleared by its "Reset everything" (onResetAll), with no change for callers.
public final class PocketState {

    private PocketState() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Nine zeros: no pockets anywhere. A world holding this is not stored at all. */
    private static final List<Integer> NO_POCKETS = Collections.nCopies(Pockets.HOTBAR_SLOTS, 0);

    /** World id to its nine counts, immutable lists. Sorted, so the file is stable. */
    private static final Map<String, List<Integer>> BY_WORLD = Collections.synchronizedMap(new TreeMap<>());

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(HotbarPockets.MOD_ID).resolve("pockets.json");
    }

    /**
     * Where the player is: {@code singleplayer:<level name>} or {@code server:<address>},
     * or null outside a world (reads give no pockets, writes do nothing).
     */
    private static @Nullable String worldId() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer sp = mc.getSingleplayerServer();
        if (sp != null) return "singleplayer:" + sp.getWorldData().getLevelName();
        ServerData server = mc.getCurrentServer();
        return server != null ? "server:" + server.ip : null;
    }

    /** Reads the file once, at client init. A missing or broken file starts empty. */
    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld") ? root.getAsJsonObject("perWorld") : new JsonObject();
            for (var e : perWorld.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonObject().getAsJsonArray("counts");
                List<Integer> counts = new ArrayList<>(NO_POCKETS);
                for (int i = 0; arr != null && i < Pockets.HOTBAR_SLOTS && i < arr.size(); i++) {
                    counts.set(i, clamp(arr.get(i).getAsInt()));
                }
                if (!NO_POCKETS.equals(counts)) BY_WORLD.put(e.getKey(), List.copyOf(counts));
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | ClassCastException ex) {
            HotbarPockets.LOGGER.error("[hotbarpockets] failed to read {}, starting empty", path, ex);
        }
    }

    private static int clamp(int c) {
        return Math.max(0, Math.min(Pockets.MAX_PER_SLOT, c));
    }

    private static List<Integer> current() {
        String world = worldId();
        return world == null ? NO_POCKETS : BY_WORLD.getOrDefault(world, NO_POCKETS);
    }

    /** The count for a hotbar slot in this world. A read: never creates an entry. */
    public static int count(int hotbar) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return 0;
        return current().get(hotbar);
    }

    private static void setCount(int hotbar, int value) {
        String world = worldId();
        if (world == null) return;
        List<Integer> next = new ArrayList<>(current());
        next.set(hotbar, clamp(value));
        if (NO_POCKETS.equals(next)) BY_WORLD.remove(world);
        else BY_WORLD.put(world, List.copyOf(next));
        save();
        // Push what is now stored, so the hover state can never disagree with it.
        PocketHoverState.setCount(hotbar, count(hotbar));
    }

    /** Grows a hotbar slot's pockets by one, up to 3. At 0 this adds the first. */
    public static void grow(int hotbar) {
        int c = count(hotbar);
        if (c < Pockets.MAX_PER_SLOT) setCount(hotbar, c + 1);
    }

    /**
     * Shrinks a hotbar slot's pockets by one. The server moves the removed top pocket's
     * item into the inventory, or drops it if there's no room; then the count drops.
     */
    public static void shrink(int hotbar) {
        int c = count(hotbar);
        if (c <= 0) return;
        ClientPlayNetworking.send(new PocketEvictC2S(hotbar, c - 1, c));
        setCount(hotbar, c - 1);
    }

    /** Pushes every hotbar slot's count into the server-safe hover state. */
    public static void pushAll() {
        List<Integer> counts = current();
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) PocketHoverState.setCount(n, counts.get(n));
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject perWorld = new JsonObject();
            synchronized (BY_WORLD) {
                BY_WORLD.forEach((world, counts) -> {
                    JsonArray arr = new JsonArray();
                    counts.forEach(arr::add);
                    JsonObject w = new JsonObject();
                    w.add("counts", arr);
                    perWorld.add(world, w);
                });
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException ex) {
            HotbarPockets.LOGGER.error("[hotbarpockets] failed to write {}, counts won't persist", path, ex);
        }
    }
}
