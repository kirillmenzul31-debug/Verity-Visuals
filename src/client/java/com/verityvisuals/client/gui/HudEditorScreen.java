package com.verityvisuals.client.gui;

import com.verityvisuals.client.config.HudElementConfig;
import com.verityvisuals.client.config.HudModule;
import com.verityvisuals.client.config.VisualSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

public final class HudEditorScreen extends Screen {
	private static final int PANEL = 0xD018191C;
	private final Map<HudModule, Box> boxes = new EnumMap<>(HudModule.class);
	private HudModule dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public HudEditorScreen() {
		super(Component.translatable("verityvisuals.hud_editor.title"));
	}

	@Override
	public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x8808090B);
		int panelWidth = Math.max(1, Math.min(420, width - 24));
		int left = Math.max(0, (width - panelWidth) / 2);
		graphics.fill(left, 12, left + panelWidth, 54, PANEL);
		graphics.fill(left, 12, left + panelWidth, 14, VisualSettings.menuAccentColor);
		graphics.drawString(font, fitText(Component.translatable("verityvisuals.hud_editor.title"),
						Math.max(1, panelWidth - 24)),
				left + 12, 22, 0xFFF2F2F2, false);
		graphics.drawString(font, fitText(Component.translatable("verityvisuals.hud_editor.hint"),
						Math.max(1, panelWidth - 24)),
				left + 12, 38, 0xFFB4B7BD, false);
		boxes.clear();

		for (HudModule module : HudModule.values()) {
			HudElementConfig config = VisualSettings.hudElement(module);
			if (!config.enabled || (module == HudModule.INVENTORY && !VisualSettings.inventoryHudEnabled)) {
				continue;
			}
			Component label = Component.translatable("verityvisuals.hud.module." + module.id());
			int boxWidth = Math.min(Math.max(1, width - 16), Math.max(92, font.width(label) + 16));
			int maxX = Math.max(0, width - boxWidth);
			int x = config.x < 0 ? module == HudModule.INVENTORY ? Math.min(8, maxX)
					: Math.max(0, maxX - 8)
					: Math.max(0, Math.min(config.x, maxX));
			int maxY = Math.max(0, height - 20);
			int y = Math.max(Math.min(62, maxY), Math.min(config.y, maxY));
			Box box = new Box(x, y, boxWidth, 20);
			boxes.put(module, box);
			graphics.fill(x, y, x + boxWidth, y + 20, PANEL);
			graphics.fill(x, y, x + 2, y + 20, VisualSettings.menuAccentColor);
			graphics.drawString(font, fitText(label, boxWidth - 16),
					x + 8, y + 6, 0xFFF2F2F2, false);
		}
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (Map.Entry<HudModule, Box> entry : boxes.entrySet()) {
				Box box = entry.getValue();
				if (event.x() >= box.x && event.x() <= box.x + box.width
						&& event.y() >= box.y && event.y() <= box.y + box.height) {
					dragging = entry.getKey();
					dragOffsetX = (int) event.x() - box.x;
					dragOffsetY = (int) event.y() - box.y;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
		if (dragging == null) {
			return super.mouseDragged(event, deltaX, deltaY);
		}
		HudElementConfig config = VisualSettings.hudElement(dragging);
		Box box = boxes.get(dragging);
		int boxWidth = box == null ? 0 : box.width;
		config.x = Math.max(0, Math.min((int) event.x() - dragOffsetX, width - boxWidth));
		config.y = Math.max(0, Math.min((int) event.y() - dragOffsetY, height - 20));
		return true;
	}

	@Override
	public boolean mouseReleased(@NonNull MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			VisualSettings.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		VisualSettings.save();
		super.onClose();
	}

	@Override
	public void removed() {
		VisualSettings.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private @NonNull Component fitText(@NonNull Component component, int maxWidth) {
		if (font.width(component) <= maxWidth) {
			return component;
		}
		String text = component.getString();
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + "...") > maxWidth) {
			end -= Character.charCount(text.codePointBefore(end));
		}
		return Component.literal(text.substring(0, end) + "...");
	}

	private record Box(int x, int y, int width, int height) {
	}
}
