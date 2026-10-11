package com.trevorschoeny.inventorymax.pocket;

import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.api.slot.SlotScreenRect;
import com.trevlar.menukit.api.slot.VanillaSlotResolver;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Screen-space pocket geometry (client-only). Every coordinate here is an
 * <b>absolute screen pixel</b>, worked out on each call from the live hotbar
 * slot positions via MenuKit's {@link VanillaSlotResolver}. That is the same
 * coordinate space the pixel-panel origin suppliers and element input use,
 * which is what keeps the geometry right on any screen that shows the player
 * inventory (survival and the creative inventory tab, where the resolver
 * unwraps the creative {@code SlotWrapper}).
 *
 * <p>Kept apart from {@link Pockets} because resolving needs the <em>screen</em>
 * (a client type); {@code Pockets} stays server-safe for the slot mixin and
 * {@link PocketHoverState}.
 */
public final class PocketGeometry {

    private PocketGeometry() {}

    /** Plus and minus button size (px). */
    public static final int BUTTON_SIZE = 7;
    /** Height of the plus and minus button strip (buttons plus 2px above and below). */
    public static final int BUTTON_PANEL_H = BUTTON_SIZE + 4;
    /** Margin between the raised pockets-panel edge and the slot row. */
    public static final int ROW_PAD = 4;

    /**
     * The hotbar slot {@code n} (0 to 8) as an absolute screen rect, or null when
     * this screen doesn't show it. {@code rect.x/y} is the item's top-left;
     * {@code rect.frameX()/frameY()} is the 18px frame's top-left.
     */
    public static @Nullable SlotScreenRect hotbar(AbstractContainerScreen<?> screen, int n) {
        return VanillaSlotResolver.resolve(screen, n).orElse(null);
    }

    /**
     * Absolute frame-x of the pocket at {@code depth} (0 = leftmost) when the
     * revealed pockets float as one horizontal row of {@code count} slots,
     * centered over hotbar slot {@code hotbar}. {@code SLOT/2 - count*SLOT/2}
     * shifts the row left so its center sits over the hotbar slot's center;
     * {@code depth*SLOT} steps right across the row.
     */
    public static int rowX(SlotScreenRect hotbar, int count, int depth) {
        return hotbar.frameX() + Pockets.SLOT / 2 - (count * Pockets.SLOT) / 2
                + depth * Pockets.SLOT;
    }

    /**
     * Absolute y of the floating pocket row: one slot height above the hotbar
     * item row, leaving {@link Pockets#HOTBAR_GAP} between the row's bottom
     * edge and the hotbar slot's top.
     */
    public static int rowY(SlotScreenRect hotbar) {
        return hotbar.y() - Pockets.HOTBAR_GAP - Pockets.SLOT;
    }

    /** Absolute top of the plus and minus strip, just below the hotbar slot. */
    public static int buttonsTop(SlotScreenRect hotbar) {
        return hotbar.y() + Pockets.SLOT;
    }

    /** Absolute origin x of the plus and minus strip (the minus button's left edge). */
    public static int buttonsX(SlotScreenRect hotbar) {
        return hotbar.frameX() + 1;
    }

    /**
     * Whether this screen's live menu actually has the pocket slots: true on the
     * survival inventory (the {@code CreatedSlot} sits directly in the menu) and
     * the creative <em>inventory</em> tab (wrapped, and unwrapped by
     * {@link CreatedSlot#of}); false everywhere else. The hotbar alone isn't
     * enough to go on: creative's other tabs show a hotbar but not the pockets,
     * and container screens show neither.
     */
    public static boolean pocketsPresent(AbstractContainerScreen<?> screen) {
        for (Slot s : screen.getMenu().slots) {
            CreatedSlot mk = CreatedSlot.of(s);
            if (mk != null && Pockets.isPocketGroup(mk.groupId())) return true;
        }
        return false;
    }
}
