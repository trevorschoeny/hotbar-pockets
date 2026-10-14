package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotScreenRect;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The chrome around the shown pockets, on MenuKit pixel panels. The pocket slots
 * themselves are SlotKit's ({@link Pockets}); these two panels follow the shown column:
 * <ul>
 *   <li><b>The backing box</b>: a raised box behind the row, sized to the shown count.
 *       It draws under the pocket panels ({@link Pockets#PANEL_PRIORITY} is one above
 *       it) and is opaque, so a click in its margin doesn't fall through to the
 *       inventory slot the row floats over, while each pocket's own panel sends clicks
 *       on it to its slot.</li>
 *   <li><b>The plus and minus buttons</b> below the hotbar slot, shown even with 0
 *       pockets so plus can add the first.</li>
 * </ul>
 */
public final class PocketPixelPanels {

    private PocketPixelPanels() {}

    /**
     * Opens the Pockets settings and answers true, or answers false when there are no
     * settings to open. Only Inventory Plus has a settings menu, so the {@code ip}
     * package sets this; without IP a Ctrl-click on + or - is a plain click.
     */
    public static volatile BooleanSupplier openSettings = () -> false;

    /** Registers both panels. Call once at client init. */
    public static void register() {
        Panel backing = Panel.builder(HotbarPockets.MOD_ID + ":pocket_backing")
                .elements(List.of(new RowSpacer()))
                .visible(true)
                .style(PanelStyle.RAISED)
                .position(PanelPosition.pixel(PocketPixelPanels::backingOrigin)
                        .priority(Pockets.PANEL_PRIORITY - 1))
                .build();
        new ScreenPanelAdapter(backing, PocketGeometry.ROW_PAD).onPlayerInventory();

        Panel buttons = Panel.builder(HotbarPockets.MOD_ID + ":pocket_buttons")
                .elements(List.of(
                        new ResizeButton(false, 0),
                        new ResizeButton(true, PocketGeometry.BUTTON_SIZE + 1)))
                .visible(true)
                .style(PanelStyle.NONE)
                // Above every ordinary panel (Inventory Plus's buttons included), so
                // plus and minus are never hidden and take their clicks.
                .popover()
                .position(PanelPosition.pixel(PocketPixelPanels::buttonsOrigin))
                .build()
                // Only a hit inside a button counts; the 1px gap between them eats nothing.
                .opaque(false);
        new ScreenPanelAdapter(buttons, 0).onPlayerInventory();
    }

    // The backing box's outer top-left: the row's first frame less the pad. Null unless
    // a column with at least one pocket is shown.
    private static ScreenOrigin backingOrigin() {
        int rev = PocketHoverState.revealedHotbar();
        if (rev < 0) return null;
        int count = PocketHoverState.count(rev);
        if (count <= 0) return null;
        SlotScreenRect anchor = PocketGeometry.shownAnchor(rev);
        if (anchor == null) return null;
        return new ScreenOrigin(
                PocketGeometry.rowX(anchor, count, 0) - PocketGeometry.ROW_PAD,
                PocketGeometry.rowY(anchor) - PocketGeometry.ROW_PAD);
    }

    // The plus and minus strip's top-left: just below the shown column's hotbar slot.
    // Null when no column is shown.
    private static ScreenOrigin buttonsOrigin() {
        int rev = PocketHoverState.revealedHotbar();
        if (rev < 0) return null;
        SlotScreenRect anchor = PocketGeometry.shownAnchor(rev);
        if (anchor == null) return null;
        return new ScreenOrigin(PocketGeometry.buttonsX(anchor), PocketGeometry.buttonsTop(anchor) + 2);
    }

    /** Takes the shown row's size, so the raised box fits 1, 2 or 3 pockets. Draws nothing. */
    private static final class RowSpacer implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() {
            int rev = PocketHoverState.revealedHotbar();
            return rev < 0 ? 0 : PocketHoverState.count(rev) * Pockets.SLOT;
        }
        @Override public int getHeight() { return Pockets.SLOT; }
        @Override public void render(RenderContext ctx) {}
    }

    /** Colors of the boxed buttons. */
    private static final int BTN_BORDER = 0xFF373737;
    private static final int BTN_FACE = 0xFF8B8B8B;
    private static final int BTN_FACE_HOVER = 0xFFFFFFFF;
    private static final int GLYPH = 0xFF202020;
    private static final int GLYPH_DISABLED = 0xFF5A5A5A;

    /**
     * One boxed resize button (minus or plus): a bordered box with a bar or a cross,
     * greyed at its limit (minus at 0 pockets, plus at 3). A greyed button still takes
     * the click and does nothing. The panel only calls {@link #mouseClicked} for hits
     * inside the button. Ctrl-click (Cmd on a Mac) opens the Pockets settings instead,
     * when there are any ({@link #openSettings}), as Inventory Max's buttons did.
     */
    private static final class ResizeButton implements PanelElement {

        private final boolean plus;
        private final int childX;

        ResizeButton(boolean plus, int childX) {
            this.plus = plus;
            this.childX = childX;
        }

        @Override public int getChildX() { return childX; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth()  { return PocketGeometry.BUTTON_SIZE; }
        @Override public int getHeight() { return PocketGeometry.BUTTON_SIZE; }

        /** Enabled while the shown column can still grow or shrink. */
        private boolean enabled() {
            int rev = PocketHoverState.revealedHotbar();
            if (rev < 0) return false;
            int c = PocketHoverState.count(rev);
            return plus ? c < Pockets.MAX_PER_SLOT : c > 0;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            int x = ctx.originX() + childX;
            int y = ctx.originY();
            int w = getWidth(), h = getHeight();
            boolean on = enabled();
            boolean hover = on && ctx.isHovered(childX, 0, w, h);
            g.fill(x, y, x + w, y + h, BTN_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? BTN_FACE_HOVER : BTN_FACE);
            int glyph = on ? GLYPH : GLYPH_DISABLED;
            int cy = y + h / 2;
            int cx = x + w / 2;
            g.fill(x + 2, cy, x + w - 2, cy + 1, glyph);           // horizontal bar
            if (plus) g.fill(cx, y + 2, cx + 1, y + h - 2, glyph); // the vertical bar makes the cross
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (Click.of(button).ctrl() && openSettings.getAsBoolean()) return true;
            int rev = PocketHoverState.revealedHotbar();
            if (rev < 0) return false;
            if (plus) PocketState.grow(rev);
            else PocketState.shrink(rev);
            return true; // consumed either way (greyed at the limit, it does nothing)
        }
    }
}
