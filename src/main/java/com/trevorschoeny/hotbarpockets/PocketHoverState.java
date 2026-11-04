package com.trevorschoeny.hotbarpockets;

/**
 * Server-safe reveal and hover state for pockets, read by each slot's
 * {@code revealWhen} predicate.
 *
 * <p><b>No client imports, on purpose.</b> {@link Pockets#register} runs on both
 * sides and captures {@code () -> PocketHoverState.isRevealed(n, d)} as the reveal
 * predicate. SlotKit only evaluates it on the client (the server keeps slots
 * visible so their contents sync), but the lambda is built on the server too,
 * so this class must not pull in client types. It is a plain set of flags the
 * client updates each frame; the counts are pushed in from the
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
    /** Per-hotbar-slot pocket counts (0 to 3), pushed by the client. */
    private static final int[] counts = new int[Pockets.HOTBAR_SLOTS];

    /**
     * Hotbar slot whose row is shrinking back into its arc after the cursor left it, or -1.
     * Its pockets stay laid out (and drawn) until the shrink ends; the shrinking panels take
     * no input. Set by the client's arc drawing (PocketArcs).
     */
    private static volatile int closingHotbar = -1;

    /** True if pocket (hotbar, depth) should be visible now: its column is shown and its count reaches it. */
    public static boolean isRevealed(int hotbar, int depth) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return false;
        return shownHotbar() == hotbar && counts[hotbar] > depth;
    }

    /** The hovered column: the one the keys and the + and - act on. */
    public static int revealedHotbar() { return revealedHotbar; }
    public static void setRevealedHotbar(int hotbar) { revealedHotbar = hotbar; }

    /** The column whose row is laid out: the hovered one, else one still shrinking away. */
    public static int shownHotbar() { return revealedHotbar >= 0 ? revealedHotbar : closingHotbar; }
    public static void setClosingHotbar(int hotbar) { closingHotbar = hotbar; }

    public static int count(int hotbar) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return 0;
        return counts[hotbar];
    }

    /** The client pushes the live count for a hotbar slot. */
    public static void setCount(int hotbar, int count) {
        if (hotbar < 0 || hotbar >= Pockets.HOTBAR_SLOTS) return;
        counts[hotbar] = count;
    }
}
