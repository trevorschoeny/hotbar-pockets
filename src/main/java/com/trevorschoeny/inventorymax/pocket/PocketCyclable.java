package com.trevorschoeny.inventorymax.pocket;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.CyclerOperation;
import com.trevorschoeny.inventoryplus.api.HotbarCyclable;
import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.operations.InventoryMaxOperations;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.TriBool;
import net.minecraft.client.Minecraft;
import com.trevlar.menukit.api.slot.Storage;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits the pockets' ring rotation to the {@link HotbarCyclable} contract, the
 * pocket counterpart of Column Cycler's {@code ColumnCyclerCyclable}. Auto Tool
 * Switch and Restock use this through {@code HotbarCyclableRegistry} without
 * knowing about pockets; to them pockets are just more candidates.
 *
 * <h3>Why pockets are "extra" slots</h3>
 *
 * Column Cycler's cycle slots are in the player's real inventory (container
 * slots 9 to 35), so they fit {@link #hotbarPositionOf}'s 0 to 35 contract
 * directly. Pockets don't: their contents live in a separate attachment
 * ({@link Pockets#POCKETS}), outside slots 0 to 35 entirely. So they are
 * offered through {@link #extraSearchSlots(Player)} with an <b>encoded id</b>
 * (see {@link #POCKET_ID_BASE}) that the registry routes back here, and their
 * live contents are read straight from the synced attachment. That is the same
 * path the cycle HUD uses, and it works in the world, where the pocket
 * <i>slots</i> are inert.
 *
 * <h3>The server brings items to the hotbar</h3>
 *
 * Unlike Column Cycler, whose rotation applies locally at once, a pocket
 * rotation is a {@link PocketRotateC2S} round trip, so the rotated contents
 * arrive a tick or two later. {@link #bringToHotbar(int)} therefore marks the
 * destination hotbar slot via {@link AutoRestockSuppression} so the late
 * arrival isn't mistaken for the item running out. Pocket tools aren't in
 * Restock's snapshot of slots 0 to 35, so a newly arrived one would otherwise
 * look like a brand-new stack. Column Cycler doesn't need this because its
 * slots <i>are</i> in the snapshot and its rotation is immediate.
 */
public final class PocketCyclable implements HotbarCyclable {

    /** Registered once via {@code HotbarCyclableRegistry#register} at IM client init. */
    public static final PocketCyclable INSTANCE = new PocketCyclable();

    private PocketCyclable() {}

    /**
     * Base of the encoded extra-slot ids: {@code POCKET_ID_BASE +
     * flatIndex(hotbar, depth)}, covering {@code [100, 100 + TOTAL)}. 100 is
     * well clear of inventory slots 0 to 35 <i>and</i> Column Cycler's 9 to 35,
     * which meets {@link HotbarCyclable}'s rule that ids don't overlap, so a
     * returned id routes to exactly one cycler.
     */
    private static final int POCKET_ID_BASE = 100;

    /** Encodes a pocket's {@code (hotbar, depth)} as an extra-slot id. */
    public static int encodeId(int hotbar, int depth) {
        return POCKET_ID_BASE + Pockets.flatIndex(hotbar, depth);
    }

    /** True if {@code id} is one of this cycler's encoded pocket ids. */
    public static boolean isPocketId(int id) {
        return id >= POCKET_ID_BASE && id < POCKET_ID_BASE + Pockets.TOTAL;
    }

    /** The hotbar column of a pocket id (undefined unless {@link #isPocketId}). */
    public static int hotbarOf(int id) {
        return (id - POCKET_ID_BASE) / Pockets.MAX_PER_SLOT;
    }

    /** The depth of a pocket id (0 is closest to the hand). */
    public static int depthOf(int id) {
        return (id - POCKET_ID_BASE) % Pockets.MAX_PER_SLOT;
    }

    @Override
    public int hotbarPositionOf(int slot) {
        if (!isPocketId(slot)) return -1;
        if (!IMConfig.pocketCyclerEnabled()) return -1;
        int hotbar = hotbarOf(slot);
        int depth = depthOf(slot);
        // Only shown pockets are reachable: depth must be within the hotbar
        // slot's current per-world pocket count. (Hidden pockets exist on the
        // server but aren't part of any active cycle.)
        if (depth >= PocketState.count(hotbar)) return -1;
        // A pocket's column is the hotbar position the rotation brings it to.
        return hotbar;
    }

    @Override
    public List<ExtraSlot> extraSearchSlots(Player player) {
        if (!IMConfig.pocketCyclerEnabled()) return List.of();
        // Whether Restock and Auto Tool Switch may take from pockets is their
        // reach (Inventory Plus's Reach tab): the caller filters these through
        // the cycler's allows, which Inventory Plus's veto answers.
        // Read live contents from the synced attachment. This works in the world,
        // where the pocket slots are inert and hidden (the cycle HUD does the same).
        Storage pockets = Pockets.POCKETS.bind(player);
        List<ExtraSlot> out = new ArrayList<>();
        for (int hotbar = 0; hotbar < Pockets.HOTBAR_SLOTS; hotbar++) {
            int count = PocketState.count(hotbar);
            for (int depth = 0; depth < count; depth++) {
                ItemStack stack = pockets.getStack(Pockets.flatIndex(hotbar, depth));
                if (stack.isEmpty()) continue;
                out.add(new ExtraSlot(encodeId(hotbar, depth), stack));
            }
        }
        return out;
    }

    @Override
    public CyclerOperation bringToHotbar(int slot) {
        return bringToHotbar(slot, InventoryMaxOperations.POCKET_CYCLE, InventoryMaxOperations.POCKET_CYCLE);
    }

    /**
     * Brings the pocket down as {@code take}/{@code put}: a restock or tool switch
     * through a pocket is judged as that, not as the player cycling. Asked here
     * before sending, and again on the server for the operation the packet names.
     * The undo reverses under the same operation.
     */
    @Override
    public CyclerOperation bringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        // Check the id again: the caller may hold a stale one, or the pocket
        // count may have changed between the query and now.
        if (hotbarPositionOf(slot) == -1) return CyclerOperation.NO_OP;
        int hotbar = hotbarOf(slot);
        int depth = depthOf(slot);
        int count = PocketState.count(hotbar);
        // Fewest steps to bring depth d into the hand: FORWARD d+1 steps (each
        // brings pocket 0 into the hand) or BACKWARD count-d steps (each brings
        // the topmost pocket into the hand). Take the shorter direction, as
        // ColumnCyclerRotator does, since each step is one rotation packet.
        int fwdSteps = depth + 1;
        int bwdSteps = count - depth;
        boolean forward = fwdSteps <= bwdSteps;
        int steps = Math.min(fwdSteps, bwdSteps);
        if (steps <= 0) return CyclerOperation.NO_OP;
        // The same judgement Inventory Plus asks before choosing this pocket as a
        // source, so a pocket it chose is one this bring will run for.
        if (!allowsBringToHotbar(slot, take, put)) return CyclerOperation.NO_OP;
        rotate(hotbar, count, forward, steps, take, put);
        // Undo: same step count, opposite direction. The parameters are captured
        // so the reversal doesn't depend on current state, which may have
        // drifted, as the CyclerOperation contract requires.
        final int undoHotbar = hotbar;
        final int undoCount = count;
        final int undoSteps = steps;
        final boolean undoForward = !forward;
        return () -> rotate(undoHotbar, undoCount, undoForward, undoSteps, take, put);
    }

    @Override
    public boolean quickMoveOut(int slot) {
        return quickMoveOut(slot, InventoryMaxOperations.POCKET_CYCLE, InventoryMaxOperations.POCKET_CYCLE);
    }

    /**
     * Quick-moves the pocket out as {@code take}/{@code put}. The pocket being
     * emptied is asked here; where the item lands is judged on the server, inside
     * the quick-move, under the operation the packet names.
     */
    @Override
    public boolean quickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        // Same id check as bringToHotbar: only this cycler's shown pockets.
        if (hotbarPositionOf(slot) == -1) return false;
        if (!allowsQuickMoveOut(slot, take, put)) {
            // Ours, and refused. True, not false: false would tell the caller to
            // click this pocket id as an ordinary inventory slot.
            return true;
        }
        // The server does the move: in the world the pocket slot is inert on the
        // client, so the server runs the real quick-move and routes the item
        // (totem to the equipment slot, armor to its armor slot) the way a
        // shift-click would, then syncs the result back. No hotbar slot changes
        // here (the item leaves the pocket for its destination), so unlike
        // bringToHotbar there's no Restock slot to reset.
        ClientPlayNetworking.send(new PocketQuickMoveC2S(hotbarOf(slot), depthOf(slot), take.id(), put.id()));
        return true;
    }

    /**
     * Whether bringing this pocket down as {@code take}/{@code put} would run:
     * every slot in its hotbar column's ring, the hotbar slot included, allows
     * both, which is {@link PocketRing#rotationAllowed}. Inventory Plus asks
     * this while it chooses a source for a restock or a tool switch, so a
     * pocket that would refuse is passed over instead of chosen and then
     * silently not moved. {@link #bringToHotbar} applies the same answer.
     */
    @Override
    public boolean allowsBringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (hotbarPositionOf(slot) == -1) return false;
        Player player = Minecraft.getInstance().player;
        int hotbar = hotbarOf(slot);
        return player != null && PocketRing.rotationAllowed(
                player.inventoryMenu, player, hotbar, PocketState.count(hotbar), take, put);
    }

    /**
     * Whether quick-moving this pocket out as {@code take}/{@code put} would
     * run: the pocket allows {@code take}, which is
     * {@link PocketRing#takeAllowed}. Where the item lands is judged on the
     * server inside the move, so {@code put} is not asked here.
     * {@link #quickMoveOut} applies the same answer.
     */
    @Override
    public boolean allowsQuickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (hotbarPositionOf(slot) == -1) return false;
        Player player = Minecraft.getInstance().player;
        return player != null && PocketRing.takeAllowed(
                player.inventoryMenu, player, hotbarOf(slot), depthOf(slot), take);
    }

    // Sends steps one-step rotation requests for hotbar's ring, and marks the
    // slot as deliberately changing so Restock resets its baseline across the
    // round trip rather than reading the swapped-in tool as damaged or run out.
    // (The class doc explains why pockets need this and Column Cycler doesn't.)
    private static void rotate(int hotbar, int count, boolean forward, int steps,
                               BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        InventoryPlusApi.suppressRestockFor(hotbar);
        for (int i = 0; i < steps; i++) {
            ClientPlayNetworking.send(new PocketRotateC2S(hotbar, count, forward, take.id(), put.id()));
        }
    }
}
