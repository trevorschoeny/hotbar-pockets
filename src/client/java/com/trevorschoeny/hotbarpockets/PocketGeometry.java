package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.group.VanillaSlotResolver;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Screen-space pocket geometry (client only). Every coordinate is an <b>absolute screen
 * pixel</b>, worked out on each call from the live hotbar slots through SlotKit's
 * {@link VanillaSlotResolver}, which is what keeps it right on every container screen,
 * creative's inventory tab included (the resolver unwraps creative's slot wrapper).
 */
public final class PocketGeometry {

    private PocketGeometry() {}

    /** Plus and minus button size (px). */
    public static final int BUTTON_SIZE = 7;
    /** Height of the plus and minus strip (buttons plus 2px above and below). */
    public static final int BUTTON_PANEL_H = BUTTON_SIZE + 4;
    /** Margin between the raised backing box's edge and the pocket row. */
    public static final int ROW_PAD = 4;

    /**
     * Where pocket (hotbar, depth)'s slot frame sits this frame: in the floating row over
     * the shown column. Null (skip the frame) unless that column is shown, the pocket is
     * within its count, and the open screen has the pockets and the hotbar slot.
     */
    static @Nullable ScreenOrigin pocketOrigin(int hotbar, int depth) {
        if (PocketHoverState.revealedHotbar() != hotbar) return null;
        int count = PocketHoverState.count(hotbar);
        if (depth >= count) return null;
        SlotScreenRect anchor = shownAnchor(hotbar);
        return anchor == null ? null : new ScreenOrigin(rowX(anchor, count, depth), rowY(anchor));
    }

    /** The hotbar slot {@code hotbar} on the open screen, when that screen has the pockets. */
    static @Nullable SlotScreenRect shownAnchor(int hotbar) {
        if (!PocketsSettings.enabled()) return null;
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return null;
        if (!pocketsPresent(screen)) return null;
        return hotbar(screen, hotbar);
    }

    /**
     * The hotbar slot {@code n} (0 to 8) as an absolute screen rect, or null when this
     * screen doesn't show it. {@code x/y} is the item's top-left; {@code frameX()/frameY()}
     * is the 18px frame's.
     */
    public static @Nullable SlotScreenRect hotbar(AbstractContainerScreen<?> screen, int n) {
        return VanillaSlotResolver.resolve(screen, n).orElse(null);
    }

    /**
     * Absolute frame x of the pocket at {@code depth} (0 leftmost) when the shown pockets
     * float as one row of {@code count}, centered over hotbar slot {@code hotbar}.
     */
    public static int rowX(SlotScreenRect hotbar, int count, int depth) {
        return hotbar.frameX() + Pockets.SLOT / 2 - (count * Pockets.SLOT) / 2 + depth * Pockets.SLOT;
    }

    /** Absolute y of the floating row: {@link Pockets#HOTBAR_GAP} above the hotbar slot. */
    public static int rowY(SlotScreenRect hotbar) {
        return hotbar.y() - Pockets.HOTBAR_GAP - Pockets.SLOT;
    }

    /** Absolute top of the plus and minus strip, just below the hotbar slot. */
    public static int buttonsTop(SlotScreenRect hotbar) {
        return hotbar.y() + Pockets.SLOT;
    }

    /** Absolute x of the plus and minus strip (the minus button's left edge). */
    public static int buttonsX(SlotScreenRect hotbar) {
        return hotbar.frameX() + 1;
    }

    /**
     * Whether this screen shows pockets: any screen whose menu holds them. SlotKit builds
     * the slots on every container menu, so that's the inventory, creative's inventory tab
     * and every container (a chest, a furnace, a villager). Creative's item tabs have only
     * the hotbar on their menu (SlotKit adds nothing there), so they're left out.
     */
    public static boolean pocketsPresent(AbstractContainerScreen<?> screen) {
        for (Slot s : screen.getMenu().slots) {
            CreatedSlot created = CreatedSlot.of(s);
            if (created != null && Pockets.isPocket(created)) return true;
        }
        return false;
    }
}
