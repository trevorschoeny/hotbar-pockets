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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import testgallery.SceneWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for the modes scene (test-gallery/scenes/hotbar-pockets/modes.json): where the pocket
 * column shows. Hovering hotbar slot 1 shows its two pockets on the survival inventory, on
 * creative's inventory tab and on a chest, and nothing on a creative item tab. "Shows" is read
 * off what the panels draw from: the pocket's frame origin this frame, and its slot being live.
 *
 * <p>In this package so it can ask {@link PocketGeometry#pocketOrigin} directly.
 */
public class ModeChecks implements FabricClientGameTest {

    /** The hotbar slot the scene has the player hover (slot 2 in the labels, index 1). */
    private static final int HOTBAR = 1;

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "hotbar-pockets", "modes")) {
            this.scene = scene;
            // Two pockets behind the hovered slot, as the scene's first station has the player add.
            setCount(2);
            run("hotbarpockets:survival", "survival", () -> {
                // Filled in the world, with no screen open, as a pickup or a restock does.
                setPocket(new ItemStack(Items.DIAMOND, 5));
                ctx.getInput().pressKey(o -> o.keyInventory);
                ctx.waitTicks(3);
                expectShown(true);
                expectContents();
                expectTurnInHandAtOnce();
            });
            run("hotbarpockets:survival", "survival", this::expectOverRecipeBook);
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
                expectShown(true);
                expectPlusAdds();
                expectShiftClickNeverFillsPocket();
            });
        } finally {
            setCount(0);
        }
        if (!failures.isEmpty()) throw new AssertionError("pocket mode checks failed:\n- " + String.join("\n- ", failures));
    }

    /** Steps HOTBAR's count to {@code want}. */
    private void setCount(int want) { setCount(HOTBAR, want); }

    /** Steps {@code hotbar}'s count to {@code want} with + and -, waiting for the server's answer each time. */
    private void setCount(int hotbar, int want) {
        for (int i = 0; i < Pockets.MAX_PER_SLOT; i++) {
            int c = ctx.computeOnClient(mc -> PocketState.count(hotbar));
            if (c == want) return;
            ctx.runOnClient(mc -> { if (c < want) PocketState.grow(hotbar); else PocketState.shrink(hotbar); });
            ctx.waitTicks(3);
        }
        int c = ctx.computeOnClient(mc -> PocketState.count(hotbar));
        if (c != want) throw new AssertionError("couldn't set slot " + hotbar + "'s pockets to " + want + ", the server says " + c);
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

    /** Clicks the shown column's + (the hover stays on), expects a third pocket, then clicks - to undo it. */
    private void expectPlusAdds() {
        double[][] at = ctx.computeOnClient(mc -> {
            SlotScreenRect r = PocketGeometry.hotbar(screen(mc), HOTBAR);
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            // The strip as PocketPixelPanels places it: minus at buttonsX, plus one button and a pixel right.
            double y = (PocketGeometry.buttonsTop(r) + 2 + PocketGeometry.BUTTON_SIZE / 2.0) * f;
            double minus = (PocketGeometry.buttonsX(r) + PocketGeometry.BUTTON_SIZE / 2.0) * f;
            return new double[][]{ { minus + (PocketGeometry.BUTTON_SIZE + 1) * f, y }, { minus, y } };
        });
        int before = ctx.computeOnClient(mc -> PocketState.count(HOTBAR));
        click(at[0]);
        int after = ctx.computeOnClient(mc -> PocketState.count(HOTBAR));
        if (after != before + 1) throw new AssertionError("+ took " + before + " pockets to " + after + " on the chest");
        click(at[1]);
        int undone = ctx.computeOnClient(mc -> PocketState.count(HOTBAR));
        if (undone != before) throw new AssertionError("- took " + after + " pockets to " + undone + " on the chest");
    }

    /**
     * Shift-clicks the chest's oak logs (menu slot 0) and confirms they never land in a pocket:
     * pockets never take a shift-click ({@link Pockets#register}).
     */
    private void expectShiftClickNeverFillsPocket() {
        shiftClick(0);
        String wrong = ctx.computeOnClient(mc -> {
            for (int d = 0; d < 2; d++) {
                Slot live = pocketSlot(mc, d);
                if (live != null && live.getItem().is(Items.OAK_LOG)) return "oak logs reached pocket " + d;
            }
            return null;
        });
        if (wrong != null) throw new AssertionError(wrong);
    }

    /** Shift-clicks menu slot {@code index} on the open screen with the real mouse. */
    private void shiftClick(int index) {
        double[] at = ctx.computeOnClient(mc -> {
            var screen = screen(mc);
            Slot s = screen.getMenu().slots.get(index);
            var edge = (com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor) screen;
            double f = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{ (edge.sk$getLeftPos() + s.x + 8) * f, (edge.sk$getTopPos() + s.y + 8) * f };
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            var mouse = (net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor) mc.mouseHandler;
            for (int action : new int[]{ 1, 0 }) mouse.invokeOnButton(mc.getWindow().handle(),
                    new net.minecraft.client.input.MouseButtonInfo(
                            com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 1), action);
        });
        ctx.waitTicks(5);
    }

    private void click(double[] at) {
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);   // SDL numbering in 26.x
        ctx.waitTicks(3);
    }

    /**
     * With the recipe book open, the pockets of hotbar slot 1 reach left over the book. They
     * draw over it and a click on one lands on the pocket, not the book (MenuKit 7.6.0).
     */
    private void expectOverRecipeBook() {
        final int hotbar = 0, count = 3;
        setCount(hotbar, count);
        try {
            ctx.getInput().pressKey(o -> o.keyInventory);
            ctx.waitTicks(3);
            // MK.setRecipeBookOpen shows the book but leaves the frame where it was; the
            // setting is remembered, so reopen the inventory and vanilla lays it out as the
            // recipe button does.
            ctx.runOnClient(mc -> com.trevlar.menukit.api.MK.setRecipeBookOpen(true));
            ctx.setScreen(() -> null);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(o -> o.keyInventory);
            ctx.waitTicks(3);
            double[] at = ctx.computeOnClient(mc -> {
                SlotScreenRect r = PocketGeometry.hotbar(screen(mc), hotbar);
                double f = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                return new double[]{ (r.x() + 8) * f, (r.y() + 8) * f, f, r.frameX() - 7 };
            });
            ctx.getInput().setCursorPos(at[0], at[1]);
            ctx.waitTicks(3);
            // The leftmost pocket that sits left of the inventory frame, over the book.
            int[] over = ctx.computeOnClient(mc -> {
                if (!com.trevlar.menukit.api.MK.isRecipeBookOpen()) throw new AssertionError("the recipe book didn't open");
                for (int d = 0; d < count; d++) {
                    var o = PocketGeometry.pocketOrigin(hotbar, d);
                    if (o != null && o.x() + 9 < at[3]) return new int[]{ d, o.x() + 9, o.y() + 9 };
                }
                return null;
            });
            if (over == null) throw new AssertionError("no pocket of hotbar slot 1 reaches over the recipe book; nothing to check");
            scene.world().getServer().runOnServer(server -> Pockets.POCKETS
                    .bind(server.getPlayerList().getPlayers().getFirst())
                    .setStack(Pockets.flatIndex(hotbar, over[0]), new ItemStack(Items.DIAMOND, 5)));
            ctx.waitTicks(5);
            // Walk the cursor across the gap to the pocket, as a player does, so the row stays.
            ctx.getInput().setCursorPos(over[1] * at[2], at[1] - 12 * at[2]);
            ctx.waitTicks(2);
            ctx.getInput().setCursorPos(over[1] * at[2], over[2] * at[2]);
            ctx.waitTicks(3);
            ctx.takeScreenshot("pockets-check-recipe_book");
            click(new double[]{ over[1] * at[2], over[2] * at[2] });
            ctx.waitTicks(5);
            String carried = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().toString());
            if (!carried.startsWith("5 minecraft:diamond"))
                throw new AssertionError("a click on pocket " + over[0] + " over the recipe book picked up " + carried + ", not its diamonds");
        } finally {
            ctx.runOnClient(mc -> com.trevlar.menukit.api.MK.setRecipeBookOpen(false));
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
            setCount(hotbar, 0);
            command("clear @p minecraft:diamond");
        }
    }

    /** Writes pocket (HOTBAR, 0) on the server, then gives the sync a few ticks. */
    private void setPocket(ItemStack stack) {
        scene.world().getServer().runOnServer(server -> Pockets.POCKETS
                .bind(server.getPlayerList().getPlayers().getFirst())
                .setStack(Pockets.flatIndex(HOTBAR, 0), stack));
        ctx.waitTicks(5);
    }

    /**
     * The diamonds {@link #setPocket} put in pocket (HOTBAR, 0) while the client's slot was
     * hidden reached the client: its attachment, which the HUD strip reads, and the pocket's
     * menu slot, which the panel draws. Empties the pocket afterwards.
     */
    private void expectContents() {
        String wrong = ctx.computeOnClient(mc -> {
            ItemStack attached = Pockets.POCKETS.bind(mc.player).getStack(Pockets.flatIndex(HOTBAR, 0));
            Slot slot = pocketSlot(mc, 0);
            ItemStack shown = slot == null ? ItemStack.EMPTY : slot.getItem();
            if (attached.isEmpty() || shown.isEmpty())
                return "pocket 0 holds diamonds on the server; the client has " + attached
                        + " in its attachment and " + shown + " in the slot";
            return null;
        });
        ctx.takeScreenshot("pockets-check-contents");
        setPocket(ItemStack.EMPTY);
        if (wrong != null) throw new AssertionError(wrong);
    }

    /**
     * A turn puts the new item in the client's hand the moment the key is pressed, before
     * the server answers, and the server ends up with the same.
     */
    private void expectTurnInHandAtOnce() {
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);
        setPocket(new ItemStack(Items.GOLDEN_APPLE, 2));
        String wrong = ctx.computeOnClient(mc -> {
            mc.player.getInventory().setSelectedSlot(HOTBAR);
            PocketInput.cycle(HOTBAR, true);
            // Same client task: no packet from the server can have arrived yet.
            ItemStack held = mc.player.getInventory().getItem(HOTBAR);
            return held.is(Items.GOLDEN_APPLE) ? null : "right after the key the client holds " + held + ", not the golden apples";
        });
        if (wrong != null) throw new AssertionError(wrong);
        ctx.waitTicks(5);
        boolean serverAgrees = scene.world().getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getInventory().getItem(HOTBAR).is(Items.GOLDEN_APPLE));
        if (!serverAgrees) throw new AssertionError("the server didn't turn the golden apples into the hand");
        // Back as it was: the apples into their pocket, then out of it.
        ctx.runOnClient(mc -> PocketInput.cycle(HOTBAR, false));
        ctx.waitTicks(5);
        setPocket(ItemStack.EMPTY);
        ctx.getInput().pressKey(o -> o.keyInventory);
        ctx.waitTicks(3);
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
        command("tp @p " + (x + 0.5) + " " + y + " " + (z - 1.5));   // within reach; the player stands 8 back
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
