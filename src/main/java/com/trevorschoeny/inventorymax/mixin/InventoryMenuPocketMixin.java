package com.trevorschoeny.inventorymax.mixin;

import com.trevorschoeny.inventorymax.pocket.PocketHoverState;
import com.trevorschoeny.inventorymax.pocket.Pockets;
import com.trevorschoeny.inventorymax.pocket.SliceStorage;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.slot.SlotGroupCategory;
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
 * Creates the pocket slots. Injects at the TAIL of {@link InventoryMenu}'s
 * constructor and adds the full pocket set: one slot for every (hotbar slot,
 * depth) pair, 27 in all.
 *
 * <p>Each slot is its own panel with its own reveal predicate based on
 * {@link PocketHoverState#isRevealed(int, int)}, so each depth can be shown on
 * its own (the panel-level reveal API alone can't show 1 of 3). All 27 slots
 * always exist on the server, so their contents sync and persist; the client
 * shows only the configured count per hotbar slot.
 *
 * <p>Running at the constructor TAIL means the slots come back on every menu
 * rebuild (login, respawn, dimension change) on both sides, with no lifecycle
 * hook.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuPocketMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void inventoryMax$addPocketSlots(Inventory inv, boolean active,
                                                 Player player, CallbackInfo ci) {
        Storage backing = Pockets.POCKETS.bind(player);
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        for (int n = 0; n < Pockets.HOTBAR_SLOTS; n++) {
            for (int d = 0; d < Pockets.MAX_PER_SLOT; d++) {
                final int hotbar = n;
                final int depth = d;
                // 1-slot window onto the shared 27-slot attachment. The slice
                // also fires the advancement trigger on server-side writes.
                Storage slice = new SliceStorage(
                        backing, Pockets.flatIndex(n, d), 1, player);
                CreatedSlots.onto(menu, player)
                        .panel(Pockets.panelId(n, d))
                        .group(Pockets.groupId(n, d))
                        // Pockets are general player storage: a feature searching the
                        // player's inventory should find a stack in a pocket the same
                        // way it finds one in the main grid. Unlike the equipment slots,
                        // which get their own categories precisely so searches skip them.
                        .category(SlotGroupCategory.PLAYER_INVENTORY)
                        .storage(slice)
                        .layout(Pockets.pocketX(n), Pockets.pocketY(d), 1)
                        // Server-safe predicate (no client imports); only
                        // evaluated client-side by MenuKit.
                        .revealWhen(() -> PocketHoverState.isRevealed(hotbar, depth))
                        // Created without behavior: MENDING (a damaged Mending item in
                        // any pocket repairs from XP) is declared by the slot's address
                        // in Pockets.declareSlotBehavior(), not on the builder.
                        .register();
            }
        }
    }
}
