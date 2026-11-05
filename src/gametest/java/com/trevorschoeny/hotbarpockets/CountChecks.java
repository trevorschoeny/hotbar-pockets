package com.trevorschoeny.hotbarpockets;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Pocket counts on a dedicated server, over real connections: the server owns them
 * ({@link PocketCounts}). A player updating from 2.1.0 keeps the layout their client
 * saved, and no item is left in a pocket past the count. Counts survive leaving and
 * rejoining, and death. A pocket past the count is refused by the server itself, and a
 * shift-click never lands in a pocket. Shrinking in creative with
 * a full inventory drops the removed pocket's item instead of deleting it.
 */
public class CountChecks implements FabricClientGameTest {

    private static final Path LEGACY = FabricLoader.getInstance().getConfigDir()
            .resolve(HotbarPockets.MOD_ID).resolve("pockets.json");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
            String world;
            // First visit: no counts anywhere yet. An item lands in pocket (2, 2), past
            // a count of 0, as one left behind by an old client would be.
            try (TestDedicatedServerConnection conn = server.connect()) {
                ctx.waitTicks(5);
                world = ctx.computeOnClient(mc -> "server:" + mc.getCurrentServer().ip);
                server.runOnServer(s -> Pockets.POCKETS.bind(player(s))
                        .setStack(Pockets.flatIndex(2, 2), new ItemStack(Items.DIAMOND, 3)));
            }

