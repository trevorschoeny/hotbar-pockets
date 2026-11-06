package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.ItemAnimation.Pose;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.hud.HotbarDecorations;
import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.core.DrawAlpha;
import com.trevlar.menukit.core.ItemOutline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets' ring on the HUD, in the two looks of {@link PocketStrip}, drawn on MenuKit's
 * hotbar building blocks with the look the cycle strip gave them before MenuKit 8.0.0:
 * <ul>
 *   <li><b>The arc</b> (pocket previews): small icons just above every hotbar slot with
 *       pockets, a {@link HotbarDecorations#under} decoration, so each slot's own item
 *       covers the icons that reach into it. A turn moves each stack in a straight line to
 *       the next point (arc to arc, arc to slot, slot to arc), growing or shrinking as it
 *       goes; meanwhile the slot's own item is hidden ({@link #ownsHeld}) and the arc draws
 *       the stacks coming into and leaving it. Screens draw the same arc
 *       ({@link #drawArc}, from PocketArcs).</li>
 *   <li><b>The mini hotbar</b>: the held slot's ring as a small hotbar right of the vanilla
 *       one, in a {@link HudPanel} in {@link OutsideRegion#RIGHT_ALIGN_BOTTOM}, so it stacks
 *       with other mods' panels there and steps past the offhand slot. A turn slides its
 *       stacks one cell, the one that wrapped coming in from the far end.</li>
 * </ul>
 */
public final class PocketHud {

    private PocketHud() {}

    // Vanilla's hotbar measurements, in GUI px.
    private static final int SLOT_PX = 20;
    private static final int BORDER_PX = 1;
    private static final int BAR_HEIGHT_PX = 22;
    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_W = 24, SELECTION_H = 23;

    /** Registers the arc, its hide rule and the mini hotbar's panel. Call once at client init. */
    public static void register() {
        HotbarDecorations.under(HotbarPockets.MOD_ID + ":arc", (g, slot, stack, x, y) -> drawHudArc(g, slot, x + 8, y + 8));
        HotbarDecorations.hideItem(HotbarPockets.MOD_ID + ":turn", PocketHud::ownsHeld);
        HudPanel.builder(HotbarPockets.MOD_ID + ":mini_hotbar", ModeScope.BOTH)
                .region(OutsideRegion.RIGHT_ALIGN_BOTTOM)
                // 4 px off the hotbar, where the cycle strip sat.
                .gap(4)
                .visibleWhen(() -> miniRing() != null)
                .element(new MiniHotbar())
                .build();
    }

    // The arc

    /** The arc's ring on {@code hotbar} when previews are on and it fits an arc, else null. */
    private static @Nullable List<ItemStack> arcRing(int hotbar) {
        if (!PocketsSettings.enabled() || !PocketsSettings.previews()) return null;
        List<ItemStack> ring = PocketStrip.ring(hotbar);
        return ring == null || PocketStrip.points(ring.size() - 1).isEmpty() ? null : ring;
    }

    /**
     * Whether the arc draws {@code hotbar}'s stack itself, so the hotbar draws that slot
     * empty: during a turn, and while the ring holds a stack other than {@code inSlot} (a
     * turn predicted that the server hasn't confirmed).
     */
    private static boolean ownsHeld(int hotbar, ItemStack inSlot) {
        List<ItemStack> ring = arcRing(hotbar);
        if (ring == null) return false;
        return PocketStrip.turning(hotbar) || !ItemStack.matches(ring.getFirst(), inSlot);
    }

    /** The HUD's arc on {@code hotbar}, plus the held stack where the arc owns it at rest. */
    private static void drawHudArc(GuiGraphicsExtractor g, int hotbar, int cx, int cy) {
        List<ItemStack> ring = arcRing(hotbar);
        if (ring == null) return;
        drawArc(g, hotbar, cx, cy);
        // At rest but owning the slot: a predicted turn the server hasn't confirmed. The
        // hotbar drew the slot empty, so the predicted held stack goes there, as vanilla would.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !PocketStrip.turning(hotbar) && ownsHeld(hotbar, mc.player.getInventory().getItem(hotbar))) {
            g.item(ring.getFirst(), cx - 8, cy - 8);
            g.itemDecorations(mc.font, ring.getFirst(), cx - 8, cy - 8);
        }
    }

    /**
     * Each stack's pose this frame for a ring of {@code size}, index {@code j} being the stack
     * {@code j} steps after the held one (0 the held one, {@code j} pocket {@code j - 1}). At
     * rest the held one is {@link Pose#NONE} and the pockets at their points; during a turn
     * each moves in a straight line from the pose it held before to its pose now, its scale
     * going linearly between the two. Forward, the stack now at {@code j} was at {@code j + 1}.
     */
    private static List<Pose> arcPoses(int hotbar, int size) {
        List<Pose> rest = new ArrayList<>(size);
        rest.add(Pose.NONE);
        rest.addAll(PocketStrip.points(size - 1));
        if (!PocketStrip.turning(hotbar)) return rest;
        float p = PocketStrip.turnProgress();
        int step = PocketStrip.turnForward() ? 1 : -1;
        List<Pose> out = new ArrayList<>(size);
        for (int j = 0; j < size; j++) {
            Pose from = rest.get(Math.floorMod(j + step, size)), to = rest.get(j);
            out.add(new Pose(lerp(from.scale(), to.scale(), p),
                    lerp(from.offsetX(), to.offsetX(), p), lerp(from.offsetY(), to.offsetY(), p)));
        }
        return out;
    }

    private static float lerp(float a, float b, float p) {
        return a * (1f - p) + b * p; // exact at both ends, so a turn ends exactly at rest
    }

    /**
     * The arc on {@code hotbar} around a held item centered at {@code (cx, cy)}, turn
     * included; nothing without previews or pockets. At rest it draws the pockets and leaves
     * the held stack to the slot. During a turn every stack moves, the held ones included:
     * the leaving stack (the one that was held, headed for a point) draws over the pockets it
     * crosses, since it was on top a moment ago, and the entering one (headed for the hand)
     * last, on top of that. Stacks here draw without their count: at this size it can't be read.
     */
    static void drawArc(GuiGraphicsExtractor g, int hotbar, float cx, float cy) {
        List<ItemStack> ring = arcRing(hotbar);
        if (ring == null) return;
        int n = ring.size();
        List<Pose> poses = arcPoses(hotbar, n);
        boolean turning = PocketStrip.turning(hotbar);
        // The stack animating away from the held pose: the one step back in the turn's direction.
        int leaving = !turning ? -1 : Math.floorMod(PocketStrip.turnForward() ? -1 : 1, n);
        for (int j = n - 1; j >= 1; j--) {
            if (j != leaving) drawPosed(g, ring.get(j), poses.get(j), cx, cy);
        }
        if (leaving >= 0) drawPosed(g, ring.get(leaving), poses.get(leaving), cx, cy);
        if (turning) drawPosed(g, ring.getFirst(), poses.getFirst(), cx, cy);
    }

    private static void drawPosed(GuiGraphicsExtractor g, ItemStack stack, Pose pose, float cx, float cy) {
        drawArcItem(g, stack, cx + pose.offsetX(), cy + pose.offsetY(), pose.scale());
    }

    /**
     * {@code stack} centered at {@code (cx, cy)}, {@code scale} times its 16 px size, without
     * its count, over its shadow: its own shape, dark and faint, offset down and right like a
     * text shadow, full at the arc's icon size and gone at the slot's, so a stack flown into a
     * slot lands with none. An empty stack draws nothing.
     */
    static void drawArcItem(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float scale) {
        if (stack.isEmpty()) return;
        float strength = scale <= PocketStrip.ICON_SCALE ? 1f
                : Math.clamp((1f - scale) / (1f - PocketStrip.ICON_SCALE), 0f, 1f);
        int alpha = Math.round(255 * PocketStrip.SHADOW_OPACITY * strength);
        if (alpha > 0) {
            float offset = PocketStrip.SHADOW_OFFSET * scale;
            // ponytail: MenuKit's internal silhouette pass, the one SlotRendering.drawItemOutline
            // wraps; swap for a public item-shadow call once MenuKit has one.
            ItemOutline.silhouettes(alpha << 24, () -> drawScaled(g, stack, cx + offset, cy + offset, scale));
        }
        drawScaled(g, stack, cx, cy, scale);
    }

    /**
     * {@link #drawArcItem(GuiGraphicsExtractor, ItemStack, float, float, float)} plus the
     * stack's count, durability bar and cooldown, scaled and centered with it, at
     * {@code decorationsAlpha}: 0 draws none, 1 as a slot draws them. For a copy flown from
     * the arc into a slot, so its count fades in on the way.
     */
    static void drawArcItem(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float scale,
                            float decorationsAlpha) {
        drawArcItem(g, stack, cx, cy, scale);
        if (stack.isEmpty() || decorationsAlpha <= 0f) return;
        // ponytail: MenuKit's internal draw opacity, the one PanelTransform fades panels
        // with; swap for a public call once MenuKit has one.
        float before = DrawAlpha.push(decorationsAlpha);
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx, cy);
            g.pose().scale(scale, scale);
            g.itemDecorations(Minecraft.getInstance().font, stack, -8, -8);
        } finally {
            g.pose().popMatrix();
            DrawAlpha.pop(before);
        }
    }

    private static void drawScaled(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float scale) {
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx, cy);
            g.pose().scale(scale, scale);
            g.item(stack, -8, -8);
        } finally {
            g.pose().popMatrix();
        }
    }

    // The mini hotbar

    /** The held slot's ring for the mini hotbar when it's on, else null. Spectators have no hotbar. */
    private static @Nullable List<ItemStack> miniRing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return null;
        if (!PocketsSettings.enabled() || !PocketsSettings.miniHotbar()) return null;
        return PocketStrip.ring(mc.player.getInventory().getSelectedSlot());
    }

    /**
     * A row, left to right: previous, held, next, the one after... For two: held, other. The
     * held one is at index {@link #rowHeld}.
     */
    private static List<ItemStack> rowOrder(List<ItemStack> ring) {
        int n = ring.size();
        if (n == 2) return ring;
        List<ItemStack> out = new ArrayList<>(n);
        out.add(ring.getLast());
        out.addAll(ring.subList(0, n - 1));
        return out;
    }

    private static int rowHeld(int size) {
        return size == 2 ? 0 : 1;
    }

    /** The mini hotbar: the bar, its stacks and the selection frame, at the ring's live width. */
    private static final class MiniHotbar implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getHeight() { return BAR_HEIGHT_PX; }

        @Override
        public int getWidth() {
            List<ItemStack> ring = miniRing();
            return ring == null ? 0 : ring.size() * SLOT_PX + 2 * BORDER_PX;
        }

        @Override
        public void render(RenderContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            List<ItemStack> ring = miniRing();
            if (ring == null || mc.player == null) return;
            GuiGraphicsExtractor g = ctx.graphics();
            int x = ctx.originX(), y = ctx.originY();
            List<ItemStack> items = rowOrder(ring);
            int n = items.size(), hi = rowHeld(n);
            int width = n * SLOT_PX + 2 * BORDER_PX;
            boolean turning = PocketStrip.turning(mc.player.getInventory().getSelectedSlot());
            boolean forward = PocketStrip.turnForward();
            // Forward, the stacks start one cell right and move left; backward, the mirror.
            int px = turning ? Math.round((1f - PocketStrip.turnProgress()) * SLOT_PX) : 0;
            int offset = forward ? px : -px;

            drawBar(g, x, y, width);
            int cellsLeft = x + BORDER_PX;
            g.enableScissor(cellsLeft, y, x + width - BORDER_PX, y + BAR_HEIGHT_PX);
            try {
                for (int k = 0; k < n; k++) drawCellItem(g, mc, items.get(k), cellsLeft + k * SLOT_PX + offset, y);
                if (turning) {
                    // The stack that wrapped around enters from the far end: forward, the
                    // last one from off the left; backward, the first from off the right.
                    int wrap = forward ? n - 1 : 0;
                    int base = forward ? -SLOT_PX : n * SLOT_PX;
                    drawCellItem(g, mc, items.get(wrap), cellsLeft + base + offset, y);
                }
            } finally {
                g.disableScissor();
            }
            // The selection frame starts two px left of its cell, as vanilla's does.
            int cellX = cellsLeft + hi * SLOT_PX;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE, SELECTION_W, SELECTION_H, 0, 0,
                    cellX - BORDER_PX - 1, y - 1, SELECTION_W, SELECTION_H);
        }
    }

    /** A hotbar bar {@code width} px wide: left cap, tiled cells, right cap, outlined on every side. */
    private static void drawBar(GuiGraphicsExtractor g, int x, int y, int width) {
        int cells = (width - 2 * BORDER_PX) / SLOT_PX;
        int cellsLeft = x + BORDER_PX;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, BAR_HEIGHT_PX, 0, 0,
                x, y, BORDER_PX, BAR_HEIGHT_PX);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, BAR_HEIGHT_PX, BORDER_PX, 0,
                cellsLeft, y, cells * SLOT_PX, BAR_HEIGHT_PX);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, BAR_HEIGHT_PX, HOTBAR_SPRITE_W - BORDER_PX, 0,
                cellsLeft + cells * SLOT_PX, y, BORDER_PX, BAR_HEIGHT_PX);
    }

    /** A stack in the cell whose left edge is {@code cellX}, placed as vanilla's hotbar places items (2 in, 3 down). */
    private static void drawCellItem(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack, int cellX, int cellY) {
        if (stack.isEmpty()) return;
        g.item(stack, cellX + 2, cellY + 3);
        g.itemDecorations(mc.font, stack, cellX + 2, cellY + 3);
    }
}
