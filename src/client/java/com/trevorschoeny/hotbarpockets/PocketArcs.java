package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.PanelTransform;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.menukit.api.panel.SlotItemLooks;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Pocket previews on container screens: each hotbar slot with pockets shows its arc above
 * it, drawn exactly as the HUD draws it ({@link PocketHud#drawArc}), so you
 * can see what every column holds without hovering. A resting arc is an under-decoration
 * of its hotbar slot ({@link #drawRestingArc}): on the screen's image, under the slot's own
 * item, as on the HUD, so the slot's stack, highlight and pickup bob draw over it as vanilla's.
 *
 * <h3>The hover slide</h3>
 * When a column's pocket row opens, that column's arc items slide in straight lines,
 * growing from half to full size, into their slots in the row, and the row's panels grow
 * out of the arc and fade in with them ({@link #rowTransform}). When it closes, all of it
 * goes back into the arc; the row stays laid out until then
 * ({@link PocketHoverState#isShown}). Each column slides on its own clock, so moving
 * straight from one slot to the next shrinks the old row while the new one grows, and a
 * slide cut short turns back from where it got to. The timing is the turn's spring, quicker
 * ({@link PocketStrip#hoverProgress}).
 * Meanwhile the row's own items are hidden
 * ({@link #hideWhileSliding}), or each would show twice: flying, and in its slot.
 *
 * <p>For looking only: the arcs take no clicks, and the row is still where pockets are used.
 */
public final class PocketArcs {

    private PocketArcs() {}

    /** The hover slide's panel: above the pocket row (and its backing box), so the sliding items draw over its frames. */
    private static final int PRIORITY = PanelPosition.DEFAULT_PRIORITY + 2;

    /**
     * How big the row's panels start when growing out of the arc.
     * One value, about an arc's width over a three-pocket row; make it per-count if a
     * single pocket looks off.
     */
    private static final float ROW_START_SCALE = 0.3f;

    /** How far above the hotbar item's center the arc's middle sits: the row grows from there. */
    private static final float ARC_RISE = 9f;

    // The hover slides, one per column, render thread only. lastRevealed is the column
    // hovered last frame. Per column: running while a slide is under way, opening its
    // direction, from where it started (0 at the arc, 1 in the row), start its clock.
    private static int lastRevealed = -1;
    private static final boolean[] running = new boolean[Pockets.HOTBAR_SLOTS];
    private static final boolean[] opening = new boolean[Pockets.HOTBAR_SLOTS];
    private static final float[] from = new float[Pockets.HOTBAR_SLOTS];
    private static final long[] start = new long[Pockets.HOTBAR_SLOTS];

    /** Registers the resting arcs, the hover slide's panel and the row-hiding look. Call once at client init. */
    public static void register() {
        // One panel at the screen's corner; its element draws the hover slide in screen coordinates.
        Panel arcs = Panel.builder(HotbarPockets.MOD_ID + ":pocket_arcs")
                .elements(List.of(new Arcs()))
                .visible(true)
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.screen(
                        PanelPosition.pixel(() -> new ScreenOrigin(0, 0)).priority(PRIORITY)))
                .build()
                // Looking only: every click passes through to what's below.
                .opaque(false);
        new ScreenPanelAdapter(arcs, 0).onAny();
        SlotItemLooks.declare(HotbarPockets.MOD_ID + ":pocket_row_sliding", PocketArcs::hideWhileSliding);
        SlotDecorations.under(HotbarPockets.MOD_ID + ":arcs", PocketArcs::drawRestingArc);
    }

    /** Whether column {@code n}'s slide is running now. */
    private static boolean sliding(int n) {
        return running[n] && !PocketStrip.done(start[n], PocketStrip.HOVER_MILLIS);
    }

    // A row slot's item hides while its column's row opens or closes; any other slot is left be.
    private static ItemAnimation.@Nullable Frame hideWhileSliding(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack) {
        CreatedSlot created = CreatedSlot.of(slot);
        if (created == null) return null;
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            if (!sliding(n)) continue;
            for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
                if (Pockets.isPocket(created, n, d)) return ItemAnimation.HIDDEN.at(1f);   // the item, its count and its bar
            }
        }
        return null;
    }

    // A hotbar slot's arc at rest, under the slot's item. Not the column whose row is open
    // (the row is its view), nor one sliding (the slide panel draws that).
    private static void drawRestingArc(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot,
                                       ItemStack stack, int x, int y) {
        if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= Pockets.HOTBAR_SLOTS) return;
        if (!PocketsSettings.enabled() || !PocketsSettings.previews()) return;
        int n = slot.getContainerSlot();
        // Null where this screen has no pockets.
        if (PocketGeometry.shownAnchor(n) == null) return;
        if (n == PocketHoverState.revealedHotbar() || sliding(n)) return;
        PocketHud.drawArc(g, n, x + 8, y + 8);
    }

    /**
     * The hover slide, drawn in screen coordinates over everything. Sized 1 px because
     * MenuKit skips an empty panel; the panel isn't opaque, so it takes no clicks.
     */
    private static final class Arcs implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth()  { return 1; }
        @Override public int getHeight() { return 1; }

        @Override
        public void render(RenderContext ctx) {
            if (!PocketsSettings.enabled() || !PocketsSettings.previews()) {
                // No slides without previews; a row cut off mid-shrink isn't left laid out.
                for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) finish(n);
                lastRevealed = PocketHoverState.revealedHotbar();
                return;
            }
            trackHover();
            for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
                // A slide that's over stops; a closed row's shrink being over, it stops being laid out.
                if (running[n] && !sliding(n)) finish(n);
                if (!sliding(n)) continue;
                // Null where this screen has no pockets.
                SlotScreenRect slot = PocketGeometry.shownAnchor(n);
                if (slot != null) drawSlide(ctx.graphics(), n, slot);
            }
        }
    }

    // Starts slides when the hovered column changes: the one left shrinks back to its arc,
    // the one entered grows into its row, both at once.
    private static void trackHover() {
        int revealed = PocketHoverState.revealedHotbar();
        if (revealed == lastRevealed) return;
        if (lastRevealed >= 0) begin(lastRevealed, false);
        if (revealed >= 0) begin(revealed, true);
        lastRevealed = revealed;
    }

    // Starts column n's slide towards its row (opening) or its arc, from wherever it is now:
    // part way if a slide the other way was still running, else from the far end.
    private static void begin(int n, boolean open) {
        from[n] = sliding(n) ? rowness(n) : open ? 0f : 1f;
        opening[n] = open;
        start[n] = Util.getMillis();
        running[n] = true;
        PocketHoverState.setClosing(n, !open);
    }

    private static void finish(int n) {
        running[n] = false;
        PocketHoverState.setClosing(n, false);
    }

    /** Restarts every running hover slide's clock now. For the preview shots, which start them at chosen times. */
    static void restampSlide() {
        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) if (running[n]) start[n] = Util.getMillis();
    }

    /**
     * Column {@code n}'s slide position: 0 at the arc, 1 in the row, a little past either
     * end at the spring's bounce.
     */
    private static float rowness(int n) {
        float to = opening[n] ? 1f : 0f;
        return from[n] + (to - from[n]) * PocketStrip.hoverProgress(start[n]);
    }

    /**
     * How the row's panels (the backing box and every pocket panel) draw this frame: while a
     * row slides, scaled about the middle of its arc from {@link #ROW_START_SCALE} to full
     * size and faded in with it; otherwise as laid out. MenuKit keeps a panel that isn't at
     * full size from taking input, so a click never lands on a row still growing.
     */
    static PanelTransform rowTransform(int n) {
        if (!sliding(n)) return PanelTransform.IDENTITY;
        SlotScreenRect slot = PocketGeometry.shownAnchor(n);
        if (slot == null) return PanelTransform.IDENTITY;
        float k = rowness(n);
        float scale = Math.max(0.05f, ROW_START_SCALE + (1f - ROW_START_SCALE) * k);
        return new PanelTransform(scale, slot.x() + 8, slot.y() + 8 - ARC_RISE, k);
    }

    /**
     * The plus and minus strip, mirroring the row: while a row slides it grows out from
     * under the hotbar slot (scaled about the middle of the slot's bottom edge) and fades
     * in, on the same spring, and goes back the same way.
     */
    static PanelTransform buttonsTransform(int n) {
        if (!sliding(n)) return PanelTransform.IDENTITY;
        SlotScreenRect slot = PocketGeometry.shownAnchor(n);
        if (slot == null) return PanelTransform.IDENTITY;
        float k = rowness(n);
        float scale = Math.max(0.05f, ROW_START_SCALE + (1f - ROW_START_SCALE) * k);
        return new PanelTransform(scale, slot.x() + 8, PocketGeometry.buttonsTop(slot), k);
    }

    /**
     * Column {@code n}'s stacks part way between its arc and its row: each in a straight line
     * from its arc point to its row slot's center, scaling from the arc's size to full size.
     * {@code k} is 0 at the arc and 1 in the row (a little past either end at the spring's
     * overshoot).
     */
    private static void drawSlide(GuiGraphicsExtractor g, int n, SlotScreenRect slot) {
        Minecraft mc = Minecraft.getInstance();
        int count = PocketState.count(n);
        if (mc.player == null || count < 1) return;
        float k = rowness(n);
        List<ItemAnimation.Pose> arc = PocketStrip.points(count);
        Storage pockets = Pockets.POCKETS.bind(mc.player);
        float cx = slot.x() + 8, cy = slot.y() + 8;   // the hotbar item's center
        for (int d = 0; d < count; d++) {
            ItemStack stack = pockets.getStack(Pockets.flatIndex(n, d));
            if (stack.isEmpty()) continue;
            ItemAnimation.Pose at = arc.get(d);
            float ax = cx + at.offsetX(), ay = cy + at.offsetY();
            // The row slot's 18 px frame is at rowX/rowY; its item's center is 9 in.
            float rx = PocketGeometry.rowX(slot, count, d) + 9, ry = PocketGeometry.rowY(slot) + 9;
            PocketHud.drawArcItem(g, stack, ax + (rx - ax) * k, ay + (ry - ay) * k,
                    PocketStrip.ICON_SCALE + (1f - PocketStrip.ICON_SCALE) * k,
                    // The count and bar grow and fade in with the item, so the slot's own
                    // take over seamlessly when the slide ends.
                    Math.clamp(k, 0f, 1f));
        }
    }
}
