package com.trevorschoeny.inventorymax.mixin;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-click (quick-move) routing for the equipment slots. The slot kit leaves
 * this to the consumer, since MenuKit's {@code quickMoveStack} knows nothing of
 * registered slots. Runs on the server for the authoritative move and on the
 * client for prediction. Creative slot sync is MenuKit's job (its creative
 * set-slot bridge routes placements into registered slots), so this mixin has
 * no creative special case.
 *
 * <p>Rules, all only when the equipment slots are present:
 * <ul>
 *   <li><b>An elytra goes to the elytra slot, never the chestplate slot.</b> A
 *       shift-clicked elytra goes to the equipment elytra slot first. If that is
 *       full it falls back to the normal main and hotbar shuffle. It must
 *       <em>never</em> land in the vanilla chest-armor slot, which vanilla would
 *       otherwise do, since an elytra reports {@code EquipmentSlot.CHEST}.</li>
 *   <li><b>A totem goes to the totem slot first</b> (if empty), else vanilla.</li>
 *   <li><b>Out of an equipment slot, items go to the inventory</b> (main and
 *       hotbar), skipping armor and offhand.</li>
 * </ul>
 *
 * <p>Injects at HEAD. For the cases above it does the whole move plus vanilla's
 * exact closing bookkeeping ({@code setByPlayer}, {@code setChanged}, the count
 * compare, {@code onTake}) and cancels; everything else goes to vanilla
 * untouched.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuQuickMoveMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void inventoryMax$equipQuickMove(Player player, int index,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        if (!IMConfig.equipmentSlotsEnabled()) return; // feature off: vanilla routing
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        Slot from = menu.slots.get(index);
        if (from == null || !from.hasItem()) return; // let vanilla return EMPTY

        int elytraIdx = inventoryMax$findEquip(menu, EquipmentSlots.ELYTRA_GROUP);
        int totemIdx = inventoryMax$findEquip(menu, EquipmentSlots.TOTEM_GROUP);
        if (elytraIdx < 0 || totemIdx < 0) return; // equipment slots absent: vanilla

        ItemStack live = from.getItem(); // the live stack moveItemStackTo mutates

        // Out of an equipment slot: to main and hotbar (skip armor and offhand).
        if (index == elytraIdx || index == totemIdx) {
            // No binding check needed here: vanilla's doClick pre-checks
            // slot.mayPickup before it ever calls quickMoveStack, and MKC's
            // binding behavior (SlotSpec.binding() / ContainerKeys.BINDING)
            // makes mayPickup false for a bound item, so a bound item's
            // shift-click never reaches this router. MenuKit enforces Curse of
            // Binding on every removal path.
            cir.setReturnValue(inventoryMax$moveAndFinish(player, from, live, 9, 45, false));
            return;
        }

        // An elytra from anywhere else: to the elytra slot, never the chestplate slot.
        if (EquipmentSlots.isElytra(live) && IMConfig.elytraSlotOn()) {
            if (!menu.slots.get(elytraIdx).hasItem()) {
                cir.setReturnValue(inventoryMax$moveAndFinish(
                        player, from, live, elytraIdx, elytraIdx + 1, false));
                return;
            }
            // Elytra slot occupied: the normal inventory shuffle, which never
            // touches the chest-armor slot.
            int[] dest = inventoryMax$inventoryShuffle(index);
            cir.setReturnValue(inventoryMax$moveAndFinish(
                    player, from, live, dest[0], dest[1], false));
            return;
        }

        // A totem from a non-equipment slot: to the totem slot if it is empty.
        if (EquipmentSlots.isTotem(live) && IMConfig.totemSlotOn() && !menu.slots.get(totemIdx).hasItem()) {
            cir.setReturnValue(inventoryMax$moveAndFinish(
                    player, from, live, totemIdx, totemIdx + 1, false));
            return;
        }

        // Anything else (chestplates, ordinary items, a second totem) goes to vanilla.
    }

    /** Menu index of the registered equipment slot in {@code groupId}, or -1. */
    private static int inventoryMax$findEquip(AbstractContainerMenu menu, String groupId) {
        for (int k = 0; k < menu.slots.size(); k++) {
            if (menu.slots.get(k) instanceof CreatedSlot mk && groupId.equals(mk.groupId())) {
                return k;
            }
        }
        return -1;
    }

    /** Vanilla's destination for a non-equippable item: main to hotbar, hotbar to main, else the whole inventory. */
    private static int[] inventoryMax$inventoryShuffle(int index) {
        if (index >= 9 && index < 36) return new int[]{36, 45};  // main → hotbar
        if (index >= 36 && index < 45) return new int[]{9, 36};  // hotbar → main
        return new int[]{9, 45};                                 // armor or offhand → inventory
    }

    // Does the move and repeats vanilla quickMoveStack's closing bookkeeping
    // exactly. Returns the original stack (what was moved), or EMPTY when
    // nothing moved, as vanilla does.
    private ItemStack inventoryMax$moveAndFinish(Player player, Slot from, ItemStack live,
                                                      int start, int end, boolean reverse) {
        ItemStack original = live.copy();
        boolean moved = ((AbstractContainerMenuMoveInvoker) (Object) this)
                .inventoryMax$moveItemStackTo(live, start, end, reverse);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (live.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY, original);
        } else {
            from.setChanged();
        }
        if (live.getCount() == original.getCount()) {
            return ItemStack.EMPTY; // nothing actually moved
        }
        from.onTake(player, live);
        return original;
    }
}
