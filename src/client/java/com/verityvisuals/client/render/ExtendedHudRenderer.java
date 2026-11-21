package com.verityvisuals.client.render;

import com.verityvisuals.client.config.HudElementConfig;
import com.verityvisuals.client.config.HudModule;
import com.verityvisuals.client.config.VisualSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ExtendedHudRenderer {
	private static final int PANEL = 0xB0101115;
	private static final int MUTED = 0xFFB4B7BD;

	private ExtendedHudRenderer() {
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.screen != null) {
			return;
		}

		List<Rect> occupied = new ArrayList<>();
		int[] inventoryBounds = InventoryHudRenderer.getHudBounds(minecraft, player);
		if (inventoryBounds[2] > inventoryBounds[0] && inventoryBounds[3] > inventoryBounds[1]) {
			occupied.add(new Rect(inventoryBounds[0], inventoryBounds[1],
					inventoryBounds[2], inventoryBounds[3]));
		}
		for (HudModule module : HudModule.values()) {
			if (module == HudModule.INVENTORY) {
				continue;
			}
			HudElementConfig config = VisualSettings.hudElement(module);
			if (!config.enabled) {
				continue;
			}
			List<@NonNull String> lines = getLines(module, minecraft, player);
			if (!lines.isEmpty()) {
				Rect bounds = drawLines(graphics, minecraft, config, lines, occupied);
				occupied.add(bounds);
			}
		}
	}

	private static List<@NonNull String> getLines(HudModule module, Minecraft minecraft, LocalPlayer player) {
		return switch (module) {
			case COORDINATES -> List.of(
					ComponentText.get("verityvisuals.hud.coordinates"),
					Objects.requireNonNull(String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
							player.getX(), player.getY(), player.getZ())));
			case PERFORMANCE -> List.of(
					ComponentText.get("verityvisuals.hud.performance",
							minecraft.getFps(), getPing(minecraft, player)));
			case KEYSTROKES -> List.of(
					key(minecraft.options.keyUp, "W"),
					key(minecraft.options.keyLeft, "A") + "  "
							+ key(minecraft.options.keyDown, "S") + "  "
							+ key(minecraft.options.keyRight, "D"),
					key(minecraft.options.keyJump, ComponentText.get("verityvisuals.hud.jump")));
			case CPS -> List.of(ComponentText.get("verityvisuals.hud.cps",
					ClientEffectsController.getClicksPerSecond()));
			case ARMOR_STATUS -> getArmorLines(player);
			case POTION_EFFECTS -> getPotionLines(player);
			case HEALTH_FOOD -> List.of(ComponentText.get("verityvisuals.hud.health_food",
					Math.round(player.getHealth()), player.getFoodData().getFoodLevel()));
			case TARGET_INFO -> getTargetLines(minecraft);
			case DIRECTION -> List.of(ComponentText.get("verityvisuals.hud.direction",
					ComponentText.get(directionKey(player.getYRot()))));
			case INVENTORY -> List.of();
		};
	}

	private static List<@NonNull String> getArmorLines(Player player) {
		List<@NonNull String> lines = new ArrayList<>();
		lines.add(ComponentText.get("verityvisuals.hud.armor_status"));
		int count = 0;
		for (EquipmentSlot slot : List.<@NonNull EquipmentSlot>of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				int remaining = Math.max(0,
						Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100.0F / stack.getMaxDamage()));
				lines.add(stack.getHoverName().getString() + " " + remaining + "%");
				count++;
			}
		}
		if (count == 0) {
			lines.add(ComponentText.get("verityvisuals.hud.no_armor"));
		}
		return lines;
	}

	private static List<@NonNull String> getPotionLines(Player player) {
		List<@NonNull String> lines = new ArrayList<>();
		lines.add(ComponentText.get("verityvisuals.hud.effects"));
		player.getActiveEffects().stream().limit(4).forEach(effect ->
				lines.add(ComponentText.get(effect.getDescriptionId()) + " "
						+ (effect.isInfiniteDuration() ? ComponentText.get("verityvisuals.hud.infinite")
						: formatDuration(effect.getDuration()))));
		if (lines.size() == 1) {
			lines.add(ComponentText.get("verityvisuals.hud.no_effects"));
		}
		return lines;
	}

	private static List<@NonNull String> getTargetLines(Minecraft minecraft) {
		if (!(minecraft.hitResult instanceof EntityHitResult hit)
				|| !(hit.getEntity() instanceof LivingEntity target)) {
			return List.of();
		}
		return List.of(ComponentText.get("verityvisuals.hud.target"),
				Objects.requireNonNull(target.getName().getString()),
				ComponentText.get("verityvisuals.hud.health_points",
						Math.max(0, Math.round(target.getHealth()))));
	}

	private static Rect drawLines(GuiGraphics graphics, Minecraft minecraft,
								  HudElementConfig config, List<@NonNull String> lines, List<Rect> occupied) {
		float scale = config.scale * VisualSettings.hudScale;
		int width = 90;
		for (String line : lines) {
			width = Math.max(width, minecraft.font.width(line) + 14);
		}
		int height = lines.size() * 11 + 6;
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		float maxScale = Math.min(Math.max(0.01F, (screenWidth - 8.0F) / width),
				Math.max(0.01F, (screenHeight - 8.0F) / height));
		scale = Math.max(0.01F, Math.min(scale, maxScale));
		int drawWidth = Math.round(width * scale);
		int drawHeight = Math.round(height * scale);
		int x = config.x < 0 ? screenWidth - drawWidth - 8 : config.x;
		x = Mth.clamp(x, 0, Math.max(0, screenWidth - drawWidth));
		int y = Mth.clamp(config.y, 0, Math.max(0, screenHeight - drawHeight));
		if (config.x < 0) {
			for (int attempt = 0; attempt < occupied.size() + 1; attempt++) {
				Rect candidate = new Rect(x, y, x + drawWidth, y + drawHeight);
				Rect collision = occupied.stream().filter(candidate::intersects).findFirst().orElse(null);
				if (collision == null) {
					break;
				}
				y = collision.bottom + 4;
				if (y + drawHeight > screenHeight) {
					y = Math.max(0, screenHeight - drawHeight);
					break;
				}
			}
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, width, height, PANEL);
		graphics.fill(0, 0, 2, height, VisualSettings.menuAccentColor);
		for (int index = 0; index < lines.size(); index++) {
			graphics.drawString(minecraft.font, lines.get(index), 7, 4 + index * 11,
					index == 0 ? MUTED : config.color, false);
		}
		graphics.pose().popMatrix();
		return new Rect(x, y, x + drawWidth, y + drawHeight);
	}

	private static @NonNull String key(net.minecraft.client.KeyMapping mapping, @NonNull String label) {
		return "[" + (mapping.isDown() ? "§e" : "§7") + label + "§r]";
	}

	private static int getPing(Minecraft minecraft, LocalPlayer player) {
		var connection = minecraft.getConnection();
		if (connection == null) {
			return 0;
		}
		var info = connection.getPlayerInfo(player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	private static @NonNull String directionKey(float yaw) {
		return switch (Mth.floor(yaw * 4.0F / 360.0F + 0.5F) & 3) {
			case 0 -> "verityvisuals.hud.south";
			case 1 -> "verityvisuals.hud.west";
			case 2 -> "verityvisuals.hud.north";
			default -> "verityvisuals.hud.east";
		};
	}

	private static @NonNull String formatDuration(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		return Objects.requireNonNull(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
	}

	private static final class ComponentText {
		private static @NonNull String get(@NonNull String key, @NonNull Object @NonNull... args) {
			return Objects.requireNonNull(
					net.minecraft.network.chat.Component.translatable(key, args).getString());
		}
	}

	private record Rect(int left, int top, int right, int bottom) {
		private boolean intersects(Rect other) {
			return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
		}
	}
}
