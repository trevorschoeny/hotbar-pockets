package com.trevorschoeny.inventorymax.pocket;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.SlotGroupSet;
import com.trevlar.menukit.api.slot.SlotGroups;
import com.trevlar.menukit.containers.api.slot.ContainerKeys;
import com.trevlar.menukit.containers.api.storage.StorageAttachment;
import com.trevlar.menukit.api.window.TriBool;
import com.trevlar.menukit.api.window.Window;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Pocket constants and the server-persistent pocket storage.
 *
 * <h3>Structure</h3>
 *
 * The slot kit adds a <b>fixed</b> set of slots at menu construction; it can't
 * add or remove slots per player later. So the <b>maximum</b> is always added,
 * 3 pocket slots for each of the 9 hotbar slots (27 total), as real,
 * server-synced, persistent slots, and a <b>client-side per-world count</b>
 * ({@link PocketState}) decides how many of each hotbar slot's 3 pockets are
 * shown and usable. Hidden pockets are inert: invisible and non-interactive,
 * but their contents still persist on the server. Adding a pocket just raises
 * the count from 0; resizing changes it (1 to 3).
 *
 * <p>Each (hotbar, depth) pocket is its own single-slot panel with its own
 * reveal predicate. That is what lets each depth show on its own, which the
 * panel-level reveal API can't do (one panel shows all or nothing).
 */
public final class Pockets {

    private Pockets() {}

    public static final String MOD_ID = "inventorymax";

    /** Hotbar slots (0 to 8). */
    public static final int HOTBAR_SLOTS = 9;
    /** Max pockets stacked above one hotbar slot. */
    public static final int MAX_PER_SLOT = 3;
    /** Total registered pocket slots (9 × 3). */
    public static final int TOTAL = HOTBAR_SLOTS * MAX_PER_SLOT;

    // Screen-relative layout (added to leftPos/topPos at render time).
    /** Vanilla hotbar slot row y. */
    public static final int HOTBAR_Y = 142;
    /**
     * X of the pocket column (and hotbar-slot hover). 7, not 8: a registered
     * slot's 18px frame must sit 1px left of the vanilla item x so the pocket
     * frame lines up with the vanilla hotbar slot frame below it (at 8 both
     * panels were a pixel too far right).
     */
    public static final int HOTBAR0_X = 7;
    /** Vanilla slot pitch. */
    public static final int SLOT = 18;
    /** Gap between the hotbar slot and the bottom of the pocket column. */
    public static final int HOTBAR_GAP = 8;
    /** Height of the plus and minus button row drawn above the topmost pocket. */
    public static final int BUTTON_ROW_H = 9;

    /**
     * Player-attached pocket storage: 27 slots, survives logout and restart.
     * Declared at common init. Each slot takes its own (hotbar, depth) slice.
     */
    public static StorageAttachment<Player, NonNullList<ItemStack>> POCKETS;

    /** Call from the main entrypoint at common init. */
    public static void register() {
        POCKETS = StorageAttachment.playerAttached(MOD_ID, "pockets", TOTAL);
    }

    /**
     * Declares each pocket slot's MENDING opt-in by the slot's address, once at
     * common init (the creation mixin builds the slots without behavior). A
     * damaged Mending item in any pocket joins the XP-orb repair pool. Idempotent.
     */
    public static void declareSlotBehavior() {
        for (int n = 0; n < HOTBAR_SLOTS; n++) {
            for (int d = 0; d < MAX_PER_SLOT; d++) {
                String g = groupId(n, d);
                Window.slot(Address.createdSlot(CreatedSlots.groupId(panelId(n, d), g), 0))
                        .set(ContainerKeys.MENDING, TriBool.TRUE);
            }
        }
    }

    /** Flat index into the 27-slot backing for (hotbar, depth). */
    public static int flatIndex(int hotbar, int depth) {
        return hotbar * MAX_PER_SLOT + depth;
    }

    /** Slot group id for (hotbar, depth). */
    public static String groupId(int hotbar, int depth) {
        return "pocket_" + hotbar + "_" + depth;
    }

    /**
     * The panel id a pocket's slot is registered under. One source for the
     * creation mixin, the behavior declarations and the group declarations, so a
     * mismatch can never produce a second, set-less group when the menu builds.
     */
    public static String panelId(int hotbar, int depth) {
        return MOD_ID + ":" + groupId(hotbar, depth);
    }

    /** The one row the 27 pockets share in MenuKit's slot group listing. */
    public static final SlotGroupSet POCKET_SET = new SlotGroupSet(MOD_ID, "pockets");

    /**
     * Declares the 27 pocket groups with MenuKit at common init: player inventory
     * storage (searches should find a stack in a pocket), all in {@link #POCKET_SET}
     * so a settings screen shows one "Pockets" row rather than 27. Idempotent.
     */
    public static void declareGroups() {
        for (int n = 0; n < HOTBAR_SLOTS; n++) {
            for (int d = 0; d < MAX_PER_SLOT; d++) {
                SlotGroups.declare(CreatedSlots.groupId(panelId(n, d), groupId(n, d)),
                        SlotGroupCategory.PLAYER_INVENTORY, POCKET_SET);
            }
        }
    }

    /**
     * True for any pocket slot's group id (see {@link #groupId}). Used to check
     * whether a screen's live menu actually has the pockets.
     */
    public static boolean isPocketGroup(String groupId) {
        return groupId.startsWith("pocket_");
    }

    /** Screen-relative x of the pocket column for a hotbar slot. */
    public static int pocketX(int hotbar) {
        return HOTBAR0_X + hotbar * SLOT;
    }

    /**
     * Screen-relative y of a pocket at {@code depth} (0 = closest to the
     * hotbar). Pockets stack upward from just above the hotbar.
     *
     * <p>This is the original <b>vertical</b> column layout, now used only as the
     * slot's starting position. At runtime the shown pockets move into the
     * floating horizontal row ({@link PocketGeometry#rowX} and
     * {@link PocketGeometry#rowY}); each frame the pocket's {@code SlotElement}
     * writes that row position straight into the live slot's {@code Slot.x/y}
     * (since MenuKit 3.2.0), which is what vanilla actually draws.
     */
    public static int pocketY(int depth) {
        return HOTBAR_Y - HOTBAR_GAP - (depth + 1) * SLOT;
    }

    // The floating-row and live-hotbar geometry (absolute screen pixels, worked
    // out each frame from the actual hotbar slots, creative included) is in the
    // client-only PocketGeometry, so this class stays server-safe for the slot
    // mixin and PocketHoverState.
}
