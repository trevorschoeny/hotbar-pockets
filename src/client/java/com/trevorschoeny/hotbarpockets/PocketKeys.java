package com.trevorschoeny.hotbarpockets;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * The two cycle keys, as vanilla {@link KeyMapping}s so they show in Controls and can
 * be rebound. Right and Left arrow by default, clear of Inventory Plus's Column Cycler
 * keys ({@code ] [}). Pockets show on hover and are resized with the on-screen + and -,
 * so there are no other keys.
 */
public final class PocketKeys {

    private PocketKeys() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(HotbarPockets.MOD_ID, "controls"));

    public static final KeyMapping CYCLE_FORWARD = new KeyMapping(
            "key.hotbarpockets.cycle_forward",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT, CATEGORY);

    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping(
            "key.hotbarpockets.cycle_backward",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LEFT, CATEGORY);

    public static void register() {
        KeyMappingHelper.registerKeyMapping(CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(CYCLE_BACKWARD);
    }
}
