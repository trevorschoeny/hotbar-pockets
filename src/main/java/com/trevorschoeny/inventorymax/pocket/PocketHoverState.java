package com.trevorschoeny.inventorymax.pocket;

/**
 * Server-safe reveal and hover state for pockets, read by each slot's
 * {@code revealWhen} predicate.
 *
 * <p><b>No client imports, on purpose.</b> The slot mixin runs on both sides
 * and captures {@code () -> PocketHoverState.isRevealed(n, d)} as the reveal
 * predicate. MenuKit only evaluates it on the client (the server keeps slots
 * visible so their contents sync), but the lambda is built on the server too,
 * so this class must not pull in client types. It is a plain set of flags the
 * client updates each frame; the per-world counts are pushed in from the
 * client-side {@link PocketState}.
 *
 * <h3>Reveal model</h3>
 *
 * One hotbar slot's pocket column shows at a time (the hovered one, with
 * hysteresis). A pocket (hotbar, depth) shows only if that hotbar slot is the
 * revealed one and the player's configured count for it is greater than depth.
 */
public final class PocketHoverState {

    private PocketHoverState() {}

    /** Hotbar slot whose pocket column is currently revealed, or -1. */
    private static volatile int revealedHotbar = -1;
    /** Hotbar slot directly under the cursor (for the attach keybind), or -1. */
    private static volatile int hoveredHotbar = -1;
    /** Pocket depth under the cursor within the revealed column, or -1. */
    private static volatile int hoveredDepth = -1;
    /** Per-hotbar-slot pocket counts (0 to 3), pushed by the client. */
    private static final int[] counts = new int[Pockets.HOTBAR_SLOTS];

    /** True if pocket (hotbar, depth) should be visible and interactive now. */
    public static boolean isRevealed(int hotbar, int depth) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return false;
        return revealedHotbar == hotbar && counts[hotbar] > depth;
    }

    public static int revealedHotbar() { return revealedHotbar; }
    public static void setRevealedHotbar(int hotbar) { revealedHotbar = hotbar; }

    public static int hoveredHotbar() { return hoveredHotbar; }
    public static void setHoveredHotbar(int hotbar) { hoveredHotbar = hotbar; }

    public static int hoveredDepth() { return hoveredDepth; }
    public static void setHoveredDepth(int depth) { hoveredDepth = depth; }

    public static int count(int hotbar) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return 0;
        return counts[hotbar];
    }

    /** The client pushes the live per-world count for a hotbar slot. */
    public static void setCount(int hotbar, int count) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        counts[hotbar] = count;
    }
}
