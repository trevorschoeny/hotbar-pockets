package com.trevorschoeny.hotbarpockets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Inventory Max recovery without a game: a player file's attachments, written by
 * hand in the shape Inventory Max 1.3.2 left on disk, decode through the registered
 * codec under the registered ids, and the give-back puts the stacks into an inventory's
 * 36 slots or hands back what doesn't fit.
 */
class InventoryMaxRecoveryTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Items get their default components when a world's registries load; bind them
        // the way the data generator does, from the vanilla registries alone.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    // One saved slot, as ItemContainerContents writes it: {slot, item: {id, count, components}},
    // with count left out when it is 1.
    private static CompoundTag slot(int index, String id, int count, CompoundTag components) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id);
        if (count != 1) item.putInt("count", count);
        if (components != null) item.put("components", components);
        CompoundTag entry = new CompoundTag();
        entry.putInt("slot", index);
        entry.put("item", item);
        return entry;
    }

    // A player file's Fabric attachment block, as Inventory Max left it: pockets at
    // flat indexes (hotbar * 3 + depth), equipment at 0 (elytra) and 1 (totem).
    private static CompoundTag inventoryMaxPlayerFile() {
        ListTag pockets = new ListTag();
        pockets.add(slot(0, "minecraft:diamond", 5, null));
        pockets.add(slot(26, "minecraft:cobblestone", 64, null));
        ListTag equipment = new ListTag();
        CompoundTag worn = new CompoundTag();
        worn.putInt("minecraft:damage", 100);
        equipment.add(slot(0, "minecraft:elytra", 1, worn));
        equipment.add(slot(1, "minecraft:totem_of_undying", 1, null));
        CompoundTag attachments = new CompoundTag();
        attachments.put(InventoryMaxRecovery.LEGACY_POCKETS_ID.toString(), pockets);
        attachments.put(InventoryMaxRecovery.LEGACY_EQUIPMENT_ID.toString(), equipment);
        CompoundTag player = new CompoundTag();
        player.put("fabric:attachments", attachments);
        return player;
    }

    private static List<ItemStack> decode(CompoundTag player, String id) {
        var tag = player.getCompoundOrEmpty("fabric:attachments").get(id);
        ItemContainerContents contents = ItemContainerContents.CODEC
                .parse(NbtOps.INSTANCE, tag).getOrThrow();
        // The hand-built tag is exactly what the codec writes, not just something it accepts.
        assertEquals(tag, ItemContainerContents.CODEC.encodeStart(NbtOps.INSTANCE, contents).getOrThrow());
        List<ItemStack> out = new ArrayList<>();
        contents.nonEmptyItemCopyStream().forEach(out::add);
        return out;
    }

    private static List<ItemStack> allSaved() {
        CompoundTag file = inventoryMaxPlayerFile();
        List<ItemStack> stacks = new ArrayList<>(decode(file, "inventorymax:pockets"));
        stacks.addAll(decode(file, "inventorymax:equipment"));
        return stacks;
    }

    @Test
    void decodesInventoryMaxsOnDiskShape() {
        assertEquals("inventorymax:pockets", InventoryMaxRecovery.LEGACY_POCKETS_ID.toString());
        assertEquals("inventorymax:equipment", InventoryMaxRecovery.LEGACY_EQUIPMENT_ID.toString());
        List<ItemStack> s = allSaved();
        assertEquals(4, s.size());
        assertTrue(s.get(0).is(Items.DIAMOND));
        assertEquals(5, s.get(0).getCount());
        assertTrue(s.get(1).is(Items.COBBLESTONE));
        assertEquals(64, s.get(1).getCount());
        assertTrue(s.get(2).is(Items.ELYTRA));
        assertEquals(100, s.get(2).get(DataComponents.DAMAGE));
        assertTrue(s.get(3).is(Items.TOTEM_OF_UNDYING));
    }

    @Test
    void emptyInventoryTakesEverything() {
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        List<ItemStack> leftovers = InventoryMaxRecovery.giveBack(slots, allSaved());
        assertTrue(leftovers.isEmpty());
        assertEquals(4, slots.stream().filter(s -> !s.isEmpty()).count());
        assertEquals(100, slots.get(2).get(DataComponents.DAMAGE));
    }

    @Test
    void fullInventoryTopsUpThenHandsBackTheRest() {
        // Full of dirt, except one slot of 60 diamonds: 4 of the 5 diamonds top it up.
        NonNullList<ItemStack> slots = NonNullList.withSize(36, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) slots.set(i, new ItemStack(Items.DIRT, 64));
        slots.set(7, new ItemStack(Items.DIAMOND, 60));
        List<ItemStack> leftovers = InventoryMaxRecovery.giveBack(slots, allSaved());
        assertEquals(64, slots.get(7).getCount());
        assertEquals(4, leftovers.size());
        assertTrue(leftovers.get(0).is(Items.DIAMOND));
        assertEquals(1, leftovers.get(0).getCount());
        assertEquals(64, leftovers.get(1).getCount());
        assertTrue(leftovers.get(2).is(Items.ELYTRA));
        assertTrue(leftovers.get(3).is(Items.TOTEM_OF_UNDYING));
    }
}
