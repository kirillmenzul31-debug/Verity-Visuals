package com.verityvisuals.client.config;

import java.util.Locale;
import java.util.UUID;

public final class Waypoint {
	public String id;
	public String name;
	public double x;
	public double y;
	public double z;
	public String dimension;
	public int color;
	public boolean enabled = true;

	public Waypoint() {
		this.id = UUID.randomUUID().toString();
		this.name = "Waypoint";
		this.dimension = "overworld";
		this.color = 0xFFFFC900;
	}

	public Waypoint(String name, double x, double y, double z,
					String dimension, int color) {
		this.id = UUID.randomUUID().toString();
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
		this.enabled = true;
	}

	public static String sanitizeDimension(String dimension) {
		if (dimension == null || dimension.isBlank()) {
			return "minecraft:overworld";
		}
		String normalized = dimension.strip().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "overworld" -> "minecraft:overworld";
			case "nether", "the_nether" -> "minecraft:the_nether";
			case "end", "the_end" -> "minecraft:the_end";
			default -> normalized;
		};
	}

	public static String normalizeName(String name) {
		if (name == null) {
			return "Waypoint";
		}
		String normalized = name.strip();
		return normalized.isEmpty() ? "Waypoint" : normalized.substring(0, Math.min(32, normalized.length()));
	}
}
