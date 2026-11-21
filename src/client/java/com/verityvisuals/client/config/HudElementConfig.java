package com.verityvisuals.client.config;

public final class HudElementConfig {
	public boolean enabled;
	public int x = -1;
	public int y;
	public float scale = 1.0F;
	public int color = 0xFFFFFFFF;

	public HudElementConfig() {
	}

	public HudElementConfig(boolean enabled, int x, int y, float scale, int color) {
		this.enabled = enabled;
		this.x = x;
		this.y = y;
		this.scale = scale;
		this.color = color;
	}

	public HudElementConfig copy() {
		HudElementConfig copy = new HudElementConfig(enabled, x, y, scale, color);
		copy.scale = Float.isFinite(scale) ? Math.max(0.5F, Math.min(2.0F, scale)) : 1.0F;
		copy.y = Math.max(0, y);
		return copy;
	}
}
