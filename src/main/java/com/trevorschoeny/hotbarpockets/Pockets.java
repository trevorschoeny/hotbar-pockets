package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroupSet;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevlar.slotkit.api.slot.ContainerPanel;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.storage.StorageAttachment.PlayerStorageAttachment;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The pockets: their storage, their ids, and their 27 SlotKit panels.
 *
 * <h3>Structure</h3>
 *
 * SlotKit builds a fixed set of slots when a menu is built; it can't add or remove
 * slots per player later. So the maximum always exists, 3 pockets behind each of the
 * 9 hotbar slots (27 in all), as real, server-synced, saved slots, and a client-side
 * per-world count ({@code PocketState}) decides how many of each hotbar slot's 3 are
 * shown and usable. A hidden pocket is inert: invisible and unclickable, but its item
 * stays saved on the server.
 *
 * <p>Each (hotbar, depth) pocket is its own single-slot {@link ContainerPanel}, with its
 * own reveal predicate and its own pixel origin over its hotbar slot, so each depth
 * shows on its own and the row re-centres as the count changes. SlotKit builds them on
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

    /** Each pocket panel's one group. SlotKit derives the group id from it and the panel id. */
    public static final String GROUP = "slot";

    /** The one row the 27 pockets share in slot group listings (IP's Reach, any settings screen). */
    public static final SlotGroupSet POCKET_SET = new SlotGroupSet(MOD_ID, "pockets");

    /**
     * Above the backing box the client draws behind a shown row (lower draws first,
     * so under). MenuKit's default is 100; the box takes one below it.
     */
    public static final int PANEL_PRIORITY = PanelPosition.DEFAULT_PRIORITY + 1;

    /** The 27 stacks, saved with the player on the server. Drop on death as the inventory does. */
    public static PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS;

    /**
     * Where pocket (hotbar, depth) sits on the open screen this frame, in absolute screen
     * pixels, or {@code null} to skip it. The panels are declared here in common code,
     * but only the client ever asks; the client sets this at init ({@code PocketGeometry}),
     * which is how a server-safe declaration reaches client-only screen geometry.
     */
    @FunctionalInterface
    public interface Origin {
        @Nullable ScreenOrigin of(int hotbar, int depth);
    }

    /** Never asked on a server. Replaced by the client at init. */
    public static volatile Origin origin = (hotbar, depth) -> null;

    /** Call once from the main entrypoint, on both sides. */
    public static void register() {
        POCKETS = StorageAttachment.playerAttached(MOD_ID, "pockets", TOTAL);

        for (int n = 0; n < HOTBAR_SLOTS; n++) {
            for (int d = 0; d < MAX_PER_SLOT; d++) {
                final int hotbar = n;
                final int depth = d;
                // The set must be named on the group's first declaration, so declare
                // before ContainerPanel.register(), which declares the group without one.
                // PLAYER_INVENTORY: a pocket is general storage, so a mod searching the
                // player's inventory (Mend Anywhere, IP's Restock) finds stacks in it.
                SlotGroups.declare(groupId(n, d), SlotGroupCategory.PLAYER_INVENTORY, POCKET_SET);

                ContainerPanel.define(panelId(n, d))
                        .at(PanelPosition.pixel(() -> origin.of(hotbar, depth)).priority(PANEL_PRIORITY), 0)
                        // Flush: the slot draws its own frame; the client's backing box
                        // draws the raised row behind the shown pockets.
                        .style(PanelStyle.NONE)
                        // Opaque (the default): the panel claims its 18x18, which sends
                        // the point to its own slot, so a pocket wins over the inventory
                        // slot it floats over.
                        .visibleWhen(PocketsSettings::enabled)
                        .addSlot(SlotSpec.at(GROUP, SlotGroupCategory.PLAYER_INVENTORY)
                                // A 1-slot window onto the shared 27-stack attachment. The
                                // slice also fires the advancement trigger on server writes.
                                .storage(p -> new SliceStorage(POCKETS.bind(p), flatIndex(hotbar, depth), 1, p))
                                .label("Pocket")
                                // Read only on the client: shown while this column is the
                                // hovered one and its count reaches this depth.
                                .revealWhen(() -> PocketHoverState.isRevealed(hotbar, depth))
                                // Pockets take no part in vanilla's bulk moves:
                                // a double-click, a chest's shift-click routing or a drag
                                // never pulls from or pours into one. Shift-click out stays,
                                // so a pocket can still be emptied quickly.
                                .shiftClickIn(false)
                                .collect(false)
                                .dragFill(false))
                        .register();
            }
        }
    }

    /** Flat index into the 27-stack attachment for (hotbar, depth). */
    public static int flatIndex(int hotbar, int depth) {
        return hotbar * MAX_PER_SLOT + depth;
    }

    /** The panel id of pocket (hotbar, depth): {@code hotbarpockets:pocket_<hotbar>_<depth>}. */
    public static String panelId(int hotbar, int depth) {
        return MOD_ID + ":pocket_" + hotbar + "_" + depth;
    }

    /** The slot group of pocket (hotbar, depth), as SlotKit names it. */
    public static SlotGroupId.Created groupId(int hotbar, int depth) {
        return ContainerPanel.groupId(panelId(hotbar, depth), GROUP);
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
