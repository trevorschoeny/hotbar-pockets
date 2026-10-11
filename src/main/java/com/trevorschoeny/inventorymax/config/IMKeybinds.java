package com.trevorschoeny.inventorymax.config;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * IM's keybinds, registered as vanilla {@link KeyMapping}s so they show in the
 * Controls menu and can be rebound. They live in IM rather than IP's
 * {@code IPKeybinds} because IM depends on IP, not the reverse.
 *
 * <p>Pockets show on hover and are added or resized with the on-screen plus and
 * minus buttons, so the only keys are Cycle Forward and Cycle Backward. They
 * default to the Right and Left arrow keys, which don't clash with Column
 * Cycler's {@code ] [}.
 */
public final class IMKeybinds {

    private IMKeybinds() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("inventorymax", "controls"));

    public static final KeyMapping CYCLE_FORWARD = new KeyMapping(
            "key.inventorymax.pocket_cycle_forward",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);

    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping(
            "key.inventorymax.pocket_cycle_backward",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);

    public static void register() {
        KeyMappingHelper.registerKeyMapping(CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(CYCLE_BACKWARD);
    }
}
