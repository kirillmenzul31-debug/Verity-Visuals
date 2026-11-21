package com.verityvisuals.client.gui;

import com.verityvisuals.client.VerityVisualsClient;
import com.verityvisuals.client.config.AutoSwapController;
import com.verityvisuals.client.config.HudElementConfig;
import com.verityvisuals.client.config.HudModule;
import com.verityvisuals.client.config.VisualSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.verityvisuals.client.config.Waypoint;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class VerityVisualsScreen extends Screen {
	private static final int PANEL_WIDTH = 640;
	private static final int PANEL_HEIGHT = 420;
	private static final int WAYPOINT_PAGE_SIZE = 9;
	private static final int TEXT = 0xFFF2F2F2;
	private static final int MUTED = 0xFF92969D;
	private static final int PANEL = 0xF018191C;
	private static final Tab[] TABS = Tab.values();

	private final Map<String, Float> hoverValues = new HashMap<>();
	private final Map<String, Float> toggleValues = new HashMap<>();
	private final Map<String, Float> sliderValues = new HashMap<>();
	private final long openedAt = System.nanoTime();
	private Tab selectedTab = Tab.HUD;
	private HudModule selectedHud = HudModule.INVENTORY;
	private long tabChangedAt = System.nanoTime();
	private long closingAt;
	private String draggingSlider;
	private boolean resetConfirmation;
	private @Nullable String notificationKey;
	private long notificationAt;
	private int selectedWaypoint = -1;
	private int waypointPage;
	private @NonNull String waypointName = "";
	private @NonNull String waypointX = "";
	private @NonNull String waypointY = "";
	private @NonNull String waypointZ = "";
	private @Nullable String activeTextField;
	private @NonNull String autoSwapItemDraft = VisualSettings.autoSwapItemId;
	private final Map<String, Integer> textCarets = new HashMap<>();
	private boolean capturingAutoSwapKey;

	public VerityVisualsScreen() {
		super(Component.translatable("verityvisuals.menu.title"));
		try {
			if (VisualSettings.selectedMenuTab == null) {
				throw new IllegalArgumentException("Saved menu tab is missing");
			}
			selectedTab = Tab.valueOf(VisualSettings.selectedMenuTab);
		} catch (IllegalArgumentException exception) {
			selectedTab = Tab.HUD;
			VerityVisualsClient.LOGGER.warn("Ignoring invalid saved menu tab '{}'",
					VisualSettings.selectedMenuTab);
			VisualSettings.selectedMenuTab = Tab.HUD.name();
		}
	}

	public void requestClose() {
		if (closingAt == 0L) {
			closingAt = System.nanoTime();
		}
	}

	@Override
	public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		float animation = animationProgress();
		if (closingAt != 0L && animation <= 0.0F) {
			Minecraft.getInstance().setScreen(null);
			return;
		}

		Layout layout = getLayout(animation);
		int backdropAlpha = Math.round(0x88 * animation);
		graphics.fill(0, 0, width, height, backdropAlpha << 24 | 0x0008090B);
		float panelX = layout.offsetX;
		float panelY = layout.offsetY;
		float panelMouseX = layout.toPanelX(mouseX);
		float panelMouseY = layout.toPanelY(mouseY);
		float contentTransition = Math.min(1.0F,
				(System.nanoTime() - tabChangedAt) / 180_000_000.0F);
		float transitionOffset = (1.0F - ease(contentTransition)) * 14.0F;
		float contentMouseX = panelMouseX - transitionOffset;

		graphics.pose().pushMatrix();
		graphics.pose().translate(panelX, panelY);
		graphics.pose().scale(layout.scale, layout.scale);
		int panelColor = withOpacity(PANEL,
				VisualSettings.menuTransparency * (0.78F + 0.22F * animation));
		int accent = VisualSettings.menuAccentColor;
		graphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, panelColor);
		graphics.fill(0, 0, PANEL_WIDTH, 3, accent);
		graphics.drawString(font, Component.translatable("verityvisuals.menu.title"),
				18, 15, TEXT, false);
		@NonNull String version = Objects.requireNonNull(FabricLoader.getInstance()
				.getModContainer(VerityVisualsClient.MOD_ID)
				.orElseThrow()
				.getMetadata()
				.getVersion()
				.getFriendlyString());
		graphics.drawString(font, version,
				PANEL_WIDTH - font.width(version) - 18, 16, accent, false);
		drawTabs(graphics, panelMouseX, panelMouseY);

		graphics.pose().pushMatrix();
		graphics.pose().translate(transitionOffset, 0.0F);
		switch (selectedTab) {
			case HUD -> drawHudTab(graphics, contentMouseX, panelMouseY);
			case VISUALS -> drawVisualsTab(graphics, contentMouseX, panelMouseY);
			case EFFECTS -> drawEffectsTab(graphics, contentMouseX, panelMouseY);
			case WAYPOINTS -> drawWaypointsTab(graphics, contentMouseX, panelMouseY);
			case AUTO_SWAP -> drawAutoSwapTab(graphics, contentMouseX, panelMouseY);
			case SETTINGS -> drawSettingsTab(graphics, contentMouseX, panelMouseY);
		}
		graphics.pose().popMatrix();

		graphics.fill(16, PANEL_HEIGHT - 32, PANEL_WIDTH - 16, PANEL_HEIGHT - 31, 0xFF34363A);
		graphics.drawString(font, Component.translatable("verityvisuals.menu.close_hint"),
				18, PANEL_HEIGHT - 22, MUTED, false);
		String notification = notificationKey;
		if (notification != null && System.nanoTime() - notificationAt < 1_800_000_000L) {
			Component message = Component.translatable(notification);
			graphics.drawString(font, message,
					PANEL_WIDTH - font.width(message) - 20,
					PANEL_HEIGHT - 22, accent, false);
		}
		if (resetConfirmation) {
			drawResetConfirmation(graphics, panelMouseX, panelMouseY);
		}
		graphics.pose().popMatrix();

		Component tooltip = tooltipAt(contentMouseX, panelMouseY);
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
		}
	}

	private void drawTabs(GuiGraphics graphics, float mouseX, float mouseY) {
		for (int index = 0; index < TABS.length; index++) {
			Tab tab = TABS[index];
			int x = tabX(index);
			boolean selected = tab == selectedTab;
			float hover = animate(hoverValues, "tab." + tab.name(),
					inside(mouseX, mouseY, x, 43, 98, 25) ? 1.0F : 0.0F);
			graphics.fill(x, 43, x + 98, 68,
					selected ? 0xFF303136 : interpolateColor(0xFF202124, 0xFF292A2E, hover));
			if (selected) {
				graphics.fill(x, 66, x + 98, 68, VisualSettings.menuAccentColor);
			}
			graphics.drawCenteredString(font, Component.translatable(tab.translationKey),
					x + 49, 51, selected ? TEXT : MUTED);
		}
	}

	private static int tabX(int index) {
		int totalWidth = TABS.length * 98 + (TABS.length - 1) * 4;
		return (PANEL_WIDTH - totalWidth) / 2 + index * 102;
	}

	private void drawHudTab(GuiGraphics graphics, float mouseX, float mouseY) {
		graphics.drawString(font, Component.translatable("verityvisuals.menu.hud_catalog"),
				18, 78, MUTED, false);
		for (int index = 0; index < HudModule.values().length; index++) {
			HudModule module = HudModule.values()[index];
			int y = 93 + index * 24;
			boolean selected = module == selectedHud;
			float hover = animate(hoverValues, "hud." + module.name(),
					inside(mouseX, mouseY, 18, y, 214, 21) ? 1.0F : 0.0F);
			graphics.fill(18, y, 232, y + 21, selected
					? 0xFF303136
					: interpolateColor(0xFF202124, 0xFF2C2D31, hover));
			graphics.drawString(font,
					Component.translatable("verityvisuals.hud.module." + module.id()),
					26, y + 6, TEXT, false);
			drawToggle(graphics, "hud." + module.name(), 188, y + 4,
					hudEnabled(module), mouseX, mouseY);
		}

		HudElementConfig config = VisualSettings.hudElement(selectedHud);
		graphics.fill(248, 80, 622, 380, 0x77202023);
		graphics.drawString(font,
				Component.translatable("verityvisuals.hud.module." + selectedHud.id()),
				262, 90, TEXT, false);
		graphics.drawString(font, Component.translatable("verityvisuals.hud_editor.drag"),
				262, 108, MUTED, false);
		drawSlider(graphics, "hudScale", 262, 124, 160,
				VisualSettings.hudScale, 0.5F, 2.0F,
				Component.translatable("verityvisuals.setting.hud_scale"));
		drawSlider(graphics, "elementScale", 262, 164, 270,
				config.scale, 0.5F, 2.0F,
				Component.translatable("verityvisuals.setting.element_scale"));
		graphics.drawString(font, Component.translatable("verityvisuals.setting.hud_color"),
				262, 201, MUTED, false);
		drawColorButton(graphics, "hudColor", 262, 217, 138, 24,
				Component.translatable("verityvisuals.setting.change_color"),
				VisualSettings.hudElement(selectedHud).color, mouseX, mouseY);
		graphics.drawString(font, Component.translatable("verityvisuals.hud.position",
				config.x < 0 ? Component.translatable("verityvisuals.hud.anchor.auto")
						: Component.literal(Objects.requireNonNull(Integer.toString(config.x))),
				config.y),
				262, 251, MUTED, false);
		drawButton(graphics, "editHud", 262, 276, 230, 28,
				Component.translatable("verityvisuals.hud_editor.open"), mouseX, mouseY);
		if (selectedHud == HudModule.INVENTORY) {
			graphics.drawString(font, Component.translatable("verityvisuals.menu.inventory_components"),
					262, 311, MUTED, false);
			drawMiniToggle(graphics, "legacy.armor", 262, 331,
					Component.translatable("verityvisuals.setting.hud_armor"), VisualSettings.showArmor,
					mouseX, mouseY);
			drawMiniToggle(graphics, "legacy.hotbar", 430, 331,
					Component.translatable("verityvisuals.setting.hud_hotbar"), VisualSettings.showInventory,
					mouseX, mouseY);
			drawMiniToggle(graphics, "legacy.effects", 262, 350,
					Component.translatable("verityvisuals.setting.hud_effects"), VisualSettings.showEffects,
					mouseX, mouseY);
			drawMiniToggle(graphics, "legacy.vitals", 430, 350,
					Component.translatable("verityvisuals.setting.hud_vitals"), VisualSettings.showVitals,
					mouseX, mouseY);
			drawMiniToggle(graphics, "legacy.selected", 262, 369,
					Component.translatable("verityvisuals.setting.hud_selected_item"),
					VisualSettings.showSelectedItem, mouseX, mouseY);
		}
	}

	private void drawVisualsTab(GuiGraphics graphics, float mouseX, float mouseY) {
		drawSection(graphics, 20, 84, "verityvisuals.feature.block_outline");
		drawMiniToggle(graphics, "visual.outline", 470, 82,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.blockOutlineEnabled,
				mouseX, mouseY);
		drawSlider(graphics, "outlineThickness", 30, 124, 270,
				VisualSettings.outlineThickness, 1.0F, 6.0F,
				Component.translatable("verityvisuals.setting.outline_thickness"));
		drawColorButton(graphics, "outlineColor", 326, 132, 170, 25,
				Component.translatable("verityvisuals.setting.outline_color"),
				VisualSettings.outlineColor, mouseX, mouseY);

		drawSection(graphics, 20, 178, "verityvisuals.feature.china_hat");
		drawMiniToggle(graphics, "visual.hat", 470, 176,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.chinaHatEnabled,
				mouseX, mouseY);
		drawSlider(graphics, "hatSize", 30, 218, 270,
				VisualSettings.chinaHatSize, 0.4F, 1.3F,
				Component.translatable("verityvisuals.setting.hat_size"));
		drawSlider(graphics, "hatHeight", 326, 218, 270,
				VisualSettings.chinaHatHeight, 0.25F, 1.0F,
				Component.translatable("verityvisuals.setting.hat_height"));
		drawSlider(graphics, "hatOpacity", 30, 274, 270,
				VisualSettings.chinaHatOpacity, 0.1F, 1.0F,
				Component.translatable("verityvisuals.setting.hat_opacity"));
		drawColorButton(graphics, "hatColor", 326, 282, 170, 25,
				Component.translatable("verityvisuals.setting.hat_color"),
				VisualSettings.chinaHatColor, mouseX, mouseY);

		drawSection(graphics, 20, 328, "verityvisuals.feature.fullbright");
		drawMiniToggle(graphics, "visual.brightness", 470, 326,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.fullbrightEnabled,
				mouseX, mouseY);
		drawSlider(graphics, "brightness", 326, 366, 270,
				VisualSettings.brightness, 0.6F, 1.0F,
				Component.translatable("verityvisuals.setting.brightness_intensity"));
	}

	private void drawEffectsTab(GuiGraphics graphics, float mouseX, float mouseY) {
		graphics.drawString(font, Component.translatable("verityvisuals.effects.death"),
				24, 94, TEXT, false);
		drawMiniToggle(graphics, "effect.death", 470, 91,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.deathEffectEnabled,
				mouseX, mouseY);
		drawSlider(graphics, "deathDuration", 32, 139, 280,
				VisualSettings.deathEffectDuration, 0.5F, 8.0F,
				Component.translatable("verityvisuals.effects.duration"));
		drawColorButton(graphics, "deathColor", 344, 146, 180, 25,
				Component.translatable("verityvisuals.effects.color"),
				VisualSettings.deathEffectColor, mouseX, mouseY);
		graphics.fill(22, 185, PANEL_WIDTH - 22, 186, 0xFF34363A);
		graphics.drawString(font, Component.translatable("verityvisuals.effects.hit"),
				24, 207, TEXT, false);
		drawMiniToggle(graphics, "effect.hit", 470, 204,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.hitEffectEnabled,
				mouseX, mouseY);
		drawSlider(graphics, "hitDuration", 32, 251, 280,
				VisualSettings.hitEffectDuration, 0.1F, 2.0F,
				Component.translatable("verityvisuals.effects.duration"));
		drawSlider(graphics, "hitIntensity", 344, 251, 280,
				VisualSettings.hitEffectIntensity, 0.1F, 1.0F,
				Component.translatable("verityvisuals.effects.intensity"));
		drawColorButton(graphics, "hitColor", 32, 312, 180, 25,
				Component.translatable("verityvisuals.effects.color"),
				VisualSettings.hitEffectColor, mouseX, mouseY);
		graphics.drawString(font, Component.translatable("verityvisuals.effects.description"),
				24, 362, MUTED, false);
	}

	private void drawWaypointsTab(GuiGraphics graphics, float mouseX, float mouseY) {
		graphics.drawString(font, Component.translatable("verityvisuals.waypoint.list"), 24, 92, TEXT, false);
		graphics.fill(24, 112, 290, 370, 0x77202023);
		int pageCount = Math.max(1,
				(VisualSettings.waypoints.size() + WAYPOINT_PAGE_SIZE - 1) / WAYPOINT_PAGE_SIZE);
		waypointPage = Math.min(waypointPage, pageCount - 1);
		int start = waypointPage * WAYPOINT_PAGE_SIZE;
		int end = Math.min(VisualSettings.waypoints.size(), start + WAYPOINT_PAGE_SIZE);
		int visible = 0;
		for (int index = start; index < end; index++) {
			Waypoint waypoint = VisualSettings.waypoints.get(index);
			int y = 118 + visible * 24;
			boolean selected = index == selectedWaypoint;
			graphics.fill(28, y, 286, y + 21, selected ? 0xFF303136 : 0xFF242529);
			graphics.fill(32, y + 5, 36, y + 16, waypoint.color);
			Minecraft minecraft = Minecraft.getInstance();
			var player = minecraft.player;
			String distanceText = null;
			if (player != null && minecraft.level != null
					&& currentDimension(minecraft).equals(Waypoint.sanitizeDimension(waypoint.dimension))) {
				double distance = player.position().distanceTo(
						new Vec3(waypoint.x, waypoint.y, waypoint.z));
				distanceText = Math.round(distance) + "m";
			}
			int nameWidth = distanceText == null ? 160 : Math.max(0, 164 - font.width(distanceText));
			graphics.drawString(font, fitText(
					Component.literal(Objects.requireNonNull(Waypoint.normalizeName(waypoint.name))),
					nameWidth), 42, y + 6, TEXT, false);
			if (distanceText != null) {
				graphics.drawString(font, Component.literal(distanceText),
						210 - font.width(distanceText), y + 6, MUTED, false);
			}
			drawToggle(graphics, "waypoint." + index, 250, y + 4, waypoint.enabled, mouseX, mouseY);
			visible++;
		}
		if (pageCount > 1) {
			drawButton(graphics, "waypointPrevious", 28, 340, 36, 22,
					Component.literal("<"), mouseX, mouseY);
			graphics.drawCenteredString(font, Component.literal((waypointPage + 1) + "/" + pageCount),
					157, 347, MUTED);
			drawButton(graphics, "waypointNext", 250, 340, 36, 22,
					Component.literal(">"), mouseX, mouseY);
		}
		graphics.drawString(font, Component.translatable("verityvisuals.waypoint.editor"), 318, 92, TEXT, false);
		drawTextField(graphics, "waypoint.name", 318, 112, 280, 24,
				Component.translatable("verityvisuals.waypoint.name.placeholder"),
				waypointName, mouseX, mouseY);
		drawTextField(graphics, "waypoint.x", 318, 152, 86, 24,
				Component.translatable("verityvisuals.waypoint.x.placeholder"),
				waypointX, mouseX, mouseY);
		drawTextField(graphics, "waypoint.y", 412, 152, 86, 24,
				Component.translatable("verityvisuals.waypoint.y.placeholder"),
				waypointY, mouseX, mouseY);
		drawTextField(graphics, "waypoint.z", 506, 152, 92, 24,
				Component.translatable("verityvisuals.waypoint.z.placeholder"),
				waypointZ, mouseX, mouseY);
		drawButton(graphics, "waypointNew", 318, 192, 130, 28,
				Component.translatable("verityvisuals.waypoint.new"), mouseX, mouseY);
		drawButton(graphics, "waypointSave", 456, 192, 142, 28,
				Component.translatable("verityvisuals.waypoint.save"), mouseX, mouseY);
		drawButton(graphics, "waypointDelete", 318, 232, 130, 28,
				Component.translatable("verityvisuals.waypoint.delete"), mouseX, mouseY);
		drawButton(graphics, "waypointCurrent", 456, 232, 142, 28,
				Component.translatable("verityvisuals.waypoint.use_current"), mouseX, mouseY);
		drawColorButton(graphics, "waypointColor", 318, 272, 280, 28,
				Component.translatable("verityvisuals.setting.change_color"),
				selectedWaypoint >= 0 && selectedWaypoint < VisualSettings.waypoints.size()
						? VisualSettings.waypoints.get(selectedWaypoint).color : 0xFFFFC900,
				mouseX, mouseY);
	}

	private void drawTextField(GuiGraphics graphics, String id, int x, int y, int width, int height,
			@NonNull Component placeholder, @NonNull String value, float mouseX, float mouseY) {
		boolean focused = id.equals(activeTextField);
		float hover = inside(mouseX, mouseY, x, y, width, height) ? 1.0F : 0.0F;
		graphics.fill(x, y, x + width, y + height,
				interpolateColor(0xFF202124, 0xFF303136, hover));
		if (focused) {
			graphics.fill(x, y, x + width, y + 2, VisualSettings.menuAccentColor);
		}
		int textX = x + 8;
		int textY = y + (height - 8) / 2;
		if (value.isEmpty()) {
			graphics.drawString(font, fitText(placeholder, width - (focused ? 20 : 16)),
					textX + (focused ? 4 : 0), textY, 0xAA92969D, false);
			if (focused && cursorVisible()) {
				graphics.fill(textX, textY - 1, textX + 1, textY + 9, 0xFFB4B7BD);
			}
			return;
		}
		int caret = Math.min(textCarets.getOrDefault(id, value.length()), value.length());
		TextView textView = textView(value, caret, width - 16);
		graphics.drawString(font, textView.text, textX, textY, TEXT, false);
		if (focused && cursorVisible()) {
			int cursorX = textX + font.width(Objects.requireNonNull(value.substring(textView.start, caret)));
			graphics.fill(cursorX, textY - 1, cursorX + 1, textY + 9, TEXT);
		}
	}

	private void drawAutoSwapTab(GuiGraphics graphics, float mouseX, float mouseY) {
		drawMiniToggle(graphics, "autoswap.enabled", 24, 92,
				Component.translatable("verityvisuals.state.enabled"), VisualSettings.autoSwapEnabled,
				mouseX, mouseY);
		drawTextField(graphics, "autoswap.item", 24, 126, 280, 24,
				Component.translatable("verityvisuals.autoswap.item_hint"),
				autoSwapItemDraft, mouseX, mouseY);
		drawButton(graphics, "autoswap.save", 314, 126, 140, 24,
				Component.translatable("verityvisuals.autoswap.save"), mouseX, mouseY);
		@NonNull String itemIdText = Objects.requireNonNull(autoSwapItemDraft.strip());
		Identifier itemId = Identifier.tryParse(itemIdText);
		boolean validItem = itemId != null && itemIdText.equals(itemId.toString())
				&& BuiltInRegistries.ITEM.containsKey(itemId);
		if (!autoSwapItemDraft.isEmpty() && !validItem) {
			graphics.drawString(font, Component.translatable("verityvisuals.autoswap.invalid_item"),
					24, 158, 0xFFFF7777, false);
		}
		graphics.drawString(font, Component.translatable("verityvisuals.autoswap.activation_key"),
				24, 178, TEXT, false);
		drawButton(graphics, "autoswap.key", 24, 196, 260, 28,
				capturingAutoSwapKey
						? Component.translatable("verityvisuals.autoswap.press_key")
						: Component.translatable("verityvisuals.autoswap.key_binding",
								AutoSwapController.activationKeyName()),
				mouseX, mouseY);
		if (AutoSwapController.hasHotbarKeyConflict()) {
			graphics.drawString(font, Component.translatable("verityvisuals.autoswap.key_conflict"),
					24, 228, 0xFFFF7777, false);
		}
		graphics.drawWordWrap(font,
				Component.translatable("verityvisuals.autoswap.description"),
				24, 242, 574, MUTED);
	}

	private void drawSettingsTab(GuiGraphics graphics, float mouseX, float mouseY) {
		float maximumScale = maximumMenuScale();
		float minimumScale = minimumMenuScale();
		drawSlider(graphics, "menuScale", 30, 102, 270,
				effectiveMenuScale(), minimumScale, maximumScale,
				Component.translatable("verityvisuals.settings.menu_scale"));
		drawColorButton(graphics, "menuAccent", 326, 110, 220, 25,
				Component.translatable("verityvisuals.settings.accent"),
				VisualSettings.menuAccentColor, mouseX, mouseY);
		drawSlider(graphics, "menuTransparency", 30, 164, 270,
				VisualSettings.menuTransparency, 0.45F, 1.0F,
				Component.translatable("verityvisuals.settings.transparency"));
		drawSlider(graphics, "animationIntensity", 326, 164, 270,
				VisualSettings.animationIntensity, 0.0F, 1.5F,
				Component.translatable("verityvisuals.settings.animation"));
		drawMiniToggle(graphics, "setting.compact", 30, 224,
				Component.translatable("verityvisuals.settings.compact"), VisualSettings.compactMode,
				mouseX, mouseY);
		drawButton(graphics, "reset", 30, 284, 220, 30,
				Component.translatable("verityvisuals.settings.reset"), mouseX, mouseY);
		graphics.drawString(font, Component.translatable("verityvisuals.settings.reset_hint"),
				30, 326, MUTED, false);
	}

	private void drawSection(GuiGraphics graphics, int x, int y, @NonNull String translationKey) {
		graphics.drawString(font, Component.translatable(translationKey), x, y, TEXT, false);
		graphics.fill(x, y + 20, PANEL_WIDTH - 20, y + 21, 0xFF34363A);
	}

	private void drawToggle(GuiGraphics graphics, @NonNull String id, int x, int y, boolean enabled,
			float mouseX, float mouseY) {
		float progress = animate(toggleValues, id, enabled ? 1.0F : 0.0F);
		float hover = animate(hoverValues, "toggle." + id,
				insideToggle(mouseX, mouseY, x, y) ? 1.0F : 0.0F);
		graphics.fill(x, y, x + 34, y + 14,
				interpolateColor(0xFF505158, VisualSettings.menuAccentColor, progress));
		graphics.fill(x + Math.round(2 + progress * 18), y + 2,
				x + Math.round(12 + progress * 18), y + 12,
				interpolateColor(0xFFD8D8D8, 0xFFFFFFFF, hover));
	}

	private void drawMiniToggle(GuiGraphics graphics, @NonNull String id, int x, int y,
			@NonNull Component label, boolean enabled, float mouseX, float mouseY) {
		graphics.drawString(font, fitText(label, 128), x, y + 4, enabled ? TEXT : MUTED, false);
		drawToggle(graphics, id, x + 134, y + 2, enabled, mouseX, mouseY);
	}

	private void drawSlider(GuiGraphics graphics, @NonNull String id, int x, int y, int sliderWidth,
			float value, float minimum, float maximum, @NonNull Component label) {
		graphics.drawString(font, label, x, y, MUTED, false);
		float range = maximum - minimum;
		float fraction = range <= 0.0F ? 0.0F
				: Math.max(0.0F, Math.min(1.0F, (value - minimum) / range));
		float smooth = animate(sliderValues, id, fraction);
		int barY = y + 20;
		int knobX = x + Math.round((sliderWidth - 8) * smooth);
		graphics.fill(x, barY, x + sliderWidth, barY + 3, 0xFF44464B);
		graphics.fill(x, barY, knobX + 4, barY + 3, VisualSettings.menuAccentColor);
		graphics.fill(knobX, barY - 4, knobX + 8, barY + 7, TEXT);
		float displayedValue = minimum + range * fraction;
		graphics.drawString(font, String.format(Locale.ROOT, "%.2f", displayedValue),
				x + sliderWidth + 9, y + 13, TEXT, false);
	}

	private void drawButton(GuiGraphics graphics, @NonNull String id, int x, int y, int buttonWidth,
			int buttonHeight, @NonNull Component label, float mouseX, float mouseY) {
		float hover = animate(hoverValues, "button." + id,
				inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight) ? 1.0F : 0.0F);
		graphics.fill(x, y, x + buttonWidth, y + buttonHeight,
				interpolateColor(0xFF242529, 0xFF34363A, hover));
		graphics.fill(x, y, x + 2, y + buttonHeight, VisualSettings.menuAccentColor);
		graphics.drawCenteredString(font, fitText(label, buttonWidth - 14), x + buttonWidth / 2,
				y + (buttonHeight - 8) / 2, TEXT);
	}

	private void drawColorButton(GuiGraphics graphics, @NonNull String id, int x, int y, int buttonWidth,
			int buttonHeight, @NonNull Component label, int color, float mouseX, float mouseY) {
		float hover = animate(hoverValues, "button." + id,
				inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight) ? 1.0F : 0.0F);
		graphics.fill(x, y, x + buttonWidth, y + buttonHeight,
				interpolateColor(0xFF242529, 0xFF34363A, hover));
		graphics.fill(x, y, x + 2, y + buttonHeight, VisualSettings.menuAccentColor);
		int swatchX = x + 8;
		int swatchY = y + (buttonHeight - 16) / 2;
		graphics.fill(swatchX, swatchY, swatchX + 16, swatchY + 16, color);
		graphics.fill(swatchX + 2, swatchY + 2, swatchX + 14, swatchY + 14, 0xFF202124);
		graphics.fill(swatchX + 4, swatchY + 4, swatchX + 12, swatchY + 12, color);
		int labelStart = swatchX + 24;
		int labelWidth = Math.max(0, x + buttonWidth - labelStart - 6);
		graphics.drawCenteredString(font, fitText(label, labelWidth),
				labelStart + labelWidth / 2, y + (buttonHeight - 8) / 2, TEXT);
	}

	private @NonNull Component fitText(@NonNull Component component, int maxWidth) {
		String text = component.getString();
		if (font.width(component) <= maxWidth) {
			return component;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
			end = previousOffset(text, end);
		}
		return Component.literal(text.substring(0, end) + ellipsis);
	}

	private TextView textView(@NonNull String value, int caret, int maxWidth) {
		int safeWidth = Math.max(1, maxWidth);
		int start = Math.min(caret, value.length());
		while (start > 0 && font.width(Objects.requireNonNull(value.substring(start, caret))) > safeWidth - 1) {
			start = previousOffset(value, start);
		}
		int end = caret;
		while (end < value.length()) {
			int next = nextOffset(value, end);
			if (font.width(Objects.requireNonNull(value.substring(start, next))) > safeWidth) {
				break;
			}
			end = next;
		}
		return new TextView(Objects.requireNonNull(value.substring(start, end)), start);
	}

	private void placeCaretAtMouse(String id, float mouseOffset) {
		String value = textFieldValue(id);
		if (value.isEmpty()) {
			textCarets.put(id, 0);
			return;
		}
		TextView visible = textView(value, textCarets.getOrDefault(id, value.length()), fieldWidth(id) - 16);
		int targetX = Math.max(0, (int) mouseOffset);
		int caret = visible.start;
		while (caret < value.length()) {
			int next = nextOffset(value, caret);
			if (font.width(Objects.requireNonNull(value.substring(visible.start, next))) > targetX) {
				break;
			}
			caret = next;
		}
		textCarets.put(id, caret);
	}

	private static boolean cursorVisible() {
		return (System.currentTimeMillis() / 500L & 1L) == 0L;
	}

	private static int previousOffset(String value, int offset) {
		return offset <= 0 ? 0 : offset - Character.charCount(value.codePointBefore(offset));
	}

	private static int nextOffset(String value, int offset) {
		return offset >= value.length() ? value.length()
				: offset + Character.charCount(value.codePointAt(offset));
	}

	private @NonNull String textFieldValue(String id) {
		return switch (id) {
			case "waypoint.name" -> waypointName;
			case "waypoint.x" -> waypointX;
			case "waypoint.y" -> waypointY;
			case "waypoint.z" -> waypointZ;
			case "autoswap.item" -> autoSwapItemDraft;
			default -> "";
		};
	}

	private int fieldWidth(String id) {
		return switch (id) {
			case "waypoint.name" -> 280;
			case "waypoint.x", "waypoint.y" -> 86;
			case "waypoint.z" -> 92;
			case "autoswap.item" -> 280;
			default -> 0;
		};
	}

	private boolean activeFieldContains(float mouseX, float mouseY) {
		if (activeTextField == null) {
			return false;
		}
		return switch (activeTextField) {
			case "waypoint.name" -> selectedTab == Tab.WAYPOINTS
					&& inside(mouseX, mouseY, 318, 112, 280, 24);
			case "waypoint.x" -> selectedTab == Tab.WAYPOINTS
					&& inside(mouseX, mouseY, 318, 152, 86, 24);
			case "waypoint.y" -> selectedTab == Tab.WAYPOINTS
					&& inside(mouseX, mouseY, 412, 152, 86, 24);
			case "waypoint.z" -> selectedTab == Tab.WAYPOINTS
					&& inside(mouseX, mouseY, 506, 152, 92, 24);
			case "autoswap.item" -> selectedTab == Tab.AUTO_SWAP
					&& inside(mouseX, mouseY, 24, 126, 280, 24);
			default -> false;
		};
	}

	private record TextView(@NonNull String text, int start) {
	}

	private void drawResetConfirmation(GuiGraphics graphics, float mouseX, float mouseY) {
		int x = (PANEL_WIDTH - 330) / 2;
		int y = (PANEL_HEIGHT - 130) / 2;
		graphics.fill(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xAA000000);
		graphics.fill(x, y, x + 330, y + 130, 0xFF202124);
		graphics.fill(x, y, x + 330, y + 3, VisualSettings.menuAccentColor);
		graphics.drawCenteredString(font,
				Component.translatable("verityvisuals.settings.reset_confirm"), PANEL_WIDTH / 2, y + 22, TEXT);
		drawButton(graphics, "resetYes", x + 28, y + 69, 126, 28,
				Component.translatable("verityvisuals.settings.confirm"), mouseX, mouseY);
		drawButton(graphics, "resetNo", x + 176, y + 69, 126, 28,
				Component.translatable("verityvisuals.menu.cancel"), mouseX, mouseY);
	}

	private boolean selectTab(float mouseX, float mouseY) {
		for (int index = 0; index < TABS.length; index++) {
			if (inside(mouseX, mouseY, tabX(index), 43, 98, 25)) {
				Tab next = TABS[index];
				activeTextField = null;
				capturingAutoSwapKey = false;
				if (selectedTab != next) {
					selectedTab = next;
					VisualSettings.selectedMenuTab = selectedTab.name();
					VisualSettings.save();
					tabChangedAt = System.nanoTime();
				}
				return true;
			}
		}
		return false;
	}

	private boolean beginSliderDrag(float mouseX, float mouseY) {
		if (selectedTab == Tab.HUD && inside(mouseX, mouseY, 262, 143, 160, 12)) {
			draggingSlider = "hudScale";
		} else if (selectedTab == Tab.HUD && inside(mouseX, mouseY, 262, 183, 270, 12)) {
			draggingSlider = "elementScale";
		} else if (selectedTab == Tab.VISUALS && inside(mouseX, mouseY, 30, 136, 270, 12)) {
			draggingSlider = "outlineThickness";
		} else if (selectedTab == Tab.VISUALS && inside(mouseX, mouseY, 30, 230, 270, 12)) {
			draggingSlider = "hatSize";
		} else if (selectedTab == Tab.VISUALS && inside(mouseX, mouseY, 326, 230, 270, 12)) {
			draggingSlider = "hatHeight";
		} else if (selectedTab == Tab.VISUALS && inside(mouseX, mouseY, 30, 286, 270, 12)) {
			draggingSlider = "hatOpacity";
		} else if (selectedTab == Tab.VISUALS && inside(mouseX, mouseY, 326, 378, 270, 12)) {
			draggingSlider = "brightness";
		} else if (selectedTab == Tab.EFFECTS && inside(mouseX, mouseY, 32, 157, 280, 12)) {
			draggingSlider = "deathDuration";
		} else if (selectedTab == Tab.EFFECTS && inside(mouseX, mouseY, 32, 269, 280, 12)) {
			draggingSlider = "hitDuration";
		} else if (selectedTab == Tab.EFFECTS && inside(mouseX, mouseY, 344, 269, 280, 12)) {
			draggingSlider = "hitIntensity";
		} else if (selectedTab == Tab.SETTINGS && inside(mouseX, mouseY, 30, 120, 270, 12)) {
			draggingSlider = "menuScale";
		} else if (selectedTab == Tab.SETTINGS && inside(mouseX, mouseY, 30, 182, 270, 12)) {
			draggingSlider = "menuTransparency";
		} else if (selectedTab == Tab.SETTINGS && inside(mouseX, mouseY, 326, 182, 270, 12)) {
			draggingSlider = "animationIntensity";
		} else {
			return false;
		}
		applySlider(mouseX);
		return true;
	}

	private void applySlider(float mouseX) {
		int x = sliderStart(draggingSlider);
		int width = sliderWidth(draggingSlider);
		float fraction = Math.max(0.0F, Math.min(1.0F, (mouseX - x) / width));
		switch (draggingSlider) {
			case "hudScale" -> VisualSettings.hudScale = lerp(0.5F, 2.0F, fraction);
			case "elementScale" -> VisualSettings.hudElement(selectedHud).scale = lerp(0.5F, 2.0F, fraction);
			case "outlineThickness" -> VisualSettings.outlineThickness = lerp(1.0F, 6.0F, fraction);
			case "hatSize" -> VisualSettings.chinaHatSize = lerp(0.4F, 1.3F, fraction);
			case "hatHeight" -> VisualSettings.chinaHatHeight = lerp(0.25F, 1.0F, fraction);
			case "hatOpacity" -> VisualSettings.chinaHatOpacity = lerp(0.1F, 1.0F, fraction);
			case "brightness" -> VisualSettings.brightness = lerp(0.6F, 1.0F, fraction);
			case "deathDuration" -> VisualSettings.deathEffectDuration = lerp(0.5F, 8.0F, fraction);
			case "hitDuration" -> VisualSettings.hitEffectDuration = lerp(0.1F, 2.0F, fraction);
			case "hitIntensity" -> VisualSettings.hitEffectIntensity = lerp(0.1F, 1.0F, fraction);
			case "menuScale" -> VisualSettings.menuScale = lerp(minimumMenuScale(), maximumMenuScale(), fraction);
			case "menuTransparency" -> VisualSettings.menuTransparency = lerp(0.45F, 1.0F, fraction);
			case "animationIntensity" -> VisualSettings.animationIntensity = lerp(0.0F, 1.5F, fraction);
			default -> {
			}
		}
	}

	private int sliderStart(String id) {
		return switch (id) {
			case "hudScale", "elementScale", "hatSize", "hatOpacity", "outlineThickness", "brightness",
					"deathDuration", "hitDuration", "menuScale", "menuTransparency" -> {
				if (id.equals("hudScale") || id.equals("elementScale"))
					yield 262;
				if (id.equals("hatSize") || id.equals("hatOpacity") || id.equals("outlineThickness"))
					yield 30;
				if (id.equals("brightness"))
					yield 326;
				if (id.equals("deathDuration") || id.equals("hitDuration"))
					yield 32;
				yield 30;
			}
			case "hatHeight", "hitIntensity", "animationIntensity" ->
				id.equals("hatHeight") || id.equals("animationIntensity") ? 326 : 344;
			default -> 0;
		};
	}

	private int sliderWidth(String id) {
		return id.equals("hudScale") ? 160
				: id.equals("hitIntensity") || id.equals("animationIntensity") || id.equals("hatHeight") ? 270
						: id.equals("deathDuration") || id.equals("hitDuration") ? 280 : 270;
	}

	private boolean handleClick(float mouseX, float mouseY) {
		if (resetConfirmation) {
			int left = (PANEL_WIDTH - 330) / 2;
			int top = (PANEL_HEIGHT - 130) / 2;
			if (inside(mouseX, mouseY, left + 28, top + 69, 126, 28)) {
				VisualSettings.resetToDefaults();
				AutoSwapController.setActivationKeyCode(VisualSettings.autoSwapKeyCode);
				VisualSettings.save();
				selectedTab = Tab.HUD;
				selectedHud = HudModule.INVENTORY;
				resetConfirmation = false;
				notify("verityvisuals.notification.reset");
			} else if (inside(mouseX, mouseY, left + 176, top + 69, 126, 28)
					|| !inside(mouseX, mouseY, left, top, 330, 130)) {
				resetConfirmation = false;
			}
			return true;
		}

		switch (selectedTab) {
			case HUD -> {
				if (inside(mouseX, mouseY, 262, 217, 138, 24)) {
					VisualSettings.hudElement(selectedHud).color = nextColor(
							VisualSettings.hudElement(selectedHud).color);
				} else if (inside(mouseX, mouseY, 262, 276, 230, 28)) {
					Minecraft.getInstance().setScreen(new HudEditorScreen());
					return true;
				} else if (selectedHud == HudModule.INVENTORY && handleLegacyHudClick(mouseX, mouseY)) {
					// Legacy component toggles are handled without changing their saved setting
					// model.
				} else if (inside(mouseX, mouseY, 18, 93, 214, 261)) {
					int index = ((int) mouseY - 93) / 24;
					if (index >= 0 && index < HudModule.values().length) {
						HudModule module = HudModule.values()[index];
						if (mouseX >= 188) {
							toggleHud(module);
						} else {
							selectedHud = module;
						}
					}
				} else {
					return false;
				}
			}
			case VISUALS -> {
				if (insideToggle(mouseX, mouseY, 604, 84)) {
					VisualSettings.blockOutlineEnabled = !VisualSettings.blockOutlineEnabled;
				} else if (inside(mouseX, mouseY, 326, 132, 170, 25)) {
					VisualSettings.outlineColor = nextColor(VisualSettings.outlineColor);
				} else if (insideToggle(mouseX, mouseY, 604, 178)) {
					VisualSettings.chinaHatEnabled = !VisualSettings.chinaHatEnabled;
				} else if (inside(mouseX, mouseY, 326, 282, 170, 25)) {
					VisualSettings.chinaHatColor = nextColor(VisualSettings.chinaHatColor);
				} else if (insideToggle(mouseX, mouseY, 604, 328)) {
					VisualSettings.fullbrightEnabled = !VisualSettings.fullbrightEnabled;
				} else {
					return false;
				}
			}
			case EFFECTS -> {
				if (insideToggle(mouseX, mouseY, 604, 93)) {
					VisualSettings.deathEffectEnabled = !VisualSettings.deathEffectEnabled;
				} else if (inside(mouseX, mouseY, 344, 146, 180, 25)) {
					VisualSettings.deathEffectColor = nextColor(VisualSettings.deathEffectColor);
				} else if (insideToggle(mouseX, mouseY, 604, 206)) {
					VisualSettings.hitEffectEnabled = !VisualSettings.hitEffectEnabled;
				} else if (inside(mouseX, mouseY, 32, 312, 180, 25)) {
					VisualSettings.hitEffectColor = nextColor(VisualSettings.hitEffectColor);
				} else {
					return false;
				}
			}
			case SETTINGS -> {
				if (inside(mouseX, mouseY, 326, 110, 220, 25)) {
					VisualSettings.menuAccentColor = nextColor(VisualSettings.menuAccentColor);
				} else if (insideToggle(mouseX, mouseY, 164, 226)) {
					VisualSettings.compactMode = !VisualSettings.compactMode;
				} else if (inside(mouseX, mouseY, 30, 284, 220, 30)) {
					resetConfirmation = true;
					return true;
				} else {
					return false;
				}
			}
			case WAYPOINTS -> {
				if (inside(mouseX, mouseY, 318, 112, 280, 24)) {
					activeTextField = "waypoint.name";
					placeCaretAtMouse(activeTextField, mouseX - 326);
				} else if (inside(mouseX, mouseY, 318, 152, 86, 24)) {
					activeTextField = "waypoint.x";
					placeCaretAtMouse(activeTextField, mouseX - 326);
				} else if (inside(mouseX, mouseY, 412, 152, 86, 24)) {
					activeTextField = "waypoint.y";
					placeCaretAtMouse(activeTextField, mouseX - 420);
				} else if (inside(mouseX, mouseY, 506, 152, 92, 24)) {
					activeTextField = "waypoint.z";
					placeCaretAtMouse(activeTextField, mouseX - 514);
				} else if (inside(mouseX, mouseY, 318, 192, 130, 28)) {
					selectedWaypoint = -1;
					waypointName = "";
					waypointX = "";
					waypointY = "";
					waypointZ = "";
					activeTextField = null;
				} else if (inside(mouseX, mouseY, 456, 192, 142, 28)) {
					saveWaypoint();
				} else if (inside(mouseX, mouseY, 318, 272, 280, 28) && selectedWaypoint >= 0
						&& selectedWaypoint < VisualSettings.waypoints.size()) {
					Waypoint waypoint = VisualSettings.waypoints.get(selectedWaypoint);
					waypoint.color = nextColor(waypoint.color);
				} else if (inside(mouseX, mouseY, 318, 232, 130, 28)) {
					if (selectedWaypoint >= 0 && selectedWaypoint < VisualSettings.waypoints.size()) {
						VisualSettings.waypoints.remove(selectedWaypoint);
						selectedWaypoint = -1;
						waypointName = "";
						waypointX = "";
						waypointY = "";
						waypointZ = "";
					}
				} else if (inside(mouseX, mouseY, 456, 232, 142, 28)) {
					useCurrentPosition();
				} else {
					int pageCount = Math.max(1,
							(VisualSettings.waypoints.size() + WAYPOINT_PAGE_SIZE - 1) / WAYPOINT_PAGE_SIZE);
					if (pageCount > 1 && inside(mouseX, mouseY, 28, 340, 36, 22)) {
						waypointPage = Math.max(0, waypointPage - 1);
						return true;
					}
					if (pageCount > 1 && inside(mouseX, mouseY, 250, 340, 36, 22)) {
						waypointPage = Math.min(pageCount - 1, waypointPage + 1);
						return true;
					}
					int start = waypointPage * WAYPOINT_PAGE_SIZE;
					int end = Math.min(VisualSettings.waypoints.size(), start + WAYPOINT_PAGE_SIZE);
					for (int index = start; index < end; index++) {
						int y = 118 + (index - start) * 24;
						if (inside(mouseX, mouseY, 28, y, 258, 21)) {
							selectedWaypoint = index;
							Waypoint selected = VisualSettings.waypoints.get(index);
							waypointName = Objects.requireNonNull(selected.name);
							waypointX = Objects.requireNonNull(Double.toString(selected.x));
							waypointY = Objects.requireNonNull(Double.toString(selected.y));
							waypointZ = Objects.requireNonNull(Double.toString(selected.z));
							if (insideToggle(mouseX, mouseY, 250, y + 4)) {
								Waypoint waypoint = VisualSettings.waypoints.get(index);
								waypoint.enabled = !waypoint.enabled;
							}
							break;
						}
					}
				}
			}
			case AUTO_SWAP -> {
				if (insideToggle(mouseX, mouseY, 158, 94)) {
					VisualSettings.autoSwapEnabled = !VisualSettings.autoSwapEnabled;
				} else if (inside(mouseX, mouseY, 24, 126, 280, 24)) {
					activeTextField = "autoswap.item";
					placeCaretAtMouse(activeTextField, mouseX - 32);
				} else if (inside(mouseX, mouseY, 314, 126, 140, 24)) {
					String itemIdText = autoSwapItemDraft;
					Identifier itemId = Identifier.tryParse(Objects.requireNonNull(itemIdText.strip()));
					if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
						notify("verityvisuals.autoswap.invalid_item");
						return true;
					}
					VisualSettings.autoSwapItemId = itemId.toString();
					autoSwapItemDraft = VisualSettings.autoSwapItemId;
					notify("verityvisuals.notification.saved");
				} else if (inside(mouseX, mouseY, 24, 196, 260, 28)) {
					activeTextField = null;
					capturingAutoSwapKey = true;
				}
			}
		}
		VisualSettings.save();
		notify("verityvisuals.notification.saved");
		return true;
	}

	private boolean handleLegacyHudClick(float mouseX, float mouseY) {
		if (insideToggle(mouseX, mouseY, 396, 333)) {
			VisualSettings.showArmor = !VisualSettings.showArmor;
		} else if (insideToggle(mouseX, mouseY, 564, 333)) {
			VisualSettings.showInventory = !VisualSettings.showInventory;
		} else if (insideToggle(mouseX, mouseY, 396, 352)) {
			VisualSettings.showEffects = !VisualSettings.showEffects;
		} else if (insideToggle(mouseX, mouseY, 564, 352)) {
			VisualSettings.showVitals = !VisualSettings.showVitals;
		} else if (insideToggle(mouseX, mouseY, 396, 371)) {
			VisualSettings.showSelectedItem = !VisualSettings.showSelectedItem;
		} else {
			return false;
		}
		return true;
	}

	private Component tooltipAt(float mouseX, float mouseY) {
		if (selectedTab == Tab.HUD && inside(mouseX, mouseY, 18, 93, 214, 261)) {
			int index = ((int) mouseY - 93) / 24;
			if (index >= 0 && index < HudModule.values().length) {
				return Component.translatable("verityvisuals.hud.module."
						+ HudModule.values()[index].id() + ".description");
			}
		}
		if (selectedTab == Tab.HUD && inside(mouseX, mouseY, 262, 143, 160, 12)) {
			return Component.translatable("verityvisuals.setting.hud_scale.tooltip");
		}
		if (selectedTab == Tab.HUD && inside(mouseX, mouseY, 262, 183, 270, 12)) {
			return Component.translatable("verityvisuals.setting.element_scale.tooltip");
		}
		if (selectedTab == Tab.VISUALS) {
			if (inside(mouseX, mouseY, 18, 78, 620, 30)) {
				return Component.translatable("verityvisuals.feature.block_outline.description");
			}
			if (inside(mouseX, mouseY, 18, 172, 620, 30)) {
				return Component.translatable("verityvisuals.feature.china_hat.description");
			}
			if (inside(mouseX, mouseY, 18, 322, 620, 30)) {
				return Component.translatable("verityvisuals.feature.fullbright.description");
			}
			if (inside(mouseX, mouseY, 30, 136, 270, 12)) {
				return Component.translatable("verityvisuals.setting.outline_thickness.tooltip");
			}
			if (inside(mouseX, mouseY, 30, 230, 270, 12)) {
				return Component.translatable("verityvisuals.setting.hat_size.tooltip");
			}
			if (inside(mouseX, mouseY, 326, 230, 270, 12)) {
				return Component.translatable("verityvisuals.setting.hat_height.tooltip");
			}
			if (inside(mouseX, mouseY, 30, 286, 270, 12)) {
				return Component.translatable("verityvisuals.setting.hat_opacity.tooltip");
			}
			if (inside(mouseX, mouseY, 326, 378, 270, 12)) {
				return Component.translatable("verityvisuals.setting.brightness_intensity.tooltip");
			}
		}
		if (selectedTab == Tab.EFFECTS) {
			if (inside(mouseX, mouseY, 32, 157, 280, 12)
					|| inside(mouseX, mouseY, 32, 269, 280, 12)) {
				return Component.translatable("verityvisuals.effects.duration.tooltip");
			}
			if (inside(mouseX, mouseY, 344, 269, 280, 12)) {
				return Component.translatable("verityvisuals.effects.intensity.tooltip");
			}
		}
		if (selectedTab == Tab.SETTINGS) {
			if (inside(mouseX, mouseY, 30, 120, 270, 12)) {
				return Component.translatable("verityvisuals.settings.menu_scale.tooltip");
			}
			if (inside(mouseX, mouseY, 30, 182, 270, 12)) {
				return Component.translatable("verityvisuals.settings.transparency.tooltip");
			}
			if (inside(mouseX, mouseY, 326, 182, 270, 12)) {
				return Component.translatable("verityvisuals.settings.animation.tooltip");
			}
		}
		return null;
	}

	private boolean hudEnabled(HudModule module) {
		if (module == HudModule.INVENTORY) {
			return VisualSettings.inventoryHudEnabled
					&& VisualSettings.hudElement(HudModule.INVENTORY).enabled;
		}
		return VisualSettings.hudElement(module).enabled;
	}

	private void toggleHud(HudModule module) {
		HudElementConfig config = VisualSettings.hudElement(module);
		config.enabled = !config.enabled;
		if (module == HudModule.INVENTORY) {
			VisualSettings.inventoryHudEnabled = config.enabled;
		}
	}

	private void notify(@NonNull String key) {
		notificationKey = key;
		notificationAt = System.nanoTime();
	}

	private static String currentDimension(Minecraft minecraft) {
		if (minecraft.level == null) {
			return "overworld";
		}
		return Waypoint.sanitizeDimension(minecraft.level.dimension().identifier().toString());
	}

	private void appendToTextField(String text) {
		if (activeTextField == null) {
			return;
		}
		String id = activeTextField;
		String current = textFieldValue(id);
		int caret = Math.min(textCarets.getOrDefault(id, current.length()), current.length());
		String accepted = id.startsWith("waypoint.") && !id.equals("waypoint.name")
				? coordinateCharacters(text) : text.replace('\n', ' ').replace('\r', ' ');
		int maximum = id.equals("autoswap.item") ? 64 : id.equals("waypoint.name") ? 32 : 16;
		String candidate = limitInput(current.substring(0, caret) + accepted + current.substring(caret), maximum);
		int nextCaret = Math.min(candidate.length(), caret + accepted.length());
		switch (id) {
			case "waypoint.name" -> waypointName = candidate;
			case "waypoint.x" -> waypointX = candidate;
			case "waypoint.y" -> waypointY = candidate;
			case "waypoint.z" -> waypointZ = candidate;
			case "autoswap.item" -> autoSwapItemDraft = candidate;
			default -> {
				return;
			}
		}
		textCarets.put(id, nextCaret);
	}

	private void updateTextField() {
		if (activeTextField == null) {
			return;
		}
		String id = activeTextField;
		String value = textFieldValue(id);
		int caret = Math.min(textCarets.getOrDefault(id, value.length()), value.length());
		if (caret == 0) {
			return;
		}
		int previous = previousOffset(value, caret);
		setTextFieldValue(id, value.substring(0, previous) + value.substring(caret));
		textCarets.put(id, previous);
	}

	private static String limitInput(String value, int length) {
		int end = 0;
		for (int count = 0; count < length && end < value.length(); count++) {
			end = nextOffset(value, end);
		}
		return value.substring(0, end).replace('\n', ' ').replace('\r', ' ');
	}

	private static String coordinateCharacters(String text) {
		StringBuilder filtered = new StringBuilder(text.length());
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (Character.isDigit(character) || character == '-' || character == '.'
					|| character == ',') {
				filtered.append(character);
			}
		}
		return filtered.toString();
	}

	private void setTextFieldValue(String id, @NonNull String value) {
		switch (id) {
			case "waypoint.name" -> waypointName = value;
			case "waypoint.x" -> waypointX = value;
			case "waypoint.y" -> waypointY = value;
			case "waypoint.z" -> waypointZ = value;
			case "autoswap.item" -> autoSwapItemDraft = value;
			default -> {
			}
		}
	}

	private void deleteForward() {
		if (activeTextField == null) {
			return;
		}
		String id = activeTextField;
		String value = textFieldValue(id);
		int caret = Math.min(textCarets.getOrDefault(id, value.length()), value.length());
		if (caret >= value.length()) {
			return;
		}
		int next = nextOffset(value, caret);
		setTextFieldValue(id, value.substring(0, caret) + value.substring(next));
	}

	private void moveCaret(int key) {
		if (activeTextField == null) {
			return;
		}
		String id = activeTextField;
		String value = textFieldValue(id);
		int caret = Math.min(textCarets.getOrDefault(id, value.length()), value.length());
		int nextCaret = switch (key) {
			case GLFW.GLFW_KEY_LEFT -> previousOffset(value, caret);
			case GLFW.GLFW_KEY_RIGHT -> nextOffset(value, caret);
			case GLFW.GLFW_KEY_HOME -> 0;
			case GLFW.GLFW_KEY_END -> value.length();
			default -> caret;
		};
		textCarets.put(id, nextCaret);
	}

	private boolean isAutoSwapKeyButton(float mouseX, float mouseY) {
		return selectedTab == Tab.AUTO_SWAP && inside(mouseX, mouseY, 24, 196, 260, 28);
	}

	private void saveWaypoint() {
		try {
			double x = Double.parseDouble(waypointX.replace(',', '.'));
			double y = Double.parseDouble(waypointY.replace(',', '.'));
			double z = Double.parseDouble(waypointZ.replace(',', '.'));
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
					|| x < -30_000_000 || x > 30_000_000
					|| y < -2048 || y > 2048
					|| z < -30_000_000 || z > 30_000_000) {
				notify("verityvisuals.waypoint.invalid");
				return;
			}
			String dimension = "overworld";
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null) {
				dimension = Waypoint.sanitizeDimension(minecraft.level.dimension().identifier().toString());
			}
			if (selectedWaypoint >= 0 && selectedWaypoint < VisualSettings.waypoints.size()) {
				Waypoint waypoint = VisualSettings.waypoints.get(selectedWaypoint);
				waypoint.name = Waypoint.normalizeName(waypointName);
				waypoint.x = x;
				waypoint.y = y;
				waypoint.z = z;
				waypoint.dimension = dimension;
			} else {
				Waypoint waypoint = new Waypoint(Waypoint.normalizeName(waypointName), x, y, z, dimension,
						nextColor(0xFFFFC900));
				VisualSettings.waypoints.add(waypoint);
				selectedWaypoint = VisualSettings.waypoints.size() - 1;
				waypointPage = selectedWaypoint / WAYPOINT_PAGE_SIZE;
			}
			VisualSettings.save();
			notify("verityvisuals.notification.saved");
		} catch (NumberFormatException exception) {
			notify("verityvisuals.waypoint.invalid");
		}
	}

	private void useCurrentPosition() {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 position = minecraft.player == null ? null : minecraft.player.position();
		if (position == null) {
			notify("verityvisuals.waypoint.invalid");
			return;
		}
		waypointX = Objects.requireNonNull(String.format(Locale.ROOT, "%.1f", position.x));
		waypointY = Objects.requireNonNull(String.format(Locale.ROOT, "%.1f", position.y));
		waypointZ = Objects.requireNonNull(String.format(Locale.ROOT, "%.1f", position.z));
		notify("verityvisuals.waypoint.current");
	}

	private float animationProgress() {
		long now = System.nanoTime();
		float duration = VisualSettings.animationIntensity <= 0.0F
				? 0.0F
				: 220_000_000.0F / VisualSettings.animationIntensity;
		if (duration == 0.0F) {
			return closingAt == 0L ? 1.0F : 0.0F;
		}
		float progress = Math.min(1.0F, (now - (closingAt == 0L ? openedAt : closingAt)) / duration);
		return closingAt == 0L ? ease(progress) : 1.0F - ease(progress);
	}

	private Layout getLayout(float animation) {
		float widthScale = Math.max(0.25F, (width - 32.0F) / PANEL_WIDTH);
		float heightScale = Math.max(0.25F, (height - 32.0F) / PANEL_HEIGHT);
		float fitScale = Math.min(widthScale, heightScale);
		float desiredScale = effectiveMenuScale() * (VisualSettings.compactMode ? 0.9F : 1.0F);
		float scale = Math.max(0.25F, Math.min(fitScale, desiredScale));
		float openingScale = 1.0F - 0.035F * VisualSettings.animationIntensity * (1.0F - animation);
		scale *= Math.max(0.8F, openingScale);
		scale = Math.max(0.25F, scale);
		return new Layout(scale, (width - PANEL_WIDTH * scale) / 2.0F,
				(height - PANEL_HEIGHT * scale) / 2.0F);
	}

	private float maximumMenuScale() {
		float widthScale = Math.max(0.25F, (width - 32.0F) / PANEL_WIDTH);
		float heightScale = Math.max(0.25F, (height - 32.0F) / PANEL_HEIGHT);
		float compactFactor = VisualSettings.compactMode ? 0.9F : 1.0F;
		float maximumFitScale = Math.min(widthScale, heightScale) / compactFactor;
		return Math.max(0.25F, Math.min(1.25F, maximumFitScale));
	}

	private float minimumMenuScale() {
		return Math.min(0.75F, maximumMenuScale());
	}

	private float effectiveMenuScale() {
		return Math.max(minimumMenuScale(),
				Math.min(maximumMenuScale(), VisualSettings.menuScale));
	}

	private float animate(Map<String, Float> values, String key, float target) {
		float previous = values.getOrDefault(key, target);
		float next = previous + (target - previous) * 0.22F;
		if (Math.abs(target - next) < 0.002F) {
			next = target;
		}
		values.put(key, next);
		return next;
	}

	private static float ease(float value) {
		return 1.0F - (float) Math.pow(1.0F - Math.max(0.0F, Math.min(1.0F, value)), 3.0);
	}

	private static float lerp(float minimum, float maximum, float fraction) {
		return minimum + (maximum - minimum) * fraction;
	}

	private static int nextColor(int current) {
		if (current == 0xFFFFC900) {
			return 0xFF55D6FF;
		}
		if (current == 0xFF55D6FF) {
			return 0xFFFF66CC;
		}
		if (current == 0xFFFF66CC) {
			return 0xFFFFFFFF;
		}
		return 0xFFFFC900;
	}

	private static int withOpacity(int argb, float opacity) {
		int alpha = Math.max(0, Math.min(255, Math.round((argb >>> 24) * opacity)));
		return (argb & 0x00FFFFFF) | alpha << 24;
	}

	private static int interpolateColor(int from, int to, float amount) {
		int red = Math.round(((from >> 16) & 255) * (1.0F - amount) + ((to >> 16) & 255) * amount);
		int green = Math.round(((from >> 8) & 255) * (1.0F - amount) + ((to >> 8) & 255) * amount);
		int blue = Math.round((from & 255) * (1.0F - amount) + (to & 255) * amount);
		return (from & 0xFF000000) | red << 16 | green << 8 | blue;
	}

	private static boolean inside(float x, float y, int left, int top, int boxWidth, int boxHeight) {
		return x >= left && x <= left + boxWidth && y >= top && y <= top + boxHeight;
	}

	private static boolean insideToggle(float mouseX, float mouseY, int x, int y) {
		return inside(mouseX, mouseY, x - 4, y - 2, 42, 18);
	}

	@Override
	public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		Layout layout = getLayout(animationProgress());
		float mouseX = layout.toPanelX(event.x());
		float mouseY = layout.toPanelY(event.y());
		if (resetConfirmation) {
			return handleClick(mouseX, mouseY);
		}
		if (selectTab(mouseX, mouseY)) {
			return true;
		}
		float contentMouseX = mouseX - tabTransitionOffset();
		if (activeTextField != null && !activeFieldContains(contentMouseX, mouseY)) {
			activeTextField = null;
		}
		if (capturingAutoSwapKey && !isAutoSwapKeyButton(contentMouseX, mouseY)) {
			capturingAutoSwapKey = false;
		}
		if (beginSliderDrag(contentMouseX, mouseY)) {
			return true;
		}
		if (handleClick(contentMouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
		if (draggingSlider == null) {
			return super.mouseDragged(event, deltaX, deltaY);
		}
		Layout layout = getLayout(animationProgress());
		applySlider(layout.toPanelX(event.x()) - tabTransitionOffset());
		return true;
	}

	private float tabTransitionOffset() {
		float progress = Math.min(1.0F, (System.nanoTime() - tabChangedAt) / 180_000_000.0F);
		return (1.0F - ease(progress)) * 14.0F;
	}

	@Override
	public boolean mouseReleased(@NonNull MouseButtonEvent event) {
		if (draggingSlider != null) {
			draggingSlider = null;
			VisualSettings.save();
			notify("verityvisuals.notification.saved");
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(@NonNull KeyEvent event) {
		if (capturingAutoSwapKey) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				capturingAutoSwapKey = false;
				return true;
			}
			for (var hotbarKey : Minecraft.getInstance().options.keyHotbarSlots) {
				if (hotbarKey.matches(event)) {
					notify("verityvisuals.autoswap.key_conflict");
					return true;
				}
			}
			AutoSwapController.setActivationKeyCode(event.key());
			if (event.key() >= GLFW.GLFW_KEY_SPACE && event.key() <= GLFW.GLFW_KEY_LAST) {
				VisualSettings.save();
				capturingAutoSwapKey = false;
				notify("verityvisuals.notification.saved");
			}
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (resetConfirmation) {
				resetConfirmation = false;
			} else {
				requestClose();
			}
			return true;
		}
		if (activeTextField != null) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_BACKSPACE -> updateTextField();
				case GLFW.GLFW_KEY_DELETE -> deleteForward();
				case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
						GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> moveCaret(event.key());
				default -> {
					return super.keyPressed(event);
				}
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(@NonNull CharacterEvent event) {
		if (activeTextField != null) {
			appendToTextField(event.codepointAsString());
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		VisualSettings.save();
		super.onClose();
	}

	@Override
	public void removed() {
		if (draggingSlider != null) {
			draggingSlider = null;
			VisualSettings.save();
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Tab {
		HUD("verityvisuals.tab.hud"),
		VISUALS("verityvisuals.tab.visuals"),
		EFFECTS("verityvisuals.tab.effects"),
		WAYPOINTS("verityvisuals.tab.waypoints"),
		AUTO_SWAP("verityvisuals.tab.auto_swap"),
		SETTINGS("verityvisuals.tab.settings");

		private final @NonNull String translationKey;

		Tab(@NonNull String translationKey) {
			this.translationKey = translationKey;
		}
	}

	private record Layout(float scale, float offsetX, float offsetY) {
		private float toPanelX(double x) {
			return (float) ((x - offsetX) / scale);
		}

		private float toPanelY(double y) {
			return (float) ((y - offsetY) / scale);
		}
	}
}
