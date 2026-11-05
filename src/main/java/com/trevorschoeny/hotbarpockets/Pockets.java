package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroupSet;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.storage.StorageAttachment.PlayerStorageAttachment;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The pockets: their storage, their ids, and their 27 SlotKit groups.
 *
 * <h3>Structure</h3>
 *
 * SlotKit builds a fixed set of slots when a menu is built; it can't add or remove
 * slots per player later. So the maximum always exists, 3 pockets behind each of the
 * 9 hotbar slots (27 in all), as real, server-synced, saved slots, and the player's
 * count ({@link PocketCounts}, kept on the server) decides how many of each hotbar
 * slot's 3 are live. Each pocket opts in to SlotKit's active count, so one past the
 * count is inert on both sides: invisible, unclickable and skipped by shift-clicks,
 * while its item stays saved on the server.
 *
 * <p>Each (hotbar, depth) pocket is its own single-slot {@link ParityGroup}, with its
 * own reveal predicate. The client shows each in its own MenuKit panel with its own pixel
 * origin over its hotbar slot ({@code PocketPixelPanels}), so each depth shows on its own
 * and the row re-centers as the count changes. SlotKit builds the groups on
 * every container menu on both sides with no mixin of ours, and its join check compares
 * the 27 layouts, so a client whose pockets differ from the server's is refused instead
 * of desyncing.
 */
public final class Pockets {

    private Pockets() {}

    public static final String MOD_ID = HotbarPockets.MOD_ID;

    /** Hotbar slots (0 to 8). */
    public static final int HOTBAR_SLOTS = 9;
    /** Max pockets behind one hotbar slot. */
    public static final int MAX_PER_SLOT = 3;
    /** Total pocket slots (9 × 3). */
    public static final int TOTAL = HOTBAR_SLOTS * MAX_PER_SLOT;

    /** Vanilla slot pitch. */
    public static final int SLOT = 18;
    /** Gap between the hotbar slot and the bottom of the floating pocket row. */
    public static final int HOTBAR_GAP = 8;

    /** Each pocket's one group. SlotKit derives the group id from it and the owner (panel) id. */
    public static final String GROUP = "slot";

    /** The one row the 27 pockets share in slot group listings (any settings screen). */
    public static final SlotGroupSet POCKET_SET = new SlotGroupSet(MOD_ID, "pockets");

    /** The 27 stacks, saved with the player on the server. Drop on death as the inventory does. */
    public static PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS;

    // Each pocket's group, by flatIndex, for its active count.
    private static final ParityGroup[] GROUPS = new ParityGroup[TOTAL];

    /** Call once from the main entrypoint, on both sides. */
    public static void register() {
        POCKETS = StorageAttachment.playerAttached(MOD_ID, "pockets", TOTAL);

        for (int n = 0; n < HOTBAR_SLOTS; n++) {
            for (int d = 0; d < MAX_PER_SLOT; d++) {
                final int hotbar = n;
                final int depth = d;
                // The set must be named on the group's first declaration, so declare
                // before ParityGroup.register(), which declares the group without one.
                // PLAYER_INVENTORY: a pocket is general storage, so a mod searching the
                // player's inventory (Mend Anywhere, IP's Restock) finds stacks in it.
                SlotGroups.declare(groupId(n, d), SlotGroupCategory.PLAYER_INVENTORY, POCKET_SET);

                // The 1.x panel id stays the owner, so the group id, the saved stacks and
                // every setting keyed to them carry over unchanged.
                GROUPS[flatIndex(n, d)] = ParityGroup.register(panelId(n, d), SlotSpec.at(GROUP, SlotGroupCategory.PLAYER_INVENTORY)
                                // A 1-slot window onto the shared 27-stack attachment. The
                                // slice also fires the advancement trigger on server writes.
                                .storage(p -> new SliceStorage(POCKETS.bind(p), flatIndex(hotbar, depth), 1, p))
                                .label("Pocket")
                                // Live (1) only while the player's count reaches this
                                // depth; the server sets it (PocketCounts).
                                .activeCount(0)
                                // Read only on the client: shown while this column is the
                                // hovered one and its count reaches this depth.
                                .revealWhen(() -> PocketHoverState.isRevealed(hotbar, depth))
                                // Pockets take no part in vanilla's bulk moves: a shift-click,
                                // a double-click or a drag never pours into or pulls from one,
                                // with or without Inventory Plus (Trev, 2026-11-05). Shift-click
                                // out always works, so a pocket can be emptied quickly.
                                .shiftClickIn(false)
                                .collect(false)
                                .dragFill(false));
            }
        }
    }

    /** How many pockets {@code hotbar} has live for {@code player}, on the player's side. */
    public static int count(Player player, int hotbar) {
        int c = 0;
        while (c < MAX_PER_SLOT && GROUPS[flatIndex(hotbar, c)].activeCount(player) > 0) c++;
        return c;
    }

    /** Server: makes depths {@code 0..count-1} of {@code hotbar} live and the rest inert. */
    static void applyCount(ServerPlayer player, int hotbar, int count) {
        for (int d = 0; d < MAX_PER_SLOT; d++) GROUPS[flatIndex(hotbar, d)].setActiveCount(player, d < count ? 1 : 0);
    }

    /** Flat index into the 27-stack attachment for (hotbar, depth). */
    public static int flatIndex(int hotbar, int depth) {
        return hotbar * MAX_PER_SLOT + depth;
    }

    /** The owner (and client panel) id of pocket (hotbar, depth): {@code hotbarpockets:pocket_<hotbar>_<depth>}. */
    public static String panelId(int hotbar, int depth) {
        return MOD_ID + ":pocket_" + hotbar + "_" + depth;
    }

    /** The slot group of pocket (hotbar, depth), as SlotKit names it. */
    public static SlotGroupId.Created groupId(int hotbar, int depth) {
        return ParityGroup.idOf(panelId(hotbar, depth), GROUP);
    }

    /** True when {@code slot} is pocket (hotbar, depth). */
    public static boolean isPocket(CreatedSlot slot, int hotbar, int depth) {
        SlotGroupId.Created id = groupId(hotbar, depth);
        return id.panelId().equals(slot.panelId()) && id.groupId().equals(slot.groupId());
    }

    /** True when {@code slot} is any pocket (used to tell whether a screen's menu has them). */
    public static boolean isPocket(CreatedSlot slot) {
        return slot.panelId().startsWith(MOD_ID + ":pocket_");
    }
}
