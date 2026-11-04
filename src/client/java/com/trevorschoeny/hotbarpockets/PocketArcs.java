package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.hud.HudCycleStrip;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.PanelTransform;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.panel.SlotItemLooks;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Pocket previews on container screens: each hotbar slot with pockets shows its arc above
 * it, drawn by MenuKit exactly as the HUD draws it ({@link HudCycleStrip#drawArc}), so you
 * can see what every column holds without hovering.
 *
 * <h3>The hover slide</h3>
 * When a column's pocket row opens, that column's arc items slide in straight lines,
 * growing from half to full size, into their slots in the row, and the row's panels grow
 * out of the arc and fade in with them ({@link #rowTransform}). When it closes, all of it
 * goes back into the arc; the row stays laid out until then
 * ({@link PocketHoverState#shownHotbar}). The timing is the cycle strip's spring
 * ({@link HudCycleStrip#begin}). Meanwhile the row's own items are hidden
 * ({@link #hideWhileSliding}), or each would show twice: flying, and in its slot.
 *
 * <p>For looking only: the arcs take no clicks, and the row is still where pockets are used.
 */
public final class PocketArcs {

    private PocketArcs() {}

    /** Above the pocket row (and its backing box), so the sliding items draw over its frames. */
    private static final int PRIORITY = PanelPosition.DEFAULT_PRIORITY + 2;

    /**
     * How big the row's panels start when growing out of the arc.
     * ponytail: one value, about an arc's width over a three-pocket row; per-count if 1 looks off.
     */
    private static final float ROW_START_SCALE = 0.3f;

    /** How far above the hotbar item's center the arc's middle sits: the row grows from there. */
    private static final float ARC_RISE = 9f;

    /** Draws nothing: a row slot's item while the arc's copy of it is still flying in. */
    private static final ItemAnimation HIDDEN = (g, stack, x, y, t) -> {};

    // The hover slide, render thread only. lastRevealed is the column shown last frame;
    // slideColumn the one sliding (or -1), opening its direction, slideStart its clock.
    private static int lastRevealed = -1;
    private static int slideColumn = -1;
    private static boolean opening;
    private static HudCycleStrip.@Nullable SlideStart slideStart;

    /** Registers the arcs panel and the row-hiding look. Call once at client init. */
    public static void register() {
        // One panel at the screen's corner; its element draws every arc in screen coordinates.
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
    }

    /** Whether a slide is running now. */
    private static boolean sliding() {
        return slideColumn >= 0 && slideStart != null && !HudCycleStrip.slideDone(slideStart);
    }

    // A row slot's item hides while its column's row opens or closes; any other slot is left be.
    private static ItemAnimation.@Nullable Frame hideWhileSliding(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack) {
        if (!sliding()) return null;
        CreatedSlot created = CreatedSlot.of(slot);
        if (created == null) return null;
        for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
            if (Pockets.isPocket(created, slideColumn, d)) return HIDDEN.at(1f);
        }
        return null;
    }

    /**
     * Every hotbar slot's arc, and the hover slide, drawn in screen coordinates. Sized 1 px
     * because MenuKit skips an empty panel; the panel isn't opaque, so it takes no clicks.
     */
    private static final class Arcs implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth()  { return 1; }
        @Override public int getHeight() { return 1; }

        @Override
        public void render(RenderContext ctx) {
            if (!PocketsSettings.enabled() || !PocketsSettings.previews()) return;
            trackHover();
            // A closed row's shrink is over: it stops being laid out.
            if (!opening && slideColumn >= 0 && !sliding()) {
                PocketHoverState.setClosingHotbar(-1);
                slideColumn = -1;
            }
            GuiGraphicsExtractor g = ctx.graphics();
            int revealed = PocketHoverState.revealedHotbar();
            for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
                // Null where this screen has no pockets or no such hotbar slot.
                SlotScreenRect slot = PocketGeometry.shownAnchor(n);
                if (slot == null) continue;
                if (n == slideColumn && sliding()) drawSlide(g, n, slot);
                // The open row is this column's view; its arc stays hidden meanwhile.
                else if (n != revealed) HudCycleStrip.drawArc(g, PocketStrip.ARC, n, slot.x() + 8, slot.y() + 8);
            }
        }
    }

    // Starts a slide when the shown column changes: the new column's arc slides into its
    // row; with no column shown now, the one that closed slides back to its arc. Moving
    // straight from one column to another opens the new one; the old arc just reappears.
    private static void trackHover() {
        int revealed = PocketHoverState.revealedHotbar();
        if (revealed == lastRevealed) return;
        if (revealed >= 0) { slideColumn = revealed; opening = true; PocketHoverState.setClosingHotbar(-1); }
        else { slideColumn = lastRevealed; opening = false; PocketHoverState.setClosingHotbar(lastRevealed); }
        slideStart = HudCycleStrip.begin();
        lastRevealed = revealed;
    }

    /** Restarts a running hover slide's clock now. For the preview shots, which start it at chosen times. */
    static void restampSlide() {
        if (slideColumn >= 0) slideStart = HudCycleStrip.begin();
    }

    /**
     * The slide's position: 0 at the arc, 1 in the row, a little past either end at the
     * spring's bounce.
     */
    private static float rowness() {
        float p = slideStart == null ? 1f : HudCycleStrip.slideProgress(slideStart);
        return opening ? p : 1f - p;
    }

    /**
     * How the row's panels (the backing box and every pocket panel) draw this frame: while a
     * row slides, scaled about the middle of its arc from {@link #ROW_START_SCALE} to full
     * size and faded in with it; otherwise as laid out. MenuKit keeps a panel that isn't at
     * full size from taking input, so a click never lands on a row still growing.
     */
    static PanelTransform rowTransform() {
        if (!sliding()) return PanelTransform.IDENTITY;
        SlotScreenRect slot = PocketGeometry.shownAnchor(slideColumn);
        if (slot == null) return PanelTransform.IDENTITY;
        float k = rowness();
        float scale = Math.max(0.05f, ROW_START_SCALE + (1f - ROW_START_SCALE) * k);
        return new PanelTransform(scale, slot.x() + 8, slot.y() + 8 - ARC_RISE, k);
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
        if (mc.player == null || count < 1 || slideStart == null) return;
        float k = rowness();
        List<ItemAnimation.Pose> arc = HudCycleStrip.arcPoints(count);
        Storage pockets = Pockets.POCKETS.bind(mc.player);
        float cx = slot.x() + 8, cy = slot.y() + 8;   // the hotbar item's center
        for (int d = 0; d < count; d++) {
            ItemStack stack = pockets.getStack(Pockets.flatIndex(n, d));
            if (stack.isEmpty()) continue;
            ItemAnimation.Pose at = arc.get(d);
            float ax = cx + at.offsetX(), ay = cy + at.offsetY();
            // The row slot's 18 px frame is at rowX/rowY; its item's center is 9 in.
            float rx = PocketGeometry.rowX(slot, count, d) + 9, ry = PocketGeometry.rowY(slot) + 9;
            HudCycleStrip.drawArcItem(g, stack, ax + (rx - ax) * k, ay + (ry - ay) * k,
                    HudCycleStrip.ARC_SCALE + (1f - HudCycleStrip.ARC_SCALE) * k);
        }
    }
}
