package com.trevorschoeny.inventorymax.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventorymax.InventoryMax;
import com.trevorschoeny.inventorymax.pocket.PocketHudMode;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global IM config — feature toggles for Pocket Cycler. Mirrors IP's
 * {@code IPConfig} static-field + JSON pattern. Persisted to
 * {@code config/inventorymax/config.json}.
 *
 * <p>IM owns its own config file + ModMenu entry rather than extending IP's
 * (dependency direction is IM→IP; IM storing state in IP's file would invert
 * ownership). Reuses IP's {@link PocketHudMode} enum for the shared cycle HUD.
 *
 * <p>Per-world pocket counts are NOT here — those live per-world in
 * {@code PocketState} (different layouts per world, same as Column Cycler's
 * slot membership).
 */
public final class IMConfig {

    private IMConfig() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Pocket Cycler master toggle. Default ON — installing IM is itself the
    // opt-in; pockets are a manual feature (not automation), so the
    // conservative-automation-default rule doesn't apply.
    private static boolean pocketCyclerEnabled = true;
    // Shared cycle HUD mode for pockets (None / Mini-hotbar). Default
    // MINI_HOTBAR, matching Column Cycler.
    private static PocketHudMode pocketHudMode = PocketHudMode.MINI_HOTBAR;
    // "Pockets supply automation" is gone (Reach build stage 4): whether
    // Restock or Auto Tool Switch may take from pockets is the Pockets box in
    // their reach, in Inventory Plus's Reach tab. A player who had it off gets
    // those boxes cleared once, on upgrade (InventoryMaxClient).
    private static @org.jetbrains.annotations.Nullable Boolean legacyPocketsSupplyAutomation;
    // Equipment Slots (elytra + totem) master toggle. Default ON. OFF hides
    // the slots (content persists, inert — same model as a 0-count pocket
    // column) and disarms the slot behaviors: glide, totem-fires-last,
    // totem auto-restock, HUD cues, shift-click routing. The behavior gates
    // are read where the code runs (client render / server mixins) — in
    // singleplayer both read the same file; on a dedicated server the
    // server's config governs the server-side behaviors (mending pattern).
    private static boolean equipmentSlotsEnabled = true;
    // The elytra/totem HUD cue icons beside the hotbar (sub of Equipment
    // Slots). Default ON — matches shipped behavior.
    private static boolean equipmentHudCue = true;
    // Each slot on its own under the master (Trev, 2026-10-04). Off hides that
    // slot and disarms its behaviors only: elytra = glide + elytra routing,
    // totem = totem-fires-last + totem restock + totem routing.
    private static boolean elytraSlotEnabled = true;
    private static boolean totemSlotEnabled = true;
    // The on/off switches in Inventory Plus's inventory column (Trev, 2026-10-04).
    // Hidden by default (Trev, 2026-10-11 smoke: "too many buttons"); each
    // feature's own default and keys are unchanged.
    private static boolean pocketsShowButton = false;
    private static boolean equipmentSlotsShowButton = false;
    private static boolean mendAnywhereShowButton = false;
    // Container locks: the old master toggle is gone (Reach build stage 4).
    // Whether a player's container locks are also kept on the server is that
    // player's choice, Inventory Plus's "Also keep container locks on the
    // server"; the server enforces whatever the shared channel holds.
    // "Mend any item in the inventory" — ambient XP mending across the whole
    // vanilla inventory, not just equipped/registered. Default ON (the feature is
    // the point; equip + pocket mending are separate always-on slot opt-ins).
    // Gates only the vanilla-inventory mend provider, read server-side.
    private static boolean mendInventoryItems = true;

    /**
     * Every setting at its default, taken before the file is read, so a tab's
     * Reset to Defaults ({@link #reset}) never restates a default by hand.
     * Declared after every field, so each is at its default here.
     */
    private static final JsonObject DEFAULTS = settingsJson();

    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventorymax")
                .resolve("config.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) {
            InventoryMax.LOGGER.info("[config] no config at {} — using defaults", path);
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            apply(root);
            if (root.has("pocketsSupplyAutomation")) {
                legacyPocketsSupplyAutomation = readBool(root, "pocketsSupplyAutomation", true);
            }
            InventoryMax.LOGGER.info("[config] loaded from {}", path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryMax.LOGGER.error("[config] failed to read {} — using defaults", path, e);
        }
    }

    /** Reads every setting {@code root} has; the rest keep their values. */
    private static void apply(JsonObject root) {
        pocketCyclerEnabled = readBool(root, "pocketCyclerEnabled", pocketCyclerEnabled);
        pocketHudMode = PocketHudMode.fromName(readString(root, "pocketHudMode", null), pocketHudMode);
        equipmentSlotsEnabled = readBool(root, "equipmentSlotsEnabled", equipmentSlotsEnabled);
        equipmentHudCue = readBool(root, "equipmentHudCue", equipmentHudCue);
        elytraSlotEnabled = readBool(root, "elytraSlotEnabled", elytraSlotEnabled);
        totemSlotEnabled = readBool(root, "totemSlotEnabled", totemSlotEnabled);
        pocketsShowButton = readBool(root, "pocketsShowButton", pocketsShowButton);
        equipmentSlotsShowButton = readBool(root, "equipmentSlotsShowButton", equipmentSlotsShowButton);
        mendAnywhereShowButton = readBool(root, "mendAnywhereShowButton", mendAnywhereShowButton);
        mendInventoryItems = readBool(root, "mendInventoryItems", mendInventoryItems);
    }

