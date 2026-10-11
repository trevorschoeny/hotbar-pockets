package com.trevorschoeny.inventorymax.mixin;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevorschoeny.inventorymax.equipment.EquipmentSlots;
import com.trevorschoeny.inventorymax.pocket.SliceStorage;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.slot.Storage;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates the equipment slots, like {@link InventoryMenuPocketMixin} does for
 * pockets. Injects at the TAIL of {@link InventoryMenu}'s constructor and adds
 * two fixed, type-filtered slots stacked above the offhand: an elytra slot and
 * a totem slot.
 *
 * <p>They are two separate slots because they accept different items, so each
 * needs its own {@link InteractionPolicy} and therefore its own group. Each
 * holds a single item (a totem would otherwise stack to 64). The contents are a
 * 1-slot window onto the shared 2-slot {@link EquipmentSlots#EQUIPMENT}
 * attachment through {@link SliceStorage}, which also fires the advancement
 * trigger on server-side writes (so putting an elytra into the slot still earns
 * "Sky's the Limit").
 *
 * <p>Unlike pockets there is no hover reveal and no moving at runtime. The
 * slots are always visible at a fixed spot <em>while the feature is on</em>:
 * the reveal predicate is the Equipment Slots toggle. As with pockets, the
 * server keeps syncing the contents, and when the feature is off the client
 * hides the slots and makes them inert while their contents stay. Running at
 * the constructor TAIL means the slots come back on every menu rebuild (login,
 * respawn, dimension change) on both sides, with no lifecycle hook.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuEquipmentMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void inventoryMax$addEquipmentSlots(Inventory inv, boolean active,
                                                  Player player, CallbackInfo ci) {
        Storage backing = EquipmentSlots.EQUIPMENT.bind(player);
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        // Elytra slot (top): accepts only elytra, one item.
        CreatedSlots.onto(menu, player)
                .panel(EquipmentSlots.panelId(EquipmentSlots.ELYTRA_GROUP))
                .group(EquipmentSlots.ELYTRA_GROUP)
                .category(EquipmentSlots.EQUIPMENT_ELYTRA)
                .storage(new SliceStorage(backing, EquipmentSlots.ELYTRA_INDEX, 1, player))
                .layout(EquipmentSlots.SLOT_X, EquipmentSlots.ELYTRA_Y, 1)
                .revealWhen(IMConfig::equipmentSlotsEnabled)
                // Created without behavior: the accept filter and single-item cap
                // (GATING), Curse of Binding (BINDING) and XP mending (MENDING) are
                // declared by the slot's address in EquipmentSlots.declareSlotBehavior().
                .register();

        // Totem slot (bottom, just above the offhand): accepts only totems, one item.
        CreatedSlots.onto(menu, player)
                .panel(EquipmentSlots.panelId(EquipmentSlots.TOTEM_GROUP))
                .group(EquipmentSlots.TOTEM_GROUP)
                .category(EquipmentSlots.EQUIPMENT_TOTEM)
                .storage(new SliceStorage(backing, EquipmentSlots.TOTEM_INDEX, 1, player))
                .layout(EquipmentSlots.SLOT_X, EquipmentSlots.TOTEM_Y, 1)
                .revealWhen(IMConfig::equipmentSlotsEnabled)
                // Created without behavior: GATING (totems only, single item), BINDING
                // and MENDING are declared by address in EquipmentSlots.declareSlotBehavior().
                .register();
    }
}
