package com.verityvisuals.client.render;

import com.verityvisuals.client.config.VisualSettings;
import com.verityvisuals.client.config.HudElementConfig;
import com.verityvisuals.client.config.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class InventoryHudRenderer {
	private static final int PANEL = 0xB0101115;
	private static final int MUTED = 0xFFB4B7BD;
	private static final @NonNull EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private InventoryHudRenderer() {
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		HudElementConfig config = VisualSettings.hudElement(HudModule.INVENTORY);
		if (!VisualSettings.inventoryHudEnabled || !config.enabled || player == null || minecraft.screen != null) {
			return;
		}

		float scale = getEffectiveScale(minecraft, player, config.scale);
		int contentHeight = getContentHeight(player);
		int guiWidth = minecraft.getWindow().getGuiScaledWidth();
		int guiHeight = minecraft.getWindow().getGuiScaledHeight();
		int drawWidth = Math.round(190.0F * scale);
		int drawHeight = Math.round(contentHeight * scale);
		int x = config.x < 0 ? 8 : Math.max(0, Math.min(config.x, guiWidth - drawWidth));
		int y = Math.max(0, Math.min(config.y, guiHeight - drawHeight));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		x = 0;
		y = 0;
		int width = 190;
		if (VisualSettings.showVitals) {
			panel(graphics, x, y, width, 22);
			@NonNull Component health = Component.translatable("verityvisuals.hud.health",
					Math.round(player.getHealth()), Math.round(player.getMaxHealth()));
			@NonNull Component food = Component.translatable("verityvisuals.hud.food",
					player.getFoodData().getFoodLevel());
			graphics.drawString(minecraft.font, fitText(minecraft, health, 98),
					x + 7, y + 7, config.color);
			graphics.drawString(minecraft.font, fitText(minecraft, food, 72),
					x + 111, y + 7, config.color);
			y += 27;
		}

		if (VisualSettings.showArmor) {
			panel(graphics, x, y, width, 42);
			graphics.drawString(minecraft.font, Component.translatable("verityvisuals.hud.armor"),
					x + 7, y + 4, MUTED);
			for (int i = 0; i < ARMOR_SLOTS.length; i++) {
				ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
				int itemX = x + 8 + i * 43;
				graphics.renderItem(stack, itemX, y + 17);
				if (stack.isDamageableItem() && !stack.isEmpty()) {
					int percent = durabilityPercent(stack);
					graphics.drawString(minecraft.font, percent + "%", itemX + 18, y + 20,
							durabilityColor(percent));
				}
			}
			y += 47;
		}

		if (VisualSettings.showInventory) {
			panel(graphics, x, y, width, 42);
			graphics.drawString(minecraft.font, Component.translatable("verityvisuals.hud.hotbar"),
					x + 7, y + 4, MUTED);
			for (int slot = 0; slot < 9; slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				int itemX = x + 5 + slot * 20;
				int itemY = y + 17;
				graphics.fill(itemX, itemY, itemX + 19, itemY + 19, slot == player.getInventory().getSelectedSlot()
						? 0xAAFFD44A : 0x88505055);
				graphics.renderItem(stack, itemX + 1, itemY + 1);
				graphics.renderItemDecorations(minecraft.font, stack, itemX + 1, itemY + 1);
				if (stack.isDamageableItem() && !stack.isEmpty()) {
					drawDurabilityBar(graphics, stack, itemX + 2, itemY + 17, 15);
				}
			}
			y += 47;
		}

		if (VisualSettings.showSelectedItem) {
			ItemStack selected = player.getMainHandItem();
			if (!selected.isEmpty()) {
				int height = selected.isDamageableItem() ? 36 : 24;
				panel(graphics, x, y, width, height);
				graphics.renderItem(selected, x + 5, y + 3);
				graphics.drawString(minecraft.font, fitText(minecraft, selected.getHoverName(), width - 34),
						x + 26, y + 7, config.color);
				if (selected.isDamageableItem()) {
					int percent = durabilityPercent(selected);
					graphics.drawString(minecraft.font,
							Component.translatable("verityvisuals.hud.durability", percent),
							x + 26, y + 21, durabilityColor(percent));
				}
				y += height + 5;
			}
		}

		if (VisualSettings.showEffects) {
			List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());
			if (!effects.isEmpty()) {
				panel(graphics, x, y, width, 20 + effects.size() * 20);
				graphics.drawString(minecraft.font, Component.translatable("verityvisuals.hud.effects"),
						x + 7, y + 4, MUTED);
				for (int i = 0; i < effects.size(); i++) {
					MobEffectInstance effect = effects.get(i);
					int rowY = y + 18 + i * 20;
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
							Gui.getMobEffectSprite(effect.getEffect()), x + 5, rowY, 16, 16);
					String name = Component.translatable(effect.getDescriptionId()).getString();
					@NonNull String duration = effect.isInfiniteDuration()
							? Component.translatable("verityvisuals.hud.infinite").getString()
							: formatDuration(effect.getDuration());
					int durationX = x + width - minecraft.font.width(duration) - 7;
					graphics.drawString(minecraft.font,
							fitText(minecraft, Component.literal(name), durationX - x - 28),
							x + 24, rowY + 4, config.color);
					graphics.drawString(minecraft.font, duration, durationX, rowY + 4, MUTED);
				}
			}
		}
		graphics.pose().popMatrix();
	}

	private static float getEffectiveScale(Minecraft minecraft, Player player, float elementScale) {
		int contentHeight = getContentHeight(player);
		int guiWidth = minecraft.getWindow().getGuiScaledWidth();
		int guiHeight = minecraft.getWindow().getGuiScaledHeight();
		float widthScale = Math.max(0.1F, (guiWidth - 16.0F) / 190.0F);
		float heightScale = Math.max(0.1F, (guiHeight - 16.0F) / contentHeight);
		return Math.min(VisualSettings.hudScale * elementScale, Math.min(widthScale, heightScale));
	}

	private static int getContentHeight(Player player) {
		int contentHeight = 16;
		if (VisualSettings.showVitals) {
			contentHeight += 27;
		}
		if (VisualSettings.showArmor) {
			contentHeight += 47;
		}
		if (VisualSettings.showInventory) {
			contentHeight += 47;
		}
		if (VisualSettings.showSelectedItem && !player.getMainHandItem().isEmpty()) {
			contentHeight += player.getMainHandItem().isDamageableItem() ? 41 : 29;
		}
		if (VisualSettings.showEffects) {
			int effectCount = player.getActiveEffects().size();
			if (effectCount > 0) {
				contentHeight += 20 + effectCount * 20;
			}
		}

		return contentHeight;
	}

	public static int[] getHudBounds(Minecraft minecraft, Player player) {
		HudElementConfig config = VisualSettings.hudElement(HudModule.INVENTORY);
		if (!VisualSettings.inventoryHudEnabled || !config.enabled || minecraft.screen != null) {
			return new int[]{0, 0, 0, 0};
		}
		float scale = getEffectiveScale(minecraft, player, config.scale);
		int width = Math.round(190.0F * scale);
		int height = Math.round(getContentHeight(player) * scale);
		int guiWidth = minecraft.getWindow().getGuiScaledWidth();
		int guiHeight = minecraft.getWindow().getGuiScaledHeight();
		int x = config.x < 0 ? 8 : Math.max(0, Math.min(config.x, guiWidth - width));
		int y = Math.max(0, Math.min(config.y, guiHeight - height));
		return new int[]{x, y, x + width, y + height};
	}

	private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, PANEL);
		graphics.fill(x, y, x + 2, y + height, VisualSettings.menuAccentColor);
	}

	private static int durabilityPercent(ItemStack stack) {
		return Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100.0F / stack.getMaxDamage());
	}

	private static int durabilityColor(int percent) {
		if (percent < 25) {
			return 0xFFFF5555;
		}
		if (percent < 60) {
			return 0xFFFFC900;
		}
		return 0xFF7CDE80;
	}

	private static void drawDurabilityBar(GuiGraphics graphics, ItemStack stack, int x, int y, int width) {
		int percent = durabilityPercent(stack);
		graphics.fill(x, y, x + width, y + 2, 0xFF252525);
		graphics.fill(x, y, x + width * percent / 100, y + 1, durabilityColor(percent));
	}

	private static @NonNull Component fitText(Minecraft minecraft, @NonNull Component component, int maxWidth) {
		if (maxWidth <= 0) {
			return Component.empty();
		}
		if (minecraft.font.width(component) <= maxWidth) {
			return component;
		}
		String text = component.getString();
		int end = text.length();
		while (end > 0 && minecraft.font.width(text.substring(0, end) + "...") > maxWidth) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return Component.literal(text.substring(0, end) + "...");
	}

	private static @NonNull String formatDuration(int ticks) {
		int totalSeconds = Math.max(0, ticks) / 20;
		int seconds = totalSeconds % 60;
		return totalSeconds / 60 + ":" + (seconds < 10 ? "0" : "") + seconds;
	}
}
