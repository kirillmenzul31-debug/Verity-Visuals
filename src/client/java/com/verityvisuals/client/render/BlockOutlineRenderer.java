package com.verityvisuals.client.render;

import com.verityvisuals.client.config.VisualSettings;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class BlockOutlineRenderer {
	private BlockOutlineRenderer() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			if (!VisualSettings.blockOutlineEnabled) {
				return true;
			}

			Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
			ShapeRenderer.renderShape(
					context.matrices(),
					context.consumers().getBuffer(RenderTypes.lines()),
					outline.shape(),
					outline.pos().getX() - camera.x,
					outline.pos().getY() - camera.y,
					outline.pos().getZ() - camera.z,
					VisualSettings.outlineColor,
					VisualSettings.outlineThickness
			);
			return false;
		});
	}
}
