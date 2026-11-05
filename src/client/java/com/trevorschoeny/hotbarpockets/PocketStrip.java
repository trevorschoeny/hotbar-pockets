package com.trevorschoeny.hotbarpockets;

import com.trevlar.menukit.api.hud.HudCycleStrip;
import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The pockets' ring on MenuKit's cycle strip, in two looks: the arc above every hotbar
 * slot (pocket previews) and the mini hotbar beside the held slot. The ring is the held
 * stack, then pocket 0, 1, 2, which is the order a forward turn brings them to the hand
 * ({@link PocketServerOps#rotate}).
 *
 * <h3>Reading the contents</h3>
 * The items come straight from the player's pocket attachment on the client. In the
 * world the client's pocket slots are inert, but the server's are always live, so
 * vanilla slot sync keeps the client's attachment filled.
 *
 * <h3>Predicting the turn</h3>
 * The server turns the ring and the new order arrives a round trip later, but the strip
 * slides the moment the key is pressed. So {@link #predict} stores the turned ring and
 * {@link #viewFor} returns it until the server's ring matches it, or the arc would draw
 * the old order mid-slide (the stack leaving the hand drawn as the held one, over the one
 * coming in) and then jump. A turn the server refuses never matches, so the prediction
 * also gives up after {@link #PREDICTION_LIMIT_MILLIS} on the game clock, the one
 * MenuKit times the slide on.
 */
public final class PocketStrip implements HudCycleStrip.Source {

    /**
     * How Pockets' arc looks and moves, on the HUD, on screens and in the hover slide
     * (PocketArcs). Trev's values, 2026-11-04: icons at 2/5 size over a faint shadow of
     * their own shape, one icon pixel down and right; 333 ms slides on {@link #slideCurve}.
     * The resting points are MenuKit's.
     */
    public static final HudCycleStrip.Style STYLE = HudCycleStrip.Style.builder()
            .iconScale(0.4f)
            .shadow(true).shadowOffset(1f).shadowOpacity(0.375f).shadowFades(true)
            .slide(333, PocketStrip::slideCurve)
            .build();

    /**
     * The hover slide's timing (PocketArcs): the arc's look, on the same curve, 70% quicker
     * than a turn (333 / 1.7, Trev, 2026-11-05).
     */
    public static final HudCycleStrip.Style HOVER_STYLE = STYLE.toBuilder()
            .slide(196, PocketStrip::slideCurve)
            .build();

    /**
     * The slide's curve: a spring let go at full speed, {@code 1 - e^(-6.5t) cos(2.2πt)}. It
     * leaves at once (a third of the way in the first twentieth), so a key press answers
     * immediately, overshoots about 8% at 0.35 and settles by about 0.65. Trev, 2026-11-05:
     * the items should visibly slide, bounce a little and feel responsive. MenuKit's SPRING
     * covered most of the distance in a pop; a gently starting spring felt slow off the key.
     * The last term takes the spring's leftover at 1 out linearly, so it ends at exactly 1.
     * ponytail: tuned by eye from clips; move into MenuKit as an Easing if another mod
     * wants the same feel.
     */
    private static float slideCurve(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return (float) (1.0 - springRest(t) + springRest(1.0) * t);
    }

    private static final double DECAY = 6.5, FREQ = 2.2 * Math.PI;

    // How far the spring still is from rest at t, in the units of the whole move.
    private static double springRest(double t) {
        return Math.exp(-DECAY * t) * Math.cos(FREQ * t);
    }

    /** Pocket previews: the ring as an arc above every hotbar slot with pockets. */
    public static final PocketStrip ARC = new PocketStrip(HudCycleStrip.Axis.ARC, PocketsSettings::previews);
    /** The mini hotbar beside the held slot. */
    public static final PocketStrip ROW = new PocketStrip(HudCycleStrip.Axis.ROW, PocketsSettings::miniHotbar);
    /** Both looks; each draws only while its own setting is on, and a turn slides both. */
    public static final List<PocketStrip> ALL = List.of(ARC, ROW);

    private final HudCycleStrip.Axis axis;
    private final BooleanSupplier on;

    private PocketStrip(HudCycleStrip.Axis axis, BooleanSupplier on) {
        this.axis = axis;
        this.on = on;
    }

    @Override
    public HudCycleStrip.Axis axis() { return axis; }

    @Override
    public HudCycleStrip.Style style() { return STYLE; }

    /** The longest a predicted ring waits for the server's: a refused turn, or a very slow round trip. */
    private static final long PREDICTION_LIMIT_MILLIS = 2000L;
    private static int predictedHotbar = -1;
    private static @Nullable List<ItemStack> predictedRing = null;
    private static long predictedUntil = 0L;

    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        if (!PocketsSettings.enabled() || !on.getAsBoolean()) return null;
        List<ItemStack> ring = pendingOrLive(hotbarSlot);
        return ring == null ? null : new HudCycleStrip.View(ring, 0);
    }

    /**
     * Stores the ring {@code hotbar} will have after one turn, before the server confirms
     * it. Forward brings the next member to the hand, so the ring shifts one left.
     */
    public static void predict(int hotbar, boolean forward) {
        // From the pending prediction when a turn is still unconfirmed, so two quick turns
        // predict two steps.
        List<ItemStack> ring = pendingOrLive(hotbar);
        if (ring == null) return;
        List<ItemStack> next = new ArrayList<>(ring);
        if (forward) next.add(next.removeFirst());
        else next.addFirst(next.removeLast());
        predictedHotbar = hotbar;
        predictedRing = List.copyOf(next);
        predictedUntil = Util.getMillis() + PREDICTION_LIMIT_MILLIS;
    }

    /**
     * The predicted ring while the server hasn't caught up with it, otherwise the live one
     * (null without pockets). The prediction ends once the live ring matches it or its
     * limit passes.
     */
    private static @Nullable List<ItemStack> pendingOrLive(int hotbar) {
        List<ItemStack> live = ring(hotbar);
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
    private static @Nullable List<ItemStack> ring(int hotbar) {
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
