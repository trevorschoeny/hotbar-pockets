package com.trevorschoeny.hotbarpockets;

/**
 * Hotbar Pockets' settings, in one place so only this class changes when ConfigKit
 * lands (beryl-split.md, "Settings"). Until then each setting is a plain field at its
 * default. With ConfigKit it becomes:
 * <pre>
 * public static final Settings S = Settings.of("hotbarpockets");
 * public static final Setting&lt;Boolean&gt; ENABLED = S.bool("enabled", true);
 * public static final Setting&lt;PocketHudMode&gt; HUD = S.choice("hud", PocketHudMode.MINI_HOTBAR);
 * public static final Setting&lt;Boolean&gt; SHOW_BUTTON = S.bool("show_button", false); // listed only with Inventory Plus
 * // and the two cycle keys: S.key(PocketKeys.CYCLE_FORWARD), S.key(PocketKeys.CYCLE_BACKWARD)
 * </pre>
 * All of them are client settings. Pocket counts aren't settings: they're per-world
 * state ({@code PocketState}).
 */
public final class PocketsSettings {

    private PocketsSettings() {}

    // Off hides every pocket and stops the keys; the items stay in their pockets.
    private static volatile boolean enabled = true;
    // Beside the hotbar: the mini hotbar (MenuKit's cycle strip) or nothing.
    private static volatile PocketHudMode hud = PocketHudMode.MINI_HOTBAR;
    // Inventory Plus's column switch for this mod. Hidden by default.
    private static volatile boolean showButton = false;

    public static boolean enabled()     { return enabled; }
    public static PocketHudMode hud()   { return hud; }
    public static boolean showButton()  { return showButton; }

    // ponytail: setters are not saved anywhere until ConfigKit; a toggle lasts the session.
    public static void setEnabled(boolean v)       { enabled = v; }
    public static void setHud(PocketHudMode v)     { hud = v; }
    public static void setShowButton(boolean v)    { showButton = v; }
}
