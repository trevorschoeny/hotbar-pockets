package com.trevorschoeny.inventorymax.equipment;

import com.trevorschoeny.inventorymax.config.IMConfig;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.containers.api.slot.SlotElement;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.slot.VanillaSlotResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * How the equipment slots are shown: one pixel-positioned panel per slot
 * (elytra, totem), each holding its {@link SlotElement} and empty-slot
 * placeholder icon, placed with MenuKit's pixel position override.
 *
 * <h3>Why two panels, not one</h3>
 *
 * The two screens need different arrangements, so each slot anchors to its own
 * vanilla neighbor and the pair's shape follows from that:
 * <ul>
 *   <li><b>Survival inventory:</b> a vertical stack directly above the offhand,
 *       elytra two slot pitches up and totem one (totem right on top of the
 *       offhand).</li>
 *   <li><b>Creative inventory tab:</b> vanilla shows no offhand there (its
 *       wrapper sits uselessly under the tab strip), so the pair sits beside the
 *       leggings and boots column instead: one column to the right with a 1px
 *       gap (like the offhand's own 1px gap), the pair centered on the column's
 *       vertical midpoint. Anchoring to the real leggings and boots wrappers,
 *       not hardcoded pixels, keeps the pair in place if creative's layout
 *       ever moves.</li>
 * </ul>
 *
 * A {@code null} origin (the anchor slot is not on this screen, as in chests or
 * creative's other tabs) skips the panel entirely. {@link VanillaSlotResolver}
 * returns absolute positions and unwraps creative's {@code SlotWrapper}, so
 * each branch is one expression, with no per-screen mixins.
 */
public final class EquipPixelPanels {

    private EquipPixelPanels() {}

    /** Elytra sits 2 slot-pitches above the offhand on survival; totem 1. */
    private static final int ELYTRA_ABOVE_OFFHAND = 2;
    private static final int TOTEM_ABOVE_OFFHAND = 1;

    /** Vanilla player-inventory container indices for the creative anchors. */
    private static final int BOOTS_INDEX = 36;
    private static final int LEGGINGS_INDEX = 37;

    /** Registers both panels. Call once at IM client init. */
    public static void register() {
        present(EquipmentSlots.ELYTRA_GROUP, ELYTRA_ABOVE_OFFHAND, /*pairIndex*/ 0);
        present(EquipmentSlots.TOTEM_GROUP, TOTEM_ABOVE_OFFHAND, /*pairIndex*/ 1);
    }

    private static void present(String group, int slotsAboveOffhand, int pairIndex) {
        String dataPanel = EquipmentSlots.MOD_ID + ":" + group;
        Panel panel = Panel.builder(dataPanel + ":present")
                .elements(List.of(
                        // Slot first, icon after, so the icon draws over an
                        // empty slot's frame.
                        SlotElement.builder().slot(CreatedSlots.groupId(dataPanel, group), 0).build(),
                        new EmptyIcon(group)))
                .visible(true)
                // The SlotElement draws its own frame; no backing of ours.
                .style(PanelStyle.NONE)
                .position(PanelPosition.pixel(
                        () -> anchor(slotsAboveOffhand, pairIndex)))
                .build()
                // The slot is a click-through hole vanilla routes into; the
                // panel must not eat input around it.
                .opaque(false);
        new ScreenPanelAdapter(panel, 0).onPlayerInventory();
    }

    // Per-frame outer origin for one equipment slot. On the creative inventory
    // tab: one column right of the leggings and boots with a 1px gap (like the
    // offhand slot's 1px gap), the 2-slot pair centered on the column's
    // vertical midpoint, pairIndex 0 on top (elytra) and 1 below (totem).
    // Everywhere else: the stack above the offhand. Null (skip) when no
    // container screen is open or the anchor slots aren't on it.
    private static ScreenOrigin anchor(int slotsAboveOffhand, int pairIndex) {
        // pairIndex 0 is the elytra slot, 1 the totem; each hides on its own switch.
        if (!(pairIndex == 0 ? IMConfig.elytraSlotOn() : IMConfig.totemSlotOn())) return null;
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return null;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            var leggings = VanillaSlotResolver.resolve(screen, LEGGINGS_INDEX).orElse(null);
            var boots = VanillaSlotResolver.resolve(screen, BOOTS_INDEX).orElse(null);
            if (leggings == null || boots == null) return null;
            // 1px gap to the column, like the offhand's. The pair's top is the
            // column midpoint minus one pitch, so the 36px pair straddles the
            // middle of the leggings to boots span, which may have a gap.
            int x = leggings.frameX() + CreatedSlots.SLOT_PITCH + 1;
            int columnTop = leggings.frameY();
            int columnBottom = boots.frameY() + CreatedSlots.SLOT_PITCH;
            int pairTop = (columnTop + columnBottom) / 2 - CreatedSlots.SLOT_PITCH;
            return new ScreenOrigin(x, pairTop + pairIndex * CreatedSlots.SLOT_PITCH);
        }
        return VanillaSlotResolver.resolve(screen, Inventory.SLOT_OFFHAND)
                .map(offhand -> new ScreenOrigin(offhand.frameX(),
                        offhand.frameY() - slotsAboveOffhand * CreatedSlots.SLOT_PITCH))
                .orElse(null);
    }

    /**
     * Faint placeholder over one equipment slot while it is empty. The
     * {@link SlotElement} draws the frame and item. Render-only, never consumes
     * clicks.
     */
    private static final class EmptyIcon implements PanelElement {

        private final String group;

        EmptyIcon(String group) {
            this.group = group;
        }

        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth()  { return SlotRendering.DEFAULT_SIZE; }
        @Override public int getHeight() { return SlotRendering.DEFAULT_SIZE; }

        @Override
        public void render(RenderContext ctx) {
            CreatedSlot mk = find(group);
            if (mk == null || !mk.getItem().isEmpty()) return;
            Identifier sprite = EquipSlotIcons.spriteFor(group);
            if (sprite == null) return;
            EquipSlotIcons.draw(ctx.graphics(), sprite,
                    ctx.originX() + SlotRendering.ITEM_INSET,
                    ctx.originY() + SlotRendering.ITEM_INSET);
        }

        // The equipment slot for group in this screen's live menu: direct on
        // the survival inventory, unwrapped from the creative SlotWrapper on
        // the creative inventory tab. Null when absent (the panel is skipped
        // there anyway, so this is only a safety check).
        private static CreatedSlot find(String group) {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs)) {
                return null;
            }
            for (Slot s : acs.getMenu().slots) {
                CreatedSlot mk = CreatedSlot.of(s);
                if (mk != null && group.equals(mk.groupId())) return mk;
            }
            return null;
        }
    }
}
