package com.verityvisuals.client;

import com.verityvisuals.client.config.VisualSettings;
import com.verityvisuals.client.config.AutoSwapController;
import com.verityvisuals.client.gui.VerityVisualsScreen;
import com.verityvisuals.client.render.BlockOutlineRenderer;
import com.verityvisuals.client.render.ClientEffectsController;
import com.verityvisuals.client.render.ChinaHatRenderer;
import com.verityvisuals.client.render.ExtendedHudRenderer;
import com.verityvisuals.client.render.InventoryHudRenderer;
import com.verityvisuals.client.render.WaypointRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public class VerityVisualsClient implements ClientModInitializer {
	public static final String MOD_ID = "verityvisuals";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final @NonNull Identifier OPEN_MENU_KEY_CATEGORY =
			Identifier.fromNamespaceAndPath(MOD_ID, "main");
	private static KeyMapping openMenuKey;

	@Override
	public void onInitializeClient() {
		VisualSettings.load();
		KeyMapping.Category keyCategory = KeyMapping.Category.register(OPEN_MENU_KEY_CATEGORY);
		AutoSwapController.register(keyCategory);
		WaypointRenderer.register();
		BlockOutlineRenderer.register();
		ChinaHatRenderer.register();
		ClientEffectsController.register();
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.MISC_OVERLAYS,
				Identifier.fromNamespaceAndPath(MOD_ID, "inventory_hud"),
				(context, tickCounter) -> {
					ClientEffectsController.renderEffects(context);
					InventoryHudRenderer.render(context);
					ExtendedHudRenderer.render(context);
				}
		);
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.verityvisuals.open_menu",
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				keyCategory
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				if (client.screen instanceof VerityVisualsScreen screen) {
					screen.requestClose();
				} else {
					client.setScreen(new VerityVisualsScreen());
				}
			}
		});
	}
}
