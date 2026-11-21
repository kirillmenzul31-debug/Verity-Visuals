package com.verityvisuals.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.verityvisuals.client.VerityVisualsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public final class VisualSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("verityvisuals.json");
	public static final Map<HudModule, HudElementConfig> hudElements = createHudElements();

	public static boolean blockOutlineEnabled;
	public static boolean chinaHatEnabled;
	public static boolean inventoryHudEnabled = true;
	public static boolean fullbrightEnabled;
	public static float outlineThickness = 2.0F;
	public static int outlineColor = 0xFFFFC900;
	public static float chinaHatSize = 0.7F;
	public static float chinaHatHeight = 0.3F;
	public static float chinaHatOpacity = 0.8F;
	public static int chinaHatColor = 0xFFFFC900;
	public static float hudScale = 1.0F;
	public static boolean showArmor = true;
	public static boolean showInventory = true;
	public static boolean showEffects = true;
	public static boolean showVitals = true;
	public static boolean showSelectedItem = true;
	public static int hudColor = 0xFFFFFFFF;
	public static float brightness = 0.9F;
	public static boolean deathEffectEnabled;
	public static float deathEffectDuration = 2.5F;
	public static int deathEffectColor = 0xFFFF3344;
	public static boolean hitEffectEnabled;
	public static float hitEffectDuration = 0.35F;
	public static float hitEffectIntensity = 0.65F;
	public static int hitEffectColor = 0xFFFFC900;
	public static float menuScale = 1.0F;
	public static int menuAccentColor = 0xFFFFC900;
	public static float menuTransparency = 0.94F;
	public static float animationIntensity = 1.0F;
	public static boolean compactMode;
	public static String selectedMenuTab = "HUD";
	public static final List<Waypoint> waypoints = new ArrayList<>();
	public static boolean autoSwapEnabled;
	public static @NonNull String autoSwapItemId = "";
	public static int autoSwapKeyCode = GLFW.GLFW_KEY_G;
	public static String autoSwapCondition = "ALWAYS";

	private VisualSettings() {
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}

		try {
			SettingsData saved = GSON.fromJson(Files.readString(FILE), SettingsData.class);
			if (saved != null) {
				copyFrom(saved);
			}
		} catch (IOException | RuntimeException exception) {
			VerityVisualsClient.LOGGER.error("Could not load Verity Visuals settings from {}", FILE, exception);
		}
	}

	public static void save() {
		Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(temporary, GSON.toJson(toData()));
			try {
				Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			VerityVisualsClient.LOGGER.error("Could not save Verity Visuals settings to {}", FILE, exception);
		}
	}

	private static void copyFrom(SettingsData saved) {
		blockOutlineEnabled = saved.blockOutlineEnabled;
		chinaHatEnabled = saved.chinaHatEnabled;
		inventoryHudEnabled = saved.inventoryHudEnabled;
		fullbrightEnabled = saved.fullbrightEnabled;
		outlineThickness = clamp(saved.outlineThickness, 1.0F, 6.0F, 2.0F);
		outlineColor = saved.outlineColor;
		chinaHatSize = clamp(saved.chinaHatSize, 0.4F, 1.3F, 0.7F);
		chinaHatHeight = clamp(saved.chinaHatHeight, 0.25F, 1.0F, 0.3F);
		chinaHatOpacity = clamp(saved.chinaHatOpacity, 0.1F, 1.0F, 0.8F);
		chinaHatColor = saved.chinaHatColor;
		hudScale = clamp(saved.hudScale, 0.5F, 2.0F, 1.0F);
		showArmor = saved.showArmor;
		showInventory = saved.showInventory;
		showEffects = saved.showEffects;
		showVitals = saved.showVitals;
		showSelectedItem = saved.showSelectedItem;
		hudColor = saved.hudColor;
		brightness = clamp(saved.brightness, 0.6F, 1.0F, 0.9F);
		if (saved.hudElements != null) {
			for (HudModule module : HudModule.values()) {
				HudElementConfig element = saved.hudElements.get(module);
				if (element != null) {
					hudElements.put(module, element.copy());
				}
			}
		} else {
			hudElement(HudModule.INVENTORY).color = hudColor;
		}
		deathEffectEnabled = saved.deathEffectEnabled;
		deathEffectDuration = clamp(saved.deathEffectDuration, 0.5F, 8.0F, 2.5F);
		deathEffectColor = saved.deathEffectColor;
		hitEffectEnabled = saved.hitEffectEnabled;
		hitEffectDuration = clamp(saved.hitEffectDuration, 0.1F, 2.0F, 0.35F);
		hitEffectIntensity = clamp(saved.hitEffectIntensity, 0.1F, 1.0F, 0.65F);
		hitEffectColor = saved.hitEffectColor;
		menuScale = clamp(saved.menuScale, 0.75F, 1.25F, 1.0F);
		menuAccentColor = saved.menuAccentColor;
		menuTransparency = clamp(saved.menuTransparency, 0.45F, 1.0F, 0.94F);
		animationIntensity = clamp(saved.animationIntensity, 0.0F, 1.5F, 1.0F);
		compactMode = saved.compactMode;
		if (saved.selectedMenuTab != null && !saved.selectedMenuTab.isBlank()) {
			selectedMenuTab = saved.selectedMenuTab;
		}
		waypoints.clear();
		if (saved.waypoints != null) {
			for (Waypoint waypoint : saved.waypoints) {
				if (waypoint != null) {
					waypoint.name = Waypoint.normalizeName(waypoint.name);
					waypoint.dimension = Waypoint.sanitizeDimension(waypoint.dimension);
					if (Double.isFinite(waypoint.x) && Double.isFinite(waypoint.y)
							&& Double.isFinite(waypoint.z)
							&& waypoint.x >= -30_000_000.0 && waypoint.x <= 30_000_000.0
							&& waypoint.y >= -2048.0 && waypoint.y <= 2048.0
							&& waypoint.z >= -30_000_000.0 && waypoint.z <= 30_000_000.0) {
						waypoints.add(waypoint);
					}
				}
			}
		}
		autoSwapEnabled = saved.autoSwapEnabled;
		autoSwapItemId = saved.autoSwapItemId == null
				? ""
				: Objects.requireNonNull(saved.autoSwapItemId.strip());
		autoSwapKeyCode = saved.autoSwapKeyCode >= GLFW.GLFW_KEY_SPACE
				&& saved.autoSwapKeyCode <= GLFW.GLFW_KEY_LAST
				? saved.autoSwapKeyCode : GLFW.GLFW_KEY_G;
		autoSwapCondition = switch (saved.autoSwapCondition == null ? "" : saved.autoSwapCondition) {
			case "LOW_HEALTH", "IN_NETHER", "IN_END" -> saved.autoSwapCondition;
			default -> "ALWAYS";
		};
	}

	private static float clamp(float value, float minimum, float maximum, float fallback) {
		return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
	}

	private static SettingsData toData() {
		SettingsData data = new SettingsData();
		data.blockOutlineEnabled = blockOutlineEnabled;
		data.chinaHatEnabled = chinaHatEnabled;
		data.inventoryHudEnabled = inventoryHudEnabled;
		data.fullbrightEnabled = fullbrightEnabled;
		data.outlineThickness = outlineThickness;
		data.outlineColor = outlineColor;
		data.chinaHatSize = chinaHatSize;
		data.chinaHatHeight = chinaHatHeight;
		data.chinaHatOpacity = chinaHatOpacity;
		data.chinaHatColor = chinaHatColor;
		data.hudScale = hudScale;
		data.showArmor = showArmor;
		data.showInventory = showInventory;
		data.showEffects = showEffects;
		data.showVitals = showVitals;
		data.showSelectedItem = showSelectedItem;
		data.hudColor = hudColor;
		data.brightness = brightness;
		data.hudElements = new EnumMap<>(hudElements);
		data.deathEffectEnabled = deathEffectEnabled;
		data.deathEffectDuration = deathEffectDuration;
		data.deathEffectColor = deathEffectColor;
		data.hitEffectEnabled = hitEffectEnabled;
		data.hitEffectDuration = hitEffectDuration;
		data.hitEffectIntensity = hitEffectIntensity;
		data.hitEffectColor = hitEffectColor;
		data.menuScale = menuScale;
		data.menuAccentColor = menuAccentColor;
		data.menuTransparency = menuTransparency;
		data.animationIntensity = animationIntensity;
		data.compactMode = compactMode;
		data.selectedMenuTab = selectedMenuTab;
		data.waypoints = new ArrayList<>(waypoints);
		data.autoSwapEnabled = autoSwapEnabled;
		data.autoSwapItemId = autoSwapItemId;
		data.autoSwapKeyCode = autoSwapKeyCode;
		data.autoSwapCondition = autoSwapCondition;
		return data;
	}

	private static Map<HudModule, HudElementConfig> createHudElements() {
		Map<HudModule, HudElementConfig> elements = new EnumMap<>(HudModule.class);
		for (HudModule module : HudModule.values()) {
			elements.put(module, new HudElementConfig(
					module == HudModule.INVENTORY, -1, module.defaultY(), 1.0F, 0xFFFFFFFF));
		}
		return elements;
	}

	public static HudElementConfig hudElement(HudModule module) {
		return hudElements.computeIfAbsent(module, key -> new HudElementConfig(
				key == HudModule.INVENTORY, -1, key.defaultY(), 1.0F, 0xFFFFFFFF));
	}

	public static void resetToDefaults() {
		hudElements.clear();
		hudElements.putAll(createHudElements());
		blockOutlineEnabled = false;
		chinaHatEnabled = false;
		inventoryHudEnabled = true;
		fullbrightEnabled = false;
		outlineThickness = 2.0F;
		outlineColor = 0xFFFFC900;
		chinaHatSize = 0.7F;
		chinaHatHeight = 0.3F;
		chinaHatOpacity = 0.8F;
		chinaHatColor = 0xFFFFC900;
		hudScale = 1.0F;
		showArmor = true;
		showInventory = true;
		showEffects = true;
		showVitals = true;
		showSelectedItem = true;
		hudColor = 0xFFFFFFFF;
		brightness = 0.9F;
		deathEffectEnabled = false;
		deathEffectDuration = 2.5F;
		deathEffectColor = 0xFFFF3344;
		hitEffectEnabled = false;
		hitEffectDuration = 0.35F;
		hitEffectIntensity = 0.65F;
		hitEffectColor = 0xFFFFC900;
		menuScale = 1.0F;
		menuAccentColor = 0xFFFFC900;
		menuTransparency = 0.94F;
		animationIntensity = 1.0F;
		compactMode = false;
		selectedMenuTab = "HUD";
		waypoints.clear();
		autoSwapEnabled = false;
		autoSwapItemId = "";
		autoSwapKeyCode = GLFW.GLFW_KEY_G;
		autoSwapCondition = "ALWAYS";
	}

	private static final class SettingsData {
		private boolean blockOutlineEnabled;
		private boolean chinaHatEnabled;
		private boolean inventoryHudEnabled = true;
		private boolean fullbrightEnabled;
		private float outlineThickness = 2.0F;
		private int outlineColor = 0xFFFFC900;
		private float chinaHatSize = 0.7F;
		private float chinaHatHeight = 0.3F;
		private float chinaHatOpacity = 0.8F;
		private int chinaHatColor = 0xFFFFC900;
		private float hudScale = 1.0F;
		private boolean showArmor = true;
		private boolean showInventory = true;
		private boolean showEffects = true;
		private boolean showVitals = true;
		private boolean showSelectedItem = true;
		private int hudColor = 0xFFFFFFFF;
		private float brightness = 0.9F;
		private Map<HudModule, HudElementConfig> hudElements;
		private boolean deathEffectEnabled;
		private float deathEffectDuration = 2.5F;
		private int deathEffectColor = 0xFFFF3344;
		private boolean hitEffectEnabled;
		private float hitEffectDuration = 0.35F;
		private float hitEffectIntensity = 0.65F;
		private int hitEffectColor = 0xFFFFC900;
		private float menuScale = 1.0F;
		private int menuAccentColor = 0xFFFFC900;
		private float menuTransparency = 0.94F;
		private float animationIntensity = 1.0F;
		private boolean compactMode;
		private String selectedMenuTab = "HUD";
		private List<Waypoint> waypoints = new ArrayList<>();
		private boolean autoSwapEnabled;
		private String autoSwapItemId = "";
		private int autoSwapKeyCode = GLFW.GLFW_KEY_G;
		private String autoSwapCondition = "ALWAYS";
	}
}
