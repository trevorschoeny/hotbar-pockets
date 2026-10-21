package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import testgallery.SceneWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for the modes scene (test-gallery/scenes/hotbar-pockets/modes.json): where the pocket
 * column shows. Hovering hotbar slot 1 shows its two pockets on the survival inventory and on
 * creative's inventory tab, and nothing on a creative item tab or a chest. "Shows" is read off
 * what the panels draw from: the pocket's frame origin this frame, and its slot being live.
 *
 * <p>In this package so it can ask {@link PocketGeometry#pocketOrigin} directly.
 */
public class ModeChecks implements FabricClientGameTest {

    /** The hotbar slot the scene has Trev hover (slot 2 in the labels, index 1). */
    private static final int HOTBAR = 1;

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "hotbar-pockets", "modes")) {
            this.scene = scene;
            // Two pockets behind the hovered slot, as the scene's first station has Trev add.
            ctx.runOnClient(mc -> { while (PocketState.count(HOTBAR) < 2) PocketState.grow(HOTBAR); });
            run("hotbarpockets:survival", "survival", () -> {
                ctx.getInput().pressKey(o -> o.keyInventory);
                ctx.waitTicks(3);
                expectShown(true);
            });
            run("hotbarpockets:creative_inventory", "creative", () -> {
                openCreativeTab(CreativeModeTab.Type.INVENTORY);
                expectShown(true);
            });
            run("hotbarpockets:creative_item_tab", "creative", () -> {
                openCreativeTab(CreativeModeTab.Type.CATEGORY);
                expectShown(false);
                String onMenu = ctx.computeOnClient(mc -> {
                    for (Slot s : screen(mc).getMenu().slots) {
                        CreatedSlot c = CreatedSlot.of(s);
                        if (c != null && Pockets.isPocket(c)) return "a pocket slot is on the item tab's menu";
                    }
                    return null;
                });
                if (onMenu != null) throw new AssertionError(onMenu);
            });
            run("hotbarpockets:chest", "survival", () -> {
                // The station's chest sits at (2, 0, 2) from its origin.
                var o = scene.station("hotbarpockets:chest").getAsJsonArray("origin");
                openChest(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
                expectShown(false);
            });
        } finally {
            ctx.runOnClient(mc -> { while (PocketState.count(HOTBAR) > 0) PocketState.shrink(HOTBAR); });
        }
        if (!failures.isEmpty()) throw new AssertionError("pocket mode checks failed:\n- " + String.join("\n- ", failures));
    }

    /** Runs one station's check in {@code mode}, recording a failure instead of stopping. */
    private void run(String id, String mode, Runnable check) {
        try {
            scene.standAt(scene.station(id), 30);
            command("gamemode " + mode + " @p");
            check.run();
        } catch (AssertionError e) {
            failures.add(id + ": " + e.getMessage());
        } finally {
            ctx.takeScreenshot("pockets-check-" + id.substring(id.indexOf(':') + 1));
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    /** Hovers hotbar slot {@link #HOTBAR} on the open screen, then reads whether its pockets show. */
    private void expectShown(boolean shown) {
        double[] at = ctx.computeOnClient(mc -> {
            SlotScreenRect r = PocketGeometry.hotbar(screen(mc), HOTBAR);
            if (r == null) throw new AssertionError("no hotbar slot " + HOTBAR + " on " + mc.gui.screen());
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[]{ (r.x() + 8) * f, (r.y() + 8) * f };
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(3);   // the hover tick runs before each frame's draw
        String wrong = ctx.computeOnClient(mc -> {
            for (int d = 0; d < 2; d++) {
                boolean drawn = PocketGeometry.pocketOrigin(HOTBAR, d) != null;
                Slot live = pocketSlot(mc, d);
                boolean active = live != null && live.isActive();
                if (drawn != shown) return "pocket " + d + (shown ? " not drawn" : " drawn") + " on " + mc.gui.screen();
                if (active != shown) return "pocket " + d + (shown ? " slot not live" : " slot live") + " on " + mc.gui.screen();
            }
            return null;
        });
        if (wrong != null) throw new AssertionError(wrong);
    }

    /** Opens creative's inventory and switches to the first tab of {@code type}. */
    private void openCreativeTab(CreativeModeTab.Type type) {
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> {
            if (!(mc.gui.screen() instanceof FabricCreativeModeInventoryScreen screen))
                throw new AssertionError("the inventory key opened " + mc.gui.screen() + " in creative");
            screen.setSelectedTab(CreativeModeTabs.allTabs().stream()
                    .filter(t -> t.getType() == type).findFirst().orElseThrow());
        });
        ctx.waitTicks(3);
    }

    /** Steps up to the chest at (x, y, z) and opens it, as a right-click on its north face does. */
    private void openChest(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        command("tp @p " + (x + 0.5) + " " + y + " " + (z - 1.5));   // within reach; Trev stands 8 back
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
        ctx.waitTicks(5);
        boolean open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
        if (!open) throw new AssertionError("the chest didn't open");
    }

    /** Pocket (HOTBAR, depth)'s slot on the open menu, or null when it isn't there. */
    private static Slot pocketSlot(Minecraft mc, int depth) {
        for (Slot s : screen(mc).getMenu().slots) {
            CreatedSlot c = CreatedSlot.of(s);
            if (c != null && Pockets.isPocket(c, HOTBAR, depth)) return s;
        }
        return null;
    }

    private static AbstractContainerScreen<?> screen(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) throw new AssertionError("no container screen open");
        return s;
    }

    private void command(String c) {
        scene.world().getServer().runCommand(c);
        ctx.waitTicks(2);
    }
}
