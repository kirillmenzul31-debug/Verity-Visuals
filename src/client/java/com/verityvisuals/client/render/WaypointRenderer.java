package com.verityvisuals.client.render;

import com.verityvisuals.client.config.VisualSettings;
import com.verityvisuals.client.config.Waypoint;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class WaypointRenderer {
	private static final double MAX_RENDER_DISTANCE = 256.0;
	private static final float MARKER_HALF_SIZE = 0.16F;
	private static final float MARKER_LINE_WIDTH = 1.5F;

	private WaypointRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			Minecraft minecraft = Minecraft.getInstance();
			var player = minecraft.player;
			var level = minecraft.level;
			if (player == null || level == null) {
				return;
			}
			String currentDimension = Waypoint.sanitizeDimension(
					level.dimension().identifier().toString());
			Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
			Vec3 playerPosition = player.position();
			for (Waypoint waypoint : VisualSettings.waypoints) {
				if (!waypoint.enabled || !currentDimension.equals(Waypoint.sanitizeDimension(waypoint.dimension))) {
					continue;
				}
				Vec3 waypointPosition = new Vec3(waypoint.x, waypoint.y, waypoint.z);
				if (playerPosition.distanceToSqr(waypointPosition) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
					continue;
				}
				ShapeRenderer.renderShape(
						context.matrices(),
						context.consumers().getBuffer(RenderTypes.lines()),
						Shapes.box(-MARKER_HALF_SIZE, -MARKER_HALF_SIZE, -MARKER_HALF_SIZE,
								MARKER_HALF_SIZE, MARKER_HALF_SIZE, MARKER_HALF_SIZE),
						waypoint.x - camera.x,
						waypoint.y - camera.y,
						waypoint.z - camera.z,
						waypoint.color,
						MARKER_LINE_WIDTH);
			}
		});
	}
}
