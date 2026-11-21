package com.verityvisuals.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public final class AutoSwapController {
	private static KeyMapping activationKey;

	private AutoSwapController() {
	}

	public static void register(KeyMapping.@NonNull Category category) {
		activationKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.verityvisuals.auto_swap",
				VisualSettings.autoSwapKeyCode,
				category
		));
		ClientTickEvents.END_CLIENT_TICK.register(AutoSwapController::tick);
	}

	public static void setActivationKeyCode(int keyCode) {
		if (activationKey == null || keyCode < GLFW.GLFW_KEY_SPACE || keyCode > GLFW.GLFW_KEY_LAST) {
			return;
		}
		activationKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
		VisualSettings.autoSwapKeyCode = keyCode;
	}

	public static Component activationKeyName() {
		return activationKey == null
				? Component.empty()
				: activationKey.getTranslatedKeyMessage();
	}

	public static boolean hasHotbarKeyConflict() {
		if (activationKey == null) {
			return false;
		}
		KeyMapping activeKey = Objects.requireNonNull(activationKey);
		for (KeyMapping hotbarKey : Minecraft.getInstance().options.keyHotbarSlots) {
			if (activeKey.same(Objects.requireNonNull(hotbarKey))) {
				return true;
			}
		}
		return false;
	}

	private static void tick(Minecraft minecraft) {
		boolean activated = activationKey != null && activationKey.consumeClick();
		LocalPlayer player = minecraft.player;
		if (!activated || !VisualSettings.autoSwapEnabled || player == null || minecraft.screen != null) {
			return;
		}
		if (hasHotbarKeyConflict()) {
			return;
		}

		@NonNull String itemIdText = Objects.requireNonNull(VisualSettings.autoSwapItemId.strip());
		Identifier itemId = Identifier.tryParse(itemIdText);
		if (itemId == null || !itemIdText.equals(itemId.toString())
				|| !BuiltInRegistries.ITEM.containsKey(itemId)) {
			player.displayClientMessage(
					Component.translatable("verityvisuals.autoswap.invalid_item"), true);
			return;
		}

		int selectedSlot = player.getInventory().getSelectedSlot();
		ItemStack selectedStack = player.getInventory().getItem(selectedSlot);
		if (!selectedStack.isEmpty()
				&& itemId.equals(BuiltInRegistries.ITEM.getKey(selectedStack.getItem()))) {
			return;
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				if (slot != selectedSlot) {
					player.getInventory().setSelectedSlot(slot);
				}
				return;
			}
		}
		player.displayClientMessage(
				Component.translatable("verityvisuals.autoswap.not_in_hotbar"), true);
	}
}
