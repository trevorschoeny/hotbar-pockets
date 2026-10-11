package com.trevorschoeny.inventorymax.pocket;

import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventorymax.InventoryMax;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Client-side, per-world pocket <b>counts</b>: how many pockets (0 to 3) are
 * attached to each of the 9 hotbar slots. The 27 slots always exist on the
 * server; this decides how many of each hotbar slot's 3 are shown and usable.
 *
 * <p>Client-side and per world, like Column Cycler's slot membership: players
 * have different layouts per world, and the count only controls what the client
 * shows. The server doesn't track it (the rotation and eviction payloads carry
 * the count). Saved to {@code config/inventorymax/pockets.json}.
 *
 * <p>Pushes counts into the server-safe {@link PocketHoverState} so the slot's
 * reveal predicate sees them.
 */
public final class PocketState {

    private PocketState() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Nine zeros: no pockets on any hotbar slot. The store's "nothing stored" value. */
    private static final List<Integer> NO_POCKETS = Collections.nCopies(Pockets.HOTBAR_SLOTS, 0);

    /**
     * Pocket count (0 to 3) for each of the 9 hotbar slots, per world. Inventory
     * Plus's {@link WorldStore}: an immutable list per world, read without
     * creating anything, saved only when a count actually changes. A world
     * with no pockets anywhere is not stored at all, which is what the old
     * "skip all-zero worlds" check in save() was reaching for.
     *
     * <p>Before this, {@code count(hotbar)} was a read that created an entry,
     * so rendering the pocket HUD wrote a nine-zero array for the current
     * world every frame, and the array itself was mutated in place.
     */
    private static final WorldStore<List<Integer>> COUNTS =
            WorldStore.of(NO_POCKETS, List::copyOf, PocketState::save);
    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventorymax").resolve("pockets.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld") ? root.getAsJsonObject("perWorld") : new JsonObject();
            int worlds = 0;
            for (var e : perWorld.entrySet()) {
                JsonObject w = e.getValue().getAsJsonObject();
                List<Integer> counts = new ArrayList<>(NO_POCKETS);
                if (w.has("counts")) {
                    JsonArray arr = w.getAsJsonArray("counts");
                    for (int i = 0; i < Pockets.HOTBAR_SLOTS && i < arr.size(); i++) {
                        counts.set(i, clamp(arr.get(i).getAsInt()));
                    }
                }
                COUNTS.load(e.getKey(), counts);
                if (!NO_POCKETS.equals(counts)) worlds++;
            }
            InventoryMax.LOGGER.info("[pockets] loaded counts for {} world(s)", worlds);
        } catch (IOException | JsonSyntaxException | IllegalStateException ex) {
            InventoryMax.LOGGER.error("[pockets] failed to read {} — starting empty", path, ex);
        }
    }

    private static int clamp(int c) {
        return Math.max(0, Math.min(Pockets.MAX_PER_SLOT, c));
    }

    public static int count(int hotbar) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return 0;
        return COUNTS.get().get(hotbar);   // a read: never creates an entry
    }

    private static void setCount(int hotbar, int value) {
        int clamped = clamp(value);
        COUNTS.modify(counts -> {
            List<Integer> next = new ArrayList<>(counts);
            next.set(hotbar, clamped);
            return next;
        });
        // Push what the store now holds, so the hover state can never disagree with it.
        PocketHoverState.setCount(hotbar, count(hotbar));
    }

    /** Grows a pocket panel by one, up to 3. At 0 this adds the first pocket. */
    public static void grow(int hotbar) {
        int c = count(hotbar);
        if (c < Pockets.MAX_PER_SLOT) setCount(hotbar, c + 1);
    }

    /**
     * Shrinks a pocket panel by one. Moves the removed top pocket's item to the
     * inventory (or drops it) through a server payload, then lowers the count.
     * At count 1 this removes the last pocket.
     */
    public static void shrink(int hotbar) {
        int c = count(hotbar);
        if (c <= 0) return;
        int newCount = c - 1;
        // Server evicts the now-removed pocket depths [newCount, c).
        ClientPlayNetworking.send(new PocketEvictC2S(hotbar, newCount, c));
        setCount(hotbar, newCount);
    }

    /** Pushes every hotbar slot's count into the server-safe hover state. */
    public static void pushAll() {
        List<Integer> counts = COUNTS.get();
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            PocketHoverState.setCount(n, counts.get(n));
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            // The store never holds an all-zero world, so every world it visits is written.
            COUNTS.forEachWorld((worldId, counts) -> {
                JsonArray arr = new JsonArray();
                counts.forEach(arr::add);
                JsonObject w = new JsonObject();
                w.add("counts", arr);
                perWorld.add(worldId, w);
            });
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException ex) {
            InventoryMax.LOGGER.error("[pockets] failed to write {} — counts won't persist", path, ex);
        }
    }
}
