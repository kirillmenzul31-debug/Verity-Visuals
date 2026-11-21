package com.verityvisuals.client.config;

public enum HudModule {
	INVENTORY("inventory", 8),
	COORDINATES("coordinates", 8),
	PERFORMANCE("performance", 38),
	KEYSTROKES("keystrokes", 62),
	CPS("cps", 112),
	ARMOR_STATUS("armor_status", 144),
	POTION_EFFECTS("potion_effects", 210),
	HEALTH_FOOD("health_food", 278),
	TARGET_INFO("target_info", 308),
	DIRECTION("direction", 354);

	private final String id;
	private final int defaultY;

	HudModule(String id, int defaultY) {
		this.id = id;
		this.defaultY = defaultY;
	}

	public String id() {
		return id;
	}

	public int defaultY() {
		return defaultY;
	}
}
