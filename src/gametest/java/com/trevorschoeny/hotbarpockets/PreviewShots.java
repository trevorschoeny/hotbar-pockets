package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.group.SlotScreenRect;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import testgallery.SceneWorld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Not a check: frame sequences of pocket previews for Trev to watch, the HUD arcs turning
 * and the inventory's hover slide. Runs only when {@code /tmp/hotbarpockets-shots} exists,
 * so ordinary check runs skip it.
 *
 * <p>See {@link #capture} for how the frames reach 60 fps. Each frame's time goes into
 * {@code <name>.times} beside the screenshots, for ffmpeg to put them in order. Each frame's game time goes into
 */
public class PreviewShots implements FabricClientGameTest {

    private static final Path FLAG = Path.of("/tmp/hotbarpockets-shots");
    /** How much slower the game clock runs while frames are taken. */
    private static final long SLOW = 10;
    /** Phase-shifted runs per animation: 3 gives a frame every 16.7 ms. */
    private static final int PASSES = 3;
    /** Frames per run, 50 ms apart: the 450 ms slide (PocketStrip.STYLE) and a little rest after. */
    private static final int FRAMES_PER_PASS = 10;

    private ClientGameTestContext ctx;
    private SceneWorld.Opened scene;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!Files.exists(FLAG)) return;
        this.ctx = ctx;
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "hotbar-pockets", "modes")) {
            this.scene = scene;
            scene.standAt(scene.station("hotbarpockets:survival"), 30);
            fill();
            command("gamemode survival @p");
            // No chat over the hotbar in the frames.
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
            ctx.waitTicks(5);

            // The HUD: arcs over slots 1 to 4, then a forward turn on the held slot 2.
            ctx.takeScreenshot("previews-hud-rest");
            // Slot 1's three pockets turning forward twice, then back twice: one capture per
            // turn, each then made for real so the next starts where it ended. A turn's slide
            // starts inside the turn, so the shifted clock wraps the turn itself.
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            ctx.waitTicks(5);
            boolean[] turns = { true, true, false, false };
            for (int i = 0; i < turns.length; i++) {
                boolean forward = turns[i];
                capture("previews-hud-turns-" + i, () -> {}, () -> PocketInput.cycle(0, forward), () -> {
                    ctx.waitTicks(10);
                    ctx.runOnClient(mc -> PocketInput.cycle(0, !forward));
                    ctx.waitTicks(10);
                });
                ctx.runOnClient(mc -> PocketInput.cycle(0, forward));
                ctx.waitTicks(10);
            }
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
            ctx.waitTicks(5);

            // The inventory: every slot's arc, then hovering slot 1 opens its row and leaving closes it.
            ctx.getInput().pressKey(o -> o.keyInventory);
            ctx.waitTicks(3);
            ctx.getInput().setCursorPos(0, 0);
            ctx.waitTicks(3);
            ctx.takeScreenshot("previews-inventory-rest");
            double[] slot1 = slotCenter(0);
            Runnable hover = () -> { ctx.getInput().setCursorPos(slot1[0], slot1[1]); ctx.waitTick(); };
            Runnable leave = () -> { ctx.getInput().setCursorPos(0, 0); ctx.waitTick(); };
            Runnable settle = () -> ctx.waitTicks(10);
            capture("previews-inventory-open", hover, PocketArcs::restampSlide, () -> { leave.run(); settle.run(); });
            hover.run();
            settle.run();
            capture("previews-inventory-close", leave, PocketArcs::restampSlide, () -> { hover.run(); settle.run(); });
        }
    }

    /** Hotbar items and pockets: three full pockets, two, one, and two with one empty. */
    private void fill() {
        int[] counts = { 3, 2, 1, 2 };
        for (int n = 0; n < counts.length; n++) {
            for (int c = 0; c < counts[n]; c++) {
                int hotbar = n;
                ctx.runOnClient(mc -> PocketState.grow(hotbar));
                ctx.waitTicks(3);
            }
        }
        scene.world().getServer().runOnServer(server -> {
            var p = server.getPlayerList().getPlayers().getFirst();
            var inv = p.getInventory();
            ItemStack[] hotbar = { new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_PICKAXE),
                    new ItemStack(Items.TORCH, 32), new ItemStack(Items.BREAD, 12) };
            for (int i = 0; i < hotbar.length; i++) inv.setItem(i, hotbar[i]);
            var pockets = Pockets.POCKETS.bind(p);
            ItemStack[][] in = {
                    { new ItemStack(Items.BOW), new ItemStack(Items.SHIELD), new ItemStack(Items.CROSSBOW) },
                    { new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.DIAMOND_AXE) },
                    { new ItemStack(Items.LANTERN, 8) },
                    { new ItemStack(Items.COOKED_BEEF, 16), ItemStack.EMPTY } };
            for (int n = 0; n < in.length; n++)
                for (int d = 0; d < in[n].length; d++) pockets.setStack(Pockets.flatIndex(n, d), in[n][d]);
        });
        ctx.waitTicks(5);
    }

    /**
     * Frames of one animation at 60 fps. Fabric's client gametest renders one frame per
     * tick, a tick comes every 50 ms of game time, and saving a screenshot takes about
     * 300 ms of real time. So during a pass the game clock runs {@link #SLOW} times slow
     * (a screenshot then costs less than a tick, and frames land exactly 50 ms apart), and
     * the animation runs {@link #PASSES} times, each started a further 50 / PASSES ms
     * early, so the passes' frames interleave. {@code start} starts the animation;
     * {@code restamp} restarts its clock, run with the clock shifted back by the pass's
     * offset; {@code undo} puts things back between passes, at normal speed.
     */
    private void capture(String name, Runnable start, Runnable restamp, Runnable undo) {
        List<String> times = new ArrayList<>();
        Path dir = null;
        for (int pass = 0; pass < PASSES; pass++) {
            long shiftNanos = pass * 50_000_000L / PASSES;
            long gameBase = Util.getNanos(), realBase = System.nanoTime();
            java.util.function.LongSupplier slow = () -> gameBase + (System.nanoTime() - realBase) / SLOW;
            ctx.runOnClient(mc -> Util.setTimeSource(slow::getAsLong));
            try {
                start.run();
                // The shift reaches only the restamp: the clock is put back in the same
                // client task, so the game's ticks never see it.
                ctx.runOnClient(mc -> {
                    Util.setTimeSource(() -> slow.getAsLong() - shiftNanos);
                    restamp.run();
                    Util.setTimeSource(slow::getAsLong);
                });
                for (int k = 0; k < FRAMES_PER_PASS; k++) {
                    Path shot = ctx.takeScreenshot(String.format("%s-p%d-%02d", name, pass, k));
                    dir = shot.getParent();
                    times.add(shot.getFileName() + " " + (k * 50 + shiftNanos / 1_000_000L));
                }
            } finally {
                ctx.runOnClient(mc -> Util.setTimeSource(System::nanoTime));
            }
            undo.run();
        }
        try {
            if (dir != null) Files.write(dir.resolve(name + ".times"), times);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Hotbar slot {@code n}'s item center on the open screen, in window pixels. */
    private double[] slotCenter(int n) {
        return ctx.computeOnClient(mc -> {
            SlotScreenRect r = PocketGeometry.hotbar((AbstractContainerScreen<?>) mc.gui.screen(), n);
            double f = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{ (r.x() + 8) * f, (r.y() + 8) * f };
        });
    }

    private void command(String c) {
        scene.world().getServer().runCommand(c);
        ctx.waitTicks(2);
    }
}
