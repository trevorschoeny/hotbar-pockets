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
 * Shift-click (quick-move) routing for the equipment slots — the consumer work
 * the slot kit leaves to us (MenuKit's {@code quickMoveStack} knows nothing of
 * registered slots). Runs server-side for the authoritative move and client-side
 * for prediction. Creative slot sync is the library's job (§0051) — its
 * creative-set-slot bridge routes placements into registered slots — so this mixin
 * does not special-case creative.
 *
 * <p>Rules (Trev, 2026-06-17), all gated on the equipment slots being present:
 * <ul>
 *   <li><b>Elytra → dedicated elytra slot, never the chestplate slot.</b> A
 *       shift-clicked elytra goes to the equipment elytra slot first; if that's
 *       full it falls back to the normal main↔hotbar shuffle — it must <em>never</em>
 *       land in the vanilla chest-armor slot (which vanilla would otherwise do,
 *       since an elytra reports {@code EquipmentSlot.CHEST}).</li>
 *   <li><b>Totem → dedicated totem slot first</b> (if empty), else vanilla.</li>
 *   <li><b>Out of an equipment slot → the inventory</b> (main + hotbar),
 *       skipping armor/offhand.</li>
 * </ul>
 *
 * <p>We HEAD-inject and, for the cases we own, fully perform the move +
 * vanilla's exact tail bookkeeping ({@code setByPlayer}/{@code setChanged}/
 * count-compare/{@code onTake}) and cancel; everything else falls through to
 * vanilla untouched.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuQuickMoveMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void inventoryMax$equipQuickMove(Player player, int index,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        if (!IMConfig.equipmentSlotsEnabled()) return; // feature off → vanilla routing
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        Slot from = menu.slots.get(index);
        if (from == null || !from.hasItem()) return; // let vanilla return EMPTY

        int elytraIdx = inventoryMax$findEquip(menu, EquipmentSlots.ELYTRA_GROUP);
        int totemIdx = inventoryMax$findEquip(menu, EquipmentSlots.TOTEM_GROUP);
        if (elytraIdx < 0 || totemIdx < 0) return; // equipment slots absent → vanilla

        ItemStack live = from.getItem(); // the live stack moveItemStackTo mutates

        // ── OUT of an equipment slot → main + hotbar (skip armor/offhand) ──
        if (index == elytraIdx || index == totemIdx) {
            // No binding check needed here: vanilla's doClick pre-checks
            // slot.mayPickup before it ever calls quickMoveStack, and the §0053
            // MKC binding behavior (SlotSpec.binding() / ContainerKeys.BINDING)
            // makes mayPickup false for a bound item — so a bound item's
            // shift-click never reaches this router.
            // Curse of Binding is fully library-enforced on every removal path.
            cir.setReturnValue(inventoryMax$moveAndFinish(player, from, live, 9, 45, false));
            return;
        }

        // ── An elytra anywhere else → dedicated elytra slot; NEVER the chestplate ──
        if (EquipmentSlots.isElytra(live) && IMConfig.elytraSlotOn()) {
            if (!menu.slots.get(elytraIdx).hasItem()) {
                cir.setReturnValue(inventoryMax$moveAndFinish(
                        player, from, live, elytraIdx, elytraIdx + 1, false));
                return;
            }
            // Elytra slot occupied → normal inventory shuffle, which by
            // construction never touches the chest-armor slot.
            int[] dest = inventoryMax$inventoryShuffle(index);
            cir.setReturnValue(inventoryMax$moveAndFinish(
                    player, from, live, dest[0], dest[1], false));
            return;
        }

        // ── A totem from a non-equipment slot → dedicated totem slot if empty ──
        if (EquipmentSlots.isTotem(live) && IMConfig.totemSlotOn() && !menu.slots.get(totemIdx).hasItem()) {
            cir.setReturnValue(inventoryMax$moveAndFinish(
                    player, from, live, totemIdx, totemIdx + 1, false));
            return;
        }

        // Anything else (chestplates, ordinary items, a 2nd totem) → vanilla.
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

    /** Vanilla's non-equippable destination: main→hotbar, hotbar→main, else whole inventory. */
    private static int[] inventoryMax$inventoryShuffle(int index) {
        if (index >= 9 && index < 36) return new int[]{36, 45};  // main → hotbar
        if (index >= 36 && index < 45) return new int[]{9, 36};  // hotbar → main
        return new int[]{9, 45};                                 // armor/offhand → inventory
    }

    /**
     * Performs the move and replicates vanilla {@code quickMoveStack}'s tail
     * bookkeeping exactly. Returns the original stack (what was moved) or EMPTY
     * when nothing moved — matching vanilla's contract.
     */
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
