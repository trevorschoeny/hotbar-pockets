package com.trevorschoeny.inventorymax.operations;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.KindTag;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotOperations.AppliesTo;
import com.trevlar.menukit.api.window.SlotOperations.Role;
import com.trevlar.menukit.api.window.Tier;
import com.trevlar.menukit.api.window.TriBool;

import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * Inventory Max's own slot operation, Pocket Cycler, and the recipe it is
 * built with.
 *
 * <p>Common code, safe on a dedicated server: it names MenuKit types only. A
 * pocket move can also serve another mod's operation (Inventory Plus's restock
 * or Auto Tool Switch); the packet names it by id and the server looks it up in
 * MenuKit with {@link #served}, which is why no copy of those keys lives here.
 */
public final class InventoryMaxOperations {

    private InventoryMaxOperations() {}

    /** Pocket Cycler may rotate items through a slot. Role BOTH. */
    public static final BehaviorKey<TriBool> POCKET_CYCLE = recipe("inventorymax", "pocket_cycle");

    /**
     * Defines Pocket Cycler with its role and where it acts, so MenuKit lists
     * it and the Reach tab offers only those groups. Common init, so it is
     * listed from the title screen and known to a dedicated server. Idempotent.
     *
     * <p>It moves items between a hotbar slot and the pockets behind it, so it
     * applies to the hotbar and the pockets, the same shape as Inventory Plus's
     * cyclers. A known limit: MenuKit's {@code AppliesTo}
     * cannot name the hotbar and pockets without the main inventory, because a
     * pocket declares {@code PLAYER_INVENTORY} and goes where that category
     * goes; so it takes the carried slots and drops the equipment slots. The
     * main inventory stays offered until {@code AppliesTo} can name a mod's
     * set on its own.
     */
    public static void define() {
        AppliesTo pocketsAndHotbar = AppliesTo.vanilla(SlotGroupCategory.PLAYER_INVENTORY, SlotGroupCategory.PLAYER_HOTBAR)
                .except(CreatedSlots.groupId(EquipmentSlots.panelId(EquipmentSlots.ELYTRA_GROUP), EquipmentSlots.ELYTRA_GROUP),
                        CreatedSlots.groupId(EquipmentSlots.panelId(EquipmentSlots.TOTEM_GROUP), EquipmentSlots.TOTEM_GROUP));
        SlotOperations.define(POCKET_CYCLE, Role.BOTH, pocketsAndHotbar);
    }

    /**
     * The operation a pocket packet names, as the server knows it: any operation
     * some mod defined, typed as the allow/deny kind every slot operation is.
     * {@code null} for an id nobody defined, or of another type; the caller
     * then drops the packet rather than guess (it is never read as Pocket Cycler).
     */
    @SuppressWarnings("unchecked")
    public static @Nullable BehaviorKey<TriBool> served(Identifier id) {
        BehaviorKey<?> key = SlotOperations.byId(id);
        return key != null && key.valueType() == TriBool.class ? (BehaviorKey<TriBool>) key : null;
    }

    /**
     * MenuKit's recipe for an operation of one's own ({@code docs/recipes.md}): a
     * {@link TriBool} defaulting to allowed, on the server tier, for vanilla and
     * created slots. {@link BehaviorKey} is a record, so the same recipe with the
     * same id gives an equal key, wherever it is built.
     */
    public static BehaviorKey<TriBool> recipe(String namespace, String path) {
        return BehaviorKey.of(Identifier.fromNamespaceAndPath(namespace, path),
                TriBool.class, TriBool.TRUE, Tier.SERVER, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
    }
}
