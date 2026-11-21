package com.verityvisuals.client.mixin;

import com.verityvisuals.client.config.VisualSettings;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
public abstract class FullBrightMixin {
	@ModifyArg(
			method = "updateLightTexture(F)V",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
					ordinal = 6,
					remap = false
			),
			index = 0
	)
	private float verity$boostBrightnessFactor(float vanillaFactor) {
		if (!VisualSettings.fullbrightEnabled) {
			return vanillaFactor;
		}
		return Math.max(vanillaFactor, VisualSettings.brightness * 8.0F);
	}
}
