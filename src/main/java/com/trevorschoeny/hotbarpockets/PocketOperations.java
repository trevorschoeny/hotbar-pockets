package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.KindTag;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotOperations.AppliesTo;
import com.trevlar.slotkit.api.window.SlotOperations.Role;
import com.trevlar.slotkit.api.window.Tier;
import com.trevlar.slotkit.api.window.TriBool;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * Hotbar Pockets' own slot operation, Pocket Cycler.
 *
 * <p>Common code, safe on a dedicated server. A pocket move can also serve another
 * mod's operation (Inventory Plus's restock or Auto Tool Switch); the packet names it
 * by id and the server looks it up with {@link #served}, so no copy of those keys lives
 * here.
 */
public final class PocketOperations {

    private PocketOperations() {}

    /** Pocket Cycler may rotate items through a slot. Role BOTH. */
    public static final BehaviorKey<TriBool> POCKET_CYCLE = BehaviorKey.of(
            Identifier.fromNamespaceAndPath(HotbarPockets.MOD_ID, "pocket_cycle"),
            TriBool.class, TriBool.TRUE, Tier.SERVER, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Defines Pocket Cycler so SlotKit lists it and locks or Reach can refuse it. It
     * moves items between a hotbar slot and the pockets behind it, so it applies to the
     * hotbar and to inventory-category groups, the pockets among them. {@code only}
     * admits another mod's group only by a category named here, so slots of their own
     * kind (an elytra or totem slot) never show under Pocket Cycler, without either mod
     * naming the other.
     */
    public static void define() {
        SlotOperations.define(POCKET_CYCLE, Role.BOTH,
                AppliesTo.only(SlotGroupCategory.PLAYER_HOTBAR, SlotGroupCategory.PLAYER_INVENTORY));
    }

    /**
     * The operation a pocket packet names, as the server knows it, or {@code null} for an
     * id nobody defined or one of another type; the caller then drops the packet rather
     * than guess (it is never read as Pocket Cycler).
     */
    @SuppressWarnings("unchecked")
    public static @Nullable BehaviorKey<TriBool> served(Identifier id) {
        BehaviorKey<?> key = SlotOperations.byId(id);
        return key != null && key.valueType() == TriBool.class ? (BehaviorKey<TriBool>) key : null;
    }
}