    /** Puts the settings named in {@code keys} back to their defaults and saves. */
    public static void reset(String... keys) {
        JsonObject part = new JsonObject();
        for (String key : keys) {
            if (!DEFAULTS.has(key)) throw new IllegalArgumentException("[config] no setting " + key);
            part.add(key, DEFAULTS.get(key));
        }
        apply(part);
        save();
    }

    /** Every setting back to its default, and saves: the settings menu's Reset everything. */
    public static void resetAll() {
        apply(DEFAULTS);
        save();
    }

    private static boolean readBool(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive()
                ? root.get(key).getAsBoolean() : fallback;
    }

    private static String readString(JsonObject root, String key, String fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive()
                ? root.get(key).getAsString() : fallback;
    }

    /** Every setting as the file writes it, without the version. */
    private static JsonObject settingsJson() {
        JsonObject root = new JsonObject();
        root.addProperty("pocketCyclerEnabled", pocketCyclerEnabled);
        root.addProperty("pocketHudMode", pocketHudMode.name());
        root.addProperty("equipmentSlotsEnabled", equipmentSlotsEnabled);
        root.addProperty("equipmentHudCue", equipmentHudCue);
        root.addProperty("elytraSlotEnabled", elytraSlotEnabled);
        root.addProperty("totemSlotEnabled", totemSlotEnabled);
        root.addProperty("pocketsShowButton", pocketsShowButton);
        root.addProperty("equipmentSlotsShowButton", equipmentSlotsShowButton);
        root.addProperty("mendAnywhereShowButton", mendAnywhereShowButton);
        root.addProperty("mendInventoryItems", mendInventoryItems);
        return root;
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            settingsJson().entrySet().forEach(e -> root.add(e.getKey(), e.getValue()));
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryMax.LOGGER.error("[config] failed to write {} — changes won't persist", path, e);
        }
    }

    public static boolean pocketCyclerEnabled() { return pocketCyclerEnabled; }
    public static void setPocketCyclerEnabled(boolean v) { pocketCyclerEnabled = v; save(); }

    public static PocketHudMode pocketHudMode() { return pocketHudMode; }
    public static void setPocketHudMode(PocketHudMode v) { pocketHudMode = v; save(); }

    /**
     * The removed "pockets supply automation" setting as the file last held
     * it, or {@code null} when the file never had it. For the one-time upgrade
     * into Reach; {@link #forgetLegacy} drops it from the file.
     */
    public static @org.jetbrains.annotations.Nullable Boolean legacyPocketsSupplyAutomation() {
        return legacyPocketsSupplyAutomation;
    }

    /** Rewrites the file without the removed settings, so an upgrade runs once. */
    public static void forgetLegacy() {
        legacyPocketsSupplyAutomation = null;
        save();
    }

    public static boolean equipmentSlotsEnabled() { return equipmentSlotsEnabled; }
    public static void setEquipmentSlotsEnabled(boolean v) { equipmentSlotsEnabled = v; save(); }

    public static boolean equipmentHudCue() { return equipmentHudCue; }
    public static void setEquipmentHudCue(boolean v) { equipmentHudCue = v; save(); }

    public static boolean elytraSlotEnabled() { return elytraSlotEnabled; }
    public static void setElytraSlotEnabled(boolean v) { elytraSlotEnabled = v; save(); }
    public static boolean totemSlotEnabled() { return totemSlotEnabled; }
    public static void setTotemSlotEnabled(boolean v) { totemSlotEnabled = v; save(); }

    /** The elytra slot is live: Equipment Slots on and the elytra slot on. */
    public static boolean elytraSlotOn() { return equipmentSlotsEnabled && elytraSlotEnabled; }
    /** The totem slot is live: Equipment Slots on and the totem slot on. */
    public static boolean totemSlotOn() { return equipmentSlotsEnabled && totemSlotEnabled; }

    public static boolean pocketsShowButton() { return pocketsShowButton; }
    public static void setPocketsShowButton(boolean v) { pocketsShowButton = v; save(); }
    public static boolean equipmentSlotsShowButton() { return equipmentSlotsShowButton; }
    public static void setEquipmentSlotsShowButton(boolean v) { equipmentSlotsShowButton = v; save(); }
    public static boolean mendAnywhereShowButton() { return mendAnywhereShowButton; }
    public static void setMendAnywhereShowButton(boolean v) { mendAnywhereShowButton = v; save(); }


    public static boolean mendInventoryItems() { return mendInventoryItems; }
    public static void setMendInventoryItems(boolean v) { mendInventoryItems = v; save(); }
}