            // The update: this client's old file says 1 pocket behind slot 0 and 2 behind slot 1.
            writeLegacy(world, "[1, 2, 0, 0, 0, 0, 0, 0, 0]");
            try (TestDedicatedServerConnection conn = server.connect()) {
                ctx.waitTicks(10);
                expectCounts(ctx, server, "after migrating", 1, 2, 3, 0, 0, 0, 0, 0, 0);
                server.runOnServer(s -> check(Pockets.POCKETS.bind(player(s)).getStack(Pockets.flatIndex(2, 2)).is(Items.DIAMOND),
                        "the diamonds in pocket (2, 2) are gone after migrating"));
                serverRefusesPastCount(ctx, server);
                creativeShrinkKeepsItem(ctx, server);

                // A change the server makes now is the player's, not the file's.
                ctx.runOnClient(mc -> PocketState.grow(4));
                ctx.waitTicks(5);
                ctx.runOnClient(mc -> PocketState.grow(4));
                ctx.waitTicks(5);
                expectCounts(ctx, server, "after growing slot 4 twice", 1, 2, 3, 0, 2, 0, 0, 0, 0);

                server.runCommand("kill @a");
                ctx.waitFor(mc -> mc.gui.screen() instanceof DeathScreen);
                ctx.runOnClient(mc -> mc.player.respawn());
                ctx.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null);
                ctx.waitTicks(10);
                // Death dropped the diamonds, so slot 2 is back to its saved count.
                expectCounts(ctx, server, "after death", 1, 2, 0, 0, 2, 0, 0, 0, 0);
            }

            // Rejoining: the server's counts hold, and the file (still 1, 2, 0...) is ignored.
            try (TestDedicatedServerConnection conn = server.connect()) {
                ctx.waitTicks(10);
                expectCounts(ctx, server, "after rejoining", 1, 2, 0, 0, 2, 0, 0, 0, 0);
            }
        } finally {
            try {
                Files.deleteIfExists(LEGACY);
            } catch (IOException e) {
                throw new AssertionError("couldn't remove the test's legacy file " + LEGACY, e);
            }
        }
    }

    /**
     * Slot 3 has no pockets. Its first pocket is inert on the server, and refuses a stack.
     * Slot 0's first pocket, within its count, takes one. A server-side shift-click from the
     * inventory lands in no pocket at all, with or without Inventory Plus.
     */
    private static void serverRefusesPastCount(ClientGameTestContext ctx, TestDedicatedServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Slot hidden = p.inventoryMenu.getSlot(PocketRing.pocketMenuIndex(p.inventoryMenu, 3, 0));
            Slot live = p.inventoryMenu.getSlot(PocketRing.pocketMenuIndex(p.inventoryMenu, 0, 0));
            ItemStack dirt = new ItemStack(Items.DIRT);
            check(!hidden.mayPlace(dirt) && CreatedSlot.of(hidden).isInert(), "pocket (3, 0), past slot 3's count of 0, is live on the server");
            check(live.mayPlace(dirt), "pocket (0, 0), within slot 0's count of 1, refuses dirt on the server");

            int from = 20;   // main inventory slot 20 is menu slot 20 on the inventory
            p.getInventory().setItem(from, new ItemStack(Items.DIRT, 64 * 3));
            p.inventoryMenu.quickMoveStack(p, from);
            var stacks = Pockets.POCKETS.bind(p);
            for (int i = 0; i < Pockets.TOTAL; i++) {
                check(!stacks.getStack(i).is(Items.DIRT), "a shift-click put dirt in pocket " + i);
            }
            for (int i = 0; i < Pockets.TOTAL; i++) if (stacks.getStack(i).is(Items.DIRT)) stacks.setStack(i, ItemStack.EMPTY);
            p.getInventory().setItem(from, ItemStack.EMPTY);
        });
    }

    /**
     * In creative with a full inventory, shrinking slot 0 from 1 pocket to 0 drops the
     * pocket's diamonds at the player's feet. Vanilla's Inventory.add would delete them.
     * Leaves slot 0 at 1 pocket and the player in survival, as it found them.
     */
    private static void creativeShrinkKeepsItem(ClientGameTestContext ctx, TestDedicatedServerContext server) {
        server.runCommand("gamemode creative @a");
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            var slots = p.getInventory().getNonEquipmentItems();
            for (int i = 0; i < slots.size(); i++) slots.set(i, new ItemStack(Items.STONE, 64));
            Pockets.POCKETS.bind(p).setStack(Pockets.flatIndex(0, 0), new ItemStack(Items.DIAMOND, 5));
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> PocketState.shrink(0));
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(Pockets.count(p, 0) == 0, "slot 0 still has a pocket after shrinking in creative");
            int dropped = p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(4),
                    e -> e.getItem().is(Items.DIAMOND)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(dropped == 5, "shrinking in creative with a full inventory kept " + dropped + " of 5 diamonds");
            p.getInventory().clearContent();
        });
        server.runCommand("kill @e[type=item]");
        server.runCommand("gamemode survival @a");
        ctx.runOnClient(mc -> PocketState.grow(0));
        ctx.waitTicks(5);
    }

    /** The nine counts on the server and, as SlotKit synced them, on the client. */
    private static void expectCounts(ClientGameTestContext ctx, TestDedicatedServerContext server, String when, int... want) {
        int[] onServer = server.computeOnServer(s -> counts(player(s)));
        int[] onClient = ctx.computeOnClient(mc -> counts(mc.player));
        check(Arrays.equals(onServer, want), when + ": the server's counts are " + Arrays.toString(onServer) + ", expected " + Arrays.toString(want));
        check(Arrays.equals(onClient, want), when + ": the client's counts are " + Arrays.toString(onClient) + ", expected " + Arrays.toString(want));
    }

    private static int[] counts(net.minecraft.world.entity.player.Player p) {
        int[] c = new int[Pockets.HOTBAR_SLOTS];
        for (int n = 0; n < c.length; n++) c[n] = Pockets.count(p, n);
        return c;
    }

    private static void writeLegacy(String world, String counts) {
        try {
            Files.createDirectories(LEGACY.getParent());
            Files.writeString(LEGACY, "{\"version\": 1, \"perWorld\": {\"" + world + "\": {\"counts\": " + counts + "}}}");
        } catch (IOException e) {
            throw new AssertionError("couldn't write the legacy file " + LEGACY, e);
        }
        PocketState.loadLegacy();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
