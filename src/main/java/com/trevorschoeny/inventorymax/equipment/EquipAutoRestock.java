package com.trevorschoeny.inventorymax.equipment;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.api.window.SlotOperations;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import com.trevorschoeny.inventorymax.config.IMConfig;

import com.trevorschoeny.inventoryplus.api.HotbarCyclable.ExtraSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Auto-restock for the equipment totem slot — when the slot's totem is consumed
 * by a death save, slide another totem in from the inventory so one is always
 * ready. Composes Inventory Plus's {@link AutoRestockSearch} (IM→IP, allowed); IM
 * owns the move into its own registered slot via the shift-click routing we already
 * built.
 *
 * <p>Client tick, mirroring IP's own auto-restock. Fires only on the consume
 * <em>transition</em> (slot held a totem last tick, empty now) and only during
 * gameplay (no screen open) — so a death save refills, but manually pulling the
 * totem out in the inventory screen does not, and a slot that was never filled is
 * left alone. Follows IP's auto-restock toggle ({@code autoRestockItem} — a totem
 * is a non-damageable item, the same bucket IP refills the offhand under).
 */
public final class EquipAutoRestock {

    private EquipAutoRestock() {}

    /** Bare totem matched against the inventory (by item kind, not components).
     *  26.2: constructed lazily — ItemStack's ctor now requires the item's
     *  data components to be BOUND ("Components not bound yet" NPE otherwise),
     *  and class-init runs on the first client tick, before registry freeze
     *  completes. First tick() call after binding constructs it once. */
    private static ItemStack totemProbe = null;

    private static ItemStack totemProbe() {
        if (totemProbe == null) totemProbe = new ItemStack(Items.TOTEM_OF_UNDYING);
        return totemProbe;
    }

    /** Whether the totem slot held a totem last tick — to catch the consume transition. */
    private static boolean totemSlotFilled = false;

    /** Tick handler — register against {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft mc) {
        if (mc == null) return;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;
        if (player.containerMenu != player.inventoryMenu) return; // only the player's own menu

        boolean filledNow = !EquipmentSlots.getTotem(player).isEmpty();
        boolean justConsumed = totemSlotFilled && !filledNow;
        totemSlotFilled = filledNow;

        if (!justConsumed) return;                // only the full→empty transition
        if (mc.gui.screen() != null) return;            // gameplay only — not while editing the inventory
        if (!IMConfig.totemSlotOn()) return;            // totem slot off → no totem restock
        if (!InventoryPlusApi.isItemRestockEnabled()) return;  // follow the auto-restock toggle

        Inventory inv = player.getInventory();
        // A backup totem may live in a pocket too — outside the 0–35 model — so
        // include the cyclers' extra slots in the search.
        // Only pockets that would let a restock quick-move the totem out are
        // offered, so a refused pocket is passed over rather than chosen and
        // then refused (Inventory Plus 1.7.0's extraSlotsToQuickMove).
        List<ExtraSlot> extras = InventoryPlusApi.extraSlotsToQuickMove(player,
                InventoryPlusOperations.RESTOCK_TAKE, InventoryPlusOperations.RESTOCK_PUT);
        // Same bucket IP refills the offhand under (see class doc) — the totem
        // is a non-damageable item, so this follows Item Restock's locked-item
        // setting, per IP 1.4.0's Locked Items (Trev, 2026-09-06).
        int source = InventoryPlusApi.findRestockSource(inv, totemProbe(), extras);
        if (source == InventoryPlusApi.NO_SOURCE) return;
        // The totem slot is the one being refilled; asked before either path. The
        // source was screened for restock-take by Inventory Plus's search.
        if (!totemSlotAllowsRestock(player)) return;

        // A pocket source can't be shift-clicked client-side in-world (the pocket
        // slot is inert), so route it through the cycler, which performs the move
        // server-side (the totem lands in the equip slot via the same quick-move
        // routing). Returns false for an ordinary 0–35 source — fall through to
        // the normal client-side quick-move.
        if (InventoryPlusApi.quickMoveOut(source,
                InventoryPlusOperations.RESTOCK_TAKE, InventoryPlusOperations.RESTOCK_PUT)) return;

        // Inventory source: the search returns a container index and the
        // shift-click names a menu slot, so convert through Inventory Plus's one
        // conversion (the same one its own restock uses), then shift-click it.
        // Our quick-move routing drops the totem into the now-empty totem slot.
        int menuSlot = PlayerMenuSlots.menuIndexOf(player.inventoryMenu, player, source);
        if (menuSlot < 0) return;
        // A shift-click, judged as a restock rather than as the shift-click it is.
        SlotOperations.as(InventoryPlusOperations.RESTOCK_TAKE, InventoryPlusOperations.RESTOCK_PUT,
                () -> gameMode.handleContainerInput(
                        player.inventoryMenu.containerId, menuSlot, 0, ContainerInput.QUICK_MOVE, player));
    }

    /** Whether restock may fill the equipment totem slot on the player's inventory menu. */
    private static boolean totemSlotAllowsRestock(LocalPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        for (Slot s : menu.slots) {
            if (s instanceof CreatedSlot mk && EquipmentSlots.TOTEM_GROUP.equals(mk.groupId())) {
                return SlotOperations.allows(menu, s, player, InventoryPlusOperations.RESTOCK_PUT);
            }
        }
        return false; // no totem slot on the menu: nothing to refill
    }
}
