package com.trevorschoeny.hotbarpockets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hotbar Pockets' settings, the one place they live: plain fields, loaded from and
 * saved to {@code config/hotbarpockets/config.json}, as Inventory Max's IMConfig was.
 * Every setter saves. The two cycle keys are vanilla key mappings and save with the
 * game's options, not here.
 *
 * <p>All of them are client settings; only client code reads them. Pocket counts aren't
 * settings: they're per-world state ({@code PocketState}, in the same folder).
 *
 * <p>Changed from the Pockets tab in Inventory Plus's settings menu when IP is
 * installed. Without IP there's no menu and the defaults (or the file) apply.
 */
public final class PocketsSettings {

    private PocketsSettings() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // The defaults, named once so load and reset agree.
    private static final boolean DEFAULT_ENABLED = true;
    private static final PocketHudMode DEFAULT_HUD = PocketHudMode.MINI_HOTBAR;
    private static final boolean DEFAULT_SHOW_BUTTON = false;

    // Off hides every pocket and stops the keys; the items stay in their pockets.
    private static volatile boolean enabled = DEFAULT_ENABLED;
    // Beside the hotbar: the mini hotbar (MenuKit's cycle strip) or nothing.
    private static volatile PocketHudMode hud = DEFAULT_HUD;
    // Inventory Plus's column switch for this mod. Hidden by default.
    private static volatile boolean showButton = DEFAULT_SHOW_BUTTON;

    public static boolean enabled()     { return enabled; }
    public static PocketHudMode hud()   { return hud; }
    public static boolean showButton()  { return showButton; }

    public static void setEnabled(boolean v)       { enabled = v; save(); }
    public static void setHud(PocketHudMode v)     { hud = v; save(); }
    public static void setShowButton(boolean v)    { showButton = v; save(); }

    /** Every setting back to its default, and saves: Reset to Defaults and IP's Reset everything. */
    public static void reset() {
        enabled = DEFAULT_ENABLED;
        hud = DEFAULT_HUD;
        showButton = DEFAULT_SHOW_BUTTON;
        save();
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(HotbarPockets.MOD_ID).resolve("config.json");
    }

    /**
     * Reads the file once, at client init. A missing file, or a missing or unreadable
     * key, leaves that setting at its default. Nothing is written until a setting changes.
     */
    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.has("enabled")) enabled = root.get("enabled").getAsBoolean();
            if (root.has("showButton")) showButton = root.get("showButton").getAsBoolean();
            if (root.has("hud")) {
                // An unknown name (hand-edited, or from a later version) keeps the default.
                try {
                    hud = PocketHudMode.valueOf(root.get("hud").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | ClassCastException
                 | UnsupportedOperationException ex) {
            HotbarPockets.LOGGER.error("[hotbarpockets] failed to read {}, using defaults", path, ex);
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("hud", hud.name());
            root.addProperty("showButton", showButton);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException ex) {
            HotbarPockets.LOGGER.error("[hotbarpockets] failed to write {}, settings won't persist", path, ex);
        }
    }
}
