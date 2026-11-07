package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.slot.SlotFlowElement;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The shown pockets and their chrome, on MenuKit pixel panels. The pocket slots are
 * SlotKit groups ({@link Pockets}); these panels show while their column does:
 * <ul>
 *   <li><b>The pockets</b>: one flush single-slot panel per (hotbar, depth), at its place
 *       in the floating row ({@link PocketGeometry#pocketOrigin}), or skipped when that
 *       pocket isn't shown. Opaque, so the panel claims its 18x18 and a pocket wins over
 *       the inventory slot it floats over.</li>
 *   <li><b>The backing box</b>: a raised box behind the row, sized to the shown count.
 *       It draws under the pocket panels ({@link #POCKET_PRIORITY} is one above
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

    /**
     * Above the backing box (lower draws first, so under). MenuKit's default is 100; the
     * box takes one below it.
     */
    private static final int POCKET_PRIORITY = PanelPosition.DEFAULT_PRIORITY + 1;

    /** Registers the pocket panels and the chrome. Call once at client init. */
    public static void register() {
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
                final int hotbar = n;
                final int depth = d;
                Panel pocket = Panel.builder(Pockets.panelId(n, d))
                        .add(SlotFlowElement.of(Pockets.groupId(n, d), 1))
                        // Flush: the slot draws its own frame; the backing box draws the
                        // raised row behind the shown pockets.
                        .style(PanelStyle.NONE)
                        // Both modes, one placement: the origin is worked out from the live
                        // hotbar slot, which is wherever the open screen puts it.
                        .in(ModeScope.BOTH, Placement.screen(
                                PanelPosition.pixel(() -> PocketGeometry.pocketOrigin(hotbar, depth))
                                        .priority(POCKET_PRIORITY)))
                        .build()
                        .visibleWhen(PocketsSettings::enabled)
                        // Grows out of the arc and fades in as the row opens (PocketArcs).
                        .transform(() -> PocketArcs.rowTransform(hotbar));
                // Every container screen: pockets are ever-present. Where the menu has no
                // pockets (a creative item tab) the origin is null and nothing draws.
                new ScreenPanelAdapter(pocket, 0).onAny();
            }
        }

        // A backing box and a plus and minus strip per column, so a row shrinking away
        // keeps its chrome while the next column's grows (PocketArcs).
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) registerChrome(n);
    }

    private static void registerChrome(int hotbar) {
        Panel backing = Panel.builder(HotbarPockets.MOD_ID + ":pocket_backing_" + hotbar)
                .elements(List.of(new RowSpacer(hotbar)))
                .visible(true)
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> backingOrigin(hotbar))
                        .priority(POCKET_PRIORITY - 1)))
                .build()
                .transform(() -> PocketArcs.rowTransform(hotbar));
        new ScreenPanelAdapter(backing, PocketGeometry.ROW_PAD).onAny();

        Panel buttons = Panel.builder(HotbarPockets.MOD_ID + ":pocket_buttons_" + hotbar)
                .elements(List.of(
                        new ResizeButton(hotbar, false, 0),
                        new ResizeButton(hotbar, true, PocketGeometry.BUTTON_SIZE + 1)))
                .visible(true)
                .style(PanelStyle.NONE)
                // Above every ordinary panel (Inventory Plus's buttons included), so
                // plus and minus are never hidden and take their clicks.
                .popover()
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> buttonsOrigin(hotbar))))
                .build()
                // Only a hit inside a button counts; the 1px gap between them eats nothing.
                .opaque(false)
                // Grows out from under the slot and fades in as the row grows out of the arc.
                .transform(() -> PocketArcs.buttonsTransform(hotbar));
        new ScreenPanelAdapter(buttons, 0).onAny();
    }

    // The backing box's outer top-left: the row's first frame less the pad. Null unless
    // the column is shown with at least one pocket.
    private static ScreenOrigin backingOrigin(int rev) {
        if (!PocketHoverState.isShown(rev)) return null;
        int count = PocketHoverState.count(rev);
        if (count <= 0) return null;
        SlotScreenRect anchor = PocketGeometry.shownAnchor(rev);
        if (anchor == null) return null;
        return new ScreenOrigin(
                PocketGeometry.rowX(anchor, count, 0) - PocketGeometry.ROW_PAD,
                PocketGeometry.rowY(anchor) - PocketGeometry.ROW_PAD);
    }

    // The plus and minus strip's top-left: just below the column's hotbar slot, while it's
    // shown (hovered or still shrinking away). Null otherwise.
    private static ScreenOrigin buttonsOrigin(int rev) {
        if (!PocketHoverState.isShown(rev)) return null;
        SlotScreenRect anchor = PocketGeometry.shownAnchor(rev);
        if (anchor == null) return null;
        return new ScreenOrigin(PocketGeometry.buttonsX(anchor), PocketGeometry.buttonsTop(anchor) + 2);
    }

    /** Takes its column's row size, so the raised box fits 1, 2 or 3 pockets. Draws nothing. */
    private record RowSpacer(int hotbar) implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return PocketHoverState.count(hotbar) * Pockets.SLOT; }
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
     * grayed at its limit (minus at 0 pockets, plus at 3). A grayed button still takes
     * the click and does nothing. The panel only calls {@link #mouseClicked} for hits
     * inside the button. Ctrl-click (Cmd on a Mac) opens the Pockets settings instead,
     * when there are any ({@link #openSettings}), as Inventory Max's buttons did.
     */
    private static final class ResizeButton implements PanelElement {

        private final int hotbar;
        private final boolean plus;
        private final int childX;

        ResizeButton(int hotbar, boolean plus, int childX) {
            this.hotbar = hotbar;
            this.plus = plus;
            this.childX = childX;
        }

        @Override public int getChildX() { return childX; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth()  { return PocketGeometry.BUTTON_SIZE; }
        @Override public int getHeight() { return PocketGeometry.BUTTON_SIZE; }

        /** Enabled while the column can still grow or shrink. */
        private boolean enabled() {
            int c = PocketHoverState.count(hotbar);
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
            // Only the hovered column's strip acts; one shrinking away is just looks.
            if (PocketHoverState.revealedHotbar() != hotbar) return false;
            if (plus) PocketState.grow(hotbar);
            else PocketState.shrink(hotbar);
            return true; // consumed either way (grayed at the limit, it does nothing)
        }
    }
}
