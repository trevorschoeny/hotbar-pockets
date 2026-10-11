package com.trevorschoeny.inventorymax.mending;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevlar.menukit.containers.api.slot.MendingCandidates;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Mend Anywhere: feeds the player's whole vanilla inventory into MenuKit:
 * Containers' XP-repair pool through the {@link MendingCandidates} hook, so a
 * damaged Mending item repairs while it sits in the inventory, not only when
 * equipped or in a registered slot.
 *
 * <p>Uses MKC's single {@code ExperienceOrb} intercept, so there is no second
 * mixin competing with it. MKC applies the candidate test itself (damaged and
 * has {@code REPAIR_WITH_XP}), so the slots are handed over unfiltered.
 *
 * <h3>Scope</h3>
 * Hotbar and main inventory (container slots 0 to 35), <b>except the selected
 * hotbar slot</b>. That is the main hand, which vanilla already mends, and
 * skipping it keeps it from appearing twice in the pool. Offhand and armor are
 * outside 0 to 35 and are also vanilla's already. The equipment slots and
 * pockets opt in separately at the slot level (MKC's mending behavior,
 * {@code SlotSpec.mending()} or {@code ContainerKeys.MENDING}, always on); this
 * is the vanilla-inventory part, which can be turned off.
 *
 * <h3>Server side</h3>
 * Gathered on the server during XP pickup. Gated by
 * {@link IMConfig#mendInventoryItems()}, which is safe to read here because IM
 * requires IP (which owns the config's enum), so IMConfig is present wherever
 * IM runs. {@link MendingCandidates.Candidate#stack()} returns the live
 * inventory stack, which MKC repairs in place;
 * {@link MendingCandidates.Candidate#onRepaired()} sends the new durability to
 * the client. Nothing extra is needed to save it, since it is in the saved
 * {@link Inventory}.
 */
public final class InventoryMendingProvider implements MendingCandidates.Provider {

    public static final InventoryMendingProvider INSTANCE = new InventoryMendingProvider();

    private InventoryMendingProvider() {}

    @Override
    public List<MendingCandidates.Candidate> candidatesFor(ServerPlayer player) {
        if (!IMConfig.mendInventoryItems()) return List.of();
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();
        List<MendingCandidates.Candidate> out = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;        // main hand, already in vanilla's pool
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            out.add(new MendingCandidates.Candidate() {
                @Override public ItemStack stack() { return stack; }
                @Override public void onRepaired() { player.inventoryMenu.broadcastChanges(); }
            });
        }
        return out;
    }
}
