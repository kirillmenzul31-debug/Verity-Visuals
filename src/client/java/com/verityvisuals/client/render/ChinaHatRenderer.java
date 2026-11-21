package com.verityvisuals.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

public final class ChinaHatRenderer {
	private ChinaHatRenderer() {
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityType == EntityType.PLAYER && entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new ChinaHatFeatureRenderer(avatarRenderer));
			}
		});
	}
}
