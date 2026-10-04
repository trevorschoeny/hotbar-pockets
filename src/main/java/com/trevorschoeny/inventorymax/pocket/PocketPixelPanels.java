package com.trevorschoeny.inventorymax.pocket;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.containers.api.slot.SlotElement;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.slot.SlotScreenRect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets' presentation, on the panel pipeline via the §0057-Revision
 * <b>pixel-precision override</b> (Trev's call, 2026-07-01) — the last piece of
 * pocket rendering that regions couldn't say: a row that floats centered over
 * the <em>hovered</em> hotbar column and re-centers as that column's count
 * changes.
 *
 * <h3>Shape</h3>
 * <ul>
 *   <li><b>One row panel per hotbar column</b> (9): style {@code RAISED} (the
 *       backing the old consumer-drawn panel provided), hosting that column's 3
 *       {@link SlotElement}s at the fixed 18px pitch. A hidden pocket's element
 *       self-hides (its slot is inert), and panel measurement skips invisible
 *       elements — so the raised box auto-sizes to the revealed count. Only the
 *       <em>origin</em> moves (the per-frame pixel supplier recomputes the §0047
 *       centering); the elements never do.</li>
 *   <li><b>One +/− buttons panel</b>, positioned under whichever column is
 *       revealed. The buttons are consumer {@link PanelElement}s (the panel
 *       dispatcher hit-tests bounds before calling {@code mouseClicked}, so an
 *       element just acts). Shown even at 0 pockets, so + can attach the first.</li>
 * </ul>
 *
 * <p>Reveal/hover state itself is {@link PocketHover}'s per-frame tick; the
 * suppliers here only read it. A {@code null} origin skips the frame — the
 * "this screen doesn't manifest pockets" escape (wrong column, cycler off,
 * hotbar unresolvable, or a menu without our slots).
 *
 * <p>What the library owns (unchanged from the presence era): slot frame/item/
 * hover render ({@code SlotElement}), hover→click routing into the covered
 * slot, and click-eating over the panel's empty space (panel opacity).
 */
public final class PocketPixelPanels {

    private PocketPixelPanels() {}

    /** Register all pocket presentation panels. Call once at IM client init. */
    public static void register() {
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            registerRow(n);
        }
        registerButtons();
    }

    // ── The floating row (one panel per column) ─────────────────────────

    private static void registerRow(int hotbar) {
        List<PanelElement> slots = new ArrayList<>(Pockets.MAX_PER_SLOT);
        for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
            // Presents the data slot registered by the pocket mixin — panel id
            // "inventorymax:pocket_n_d", group "pocket_n_d", 1 slot each. The
            // element self-hides while its slot is inert (not revealed).
            String g = Pockets.groupId(hotbar, d);
            slots.add(SlotElement.builder().slot(CreatedSlots.groupId("inventorymax:" + g, g), 0)
                    .at(d * Pockets.SLOT, 0).build());
        }
        Panel row = Panel.builder("inventorymax:pocketrow_" + hotbar)
                .elements(slots)
                .visible(true)
                // RAISED draws the padded backing box behind the slots — the
                // old consumer-drawn pocket panel, now a panel property.
                .style(PanelStyle.RAISED)
                // Above every ordinary panel, Inventory Plus's buttons included
                // (Trev, 2026-10-04), and it takes the clicks there (MenuKit 956188a).
                .popover()
                .position(PanelPosition.pixel(() -> rowOrigin(hotbar)))
                .build();
        // Default opacity (opaque): clicks in the backing margin are eaten, so
        // a carried item can't fall through the revealed panel's empty space —
        // the gap-guard the old dispatch provided. The slots themselves are
        // click-through holes vanilla routes into.
        new ScreenPanelAdapter(row, PocketGeometry.ROW_PAD).onPlayerInventory();
    }

    /**
     * Per-frame outer origin of column {@code hotbar}'s row panel — the §0047
     * centering math over the live hotbar slot, minus the backing pad (the
     * supplier positions the panel's OUTER top-left). Null (skip frame) unless
     * this column is revealed with ≥1 pocket on a screen that carries pockets.
     */
    private static ScreenOrigin rowOrigin(int hotbar) {
        if (!IMConfig.pocketCyclerEnabled()) return null;
        if (PocketHoverState.revealedHotbar() != hotbar) return null;
        int count = PocketHoverState.count(hotbar);
        if (count <= 0) return null; // nothing above the slot at 0 pockets
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null || !PocketGeometry.pocketsPresent(screen)) return null;
        SlotScreenRect anchor = PocketGeometry.hotbar(screen, hotbar);
        if (anchor == null) return null;
        return new ScreenOrigin(
                PocketGeometry.rowX(anchor, count, 0) - PocketGeometry.ROW_PAD,
                PocketGeometry.rowY(anchor) - PocketGeometry.ROW_PAD);
    }

    // ── The +/− buttons (one shared panel, follows the revealed column) ──

    private static void registerButtons() {
        Panel buttons = Panel.builder("inventorymax:pocketbuttons")
                .elements(List.of(
                        new ResizeButton(false, 0),
                        new ResizeButton(true, PocketGeometry.BUTTON_SIZE + 1)))
                .visible(true)
                .style(PanelStyle.NONE)
                // On the pocket row's layer, so the +/- never hide under a button.
                .popover()
                .position(PanelPosition.pixel(PocketPixelPanels::buttonsOrigin))
                .build()
                // The two boxes float in empty space below the hotbar; the 1px
                // gap between them must not eat clicks (old behavior: only a
                // hit inside a button consumed).
                .opaque(false);
        new ScreenPanelAdapter(buttons, 0).onPlayerInventory();
    }

    /**
     * Per-frame outer origin of the +/− strip: just below the revealed column's
     * hotbar slot (2px down into the strip). Shown for ANY revealed column —
     * count 0 included, so + can attach the first pocket. Null when no column
     * is revealed (or the screen doesn't carry pockets).
     */
    private static ScreenOrigin buttonsOrigin() {
        if (!IMConfig.pocketCyclerEnabled()) return null;
        int rev = PocketHoverState.revealedHotbar();
        if (rev < 0) return null;
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null || !PocketGeometry.pocketsPresent(screen)) return null;
        SlotScreenRect anchor = PocketGeometry.hotbar(screen, rev);
        if (anchor == null) return null;
        return new ScreenOrigin(
                PocketGeometry.buttonsX(anchor),
                PocketGeometry.buttonsTop(anchor) + 2);
    }

    private static AbstractContainerScreen<?> containerScreen() {
        return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs
                ? acs : null;
    }

    // ── The +/− button element ───────────────────────────────────────────

    /** Palette — matches the pre-migration consumer-drawn boxed buttons. */
    private static final int BTN_BORDER = 0xFF373737;
    private static final int BTN_FACE = 0xFF8B8B8B;
    private static final int BTN_FACE_HOVER = 0xFFFFFFFF;
    private static final int GLYPH = 0xFF202020;
    private static final int GLYPH_DISABLED = 0xFF5A5A5A;

    /**
     * One boxed resize button (− or +). Bordered box with a bar/cross glyph,
     * graying out at its limit (− at 0 pockets, + at max) — grayed still
     * consumes the click as a no-op, exactly as before. Any mouse button
     * works (pre-migration behavior). The dispatcher only calls
     * {@link #mouseClicked} for in-bounds hits, so no self hit-test needed.
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

        /** Enabled while the revealed column can still grow/shrink. */
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
            g.fill(x, y, x + w, y + h, BTN_BORDER);                                   // border
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? BTN_FACE_HOVER : BTN_FACE); // face
            int glyph = on ? GLYPH : GLYPH_DISABLED;
            int cy = y + h / 2;
            int cx = x + w / 2;
            g.fill(x + 2, cy, x + w - 2, cy + 1, glyph);           // horizontal bar
            if (plus) g.fill(cx, y + 2, cx + 1, y + h - 2, glyph); // vertical bar → cross
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            // Ctrl+click (Cmd on a Mac) opens the Pockets settings tab instead,
            // as every Inventory Plus and Inventory Max button does.
            if (Click.of(button).ctrl()) {
                InventoryPlusApi.openSettings("inventorymax:pockets");
                return true;
            }
            int rev = PocketHoverState.revealedHotbar();
            if (rev < 0) return false;
            if (plus) {
                if (PocketState.count(rev) < Pockets.MAX_PER_SLOT) PocketState.grow(rev);
            } else {
                if (PocketState.count(rev) > 0) PocketState.shrink(rev);
            }
            return true; // consumed either way (grayed at limit → no-op)
        }
    }
}
