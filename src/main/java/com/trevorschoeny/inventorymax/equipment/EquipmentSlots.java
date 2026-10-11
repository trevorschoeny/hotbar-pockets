package com.trevorschoeny.inventorymax.equipment;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.containers.api.slot.ContainerKeys;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.slot.SlotGroups;
import com.trevlar.menukit.api.slot.Storage;
import com.trevlar.menukit.containers.api.storage.StorageAttachment;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.GatingContext;
import com.trevlar.menukit.api.window.SlotGate;
import com.trevlar.menukit.api.window.TriBool;
import com.trevlar.menukit.api.window.Window;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * Equipment Slots: constants, the server-persistent storage, and the per-slot
 * accept filters.
 *
 * <h3>Design</h3>
 *
 * Two extra equipment slots, one for an <b>elytra</b> and one for a
 * <b>totem</b>, stacked directly above the vanilla offhand slot, so the player
 * can wear an elytra and a chestplate, or keep a totem ready and still hold
 * something in the offhand.
 *
 * <p>These are registered menu slots backed by a player attachment (the same
 * slot kit the pockets use), not new vanilla {@code EquipmentSlot}s. The
 * equipment enum can't be extended cleanly, and doing so would be fragile. The
 * catch is that a registered-attachment slot is invisible to vanilla's
 * equipment loops, so it gets no vanilla behavior automatically. Every vanilla
 * mechanic the slots need (glide, totem, render, mending, binding, death drop)
 * is made to also check the extra slot, with one narrow injection each that
 * reuses vanilla's code. This file holds only the storage and filters; the
 * per-mechanic changes are in the {@code mixin} package.
 *
 * <h3>Persistence</h3>
 *
 * A 2-slot player attachment (index {@link #ELYTRA_INDEX} and
 * {@link #TOTEM_INDEX}) survives logout, restart and death, and syncs through
 * vanilla's slot protocol. Declared at common init, bound by the slot mixin.
 */
public final class EquipmentSlots {

    /**
     * What the equipment slots are, in MenuKit's slot group categories (MKC 5.0.0).
     *
     * <p>Deliberately not {@code PLAYER_INVENTORY}. These are dedicated gear slots,
     * not general storage: a feature searching the player's inventory for a stack to
     * restock from, or a firework to fire, must not treat the elytra a player is
     * flying on as a spare item. Their own categories let those searches skip them
     * while a feature that specifically wants "the equipment elytra" can still ask.
     */
    public static final SlotGroupCategory EQUIPMENT_ELYTRA =
            new SlotGroupCategory("inventorymax", "equipment_elytra");
    public static final SlotGroupCategory EQUIPMENT_TOTEM =
            new SlotGroupCategory("inventorymax", "equipment_totem");

    private EquipmentSlots() {}

    public static final String MOD_ID = "inventorymax";

    /** Storage slot indices. */
    public static final int ELYTRA_INDEX = 0;
    public static final int TOTEM_INDEX = 1;
    public static final int TOTAL = 2;

    /** Slot panel and group ids. */
    public static final String ELYTRA_GROUP = "equip_elytra";
    public static final String TOTEM_GROUP = "equip_totem";

    // Screen-relative layout (frame top-left, added to leftPos/topPos).
    //
    // The slots stack directly above the vanilla offhand slot. Vanilla draws
    // the offhand item at (77, 62), so its 18px frame sits at (76, 61). A
    // registered slot's Slot.x/y (written by SlotElement each frame since
    // MenuKit 3.2.0) is its frame top-left, and vanilla insets the 16px item by
    // 1px as for any slot, so these are frame coordinates, 1px up and left of
    // the item, the same way the pocket column aligns to the hotbar. They may
    // need a pixel of adjustment, as the pockets did.

    /** Frame x, above the offhand frame (offhand item at 77, frame at 76). */
    public static final int SLOT_X = 76;
    /** Elytra slot frame y (top of the stack). */
    public static final int ELYTRA_Y = 25;
    /** Totem slot frame y (bottom of the stack, directly above the offhand frame at 61). */
    public static final int TOTEM_Y = 43;

    // No creative-specific coordinates: EquipPixelPanels anchors both slots to
    // the real offhand via MenuKit's VanillaSlotResolver, which resolves the
    // offhand's live position on the survival inventory and the creative tab. The
    // SLOT_X/ELYTRA_Y/TOTEM_Y above remain only as the slot's construction seed
    // (InventoryMenuEquipmentMixin); the per-frame position is the pixel panel's.

    /**
     * Player-attached equipment storage: 2 slots, survives logout and restart.
     * Declared at common init; each slot takes its own slice.
     */
    public static StorageAttachment<Player, NonNullList<ItemStack>> EQUIPMENT;

    /** Call from the main entrypoint at common init. */
    public static void register() {
        EQUIPMENT = StorageAttachment.playerAttached(MOD_ID, "equipment", TOTAL);
    }

    /**
     * The panel id an equipment slot is registered under. One source for the
     * creation mixin, the behavior declarations and the group declarations.
     */
    public static String panelId(String group) {
        return MOD_ID + ":" + group;
    }

    /**
     * Declares the elytra and totem groups with MenuKit at common init, each in its
     * own category and in no set, so a settings screen lists "Elytra slot" and
     * "Totem slot". Idempotent.
     */
    public static void declareGroups() {
        SlotGroups.declare(CreatedSlots.groupId(panelId(ELYTRA_GROUP), ELYTRA_GROUP), EQUIPMENT_ELYTRA);
        SlotGroups.declare(CreatedSlots.groupId(panelId(TOTEM_GROUP), TOTEM_GROUP), EQUIPMENT_TOTEM);
    }

    /**
     * Declares the equipment slots' server behavior by address, once at common
     * init (the creation mixin builds the slots without any): GATING (accept
     * filter and single-item cap), Curse of Binding (BINDING), and XP mending
     * (MENDING). Idempotent.
     */
    public static void declareSlotBehavior() {
        declare(ELYTRA_GROUP, EquipmentSlots::isElytra);
        declare(TOTEM_GROUP, EquipmentSlots::isTotem);
    }

    /** GATING (filter and single item), BINDING and MENDING on the group's one slot, by address. */
    private static void declare(String group, Predicate<ItemStack> accepts) {
        Address a = Address.createdSlot(CreatedSlots.groupId(panelId(group), group), 0);
        Window.slot(a)
                .gate(new SlotGate() {
                    @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) { return accepts.test(stack); }
                    @Override public boolean mayPickup(Player player, GatingContext ctx) { return true; }
                    @Override public int maxStackSize(ItemStack stack, int vanillaMax) { return Math.min(1, vanillaMax); }
                })
                .binding(true);
        Window.slot(a).set(ContainerKeys.MENDING, TriBool.TRUE);
    }

    // The passive-behavior mixins (glide, totem and so on) read the slot
    // contents straight off the player attachment, the same storage the menu
    // slot binds, so survival, creative and a closed menu all see the same data.

    /** The elytra in the equipment elytra slot, or EMPTY. */
    public static ItemStack getElytra(Player player) {
        return EQUIPMENT.bind(player).getStack(ELYTRA_INDEX);
    }

    /** The totem in the equipment totem slot, or EMPTY. */
    public static ItemStack getTotem(Player player) {
        return EQUIPMENT.bind(player).getStack(TOTEM_INDEX);
    }

    /**
     * Wears the equipment elytra down by {@code amount} and writes it back.
     * The storage reads return a detached stack (the attachment rebuilds a list
     * on each read), so a change only sticks once {@code setStack} writes it
     * back, which re-saves the attachment and syncs the new durability to the
     * client.
     * A {@code ServerLevel}-backed {@code entity} is required for the damage to
     * apply (vanilla gates {@code hurtAndBreak} on the server side).
     */
    public static void wearElytra(Player player, LivingEntity entity, int amount) {
        Storage storage = EQUIPMENT.bind(player);
        ItemStack elytra = storage.getStack(ELYTRA_INDEX);
        if (elytra.isEmpty()) return;
        elytra.hurtAndBreak(amount, entity, EquipmentSlot.CHEST);
        storage.setStack(ELYTRA_INDEX, elytra);
    }

    /**
     * Consumes one totem from the equipment totem slot and writes it back. Same
     * detached-copy caveat as {@link #wearElytra}: the read returns a copy, so the
     * shrink only sticks (and syncs to the client) once {@code setStack} writes it back.
     * Called when the slot's totem fires a death save.
     */
    public static void consumeTotem(Player player) {
        Storage storage = EQUIPMENT.bind(player);
        ItemStack totem = storage.getStack(TOTEM_INDEX);
        if (totem.isEmpty()) return;
        totem.shrink(1);
        storage.setStack(TOTEM_INDEX, totem);
    }

    // Per-slot accept filters. The elytra slot accepts only elytra and the
    // totem slot only totems of undying. These are literal item checks rather
    // than the broader GLIDER or DEATH_PROTECTION components, on purpose;
    // accepting any glider or any death-protection item would be a one-line
    // change.

    /** True for an elytra (the only item the elytra slot accepts). */
    public static boolean isElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA);
    }

    /** True for a totem of undying (the only item the totem slot accepts). */
    public static boolean isTotem(ItemStack stack) {
        return stack.is(Items.TOTEM_OF_UNDYING);
    }
}
