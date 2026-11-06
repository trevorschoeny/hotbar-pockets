package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets' ring and how it moves, for both looks {@link PocketHud} draws: the arc above
 * every hotbar slot (pocket previews) and the mini hotbar beside the held slot. The ring is
 * the held stack, then pocket 0, 1, 2, which is the order a forward turn brings them to the
 * hand ({@link PocketServerOps#rotate}).
 *
 * <h3>Reading the contents</h3>
 * The items come straight from the player's pocket attachment on the client. In the
 * world the client's pocket slots are inert, but the server's are always live, so
 * vanilla slot sync keeps the client's attachment filled.
 *
 * <h3>Predicting the turn</h3>
 * The server turns the ring and the new order arrives a round trip later, but the HUD
 * slides the moment the key is pressed. So {@link #predict} stores the turned ring and
 * {@link #ring} returns it until the server's ring matches it, or the arc would draw
 * the old order mid-slide (the stack leaving the hand drawn as the held one, over the one
 * coming in) and then jump. A turn the server refuses never matches, so the prediction
 * also gives up after {@link #PREDICTION_LIMIT_MILLIS}.
 *
 * <p>Every clock here is {@link Util#getMillis()}. Render thread only.
 */
public final class PocketStrip {

    private PocketStrip() {}

    /**
     * A spring for a slide: {@code 1 - e^(-decay t) (cos(freq t) + k sin(freq t))}, with
     * {@code k = ramp * decay / freq}. Ramp 0 lets the spring go at full speed; ramp 1 starts
     * it from rest (a plain step response); between, a short ramp-up. Decay sets how fast the
     * bounce dies, freq how quickly it swings. The last term takes what's left at 1 out
     * linearly, so it ends at exactly 1.
     */
    private static Easing spring(double decay, double freq, double ramp) {
        double k = ramp * decay / freq;
        java.util.function.DoubleUnaryOperator rest = t -> Math.exp(-decay * t) * (Math.cos(freq * t) + k * Math.sin(freq * t));
        double leftover = rest.applyAsDouble(1.0);
        return t -> t <= 0f ? 0f : t >= 1f ? 1f : (float) (1.0 - rest.applyAsDouble(t) + leftover * t);
    }

    /**
     * A turn on the HUD: the hover's snappy bounce with a ramp-up off
     * the key (ramp 0.4, then 0.6, then 0.8, each slowing the travel a little). Most of the
     * way by 0.35, about 5% over at 0.45, settled by about 0.7.
     * Tuned by eye from clips.
     */
    private static final Easing TURN_CURVE = spring(6.5, 2.2 * Math.PI, 0.8);

    /**
     * The hover slide: let go at full speed, a third of the way in the first twentieth,
     * overshooting about 8% at 0.35 and settled by about 0.65. Snappy, for opening a row.
     */
    private static final Easing HOVER_CURVE = spring(6.5, 2.2 * Math.PI, 0);

    /** How long a turn slides, on {@link #TURN_CURVE}. */
    static final long TURN_MILLIS = 333L;

    /** How long the hover slide takes (PocketArcs), 70% quicker than a turn (333 / 1.7). */
    static final long HOVER_MILLIS = 196L;

    // The arc's look: icons at 2/5 size over a faint shadow of their own shape, one icon
    // pixel down and right, fading out as an icon grows into the slot.

    /** How big an arc's icons draw at rest, as a fraction of the 16 px item. */
    static final float ICON_SCALE = 0.4f;
    /** How far the shadow sits down and right, in the icon's own pixels. */
    static final float SHADOW_OFFSET = 1f;
    /** The shadow's opacity at full strength. */
    static final float SHADOW_OPACITY = 0.375f;

    /**
     * Where an arc of 1, 2 or 3 pockets rests, pocket 0 first, in GUI px from the held item's
     * center (negative y is up). The 6.4 px icons sit 6.4 or 7.2 px apart, so they touch but
     * never overlap, and three stay inside the slot's 20 px pitch. The hotbar's top edge is at
     * y = -11 and the XP bar's bottom at y = -13.
     */
    private static final List<List<ItemAnimation.Pose>> POINTS = List.of(
            List.of(),
            List.of(pose(0f, -9.5f)),
            List.of(pose(-3.6f, -9f), pose(3.6f, -9f)),
            List.of(pose(-6.4f, -8f), pose(0f, -9.5f), pose(6.4f, -8f)));

    private static ItemAnimation.Pose pose(float x, float y) {
        return new ItemAnimation.Pose(ICON_SCALE, x, y);
    }

    /** Where an arc of {@code pockets} pockets rests, pocket 0 first; empty for a count it has no points for. */
    static List<ItemAnimation.Pose> points(int pockets) {
        return pockets >= 1 && pockets < POINTS.size() ? POINTS.get(pockets) : List.of();
    }

    /** {@code easing}'s progress {@code millis} long since {@code start}: 0 at the start, exactly 1 once over. */
    private static float progress(long start, long millis, Easing easing) {
        float t = (float) (Util.getMillis() - start) / millis;
        return t <= 0f ? 0f : t >= 1f ? 1f : easing.apply(t);
    }

    /** Whether a slide {@code millis} long that began at {@code start} is over. */
    static boolean done(long start, long millis) {
        return Util.getMillis() - start >= millis;
    }

    /** The hover slide's progress since {@code start}, on its curve. */
    static float hoverProgress(long start) {
        return progress(start, HOVER_MILLIS, HOVER_CURVE);
    }

    // The turn in flight, if any: one at a time, since a turn is the held slot's and a new
    // one starts over from the new order. Both looks read the same clock.
    private static int turnHotbar = -1;
    private static boolean turnForward;
    private static long turnStart;

    /** Slides both looks on {@code hotbar} one step; call right after the ring turned. */
    static void slide(int hotbar, boolean forward) {
        turnHotbar = hotbar;
        turnForward = forward;
        turnStart = Util.getMillis();
    }

    /** Whether a turn is sliding on {@code hotbar} now. */
    static boolean turning(int hotbar) {
        return turnHotbar == hotbar && !done(turnStart, TURN_MILLIS);
    }

    /** The running turn's direction; only meaningful while {@link #turning}. */
    static boolean turnForward() {
        return turnForward;
    }

    /** The running turn's progress on its curve, 1 at rest. */
    static float turnProgress() {
        return progress(turnStart, TURN_MILLIS, TURN_CURVE);
    }

    /** The longest a predicted ring waits for the server's: a refused turn, or a very slow round trip. */
    private static final long PREDICTION_LIMIT_MILLIS = 2000L;
    private static int predictedHotbar = -1;
    private static @Nullable List<ItemStack> predictedRing = null;
    private static long predictedUntil = 0L;

    /**
     * Stores the ring {@code hotbar} will have after one turn, before the server confirms
     * it. Forward brings the next member to the hand, so the ring shifts one left.
     */
    public static void predict(int hotbar, boolean forward) {
        // From the pending prediction when a turn is still unconfirmed, so two quick turns
        // predict two steps.
        List<ItemStack> ring = ring(hotbar);
        if (ring == null) return;
        List<ItemStack> next = new ArrayList<>(ring);
        if (forward) next.add(next.removeFirst());
        else next.addFirst(next.removeLast());
        predictedHotbar = hotbar;
        predictedRing = List.copyOf(next);
        predictedUntil = Util.getMillis() + PREDICTION_LIMIT_MILLIS;
    }

    /**
     * The ring a hotbar slot shows, held stack first: the predicted one while the server
     * hasn't caught up with it, otherwise the live one; null without pockets. The
     * prediction ends once the live ring matches it or its limit passes.
     */
    static @Nullable List<ItemStack> ring(int hotbar) {
        List<ItemStack> live = liveRing(hotbar);
        if (predictedRing == null || predictedHotbar != hotbar) return live;
        if (live != null && sameStacks(live, predictedRing) || Util.getMillis() >= predictedUntil) {
            predictedRing = null;
            return live;
        }
        return predictedRing;
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    /** The live ring for a hotbar slot, held stack first, or null without pockets. */
    private static @Nullable List<ItemStack> liveRing(int hotbar) {
        int count = PocketState.count(hotbar);
        Minecraft mc = Minecraft.getInstance();
        if (count < 1 || mc.player == null) return null;
        Storage pockets = Pockets.POCKETS.bind(mc.player);
        List<ItemStack> ring = new ArrayList<>(count + 1);
        ring.add(mc.player.getInventory().getItem(hotbar));
        for (int depth = 0; depth < count; depth++) {
            ring.add(pockets.getStack(Pockets.flatIndex(hotbar, depth)));
        }
        return ring;
    }
}
