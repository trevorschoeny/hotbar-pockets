package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.group.SlotScreenRect;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Per-frame hover tracking for pockets (client). Decides which hotbar slot's
 * pocket column is shown.
 *
 * <p><b>Shown on hover, no keybind.</b> Hovering any hotbar slot shows its two
 * panels: the pockets above (however many are attached) and the plus and minus
 * strip below (always, even with 0 pockets, so plus can add the first). One
 * hover zone covers the strip below, the hotbar slot and the pockets above, so
 * the cursor can move over the whole stack without it closing.
 *
 * <p><b>Driven by {@code ScreenEvents.beforeExtract}</b>, which runs once per
 * frame before the pixel panels resolve their origins (which read this state).
 * All geometry is absolute screen pixels taken from the live hotbar slots
 * ({@link PocketGeometry}), so the same math works on survival and the creative
 * inventory tab. Any other screen ({@link PocketGeometry#pocketsPresent}) clears
 * the state, which hides every pocket there.
 */
public final class PocketHover {

    private PocketHover() {}

    /** Registers the per-frame hover tick. Call once at client init. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> acs) {
                ScreenEvents.beforeExtract(screen).register(
                        (s, graphics, mouseX, mouseY, tickDelta) ->
                                updateHover(acs, mouseX, mouseY));
            }
        });
    }

    private static void updateHover(AbstractContainerScreen<?> screen,
                                    double mouseX, double mouseY) {
        // Keep the server-safe counts current for the slots' reveal predicate.
        PocketState.pushAll();

        if (!PocketsSettings.enabled() || !PocketGeometry.pocketsPresent(screen)) {
            PocketHoverState.setRevealedHotbar(-1);
            return;
        }

        int hovered = columnUnderCursor(screen, mouseX, mouseY);

        // Show on any hotbar hover, even with 0 pockets (the plus and minus
        // strip still shows, so plus can add one). Otherwise stay open while
        // the cursor is anywhere in the zone.
        int currently = PocketHoverState.revealedHotbar();
        int newReveal = -1;
        if (hovered >= 0) {
            newReveal = hovered;
        } else if (currently >= 0 && inSustainZone(screen, mouseX, mouseY, currently)) {
            newReveal = currently;
        }
        PocketHoverState.setRevealedHotbar(newReveal);
    }

    // The hotbar column the cursor is over, counting both the hotbar slot and
    // the plus and minus strip below it, so hovering the strip shows the column
    // too. Geometry comes from the live hotbar slots, so it works on whatever
    // screen the tick fired on.
    private static int columnUnderCursor(AbstractContainerScreen<?> screen,
                                         double mx, double my) {
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            SlotScreenRect r = PocketGeometry.hotbar(screen, n);
            if (r == null) continue;
            // The hotbar slot itself.
            if (inBox(mx, my, r.frameX(), r.y(), 16, 16)) return n;
            // The plus and minus strip just below it.
            if (inBox(mx, my, r.frameX() - 1, PocketGeometry.buttonsTop(r),
                    18, PocketGeometry.BUTTON_PANEL_H)) {
                return n;
            }
        }
        return -1;
    }

    // The zone that keeps a shown column open: the floating pocket row above,
    // the hotbar slot, and the plus and minus strip below, so the cursor can
    // move over the whole stack without it closing. Its width follows the row
    // (which widens with the pocket count); with 0 pockets it is the hotbar
    // slot's column, so the strip alone still stays open.
    private static boolean inSustainZone(AbstractContainerScreen<?> screen,
                                         double mx, double my, int hotbar) {
        SlotScreenRect r = PocketGeometry.hotbar(screen, hotbar);
        if (r == null) return false;
        int c = PocketState.count(hotbar);
        int pad = 2;
        int zleft, zright, ztop;
        if (c > 0) {
            zleft = PocketGeometry.rowX(r, c, 0) - pad;
            zright = PocketGeometry.rowX(r, c, c - 1) + Pockets.SLOT + pad;
            ztop = PocketGeometry.rowY(r) - pad;
        } else {
            zleft = r.frameX() - pad;
            zright = r.frameX() + Pockets.SLOT + pad;
            ztop = r.y() - pad;
        }
        int zbot = PocketGeometry.buttonsTop(r) + PocketGeometry.BUTTON_PANEL_H + pad;
        return inBox(mx, my, zleft, ztop, zright - zleft, zbot - ztop);
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
