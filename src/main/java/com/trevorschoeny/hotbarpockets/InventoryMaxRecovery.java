package com.trevorschoeny.hotbarpockets;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Gives players back what Inventory Max held.
 *
 * <p>Inventory Max 1.3.2 kept its 27 pockets and its elytra and totem slots in two
 * Fabric attachments on the player, {@code inventorymax:pockets} and
 * {@code inventorymax:equipment}, and never asked anyone to empty them. Hotbar Pockets
 * 2.0.0 arrives as an update on the same Modrinth project with new storage, so without
 * this those items would sit in player files under ids nothing registers, and Fabric
 * would drop them with a warning on the next save.
 *
 * <p>This class registers both ids again, with the codec Inventory Max saved them with
 * (vanilla's {@link ItemContainerContents}), for this recovery only. It is the one place
 * in the mod that uses an {@code inventorymax:} id: nothing else reads or writes these
 * attachments. On join, whatever they hold goes into the player's inventory, what doesn't
 * fit lands at their feet, and both attachments are removed so it happens once.
 */
public final class InventoryMaxRecovery {

    private InventoryMaxRecovery() {}

    // Inventory Max's exact ids. Changing either string loses the items it names.
    static final Identifier LEGACY_POCKETS_ID = Identifier.fromNamespaceAndPath("inventorymax", "pockets");
    static final Identifier LEGACY_EQUIPMENT_ID = Identifier.fromNamespaceAndPath("inventorymax", "equipment");

    // Persistent, as Inventory Max had them, so a player who doesn't join for months
    // keeps their items on disk until they do. No initializer: an absent attachment
    // means there is nothing to return.
    private static AttachmentType<ItemContainerContents> legacyPockets;
    private static AttachmentType<ItemContainerContents> legacyEquipment;

    /** Call once from the main entrypoint. */
    public static void register() {
        legacyPockets = AttachmentRegistry.create(LEGACY_POCKETS_ID,
                builder -> builder.persistent(ItemContainerContents.CODEC));
        legacyEquipment = AttachmentRegistry.create(LEGACY_EQUIPMENT_ID,
                builder -> builder.persistent(ItemContainerContents.CODEC));
        // JOIN runs on the server thread, with the player already in their level.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> recover(handler.player));
    }

    private static void recover(ServerPlayer player) {
        ItemContainerContents pockets = player.removeAttached(legacyPockets);
        ItemContainerContents equipment = player.removeAttached(legacyEquipment);
        if (pockets == null && equipment == null) return;

        List<ItemStack> stacks = new ArrayList<>();
        if (pockets != null) pockets.nonEmptyItemCopyStream().forEach(stacks::add);
        if (equipment != null) equipment.nonEmptyItemCopyStream().forEach(stacks::add);
        if (stacks.isEmpty()) return;

        List<ItemStack> leftovers = giveBack(player.getInventory().getNonEquipmentItems(), stacks);
        player.getInventory().setChanged();

        // What didn't fit drops where the player stands, without the usual throw: still,
        // only they can pick it up, and with no pickup delay, so the moment they make
        // room it comes in. A plain drop would also count toward their "dropped" stat.
        for (ItemStack rest : leftovers) {
            ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), rest, 0, 0, 0);
            item.setThrower(player);
            item.setTarget(player.getUUID());
            item.setNoPickUpDelay();
            player.level().addFreshEntity(item);
        }

        player.sendSystemMessage(leftovers.isEmpty()
                ? Component.translatableWithFallback("hotbarpockets.inventorymax_returned",
                        "Your Inventory Max items are back in your inventory.")
                : Component.translatableWithFallback("hotbarpockets.inventorymax_returned_dropped",
                        "Your Inventory Max items were returned. What didn't fit is at your feet."));
        HotbarPockets.LOGGER.info("Returned {} Inventory Max stack(s) to {}, {} dropped",
                stacks.size(), player.getName().getString(), leftovers.size());
    }

    /**
     * Puts each stack into {@code slots} (the player's 36 main and hotbar slots), topping
     * up matching stacks first and then filling empty slots, and returns what didn't fit.
     * The stacks are consumed.
     *
     * <p>Not {@code Inventory.add}: for a player in creative it deletes whatever doesn't
     * fit, and these are items the player can't get back any other way.
     */
    static List<ItemStack> giveBack(NonNullList<ItemStack> slots, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            for (ItemStack held : slots) {
                if (stack.isEmpty()) break;
                if (ItemStack.isSameItemSameComponents(held, stack)) {
                    int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                    if (moved > 0) {
                        held.grow(moved);
                        stack.shrink(moved);
                    }
                }
            }
            for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) {
                    slots.set(i, stack.split(stack.getMaxStackSize()));
                }
            }
            if (!stack.isEmpty()) leftovers.add(stack);
        }
        return leftovers;
    }
}
